CREATE SCHEMA identidad;
CREATE SCHEMA contexto;
CREATE SCHEMA autorizacion;
CREATE SCHEMA auditoria;
CREATE SCHEMA versionado;
CREATE SCHEMA catalogo;

CREATE TABLE identidad.usuario (
    id uuid PRIMARY KEY,
    email text NOT NULL CHECK (length(btrim(email)) BETWEEN 3 AND 254),
    normalized_email text GENERATED ALWAYS AS (lower(btrim(email))) STORED UNIQUE,
    display_name text NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 200),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE contexto.organizacion (
    id uuid PRIMARY KEY,
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE contexto.proyecto (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES contexto.organizacion(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, organization_id),
    UNIQUE (organization_id, name)
);
CREATE TABLE contexto.membresia_organizacion (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    organization_id uuid NOT NULL REFERENCES contexto.organizacion(id) ON DELETE RESTRICT,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
    assigned_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, organization_id),
    UNIQUE (id, user_id, organization_id)
);
CREATE INDEX membresia_organizacion_org_idx ON contexto.membresia_organizacion(organization_id);
CREATE INDEX membresia_organizacion_actor_idx ON contexto.membresia_organizacion(assigned_by);
CREATE TABLE contexto.membresia_proyecto (
    id uuid PRIMARY KEY,
    organization_membership_id uuid NOT NULL,
    user_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    project_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
    assigned_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_membership_id, user_id, organization_id)
        REFERENCES contexto.membresia_organizacion(id,user_id,organization_id) ON DELETE RESTRICT,
    FOREIGN KEY (project_id, organization_id) REFERENCES contexto.proyecto(id,organization_id) ON DELETE RESTRICT,
    UNIQUE (user_id,project_id),
    UNIQUE (id,user_id,project_id)
);
CREATE INDEX membresia_proyecto_org_member_idx ON contexto.membresia_proyecto(organization_membership_id,user_id,organization_id);
CREATE INDEX membresia_proyecto_project_idx ON contexto.membresia_proyecto(project_id,organization_id);
CREATE INDEX membresia_proyecto_actor_idx ON contexto.membresia_proyecto(assigned_by);

CREATE TABLE autorizacion.rol (
    id uuid PRIMARY KEY,
    code text NOT NULL UNIQUE,
    name text NOT NULL,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive'))
);
CREATE TABLE autorizacion.permiso (
    id uuid PRIMARY KEY,
    code text NOT NULL UNIQUE,
    description text NOT NULL,
    operation_scope text NOT NULL CHECK (operation_scope IN ('SELF','PLATFORM','PROJECT')),
    available boolean NOT NULL DEFAULT true
);
CREATE TABLE autorizacion.rol_permiso (
    role_id uuid NOT NULL REFERENCES autorizacion.rol(id) ON DELETE RESTRICT,
    permission_id uuid NOT NULL REFERENCES autorizacion.permiso(id) ON DELETE RESTRICT,
    assigned_by uuid REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(role_id,permission_id)
);
CREATE INDEX rol_permiso_permiso_idx ON autorizacion.rol_permiso(permission_id);
CREATE INDEX rol_permiso_actor_idx ON autorizacion.rol_permiso(assigned_by);
CREATE TABLE autorizacion.asignacion_rol (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    role_id uuid NOT NULL REFERENCES autorizacion.rol(id) ON DELETE RESTRICT,
    scope_type text NOT NULL CHECK(scope_type IN ('PLATFORM','PROJECT')),
    project_membership_id uuid,
    project_id uuid,
    status text NOT NULL DEFAULT 'active' CHECK(status IN ('active','inactive')),
    assigned_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    CHECK ((scope_type='PLATFORM' AND project_membership_id IS NULL AND project_id IS NULL)
        OR (scope_type='PROJECT' AND project_membership_id IS NOT NULL AND project_id IS NOT NULL)),
    FOREIGN KEY(project_membership_id,user_id,project_id)
        REFERENCES contexto.membresia_proyecto(id,user_id,project_id) ON DELETE RESTRICT,
    UNIQUE(id,project_id)
);
CREATE UNIQUE INDEX asignacion_rol_activa_uniq ON autorizacion.asignacion_rol(user_id,role_id,scope_type,project_id)
    NULLS NOT DISTINCT WHERE status='active';
CREATE INDEX asignacion_rol_member_idx ON autorizacion.asignacion_rol(project_membership_id,user_id,project_id);
CREATE INDEX asignacion_rol_role_idx ON autorizacion.asignacion_rol(role_id);
CREATE INDEX asignacion_rol_actor_idx ON autorizacion.asignacion_rol(assigned_by);

CREATE TABLE auditoria.evento (
    id uuid PRIMARY KEY,
    actor_type text NOT NULL CHECK(actor_type IN ('USER','SERVICE','ANONYMOUS')),
    actor_id uuid,
    project_id uuid REFERENCES contexto.proyecto(id) ON DELETE RESTRICT,
    resource_type text NOT NULL,
    resource_id uuid,
    action text NOT NULL,
    occurred_at timestamptz NOT NULL,
    outcome text NOT NULL CHECK(outcome IN ('SUCCESS','FAILURE','DENIED')),
    request_id uuid,
    safe_details jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(safe_details)='object'),
    CHECK ((actor_type='ANONYMOUS' AND actor_id IS NULL) OR (actor_type<>'ANONYMOUS' AND actor_id IS NOT NULL))
);
CREATE INDEX evento_project_time_idx ON auditoria.evento(project_id,occurred_at DESC);
CREATE INDEX evento_request_idx ON auditoria.evento(request_id);
CREATE TABLE versionado.version_recurso (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES contexto.proyecto(id) ON DELETE RESTRICT,
    resource_type text NOT NULL,
    resource_id uuid NOT NULL,
    number bigint NOT NULL CHECK(number>0),
    previous_version_id uuid,
    created_at timestamptz NOT NULL,
    actor_type text NOT NULL CHECK(actor_type IN ('USER','SERVICE')),
    actor_id uuid NOT NULL,
    reason text NOT NULL,
    representation_version integer NOT NULL CHECK(representation_version>0),
    state_payload jsonb NOT NULL CHECK(jsonb_typeof(state_payload)='object'),
    content_hash text NOT NULL CHECK(content_hash ~ '^[0-9a-f]{64}$'),
    audit_event_id uuid REFERENCES auditoria.evento(id) ON DELETE RESTRICT,
    UNIQUE(project_id,resource_type,resource_id,number),
    UNIQUE(id,project_id,resource_type,resource_id),
    FOREIGN KEY(previous_version_id,project_id,resource_type,resource_id)
        REFERENCES versionado.version_recurso(id,project_id,resource_type,resource_id) ON DELETE RESTRICT,
    CHECK ((number=1 AND previous_version_id IS NULL) OR (number>1 AND previous_version_id IS NOT NULL))
);
CREATE INDEX version_recurso_prev_idx ON versionado.version_recurso(previous_version_id,project_id,resource_type,resource_id);
CREATE INDEX version_recurso_event_idx ON versionado.version_recurso(audit_event_id);

-- El historial no se reescribe por las operaciones ordinarias de la aplicación.
CREATE FUNCTION auditoria.rechazar_cambio_historico() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Los registros historicos son inmutables' USING ERRCODE='23000';
END;
$$;
CREATE TRIGGER evento_inmutable BEFORE UPDATE OR DELETE ON auditoria.evento
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_cambio_historico();
CREATE TRIGGER version_inmutable BEFORE UPDATE OR DELETE ON versionado.version_recurso
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_cambio_historico();
