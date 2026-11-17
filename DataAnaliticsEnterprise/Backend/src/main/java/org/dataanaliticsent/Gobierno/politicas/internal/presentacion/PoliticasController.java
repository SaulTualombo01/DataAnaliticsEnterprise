package org.dataanaliticsent.Gobierno.politicas.internal.presentacion;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.web.CuerpoJson;
import org.dataanaliticsent.Gobierno.comun.web.ParametrosConsulta;
import org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno;
import org.dataanaliticsent.Gobierno.comun.web.JsonGobierno;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CambiosPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.FiltroPoliticas;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.NuevaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.TipoRegla;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Rutas HTTP de políticas de gobierno. Contrato: docs/contratos/m3/gobierno-http.openapi.json. */
@RestController
@RequestMapping(RespuestasGobierno.BASE)
public class PoliticasController {
    private static final String JSON = "application/json";
    private static final String MERGE_PATCH = "application/merge-patch+json";

    private final PoliticasApi politicas;

    public PoliticasController(PoliticasApi politicas) { this.politicas = politicas; }

    @GetMapping("/governance-policies")
    public ResponseEntity<Map<String, Object>> buscar(@PathVariable("project_id") UUID projectId,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize,
            @RequestParam(name = "q", required = false) String texto,
            @RequestParam(name = "status", required = false) String estado,
            @RequestParam(name = "category", required = false) String categoria,
            @RequestParam(name = "owner_id", required = false) String responsable,
            @RequestParam(name = "asset_id", required = false) String activo) {
        FiltroPoliticas filtro = new FiltroPoliticas(texto,
                ParametrosConsulta.enumeracion("status", estado, EstadoPolitica.class),
                ParametrosConsulta.enumeracion("category", categoria, CategoriaPolitica.class),
                ParametrosConsulta.uuid("owner_id", responsable), ParametrosConsulta.uuid("asset_id", activo));
        return RespuestasGobierno.pagina(politicas.buscar(projectId, filtro, ParametrosConsulta.page(page),
                ParametrosConsulta.pageSize(pageSize)), JsonGobierno::politica);
    }

