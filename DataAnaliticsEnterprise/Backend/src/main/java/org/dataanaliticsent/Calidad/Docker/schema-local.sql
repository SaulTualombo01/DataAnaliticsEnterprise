-- Copia estructural para pruebas locales. No ejecutar sobre el proyecto remoto existente.
CREATE SCHEMA m2;
CREATE ROLE m2_backend NOLOGIN NOBYPASSRLS;


GRANT USAGE ON SCHEMA m2 TO m2_backend;
CREATE TABLE m2.asset_refs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  source_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.audit_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid,
  actor_id uuid,
  request_id uuid NOT NULL,
  action character varying(100) NOT NULL,
  resource_type character varying(80) NOT NULL,
  resource_id uuid,
  outcome character varying(10) NOT NULL,
  reason_code character varying(80),
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.evaluation_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  evaluation_id uuid NOT NULL,
  rule_version_id uuid NOT NULL
);
CREATE TABLE m2.evaluations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  input_ref_id uuid NOT NULL,
  requested_by uuid NOT NULL,
  status character varying(16) DEFAULT 'QUEUED'::character varying NOT NULL,
  quality_outcome character varying(20) DEFAULT 'NOT_ASSESSED'::character varying NOT NULL,
  request_id uuid NOT NULL,
  idempotency_key uuid,
  request_sha256 character varying(64),
  started_at timestamp with time zone,
  finished_at timestamp with time zone,
  failure_code character varying(80),
  lease_until timestamp with time zone,
  retry_of uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  revision bigint DEFAULT 0 NOT NULL
);
CREATE TABLE m2.incident_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  incident_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  from_status character varying(20),
  to_status character varying(20) NOT NULL,
  comment_ciphertext text,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.incidents (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  evaluation_id uuid NOT NULL,
  rule_result_id uuid NOT NULL,
  field_name character varying(160),
  record_reference character varying(160),
  problem_code character varying(80) NOT NULL,
  severity character varying(10) NOT NULL,
  status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
  evidence_ciphertext text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  revision bigint DEFAULT 0 NOT NULL
);
CREATE TABLE m2.input_refs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  read_id uuid NOT NULL,
  snapshot_id uuid,
  metadata_version_id uuid NOT NULL,
  scope_definition jsonb NOT NULL,
  complete boolean NOT NULL,
  row_count bigint NOT NULL,
  byte_count bigint NOT NULL,
  acquired_at timestamp with time zone NOT NULL,
  expires_at timestamp with time zone,
  content_sha256 character varying(64),
  protected_object_ref character varying(1000),
  availability character varying(16) DEFAULT 'AVAILABLE'::character varying NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.password_reset_tokens (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  token_hash character varying(64) NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  used_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.projects (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  organization_id uuid NOT NULL,
  name character varying(160) NOT NULL,
  active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  revision bigint DEFAULT 0 NOT NULL
);
CREATE TABLE m2.quality_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  name character varying(160) NOT NULL,
  enabled boolean DEFAULT true NOT NULL,
  created_by uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  revision bigint DEFAULT 0 NOT NULL
);
CREATE TABLE m2.role_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  user_id uuid NOT NULL,
  role_name character varying(40) NOT NULL,
  asset_scope_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.rule_results (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  evaluation_id uuid NOT NULL,
  rule_version_id uuid NOT NULL,
  measurement_state character varying(20) NOT NULL,
  candidate_units bigint NOT NULL,
  applicable_units bigint NOT NULL,
  numerator bigint NOT NULL,
  denominator bigint NOT NULL,
  threshold numeric(7,4),
  minimum_coverage numeric(7,4) NOT NULL,
  metric_value numeric GENERATED ALWAYS AS (
CASE
    WHEN (((measurement_state)::text = 'MEASURED'::text) AND (denominator > 0)) THEN ((100.0 * (numerator)::numeric) / (denominator)::numeric)
    ELSE NULL::numeric
END) STORED,
  coverage_value numeric GENERATED ALWAYS AS (
CASE
    WHEN (applicable_units > 0) THEN ((100.0 * (denominator)::numeric) / (applicable_units)::numeric)
    ELSE NULL::numeric
END) STORED,
  compliance_state character varying(24) GENERATED ALWAYS AS (
CASE
    WHEN ((measurement_state)::text = 'NOT_APPLICABLE'::text) THEN 'NOT_APPLICABLE'::text
    WHEN ((measurement_state)::text <> 'MEASURED'::text) THEN 'NOT_EVALUABLE'::text
    WHEN (((denominator)::numeric * 100.0) < (minimum_coverage * (applicable_units)::numeric)) THEN 'INSUFFICIENT_COVERAGE'::text
    WHEN (threshold IS NULL) THEN 'NO_THRESHOLD'::text
    WHEN (((numerator)::numeric * 100.0) >= (threshold * (denominator)::numeric)) THEN 'PASS'::text
    ELSE 'FAIL'::text
END) STORED,
  exclusion_reason character varying(500),
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.rule_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  project_id uuid NOT NULL,
  asset_id uuid NOT NULL,
  rule_id uuid NOT NULL,
  version_number integer NOT NULL,
  dimension character varying(20) NOT NULL,
  definition jsonb NOT NULL,
  formula_version character varying(40) NOT NULL,
  threshold numeric(7,4),
  minimum_coverage numeric(7,4) DEFAULT 100 NOT NULL,
  critical boolean DEFAULT false NOT NULL,
  created_by uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE m2.spring_session (
  primary_id character(36) NOT NULL,
  session_id character(36) NOT NULL,
  creation_time bigint NOT NULL,
  last_access_time bigint NOT NULL,
  max_inactive_interval integer NOT NULL,
  expiry_time bigint NOT NULL,
  principal_name character varying(100)
);
CREATE TABLE m2.spring_session_attributes (
  session_primary_id character(36) NOT NULL,
  attribute_name character varying(200) NOT NULL,
  attribute_bytes bytea NOT NULL
);
CREATE TABLE m2.users (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  email_ciphertext text NOT NULL,
  email_lookup_hash character varying(64) NOT NULL,
  password_hash text NOT NULL,
  active boolean DEFAULT true NOT NULL,
  auth_version bigint DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  revision bigint DEFAULT 0 NOT NULL
);
ALTER TABLE m2.evaluation_rules ADD CONSTRAINT evaluation_rules_pkey PRIMARY KEY (id);
ALTER TABLE m2.evaluation_rules ADD CONSTRAINT evaluation_rules_project_id_asset_id_id_key UNIQUE (project_id, asset_id, id);
ALTER TABLE m2.evaluation_rules ADD CONSTRAINT evaluation_rules_project_id_evaluation_id_rule_version_id_key UNIQUE (project_id, evaluation_id, rule_version_id);
ALTER TABLE m2.users ADD CONSTRAINT users_email_ciphertext_check CHECK ((email_ciphertext ~~ 'v1:%'::text));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_measurement_state_check CHECK (((measurement_state)::text = ANY ((ARRAY['MEASURED'::character varying, 'NOT_EVALUABLE'::character varying, 'NOT_APPLICABLE'::character varying])::text[])));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_candidate_units_check CHECK ((candidate_units >= 0));
ALTER TABLE m2.users ADD CONSTRAINT users_email_lookup_hash_check CHECK (((email_lookup_hash)::text ~ '^[0-9a-f]{64}$'::text));
ALTER TABLE m2.users ADD CONSTRAINT users_password_hash_check CHECK ((password_hash ~~ '$argon2id$%'::text));
ALTER TABLE m2.users ADD CONSTRAINT users_auth_version_check CHECK ((auth_version >= 0));
ALTER TABLE m2.users ADD CONSTRAINT users_revision_check CHECK ((revision >= 0));
ALTER TABLE m2.users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE m2.users ADD CONSTRAINT users_email_lookup_hash_key UNIQUE (email_lookup_hash);
ALTER TABLE m2.projects ADD CONSTRAINT projects_revision_check CHECK ((revision >= 0));
ALTER TABLE m2.projects ADD CONSTRAINT projects_pkey PRIMARY KEY (id);
ALTER TABLE m2.role_assignments ADD CONSTRAINT role_assignments_role_name_check CHECK (((role_name)::text = ANY ((ARRAY['SYSTEM_ADMIN'::character varying, 'CATALOG_MANAGER'::character varying, 'QUALITY_MANAGER'::character varying, 'GOVERNANCE_MANAGER'::character varying, 'BUSINESS_ANALYST'::character varying])::text[])));
ALTER TABLE m2.role_assignments ADD CONSTRAINT role_assignments_check CHECK ((((role_name)::text <> 'SYSTEM_ADMIN'::text) OR (asset_scope_id IS NULL)));
ALTER TABLE m2.role_assignments ADD CONSTRAINT role_assignments_pkey PRIMARY KEY (id);
ALTER TABLE m2.role_assignments ADD CONSTRAINT role_assignments_project_id_user_id_role_name_asset_scope_i_key UNIQUE NULLS NOT DISTINCT (project_id, user_id, role_name, asset_scope_id);
ALTER TABLE m2.password_reset_tokens ADD CONSTRAINT password_reset_tokens_token_hash_check CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text));
ALTER TABLE m2.password_reset_tokens ADD CONSTRAINT password_reset_tokens_check CHECK ((expires_at > created_at));
ALTER TABLE m2.password_reset_tokens ADD CONSTRAINT password_reset_tokens_check1 CHECK (((used_at IS NULL) OR (used_at >= created_at)));
ALTER TABLE m2.password_reset_tokens ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);
ALTER TABLE m2.password_reset_tokens ADD CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash);
ALTER TABLE m2.asset_refs ADD CONSTRAINT asset_refs_pkey PRIMARY KEY (id);
ALTER TABLE m2.asset_refs ADD CONSTRAINT asset_refs_project_id_asset_id_key UNIQUE (project_id, asset_id);
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_scope_definition_check CHECK ((jsonb_typeof(scope_definition) = 'object'::text));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_row_count_check CHECK ((row_count >= 0));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_byte_count_check CHECK ((byte_count >= 0));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_content_sha256_check CHECK (((content_sha256 IS NULL) OR ((content_sha256)::text ~ '^[0-9a-f]{64}$'::text)));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_protected_object_ref_check CHECK (((protected_object_ref IS NULL) OR (POSITION(('?'::text) IN (protected_object_ref)) = 0)));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_availability_check CHECK (((availability)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'EXPIRED'::character varying, 'DELETED'::character varying])::text[])));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_check CHECK (((expires_at IS NULL) OR (expires_at > acquired_at)));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_check1 CHECK (((snapshot_id IS NOT NULL) OR (protected_object_ref IS NULL)));
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_pkey PRIMARY KEY (id);
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_project_id_asset_id_id_key UNIQUE (project_id, asset_id, id);
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_project_id_read_id_key UNIQUE (project_id, read_id);
ALTER TABLE m2.quality_rules ADD CONSTRAINT quality_rules_revision_check CHECK ((revision >= 0));
ALTER TABLE m2.quality_rules ADD CONSTRAINT quality_rules_pkey PRIMARY KEY (id);
ALTER TABLE m2.quality_rules ADD CONSTRAINT quality_rules_project_id_asset_id_id_key UNIQUE (project_id, asset_id, id);
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_version_number_check CHECK ((version_number > 0));
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_dimension_check CHECK (((dimension)::text = ANY ((ARRAY['COMPLETENESS'::character varying, 'ACCURACY'::character varying, 'CONSISTENCY'::character varying, 'UNIQUENESS'::character varying, 'VALIDITY'::character varying, 'TIMELINESS'::character varying])::text[])));
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_definition_check CHECK ((jsonb_typeof(definition) = 'object'::text));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_applicable_units_check CHECK ((applicable_units >= 0));
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_threshold_check CHECK (((threshold >= (0)::numeric) AND (threshold <= (100)::numeric)));
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_minimum_coverage_check CHECK (((minimum_coverage >= (0)::numeric) AND (minimum_coverage <= (100)::numeric)));
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_pkey PRIMARY KEY (id);
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_project_id_asset_id_id_key UNIQUE (project_id, asset_id, id);
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_project_id_rule_id_version_number_key UNIQUE (project_id, rule_id, version_number);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_status_check CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_quality_outcome_check CHECK (((quality_outcome)::text = ANY ((ARRAY['NOT_ASSESSED'::character varying, 'PASS'::character varying, 'FAIL'::character varying, 'NO_DATA'::character varying, 'INCOMPLETE'::character varying])::text[])));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_numerator_check CHECK ((numerator >= 0));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_request_sha256_check CHECK (((request_sha256 IS NULL) OR ((request_sha256)::text ~ '^[0-9a-f]{64}$'::text)));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_revision_check CHECK ((revision >= 0));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_check CHECK (((idempotency_key IS NULL) = (request_sha256 IS NULL)));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_check1 CHECK (((((status)::text = 'QUEUED'::text) AND (started_at IS NULL) AND (finished_at IS NULL)) OR (((status)::text = 'RUNNING'::text) AND (started_at IS NOT NULL) AND (finished_at IS NULL)) OR (((status)::text = 'COMPLETED'::text) AND (started_at IS NOT NULL) AND (finished_at IS NOT NULL)) OR (((status)::text = 'FAILED'::text) AND (finished_at IS NOT NULL))));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_check2 CHECK (((started_at IS NULL) OR (started_at >= created_at)));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_check3 CHECK (((finished_at IS NULL) OR (finished_at >= COALESCE(started_at, created_at))));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_check4 CHECK ((((status)::text <> 'FAILED'::text) OR ((quality_outcome)::text = ANY ((ARRAY['NOT_ASSESSED'::character varying, 'INCOMPLETE'::character varying])::text[]))));
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_pkey PRIMARY KEY (id);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_project_id_asset_id_id_key UNIQUE (project_id, asset_id, id);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_project_id_id_key UNIQUE (project_id, id);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_project_id_requested_by_idempotency_key_key UNIQUE (project_id, requested_by, idempotency_key);
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_denominator_check CHECK ((denominator >= 0));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_threshold_check CHECK (((threshold >= (0)::numeric) AND (threshold <= (100)::numeric)));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_minimum_coverage_check CHECK (((minimum_coverage >= (0)::numeric) AND (minimum_coverage <= (100)::numeric)));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_check CHECK (((candidate_units >= applicable_units) AND (applicable_units >= denominator) AND (denominator >= numerator)));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_check1 CHECK (((((measurement_state)::text = 'MEASURED'::text) AND (denominator > 0)) OR (((measurement_state)::text = 'NOT_EVALUABLE'::text) AND (denominator = 0)) OR (((measurement_state)::text = 'NOT_APPLICABLE'::text) AND (applicable_units = 0) AND (denominator = 0))));
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_pkey PRIMARY KEY (id);
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_project_id_asset_id_id_key UNIQUE (project_id, asset_id, id);
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_severity_check CHECK (((severity)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])));
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying, 'DISMISSED'::character varying])::text[])));
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_evidence_ciphertext_check CHECK (((evidence_ciphertext IS NULL) OR (evidence_ciphertext ~~ 'v1:%'::text)));
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_revision_check CHECK ((revision >= 0));
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_pkey PRIMARY KEY (id);
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_project_id_asset_id_id_key UNIQUE (project_id, asset_id, id);
ALTER TABLE m2.incident_events ADD CONSTRAINT incident_events_from_status_check CHECK (((from_status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying, 'DISMISSED'::character varying])::text[])));
ALTER TABLE m2.incident_events ADD CONSTRAINT incident_events_to_status_check CHECK (((to_status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying, 'DISMISSED'::character varying])::text[])));
ALTER TABLE m2.incident_events ADD CONSTRAINT incident_events_comment_ciphertext_check CHECK (((comment_ciphertext IS NULL) OR (comment_ciphertext ~~ 'v1:%'::text)));
ALTER TABLE m2.incident_events ADD CONSTRAINT incident_events_pkey PRIMARY KEY (id);
ALTER TABLE m2.audit_events ADD CONSTRAINT audit_events_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILURE'::character varying])::text[])));
ALTER TABLE m2.audit_events ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);
ALTER TABLE m2.spring_session ADD CONSTRAINT spring_session_pkey PRIMARY KEY (primary_id);
ALTER TABLE m2.spring_session ADD CONSTRAINT spring_session_session_id_key UNIQUE (session_id);
ALTER TABLE m2.spring_session_attributes ADD CONSTRAINT spring_session_attributes_pkey PRIMARY KEY (session_primary_id, attribute_name);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_project_id_retry_of_fkey FOREIGN KEY (project_id, retry_of) REFERENCES m2.evaluations(project_id, id);
ALTER TABLE m2.evaluation_rules ADD CONSTRAINT evaluation_rules_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.evaluation_rules ADD CONSTRAINT evaluation_rules_project_id_asset_id_evaluation_id_fkey FOREIGN KEY (project_id, asset_id, evaluation_id) REFERENCES m2.evaluations(project_id, asset_id, id);
ALTER TABLE m2.evaluation_rules ADD CONSTRAINT evaluation_rules_project_id_asset_id_rule_version_id_fkey FOREIGN KEY (project_id, asset_id, rule_version_id) REFERENCES m2.rule_versions(project_id, asset_id, id);
ALTER TABLE m2.role_assignments ADD CONSTRAINT role_assignments_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.role_assignments ADD CONSTRAINT role_assignments_user_id_fkey FOREIGN KEY (user_id) REFERENCES m2.users(id);
ALTER TABLE m2.password_reset_tokens ADD CONSTRAINT password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES m2.users(id);
ALTER TABLE m2.asset_refs ADD CONSTRAINT asset_refs_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.input_refs ADD CONSTRAINT input_refs_project_id_asset_id_fkey FOREIGN KEY (project_id, asset_id) REFERENCES m2.asset_refs(project_id, asset_id);
ALTER TABLE m2.quality_rules ADD CONSTRAINT quality_rules_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.quality_rules ADD CONSTRAINT quality_rules_created_by_fkey FOREIGN KEY (created_by) REFERENCES m2.users(id);
ALTER TABLE m2.quality_rules ADD CONSTRAINT quality_rules_project_id_asset_id_fkey FOREIGN KEY (project_id, asset_id) REFERENCES m2.asset_refs(project_id, asset_id);
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES m2.users(id);
ALTER TABLE m2.rule_versions ADD CONSTRAINT rule_versions_project_id_asset_id_rule_id_fkey FOREIGN KEY (project_id, asset_id, rule_id) REFERENCES m2.quality_rules(project_id, asset_id, id);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES m2.users(id);
ALTER TABLE m2.evaluations ADD CONSTRAINT evaluations_project_id_asset_id_input_ref_id_fkey FOREIGN KEY (project_id, asset_id, input_ref_id) REFERENCES m2.input_refs(project_id, asset_id, id);
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_project_id_asset_id_evaluation_id_fkey FOREIGN KEY (project_id, asset_id, evaluation_id) REFERENCES m2.evaluations(project_id, asset_id, id);
ALTER TABLE m2.rule_results ADD CONSTRAINT rule_results_project_id_evaluation_id_rule_version_id_fkey FOREIGN KEY (project_id, evaluation_id, rule_version_id) REFERENCES m2.evaluation_rules(project_id, evaluation_id, rule_version_id);
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_project_id_asset_id_evaluation_id_fkey FOREIGN KEY (project_id, asset_id, evaluation_id) REFERENCES m2.evaluations(project_id, asset_id, id);
ALTER TABLE m2.incidents ADD CONSTRAINT incidents_project_id_asset_id_rule_result_id_fkey FOREIGN KEY (project_id, asset_id, rule_result_id) REFERENCES m2.rule_results(project_id, asset_id, id);
ALTER TABLE m2.incident_events ADD CONSTRAINT incident_events_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.incident_events ADD CONSTRAINT incident_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES m2.users(id);
ALTER TABLE m2.incident_events ADD CONSTRAINT incident_events_project_id_asset_id_incident_id_fkey FOREIGN KEY (project_id, asset_id, incident_id) REFERENCES m2.incidents(project_id, asset_id, id);
ALTER TABLE m2.audit_events ADD CONSTRAINT audit_events_project_id_fkey FOREIGN KEY (project_id) REFERENCES m2.projects(id);
ALTER TABLE m2.audit_events ADD CONSTRAINT audit_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES m2.users(id);
ALTER TABLE m2.spring_session_attributes ADD CONSTRAINT spring_session_attributes_session_primary_id_fkey FOREIGN KEY (session_primary_id) REFERENCES m2.spring_session(primary_id) ON DELETE CASCADE;
CREATE OR REPLACE FUNCTION m2.current_actor_id()
 RETURNS uuid
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
 SELECT nullif(current_setting('app.user_id', true), '')::uuid;
