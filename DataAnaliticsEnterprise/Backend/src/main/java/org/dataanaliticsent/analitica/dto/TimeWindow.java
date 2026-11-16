package org.dataanaliticsent.analitica.dto;

import java.time.OffsetDateTime;

/** Ventana semiabierta: incluye {@code from} y excluye {@code to}. */
public record TimeWindow(OffsetDateTime from, OffsetDateTime to) {
    public TimeWindow {
        if (from == null || to == null) {
            throw new IllegalArgumentException("La ventana requiere from y to");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("La ventana requiere from anterior a to");
        }
    }
}
