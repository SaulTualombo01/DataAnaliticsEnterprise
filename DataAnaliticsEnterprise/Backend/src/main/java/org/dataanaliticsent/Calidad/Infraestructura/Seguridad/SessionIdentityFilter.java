package org.dataanaliticsent.Calidad.Infraestructura.Seguridad;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.*;

/**
 * La sesión conserva solo identificador, versión y fecha; los roles se vuelven a leer por operación.
 */
public class SessionIdentityFilter extends OncePerRequestFilter {
    private final QualityRepository repo;
    private final M2Properties properties;
    private final Clock clock;

    public SessionIdentityFilter(QualityRepository repo, M2Properties properties, Clock clock) {
        this.repo = repo;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("m2_user") instanceof String id) {
            try {
                var user = repo.user(UUID.fromString(id)).orElse(null);
                Object created = session.getAttribute("m2_created");
                boolean valid = user != null && Boolean.TRUE.equals(user.active) && Objects.equals(session.getAttribute("m2_auth_version"), user.authVersion)
                        && created instanceof Long timestamp && clock.millis() - timestamp < properties.getSecurity().getSessionAbsoluteTimeout().toMillis();
                if (valid)
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id, null, List.of()));
                else session.invalidate();
            } catch (IllegalArgumentException ex) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
