package org.dataanaliticsent.TroncoComun.secretos;
import java.util.Arrays;
/** Material efímero solo para código de conector confiable; nunca es un DTO HTTP. */
public final class CredencialPostgres implements AutoCloseable {
    private final char[] usuario,clave; private boolean cerrada;
    public CredencialPostgres(char[] usuario,char[] clave) {
        if(usuario==null||clave==null||usuario.length<1||usuario.length>256||clave.length<1||clave.length>4096)
            throw new SecretosException(SecretosException.Codigo.INVALIDO);
        for(char c:usuario) if(c==0) throw new SecretosException(SecretosException.Codigo.INVALIDO);
        this.usuario=usuario.clone();this.clave=clave.clone();
    }
    public char[] usuario() { exigir();return usuario.clone(); }
    public char[] clave() { exigir();return clave.clone(); }
    private void exigir() { if(cerrada)throw new SecretosException(SecretosException.Codigo.NO_DISPONIBLE); }
    @Override public void close() { Arrays.fill(usuario,'\0');Arrays.fill(clave,'\0');cerrada=true; }
    @Override public String toString() { return "CredencialPostgres[protegida]"; }
}
