package org.dataanaliticsent.identidad;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.servlet.session.cookie.secure=false","tic.auth.login-ip-limit=500"})
@Import(AutenticacionHttpPostgresTests.Config.class)
class AutenticacionHttpPostgresTests {
    private static final String PASSWORD="PruebaSegura!2026";
    private static final String NEW_PASSWORD="NuevaPruebaSegura!2026";
    @Value("${local.server.port}") int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired RelojPrueba reloj;

    @BeforeEach void resetReloj() { reloj.offset.set(Duration.ZERO); }
    @AfterEach void restablecerReloj() { reloj.offset.set(Duration.ZERO); }

    @Test void accesoAnonimoNoPuedeSuplantarActorYErroresSonCorrelacionados() throws Exception {
        try(Browser b=new Browser()) {
            var response=b.request("GET","/api/v1/auth/me",null,null,Map.of("X-User-Id",UUID.randomUUID().toString(),"X-Request-Id","invalido"));
            assertEquals(401,response.statusCode()); assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/problem+json"));
            var body=body(response); UUID.fromString((String)body.get("request_id"));
            assertEquals(body.get("request_id"),response.headers().firstValue("X-Request-Id").orElseThrow());
            assertFalse(body.containsKey("data")); assertFalse(response.body().contains("password_hash"));
        }
    }

    @Test void loginUsaCookieHttpOnlyYRenuevaLaSesionSinAceptarIdentidadDelCliente() throws Exception {
        Cuenta cuenta=cuenta(false);
        try(Browser b=new Browser()) {
            var csrfResponse=b.get("/api/v1/auth/csrf");
            assertTrue(csrfResponse.headers().allValues("Set-Cookie").stream().anyMatch(c->c.toLowerCase().contains("httponly")&&c.toLowerCase().contains("samesite=lax")));
            String original=b.cookie();
            var token=data(csrfResponse);
            assertEquals(200,b.login(cuenta.email(),PASSWORD,token).statusCode());
            assertNotEquals(original,b.cookie());
            var me=b.request("GET","/api/v1/auth/me",null,null,Map.of("X-User-Id",UUID.randomUUID().toString()));
            assertEquals(200,me.statusCode()); assertEquals(cuenta.id().toString(),data(me).get("id"));
            assertFalse(me.body().contains(PASSWORD)); assertFalse(me.body().contains("bcrypt"));
            assertEquals(401,getConCookie(original).statusCode());
        }
    }

    @Test void csrfProtegeElLoginYCierreAutenticadoPeroCerrarSinSesionEsIdempotente() throws Exception {
        Cuenta cuenta=cuenta(false);
        try(Browser b=new Browser()) {
            assertEquals(403,b.postForm("/api/v1/auth/login",form(cuenta.email(),PASSWORD),null).statusCode());
            assertEquals(200,b.login(cuenta.email(),PASSWORD).statusCode());
            String cookie=b.cookie();
            assertEquals(403,b.postForm("/api/v1/auth/logout","",null).statusCode());
            assertEquals(200,b.get("/api/v1/auth/me").statusCode());
            assertEquals(204,b.postForm("/api/v1/auth/logout","",b.csrf()).statusCode());
            assertEquals(401,b.get("/api/v1/auth/me").statusCode());
            assertEquals(401,getConCookie(cookie).statusCode());
            assertEquals(204,b.postForm("/api/v1/auth/logout","",null).statusCode());
        }
    }

    @Test void loginRechazaSeleccionDeActorYParametrosDuplicados() throws Exception {
        Cuenta cuenta=cuenta(false);
        try(Browser b=new Browser()) {
            assertEquals(400,b.postForm("/api/v1/auth/login",form(cuenta.email(),PASSWORD)+"&user_id="+UUID.randomUUID(),b.csrf()).statusCode());
            assertEquals(400,b.postForm("/api/v1/auth/login",form(cuenta.email(),PASSWORD)+"&email=otra%40example.test",b.csrf()).statusCode());
            assertEquals(401,b.get("/api/v1/auth/me").statusCode());
        }
    }

