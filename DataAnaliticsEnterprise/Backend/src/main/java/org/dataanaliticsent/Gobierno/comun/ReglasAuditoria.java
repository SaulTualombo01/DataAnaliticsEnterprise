package org.dataanaliticsent.Gobierno.comun;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Validaciones comunes de códigos y de detalles seguros de auditoría y versiones. */
public final class ReglasAuditoria {
    private static final Pattern TIPO_RECURSO = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Pattern ACCION = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");
    private static final List<String> CLAVES_PROHIBIDAS =
            List.of("password", "contrasena", "clave", "secret", "token", "credential", "credencial");
    private static final int LONGITUD_MAXIMA = 200;

    private ReglasAuditoria() { }

    public static void validarTipoRecurso(String tipo) {
        if (tipo.length() > LONGITUD_MAXIMA || !TIPO_RECURSO.matcher(tipo).matches()) {
            throw new IllegalArgumentException("resourceType inválido: use minúsculas separadas por puntos");
        }
    }

    public static void validarAccion(String accion) {
        if (accion.length() > LONGITUD_MAXIMA || !ACCION.matcher(accion).matches()) {
            throw new IllegalArgumentException("action inválida: use un código como m3.policies.create");
        }
    }

    /** Rechaza claves que sugieren secretos, en cualquier nivel del mapa. */
    public static void validarSinSecretos(Map<?, ?> datos) {
        for (Map.Entry<?, ?> e : datos.entrySet()) {
            String clave = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            for (String prohibida : CLAVES_PROHIBIDAS) {
                if (clave.contains(prohibida)) {
                    throw new IllegalArgumentException("No se registran secretos: clave '" + e.getKey() + "'");
                }
            }
            if (e.getValue() instanceof Map<?, ?> anidado) validarSinSecretos(anidado);
            if (e.getValue() instanceof List<?> lista) {
                for (Object item : lista) if (item instanceof Map<?, ?> m) validarSinSecretos(m);
            }
        }
    }
}
