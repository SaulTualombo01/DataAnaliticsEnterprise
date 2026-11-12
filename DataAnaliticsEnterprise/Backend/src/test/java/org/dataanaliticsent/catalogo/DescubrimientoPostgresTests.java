package org.dataanaliticsent.catalogo;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.catalogo.CatalogoApi.*;
import org.dataanaliticsent.catalogo.DescubrimientoFallidoException.Codigo;
import org.dataanaliticsent.catalogo.internal.aplicacion.LimitesDescubrimiento;
import org.dataanaliticsent.catalogo.internal.dominio.ExtractorPostgres;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("postgres")
@SpringBootTest
@Execution(ExecutionMode.SAME_THREAD)
class DescubrimientoPostgresTests {
    @Autowired DescubrimientoPostgresApi discovery;
    @Autowired CatalogoApi catalogo;
    @Autowired JdbcClient jdbc;
    @Autowired LimitesDescubrimiento limites;
    @MockitoBean IdentidadActualApi identidad;
    @MockitoSpyBean ExtractorPostgres extractor;

    @Test void descubreColumnasClavesCompuestasYProcedenciaSinLeerFilas() throws Exception {
        Fixture f=fixture("GESTOR_CATALOGO","fixture");
        var resultado=descubrir(f,"m103_core");
        assertEquals(2,resultado.tables().size());
        var parent=tabla(resultado,"parent"); var child=tabla(resultado,"child");
        var detalle=catalogo.consultarActivo(f.project(),child.assetId()).orElseThrow();
        assertEquals(OrigenEstructura.DECLARED,detalle.structureOrigin());
        assertEquals("m103_core",detalle.originNamespace());
        assertEquals(child.metadataVersionId(),detalle.metadataVersionId());
        var columns=catalogo.listarCampos(f.project(),parent.assetId());
        var pk=catalogo.listarRestricciones(f.project(),parent.assetId()).stream()
                .filter(k->k.kind()==TipoRestriccion.PK).findFirst().orElseThrow();
        assertEquals(List.of(columns.get(1).id(),columns.get(0).id()),pk.members().stream().map(MiembroRestriccion::sourceFieldId).toList());
        var fk=catalogo.listarRestricciones(f.project(),child.assetId()).stream()
                .filter(k->k.name().equals("child_parent_fk")).findFirst().orElseThrow();
        assertEquals(parent.assetId(),fk.targetAssetId());
        assertEquals(ResolucionReferencia.RESOLVED,fk.resolution());
        assertEquals(List.of("code","tenant"),fk.members().stream().map(MiembroRestriccion::targetFieldName).toList());
        String payload=jdbc.sql("SELECT state_payload::text FROM versionado.version_recurso WHERE id=:id")
                .param("id",child.metadataVersionId()).query(String.class).single();
        assertTrue(payload.contains("child_parent_fk"));
        assertFalse(payload.contains("NEVER_COPY_ROW_7"));
        assertFalse(payload.contains(env("TIC_SOURCE_FIXTURE_PASSWORD")));
        assertEquals(1L,jdbc.sql("SELECT count(*) FROM auditoria.evento WHERE request_id=:id AND action='m1.discovery.complete'")
                .param("id",resultado.requestId()).query(Long.class).single());
        assertEquals(2L,jdbc.sql("SELECT count(*) FROM versionado.version_recurso v JOIN auditoria.evento a ON a.id=v.audit_event_id WHERE a.request_id=:id")
                .param("id",resultado.requestId()).query(Long.class).single());
        try(var conn=DriverManager.getConnection(sourceUrl(),env("TIC_SOURCE_FIXTURE_USER"),env("TIC_SOURCE_FIXTURE_PASSWORD"));
                var q=conn.createStatement()) {
            SQLException denegado=assertThrows(SQLException.class,()->q.executeQuery("SELECT * FROM m103_core.parent"));
            assertEquals("42501",denegado.getSQLState());
        }
    }

