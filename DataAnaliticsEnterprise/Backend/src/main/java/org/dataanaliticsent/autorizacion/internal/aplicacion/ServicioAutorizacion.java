package org.dataanaliticsent.autorizacion.internal.aplicacion;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.*;
import org.dataanaliticsent.autorizacion.internal.dominio.*;
import org.springframework.stereotype.Service;

@Service
public class ServicioAutorizacion implements AutorizacionApi {
    private final EvaluadorPermisos evaluador;
    public ServicioAutorizacion(RepositorioPermisos datos, ResolvedorRecursos recursos) {
        evaluador = new EvaluadorPermisos(datos, recursos);
    }
    @Override
    public DecisionAcceso evaluar(UUID actorId, SolicitudAcceso solicitud) { return evaluador.evaluar(actorId, solicitud); }
    @Override
    public List<AlcanceAutorizado> alcancesPermitidos(UUID actorId, String permiso, UUID proyectoId) {
        return evaluador.alcancesPermitidos(actorId, permiso, proyectoId);
    }
}
