package org.dataanaliticsent.analitica.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** {@code readId} es null mientras TC13 no esté integrado y los valores sean sintéticos. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KpiEvaluateResponse(
        UUID evaluationId,
        UUID readId,
        UUID assetId,
        ExecutionState executionState,
        Scope scope,
        OffsetDateTime evaluatedAt,
        List<KpiResult> results) {
}
