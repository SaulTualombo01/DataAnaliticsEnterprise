package org.dataanaliticsent.catalogo;

/** Conflicto de publicación sin incluir SQL ni valores de la fuente en el mensaje público. */
public class ConflictoCatalogoException extends RuntimeException {
    public ConflictoCatalogoException() {
        super("No se pudo registrar el activo por un conflicto de integridad");
    }
}
