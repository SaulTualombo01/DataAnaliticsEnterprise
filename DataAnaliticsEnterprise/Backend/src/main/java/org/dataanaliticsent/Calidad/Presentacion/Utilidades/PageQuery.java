package org.dataanaliticsent.Calidad.Presentacion.Utilidades;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.invalid;

/** Valida antes de calcular el desplazamiento, incluido el desbordamiento entero. */
public record PageQuery(int page, int size) {
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 1 || size < 1 || size > MAX_SIZE || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw invalid("page empieza en 1 y page_size debe estar entre 1 y 100.");
        }
    }

    public int offset() {
        return (page - 1) * size;
    }

    public PageInfo info(int returned, boolean hasNext) {
        return new PageInfo(page, size, returned, hasNext);
    }

    public PageInfo info(int returned, long total) {
        return new PageInfo(page, size, returned, (long) page * size < total, total);
    }
}
