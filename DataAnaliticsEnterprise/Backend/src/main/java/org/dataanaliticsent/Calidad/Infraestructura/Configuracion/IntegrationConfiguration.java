package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import org.dataanaliticsent.Calidad.Aplicacion.InputProvider;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.util.*;

@Configuration
public class IntegrationConfiguration {
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean(name="externalInputProvider") @ConditionalOnMissingBean(name="externalInputProvider")
    public InputProvider unavailableInputProvider(@Value("${m2.integrations.mode:uploads}") String mode) {
        return new InputProvider() {
            private ApiException unavailable() {
                if("uploads".equals(mode))return org.dataanaliticsent.Calidad.Aplicacion.AccessService.missing();
                return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Integración pendiente.","Configura el adaptador M1/TC13 antes de consultar fuentes externas.");
            }
            public List<Asset> assets(UUID actor,UUID project) { if("uploads".equals(mode))return List.of();throw unavailable(); }
            public Capture acquire(UUID actor,UUID project,UUID asset,UUID metadata,UUID snapshot) { throw unavailable(); }
            public Capture read(UUID actor,UUID project,UUID asset,UUID metadata,UUID read,UUID snapshot) { throw unavailable(); }
        };
    }
}

