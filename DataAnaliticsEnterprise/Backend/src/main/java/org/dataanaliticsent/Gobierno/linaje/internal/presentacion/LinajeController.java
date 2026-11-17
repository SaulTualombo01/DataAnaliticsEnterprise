package org.dataanaliticsent.Gobierno.linaje.internal.presentacion;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.web.CuerpoJson;
import org.dataanaliticsent.Gobierno.comun.web.ParametrosConsulta;
import org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno;
import org.dataanaliticsent.Gobierno.comun.web.JsonGobierno;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Direccion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.NuevaRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Relaciones de linaje entre activos y grafo de orígenes y destinos. */
@RestController
@RequestMapping(RespuestasGobierno.BASE)
public class LinajeController {
    private final LinajeApi linaje;

    public LinajeController(LinajeApi linaje) { this.linaje = linaje; }

    /** Registrar una relación igual a una vigente devuelve la existente (también 201). */
    @PostMapping(path = "/lineage-relations", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> registrar(@PathVariable("project_id") UUID projectId,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("origin_asset_id", "target_asset_id", "type", "description", "reason"));
        UUID origen = c.uuid("origin_asset_id", true);
        UUID destino = c.uuid("target_asset_id", true);
        TipoRelacion tipo = c.enumeracion("type", TipoRelacion.class, true);
        String descripcion = c.texto("description", false);
        String motivo = c.texto("reason", true);
        c.validar();
        Relacion r = linaje.registrar(new NuevaRelacion(projectId, origen, destino, tipo, descripcion, motivo));
        return RespuestasGobierno.creado(URI.create("/api/v1/projects/" + projectId + "/lineage-relations/" + r.id()),
                JsonGobierno.relacion(r), null);
    }

    @GetMapping("/lineage-relations/{relation_id}")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable("project_id") UUID projectId,
            @PathVariable("relation_id") UUID relationId) {
        Relacion r = linaje.consultar(projectId, relationId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("Relación de linaje no disponible"));
        return RespuestasGobierno.ok(JsonGobierno.relacion(r));
    }

    @PostMapping(path = "/lineage-relations/{relation_id}/retirements", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> retirar(@PathVariable("project_id") UUID projectId,
            @PathVariable("relation_id") UUID relationId, @RequestBody(required = false) Map<String, Object> cuerpo) {
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("reason"));
        String motivo = c.texto("reason", true);
        c.validar();
        return RespuestasGobierno.ok(JsonGobierno.relacion(linaje.retirar(projectId, relationId, motivo)));
    }

    /** direction: ORIGENES, DESTINOS o AMBOS (por defecto). depth: 1 a 5 (por defecto 1). */
    @GetMapping("/assets/{asset_id}/lineage")
    public ResponseEntity<Map<String, Object>> grafo(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId, @RequestParam(name = "direction", required = false) String direccion,
            @RequestParam(name = "depth", required = false) String profundidad) {
        Direccion d = direccion == null ? Direccion.AMBOS : ParametrosConsulta.enumeracion("direction", direccion, Direccion.class);
        int nivel = ParametrosConsulta.entero("depth", profundidad, 1, 1, 5);
        return RespuestasGobierno.ok(JsonGobierno.grafo(linaje.grafo(projectId, assetId, d, nivel)));
    }

}
