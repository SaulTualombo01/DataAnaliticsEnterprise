package org.dataanaliticsent.contexto.internal.aplicacion;

import java.util.UUID;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.springframework.stereotype.Component;

/** Exige identidad verificada y un permiso de plataforma antes de cualquier lectura o cambio. */
@Component
public class GuardiaPlataforma {
    private final IdentidadActualApi identidad;
    private final AutorizacionApi autorizacion;

    public GuardiaPlataforma(IdentidadActualApi identidad, AutorizacionApi autorizacion) {
        this.identidad = identidad;
        this.autorizacion = autorizacion;
    }

    /** Devuelve el actor autorizado. */
    public UUID exigir(String permiso) {
        UUID actorId = identidad.usuarioId();
        autorizacion.exigir(actorId, SolicitudAcceso.plataforma(permiso));
        return actorId;
    }
}
