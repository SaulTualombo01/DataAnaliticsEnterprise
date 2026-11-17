package org.dataanaliticsent.Gobierno.comun.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.dataanaliticsent.Gobierno.comun.web.SolicitudInvalidaException.ErrorCampo;

/**
 * Lectura validada de un cuerpo JSON. Rechaza campos desconocidos, acumula todos los errores
 * y los informa juntos (422) en lugar de detenerse en el primero.
 */
public final class CuerpoJson {
    private static final Pattern UUID_CANONICO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Map<String, Object> cuerpo;
    private final List<ErrorCampo> errores = new ArrayList<>();

    private CuerpoJson(Map<String, Object> cuerpo) { this.cuerpo = cuerpo; }

    /** null equivale a un objeto vacío; los campos fuera de permitidos se rechazan. */
    public static CuerpoJson de(Map<String, Object> cuerpo, Set<String> permitidos) {
        CuerpoJson c = new CuerpoJson(cuerpo == null ? Map.of() : cuerpo);
        for (String campo : c.cuerpo.keySet()) {
            if (!permitidos.contains(campo)) c.error(campo, "UNKNOWN_FIELD", "Campo no admitido");
        }
        return c;
    }

    public boolean contiene(String campo) { return cuerpo.containsKey(campo); }

    /** Texto; vacío o ausente es error si obligatorio. Un null explícito se devuelve como null. */
    public String texto(String campo, boolean obligatorio) {
        Object valor = cuerpo.get(campo);
        if (valor == null) {
            if (obligatorio) error(campo, "REQUIRED", "Campo obligatorio");
            return null;
        }
        if (!(valor instanceof String s)) {
            error(campo, "TYPE", "Debe ser texto");
            return null;
        }
        if (obligatorio && s.isBlank()) error(campo, "REQUIRED", "Campo obligatorio");
        return s;
    }

    public UUID uuid(String campo, boolean obligatorio) {
        String texto = texto(campo, obligatorio);
        if (texto == null) return null;
        if (!UUID_CANONICO.matcher(texto).matches()) {
            error(campo, "UUID", "Debe ser un UUID en minúsculas");
            return null;
        }
        return UUID.fromString(texto);
    }

    public <E extends Enum<E>> E enumeracion(String campo, Class<E> tipo, boolean obligatorio) {
        String texto = texto(campo, obligatorio);
        if (texto == null) return null;
        try {
            return Enum.valueOf(tipo, texto);
        } catch (IllegalArgumentException e) {
            error(campo, "ENUM", "Valor no admitido: " + texto);
            return null;
        }
    }

    /** Lanza SolicitudInvalidaException si hubo algún error. */
    public CuerpoJson validar() {
        if (!errores.isEmpty()) throw new SolicitudInvalidaException(errores);
        return this;
    }

    private void error(String campo, String codigo, String mensaje) {
        errores.add(new ErrorCampo("body", "/" + campo, codigo, mensaje));
    }
}