$function$
;
CREATE OR REPLACE FUNCTION m2.current_project_id()
 RETURNS uuid
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
 SELECT nullif(current_setting('app.project_id', true), '')::uuid;
$function$
;
CREATE OR REPLACE FUNCTION m2.guard_evaluation()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
BEGIN
 IF TG_OP = 'INSERT' THEN
   IF NEW.status <> 'QUEUED' OR NEW.quality_outcome <> 'NOT_ASSESSED' THEN
     RAISE EXCEPTION 'Una evaluación debe crearse en cola y sin calificación' USING ERRCODE = '23514';
   END IF;
   RETURN NEW;
 END IF;
 IF OLD.status IN ('COMPLETED','FAILED') THEN
   RAISE EXCEPTION 'La evaluación finalizada es inmutable' USING ERRCODE = '23514';
 END IF;
 IF (NEW.project_id, NEW.asset_id, NEW.input_ref_id, NEW.requested_by, NEW.request_id, NEW.idempotency_key, NEW.request_sha256, NEW.created_at)
   IS DISTINCT FROM
   (OLD.project_id, OLD.asset_id, OLD.input_ref_id, OLD.requested_by, OLD.request_id, OLD.idempotency_key, OLD.request_sha256, OLD.created_at) THEN
   RAISE EXCEPTION 'No se puede sustituir la entrada o contexto de una evaluación' USING ERRCODE = '23514';
 END IF;
 IF NOT ((OLD.status = 'QUEUED' AND NEW.status IN ('QUEUED','RUNNING','FAILED')) OR
         (OLD.status = 'RUNNING' AND NEW.status IN ('RUNNING','COMPLETED','FAILED'))) THEN
   RAISE EXCEPTION 'Transición de ejecución inválida' USING ERRCODE = '23514';
 END IF;
 IF NEW.status = 'COMPLETED' THEN
   IF NOT EXISTS (SELECT 1 FROM m2.evaluation_rules er WHERE er.evaluation_id = NEW.id AND er.project_id = NEW.project_id)
      OR EXISTS (SELECT 1 FROM m2.evaluation_rules er WHERE er.evaluation_id = NEW.id AND er.project_id = NEW.project_id
         AND NOT EXISTS (SELECT 1 FROM m2.rule_results rr WHERE rr.evaluation_id = er.evaluation_id AND rr.rule_version_id = er.rule_version_id AND rr.project_id = er.project_id)) THEN
     RAISE EXCEPTION 'Faltan resultados de reglas seleccionadas' USING ERRCODE = '23514';
   END IF;
   IF NOT EXISTS (SELECT 1 FROM m2.input_refs i WHERE i.id = NEW.input_ref_id AND i.project_id = NEW.project_id AND i.complete) THEN
     RAISE EXCEPTION 'No se puede completar una evaluación con entrada parcial' USING ERRCODE = '23514';
   END IF;
   IF NEW.quality_outcome = 'PASS' AND EXISTS (SELECT 1 FROM m2.rule_results rr WHERE rr.evaluation_id = NEW.id AND rr.project_id = NEW.project_id AND rr.compliance_state <> 'PASS') THEN
     RAISE EXCEPTION 'No se puede aprobar con medidas incumplidas o no evaluables' USING ERRCODE = '23514';
   END IF;
 END IF;
 RETURN NEW;
