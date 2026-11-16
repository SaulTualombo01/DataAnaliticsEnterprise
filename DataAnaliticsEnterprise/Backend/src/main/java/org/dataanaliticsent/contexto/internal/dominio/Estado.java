package org.dataanaliticsent.contexto.internal.dominio;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** El valor en minúsculas es tanto el JSON del contrato como el texto de la columna status. */
public enum Estado {
    ACTIVE, INACTIVE;

    @JsonValue
    public String valor() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Estado de(String valor) {
        return valueOf(valor.toUpperCase(Locale.ROOT));
    }
}
