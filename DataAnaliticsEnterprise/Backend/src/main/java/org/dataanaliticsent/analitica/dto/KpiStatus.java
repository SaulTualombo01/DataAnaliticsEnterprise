package org.dataanaliticsent.analitica.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum KpiStatus {
    OK, WARNING, CRITICAL, NOT_EVALUABLE;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
