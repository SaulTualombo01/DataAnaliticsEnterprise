package org.dataanaliticsent.Gobierno.comun.internal.infraestructura.sustitutos;

import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoCalidad;
import org.springframework.stereotype.Component;

/**
 * TEMPORAL mientras la rama no integra M2: no hay resultados de calidad, así que las reglas
 * CALIDAD_APROBADA quedan INDETERMINATE (nunca se inventa una aprobación). Al integrar,
 * reemplazar este archivo por un adaptador sobre la API de M2.
 */
@Component
class CalidadSinModulo2 implements PuertoCalidad {
    @Override
    public Optional<EstadoCalidad> estadoCalidad(UUID projectId, UUID assetId) {
        return Optional.empty();
    }
}
