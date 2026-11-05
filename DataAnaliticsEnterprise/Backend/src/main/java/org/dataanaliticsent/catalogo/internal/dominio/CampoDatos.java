package org.dataanaliticsent.catalogo.internal.dominio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CampoDatos(UUID id, UUID projectId, UUID assetId, String name, String path, int position,
        String nativeType, TipoLogico logicalType, Boolean nullable, String businessDescription,
        List<String> synonyms, Estado status) {

    public enum TipoLogico { STRING, INTEGER, NUMBER, BOOLEAN, DATE, DATETIME, OBJECT, ARRAY, MIXED, UNKNOWN }
    public enum Estado { ACTIVE, MISSING }

    public CampoDatos {
        Objects.requireNonNull(id); Objects.requireNonNull(projectId); Objects.requireNonNull(assetId);
        Validacion.texto(name, "Nombre de campo", 200); Validacion.texto(path, "Ruta de campo", 1000);
        Objects.requireNonNull(logicalType); Objects.requireNonNull(status);
        if (position < 1) throw new IllegalArgumentException("Posición inválida");
        Validacion.descripcion(businessDescription);
        synonyms = List.copyOf(synonyms);
    }
}
