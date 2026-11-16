package org.dataanaliticsent.analitica.dto;

/** Códigos del contrato M4; el nombre de cada constante es su valor JSON. */
public enum KpiCode {
    DQ_VALIDEZ(KpiCategory.CALIDAD, "ratio"),
    DQ_UNICIDAD(KpiCategory.CALIDAD, "ratio"),
    VOL_FILAS(KpiCategory.VOLUMEN, "filas"),
    VOL_VARIACION(KpiCategory.VOLUMEN, "ratio"),
    COMP_COLUMNA(KpiCategory.COMPLETITUD, "ratio"),
    COMP_ACTIVO(KpiCategory.COMPLETITUD, "ratio"),
    PERF_LATENCIA_LECTURA(KpiCategory.RENDIMIENTO, "ms"),
    PERF_THROUGHPUT(KpiCategory.RENDIMIENTO, "filas/s"),
    TICKET_PROMEDIO(KpiCategory.COMERCIAL, "USD");

    private final KpiCategory category;
    private final String unit;

    KpiCode(KpiCategory category, String unit) {
        this.category = category;
        this.unit = unit;
    }

    public KpiCategory category() {
        return category;
    }

    public String unit() {
        return unit;
    }
}
