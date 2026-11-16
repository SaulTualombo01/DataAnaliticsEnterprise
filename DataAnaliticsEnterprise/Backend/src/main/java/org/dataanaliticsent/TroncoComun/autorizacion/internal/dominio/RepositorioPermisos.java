package org.dataanaliticsent.TroncoComun.autorizacion.internal.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.autorizacion.AlcanceAutorizado;
import org.dataanaliticsent.TroncoComun.autorizacion.SolicitudAcceso;

public interface RepositorioPermisos {
    record Permiso(SolicitudAcceso.Ambito ambito, boolean disponible) { }
    boolean cuentaActiva(UUID usuarioId);
    Optional<Permiso> buscarPermiso(String codigo);
    boolean contextoVigente(UUID usuarioId, UUID proyectoId);
    default boolean contextoVigente(UUID usuarioId,UUID proyectoId,String permiso) { return contextoVigente(usuarioId,proyectoId); }
    boolean permisoPlataforma(UUID usuarioId, String permiso);
    default boolean permisoOrganizacion(UUID usuarioId, String permiso, UUID organizacionId) { return false; }
    List<AlcanceAutorizado> alcances(UUID usuarioId, String permiso, UUID proyectoId);
}
