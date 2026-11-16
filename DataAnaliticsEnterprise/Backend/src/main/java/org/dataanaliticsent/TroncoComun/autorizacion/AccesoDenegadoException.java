package org.dataanaliticsent.TroncoComun.autorizacion;

public class AccesoDenegadoException extends RuntimeException {
    private final DecisionAcceso.Motivo motivo;
    public AccesoDenegadoException(DecisionAcceso.Motivo motivo) {
        super("Acceso denegado: " + motivo);
        this.motivo = motivo;
    }
    public DecisionAcceso.Motivo motivo() { return motivo; }
}
