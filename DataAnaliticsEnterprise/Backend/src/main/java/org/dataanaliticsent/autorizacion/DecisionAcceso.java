package org.dataanaliticsent.autorizacion;

import java.util.Objects;

public record DecisionAcceso(Motivo motivo) {
    public enum Motivo {
        PERMITIDO, SIN_IDENTIDAD, CUENTA_NO_ACTIVA, PERMISO_DESCONOCIDO, PERMISO_NO_DISPONIBLE,
        AMBITO_INCORRECTO, TITULAR_INCORRECTO, CONTEXTO_NO_AUTORIZADO,
        PERMISO_NO_CONCEDIDO, RECURSO_NO_VALIDADO, ALCANCE_INSUFICIENTE
    }
    public DecisionAcceso { Objects.requireNonNull(motivo); }
    public boolean permitido() { return motivo == Motivo.PERMITIDO; }
}
