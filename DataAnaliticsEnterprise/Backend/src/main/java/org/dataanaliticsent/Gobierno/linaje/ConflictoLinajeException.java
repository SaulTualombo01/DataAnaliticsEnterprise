package org.dataanaliticsent.Gobierno.linaje;

/** La relación crearía un ciclo o choca con el estado vigente (por ejemplo, ya está retirada). */
public class ConflictoLinajeException extends RuntimeException {
    public ConflictoLinajeException(String detalle) { super(detalle); }
}
