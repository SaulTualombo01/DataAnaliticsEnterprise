package org.dataanaliticsent.contexto.internal.dominio;

/** Misma regla que el CHECK de las columnas name: entre 1 y 200 caracteres tras recortar espacios. */
public final class NombreContexto {
    public static final int LONGITUD_MAXIMA = 200;

    private NombreContexto() {
    }

    public static String normalizar(String nombre) {
        if (nombre == null) {
            throw new IllegalArgumentException("name es obligatorio");
        }
        String limpio = nombre.strip();
        if (limpio.isEmpty() || limpio.length() > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException("name debe tener entre 1 y " + LONGITUD_MAXIMA + " caracteres");
        }
        return limpio;
    }
}
