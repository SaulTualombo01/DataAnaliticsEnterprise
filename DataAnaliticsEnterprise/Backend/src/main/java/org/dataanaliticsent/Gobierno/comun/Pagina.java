package org.dataanaliticsent.Gobierno.comun;

import java.util.List;

/** Página de resultados: page empieza en 1; has_next sin total inventado. */
public record Pagina<T>(List<T> items, int page, int pageSize, boolean hasNext) {
    public static final int PAGE_SIZE_POR_DEFECTO = 25;
    public static final int PAGE_SIZE_MAXIMO = 100;

    public Pagina {
        items = List.copyOf(items);
    }

    /** Rechaza valores fuera de rango en vez de corregirlos en silencio. */
    public static void validar(int page, int pageSize) {
        if (page < 1) throw new IllegalArgumentException("page debe ser mayor o igual a 1");
        if (pageSize < 1 || pageSize > PAGE_SIZE_MAXIMO) {
            throw new IllegalArgumentException("page_size debe estar entre 1 y " + PAGE_SIZE_MAXIMO);
        }
    }
}
