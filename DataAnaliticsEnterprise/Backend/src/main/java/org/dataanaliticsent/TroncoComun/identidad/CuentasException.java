package org.dataanaliticsent.TroncoComun.identidad;

public class CuentasException extends RuntimeException {
    public enum Tipo { INVALIDO, NO_DISPONIBLE, CONFLICTO, INVITACION_INVALIDA, ENTREGA_NO_CONFIGURADA }
    private final Tipo tipo;
    public CuentasException(Tipo tipo) { super("No se pudo completar la operación de cuenta"); this.tipo=tipo; }
    public Tipo tipo() { return tipo; }
}