    @Test void referenciasFueraDelAlcanceDistinguenNoResueltoYNoAccesible() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture"); var result=descubrir(f,"m103_core");
        var keys=catalogo.listarRestricciones(f.project(),tabla(result,"child").assetId());
        var visible=keys.stream().filter(k->k.name().equals("child_visible_fk")).findFirst().orElseThrow();
        var hidden=keys.stream().filter(k->k.name().equals("child_hidden_fk")).findFirst().orElseThrow();
        assertEquals(ResolucionReferencia.UNRESOLVED,visible.resolution());
        assertNull(visible.targetAssetId()); assertEquals("target_id",visible.members().getFirst().targetFieldName());
        assertEquals(ResolucionReferencia.NOT_ACCESSIBLE,hidden.resolution());
        assertNull(hidden.targetAssetId()); assertNull(hidden.members().getFirst().targetFieldName());
    }

    @Test void referenciasCiclicasSePublicanEnUnSoloLote() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture"); var result=descubrir(f,"m103_cycle");
        var a=tabla(result,"a"); var b=tabla(result,"b");
        assertEquals(b.assetId(),catalogo.listarRestricciones(f.project(),a.assetId()).stream()
                .filter(k->k.kind()==TipoRestriccion.FK).findFirst().orElseThrow().targetAssetId());
        assertEquals(a.assetId(),catalogo.listarRestricciones(f.project(),b.assetId()).stream()
                .filter(k->k.kind()==TipoRestriccion.FK).findFirst().orElseThrow().targetAssetId());
    }

    @Test void identificadoresCitadosNoSeInterpretanComoSql() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture"); var result=descubrir(f,"Ventas raras");
        assertEquals("Pedidos. \"2026\"",result.tables().getFirst().name());
        assertEquals("a.b",catalogo.listarCampos(f.project(),result.tables().getFirst().assetId()).getFirst().name());
        Fixture bad=fixture("GESTOR_CATALOGO","fixture");
        var error=assertThrows(DescubrimientoFallidoException.class,()->descubrir(bad,"m103_core' OR true --"));
        assertEquals(Codigo.ALCANCE_NO_ACCESIBLE,error.codigo()); assertEquals(0,activos(bad));
    }

    @Test void columnasEliminadasSeOmitenYTiposDesconocidosNoSeInfierenDeFilas() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture"); var result=descubrir(f,"m103_types");
        var fields=catalogo.listarCampos(f.project(),result.tables().getFirst().assetId());
        assertFalse(fields.stream().anyMatch(c->c.name().equals("discarded")));
        assertEquals(TipoLogico.UNKNOWN,fields.stream().filter(c->c.name().equals("document")).findFirst().orElseThrow().logicalType());
        assertEquals(TipoLogico.ARRAY,fields.stream().filter(c->c.name().equals("tags")).findFirst().orElseThrow().logicalType());
        assertEquals(3,fields.stream().filter(c->c.name().equals("opaque")).findFirst().orElseThrow().position());
    }

    @ParameterizedTest @ValueSource(strings={"tables","columns","keyMembers"})
    void limiteExcedidoNoPublicaResultadosParciales(String tipo) {
        Fixture f=fixture("GESTOR_CATALOGO","fixture");
        int t=limites.getMaxTables(),c=limites.getMaxColumns(),k=limites.getMaxKeyMembers();
        try {
            switch(tipo) { case "tables"->limites.setMaxTables(1); case "columns"->limites.setMaxColumns(1); case "keyMembers"->limites.setMaxKeyMembers(1); }
            var error=assertThrows(DescubrimientoFallidoException.class,()->descubrir(f,"m103_core"));
            assertEquals(Codigo.LIMITE_EXCEDIDO,error.codigo()); assertEquals(0,activos(f));
        } finally { limites.setMaxTables(t); limites.setMaxColumns(c); limites.setMaxKeyMembers(k); }
    }

    @Test void esquemaSinUsoYClaveIncorrectaProducenErroresSeguros() {
        Fixture forbidden=fixture("GESTOR_CATALOGO","fixture");
        var e=assertThrows(DescubrimientoFallidoException.class,()->descubrir(forbidden,"m103_forbidden"));
        assertEquals(Codigo.ALCANCE_NO_ACCESIBLE,e.codigo());
        Fixture bad=fixture("GESTOR_CATALOGO","fixture_bad");
        e=assertThrows(DescubrimientoFallidoException.class,()->descubrir(bad,"m103_core"));
        assertEquals(Codigo.ORIGEN_NO_DISPONIBLE,e.codigo()); assertNull(e.getCause());
        assertFalse(e.getMessage().contains(env("TIC_SOURCE_FIXTURE_USER")));
        assertEquals(0,activos(bad));
    }

    @Test void destinoModificadoSeRechazaAntesDeConectar() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture");
        jdbc.sql("UPDATE catalogo.fuente SET connection_configuration=jsonb_set(connection_configuration,'{host}','\"otro.example.test\"') WHERE id=:id")
                .param("id",f.source()).update();
        var error=assertThrows(DescubrimientoFallidoException.class,()->descubrir(f,"m103_core"));
        assertEquals(Codigo.CONFIGURACION_ORIGEN,error.codigo()); assertEquals(0,activos(f));
    }

    @Test void repeticionConDuplicadoRevierteHastaTablasNuevasDelMismoLote() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture"); descubrir(f,"m103_core");
        assertThrows(ConflictoCatalogoException.class,()->discovery.descubrir(new DescubrimientoPostgresApi.Solicitud(
                f.project(),f.source(),List.of("m103_cycle","m103_core"))));
        assertEquals(2,activos(f));
        assertEquals(2L,jdbc.sql("SELECT count(*) FROM versionado.version_recurso WHERE project_id=:id")
                .param("id",f.project()).query(Long.class).single());
        assertEquals(1L,jdbc.sql("SELECT count(*) FROM auditoria.evento WHERE project_id=:id AND action='m1.discovery.complete'")
                .param("id",f.project()).query(Long.class).single());
    }

    @Test void esquemaVacioConservaObservacionSinInventarActivos() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture");
        var result=descubrir(f,"m103_empty"); assertTrue(result.tables().isEmpty());
        assertEquals(0,activos(f));
        assertTrue(jdbc.sql("SELECT safe_details->>'metadata_only' FROM auditoria.evento WHERE request_id=:id")
                .param("id",result.requestId()).query(String.class).single().equals("true"));
    }

    @Test void sinPermisoNoSeInvocaElExtractor() {
        Fixture f=fixture("ANALISTA_CALIDAD","fixture");
        assertThrows(AccesoDenegadoException.class,()->descubrir(f,"m103_core"));
        verifyNoInteractions(extractor); assertEquals(0,activos(f));
    }

    @Test void revocacionDuranteLecturaImpidePublicacion() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture");
        doAnswer(inv->{ Object result=inv.callRealMethod();
            jdbc.sql("UPDATE autorizacion.asignacion_rol SET revoked_at=now() WHERE id=:id").param("id",f.assignment()).update();
            return result;
        }).when(extractor).extraer(any(),any());
        assertThrows(AccesoDenegadoException.class,()->descubrir(f,"m103_core")); assertEquals(0,activos(f));
    }

    @Test void cambioDeConfiguracionDuranteLecturaImpidePublicacion() {
        Fixture f=fixture("GESTOR_CATALOGO","fixture");
        doAnswer(inv->{ Object result=inv.callRealMethod();
            jdbc.sql("UPDATE catalogo.fuente SET revision=revision+1 WHERE id=:id").param("id",f.source()).update();
            return result;
        }).when(extractor).extraer(any(),any());
        assertThrows(IllegalStateException.class,()->descubrir(f,"m103_core")); assertEquals(0,activos(f));
    }

    private DescubrimientoPostgresApi.Resultado descubrir(Fixture f,String schema) {
        return discovery.descubrir(new DescubrimientoPostgresApi.Solicitud(f.project(),f.source(),List.of(schema)));
    }
    private DescubrimientoPostgresApi.TablaRegistrada tabla(DescubrimientoPostgresApi.Resultado r,String name) {
        return r.tables().stream().filter(t->t.name().equals(name)).findFirst().orElseThrow();
    }
    private long activos(Fixture f) {
        return jdbc.sql("SELECT count(*) FROM catalogo.activo WHERE project_id=:id").param("id",f.project()).query(Long.class).single();
    }
    private static String env(String key) {
        String value=System.getenv(key); assertNotNull(value,"Ejecutar con tools/verificar_postgres.py para preparar el origen temporal M1-03");
        return value;
    }
    private static String sourceUrl() { return "jdbc:postgresql://"+env("TIC_SOURCE_FIXTURE_HOST")+":"+env("TIC_SOURCE_FIXTURE_PORT")+"/"+env("TIC_SOURCE_FIXTURE_DATABASE"); }
    private Fixture fixture(String role,String ref) {
        UUID user=UUID.randomUUID(),org=UUID.randomUUID(),project=UUID.randomUUID(),om=UUID.randomUUID();
        UUID pm=UUID.randomUUID(),assignment=UUID.randomUUID(),source=UUID.randomUUID();
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Descubrimiento')")
                .param("id",user).param("email",user+"@example.test").update();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'Descubrimiento')").param("id",org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'Descubrimiento')")
                .param("id",project).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:user)")
                .param("id",om).param("user",user).param("org",org).update();
        jdbc.sql("INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by) VALUES(:id,:om,:user,:org,:project,:user)")
                .param("id",pm).param("om",om).param("user",user).param("org",org).param("project",project).update();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT :id,:user,id,'PROJECT',:member,:project,:user FROM autorizacion.rol WHERE code=:role")
                .param("id",assignment).param("user",user).param("member",pm).param("project",project).param("role",role).update();
        jdbc.sql("INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope) VALUES(:id,:assignment,:project,'PROJECT')")
                .param("id",UUID.randomUUID()).param("assignment",assignment).param("project",project).update();
        jdbc.sql("""
                INSERT INTO catalogo.fuente(id,project_id,name,type,status,connection_configuration,credential_ref,created_by,created_at,updated_at)
                VALUES(:id,:project,'Origen temporal M1-03','postgresql','enabled',
                    jsonb_build_object('host',CAST(:host AS text),'port',CAST(:port AS text),'database',CAST(:database AS text)),:ref,:actor,now(),now())
                """).param("id",source).param("project",project).param("host",env("TIC_SOURCE_FIXTURE_HOST"))
                .param("port",env("TIC_SOURCE_FIXTURE_PORT")).param("database",env("TIC_SOURCE_FIXTURE_DATABASE"))
                .param("ref",ref).param("actor",user).update();
        when(identidad.usuarioId()).thenReturn(user);
        return new Fixture(user,project,source,assignment);
    }
    private record Fixture(UUID user,UUID project,UUID source,UUID assignment) { }
}
