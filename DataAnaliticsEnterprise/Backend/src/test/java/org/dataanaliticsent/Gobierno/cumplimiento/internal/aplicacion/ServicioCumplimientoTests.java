package org.dataanaliticsent.Gobierno.cumplimiento.internal.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.auditoria.internal.aplicacion.ServicioAuditoria;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.DoblesGobierno;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.PuertoCalidad.EstadoCalidad;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.EstadoCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.dataanaliticsent.Gobierno.fichas.ConflictoFichaException;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.DatosFicha;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.fichas.internal.aplicacion.ServicioFichas;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.NuevaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.TipoRegla;
import org.dataanaliticsent.Gobierno.politicas.internal.aplicacion.ServicioPoliticas;
import org.dataanaliticsent.Gobierno.versionado.internal.aplicacion.ServicioVersionado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Flujo completo de la fase 3 con los servicios reales sobre repositorios en memoria. */
class ServicioCumplimientoTests {
    private static final UUID P = DoblesGobierno.PROYECTO;
    private static final UUID CLIENTES = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID RESPONSABLE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    private final DoblesGobierno.Auditoria eventos = new DoblesGobierno.Auditoria();
    private final DoblesGobierno.Versiones versiones = new DoblesGobierno.Versiones();
    private final DoblesGobierno.Autorizacion autorizacion = new DoblesGobierno.Autorizacion();
    private final DoblesGobierno.Catalogo catalogo = new DoblesGobierno.Catalogo().registrar(P, CLIENTES);
    private final DoblesGobierno.Evaluaciones evaluaciones = new DoblesGobierno.Evaluaciones();
    private Optional<EstadoCalidad> calidadM2 = Optional.empty();

    private final ServicioAuditoria auditoria = new ServicioAuditoria(eventos, DoblesGobierno.identidad(), autorizacion);
    private final ServicioVersionado versionado = new ServicioVersionado(versiones, DoblesGobierno.identidad(), autorizacion);
    private final ServicioPoliticas politicas = new ServicioPoliticas(new DoblesGobierno.Politicas(), auditoria, versionado,
            DoblesGobierno.identidad(), autorizacion, catalogo, DoblesGobierno.sinTransaccion());
    private final ServicioFichas fichas = new ServicioFichas(new DoblesGobierno.Fichas(), auditoria, versionado,
            DoblesGobierno.identidad(), autorizacion, catalogo, DoblesGobierno.sinTransaccion());
    private final ServicioCumplimiento cumplimiento = new ServicioCumplimiento(evaluaciones, politicas, fichas, auditoria,
            DoblesGobierno.identidad(), autorizacion, catalogo, (proyecto, activo) -> calidadM2,
            DoblesGobierno.sinTransaccion(), DoblesGobierno.RELOJ);

    @BeforeEach
    void concederPermisosDeGobierno() {
        for (String permiso : List.of(PermisosGobierno.POLITICAS_LEER, PermisosGobierno.POLITICAS_CREAR,
                PermisosGobierno.POLITICAS_CICLO_VIDA, PermisosGobierno.POLITICAS_ACTIVOS, PermisosGobierno.POLITICAS_REGLAS,
                PermisosGobierno.FICHAS_LEER, PermisosGobierno.FICHAS_ACTUALIZAR,
                PermisosGobierno.CUMPLIMIENTO_LEER, PermisosGobierno.CUMPLIMIENTO_EVALUAR)) {
            autorizacion.conceder(permiso, P);
        }
    }

    /** Política activa "Datos personales" sobre clientes: responsable + clasificación CONFIDENCIAL. */
    private Politica politicaPii() {
        Politica p = politicas.crear(new NuevaPolitica(P, "Datos personales", null, CategoriaPolitica.PRIVACIDAD, RESPONSABLE));
        p = politicas.agregarRegla(P, p.id(), p.revision(), TipoRegla.REQUIERE_RESPONSABLE, null, "Responsable obligatorio");
        p = politicas.agregarRegla(P, p.id(), p.revision(), TipoRegla.CLASIFICACION_MINIMA, "CONFIDENCIAL", "PII sensible");
        p = politicas.asociarActivo(P, p.id(), p.revision(), CLIENTES, "Clientes tiene PII");
        return politicas.cambiarEstado(P, p.id(), p.revision(), EstadoPolitica.ACTIVE, "Aprobada");
    }

    @Test
    void sinPoliticasActivasNoAplica() {
        Evaluacion e = cumplimiento.evaluar(P, CLIENTES);
        assertEquals(EstadoCumplimiento.NOT_APPLICABLE, e.estado());
        assertTrue(e.politicas().isEmpty());
    }

