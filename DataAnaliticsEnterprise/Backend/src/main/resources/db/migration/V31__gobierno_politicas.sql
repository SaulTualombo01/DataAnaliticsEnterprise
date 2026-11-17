-- M3 Gobierno, fase 2: políticas de gobierno y su asociación con activos del catálogo.
-- project_id, owner_id, created_by y asset_id son UUID sin FK: proyecto, usuario y activo
-- pertenecen al tronco común y a M1, que esta rama todavía no integra.

CREATE TABLE gobierno.politica (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    description text NOT NULL DEFAULT '' CHECK (length(description) <= 2000),
    category text NOT NULL CHECK (category IN ('CLASIFICACION','PRIVACIDAD','RETENCION','CALIDAD','ACCESO','OTRA')),
    owner_id uuid NOT NULL,
    status text NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','RETIRED')),
    revision bigint NOT NULL CHECK (revision >= 0),
    current_version_id uuid NOT NULL REFERENCES gobierno.version_recurso(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (id, project_id)
);
-- Nombre único por proyecto entre las políticas no retiradas, sin distinguir mayúsculas.
CREATE UNIQUE INDEX politica_nombre_vigente_uniq ON gobierno.politica (project_id, lower(btrim(name)))
    WHERE status <> 'RETIRED';
CREATE INDEX politica_project_idx ON gobierno.politica (project_id, created_at, id);
CREATE INDEX politica_version_idx ON gobierno.politica (current_version_id);

-- Asociación vigente o retirada; el historial completo queda en versiones y auditoría.
CREATE TABLE gobierno.politica_activo (
    policy_id uuid NOT NULL,
    project_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    active boolean NOT NULL,
    added_at timestamptz NOT NULL,
    added_by uuid NOT NULL,
    removed_at timestamptz,
    removed_by uuid,
    PRIMARY KEY (policy_id, asset_id),
    FOREIGN KEY (policy_id, project_id) REFERENCES gobierno.politica(id, project_id) ON DELETE RESTRICT,
    CHECK ((active AND removed_at IS NULL AND removed_by IS NULL)
        OR (NOT active AND removed_at IS NOT NULL AND removed_by IS NOT NULL))
);
CREATE INDEX politica_activo_asset_idx ON gobierno.politica_activo (project_id, asset_id) WHERE active;
