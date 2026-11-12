package org.dataanaliticsent.Calidad.Infraestructura;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mapeos del esquema m2 inspeccionado; no se generan ni alteran tablas. No son DTO HTTP. */
public final class PersistenceModels {
    private PersistenceModels() {}

    @Entity(name = "UserAccount")
    @Table(name = "users", schema = "m2")
    public static class UserAccount {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "email_ciphertext", columnDefinition = "text")
        public String emailCiphertext;
        @Column(name = "email_lookup_hash")
        public String emailLookupHash;
        @Column(name = "password_hash", columnDefinition = "text")
        public String passwordHash;
        @Column(name = "active")
        public Boolean active;
        @Column(name = "auth_version")
        public Long authVersion;
        @Column(name = "created_at")
        public Instant createdAt;
        @Version
        @Column(name = "revision")
        public Long revision;
    }

    @Entity(name = "Project")
    @Table(name = "projects", schema = "m2")
    public static class Project {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "organization_id")
        public UUID organizationId;
        @Column(name = "name")
        public String name;
        @Column(name = "active")
        public Boolean active;
        @Column(name = "created_at")
        public Instant createdAt;
        @Version
        @Column(name = "revision")
        public Long revision;
    }

    @Entity(name = "RoleAssignment")
    @Table(name = "role_assignments", schema = "m2")
    public static class RoleAssignment {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "user_id")
        public UUID userId;
        @Column(name = "role_name")
        public String roleName;
        @Column(name = "asset_scope_id")
        public UUID assetScopeId;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "PasswordResetToken")
    @Table(name = "password_reset_tokens", schema = "m2")
    public static class PasswordResetToken {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "user_id")
        public UUID userId;
        @Column(name = "token_hash")
        public String tokenHash;
        @Column(name = "expires_at")
        public Instant expiresAt;
        @Column(name = "used_at")
        public Instant usedAt;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "AssetRef")
    @Table(name = "asset_refs", schema = "m2")
    public static class AssetRef {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "source_id")
        public UUID sourceId;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "InputRef")
    @Table(name = "input_refs", schema = "m2")
    public static class InputRef {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "read_id")
        public UUID readId;
        @Column(name = "snapshot_id")
        public UUID snapshotId;
        @Column(name = "metadata_version_id")
        public UUID metadataVersionId;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "scope_definition")
        public JsonNode scopeDefinition;
        @Column(name = "complete")
        public Boolean complete;
        @Column(name = "row_count")
        public Long rowCount;
        @Column(name = "byte_count")
        public Long byteCount;
        @Column(name = "acquired_at")
        public Instant acquiredAt;
        @Column(name = "expires_at")
        public Instant expiresAt;
        @Column(name = "content_sha256")
        public String contentSha256;
        @Column(name = "protected_object_ref")
        public String protectedObjectRef;
        @Column(name = "availability")
        public String availability;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "QualityRule")
    @Table(name = "quality_rules", schema = "m2")
    public static class QualityRule {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "name")
        public String name;
        @Column(name = "enabled")
        public Boolean enabled;
        @Column(name = "created_by")
        public UUID createdBy;
        @Column(name = "created_at")
        public Instant createdAt;
        @Version
        @Column(name = "revision")
        public Long revision;
    }

    @Entity(name = "RuleVersion")
    @Table(name = "rule_versions", schema = "m2")
    @Immutable
    public static class RuleVersion {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "rule_id")
        public UUID ruleId;
        @Column(name = "version_number")
        public Integer versionNumber;
        @Column(name = "dimension")
        public String dimension;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "definition")
        public JsonNode definition;
        @Column(name = "formula_version")
        public String formulaVersion;
        @Column(name = "threshold")
        public BigDecimal threshold;
        @Column(name = "minimum_coverage")
        public BigDecimal minimumCoverage;
        @Column(name = "critical")
        public Boolean critical;
        @Column(name = "created_by")
        public UUID createdBy;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "Evaluation")
    @Table(name = "evaluations", schema = "m2")
    public static class Evaluation {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "input_ref_id")
        public UUID inputRefId;
        @Column(name = "requested_by")
        public UUID requestedBy;
        @Column(name = "status")
        public String status;
        @Column(name = "quality_outcome")
        public String qualityOutcome;
        @Column(name = "request_id")
        public UUID requestId;
        @Column(name = "idempotency_key")
        public UUID idempotencyKey;
        @Column(name = "request_sha256")
        public String requestSha256;
        @Column(name = "started_at")
        public Instant startedAt;
        @Column(name = "finished_at")
        public Instant finishedAt;
        @Column(name = "failure_code")
        public String failureCode;
        @Column(name = "lease_until")
        public Instant leaseUntil;
        @Column(name = "retry_of")
        public UUID retryOf;
        @Column(name = "created_at")
        public Instant createdAt;
        @Version
        @Column(name = "revision")
        public Long revision;
    }

    @Entity(name = "EvaluationRule")
    @Table(name = "evaluation_rules", schema = "m2")
    @Immutable
    public static class EvaluationRule {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "evaluation_id")
        public UUID evaluationId;
        @Column(name = "rule_version_id")
        public UUID ruleVersionId;
    }

    @Entity(name = "RuleResult")
    @Table(name = "rule_results", schema = "m2")
    @Immutable
    public static class RuleResult {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "evaluation_id")
        public UUID evaluationId;
        @Column(name = "rule_version_id")
        public UUID ruleVersionId;
        @Column(name = "measurement_state")
        public String measurementState;
        @Column(name = "candidate_units")
        public Long candidateUnits;
        @Column(name = "applicable_units")
        public Long applicableUnits;
        @Column(name = "numerator")
        public Long numerator;
        @Column(name = "denominator")
        public Long denominator;
        @Column(name = "threshold")
        public BigDecimal threshold;
        @Column(name = "minimum_coverage")
        public BigDecimal minimumCoverage;
        @Column(name = "metric_value", insertable = false, updatable = false)
        public BigDecimal metricValue;
        @Column(name = "coverage_value", insertable = false, updatable = false)
        public BigDecimal coverageValue;
        @Column(name = "compliance_state", insertable = false, updatable = false)
        public String complianceState;
        @Column(name = "exclusion_reason")
        public String exclusionReason;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "Incident")
    @Table(name = "incidents", schema = "m2")
    public static class Incident {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "evaluation_id")
        public UUID evaluationId;
        @Column(name = "rule_result_id")
        public UUID ruleResultId;
        @Column(name = "field_name")
        public String fieldName;
        @Column(name = "record_reference")
        public String recordReference;
        @Column(name = "problem_code")
        public String problemCode;
        @Column(name = "severity")
        public String severity;
        @Column(name = "status")
        public String status;
        @Column(name = "evidence_ciphertext", columnDefinition = "text")
        public String evidenceCiphertext;
        @Column(name = "created_at")
        public Instant createdAt;
        @Version
        @Column(name = "revision")
        public Long revision;
    }

    @Entity(name = "IncidentEvent")
    @Table(name = "incident_events", schema = "m2")
    @Immutable
    public static class IncidentEvent {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "asset_id")
        public UUID assetId;
        @Column(name = "incident_id")
        public UUID incidentId;
        @Column(name = "actor_id")
        public UUID actorId;
        @Column(name = "from_status")
        public String fromStatus;
        @Column(name = "to_status")
        public String toStatus;
        @Column(name = "comment_ciphertext", columnDefinition = "text")
        public String commentCiphertext;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "AuditEvent")
    @Table(name = "audit_events", schema = "m2")
    @Immutable
    public static class AuditEvent {
        @Id
        @Column(name = "id")
        public UUID id;
        @Column(name = "project_id")
        public UUID projectId;
        @Column(name = "actor_id")
        public UUID actorId;
        @Column(name = "request_id")
        public UUID requestId;
        @Column(name = "action")
        public String action;
        @Column(name = "resource_type")
        public String resourceType;
        @Column(name = "resource_id")
        public UUID resourceId;
        @Column(name = "outcome")
        public String outcome;
        @Column(name = "reason_code")
        public String reasonCode;
        @Column(name = "created_at")
        public Instant createdAt;
    }
}

