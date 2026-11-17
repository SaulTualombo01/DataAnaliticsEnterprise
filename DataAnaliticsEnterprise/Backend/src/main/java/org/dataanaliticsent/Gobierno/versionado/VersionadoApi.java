package org.dataanaliticsent.Gobierno.versionado;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;

/**
 * Servicio de versiones liderado por M3. Solo un cambio efectivo crea versión: guardar el
 * mismo estado no crea otra. La consulta exige {@link PermisosGobierno#HISTORIAL_LEER}.
 */
public interface VersionadoApi {

    /**
     * Versión que el llamador leyó antes de cambiar el recurso. {@link #ninguna()} indica que
     * espera crear la primera versión; un valor null en la solicitud omite la comprobación.
     */
    record Previa(UUID versionId) {
        public static Previa ninguna() { return new Previa(null); }
        public static Previa de(UUID versionId) { return new Previa(Objects.requireNonNull(versionId)); }
    }

    /** estado contiene solo el estado versionable del recurso, nunca secretos ni filas de negocio. */
    record SolicitudVersion(UUID projectId, String resourceType, UUID resourceId, AuditoriaApi.Actor actor,
            String reason, Map<String, Object> estado, UUID auditEventId, Previa previaEsperada) {
        public SolicitudVersion {
            Objects.requireNonNull(projectId, "projectId obligatorio");
            Objects.requireNonNull(resourceType, "resourceType obligatorio");
            Objects.requireNonNull(resourceId, "resourceId obligatorio");
            Objects.requireNonNull(actor, "actor obligatorio");
            if (actor.tipo() == AuditoriaApi.TipoActor.ANONYMOUS) {
                throw new IllegalArgumentException("Una versión exige un actor identificado");
            }
            if (reason == null || reason.isBlank() || reason.strip().length() > 500) {
                throw new IllegalArgumentException("reason obligatoria de 1 a 500 caracteres");
            }
            Objects.requireNonNull(estado, "estado obligatorio");
            estado = Map.copyOf(estado);
        }
    }

    /** creada=false indica que el estado no cambió y se devuelve la versión vigente. */
    record ResultadoVersion(UUID versionId, long number, boolean creada, String contentHash) { }

    record Version(UUID id, UUID projectId, String resourceType, UUID resourceId, long number,
            UUID previousVersionId, Instant createdAt, AuditoriaApi.Actor actor, String reason,
            int representationVersion, String estadoJson, String contentHash, UUID auditEventId) { }

    ResultadoVersion versionar(SolicitudVersion solicitud);

    /** Versiones del recurso, de la más reciente a la más antigua. Exige m3.history.read. */
    Pagina<Version> historial(UUID projectId, String resourceType, UUID resourceId, int page, int pageSize);

    /** Una versión concreta; consultarla no altera la vigente. Exige m3.history.read. */
    Optional<Version> version(UUID projectId, String resourceType, UUID resourceId, UUID versionId);
}
