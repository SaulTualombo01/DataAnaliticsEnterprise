package org.dataanaliticsent.Calidad.Presentacion.Utilidades;

public record PageInfo(
        int page,
        int pageSize,
        int returnedCount,
        boolean hasNext,
        Long totalCount
) {
    public PageInfo(int page,int pageSize,int returnedCount,boolean hasNext){this(page,pageSize,returnedCount,hasNext,null);}
}