END;
$function$
;
CREATE OR REPLACE FUNCTION m2.guard_incident_result()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM m2.rule_results r WHERE r.id = NEW.rule_result_id AND r.project_id = NEW.project_id AND r.evaluation_id = NEW.evaluation_id AND r.asset_id = NEW.asset_id) THEN
   RAISE EXCEPTION 'La incidencia debe corresponder al resultado de la misma evaluación' USING ERRCODE = '23514';
 END IF;
 IF TG_OP = 'UPDATE' AND (NEW.project_id, NEW.asset_id, NEW.evaluation_id, NEW.rule_result_id, NEW.field_name, NEW.record_reference, NEW.problem_code, NEW.created_at)
    IS DISTINCT FROM (OLD.project_id, OLD.asset_id, OLD.evaluation_id, OLD.rule_result_id, OLD.field_name, OLD.record_reference, OLD.problem_code, OLD.created_at) THEN
   RAISE EXCEPTION 'La evidencia de detección no puede reasignarse' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END;
$function$
;
CREATE OR REPLACE FUNCTION m2.guard_input_reference()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
BEGIN
 IF (to_jsonb(NEW) - 'availability') IS DISTINCT FROM (to_jsonb(OLD) - 'availability') THEN
   RAISE EXCEPTION 'La entrada identificada es inmutable' USING ERRCODE = '23514';
 END IF;
 IF OLD.availability = 'DELETED' AND NEW.availability <> 'DELETED' THEN
   RAISE EXCEPTION 'Una entrada eliminada no puede reactivarse' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END;
