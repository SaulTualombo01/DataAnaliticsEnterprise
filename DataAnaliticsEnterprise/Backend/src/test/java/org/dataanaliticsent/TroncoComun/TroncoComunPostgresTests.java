package org.dataanaliticsent.TroncoComun;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest
class TroncoComunPostgresTests {
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @Test
    void migraSinDuplicarRolesYPermisosNiDarAccesoFuncionalAlAdministrador() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(6L, jdbc.sql("SELECT count(*) FROM autorizacion.rol").query(Long.class).single());
        assertEquals(78L, jdbc.sql("SELECT count(*) FROM autorizacion.permiso").query(Long.class).single());
        assertEquals(0L, jdbc.sql("""
                SELECT count(*) FROM autorizacion.rol_permiso rp
                JOIN autorizacion.rol r ON r.id=rp.role_id JOIN autorizacion.permiso p ON p.id=rp.permission_id
                WHERE r.code='ADMIN_SISTEMA' AND p.code LIKE 'm1.%' AND p.code<>'m1.progress.read'
                """).query(Long.class).single());
        assertFalse(jdbc.sql("SELECT available FROM autorizacion.permiso WHERE code='m1.assets.diagram.read'").query(Boolean.class).single());
    }

    @Test
    void noVinculaUnaMembresiaOrganizativaAUnProyectoAjeno() {
        Fixture a = fixture();
        Fixture b = fixture();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by)
                VALUES(:id,:membership,:user,:org,:project,:user)
                """).param("id", UUID.randomUUID()).param("membership", a.organizationMembership())
                .param("user", a.user()).param("org", a.organization()).param("project", b.project()).update());
    }

    @Test
    void unaAsignacionDePlataformaNoAceptaContextoDeProyecto() {
        Fixture f = fixture();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by)
                SELECT :id,:user,id,'PLATFORM',:membership,:project,:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'
                """).param("id", UUID.randomUUID()).param("user", f.user())
                .param("membership", f.projectMembership()).param("project", f.project()).update());
    }

    @Test
    void alcanceNoPuedeCambiarElProyectoDeSuAsignacion() {
        Fixture a = fixture();
        Fixture b = fixture();
        UUID assignment = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by)
                SELECT :id,:user,id,'PROJECT',:membership,:project,:user FROM autorizacion.rol WHERE code='GESTOR_CATALOGO'
                """).param("id", assignment).param("user", a.user()).param("membership", a.projectMembership())
                .param("project", a.project()).update();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope)
                VALUES(:id,:assignment,:project,'PROJECT')
                """).param("id", UUID.randomUUID()).param("assignment", assignment).param("project", b.project()).update());
    }

    @Test
    void eventosYVersionesNoSeModificanPorOperacionesOrdinarias() {
        Fixture f = fixture();
        UUID event = UUID.randomUUID(), version = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,occurred_at,outcome)
                VALUES(:id,'USER',:user,:project,'contexto.proyecto',:project,'prueba',now(),'SUCCESS')
                """).param("id", event).param("user", f.user()).param("project", f.project()).update();
        jdbc.sql("""
                INSERT INTO versionado.version_recurso(id,project_id,resource_type,resource_id,number,created_at,actor_type,actor_id,
                    reason,representation_version,state_payload,content_hash,audit_event_id)
                VALUES(:id,:project,'contexto.proyecto',:project,1,now(),'USER',:user,'Prueba',1,'{}',repeat('0',64),:event)
                """).param("id", version).param("project", f.project()).param("user", f.user()).param("event", event).update();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("UPDATE versionado.version_recurso SET reason='otro' WHERE id=:id")
                .param("id", version).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("DELETE FROM auditoria.evento WHERE id=:id").param("id", event).update());
    }

    private Fixture fixture() {
        UUID user = UUID.randomUUID(), org = UUID.randomUUID(), project = UUID.randomUUID();
        UUID orgMember = UUID.randomUUID(), projectMember = UUID.randomUUID();
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba')")
                .param("id", user).param("email", user+"@example.test").update();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'Prueba')").param("id", org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'Prueba')")
                .param("id", project).param("org", org).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:user)")
                .param("id", orgMember).param("user", user).param("org", org).update();
        jdbc.sql("""
                INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by)
                VALUES(:id,:membership,:user,:org,:project,:user)
                """).param("id", projectMember).param("membership", orgMember).param("user", user).param("org", org).param("project", project).update();
        return new Fixture(user,org,project,orgMember,projectMember);
    }

    private record Fixture(UUID user, UUID organization, UUID project, UUID organizationMembership, UUID projectMembership) { }
}
