package org.dataanaliticsent.Gobierno.comun.internal.infraestructura;

import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.springframework.stereotype.Component;

/**
 * Actor de la sesión verificada por el tronco común (cookie TICSESSION). Sin sesión válida,
 * IdentidadActualApi lanza IdentidadNoVerificadaException, que M3 responde con 401.
 */
@Component
class IdentidadTroncoComun implements PuertoIdentidad {
    private final IdentidadActualApi identidad;

    IdentidadTroncoComun(IdentidadActualApi identidad) { this.identidad = identidad; }

    @Override
    public UUID actorActual() {
        return identidad.usuarioId();
    }
}
