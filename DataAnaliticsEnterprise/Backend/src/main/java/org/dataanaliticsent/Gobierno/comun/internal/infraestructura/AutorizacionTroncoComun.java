package org.dataanaliticsent.Gobierno.comun.internal.infraestructura;

import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.TroncoComun.autorizacion.AutorizacionApi;
import org.dataanaliticsent.TroncoComun.autorizacion.DecisionAcceso;
import org.dataanaliticsent.TroncoComun.autorizacion.SolicitudAcceso;
import org.springframework.stereotype.Component;

/**
 * Permisos m3.* evaluados por el RBAC del tronco común en ámbito PROJECT: cuenta activa,
 * membresía vigente, rol con el permiso y proyecto activo (o archivado solo para lecturas).
 * Se consulta en cada operación, sin caché de privilegios.
 */
@Component
class AutorizacionTroncoComun implements PuertoAutorizacion {
    private final AutorizacionApi autorizacion;

    AutorizacionTroncoComun(AutorizacionApi autorizacion) { this.autorizacion = autorizacion; }

    @Override
    public void exigir(UUID actorId, String permiso, UUID projectId) {
        DecisionAcceso decision = autorizacion.evaluar(actorId,
                new SolicitudAcceso(permiso, SolicitudAcceso.Ambito.PROJECT, projectId, null, null));
        if (!decision.permitido()) {
            throw new AccesoDenegadoGobiernoException("Acceso denegado: " + decision.motivo());
        }
    }
}
