package org.dataanaliticsent.Gobierno.cumplimiento.internal.dominio;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;

public interface RepositorioEvaluaciones {
    void guardar(Evaluacion evaluacion);

    /** De la más reciente a la más antigua. */
    List<Evaluacion> listar(UUID projectId, UUID assetId, int limite, int desplazamiento);
}
