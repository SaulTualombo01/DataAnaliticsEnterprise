package org.dataanaliticsent.Gobierno.comun.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.Pagina;

/**
 * Parámetros de consulta comunes. Un valor fuera de rango produce 422; nunca se corrige en
 * silencio, como piden las convenciones del equipo.
 */
public final class ParametrosConsulta {
    private ParametrosConsulta() { }

    public static int page(String valor) {
        return entero("page", valor, 1, 1, Integer.MAX_VALUE);
    }

    public static int pageSize(String valor) {
        return entero("page_size", valor, Pagina.PAGE_SIZE_POR_DEFECTO, 1, Pagina.PAGE_SIZE_MAXIMO);
    }

    public static int entero(String nombre, String valor, int porDefecto, int minimo, int maximo) {
        if (valor == null) return porDefecto;
        try {
            int n = Integer.parseInt(valor);
            if (n >= minimo && n <= maximo) return n;
        } catch (NumberFormatException e) {
            // se informa abajo
        }
        throw SolicitudInvalidaException.de("query", nombre, "RANGE",
                "Debe ser un entero entre " + minimo + " y " + maximo);
    }

    public static UUID uuid(String nombre, String valor) {
        if (valor == null) return null;
        try {
            UUID id = UUID.fromString(valor);
            if (id.toString().equals(valor)) return id;
        } catch (IllegalArgumentException e) {
            // se informa abajo
        }
        throw SolicitudInvalidaException.de("query", nombre, "UUID", "Debe ser un UUID en minúsculas");
    }

    public static <E extends Enum<E>> E enumeracion(String nombre, String valor, Class<E> tipo) {
        if (valor == null) return null;
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw SolicitudInvalidaException.de("query", nombre, "ENUM", "Valor no admitido: " + valor);
        }
    }

    /** Instante UTC terminado en Z, por ejemplo 2026-11-17T12:00:00Z. */
    public static Instant instante(String nombre, String valor) {
        if (valor == null) return null;
        try {
            if (valor.endsWith("Z")) return Instant.parse(valor);
        } catch (DateTimeParseException e) {
            // se informa abajo
        }
        throw SolicitudInvalidaException.de("query", nombre, "DATE_TIME", "Debe ser una fecha UTC terminada en Z");
    }
}
