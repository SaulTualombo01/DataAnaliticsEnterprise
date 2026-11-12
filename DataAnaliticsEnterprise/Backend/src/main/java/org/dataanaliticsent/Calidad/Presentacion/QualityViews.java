package org.dataanaliticsent.Calidad.Presentacion;

import java.util.*;

import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

/**
 * Proyecciones explícitas: ningún hash, secreto, ciphertext o entidad se publica por accidente.
 */
public final class QualityViews {
    private QualityViews() {
    }

    public static Map<String, Object> of(Object value) {
        return switch (value) {
            case QualityRule r ->
                    map("id", r.id, "project_id", r.projectId, "asset_id", r.assetId, "name", r.name, "enabled", r.enabled, "revision", r.revision, "created_at", r.createdAt);
            case RuleVersion v ->
                    map("id", v.id, "project_id", v.projectId, "asset_id", v.assetId, "rule_id", v.ruleId, "version_number", v.versionNumber, "dimension", v.dimension, "definition", v.definition, "formula_version", v.formulaVersion, "threshold", v.threshold, "minimum_coverage", v.minimumCoverage, "critical", v.critical, "created_at", v.createdAt);
            case Evaluation e ->
                    map("id", e.id, "project_id", e.projectId, "asset_id", e.assetId, "input_ref_id", e.inputRefId, "state", e.status, "quality_outcome", e.qualityOutcome, "submitted_at", e.createdAt, "started_at", e.startedAt, "finished_at", e.finishedAt, "failure_code", e.failureCode, "request_id", e.requestId, "revision", e.revision, "retry_of", e.retryOf);
            case RuleResult r ->
                    map("id", r.id, "project_id", r.projectId, "asset_id", r.assetId, "evaluation_id", r.evaluationId, "rule_version_id", r.ruleVersionId, "measurement_state", r.measurementState, "candidate_units", r.candidateUnits, "applicable_units", r.applicableUnits, "numerator", r.numerator, "denominator", r.denominator, "metric_value", r.metricValue, "coverage_value", r.coverageValue, "compliance_state", r.complianceState, "threshold", r.threshold, "minimum_coverage", r.minimumCoverage);
            case Incident i ->
                    map("id", i.id, "project_id", i.projectId, "asset_id", i.assetId, "evaluation_id", i.evaluationId, "rule_result_id", i.ruleResultId, "field_name", i.fieldName, "record_reference", i.recordReference, "problem_code", i.problemCode, "severity", i.severity, "status", i.status, "revision", i.revision, "created_at", i.createdAt);
            case IncidentEvent e ->
                    map("id", e.id, "incident_id", e.incidentId, "actor_id", e.actorId, "from_status", e.fromStatus, "to_status", e.toStatus, "created_at", e.createdAt);
            case AuditEvent e ->
                    map("id", e.id, "project_id", e.projectId, "actor_id", e.actorId, "request_id", e.requestId, "action", e.action, "resource_type", e.resourceType, "resource_id", e.resourceId, "outcome", e.outcome, "reason_code", e.reasonCode, "created_at", e.createdAt);
            case RoleAssignment r ->
                    map("id", r.id, "project_id", r.projectId, "user_id", r.userId, "role_name", r.roleName, "asset_scope_id", r.assetScopeId, "created_at", r.createdAt);
            case UserAccount u ->
                    map("id", u.id, "active", u.active, "revision", u.revision, "created_at", u.createdAt);
            default -> throw new IllegalArgumentException("Tipo sin proyección pública.");
        };
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
