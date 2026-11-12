package org.dataanaliticsent.catalogo;

/** Código estable sin mensajes SQL, parámetros de conexión ni secretos del origen. */
public class DescubrimientoFallidoException extends RuntimeException {
    public enum Codigo { CONFIGURACION_ORIGEN, ORIGEN_NO_DISPONIBLE, ALCANCE_NO_ACCESIBLE, LIMITE_EXCEDIDO }
    private final Codigo codigo;
    public DescubrimientoFallidoException(Codigo codigo) {
        super("No se pudo completar el descubrimiento PostgreSQL: " + codigo);
        this.codigo = codigo;
    }
    public Codigo codigo() { return codigo; }
}
