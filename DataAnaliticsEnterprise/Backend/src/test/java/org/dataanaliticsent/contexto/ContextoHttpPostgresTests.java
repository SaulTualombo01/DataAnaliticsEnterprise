package org.dataanaliticsent.contexto;

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
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","tic.auth.login-ip-limit=1000"})
class ContextoHttpPostgresTests {
    private static final List<Map<String,Object>> EVIDENCE=new ArrayList<>();
    private static final String PASSWORD="Clave sintetica de pruebas 2026";
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Test void sesionRealDesignaYRevocaDelegacionSinCambiarLaCookieDelEmpleado() throws Exception {
        var global=cuenta(true); var empleado=cuenta(false);
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(global); user.login(empleado);
            var created=admin.post("/api/v1/admin/organizations",Map.of("name","Empresa HTTP","reason","Creación"));
            assertEquals(201,created.statusCode()); var org=(String)data(created).get("id");
            assertEquals(404,user.get("/api/v1/organizations/"+org).statusCode());
            var assigned=admin.post("/api/v1/admin/organizations/"+org+"/administrators",Map.of("user_id",empleado.id().toString(),"reason","Designación"));
            assertEquals(201,assigned.statusCode()); String assignment=(String)data(assigned).get("id");
            assertEquals(200,user.get("/api/v1/organizations/"+org).statusCode());
            var project=user.post("/api/v1/organizations/"+org+"/projects",Map.of("name","Proyecto HTTP","reason","Creación"));
            assertEquals(201,project.statusCode()); assertEquals(org,data(project).get("organization_id"));
            assertEquals(204,admin.post("/api/v1/admin/organizations/"+org+"/administrators/"+assignment+"/revocations",Map.of("reason","Retiro")).statusCode());
            assertEquals(404,user.get("/api/v1/organizations/"+org+"/projects").statusCode());
            assertEquals(200,user.get("/api/v1/auth/me").statusCode());
            for(var response:List.of(created,assigned,project)) {
                UUID resource=UUID.fromString((String)data(response).get("id"));
                UUID request=jdbc.sql("SELECT request_id FROM auditoria.evento WHERE resource_id=:id ORDER BY occurred_at LIMIT 1").param("id",resource).query(UUID.class).single();
                assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(),request.toString());
            }
        }
    }
    @Test void mantieneSesionCsrfYNoPermiteSuplantarActor() throws Exception {
        var global=cuenta(true); var empleado=cuenta(false);
        try(var anon=new Browser();var admin=new Browser();var user=new Browser()) {
            assertEquals(401,anon.get("/api/v1/admin/organizations").statusCode());
            admin.login(global);
            assertEquals(403,admin.raw("POST","/api/v1/admin/organizations",json.writeValueAsString(Map.of("name","Sin CSRF","reason","Prueba")),"application/json",Map.of()).statusCode());
            user.login(empleado);
            var spoof=user.raw("GET","/api/v1/admin/organizations",null,null,Map.of("X-User-Id",global.id().toString(),"X-Role","ADMIN_SISTEMA"));
            assertEquals(403,spoof.statusCode()); assertFalse(spoof.body().contains("SELECT"));
        }
    }
    @Test void administradorDeEmpresaNoCreaOtraEmpresaNiDesignaPrivilegios() throws Exception {
        var global=cuenta(true); var empleado=cuenta(false);
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(global); user.login(empleado);
            String org=(String)data(admin.post("/api/v1/admin/organizations",Map.of("name","Empresa","reason","Creación"))).get("id");
            admin.post("/api/v1/admin/organizations/"+org+"/administrators",Map.of("user_id",empleado.id().toString(),"reason","Designación"));
            assertEquals(403,user.post("/api/v1/admin/organizations",Map.of("name","Ajena","reason","No autorizado")).statusCode());
            assertEquals(403,user.post("/api/v1/admin/organizations/"+org+"/administrators",Map.of("user_id",empleado.id().toString(),"reason","Autoelevación")).statusCode());
            assertEquals(404,user.get("/api/v1/organizations/"+UUID.randomUUID()).statusCode());
        }
    }
    @Test void rechazaParametrosDuplicadosCamposExtraYUuidNoCanonico() throws Exception {
        try(var admin=new Browser()) {
            admin.login(cuenta(true));
            assertEquals(422,admin.get("/api/v1/admin/organizations?page=1&page=2").statusCode());
            assertEquals(422,admin.get("/api/v1/admin/organizations?page_size=101").statusCode());
            assertEquals(422,admin.get("/api/v1/admin/organizations?actor_id="+UUID.randomUUID()).statusCode());
            assertEquals(422,admin.post("/api/v1/admin/organizations",Map.of("name","Empresa","reason","Prueba","actor_id",UUID.randomUUID())).statusCode());
            assertEquals(422,admin.get("/api/v1/organizations/1-1-1-1-1").statusCode());
            assertEquals(422,admin.post("/api/v1/admin/organizations",Map.of("name"," ","reason","Prueba")).statusCode());
            var empty=admin.get("/api/v1/admin/organizations?page=2147483647&page_size=100");
            assertEquals(200,empty.statusCode()); assertTrue(empty.body().contains("\"data\":[]"));
        }
    }
    @Test void conflictoDeNombreNoRevelaSqlYLaPaginacionMantieneElContexto() throws Exception {
        try(var admin=new Browser()) {
            admin.login(cuenta(true)); String org=(String)data(admin.post("/api/v1/admin/organizations",Map.of("name","Empresa","reason","Prueba"))).get("id");
            String path="/api/v1/organizations/"+org+"/projects";
            assertEquals(201,admin.post(path,Map.of("name","Proyecto","reason","Prueba")).statusCode());
            var duplicate=admin.post(path,Map.of("name","Proyecto","reason","Duplicado"));
            assertEquals(409,duplicate.statusCode()); assertFalse(duplicate.body().contains("unique")); assertFalse(duplicate.body().contains("INSERT"));
            var list=admin.get(path+"?page_size=1"); assertEquals(200,list.statusCode());
            assertTrue(list.body().contains("\"returned_count\":1")); assertTrue(list.body().contains("\"has_next\":false"));
        }
    }
    private record Cuenta(UUID id,String email) { }
    @AfterAll static void guardarEvidencia() throws Exception {
        var path=Path.of("target","contract-evidence","contexto.json"); Files.createDirectories(path.getParent());
        Files.writeString(path,new ObjectMapper().writeValueAsString(EVIDENCE));
    }
    private Cuenta cuenta(boolean admin) {
        UUID id=UUID.randomUUID(); String email=id+"@example.test";
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba HTTP')").param("id",id).param("email",email).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)").param("id",id).param("hash",encoder.encode(PASSWORD)).update();
        if(admin) jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'")
                .param("id",UUID.randomUUID()).param("user",id).update();
        return new Cuenta(id,email);
    }
    private Map<String,Object> body(HttpResponse<String> response) { return json.readValue(response.body(),new TypeReference<Map<String,Object>>(){}); }
    @SuppressWarnings("unchecked") private Map<String,Object> data(HttpResponse<String> response) { return (Map<String,Object>)body(response).get("data"); }
    private class Browser implements AutoCloseable {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> raw(String method,String path,String value,String type,Map<String,String> headers) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
            if(type!=null) builder.header("Content-Type",type); headers.forEach(builder::header);
            builder.method(method,value==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(value));
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            if(path.contains("/organizations") && response.statusCode()!=204) {
                String schema=response.statusCode()>=400?"Problem":path.contains("/administrators")?(method.equals("GET")?"AdministratorList":"AdministratorResponse")
                    :path.contains("/projects")?(method.equals("GET")?"ProjectList":"ProjectResponse")
                    :path.split("\\?")[0].equals("/api/v1/admin/organizations") && method.equals("GET")?"OrganizationList":"OrganizationResponse";
                EVIDENCE.add(Map.of("schema",schema,"status",response.statusCode(),"body",body(response),
                        "request_id_header",response.headers().firstValue("X-Request-Id").orElseThrow()));
            }
            return response;
        }
        HttpResponse<String> get(String path) throws Exception { return raw("GET",path,null,null,Map.of()); }
        Map<String,String> csrf() throws Exception {
            var token=data(get("/api/v1/auth/csrf")); return Map.of((String)token.get("header_name"),(String)token.get("token"));
        }
        void login(Cuenta cuenta) throws Exception {
            String form="email="+URLEncoder.encode(cuenta.email(),StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(PASSWORD,StandardCharsets.UTF_8);
            assertEquals(200,raw("POST","/api/v1/auth/login",form,"application/x-www-form-urlencoded",csrf()).statusCode());
        }
        HttpResponse<String> post(String path,Object value) throws Exception { return raw("POST",path,json.writeValueAsString(value),"application/json",csrf()); }
        public void close() { client.close(); }
    }
}
