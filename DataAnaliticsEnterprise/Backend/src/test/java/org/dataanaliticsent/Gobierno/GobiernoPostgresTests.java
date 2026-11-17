package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.EventoNuevo;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Filtro;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Previa;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.ResultadoVersion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.SolicitudVersion;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Migración V30 y adaptadores JDBC sobre PostgreSQL real. Ejecutar solo contra una base
 * dedicada de pruebas: mvn -Ppostgres-it verify con DB_URL, DB_USER y DB_PASSWORD.
 */
@SpringBootTest
@Tag("postgres")
class GobiernoPostgresTests {
    static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    /** Sustituye la identidad y la autorización que en esta rama niegan todo. */
    @TestConfiguration
    static class AccesoDePrueba {
        @Bean @Primary PuertoIdentidad identidadDePrueba() { return () -> ACTOR; }
        @Bean @Primary PuertoAutorizacion autorizacionDePrueba() { return (actor, permiso, proyecto) -> { }; }
    }

    @Autowired AuditoriaApi auditoria;
    @Autowired VersionadoApi versionado;
    @Autowired JdbcClient jdbc;

    @Test
    void registraYConsultaEventosAisladosPorProyecto() {
        UUID proyecto = UUID.randomUUID();
        UUID otroProyecto = UUID.randomUUID();
        UUID id = auditoria.registrar(new EventoNuevo(Actor.usuario(ACTOR), UUID.randomUUID(), proyecto,
                "gobierno.politica", UUID.randomUUID(), "m3.policies.create", Resultado.SUCCESS, UUID.randomUUID(),
                Map.of("nombre", "Datos personales")));
        auditoria.registrar(new EventoNuevo(Actor.anonimo(), null, otroProyecto, "gobierno.politica", null,
                "m3.policies.read", Resultado.DENIED, null, Map.of()));

        var pagina = auditoria.consultar(Filtro.delProyecto(proyecto), 1, 25);

        assertEquals(1, pagina.items().size());
        Evento evento = pagina.items().getFirst();
        assertEquals(id, evento.id());
        assertEquals(Actor.usuario(ACTOR), evento.actor());
        assertTrue(evento.safeDetailsJson().contains("Datos personales"));
        assertFalse(pagina.hasNext());
    }

    @Test
    void versionaSoloCambiosEfectivosYEnlazaElEvento() {
        UUID proyecto = UUID.randomUUID();
        UUID politica = UUID.randomUUID();
        UUID evento = auditoria.registrar(new EventoNuevo(Actor.usuario(ACTOR), null, proyecto, "gobierno.politica",
                politica, "m3.policies.create", Resultado.SUCCESS, null, Map.of()));

        ResultadoVersion v1 = versionado.versionar(new SolicitudVersion(proyecto, "gobierno.politica", politica,
                Actor.usuario(ACTOR), "Alta", Map.of("estado", "DRAFT"), evento, Previa.ninguna()));
        ResultadoVersion igual = versionado.versionar(new SolicitudVersion(proyecto, "gobierno.politica", politica,
                Actor.usuario(ACTOR), "Sin cambios", Map.of("estado", "DRAFT"), null, Previa.de(v1.versionId())));
        ResultadoVersion v2 = versionado.versionar(new SolicitudVersion(proyecto, "gobierno.politica", politica,
                Actor.usuario(ACTOR), "Activar", Map.of("estado", "ACTIVE"), null, Previa.de(v1.versionId())));

        assertFalse(igual.creada());
        assertEquals(2, v2.number());
        var historial = versionado.historial(proyecto, "gobierno.politica", politica, 1, 25);
        assertEquals(2, historial.items().size());
        assertEquals(v1.versionId(), historial.items().getFirst().previousVersionId());
        assertEquals(evento, versionado.version(proyecto, "gobierno.politica", politica, v1.versionId())
                .orElseThrow().auditEventId());
    }

    @Test
    void elHistorialNoSePuedeEditarNiBorrar() {
        UUID proyecto = UUID.randomUUID();
        UUID id = auditoria.registrar(new EventoNuevo(Actor.usuario(ACTOR), null, proyecto, "gobierno.politica",
                null, "m3.policies.create", Resultado.SUCCESS, null, Map.of()));

        assertThrows(DataAccessException.class, () -> jdbc.sql(
                "UPDATE gobierno.evento_auditoria SET outcome='FAILURE' WHERE id=:id").param("id", id).update());
        assertThrows(DataAccessException.class, () -> jdbc.sql(
                "DELETE FROM gobierno.evento_auditoria WHERE id=:id").param("id", id).update());
    }

    /** V34 en el catálogo RBAC común: gobierno tiene todo, los demás roles solo lecturas, el administrador nada. */
    @Test
    void permisosM3EnElCatalogoComun() {
        assertEquals(14L, jdbc.sql("SELECT count(*) FROM autorizacion.permiso WHERE code LIKE 'm3.%' AND operation_scope='PROJECT'")
                .query(Long.class).single());
        assertEquals(14L, contarPermisosM3("RESPONSABLE_GOBIERNO"));
        assertEquals(4L, contarPermisosM3("ANALISTA_NEGOCIO"));
        assertEquals(0L, contarPermisosM3("ADMIN_SISTEMA"));
        assertEquals(0L, jdbc.sql("""
                SELECT count(*) FROM autorizacion.rol_permiso rp
                JOIN autorizacion.rol r ON r.id=rp.role_id JOIN autorizacion.permiso p ON p.id=rp.permission_id
                WHERE r.code<>'RESPONSABLE_GOBIERNO' AND p.code LIKE 'm3.%' AND p.code NOT LIKE '%.read'
                """).query(Long.class).single());
    }

    private long contarPermisosM3(String rol) {
        return jdbc.sql("""
                SELECT count(*) FROM autorizacion.rol_permiso rp
                JOIN autorizacion.rol r ON r.id=rp.role_id JOIN autorizacion.permiso p ON p.id=rp.permission_id
                WHERE r.code=:rol AND p.code LIKE 'm3.%'
                """).param("rol", rol).query(Long.class).single();
    }
}
