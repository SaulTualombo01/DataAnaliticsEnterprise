package org.dataanaliticsent.Gobierno.comun;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta a M2 del estado de aprobación de calidad de un activo. M3 no calcula calidad: solo
 * consume el resultado publicado. Al integrar, lo implementa un adaptador sobre la API de M2.
 */
public interface PuertoCalidad {
    enum EstadoCalidad { APROBADO, NO_APROBADO }

    /** Vacío si M2 todavía no evaluó el activo o no hay resultado vigente. */
    Optional<EstadoCalidad> estadoCalidad(UUID projectId, UUID assetId);
}
