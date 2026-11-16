package org.dataanaliticsent.contexto.internal.presentacion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.dataanaliticsent.contexto.internal.aplicacion.ServicioOrganizaciones;
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
@RequestMapping("/api/v1/admin/organizations")
public class OrganizacionesController {
    private final ServicioOrganizaciones servicio;
    private final ProblemasHttp salida;

    public OrganizacionesController(ServicioOrganizaciones servicio, ProblemasHttp salida) {
        this.servicio = servicio;
        this.salida = salida;
    }

    @GetMapping
    public void listar(
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, servicio.listar(new Pagina(limit, offset)));
    }

    @PostMapping
    public void crear(@RequestBody SolicitudNombre solicitud, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        var creada = servicio.crear(solicitud.name());
        response.setStatus(HttpServletResponse.SC_CREATED);
        salida.datos(request, response, creada);
    }

    @GetMapping("/{organizationId}")
    public void obtener(@PathVariable UUID organizationId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        salida.datos(request, response, servicio.obtener(organizationId));
    }

    @PatchMapping("/{organizationId}")
    public void renombrar(
            @PathVariable UUID organizationId, @RequestBody SolicitudNombre solicitud,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        salida.datos(request, response, servicio.renombrar(organizationId, solicitud.name()));
    }

    @PostMapping("/{organizationId}/activate")
    public void activar(@PathVariable UUID organizationId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        salida.datos(request, response, servicio.activar(organizationId));
    }

    @PostMapping("/{organizationId}/deactivate")
    public void desactivar(@PathVariable UUID organizationId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        salida.datos(request, response, servicio.desactivar(organizationId));
    }
}
