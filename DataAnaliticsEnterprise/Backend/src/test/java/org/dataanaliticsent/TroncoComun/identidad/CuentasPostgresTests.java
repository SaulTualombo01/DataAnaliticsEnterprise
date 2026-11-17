package org.dataanaliticsent.TroncoComun.identidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi;
import org.dataanaliticsent.TroncoComun.identidad.CuentasApi.*;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.PoliticaInvitaciones;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.TokensInvitacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Tag("postgres")
@SpringBootTest
class CuentasPostgresTests {
    private static final String PASSWORD="Frase sintetica para pruebas 2026";
    private static final Instant NOW=Instant.parse("2026-11-14T04:00:00Z");
    private final ThreadLocal<UUID> actor=new ThreadLocal<>();
    @Autowired CuentasApi api;
    @Autowired ContextoApi contexto;
    @Autowired AutorizacionApi permisos;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean IdentidadActualApi identidad;
    @MockitoBean Clock clock;
    @MockitoBean PoliticaInvitaciones policy;
    @BeforeEach void init() {
        when(identidad.usuarioId()).thenAnswer(call->actor.get()); when(clock.instant()).thenReturn(NOW);
        when(policy.invitationTtl()).thenReturn(Duration.ofHours(24)); when(policy.manualDeliveryEnabled()).thenReturn(true);
    }
    private record Fixture(UUID global,UUID org) { }
    private Fixture fixture() {
        UUID global=global(false); actor.set(global);
        return new Fixture(global,contexto.crearOrganizacion("Empresa "+UUID.randomUUID(),"Prueba").id());
    }
    private UUID cuenta(boolean credencial) {
        UUID id=UUID.randomUUID(); jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba')").param("id",id).param("email",id+"@example.test").update();
        if(credencial) jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)").param("id",id).param("hash",encoder.encode(PASSWORD)).update();
        return id;
    }
    private UUID global(boolean credencial) {
        UUID id=cuenta(credencial);
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'")
            .param("id",UUID.randomUUID()).param("user",id).update(); return id;
    }
    private EntregaInvitacion invitar(UUID org) { return api.invitar(org,UUID.randomUUID()+"@example.test","Empleado","Invitación de prueba"); }
    private long count(String sql,UUID id) { return jdbc.sql(sql).param("id",id).query(Long.class).single(); }
    private void invalid(Runnable operation) { assertEquals(CuentasException.Tipo.INVITACION_INVALIDA,assertThrows(CuentasException.class,operation::run).tipo()); }

    @Test void activaUnaSolaCuentaEnLaEmpresaFijadaSinRolesNiPasswordEnClaro() {
        var f=fixture(); var e=invitar(f.org());
        String hash=jdbc.sql("SELECT token_hash FROM identidad.invitacion WHERE id=:id").param("id",e.invitation().id()).query(String.class).single();
        assertEquals(TokensInvitacion.hash(e.activationToken()),hash); assertFalse(e.toString().contains(e.activationToken()));
        var user=api.activar(e.activationToken(),PASSWORD);
        String stored=jdbc.sql("SELECT password_hash FROM identidad.credencial_local WHERE user_id=:id").param("id",user.id()).query(String.class).single();
        assertTrue(encoder.matches(PASSWORD,stored)); assertNotEquals(PASSWORD,stored);
        assertEquals(1L,count("SELECT count(*) FROM contexto.membresia_organizacion WHERE user_id=:id AND status='active'",user.id()));
        assertEquals(0L,count("SELECT count(*) FROM contexto.membresia_proyecto WHERE user_id=:id",user.id()));
        assertEquals(0L,count("SELECT count(*) FROM autorizacion.asignacion_rol WHERE user_id=:id",user.id()));
        assertEquals(0L,count("SELECT count(*) FROM autorizacion.administrador_empresa WHERE user_id=:id",user.id()));
        invalid(()->api.activar(e.activationToken(),PASSWORD));
        assertEquals(1L,count("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action='tc.invitation.accept'",e.invitation().id()));
        var details=jdbc.sql("SELECT safe_details::text FROM auditoria.evento WHERE resource_id=:id").param("id",e.invitation().id()).query(String.class).list();
        assertTrue(details.stream().noneMatch(v->v.contains(e.activationToken()) || v.contains(PASSWORD) || v.contains(hash)));
    }
    @Test void invitacionCaducadaNoCreaUsuarioYLaListaLaMuestraExpirada() {
        var f=fixture(); var e=invitar(f.org()); when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(2)));
        invalid(()->api.activar(e.activationToken(),PASSWORD));
        assertEquals("expired",api.listarInvitaciones(f.org(),1,25).data().getFirst().status());
        assertFalse(jdbc.sql("SELECT EXISTS(SELECT 1 FROM identidad.usuario WHERE email=:email)").param("email",e.invitation().email()).query(Boolean.class).single());
    }
    @Test void reemitirYRevocarInvalidaTokensAnterioresSinReescribirHistorial() {
        var f=fixture(); var one=invitar(f.org());
        var two=api.invitar(f.org(),one.invitation().email(),"Empleado","Reemisión");
        invalid(()->api.activar(one.activationToken(),PASSWORD));
        api.revocarInvitacion(f.org(),two.invitation().id(),"Retiro"); api.revocarInvitacion(f.org(),two.invitation().id(),"Reintento");
        invalid(()->api.activar(two.activationToken(),PASSWORD)); assertEquals(2,api.listarInvitaciones(f.org(),1,25).data().size());
        assertEquals(1L,count("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action='tc.invitation.revoke'",two.invitation().id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("UPDATE identidad.invitacion SET status='pending',revoked_at=NULL WHERE id=:id").param("id",two.invitation().id()).update());
    }
    @Test void perdidaDePermisoDelInvitanteYEempresaSuspendidaImposibilitanActivar() {
        var f=fixture(); UUID admin=cuenta(false); var a=contexto.asignarAdministrador(f.org(),admin,"Delegación");
        actor.set(admin); var e=invitar(f.org()); actor.set(f.global()); contexto.revocarAdministrador(f.org(),a.id(),"Retiro");
        invalid(()->api.activar(e.activationToken(),PASSWORD));
        var other=invitar(f.org()); jdbc.sql("UPDATE contexto.organizacion SET status='inactive' WHERE id=:id").param("id",f.org()).update();
        invalid(()->api.activar(other.activationToken(),PASSWORD));
    }
    @Test void correoUnicoNormalizadoImpideDuplicarCuentaEntreEmpresas() {
        var f=fixture(); UUID b=contexto.crearOrganizacion("B","Prueba").id(); String email=UUID.randomUUID()+"@example.test";
        var one=api.invitar(f.org(),"  "+email.toUpperCase()+"  ","Empleado","Prueba");
        var two=api.invitar(b,email,"Empleado","Prueba"); var user=api.activar(one.activationToken(),PASSWORD);
        assertEquals(email,user.email()); invalid(()->api.activar(two.activationToken(),PASSWORD));
        assertEquals(CuentasException.Tipo.CONFLICTO,assertThrows(CuentasException.class,()->api.invitar(b,email,"Otro","Prueba")).tipo());
        assertEquals(1L,count("SELECT count(*) FROM contexto.membresia_organizacion WHERE user_id=:id",user.id()));
    }
    @Test void consumoConcurrenteDeTokenPublicaUnaSolaCuenta() throws Exception {
        var f=fixture(); var e=invitar(f.org());
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work=()->{ try { api.activar(e.activationToken(),PASSWORD); return true; } catch(CuentasException denied) { return false; } };
            var a=pool.submit(work); var b=pool.submit(work); assertNotEquals(a.get(),b.get());
        }
        assertEquals(1L,jdbc.sql("SELECT count(*) FROM identidad.usuario WHERE normalized_email=:email").param("email",e.invitation().email()).query(Long.class).single());
    }
    @Test void empresaNoPuedeListarAjenaDesactivarAdministradoresNiSuplantarSistema() {
        var f=fixture(); UUID admin=cuenta(false),peer=cuenta(false); contexto.asignarAdministrador(f.org(),admin,"Delegación"); contexto.asignarAdministrador(f.org(),peer,"Delegación");
        UUID b=contexto.crearOrganizacion("Ajena","Prueba").id(); var e=invitar(b); var user=api.activar(e.activationToken(),PASSWORD); actor.set(admin);
        assertThrows(AccesoDenegadoException.class,()->api.listarUsuarios(1,25));
        assertThrows(AccesoDenegadoException.class,()->api.listarEmpleados(b,1,25));
        assertThrows(AccesoDenegadoException.class,()->api.invitar(b,UUID.randomUUID()+"@example.test","X","Prueba"));
        assertThrows(CuentasException.class,()->api.desactivarEmpleado(f.org(),peer,"No permitido"));
        assertThrows(CuentasException.class,()->api.desactivarEmpleado(f.org(),admin,"No permitido"));
        assertEquals(CuentasException.Tipo.NO_DISPONIBLE,assertThrows(CuentasException.class,()->api.desactivarEmpleado(f.org(),user.id(),"Ajeno")).tipo());
    }
    @Test void desactivarRetiraSesionMembresiasRolesEInvitacionesYReactivarNoLosRecupera() {
        var f=fixture(); var e=invitar(f.org()); var user=api.activar(e.activationToken(),PASSWORD);
        contexto.asignarAdministrador(f.org(),user.id(),"Delegación"); actor.set(user.id()); var own=invitar(f.org()); actor.set(f.global());
        UUID project=contexto.crearProyecto(f.org(),"Proyecto","Prueba").id(),pm=UUID.randomUUID();
        jdbc.sql("INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by) SELECT :id,id,user_id,organization_id,:project,:actor FROM contexto.membresia_organizacion WHERE user_id=:user AND organization_id=:org")
            .param("id",pm).param("project",project).param("actor",f.global()).param("user",user.id()).param("org",f.org()).update();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT :id,:user,id,'PROJECT',:pm,:project,:actor FROM autorizacion.rol WHERE code='GESTOR_CATALOGO'")
            .param("id",UUID.randomUUID()).param("user",user.id()).param("pm",pm).param("project",project).param("actor",f.global()).update();
        long version=count("SELECT session_version FROM identidad.credencial_local WHERE user_id=:id",user.id());
        api.desactivarUsuario(user.id(),"Baja del empleado"); api.desactivarUsuario(user.id(),"Reintento");
        assertEquals(version+1,count("SELECT session_version FROM identidad.credencial_local WHERE user_id=:id",user.id()));
        for(String table:new String[]{"contexto.membresia_organizacion","contexto.membresia_proyecto","autorizacion.asignacion_rol","autorizacion.administrador_empresa"})
            assertEquals(0L,count("SELECT count(*) FROM "+table+" WHERE user_id=:id AND status='active'",user.id()));
        invalid(()->api.activar(own.activationToken(),PASSWORD));
        jdbc.sql("UPDATE identidad.usuario SET status='active' WHERE id=:id").param("id",user.id()).update();
        assertFalse(permisos.evaluar(user.id(),SolicitudAcceso.organizacion("tc.company.users.invite",f.org())).permitido());
        assertEquals(1L,count("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action='tc.user.deactivate'",user.id()));
    }
    @Test void administradorDeEmpresaDesactivaEmpleadoPropioPeroNoCuentaConOtraEmpresaActiva() {
        var f=fixture(); UUID admin=cuenta(false); contexto.asignarAdministrador(f.org(),admin,"Delegación");
        var user=api.activar(invitar(f.org()).activationToken(),PASSWORD); actor.set(admin);
        api.desactivarEmpleado(f.org(),user.id(),"Baja"); assertEquals("inactive",api.listarEmpleados(f.org(),1,25).data().stream().filter(u->u.id().equals(user.id())).findFirst().orElseThrow().status());
        actor.set(f.global()); var shared=api.activar(invitar(f.org()).activationToken(),PASSWORD); UUID b=contexto.crearOrganizacion("B","Prueba").id();
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:actor)").param("id",UUID.randomUUID()).param("user",shared.id()).param("org",b).param("actor",f.global()).update());
        actor.set(admin); api.desactivarEmpleado(f.org(),shared.id(),"Baja dentro de una única empresa");
    }
    private void retirarAdministradoresPrevios() {
        // Solo datos sintéticos en el clúster desechable de pruebas.
        jdbc.sql("UPDATE identidad.usuario SET status='inactive' WHERE id IN (SELECT ar.user_id FROM autorizacion.asignacion_rol ar JOIN autorizacion.rol r ON r.id=ar.role_id WHERE r.code='ADMIN_SISTEMA' AND ar.status='active')").update();
    }
    @Test void protegeUltimoAdministradorConCredencialYPermiteRetiroSoloConRelevoAccesible() {
        retirarAdministradoresPrevios(); UUID first=global(true); actor.set(first);
        assertEquals(CuentasException.Tipo.CONFLICTO,assertThrows(CuentasException.class,()->api.desactivarUsuario(first,"Autobaja")).tipo());
        global(false); assertThrows(CuentasException.class,()->api.desactivarUsuario(first,"Cuenta sin clave no es relevo"));
        global(true); api.desactivarUsuario(first,"Relevo disponible");
        assertEquals("inactive",jdbc.sql("SELECT status FROM identidad.usuario WHERE id=:id").param("id",first).query(String.class).single());
    }
    @Test void bajasConcurrentesNoEliminanLosDosAdministradoresAccesibles() throws Exception {
        retirarAdministradoresPrevios(); UUID first=global(true),second=global(true);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> a=()->{ actor.set(first); try { api.desactivarUsuario(first,"Autobaja concurrente"); return true; } catch(CuentasException|AccesoDenegadoException denied) { return false; } };
            Callable<Boolean> b=()->{ actor.set(second); try { api.desactivarUsuario(second,"Autobaja concurrente"); return true; } catch(CuentasException|AccesoDenegadoException denied) { return false; } };
            var one=pool.submit(a); var two=pool.submit(b); assertNotEquals(one.get(),two.get());
        }
        assertEquals(1L,jdbc.sql("SELECT count(*) FROM identidad.usuario WHERE id IN (:a,:b) AND status='active'").param("a",first).param("b",second).query(Long.class).single());
    }
    @Test void falloDeAuditoriaRevierteInvitacionYActivacionSinDejarCredencialOMembresia() {
        var f=fixture(); var e=invitar(f.org());
        jdbc.sql("CREATE FUNCTION auditoria.fallo_cuentas_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='tc.invitation.accept' THEN RAISE EXCEPTION 'prueba atomica'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER fallo_cuentas_test BEFORE INSERT ON auditoria.evento FOR EACH ROW EXECUTE FUNCTION auditoria.fallo_cuentas_test()").update();
        try { assertThrows(Exception.class,()->api.activar(e.activationToken(),PASSWORD)); }
        finally { jdbc.sql("DROP TRIGGER fallo_cuentas_test ON auditoria.evento").update(); jdbc.sql("DROP FUNCTION auditoria.fallo_cuentas_test()").update(); }
        assertFalse(jdbc.sql("SELECT EXISTS(SELECT 1 FROM identidad.usuario WHERE email=:email)").param("email",e.invitation().email()).query(Boolean.class).single());
        assertEquals("pending",api.listarInvitaciones(f.org(),1,25).data().getFirst().status());
        assertNotNull(api.activar(e.activationToken(),PASSWORD));
    }
    @Test void canalDeshabilitadoNoGuardaInvitacionYValidaCamposYPaginacion() {
        var f=fixture(); when(policy.manualDeliveryEnabled()).thenReturn(false);
        assertEquals(CuentasException.Tipo.ENTREGA_NO_CONFIGURADA,assertThrows(CuentasException.class,()->invitar(f.org())).tipo());
        assertTrue(api.listarInvitaciones(f.org(),1,25).data().isEmpty()); when(policy.manualDeliveryEnabled()).thenReturn(true);
        assertThrows(CuentasException.class,()->api.invitar(f.org(),"sin-correo","Persona","Prueba"));
        assertThrows(CuentasException.class,()->api.listarEmpleados(f.org(),0,25));
        assertThrows(CuentasException.class,()->api.listarUsuarios(1,101));
        invitar(f.org()); invitar(f.org()); assertTrue(api.listarInvitaciones(f.org(),1,1).hasNext());
        assertFalse(api.listarInvitaciones(f.org(),2,1).hasNext()); assertTrue(api.listarUsuarios(Integer.MAX_VALUE,100).data().isEmpty());
    }
    @Test void claveDebilNoConsumeElTokenYNoPermiteModificarLaInvitacion() {
        var f=fixture(); var e=invitar(f.org());
        assertEquals(CuentasException.Tipo.INVALIDO,assertThrows(CuentasException.class,()->api.activar(e.activationToken(),"corta")).tipo());
        assertThrows(CuentasException.class,()->api.activar(e.activationToken(),"é".repeat(37)));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("UPDATE identidad.invitacion SET email='otro@example.test' WHERE id=:id").param("id",e.invitation().id()).update());
        assertNotNull(api.activar(e.activationToken(),PASSWORD));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("DELETE FROM identidad.invitacion WHERE id=:id").param("id",e.invitation().id()).update());
    }
}
