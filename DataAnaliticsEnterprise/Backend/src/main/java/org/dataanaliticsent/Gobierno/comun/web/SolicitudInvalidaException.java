package org.dataanaliticsent.Gobierno.comun.web;

import java.util.List;

/** Errores de validación por campo (HTTP 422 VALIDATION_ERROR) según las convenciones del equipo. */
public class SolicitudInvalidaException extends RuntimeException {

    /** location: body, query o path. field: puntero del campo, por ejemplo /name o page. */
    public record ErrorCampo(String location, String field, String code, String message) { }

    private final List<ErrorCampo> errores;

    public SolicitudInvalidaException(List<ErrorCampo> errores) {
        super("La solicitud contiene datos no válidos");
        this.errores = List.copyOf(errores);
    }

    public static SolicitudInvalidaException de(String location, String field, String code, String message) {
        return new SolicitudInvalidaException(List.of(new ErrorCampo(location, field, code, message)));
    }

    public List<ErrorCampo> errores() { return errores; }
}
