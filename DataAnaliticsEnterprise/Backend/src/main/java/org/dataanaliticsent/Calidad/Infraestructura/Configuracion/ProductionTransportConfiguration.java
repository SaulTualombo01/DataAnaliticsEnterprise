package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Un perfil de producción con TLS o cookie segura desactivados debe fallar antes de abrir el puerto. */
@Configuration
@Profile("production")
public class ProductionTransportConfiguration {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> enforceProductionTransport(Environment environment) {
        return factory -> {
            if (!environment.getProperty("server.ssl.enabled",Boolean.class,false)
                    || !environment.getProperty("server.servlet.session.cookie.secure",Boolean.class,false)
                    || !"__Host-M2SESSION".equals(environment.getProperty("server.servlet.session.cookie.name"))
                    || !"/".equals(environment.getProperty("server.servlet.session.cookie.path"))
                    || environment.getProperty("server.servlet.session.cookie.domain") != null)
                throw new IllegalStateException("Producción requiere TLS y una cookie segura limitada al host.");
        };
    }
}
