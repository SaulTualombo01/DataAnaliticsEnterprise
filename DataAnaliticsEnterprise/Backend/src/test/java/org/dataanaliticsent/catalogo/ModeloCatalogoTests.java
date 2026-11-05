package org.dataanaliticsent.catalogo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModeloCatalogoTests {
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Test
    void rechazaSecretosDentroDeConfiguracion() {
        assertThrows(IllegalArgumentException.class, () -> fuente(Map.of(
                "host", "localhost", "port", "5432", "database", "origen", "password", "no-guardar")));
    }

    @Test
    void configuracionEsInmutableYToStringNoRevelaReferencia() {
        var fuente = fuente(Map.of("host", "localhost", "port", "5432", "database", "origen"));
        assertThrows(UnsupportedOperationException.class, () -> fuente.configuration().put("host", "otro"));
        assertFalse(fuente.toString().contains("referencia-protegida"));
        assertFalse(fuente.toString().contains("localhost"));
    }

    @Test
    void noAceptaCamposDeOtroProyecto() {
        var activo = activo();
        var campo = campo(UUID.randomUUID(), activo.id(), "id", 1);
        assertThrows(IllegalArgumentException.class, () -> new EstadoCatalogo(activo, List.of(campo)));
    }

    @Test
    void noAceptaRutasActivasDuplicadas() {
        var activo = activo();
        assertThrows(IllegalArgumentException.class, () -> new EstadoCatalogo(activo, List.of(
                campo(activo.projectId(), activo.id(), "id", 1), campo(activo.projectId(), activo.id(), "id", 2))));
    }

    @Test
    void conservaNullableDesconocido() {
        var activo = activo();
        var campo = campo(activo.projectId(), activo.id(), "id", 1);
        assertNull(new EstadoCatalogo(activo, List.of(campo)).campos().getFirst().nullable());
    }

    @Test
    void unaExtraccionParcialNoPuedeCompletar() {
        assertThrows(IllegalArgumentException.class, () -> new EjecucionDescubrimiento(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                EjecucionDescubrimiento.Estado.COMPLETED, NOW, NOW.plusSeconds(1), false, 100L, 70));
    }

    private static FuenteDatos fuente(Map<String,String> configuration) {
        return new FuenteDatos(UUID.randomUUID(), UUID.randomUUID(), "Origen", FuenteDatos.Tipo.POSTGRESQL,
                FuenteDatos.Estado.ENABLED, configuration, "referencia-protegida", UUID.randomUUID(), NOW, NOW, 0);
    }

    private static ActivoDatos activo() {
        return new ActivoDatos(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "clientes", ActivoDatos.Tipo.TABLE,
                "public", "clientes", ActivoDatos.Estado.ACTIVE, ActivoDatos.Origen.DECLARED,
                "Clientes", List.of(), UUID.randomUUID(), NOW, NOW, 0);
    }

    private static CampoDatos campo(UUID project, UUID asset, String path, int position) {
        return new CampoDatos(UUID.randomUUID(), project, asset, path, path, position, "text",
                CampoDatos.TipoLogico.STRING, null, "Descripción", List.of(), CampoDatos.Estado.ACTIVE);
    }
}
