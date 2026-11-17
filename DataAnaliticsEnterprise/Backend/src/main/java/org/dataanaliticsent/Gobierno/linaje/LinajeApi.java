package org.dataanaliticsent.Gobierno.linaje;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Linaje de datos (RF-M3.5): relaciones conocidas de procedencia entre activos del catálogo.
 * Se registran de forma explícita; no se infieren por semejanza de nombres. Cada alta y retiro
 * se audita y se versiona. El grafo no admite ciclos.
 */
public interface LinajeApi {

    /** Cómo llegan los datos del origen al destino. */
    enum TipoRelacion { COPIA, TRANSFORMA, AGREGA, ALIMENTA }

    enum EstadoRelacion { ACTIVE, RETIRED }

    /** ORIGENES: de dónde viene el activo. DESTINOS: a qué activos alimenta. */
    enum Direccion { ORIGENES, DESTINOS, AMBOS }

    record Relacion(UUID id, UUID projectId, UUID originAssetId, UUID targetAssetId, TipoRelacion tipo,
            String descripcion, EstadoRelacion estado, UUID currentVersionId, Instant createdAt, UUID createdBy,
            Instant retiredAt, UUID retiredBy) { }

    record NuevaRelacion(UUID projectId, UUID originAssetId, UUID targetAssetId, TipoRelacion tipo,
            String descripcion, String reason) {
        public NuevaRelacion {
            Objects.requireNonNull(projectId, "projectId obligatorio");
            Objects.requireNonNull(originAssetId, "originAssetId obligatorio");
            Objects.requireNonNull(targetAssetId, "targetAssetId obligatorio");
            Objects.requireNonNull(tipo, "tipo obligatorio");
        }
    }

    /** Activo del recorrido. distancia 0 es el consultado; negativa, sus orígenes (-1 directo); positiva, sus destinos. */
    record Nodo(UUID assetId, int distancia) { }

    /** truncado=true si se alcanzó el límite de nodos antes de terminar el recorrido. */
    record Grafo(UUID assetId, Direccion direccion, int profundidad, List<Nodo> nodos, List<Relacion> relaciones,
            boolean truncado) {
        public Grafo { nodos = List.copyOf(nodos); relaciones = List.copyOf(relaciones); }
    }

    /**
     * Exige m3.lineage.manage. Ambos activos deben existir en el catálogo del proyecto. Registrar
     * una relación igual a una vigente la devuelve sin duplicarla; una que cree un ciclo se rechaza.
     */
    Relacion registrar(NuevaRelacion nueva);

    /** Exige m3.lineage.manage. La relación queda RETIRED y se conserva en el historial. */
    Relacion retirar(UUID projectId, UUID relationId, String reason);

    /** Exige m3.lineage.read. */
    Optional<Relacion> consultar(UUID projectId, UUID relationId);

    /** Exige m3.lineage.read. Recorre solo relaciones vigentes, con profundidad de 1 a 5. */
    Grafo grafo(UUID projectId, UUID assetId, Direccion direccion, int profundidad);
}
