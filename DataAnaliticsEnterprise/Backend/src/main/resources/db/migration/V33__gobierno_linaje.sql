-- M3 Gobierno, fase 4: relaciones de linaje conocidas entre activos del catálogo.
-- Dirección: origin_asset_id (de donde salen los datos) → target_asset_id (a donde llegan).
-- project_id, activos y actores son UUID sin FK: esas tablas no existen en esta rama.

CREATE TABLE gobierno.relacion_linaje (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    origin_asset_id uuid NOT NULL,
    target_asset_id uuid NOT NULL,
    relation_type text NOT NULL CHECK (relation_type IN ('COPIA','TRANSFORMA','AGREGA','ALIMENTA')),
    description text NOT NULL DEFAULT '' CHECK (length(description) <= 1000),
    status text NOT NULL CHECK (status IN ('ACTIVE','RETIRED')),
    current_version_id uuid NOT NULL REFERENCES gobierno.version_recurso(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    retired_at timestamptz,
    retired_by uuid,
    CHECK (origin_asset_id <> target_asset_id),
    CHECK ((status = 'ACTIVE' AND retired_at IS NULL AND retired_by IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_by IS NOT NULL))
);
-- Una sola relación vigente por origen, destino y tipo.
CREATE UNIQUE INDEX relacion_linaje_vigente_uniq ON gobierno.relacion_linaje (project_id, origin_asset_id, target_asset_id, relation_type)
    WHERE status = 'ACTIVE';
-- Recorridos del grafo hacia destinos y hacia orígenes.
CREATE INDEX relacion_linaje_origen_idx ON gobierno.relacion_linaje (project_id, origin_asset_id) WHERE status = 'ACTIVE';
CREATE INDEX relacion_linaje_destino_idx ON gobierno.relacion_linaje (project_id, target_asset_id) WHERE status = 'ACTIVE';
CREATE INDEX relacion_linaje_version_idx ON gobierno.relacion_linaje (current_version_id);
