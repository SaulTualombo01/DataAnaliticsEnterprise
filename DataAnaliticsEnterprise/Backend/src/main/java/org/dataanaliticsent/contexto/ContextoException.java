package org.dataanaliticsent.contexto;

public final class ContextoException extends RuntimeException {
    public enum Tipo { INVALIDO, NO_DISPONIBLE, CONFLICTO }
    private final Tipo tipo;
    public ContextoException(Tipo tipo) { super("No se pudo completar la operación de contexto"); this.tipo=tipo; }
    public Tipo tipo() { return tipo; }
}
