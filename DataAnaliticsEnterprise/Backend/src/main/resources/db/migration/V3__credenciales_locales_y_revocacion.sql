CREATE TABLE identidad.credencial_local (
    user_id uuid PRIMARY KEY REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    password_hash text NOT NULL CHECK(password_hash ~ '^\{bcrypt\}\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),
    session_version bigint NOT NULL DEFAULT 0 CHECK(session_version>=0),
    changed_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION identidad.versionar_cambio_clave() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
        NEW.session_version := OLD.session_version + 1;
        NEW.changed_at := now();
    END IF;
    IF NEW.session_version < OLD.session_version THEN
        RAISE EXCEPTION 'La version de sesion no puede retroceder' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER credencial_version BEFORE UPDATE ON identidad.credencial_local
    FOR EACH ROW EXECUTE FUNCTION identidad.versionar_cambio_clave();

CREATE FUNCTION identidad.revocar_por_cambio_estado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        UPDATE identidad.credencial_local SET session_version=session_version+1 WHERE user_id=NEW.id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER usuario_revocacion AFTER UPDATE OF status ON identidad.usuario
    FOR EACH ROW EXECUTE FUNCTION identidad.revocar_por_cambio_estado();
