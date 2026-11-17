package org.dataanaliticsent.Gobierno.auditoria;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;

/**
 * Servicio de auditoría liderado por M3. Los módulos registran hechos ya autorizados por
 * su propio caso de uso; la consulta exige el permiso {@link PermisosGobierno#AUDITORIA_LEER}.
 * Los eventos no se editan ni se borran.
 */
public interface AuditoriaApi {

    enum TipoActor { USER, SERVICE, ANONYMOUS }

    enum Resultado { SUCCESS, FAILURE, DENIED }

    /** Quién hizo la operación. Un intento anónimo no lleva identificador; nunca se inventa uno. */
    record Actor(TipoActor tipo, UUID id) {
        public Actor {
            Objects.requireNonNull(tipo, "tipo de actor obligatorio");
            if ((tipo == TipoActor.ANONYMOUS) != (id == null)) {
                throw new IllegalArgumentException("Un actor anónimo no tiene id y los demás sí");
            }
        }
        public static Actor usuario(UUID id) { return new Actor(TipoActor.USER, Objects.requireNonNull(id)); }
        public static Actor servicio(UUID id) { return new Actor(TipoActor.SERVICE, Objects.requireNonNull(id)); }
        public static Actor anonimo() { return new Actor(TipoActor.ANONYMOUS, null); }
    }

    /**
     * Hecho a registrar. resourceType usa nombres como "gobierno.politica"; action, códigos
     * como "m3.policies.create". safeDetails solo admite datos no sensibles.
     */
    record EventoNuevo(Actor actor, UUID organizationId, UUID projectId, String resourceType, UUID resourceId,
            String action, Resultado outcome, UUID requestId, Map<String, Object> safeDetails) {
        public EventoNuevo {
            Objects.requireNonNull(actor, "actor obligatorio");
            Objects.requireNonNull(resourceType, "resourceType obligatorio");
            Objects.requireNonNull(action, "action obligatoria");
            Objects.requireNonNull(outcome, "outcome obligatorio");
            safeDetails = safeDetails == null ? Map.of() : Map.copyOf(safeDetails);
        }
    }

    /** Evento conservado. safeDetailsJson es el JSON canónico guardado. */
    record Evento(UUID id, Actor actor, UUID organizationId, UUID projectId, String resourceType, UUID resourceId,
            String action, Instant occurredAt, Resultado outcome, UUID requestId, String safeDetailsJson) { }

    /** Filtros de consulta. projectId es obligatorio: no hay consultas globales. */
    record Filtro(UUID projectId, UUID actorId, String action, String resourceType, UUID resourceId,
            Resultado outcome, Instant desde, Instant hasta) {
        public Filtro {
            Objects.requireNonNull(projectId, "projectId obligatorio");
            if (desde != null && hasta != null && !desde.isBefore(hasta)) {
                throw new IllegalArgumentException("desde debe ser anterior a hasta");
            }
        }
        public static Filtro delProyecto(UUID projectId) {
            return new Filtro(projectId, null, null, null, null, null, null, null);
        }
    }

    /** Registra el hecho y devuelve su identificador generado por el servidor. */
    UUID registrar(EventoNuevo evento);

    /** Eventos del proyecto, del más reciente al más antiguo. Exige m3.audit.read. */
    Pagina<Evento> consultar(Filtro filtro, int page, int pageSize);
}
