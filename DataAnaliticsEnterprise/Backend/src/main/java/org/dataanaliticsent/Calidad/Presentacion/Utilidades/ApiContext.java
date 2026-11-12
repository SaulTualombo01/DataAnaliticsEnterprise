package org.dataanaliticsent.Calidad.Presentacion.Utilidades;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;

/** Contexto HTTP común; no contiene decisiones de autorización. */
public final class ApiContext {
    private ApiContext() {}

    public static UUID actor(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    public static UUID requestId(HttpServletRequest request) {
        return UUID.fromString(RequestIdFilter.resolveRequestId(request));
    }

    public static <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.resolveRequestId(request));
    }
}
