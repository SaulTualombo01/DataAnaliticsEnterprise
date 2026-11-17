package org.dataanaliticsent.Gobierno.comun.internal.infraestructura.sustitutos;

import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.springframework.stereotype.Component;

/**
 * TEMPORAL mientras la rama no integra el tronco común: deniega todo por defecto, nunca
 * concede acceso. Al integrar, reemplazar este archivo por un adaptador sobre AutorizacionApi.
 */
@Component
class AutorizacionSinTroncoComun implements PuertoAutorizacion {
    @Override
    public void exigir(UUID actorId, String permiso, UUID projectId) {
        throw new AccesoDenegadoGobiernoException("Autorización no disponible: falta integrar el tronco común");
    }
}
