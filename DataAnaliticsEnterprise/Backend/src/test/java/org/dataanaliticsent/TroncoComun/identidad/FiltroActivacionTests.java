package org.dataanaliticsent.TroncoComun.identidad;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class FiltroActivacionTests {
    private FiltroActivacion filter(int limit) { return new FiltroActivacion(Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"),ZoneOffset.UTC),
        new ParametrosCuentas(Duration.ofHours(24),false,Duration.ofMinutes(15),limit),new ProblemasHttp(new ObjectMapper())); }
    private MockHttpServletRequest request(String body) {
        var req=new MockHttpServletRequest("POST","/api/v1/auth/invitations/accept"); req.setServletPath("/api/v1/auth/invitations/accept");
        req.setContentType("application/json"); req.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); req.setRemoteAddr("127.0.0.1"); return req;
    }
    @Test void limitaAntesDeLlegarAlServicioYNoConfiaEnCabecerasDeProxy() throws Exception {
        var filter=filter(2); var reached=new AtomicInteger();
        for(int i=0;i<3;i++) {
            var req=request("{}"); req.addHeader("X-Forwarded-For","192.0.2."+i); var res=new MockHttpServletResponse();
            filter.doFilter(req,res,(a,b)->reached.incrementAndGet()); assertEquals(i<2?200:429,res.getStatus());
            if(i==2) { assertEquals("900",res.getHeader("Retry-After")); assertTrue(res.getContentAsString().contains("ACTIVATION_RATE_LIMITED")); }
        }
        assertEquals(2,reached.get());
    }
    @Test void limitaBytesInclusoSinContentLengthYConservaElCuerpoValido() throws Exception {
        var filter=filter(10); var res=new MockHttpServletResponse();
        filter.doFilter(request("x".repeat(8193)),res,(a,b)->fail("No llegar al servicio")); assertEquals(413,res.getStatus());
        var ok=new MockHttpServletResponse(); String body="{\"activation_token\":\"sintético\"}";
        filter.doFilter(request(body),ok,(a,b)->assertEquals(body,new String(a.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void rechazaTipoDeContenidoAntesDeProcesarClave() throws Exception {
        var req=request("{}"); req.setContentType("text/plain"); var res=new MockHttpServletResponse();
        filter(10).doFilter(req,res,(a,b)->fail("No llegar al servicio")); assertEquals(415,res.getStatus());
    }
}
