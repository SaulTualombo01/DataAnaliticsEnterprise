package org.dataanaliticsent.Gobierno.politicas;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.Pagina;

/**
 * Políticas de gobierno de M3 (RF-M3.1 y RF-M3.2.1). El actor sale de la identidad verificada,
 * nunca de los argumentos. Cada cambio efectivo se audita y crea una versión de la política.
 * Las ediciones exigen la revisión leída (expectedRevision) para no pisar cambios ajenos.
 */
public interface PoliticasApi {

    /** DRAFT → ACTIVE ↔ INACTIVE; cualquiera menos RETIRED → RETIRED (terminal). */
    enum EstadoPolitica { DRAFT, ACTIVE, INACTIVE, RETIRED }

    enum CategoriaPolitica { CLASIFICACION, PRIVACIDAD, RETENCION, CALIDAD, ACCESO, OTRA }

    record Politica(UUID id, UUID projectId, String name, String description, CategoriaPolitica category,
            UUID ownerId, EstadoPolitica status, long revision, UUID currentVersionId, List<UUID> assetIds,
            Instant createdAt, UUID createdBy, Instant updatedAt) {
        public Politica { assetIds = List.copyOf(assetIds); }
    }

    record NuevaPolitica(UUID projectId, String name, String description, CategoriaPolitica category, UUID ownerId) {
        public NuevaPolitica {
            Objects.requireNonNull(projectId, "projectId obligatorio");
            Objects.requireNonNull(category, "category obligatoria");
            Objects.requireNonNull(ownerId, "ownerId obligatorio");
        }
    }

    /** null en un campo significa "no cambiar". description vacía la deja en blanco. */
    record CambiosPolitica(String name, String description, CategoriaPolitica category, UUID ownerId) { }

    /** Todos los filtros son opcionales; texto busca en nombre y descripción. */
    record FiltroPoliticas(String texto, EstadoPolitica status, CategoriaPolitica category, UUID ownerId, UUID assetId) {
        public static FiltroPoliticas ninguno() { return new FiltroPoliticas(null, null, null, null, null); }
    }

    /** aplicable=true solo si la política está ACTIVE. */
    record PoliticaAplicable(Politica politica, boolean aplicable) { }

    /** Exige m3.policies.create. Nace en DRAFT, revisión 0 y versión 1. */
    Politica crear(NuevaPolitica nueva);

    /** Exige m3.policies.read. */
    Optional<Politica> consultar(UUID projectId, UUID policyId);

    /** Exige m3.policies.read. Orden: creación y id ascendentes. */
    Pagina<Politica> buscar(UUID projectId, FiltroPoliticas filtro, int page, int pageSize);

    /** Exige m3.policies.update. Sin cambios efectivos no sube la revisión ni crea versión. */
    Politica actualizar(UUID projectId, UUID policyId, long expectedRevision, CambiosPolitica cambios, String reason);

    /** Exige m3.policies.lifecycle. Una transición no permitida produce ConflictoPoliticaException. */
    Politica cambiarEstado(UUID projectId, UUID policyId, long expectedRevision, EstadoPolitica nuevo, String reason);

    /** Exige m3.policies.assets.manage. El activo debe existir en el catálogo del proyecto. */
    Politica asociarActivo(UUID projectId, UUID policyId, long expectedRevision, UUID assetId, String reason);

    /** Exige m3.policies.assets.manage. */
    Politica retirarActivo(UUID projectId, UUID policyId, long expectedRevision, UUID assetId, String reason);

    /** Exige m3.policies.read. Políticas asociadas al activo, indicando cuáles aplican. */
    List<PoliticaAplicable> politicasDeActivo(UUID projectId, UUID assetId);
}
