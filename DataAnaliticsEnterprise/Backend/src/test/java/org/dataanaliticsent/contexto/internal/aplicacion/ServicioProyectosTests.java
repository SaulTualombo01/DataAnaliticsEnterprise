package org.dataanaliticsent.contexto.internal.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.contexto.internal.dominio.ConflictoContextoException;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.Proyecto;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.contexto.internal.dominio.RepositorioContexto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServicioProyectosTests {
    private static final OffsetDateTime CREADO = OffsetDateTime.parse("2026-11-01T09:00:00Z");

    private final UUID organizacionId = UUID.randomUUID();
    private final UUID proyectoId = UUID.randomUUID();
    private final Proyecto proyecto = new Proyecto(proyectoId, organizacionId, "Ventas", Estado.ACTIVE, CREADO, CREADO);
    private GuardiaPlataforma guardia;
    private RepositorioContexto repositorio;
    private ServicioProyectos servicio;

    @BeforeEach
    void preparar() {
        guardia = mock(GuardiaPlataforma.class);
        repositorio = mock(RepositorioContexto.class);
        servicio = new ServicioProyectos(guardia, repositorio);
    }

    @Test
    void listaLosProyectosDeUnaOrganizacionExistente() {
        Pagina pagina = new Pagina(50, 0);
        organizacionCon(Estado.ACTIVE);
        when(repositorio.proyectos(organizacionId, pagina)).thenReturn(List.of(proyecto));

        assertEquals(List.of(proyecto), servicio.listar(organizacionId, pagina));
        verify(guardia).exigir("tc.projects.read");
    }

    @Test
    void noListaProyectosDeUnaOrganizacionInexistente() {
        when(repositorio.organizacion(organizacionId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.listar(organizacionId, new Pagina(50, 0)));
        verify(repositorio, never()).proyectos(any(), any());
    }

    @Test
    void creaElProyectoEnUnaOrganizacionActiva() {
        organizacionCon(Estado.ACTIVE);
        when(repositorio.crearProyecto(any(), eq(organizacionId), eq("Ventas"))).thenReturn(proyecto);

        assertEquals(proyecto, servicio.crear(organizacionId, " Ventas "));
        verify(guardia).exigir("tc.projects.create");
    }

    @Test
    void noCreaProyectosEnUnaOrganizacionInactiva() {
        organizacionCon(Estado.INACTIVE);

        assertThrows(ConflictoContextoException.class, () -> servicio.crear(organizacionId, "Ventas"));
        verify(repositorio, never()).crearProyecto(any(), any(), any());
    }

    @Test
    void unNombreDuplicadoEsUnConflicto() {
        organizacionCon(Estado.ACTIVE);
        when(repositorio.crearProyecto(any(), any(), any())).thenThrow(new DuplicateKeyException("duplicado"));
        when(repositorio.renombrarProyecto(proyectoId, "Ventas")).thenThrow(new DuplicateKeyException("duplicado"));

        assertThrows(ConflictoContextoException.class, () -> servicio.crear(organizacionId, "Ventas"));
        assertThrows(ConflictoContextoException.class, () -> servicio.renombrar(proyectoId, "Ventas"));
    }

    @Test
    void soloReactivaDentroDeUnaOrganizacionActiva() {
        when(repositorio.proyecto(proyectoId)).thenReturn(Optional.of(proyecto));
        organizacionCon(Estado.INACTIVE);

        assertThrows(ConflictoContextoException.class, () -> servicio.activar(proyectoId));
        verify(repositorio, never()).cambiarEstadoProyecto(any(), any());

        organizacionCon(Estado.ACTIVE);
        when(repositorio.cambiarEstadoProyecto(proyectoId, Estado.ACTIVE)).thenReturn(Optional.of(proyecto));

        assertEquals(proyecto, servicio.activar(proyectoId));
        verify(guardia, times(2)).exigir("tc.projects.activate");
    }

    @Test
    void desactivaSinConsultarLaOrganizacion() {
        Proyecto inactivo = new Proyecto(proyectoId, organizacionId, "Ventas", Estado.INACTIVE, CREADO, CREADO);
        when(repositorio.cambiarEstadoProyecto(proyectoId, Estado.INACTIVE)).thenReturn(Optional.of(inactivo));

        assertEquals(Estado.INACTIVE, servicio.desactivar(proyectoId).status());
        verify(guardia).exigir("tc.projects.deactivate");
        verify(repositorio, never()).organizacion(any());
    }

    @Test
    void unProyectoInexistenteNoSeEncuentra() {
        when(repositorio.proyecto(proyectoId)).thenReturn(Optional.empty());
        when(repositorio.renombrarProyecto(proyectoId, "Nuevo")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(proyectoId));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.renombrar(proyectoId, "Nuevo"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.activar(proyectoId));
    }

    @Test
    void sinPermisoNoSeTocaElRepositorio() {
        when(guardia.exigir(any())).thenThrow(new AccesoDenegadoException(Motivo.PERMISO_NO_CONCEDIDO));

        assertThrows(AccesoDenegadoException.class, () -> servicio.listar(organizacionId, new Pagina(50, 0)));
        assertThrows(AccesoDenegadoException.class, () -> servicio.crear(organizacionId, "Ventas"));
        assertThrows(AccesoDenegadoException.class, () -> servicio.activar(proyectoId));
        verifyNoInteractions(repositorio);
    }

    private void organizacionCon(Estado estado) {
        when(repositorio.organizacion(organizacionId))
                .thenReturn(Optional.of(new Organizacion(organizacionId, "Acme", estado, CREADO, CREADO)));
    }
}
