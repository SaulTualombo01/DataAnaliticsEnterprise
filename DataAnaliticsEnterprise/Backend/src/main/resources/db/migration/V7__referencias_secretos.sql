-- TC-11: no clave maestra ni credenciales en claro. Esquema privado del propietario.
CREATE SCHEMA secretos;
CREATE TABLE secretos.referencia (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES contexto.proyecto(id) ON DELETE RESTRICT,
    source_id uuid NOT NULL,
    revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
    status text NOT NULL CHECK(status IN ('ACTIVE','DISABLED','RETIRED')),
    target_host text NOT NULL CHECK(length(target_host) BETWEEN 1 AND 253),
    target_port integer NOT NULL CHECK(target_port BETWEEN 1 AND 65535),
    target_database text NOT NULL CHECK(length(target_database) BETWEEN 1 AND 128),
    sslmode text NOT NULL CHECK(sslmode IN ('verify-full','verify-ca','require','disable')),
    key_id text,
    nonce bytea,
    ciphertext bytea,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK((status='RETIRED' AND key_id IS NULL AND nonce IS NULL AND ciphertext IS NULL)
       OR (status IN ('ACTIVE','DISABLED') AND key_id IS NOT NULL AND key_id ~ '^[a-z][a-z0-9_]{0,31}$'
           AND nonce IS NOT NULL AND octet_length(nonce)=12 AND ciphertext IS NOT NULL
           AND octet_length(ciphertext) BETWEEN 26 AND 8728))
);
CREATE UNIQUE INDEX referencia_fuente_vigente_uniq ON secretos.referencia(project_id,source_id) WHERE status<>'RETIRED';
CREATE INDEX referencia_fuente_idx ON secretos.referencia(project_id,source_id);
CREATE INDEX referencia_clave_idx ON secretos.referencia(key_id) WHERE status<>'RETIRED';
-- Sin FK al catálogo: M1 conserva la propiedad; SPI confirma fuente/proyecto vigentes.
CREATE FUNCTION secretos.conservar_identidad() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Referencia preservada' USING ERRCODE='23000';
    END IF;
    IF NEW.id<>OLD.id OR NEW.project_id<>OLD.project_id OR NEW.source_id<>OLD.source_id
        OR NEW.created_at<>OLD.created_at OR OLD.status='RETIRED' OR NEW.revision<>OLD.revision+1 THEN
        RAISE EXCEPTION 'Transicion de referencia invalida' USING ERRCODE='23000';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER referencia_identidad BEFORE UPDATE OR DELETE ON secretos.referencia
    FOR EACH ROW EXECUTE FUNCTION secretos.conservar_identidad();
