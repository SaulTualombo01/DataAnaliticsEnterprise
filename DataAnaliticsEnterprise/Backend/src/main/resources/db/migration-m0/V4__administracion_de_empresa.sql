-- Extensión incremental: no reescribe migraciones aplicadas ni crea cuentas.
ALTER TABLE autorizacion.permiso DROP CONSTRAINT permiso_operation_scope_check;
ALTER TABLE autorizacion.permiso ADD CONSTRAINT permiso_operation_scope_check
    CHECK(operation_scope IN ('SELF','PLATFORM','PROJECT','ORGANIZATION'));

INSERT INTO autorizacion.rol(id,code,name) VALUES('35fe2a78-6a38-5e54-b405-290284c8f2ee','ADMIN_EMPRESA','Administrador de empresa');
CREATE TABLE autorizacion.administrador_empresa (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    organization_id uuid NOT NULL REFERENCES contexto.organizacion(id) ON DELETE RESTRICT,
    organization_membership_id uuid NOT NULL,
    role_id uuid NOT NULL REFERENCES autorizacion.rol(id) ON DELETE RESTRICT CHECK(role_id='35fe2a78-6a38-5e54-b405-290284c8f2ee'),
    status text NOT NULL DEFAULT 'active' CHECK(status IN ('active','inactive')),
    assigned_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    FOREIGN KEY(organization_membership_id,user_id,organization_id)
        REFERENCES contexto.membresia_organizacion(id,user_id,organization_id) ON DELETE RESTRICT,
    CHECK((status='active' AND revoked_at IS NULL) OR (status='inactive' AND revoked_at IS NOT NULL))
);
CREATE UNIQUE INDEX administrador_empresa_usuario_vigente_uniq ON autorizacion.administrador_empresa(user_id)
    WHERE status='active' AND revoked_at IS NULL;
CREATE INDEX administrador_empresa_org_idx ON autorizacion.administrador_empresa(organization_id,assigned_at,id);
CREATE INDEX administrador_empresa_member_idx ON autorizacion.administrador_empresa(organization_membership_id,user_id,organization_id);
CREATE INDEX administrador_empresa_role_idx ON autorizacion.administrador_empresa(role_id);
CREATE INDEX administrador_empresa_actor_idx ON autorizacion.administrador_empresa(assigned_by);

CREATE FUNCTION autorizacion.proteger_administrador_empresa() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.revoked_at IS NOT NULL OR NEW.user_id<>OLD.user_id OR NEW.organization_id<>OLD.organization_id
       OR NEW.organization_membership_id<>OLD.organization_membership_id OR NEW.role_id<>OLD.role_id
       OR NEW.assigned_by<>OLD.assigned_by OR NEW.assigned_at<>OLD.assigned_at OR NEW.id<>OLD.id THEN
        RAISE EXCEPTION 'La asignacion no puede reasignarse ni reactivarse' USING ERRCODE='23000';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER administrador_empresa_inmutable BEFORE UPDATE ON autorizacion.administrador_empresa
    FOR EACH ROW EXECUTE FUNCTION autorizacion.proteger_administrador_empresa();

CREATE FUNCTION autorizacion.revocar_administracion_de_membresia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE autorizacion.administrador_empresa SET status='inactive',revoked_at=now()
    WHERE organization_membership_id=NEW.id AND status='active' AND revoked_at IS NULL;
    RETURN NEW;
END;
$$;
CREATE TRIGGER membresia_retira_administracion AFTER UPDATE OF status ON contexto.membresia_organizacion
    FOR EACH ROW WHEN(NEW.status='inactive' AND OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION autorizacion.revocar_administracion_de_membresia();

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('e67b3a0f-17dc-5378-8bc1-12482f9c3fea','tc.company_administrators.assign','Designar administrador de una empresa','PLATFORM');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('b20bf06b-bbc5-5572-b173-c21fbd3af58b','e67b3a0f-17dc-5378-8bc1-12482f9c3fea');
INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('bebbe0b8-ff10-5ef5-9fdc-e5bb0946cca4','tc.company_administrators.read','Consultar designaciones de administradores de empresa','PLATFORM');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('b20bf06b-bbc5-5572-b173-c21fbd3af58b','bebbe0b8-ff10-5ef5-9fdc-e5bb0946cca4');
INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('26248bc9-453f-588d-82a3-115993b6ce11','tc.company_administrators.revoke','Revocar una designacion de administrador de empresa','PLATFORM');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('b20bf06b-bbc5-5572-b173-c21fbd3af58b','26248bc9-453f-588d-82a3-115993b6ce11');
INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('13cc29b1-3d2d-567a-ada2-b4b7bed2c978','tc.company.organizations.read','Consultar contexto administrativo de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('b20bf06b-bbc5-5572-b173-c21fbd3af58b','13cc29b1-3d2d-567a-ada2-b4b7bed2c978');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('35fe2a78-6a38-5e54-b405-290284c8f2ee','13cc29b1-3d2d-567a-ada2-b4b7bed2c978');
INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('f6a597f4-c676-59d2-8042-a88a08904c22','tc.company.projects.read','Consultar proyectos administrativos de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('b20bf06b-bbc5-5572-b173-c21fbd3af58b','f6a597f4-c676-59d2-8042-a88a08904c22');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('35fe2a78-6a38-5e54-b405-290284c8f2ee','f6a597f4-c676-59d2-8042-a88a08904c22');
INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('816af2d7-06e7-5664-9690-6920e77b7025','tc.company.projects.create','Crear proyectos de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('b20bf06b-bbc5-5572-b173-c21fbd3af58b','816af2d7-06e7-5664-9690-6920e77b7025');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES('35fe2a78-6a38-5e54-b405-290284c8f2ee','816af2d7-06e7-5664-9690-6920e77b7025');
