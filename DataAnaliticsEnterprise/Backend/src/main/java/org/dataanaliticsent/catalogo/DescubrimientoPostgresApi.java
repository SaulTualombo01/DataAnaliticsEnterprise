package org.dataanaliticsent.catalogo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Descubrimiento síncrono y acotado de metadatos; no recibe URLs ni contraseñas. */
public interface DescubrimientoPostgresApi {
    Resultado descubrir(Solicitud solicitud);

    record Solicitud(UUID projectId, UUID sourceId, List<String> schemas) {
        public Solicitud {
            Objects.requireNonNull(projectId); Objects.requireNonNull(sourceId);
            schemas = List.copyOf(schemas);
        }
    }
    record Resultado(UUID requestId, UUID projectId, UUID sourceId, Instant completedAt,
            List<TablaRegistrada> tables) {
        public Resultado { tables = List.copyOf(tables); }
    }
    record TablaRegistrada(UUID assetId, UUID metadataVersionId, String namespace, String name,
            int columnCount, int constraintCount) { }
}
