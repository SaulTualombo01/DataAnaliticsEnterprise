package org.dataanaliticsent.catalogo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest
class CatalogoPostgresTests {
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    @Autowired RepositorioCatalogo catalogo;
    @Autowired JdbcClient jdbc;
    @Autowired tools.jackson.databind.ObjectMapper json;

    @Test
    void persisteFuenteActivoCamposEHistoriaConAislamientoDeProyecto() {
        Fixture f = fixture();
        EstadoCatalogo estado = estado(f, UUID.randomUUID());
        catalogo.registrarActivo(estado, f.actor(), UUID.randomUUID());
        assertEquals(f.source(), catalogo.buscarFuente(f.project(), f.source().id()).orElseThrow());
        assertEquals(estado.activo(), catalogo.buscarActivo(f.project(), estado.activo().id()).orElseThrow());
        assertEquals(estado.campos(), catalogo.listarCampos(f.project(), estado.activo().id()));
        assertEquals(estado, catalogo.consultarVersion(f.project(), estado.activo().id(), estado.activo().metadataVersionId()).orElseThrow());
        UUID otherProject = UUID.randomUUID();
        assertTrue(catalogo.buscarFuente(otherProject, f.source().id()).isEmpty());
        assertTrue(catalogo.buscarActivo(otherProject, estado.activo().id()).isEmpty());
        assertTrue(catalogo.listarCampos(otherProject, estado.activo().id()).isEmpty());
        assertTrue(catalogo.consultarVersion(otherProject, estado.activo().id(), estado.activo().metadataVersionId()).isEmpty());
        String payload = jdbc.sql("SELECT state_payload::text FROM versionado.version_recurso WHERE id=:id")
                .param("id", estado.activo().metadataVersionId()).query(String.class).single();
        assertFalse(payload.contains("referencia-no-publica"));
        assertFalse(payload.contains("connection_configuration"));
    }

    @Test
    void falloDeCampoRevierteActivoVersionYEvento() {
        Fixture f = fixture();
        UUID fieldId = UUID.randomUUID();
        catalogo.registrarActivo(estado(f, fieldId), f.actor(), UUID.randomUUID());
        EstadoCatalogo segundo = estado(f, fieldId);
        assertThrows(DataIntegrityViolationException.class, () -> catalogo.registrarActivo(segundo, f.actor(), UUID.randomUUID()));
        assertTrue(catalogo.buscarActivo(f.project(), segundo.activo().id()).isEmpty());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM versionado.version_recurso WHERE resource_id=:id")
                .param("id", segundo.activo().id()).query(Long.class).single());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM auditoria.evento WHERE resource_id=:id")
                .param("id", segundo.activo().id()).query(Long.class).single());
    }

    @Test
    void rechazaFuenteDeshabilitadaSinPublicarActivo() {
        Fixture f = fixture();
        jdbc.sql("UPDATE catalogo.fuente SET status='disabled' WHERE id=:id").param("id", f.source().id()).update();
        EstadoCatalogo estado = estado(f, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> catalogo.registrarActivo(estado, f.actor(), UUID.randomUUID()));
        assertTrue(catalogo.buscarActivo(f.project(), estado.activo().id()).isEmpty());
    }

    @Test
    void restriccionesImpediranCamposDeOtroProyectoEHistoriaModificada() {
        Fixture f = fixture();
        Fixture other = fixture();
        EstadoCatalogo estado = estado(f, UUID.randomUUID());
        catalogo.registrarActivo(estado, f.actor(), UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO catalogo.campo(id,project_id,asset_id,name,path,position,logical_type,status)
                VALUES(:id,:project,:asset,'ajeno','ajeno',2,'string','active')
                """).param("id", UUID.randomUUID()).param("project", other.project()).param("asset", estado.activo().id()).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("UPDATE versionado.version_recurso SET reason='cambio' WHERE id=:id")
                .param("id", estado.activo().metadataVersionId()).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("DELETE FROM auditoria.evento WHERE resource_id=:id")
                .param("id", estado.activo().id()).update());
    }

    @Test
    void noPublicaActivoConVersionDeOtroRecurso() {
        Fixture f = fixture();
        EstadoCatalogo estado = estado(f, UUID.randomUUID());
        catalogo.registrarActivo(estado, f.actor(), UUID.randomUUID());
        UUID wrongId = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO catalogo.activo(id,project_id,source_id,name,kind,origin_object_path,status,structure_origin,
                    current_metadata_version_id,last_discovery_at,updated_at)
                SELECT :wrong,project_id,source_id,'otro','table',:path,'active','declared',current_metadata_version_id,last_discovery_at,updated_at
                FROM catalogo.activo WHERE id=:original
                """).param("wrong", wrongId).param("path", wrongId.toString()).param("original", estado.activo().id()).update());
        assertTrue(catalogo.buscarActivo(f.project(), wrongId).isEmpty());
    }

    @Test
    void representacionHistoricaSinRestriccionesSigueSiendoLegible() {
        Fixture f=fixture();
        EstadoCatalogo estado=estado(f,UUID.randomUUID());
        catalogo.registrarActivo(estado,f.actor(),UUID.randomUUID());
        String payload=jdbc.sql("SELECT state_payload::text FROM versionado.version_recurso WHERE id=:id")
                .param("id",estado.activo().metadataVersionId()).query(String.class).single();
        var legacy=(tools.jackson.databind.node.ObjectNode)json.readTree(payload);
        legacy.remove("restricciones");
        assertEquals(estado,json.readValue(json.writeValueAsString(legacy),EstadoCatalogo.class));
    }

    private Fixture fixture() {
        UUID actor = UUID.randomUUID(), org = UUID.randomUUID(), project = UUID.randomUUID();
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Prueba')")
                .param("id", actor).param("email", actor + "@example.test").update();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'Prueba')").param("id", org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'Prueba')")
                .param("id", project).param("org", org).update();
        var source = new FuenteDatos(UUID.randomUUID(), project, "Origen de prueba", FuenteDatos.Tipo.POSTGRESQL,
                FuenteDatos.Estado.ENABLED, Map.of("host","localhost","port","5432","database","origen"),
                "referencia-no-publica", actor, NOW, NOW, 0);
        catalogo.registrarFuente(source);
        return new Fixture(actor, project, source);
    }

    private EstadoCatalogo estado(Fixture f, UUID fieldId) {
        UUID id = UUID.randomUUID();
        var asset = new ActivoDatos(id, f.project(), f.source().id(), "clientes", ActivoDatos.Tipo.TABLE,
                "public", "clientes_" + id, ActivoDatos.Estado.ACTIVE, ActivoDatos.Origen.DECLARED,
                "Clientes de prueba", List.of("clientes"), UUID.randomUUID(), NOW, NOW, 0);
        var field = new CampoDatos(fieldId, f.project(), id, "id", "id", 1, "integer",
                CampoDatos.TipoLogico.INTEGER, null, "Identificador", List.of(), CampoDatos.Estado.ACTIVE);
        return new EstadoCatalogo(asset, List.of(field));
    }

    private record Fixture(UUID actor, UUID project, FuenteDatos source) { }
}
