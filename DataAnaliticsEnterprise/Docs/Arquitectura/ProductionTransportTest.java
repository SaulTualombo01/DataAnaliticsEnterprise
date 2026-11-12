package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionTransportTest {
    private MockEnvironment secure() {
        return new MockEnvironment().withProperty("server.ssl.enabled","true")
                .withProperty("server.servlet.session.cookie.secure","true")
                .withProperty("server.servlet.session.cookie.name","__Host-M2SESSION")
                .withProperty("server.servlet.session.cookie.path","/");
    }

    @Test void productionRejectsTlsAndCookieDowngradesBeforeOpeningAPort() {
        var configuration = new ProductionTransportConfiguration();
        assertDoesNotThrow(() -> configuration.enforceProductionTransport(secure()).customize(new TomcatServletWebServerFactory()));
        for (var change : new String[][]{{"server.ssl.enabled","false"},{"server.servlet.session.cookie.secure","false"},
                {"server.servlet.session.cookie.name","insecure"},{"server.servlet.session.cookie.path","/other"},
                {"server.servlet.session.cookie.domain","example.test"}}) {
            assertThrows(IllegalStateException.class, () -> configuration.enforceProductionTransport(secure().withProperty(change[0],change[1]))
                    .customize(new TomcatServletWebServerFactory()));
        }
    }
}
