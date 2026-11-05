package org.dataanaliticsent.catalogo.internal.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ActivoDatos(UUID id, UUID projectId, UUID sourceId, String name, Tipo kind,
        String originNamespace, String originObjectPath, Estado status, Origen structureOrigin,
        String businessDescription, List<String> synonyms, UUID metadataVersionId,
        Instant lastDiscoveryAt, Instant updatedAt, long revision) {

    public enum Tipo { TABLE, CSV_DATASET, JSON_COLLECTION, API_COLLECTION }
    public enum Estado { ACTIVE, MISSING, RETIRED }
    public enum Origen { DECLARED, INFERRED }

    public ActivoDatos {
        Objects.requireNonNull(id); Objects.requireNonNull(projectId); Objects.requireNonNull(sourceId);
        Validacion.texto(name, "Nombre de activo", 200);
        Validacion.texto(originObjectPath, "Localizador", 1000);
        if (originNamespace != null && originNamespace.length() > 500) throw new IllegalArgumentException("Namespace demasiado largo");
        Objects.requireNonNull(kind); Objects.requireNonNull(status); Objects.requireNonNull(structureOrigin);
        Objects.requireNonNull(metadataVersionId); Objects.requireNonNull(lastDiscoveryAt); Objects.requireNonNull(updatedAt);
        Validacion.descripcion(businessDescription);
        synonyms = List.copyOf(synonyms);
        if (revision < 0) throw new IllegalArgumentException("Revisión inválida");
    }
}
