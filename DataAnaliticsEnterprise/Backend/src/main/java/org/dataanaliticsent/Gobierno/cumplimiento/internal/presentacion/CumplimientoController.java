package org.dataanaliticsent.Gobierno.cumplimiento.internal.presentacion;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.web.ParametrosConsulta;
import org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno;
import org.dataanaliticsent.Gobierno.comun.web.JsonGobierno;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Evaluación de cumplimiento. GET .../compliance es el estado que consume M4 para sus semáforos. */
@RestController
@RequestMapping(RespuestasGobierno.BASE + "/assets/{asset_id}")
public class CumplimientoController {
    private final CumplimientoApi cumplimiento;

    public CumplimientoController(CumplimientoApi cumplimiento) { this.cumplimiento = cumplimiento; }

    /** Evalúa en el momento (síncrono) y guarda el resultado. No lleva cuerpo. */
    @PostMapping("/compliance-evaluations")
    public ResponseEntity<Map<String, Object>> evaluar(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId) {
        Evaluacion e = cumplimiento.evaluar(projectId, assetId);
        URI ubicacion = URI.create("/api/v1/projects/" + projectId + "/assets/" + assetId + "/compliance");
        return RespuestasGobierno.creado(ubicacion, JsonGobierno.evaluacion(e), null);
    }

    @GetMapping("/compliance-evaluations")
    public ResponseEntity<Map<String, Object>> historial(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId, @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize) {
        return RespuestasGobierno.pagina(cumplimiento.historial(projectId, assetId, ParametrosConsulta.page(page),
                ParametrosConsulta.pageSize(pageSize)), JsonGobierno::evaluacion);
    }

    /** Última evaluación. 404 si el activo nunca se evaluó. */
    @GetMapping("/compliance")
    public ResponseEntity<Map<String, Object>> ultima(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId) {
        Evaluacion e = cumplimiento.ultima(projectId, assetId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("El activo todavía no tiene evaluaciones"));
        return RespuestasGobierno.ok(JsonGobierno.evaluacion(e));
    }

}
