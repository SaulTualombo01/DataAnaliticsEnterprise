-- Abortar conflictos previos; no borrar ni elegir roles/empresa automáticamente.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM contexto.membresia_organizacion WHERE status='active' GROUP BY user_id HAVING count(*)>1) THEN
   RAISE EXCEPTION 'Resolver pertenencias empresariales activas multiples antes de migrar' USING ERRCODE='23514';
 END IF;
 IF EXISTS(SELECT 1 FROM autorizacion.asignacion_rol a JOIN autorizacion.rol r ON r.id=a.role_id
   WHERE a.scope_type='PROJECT' AND a.status='active' AND a.revoked_at IS NULL AND r.code IN ('GESTOR_CATALOGO','ANALISTA_CALIDAD','RESPONSABLE_GOBIERNO','ANALISTA_NEGOCIO')
   GROUP BY a.user_id,a.project_id HAVING count(*)>1) THEN
   RAISE EXCEPTION 'Resolver multiples roles funcionales vigentes antes de migrar' USING ERRCODE='23514';
 END IF;
END $$;
CREATE UNIQUE INDEX membresia_una_empresa_activa_uniq ON contexto.membresia_organizacion(user_id) WHERE status='active';
CREATE UNIQUE INDEX asignacion_un_rol_funcional_uniq ON autorizacion.asignacion_rol(user_id,project_id)
 WHERE scope_type='PROJECT' AND status='active' AND revoked_at IS NULL AND role_id IN ('676cdc91-3263-5121-9986-d3f1fe6a474a','9c04ad7a-d8fd-5573-b7e3-4ea01238cdb1','5b2efe01-cec6-57fb-8c18-8f3f26a58df0','2ce69b41-a88c-5adb-b984-a7be914490e0');

ALTER TABLE contexto.proyecto DROP CONSTRAINT proyecto_status_check;
ALTER TABLE contexto.proyecto ADD CONSTRAINT proyecto_status_check CHECK(status IN ('active','inactive','archived'));
ALTER TABLE contexto.proyecto ADD COLUMN admin_revision bigint NOT NULL DEFAULT 0 CHECK(admin_revision>=0);
ALTER TABLE identidad.usuario ADD COLUMN admin_revision bigint NOT NULL DEFAULT 0 CHECK(admin_revision>=0);
CREATE FUNCTION contexto.revision_administrativa() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_SCHEMA='identidad' THEN
   IF NEW.display_name IS DISTINCT FROM OLD.display_name OR NEW.status IS DISTINCT FROM OLD.status OR NEW.email IS DISTINCT FROM OLD.email THEN
     NEW.admin_revision:=OLD.admin_revision+1; NEW.updated_at:=now(); ELSE NEW.admin_revision:=OLD.admin_revision; END IF;
 ELSE
   IF NEW.name IS DISTINCT FROM OLD.name OR NEW.status IS DISTINCT FROM OLD.status THEN
     NEW.admin_revision:=OLD.admin_revision+1; NEW.updated_at:=now(); ELSE NEW.admin_revision:=OLD.admin_revision; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER usuario_revision BEFORE UPDATE ON identidad.usuario FOR EACH ROW EXECUTE FUNCTION contexto.revision_administrativa();
CREATE TRIGGER proyecto_revision BEFORE UPDATE ON contexto.proyecto FOR EACH ROW EXECUTE FUNCTION contexto.revision_administrativa();

CREATE FUNCTION autorizacion.proteger_asignacion_retirada() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id<>OLD.id OR NEW.user_id<>OLD.user_id OR NEW.role_id<>OLD.role_id OR NEW.scope_type<>OLD.scope_type
    OR NEW.project_membership_id IS DISTINCT FROM OLD.project_membership_id OR NEW.project_id IS DISTINCT FROM OLD.project_id
    OR NEW.assigned_by<>OLD.assigned_by OR NEW.assigned_at<>OLD.assigned_at
    OR (OLD.status='inactive' AND NEW.status<>'inactive') OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
    RAISE EXCEPTION 'Una asignacion retirada no se reasigna ni reactiva' USING ERRCODE='23000';
 END IF;
 IF NEW.status='inactive' OR NEW.revoked_at IS NOT NULL THEN NEW.status:='inactive'; NEW.revoked_at:=COALESCE(NEW.revoked_at,now()); END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER asignacion_historial BEFORE UPDATE ON autorizacion.asignacion_rol FOR EACH ROW EXECUTE FUNCTION autorizacion.proteger_asignacion_retirada();

CREATE FUNCTION autorizacion.retirar_roles_de_membresia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE autorizacion.asignacion_rol SET status='inactive',revoked_at=now()
 WHERE project_membership_id=NEW.id AND status='active' AND revoked_at IS NULL;
 RETURN NEW;
