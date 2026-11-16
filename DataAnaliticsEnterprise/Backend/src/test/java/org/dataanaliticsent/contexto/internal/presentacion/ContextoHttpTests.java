package org.dataanaliticsent.contexto.internal.presentacion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.contexto.internal.aplicacion.ServicioMembresias;
import org.dataanaliticsent.contexto.internal.aplicacion.ServicioOrganizaciones;
import org.dataanaliticsent.contexto.internal.aplicacion.ServicioProyectos;
import org.dataanaliticsent.contexto.internal.dominio.ConflictoContextoException;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.Membresia;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.Proyecto;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin contexto de Spring ni seguridad: verifica rutas, envoltura {data, meta} y errores problem+json. */
class ContextoHttpTests {
    private static final String BASE = "/api/v1/admin";
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-11-01T09:00:00Z");

    private final UUID organizacionId = UUID.randomUUID();
    private final UUID proyectoId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final Organizacion organizacion = new Organizacion(organizacionId, "Acme", Estado.ACTIVE, AHORA, AHORA);
    private final Proyecto proyecto = new Proyecto(proyectoId, organizacionId, "Ventas", Estado.ACTIVE, AHORA, AHORA);
    private ServicioOrganizaciones organizaciones;
    private ServicioProyectos proyectos;
    private ServicioMembresias membresias;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        organizaciones = mock(ServicioOrganizaciones.class);
        proyectos = mock(ServicioProyectos.class);
        membresias = mock(ServicioMembresias.class);
        ProblemasHttp problemas = new ProblemasHttp(JsonMapper.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(
                        new OrganizacionesController(organizaciones, problemas),
                        new ProyectosController(proyectos, problemas),
                        new MembresiasController(membresias, problemas))
                .setControllerAdvice(new ContextoErroresAdvice(problemas))
                .build();
    }

    @Test
    void listaOrganizacionesConPaginacionPorDefecto() throws Exception {
        when(organizaciones.listar(new Pagina(50, 0))).thenReturn(List.of(organizacion));

        mvc.perform(get(BASE + "/organizations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[0].id").value(organizacionId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.data[0].created_at").exists())
                .andExpect(jsonPath("$.meta.request_id").isNotEmpty());
    }

    @Test
    void creaUnaOrganizacionCon201() throws Exception {
        when(organizaciones.crear("Acme")).thenReturn(organizacion);

        mvc.perform(post(BASE + "/organizations").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Acme"))
                .andExpect(jsonPath("$.meta.request_id").isNotEmpty());
    }

    @Test
    void creaUnProyectoDentroDeLaOrganizacionDeLaRuta() throws Exception {
        when(proyectos.crear(organizacionId, "Ventas")).thenReturn(proyecto);

        mvc.perform(post(BASE + "/organizations/" + organizacionId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ventas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.organization_id").value(organizacionId.toString()));
    }

    @Test
    void renombraYCambiaElEstadoDeUnProyecto() throws Exception {
        when(proyectos.renombrar(proyectoId, "Ventas")).thenReturn(proyecto);
        when(proyectos.desactivar(proyectoId)).thenReturn(
                new Proyecto(proyectoId, organizacionId, "Ventas", Estado.INACTIVE, AHORA, AHORA));

        mvc.perform(patch(BASE + "/projects/" + proyectoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ventas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ventas"));
        mvc.perform(post(BASE + "/projects/" + proyectoId + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("inactive"));
    }

    @Test
    void asignaYRevocaMembresias() throws Exception {
        Membresia activa = new Membresia(
                UUID.randomUUID(), usuarioId, proyectoId, organizacionId, Estado.ACTIVE, UUID.randomUUID(), AHORA);
        Membresia revocada = new Membresia(
                activa.id(), usuarioId, proyectoId, organizacionId, Estado.INACTIVE, activa.assignedBy(), AHORA);
        when(membresias.asignar(proyectoId, usuarioId)).thenReturn(activa);
        when(membresias.revocar(proyectoId, usuarioId)).thenReturn(revocada);

        mvc.perform(post(BASE + "/projects/" + proyectoId + "/memberships")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"user_id\":\"" + usuarioId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(usuarioId.toString()))
                .andExpect(jsonPath("$.data.assigned_by").exists());
        mvc.perform(delete(BASE + "/projects/" + proyectoId + "/memberships/" + usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("inactive"));
    }

    @Test
    void unRecursoInexistenteDevuelve404EnProblemJson() throws Exception {
        when(organizaciones.obtener(organizacionId)).thenThrow(new RecursoNoEncontradoException());

        mvc.perform(get(BASE + "/organizations/" + organizacionId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.instance").value(BASE + "/organizations/" + organizacionId))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void unConflictoDevuelve409ConSuMotivo() throws Exception {
        when(proyectos.activar(proyectoId)).thenThrow(new ConflictoContextoException("La organización no está activa"));

        mvc.perform(post(BASE + "/projects/" + proyectoId + "/activate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("La organización no está activa"));
    }

    @Test
    void lasSolicitudesInvalidasDevuelven400SinLlegarAlServicio() throws Exception {
        mvc.perform(get(BASE + "/organizations?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(BASE + "/organizations?limit=abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/organizations/no-es-uuid"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/organizations").contentType(MediaType.APPLICATION_JSON).content("{mal json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        verifyNoInteractions(organizaciones);
    }

    @Test
    void unFalloDePersistenciaDevuelve503() throws Exception {
        when(organizaciones.listar(any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));

        mvc.perform(get(BASE + "/organizations"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONTEXT_UNAVAILABLE"));
    }
}
