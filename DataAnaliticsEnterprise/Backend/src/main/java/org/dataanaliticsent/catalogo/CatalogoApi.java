package org.dataanaliticsent.catalogo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Contrato Java del catálogo. La identidad se obtiene del servidor, no de sus argumentos. */
public interface CatalogoApi {
    RegistroActivo registrarActivo(AltaActivo solicitud);
    Optional<DetalleActivo> consultarActivo(UUID proyectoId, UUID activoId);
    List<CampoCatalogo> listarCampos(UUID proyectoId, UUID activoId);

    List<RestriccionCatalogo> listarRestricciones(UUID proyectoId, UUID activoId);

    Optional<PaginaCampos> consultarCamposVersion(UUID proyectoId, UUID activoId, UUID versionId, int page, int pageSize);
    record PaginaCampos(UUID organizationId, UUID projectId, UUID assetId, UUID metadataVersionId,
            int page, int pageSize, List<CampoCatalogo> fields, boolean hasNext) {
        public PaginaCampos { fields = List.copyOf(fields); }
    }

    enum TipoRestriccion { PK, UNIQUE, FK }
    enum ResolucionReferencia { RESOLVED, UNRESOLVED, NOT_ACCESSIBLE }
    record ReferenciaTabla(String namespace, String name) { }
    record MiembroRestriccion(int position, UUID sourceFieldId, String targetFieldName) { }
    record RestriccionCatalogo(UUID id, UUID projectId, UUID assetId, String name, TipoRestriccion kind,
            OrigenEstructura origin, UUID targetAssetId, ReferenciaTabla targetLocator,
            ResolucionReferencia resolution, List<MiembroRestriccion> members) {
        public RestriccionCatalogo { members = List.copyOf(members); }
    }

    enum TipoActivo { TABLE, CSV_DATASET, JSON_COLLECTION, API_COLLECTION }
    enum OrigenEstructura { DECLARED, INFERRED }
    enum EstadoActivo { ACTIVE, MISSING, RETIRED }
    enum TipoLogico { STRING, INTEGER, NUMBER, BOOLEAN, DATE, DATETIME, OBJECT, ARRAY, MIXED, UNKNOWN }
    enum EstadoCampo { ACTIVE, MISSING }

    /** Solo metadatos técnicos: no acepta actor, IDs nuevos, credenciales ni filas empresariales. */
    record AltaActivo(UUID projectId, UUID sourceId, String name, TipoActivo kind,
            String originNamespace, String originObjectPath, OrigenEstructura structureOrigin,
            List<AltaCampo> fields) {
        public AltaActivo {
            Objects.requireNonNull(projectId); Objects.requireNonNull(sourceId);
            Objects.requireNonNull(kind); Objects.requireNonNull(structureOrigin);
            fields = List.copyOf(fields);
        }
    }

    record AltaCampo(String name, String path, int position, String nativeType,
            TipoLogico logicalType, Boolean nullable) {
        public AltaCampo { Objects.requireNonNull(logicalType); }
    }

    /** Confirmación del alta; no concede por sí misma permiso de consulta. */
    record RegistroActivo(UUID id, UUID projectId, UUID sourceId, UUID metadataVersionId,
            Instant registeredAt, UUID requestId) { }

    record DetalleActivo(UUID id, UUID projectId, UUID sourceId, String name, TipoActivo kind,
            String originNamespace, String originObjectPath, EstadoActivo status,
            OrigenEstructura structureOrigin, String businessDescription, List<String> synonyms,
            UUID metadataVersionId, Instant lastDiscoveryAt, Instant updatedAt, long revision,
            UUID organizationId, List<String> tags) {
        public DetalleActivo { synonyms = List.copyOf(synonyms); tags = List.copyOf(tags); }
    }

    record CampoCatalogo(UUID id, UUID projectId, UUID assetId, String name, String path,
            int position, String nativeType, TipoLogico logicalType, Boolean nullable,
            String businessDescription, List<String> synonyms, EstadoCampo status) {
        public CampoCatalogo { synonyms = List.copyOf(synonyms); }
    }
}
