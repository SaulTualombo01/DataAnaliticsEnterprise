package org.dataanaliticsent.Calidad.Presentacion.Errores;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final List<FieldErrorDetail> errors;

    public record FieldErrorDetail(
            String location,
            String field,
            String code,
            String message
    ) {}

    public ApiException(HttpStatus status, String code, String title, String detail) {
        this(status, code, title, detail, null);
    }

    public ApiException(HttpStatus status, String code, String title, String detail, List<FieldErrorDetail> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.errors = errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public List<FieldErrorDetail> getErrors() {
        return errors;
    }
}
