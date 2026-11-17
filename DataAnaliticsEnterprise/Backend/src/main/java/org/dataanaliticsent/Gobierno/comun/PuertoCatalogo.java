package org.dataanaliticsent.Gobierno.comun;

import java.util.UUID;

/**
 * Consulta al catálogo de M1. M3 no crea un segundo catálogo: solo comprueba que el activo
 * exista en el proyecto. Al integrar, lo implementa un adaptador sobre CatalogoApi de M1.
 */
public interface PuertoCatalogo {
    boolean activoExiste(UUID projectId, UUID assetId);
}
