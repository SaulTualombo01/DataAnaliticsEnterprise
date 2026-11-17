-- M3 Gobierno: auditoría y versiones mientras la rama trabaja sin el tronco común.
-- Mismas columnas que auditoria.evento y versionado.version_recurso del tronco (V1),
-- más organization_id en el evento. Al integrar, estos datos pasan a esas tablas y
-- se añaden las claves foráneas hacia contexto, identidad y catálogo.
-- Proyecto, organización, actor y recurso se guardan como UUID sin FK: esas tablas
-- no existen en esta rama.

CREATE SCHEMA gobierno;

CREATE TABLE gobierno.evento_auditoria (
    id uuid PRIMARY KEY,
    actor_type text NOT NULL CHECK (actor_type IN ('USER','SERVICE','ANONYMOUS')),
    actor_id uuid,
    organization_id uuid,
    project_id uuid,
    resource_type text NOT NULL CHECK (resource_type ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$'),
    resource_id uuid,
    action text NOT NULL CHECK (action ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'),
    occurred_at timestamptz NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('SUCCESS','FAILURE','DENIED')),
    request_id uuid,
    safe_details jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(safe_details) = 'object'),
    CHECK ((actor_type = 'ANONYMOUS' AND actor_id IS NULL) OR (actor_type <> 'ANONYMOUS' AND actor_id IS NOT NULL))
);
CREATE INDEX evento_auditoria_project_time_idx ON gobierno.evento_auditoria (project_id, occurred_at DESC, id DESC);
CREATE INDEX evento_auditoria_org_time_idx ON gobierno.evento_auditoria (organization_id, occurred_at DESC, id DESC);
CREATE INDEX evento_auditoria_resource_idx ON gobierno.evento_auditoria (resource_type, resource_id);
CREATE INDEX evento_auditoria_request_idx ON gobierno.evento_auditoria (request_id);

CREATE TABLE gobierno.version_recurso (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    resource_type text NOT NULL CHECK (resource_type ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$'),
    resource_id uuid NOT NULL,
    number bigint NOT NULL CHECK (number > 0),
    previous_version_id uuid,
    created_at timestamptz NOT NULL,
    actor_type text NOT NULL CHECK (actor_type IN ('USER','SERVICE')),
    actor_id uuid NOT NULL,
    reason text NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    representation_version integer NOT NULL CHECK (representation_version > 0),
    state_payload jsonb NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    content_hash text NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    audit_event_id uuid REFERENCES gobierno.evento_auditoria(id) ON DELETE RESTRICT,
    UNIQUE (project_id, resource_type, resource_id, number),
    UNIQUE (id, project_id, resource_type, resource_id),
    FOREIGN KEY (previous_version_id, project_id, resource_type, resource_id)
        REFERENCES gobierno.version_recurso(id, project_id, resource_type, resource_id) ON DELETE RESTRICT,
    CHECK ((number = 1 AND previous_version_id IS NULL) OR (number > 1 AND previous_version_id IS NOT NULL))
);
CREATE INDEX version_recurso_prev_idx ON gobierno.version_recurso (previous_version_id, project_id, resource_type, resource_id);
CREATE INDEX version_recurso_event_idx ON gobierno.version_recurso (audit_event_id);

-- El historial no se reescribe ni se borra por las operaciones ordinarias.
CREATE FUNCTION gobierno.rechazar_cambio_historico() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Los registros historicos son inmutables' USING ERRCODE = '23000';
END;
$$;
CREATE TRIGGER evento_auditoria_inmutable BEFORE UPDATE OR DELETE ON gobierno.evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION gobierno.rechazar_cambio_historico();
CREATE TRIGGER version_recurso_inmutable BEFORE UPDATE OR DELETE ON gobierno.version_recurso
    FOR EACH ROW EXECUTE FUNCTION gobierno.rechazar_cambio_historico();
