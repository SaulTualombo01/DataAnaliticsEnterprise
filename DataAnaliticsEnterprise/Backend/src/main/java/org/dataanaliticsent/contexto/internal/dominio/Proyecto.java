package org.dataanaliticsent.contexto.internal.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Proyecto(
        UUID id, UUID organizationId, String name, Estado status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
