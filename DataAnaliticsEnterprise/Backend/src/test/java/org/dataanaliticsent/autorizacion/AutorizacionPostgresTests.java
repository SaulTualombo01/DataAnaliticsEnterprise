package org.dataanaliticsent.autorizacion;

import java.util.UUID;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest
@Execution(ExecutionMode.SAME_THREAD)
class AutorizacionPostgresTests {
    @Autowired AutorizacionApi api;
    @Autowired JdbcClient jdbc;


    @Test void gestorTienePermisosDeCatalogoYCalidadSoloProgreso() {
        Fixture gestor=fixture("GESTOR_CATALOGO"),calidad=fixture("ANALISTA_CALIDAD");
        assertTrue(api.evaluar(gestor.user(),SolicitudAcceso.proyecto("m1.assets.search",gestor.project())).permitido());
        assertFalse(api.evaluar(gestor.user(),SolicitudAcceso.plataforma("tc.users.create")).permitido());
        assertTrue(api.evaluar(calidad.user(),SolicitudAcceso.proyecto("m1.progress.read",calidad.project())).permitido());
        assertFalse(api.evaluar(calidad.user(),SolicitudAcceso.proyecto("m1.assets.search",calidad.project())).permitido());
        assertFalse(api.evaluar(gestor.user(),SolicitudAcceso.proyecto("m1.assets.search",calidad.project())).permitido());
    }

    @Test void administradorGlobalNoHeredaAccesoAProyectos() {
        UUID admin=usuario();
        jdbc.sql("""
                INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by)
                SELECT :id,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'
                """).param("id",UUID.randomUUID()).param("user",admin).update();
        Fixture proyecto=fixture("GESTOR_CATALOGO");
        assertTrue(api.evaluar(admin,SolicitudAcceso.plataforma("tc.users.create")).permitido());
        assertFalse(api.evaluar(admin,SolicitudAcceso.proyecto("m1.progress.read",proyecto.project())).permitido());
        assertFalse(api.evaluar(admin,SolicitudAcceso.proyecto("m1.assets.search",proyecto.project())).permitido());
    }

    @Test void administradorContextualSoloObservaNoAdministraLaPlataforma() {
        Fixture f=fixture("ADMIN_SISTEMA");
        assertTrue(api.evaluar(f.user(),SolicitudAcceso.proyecto("m1.progress.read",f.project())).permitido());
        assertFalse(api.evaluar(f.user(),SolicitudAcceso.proyecto("m1.assets.search",f.project())).permitido());
        assertFalse(api.evaluar(f.user(),SolicitudAcceso.plataforma("tc.users.create")).permitido());
    }

    @Test void revocacionSeAplicaEnLaSiguienteEvaluacion() {
        Fixture f=fixture("GESTOR_CATALOGO");
        var solicitud=SolicitudAcceso.proyecto("m1.assets.search",f.project());
        assertTrue(api.evaluar(f.user(),solicitud).permitido());
        jdbc.sql("UPDATE autorizacion.asignacion_rol SET revoked_at=now() WHERE id=:id").param("id",f.assignment()).update();
        assertFalse(api.evaluar(f.user(),solicitud).permitido());
    }

    @ParameterizedTest
    @ValueSource(strings={"usuario","organizacion","proyecto","membresia_organizacion","membresia_proyecto","asignacion"})
    void cualquierDesactivacionRelevanteRetiraAcceso(String elemento) {
        Fixture f=fixture("GESTOR_CATALOGO");
        var solicitud=SolicitudAcceso.proyecto("m1.assets.search",f.project());
        assertTrue(api.evaluar(f.user(),solicitud).permitido());
        switch(elemento) {
            case "usuario" -> desactivar("identidad.usuario",f.user());
            case "organizacion" -> desactivar("contexto.organizacion",f.org());
            case "proyecto" -> desactivar("contexto.proyecto",f.project());
            case "membresia_organizacion" -> desactivar("contexto.membresia_organizacion",f.orgMember());
            case "membresia_proyecto" -> desactivar("contexto.membresia_proyecto",f.projectMember());
            case "asignacion" -> desactivar("autorizacion.asignacion_rol",f.assignment());
            default -> throw new IllegalArgumentException();
        }
        assertFalse(api.evaluar(f.user(),solicitud).permitido());
    }

