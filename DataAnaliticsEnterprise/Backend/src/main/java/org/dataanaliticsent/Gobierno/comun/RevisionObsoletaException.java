package org.dataanaliticsent.Gobierno.comun;

/**
 * El recurso cambió desde la revisión que leyó el llamador (o ya existía al intentar crearlo).
 * En HTTP es 412: hay que volver a consultarlo antes de repetir el cambio. Se distingue de un
 * conflicto de negocio (409), como un nombre repetido o una transición no permitida.
 */
public class RevisionObsoletaException extends RuntimeException {
    public RevisionObsoletaException(String detalle) { super(detalle); }
}
