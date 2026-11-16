package org.dataanaliticsent.contexto.internal.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.contexto.internal.dominio.RepositorioContexto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServicioOrganizacionesTests {
    private static final OffsetDateTime CREADA = OffsetDateTime.parse("2026-11-01T09:00:00Z");

    private final UUID id = UUID.randomUUID();
    private final Organizacion activa = new Organizacion(id, "Acme", Estado.ACTIVE, CREADA, CREADA);
    private GuardiaPlataforma guardia;
    private RepositorioContexto repositorio;
    private ServicioOrganizaciones servicio;

    @BeforeEach
    void preparar() {
        guardia = mock(GuardiaPlataforma.class);
        repositorio = mock(RepositorioContexto.class);
        servicio = new ServicioOrganizaciones(guardia, repositorio);
    }

    @Test
    void listaConElPermisoDeLectura() {
        Pagina pagina = new Pagina(10, 20);
        when(repositorio.organizaciones(pagina)).thenReturn(List.of(activa));

        assertEquals(List.of(activa), servicio.listar(pagina));
        verify(guardia).exigir("tc.organizations.read");
    }

    @Test
    void creaConElNombreNormalizado() {
        when(repositorio.crearOrganizacion(any(), eq("Acme"))).thenReturn(activa);

        assertEquals(activa, servicio.crear("  Acme  "));
        verify(guardia).exigir("tc.organizations.create");
    }

    @Test
    void rechazaNombresVaciosODemasiadoLargos() {
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("   "));
        assertThrows(IllegalArgumentException.class, () -> servicio.crear(null));
        assertThrows(IllegalArgumentException.class, () -> servicio.renombrar(id, "x".repeat(201)));
        verifyNoInteractions(repositorio);
    }

    @Test
    void renombraYExigeElPermisoDeActualizacion() {
        Organizacion renombrada = new Organizacion(id, "Nueva", Estado.ACTIVE, CREADA, CREADA);
        when(repositorio.renombrarOrganizacion(id, "Nueva")).thenReturn(Optional.of(renombrada));

        assertEquals(renombrada, servicio.renombrar(id, "Nueva"));
        verify(guardia).exigir("tc.organizations.update");
    }

    @Test
    void activaYDesactivaConPermisosDistintos() {
        Organizacion inactiva = new Organizacion(id, "Acme", Estado.INACTIVE, CREADA, CREADA);
        when(repositorio.cambiarEstadoOrganizacion(id, Estado.INACTIVE)).thenReturn(Optional.of(inactiva));
        when(repositorio.cambiarEstadoOrganizacion(id, Estado.ACTIVE)).thenReturn(Optional.of(activa));

        assertEquals(Estado.INACTIVE, servicio.desactivar(id).status());
        assertEquals(Estado.ACTIVE, servicio.activar(id).status());
        verify(guardia).exigir("tc.organizations.deactivate");
        verify(guardia).exigir("tc.organizations.activate");
    }

    @Test
    void unaOrganizacionInexistenteNoSeEncuentra() {
        when(repositorio.organizacion(id)).thenReturn(Optional.empty());
        when(repositorio.renombrarOrganizacion(id, "Nueva")).thenReturn(Optional.empty());
        when(repositorio.cambiarEstadoOrganizacion(id, Estado.ACTIVE)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(id));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.renombrar(id, "Nueva"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.activar(id));
    }

    @Test
    void sinPermisoNoSeTocaElRepositorio() {
        when(guardia.exigir(any())).thenThrow(new AccesoDenegadoException(Motivo.PERMISO_NO_CONCEDIDO));

        assertThrows(AccesoDenegadoException.class, () -> servicio.listar(new Pagina(50, 0)));
        assertThrows(AccesoDenegadoException.class, () -> servicio.obtener(id));
        assertThrows(AccesoDenegadoException.class, () -> servicio.crear("Acme"));
        assertThrows(AccesoDenegadoException.class, () -> servicio.desactivar(id));
        verifyNoInteractions(repositorio);
    }
}
