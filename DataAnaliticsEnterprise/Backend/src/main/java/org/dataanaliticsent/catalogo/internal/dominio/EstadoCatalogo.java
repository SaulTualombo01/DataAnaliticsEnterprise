package org.dataanaliticsent.catalogo.internal.dominio;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Estado tipado de la primera versión de un activo; no contiene registros empresariales. */
public record EstadoCatalogo(ActivoDatos activo, List<CampoDatos> campos) {
    public EstadoCatalogo {
        Objects.requireNonNull(activo);
        campos = List.copyOf(campos);
        var ids = new HashSet<UUID>();
        var paths = new HashSet<String>();
        var positions = new HashSet<Integer>();
        for (CampoDatos campo : campos) {
            if (!campo.assetId().equals(activo.id()) || !campo.projectId().equals(activo.projectId())) {
                throw new IllegalArgumentException("Campo fuera del activo/proyecto");
            }
            if (!ids.add(campo.id())) throw new IllegalArgumentException("ID de campo duplicado");
            if (campo.status() == CampoDatos.Estado.ACTIVE
                    && (!paths.add(campo.path()) || !positions.add(campo.position()))) {
                throw new IllegalArgumentException("Ruta o posición activa duplicada");
            }
        }
    }
}
