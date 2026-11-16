package org.dataanaliticsent.analitica.servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.dataanaliticsent.analitica.dto.ExecutionState;
import org.dataanaliticsent.analitica.dto.KpiCode;
import org.dataanaliticsent.analitica.dto.KpiEvaluateRequest;
import org.dataanaliticsent.analitica.dto.KpiEvaluateResponse;
import org.dataanaliticsent.analitica.dto.KpiResult;
import org.dataanaliticsent.analitica.dto.KpiStatus;
import org.dataanaliticsent.analitica.dto.Scope;
import org.dataanaliticsent.analitica.dto.TimeWindow;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KpiServiceImpl implements KpiService {

    static final String PERMISO_EVALUAR = "m4.kpis.evaluate";

    /**
     * Consulta simulada: los pedidos son sintéticos y se asignan al proyecto solicitante.
     * Al integrar la lectura real (TC13) solo cambia el CTE {@code pedidos_sinteticos};
     * el filtro por {@code project_id} y la ventana semiabierta se conservan.
     * Parámetros: projectId, projectId, desde (nullable), hasta (nullable).
     */
    static final String SQL_TICKET_PROMEDIO = """
            WITH pedidos_sinteticos (project_id, created_at, total) AS (
                SELECT CAST(? AS uuid), v.created_at, v.total
                FROM (VALUES
                    (TIMESTAMPTZ '2026-11-10 10:00:00+00', 42.50),
                    (TIMESTAMPTZ '2026-11-11 10:00:00+00', 44.10),
                    (TIMESTAMPTZ '2026-11-12 10:00:00+00', 39.80),
                    (TIMESTAMPTZ '2026-11-13 10:00:00+00', 45.30),
                    (TIMESTAMPTZ '2026-11-14 10:00:00+00', 47.90),
                    (TIMESTAMPTZ '2026-11-15 10:00:00+00', 46.20),
                    (TIMESTAMPTZ '2026-11-16 10:00:00+00', 49.60)
                ) AS v (created_at, total)
            )
            SELECT COALESCE(SUM(p.total), 0) AS ingresos, COUNT(*) AS pedidos
            FROM pedidos_sinteticos p
            WHERE p.project_id = CAST(? AS uuid)
              AND p.created_at >= COALESCE(CAST(? AS timestamptz), '-infinity')
              AND p.created_at < COALESCE(CAST(? AS timestamptz), 'infinity')
            """;

    private static final int ESCALA_MONEDA = 2;

    record AgregadoPedidos(BigDecimal ingresos, long pedidos) {
    }

    private final JdbcTemplate jdbc;
    private final AutorizacionApi autorizacion;
    private final IdentidadActualApi identidad;
    private final Clock reloj;

    public KpiServiceImpl(JdbcTemplate jdbc, AutorizacionApi autorizacion, IdentidadActualApi identidad, Clock reloj) {
        this.jdbc = jdbc;
        this.autorizacion = autorizacion;
        this.identidad = identidad;
        this.reloj = reloj;
    }

    @Override
    public KpiEvaluateResponse evaluar(UUID projectId, KpiEvaluateRequest solicitud) {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(solicitud);
        autorizacion.exigir(identidad.usuarioId(), SolicitudAcceso.proyecto(PERMISO_EVALUAR, projectId));

        AgregadoPedidos agregado = consultarPedidos(projectId, solicitud.window());
        List<KpiResult> resultados = solicitud.kpiCodes().stream()
                .map(codigo -> codigo == KpiCode.TICKET_PROMEDIO
                        ? calcularTicketPromedio(agregado)
                        : KpiResult.noEvaluable(codigo))
                .toList();

        return new KpiEvaluateResponse(
                UUID.randomUUID(),
                null,
                solicitud.assetId(),
                agregado.pedidos() == 0 ? ExecutionState.EMPTY : ExecutionState.COMPLETED,
                new Scope(true, agregado.pedidos(), null),
                OffsetDateTime.now(reloj),
                resultados);
    }

    /** Ticket promedio = ingresos / pedidos, redondeado a 2 decimales (HALF_UP). Sin pedidos no es evaluable. */
    static KpiResult calcularTicketPromedio(AgregadoPedidos agregado) {
        if (agregado.pedidos() == 0) {
            return KpiResult.noEvaluable(KpiCode.TICKET_PROMEDIO);
        }
        BigDecimal promedio = agregado.ingresos()
                .divide(BigDecimal.valueOf(agregado.pedidos()), ESCALA_MONEDA, RoundingMode.HALF_UP);
        return KpiResult.sinUmbrales(KpiCode.TICKET_PROMEDIO, promedio, KpiStatus.OK);
    }

    private AgregadoPedidos consultarPedidos(UUID projectId, TimeWindow ventana) {
        OffsetDateTime desde = ventana == null ? null : ventana.from();
        OffsetDateTime hasta = ventana == null ? null : ventana.to();
        return jdbc.queryForObject(
                SQL_TICKET_PROMEDIO,
                (rs, fila) -> new AgregadoPedidos(rs.getBigDecimal("ingresos"), rs.getLong("pedidos")),
                projectId, projectId, desde, hasta);
    }
}
