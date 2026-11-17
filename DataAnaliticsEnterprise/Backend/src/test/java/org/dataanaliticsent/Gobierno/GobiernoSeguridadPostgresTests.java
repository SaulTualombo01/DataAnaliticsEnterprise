package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * M3 con la seguridad REAL del tronco común: sesión TICSESSION, CSRF y RBAC por proyecto con los
 * permisos m3.* de V34. Solo el catálogo de M1 sigue simulado (todavía no está en el tronco).
 */
@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.session.cookie.secure=false", "tic.auth.login-ip-limit=2000"})
class GobiernoSeguridadPostgresTests {
    private static final String CLAVE = "Frase sintetica de gobierno 2026";

    @TestConfiguration
    static class CatalogoDePrueba {
        /** M1 aún no está integrado en el tronco: todo activo se considera existente en la prueba. */
        @Bean @Primary PuertoCatalogo catalogoDePrueba() { return (proyecto, activo) -> true; }
    }

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private record Contexto(UUID org, UUID proyecto) { }

    private Contexto proyecto() {
        UUID org = UUID.randomUUID();
        UUID proyecto = UUID.randomUUID();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,:n)").param("id", org).param("n", "Empresa " + org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:o,'Comercio electrónico')")
                .param("id", proyecto).param("o", org).update();
        return new Contexto(org, proyecto);
    }

    /** Cuenta activa con membresía en la empresa y el proyecto, y el rol funcional con alcance PROJECT. */
    private String cuenta(Contexto c, String rol) {
        UUID id = UUID.randomUUID();
        String email = id + "@example.test";
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:e,'Prueba M3')").param("id", id).param("e", email).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:h)").param("id", id).param("h", encoder.encode(CLAVE)).update();
        UUID mo = UUID.randomUUID();
        UUID mp = UUID.randomUUID();
        UUID asignacion = UUID.randomUUID();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:u,:o,:u)")
                .param("id", mo).param("u", id).param("o", c.org()).update();
        jdbc.sql("""
                INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by)
                VALUES(:id,:mo,:u,:o,:p,:u)""").param("id", mp).param("mo", mo).param("u", id).param("o", c.org())
                .param("p", c.proyecto()).update();
        jdbc.sql("""
                INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by)
                SELECT :id,:u,id,'PROJECT',:mp,:p,:u FROM autorizacion.rol WHERE code=:rol""")
                .param("id", asignacion).param("u", id).param("mp", mp).param("p", c.proyecto()).param("rol", rol).update();
        jdbc.sql("INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope) VALUES(:id,:a,:p,'PROJECT')")
                .param("id", UUID.randomUUID()).param("a", asignacion).param("p", c.proyecto()).update();
        return email;
    }

    @Test
    void sinSesionLasRutasDeM3Responden401() throws Exception {
        try (var anonimo = new Navegador()) {
            var r = anonimo.get("/api/v1/projects/" + UUID.randomUUID() + "/governance-policies");
            assertEquals(401, r.statusCode(), r.body());
            assertEquals("AUTHENTICATION_REQUIRED", cuerpo(r).get("code"));
        }
    }

    @Test
    void elResponsableDeGobiernoTrabajaConSuSesionYQuedaComoActor() throws Exception {
        Contexto c = proyecto();
        String base = "/api/v1/projects/" + c.proyecto();
        try (var gobierno = new Navegador()) {
            gobierno.login(cuenta(c, "RESPONSABLE_GOBIERNO"));
            String crear = "{\"name\":\"Datos personales\",\"category\":\"PRIVACIDAD\",\"owner_id\":\"" + UUID.randomUUID() + "\"}";

            assertEquals(403, gobierno.raw("POST", base + "/governance-policies", crear, Map.of()).statusCode(), "sin CSRF");
            var creada = gobierno.write("POST", base + "/governance-policies", crear);
            assertEquals(201, creada.statusCode(), creada.body());
            assertEquals("\"0\"", creada.headers().firstValue("ETag").orElseThrow());

            String actor = (String) datos(creada).get("created_by");
            var eventos = gobierno.get(base + "/audit-events?action=m3.policies.create");
            assertEquals(200, eventos.statusCode(), eventos.body());
            assertTrue(eventos.body().contains("\"actor_id\":\"" + actor + "\""));

            String activo = UUID.randomUUID().toString();
            var ficha = gobierno.write("PUT", base + "/assets/" + activo + "/governance-profile",
                    "{\"owner_id\":\"" + actor + "\",\"classification\":\"CONFIDENCIAL\",\"reason\":\"Alta\"}");
            assertEquals(201, ficha.statusCode(), ficha.body());
            assertEquals(201, gobierno.write("POST", base + "/assets/" + activo + "/compliance-evaluations", null).statusCode());
        }
    }

    @Test
    void elAnalistaDeNegocioSoloLeeYOtroProyectoNoVeNada() throws Exception {
        Contexto c = proyecto();
        Contexto otro = proyecto();
        String base = "/api/v1/projects/" + c.proyecto();
        String activo = UUID.randomUUID().toString();
        try (var gobierno = new Navegador(); var analista = new Navegador(); var ajeno = new Navegador()) {
            gobierno.login(cuenta(c, "RESPONSABLE_GOBIERNO"));
            analista.login(cuenta(c, "ANALISTA_NEGOCIO"));
            ajeno.login(cuenta(otro, "RESPONSABLE_GOBIERNO"));
            assertEquals(201, gobierno.write("POST", base + "/assets/" + activo + "/compliance-evaluations", null).statusCode());

            // M4 consume el cumplimiento con el rol de analista.
            var cumplimiento = analista.get(base + "/assets/" + activo + "/compliance");
            assertEquals(200, cumplimiento.statusCode(), cumplimiento.body());
            assertEquals("NOT_APPLICABLE", datos(cumplimiento).get("status"));
            assertEquals(200, analista.get(base + "/governance-policies").statusCode());

            var crear = analista.write("POST", base + "/governance-policies",
                    "{\"name\":\"X\",\"category\":\"OTRA\",\"owner_id\":\"" + UUID.randomUUID() + "\"}");
            assertEquals(403, crear.statusCode(), crear.body());
            assertEquals("ACCESS_DENIED", cuerpo(crear).get("code"));
            assertEquals(403, analista.get(base + "/audit-events").statusCode());

            assertEquals(403, ajeno.get(base + "/governance-policies").statusCode());
            assertEquals(403, ajeno.get(base + "/assets/" + activo + "/compliance").statusCode());
        }
    }

    private Map<String, Object> cuerpo(HttpResponse<String> r) {
        return json.readValue(r.body(), new TypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> datos(HttpResponse<String> r) { return (Map<String, Object>) cuerpo(r).get("data"); }

    /** Navegador mínimo con cookies, como el de las pruebas HTTP del tronco común. */
    private class Navegador implements AutoCloseable {
        final HttpClient cliente = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> raw(String metodo, String ruta, String contenido, Map<String, String> cabeceras) throws Exception {
            var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ruta)).timeout(Duration.ofSeconds(20));
            if (contenido != null) b.header("Content-Type", "application/json");
            cabeceras.forEach(b::header);
            return cliente.send(b.method(metodo, contenido == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(contenido)).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> get(String ruta) throws Exception { return raw("GET", ruta, null, Map.of()); }

        Map<String, String> csrf() throws Exception {
            var token = datos(get("/api/v1/auth/csrf"));
            return Map.of((String) token.get("header_name"), (String) token.get("token"));
        }

        HttpResponse<String> write(String metodo, String ruta, String contenido) throws Exception {
            return raw(metodo, ruta, contenido, csrf());
        }

        void login(String email) throws Exception {
            var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded");
            csrf().forEach(b::header);
            String form = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(CLAVE, StandardCharsets.UTF_8);
            var r = cliente.send(b.POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode(), r.body());
        }

        @Override public void close() { cliente.close(); }
    }
}