    @Test void reconfigurarPermisosDelRolSeReflejaSinReiniciar() {
        Fixture f=fixture("GESTOR_CATALOGO");
        var solicitud=SolicitudAcceso.proyecto("m1.assets.search",f.project());
        assertTrue(api.evaluar(f.user(),solicitud).permitido());
        UUID role=jdbc.sql("SELECT id FROM autorizacion.rol WHERE code='GESTOR_CATALOGO'").query(UUID.class).single();
        UUID permission=jdbc.sql("SELECT id FROM autorizacion.permiso WHERE code='m1.assets.search'").query(UUID.class).single();
        try {
            jdbc.sql("DELETE FROM autorizacion.rol_permiso WHERE role_id=:role AND permission_id=:permission")
                    .param("role",role).param("permission",permission).update();
            assertFalse(api.evaluar(f.user(),solicitud).permitido());
        } finally {
            jdbc.sql("INSERT INTO autorizacion.rol_permiso(role_id,permission_id) VALUES(:role,:permission)")
                    .param("role",role).param("permission",permission).update();
        }
        assertTrue(api.evaluar(f.user(),solicitud).permitido());
    }

    @Test void opcionDeshabilitadaYRolInactivoSonDenegados() {
        Fixture f=fixture("GESTOR_CATALOGO");
        assertEquals(Motivo.PERMISO_NO_DISPONIBLE,api.evaluar(f.user(),SolicitudAcceso.proyecto("m1.assets.diagram.read",f.project())).motivo());
        try {
            jdbc.sql("UPDATE autorizacion.rol SET status='inactive' WHERE code='GESTOR_CATALOGO'").update();
            assertFalse(api.evaluar(f.user(),SolicitudAcceso.proyecto("m1.assets.search",f.project())).permitido());
        } finally { jdbc.sql("UPDATE autorizacion.rol SET status='active' WHERE code='GESTOR_CATALOGO'").update(); }
    }

    @Test void selfNoNecesitaMembresiaNiDaAccesoAOtraCuenta() {
        UUID user=usuario();
        assertTrue(api.evaluar(user,SolicitudAcceso.propia("tc.self.profile.read",user)).permitido());
        assertFalse(api.evaluar(user,SolicitudAcceso.propia("tc.self.profile.read",usuario())).permitido());
    }

    // Los casos de jerarquía y alcance sobre recursos reales se verifican en
    // CatalogoAutorizacionPostgresTests, conservando las FK de M1.

    private void desactivar(String tabla,UUID id) {
        // tabla solo procede del switch cerrado de esta prueba, no de una petición.
        jdbc.sql("UPDATE "+tabla+" SET status='inactive' WHERE id=:id").param("id",id).update();
    }
    private UUID usuario() {
        UUID id=UUID.randomUUID();
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba RBAC')")
                .param("id",id).param("email",id+"@example.test").update();
        return id;
    }
    private Fixture fixture(String role) {
        UUID user=usuario(),org=UUID.randomUUID(),project=UUID.randomUUID(),om=UUID.randomUUID(),pm=UUID.randomUUID(),assignment=UUID.randomUUID();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'RBAC')").param("id",org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'RBAC')").param("id",project).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:user)")
                .param("id",om).param("user",user).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by) VALUES(:id,:om,:user,:org,:project,:user)")
                .param("id",pm).param("om",om).param("user",user).param("org",org).param("project",project).update();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT :id,:user,id,'PROJECT',:member,:project,:user FROM autorizacion.rol WHERE code=:role")
                .param("id",assignment).param("user",user).param("member",pm).param("project",project).param("role",role).update();
        jdbc.sql("INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope) VALUES(:id,:assignment,:project,'PROJECT')")
                .param("id",UUID.randomUUID()).param("assignment",assignment).param("project",project).update();
        return new Fixture(user,org,project,om,pm,assignment);
    }
    private record Fixture(UUID user,UUID org,UUID project,UUID orgMember,UUID projectMember,UUID assignment) { }

}
