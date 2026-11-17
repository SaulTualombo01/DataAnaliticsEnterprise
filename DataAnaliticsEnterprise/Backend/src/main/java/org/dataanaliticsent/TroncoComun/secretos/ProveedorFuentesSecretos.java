package org.dataanaliticsent.TroncoComun.secretos;
import java.util.*;
/** SPI del propietario M1. Solo metadatos vigentes del servidor, sin credenciales. */
public interface ProveedorFuentesSecretos {
    Optional<Fuente> consultar(UUID project,UUID source);
    record Fuente(UUID projectId,UUID sourceId,SecretosApi.DestinoPostgres destino,boolean habilitada,boolean retirada) {
        public Fuente { Objects.requireNonNull(projectId);Objects.requireNonNull(sourceId);Objects.requireNonNull(destino); }
    }
}
