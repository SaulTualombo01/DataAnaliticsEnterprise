package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tic.auth")
public record ParametrosIdentidad(Duration absoluteSessionTimeout,Duration loginWindow,int loginAccountLimit,int loginIpLimit) {
    public ParametrosIdentidad {
        if(absoluteSessionTimeout==null||absoluteSessionTimeout.isNegative()||absoluteSessionTimeout.isZero()
                ||loginWindow==null||loginWindow.isNegative()||loginWindow.isZero()||loginAccountLimit<1||loginIpLimit<1) {
            throw new IllegalArgumentException("Parámetros de identidad inválidos");
        }
    }
}
