-- Ejecutar con el propietario del esquema. Admite bases nuevas y existentes.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('m2.role-revocation'));

-- La política FOR ALL anterior también permitiría cualquier DELETE tras el GRANT.
DROP POLICY IF EXISTS backend_identity ON m2.role_assignments;
DROP POLICY IF EXISTS backend_identity_read ON m2.role_assignments;
DROP POLICY IF EXISTS backend_identity_insert ON m2.role_assignments;
DROP POLICY IF EXISTS backend_identity_update ON m2.role_assignments;
DROP POLICY IF EXISTS admin_role_delete ON m2.role_assignments;

CREATE POLICY backend_identity_read ON m2.role_assignments FOR SELECT TO m2_backend USING (true);
CREATE POLICY backend_identity_insert ON m2.role_assignments FOR INSERT TO m2_backend WITH CHECK (true);
CREATE POLICY backend_identity_update ON m2.role_assignments FOR UPDATE TO m2_backend USING (true) WITH CHECK (true);
CREATE POLICY admin_role_delete ON m2.role_assignments FOR DELETE TO m2_backend
    USING (project_id = m2.current_project_id() AND m2.has_role(ARRAY['SYSTEM_ADMIN'::text]));

GRANT DELETE ON m2.role_assignments TO m2_backend;
COMMIT;
