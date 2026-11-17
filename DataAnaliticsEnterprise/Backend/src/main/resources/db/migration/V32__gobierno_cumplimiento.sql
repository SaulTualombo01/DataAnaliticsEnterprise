-- M3 Gobierno, fase 3: reglas verificables de las políticas, ficha de gobierno del activo
-- (responsable del dato y clasificación formal) y evaluaciones de cumplimiento.
-- project_id, asset_id y los actores son UUID sin FK: esas tablas no existen en esta rama.

-- Regla verificable de una política. Forma parte del estado versionado de la política.
CREATE TABLE gobierno.regla_politica (
    id uuid PRIMARY KEY,
    policy_id uuid NOT NULL,
    project_id uuid NOT NULL,
    rule_type text NOT NULL CHECK (rule_type IN
        ('REQUIERE_RESPONSABLE','REQUIERE_CLASIFICACION','CLASIFICACION_MINIMA','CALIDAD_APROBADA')),
    parameter text CHECK (parameter IS NULL OR length(parameter) BETWEEN 1 AND 100),
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    FOREIGN KEY (policy_id, project_id) REFERENCES gobierno.politica(id, project_id) ON DELETE RESTRICT,
    UNIQUE NULLS NOT DISTINCT (policy_id, rule_type, parameter)
);
CREATE INDEX regla_politica_policy_idx ON gobierno.regla_politica (policy_id, project_id);

-- Ficha de gobierno de un activo del catálogo: lo que M3 sabe del activo y M1 no administra.
CREATE TABLE gobierno.ficha_activo (
    project_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    owner_id uuid,
    classification text CHECK (classification IN ('PUBLICA','INTERNA','CONFIDENCIAL','RESTRINGIDA')),
    revision bigint NOT NULL CHECK (revision >= 0),
    current_version_id uuid NOT NULL REFERENCES gobierno.version_recurso(id) ON DELETE RESTRICT,
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL,
    PRIMARY KEY (project_id, asset_id)
);
CREATE INDEX ficha_activo_version_idx ON gobierno.ficha_activo (current_version_id);

-- Resultado histórico de evaluar un activo contra sus políticas activas. No se edita.
CREATE TABLE gobierno.evaluacion_cumplimiento (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    overall_status text NOT NULL CHECK (overall_status IN ('COMPLIANT','NON_COMPLIANT','INDETERMINATE','NOT_APPLICABLE')),
    evaluated_at timestamptz NOT NULL,
    evaluated_by uuid NOT NULL,
    profile_version_id uuid REFERENCES gobierno.version_recurso(id) ON DELETE RESTRICT,
    results jsonb NOT NULL CHECK (jsonb_typeof(results) = 'array'),
    audit_event_id uuid NOT NULL REFERENCES gobierno.evento_auditoria(id) ON DELETE RESTRICT
);
CREATE INDEX evaluacion_asset_time_idx ON gobierno.evaluacion_cumplimiento (project_id, asset_id, evaluated_at DESC, id DESC);
CREATE INDEX evaluacion_profile_idx ON gobierno.evaluacion_cumplimiento (profile_version_id);
CREATE INDEX evaluacion_event_idx ON gobierno.evaluacion_cumplimiento (audit_event_id);

CREATE TRIGGER evaluacion_inmutable BEFORE UPDATE OR DELETE ON gobierno.evaluacion_cumplimiento
    FOR EACH ROW EXECUTE FUNCTION gobierno.rechazar_cambio_historico();
