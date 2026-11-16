package org.dataanaliticsent.TroncoComun.autorizacion;

import java.util.List;
import java.util.UUID;

/** API Java interna. actorId debe proceder del mecanismo de identidad verificado en servidor. */
public interface AutorizacionApi {
    DecisionAcceso evaluar(UUID actorId, SolicitudAcceso solicitud);
    List<AlcanceAutorizado> alcancesPermitidos(UUID actorId, String permiso, UUID proyectoId);

    default void exigir(UUID actorId, SolicitudAcceso solicitud) {
        DecisionAcceso decision = evaluar(actorId, solicitud);
        if (!decision.permitido()) throw new AccesoDenegadoException(decision.motivo());
    }
}