    @Test void credencialesIncorrectasCuentaInactivaYCuentaAusenteNoSeDistinguen() throws Exception {
        Cuenta cuenta=cuenta(false);
        try(Browser b=new Browser()) {
            var incorrecta=b.login(cuenta.email(),"ClaveIncorrecta!2026");
            var ausente=b.login(UUID.randomUUID()+"@example.test",PASSWORD);
            jdbc.sql("UPDATE identidad.usuario SET status='inactive' WHERE id=:id").param("id",cuenta.id()).update();
            var inactiva=b.login(cuenta.email(),PASSWORD);
            for(var r:java.util.List.of(incorrecta,ausente,inactiva)) {
                assertEquals(401,r.statusCode()); assertEquals("INVALID_CREDENTIALS",body(r).get("code"));
                assertEquals("Credenciales inválidas",body(r).get("detail"));
            }
        }
    }

    @Test void desactivarYReactivarCuentaNoResucitaUnaSesionAnterior() throws Exception {
        Cuenta cuenta=cuenta(false);
        try(Browser b=new Browser()) {
            assertEquals(200,b.login(cuenta.email(),PASSWORD).statusCode());
            jdbc.sql("UPDATE identidad.usuario SET status='inactive' WHERE id=:id").param("id",cuenta.id()).update();
            jdbc.sql("UPDATE identidad.usuario SET status='active' WHERE id=:id").param("id",cuenta.id()).update();
            assertEquals(401,b.get("/api/v1/auth/me").statusCode());
        }
    }

    @Test void cambioDeClaveRevocaTodasLasSesionesYNoExponeElHash() throws Exception {
        Cuenta cuenta=cuenta(false);
        try(Browser primero=new Browser();Browser segundo=new Browser()) {
            assertEquals(200,primero.login(cuenta.email(),PASSWORD).statusCode());
            assertEquals(200,segundo.login(cuenta.email(),PASSWORD).statusCode());
            var cambio=primero.postJson("/api/v1/auth/password",json.writeValueAsString(Map.of("current_password",PASSWORD,"new_password",NEW_PASSWORD)),primero.csrf());
            assertEquals(204,cambio.statusCode());
            assertEquals(401,primero.get("/api/v1/auth/me").statusCode());
            assertEquals(401,segundo.get("/api/v1/auth/me").statusCode());
            assertEquals(401,primero.login(cuenta.email(),PASSWORD).statusCode());
            assertEquals(200,primero.login(cuenta.email(),NEW_PASSWORD).statusCode());
            String hash=jdbc.sql("SELECT password_hash FROM identidad.credencial_local WHERE user_id=:id").param("id",cuenta.id()).query(String.class).single();
            assertNotEquals(NEW_PASSWORD,hash); assertTrue(encoder.matches(NEW_PASSWORD,hash));
        }
    }

    @Test void cambioDeClaveRechazaCamposAjenosYPoliticaInvalida() throws Exception {
        Cuenta cuenta=cuenta(false);
        try(Browser b=new Browser()) {
            assertEquals(200,b.login(cuenta.email(),PASSWORD).statusCode());
            assertEquals(400,b.postJson("/api/v1/auth/password",json.writeValueAsString(Map.of("current_password",PASSWORD,"new_password","corta")),b.csrf()).statusCode());
            assertEquals(400,b.postJson("/api/v1/auth/password",json.writeValueAsString(Map.of("current_password",PASSWORD,"new_password",NEW_PASSWORD,"user_id",UUID.randomUUID())),b.csrf()).statusCode());
            assertEquals(200,b.get("/api/v1/auth/me").statusCode());
        }
    }

    @Test void administradorPuedeRevocarPeroUsuarioNormalNoPuede() throws Exception {
        Cuenta admin=cuenta(true),user=cuenta(false);
        try(Browser a=new Browser();Browser u=new Browser()) {
            assertEquals(200,a.login(admin.email(),PASSWORD).statusCode()); assertEquals(200,u.login(user.email(),PASSWORD).statusCode());
            assertEquals(403,u.postForm("/api/v1/admin/users/"+admin.id()+"/sessions/revoke","",u.csrf()).statusCode());
            assertEquals(204,a.postForm("/api/v1/admin/users/"+user.id()+"/sessions/revoke","",a.csrf()).statusCode());
            assertEquals(401,u.get("/api/v1/auth/me").statusCode());
            assertEquals(200,a.get("/api/v1/auth/me").statusCode());
        }
    }

