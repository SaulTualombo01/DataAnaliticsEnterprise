package org.dataanaliticsent.contexto.internal.presentacion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.dataanaliticsent.contexto.internal.aplicacion.ServicioProyectos;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class ProyectosController {
    private final ServicioProyectos servicio;
    private final ProblemasHttp salida;

    public ProyectosController(ServicioProyectos servicio, ProblemasHttp salida) {
        this.servicio = servicio;
        this.salida = salida;
    }

    @GetMapping("/organizations/{organizationId}/projects")
    public void listar(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, servicio.listar(organizationId, new Pagina(limit, offset)));
    }

    @PostMapping("/organizations/{organizationId}/projects")
    public void crear(
            @PathVariable UUID organizationId, @RequestBody SolicitudNombre solicitud,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        var creado = servicio.crear(organizationId, solicitud.name());
        response.setStatus(HttpServletResponse.SC_CREATED);
        salida.datos(request, response, creado);
    }

    @GetMapping("/projects/{projectId}")
    public void obtener(@PathVariable UUID projectId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        salida.datos(request, response, servicio.obtener(projectId));
    }

    @PatchMapping("/projects/{projectId}")
    public void renombrar(
            @PathVariable UUID projectId, @RequestBody SolicitudNombre solicitud,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, servicio.renombrar(projectId, solicitud.name()));
    }

    @PostMapping("/projects/{projectId}/activate")
    public void activar(@PathVariable UUID projectId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        salida.datos(request, response, servicio.activar(projectId));
    }

    @PostMapping("/projects/{projectId}/deactivate")
    public void desactivar(@PathVariable UUID projectId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        salida.datos(request, response, servicio.desactivar(projectId));
    }
}
