package org.dataanaliticsent.Gobierno.comun;

/** Operación de M3 sin identidad verificada o sin permiso en el proyecto. */
public class AccesoDenegadoGobiernoException extends RuntimeException {
    public AccesoDenegadoGobiernoException(String motivo) { super(motivo); }
}
