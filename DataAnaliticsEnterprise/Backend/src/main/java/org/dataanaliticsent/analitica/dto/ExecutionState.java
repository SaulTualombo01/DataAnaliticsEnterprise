package org.dataanaliticsent.analitica.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** PARTIAL indica una lectura incompleta: no debe presentarse como evaluación final. */
public enum ExecutionState {
    COMPLETED, PARTIAL, EMPTY;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