END $$;
CREATE TRIGGER membresia_retira_roles AFTER UPDATE OF status ON contexto.membresia_proyecto
 FOR EACH ROW WHEN(NEW.status='inactive' AND OLD.status IS DISTINCT FROM NEW.status) EXECUTE FUNCTION autorizacion.retirar_roles_de_membresia();

CREATE FUNCTION contexto.retirar_participacion_empresarial() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE contexto.membresia_proyecto SET status='inactive' WHERE organization_membership_id=NEW.id AND status='active';
 UPDATE identidad.invitacion SET status='revoked',revoked_at=now() WHERE organization_id=NEW.organization_id AND invited_by=NEW.user_id AND status='pending';
 RETURN NEW;
END $$;
CREATE TRIGGER membresia_retira_proyectos AFTER UPDATE OF status ON contexto.membresia_organizacion
 FOR EACH ROW WHEN(NEW.status='inactive' AND OLD.status IS DISTINCT FROM NEW.status) EXECUTE FUNCTION contexto.retirar_participacion_empresarial();
CREATE FUNCTION autorizacion.retirar_invitaciones_de_delegacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE identidad.invitacion SET status='revoked',revoked_at=now() WHERE organization_id=NEW.organization_id AND invited_by=NEW.user_id AND status='pending';
 RETURN NEW;
END $$;
CREATE TRIGGER administrador_retira_invitaciones AFTER UPDATE OF status ON autorizacion.administrador_empresa
 FOR EACH ROW WHEN(NEW.status='inactive' AND OLD.status IS DISTINCT FROM NEW.status) EXECUTE FUNCTION autorizacion.retirar_invitaciones_de_delegacion();

-- Lecturas exclusivamente persistidas revisadas para M1. M2/M3/M4 quedan cerrados.
ALTER TABLE autorizacion.permiso ADD COLUMN archived_read boolean NOT NULL DEFAULT false;
UPDATE autorizacion.permiso SET archived_read=true WHERE operation_scope='PROJECT' AND code IN
 ('m1.sources.read','m1.discovery.read','m1.assets.read','m1.assets.search','m1.assets.fields.read','m1.dictionary.read','m1.metadata.history.read','m1.assets.diagram.read','m1.progress.read');
ALTER TABLE autorizacion.permiso ADD CONSTRAINT permiso_lectura_archivada_contextual CHECK(NOT archived_read OR operation_scope='PROJECT');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('930ff70a-4daf-5b2b-bf2b-48bcfa505f70','tc.company.users.update','Editar nombre administrativo de empleados ordinarios','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'930ff70a-4daf-5b2b-bf2b-48bcfa505f70' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('e3550d7e-6fdc-522a-a777-3f35f0fb369c','tc.company.memberships.read','Consultar membresias dentro de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'e3550d7e-6fdc-522a-a777-3f35f0fb369c' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('4e366920-71ef-5733-ae62-711f3267138f','tc.company.memberships.assign','Incorporar empleados a proyectos de su empresa','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'4e366920-71ef-5733-ae62-711f3267138f' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('a02f6c06-8d31-5d4b-85d5-fe21852d6465','tc.company.memberships.revoke','Retirar pertenencia empresarial o de proyecto','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'a02f6c06-8d31-5d4b-85d5-fe21852d6465' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('3737b24f-f8a2-5383-b540-0ab45f8202a5','tc.company.memberships.reactivate','Reactivar pertenencias sin restaurar roles','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'3737b24f-f8a2-5383-b540-0ab45f8202a5' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('942b84bd-9fa4-565f-924c-1507e99281e6','tc.company.roles.read','Consultar roles funcionales delegables e historial','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'942b84bd-9fa4-565f-924c-1507e99281e6' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('d1bb2bd0-8a9a-5c2d-acb2-8d79188a8f58','tc.company.roles.assign','Asignar o sustituir un rol funcional con alcances explicitos','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'d1bb2bd0-8a9a-5c2d-acb2-8d79188a8f58' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('4b22176c-6070-5f6c-a810-d18dcd7827aa','tc.company.roles.revoke','Retirar una asignacion funcional','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'4b22176c-6070-5f6c-a810-d18dcd7827aa' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('51a79443-c610-5445-bf41-b80dcbc4d481','tc.company.projects.update','Editar datos administrativos de proyectos','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'51a79443-c610-5445-bf41-b80dcbc4d481' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('40e134f1-630b-5ad1-b4e0-5690fcdf6513','tc.company.projects.archive','Archivar proyecto preservando lecturas autorizadas','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'40e134f1-630b-5ad1-b4e0-5690fcdf6513' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope) VALUES('86760bfb-4985-550a-88ad-ff1ab9d51ec7','tc.company.projects.reactivate','Reactivar un proyecto archivado','ORGANIZATION');
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'86760bfb-4985-550a-88ad-ff1ab9d51ec7' FROM autorizacion.rol WHERE code IN ('ADMIN_SISTEMA','ADMIN_EMPRESA');
