package org.dataanaliticsent.identidad.internal.dominio;

import java.nio.charset.StandardCharsets;

public final class PoliticaClave {
    private PoliticaClave() { }
    public static void validarNueva(String clave) {
        if(clave==null || clave.isBlank() || clave.codePointCount(0,clave.length())<15 || clave.getBytes(StandardCharsets.UTF_8).length>72) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 15 caracteres y hasta 72 bytes UTF-8");
        }
    }
}
