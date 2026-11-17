package org.dataanaliticsent.Gobierno.fichas.internal.presentacion;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.web.CuerpoJson;
import org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno;
import org.dataanaliticsent.Gobierno.comun.web.JsonGobierno;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.DatosFicha;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ficha de gobierno de un activo: responsable del dato y clasificación formal. */
@RestController
@RequestMapping(RespuestasGobierno.BASE)
public class FichasController {
    private final FichasGobiernoApi fichas;

    public FichasController(FichasGobiernoApi fichas) { this.fichas = fichas; }

    @GetMapping("/assets/{asset_id}/governance-profile")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId) {
        Ficha f = fichas.consultar(projectId, assetId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("El activo no tiene ficha de gobierno"));
        return RespuestasGobierno.ok(JsonGobierno.ficha(f), f.revision());
    }

    /**
     * Sustituye la ficha completa (PUT): owner_id y classification ausentes o null quedan sin definir.
     * Sin If-Match solo crea una ficha nueva (201); con If-Match edita la vigente (200).
     */
    @PutMapping(path = "/assets/{asset_id}/governance-profile", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> guardar(@PathVariable("project_id") UUID projectId,
            @PathVariable("asset_id") UUID assetId, @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        Long revision = ifMatch == null ? null : RespuestasGobierno.revisionEsperada(ifMatch);
        CuerpoJson c = CuerpoJson.de(cuerpo, Set.of("owner_id", "classification", "reason"));
        UUID responsable = c.uuid("owner_id", false);
        Clasificacion clasificacion = c.enumeracion("classification", Clasificacion.class, false);
        String motivo = c.texto("reason", true);
        c.validar();
        Ficha f = fichas.guardar(new DatosFicha(projectId, assetId, revision, responsable, clasificacion, motivo));
        if (revision == null) {
            URI ubicacion = URI.create("/api/v1/projects/" + projectId + "/assets/" + assetId + "/governance-profile");
            return RespuestasGobierno.creado(ubicacion, JsonGobierno.ficha(f), f.revision());
        }
        return RespuestasGobierno.ok(JsonGobierno.ficha(f), f.revision());
    }

}
