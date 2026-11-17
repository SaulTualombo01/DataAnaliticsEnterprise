package org.dataanaliticsent.Gobierno.historial.internal.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi;
import org.dataanaliticsent.Gobierno.historial.internal.dominio.RepositorioCronologia;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Direccion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Grafo;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.PoliticaAplicable;
import org.springframework.stereotype.Service;

@Service
public class ServicioHistorial implements HistorialActivoApi {
    private final RepositorioCronologia cronologia;
    private final FichasGobiernoApi fichas;
    private final PoliticasApi politicas;
    private final CumplimientoApi cumplimiento;
    private final LinajeApi linaje;
    private final PuertoIdentidad identidad;
    private final PuertoAutorizacion autorizacion;
    private final PuertoCatalogo catalogo;

    public ServicioHistorial(RepositorioCronologia cronologia, FichasGobiernoApi fichas, PoliticasApi politicas,
            CumplimientoApi cumplimiento, LinajeApi linaje, PuertoIdentidad identidad, PuertoAutorizacion autorizacion,
            PuertoCatalogo catalogo) {
        this.cronologia = cronologia;
        this.fichas = fichas;
        this.politicas = politicas;
        this.cumplimiento = cumplimiento;
        this.linaje = linaje;
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.catalogo = catalogo;
    }

    @Override
    public ResumenActivo resumen(UUID projectId, UUID assetId) {
        exigir(projectId, assetId, PermisosGobierno.HISTORIAL_LEER);
        List<Seccion> sinPermiso = new ArrayList<>();
        Ficha ficha = seccion(Seccion.FICHA, sinPermiso, () -> fichas.consultar(projectId, assetId).orElse(null));
        List<PoliticaAplicable> aplicables = seccion(Seccion.POLITICAS, sinPermiso,
                () -> politicas.politicasDeActivo(projectId, assetId));
        Evaluacion evaluacion = seccion(Seccion.CUMPLIMIENTO, sinPermiso,
                () -> cumplimiento.ultima(projectId, assetId).orElse(null));
        Grafo grafo = seccion(Seccion.LINAJE, sinPermiso, () -> linaje.grafo(projectId, assetId, Direccion.AMBOS, 1));
        return new ResumenActivo(projectId, assetId, ficha, aplicables == null ? List.of() : aplicables,
                evaluacion, grafo, sinPermiso);
    }

    @Override
    public Pagina<EntradaCronologia> cronologia(UUID projectId, UUID assetId, int page, int pageSize) {
        Pagina.validar(page, pageSize);
        exigir(projectId, assetId, PermisosGobierno.HISTORIAL_LEER);
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.AUDITORIA_LEER, projectId);
        List<EntradaCronologia> encontradas = cronologia.buscar(projectId, assetId, pageSize + 1, (page - 1) * pageSize);
        boolean hayMas = encontradas.size() > pageSize;
        return new Pagina<>(hayMas ? encontradas.subList(0, pageSize) : encontradas, page, pageSize, hayMas);
    }

    private void exigir(UUID projectId, UUID assetId, String permiso) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        Objects.requireNonNull(assetId, "assetId obligatorio");
        autorizacion.exigir(identidad.actorActual(), permiso, projectId);
        if (!catalogo.activoExiste(projectId, assetId)) {
            throw new RecursoNoEncontradoGobiernoException("El activo no está disponible en el proyecto");
        }
    }

    /** Ejecuta la consulta de una sección; si el actor no tiene ese permiso, la omite y lo anota. */
    private static <T> T seccion(Seccion nombre, List<Seccion> sinPermiso, Supplier<T> consulta) {
        try {
            return consulta.get();
        } catch (AccesoDenegadoGobiernoException e) {
            sinPermiso.add(nombre);
            return null;
        }
    }
}
