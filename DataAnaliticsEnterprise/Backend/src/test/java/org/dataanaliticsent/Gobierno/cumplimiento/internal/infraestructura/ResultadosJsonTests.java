package org.dataanaliticsent.Gobierno.cumplimiento.internal.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.EstadoCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoPolitica;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoRegla;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.TipoRegla;
import org.junit.jupiter.api.Test;

/** Lo que se guarda en la columna jsonb results se lee igual, incluidos los valores null. */
class ResultadosJsonTests {

    @Test
    void ida_y_vuelta_conserva_todos_los_datos() {
        List<ResultadoPolitica> originales = List.of(
                new ResultadoPolitica(UUID.randomUUID(), "Datos personales", UUID.randomUUID(),
                        EstadoCumplimiento.NON_COMPLIANT, null, List.of(
                        new ResultadoRegla(UUID.randomUUID(), TipoRegla.REQUIERE_RESPONSABLE, null,
                                EstadoCumplimiento.COMPLIANT, "CUMPLE"),
                        new ResultadoRegla(UUID.randomUUID(), TipoRegla.CLASIFICACION_MINIMA, "CONFIDENCIAL",
                                EstadoCumplimiento.NON_COMPLIANT, "CLASIFICACION_INSUFICIENTE"))),
                new ResultadoPolitica(UUID.randomUUID(), "Documental", UUID.randomUUID(),
                        EstadoCumplimiento.INDETERMINATE, "SIN_REGLAS", List.of()));

        assertEquals(originales, EvaluacionesJdbcAdapter.desdeJson(EvaluacionesJdbcAdapter.aJson(originales)));
    }
}
