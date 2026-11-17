package org.dataanaliticsent.Gobierno.fichas;

/** La ficha cambió (o ya existía) desde la revisión que leyó el llamador. */
public class ConflictoFichaException extends RuntimeException {
    public ConflictoFichaException(String detalle) { super(detalle); }
}
