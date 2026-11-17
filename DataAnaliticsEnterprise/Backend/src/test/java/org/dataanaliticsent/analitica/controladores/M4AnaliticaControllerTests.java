package org.dataanaliticsent.analitica.controladores;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.analitica.dto.ExecutionState;
import org.dataanaliticsent.analitica.dto.KpiCode;
import org.dataanaliticsent.analitica.dto.KpiEvaluateResponse;
import org.dataanaliticsent.analitica.dto.KpiResult;
import org.dataanaliticsent.analitica.dto.KpiStatus;
import org.dataanaliticsent.analitica.dto.Scope;
import org.dataanaliticsent.analitica.servicios.KpiService;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin contexto de Spring ni seguridad: verifica la envoltura de éxito y el formato de error de M4. */
class M4AnaliticaControllerTests {

    private static final String PROJECT_ID = "2f66e241-dd8b-508f-b5e1-85db372e9f2c";
    private static final String ASSET_ID = "f5671a70-c3d1-58ad-b14d-5ec5501146bc";
    private static final String RUTA_KPIS = "/api/v1/projects/" + PROJECT_ID + "/analytics/kpis/evaluate";
    private static final String SOLICITUD = "{\"asset_id\":\"" + ASSET_ID + "\",\"kpi_codes\":[\"TICKET_PROMEDIO\"]}";

    private KpiService kpiService;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        kpiService = mock(KpiService.class);
        ProblemasHttp problemas = new ProblemasHttp(JsonMapper.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(new M4AnaliticaController(kpiService, problemas))
                .setControllerAdvice(new M4ErroresAdvice(problemas))
                .build();
    }

    @Test
    void elPingRespondeConLaEnvolturaComun() throws Exception {
        mvc.perform(get("/api/v1/analitica/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.message").value("M4 Analitica operativo"))
                .andExpect(jsonPath("$.meta.request_id").isNotEmpty());
    }

    @Test
    void laEvaluacionSeEnvuelveEnDataYMeta() throws Exception {
        when(kpiService.evaluar(eq(UUID.fromString(PROJECT_ID)), any())).thenReturn(new KpiEvaluateResponse(
                UUID.randomUUID(), null, UUID.fromString(ASSET_ID), ExecutionState.COMPLETED,
                new Scope(true, 7, null), OffsetDateTime.parse("2026-11-16T12:00:00Z"),
                List.of(KpiResult.sinUmbrales(KpiCode.TICKET_PROMEDIO, new BigDecimal("45.06"), KpiStatus.OK))));

        mvc.perform(post(RUTA_KPIS).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.execution_state").value("completed"))
                .andExpect(jsonPath("$.data.scope.rows_evaluated").value(7))
                .andExpect(jsonPath("$.data.results[0].kpi_code").value("TICKET_PROMEDIO"))
                .andExpect(jsonPath("$.data.results[0].category").value("comercial"))
                .andExpect(jsonPath("$.data.results[0].value").value(45.06))
                .andExpect(jsonPath("$.meta.request_id").isNotEmpty());
    }

    @Test
    void unaSolicitudInvalidaDevuelveProblemJson() throws Exception {
        mvc.perform(post(RUTA_KPIS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asset_id\":\"" + ASSET_ID + "\",\"kpi_codes\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(startsWith("urn:request:")))
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        verifyNoInteractions(kpiService);
    }

    @Test
    void unProjectIdMalFormadoDevuelveProblemJson() throws Exception {
        mvc.perform(post("/api/v1/projects/no-es-uuid/analytics/kpis/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(kpiService);
    }

    @Test
    void unFalloDeDatosDevuelveAnaliticaNoDisponible() throws Exception {
        when(kpiService.evaluar(any(), any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));

        mvc.perform(post(RUTA_KPIS).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ANALYTICS_UNAVAILABLE"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }
}
