package org.dataanaliticsent.contexto;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.dataanaliticsent.autorizacion.*;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Tag("postgres")
@SpringBootTest
class ContextoPostgresTests {
    @Autowired ContextoApi api;
    @Autowired AutorizacionApi permisos;
    @Autowired JdbcClient jdbc;
    @MockitoBean IdentidadActualApi identidad;

    @Test void administradorDesignadoCreaProyectoSoloEnSuEmpresaSinHeredarM1() {
        UUID global=global(),empleado=usuario(); actor(global);
        var a=api.crearOrganizacion("Empresa A "+UUID.randomUUID(),"Prueba de alta");
        var b=api.crearOrganizacion("Empresa B "+UUID.randomUUID(),"Prueba de alta");
        var asignacion=api.asignarAdministrador(a.id(),empleado,"Designación inicial");
        actor(empleado);
        assertEquals(a.id(),api.consultarOrganizacion(a.id()).id());
        var p=api.crearProyecto(a.id(),"Proyecto autorizado","Alta de proyecto");
        assertEquals(a.id(),p.organizationId());
        assertEquals(p.id(),api.listarProyectos(a.id(),1,25).data().getFirst().id());
        assertThrows(AccesoDenegadoException.class,()->api.crearProyecto(b.id(),"Ajeno","No autorizado"));
        assertThrows(AccesoDenegadoException.class,()->api.listarOrganizaciones(1,25));
        assertFalse(permisos.evaluar(empleado,SolicitudAcceso.proyecto("m1.assets.search",p.id())).permitido());
        assertFalse(permisos.evaluar(empleado,SolicitudAcceso.plataforma("tc.users.create")).permitido());
        assertEquals(1L,eventos(asignacion.id(),"tc.company_administrator.assign"));
    }
    @Test void soloSistemaPuedeCrearEmpresaODesignarOtroAdministrador() {
        UUID global=global(),empleado=usuario(),otro=usuario(); actor(global);
        var o=api.crearOrganizacion("Empresa","Creación"); api.asignarAdministrador(o.id(),empleado,"Designación");
        actor(empleado);
        assertThrows(AccesoDenegadoException.class,()->api.crearOrganizacion("Otra","No autorizado"));
        assertThrows(AccesoDenegadoException.class,()->api.asignarAdministrador(o.id(),otro,"No autorizado"));
        actor(global); var designaciones=api.listarAdministradores(o.id(),1,25);
        assertEquals(1,designaciones.data().size());
    }
    @Test void revocarEsInmediatoIdempotenteYUnaNuevaDesignacionNoReutilizaLaAnterior() {
        UUID global=global(),empleado=usuario(); actor(global);
        var o=api.crearOrganizacion("Empresa","Creación"); var anterior=api.asignarAdministrador(o.id(),empleado,"Designación");
        assertTrue(permisos.evaluar(empleado,SolicitudAcceso.organizacion("tc.company.projects.create",o.id())).permitido());
        api.revocarAdministrador(o.id(),anterior.id(),"Revocación"); api.revocarAdministrador(o.id(),anterior.id(),"Reintento");
        assertFalse(permisos.evaluar(empleado,SolicitudAcceso.organizacion("tc.company.projects.create",o.id())).permitido());
        assertEquals(1L,eventos(anterior.id(),"tc.company_administrator.revoke"));
        var nueva=api.asignarAdministrador(o.id(),empleado,"Nueva autorización"); assertNotEquals(anterior.id(),nueva.id());
        assertEquals(2,api.listarAdministradores(o.id(),1,25).data().size());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.sql("UPDATE autorizacion.administrador_empresa SET status='active',revoked_at=NULL WHERE id=:id").param("id",anterior.id()).update());
    }
    @Test void reactivarMembresiaNoRestauraLaDesignacionRevocada() {
        UUID global=global(),empleado=usuario(); actor(global);
        var o=api.crearOrganizacion("Empresa","Creación"); var a=api.asignarAdministrador(o.id(),empleado,"Designación");
        jdbc.sql("UPDATE contexto.membresia_organizacion SET status='inactive' WHERE user_id=:user AND organization_id=:org").param("user",empleado).param("org",o.id()).update();
        jdbc.sql("UPDATE contexto.membresia_organizacion SET status='active' WHERE user_id=:user AND organization_id=:org").param("user",empleado).param("org",o.id()).update();
        assertFalse(permisos.evaluar(empleado,SolicitudAcceso.organizacion("tc.company.projects.create",o.id())).permitido());
        assertNotEquals(a.id(),api.asignarAdministrador(o.id(),empleado,"Nueva designación expresa").id());
    }
    @Test void noIncorporaEmpleadoQuePerteneceAOtraEmpresaNiReactivaMembresias() {
        UUID global=global(),empleado=usuario(); actor(global);
        var a=api.crearOrganizacion("A","Creación"); var b=api.crearOrganizacion("B","Creación");
        var designacion=api.asignarAdministrador(a.id(),empleado,"Designación");
        api.revocarAdministrador(a.id(),designacion.id(),"Retiro");
        assertThrows(ContextoException.class,()->api.asignarAdministrador(b.id(),empleado,"Cruce de empresa"));
        assertEquals(1L,jdbc.sql("SELECT count(*) FROM contexto.membresia_organizacion WHERE user_id=:user").param("user",empleado).query(Long.class).single());
        jdbc.sql("UPDATE contexto.membresia_organizacion SET status='inactive' WHERE user_id=:user").param("user",empleado).update();
        assertThrows(ContextoException.class,()->api.asignarAdministrador(a.id(),empleado,"No reactivar implícitamente"));
    }
    @Test void usuarioOEmpresaInactivosImpidenAdministrar() {
        UUID global=global(),empleado=usuario(); actor(global);
        var o=api.crearOrganizacion("Empresa","Creación"); api.asignarAdministrador(o.id(),empleado,"Designación");
        jdbc.sql("UPDATE identidad.usuario SET status='inactive' WHERE id=:user").param("user",empleado).update();
        assertFalse(permisos.evaluar(empleado,SolicitudAcceso.organizacion("tc.company.projects.create",o.id())).permitido());
        jdbc.sql("UPDATE identidad.usuario SET status='active' WHERE id=:user").param("user",empleado).update();
        jdbc.sql("UPDATE contexto.organizacion SET status='inactive' WHERE id=:org").param("org",o.id()).update();
        assertFalse(permisos.evaluar(empleado,SolicitudAcceso.organizacion("tc.company.projects.create",o.id())).permitido());
        // El sistema puede retirar delegaciones incluso si la empresa está suspendida.
        var a=api.listarAdministradores(o.id(),1,25).data().getFirst(); api.revocarAdministrador(o.id(),a.id(),"Retiro durante suspensión");
    }
    @Test void duplicadoDeProyectoRevierteSuOperacionYNoAgregaAuditoria() {
        UUID global=global(); actor(global); var o=api.crearOrganizacion("Empresa","Creación");
        var p=api.crearProyecto(o.id(),"Único","Creación");
        assertThrows(ContextoException.class,()->api.crearProyecto(o.id(),"Único","Duplicado"));
        assertEquals(1,api.listarProyectos(o.id(),1,25).data().size()); assertEquals(1L,eventos(p.id(),"tc.project.create"));
    }
    @Test void falloDeAuditoriaRevierteTambienElProyecto() {
        UUID global=global(); actor(global); var o=api.crearOrganizacion("Empresa","Creación");
        // Cambio únicamente de prueba: el trigger se limita a este proyecto por nombre.
        jdbc.sql("CREATE FUNCTION auditoria.fallo_contexto_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.safe_details->>'reason'='FALLO_ATOMICO_TEST' THEN RAISE EXCEPTION 'prueba'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER fallo_contexto_test BEFORE INSERT ON auditoria.evento FOR EACH ROW EXECUTE FUNCTION auditoria.fallo_contexto_test()").update();
        try { assertThrows(Exception.class,()->api.crearProyecto(o.id(),"Debe revertirse","FALLO_ATOMICO_TEST")); }
        finally { jdbc.sql("DROP TRIGGER fallo_contexto_test ON auditoria.evento").update(); jdbc.sql("DROP FUNCTION auditoria.fallo_contexto_test()").update(); }
        assertTrue(api.listarProyectos(o.id(),1,25).data().isEmpty());
    }
    @Test void dosDesignacionesConcurrentesParaEmpresasDistintasNoAmplianLaPertenencia() throws Exception {
        UUID global=global(),empleado=usuario(); actor(global);
        var a=api.crearOrganizacion("A","Creación"); var b=api.crearOrganizacion("B","Creación");
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> una=()->{ try { api.asignarAdministrador(a.id(),empleado,"Concurrente"); return true; } catch(ContextoException denied) { return false; } };
            Callable<Boolean> otra=()->{ try { api.asignarAdministrador(b.id(),empleado,"Concurrente"); return true; } catch(ContextoException denied) { return false; } };
            var first=pool.submit(una); var second=pool.submit(otra);
            assertNotEquals(first.get(),second.get());
        }
        assertEquals(1L,jdbc.sql("SELECT count(*) FROM autorizacion.administrador_empresa WHERE user_id=:user AND status='active'").param("user",empleado).query(Long.class).single());
        assertEquals(1L,jdbc.sql("SELECT count(*) FROM contexto.membresia_organizacion WHERE user_id=:user AND status='active'").param("user",empleado).query(Long.class).single());
    }
    @Test void paginaYValidaSinExponerOtrosProyectos() {
        UUID global=global(); actor(global); var o=api.crearOrganizacion("Empresa","Creación");
        api.crearProyecto(o.id(),"Uno","Creación"); api.crearProyecto(o.id(),"Dos","Creación");
        var first=api.listarProyectos(o.id(),1,1); assertTrue(first.hasNext()); assertEquals(1,first.data().size());
        var second=api.listarProyectos(o.id(),2,1); assertFalse(second.hasNext()); assertNotEquals(first.data().getFirst().id(),second.data().getFirst().id());
        assertTrue(api.listarProyectos(o.id(),Integer.MAX_VALUE,100).data().isEmpty());
        assertThrows(ContextoException.class,()->api.listarProyectos(o.id(),0,25));
        assertThrows(ContextoException.class,()->api.listarProyectos(o.id(),1,101));
        assertThrows(ContextoException.class,()->api.crearProyecto(o.id()," ","Creación"));
    }
    private void actor(UUID id) { when(identidad.usuarioId()).thenReturn(id); }
    private UUID usuario() {
        UUID id=UUID.randomUUID(); jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Empleado de prueba')")
                .param("id",id).param("email",id+"@example.test").update(); return id;
    }
    private UUID global() {
        UUID id=usuario();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by) SELECT :id,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'")
                .param("id",UUID.randomUUID()).param("user",id).update(); return id;
    }
    private long eventos(UUID recurso,String action) {
        return jdbc.sql("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id AND action=:action")
                .param("id",recurso).param("action",action).query(Long.class).single();
    }
}
