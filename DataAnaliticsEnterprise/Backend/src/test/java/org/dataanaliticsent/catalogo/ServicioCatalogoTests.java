package org.dataanaliticsent.catalogo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.*;
import org.dataanaliticsent.catalogo.CatalogoApi.*;
import org.dataanaliticsent.catalogo.internal.aplicacion.ServicioCatalogo;
import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.dataanaliticsent.identidad.IdentidadNoVerificadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioCatalogoTests {
    private final UUID actor = UUID.randomUUID(), project = UUID.randomUUID(), source = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-11T18:00:00Z");
    private IdentidadActualApi identidad;
    private AutorizacionApi autorizacion;
    private RepositorioCatalogo datos;
    private CatalogoApi api;

    @BeforeEach void preparar() {
        identidad = mock(IdentidadActualApi.class);
        autorizacion = mock(AutorizacionApi.class);
        datos = mock(RepositorioCatalogo.class);
        when(identidad.usuarioId()).thenReturn(actor);
        when(datos.buscarFuente(project, source)).thenReturn(Optional.of(fuente(FuenteDatos.Estado.ENABLED)));
        api = new ServicioCatalogo(identidad, autorizacion, datos, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void registroObtieneActorDelServidorYGeneraIdentidadesVersionYFecha() {
        var recibo = api.registrarActivo(alta(TipoActivo.TABLE));
        var estado = ArgumentCaptor.forClass(EstadoCatalogo.class);
        verify(autorizacion).exigir(actor, SolicitudAcceso.recurso("m1.discovery.execute", project,
                SolicitudAcceso.TipoRecurso.SOURCE, source));
        verify(datos).registrarActivo(estado.capture(), eq(actor), eq(recibo.requestId()));
        var guardado = estado.getValue();
        assertEquals(recibo.id(), guardado.activo().id());
        assertEquals(recibo.metadataVersionId(), guardado.activo().metadataVersionId());
        assertEquals(now, guardado.activo().lastDiscoveryAt());
        assertEquals(0, guardado.activo().revision());
        assertNotEquals(recibo.id(), recibo.metadataVersionId());
        assertEquals(recibo.id(), guardado.campos().getFirst().assetId());
        assertNull(guardado.activo().businessDescription());
        assertTrue(guardado.campos().getFirst().synonyms().isEmpty());
    }

    @Test void identidadAusenteInterrumpeAntesDeAutorizacionOPersistencia() {
        when(identidad.usuarioId()).thenThrow(new IdentidadNoVerificadaException());
        assertThrows(IdentidadNoVerificadaException.class, () -> api.registrarActivo(alta(TipoActivo.TABLE)));
        verifyNoInteractions(autorizacion);
        verifyNoInteractions(datos);
    }

    @Test void denegacionDeRegistroNoLeeLaFuenteNiEscribe() {
        doThrow(new AccesoDenegadoException(DecisionAcceso.Motivo.ALCANCE_INSUFICIENTE))
                .when(autorizacion).exigir(eq(actor), any());
        assertThrows(AccesoDenegadoException.class, () -> api.registrarActivo(alta(TipoActivo.TABLE)));
        verifyNoInteractions(datos);
    }

    @Test void fuenteDeshabilitadaNoPublicaMetadatos() {
        when(datos.buscarFuente(project, source)).thenReturn(Optional.of(fuente(FuenteDatos.Estado.DISABLED)));
        assertThrows(IllegalArgumentException.class, () -> api.registrarActivo(alta(TipoActivo.TABLE)));
        verify(datos, never()).registrarActivo(any(), any(), any());
    }

    @Test void tipoIncompatibleNoPublicaMetadatos() {
        assertThrows(IllegalArgumentException.class, () -> api.registrarActivo(alta(TipoActivo.CSV_DATASET)));
        verify(datos, never()).registrarActivo(any(), any(), any());
    }

    @Test void campoInvalidoImpidePublicarEstadoParcial() {
        var campo = new AltaCampo("id", "id", 0, "int8", TipoLogico.INTEGER, false);
        var alta = new AltaActivo(project, source, "clientes", TipoActivo.TABLE, "public", "clientes",
                OrigenEstructura.DECLARED, List.of(campo));
        assertThrows(IllegalArgumentException.class, () -> api.registrarActivo(alta));
        verify(datos, never()).registrarActivo(any(), any(), any());
    }

    @Test void consultaYDiccionarioUsanPermisosDistintosAntesDeLeer() {
        UUID asset = UUID.randomUUID();
        doThrow(new AccesoDenegadoException(DecisionAcceso.Motivo.PERMISO_NO_CONCEDIDO))
                .when(autorizacion).exigir(actor, SolicitudAcceso.recurso("m1.dictionary.read", project,
                        SolicitudAcceso.TipoRecurso.ASSET, asset));
        assertThrows(AccesoDenegadoException.class, () -> api.listarCampos(project, asset));
        verifyNoInteractions(datos);
        when(datos.buscarActivo(project, asset)).thenReturn(Optional.empty());
        assertTrue(api.consultarActivo(project, asset).isEmpty());
        verify(autorizacion).exigir(actor, SolicitudAcceso.recurso("m1.assets.read", project,
                SolicitudAcceso.TipoRecurso.ASSET, asset));
    }

    @Test void conflictoDePersistenciaNoExponeSqlNiDatosPrivados() {
        doThrow(new DataIntegrityViolationException("SQL con dato privado"))
                .when(datos).registrarActivo(any(), any(), any());
        var error = assertThrows(ConflictoCatalogoException.class, () -> api.registrarActivo(alta(TipoActivo.TABLE)));
        assertFalse(error.getMessage().contains("SQL"));
        assertFalse(error.getMessage().contains("dato privado"));
        assertNull(error.getCause());
    }

    private FuenteDatos fuente(FuenteDatos.Estado estado) {
        return new FuenteDatos(source, project, "Origen", FuenteDatos.Tipo.POSTGRESQL, estado,
                Map.of("host", "localhost", "port", "5432", "database", "fixture"),
                "referencia-fixture", actor, now, now, 0);
    }

    private AltaActivo alta(TipoActivo tipo) {
        return new AltaActivo(project, source, "clientes", tipo, "public", "clientes", OrigenEstructura.DECLARED,
                List.of(new AltaCampo("id", "id", 1, "int8", TipoLogico.INTEGER, false)));
    }
}
