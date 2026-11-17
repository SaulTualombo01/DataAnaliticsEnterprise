package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.LimitadorIntentos;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Presupuesto local acotado para activación anónima; no confía en X-Forwarded-For. */
public class FiltroActivacion extends OncePerRequestFilter {
    private final LimitadorIntentos limite;
    private final ProblemasHttp salida;
    private final long retry;
    public FiltroActivacion(Clock clock,ParametrosCuentas p,ProblemasHttp salida) {
        limite=new LimitadorIntentos(clock,p.activationWindow(),p.activationIpLimit(),p.activationIpLimit(),10000);
        this.salida=salida; retry=Math.max(1,p.activationWindow().toSeconds());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        return !"POST".equals(req.getMethod()) || !"/api/v1/auth/invitations/accept".equals(req.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String ip=FiltroLimiteLogin.hash(req.getRemoteAddr());
        if(!limite.permitir(ip,ip)) {
            res.setHeader("Retry-After",Long.toString(retry)); salida.escribir(req,res,429,"ACTIVATION_RATE_LIMITED","Espera antes de volver a intentar"); return;
        }
        boolean json=false;
        try { json=req.getContentType()!=null && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(req.getContentType())); }
        catch(IllegalArgumentException ignored) { }
        if(!json) { salida.escribir(req,res,415,"UNSUPPORTED_MEDIA_TYPE","Se requiere application/json"); return; }
        byte[] bytes=req.getInputStream().readNBytes(8193);
        if(bytes.length>8192) { salida.escribir(req,res,413,"REQUEST_TOO_LARGE","La solicitud supera el límite permitido"); return; }
        var wrapped=new HttpServletRequestWrapper(req) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public int read(byte[] b,int off,int len) { return input.read(b,off,len); }
                    public boolean isFinished() { return input.available()==0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Entrada síncrona"); }
                };
            }
        };
        chain.doFilter(wrapped,res);
    }
}
