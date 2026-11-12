package org.dataanaliticsent.Calidad.Presentacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.dataanaliticsent.Calidad.Aplicacion.IdentityService;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.LoginRateLimiter;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import static org.dataanaliticsent.Calidad.Presentacion.Utilidades.ApiContext.*;
import java.time.Clock;
import java.util.*;

@Tag(name = "Sesión y acceso")
@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final IdentityService identity;
    private final QualityRepository repo;
    private final LoginRateLimiter limiter;
    private final Clock clock;

    public IdentityController(IdentityService identity, QualityRepository repo, LoginRateLimiter limiter, Clock clock) {
        this.identity = identity;
        this.repo = repo;
        this.limiter = limiter;
        this.clock = clock;
    }

    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(max = 128) String password) {
    }

    public record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetConfirm(@NotBlank @Size(max = 128) String token,
                               @NotBlank @Size(min = 12, max = 128) String password) {
    }

    @GetMapping("/session/csrf")
    @Operation(
            summary = "Obtener token CSRF",
            description = "Consultar antes de cada escritura; renovar después de iniciar/cerrar sesión.")
    public ApiResponse<?> csrf(@io.swagger.v3.oas.annotations.Parameter(hidden = true) CsrfToken token, HttpServletRequest request) {
        if (request.getSession(false) == null) {
            limiter.checkAnonymousSession(request.getRemoteAddr());
            request.getSession(true).setMaxInactiveInterval(300);
        }
        return response(Map.of("token", token.getToken(), "header_name", token.getHeaderName()), request);
    }

    @PostMapping("/sessions")
    @Operation(
            summary = "Iniciar sesión",
            description = "Credenciales válidas y CSRF; la cookie de sesión la gestiona el navegador.")
    public ApiResponse<?> login(@Valid @RequestBody Credentials credentials, HttpServletRequest request, HttpServletResponse response) {
        limiter.check(request.getRemoteAddr());
        var user = identity.authenticate(credentials.email(), credentials.password());
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute("m2_user", user.id.toString());
        session.setAttribute("m2_auth_version", user.authVersion);
        session.setAttribute("m2_created", clock.millis());
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        return response(Map.of("id", user.id), request);
    }

    @DeleteMapping("/sessions/current")
    @Operation(
            summary = "Cerrar sesión",
            description = "Sesión y CSRF obligatorios.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Operación completada o aceptada."))
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "Consultar cuenta y roles",
            description = "Requiere sesión.")
    public ApiResponse<?> me(Principal principal, HttpServletRequest request) {
        UUID actor = actor(principal);
        var roles = repo.roles(actor, null).stream().map(r -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("project_id", r.projectId);
            value.put("project_name", repo.project(r.projectId).map(p -> p.name).orElse("Proyecto"));
            value.put("role", r.roleName);
            value.put("asset_scope_id", r.assetScopeId);
            return value;
        }).toList();
        return response(Map.of("id", actor, "roles", roles), request);
    }

    @PostMapping("/password-resets")
    @Operation(
            summary = "Solicitar recuperación de acceso",
            description = "Envía un código de 15 minutos mediante SMTP configurado. Conserva una respuesta genérica para cuentas inexistentes, inactivas o técnicas y ante fallos de envío. Devuelve 503 si la recuperación está deshabilitada.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Operación completada o aceptada."))
    public ResponseEntity<?> requestReset(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        identity.requestReset(body.email());
        return ResponseEntity.accepted().body(response(Map.of("message", "Si la cuenta está habilitada, recibirás las instrucciones por el canal configurado."), request));
    }

    @PostMapping("/password-resets/confirm")
    @Operation(
            summary = "Confirmar recuperación",
            description = "Código de un solo uso y CSRF. Invalida los demás códigos y las sesiones anteriores de la cuenta.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Operación completada o aceptada."))
    public ResponseEntity<Void> confirm(@Valid @RequestBody ResetConfirm body, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        identity.confirmReset(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}