    @PostMapping(path = "/governance-policies", consumes = JSON)
    public ResponseEntity<Map<String, Object>> crear(@PathVariable("project_id") UUID projectId,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("name", "description", "category", "owner_id"));
        String nombre = c.texto("name", true);
        String descripcion = c.texto("description", false);
        CategoriaPolitica categoria = c.enumeracion("category", CategoriaPolitica.class, true);
        UUID responsable = c.uuid("owner_id", true);
        c.validar();
        Politica p = politicas.crear(new NuevaPolitica(projectId, nombre, descripcion, categoria, responsable));
        return RespuestasGobierno.creado(URI.create(ruta(projectId, p.id())), JsonGobierno.politica(p), p.revision());
    }

    @GetMapping("/governance-policies/{policy_id}")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable("project_id") UUID projectId,
            @PathVariable("policy_id") UUID policyId) {
        Politica p = politicas.consultar(projectId, policyId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("Política no disponible"));
        return RespuestasGobierno.ok(JsonGobierno.politica(p), p.revision());
    }

    /** JSON Merge Patch: un campo ausente no cambia. reason es obligatorio. */
    @PatchMapping(path = "/governance-policies/{policy_id}", consumes = {JSON, MERGE_PATCH})
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable("project_id") UUID projectId,
            @PathVariable("policy_id") UUID policyId, @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        long revision = RespuestasGobierno.revisionEsperada(ifMatch);
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("name", "description", "category", "owner_id", "reason"));
        String nombre = c.texto("name", false);
        String descripcion = c.contiene("description") ? orVacio(c.texto("description", false)) : null;
        CategoriaPolitica categoria = c.enumeracion("category", CategoriaPolitica.class, false);
        UUID responsable = c.uuid("owner_id", false);
        String motivo = c.texto("reason", true);
        c.validar();
        Politica p = politicas.actualizar(projectId, policyId, revision,
                new CambiosPolitica(nombre, descripcion, categoria, responsable), motivo);
        return RespuestasGobierno.ok(JsonGobierno.politica(p), p.revision());
    }

    @PostMapping(path = "/governance-policies/{policy_id}/status-changes", consumes = JSON)
    public ResponseEntity<Map<String, Object>> cambiarEstado(@PathVariable("project_id") UUID projectId,
            @PathVariable("policy_id") UUID policyId, @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        long revision = RespuestasGobierno.revisionEsperada(ifMatch);
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("status", "reason"));
        EstadoPolitica estado = c.enumeracion("status", EstadoPolitica.class, true);
        String motivo = c.texto("reason", true);
        c.validar();
        Politica p = politicas.cambiarEstado(projectId, policyId, revision, estado, motivo);
        return RespuestasGobierno.ok(JsonGobierno.politica(p), p.revision());
    }

    @PostMapping(path = "/governance-policies/{policy_id}/assets", consumes = JSON)
    public ResponseEntity<Map<String, Object>> asociarActivo(@PathVariable("project_id") UUID projectId,
            @PathVariable("policy_id") UUID policyId, @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        long revision = RespuestasGobierno.revisionEsperada(ifMatch);
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("asset_id", "reason"));
        UUID activo = c.uuid("asset_id", true);
        String motivo = c.texto("reason", true);
        c.validar();
        Politica p = politicas.asociarActivo(projectId, policyId, revision, activo, motivo);
        return RespuestasGobierno.ok(JsonGobierno.politica(p), p.revision());
    }

    @PostMapping(path = "/governance-policies/{policy_id}/asset-removals", consumes = JSON)
    public ResponseEntity<Map<String, Object>> retirarActivo(@PathVariable("project_id") UUID projectId,
            @PathVariable("policy_id") UUID policyId, @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        long revision = RespuestasGobierno.revisionEsperada(ifMatch);
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("asset_id", "reason"));
        UUID activo = c.uuid("asset_id", true);
        String motivo = c.texto("reason", true);
        c.validar();
        Politica p = politicas.retirarActivo(projectId, policyId, revision, activo, motivo);
        return RespuestasGobierno.ok(JsonGobierno.politica(p), p.revision());
    }

    @PostMapping(path = "/governance-policies/{policy_id}/rules", consumes = JSON)
    public ResponseEntity<Map<String, Object>> agregarRegla(@PathVariable("project_id") UUID projectId,
            @PathVariable("policy_id") UUID policyId, @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        long revision = RespuestasGobierno.revisionEsperada(ifMatch);
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("type", "parameter", "reason"));
        TipoRegla tipo = c.enumeracion("type", TipoRegla.class, true);
        String parametro = c.texto("parameter", false);
        String motivo = c.texto("reason", true);
        c.validar();
        Politica p = politicas.agregarRegla(projectId, policyId, revision, tipo, parametro, motivo);
        return RespuestasGobierno.ok(JsonGobierno.politica(p), p.revision());
    }

    @PostMapping(path = "/governance-policies/{policy_id}/rule-removals", consumes = JSON)
    public ResponseEntity<Map<String, Object>> quitarRegla(@PathVariable("project_id") UUID projectId,
            @PathVariable("policy_id") UUID policyId, @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        long revision = RespuestasGobierno.revisionEsperada(ifMatch);
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("rule_id", "reason"));
        UUID regla = c.uuid("rule_id", true);
        String motivo = c.texto("reason", true);
        c.validar();
        Politica p = politicas.quitarRegla(projectId, policyId, revision, regla, motivo);
        return RespuestasGobierno.ok(JsonGobierno.politica(p), p.revision());
    }

    @GetMapping("/assets/{asset_id}/governance-policies")
    public ResponseEntity<Map<String, Object>> politicasDeActivo(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId) {
        return RespuestasGobierno.ok(politicas.politicasDeActivo(projectId, assetId).stream()
                .map(JsonGobierno::politicaAplicable).toList());
    }

    private static String ruta(UUID projectId, UUID policyId) {
        return "/api/v1/projects/" + projectId + "/governance-policies/" + policyId;
    }

    private static String orVacio(String texto) { return texto == null ? "" : texto; }
}
