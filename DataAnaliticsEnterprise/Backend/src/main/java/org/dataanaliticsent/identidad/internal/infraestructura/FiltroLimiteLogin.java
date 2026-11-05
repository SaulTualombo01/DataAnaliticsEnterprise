package org.dataanaliticsent.identidad.internal.infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.time.Duration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.dataanaliticsent.identidad.internal.dominio.AuditoriaIdentidad;
import org.dataanaliticsent.identidad.internal.dominio.LimitadorIntentos;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroLimiteLogin extends OncePerRequestFilter {
    private final LimitadorIntentos limite;
    private final ProblemasHttp errores;
    private final AuditoriaIdentidad auditoria;
    private final long espera;
    public FiltroLimiteLogin(LimitadorIntentos limite,ProblemasHttp errores,AuditoriaIdentidad auditoria,Duration ventana) {
        this.limite=limite; this.errores=errores; this.auditoria=auditoria; espera=Math.max(1,ventana.toSeconds());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(request.getServletPath()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        boolean formato=false;
        try { formato=request.getContentType()!=null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(MediaType.parseMediaType(request.getContentType())); }
        catch(IllegalArgumentException ignored) { /* Formato inválido: respuesta pública segura. */ }
        if(!formato) { errores.escribir(request,response,415,"UNSUPPORTED_MEDIA_TYPE","Usa application/x-www-form-urlencoded"); return; }
        if(!Set.of("email","password").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(values->values.length!=1)) {
            errores.escribir(request,response,400,"INVALID_REQUEST","Envía únicamente email y password una vez"); return;
        }
        String email=request.getParameter("email"),clave=request.getParameter("password");
        if(!limite.permitir(hash(normalizar(email)),hash(request.getRemoteAddr()))) {
            response.setHeader("Retry-After",Long.toString(espera));
            errores.escribir(request,response,429,"LOGIN_RATE_LIMITED","Espera antes de volver a intentar"); return;
        }
        if(email==null||email.isBlank()||email.length()>254||clave==null||clave.isEmpty()||clave.getBytes(StandardCharsets.UTF_8).length>72) {
            try { auditoria.registrar(null,null,"auth.login","FAILURE",UUID.fromString(ProblemasHttp.requestId(request))); }
            catch(DataAccessException unavailable) { errores.escribir(request,response,503,"IDENTITY_UNAVAILABLE","No se pudo verificar el acceso"); return; }
            errores.escribir(request,response,401,"INVALID_CREDENTIALS","Credenciales inválidas"); return;
        }
        chain.doFilter(request,response);
    }
    public static String normalizar(String email) { return email==null?"":email.trim().toLowerCase(Locale.ROOT); }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
