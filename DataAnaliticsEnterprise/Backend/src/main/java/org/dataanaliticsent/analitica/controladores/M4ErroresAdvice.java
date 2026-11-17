package org.dataanaliticsent.analitica.controladores;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores propios de M4 en application/problem+json. Solo aplica a los controladores de analítica;
 * identidad y acceso denegado siguen resolviéndose en el manejador del tronco común.
 */
@RestControllerAdvice(assignableTypes = M4AnaliticaController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class M4ErroresAdvice {

    private final ProblemasHttp problemas;

    public M4ErroresAdvice(ProblemasHttp problemas) {
        this.problemas = problemas;
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public void solicitudInvalida(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problemas.escribir(request, response, 400, "INVALID_REQUEST", "Revisa los campos de la solicitud de analítica");
    }

    @ExceptionHandler(DataAccessException.class)
    public void analiticaNoDisponible(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problemas.escribir(request, response, 503, "ANALYTICS_UNAVAILABLE", "No se pudo completar la operación de analítica");
    }
}
