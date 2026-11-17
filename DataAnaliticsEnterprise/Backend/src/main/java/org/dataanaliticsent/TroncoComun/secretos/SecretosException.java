package org.dataanaliticsent.TroncoComun.secretos;
/** Sin causas/valores externos: los conectores deben devolver resultados seguros. */
public final class SecretosException extends RuntimeException {
    public enum Codigo { INVALIDO, NO_DISPONIBLE, CONFLICTO, CIFRADO_NO_DISPONIBLE, CONECTOR_FALLIDO }
    private final Codigo codigo;
    public SecretosException(Codigo codigo) { super("Operacion de credenciales no disponible: "+codigo); this.codigo=codigo; }
    public Codigo codigo() { return codigo; }
}
