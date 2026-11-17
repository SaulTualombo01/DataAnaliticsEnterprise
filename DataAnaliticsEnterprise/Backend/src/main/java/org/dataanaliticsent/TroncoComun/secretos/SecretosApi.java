package org.dataanaliticsent.TroncoComun.secretos;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
/** Contrato Java del monolito. No publicar por REST ni devolver material del callback. */
public interface SecretosApi {
    enum Estado { ACTIVE, DISABLED, RETIRED }
    enum Uso { PROBAR_CONEXION, DESCUBRIR_METADATOS }
    record DestinoPostgres(String host,int port,String database,String sslmode) {
        public DestinoPostgres {
            if(host==null||!host.matches("[a-zA-Z0-9][a-zA-Z0-9.:-]{0,252}")||port<1||port>65535
                ||database==null||database.isBlank()||database.length()>128||database.chars().anyMatch(c->c<32||c=='|'||c==127)
                ||!Set.of("verify-full","verify-ca","require","disable").contains(sslmode==null?"":sslmode))
                throw new SecretosException(SecretosException.Codigo.INVALIDO);
            host=host.toLowerCase(Locale.ROOT);
        }
    }
    record Referencia(UUID id,UUID projectId,UUID sourceId,long revision,Estado status,Instant createdAt,Instant updatedAt) { }
    Referencia registrar(UUID project,UUID source,DestinoPostgres destino,CredencialPostgres material,String motivo);
    Referencia consultar(UUID project,UUID source,UUID reference);
    Referencia reemplazar(UUID project,UUID source,UUID reference,long revision,DestinoPostgres destino,CredencialPostgres material,String motivo);
    Referencia deshabilitar(UUID project,UUID source,UUID reference,long revision,String motivo);
    Referencia retirar(UUID project,UUID source,UUID reference,long revision,String motivo);
    /** Recifra una referencia con la clave activa, conservando identidad y estado. */
    Referencia recifrar(UUID project,UUID source,UUID reference,long revision,String motivo);
    /** Cierra el material incluso ante fallo. No acepta actor/permiso libre ni hace llamadas HTTP. */
    <T> T usarPostgres(UUID project,UUID source,UUID reference,DestinoPostgres destino,Uso uso,Function<CredencialPostgres,T> conector);
}
