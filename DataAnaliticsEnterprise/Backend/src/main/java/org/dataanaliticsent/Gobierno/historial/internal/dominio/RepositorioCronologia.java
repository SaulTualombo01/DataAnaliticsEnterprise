package org.dataanaliticsent.Gobierno.historial.internal.dominio;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.EntradaCronologia;

public interface RepositorioCronologia {
    /**
     * Eventos del proyecto cuyo recurso es el activo o que lo mencionan en sus detalles
     * (asset_id, origin_asset_id o target_asset_id), con la versión que generaron si la hubo.
     */
    List<EntradaCronologia> buscar(UUID projectId, UUID assetId, int limite, int desplazamiento);
}
