package org.dataanaliticsent.Gobierno.auditoria.internal.presentacion;

import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Filtro;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.comun.web.ParametrosConsulta;
import org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno;
import org.dataanaliticsent.Gobierno.comun.web.JsonGobierno;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de eventos de auditoría del proyecto. Registrar eventos es solo por la API Java. */
@RestController
@RequestMapping(RespuestasGobierno.BASE)
public class AuditoriaController {
    private final AuditoriaApi auditoria;

    public AuditoriaController(AuditoriaApi auditoria) { this.auditoria = auditoria; }

    @GetMapping("/audit-events")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable("project_id") UUID projectId,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize,
            @RequestParam(name = "actor_id", required = false) String actor,
            @RequestParam(name = "action", required = false) String accion,
            @RequestParam(name = "resource_type", required = false) String tipoRecurso,
            @RequestParam(name = "resource_id", required = false) String recurso,
            @RequestParam(name = "outcome", required = false) String resultado,
            @RequestParam(name = "from", required = false) String desde,
            @RequestParam(name = "to", required = false) String hasta) {
        Filtro filtro = new Filtro(projectId, ParametrosConsulta.uuid("actor_id", actor), accion, tipoRecurso,
                ParametrosConsulta.uuid("resource_id", recurso),
                ParametrosConsulta.enumeracion("outcome", resultado, Resultado.class),
                ParametrosConsulta.instante("from", desde), ParametrosConsulta.instante("to", hasta));
        return RespuestasGobierno.pagina(auditoria.consultar(filtro, ParametrosConsulta.page(page),
                ParametrosConsulta.pageSize(pageSize)), JsonGobierno::evento);
    }

}
