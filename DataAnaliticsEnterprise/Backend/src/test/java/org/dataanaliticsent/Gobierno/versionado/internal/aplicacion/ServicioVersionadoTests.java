package org.dataanaliticsent.Gobierno.versionado.internal.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.versionado.ConflictoVersionException;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Previa;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.ResultadoVersion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.SolicitudVersion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Version;
import org.dataanaliticsent.Gobierno.comun.DoblesGobierno;
import org.junit.jupiter.api.Test;

class ServicioVersionadoTests {
    private static final UUID POLITICA = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private final DoblesGobierno.Versiones repositorio = new DoblesGobierno.Versiones();
    private final DoblesGobierno.Autorizacion autorizacion = new DoblesGobierno.Autorizacion();
    private final ServicioVersionado servicio =
            new ServicioVersionado(repositorio, DoblesGobierno.identidad(), autorizacion, DoblesGobierno.RELOJ);

    private SolicitudVersion solicitud(Map<String, Object> estado, Previa previa) {
        return new SolicitudVersion(DoblesGobierno.PROYECTO, "gobierno.politica", POLITICA, Actor.usuario(DoblesGobierno.ACTOR),
                "Cambio de prueba", estado, null, previa);
    }

    @Test
    void laPrimeraVersionEsLaUnoSinPrevia() {
        ResultadoVersion r = servicio.versionar(solicitud(Map.of("estado", "DRAFT"), Previa.ninguna()));

        assertTrue(r.creada());
        assertEquals(1, r.number());
        assertNull(repositorio.versiones.getFirst().previousVersionId());
    }

    @Test
    void guardarElMismoEstadoNoCreaOtraVersion() {
        ResultadoVersion v1 = servicio.versionar(solicitud(Map.of("estado", "DRAFT", "nombre", "PII"), null));
        ResultadoVersion repetida = servicio.versionar(solicitud(Map.of("nombre", "PII", "estado", "DRAFT"), null));

        assertFalse(repetida.creada());
        assertEquals(v1.versionId(), repetida.versionId());
        assertEquals(1, repositorio.versiones.size());
    }

    @Test
    void unCambioEfectivoEnlazaConLaVersionAnterior() {
        ResultadoVersion v1 = servicio.versionar(solicitud(Map.of("estado", "DRAFT"), Previa.ninguna()));
        ResultadoVersion v2 = servicio.versionar(solicitud(Map.of("estado", "ACTIVE"), Previa.de(v1.versionId())));

        assertTrue(v2.creada());
        assertEquals(2, v2.number());
        assertEquals(v1.versionId(), repositorio.versiones.get(1).previousVersionId());
    }

    @Test
    void unaPreviaObsoletaProduceConflictoSinGuardar() {
        ResultadoVersion v1 = servicio.versionar(solicitud(Map.of("estado", "DRAFT"), Previa.ninguna()));
        servicio.versionar(solicitud(Map.of("estado", "ACTIVE"), Previa.de(v1.versionId())));

        assertThrows(ConflictoVersionException.class,
                () -> servicio.versionar(solicitud(Map.of("estado", "INACTIVE"), Previa.de(v1.versionId()))));
        assertThrows(ConflictoVersionException.class,
                () -> servicio.versionar(solicitud(Map.of("estado", "INACTIVE"), Previa.ninguna())));
        assertEquals(2, repositorio.versiones.size());
    }

    @Test
    void noVersionaSecretosNiActoresAnonimos() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.versionar(solicitud(Map.of("token", "abc"), null)));
        assertThrows(IllegalArgumentException.class, () -> new SolicitudVersion(DoblesGobierno.PROYECTO, "gobierno.politica",
                POLITICA, Actor.anonimo(), "motivo", Map.of(), null, null));
        assertTrue(repositorio.versiones.isEmpty());
    }

    @Test
    void consultarHistorialExigePermisoYNoAlteraLaVigente() {
        ResultadoVersion v1 = servicio.versionar(solicitud(Map.of("estado", "DRAFT"), Previa.ninguna()));
        servicio.versionar(solicitud(Map.of("estado", "ACTIVE"), Previa.de(v1.versionId())));

        assertThrows(AccesoDenegadoGobiernoException.class,
                () -> servicio.historial(DoblesGobierno.PROYECTO, "gobierno.politica", POLITICA, 1, 25));

        autorizacion.conceder(PermisosGobierno.HISTORIAL_LEER, DoblesGobierno.PROYECTO);
        Pagina<Version> historial = servicio.historial(DoblesGobierno.PROYECTO, "gobierno.politica", POLITICA, 1, 1);
        Version antigua = servicio.version(DoblesGobierno.PROYECTO, "gobierno.politica", POLITICA, v1.versionId()).orElseThrow();

        assertEquals(2, historial.items().getFirst().number());
        assertTrue(historial.hasNext());
        assertEquals(1, antigua.number());
        assertEquals(2, repositorio.ultima(DoblesGobierno.PROYECTO, "gobierno.politica", POLITICA).orElseThrow().number());
    }

    @Test
    void compararDosVersionesDelMismoRecurso() {
        ResultadoVersion v1 = servicio.versionar(solicitud(Map.of("estado", "DRAFT", "nombre", "PII"), Previa.ninguna()));
        ResultadoVersion v2 = servicio.versionar(solicitud(Map.of("estado", "ACTIVE", "nombre", "PII"), Previa.de(v1.versionId())));
        autorizacion.conceder(PermisosGobierno.HISTORIAL_LEER, DoblesGobierno.PROYECTO);

        var comparacion = servicio.comparar(DoblesGobierno.PROYECTO, "gobierno.politica", POLITICA, v1.versionId(), v2.versionId());

        assertEquals(1, comparacion.cambios().size());
        assertEquals("estado", comparacion.cambios().getFirst().campo());
        assertEquals("\"DRAFT\"", comparacion.cambios().getFirst().valorAnterior());
        assertTrue(servicio.comparar(DoblesGobierno.PROYECTO, "gobierno.politica", POLITICA, v2.versionId(), v2.versionId())
                .cambios().isEmpty());
        assertThrows(org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException.class,
                () -> servicio.comparar(DoblesGobierno.PROYECTO, "gobierno.politica", UUID.randomUUID(),
                        v1.versionId(), v2.versionId()));
    }
}
