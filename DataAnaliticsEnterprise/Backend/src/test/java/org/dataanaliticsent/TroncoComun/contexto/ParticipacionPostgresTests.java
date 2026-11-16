package org.dataanaliticsent.TroncoComun.contexto;
import java.util.*;
import java.util.concurrent.*;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.contexto.ParticipacionApi.*;
import org.dataanaliticsent.TroncoComun.identidad.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Tag("postgres")
@SpringBootTest
@Import(ParticipacionPostgresTests.Config.class)
class ParticipacionPostgresTests {
    @Autowired ParticipacionApi api;
    @Autowired ContextoApi contexto;
    @Autowired CuentasApi cuentas;
    @Autowired AutorizacionApi permisos;
    @Autowired JdbcClient jdbc;
    @Autowired Recursos recursos;
    @MockitoBean IdentidadActualApi identidad;
    private final ThreadLocal<UUID> actor=new ThreadLocal<>();
    private static final List<Alcance> PROJECT=List.of(new Alcance(TipoAlcance.PROJECT,null));
    @BeforeEach void init() { when(identidad.usuarioId()).thenAnswer(a->actor.get()); recursos.rows.clear(); }
    private record F(UUID root,UUID admin,UUID user,UUID org,UUID project,UUID companyMember,UUID member) { }
    private UUID user() {
        UUID id=UUID.randomUUID(); jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:e,'Empleado')")
            .param("id",id).param("e",id+"@example.test").update(); return id;
    }
    private F fixture() {
        UUID root=user(),admin=user(),u=user();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:u,id,'PLATFORM',:u FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'")
            .param("id",UUID.randomUUID()).param("u",root).update(); actor.set(root);
        UUID org=contexto.crearOrganizacion("Empresa","Prueba").id(); contexto.asignarAdministrador(org,admin,"Delegación");
        UUID cm=UUID.randomUUID(); jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:u,:o,:a)").param("id",cm).param("u",u).param("o",org).param("a",root).update();
        UUID p=contexto.crearProyecto(org,"Proyecto","Prueba").id(); var m=api.incorporar(org,p,u,"Alta");
        return new F(root,admin,u,org,p,cm,m.id());
    }
    private Asignacion role(F f,String code,UUID expected,List<Alcance> scopes) { return api.asignarRol(f.org(),f.project(),f.member(),code,scopes,expected,"Asignación explícita"); }
    private boolean allowed(F f,String p) { return permisos.evaluar(f.user(),SolicitudAcceso.proyecto(p,f.project())).permitido(); }
    private long count(String sql,UUID id) { return jdbc.sql(sql).param("id",id).query(Long.class).single(); }
    @Test void empleadoPuedeTenerRolesDistintosEnDosProyectosPeroMembresiaSolaNoDaAcceso() {
        F f=fixture(); assertFalse(allowed(f,"m1.assets.search")); assertNull(api.listarMembresias(f.org(),f.project(),1,25).data().getFirst().roleCode());
        role(f,"GESTOR_CATALOGO",null,PROJECT); assertTrue(allowed(f,"m1.assets.search"));
        UUID other=contexto.crearProyecto(f.org(),"Otro","Prueba").id(); var m=api.incorporar(f.org(),other,f.user(),"Alta");
        api.asignarRol(f.org(),other,m.id(),"ANALISTA_CALIDAD",PROJECT,null,"Otra función");
        assertFalse(permisos.evaluar(f.user(),SolicitudAcceso.proyecto("m1.assets.search",other)).permitido());
        actor.set(f.user()); assertEquals(2,api.misContextos(1,25).data().size()); assertNotEquals(api.miContexto(f.project()).roleCode(),api.miContexto(other).roleCode());
    }
    @Test void sustitucionDeRolRetiraAnteriorYAlcancesConHistorialSinAcumularPrivilegios() {
        F f=fixture(); var first=role(f,"GESTOR_CATALOGO",null,PROJECT);
        var second=role(f,"ANALISTA_CALIDAD",first.id(),PROJECT); assertNotEquals(first.id(),second.id()); assertFalse(allowed(f,"m1.assets.search"));
        assertEquals(2,api.historialRoles(f.org(),f.project(),f.member(),1,25).data().size());
        assertEquals(1L,count("SELECT count(*) FROM autorizacion.alcance_asignacion WHERE role_assignment_id=:id",first.id()));
        assertEquals("inactive",jdbc.sql("SELECT status FROM autorizacion.asignacion_rol WHERE id=:id").param("id",first.id()).query(String.class).single());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("UPDATE autorizacion.asignacion_rol SET status='active',revoked_at=NULL WHERE id=:id").param("id",first.id()).update());
        assertEquals(second.id(),role(f,"ANALISTA_CALIDAD",second.id(),PROJECT).id());
    }
    @Test void dosAsignacionesConcurrentesConMismoEsperadoSoloPublicanUna() throws Exception {
        F f=fixture();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> a=()->{ actor.set(f.root()); try { role(f,"GESTOR_CATALOGO",null,PROJECT); return true; } catch(ContextoException e) { return false; } };
            Callable<Boolean> b=()->{ actor.set(f.root()); try { role(f,"ANALISTA_CALIDAD",null,PROJECT); return true; } catch(ContextoException e) { return false; } };
            var one=pool.submit(a); var two=pool.submit(b); assertNotEquals(one.get(),two.get());
        }
        assertEquals(1L,count("SELECT count(*) FROM autorizacion.asignacion_rol WHERE project_membership_id=:id AND status='active' AND revoked_at IS NULL",f.member()));
    }
    @Test void indicesDeBaseImpidenSegundoRolYSegundaEmpresaAunqueSeEviteLaApi() {
        F f=fixture(); role(f,"GESTOR_CATALOGO",null,PROJECT);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT :id,:u,id,'PROJECT',:m,:p,:a FROM autorizacion.rol WHERE code='ANALISTA_CALIDAD'")
            .param("id",UUID.randomUUID()).param("u",f.user()).param("m",f.member()).param("p",f.project()).param("a",f.root()).update());
        UUID b=contexto.crearOrganizacion("B","Prueba").id();
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:u,:o,:a)").param("id",UUID.randomUUID()).param("u",f.user()).param("o",b).param("a",f.root()).update());
    }
    @Test void retirarYReactivarProyectoNoDevuelveRolNiAlcancesAnteriores() {
        F f=fixture(); var a=role(f,"GESTOR_CATALOGO",null,PROJECT); api.revocarMembresia(f.org(),f.project(),f.member(),"Retiro");
        assertFalse(allowed(f,"m1.assets.search")); api.reactivarMembresia(f.org(),f.project(),f.member(),"Nueva incorporación");
        assertFalse(allowed(f,"m1.assets.search")); var next=role(f,"GESTOR_CATALOGO",null,PROJECT); assertNotEquals(a.id(),next.id()); assertTrue(allowed(f,"m1.assets.search"));
        api.revocarRol(f.org(),f.project(),f.member(),a.id(),"Reintento antiguo"); assertTrue(allowed(f,"m1.assets.search"));
        api.revocarRol(f.org(),f.project(),f.member(),next.id(),"Retiro"); api.revocarRol(f.org(),f.project(),f.member(),next.id(),"Reintento"); assertFalse(allowed(f,"m1.assets.search"));
    }
    @Test void revocarEmpresaRetiraTodosLosProyectosYReactivarNoRestauraNada() {
        F f=fixture(); role(f,"GESTOR_CATALOGO",null,PROJECT); api.revocarMembresiaEmpresa(f.org(),f.companyMember(),"Baja empresarial");
        assertFalse(allowed(f,"m1.assets.search")); assertEquals("inactive",api.listarMembresias(f.org(),f.project(),1,25).data().getFirst().status());
        api.reactivarMembresiaEmpresa(f.org(),f.companyMember(),"Reingreso"); assertFalse(allowed(f,"m1.assets.search"));
        api.reactivarMembresia(f.org(),f.project(),f.member(),"Reingreso al proyecto"); assertFalse(allowed(f,"m1.assets.search"));
        role(f,"GESTOR_CATALOGO",null,PROJECT); assertTrue(allowed(f,"m1.assets.search"));
    }
    @Test void noPuedeIncorporarEmpleadoAjenoNiAdministrarProyectosDeOtraEmpresa() {
        F f=fixture(),other=fixture(); actor.set(f.admin());
        assertThrows(ContextoException.class,()->api.incorporar(f.org(),f.project(),other.user(),"Cruce"));
        assertThrows(ContextoException.class,()->api.listarMembresias(f.org(),other.project(),1,25));
        assertThrows(AccesoDenegadoException.class,()->api.listarMembresias(other.org(),other.project(),1,25));
        assertThrows(ContextoException.class,()->api.revocarMembresia(f.org(),f.project(),other.member(),"Cruce"));
    }
    @Test void delegacionNoPermiteAutoconcederseRolesNiGestionarAdministradores() {
        F f=fixture(); actor.set(f.root()); var own=api.incorporar(f.org(),f.project(),f.admin(),"Alta por sistema"); actor.set(f.admin());
        assertThrows(ContextoException.class,()->api.asignarRol(f.org(),f.project(),own.id(),"GESTOR_CATALOGO",PROJECT,null,"Autoasignación"));
        assertThrows(ContextoException.class,()->api.revocarMembresia(f.org(),f.project(),own.id(),"Autoadministración"));
        actor.set(f.root()); api.asignarRol(f.org(),f.project(),own.id(),"GESTOR_CATALOGO",PROJECT,null,"Asignación por otro actor");
        assertThrows(ContextoException.class,()->role(f,"ADMIN_SISTEMA",null,PROJECT));
        assertThrows(ContextoException.class,()->role(f,"ADMIN_EMPRESA",null,PROJECT)); assertEquals(4,api.listarRoles(f.org()).size());
    }
    @Test void alcancesDeRecursoSeValidanConElPropietarioYNuncaPorIdsAportadosSinJerarquia() {
        F f=fixture(); UUID source=UUID.randomUUID(),asset=UUID.randomUUID(),sibling=UUID.randomUUID();
        recursos.rows.put(new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.SOURCE,source),new ProveedorRecursosAutorizacion.Jerarquia(f.project(),source,null));
        recursos.rows.put(new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.ASSET,asset),new ProveedorRecursosAutorizacion.Jerarquia(f.project(),source,asset));
        recursos.rows.put(new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.ASSET,sibling),new ProveedorRecursosAutorizacion.Jerarquia(f.project(),source,sibling));
        var a=role(f,"GESTOR_CATALOGO",null,List.of(new Alcance(TipoAlcance.ASSET,asset)));
        assertTrue(permisos.evaluar(f.user(),SolicitudAcceso.recurso("m1.dictionary.read",f.project(),SolicitudAcceso.TipoRecurso.ASSET,asset)).permitido());
        assertFalse(permisos.evaluar(f.user(),SolicitudAcceso.recurso("m1.dictionary.read",f.project(),SolicitudAcceso.TipoRecurso.ASSET,sibling)).permitido());
        assertThrows(ContextoException.class,()->role(f,"GESTOR_CATALOGO",a.id(),List.of(new Alcance(TipoAlcance.ASSET,UUID.randomUUID()))));
        assertThrows(ContextoException.class,()->role(f,"GESTOR_CATALOGO",a.id(),List.of(new Alcance(TipoAlcance.SOURCE,source),new Alcance(TipoAlcance.PROJECT,null))));
        assertThrows(ContextoException.class,()->role(f,"GESTOR_CATALOGO",a.id(),List.of()));
        assertThrows(ContextoException.class,()->role(f,"GESTOR_CATALOGO",a.id(),List.of(new Alcance(TipoAlcance.ASSET,asset),new Alcance(TipoAlcance.ASSET,asset))));
    }
    @Test void archivoPermiteSoloLecturasPersistidasConPermisosVigentesYRevocacionSigueDisponible() {
        F f=fixture(); var a=role(f,"GESTOR_CATALOGO",null,PROJECT); assertTrue(allowed(f,"m1.discovery.execute"));
        var archive=api.archivarProyecto(f.org(),f.project(),0,"Archivo"); assertEquals("archived",archive.project().status()); assertEquals(1,archive.revision());
        assertTrue(allowed(f,"m1.assets.search")); assertTrue(allowed(f,"m1.dictionary.read")); assertTrue(allowed(f,"m1.metadata.history.read"));
        assertFalse(allowed(f,"m1.discovery.execute")); assertFalse(allowed(f,"m1.sources.create"));
        assertThrows(ContextoException.class,()->role(f,"ANALISTA_CALIDAD",a.id(),PROJECT));
        actor.set(f.user()); assertEquals("archived",api.miContexto(f.project()).projectStatus());
        assertTrue(api.misPermisos(f.project()).stream().noneMatch(p->p.permission().equals("m1.discovery.execute")));
        actor.set(f.root()); api.revocarRol(f.org(),f.project(),f.member(),a.id(),"Retiro en archivo"); assertFalse(allowed(f,"m1.dictionary.read"));
        api.revocarMembresia(f.org(),f.project(),f.member(),"Retiro en archivo"); actor.set(f.user()); assertThrows(ContextoException.class,()->api.miContexto(f.project()));
    }
    @Test void archivoMantieneFiltroDeActivoYNoConcedeLecturaAOtrosUsuarios() {
        F f=fixture(); UUID source=UUID.randomUUID(),asset=UUID.randomUUID(),other=UUID.randomUUID();
        recursos.rows.put(new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.ASSET,asset),new ProveedorRecursosAutorizacion.Jerarquia(f.project(),source,asset));
        recursos.rows.put(new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.ASSET,other),new ProveedorRecursosAutorizacion.Jerarquia(f.project(),source,other));
        role(f,"GESTOR_CATALOGO",null,List.of(new Alcance(TipoAlcance.ASSET,asset))); api.archivarProyecto(f.org(),f.project(),0,"Archivo");
        assertTrue(permisos.evaluar(f.user(),SolicitudAcceso.recurso("m1.dictionary.read",f.project(),SolicitudAcceso.TipoRecurso.ASSET,asset)).permitido());
        assertFalse(permisos.evaluar(f.user(),SolicitudAcceso.recurso("m1.dictionary.read",f.project(),SolicitudAcceso.TipoRecurso.ASSET,other)).permitido());
        assertFalse(permisos.evaluar(f.root(),SolicitudAcceso.proyecto("m1.dictionary.read",f.project())).permitido());
    }
    @Test void reactivarProyectoConservaRolPeroNoConfundeSuspensionConArchivo() {
        F f=fixture(); role(f,"GESTOR_CATALOGO",null,PROJECT); api.archivarProyecto(f.org(),f.project(),0,"Archivo");
        assertEquals(1,api.archivarProyecto(f.org(),f.project(),0,"Reintento").revision());
        api.reactivarProyecto(f.org(),f.project(),1,"Reapertura"); assertTrue(allowed(f,"m1.discovery.execute"));
        jdbc.sql("UPDATE contexto.proyecto SET status='inactive' WHERE id=:id").param("id",f.project()).update();
        assertFalse(allowed(f,"m1.dictionary.read")); assertThrows(ContextoException.class,()->api.reactivarProyecto(f.org(),f.project(),3,"No saltar suspensión"));
    }
    @Test void revisionDeNombreYProyectoDetectaEdicionesPerdidasYSoloAuditaCambiosEfectivos() {
        F f=fixture(); var old=cuentas.consultarUsuario(f.user()); assertEquals(0,old.revision());
        var changed=cuentas.editarUsuario(f.user(),"Nombre nuevo",0,"Corrección"); assertEquals(1,changed.revision()); assertEquals(old.account().email(),changed.account().email());
        assertThrows(CuentasException.class,()->cuentas.editarUsuario(f.user(),"Obsoleto",0,"Conflicto"));
        assertEquals(1,cuentas.editarUsuario(f.user(),"Nombre nuevo",1,"Sin cambio").revision());
        assertEquals(1L,count("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action='tc.user.update'",f.user()));
        assertEquals(1,api.editarProyecto(f.org(),f.project(),"Nuevo",0,"Corrección").revision());
        assertThrows(ContextoException.class,()->api.editarProyecto(f.org(),f.project(),"Obsoleto",0,"Conflicto"));
        actor.set(f.admin()); assertEquals(2,cuentas.editarEmpleado(f.org(),f.user(),"Nombre empresarial",1,"Corrección").revision());
        assertThrows(CuentasException.class,()->cuentas.editarEmpleado(f.org(),f.admin(),"Privilegiado",0,"No permitido"));
    }
    @Test void edicionConcurrenteDeUsuarioConMismaRevisionNoPierdeUnaActualizacion() throws Exception {
        F f=fixture();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> a=()->{ actor.set(f.root()); try { cuentas.editarUsuario(f.user(),"Nombre A",0,"Concurrente"); return true; } catch(CuentasException denied) { return false; } };
            Callable<Boolean> b=()->{ actor.set(f.root()); try { cuentas.editarUsuario(f.user(),"Nombre B",0,"Concurrente"); return true; } catch(CuentasException denied) { return false; } };
            var one=pool.submit(a); var two=pool.submit(b); assertNotEquals(one.get(),two.get());
        }
        assertEquals(1,cuentas.consultarUsuario(f.user()).revision());
    }
    @Test void falloAlAuditarSustitucionRevierteRolAnteriorNuevoYAlcances() {
        F f=fixture(); var first=role(f,"GESTOR_CATALOGO",null,PROJECT);
        jdbc.sql("CREATE FUNCTION auditoria.fallo_participacion() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='tc.functional_role.assign' THEN RAISE EXCEPTION 'prueba'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER fallo_participacion BEFORE INSERT ON auditoria.evento FOR EACH ROW EXECUTE FUNCTION auditoria.fallo_participacion()").update();
        try { assertThrows(Exception.class,()->role(f,"ANALISTA_CALIDAD",first.id(),PROJECT)); }
        finally { jdbc.sql("DROP TRIGGER fallo_participacion ON auditoria.evento").update(); jdbc.sql("DROP FUNCTION auditoria.fallo_participacion()").update(); }
        assertTrue(allowed(f,"m1.assets.search")); assertEquals(1,api.historialRoles(f.org(),f.project(),f.member(),1,25).data().size());
    }
    @Test void suspensionDeEmpresaOCuentaBloqueaTambienContextoHistorico() {
        F f=fixture(); role(f,"GESTOR_CATALOGO",null,PROJECT); api.archivarProyecto(f.org(),f.project(),0,"Archivo");
        jdbc.sql("UPDATE contexto.organizacion SET status='inactive' WHERE id=:id").param("id",f.org()).update();
        assertFalse(allowed(f,"m1.dictionary.read")); actor.set(f.user()); assertThrows(ContextoException.class,()->api.miContexto(f.project()));
    }
    @Test void sinRolPuedeSeleccionarSuProyectoPeroNoObtienePermisosYSinMembresiaDesaparece() {
        F f=fixture(); actor.set(f.user()); assertNotNull(api.miContexto(f.project())); assertTrue(api.misPermisos(f.project()).isEmpty());
        actor.set(f.root()); api.revocarMembresia(f.org(),f.project(),f.member(),"Retiro"); actor.set(f.user());
        assertTrue(api.misContextos(1,25).data().isEmpty()); assertThrows(ContextoException.class,()->api.misPermisos(f.project()));
    }
    @Test void paginaValidacionYReferenciaEsperadaNoConcedenCambiosImplicitamente() {
        F f=fixture(); assertThrows(ContextoException.class,()->api.listarMembresias(f.org(),f.project(),0,25));
        assertThrows(ContextoException.class,()->api.listarMembresiasEmpresa(f.org(),1,101));
        assertThrows(ContextoException.class,()->role(f,"GESTOR_CATALOGO",UUID.randomUUID(),PROJECT));
        role(f,"GESTOR_CATALOGO",null,PROJECT);
        assertThrows(ContextoException.class,()->role(f,"ANALISTA_CALIDAD",null,PROJECT));
        assertTrue(api.historialRoles(f.org(),f.project(),f.member(),Integer.MAX_VALUE,100).data().isEmpty());
    }
    @TestConfiguration static class Config { @Bean Recursos recursosParticipacion() { return new Recursos(); } }
    static class Recursos implements ProveedorRecursosAutorizacion {
        final Map<SolicitudAcceso.Recurso,Jerarquia> rows=new ConcurrentHashMap<>();
        public boolean soporta(SolicitudAcceso.TipoRecurso type) { return true; }
        public Optional<Jerarquia> resolver(SolicitudAcceso.Recurso r) { return Optional.ofNullable(rows.get(r)); }
    }
}
