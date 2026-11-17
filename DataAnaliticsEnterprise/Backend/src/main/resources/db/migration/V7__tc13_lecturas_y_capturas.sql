-- TC13: persistencia propuesta para recibos de lectura y capturas de contenido.
-- Sigue docs/contratos/tc13 (urn:tic:tc13:1.0.0): ReadReceipt -> lectura.recibo, Capture -> lectura.captura.
-- Propuesta para revisión de los responsables de TC13; no da de alta permisos ni define cifrado o retención.

CREATE SCHEMA lectura;

CREATE TABLE lectura.recibo (
    id uuid PRIMARY KEY,                                   -- read_id
    execution_id uuid NOT NULL UNIQUE,
    origin_request_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    project_id uuid NOT NULL,
    -- Referencias lógicas al catálogo (M1). Sin FK: sus tablas llegan en V2, de otro módulo.
    source_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    metadata_version_id uuid NOT NULL,
    -- El contrato exige además elementos únicos; esa regla la valida la aplicación.
    field_ids uuid[] NOT NULL CHECK (cardinality(field_ids) BETWEEN 1 AND 100),
    -- El actor procede de la sesión del servidor; el recibo HTTP no lo expone.
    requested_by uuid NOT NULL REFERENCES identidad.usuario(id) ON DELETE RESTRICT,
    observed_at timestamptz NOT NULL,
    mode text NOT NULL CHECK (mode IN ('live','capture')),
    execution_state text NOT NULL CHECK (execution_state IN ('COMPLETED','FAILED')),
    scope_complete boolean NOT NULL,
    partial_reason text CHECK (partial_reason IN ('ROW_LIMIT','BYTE_LIMIT','TIMEOUT','SOURCE_INCOMPLETE')),
    returned_rows integer NOT NULL CHECK (returned_rows >= 0),
    expected_rows bigint CHECK (expected_rows >= 0),
    serialized_bytes bigint NOT NULL CHECK (serialized_bytes >= 0),
    coverage_ratio numeric(6,5) CHECK (coverage_ratio BETWEEN 0 AND 1),
    representation text NOT NULL DEFAULT 'raw_cells_v1' CHECK (representation = 'raw_cells_v1'),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (project_id, organization_id) REFERENCES contexto.proyecto(id, organization_id) ON DELETE RESTRICT,
    -- Destinos de las FK compuestas de lectura.captura.
    UNIQUE (id, project_id, mode, execution_state),
    -- COMPLETED implica alcance completo y sin motivo; FAILED, alcance incompleto con motivo.
    CHECK ((execution_state = 'COMPLETED') = scope_complete),
    CHECK ((execution_state = 'COMPLETED') = (partial_reason IS NULL)),
    -- La cobertura solo existe con un total esperado confiable y mayor que cero.
    CHECK (coverage_ratio IS NULL OR (expected_rows IS NOT NULL AND expected_rows > 0))
);
CREATE INDEX recibo_project_time_idx ON lectura.recibo(project_id, observed_at DESC);
-- Consulta "a una fecha": última lectura completa de un activo con observed_at <= :instante.
CREATE INDEX recibo_asset_observado_idx ON lectura.recibo(project_id, asset_id, observed_at DESC)
    WHERE execution_state = 'COMPLETED';
CREATE INDEX recibo_proyecto_fk_idx ON lectura.recibo(project_id, organization_id);
CREATE INDEX recibo_actor_idx ON lectura.recibo(requested_by);

CREATE TABLE lectura.captura (
    id uuid PRIMARY KEY,                                   -- capture_id
    read_id uuid NOT NULL UNIQUE,
    project_id uuid NOT NULL,
    -- Constantes que, vía FK compuesta, impiden capturas de lecturas live o FAILED.
    read_mode text NOT NULL DEFAULT 'capture' CHECK (read_mode = 'capture'),
    read_state text NOT NULL DEFAULT 'COMPLETED' CHECK (read_state = 'COMPLETED'),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    content_hash text NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    content_hash_encoding text NOT NULL DEFAULT 'json_utf8_compact_v1'
        CHECK (content_hash_encoding = 'json_utf8_compact_v1'),
    representation text NOT NULL DEFAULT 'raw_cells_v1' CHECK (representation = 'raw_cells_v1'),
    row_count integer NOT NULL CHECK (row_count >= 0),
    FOREIGN KEY (read_id, project_id, read_mode, read_state)
        REFERENCES lectura.recibo(id, project_id, mode, execution_state) ON DELETE RESTRICT,
    UNIQUE (id, project_id),
    CHECK (expires_at > created_at)
);
-- Barrido de vencimientos y listado de capturas vigentes de un proyecto.
CREATE INDEX captura_project_vence_idx ON lectura.captura(project_id, expires_at);

-- Contenido de una captura, en bloques ordenados de filas raw_cells_v1.
-- Al vencer la captura se borran sus bloques; la fila de lectura.captura queda como referencia.
CREATE TABLE lectura.captura_bloque (
    capture_id uuid NOT NULL,
    project_id uuid NOT NULL,
    chunk_index integer NOT NULL CHECK (chunk_index >= 0),
    row_count integer NOT NULL CHECK (row_count >= 1),
    records jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (capture_id, chunk_index),
    FOREIGN KEY (capture_id, project_id) REFERENCES lectura.captura(id, project_id) ON DELETE RESTRICT,
    CHECK (CASE WHEN jsonb_typeof(records) = 'array' THEN jsonb_array_length(records) = row_count ELSE false END)
);

-- Recibos, capturas y contenido no se reescriben. Borrar sigue permitido para la retención.
CREATE TRIGGER recibo_inmutable BEFORE UPDATE ON lectura.recibo
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_cambio_historico();
CREATE TRIGGER captura_inmutable BEFORE UPDATE ON lectura.captura
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_cambio_historico();
CREATE TRIGGER captura_bloque_inmutable BEFORE UPDATE ON lectura.captura_bloque
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_cambio_historico();
