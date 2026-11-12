package org.dataanaliticsent.Calidad.Presentacion.Errores;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiProblem> handleApiException(ApiException ex, HttpServletRequest request) {
        if (java.util.Set.of(401,403,429).contains(ex.getStatus().value()))
            log.warn("SECURITY_REQUEST_REJECTED code={} status={} request_id={}", ex.getCode(), ex.getStatus().value(), RequestIdFilter.resolveRequestId(request));
        return buildProblemResponse(
                ex.getStatus(),
                ex.getCode(),
                ex.getTitle(),
                ex.getMessage(),
                ex.getErrors(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiProblem> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiException.FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new ApiException.FieldErrorDetail(
                        "body",
                        "/" + toSnakeCase(err.getField()),
                        err.getCode() != null ? err.getCode().toUpperCase(Locale.ROOT) : "INVALID_FIELD",
                        err.getDefaultMessage() != null ? err.getDefaultMessage() : "Valor inválido."
                ))
                .toList();

        return buildProblemResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                "La solicitud contiene datos no válidos.",
                "Revisa y corrige los campos señalados en la solicitud.",
                fieldErrors,
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiProblem> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        List<ApiException.FieldErrorDetail> errors = List.of(
                new ApiException.FieldErrorDetail(
                        "query",
                        ex.getName(),
                        "INVALID_FORMAT",
                        "El formato del parámetro no es válido."
                )
        );
        return buildProblemResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                "La solicitud contiene parámetros no válidos.",
                "Uno o más parámetros no cumplen el formato requerido.",
                errors,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiProblem> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildProblemResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "El mensaje JSON no puede interpretarse.",
                "Verifica la sintaxis JSON y que no existan propiedades desconocidas.",
                null,
                request
        );
    }

    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ApiProblem> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return buildProblemResponse(
                HttpStatus.PRECONDITION_FAILED,
                "PRECONDITION_FAILED",
                "La revisión del recurso quedó obsoleta.",
                "Otro usuario modificó este recurso. Recupera la versión vigente e intenta de nuevo.",
                null,
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiProblem> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildProblemResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Autenticación requerida.",
                "Tu sesión no es válida o ha expirado. Inicia sesión nuevamente.",
                null,
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiProblem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildProblemResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Acceso denegado.",
                "No cuentas con permisos para realizar esta operación en el ámbito solicitado.",
                null,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = RequestIdFilter.resolveRequestId(request);
        log.error("Error interno no controlado [request_id={}]: {}", requestId, ex.getClass().getSimpleName());
        return buildProblemResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ocurrió un fallo interno inesperado.",
                "No se pudo completar la operación. Intenta nuevamente más tarde.",
                null,
                request
        );
    }

    private ResponseEntity<ApiProblem> buildProblemResponse(
            HttpStatus status,
            String code,
            String title,
            String detail,
            List<ApiException.FieldErrorDetail> errors,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.resolveRequestId(request);
        ApiProblem payload = ApiProblem.of(status.value(), code, title, detail, requestId, errors);
        return ResponseEntity
                .status(status)
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .header(RequestIdFilter.HEADER_NAME, requestId)
                .body(payload);
    }

    private String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
