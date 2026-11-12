package org.dataanaliticsent.catalogo.internal.dominio;

import java.util.List;

public interface ExtractorPostgres {
    List<Tabla> extraer(FuenteDatos fuente, List<String> schemas);
    record Columna(String name, int position, String nativeType, CampoDatos.TipoLogico logicalType, boolean nullable) { }
    record MiembroClave(int position, String sourceFieldName, String targetFieldName) { }
    record Clave(String name, RestriccionDatos.Tipo kind, LocalizadorTabla target,
            boolean targetAccessible, List<MiembroClave> members) {
        public Clave { members = List.copyOf(members); }
    }
    record Tabla(LocalizadorTabla locator, List<Columna> columns, List<Clave> keys) {
        public Tabla { columns = List.copyOf(columns); keys = List.copyOf(keys); }
    }
}
