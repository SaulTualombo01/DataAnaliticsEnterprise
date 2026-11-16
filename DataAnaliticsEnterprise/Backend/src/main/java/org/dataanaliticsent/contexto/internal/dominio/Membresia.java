package org.dataanaliticsent.contexto.internal.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Membresia(
        UUID id, UUID userId, UUID projectId, UUID organizationId, Estado status, UUID assignedBy,
        OffsetDateTime assignedAt) {
}