$function$
;
CREATE OR REPLACE FUNCTION m2.guard_rule_result()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
DECLARE rv m2.rule_versions; ev m2.evaluations;
BEGIN
 SELECT * INTO STRICT ev FROM m2.evaluations WHERE id = NEW.evaluation_id AND project_id = NEW.project_id;
 IF ev.status <> 'RUNNING' THEN RAISE EXCEPTION 'Solo se insertan resultados durante la ejecución' USING ERRCODE = '23514'; END IF;
 SELECT * INTO STRICT rv FROM m2.rule_versions WHERE id = NEW.rule_version_id AND project_id = NEW.project_id;
 IF NEW.threshold IS DISTINCT FROM rv.threshold OR NEW.minimum_coverage IS DISTINCT FROM rv.minimum_coverage THEN
   RAISE EXCEPTION 'El umbral debe corresponder a la versión de regla' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END;
$function$
;
CREATE OR REPLACE FUNCTION m2.guard_selected_rule()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM m2.evaluations e WHERE e.id = NEW.evaluation_id AND e.project_id = NEW.project_id AND e.status = 'QUEUED') THEN
   RAISE EXCEPTION 'Las reglas se fijan antes de ejecutar' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END;
$function$
;
CREATE OR REPLACE FUNCTION m2.has_role(allowed_roles text[], target_asset uuid DEFAULT NULL::uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
 SELECT EXISTS (
   SELECT 1 FROM m2.role_assignments r JOIN m2.users u ON u.id = r.user_id
   JOIN m2.projects p ON p.id = r.project_id
   WHERE r.project_id = m2.current_project_id() AND r.user_id = m2.current_actor_id()
   AND u.active AND p.active AND r.role_name = ANY(allowed_roles)
   AND (r.asset_scope_id IS NULL OR r.asset_scope_id = target_asset)
 );
$function$
;
CREATE OR REPLACE FUNCTION m2.reject_history_change()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
BEGIN RAISE EXCEPTION 'El registro histórico es inmutable' USING ERRCODE = '23514'; END;
$function$
;
CREATE TRIGGER immutable_history BEFORE DELETE OR UPDATE ON m2.rule_versions FOR EACH ROW EXECUTE FUNCTION m2.reject_history_change();
CREATE TRIGGER immutable_history BEFORE DELETE OR UPDATE ON m2.evaluation_rules FOR EACH ROW EXECUTE FUNCTION m2.reject_history_change();
CREATE TRIGGER immutable_history BEFORE DELETE OR UPDATE ON m2.rule_results FOR EACH ROW EXECUTE FUNCTION m2.reject_history_change();
CREATE TRIGGER immutable_history BEFORE DELETE OR UPDATE ON m2.audit_events FOR EACH ROW EXECUTE FUNCTION m2.reject_history_change();
CREATE TRIGGER immutable_history BEFORE DELETE OR UPDATE ON m2.incident_events FOR EACH ROW EXECUTE FUNCTION m2.reject_history_change();
CREATE TRIGGER immutable_input BEFORE UPDATE ON m2.input_refs FOR EACH ROW EXECUTE FUNCTION m2.guard_input_reference();
CREATE TRIGGER evaluation_state BEFORE INSERT OR UPDATE ON m2.evaluations FOR EACH ROW EXECUTE FUNCTION m2.guard_evaluation();
CREATE TRIGGER result_configuration BEFORE INSERT ON m2.rule_results FOR EACH ROW EXECUTE FUNCTION m2.guard_rule_result();
CREATE TRIGGER selected_rule_state BEFORE INSERT ON m2.evaluation_rules FOR EACH ROW EXECUTE FUNCTION m2.guard_selected_rule();
CREATE TRIGGER incident_result BEFORE INSERT OR UPDATE ON m2.incidents FOR EACH ROW EXECUTE FUNCTION m2.guard_incident_result();
ALTER TABLE m2.asset_refs ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.asset_refs FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.audit_events FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.evaluation_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.evaluation_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.evaluations ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.evaluations FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.incident_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.incident_events FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.incidents ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.incidents FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.input_refs ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.input_refs FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.password_reset_tokens FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.projects FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.quality_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.quality_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.role_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.role_assignments FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.rule_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.rule_results FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.rule_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.rule_versions FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.spring_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.spring_session FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.spring_session_attributes ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.spring_session_attributes FORCE ROW LEVEL SECURITY;
ALTER TABLE m2.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE m2.users FORCE ROW LEVEL SECURITY;
CREATE POLICY "backend_identity" ON m2.users FOR ALL TO m2_backend USING (true) WITH CHECK (true);
CREATE POLICY "backend_identity" ON m2.projects FOR ALL TO m2_backend USING (true) WITH CHECK (true);
CREATE POLICY "backend_identity_read" ON m2.role_assignments FOR SELECT TO m2_backend USING (true);
CREATE POLICY "backend_identity_insert" ON m2.role_assignments FOR INSERT TO m2_backend WITH CHECK (true);
CREATE POLICY "backend_identity_update" ON m2.role_assignments FOR UPDATE TO m2_backend USING (true) WITH CHECK (true);
CREATE POLICY "admin_role_delete" ON m2.role_assignments FOR DELETE TO m2_backend USING (project_id = m2.current_project_id() AND m2.has_role(ARRAY['SYSTEM_ADMIN'::text]));
CREATE POLICY "backend_identity" ON m2.password_reset_tokens FOR ALL TO m2_backend USING (true) WITH CHECK (true);
CREATE POLICY "backend_identity" ON m2.spring_session FOR ALL TO m2_backend USING (true) WITH CHECK (true);
CREATE POLICY "backend_identity" ON m2.spring_session_attributes FOR ALL TO m2_backend USING (true) WITH CHECK (true);
CREATE POLICY "scoped_read" ON m2.asset_refs FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['CATALOG_MANAGER'::text, 'QUALITY_MANAGER'::text, 'GOVERNANCE_MANAGER'::text, 'BUSINESS_ANALYST'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.asset_refs FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['CATALOG_MANAGER'::text, 'QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_update" ON m2.asset_refs FOR UPDATE TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['CATALOG_MANAGER'::text, 'QUALITY_MANAGER'::text], asset_id))) WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['CATALOG_MANAGER'::text, 'QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.input_refs FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.input_refs FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_update" ON m2.input_refs FOR UPDATE TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id))) WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.quality_rules FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text, 'GOVERNANCE_MANAGER'::text, 'BUSINESS_ANALYST'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.quality_rules FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.incidents FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_update" ON m2.quality_rules FOR UPDATE TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id))) WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.rule_versions FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text, 'GOVERNANCE_MANAGER'::text, 'BUSINESS_ANALYST'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.rule_versions FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.evaluations FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text, 'GOVERNANCE_MANAGER'::text, 'BUSINESS_ANALYST'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.evaluations FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_update" ON m2.evaluations FOR UPDATE TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id))) WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.evaluation_rules FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text, 'GOVERNANCE_MANAGER'::text, 'BUSINESS_ANALYST'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.evaluation_rules FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.rule_results FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text, 'GOVERNANCE_MANAGER'::text, 'BUSINESS_ANALYST'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.rule_results FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.incidents FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_update" ON m2.incidents FOR UPDATE TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id))) WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_read" ON m2.incident_events FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "scoped_insert" ON m2.incident_events FOR INSERT TO m2_backend WITH CHECK (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['QUALITY_MANAGER'::text], asset_id)));
CREATE POLICY "audit_read" ON m2.audit_events FOR SELECT TO m2_backend USING (((project_id = m2.current_project_id()) AND m2.has_role(ARRAY['SYSTEM_ADMIN'::text, 'GOVERNANCE_MANAGER'::text])));
CREATE POLICY "audit_insert" ON m2.audit_events FOR INSERT TO m2_backend WITH CHECK ((((project_id IS NULL) AND ((actor_id IS NULL) OR (actor_id = m2.current_actor_id()))) OR ((project_id = m2.current_project_id()) AND (actor_id = m2.current_actor_id()))));
GRANT INSERT ON m2.users TO m2_backend;
GRANT SELECT ON m2.users TO m2_backend;
GRANT UPDATE ON m2.users TO m2_backend;
GRANT INSERT ON m2.projects TO m2_backend;
GRANT SELECT ON m2.projects TO m2_backend;
GRANT UPDATE ON m2.projects TO m2_backend;
GRANT INSERT ON m2.role_assignments TO m2_backend;
GRANT SELECT ON m2.role_assignments TO m2_backend;
GRANT UPDATE ON m2.role_assignments TO m2_backend;
GRANT DELETE ON m2.role_assignments TO m2_backend;
GRANT INSERT ON m2.password_reset_tokens TO m2_backend;
GRANT SELECT ON m2.password_reset_tokens TO m2_backend;
GRANT UPDATE ON m2.password_reset_tokens TO m2_backend;
GRANT DELETE ON m2.password_reset_tokens TO m2_backend;
GRANT INSERT ON m2.spring_session TO m2_backend;
GRANT SELECT ON m2.spring_session TO m2_backend;
GRANT UPDATE ON m2.spring_session TO m2_backend;
GRANT DELETE ON m2.spring_session TO m2_backend;
GRANT INSERT ON m2.spring_session_attributes TO m2_backend;
GRANT SELECT ON m2.spring_session_attributes TO m2_backend;
GRANT UPDATE ON m2.spring_session_attributes TO m2_backend;
GRANT DELETE ON m2.spring_session_attributes TO m2_backend;
GRANT INSERT ON m2.asset_refs TO m2_backend;
GRANT SELECT ON m2.asset_refs TO m2_backend;
GRANT UPDATE ON m2.asset_refs TO m2_backend;
GRANT INSERT ON m2.input_refs TO m2_backend;
GRANT SELECT ON m2.input_refs TO m2_backend;
GRANT UPDATE ON m2.input_refs TO m2_backend;
GRANT INSERT ON m2.quality_rules TO m2_backend;
GRANT SELECT ON m2.quality_rules TO m2_backend;
GRANT UPDATE ON m2.quality_rules TO m2_backend;
GRANT INSERT ON m2.rule_versions TO m2_backend;
GRANT SELECT ON m2.rule_versions TO m2_backend;
GRANT INSERT ON m2.evaluations TO m2_backend;
GRANT SELECT ON m2.evaluations TO m2_backend;
GRANT UPDATE ON m2.evaluations TO m2_backend;
GRANT INSERT ON m2.evaluation_rules TO m2_backend;
GRANT SELECT ON m2.evaluation_rules TO m2_backend;
GRANT INSERT ON m2.rule_results TO m2_backend;
GRANT SELECT ON m2.rule_results TO m2_backend;
GRANT INSERT ON m2.incidents TO m2_backend;
GRANT SELECT ON m2.incidents TO m2_backend;
GRANT UPDATE ON m2.incidents TO m2_backend;
GRANT INSERT ON m2.incident_events TO m2_backend;
GRANT SELECT ON m2.incident_events TO m2_backend;
GRANT INSERT ON m2.audit_events TO m2_backend;
GRANT SELECT ON m2.audit_events TO m2_backend;

