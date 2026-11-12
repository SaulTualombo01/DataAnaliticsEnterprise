package org.dataanaliticsent.Calidad.Aplicacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.dataanaliticsent.Calidad.Dominio.QualityEngine;

import java.math.BigDecimal;
import java.util.*;

public final class QualityContracts {
    private QualityContracts() {
    }

    public record Definition(@NotNull QualityEngine.Kind kind, @NotEmpty @Size(max = 20) List<@NotBlank String> fields,
                             Boolean blankIsMissing, BigDecimal minimum, BigDecimal maximum, Set<String> allowedValues,
                             String referenceId, String referenceVersion, String referenceKey, String referenceField,
                             BigDecimal tolerance, Long maxAgeSeconds) {
    }

    public record VersionCommand(@NotNull QualityEngine.Dimension dimension, @NotNull @Valid Definition definition,
                                 @DecimalMin("0") @DecimalMax("100") BigDecimal threshold,
                                 @DecimalMin("0") @DecimalMax("100") BigDecimal minimumCoverage, boolean critical) {
    }

    public record CreateRule(@NotNull UUID assetId, @NotBlank @Size(max = 160) String name,
                             @NotNull @Valid VersionCommand version) {
    }

    public record CreateEvaluation(@NotNull UUID assetId, @NotNull UUID metadataVersionId,
                                   @NotEmpty @Size(max = 20) List<@NotNull UUID> ruleIds, UUID snapshotId,
                                   @Size(max=20) Map<@NotNull UUID,@NotNull UUID> expectedRuleVersions) {
        public CreateEvaluation(UUID assetId,UUID metadataVersionId,List<UUID> ruleIds,UUID snapshotId){this(assetId,metadataVersionId,ruleIds,snapshotId,null);}
    }

    public record FollowUp(@NotBlank String status, @Size(max = 2000) String comment) {
    }
}
