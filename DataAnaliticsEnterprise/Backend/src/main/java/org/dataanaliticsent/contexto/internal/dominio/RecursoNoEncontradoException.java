package org.dataanaliticsent.contexto.internal.dominio;

public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException() {
        super("El recurso no existe");
    }
}
