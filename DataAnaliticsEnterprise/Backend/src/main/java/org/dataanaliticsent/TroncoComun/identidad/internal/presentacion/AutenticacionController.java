package org.dataanaliticsent.TroncoComun.identidad.internal.presentacion;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AutenticacionController {
    public record CambioClave(String current_password,String new_password) {
        @Override public String toString() { return "CambioClave[protegido]"; }
    }
    private final IdentidadActualApi identidad;
    private final ProblemasHttp salida;
    public AutenticacionController(IdentidadActualApi identidad,ProblemasHttp salida) { this.identidad=identidad; this.salida=salida; }
    @GetMapping("/api/v1/auth/csrf")
    public void csrf(CsrfToken token,HttpServletRequest request,HttpServletResponse response) throws IOException {
        salida.datos(request,response,Map.of("header_name",token.getHeaderName(),"parameter_name",token.getParameterName(),"token",token.getToken()));
    }
    @GetMapping("/api/v1/auth/me")
    public void perfil(HttpServletRequest request,HttpServletResponse response) throws IOException { salida.datos(request,response,identidad.perfil()); }
    @PostMapping("/api/v1/auth/password")
    public void clave(@RequestBody CambioClave cambio,HttpServletRequest request,HttpServletResponse response) {
        identidad.cambiarClave(cambio.current_password(),cambio.new_password());
        var auth=SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request,response,auth);
        new CookieClearingLogoutHandler("TICSESSION").logout(request,response,auth);
        response.setStatus(204);
    }
    @PostMapping("/api/v1/admin/users/{id}/sessions/revoke")
    public void revocar(@PathVariable UUID id,HttpServletResponse response) { identidad.revocarSesiones(id); response.setStatus(204); }
}
