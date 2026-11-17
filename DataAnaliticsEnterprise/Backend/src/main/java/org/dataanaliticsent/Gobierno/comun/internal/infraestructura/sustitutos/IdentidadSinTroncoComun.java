package org.dataanaliticsent.Gobierno.comun.internal.infraestructura.sustitutos;

import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.springframework.stereotype.Component;

/**
 * TEMPORAL mientras la rama no integra el tronco común: no hay identidad verificada, así
 * que toda operación protegida se rechaza. Al integrar, reemplazar este archivo por un
 * adaptador sobre IdentidadActualApi.
 */
@Component
class IdentidadSinTroncoComun implements PuertoIdentidad {
    @Override
    public UUID actorActual() {
        throw new AccesoDenegadoGobiernoException("Identidad no disponible: falta integrar el tronco común");
    }
}
