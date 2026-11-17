package org.dataanaliticsent.Gobierno.comun.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.RevisionObsoletaException;
import org.dataanaliticsent.Gobierno.linaje.ConflictoLinajeException;
import org.dataanaliticsent.Gobierno.politicas.ConflictoPoliticaException;
import org.dataanaliticsent.Gobierno.versionado.ConflictoVersionException;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadNoVerificadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de las rutas de M3 en formato Problem Details (RFC 9457), con code y request_id, según
 * las convenciones del equipo. Solo atiende controladores de M3; nunca expone trazas ni SQL.
 */
@RestControllerAdvice(basePackages = "org.dataanaliticsent.Gobierno")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErroresGobiernoAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresGobiernoAdvice.class);

    @ExceptionHandler(SolicitudInvalidaException.class)
    ResponseEntity<Map<String, Object>> invalida(SolicitudInvalidaException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Revise los campos indicados.", e.errores());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> argumento(IllegalArgumentException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> ruta(MethodArgumentTypeMismatchException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Un identificador de la ruta no es válido.",
                List.of(new SolicitudInvalidaException.ErrorCampo("path", e.getName(), "UUID", "Debe ser un UUID")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> ilegible(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_JSON", "El cuerpo no es un JSON válido.", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> formato(HttpMediaTypeNotSupportedException e) {
        return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use application/json.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> metodo(HttpRequestMethodNotSupportedException e) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "La ruta no admite ese método.", List.of());
    }

    /** Sin sesión verificada del tronco común (ausente, vencida o revocada). */
    @ExceptionHandler(IdentidadNoVerificadaException.class)
    ResponseEntity<Map<String, Object>> sinSesion(IdentidadNoVerificadaException e) {
        return problema(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Inicia sesión para continuar.", List.of());
    }

    @ExceptionHandler(AccesoDenegadoGobiernoException.class)
    ResponseEntity<Map<String, Object>> denegado(AccesoDenegadoGobiernoException e) {
        return problema(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tiene permiso para esta operación.", List.of());
    }

    @ExceptionHandler(RecursoNoEncontradoGobiernoException.class)
    ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoGobiernoException e) {
        return problema(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", e.getMessage(), List.of());
    }

    @ExceptionHandler({ConflictoPoliticaException.class, ConflictoLinajeException.class, ConflictoVersionException.class})
    ResponseEntity<Map<String, Object>> conflicto(RuntimeException e) {
        return problema(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), List.of());
    }

    @ExceptionHandler(RevisionObsoletaException.class)
    ResponseEntity<Map<String, Object>> obsoleta(RevisionObsoletaException e) {
        return problema(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", e.getMessage(), List.of());
    }

    @ExceptionHandler(PrecondicionRequeridaException.class)
    ResponseEntity<Map<String, Object>> requerida(PrecondicionRequeridaException e) {
        return problema(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", e.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> inesperado(Exception e) {
        // El detalle técnico queda en el registro del servidor, relacionado por request_id.
        LOG.error("Error no controlado en M3, request_id={}", RespuestasGobierno.requestId(), e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "No se pudo completar la operación. Intente nuevamente más tarde.", List.of());
    }

    static ResponseEntity<Map<String, Object>> problema(HttpStatus estado, String codigo, String detalle,
            List<SolicitudInvalidaException.ErrorCampo> errores) {
        String requestId = RespuestasGobierno.requestId();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "about:blank");
        cuerpo.put("title", titulo(estado));
        cuerpo.put("status", estado.value());
        cuerpo.put("detail", detalle);
        cuerpo.put("instance", "urn:request:" + requestId);
        cuerpo.put("code", codigo);
        cuerpo.put("request_id", requestId);
        if (!errores.isEmpty()) {
            cuerpo.put("errors", errores.stream().map(x -> RespuestasGobierno.objeto(
                    "location", x.location(), "field", x.field(), "code", x.code(), "message", x.message())).toList());
        }
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Request-Id", requestId).body(cuerpo);
    }

    private static String titulo(HttpStatus estado) {
        return switch (estado.value()) {
            case 400 -> "Solicitud inválida";
            case 401 -> "Autenticación requerida";
            case 403 -> "Acceso denegado";
            case 404 -> "Recurso no disponible";
            case 405 -> "Método no permitido";
            case 409 -> "Conflicto";
            case 412 -> "Revisión obsoleta";
            case 415 -> "Formato no soportado";
            case 422 -> "Parámetros inválidos";
            case 428 -> "Revisión requerida";
            default -> "Error interno";
        };
    }
}
