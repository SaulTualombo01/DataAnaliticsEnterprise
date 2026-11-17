package org.dataanaliticsent.Gobierno.auditoria.internal.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.EventoNuevo;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Filtro;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.DoblesGobierno;
import org.junit.jupiter.api.Test;

class ServicioAuditoriaTests {
    private final DoblesGobierno.Auditoria repositorio = new DoblesGobierno.Auditoria();
    private final DoblesGobierno.Autorizacion autorizacion = new DoblesGobierno.Autorizacion();
    private final ServicioAuditoria servicio =
            new ServicioAuditoria(repositorio, DoblesGobierno.identidad(), autorizacion, DoblesGobierno.RELOJ);

    private EventoNuevo evento(String accion, Map<String, Object> detalles) {
        return new EventoNuevo(Actor.usuario(DoblesGobierno.ACTOR), null, DoblesGobierno.PROYECTO, "gobierno.politica",
                UUID.randomUUID(), accion, Resultado.SUCCESS, UUID.randomUUID(), detalles);
    }

    @Test
    void registraElEventoConIdYFechaDelServidorYDetallesCanonicos() {
        UUID id = servicio.registrar(evento("m3.policies.create", Map.of("nombre", "PII", "campos", 2)));

        Evento guardado = repositorio.eventos.getFirst();
        assertEquals(id, guardado.id());
        assertEquals(DoblesGobierno.RELOJ.instant(), guardado.occurredAt());
        assertEquals("{\"campos\":2,\"nombre\":\"PII\"}", guardado.safeDetailsJson());
    }

    @Test
    void rechazaCodigosInvalidosYDetallesConSecretos() {
        assertThrows(IllegalArgumentException.class, () -> servicio.registrar(evento("CrearPolitica", Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(evento("m3.policies.create", Map.of("conexion", Map.of("password", "x")))));
        assertTrue(repositorio.eventos.isEmpty());
    }

    @Test
    void unActorAnonimoNoLlevaIdentificador() {
        assertThrows(IllegalArgumentException.class,
                () -> new Actor(org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.TipoActor.ANONYMOUS, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> new Actor(org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.TipoActor.USER, null));
    }

    @Test
    void consultarSinPermisoSeDeniega() {
        servicio.registrar(evento("m3.policies.create", Map.of()));
        assertThrows(AccesoDenegadoGobiernoException.class,
                () -> servicio.consultar(Filtro.delProyecto(DoblesGobierno.PROYECTO), 1, 25));
    }

    @Test
    void conPermisoPaginaSinInventarTotal() {
        autorizacion.conceder(PermisosGobierno.AUDITORIA_LEER, DoblesGobierno.PROYECTO);
        for (int i = 0; i < 3; i++) servicio.registrar(evento("m3.policies.update", Map.of()));

        Pagina<Evento> primera = servicio.consultar(Filtro.delProyecto(DoblesGobierno.PROYECTO), 1, 2);
        Pagina<Evento> segunda = servicio.consultar(Filtro.delProyecto(DoblesGobierno.PROYECTO), 2, 2);

        assertEquals(2, primera.items().size());
        assertTrue(primera.hasNext());
        assertEquals(1, segunda.items().size());
        assertFalse(segunda.hasNext());
    }

    @Test
    void elPermisoDeOtroProyectoNoSirve() {
        autorizacion.conceder(PermisosGobierno.AUDITORIA_LEER, UUID.randomUUID());
        assertThrows(AccesoDenegadoGobiernoException.class,
                () -> servicio.consultar(Filtro.delProyecto(DoblesGobierno.PROYECTO), 1, 25));
    }

    @Test
    void parametrosDePaginaFueraDeRangoSeRechazan() {
        autorizacion.conceder(PermisosGobierno.AUDITORIA_LEER, DoblesGobierno.PROYECTO);
        assertThrows(IllegalArgumentException.class, () -> servicio.consultar(Filtro.delProyecto(DoblesGobierno.PROYECTO), 0, 25));
        assertThrows(IllegalArgumentException.class, () -> servicio.consultar(Filtro.delProyecto(DoblesGobierno.PROYECTO), 1, 101));
    }
}
