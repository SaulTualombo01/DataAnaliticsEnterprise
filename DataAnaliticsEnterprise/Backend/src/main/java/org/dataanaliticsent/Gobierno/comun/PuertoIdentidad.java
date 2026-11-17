package org.dataanaliticsent.Gobierno.comun;

import java.util.UUID;

/**
 * Actor autenticado de la petición en curso. Al integrar, lo implementa un adaptador sobre
 * IdentidadActualApi del tronco común; nunca se toma de cabeceras o cuerpos del cliente.
 */
public interface PuertoIdentidad {
    /** Lanza AccesoDenegadoGobiernoException si no hay identidad verificada. */
    UUID actorActual();
}
