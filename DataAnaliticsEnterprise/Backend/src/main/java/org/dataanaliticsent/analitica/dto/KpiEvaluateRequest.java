package org.dataanaliticsent.analitica.dto;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KpiEvaluateRequest(UUID assetId, List<KpiCode> kpiCodes, TimeWindow window) {
    public KpiEvaluateRequest {
        if (assetId == null) {
            throw new IllegalArgumentException("asset_id es obligatorio");
        }
        if (kpiCodes == null || kpiCodes.isEmpty() || kpiCodes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("kpi_codes requiere al menos un código válido");
        }
        if (EnumSet.copyOf(kpiCodes).size() != kpiCodes.size()) {
            throw new IllegalArgumentException("kpi_codes no admite duplicados");
        }
        kpiCodes = List.copyOf(kpiCodes);
    }
}
