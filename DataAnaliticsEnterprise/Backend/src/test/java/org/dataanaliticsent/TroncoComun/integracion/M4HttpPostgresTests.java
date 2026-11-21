package org.dataanaliticsent.TroncoComun.integracion;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Controlador sustituto exclusivo de pruebas: no implementa KPIs ni se publica en el JAR. */
@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.servlet.session.cookie.secure=false","tic.auth.login-ip-limit=500"})
@Import(M4HttpPostgresTests.Config.class)
class M4HttpPostgresTests {
    static final String PASSWORD="PruebaSegura!2026";
    @Value("${local.server.port}") int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    private String ruta(UUID proyecto){return "/api/v1/projects/"+proyecto+"/analytics/kpis/evaluate";}

    @Test void anonimoYCsrfSeControlanEnLasRutasExactas() throws Exception {
        try(var b=new Browser()) {
            assertEquals(401,b.get("/api/v1/analitica/ping").statusCode());
            assertEquals(403,b.request("POST",ruta(UUID.randomUUID()),"{}","application/json",Map.of()).statusCode());
            assertEquals(401,b.postJson(ruta(UUID.randomUUID()),"{}",b.csrf()).statusCode());
        }
    }
    @Test void sesionAnalistaUsaProyectoYRevocacionActuales() throws Exception {
        var f=fixture("ANALISTA_NEGOCIO");
        try(var b=new Browser()) {
            assertEquals(200,b.login(f.email(),PASSWORD).statusCode());
            assertEquals(200,b.get("/api/v1/analitica/ping").statusCode());
            var token=b.csrf();String cookie=b.cookie();
            assertEquals(403,b.request("POST",ruta(f.project()),"{}","application/json",Map.of()).statusCode());
            assertEquals(200,b.postJson(ruta(f.project()),"{}",token).statusCode());
            assertEquals(403,b.postJson(ruta(UUID.randomUUID()),"{}",token).statusCode());
            jdbc.sql("UPDATE autorizacion.asignacion_rol SET status='inactive',revoked_at=now() WHERE id=:id").param("id",f.assignment()).update();
            assertEquals(403,b.postJson(ruta(f.project()),"{}",token).statusCode());assertEquals(cookie,b.cookie());
        }
    }
    @Test void rolCatalogoNoEvaluaKpisNiSeAbrenRutasVecinas() throws Exception {
        var f=fixture("GESTOR_CATALOGO");
        try(var b=new Browser()) {
            assertEquals(200,b.login(f.email(),PASSWORD).statusCode());var token=b.csrf();
            assertEquals(403,b.postJson(ruta(f.project()),"{}",token).statusCode());
            assertEquals(403,b.get("/api/v1/analitica/ping/extra").statusCode());
            assertEquals(403,b.postJson(ruta(f.project())+"/extra","{}",token).statusCode());
            assertEquals(403,b.get(ruta(f.project())).statusCode());
        }
    }
    @Test void proyectoArchivadoNoPermiteEvaluacionM4() throws Exception {
        var f=fixture("ANALISTA_NEGOCIO");
        try(var b=new Browser()) {
            assertEquals(200,b.login(f.email(),PASSWORD).statusCode());var token=b.csrf();
            assertEquals(200,b.postJson(ruta(f.project()),"{}",token).statusCode());
            jdbc.sql("UPDATE contexto.proyecto SET status='archived' WHERE id=:id").param("id",f.project()).update();
            assertEquals(403,b.postJson(ruta(f.project()),"{}",token).statusCode());
        }
    }
    private Fixture fixture(String role) {
        UUID user=UUID.randomUUID(),org=UUID.randomUUID(),project=UUID.randomUUID(),om=UUID.randomUUID(),pm=UUID.randomUUID(),assignment=UUID.randomUUID();
        String email=user+"@example.test";
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Integracion M4')").param("id",user).param("email",email).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)").param("id",user).param("hash",encoder.encode(PASSWORD)).update();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'M4')").param("id",org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'M4')").param("id",project).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:user)").param("id",om).param("user",user).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by) VALUES(:id,:om,:user,:org,:project,:user)").param("id",pm).param("om",om).param("user",user).param("org",org).param("project",project).update();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT :id,:user,id,'PROJECT',:member,:project,:user FROM autorizacion.rol WHERE code=:role").param("id",assignment).param("user",user).param("member",pm).param("project",project).param("role",role).update();
        jdbc.sql("INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope) VALUES(:id,:assignment,:project,'PROJECT')").param("id",UUID.randomUUID()).param("assignment",assignment).param("project",project).update();
        return new Fixture(email,project,assignment);
    }
    private record Fixture(String email,UUID project,UUID assignment){}
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
        HttpResponse<String> login(String email,String password,Map<String,Object> token) throws Exception { return postForm("/api/v1/auth/login","email="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8),token); }
        HttpResponse<String> postForm(String path,String value,Map<String,Object> token) throws Exception { return request("POST",path,value,"application/x-www-form-urlencoded",token==null?Map.of():Map.of((String)token.get("header_name"),(String)token.get("token"))); }
        HttpResponse<String> postJson(String path,String value,Map<String,Object> token) throws Exception { return request("POST",path,value,"application/json",Map.of((String)token.get("header_name"),(String)token.get("token"))); }
        public void close() { client.close(); }
    }

    @TestConfiguration static class Config {
        @Bean org.ticfixtures.M4Prueba m4Prueba(IdentidadActualApi identidad,AutorizacionApi permisos){
            return new org.ticfixtures.M4Prueba(identidad,permisos);
        }
    }
}
