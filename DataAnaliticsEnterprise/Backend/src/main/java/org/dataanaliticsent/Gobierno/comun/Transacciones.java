package org.dataanaliticsent.Gobierno.comun;

import java.util.function.Supplier;

/**
 * Ejecuta un bloque en una transacción: si algo falla, no queda nada a medias (cambio,
 * evento de auditoría y versión se confirman juntos o ninguno).
 */
public interface Transacciones {
    <T> T ejecutar(Supplier<T> bloque);
}
