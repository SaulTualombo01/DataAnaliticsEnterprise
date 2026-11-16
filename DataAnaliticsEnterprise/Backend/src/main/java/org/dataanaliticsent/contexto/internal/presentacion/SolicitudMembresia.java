package org.dataanaliticsent.contexto.internal.presentacion;

import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SolicitudMembresia(UUID userId) {
}
