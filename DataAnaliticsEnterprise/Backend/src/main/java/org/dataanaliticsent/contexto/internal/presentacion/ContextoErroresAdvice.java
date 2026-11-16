package org.dataanaliticsent.contexto.internal.presentacion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.dataanaliticsent.contexto.internal.dominio.ConflictoContextoException;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de TC04 en application/problem+json. Solo aplica a los controladores de este paquete;
 * identidad y acceso denegado siguen resolviéndose en el manejador común.
 */
@RestControllerAdvice(basePackageClasses = ContextoErroresAdvice.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContextoErroresAdvice {
    private final ProblemasHttp problemas;

    public ContextoErroresAdvice(ProblemasHttp problemas) {
        this.problemas = problemas;
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public void solicitudInvalida(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problemas.escribir(request, response, 400, "INVALID_REQUEST", "Revisa los campos de la solicitud");
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public void noEncontrado(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problemas.escribir(request, response, 404, "NOT_FOUND", "El recurso no existe");
    }

    @ExceptionHandler(ConflictoContextoException.class)
    public void conflicto(ConflictoContextoException conflicto, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        problemas.escribir(request, response, 409, "CONFLICT", conflicto.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public void noDisponible(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problemas.escribir(request, response, 503, "CONTEXT_UNAVAILABLE", "No se pudo completar la operación");
    }
}
