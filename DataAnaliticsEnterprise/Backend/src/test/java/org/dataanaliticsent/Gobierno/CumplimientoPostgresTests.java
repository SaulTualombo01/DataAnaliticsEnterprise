package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCalidad;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.EstadoCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.DatosFicha;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.NuevaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.TipoRegla;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Migración V32, reglas, fichas y evaluaciones sobre PostgreSQL real (perfil postgres-it). */
@SpringBootTest
@Tag("postgres")
class CumplimientoPostgresTests {
    static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @TestConfiguration
    static class AccesoDePrueba {
        @Bean @Primary PuertoIdentidad identidadDePrueba() { return () -> ACTOR; }
        @Bean @Primary PuertoAutorizacion autorizacionDePrueba() { return (actor, permiso, proyecto) -> { }; }
        @Bean @Primary PuertoCatalogo catalogoDePrueba() { return (proyecto, activo) -> true; }
        @Bean @Primary PuertoCalidad calidadDePrueba() { return (proyecto, activo) -> Optional.empty(); }
    }

    @Autowired PoliticasApi politicas;
    @Autowired FichasGobiernoApi fichas;
    @Autowired CumplimientoApi cumplimiento;
    @Autowired JdbcClient jdbc;

    @Test
    void evaluaGuardaYLeeElDetalleDesdeJsonb() {
        UUID proyecto = UUID.randomUUID();
        UUID activo = UUID.randomUUID();
        Politica p = politicas.crear(new NuevaPolitica(proyecto, "PII", null, CategoriaPolitica.PRIVACIDAD, ACTOR));
        p = politicas.agregarRegla(proyecto, p.id(), 0, TipoRegla.CLASIFICACION_MINIMA, "CONFIDENCIAL", "PII");
        p = politicas.agregarRegla(proyecto, p.id(), 1, TipoRegla.CALIDAD_APROBADA, null, "Calidad");
        p = politicas.asociarActivo(proyecto, p.id(), 2, activo, "Clientes");
        politicas.cambiarEstado(proyecto, p.id(), 3, EstadoPolitica.ACTIVE, "Aprobada");
        fichas.guardar(new DatosFicha(proyecto, activo, null, ACTOR, Clasificacion.INTERNA, "Alta"));

        Evaluacion e = cumplimiento.evaluar(proyecto, activo);
        Evaluacion leida = cumplimiento.ultima(proyecto, activo).orElseThrow();

        assertEquals(EstadoCumplimiento.NON_COMPLIANT, e.estado());
        assertEquals(e, leida);
        assertEquals(2, politicas.consultar(proyecto, p.id()).orElseThrow().reglas().size());
    }

    @Test
    void lasEvaluacionesNoSeEditan() {
        UUID proyecto = UUID.randomUUID();
        Evaluacion e = cumplimiento.evaluar(proyecto, UUID.randomUUID());

        assertEquals(EstadoCumplimiento.NOT_APPLICABLE, e.estado());
        assertThrows(DataAccessException.class, () -> jdbc.sql(
                "UPDATE gobierno.evaluacion_cumplimiento SET overall_status='COMPLIANT' WHERE id=:id")
                .param("id", e.id()).update());
    }
}
