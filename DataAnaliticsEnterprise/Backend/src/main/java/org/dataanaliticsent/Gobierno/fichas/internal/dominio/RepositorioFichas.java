package org.dataanaliticsent.Gobierno.fichas.internal.dominio;

import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;

public interface RepositorioFichas {
    Optional<Ficha> buscar(UUID projectId, UUID assetId);

    /** Crea la ficha (revisionLeida null) o la actualiza si la revisión sigue vigente; si no, conflicto. */
    void guardar(Ficha ficha, Long revisionLeida);
}
