package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.linaje.ConflictoLinajeException;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Direccion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.EstadoRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.NuevaRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Migración V33, bloqueo por proyecto y recorridos del grafo sobre PostgreSQL real (perfil postgres-it). */
@SpringBootTest
@Tag("postgres")
class LinajePostgresTests {
    static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @TestConfiguration
    static class AccesoDePrueba {
        @Bean @Primary PuertoIdentidad identidadDePrueba() { return () -> ACTOR; }
        @Bean @Primary PuertoAutorizacion autorizacionDePrueba() { return (actor, permiso, proyecto) -> { }; }
        @Bean @Primary PuertoCatalogo catalogoDePrueba() { return (proyecto, activo) -> true; }
    }

    @Autowired LinajeApi linaje;

    @Test
    void registraRecorreRechazaCiclosYRetira() {
        UUID proyecto = UUID.randomUUID();
        UUID pedidos = UUID.randomUUID();
        UUID ventas = UUID.randomUUID();
        UUID reporte = UUID.randomUUID();
        Relacion r1 = linaje.registrar(new NuevaRelacion(proyecto, pedidos, ventas, TipoRelacion.TRANSFORMA, "ETL", "Alta"));
        linaje.registrar(new NuevaRelacion(proyecto, ventas, reporte, TipoRelacion.AGREGA, null, "Alta"));

        assertEquals(3, linaje.grafo(proyecto, reporte, Direccion.ORIGENES, 5).nodos().size());
        assertThrows(ConflictoLinajeException.class,
                () -> linaje.registrar(new NuevaRelacion(proyecto, reporte, pedidos, TipoRelacion.ALIMENTA, null, "Ciclo")));

        Relacion retirada = linaje.retirar(proyecto, r1.id(), "Baja del proceso");
        assertEquals(EstadoRelacion.RETIRED, linaje.consultar(proyecto, r1.id()).orElseThrow().estado());
        assertEquals(retirada, linaje.consultar(proyecto, r1.id()).orElseThrow());
        assertEquals(2, linaje.grafo(proyecto, reporte, Direccion.ORIGENES, 5).nodos().size());
    }
}
