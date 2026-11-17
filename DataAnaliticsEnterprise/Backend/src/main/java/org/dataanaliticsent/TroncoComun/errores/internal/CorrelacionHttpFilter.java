package org.dataanaliticsent.TroncoComun.errores.internal;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionHttpFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String id=request.getHeader("X-Request-Id");
        if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) id=UUID.randomUUID().toString();
        request.setAttribute(ProblemasHttp.REQUEST_ID,id); response.setHeader("X-Request-Id",id);
        chain.doFilter(request,response);
    }
}
