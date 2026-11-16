package org.dataanaliticsent.contexto.internal.dominio;

public record Pagina(int limit, int offset) {
    public static final int LIMITE_MAXIMO = 100;

    public Pagina {
        if (limit < 1 || limit > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("limit debe estar entre 1 y " + LIMITE_MAXIMO);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset no puede ser negativo");
        }
    }
}
