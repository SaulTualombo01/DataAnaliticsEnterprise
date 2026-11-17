package org.dataanaliticsent.Gobierno.linaje.internal.dominio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;

public interface RepositorioLinaje {
    /** Serializa los cambios de linaje de un proyecto dentro de la transacción en curso. */
    void bloquearProyecto(UUID projectId);

    Optional<Relacion> buscar(UUID projectId, UUID relationId);

    Optional<Relacion> vigenteIgual(UUID projectId, UUID originAssetId, UUID targetAssetId, TipoRelacion tipo);

    void insertar(Relacion relacion);

    /** Marca como retirada; lanza ConflictoLinajeException si ya no estaba vigente. */
    void retirar(Relacion retirada);

    /** Relaciones vigentes cuyo destino está en activos (para subir hacia los orígenes). */
    List<Relacion> entrantes(UUID projectId, Collection<UUID> activos);

    /** Relaciones vigentes cuyo origen está en activos (para bajar hacia los destinos). */
    List<Relacion> salientes(UUID projectId, Collection<UUID> activos);
}
