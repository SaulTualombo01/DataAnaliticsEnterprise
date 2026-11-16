package org.dataanaliticsent.TroncoComun.autorizacion.internal.dominio;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.TroncoComun.autorizacion.ProveedorRecursosAutorizacion.Jerarquia;

public final class EvaluadorPermisos implements AutorizacionApi {
    private static final Set<String> SELF = Set.of("tc.self.profile.read", "tc.self.credentials.change",
            "tc.self.sessions.close", "tc.self.permissions.read", "tc.self.contexts.read");
    private final RepositorioPermisos datos;
    private final ResolvedorRecursos recursos;

    public EvaluadorPermisos(RepositorioPermisos datos, ResolvedorRecursos recursos) {
        this.datos = Objects.requireNonNull(datos);
        this.recursos = Objects.requireNonNull(recursos);
    }

    @Override
    public DecisionAcceso evaluar(UUID actorId, SolicitudAcceso solicitud) {
        Objects.requireNonNull(solicitud);
        DecisionAcceso inicial = validarBase(actorId, solicitud);
        if (!inicial.permitido()) return inicial;
        return switch (solicitud.ambito()) {
            case SELF -> decision(SELF.contains(solicitud.permiso()) && actorId.equals(solicitud.titularId())
                    ? Motivo.PERMITIDO : Motivo.TITULAR_INCORRECTO);
            case PLATFORM -> decision(datos.permisoPlataforma(actorId, solicitud.permiso())
                    ? Motivo.PERMITIDO : Motivo.PERMISO_NO_CONCEDIDO);
            case PROJECT -> evaluarProyecto(actorId, solicitud);
            case ORGANIZATION -> decision(datos.permisoOrganizacion(actorId,solicitud.permiso(),solicitud.organizacionId())
                    ? Motivo.PERMITIDO : Motivo.CONTEXTO_NO_AUTORIZADO);
        };
    }

    private DecisionAcceso validarBase(UUID actorId, SolicitudAcceso solicitud) {
        if (actorId == null) return decision(Motivo.SIN_IDENTIDAD);
        if (!datos.cuentaActiva(actorId)) return decision(Motivo.CUENTA_NO_ACTIVA);
        var permiso = datos.buscarPermiso(solicitud.permiso());
        if (permiso.isEmpty()) return decision(Motivo.PERMISO_DESCONOCIDO);
        if (!permiso.get().disponible()) return decision(Motivo.PERMISO_NO_DISPONIBLE);
        if (permiso.get().ambito() != solicitud.ambito()) return decision(Motivo.AMBITO_INCORRECTO);
        return decision(Motivo.PERMITIDO);
    }

    private DecisionAcceso evaluarProyecto(UUID actorId, SolicitudAcceso solicitud) {
        if (!datos.contextoVigente(actorId, solicitud.proyectoId())) return decision(Motivo.CONTEXTO_NO_AUTORIZADO);
        var alcances = datos.alcances(actorId, solicitud.permiso(), solicitud.proyectoId());
        if (alcances.isEmpty()) return decision(Motivo.PERMISO_NO_CONCEDIDO);
        if (solicitud.recurso() == null) {
            return decision(alcances.stream().anyMatch(a -> a.proyectoId().equals(solicitud.proyectoId())
                    && a.tipo() == AlcanceAutorizado.Tipo.PROJECT) ? Motivo.PERMITIDO : Motivo.ALCANCE_INSUFICIENTE);
        }
        var jerarquia = recursos.resolver(solicitud.recurso())
                .filter(j -> j.proyectoId().equals(solicitud.proyectoId()) && j.corresponde(solicitud.recurso()));
        if (jerarquia.isEmpty()) return decision(Motivo.RECURSO_NO_VALIDADO);
        return decision(alcances.stream().anyMatch(a -> cubre(a, solicitud, jerarquia.get()))
                ? Motivo.PERMITIDO : Motivo.ALCANCE_INSUFICIENTE);
    }

    private boolean cubre(AlcanceAutorizado alcance, SolicitudAcceso solicitud, Jerarquia jerarquia) {
        if (!alcance.proyectoId().equals(solicitud.proyectoId())) return false;
        return switch (alcance.tipo()) {
            case PROJECT -> true;
            case SOURCE -> alcance.recursoId().equals(jerarquia.fuenteId());
            case ASSET -> solicitud.recurso().tipo() == SolicitudAcceso.TipoRecurso.ASSET
                    && alcance.recursoId().equals(jerarquia.activoId());
        };
    }

    @Override
    public List<AlcanceAutorizado> alcancesPermitidos(UUID actorId, String permiso, UUID proyectoId) {
        var solicitud = SolicitudAcceso.proyecto(permiso, proyectoId);
        if (!validarBase(actorId, solicitud).permitido() || !datos.contextoVigente(actorId, proyectoId)) return List.of();
        return datos.alcances(actorId, permiso, proyectoId).stream()
                .filter(a -> a.proyectoId().equals(proyectoId)).filter(this::alcanceResuelto).distinct().toList();
    }

    private boolean alcanceResuelto(AlcanceAutorizado alcance) {
        if (alcance.tipo() == AlcanceAutorizado.Tipo.PROJECT) return true;
        var tipo = alcance.tipo() == AlcanceAutorizado.Tipo.SOURCE ? SolicitudAcceso.TipoRecurso.SOURCE : SolicitudAcceso.TipoRecurso.ASSET;
        var recurso = new SolicitudAcceso.Recurso(tipo, alcance.recursoId());
        return recursos.resolver(recurso).filter(j -> j.proyectoId().equals(alcance.proyectoId()) && j.corresponde(recurso)).isPresent();
    }

    private static DecisionAcceso decision(Motivo motivo) { return new DecisionAcceso(motivo); }
}
