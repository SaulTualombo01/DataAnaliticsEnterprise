package org.dataanaliticsent.Gobierno.comun.web;

/** Falta la cabecera If-Match en una edición con control de revisión (HTTP 428). */
public class PrecondicionRequeridaException extends RuntimeException {
    public PrecondicionRequeridaException() {
        super("Obtenga el recurso y envíe su ETag en la cabecera If-Match");
    }
}
