package org.dataanaliticsent.Gobierno.comun;

import java.util.UUID;

/**
 * Comprobación de permisos por proyecto. Al integrar, lo implementa un adaptador sobre
 * AutorizacionApi del tronco común (ámbito PROJECT).
 */
public interface PuertoAutorizacion {
    /** Lanza AccesoDenegadoGobiernoException si el actor no tiene el permiso en el proyecto. */
    void exigir(UUID actorId, String permiso, UUID projectId);
}
