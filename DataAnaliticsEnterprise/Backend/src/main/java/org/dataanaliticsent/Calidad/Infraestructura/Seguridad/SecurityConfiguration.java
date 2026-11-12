package org.dataanaliticsent.Calidad.Infraestructura.Seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.RequestIdFilter;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;

import java.time.Clock;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiProblem;

@Configuration
public class SecurityConfiguration {
    @Bean
    @Order(1)
    public SecurityFilterChain documentation(HttpSecurity http) throws Exception {
        return http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .authorizeHttpRequests(auth -> auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/**").permitAll().anyRequest().denyAll())
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'")))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    public SecurityFilterChain security(HttpSecurity http, QualityRepository repo, M2Properties properties, Clock clock, ObjectMapper mapper) throws Exception {
        http.authorizeHttpRequests(a -> a.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/api/v1/health", "/api/v1/session/csrf", "/api/v1/sessions", "/api/v1/password-resets", "/api/v1/password-resets/confirm").permitAll().anyRequest().authenticated())
                .formLogin(f -> f.disable()).httpBasic(b -> b.disable()).logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .addFilterAfter(new SessionIdentityFilter(repo, properties, clock), SecurityContextHolderFilter.class)
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, error) -> problem(mapper, request, response, 401, "AUTHENTICATION_REQUIRED", "Inicia sesión para continuar."))
                        .accessDeniedHandler((request, response, error) -> problem(mapper, request, response, 403, "ACCESS_DENIED", "La sesión o el token de protección no permiten esta operación.")));
        return http.build();
    }

    private void problem(ObjectMapper mapper, jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        String id = RequestIdFilter.resolveRequestId(request);
        if (status == 403) org.slf4j.LoggerFactory.getLogger(SecurityConfiguration.class)
                .warn("SECURITY_REQUEST_REJECTED code={} status={} request_id={}", code, status, id);
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("X-Request-ID", id);
        mapper.writeValue(response.getOutputStream(), ApiProblem.of(status, code, message, message, id, null));
    }
}

