package org.dataanaliticsent.Gobierno.historial.internal.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.internal.aplicacion.ServicioAuditoria;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.DoblesGobierno;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.aplicacion.ServicioCumplimiento;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.DatosFicha;
import org.dataanaliticsent.Gobierno.fichas.internal.aplicacion.ServicioFichas;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.EntradaCronologia;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.ResumenActivo;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.Seccion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.NuevaRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;
import org.dataanaliticsent.Gobierno.linaje.internal.aplicacion.ServicioLinaje;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.NuevaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.internal.aplicacion.ServicioPoliticas;
import org.dataanaliticsent.Gobierno.versionado.internal.aplicacion.ServicioVersionado;
import org.junit.jupiter.api.Test;

class ServicioHistorialTests {
    private static final UUID P = DoblesGobierno.PROYECTO;
    private static final UUID CLIENTES = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID VENTAS = UUID.fromString("00000000-0000-4000-8000-0000000000d2");

    private final DoblesGobierno.Autorizacion autorizacion = new DoblesGobierno.Autorizacion();
    private final DoblesGobierno.Catalogo catalogo = new DoblesGobierno.Catalogo().registrar(P, CLIENTES).registrar(P, VENTAS);
    private final ServicioAuditoria auditoria = new ServicioAuditoria(new DoblesGobierno.Auditoria(), DoblesGobierno.identidad(), autorizacion);
    private final ServicioVersionado versionado = new ServicioVersionado(new DoblesGobierno.Versiones(), DoblesGobierno.identidad(), autorizacion);
    private final ServicioPoliticas politicas = new ServicioPoliticas(new DoblesGobierno.Politicas(), auditoria, versionado,
            DoblesGobierno.identidad(), autorizacion, catalogo, DoblesGobierno.sinTransaccion());
    private final ServicioFichas fichas = new ServicioFichas(new DoblesGobierno.Fichas(), auditoria, versionado,
            DoblesGobierno.identidad(), autorizacion, catalogo, DoblesGobierno.sinTransaccion());
    private final ServicioCumplimiento cumplimiento = new ServicioCumplimiento(new DoblesGobierno.Evaluaciones(), politicas,
            fichas, auditoria, DoblesGobierno.identidad(), autorizacion, catalogo, (p, a) -> java.util.Optional.empty(),
            DoblesGobierno.sinTransaccion());
    private final ServicioLinaje linaje = new ServicioLinaje(new DoblesGobierno.Linaje(), auditoria, versionado,
            DoblesGobierno.identidad(), autorizacion, catalogo, DoblesGobierno.sinTransaccion());
    private final EntradaCronologia entrada = new EntradaCronologia(null, UUID.randomUUID());
    private final ServicioHistorial historial = new ServicioHistorial((p, a, limite, desplazamiento) -> List.of(entrada),
            fichas, politicas, cumplimiento, linaje, DoblesGobierno.identidad(), autorizacion, catalogo);

    private void conceder(String... permisos) {
        for (String permiso : permisos) autorizacion.conceder(permiso, P);
    }

    private void datosDeClientes() {
        conceder(PermisosGobierno.POLITICAS_CREAR, PermisosGobierno.POLITICAS_ACTIVOS, PermisosGobierno.FICHAS_ACTUALIZAR,
                PermisosGobierno.LINAJE_ADMINISTRAR, PermisosGobierno.CUMPLIMIENTO_EVALUAR, PermisosGobierno.POLITICAS_LEER,
                PermisosGobierno.FICHAS_LEER);
        Politica p = politicas.crear(new NuevaPolitica(P, "PII", null, CategoriaPolitica.PRIVACIDAD, DoblesGobierno.ACTOR));
        politicas.asociarActivo(P, p.id(), 0, CLIENTES, "Clientes");
        fichas.guardar(new DatosFicha(P, CLIENTES, null, DoblesGobierno.ACTOR, Clasificacion.CONFIDENCIAL, "Alta"));
        linaje.registrar(new NuevaRelacion(P, CLIENTES, VENTAS, TipoRelacion.TRANSFORMA, null, "ETL"));
        cumplimiento.evaluar(P, CLIENTES);
    }

    @Test
    void resumenReuneTodoLoQueM3SabeDelActivo() {
        datosDeClientes();
        conceder(PermisosGobierno.HISTORIAL_LEER, PermisosGobierno.CUMPLIMIENTO_LEER, PermisosGobierno.LINAJE_LEER);

        ResumenActivo r = historial.resumen(P, CLIENTES);

        assertEquals(Clasificacion.CONFIDENCIAL, r.ficha().classification());
        assertEquals(1, r.politicas().size());
        assertNotNull(r.ultimaEvaluacion());
        assertEquals(2, r.linajeDirecto().nodos().size());
        assertTrue(r.seccionesNoAutorizadas().isEmpty());
    }

    @Test
    void lasSeccionesSinPermisoSeOmitenSinRevelarlas() {
        datosDeClientes();
        conceder(PermisosGobierno.HISTORIAL_LEER);

        ResumenActivo r = historial.resumen(P, CLIENTES);

        assertNull(r.ultimaEvaluacion());
        assertNull(r.linajeDirecto());
        assertEquals(List.of(Seccion.CUMPLIMIENTO, Seccion.LINAJE), r.seccionesNoAutorizadas());
        assertNotNull(r.ficha());
    }

    @Test
    void exigePermisoDeHistorialYUnActivoDelCatalogo() {
        assertThrows(AccesoDenegadoGobiernoException.class, () -> historial.resumen(P, CLIENTES));
        conceder(PermisosGobierno.HISTORIAL_LEER);
        assertThrows(RecursoNoEncontradoGobiernoException.class, () -> historial.resumen(P, UUID.randomUUID()));
    }

    @Test
    void cronologiaExigeAdemasPermisoDeAuditoria() {
        conceder(PermisosGobierno.HISTORIAL_LEER);
        assertThrows(AccesoDenegadoGobiernoException.class, () -> historial.cronologia(P, CLIENTES, 1, 25));

        conceder(PermisosGobierno.AUDITORIA_LEER);
        Pagina<EntradaCronologia> pagina = historial.cronologia(P, CLIENTES, 1, 25);
        assertEquals(1, pagina.items().size());
        assertTrue(pagina.items().getFirst().generoVersion());
    }
}
