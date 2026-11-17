package org.dataanaliticsent.Gobierno.politicas;

/**
 * La operación choca con el estado vigente: revisión obsoleta, nombre repetido en el proyecto,
 * transición de estado no permitida o política retirada.
 */
public class ConflictoPoliticaException extends RuntimeException {
    public ConflictoPoliticaException(String detalle) { super(detalle); }
}
