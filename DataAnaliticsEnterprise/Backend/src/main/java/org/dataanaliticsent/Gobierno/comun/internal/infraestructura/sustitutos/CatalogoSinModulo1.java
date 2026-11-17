package org.dataanaliticsent.Gobierno.comun.internal.infraestructura.sustitutos;

import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.springframework.stereotype.Component;

/**
 * TEMPORAL mientras la rama no integra M1: ningún activo se considera existente, así que no
 * se puede asociar una política a un activo inventado. Al integrar, reemplazar este archivo
 * por un adaptador sobre CatalogoApi.
 */
@Component
class CatalogoSinModulo1 implements PuertoCatalogo {
    @Override
    public boolean activoExiste(UUID projectId, UUID assetId) {
        return false;
    }
}
