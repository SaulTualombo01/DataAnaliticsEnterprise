package org.dataanaliticsent.analitica.dto;

import java.math.BigDecimal;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KpiResult(
        KpiCode kpiCode,
        KpiCategory category,
        BigDecimal value,
        String unit,
        KpiStatus status,
        BigDecimal thresholdWarning,
        BigDecimal thresholdCritical) {

    public static KpiResult sinUmbrales(KpiCode code, BigDecimal value, KpiStatus status) {
        return new KpiResult(code, code.category(), value, code.unit(), status, null, null);
    }

    public static KpiResult noEvaluable(KpiCode code) {
        return sinUmbrales(code, null, KpiStatus.NOT_EVALUABLE);
    }
}
