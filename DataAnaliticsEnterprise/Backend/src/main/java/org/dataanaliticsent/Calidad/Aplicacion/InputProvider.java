package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Dominio.QualityEngine;

import java.time.Instant;
import java.util.*;

/**
 * Puerto compartido M1/TC13. El adaptador real debe validar identidad, ámbito, paginación y huella.
 */
public interface InputProvider {
    record Asset(UUID id, UUID sourceId, UUID metadataVersionId, String name, Set<String> fields, String sourceKind) {
        public Asset(UUID id,UUID sourceId,UUID metadataVersionId,String name,Set<String> fields){this(id,sourceId,metadataVersionId,name,fields,"EXTERNAL");}
    }

    record Capture(UUID readId, UUID snapshotId, UUID assetId, UUID metadataVersionId,
                   Instant acquiredAt, Instant expiresAt, String sha256, long bytes, QualityEngine.Input input) {
    }

    List<Asset> assets(UUID actor, UUID project);

    Capture acquire(UUID actor, UUID project, UUID asset, UUID metadata, UUID snapshot);

    Capture read(UUID actor, UUID project, UUID asset, UUID metadata, UUID read, UUID snapshot);
}
