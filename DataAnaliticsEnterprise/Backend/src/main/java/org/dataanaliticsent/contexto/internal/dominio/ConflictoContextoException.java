package org.dataanaliticsent.contexto.internal.dominio;

/** Nombre duplicado o transición de estado no permitida. */
public class ConflictoContextoException extends RuntimeException {
    public ConflictoContextoException(String mensaje) {
        super(mensaje);
    }
}
