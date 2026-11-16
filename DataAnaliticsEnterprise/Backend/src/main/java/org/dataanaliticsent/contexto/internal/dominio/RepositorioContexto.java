package org.dataanaliticsent.contexto.internal.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Las operaciones sobre un identificador devuelven vacío cuando el recurso no existe. */
public interface RepositorioContexto {
    List<Organizacion> organizaciones(Pagina pagina);
    Optional<Organizacion> organizacion(UUID id);
    Organizacion crearOrganizacion(UUID id, String nombre);
    Optional<Organizacion> renombrarOrganizacion(UUID id, String nombre);
    Optional<Organizacion> cambiarEstadoOrganizacion(UUID id, Estado estado);

    List<Proyecto> proyectos(UUID organizacionId, Pagina pagina);
    Optional<Proyecto> proyecto(UUID id);
    Proyecto crearProyecto(UUID id, UUID organizacionId, String nombre);
    Optional<Proyecto> renombrarProyecto(UUID id, String nombre);
    Optional<Proyecto> cambiarEstadoProyecto(UUID id, Estado estado);

    List<Membresia> membresias(UUID proyectoId, Pagina pagina);

    /** Idempotente: crea la membresía o reactiva la existente, incluida la de su organización. */
    Membresia vincular(UUID usuarioId, Proyecto proyecto, UUID actorId);

    Optional<Membresia> revocar(UUID usuarioId, UUID proyectoId);
}
