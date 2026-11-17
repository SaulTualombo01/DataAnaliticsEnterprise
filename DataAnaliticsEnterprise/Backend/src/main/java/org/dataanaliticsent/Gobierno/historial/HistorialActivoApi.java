package org.dataanaliticsent.Gobierno.historial;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Grafo;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.PoliticaAplicable;

/**
 * Todo lo que M3 sabe de un activo del catálogo, en una consulta (RF-M3.6.1), y la cronología
 * de sus modificaciones relacionadas con auditoría y versiones (RF-M3.4.3).
 */
public interface HistorialActivoApi {

    /** Secciones del resumen; cada una exige el permiso de lectura de su subcomponente. */
    enum Seccion { FICHA, POLITICAS, CUMPLIMIENTO, LINAJE }

    /**
     * Las secciones sin permiso quedan en null (o vacías) y se listan en seccionesNoAutorizadas:
     * el resumen nunca revela lo que el actor no puede ver por separado.
     */
    record ResumenActivo(UUID projectId, UUID assetId, Ficha ficha, List<PoliticaAplicable> politicas,
            Evaluacion ultimaEvaluacion, Grafo linajeDirecto, List<Seccion> seccionesNoAutorizadas) {
        public ResumenActivo {
            politicas = List.copyOf(politicas);
            seccionesNoAutorizadas = List.copyOf(seccionesNoAutorizadas);
        }
    }

    /** Un hecho auditado sobre el activo. versionId no es null solo si el hecho cambió un estado versionado. */
    record EntradaCronologia(Evento evento, UUID versionId) {
        public boolean generoVersion() { return versionId != null; }
    }

    /** Exige m3.history.read. El activo debe existir en el catálogo del proyecto. */
    ResumenActivo resumen(UUID projectId, UUID assetId);

    /**
     * Exige m3.history.read y m3.audit.read. Eventos de la ficha, de las políticas que asociaron o
     * retiraron el activo, de sus evaluaciones y de su linaje, del más reciente al más antiguo.
     */
    Pagina<EntradaCronologia> cronologia(UUID projectId, UUID assetId, int page, int pageSize);
}
