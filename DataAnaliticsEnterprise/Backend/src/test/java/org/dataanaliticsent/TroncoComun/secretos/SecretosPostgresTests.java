package org.dataanaliticsent.TroncoComun.secretos;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.contexto.*;
import org.dataanaliticsent.TroncoComun.identidad.*;
import org.dataanaliticsent.TroncoComun.secretos.SecretosApi.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
@Tag("postgres")
@SpringBootTest(properties={"tic.secrets.allow-local-insecure=true"})
@Import(SecretosPostgresTests.Config.class)
class SecretosPostgresTests {
    @Autowired SecretosApi api;@Autowired ContextoApi contexto;@Autowired ParticipacionApi participacion;
    @Autowired JdbcClient jdbc;@Autowired Fuentes fuentes;@Autowired ObjectMapper json;
    @MockitoBean IdentidadActualApi identidad;
    final ThreadLocal<UUID> actor=new ThreadLocal<>();
    @DynamicPropertySource static void keys(DynamicPropertyRegistry r){
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        String key=Base64.getEncoder().encodeToString(bytes);Arrays.fill(bytes,(byte)0);
        r.add("tic.secrets.key-ids",()->"test");r.add("tic.secrets.active-key-id",()->"test");r.add("TIC_SECRETS_KEY_TEST",()->key);
    }
    @BeforeEach void init(){when(identidad.usuarioId()).thenAnswer(a->actor.get());fuentes.rows.clear();}
    record F(UUID root,UUID user,UUID org,UUID project,UUID member,UUID source,DestinoPostgres target){}
    UUID user(){
        UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba TC11')")
            .param("id",id).param("email",id+"@example.test").update();return id;
    }
    F fixture(){
        UUID root=user(),u=user();actor.set(root);
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:u,id,'PLATFORM',:u FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'")
            .param("id",UUID.randomUUID()).param("u",root).update();
        UUID org=contexto.crearOrganizacion("TC11","Prueba").id();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:u,:o,:a)")
            .param("id",UUID.randomUUID()).param("u",u).param("o",org).param("a",root).update();
        UUID p=contexto.crearProyecto(org,"Proyecto","Prueba").id();var m=participacion.incorporar(org,p,u,"Alta");
        participacion.asignarRol(org,p,m.id(),"GESTOR_CATALOGO",List.of(new ParticipacionApi.Alcance(ParticipacionApi.TipoAlcance.PROJECT,null)),null,"Rol");
        UUID source=UUID.randomUUID();var d=new DestinoPostgres("db.example.test",5432,"empresa","verify-full");
        fuentes.rows.put(source,new ProveedorFuentesSecretos.Fuente(p,source,d,true,false));actor.set(u);
        return new F(root,u,org,p,m.id(),source,d);
    }
    CredencialPostgres material(){return new CredencialPostgres("lector_sintetico".toCharArray(),"clave-sintetica-privada-2026".toCharArray());}
    Referencia alta(F f){return api.registrar(f.project(),f.source(),f.target(),material(),"Registrar");}
    <T>T usar(F f,Referencia r,java.util.function.Function<CredencialPostgres,T> callback){return api.usarPostgres(f.project(),f.source(),r.id(),f.target(),Uso.DESCUBRIR_METADATOS,callback);}
    long count(String sql,UUID id){return jdbc.sql(sql).param("id",id).query(Long.class).single();}
    @Test void persisteSoloCifradoYDevuelveReferenciaSinSecreto(){
        F f=fixture();var r=alta(f);assertEquals(1,r.revision());assertEquals(r,api.consultar(f.project(),f.source(),r.id()));
        String dto=json.writeValueAsString(r);assertFalse(dto.contains("clave"));assertFalse(dto.contains("cipher"));assertFalse(dto.contains("lector_sintetico"));
        var bytes=jdbc.sql("SELECT ciphertext FROM secretos.referencia WHERE id=:id").param("id",r.id()).query(byte[].class).single();
        assertFalse(new String(bytes,java.nio.charset.StandardCharsets.UTF_16BE).contains("clave-sintetica"));
        assertEquals(Integer.valueOf(2),usar(f,r,c->{assertArrayEquals("lector_sintetico".toCharArray(),c.usuario());assertArrayEquals("clave-sintetica-privada-2026".toCharArray(),c.clave());return 2;}));
        String events=jdbc.sql("SELECT string_agg(safe_details::text,' ') FROM auditoria.evento WHERE resource_id=:id").param("id",r.id()).query(String.class).single();
        assertFalse(events.contains("clave"));assertFalse(events.contains("lector"));assertTrue(events.contains("revision"));
    }
    @Test void deshabilitaSustituyeYRetiraSinBorrarIdentidadNiAuditoria(){
        F f=fixture();var r=alta(f);var off=api.deshabilitar(f.project(),f.source(),r.id(),1,"Pausa");assertEquals(Estado.DISABLED,off.status());
        assertThrows(SecretosException.class,()->usar(f,off,c->1));
        var next=api.reemplazar(f.project(),f.source(),r.id(),2,f.target(),material(),"Nueva asignación");assertEquals(r.id(),next.id());assertEquals(Estado.ACTIVE,next.status());
        assertEquals(Integer.valueOf(1),usar(f,next,c->1));
        var retired=api.retirar(f.project(),f.source(),r.id(),3,"Retiro");assertEquals(Estado.RETIRED,retired.status());
        assertEquals(1,count("SELECT count(*) FROM secretos.referencia WHERE id=:id AND ciphertext IS NULL AND nonce IS NULL AND key_id IS NULL",r.id()));
        assertThrows(SecretosException.class,()->api.reemplazar(f.project(),f.source(),r.id(),4,f.target(),material(),"No resucitar"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("DELETE FROM secretos.referencia WHERE id=:id").param("id",r.id()).update());
        var fresh=alta(f);assertNotEquals(r.id(),fresh.id());assertEquals(4,count("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action NOT LIKE 'tc.secrets.resolve.%'",r.id()));
    }
    @Test void rechazaReferenciaAjenaDestinoAlteradoFuenteDeshabilitadaYRetirada(){
        F f=fixture();var r=alta(f);var called=new AtomicBoolean();
        var other=new DestinoPostgres("otra.test",5432,"empresa","verify-full");
        assertThrows(SecretosException.class,()->api.usarPostgres(f.project(),f.source(),r.id(),other,Uso.PROBAR_CONEXION,c->{called.set(true);return 1;}));
        assertThrows(RuntimeException.class,()->api.consultar(UUID.randomUUID(),f.source(),r.id()));
        fuentes.rows.put(f.source(),new ProveedorFuentesSecretos.Fuente(f.project(),f.source(),f.target(),false,false));
        assertThrows(SecretosException.class,()->usar(f,r,c->{called.set(true);return 1;}));assertFalse(called.get());
        fuentes.rows.put(f.source(),new ProveedorFuentesSecretos.Fuente(f.project(),f.source(),f.target(),true,true));
        assertThrows(SecretosException.class,()->usar(f,r,c->1));
        assertEquals(Estado.RETIRED,api.retirar(f.project(),f.source(),r.id(),1,"Retiro propietario").status());
    }
    @Test void administradorNoHeredaAccesoFuncionalYRevocacionNiegaMismoActor(){
        F f=fixture();var r=alta(f);actor.set(f.root());assertThrows(AccesoDenegadoException.class,()->usar(f,r,c->1));
        participacion.revocarMembresia(f.org(),f.project(),f.member(),"Retiro");actor.set(f.user());
        assertThrows(AccesoDenegadoException.class,()->usar(f,r,c->1));
    }
    @Test void archivadoImpideConexionInclusoConHistorialLegible(){
        F f=fixture();var r=alta(f);actor.set(f.root());participacion.archivarProyecto(f.org(),f.project(),0,"Archivo");actor.set(f.user());
        assertThrows(AccesoDenegadoException.class,()->usar(f,r,c->1));
    }
    @Test void cambioConcurrenteDuranteConectorDescartaResultadoYCierraMaterial(){
        F f=fixture();var r=alta(f);var captured=new AtomicReference<CredencialPostgres>();
        assertThrows(SecretosException.class,()->usar(f,r,c->{captured.set(c);assertFalse(TransactionSynchronizationManager.isActualTransactionActive());api.retirar(f.project(),f.source(),r.id(),1,"Retiro concurrente");return "resultado";}));
        assertThrows(SecretosException.class,()->captured.get().clave());
    }
    @Test void errorDeConectorNoFiltraSuExcepcionYCierraMaterial(){
        F f=fixture();var r=alta(f);var captured=new AtomicReference<CredencialPostgres>();
        var error=assertThrows(SecretosException.class,()->usar(f,r,c->{captured.set(c);throw new IllegalStateException("clave-sintetica-privada-2026");}));
        assertEquals(SecretosException.Codigo.CONECTOR_FALLIDO,error.codigo());assertNull(error.getCause());assertFalse(error.toString().contains("privada"));
        assertThrows(SecretosException.class,()->captured.get().usuario());
    }
    @Test void revisionObsoletaYDosSustitucionesConcurrentesNoPierdenCambios() throws Exception{
        F f=fixture();var r=alta(f);assertThrows(SecretosException.class,()->api.reemplazar(f.project(),f.source(),r.id(),2,f.target(),material(),"Referencia obsoleta"));
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Boolean> job=()->{actor.set(f.user());try{api.reemplazar(f.project(),f.source(),r.id(),1,f.target(),material(),"Sustitución");return true;}catch(SecretosException e){assertEquals(SecretosException.Codigo.CONFLICTO,e.codigo());return false;}};
            var one=pool.submit(job);var two=pool.submit(job);assertNotEquals(one.get(),two.get());
        }
        assertEquals(2,api.consultar(f.project(),f.source(),r.id()).revision());assertEquals(1,count("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action='tc.secrets.replace'",r.id()));
    }
    @Test void falloAuditoriaRevierteAltaYSustitucion(){
        F f=fixture();var r=alta(f);
        jdbc.sql("CREATE FUNCTION auditoria.fallo_tc11() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action LIKE 'tc.secrets.%' THEN RAISE EXCEPTION 'prueba'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER fallo_tc11 BEFORE INSERT ON auditoria.evento FOR EACH ROW EXECUTE FUNCTION auditoria.fallo_tc11()").update();
        try{
            var second=fixture();
            assertThrows(SecretosException.class,()->alta(second));
            assertEquals(0,count("SELECT count(*) FROM secretos.referencia WHERE source_id=:id",second.source()));
            actor.set(f.user());
            assertThrows(SecretosException.class,()->api.reemplazar(f.project(),f.source(),r.id(),1,f.target(),material(),"Reemplazo"));
            assertThrows(SecretosException.class,()->usar(f,r,c->1));
        }finally{jdbc.sql("DROP TRIGGER fallo_tc11 ON auditoria.evento").update();jdbc.sql("DROP FUNCTION auditoria.fallo_tc11()").update();}
        assertEquals(1,api.consultar(f.project(),f.source(),r.id()).revision());assertEquals(Integer.valueOf(1),usar(f,r,c->1));
    }
    @Test void recifrarConservaIdentidadEstadoYMaterial(){
        F f=fixture();var r=alta(f);var next=api.recifrar(f.project(),f.source(),r.id(),1,"Rotar clave");
        assertEquals(r.id(),next.id());assertEquals(2,next.revision());assertEquals(Integer.valueOf(1),usar(f,next,c->1));
        var off=api.deshabilitar(f.project(),f.source(),r.id(),2,"Pausa");
        var again=api.recifrar(f.project(),f.source(),r.id(),off.revision(),"Rotación");assertEquals(Estado.DISABLED,again.status());assertThrows(SecretosException.class,()->usar(f,again,c->1));
    }
    @Test void fuenteSinProveedorNoSeInventaYDestinoNoPermiteTlsDebil(){
        F f=fixture();fuentes.rows.remove(f.source());assertThrows(RuntimeException.class,()->alta(f));
        var weak=new DestinoPostgres("servidor.test",5432,"db","disable");
        fuentes.rows.put(f.source(),new ProveedorFuentesSecretos.Fuente(f.project(),f.source(),weak,true,false));
        assertThrows(SecretosException.class,()->api.registrar(f.project(),f.source(),weak,material(),"TLS requerido"));
    }
    @Test void conectorRealRecuperaCredencialSoloEnSuAmbitoConPostgresTemporal(){
        F f=fixture();String url=System.getenv("TC11_SOURCE_URL"),user=System.getenv("TC11_SOURCE_USER"),password=System.getenv("TC11_SOURCE_PASSWORD");
        assertNotNull(url,"Usar tools/verificar_postgres_tc.py");
        var uri=java.net.URI.create(url.substring(5));var d=new DestinoPostgres(uri.getHost(),uri.getPort(),uri.getPath().substring(1),"disable");
        fuentes.rows.put(f.source(),new ProveedorFuentesSecretos.Fuente(f.project(),f.source(),d,true,false));
        var r=api.registrar(f.project(),f.source(),d,new CredencialPostgres(user.toCharArray(),password.toCharArray()),"Conector sintético");
        int result=api.usarPostgres(f.project(),f.source(),r.id(),d,Uso.PROBAR_CONEXION,c->{
            char[] u=c.usuario(),p=c.clave();var props=new Properties();
            try{
                props.setProperty("user",new String(u));props.setProperty("password",new String(p));props.setProperty("sslmode","disable");
                props.setProperty("connectTimeout","5");props.setProperty("socketTimeout","5");
                try(var conn=DriverManager.getConnection(url,props);var sql=conn.createStatement();var rows=sql.executeQuery("SELECT 1")){assertTrue(rows.next());return rows.getInt(1);}
                catch(java.sql.SQLException e){throw new IllegalStateException("Conector sintético falló");}
            }finally{Arrays.fill(u,'\0');Arrays.fill(p,'\0');props.clear();}
        });assertEquals(1,result);
    }

    @Test void alcanceSourceNoPermiteCredencialesDeOtraFuenteDelMismoProyecto(){
        F f=fixture();UUID other=UUID.randomUUID();
        fuentes.rows.put(other,new ProveedorFuentesSecretos.Fuente(f.project(),other,f.target(),true,false));
        actor.set(f.root());var current=participacion.consultarRol(f.org(),f.project(),f.member());
        participacion.asignarRol(f.org(),f.project(),f.member(),"GESTOR_CATALOGO",
            List.of(new ParticipacionApi.Alcance(ParticipacionApi.TipoAlcance.SOURCE,f.source())),current.id(),"Alcance limitado");
        actor.set(f.user());var r=alta(f);assertEquals(Integer.valueOf(1),usar(f,r,c->1));
        assertThrows(AccesoDenegadoException.class,()->api.registrar(f.project(),other,f.target(),material(),"Otra fuente"));
    }
    @TestConfiguration static class Config{@Bean Fuentes fuentesTC11(){return new Fuentes();}}
    static class Fuentes implements ProveedorFuentesSecretos,ProveedorRecursosAutorizacion{
        final Map<UUID,Fuente> rows=new ConcurrentHashMap<>();
        public Optional<Fuente> consultar(UUID p,UUID s){return Optional.ofNullable(rows.get(s)).filter(f->f.projectId().equals(p));}
        public boolean soporta(SolicitudAcceso.TipoRecurso type){return type==SolicitudAcceso.TipoRecurso.SOURCE;}
        public Optional<Jerarquia> resolver(SolicitudAcceso.Recurso r){return Optional.ofNullable(rows.get(r.id())).map(f->new Jerarquia(f.projectId(),f.sourceId(),null));}
    }
}
