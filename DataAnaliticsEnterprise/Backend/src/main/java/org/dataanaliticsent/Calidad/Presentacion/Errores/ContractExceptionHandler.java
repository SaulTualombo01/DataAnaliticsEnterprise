package org.dataanaliticsent.Calidad.Presentacion.Errores;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

/**
 * Casos HTTP adicionales sin reemplazar el manejador existente.
 */
@RestControllerAdvice
@Order(-1)
public class ContractExceptionHandler {
    private final GlobalExceptionHandler delegate;

    public ContractExceptionHandler(GlobalExceptionHandler delegate) {
        this.delegate = delegate;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> json(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof UnrecognizedPropertyException || ex.getCause() instanceof com.fasterxml.jackson.databind.JsonMappingException)
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Los campos o tipos no cumplen el contrato de la solicitud.", request);
        return delegate.handleMalformedJson(ex, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest r) {
        var response = error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "El método no está permitido para esta ruta.", r);
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).allow(ex.getSupportedHttpMethods() == null ? new HttpMethod[0] : ex.getSupportedHttpMethods().toArray(HttpMethod[]::new)).body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> media(Exception ex, HttpServletRequest r) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Usa el Content-Type indicado por el contrato.", r);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> missing(Exception ex, HttpServletRequest r) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El recurso no está disponible.", r);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> parameter(Exception ex, HttpServletRequest r) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Falta un parámetro obligatorio.", r);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> integrity(Exception ex, HttpServletRequest r) {
        return error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "La operación entra en conflicto con el estado actual. Actualiza la consulta.", r);
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String message, HttpServletRequest r) {
        return delegate.handleApiException(new ApiException(status, code, message, message, code.equals("VALIDATION_ERROR") ? List.of(new ApiException.FieldErrorDetail("body", "/", "INVALID_FIELD", message)) : null), r);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> uploadTooLarge(Exception ex, HttpServletRequest r) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "CSV_LIMIT_EXCEEDED", "El archivo supera el tamaño permitido. El máximo de archivo es 10 MB.", r);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<?> missingPart(Exception ex, HttpServletRequest r) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Selecciona el archivo CSV que deseas subir.", r);
    }
}
