package org.dataanaliticsent.analitica.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum KpiCategory {
    CALIDAD, VOLUMEN, COMPLETITUD, RENDIMIENTO, COMERCIAL;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
