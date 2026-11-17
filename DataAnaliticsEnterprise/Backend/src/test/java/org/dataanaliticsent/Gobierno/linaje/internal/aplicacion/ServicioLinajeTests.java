package org.dataanaliticsent.Gobierno.linaje.internal.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.auditoria.internal.aplicacion.ServicioAuditoria;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.DoblesGobierno;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.linaje.ConflictoLinajeException;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Direccion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.EstadoRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Grafo;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Nodo;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.NuevaRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;
import org.dataanaliticsent.Gobierno.versionado.internal.aplicacion.ServicioVersionado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Linaje del ejemplo de comercio electrónico: pedidos y clientes → ventas → reporte_ventas. */
class ServicioLinajeTests {
    private static final UUID P = DoblesGobierno.PROYECTO;
    private static final UUID PEDIDOS = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID CLIENTES = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    private static final UUID VENTAS = UUID.fromString("00000000-0000-4000-8000-0000000000e3");
    private static final UUID REPORTE = UUID.fromString("00000000-0000-4000-8000-0000000000e4");

    private final DoblesGobierno.Auditoria eventos = new DoblesGobierno.Auditoria();
    private final DoblesGobierno.Versiones versiones = new DoblesGobierno.Versiones();
    private final DoblesGobierno.Autorizacion autorizacion = new DoblesGobierno.Autorizacion();
    private final DoblesGobierno.Linaje linaje = new DoblesGobierno.Linaje();
    private final DoblesGobierno.Catalogo catalogo = new DoblesGobierno.Catalogo()
            .registrar(P, PEDIDOS).registrar(P, CLIENTES).registrar(P, VENTAS).registrar(P, REPORTE);
    private final ServicioLinaje servicio = new ServicioLinaje(linaje,
            new ServicioAuditoria(eventos, DoblesGobierno.identidad(), autorizacion),
            new ServicioVersionado(versiones, DoblesGobierno.identidad(), autorizacion),
            DoblesGobierno.identidad(), autorizacion, catalogo, DoblesGobierno.sinTransaccion(), DoblesGobierno.RELOJ);

    @BeforeEach
    void concederPermisos() {
        autorizacion.conceder(PermisosGobierno.LINAJE_LEER, P).conceder(PermisosGobierno.LINAJE_ADMINISTRAR, P);
    }

    private Relacion relacion(UUID origen, UUID destino, TipoRelacion tipo) {
        return servicio.registrar(new NuevaRelacion(P, origen, destino, tipo, null, "Proceso ETL documentado"));
    }

    private void cadenaDeVentas() {
        relacion(PEDIDOS, VENTAS, TipoRelacion.TRANSFORMA);
        relacion(CLIENTES, VENTAS, TipoRelacion.TRANSFORMA);
        relacion(VENTAS, REPORTE, TipoRelacion.AGREGA);
    }

    @Test
    void registrarAuditaYVersionaYNoDuplica() {
        Relacion r = relacion(PEDIDOS, VENTAS, TipoRelacion.TRANSFORMA);
        Relacion repetida = relacion(PEDIDOS, VENTAS, TipoRelacion.TRANSFORMA);

        assertEquals(r, repetida);
        assertEquals(EstadoRelacion.ACTIVE, r.estado());
        assertEquals("m3.lineage.create", eventos.eventos.getFirst().action());
        assertEquals(r.currentVersionId(), versiones.versiones.getFirst().id());
        assertEquals(1, linaje.datos.size());
    }

    @Test
    void rechazaCiclosYAutoRelacion() {
        cadenaDeVentas();

        assertThrows(ConflictoLinajeException.class, () -> relacion(REPORTE, PEDIDOS, TipoRelacion.ALIMENTA));
        assertThrows(ConflictoLinajeException.class, () -> relacion(VENTAS, CLIENTES, TipoRelacion.COPIA));
        assertThrows(IllegalArgumentException.class, () -> relacion(VENTAS, VENTAS, TipoRelacion.COPIA));
        assertEquals(3, linaje.datos.size());
    }

    @Test
    void soloSeRelacionanActivosDelCatalogo() {
        assertThrows(RecursoNoEncontradoGobiernoException.class,
                () -> relacion(PEDIDOS, UUID.randomUUID(), TipoRelacion.COPIA));
    }

    @Test
    void grafoDeOrigenesDelReporte() {
        cadenaDeVentas();

        Grafo g = servicio.grafo(P, REPORTE, Direccion.ORIGENES, 5);
        Map<UUID, Integer> distancias = g.nodos().stream().collect(Collectors.toMap(Nodo::assetId, Nodo::distancia));

        assertEquals(Map.of(REPORTE, 0, VENTAS, -1, PEDIDOS, -2, CLIENTES, -2), distancias);
        assertEquals(3, g.relaciones().size());
        assertFalse(g.truncado());
    }

    @Test
    void grafoRespetaProfundidadYDireccion() {
        cadenaDeVentas();

        Grafo destinosDePedidos = servicio.grafo(P, PEDIDOS, Direccion.DESTINOS, 1);
        Grafo ambosDeVentas = servicio.grafo(P, VENTAS, Direccion.AMBOS, 1);

        assertEquals(2, destinosDePedidos.nodos().size());
        assertEquals(4, ambosDeVentas.nodos().size());
        assertThrows(IllegalArgumentException.class, () -> servicio.grafo(P, VENTAS, Direccion.AMBOS, 6));
    }

    @Test
    void retirarConservaLaRelacionYSaleDelGrafo() {
        Relacion r = relacion(PEDIDOS, VENTAS, TipoRelacion.TRANSFORMA);

        Relacion retirada = servicio.retirar(P, r.id(), "El proceso ETL se dio de baja");
        Relacion otraVez = servicio.retirar(P, r.id(), "Repetir");

        assertEquals(EstadoRelacion.RETIRED, retirada.estado());
        assertEquals(retirada, otraVez);
        assertEquals(2, versiones.versiones.size());
        assertEquals(1, servicio.grafo(P, VENTAS, Direccion.ORIGENES, 1).nodos().size());
        assertTrue(servicio.consultar(P, r.id()).isPresent());
        // Retirada la relación, la inversa ya no forma ciclo.
        relacion(VENTAS, PEDIDOS, TipoRelacion.COPIA);
    }

    @Test
    void sinPermisoSeDeniegaYQuedaAuditado() {
        UUID otro = UUID.randomUUID();
        assertThrows(AccesoDenegadoGobiernoException.class,
                () -> servicio.registrar(new NuevaRelacion(otro, PEDIDOS, VENTAS, TipoRelacion.COPIA, null, "x")));
        assertEquals(Resultado.DENIED, eventos.eventos.getLast().outcome());
        assertThrows(AccesoDenegadoGobiernoException.class, () -> servicio.grafo(otro, VENTAS, Direccion.AMBOS, 1));
    }
}
