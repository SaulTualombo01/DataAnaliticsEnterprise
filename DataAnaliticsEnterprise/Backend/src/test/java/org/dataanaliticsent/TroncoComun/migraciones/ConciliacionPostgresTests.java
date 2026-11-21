package org.dataanaliticsent.TroncoComun.migraciones;

import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.dataanaliticsent.TroncoComun.migraciones.internal.infraestructura.ConfiguracionMigraciones;
import static org.dataanaliticsent.TroncoComun.migraciones.internal.infraestructura.ConfiguracionMigraciones.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cada caso usa una base vacía exclusiva del clúster temporal; no usa clean ni repair. */
@Tag("postgres")
class ConciliacionPostgresTests {
    @TempDir Path temp;
    private FluentConfiguration config(int db) {
        String base=System.getenv("DB_RECONCILE_BASE");
        assertNotNull(base,"Ejecutar tools/verificar_postgres_tc.py: requiere bases aisladas");
        assertTrue(base.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tic_reconcile_"));
        assertNotEquals(System.getenv("DB_URL"),base+db);
        return Flyway.configure().dataSource(base+db,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
    }
    private Flyway nuevo(int db) {var c=config(db);ConfiguracionMigraciones.preparar(c);return c.load();}
    private Flyway anterior(int db,String linea,String version) {return config(db).locations(COMUN,linea).target(version).load();}
    private String consulta(int db,String sql) throws Exception {
        try(var c=config(db).getDataSource().getConnection();var s=c.createStatement();var r=s.executeQuery(sql)) {
            assertTrue(r.next());return r.getString(1);
        }
    }
    private void ejecutar(int db,String sql) throws Exception {
        try(var c=config(db).getDataSource().getConnection();var s=c.createStatement()){s.execute(sql);}
    }
    private String historial(int db) throws Exception {
        return consulta(db,"SELECT concat(script,':',checksum) FROM flyway_schema_history WHERE version='4'");
    }
    private void finalCorrecto(int db) throws Exception {
        var f=nuevo(db);f.migrate();assertEquals("8",f.info().current().getVersion().getVersion());
        assertEquals(0,f.migrate().migrationsExecuted);assertTrue(f.validateWithResult().validationSuccessful);
        assertEquals("78",consulta(db,"SELECT count(*) FROM autorizacion.permiso"));
        assertEquals("6",consulta(db,"SELECT count(*) FROM autorizacion.rol"));
        assertEquals("1",consulta(db,"SELECT count(*) FROM autorizacion.permiso WHERE code='m4.kpis.evaluate' AND id='c43215cc-e5de-4f86-a4f6-e1a9369c30d3' AND operation_scope='PROJECT' AND NOT archived_read"));
    }
    @Test void instalacionVaciaEligeM0() throws Exception {
        finalCorrecto(1);assertTrue(historial(1).startsWith("V4__administracion_de_empresa.sql:"));
        assertEquals("0",consulta(1,"SELECT count(*) FROM flyway_schema_history WHERE version='4.1'"));
    }
    @Test void actualizaV4M0ConservandoCuentaYAuditoria() throws Exception {
        anterior(2,M0,"4").migrate();String history=historial(2);UUID u=UUID.randomUUID(),e=UUID.randomUUID();
        ejecutar(2,"INSERT INTO identidad.usuario(id,email,display_name) VALUES('"+u+"','anterior@example.test','Anterior')");
        ejecutar(2,"INSERT INTO auditoria.evento(id,actor_type,actor_id,resource_type,resource_id,action,occurred_at,outcome) VALUES('"+e+"','USER','"+u+"','identidad.usuario','"+u+"','prueba.anterior',now(),'SUCCESS')");
        String snapshot=consulta(2,"SELECT (to_jsonb(u)-'admin_revision')::text FROM identidad.usuario u WHERE id='"+u+"'");
        finalCorrecto(2);assertEquals(history,historial(2));
        assertEquals(snapshot,consulta(2,"SELECT (to_jsonb(u)-'admin_revision')::text FROM identidad.usuario u WHERE id='"+u+"'"));
        assertEquals("1",consulta(2,"SELECT count(*) FROM auditoria.evento WHERE id='"+e+"'"));
    }
    @Test void actualizaV7M0ConUnSoloPaso() throws Exception {
        anterior(3,M0,"7").migrate();String history=historial(3);
        assertEquals(1,nuevo(3).migrate().migrationsExecuted);finalCorrecto(3);assertEquals(history,historial(3));
    }
    @Test void actualizaV4M4ConservandoPermisoOriginal() throws Exception {
        anterior(4,M4,"4").migrate();String history=historial(4);
        finalCorrecto(4);assertEquals(history,historial(4));
        assertEquals("1",consulta(4,"SELECT count(*) FROM flyway_schema_history WHERE version='4.1' AND script='V4_1__administracion_de_empresa.sql'"));
        assertEquals("1",consulta(4,"SELECT count(*) FROM autorizacion.rol_permiso rp JOIN autorizacion.rol r ON r.id=rp.role_id JOIN autorizacion.permiso p ON p.id=rp.permission_id WHERE r.code='ANALISTA_NEGOCIO' AND p.code='m4.kpis.evaluate'"));
    }
    @Test void actualizacionNoReactivaPrivilegiosRetiradosEnM4() throws Exception {
        anterior(5,M4,"4").migrate();
        ejecutar(5,"UPDATE autorizacion.permiso SET available=false WHERE code='m4.kpis.evaluate'");
        ejecutar(5,"DELETE FROM autorizacion.rol_permiso WHERE permission_id='c43215cc-e5de-4f86-a4f6-e1a9369c30d3'");
        finalCorrecto(5);assertEquals("false",consulta(5,"SELECT available::text FROM autorizacion.permiso WHERE code='m4.kpis.evaluate'"));
        assertEquals("0",consulta(5,"SELECT count(*) FROM autorizacion.rol_permiso WHERE permission_id='c43215cc-e5de-4f86-a4f6-e1a9369c30d3'"));
    }
    @Test void checksumDistintoFallaSinRepararHistorial() throws Exception {
        String script="V4__permiso_evaluar_kpis_m4.sql";
        try(var in=getClass().getClassLoader().getResourceAsStream("db/migration-m4/"+script)) {
            assertNotNull(in);Files.writeString(temp.resolve(script),new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)+"\nSELECT 42;\n");
        }
        config(6).locations(COMUN,"filesystem:"+temp.toAbsolutePath().toString().replace('\\','/')).target("4").load().migrate();
        String history=historial(6);assertThrows(FlywayException.class,()->nuevo(6).migrate());
        assertEquals(history,historial(6));assertEquals("4",consulta(6,"SELECT max(version) FROM flyway_schema_history"));
    }
    @Test void v4DesconocidaBloqueaAntesDeAplicarCambios() throws Exception {
        Files.writeString(temp.resolve("V4__desconocida.sql"),"SELECT 1;\n");
        config(7).locations(COMUN,"filesystem:"+temp.toAbsolutePath().toString().replace('\\','/')).target("4").load().migrate();
        String history=historial(7);assertThrows(IllegalStateException.class,()->nuevo(7));assertEquals(history,historial(7));
        assertEquals("4",consulta(7,"SELECT max(version) FROM flyway_schema_history"));
    }
    @Test void configuracionDeAmbasLineasNoSeAcepta() {
        var c=config(8).locations(COMUN,M0,M4);
        assertThrows(IllegalStateException.class,()->ConfiguracionMigraciones.preparar(c));
    }
}
