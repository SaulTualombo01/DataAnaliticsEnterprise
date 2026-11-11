package org.dataanaliticsent.identidad;

public class IdentidadNoVerificadaException extends RuntimeException {
    public IdentidadNoVerificadaException() { super("Se requiere identidad verificada"); }
}
