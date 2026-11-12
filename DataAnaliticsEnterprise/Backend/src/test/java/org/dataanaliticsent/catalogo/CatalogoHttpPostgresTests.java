package org.dataanaliticsent.catalogo;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.catalogo.CatalogoApi.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.servlet.session.cookie.secure=false","tic.auth.login-ip-limit=500"})
class CatalogoHttpPostgresTests {
    private static final String PASSWORD="PruebaCatalogo!2026";
    private static final List<Map<String,Object>> evidencia=new ArrayList<>();
    @Value("${local.server.port}") int port;
    @Autowired CatalogoApi catalogo;
    @Autowired DescubrimientoPostgresApi descubrimiento;
    @Autowired UserDetailsService usuarios;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @AfterEach void limpiarIdentidad() { SecurityContextHolder.clearContext(); }
    @AfterAll static void guardarEvidencia() throws Exception {
        Path path=Path.of("target/contract-evidence/m104.json"); Files.createDirectories(path.getParent());
        try(var out=Files.newOutputStream(path)) { new ObjectMapper().writeValue(out,evidencia); }
    }

    @Test void origenRealSeDescubreYConsumePorHttpConSesionReal() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO");
        var resultado=como(f,()->descubrimiento.descubrir(new DescubrimientoPostgresApi.Solicitud(f.project(),f.source(),List.of("m103_core"))));
        var tabla=resultado.tables().stream().filter(t->t.name().equals("child")).findFirst().orElseThrow();
        try(Browser b=login(f)) {
            var a=b.get(ruta(f,tabla.assetId())); assertEquals(200,a.statusCode());
            assertEquals("m103_core",map(data(a).get("origin")).get("namespace"));
            assertEquals(f.org().toString(),data(a).get("organization_id"));
            var campos=b.get(ruta(f,tabla.assetId())+"/fields?metadata_version_id="+tabla.metadataVersionId()+"&page_size=2");
            assertEquals(200,campos.statusCode()); assertEquals(2,lista(campos).size());
            assertEquals(true,pageInfo(campos).get("has_next"));
            assertFalse(campos.body().contains("NEVER_COPY_ROW_7"));
        }
    }

    @Test void anonimoNoSeAutenticaConCabecerasDelCliente() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false);
        try(Browser b=new Browser()) {
            var r=b.request("GET",ruta(f,a.id()),null,null,Map.of("X-User-Id",f.user().toString()));
            assertEquals(401,r.statusCode()); assertEquals("AUTHENTICATION_REQUIRED",body(r).get("code"));
        }
    }

    @Test void detalleEsSnakeCaseDerivaOrganizacionYNoRevelaConfiguracion() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false); etiqueta(f,a.id(),"Ventas","active"); etiqueta(f,a.id(),"Oculta","inactive");
        String requestId=UUID.randomUUID().toString();
        try(Browser b=login(f)) {
            var r=b.request("GET",ruta(f,a.id()),null,null,Map.of("X-Request-Id",requestId));
            assertEquals(200,r.statusCode()); var d=data(r);
            assertEquals(a.metadataVersionId().toString(),d.get("metadata_version_id"));
            assertEquals(f.org().toString(),d.get("organization_id")); assertEquals(List.of("Ventas"),d.get("tags"));
            assertEquals(requestId,map(body(r).get("meta")).get("request_id"));
            assertEquals(requestId,r.headers().firstValue("X-Request-Id").orElseThrow());
            assertFalse(d.containsKey("projectId")); assertFalse(r.body().contains("connection_configuration"));
            assertFalse(r.body().contains("credential")); assertFalse(r.body().contains(PASSWORD));
            assertEquals("no-store",r.headers().firstValue("Cache-Control").orElseThrow());
        }
    }

    @Test void paginacionEsEstableYUnaPaginaPosteriorQuedaVacia() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false);
        String path=ruta(f,a.id())+"/fields?metadata_version_id="+a.metadataVersionId()+"&page_size=2";
        try(Browser b=login(f)) {
            var first=b.get(path); var second=b.get(path+"&page=2"); var empty=b.get(path+"&page=2147483647");
            assertEquals(200,first.statusCode()); assertEquals(200,second.statusCode()); assertEquals(200,empty.statusCode());
            assertEquals(List.of("id","nombre"),lista(first).stream().map(c->c.get("name")).toList());
            assertEquals(List.of("activo"),lista(second).stream().map(c->c.get("name")).toList());
            assertEquals(true,pageInfo(first).get("has_next")); assertEquals(false,pageInfo(second).get("has_next"));
            assertEquals(0,pageInfo(empty).get("returned_count")); assertTrue(lista(empty).isEmpty());
            assertTrue(lista(first).stream().allMatch(c->a.metadataVersionId().toString().equals(c.get("metadata_version_id"))));
        }
    }

    @Test void diccionarioVacioEs200YVersionAjenaEs404() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var empty=alta(f,true); var other=alta(f,false);
        try(Browser b=login(f)) {
            var r=b.get(ruta(f,empty.id())+"/fields?metadata_version_id="+empty.metadataVersionId());
            assertEquals(200,r.statusCode()); assertTrue(lista(r).isEmpty()); assertEquals(false,pageInfo(r).get("has_next"));
            assertEquals(404,b.get(ruta(f,empty.id())+"/fields?metadata_version_id="+other.metadataVersionId()).statusCode());
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"page=0","page_size=101","sort=name","page=1&page=2","actor_id=x","page=2147483648"})
    void parametrosInvalidosDan422ConErroresDeCampo(String query) throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false);
        try(Browser b=login(f)) {
            var r=b.get(ruta(f,a.id())+"/fields?metadata_version_id="+a.metadataVersionId()+"&"+query);
            assertEquals(422,r.statusCode()); assertEquals("VALIDATION_ERROR",body(r).get("code"));
            assertTrue(body(r).get("errors") instanceof List<?>); assertFalse(r.body().contains("contraseña"));
        }
    }

    @Test void versionObligatoriaYUuidCanonicoSeValidan() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false);
        try(Browser b=login(f)) {
            assertEquals(422,b.get(ruta(f,a.id())+"/fields").statusCode());
            assertEquals(422,b.get("/api/v1/projects/1-1-1-1-1/assets/"+a.id()).statusCode());
            assertEquals(422,b.get(ruta(f,a.id())+"/fields?metadata_version_id=invalido").statusCode());
        }
    }

    @Test void recursoAjenoEInexistenteSeOcultanConLaMismaRespuesta() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"),other=fixture("GESTOR_CATALOGO"); var a=alta(other,false);
        try(Browser b=login(f)) {
            var ajeno=b.get(ruta(other,a.id())); var inexistente=b.get(ruta(f,UUID.randomUUID()));
            assertEquals(404,ajeno.statusCode()); assertEquals(404,inexistente.statusCode());
            assertEquals(body(ajeno).get("detail"),body(inexistente).get("detail"));
            assertFalse(ajeno.body().contains("CONTEXTO_NO_AUTORIZADO"));
        }
    }

    @Test void rolSinAccionYAlcanceDeHermanoNoDanAcceso() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false); var sibling=alta(f,false);
        jdbc.sql("DELETE FROM autorizacion.alcance_asignacion WHERE role_assignment_id=:id").param("id",f.assignment()).update();
        jdbc.sql("INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope,asset_id) VALUES(:id,:assignment,:project,'ASSET',:asset)")
                .param("id",UUID.randomUUID()).param("assignment",f.assignment()).param("project",f.project()).param("asset",a.id()).update();
        try(Browser b=login(f)) {
            assertEquals(200,b.get(ruta(f,a.id())).statusCode());
            assertEquals(404,b.get(ruta(f,sibling.id())).statusCode());
            assertEquals(404,b.get(ruta(f,a.id())+"/fields?metadata_version_id="+sibling.metadataVersionId()).statusCode());
        }
        jdbc.sql("UPDATE autorizacion.asignacion_rol SET role_id=(SELECT id FROM autorizacion.rol WHERE code='ANALISTA_CALIDAD') WHERE id=:id")
                .param("id",f.assignment()).update();
        try(Browser b=login(f)) { assertEquals(403,b.get(ruta(f,a.id())).statusCode()); }
    }

    @Test void diccionarioRequiereSuPermisoIndependiente() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false);
        quitar("m1.dictionary.read");
        try(Browser b=login(f)) {
            assertEquals(200,b.get(ruta(f,a.id())).statusCode());
            assertEquals(403,b.get(ruta(f,a.id())+"/fields?metadata_version_id="+a.metadataVersionId()).statusCode());
        } finally { reponer("m1.dictionary.read"); }
    }

    @Test void versionElegidaNoSeSustituyePorLasFilasActualesYExigeHistorial() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false); UUID newer=segundaVersion(f,a);
        try(Browser b=login(f)) {
            var old=b.get(ruta(f,a.id())+"/fields?metadata_version_id="+a.metadataVersionId());
            var current=b.get(ruta(f,a.id())+"/fields?metadata_version_id="+newer);
            assertEquals(200,old.statusCode()); assertEquals(200,current.statusCode());
            assertEquals("id",lista(old).getFirst().get("name")); assertEquals("id_v2",lista(current).getFirst().get("name"));
            quitar("m1.metadata.history.read");
            try {
                assertEquals(403,b.get(ruta(f,a.id())+"/fields?metadata_version_id="+a.metadataVersionId()).statusCode());
                assertEquals(200,b.get(ruta(f,a.id())+"/fields?metadata_version_id="+newer).statusCode());
            } finally { reponer("m1.metadata.history.read"); }
        }
    }

    @Test void permisoRevocadoTieneEfectoConLaMismaCookie() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false);
        try(Browser b=login(f)) {
            assertEquals(200,b.get(ruta(f,a.id())).statusCode());
            jdbc.sql("UPDATE autorizacion.asignacion_rol SET revoked_at=now() WHERE id=:id").param("id",f.assignment()).update();
            assertEquals(403,b.get(ruta(f,a.id())).statusCode());
        }
    }

    @Test void postNoPublicadoNoModificaCatalogo() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO"); var a=alta(f,false);
        try(Browser b=login(f)) {
            var token=b.csrf();
            var r=b.request("POST",ruta(f,a.id()),"{}","application/json",Map.of((String)token.get("header_name"),(String)token.get("token")));
            assertEquals(403,r.statusCode()); assertEquals(200,b.get(ruta(f,a.id())).statusCode());
        }
    }

    private UUID segundaVersion(Fixture f,RegistroActivo a) throws Exception {
        String raw=jdbc.sql("SELECT state_payload::text FROM versionado.version_recurso WHERE id=:id").param("id",a.metadataVersionId()).query(String.class).single();
        ObjectNode node=(ObjectNode)json.readTree(raw); UUID next=UUID.randomUUID(),event=UUID.randomUUID();
        ((ObjectNode)node.get("activo")).put("metadataVersionId",next.toString());
        ((ObjectNode)node.get("activo")).put("revision",1);
        ((ObjectNode)node.get("campos").get(0)).put("name","id_v2");
        String payload=json.writeValueAsString(node);
        jdbc.sql("INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,occurred_at,outcome) VALUES(:id,'USER',:actor,:project,'catalogo.activo',:asset,'fixture.version',now(),'SUCCESS')")
                .param("id",event).param("actor",f.user()).param("project",f.project()).param("asset",a.id()).update();
        jdbc.sql("""
                INSERT INTO versionado.version_recurso(id,project_id,resource_type,resource_id,number,previous_version_id,created_at,
                    actor_type,actor_id,reason,representation_version,state_payload,content_hash,audit_event_id)
                VALUES(:id,:project,'catalogo.activo',:asset,2,:previous,now(),'USER',:actor,'Fixture histórico',1,CAST(:payload AS jsonb),:hash,:event)
                """).param("id",next).param("project",f.project()).param("asset",a.id()).param("previous",a.metadataVersionId())
                .param("actor",f.user()).param("payload",payload).param("event",event)
                .param("hash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)))).update();
        jdbc.sql("UPDATE catalogo.campo SET name='id_v2' WHERE asset_id=:id AND position=1").param("id",a.id()).update();
        jdbc.sql("UPDATE catalogo.activo SET current_metadata_version_id=:version,revision=1 WHERE id=:id").param("id",a.id()).param("version",next).update();
        return next;
    }
    private void quitar(String permission) {
        jdbc.sql("DELETE FROM autorizacion.rol_permiso WHERE role_id=(SELECT id FROM autorizacion.rol WHERE code='GESTOR_CATALOGO') AND permission_id=(SELECT id FROM autorizacion.permiso WHERE code=:p)").param("p",permission).update();
    }
    private void reponer(String permission) {
        jdbc.sql("INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT r.id,p.id FROM autorizacion.rol r CROSS JOIN autorizacion.permiso p WHERE r.code='GESTOR_CATALOGO' AND p.code=:p").param("p",permission).update();
    }
    private void etiqueta(Fixture f,UUID asset,String name,String status) {
        UUID tag=UUID.randomUUID();
        jdbc.sql("INSERT INTO catalogo.etiqueta(id,project_id,name,status) VALUES(:id,:project,:name,:status)")
                .param("id",tag).param("project",f.project()).param("name",name).param("status",status).update();
        jdbc.sql("INSERT INTO catalogo.activo_etiqueta(asset_id,tag_id,project_id,assigned_by) VALUES(:asset,:tag,:project,:actor)")
                .param("asset",asset).param("tag",tag).param("project",f.project()).param("actor",f.user()).update();
    }
    private RegistroActivo alta(Fixture f,boolean empty) {
        return como(f,()->catalogo.registrarActivo(new AltaActivo(f.project(),f.source(),"Clientes",TipoActivo.TABLE,"public","clientes_"+UUID.randomUUID(),
                OrigenEstructura.DECLARED,empty?List.of():List.of(new AltaCampo("id","id",1,"integer",TipoLogico.INTEGER,false),
                    new AltaCampo("nombre","nombre",2,"text",TipoLogico.STRING,null),new AltaCampo("activo","activo",3,"boolean",TipoLogico.BOOLEAN,true)))));
    }
    private <T> T como(Fixture f,java.util.function.Supplier<T> action) {
        var ctx=SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuarios.loadUserByUsername(f.email()),null,List.of()));
        SecurityContextHolder.setContext(ctx);
        try { return action.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    private Fixture fixture(String role) {
        UUID user=UUID.randomUUID(),org=UUID.randomUUID(),project=UUID.randomUUID(),om=UUID.randomUUID(),pm=UUID.randomUUID(),assignment=UUID.randomUUID(),source=UUID.randomUUID();
        String email=user+"@example.test";
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'HTTP catálogo')").param("id",user).param("email",email).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)").param("id",user).param("hash",encoder.encode(PASSWORD)).update();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'HTTP catálogo')").param("id",org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'HTTP catálogo')").param("id",project).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:user)").param("id",om).param("user",user).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by) VALUES(:id,:om,:user,:org,:project,:user)")
                .param("id",pm).param("om",om).param("user",user).param("org",org).param("project",project).update();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT :id,:user,id,'PROJECT',:member,:project,:user FROM autorizacion.rol WHERE code=:role")
                .param("id",assignment).param("user",user).param("member",pm).param("project",project).param("role",role).update();
        jdbc.sql("INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope) VALUES(:id,:assignment,:project,'PROJECT')").param("id",UUID.randomUUID()).param("assignment",assignment).param("project",project).update();
        jdbc.sql("""
                INSERT INTO catalogo.fuente(id,project_id,name,type,status,connection_configuration,credential_ref,created_by,created_at,updated_at)
                VALUES(:id,:project,'Origen HTTP','postgresql','enabled',jsonb_build_object('host',CAST(:host AS text),
                    'port',CAST(:port AS text),'database',CAST(:database AS text)),'fixture',:actor,now(),now())
                """).param("id",source).param("project",project).param("host",env("TIC_SOURCE_FIXTURE_HOST"))
                .param("port",env("TIC_SOURCE_FIXTURE_PORT")).param("database",env("TIC_SOURCE_FIXTURE_DATABASE")).param("actor",user).update();
        return new Fixture(user,email,org,project,source,assignment);
    }
    private String env(String name) { String v=System.getenv(name); assertNotNull(v,"Ejecutar tools/verificar_postgres.py"); return v; }
    private String ruta(Fixture f,UUID asset) { return "/api/v1/projects/"+f.project()+"/assets/"+asset; }
    private record Fixture(UUID user,String email,UUID org,UUID project,UUID source,UUID assignment) { }
    private Map<String,Object> body(HttpResponse<String> r) { return json.readValue(r.body(),new TypeReference<Map<String,Object>>(){}); }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value) { return (Map<String,Object>)value; }
    private Map<String,Object> data(HttpResponse<String> r) { return map(body(r).get("data")); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> lista(HttpResponse<String> r) { return (List<Map<String,Object>>)body(r).get("data"); }
    private Map<String,Object> pageInfo(HttpResponse<String> r) { return map(map(body(r).get("meta")).get("page_info")); }
    private Browser login(Fixture f) throws Exception {
        Browser b=new Browser(); var token=b.csrf();
        var r=b.request("POST","/api/v1/auth/login","email="+URLEncoder.encode(f.email(),StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(PASSWORD,StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded",Map.of((String)token.get("header_name"),(String)token.get("token")));
        if(r.statusCode()!=200) { b.close(); fail("No se pudo iniciar la sesión HTTP de prueba"); }
        return b;
    }
    private class Browser implements AutoCloseable {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception { return request("GET",path,null,null,Map.of()); }
        Map<String,Object> csrf() throws Exception { return data(get("/api/v1/auth/csrf")); }
        HttpResponse<String> request(String method,String path,String value,String type,Map<String,String> headers) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(20));
            if(type!=null) builder.header("Content-Type",type); headers.forEach(builder::header);
            var r=client.send(builder.method(method,value==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(value)).build(),HttpResponse.BodyHandlers.ofString());
            if(path.startsWith("/api/v1/projects/")) {
                assertFalse(r.body().contains(PASSWORD));
                evidencia.add(Map.of("schema",r.statusCode()==200?(path.split("\\?")[0].endsWith("/fields")?"FieldList":"AssetResponse"):
                        r.statusCode()==422?"ValidationProblem":"Problem","status",r.statusCode(),"body",body(r),
                        "request_id_header",r.headers().firstValue("X-Request-Id").orElse("")));
            }
            return r;
        }
        public void close() { client.close(); }
    }
}
