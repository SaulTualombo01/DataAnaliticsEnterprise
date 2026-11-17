package org.dataanaliticsent.TroncoComun.identidad;

public class IdentidadNoVerificadaException extends RuntimeException {
    public IdentidadNoVerificadaException() { super("Se requiere identidad verificada"); }
}
