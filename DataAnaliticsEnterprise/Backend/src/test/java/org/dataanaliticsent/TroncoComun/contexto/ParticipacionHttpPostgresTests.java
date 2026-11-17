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
class ParticipacionHttpPostgresTests {
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
    @Test void editarNombreNoCambiaCorreoClaveSesionNiPrivilegiosYExigeRevision() throws Exception {
        F f=fixture();
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(f.root()); user.login(f.user()); String p="/api/v1/admin/users/"+f.user().id();
            ok(admin.get(p),200,"AccountDetailResponse");
            ok(admin.write("PATCH",p,Map.of("display_name","Coerción","expected_revision",0.9,"reason","No truncar")),422,"Problem");
            ok(admin.write("PATCH",p,Map.of("display_name","Coerción","expected_revision","0","reason","No convertir")),422,"Problem");
            ok(admin.write("PATCH",p,Map.of("display_name","Nombre actualizado","expected_revision",0,"reason","Corrección")),200,"AccountDetailResponse");
            assertTrue(user.get("/api/v1/auth/me").body().contains("Nombre actualizado"));
            ok(admin.write("PATCH",p,Map.of("display_name","Obsoleto","expected_revision",0,"reason","Conflicto")),409,"Problem");
            ok(admin.write("PATCH",p,Map.of("display_name","X","expected_revision",1,"reason","Prueba","email","otro@example.test")),422,"Problem");
            ok(admin.write("PATCH",p,Map.of("display_name","X","expected_revision",1,"reason","Prueba","role","ADMIN_SISTEMA")),422,"Problem");
            ok(user.get(p),403,"Problem"); assertEquals(200,user.loginStatus(f.user()));
        }
    }
    @Test void membresiaRolArchivoYRevocacionSeReflejanConLaMismaCookie() throws Exception {
        F f=fixture();
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(f.admin()); user.login(f.user()); String member=incorporate(admin,f),base=path(f)+"/memberships/"+member;
            ok(user.get("/api/v1/auth/contexts"),200,"ContextList");
            ok(user.post("/api/v1/auth/context",Map.of("project_id",f.project())),200,"ContextResponse");
            ok(user.get("/api/v1/auth/context"),200,"ContextResponse");
            var empty=ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),200,"PermissionList"); assertTrue(empty.body().contains("\"data\":[]"));
            ok(admin.write("PUT",base+"/role",role("GESTOR_CATALOGO",null)),200,"AssignmentResponse");
            assertTrue(ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),200,"PermissionList").body().contains("m1.discovery.execute"));
            ok(admin.post(path(f)+"/archivals",Map.of("expected_revision",0,"reason","Archivo")),200,"ManagedProjectResponse");
            var readOnly=ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),200,"PermissionList"); assertFalse(readOnly.body().contains("m1.discovery.execute")); assertTrue(readOnly.body().contains("m1.dictionary.read"));
            ok(admin.post(base+"/revocations",Map.of("reason","Retiro")),204,null);
            ok(user.get("/api/v1/auth/context"),404,"Problem"); ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),404,"Problem");
            assertEquals(200,user.get("/api/v1/auth/me").statusCode());
        }
    }
    @Test void reactivarMembresiaNoRecuperaAsignacionYElHistorialConservaLaRevocada() throws Exception {
        F f=fixture();
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(f.admin()); user.login(f.user()); String member=incorporate(admin,f),base=path(f)+"/memberships/"+member;
            String old=(String)data(ok(admin.write("PUT",base+"/role",role("GESTOR_CATALOGO",null)),200,"AssignmentResponse")).get("id");
            ok(user.post("/api/v1/auth/context",Map.of("project_id",f.project())),200,"ContextResponse");
            ok(admin.post(base+"/revocations",Map.of("reason","Retiro")),204,null); ok(user.get("/api/v1/auth/context"),404,"Problem");
            ok(admin.post(base+"/reactivations",Map.of("reason","Reingreso")),200,"MemberResponse");
            ok(user.get("/api/v1/auth/context"),404,"Problem");
            assertTrue(ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),200,"PermissionList").body().contains("\"data\":[]"));
            var history=ok(admin.get(base+"/roles"),200,"AssignmentList"); assertTrue(history.body().contains(old)); assertTrue(history.body().contains("\"inactive\""));
            String next=(String)data(ok(admin.write("PUT",base+"/role",role("GESTOR_CATALOGO",null)),200,"AssignmentResponse")).get("id"); assertNotEquals(old,next);
            ok(admin.post(base+"/roles/"+old+"/revocations",Map.of("reason","Reintento antiguo")),204,null);
            assertTrue(ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),200,"PermissionList").body().contains("m1.discovery.execute"));
        }
    }
    @Test void empresaAjenaAutoasignacionYRolesAdministrativosSonRechazados() throws Exception {
        F f=fixture(),other=fixture();
        try(var admin=new Browser();var user=new Browser();var anon=new Browser()) {
            admin.login(f.admin()); user.login(f.user()); String member=incorporate(admin,f);
            ok(anon.get(path(f)+"/memberships"),401,"Problem"); ok(admin.get(path(other)+"/memberships"),404,"Problem");
            ok(user.get(path(f)+"/memberships"),404,"Problem"); ok(user.post("/api/v1/auth/context",Map.of("project_id",other.project())),404,"Problem");
            ok(admin.post(path(f)+"/memberships",Map.of("user_id",other.user().id(),"reason","Ajeno")),404,"Problem");
            ok(admin.write("PUT",path(f)+"/memberships/"+member+"/role",role("ADMIN_SISTEMA",null)),409,"Problem");
            ok(admin.get("/api/v1/organizations/"+f.org()+"/functional-roles"),200,"RoleList");
            var withActor=role("GESTOR_CATALOGO",null); withActor.put("actor_id",f.root().id());
            ok(admin.write("PUT",path(f)+"/memberships/"+member+"/role",withActor),422,"Problem");
            ok(admin.raw("GET",path(other)+"/memberships",null,Map.of("X-User-Id",other.root().id().toString())),404,"Problem");
        }
    }
    @Test void csrfParametrosUuidYAsignacionEsperadaSonObligatorios() throws Exception {
        F f=fixture();
        try(var admin=new Browser()) {
            admin.login(f.admin()); String member=incorporate(admin,f),base=path(f)+"/memberships/"+member;
            ok(admin.raw("PUT",base+"/role",json.writeValueAsString(role("GESTOR_CATALOGO",null)),Map.of()),403,"Problem");
            var missing=role("GESTOR_CATALOGO",null); missing.remove("expected_assignment_id");
            ok(admin.write("PUT",base+"/role",missing),422,"Problem");
            ok(admin.get(path(f)+"/memberships?page=1&page=2"),422,"Problem");
            ok(admin.get(path(f)+"/memberships?page_size=101"),422,"Problem");
            ok(admin.get("/api/v1/organizations/1-1-1-1-1/memberships"),422,"Problem");
            ok(admin.write("PUT",base+"/role",role("GESTOR_CATALOGO",UUID.randomUUID().toString())),409,"Problem");
            ok(admin.write("PUT",base+"/role",role("GESTOR_CATALOGO",null)),200,"AssignmentResponse");
            ok(admin.write("PUT",base+"/role",role("ANALISTA_CALIDAD",null)),409,"Problem");
        }
    }
    @Test void proyectosSeEditanArchivanYReactivanConRevisionEHistorialProtegidos() throws Exception {
        F f=fixture();
        try(var admin=new Browser()) {
            admin.login(f.admin()); ok(admin.get(path(f)),200,"ManagedProjectResponse");
            ok(admin.write("PATCH",path(f),Map.of("name","Nombre cambiado","expected_revision",0,"reason","Corrección")),200,"ManagedProjectResponse");
            ok(admin.post(path(f)+"/archivals",Map.of("expected_revision",0,"reason","Obsoleto")),409,"Problem");
            ok(admin.post(path(f)+"/archivals",Map.of("expected_revision",1,"reason","Archivo")),200,"ManagedProjectResponse");
            ok(admin.post(path(f)+"/archivals",Map.of("expected_revision",1,"reason","Reintento")),200,"ManagedProjectResponse");
            ok(admin.post(path(f)+"/memberships",Map.of("user_id",f.user().id(),"reason","Archivado")),409,"Problem");
            ok(admin.post(path(f)+"/reactivations",Map.of("expected_revision",2,"reason","Reapertura")),200,"ManagedProjectResponse");
            var event=jdbc.sql("SELECT request_id::text FROM auditoria.evento WHERE resource_id=:id AND action='tc.project.reactivate'").param("id",f.project()).query(String.class).single();
            assertNotNull(event); assertEquals(1L,jdbc.sql("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action='tc.project.archive'").param("id",f.project()).query(Long.class).single());
        }
    }
    @Test void retiroEmpresarialNoDesactivaCuentaPeroRetiraContextosYNoRestauraRolesAlReingresar() throws Exception {
        F f=fixture();
        try(var admin=new Browser();var user=new Browser()) {
            admin.login(f.admin()); user.login(f.user()); String member=incorporate(admin,f),base=path(f)+"/memberships/"+member;
            ok(admin.write("PUT",base+"/role",role("GESTOR_CATALOGO",null)),200,"AssignmentResponse");
            String org="/api/v1/organizations/"+f.org()+"/memberships";
            ok(admin.get(org),200,"CompanyMemberList");
            ok(admin.post(org+"/"+f.companyMember()+"/revocations",Map.of("reason","Retiro empresarial")),204,null);
            ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),404,"Problem"); assertEquals(200,user.get("/api/v1/auth/me").statusCode());
            ok(admin.post(org+"/"+f.companyMember()+"/reactivations",Map.of("reason","Reingreso")),200,"CompanyMemberResponse");
            ok(admin.post(base+"/reactivations",Map.of("reason","Reingreso")),200,"MemberResponse");
            assertTrue(ok(user.get("/api/v1/auth/contexts/"+f.project()+"/permissions"),200,"PermissionList").body().contains("\"data\":[]"));
        }
    }
    @AfterAll static void evidence() throws Exception {
        var p=Path.of("target","contract-evidence","participacion.json"); Files.createDirectories(p.getParent()); Files.writeString(p,new ObjectMapper().writeValueAsString(EVIDENCE));
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