    @Test void expiracionAbsolutaObligaAEntrarDeNuevo() throws Exception {
        Cuenta user=cuenta(false);
        try(Browser b=new Browser()) {
            assertEquals(200,b.login(user.email(),PASSWORD).statusCode());
            reloj.offset.set(Duration.ofHours(9));
            assertEquals(401,b.get("/api/v1/auth/me").statusCode());
        }
    }

    @Test void permisoSelfRetiradoSeAplicaAunqueLaSesionSigaValida() throws Exception {
        Cuenta user=cuenta(false);
        try(Browser b=new Browser()) {
            assertEquals(200,b.login(user.email(),PASSWORD).statusCode());
            try {
                jdbc.sql("UPDATE autorizacion.permiso SET available=false WHERE code='tc.self.profile.read'").update();
                assertEquals(403,b.get("/api/v1/auth/me").statusCode());
            } finally { jdbc.sql("UPDATE autorizacion.permiso SET available=true WHERE code='tc.self.profile.read'").update(); }
        }
    }

    @Test void intentosReiteradosTienenLimiteUniforme() throws Exception {
        String ausente=UUID.randomUUID()+"@example.test";
        try(Browser b=new Browser()) {
            for(int i=0;i<5;i++) assertEquals(401,b.login(ausente,PASSWORD).statusCode());
            var limitado=b.login(ausente,PASSWORD);
            assertEquals(429,limitado.statusCode()); assertEquals("LOGIN_RATE_LIMITED",body(limitado).get("code"));
            assertTrue(limitado.headers().firstValue("Retry-After").isPresent());
        }
    }

    private Cuenta cuenta(boolean admin) {
        UUID id=UUID.randomUUID(); String email=id+"@example.test";
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba de acceso')").param("id",id).param("email",email).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)").param("id",id).param("hash",encoder.encode(PASSWORD)).update();
        if(admin) jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'")
                .param("id",UUID.randomUUID()).param("user",id).update();
        return new Cuenta(id,email);
    }
    private record Cuenta(UUID id,String email) { }
    private String base() { return "http://127.0.0.1:"+port; }
    private Map<String,Object> body(HttpResponse<String> r) { return json.readValue(r.body(),new TypeReference<Map<String,Object>>() { }); }
    @SuppressWarnings("unchecked") private Map<String,Object> data(HttpResponse<String> r) { return (Map<String,Object>)body(r).get("data"); }
    private static String form(String email,String password) { return "email="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8); }
    private HttpResponse<String> getConCookie(String cookie) throws Exception {
        try(HttpClient client=HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/auth/me")).header("Cookie","TICSESSION="+cookie).GET().build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    private class Browser implements AutoCloseable {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> request(String method,String path,String value,String type,Map<String,String> headers) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(20));
            if(type!=null) builder.header("Content-Type",type);
            headers.forEach(builder::header);
            builder.method(method,value==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(value));
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(String path) throws Exception { return request("GET",path,null,null,Map.of()); }
        Map<String,Object> csrf() throws Exception { var r=get("/api/v1/auth/csrf"); assertEquals(200,r.statusCode()); return data(r); }
        String cookie() { return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("TICSESSION")).findFirst().orElseThrow().getValue(); }
        HttpResponse<String> login(String email,String password) throws Exception { return login(email,password,csrf()); }
        HttpResponse<String> login(String email,String password,Map<String,Object> token) throws Exception { return postForm("/api/v1/auth/login",form(email,password),token); }
        HttpResponse<String> postForm(String path,String value,Map<String,Object> token) throws Exception { return request("POST",path,value,"application/x-www-form-urlencoded",token==null?Map.of():Map.of((String)token.get("header_name"),(String)token.get("token"))); }
        HttpResponse<String> postJson(String path,String value,Map<String,Object> token) throws Exception { return request("POST",path,value,"application/json",Map.of((String)token.get("header_name"),(String)token.get("token"))); }
        public void close() { client.close(); }
    }
    @TestConfiguration static class Config { @Bean @Primary RelojPrueba relojPrueba() { return new RelojPrueba(); } }
    static class RelojPrueba extends Clock {
        final AtomicReference<Duration> offset=new AtomicReference<>(Duration.ZERO);
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.now().plus(offset.get()); }
    }
}
