package org.dataanaliticsent.Gobierno.historial.internal.presentacion;

import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.web.JsonGobierno;
import org.dataanaliticsent.Gobierno.comun.web.ParametrosConsulta;
import org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resumen de gobierno y cronología de un activo del catálogo. */
@RestController
@RequestMapping(RespuestasGobierno.BASE + "/assets/{asset_id}")
public class HistorialController {
    private final HistorialActivoApi historial;

    public HistorialController(HistorialActivoApi historial) { this.historial = historial; }

    @GetMapping("/governance-summary")
    public ResponseEntity<Map<String, Object>> resumen(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId) {
        return RespuestasGobierno.ok(JsonGobierno.resumen(historial.resumen(projectId, assetId)));
    }

    @GetMapping("/governance-history")
    public ResponseEntity<Map<String, Object>> cronologia(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId, @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize) {
        return RespuestasGobierno.pagina(historial.cronologia(projectId, assetId, ParametrosConsulta.page(page),
                ParametrosConsulta.pageSize(pageSize)), JsonGobierno::entrada);
    }
}
