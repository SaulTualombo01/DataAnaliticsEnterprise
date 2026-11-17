package org.dataanaliticsent.Gobierno.versionado;

/** Otro actor cambió el recurso: la versión previa esperada ya no es la vigente. */
public class ConflictoVersionException extends RuntimeException {
    public ConflictoVersionException(String detalle) { super(detalle); }
}
