package org.dataanaliticsent.Gobierno.versionado.internal.presentacion;

import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.web.JsonGobierno;
import org.dataanaliticsent.Gobierno.comun.web.ParametrosConsulta;
import org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno;
import org.dataanaliticsent.Gobierno.comun.web.SolicitudInvalidaException;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de versiones de cualquier recurso versionado, por ejemplo
 * /versions/gobierno.politica/{policy_id}. Crear versiones es solo por la API Java.
 */
@RestController
@RequestMapping(RespuestasGobierno.BASE + "/versions/{resource_type}/{resource_id}")
public class VersionesController {
    private final VersionadoApi versionado;

    public VersionesController(VersionadoApi versionado) { this.versionado = versionado; }

    @GetMapping
    public ResponseEntity<Map<String, Object>> historial(@PathVariable("project_id") UUID projectId,
            @PathVariable("resource_type") String tipo, @PathVariable("resource_id") UUID recurso,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize) {
        return RespuestasGobierno.pagina(versionado.historial(projectId, tipo, recurso, ParametrosConsulta.page(page),
                ParametrosConsulta.pageSize(pageSize)), JsonGobierno::version);
    }

    /** Compara dos versiones del recurso: ?from={version_id}&to={version_id}. */
    @GetMapping("/comparison")
    public ResponseEntity<Map<String, Object>> comparar(@PathVariable("project_id") UUID projectId,
            @PathVariable("resource_type") String tipo, @PathVariable("resource_id") UUID recurso,
            @RequestParam(name = "from", required = false) String desde,
            @RequestParam(name = "to", required = false) String hasta) {
        UUID d = ParametrosConsulta.uuid("from", desde);
        UUID h = ParametrosConsulta.uuid("to", hasta);
        if (d == null || h == null) {
            throw SolicitudInvalidaException.de("query", d == null ? "from" : "to", "REQUIRED", "Parámetro obligatorio");
        }
        return RespuestasGobierno.ok(JsonGobierno.comparacion(versionado.comparar(projectId, tipo, recurso, d, h)));
    }

    @GetMapping("/{version_id}")
    public ResponseEntity<Map<String, Object>> version(@PathVariable("project_id") UUID projectId,
            @PathVariable("resource_type") String tipo, @PathVariable("resource_id") UUID recurso,
            @PathVariable("version_id") UUID versionId) {
        return RespuestasGobierno.ok(JsonGobierno.version(versionado.version(projectId, tipo, recurso, versionId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("Versión no disponible"))));
    }
}
