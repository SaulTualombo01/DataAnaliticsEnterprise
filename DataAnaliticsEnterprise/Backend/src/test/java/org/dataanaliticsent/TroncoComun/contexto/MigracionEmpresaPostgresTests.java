package org.dataanaliticsent.TroncoComun.contexto;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class MigracionEmpresaPostgresTests {
    @Test void actualizaDesdeV3SinReescribirUsuariosContextoNiAuditoria() throws Exception {
        String url=System.getenv("DB_UPGRADE_URL"),user=System.getenv("DB_USER"),password=System.getenv("DB_PASSWORD");
        assertNotNull(url,"Usar el verificador aislado: requiere una base adicional DB_UPGRADE_URL");
        assertNotEquals(System.getenv("DB_URL"),url,"La base de actualización debe estar separada");
        var before=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("3").load();
        before.migrate();
        UUID account=UUID.randomUUID(),org=UUID.randomUUID(),event=UUID.randomUUID();
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            sql.execute("INSERT INTO identidad.usuario(id,email,display_name) VALUES('"+account+"','"+account+"@example.test','Cuenta anterior')");
            sql.execute("INSERT INTO contexto.organizacion(id,name) VALUES('"+org+"','Empresa anterior')");
            sql.execute("INSERT INTO auditoria.evento(id,actor_type,actor_id,resource_type,resource_id,action,occurred_at,outcome) VALUES('"+event+"','USER','"+account+"','contexto.organizacion','"+org+"','prueba.pre_v4',now(),'SUCCESS')");
            String snapshot;
            try(var rows=sql.executeQuery("SELECT row_to_json(u)::text FROM identidad.usuario u WHERE id='"+account+"'")) { assertTrue(rows.next()); snapshot=rows.getString(1); }
            var after=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("4").load();
            assertEquals(1,after.migrate().migrationsExecuted); assertEquals("4",after.info().current().getVersion().getVersion());
            assertEquals(0,after.migrate().migrationsExecuted);
            var latest=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("5").load();
            assertEquals(1,latest.migrate().migrationsExecuted); assertEquals("5",latest.info().current().getVersion().getVersion());
            assertEquals(0,latest.migrate().migrationsExecuted);
            try(var rows=sql.executeQuery("SELECT count(*) FROM identidad.invitacion")) { assertTrue(rows.next()); assertEquals(0,rows.getInt(1)); }
            try(var rows=sql.executeQuery("SELECT row_to_json(u)::text FROM identidad.usuario u WHERE id='"+account+"'")) { assertTrue(rows.next()); assertEquals(snapshot,rows.getString(1)); }
            try(var rows=sql.executeQuery("SELECT count(*) FROM auditoria.evento WHERE id='"+event+"' AND action='prueba.pre_v4'")) { assertTrue(rows.next()); assertEquals(1,rows.getInt(1)); }
            try(var rows=sql.executeQuery("SELECT count(*) FROM autorizacion.rol")) { assertTrue(rows.next()); assertEquals(6,rows.getInt(1)); }
            try(var rows=sql.executeQuery("SELECT count(*) FROM autorizacion.administrador_empresa")) { assertTrue(rows.next()); assertEquals(0,rows.getInt(1)); }
            // Datos heredados conflictivos en una base dedicada: V6 debe abortar sin reparar ni borrar.
            UUID other=UUID.randomUUID(),om=UUID.randomUUID(),otherOm=UUID.randomUUID(),project=UUID.randomUUID(),pm=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
            sql.execute("INSERT INTO contexto.organizacion(id,name) VALUES('"+other+"','Otra empresa')");
            sql.execute("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES('"+om+"','"+account+"','"+org+"','"+account+"'),('"+otherOm+"','"+account+"','"+other+"','"+account+"')");
            sql.execute("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES('"+project+"','"+org+"','Proyecto anterior')");
            sql.execute("INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by) VALUES('"+pm+"','"+om+"','"+account+"','"+org+"','"+project+"','"+account+"')");
            sql.execute("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT '"+a+"','"+account+"',id,'PROJECT','"+pm+"','"+project+"','"+account+"' FROM autorizacion.rol WHERE code='GESTOR_CATALOGO'");
            sql.execute("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT '"+b+"','"+account+"',id,'PROJECT','"+pm+"','"+project+"','"+account+"' FROM autorizacion.rol WHERE code='ANALISTA_CALIDAD'");
            var v6=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("6").load();
            assertThrows(org.flywaydb.core.api.FlywayException.class,()->v6.migrate()); assertEquals("5",v6.info().current().getVersion().getVersion());
            sql.execute("UPDATE contexto.membresia_organizacion SET status='inactive' WHERE id='"+otherOm+"'");
            assertThrows(org.flywaydb.core.api.FlywayException.class,()->v6.migrate()); assertEquals("5",v6.info().current().getVersion().getVersion());
            sql.execute("UPDATE autorizacion.asignacion_rol SET status='inactive',revoked_at=now() WHERE id='"+b+"'");
            assertEquals(1,v6.migrate().migrationsExecuted); assertEquals("6",v6.info().current().getVersion().getVersion()); assertEquals(0,v6.migrate().migrationsExecuted);
            try(var rows=sql.executeQuery("SELECT admin_revision,email FROM identidad.usuario WHERE id='"+account+"'")) { assertTrue(rows.next()); assertEquals(0,rows.getLong(1)); assertEquals(account+"@example.test",rows.getString(2)); }
            try(var rows=sql.executeQuery("SELECT count(*) FROM autorizacion.asignacion_rol WHERE id IN ('"+a+"','"+b+"')")) { assertTrue(rows.next()); assertEquals(2,rows.getInt(1)); }
            try(var rows=sql.executeQuery("SELECT count(*) FROM auditoria.evento WHERE id='"+event+"'")) { assertTrue(rows.next()); assertEquals(1,rows.getInt(1)); }
        }
    }
}
