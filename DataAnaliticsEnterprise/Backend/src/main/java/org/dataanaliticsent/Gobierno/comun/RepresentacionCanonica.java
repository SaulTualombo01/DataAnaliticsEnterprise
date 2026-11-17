package org.dataanaliticsent.Gobierno.comun;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.TemporalAccessor;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * JSON canónico para versiones y detalles de auditoría: claves ordenadas, sin espacios y
 * UTF-8. Dos estados iguales producen siempre el mismo texto y el mismo hash SHA-256,
 * sin depender del orden en que el llamador construyó el mapa.
 */
public final class RepresentacionCanonica {
    private static final int PROFUNDIDAD_MAXIMA = 32;

    private RepresentacionCanonica() { }

    public static String json(Map<String, ?> objeto) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, objeto, 0);
        return sb.toString();
    }

    public static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static void escribir(StringBuilder sb, Object valor, int profundidad) {
        if (profundidad > PROFUNDIDAD_MAXIMA) throw new IllegalArgumentException("Estado demasiado anidado");
        switch (valor) {
            case null -> sb.append("null");
            case Boolean b -> sb.append(b);
            case Integer i -> sb.append(i);
            case Long l -> sb.append(l);
            case Short s -> sb.append(s);
            case BigInteger bi -> sb.append(bi);
            case BigDecimal bd -> sb.append(bd.stripTrailingZeros().toPlainString());
            case Double d -> sb.append(finito(d));
            case Float f -> sb.append(finito(f.doubleValue()));
            case String s -> texto(sb, s);
            case UUID u -> texto(sb, u.toString());
            case Enum<?> e -> texto(sb, e.name());
            case TemporalAccessor t -> texto(sb, t.toString());
            case Map<?, ?> m -> objeto(sb, m, profundidad);
            case List<?> l -> {
                sb.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(',');
                    escribir(sb, l.get(i), profundidad + 1);
                }
                sb.append(']');
            }
            default -> throw new IllegalArgumentException("Tipo no admitido en el estado: " + valor.getClass().getSimpleName());
        }
    }

    private static void objeto(StringBuilder sb, Map<?, ?> m, int profundidad) {
        TreeMap<String, Object> ordenado = new TreeMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!(e.getKey() instanceof String clave)) throw new IllegalArgumentException("Las claves deben ser texto");
            ordenado.put(clave, e.getValue());
        }
        sb.append('{');
        boolean primero = true;
        for (Map.Entry<String, Object> e : ordenado.entrySet()) {
            if (!primero) sb.append(',');
            primero = false;
            texto(sb, e.getKey());
            sb.append(':');
            escribir(sb, e.getValue(), profundidad + 1);
        }
        sb.append('}');
    }

    private static String finito(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) throw new IllegalArgumentException("Número no finito");
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private static void texto(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
