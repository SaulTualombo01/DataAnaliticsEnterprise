package org.dataanaliticsent.contexto.internal.aplicacion;

import java.util.UUID;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.dataanaliticsent.identidad.IdentidadNoVerificadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuardiaPlataformaTests {
    private static final String PERMISO = "tc.organizations.read";

    private final UUID actorId = UUID.randomUUID();
    private IdentidadActualApi identidad;
    private AutorizacionApi autorizacion;
    private GuardiaPlataforma guardia;

    @BeforeEach
    void preparar() {
        identidad = mock(IdentidadActualApi.class);
        autorizacion = mock(AutorizacionApi.class);
        guardia = new GuardiaPlataforma(identidad, autorizacion);
    }

    @Test
    void exigeElPermisoDePlataformaParaElActorDeLaSesion() {
        when(identidad.usuarioId()).thenReturn(actorId);

        assertEquals(actorId, guardia.exigir(PERMISO));
        verify(autorizacion).exigir(actorId, SolicitudAcceso.plataforma(PERMISO));
    }

    @Test
    void propagaElAccesoDenegado() {
        when(identidad.usuarioId()).thenReturn(actorId);
        doThrow(new AccesoDenegadoException(Motivo.PERMISO_NO_CONCEDIDO))
                .when(autorizacion).exigir(actorId, SolicitudAcceso.plataforma(PERMISO));

        assertThrows(AccesoDenegadoException.class, () -> guardia.exigir(PERMISO));
    }

    @Test
    void sinIdentidadNoConsultaPermisos() {
        when(identidad.usuarioId()).thenThrow(mock(IdentidadNoVerificadaException.class));

        assertThrows(IdentidadNoVerificadaException.class, () -> guardia.exigir(PERMISO));
        verifyNoInteractions(autorizacion);
    }
}
