package org.dataanaliticsent.TroncoComun.identidad;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","tic.auth.login-ip-limit=1000",
    "tic.accounts.manual-delivery-enabled=true","tic.accounts.activation-ip-limit=1000"})
class CuentasHttpPostgresTests {
    private static final String PASSWORD="Frase sintetica HTTP de pruebas 2026";
    private static final List<Map<String,Object>> EVIDENCE=new ArrayList<>();
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    private record Cuenta(UUID id,String email) { }
    private Cuenta cuenta(boolean admin) {
        UUID id=UUID.randomUUID(); String email=id+"@example.test";
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba HTTP')").param("id",id).param("email",email).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)").param("id",id).param("hash",encoder.encode(PASSWORD)).update();
        if(admin) jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'")
            .param("id",UUID.randomUUID()).param("user",id).update(); return new Cuenta(id,email);
    }
    private Map<String,Object> body(HttpResponse<String> res) { return json.readValue(res.body(),new TypeReference<Map<String,Object>>(){}); }
    @SuppressWarnings("unchecked") private Map<String,Object> data(HttpResponse<String> res) { return (Map<String,Object>)body(res).get("data"); }
    private String org(Browser admin) throws Exception { return (String)data(admin.post("/api/v1/admin/organizations",Map.of("name","Empresa HTTP","reason","Prueba"))).get("id"); }
    private HttpResponse<String> invite(Browser admin,String org,String email) throws Exception {
        return admin.post("/api/v1/organizations/"+org+"/invitations",Map.of("email",email,"display_name","Empleado invitado","reason","Prueba"));
    }
    @Test void administradorInvitaYElEmpleadoActivaSinSesionAutomaticaNiRolesDeProyecto() throws Exception {
        try(var admin=new Browser();var anon=new Browser();var user=new Browser()) {
            admin.login(cuenta(true)); String org=org(admin); String email=UUID.randomUUID()+"@example.test";
            var created=invite(admin,org,email); assertEquals(201,created.statusCode()); String token=(String)data(created).get("activation_token");
            assertEquals("no-store",created.headers().firstValue("Cache-Control").orElseThrow());
            var listed=admin.get("/api/v1/organizations/"+org+"/invitations"); assertEquals(200,listed.statusCode());
            assertFalse(listed.body().contains(token)); assertFalse(listed.body().contains("token_hash"));
            var activated=anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token",token,"new_password",PASSWORD));
            assertEquals(201,activated.statusCode()); assertFalse(activated.body().contains("password")); assertFalse(activated.body().contains(token));
            assertEquals(401,anon.get("/api/v1/auth/me").statusCode());
            var account=new Cuenta(UUID.fromString((String)data(activated).get("id")),email); user.login(account);
            assertEquals(200,user.get("/api/v1/auth/me").statusCode());
            assertEquals(404,user.get("/api/v1/organizations/"+org+"/users").statusCode());
            assertEquals(403,user.get("/api/v1/admin/users").statusCode());
            assertEquals(422,anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token",token,"new_password",PASSWORD)).statusCode());
            var list=admin.get("/api/v1/admin/users?page_size=1"); assertEquals(200,list.statusCode()); assertFalse(list.body().contains("password_hash"));
        }
    }
    @Test void exigeCsrfTambienEnActivacionYRechazaCambioDeEmpresaORolEnElCuerpo() throws Exception {
        try(var admin=new Browser();var anon=new Browser()) {
            admin.login(cuenta(true)); String org=org(admin); var created=invite(admin,org,UUID.randomUUID()+"@example.test"); String token=(String)data(created).get("activation_token");
            var input=Map.of("activation_token",token,"new_password",PASSWORD);
            assertEquals(403,anon.raw("POST","/api/v1/auth/invitations/accept",json.writeValueAsString(input),"application/json",Map.of()).statusCode());
            assertEquals(422,anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token",token,"new_password",PASSWORD,"organization_id",UUID.randomUUID())).statusCode());
            assertEquals(422,anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token",token,"new_password",PASSWORD,"role","ADMIN_SISTEMA")).statusCode());
            assertEquals(422,anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token",token,"new_password","corta")).statusCode());
            assertEquals(201,anon.post("/api/v1/auth/invitations/accept",input).statusCode());
        }
    }
    @Test void administradorEmpresarialSoloInvitaConsultaYDesactivaSuEmpresaConRevocacionReal() throws Exception {
        var root=cuenta(true); var empresa=cuenta(false);
        try(var admin=new Browser();var manager=new Browser();var anon=new Browser();var user=new Browser()) {
            admin.login(root); String org=org(admin),other=org(admin);
            assertEquals(201,admin.post("/api/v1/admin/organizations/"+org+"/administrators",Map.of("user_id",empresa.id(),"reason","Delegación")).statusCode());
            manager.login(empresa); String email=UUID.randomUUID()+"@example.test"; var invitation=invite(manager,org,email); assertEquals(201,invitation.statusCode());
            assertEquals(404,invite(manager,other,UUID.randomUUID()+"@example.test").statusCode());
            assertEquals(404,manager.get("/api/v1/organizations/"+other+"/users").statusCode());
            var account=data(anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token",data(invitation).get("activation_token"),"new_password",PASSWORD)));
            String id=(String)account.get("id"); user.login(new Cuenta(UUID.fromString(id),email));
            String path="/api/v1/organizations/"+org+"/users/"+id+"/deactivations";
            assertEquals(403,manager.raw("POST",path,"{\"reason\":\"Baja\"}","application/json",Map.of()).statusCode());
            var deactivated=manager.post(path,Map.of("reason","Baja del empleado")); assertEquals(204,deactivated.statusCode());
            assertEquals(401,user.get("/api/v1/auth/me").statusCode()); assertEquals(401,user.loginStatus(new Cuenta(UUID.fromString(id),email)));
            assertEquals(204,manager.post(path,Map.of("reason","Reintento")).statusCode());
            assertEquals(409,manager.post("/api/v1/organizations/"+org+"/users/"+empresa.id()+"/deactivations",Map.of("reason","Autobaja no permitida")).statusCode());
            String correlation=jdbc.sql("SELECT request_id::text FROM auditoria.evento WHERE resource_id=:id AND action='tc.user.deactivate'").param("id",UUID.fromString(id)).query(String.class).single();
            assertEquals(correlation,deactivated.headers().firstValue("X-Request-Id").orElseThrow());
        }
    }
    @Test void revocarInvitacionEsIdempotenteYErroresNoDistinguenTokenDesconocidoDeRevocado() throws Exception {
        try(var admin=new Browser();var anon=new Browser()) {
            admin.login(cuenta(true)); String org=org(admin); var created=invite(admin,org,UUID.randomUUID()+"@example.test");
            @SuppressWarnings("unchecked") var invitation=(Map<String,Object>)data(created).get("invitation");
            String path="/api/v1/organizations/"+org+"/invitations/"+invitation.get("id")+"/revocations";
            assertEquals(204,admin.post(path,Map.of("reason","Retiro")).statusCode()); assertEquals(204,admin.post(path,Map.of("reason","Reintento")).statusCode());
            var revoked=anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token",data(created).get("activation_token"),"new_password",PASSWORD));
            var unknown=anon.post("/api/v1/auth/invitations/accept",Map.of("activation_token","A".repeat(43),"new_password",PASSWORD));
            assertEquals(422,revoked.statusCode()); assertEquals(body(revoked).get("code"),body(unknown).get("code")); assertEquals(body(revoked).get("detail"),body(unknown).get("detail"));
        }
    }
    @Test void rutasNuevasRechazanAnonimosSuplantacionCamposExtraYParametrosInvalidos() throws Exception {
        var global=cuenta(true); var ordinary=cuenta(false);
        try(var admin=new Browser();var user=new Browser();var anon=new Browser()) {
            assertEquals(401,anon.get("/api/v1/admin/users").statusCode()); admin.login(global); user.login(ordinary); String org=org(admin);
            assertEquals(403,user.raw("GET","/api/v1/admin/users",null,null,Map.of("X-User-Id",global.id().toString(),"X-Role","ADMIN_SISTEMA")).statusCode());
            assertEquals(422,admin.get("/api/v1/admin/users?page=1&page=2").statusCode()); assertEquals(422,admin.get("/api/v1/admin/users?page_size=101").statusCode());
            assertEquals(422,admin.get("/api/v1/organizations/1-1-1-1-1/users").statusCode());
            assertEquals(422,admin.get("/api/v1/organizations/"+org+"/users?organization_id="+UUID.randomUUID()).statusCode());
            assertEquals(422,admin.post("/api/v1/organizations/"+org+"/invitations",Map.of("email",UUID.randomUUID()+"@example.test","display_name","X","reason","Prueba","actor_id",global.id())).statusCode());
            var large=anon.raw("POST","/api/v1/auth/invitations/accept","x".repeat(8193),"application/json",anon.csrf()); assertEquals(413,large.statusCode());
            assertEquals(200,admin.get("/api/v1/admin/users?page=2147483647&page_size=100").statusCode());
        }
    }
    @Test void ultimoAdministradorNoPuedeDesactivarseYUnRelevoPermiteAutobaja() throws Exception {
        jdbc.sql("UPDATE identidad.usuario SET status='inactive' WHERE id IN (SELECT ar.user_id FROM autorizacion.asignacion_rol ar JOIN autorizacion.rol r ON r.id=ar.role_id WHERE r.code='ADMIN_SISTEMA' AND ar.status='active')").update();
        var first=cuenta(true);
        try(var admin=new Browser()) {
            admin.login(first); String path="/api/v1/admin/users/"+first.id()+"/deactivations";
            assertEquals(409,admin.post(path,Map.of("reason","Sin relevo")).statusCode()); cuenta(true);
            assertEquals(204,admin.post(path,Map.of("reason","Con relevo")).statusCode()); assertEquals(401,admin.get("/api/v1/auth/me").statusCode());
        }
    }
    @AfterAll static void evidence() throws Exception {
        var path=Path.of("target","contract-evidence","cuentas.json"); Files.createDirectories(path.getParent());
        Files.writeString(path,new ObjectMapper().writeValueAsString(EVIDENCE));
    }
    private class Browser implements AutoCloseable {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> raw(String method,String path,String value,String type,Map<String,String> headers) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
            if(type!=null) b.header("Content-Type",type); headers.forEach(b::header);
            b.method(method,value==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(value));
            var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
            boolean accounts=path.contains("/users") || path.contains("/invitations");
            // No se guarda el recibo de creación porque contiene el token de entrega una sola vez.
            boolean secret=method.equals("POST") && path.matches("/api/v1/organizations/[^/]+/invitations") && r.statusCode()<400;
            if(accounts && r.statusCode()!=204 && !secret) {
                String schema=r.statusCode()>=400?"Problem":path.contains("/invitations/accept")?"UserResponse":path.contains("/invitations")?"InvitationList":"UserList";
                EVIDENCE.add(Map.of("schema",schema,"body",body(r),"status",r.statusCode(),"request_id_header",r.headers().firstValue("X-Request-Id").orElseThrow()));
            }
            return r;
        }
        HttpResponse<String> get(String path) throws Exception { return raw("GET",path,null,null,Map.of()); }
        Map<String,String> csrf() throws Exception { var d=data(get("/api/v1/auth/csrf")); return Map.of((String)d.get("header_name"),(String)d.get("token")); }
        int loginStatus(Cuenta c) throws Exception {
            return raw("POST","/api/v1/auth/login","email="+URLEncoder.encode(c.email(),StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(PASSWORD,StandardCharsets.UTF_8),"application/x-www-form-urlencoded",csrf()).statusCode();
        }
        void login(Cuenta c) throws Exception { assertEquals(200,loginStatus(c)); }
        HttpResponse<String> post(String path,Object value) throws Exception { return raw("POST",path,json.writeValueAsString(value),"application/json",csrf()); }
        public void close() { client.close(); }
    }
}
