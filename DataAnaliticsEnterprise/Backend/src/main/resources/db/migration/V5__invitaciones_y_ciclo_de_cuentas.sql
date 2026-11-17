-- Extensión aditiva: nunca crea contraseñas predeterminadas ni modifica V1..V4.
CREATE TABLE identidad.invitacion (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES contexto.organizacion(id) ON DELETE RESTRICT,
    email text NOT NULL CHECK(email=lower(btrim(email)) AND length(email) BETWEEN 3 AND 254),
    display_name text NOT NULL CHECK(length(btrim(display_name)) BETWEEN 1 AND 200),
    invited_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    token_hash text NOT NULL UNIQUE CHECK(token_hash ~ '^[0-9a-f]{64}$'),
    status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','revoked')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK(expires_at>created_at),
    accepted_user_id uuid REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    accepted_at timestamptz,
    revoked_at timestamptz,
    CHECK((status='pending' AND accepted_user_id IS NULL AND accepted_at IS NULL AND revoked_at IS NULL)
       OR (status='accepted' AND accepted_user_id IS NOT NULL AND accepted_at IS NOT NULL AND revoked_at IS NULL)
       OR (status='revoked' AND accepted_user_id IS NULL AND accepted_at IS NULL AND revoked_at IS NOT NULL))
);
CREATE UNIQUE INDEX invitacion_pendiente_uniq ON identidad.invitacion(organization_id,email) WHERE status='pending';
CREATE INDEX invitacion_org_fecha_idx ON identidad.invitacion(organization_id,created_at,id);
CREATE INDEX invitacion_actor_idx ON identidad.invitacion(invited_by);
CREATE INDEX invitacion_usuario_idx ON identidad.invitacion(accepted_user_id);

CREATE FUNCTION identidad.proteger_invitacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'El historial de invitacion se conserva' USING ERRCODE='23000'; END IF;
    IF OLD.status<>'pending' OR NEW.id<>OLD.id OR NEW.organization_id<>OLD.organization_id
       OR NEW.email<>OLD.email OR NEW.display_name<>OLD.display_name OR NEW.invited_by<>OLD.invited_by
       OR NEW.token_hash<>OLD.token_hash OR NEW.created_at<>OLD.created_at OR NEW.expires_at<>OLD.expires_at
       OR NEW.status NOT IN ('accepted','revoked') THEN
        RAISE EXCEPTION 'La invitacion no puede reescribirse ni reutilizarse' USING ERRCODE='23000';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER invitacion_historial BEFORE UPDATE OR DELETE ON identidad.invitacion
    FOR EACH ROW EXECUTE FUNCTION identidad.proteger_invitacion();

-- La versión de sesión ya aumenta en V3. Retirar, además, los privilegios para que
-- una futura reactivación de cuenta no recupere asignaciones/membresías antiguas.
CREATE FUNCTION identidad.retirar_accesos_al_desactivar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE autorizacion.asignacion_rol SET status='inactive',revoked_at=now()
      WHERE user_id=NEW.id AND status='active' AND revoked_at IS NULL;
    UPDATE contexto.membresia_proyecto SET status='inactive' WHERE user_id=NEW.id AND status='active';
    UPDATE contexto.membresia_organizacion SET status='inactive' WHERE user_id=NEW.id AND status='active';
    UPDATE autorizacion.administrador_empresa SET status='inactive',revoked_at=now()
      WHERE user_id=NEW.id AND status='active' AND revoked_at IS NULL;
    UPDATE identidad.invitacion SET status='revoked',revoked_at=now()
      WHERE invited_by=NEW.id AND status='pending';
    RETURN NEW;
END;
$$;
CREATE TRIGGER usuario_retira_accesos AFTER UPDATE OF status ON identidad.usuario
    FOR EACH ROW WHEN(NEW.status='inactive' AND OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION identidad.retirar_accesos_al_desactivar();

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('0f0c904f-ac6c-5d10-a0ea-35a23aeb0546','tc.company.users.read','Consultar empleados de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'0f0c904f-ac6c-5d10-a0ea-35a23aeb0546' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('fb0fd746-c01a-501f-a211-64919f174067','tc.company.users.invite','Invitar una cuenta nueva a su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'fb0fd746-c01a-501f-a211-64919f174067' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('88796ec2-91f0-575c-8970-e2cf5652d6c8','tc.company.invitations.read','Consultar invitaciones de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'88796ec2-91f0-575c-8970-e2cf5652d6c8' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('dea5ed22-e175-5ffb-ab7a-d9aec1966f13','tc.company.invitations.revoke','Revocar invitaciones de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'dea5ed22-e175-5ffb-ab7a-d9aec1966f13' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('90d8660e-8fa4-5d6d-8495-ddbab01e8e91','tc.company.users.deactivate','Desactivar empleados sin privilegios administrativos','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'90d8660e-8fa4-5d6d-8495-ddbab01e8e91' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');
