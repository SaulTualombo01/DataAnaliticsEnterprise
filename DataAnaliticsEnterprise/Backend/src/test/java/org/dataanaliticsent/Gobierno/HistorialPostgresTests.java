package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.DatosFicha;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.EntradaCronologia;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.NuevaRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Cambio;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.TipoCambio;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Cronología con jsonb y comparación de versiones leídas de PostgreSQL real (perfil postgres-it). */
@SpringBootTest
@Tag("postgres")
class HistorialPostgresTests {
    static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @TestConfiguration
    static class AccesoDePrueba {
        @Bean @Primary PuertoIdentidad identidadDePrueba() { return () -> ACTOR; }
        @Bean @Primary PuertoAutorizacion autorizacionDePrueba() { return (actor, permiso, proyecto) -> { }; }
        @Bean @Primary PuertoCatalogo catalogoDePrueba() { return (proyecto, activo) -> true; }
    }

    @Autowired FichasGobiernoApi fichas;
    @Autowired LinajeApi linaje;
    @Autowired VersionadoApi versionado;
    @Autowired HistorialActivoApi historial;

    @Test
    void cronologiaIncluyeHechosDelActivoYMarcaLosQueVersionaron() {
        UUID proyecto = UUID.randomUUID();
        UUID clientes = UUID.randomUUID();
        UUID ventas = UUID.randomUUID();
        fichas.guardar(new DatosFicha(proyecto, clientes, null, ACTOR, Clasificacion.INTERNA, "Alta"));
        linaje.registrar(new NuevaRelacion(proyecto, clientes, ventas, TipoRelacion.TRANSFORMA, null, "ETL"));

        List<EntradaCronologia> entradas = historial.cronologia(proyecto, clientes, 1, 25).items();

        assertEquals(2, entradas.size());
        assertEquals("m3.lineage.create", entradas.getFirst().evento().action());
        assertTrue(entradas.stream().allMatch(EntradaCronologia::generoVersion));
        assertEquals(1, historial.cronologia(proyecto, ventas, 1, 25).items().size());
    }

    @Test
    void comparaVersionesLeidasDeJsonb() {
        UUID proyecto = UUID.randomUUID();
        UUID activo = UUID.randomUUID();
        Ficha v0 = fichas.guardar(new DatosFicha(proyecto, activo, null, ACTOR, null, "Alta"));
        Ficha v1 = fichas.guardar(new DatosFicha(proyecto, activo, 0L, ACTOR, Clasificacion.RESTRINGIDA, "Clasificar"));

        List<Cambio> cambios = versionado.comparar(proyecto, "gobierno.ficha_activo", activo,
                v0.currentVersionId(), v1.currentVersionId()).cambios();

        assertEquals(List.of(new Cambio("classification", TipoCambio.MODIFICADO, "null", "\"RESTRINGIDA\"")), cambios);
        assertFalse(historial.resumen(proyecto, activo).seccionesNoAutorizadas().size() > 0);
    }
}
