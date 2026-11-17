package org.dataanaliticsent.TroncoComun.autorizacion;

import java.util.Objects;
import java.util.UUID;

/** Restricción que el consumidor debe aplicar también a sus consultas y conteos. */
public record AlcanceAutorizado(UUID proyectoId, Tipo tipo, UUID recursoId) {
    public enum Tipo { PROJECT, SOURCE, ASSET }
    public AlcanceAutorizado {
        Objects.requireNonNull(proyectoId); Objects.requireNonNull(tipo);
        if ((tipo == Tipo.PROJECT) != (recursoId == null)) {
            throw new IllegalArgumentException("Alcance de recurso incoherente");
        }
    }
}
