package org.dataanaliticsent.Gobierno.versionado.internal.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Version;

public interface RepositorioVersiones {
    Optional<Version> ultima(UUID projectId, String resourceType, UUID resourceId);

    /** Lanza ConflictoVersionException si otra versión ocupó el mismo número. */
    void guardar(Version version);

    List<Version> listar(UUID projectId, String resourceType, UUID resourceId, int limite, int desplazamiento);

    Optional<Version> buscar(UUID projectId, String resourceType, UUID resourceId, UUID versionId);
}
