package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tic.accounts")
public record ParametrosCuentas(Duration invitationTtl,boolean manualDeliveryEnabled,Duration activationWindow,int activationIpLimit) {
    public ParametrosCuentas {
        if(invitationTtl==null || invitationTtl.compareTo(Duration.ofMinutes(5))<0 || invitationTtl.compareTo(Duration.ofDays(7))>0
            || activationWindow==null || activationWindow.isNegative() || activationWindow.isZero() || activationIpLimit<1) {
            throw new IllegalArgumentException("Configuración de cuentas inválida");
        }
    }
}
