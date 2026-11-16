package org.dataanaliticsent.analitica.controladores;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.analitica.dto.KpiEvaluateRequest;
import org.dataanaliticsent.analitica.servicios.KpiService;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Las respuestas de éxito usan la envoltura {data, meta} del tronco común. */
@RestController
public class M4AnaliticaController {

    private final KpiService kpiService;
    private final ProblemasHttp salida;

    public M4AnaliticaController(KpiService kpiService, ProblemasHttp salida) {
        this.kpiService = kpiService;
        this.salida = salida;
    }

    @GetMapping("/api/v1/analitica/ping")
    public void ping(HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, Map.of("message", "M4 Analitica operativo"));
    }

    @PostMapping("/api/v1/projects/{projectId}/analytics/kpis/evaluate")
    public void evaluarKpis(
            @PathVariable UUID projectId,
            @RequestBody KpiEvaluateRequest solicitud,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        salida.datos(request, response, kpiService.evaluar(projectId, solicitud));
    }
}
