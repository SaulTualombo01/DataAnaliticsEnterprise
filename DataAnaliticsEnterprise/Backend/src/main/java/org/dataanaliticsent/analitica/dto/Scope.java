package org.dataanaliticsent.analitica.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Scope(boolean complete, long rowsEvaluated, String reason) {
}
