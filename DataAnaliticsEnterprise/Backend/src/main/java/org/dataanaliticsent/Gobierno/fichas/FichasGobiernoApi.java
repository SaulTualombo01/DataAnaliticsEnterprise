package org.dataanaliticsent.Gobierno.fichas;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ficha de gobierno de un activo del catálogo: responsable del dato (propietario de negocio) y
 * clasificación formal. Según las decisiones del equipo, estos datos son de M3; M1 conserva la
 * identidad, la estructura y las etiquetas descriptivas. Cada cambio se audita y se versiona.
 */
public interface FichasGobiernoApi {

    /** Ordenada de menor a mayor sensibilidad. */
    enum Clasificacion {
        PUBLICA, INTERNA, CONFIDENCIAL, RESTRINGIDA;

        public boolean alMenos(Clasificacion minima) { return compareTo(minima) >= 0; }
    }

    record Ficha(UUID projectId, UUID assetId, UUID ownerId, Clasificacion classification, long revision,
            UUID currentVersionId, Instant updatedAt, UUID updatedBy) { }

    /**
     * Datos a guardar. ownerId y classification pueden quedar en null (sin definir).
     * expectedRevision null indica que la ficha todavía no existe.
     */
    record DatosFicha(UUID projectId, UUID assetId, Long expectedRevision, UUID ownerId,
            Clasificacion classification, String reason) {
        public DatosFicha {
            Objects.requireNonNull(projectId, "projectId obligatorio");
            Objects.requireNonNull(assetId, "assetId obligatorio");
        }
    }

    /** Exige m3.asset_profiles.read. */
    Optional<Ficha> consultar(UUID projectId, UUID assetId);

    /** Exige m3.asset_profiles.update. El activo debe existir en el catálogo del proyecto. */
    Ficha guardar(DatosFicha datos);
}
