CREATE TABLE catalogo.fuente (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES contexto.proyecto(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK(length(btrim(name)) BETWEEN 1 AND 200),
    type text NOT NULL CHECK(type IN ('postgresql','mysql','csv','json','rest')),
    status text NOT NULL CHECK(status IN ('enabled','disabled','retired')),
    connection_configuration jsonb NOT NULL CHECK(jsonb_typeof(connection_configuration)='object'),
    credential_ref text CHECK(length(credential_ref) BETWEEN 1 AND 128),
    created_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    last_discovery_at timestamptz,
    revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),
    CHECK(NOT (connection_configuration ?| ARRAY['password','token','secret','authorization'])),
    UNIQUE(id,project_id)
);
CREATE INDEX fuente_project_idx ON catalogo.fuente(project_id);
CREATE INDEX fuente_actor_idx ON catalogo.fuente(created_by);
CREATE TABLE catalogo.activo (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    source_id uuid NOT NULL,
    name text NOT NULL CHECK(length(btrim(name)) BETWEEN 1 AND 200),
    kind text NOT NULL CHECK(kind IN ('table','csv_dataset','json_collection','api_collection')),
    origin_namespace text,
    origin_object_path text NOT NULL CHECK(length(origin_object_path) BETWEEN 1 AND 1000),
    status text NOT NULL CHECK(status IN ('active','missing','retired')),
    structure_origin text NOT NULL CHECK(structure_origin IN ('declared','inferred')),
    business_description text CHECK(length(business_description)<=2000),
    synonyms text[] NOT NULL DEFAULT '{}',
    current_metadata_version_id uuid NOT NULL,
    version_resource_type text NOT NULL DEFAULT 'catalogo.activo' CHECK(version_resource_type='catalogo.activo'),
    last_discovery_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),
    FOREIGN KEY(source_id,project_id) REFERENCES catalogo.fuente(id,project_id) ON DELETE RESTRICT,
    FOREIGN KEY(current_metadata_version_id,project_id,version_resource_type,id)
        REFERENCES versionado.version_recurso(id,project_id,resource_type,resource_id)
        ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    UNIQUE(id,project_id),
    UNIQUE NULLS NOT DISTINCT(source_id,origin_namespace,origin_object_path)
);
CREATE INDEX activo_source_project_idx ON catalogo.activo(source_id,project_id);
CREATE INDEX activo_project_idx ON catalogo.activo(project_id,id);
CREATE INDEX activo_version_idx ON catalogo.activo(current_metadata_version_id,project_id,version_resource_type,id);
CREATE TABLE catalogo.campo (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    name text NOT NULL CHECK(length(btrim(name)) BETWEEN 1 AND 200),
    path text NOT NULL CHECK(length(path) BETWEEN 1 AND 1000),
    position integer NOT NULL CHECK(position>0),
    native_type text,
    logical_type text NOT NULL CHECK(logical_type IN ('string','integer','number','boolean','date','datetime','object','array','mixed','unknown')),
    nullable boolean,
    business_description text CHECK(length(business_description)<=2000),
    synonyms text[] NOT NULL DEFAULT '{}',
    status text NOT NULL CHECK(status IN ('active','missing')),
    FOREIGN KEY(asset_id,project_id) REFERENCES catalogo.activo(id,project_id) ON DELETE RESTRICT,
    UNIQUE(id,asset_id,project_id)
);
CREATE UNIQUE INDEX campo_path_activo_uniq ON catalogo.campo(asset_id,path) WHERE status='active';
CREATE UNIQUE INDEX campo_pos_activo_uniq ON catalogo.campo(asset_id,position) WHERE status='active';
CREATE INDEX campo_project_idx ON catalogo.campo(project_id,asset_id);
CREATE TABLE catalogo.restriccion (
    id uuid PRIMARY KEY,
    asset_id uuid NOT NULL,
    project_id uuid NOT NULL,
    name text,
    kind text NOT NULL CHECK(kind IN ('PK','UNIQUE','FK')),
    origin text NOT NULL DEFAULT 'declared' CHECK(origin='declared'),
    target_asset_id uuid,
    target_locator text,
    resolution_status text NOT NULL CHECK(resolution_status IN ('resolved','unresolved','not_accessible')),
    FOREIGN KEY(asset_id,project_id) REFERENCES catalogo.activo(id,project_id) ON DELETE RESTRICT,
    FOREIGN KEY(target_asset_id,project_id) REFERENCES catalogo.activo(id,project_id) ON DELETE RESTRICT,
    CHECK ((kind<>'FK' AND target_asset_id IS NULL AND target_locator IS NULL AND resolution_status='resolved')
       OR (kind='FK' AND ((resolution_status='resolved' AND target_asset_id IS NOT NULL)
                       OR (resolution_status IN ('unresolved','not_accessible') AND target_locator IS NOT NULL)))),
    UNIQUE(id,asset_id,project_id)
);
CREATE INDEX restriccion_asset_idx ON catalogo.restriccion(asset_id,project_id);
CREATE INDEX restriccion_target_idx ON catalogo.restriccion(target_asset_id,project_id);
CREATE TABLE catalogo.miembro_restriccion (
    constraint_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    project_id uuid NOT NULL,
    position integer NOT NULL CHECK(position>0),
    source_field_id uuid NOT NULL,
    target_field_name text,
    PRIMARY KEY(constraint_id,position),
    UNIQUE(constraint_id,source_field_id),
    FOREIGN KEY(constraint_id,asset_id,project_id) REFERENCES catalogo.restriccion(id,asset_id,project_id) ON DELETE RESTRICT,
    FOREIGN KEY(source_field_id,asset_id,project_id) REFERENCES catalogo.campo(id,asset_id,project_id) ON DELETE RESTRICT
);
CREATE INDEX miembro_campo_idx ON catalogo.miembro_restriccion(source_field_id,asset_id,project_id);
CREATE TABLE catalogo.etiqueta (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES contexto.proyecto(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK(length(btrim(name)) BETWEEN 1 AND 100),
    normalized_name text GENERATED ALWAYS AS(lower(btrim(name))) STORED,
    description text,
    status text NOT NULL DEFAULT 'active' CHECK(status IN ('active','inactive')),
    UNIQUE(project_id,normalized_name),
    UNIQUE(id,project_id)
);
CREATE TABLE catalogo.activo_etiqueta (
    asset_id uuid NOT NULL,
    tag_id uuid NOT NULL,
    project_id uuid NOT NULL,
    assigned_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(asset_id,tag_id),
    FOREIGN KEY(asset_id,project_id) REFERENCES catalogo.activo(id,project_id) ON DELETE RESTRICT,
    FOREIGN KEY(tag_id,project_id) REFERENCES catalogo.etiqueta(id,project_id) ON DELETE RESTRICT
);
CREATE INDEX activo_etiqueta_tag_idx ON catalogo.activo_etiqueta(tag_id,project_id);
CREATE INDEX activo_etiqueta_actor_idx ON catalogo.activo_etiqueta(assigned_by);
CREATE TABLE catalogo.ejecucion_descubrimiento (
    id uuid PRIMARY KEY,
    source_id uuid NOT NULL,
    project_id uuid NOT NULL,
    requested_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    request_id uuid NOT NULL,
    status text NOT NULL CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED')),
    requested_scope jsonb NOT NULL CHECK(jsonb_typeof(requested_scope)='object'),
    started_at timestamptz,
    finished_at timestamptz,
    scope_complete boolean NOT NULL DEFAULT false,
    expected_count bigint CHECK(expected_count>=0),
    processed_count bigint NOT NULL DEFAULT 0 CHECK(processed_count>=0),
    safe_error_code text,
    safe_error_message text,
    FOREIGN KEY(source_id,project_id) REFERENCES catalogo.fuente(id,project_id) ON DELETE RESTRICT,
    CHECK(status<>'COMPLETED' OR (scope_complete AND finished_at IS NOT NULL)),
    CHECK(finished_at IS NULL OR (started_at IS NOT NULL AND finished_at>=started_at)),
    UNIQUE(id,project_id)
);
CREATE INDEX ejecucion_source_idx ON catalogo.ejecucion_descubrimiento(source_id,project_id);
CREATE INDEX ejecucion_actor_idx ON catalogo.ejecucion_descubrimiento(requested_by);
CREATE TABLE catalogo.resultado_descubrimiento_activo (
    id uuid PRIMARY KEY,
    discovery_execution_id uuid NOT NULL,
    project_id uuid NOT NULL,
    asset_id uuid,
    origin_locator text NOT NULL,
    outcome text NOT NULL CHECK(outcome IN ('CREATED','CHANGED','UNCHANGED','NOT_ACCESSIBLE','ERROR')),
    previous_metadata_version_id uuid,
    result_metadata_version_id uuid,
    version_resource_type text NOT NULL DEFAULT 'catalogo.activo' CHECK(version_resource_type='catalogo.activo'),
    safe_issue text,
    FOREIGN KEY(discovery_execution_id,project_id) REFERENCES catalogo.ejecucion_descubrimiento(id,project_id) ON DELETE RESTRICT,
    FOREIGN KEY(asset_id,project_id) REFERENCES catalogo.activo(id,project_id) ON DELETE RESTRICT,
    FOREIGN KEY(previous_metadata_version_id,project_id,version_resource_type,asset_id)
        REFERENCES versionado.version_recurso(id,project_id,resource_type,resource_id) ON DELETE RESTRICT,
    FOREIGN KEY(result_metadata_version_id,project_id,version_resource_type,asset_id)
        REFERENCES versionado.version_recurso(id,project_id,resource_type,resource_id) ON DELETE RESTRICT,
    CHECK((previous_metadata_version_id IS NULL AND result_metadata_version_id IS NULL) OR asset_id IS NOT NULL)
);
CREATE INDEX resultado_ejecucion_idx ON catalogo.resultado_descubrimiento_activo(discovery_execution_id,project_id);
CREATE INDEX resultado_asset_idx ON catalogo.resultado_descubrimiento_activo(asset_id,project_id);
CREATE INDEX resultado_previous_idx ON catalogo.resultado_descubrimiento_activo(previous_metadata_version_id);
CREATE INDEX resultado_version_idx ON catalogo.resultado_descubrimiento_activo(result_metadata_version_id);

-- Integridad de recursos M1 sobre los alcances del tronco común.
ALTER TABLE autorizacion.alcance_asignacion
    ADD CONSTRAINT alcance_fuente_m1_fk FOREIGN KEY(source_id,project_id)
        REFERENCES catalogo.fuente(id,project_id) ON DELETE RESTRICT,
    ADD CONSTRAINT alcance_activo_m1_fk FOREIGN KEY(asset_id,project_id)
        REFERENCES catalogo.activo(id,project_id) ON DELETE RESTRICT;
