package org.dataanaliticsent.catalogo.internal.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto interno de persistencia. La autorización corresponde a CatalogoApi y su caso de uso; no se expone a otros módulos. */
public interface RepositorioCatalogo {
    void registrarFuente(FuenteDatos fuente);
    Optional<FuenteDatos> buscarFuente(UUID projectId, UUID sourceId);
    void registrarActivo(EstadoCatalogo estado, UUID actorId, UUID requestId);
    void registrarDescubrimiento(FuenteDatos esperada, List<String> schemas, List<EstadoCatalogo> estados,
            UUID actorId, UUID requestId, java.time.Instant instante);
    List<RestriccionDatos> listarRestricciones(UUID projectId, UUID assetId);
    Optional<ActivoDatos> buscarActivo(UUID projectId, UUID assetId);
    List<CampoDatos> listarCampos(UUID projectId, UUID assetId);
    UUID organizacionDelProyecto(UUID projectId);
    List<String> etiquetasActivo(UUID projectId, UUID assetId);
    boolean existeVersion(UUID projectId, UUID assetId, UUID versionId);
    List<CampoDatos> paginaCamposVersion(UUID projectId, UUID assetId, UUID versionId, long offset, int limit);
    Optional<EstadoCatalogo> consultarVersion(UUID projectId, UUID assetId, UUID metadataVersionId);
}
