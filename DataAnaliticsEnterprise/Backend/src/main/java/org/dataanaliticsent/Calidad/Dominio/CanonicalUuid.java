package org.dataanaliticsent.Calidad.Dominio;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CanonicalUuid {
    private static final Pattern PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private CanonicalUuid() {}

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches();
    }

    public static UUID parse(String value) {
        if (!isValid(value)) throw new IllegalArgumentException("El identificador debe ser un UUID en minúsculas.");
        return UUID.fromString(value);
    }
}
