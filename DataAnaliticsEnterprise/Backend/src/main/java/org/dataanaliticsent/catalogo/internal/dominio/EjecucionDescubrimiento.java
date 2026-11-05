package org.dataanaliticsent.catalogo.internal.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EjecucionDescubrimiento(UUID id, UUID projectId, UUID sourceId, UUID requestedBy,
        UUID requestId, Estado status, Instant startedAt, Instant finishedAt,
        boolean scopeComplete, Long expectedCount, long processedCount) {
    public enum Estado { QUEUED, RUNNING, COMPLETED, FAILED }

    public EjecucionDescubrimiento {
        Objects.requireNonNull(id); Objects.requireNonNull(projectId); Objects.requireNonNull(sourceId);
        Objects.requireNonNull(requestedBy); Objects.requireNonNull(requestId); Objects.requireNonNull(status);
        if (processedCount < 0 || (expectedCount != null && expectedCount < 0)) {
            throw new IllegalArgumentException("Conteos inválidos");
        }
        if (finishedAt != null && (startedAt == null || finishedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException("Fechas de ejecución inválidas");
        }
        if (status == Estado.COMPLETED && (!scopeComplete || finishedAt == null)) {
            throw new IllegalArgumentException("Una ejecución parcial no puede completar");
        }
    }
}
