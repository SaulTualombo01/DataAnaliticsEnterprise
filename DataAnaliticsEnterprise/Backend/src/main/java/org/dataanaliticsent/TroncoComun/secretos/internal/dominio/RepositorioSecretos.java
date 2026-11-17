package org.dataanaliticsent.TroncoComun.secretos.internal.dominio;
import java.util.*;
import org.dataanaliticsent.TroncoComun.secretos.SecretosApi.*;
public interface RepositorioSecretos {
    record Cifrado(String keyId,byte[] nonce,byte[] ciphertext) {
        public Cifrado { nonce=nonce.clone();ciphertext=ciphertext.clone(); }
        @Override public byte[] nonce(){return nonce.clone();}
        @Override public byte[] ciphertext(){return ciphertext.clone();}
        @Override public String toString(){return "Cifrado[protegido]";}
    }
    record Entrada(Referencia referencia,DestinoPostgres destino,Cifrado cifrado) {
        @Override public String toString(){return "EntradaSecretos[protegida]";}
    }
    Optional<Entrada> buscar(UUID project,UUID source,UUID reference,boolean bloquear);
    Referencia insertar(UUID project,UUID source,UUID reference,DestinoPostgres destino,Cifrado cifrado);
    Referencia actualizar(Entrada anterior,Estado estado,DestinoPostgres destino,Cifrado cifrado);
    void auditar(UUID actor,Referencia referencia,String accion,UUID request);
}
