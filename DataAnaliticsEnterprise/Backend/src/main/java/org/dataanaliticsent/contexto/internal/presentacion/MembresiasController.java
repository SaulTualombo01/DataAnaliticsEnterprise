package org.dataanaliticsent.contexto.internal.presentacion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.dataanaliticsent.contexto.internal.aplicacion.ServicioMembresias;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/projects/{projectId}/memberships")
public class MembresiasController {
    private final ServicioMembresias servicio;
    private final ProblemasHttp salida;

    public MembresiasController(ServicioMembresias servicio, ProblemasHttp salida) {
        this.servicio = servicio;
        this.salida = salida;
    }

    @GetMapping
    public void listar(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, servicio.listar(projectId, new Pagina(limit, offset)));
    }

    @PostMapping
    public void asignar(
            @PathVariable UUID projectId, @RequestBody SolicitudMembresia solicitud,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, servicio.asignar(projectId, solicitud.userId()));
    }

    @DeleteMapping("/{userId}")
    public void revocar(
            @PathVariable UUID projectId, @PathVariable UUID userId,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, servicio.revocar(projectId, userId));
    }
}
