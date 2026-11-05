package org.dataanaliticsent.catalogo.internal.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto interno de persistencia. La autorización corresponde al caso de uso, todavía pendiente. */
public interface RepositorioCatalogo {
    void registrarFuente(FuenteDatos fuente);
    Optional<FuenteDatos> buscarFuente(UUID projectId, UUID sourceId);
    void registrarActivo(EstadoCatalogo estado, UUID actorId, UUID requestId);
    Optional<ActivoDatos> buscarActivo(UUID projectId, UUID assetId);
    List<CampoDatos> listarCampos(UUID projectId, UUID assetId);
    Optional<EstadoCatalogo> consultarVersion(UUID projectId, UUID assetId, UUID metadataVersionId);
}
