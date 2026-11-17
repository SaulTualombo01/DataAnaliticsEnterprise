package org.dataanaliticsent.TroncoComun.secretos.internal.dominio;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.secretos.CredencialPostgres;
import org.dataanaliticsent.TroncoComun.secretos.SecretosApi.DestinoPostgres;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.RepositorioSecretos.Cifrado;
public interface CifradorSecretos {
    record Vinculo(UUID id,UUID project,UUID source,long revision,DestinoPostgres destino) { }
    Cifrado cifrar(Vinculo vinculo,CredencialPostgres material);
    CredencialPostgres abrir(Vinculo vinculo,Cifrado cifrado);
}
