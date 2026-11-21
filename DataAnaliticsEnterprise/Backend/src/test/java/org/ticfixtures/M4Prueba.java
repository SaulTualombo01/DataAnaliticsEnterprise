package org.ticfixtures;

import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.springframework.web.bind.annotation.*;

/** Sustituto de prueba fuera del escaneo principal; no se empaqueta en el JAR. */
@RestController
public class M4Prueba {
    private final IdentidadActualApi identidad;
    private final AutorizacionApi permisos;
    public M4Prueba(IdentidadActualApi identidad,AutorizacionApi permisos){this.identidad=identidad;this.permisos=permisos;}
    @GetMapping("/api/v1/analitica/ping") Map<String,Object> ping(){return Map.of("test_only",true);}
    @PostMapping("/api/v1/projects/{projectId}/analytics/kpis/evaluate")
    Map<String,Object> evaluar(@PathVariable UUID projectId){
        permisos.exigir(identidad.usuarioId(),SolicitudAcceso.proyecto("m4.kpis.evaluate",projectId));
        return Map.of("test_only",true);
    }
}
