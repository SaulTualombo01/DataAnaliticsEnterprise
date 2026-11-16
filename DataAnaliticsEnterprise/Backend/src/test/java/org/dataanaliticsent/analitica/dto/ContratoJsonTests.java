package org.dataanaliticsent.analitica.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los DTO deben serializarse con los nombres snake_case del contrato analitica-http.schema.json. */
class ContratoJsonTests {

    private static final String ASSET_ID = "f5671a70-c3d1-58ad-b14d-5ec5501146bc";

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void laRespuestaUsaLosNombresDelContrato() {
        KpiEvaluateResponse respuesta = new KpiEvaluateResponse(
                UUID.randomUUID(), null, UUID.fromString(ASSET_ID), ExecutionState.COMPLETED,
                new Scope(true, 7, null), OffsetDateTime.parse("2026-11-16T12:00:00Z"),
                List.of(KpiResult.sinUmbrales(KpiCode.TICKET_PROMEDIO, new BigDecimal("45.06"), KpiStatus.OK)));

        JsonNode nodo = json.readTree(json.writeValueAsString(respuesta));

        assertTrue(nodo.has("evaluation_id"));
        assertTrue(nodo.get("read_id").isNull());
        assertEquals(ASSET_ID, nodo.get("asset_id").asString());
        assertEquals("completed", nodo.get("execution_state").asString());
        assertEquals(7, nodo.get("scope").get("rows_evaluated").asInt());
        JsonNode resultado = nodo.get("results").get(0);
        assertEquals("TICKET_PROMEDIO", resultado.get("kpi_code").asString());
        assertEquals("comercial", resultado.get("category").asString());
        assertEquals("ok", resultado.get("status").asString());
        assertEquals(new BigDecimal("45.06"), resultado.get("value").decimalValue());
        assertTrue(resultado.get("threshold_warning").isNull());
    }

    @Test
    void laSolicitudSeLeeDesdeSnakeCase() {
        KpiEvaluateRequest solicitud = json.readValue("""
                {"asset_id":"%s","kpi_codes":["TICKET_PROMEDIO"],
                 "window":{"from":"2026-11-10T00:00:00Z","to":"2026-11-13T00:00:00Z"}}
                """.formatted(ASSET_ID), KpiEvaluateRequest.class);

        assertEquals(UUID.fromString(ASSET_ID), solicitud.assetId());
        assertEquals(List.of(KpiCode.TICKET_PROMEDIO), solicitud.kpiCodes());
        assertTrue(solicitud.window().from().isBefore(solicitud.window().to()));
    }

    @Test
    void rechazaSolicitudesFueraDelContrato() {
        assertThrows(JacksonException.class, () -> json.readValue(
                "{\"asset_id\":\"%s\",\"kpi_codes\":[]}".formatted(ASSET_ID), KpiEvaluateRequest.class));
        assertThrows(JacksonException.class, () -> json.readValue(
                "{\"asset_id\":\"%s\",\"kpi_codes\":[\"NO_EXISTE\"]}".formatted(ASSET_ID), KpiEvaluateRequest.class));
        assertThrows(IllegalArgumentException.class, () -> new KpiEvaluateRequest(
                UUID.fromString(ASSET_ID), List.of(KpiCode.VOL_FILAS, KpiCode.VOL_FILAS), null));
    }
}