    @Test
    void sinFichaQuedaIndeterminadoYConFichaCorrectaCumple() {
        Politica pii = politicaPii();

        Evaluacion sinFicha = cumplimiento.evaluar(P, CLIENTES);
        assertEquals(EstadoCumplimiento.INDETERMINATE, sinFicha.estado());
        assertNull(sinFicha.profileVersionId());

        Ficha ficha = fichas.guardar(new DatosFicha(P, CLIENTES, null, RESPONSABLE, Clasificacion.CONFIDENCIAL, "Alta de ficha"));
        Evaluacion conFicha = cumplimiento.evaluar(P, CLIENTES);

        assertEquals(EstadoCumplimiento.COMPLIANT, conFicha.estado());
        assertEquals(ficha.currentVersionId(), conFicha.profileVersionId());
        assertEquals(pii.currentVersionId(), conFicha.politicas().getFirst().policyVersionId());
        assertEquals(conFicha, cumplimiento.ultima(P, CLIENTES).orElseThrow());
        assertEquals(2, cumplimiento.historial(P, CLIENTES, 1, 25).items().size());
    }

    @Test
    void clasificacionInsuficienteIncumple() {
        politicaPii();
        fichas.guardar(new DatosFicha(P, CLIENTES, null, RESPONSABLE, Clasificacion.INTERNA, "Alta"));

        Evaluacion e = cumplimiento.evaluar(P, CLIENTES);

        assertEquals(EstadoCumplimiento.NON_COMPLIANT, e.estado());
        assertTrue(e.politicas().getFirst().reglas().stream()
                .anyMatch(r -> "CLASIFICACION_INSUFICIENTE".equals(r.motivo())));
    }

    @Test
    void laReglaDeCalidadUsaElResultadoDeM2() {
        Politica p = politicas.crear(new NuevaPolitica(P, "Calidad mínima", null, CategoriaPolitica.CALIDAD, RESPONSABLE));
        p = politicas.agregarRegla(P, p.id(), 0, TipoRegla.CALIDAD_APROBADA, null, "Exigir aprobación de M2");
        p = politicas.asociarActivo(P, p.id(), 1, CLIENTES, "Clientes");
        politicas.cambiarEstado(P, p.id(), 2, EstadoPolitica.ACTIVE, "Aprobada");

        assertEquals(EstadoCumplimiento.INDETERMINATE, cumplimiento.evaluar(P, CLIENTES).estado());
        calidadM2 = Optional.of(EstadoCalidad.NO_APROBADO);
        assertEquals(EstadoCumplimiento.NON_COMPLIANT, cumplimiento.evaluar(P, CLIENTES).estado());
        calidadM2 = Optional.of(EstadoCalidad.APROBADO);
        assertEquals(EstadoCumplimiento.COMPLIANT, cumplimiento.evaluar(P, CLIENTES).estado());
    }

    @Test
    void cadaEvaluacionSeAudita() {
        Evaluacion e = cumplimiento.evaluar(P, CLIENTES);
        var evento = eventos.eventos.stream().filter(x -> x.id().equals(e.auditEventId())).findFirst().orElseThrow();

        assertEquals("m3.compliance.evaluate", evento.action());
        assertEquals(e.id(), evento.resourceId());
        assertTrue(evento.safeDetailsJson().contains("NOT_APPLICABLE"));
    }

    @Test
    void evaluarSinPermisoSeDeniegaYQuedaAuditado() {
        UUID otroProyecto = UUID.randomUUID();
        assertThrows(AccesoDenegadoGobiernoException.class, () -> cumplimiento.evaluar(otroProyecto, CLIENTES));
        assertEquals(Resultado.DENIED, eventos.eventos.getLast().outcome());
        assertTrue(evaluaciones.datos.isEmpty());
    }

    @Test
    void unActivoQueNoEstaEnElCatalogoNoSeEvalua() {
        assertThrows(RecursoNoEncontradoGobiernoException.class, () -> cumplimiento.evaluar(P, UUID.randomUUID()));
    }

    @Test
    void laFichaSeVersionaYRespetaLaRevision() {
        Ficha v0 = fichas.guardar(new DatosFicha(P, CLIENTES, null, RESPONSABLE, null, "Alta"));
        Ficha igual = fichas.guardar(new DatosFicha(P, CLIENTES, 0L, RESPONSABLE, null, "Sin cambios"));
        Ficha v1 = fichas.guardar(new DatosFicha(P, CLIENTES, 0L, RESPONSABLE, Clasificacion.RESTRINGIDA, "Clasificar"));

        assertEquals(v0, igual);
        assertEquals(1, v1.revision());
        assertThrows(ConflictoFichaException.class,
                () -> fichas.guardar(new DatosFicha(P, CLIENTES, 0L, null, null, "Revisión vieja")));
        assertThrows(ConflictoFichaException.class,
                () -> fichas.guardar(new DatosFicha(P, CLIENTES, null, null, null, "Crear de nuevo")));
        assertThrows(RecursoNoEncontradoGobiernoException.class,
                () -> fichas.guardar(new DatosFicha(P, UUID.randomUUID(), null, RESPONSABLE, null, "Activo inventado")));
        assertEquals(2, versiones.versiones.stream().filter(v -> v.resourceType().equals("gobierno.ficha_activo")).count());
    }
}
