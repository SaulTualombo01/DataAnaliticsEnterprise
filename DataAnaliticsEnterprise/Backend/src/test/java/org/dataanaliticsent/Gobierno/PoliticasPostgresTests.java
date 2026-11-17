package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.politicas.ConflictoPoliticaException;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CambiosPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.FiltroPoliticas;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.NuevaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Migración V31 y PoliticasJdbcAdapter sobre PostgreSQL real (perfil postgres-it). */
@SpringBootTest
@Tag("postgres")
class PoliticasPostgresTests {
    static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    /** Sustituye identidad, autorización y catálogo, que en esta rama niegan todo. */
    @TestConfiguration
    static class AccesoDePrueba {
        @Bean @Primary PuertoIdentidad identidadDePrueba() { return () -> ACTOR; }
        @Bean @Primary PuertoAutorizacion autorizacionDePrueba() { return (actor, permiso, proyecto) -> { }; }
        @Bean @Primary PuertoCatalogo catalogoDePrueba() { return (proyecto, activo) -> true; }
    }

    @Autowired PoliticasApi politicas;
    @Autowired JdbcClient jdbc;

    private Politica crear(UUID proyecto, String nombre) {
        return politicas.crear(new NuevaPolitica(proyecto, nombre, "Prueba", CategoriaPolitica.PRIVACIDAD, ACTOR));
    }

    @Test
    void cicloCompletoConAuditoriaVersionesYActivos() {
        UUID proyecto = UUID.randomUUID();
        UUID activo = UUID.randomUUID();
        Politica p = crear(proyecto, "Datos personales");

        Politica asociada = politicas.asociarActivo(proyecto, p.id(), 0, activo, "Tabla clientes");
        Politica activa = politicas.cambiarEstado(proyecto, p.id(), 1, EstadoPolitica.ACTIVE, "Aprobada");

        Politica leida = politicas.consultar(proyecto, p.id()).orElseThrow();
        assertEquals(activa, leida);
        assertEquals(List.of(activo), asociada.assetIds());
        assertEquals(3L, jdbc.sql("SELECT count(*) FROM gobierno.version_recurso WHERE resource_id=:id")
                .param("id", p.id()).query(Long.class).single());
        assertEquals(3L, jdbc.sql("SELECT count(*) FROM gobierno.evento_auditoria WHERE resource_id=:id AND outcome='SUCCESS'")
                .param("id", p.id()).query(Long.class).single());
        assertTrue(politicas.politicasDeActivo(proyecto, activo).getFirst().aplicable());

        Politica sin = politicas.retirarActivo(proyecto, p.id(), 2, activo, "Ya no aplica");
        assertTrue(sin.assetIds().isEmpty());
        assertTrue(politicas.politicasDeActivo(proyecto, activo).isEmpty());
    }

    @Test
    void nombreUnicoPorProyectoYRevisionObsoletaRechazada() {
        UUID proyecto = UUID.randomUUID();
        Politica p = crear(proyecto, "Retención");
        crear(UUID.randomUUID(), "Retención");

        assertThrows(ConflictoPoliticaException.class, () -> crear(proyecto, "RETENCIÓN".toLowerCase()));
        politicas.actualizar(proyecto, p.id(), 0, new CambiosPolitica(null, "v2", null, null), "Cambio");
        assertThrows(ConflictoPoliticaException.class,
                () -> politicas.actualizar(proyecto, p.id(), 0, new CambiosPolitica(null, "v3", null, null), "Viejo"));
    }

    @Test
    void unFalloRevierteTodoSinDejarEventosNiVersiones() {
        UUID proyecto = UUID.randomUUID();
        crear(proyecto, "Calidad mínima");
        long eventos = jdbc.sql("SELECT count(*) FROM gobierno.evento_auditoria WHERE project_id=:p")
                .param("p", proyecto).query(Long.class).single();

        assertThrows(ConflictoPoliticaException.class, () -> crear(proyecto, "Calidad mínima"));

        assertEquals(eventos, jdbc.sql("SELECT count(*) FROM gobierno.evento_auditoria WHERE project_id=:p")
                .param("p", proyecto).query(Long.class).single());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM gobierno.version_recurso WHERE project_id=:p")
                .param("p", proyecto).query(Long.class).single());
    }

    @Test
    void buscarPorTextoNoInterpretaComodines() {
        UUID proyecto = UUID.randomUUID();
        crear(proyecto, "Acceso 100% restringido");
        crear(proyecto, "Acceso libre");

        assertEquals(1, politicas.buscar(proyecto, new FiltroPoliticas("100%", null, null, null, null), 1, 25).items().size());
        assertEquals(2, politicas.buscar(proyecto, new FiltroPoliticas("acceso", null, null, null, null), 1, 25).items().size());
    }
}
