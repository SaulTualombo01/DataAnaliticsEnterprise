BEGIN;
-- Solo expone contadores: permite aplicar la cuota del proyecto también a gestores con alcance por activo.
CREATE OR REPLACE FUNCTION m2.pending_evaluation_counts(requested_project uuid)
RETURNS TABLE(project_pending bigint, user_pending bigint)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $function$
BEGIN
  IF requested_project IS DISTINCT FROM m2.current_project_id() OR NOT EXISTS (
    SELECT 1 FROM m2.role_assignments r JOIN m2.users u ON u.id=r.user_id JOIN m2.projects p ON p.id=r.project_id
    WHERE r.project_id=requested_project AND r.user_id=m2.current_actor_id()
      AND r.role_name='QUALITY_MANAGER' AND u.active AND p.active
  ) THEN
    RAISE EXCEPTION 'Acceso denegado al contador de cola' USING ERRCODE='42501';
  END IF;
  RETURN QUERY SELECT count(*), count(*) FILTER (WHERE e.requested_by=m2.current_actor_id())
    FROM m2.evaluations e WHERE e.project_id=requested_project AND e.status IN ('QUEUED','RUNNING');
END;
$function$;
REVOKE ALL ON FUNCTION m2.pending_evaluation_counts(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION m2.pending_evaluation_counts(uuid) TO m2_backend;
CREATE INDEX IF NOT EXISTS evaluations_pending_project ON m2.evaluations(project_id,requested_by) WHERE status IN ('QUEUED','RUNNING');
COMMIT;
