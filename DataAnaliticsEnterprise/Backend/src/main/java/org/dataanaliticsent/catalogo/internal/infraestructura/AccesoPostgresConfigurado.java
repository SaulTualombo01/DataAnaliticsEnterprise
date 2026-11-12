package org.dataanaliticsent.catalogo.internal.infraestructura;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.dataanaliticsent.catalogo.DescubrimientoFallidoException;
import org.dataanaliticsent.catalogo.DescubrimientoFallidoException.Codigo;
import org.dataanaliticsent.catalogo.internal.aplicacion.LimitesDescubrimiento;
import org.dataanaliticsent.catalogo.internal.dominio.FuenteDatos;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Adaptador provisional del servidor, reemplazable por TC-11. Nunca acepta destinos libres. */
@Component
public class AccesoPostgresConfigurado {
    private final Environment entorno;
    public AccesoPostgresConfigurado(Environment entorno) { this.entorno = entorno; }

    Acceso resolver(FuenteDatos fuente, LimitesDescubrimiento limites) {
        String ref = fuente.credentialRef();
        if (ref == null || !ref.matches("[a-z][a-z0-9_]{0,63}")) throw configuracion();
        String prefix = "TIC_SOURCE_" + ref.toUpperCase(Locale.ROOT) + "_";
        String host = required(prefix + "HOST"), port = required(prefix + "PORT"), database = required(prefix + "DATABASE");
        if (!host.equals(fuente.configuration().get("host")) || !port.equals(fuente.configuration().get("port"))
                || !database.equals(fuente.configuration().get("database"))) throw configuracion();
        String ssl = entorno.getProperty(prefix + "SSLMODE", "verify-full");
        if (!Set.of("disable", "require", "verify-ca", "verify-full").contains(ssl)) throw configuracion();
        try {
            int number = Integer.parseInt(port);
            if (number < 1 || number > 65535) throw configuracion();
            URI uri = new URI("postgresql", null, host, number, null, null, null);
            if (uri.getHost() == null) throw configuracion();
            Properties properties = new Properties();
            properties.setProperty("user", required(prefix + "USER"));
            properties.setProperty("password", required(prefix + "PASSWORD"));
            properties.setProperty("sslmode", ssl);
            properties.setProperty("connectTimeout", Integer.toString(limites.getConnectTimeoutSeconds()));
            properties.setProperty("socketTimeout", Integer.toString(limites.getSocketTimeoutSeconds()));
            properties.setProperty("ApplicationName", "TIC-M1-metadata");
            return new Acceso("jdbc:" + uri.toASCIIString() + "/"
                    + URLEncoder.encode(database, StandardCharsets.UTF_8).replace("+", "%20"), properties);
        } catch (URISyntaxException | NumberFormatException error) { throw configuracion(); }
    }
    private String required(String key) {
        String value = entorno.getProperty(key);
        if (value == null || value.isBlank()) throw configuracion();
        return value;
    }
    private static DescubrimientoFallidoException configuracion() {
        return new DescubrimientoFallidoException(Codigo.CONFIGURACION_ORIGEN);
    }
    record Acceso(String url, Properties properties) {
        @Override public String toString() { return "AccesoPostgres[protegido]"; }
    }
}
