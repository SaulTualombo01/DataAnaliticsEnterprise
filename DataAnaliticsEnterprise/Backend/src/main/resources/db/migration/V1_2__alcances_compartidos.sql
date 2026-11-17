-- El módulo propietario del recurso añadirá sus referencias de integridad.
CREATE TABLE autorizacion.alcance_asignacion (
    id uuid PRIMARY KEY,
    role_assignment_id uuid NOT NULL,
    project_id uuid NOT NULL,
    resource_scope text NOT NULL CHECK(resource_scope IN ('PROJECT','SOURCE','ASSET')),
    source_id uuid,
    asset_id uuid,
    FOREIGN KEY(role_assignment_id,project_id) REFERENCES autorizacion.asignacion_rol(id,project_id) ON DELETE RESTRICT,
    CHECK ((resource_scope='PROJECT' AND source_id IS NULL AND asset_id IS NULL)
        OR (resource_scope='SOURCE' AND source_id IS NOT NULL AND asset_id IS NULL)
        OR (resource_scope='ASSET' AND asset_id IS NOT NULL AND source_id IS NULL)),
    UNIQUE NULLS NOT DISTINCT(role_assignment_id,resource_scope,source_id,asset_id)
);
CREATE INDEX alcance_role_project_idx ON autorizacion.alcance_asignacion(role_assignment_id,project_id);
CREATE INDEX alcance_source_idx ON autorizacion.alcance_asignacion(source_id,project_id);
CREATE INDEX alcance_asset_idx ON autorizacion.alcance_asignacion(asset_id,project_id);
