package org.dataanaliticsent.analitica.servicios;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.analitica.dto.ExecutionState;
import org.dataanaliticsent.analitica.dto.KpiCode;
import org.dataanaliticsent.analitica.dto.KpiEvaluateRequest;
import org.dataanaliticsent.analitica.dto.KpiEvaluateResponse;
import org.dataanaliticsent.analitica.dto.KpiResult;
import org.dataanaliticsent.analitica.dto.KpiStatus;
import org.dataanaliticsent.analitica.dto.TimeWindow;
import org.dataanaliticsent.analitica.servicios.KpiServiceImpl.AgregadoPedidos;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KpiServiceImplTests {

    private static final Instant AHORA = Instant.parse("2026-11-16T12:00:00Z");

    private final UUID actorId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private AutorizacionApi autorizacion;
    private KpiServiceImpl servicio;

    @BeforeEach
    void preparar() {
        jdbc = mock(JdbcTemplate.class);
        autorizacion = mock(AutorizacionApi.class);
        IdentidadActualApi identidad = mock(IdentidadActualApi.class);
        when(identidad.usuarioId()).thenReturn(actorId);
        servicio = new KpiServiceImpl(jdbc, autorizacion, identidad, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void calculaTicketPromedioConLosPedidosSinteticos() {
        consultaDevuelve(new AgregadoPedidos(new BigDecimal("315.40"), 7));

        KpiEvaluateResponse respuesta = servicio.evaluar(projectId, solicitud(KpiCode.TICKET_PROMEDIO));

        KpiResult ticket = respuesta.results().getFirst();
        assertEquals(new BigDecimal("45.06"), ticket.value());
        assertEquals(KpiStatus.OK, ticket.status());
        assertEquals("USD", ticket.unit());
        assertEquals(ExecutionState.COMPLETED, respuesta.executionState());
        assertEquals(7, respuesta.scope().rowsEvaluated());
        assertTrue(respuesta.scope().complete());
        assertEquals(assetId, respuesta.assetId());
        assertEquals(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), respuesta.evaluatedAt());
        assertNull(respuesta.readId());
    }

    @Test
    void exigePermisoDelProyectoYFiltraLaConsultaPorProjectId() {
        consultaDevuelve(new AgregadoPedidos(BigDecimal.TEN, 1));

        servicio.evaluar(projectId, solicitud(KpiCode.TICKET_PROMEDIO));

        verify(autorizacion).exigir(actorId, SolicitudAcceso.proyecto(KpiServiceImpl.PERMISO_EVALUAR, projectId));
        verify(jdbc).queryForObject(
                eq(KpiServiceImpl.SQL_TICKET_PROMEDIO), any(RowMapper.class),
                eq(projectId), eq(projectId), eq(null), eq(null));
    }

    @Test
    void propagaLaVentanaALaConsulta() {
        consultaDevuelve(new AgregadoPedidos(BigDecimal.TEN, 1));
        OffsetDateTime desde = OffsetDateTime.parse("2026-11-10T00:00:00Z");
        OffsetDateTime hasta = OffsetDateTime.parse("2026-11-13T00:00:00Z");

        servicio.evaluar(projectId, new KpiEvaluateRequest(
                assetId, List.of(KpiCode.TICKET_PROMEDIO), new TimeWindow(desde, hasta)));

        verify(jdbc).queryForObject(
                eq(KpiServiceImpl.SQL_TICKET_PROMEDIO), any(RowMapper.class),
                eq(projectId), eq(projectId), eq(desde), eq(hasta));
    }

    @Test
    void sinPedidosElTicketNoEsEvaluable() {
        consultaDevuelve(new AgregadoPedidos(BigDecimal.ZERO, 0));

        KpiEvaluateResponse respuesta = servicio.evaluar(projectId, solicitud(KpiCode.TICKET_PROMEDIO));

        KpiResult ticket = respuesta.results().getFirst();
        assertNull(ticket.value());
        assertEquals(KpiStatus.NOT_EVALUABLE, ticket.status());
        assertEquals(ExecutionState.EMPTY, respuesta.executionState());
        assertEquals(0, respuesta.scope().rowsEvaluated());
    }

    @Test
    void redondeaADosDecimalesHaciaArriba() {
        assertEquals(new BigDecimal("3.33"),
                KpiServiceImpl.calcularTicketPromedio(new AgregadoPedidos(new BigDecimal("10.00"), 3)).value());
        assertEquals(new BigDecimal("0.03"),
                KpiServiceImpl.calcularTicketPromedio(new AgregadoPedidos(new BigDecimal("0.05"), 2)).value());
        assertEquals(new BigDecimal("6.67"),
                KpiServiceImpl.calcularTicketPromedio(new AgregadoPedidos(new BigDecimal("20"), 3)).value());
    }

    @Test
    void losCodigosAunNoImplementadosNoSonEvaluables() {
        consultaDevuelve(new AgregadoPedidos(BigDecimal.TEN, 1));

        KpiEvaluateResponse respuesta = servicio.evaluar(
                projectId, solicitud(KpiCode.DQ_VALIDEZ, KpiCode.TICKET_PROMEDIO));

        assertEquals(KpiCode.DQ_VALIDEZ, respuesta.results().get(0).kpiCode());
        assertEquals(KpiStatus.NOT_EVALUABLE, respuesta.results().get(0).status());
        assertNull(respuesta.results().get(0).value());
        assertEquals(new BigDecimal("10.00"), respuesta.results().get(1).value());
    }

    @Test
    void sinPermisoNoSeConsultaNingunDato() {
        doThrow(new AccesoDenegadoException(Motivo.PERMISO_NO_CONCEDIDO)).when(autorizacion).exigir(any(), any());

        assertThrows(AccesoDenegadoException.class,
                () -> servicio.evaluar(projectId, solicitud(KpiCode.TICKET_PROMEDIO)));

        verifyNoInteractions(jdbc);
    }

    private KpiEvaluateRequest solicitud(KpiCode... codigos) {
        return new KpiEvaluateRequest(assetId, List.of(codigos), null);
    }

    @SuppressWarnings("unchecked")
    private void consultaDevuelve(AgregadoPedidos agregado) {
        when(jdbc.queryForObject(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(agregado);
    }
}
