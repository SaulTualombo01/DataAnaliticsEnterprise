package org.dataanaliticsent.Gobierno.politicas.internal.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.FiltroPoliticas;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.ReglaGobierno;

public interface RepositorioPoliticas {
    /** Incluye los activos asociados vigentes. */
    Optional<Politica> buscar(UUID projectId, UUID policyId);

    /** Lanza ConflictoPoliticaException si el nombre ya existe en el proyecto. */
    void insertar(Politica politica);

    /** Guarda solo si la revisión almacenada sigue siendo revisionLeida; si no, ConflictoPoliticaException. */
    void actualizar(Politica politica, long revisionLeida);

    void asociarActivo(UUID projectId, UUID policyId, UUID assetId, UUID actor, Instant instante);

    void retirarActivo(UUID projectId, UUID policyId, UUID assetId, UUID actor, Instant instante);

    void insertarRegla(UUID projectId, UUID policyId, ReglaGobierno regla, UUID actor, Instant instante);

    void borrarRegla(UUID projectId, UUID policyId, UUID ruleId);

    List<Politica> listar(UUID projectId, FiltroPoliticas filtro, int limite, int desplazamiento);

    List<Politica> deActivo(UUID projectId, UUID assetId);
}
