package org.dataanaliticsent.TroncoComun.contexto;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","tic.auth.login-ip-limit=2000"})
class AdministracionActualHttpPostgresTests {
    private static final String PASSWORD="Frase sintetica HTTP de pruebas 2026";
    private static final List<Map<String,Object>> EVIDENCE=new ArrayList<>();
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    private record Cuenta(UUID id,String email) { }
    private record F(Cuenta root,Cuenta admin,Cuenta user,UUID org,UUID project,UUID companyMember) { }
    private Cuenta cuenta(boolean root) {
        UUID id=UUID.randomUUID(); String email=id+"@example.test";
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:e,'Prueba HTTP')").param("id",id).param("e",email).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:h)").param("id",id).param("h",encoder.encode(PASSWORD)).update();
        if(root) jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:u,id,'PLATFORM',:u FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'").param("id",UUID.randomUUID()).param("u",id).update();
        return new Cuenta(id,email);
    }
    private F fixture() {
        var root=cuenta(true); var admin=cuenta(false); var user=cuenta(false);
        UUID org=UUID.randomUUID(),project=UUID.randomUUID(),cm=UUID.randomUUID(),am=UUID.randomUUID();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'Empresa HTTP')").param("id",org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:o,'Proyecto HTTP')").param("id",project).param("o",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:u,:o,:a)").param("id",cm).param("u",user.id()).param("o",org).param("a",root.id()).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:u,:o,:a)").param("id",am).param("u",admin.id()).param("o",org).param("a",root.id()).update();
        jdbc.sql("INSERT INTO autorizacion.administrador_empresa(id,user_id,organization_id,organization_membership_id,role_id,assigned_by) SELECT :id,:u,:o,:m,id,:a FROM autorizacion.rol WHERE code='ADMIN_EMPRESA'")
            .param("id",UUID.randomUUID()).param("u",admin.id()).param("o",org).param("m",am).param("a",root.id()).update();
        return new F(root,admin,user,org,project,cm);
    }
    private String path(F f) { return "/api/v1/organizations/"+f.org()+"/projects/"+f.project(); }
    private Map<String,Object> body(HttpResponse<String> r) { return json.readValue(r.body(),new TypeReference<Map<String,Object>>(){}); }
    @SuppressWarnings("unchecked") private Map<String,Object> data(HttpResponse<String> r) { return (Map<String,Object>)body(r).get("data"); }
    private HttpResponse<String> ok(HttpResponse<String> r,int status,String schema) {
        assertEquals(status,r.statusCode(),r.body()); assertEquals("no-store",r.headers().firstValue("Cache-Control").orElseThrow());
        assertFalse(r.body().contains("password_hash"));
        if(status!=204) EVIDENCE.add(Map.of("schema",status>=400?"Problem":schema,"body",body(r),"status",status,"request_id_header",r.headers().firstValue("X-Request-Id").orElseThrow()));
        return r;
    }
    private String incorporate(Browser admin,F f) throws Exception {
        return (String)data(ok(admin.post(path(f)+"/memberships",Map.of("user_id",f.user().id(),"reason","Alta explícita")),201,"MemberResponse")).get("id");
    }
    private Map<String,Object> role(String code,String expected) {
        var map=new LinkedHashMap<String,Object>(); map.put("role_code",code); map.put("expected_assignment_id",expected);
        var scope=new LinkedHashMap<String,Object>(); scope.put("resource_scope","PROJECT"); scope.put("resource_id",null);
        map.put("scopes",List.of(scope)); map.put("reason","Asignación explícita"); return map;
    }

    @Test void exigeSesionYNoInfiereAdministracionPorElPerfil() throws Exception {
        F f=fixture();
        try(var anon=new Browser();var user=new Browser()) {
            ok(anon.get("/api/v1/auth/administration"),401,"Problem"); user.login(f.user());
            var res=ok(user.get("/api/v1/auth/administration"),200,"AdminAccessResponse");
            assertEquals(List.of(),data(res).get("platform_permissions")); assertEquals(List.of(),data(res).get("companies"));
            assertFalse(res.body().contains("password")); assertFalse(res.body().contains("token"));
            ok(user.get("/api/v1/auth/administration?organization_id="+f.org()),404,"Problem");
        }
    }
    @Test void empresaDelegadaSeDescubreSinMembresiaDeProyectoYEntregaSoloPermisosVigentes() throws Exception {
        F f=fixture();
        try(var admin=new Browser()) {
            admin.login(f.admin());
            var res=ok(admin.get("/api/v1/auth/administration"),200,"AdminAccessResponse");
            assertEquals(List.of(),data(res).get("platform_permissions"));
            assertTrue(res.body().contains(f.org().toString())); assertFalse(res.body().contains("m1.assets.read"));
            var selected=ok(admin.get("/api/v1/auth/administration?organization_id="+f.org()),200,"AdminAccessResponse");
            assertTrue(selected.body().contains("tc.company.users.invite")); assertTrue(selected.body().contains("tc.company.roles.assign"));
            assertTrue(admin.get("/api/v1/auth/contexts").body().contains("\"data\":[]"));
        }
    }
    @Test void sistemaPuedeElegirEmpresaSinRecibirAccesoFuncional() throws Exception {
        F f=fixture();
        try(var admin=new Browser()) {
            admin.login(f.root());
            var res=ok(admin.get("/api/v1/auth/administration?organization_id="+f.org()),200,"AdminAccessResponse");
            assertTrue(res.body().contains("tc.organizations.create")); assertTrue(res.body().contains("tc.company_administrators.assign"));
            assertFalse(res.body().contains("m1.dictionary.read")); assertTrue(admin.get("/api/v1/auth/contexts").body().contains("\"data\":[]"));
            ok(admin.get("/api/v1/auth/administration?organization_id="+UUID.randomUUID()),404,"Problem");
        }
    }
    @Test void revocacionDeDelegacionConMismaCookieRetiraEmpresaYAcciones() throws Exception {
        F f=fixture();
        try(var admin=new Browser()) {
            admin.login(f.admin()); ok(admin.get("/api/v1/auth/administration?organization_id="+f.org()),200,"AdminAccessResponse");
            jdbc.sql("UPDATE autorizacion.administrador_empresa SET status='inactive',revoked_at=now() WHERE user_id=:u").param("u",f.admin().id()).update();
            assertEquals(List.of(),data(ok(admin.get("/api/v1/auth/administration"),200,"AdminAccessResponse")).get("companies"));
            ok(admin.get("/api/v1/auth/administration?organization_id="+f.org()),404,"Problem");
            assertEquals(200,admin.get("/api/v1/auth/me").statusCode());
        }
    }
    @Test void cambioDePermisosGlobalesSeReflejaSinInventarAtajosDeRol() throws Exception {
        F f=fixture(); UUID role=jdbc.sql("SELECT id FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'").query(UUID.class).single();
        UUID permission=jdbc.sql("SELECT id FROM autorizacion.permiso WHERE code='tc.users.read'").query(UUID.class).single();
        try(var admin=new Browser()) {
            admin.login(f.root()); assertTrue(admin.get("/api/v1/auth/administration").body().contains("tc.users.read"));
            jdbc.sql("DELETE FROM autorizacion.rol_permiso WHERE role_id=:r AND permission_id=:p").param("r",role).param("p",permission).update();
            try {
                var res=ok(admin.get("/api/v1/auth/administration"),200,"AdminAccessResponse");
                assertFalse(res.body().contains("tc.users.read")); assertEquals(403,admin.get("/api/v1/admin/users").statusCode());
            } finally { jdbc.sql("INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES(:r,:p)").param("r",role).param("p",permission).update(); }
        }
    }
    @Test void empresaInactivaNoDaFacultadesDelegadasPeroSistemaPuedeVerContextoAdministrativo() throws Exception {
        F f=fixture(); jdbc.sql("UPDATE contexto.organizacion SET status='inactive' WHERE id=:id").param("id",f.org()).update();
        try(var user=new Browser();var root=new Browser()) {
            user.login(f.admin()); assertEquals(List.of(),data(ok(user.get("/api/v1/auth/administration"),200,"AdminAccessResponse")).get("companies"));
            ok(user.get("/api/v1/auth/administration?organization_id="+f.org()),404,"Problem");
            root.login(f.root()); var res=ok(root.get("/api/v1/auth/administration?organization_id="+f.org()),200,"AdminAccessResponse");
            assertEquals(List.of(),data(res).get("organization_permissions")); assertTrue(res.body().contains("\"inactive\""));
        }
    }
    @Test void rolVigenteSeConsultaConJerarquiaYPermisoSinRecorrerTodoElHistorial() throws Exception {
        F f=fixture();
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(f.admin()); user.login(f.user()); String member=incorporate(admin,f),base=path(f)+"/memberships/"+member;
            ok(admin.get(base+"/role"),404,"Problem");
            String assignment=(String)data(ok(admin.write("PUT",base+"/role",role("GESTOR_CATALOGO",null)),200,"AssignmentResponse")).get("id");
            var current=ok(admin.get(base+"/role"),200,"AssignmentResponse"); assertEquals(assignment,data(current).get("id"));
            ok(user.get(base+"/role"),404,"Problem");
            ok(admin.get(path(f)+"/memberships/"+UUID.randomUUID()+"/role"),404,"Problem");
            ok(admin.post(base+"/roles/"+assignment+"/revocations",Map.of("reason","Retiro")),204,null);
            ok(admin.get(base+"/role"),404,"Problem");
        }
    }
    @Test void validaQueryYNoUsaCabecerasParaSuplantarUnaCuenta() throws Exception {
        F f=fixture(),other=fixture();
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(f.admin()); user.login(f.user());
            ok(admin.get("/api/v1/auth/administration?organization_id=1-1-1-1-1"),422,"Problem");
            ok(admin.get("/api/v1/auth/administration?organization_id="+f.org()+"&organization_id="+other.org()),422,"Problem");
            ok(admin.get("/api/v1/auth/administration?actor_id="+f.root().id()),422,"Problem");
            ok(admin.get("/api/v1/auth/administration?organization_id="+other.org()),404,"Problem");
            var res=ok(user.raw("GET","/api/v1/auth/administration",null,Map.of("X-User-Id",f.root().id().toString(),"X-Role","ADMIN_SISTEMA")),200,"AdminAccessResponse");
            assertEquals(List.of(),data(res).get("platform_permissions"));
        }
    }
    @AfterAll static void evidence() throws Exception {
        var p=Path.of("target","contract-evidence","administracion.json"); Files.createDirectories(p.getParent()); Files.writeString(p,new ObjectMapper().writeValueAsString(EVIDENCE));
    }
    private class Browser implements AutoCloseable {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> raw(String method,String path,String content,Map<String,String> headers) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
            if(content!=null) b.header("Content-Type","application/json"); headers.forEach(b::header);
            return client.send(b.method(method,content==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(content)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(String path) throws Exception { return raw("GET",path,null,Map.of()); }
        Map<String,String> csrf() throws Exception { var token=data(get("/api/v1/auth/csrf")); return Map.of((String)token.get("header_name"),(String)token.get("token")); }
        int loginStatus(Cuenta c) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/login")).header("Content-Type","application/x-www-form-urlencoded");
            csrf().forEach(b::header); String form="email="+URLEncoder.encode(c.email(),StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(PASSWORD,StandardCharsets.UTF_8);
            return client.send(b.POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        void login(Cuenta c) throws Exception { assertEquals(200,loginStatus(c)); }
        HttpResponse<String> write(String method,String path,Object body) throws Exception { return raw(method,path,json.writeValueAsString(body),csrf()); }
        HttpResponse<String> post(String path,Object body) throws Exception { return write("POST",path,body); }
        public void close() { client.close(); }
    }
}