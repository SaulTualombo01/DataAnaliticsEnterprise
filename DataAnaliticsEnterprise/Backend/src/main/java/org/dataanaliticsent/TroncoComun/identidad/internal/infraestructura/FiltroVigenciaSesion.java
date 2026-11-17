package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.RepositorioIdentidad;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroVigenciaSesion extends OncePerRequestFilter {
    private final RepositorioIdentidad datos;
    private final ProblemasHttp errores;
    private final Clock clock;
    private final Duration maximo;
    public FiltroVigenciaSesion(RepositorioIdentidad datos,ProblemasHttp errores,Clock clock,Duration maximo) {
        this.datos=datos; this.errores=errores; this.clock=clock; this.maximo=maximo;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            boolean valida=false;
            try {
                if(auth.getPrincipal() instanceof PrincipalLocal principal) {
                    var version=datos.versionActiva(principal.usuarioId());
                    valida=version.isPresent() && version.getAsLong()==principal.sessionVersion()
                        && principal.autenticadoEn().plus(maximo).isAfter(clock.instant());
                }
            } catch(DataAccessException failure) {
                errores.escribir(request,response,503,"IDENTITY_UNAVAILABLE","No se pudo verificar la sesión"); return;
            }
            if(!valida) {
                new SecurityContextLogoutHandler().logout(request,response,auth);
                new CookieClearingLogoutHandler("TICSESSION").logout(request,response,auth);
                if(!("POST".equals(request.getMethod()) && "/api/v1/auth/logout".equals(request.getServletPath()))) {
                    errores.escribir(request,response,401,"AUTHENTICATION_REQUIRED","Inicia sesión nuevamente"); return;
                }
            }
        }
        chain.doFilter(request,response);
    }
}
