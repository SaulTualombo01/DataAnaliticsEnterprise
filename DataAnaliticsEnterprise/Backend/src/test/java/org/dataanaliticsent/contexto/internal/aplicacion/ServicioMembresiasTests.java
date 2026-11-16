package org.dataanaliticsent.contexto.internal.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.contexto.internal.dominio.ConflictoContextoException;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.Membresia;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.Proyecto;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.contexto.internal.dominio.RepositorioContexto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServicioMembresiasTests {
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-11-01T09:00:00Z");

    private final UUID actorId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID organizacionId = UUID.randomUUID();
    private final UUID proyectoId = UUID.randomUUID();
    private final Membresia membresia = new Membresia(
            UUID.randomUUID(), usuarioId, proyectoId, organizacionId, Estado.ACTIVE, actorId, AHORA);
    private GuardiaPlataforma guardia;
    private RepositorioContexto repositorio;
    private ServicioMembresias servicio;

    @BeforeEach
    void preparar() {
        guardia = mock(GuardiaPlataforma.class);
        repositorio = mock(RepositorioContexto.class);
        servicio = new ServicioMembresias(guardia, repositorio);
        when(guardia.exigir(any())).thenReturn(actorId);
    }

    @Test
    void vinculaLaCuentaRegistrandoAlActorAutorizado() {
        Proyecto proyecto = contexto(Estado.ACTIVE, Estado.ACTIVE);
        when(repositorio.vincular(usuarioId, proyecto, actorId)).thenReturn(membresia);

        assertEquals(membresia, servicio.asignar(proyectoId, usuarioId));
        verify(guardia).exigir("tc.memberships.assign");
    }

    @Test
    void noVinculaEnUnProyectoOUnaOrganizacionInactivos() {
        contexto(Estado.INACTIVE, Estado.ACTIVE);
        assertThrows(ConflictoContextoException.class, () -> servicio.asignar(proyectoId, usuarioId));

        contexto(Estado.ACTIVE, Estado.INACTIVE);
        assertThrows(ConflictoContextoException.class, () -> servicio.asignar(proyectoId, usuarioId));

        verify(repositorio, never()).vincular(any(), any(), any());
    }

    @Test
    void unaCuentaInexistenteEsUnaSolicitudInvalida() {
        contexto(Estado.ACTIVE, Estado.ACTIVE);
        when(repositorio.vincular(any(), any(), any())).thenThrow(new DataIntegrityViolationException("fk"));

        assertThrows(IllegalArgumentException.class, () -> servicio.asignar(proyectoId, usuarioId));
        assertThrows(IllegalArgumentException.class, () -> servicio.asignar(proyectoId, null));
    }

    @Test
    void noVinculaEnUnProyectoInexistente() {
        when(repositorio.proyecto(proyectoId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.asignar(proyectoId, usuarioId));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.listar(proyectoId, new Pagina(50, 0)));
    }

    @Test
    void listaLasMembresiasDelProyecto() {
        Pagina pagina = new Pagina(50, 0);
        contexto(Estado.ACTIVE, Estado.ACTIVE);
        when(repositorio.membresias(proyectoId, pagina)).thenReturn(List.of(membresia));

        assertEquals(List.of(membresia), servicio.listar(proyectoId, pagina));
        verify(guardia).exigir("tc.memberships.read");
    }

    @Test
    void revocaDejandoLaMembresiaInactiva() {
        Membresia revocada = new Membresia(
                membresia.id(), usuarioId, proyectoId, organizacionId, Estado.INACTIVE, actorId, AHORA);
        when(repositorio.revocar(usuarioId, proyectoId)).thenReturn(Optional.of(revocada));

        assertEquals(Estado.INACTIVE, servicio.revocar(proyectoId, usuarioId).status());
        verify(guardia).exigir("tc.memberships.revoke");
    }

    @Test
    void revocarUnaMembresiaInexistenteNoSeEncuentra() {
        when(repositorio.revocar(usuarioId, proyectoId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.revocar(proyectoId, usuarioId));
    }

    @Test
    void sinPermisoNoSeTocaElRepositorio() {
        when(guardia.exigir(any())).thenThrow(new AccesoDenegadoException(Motivo.PERMISO_NO_CONCEDIDO));

        assertThrows(AccesoDenegadoException.class, () -> servicio.listar(proyectoId, new Pagina(50, 0)));
        assertThrows(AccesoDenegadoException.class, () -> servicio.asignar(proyectoId, usuarioId));
        assertThrows(AccesoDenegadoException.class, () -> servicio.revocar(proyectoId, usuarioId));
        verifyNoInteractions(repositorio);
    }

    private Proyecto contexto(Estado estadoProyecto, Estado estadoOrganizacion) {
        Proyecto proyecto = new Proyecto(proyectoId, organizacionId, "Ventas", estadoProyecto, AHORA, AHORA);
        when(repositorio.proyecto(proyectoId)).thenReturn(Optional.of(proyecto));
        when(repositorio.organizacion(organizacionId))
                .thenReturn(Optional.of(new Organizacion(organizacionId, "Acme", estadoOrganizacion, AHORA, AHORA)));
        return proyecto;
    }
}
