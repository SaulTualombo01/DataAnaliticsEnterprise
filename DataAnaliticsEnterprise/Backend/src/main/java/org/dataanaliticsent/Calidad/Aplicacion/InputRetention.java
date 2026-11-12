package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.InputRef;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** La política también se aplica a CSV anteriores a la incorporación de expires_at. */
@Component
public class InputRetention {
    private final M2Properties properties;
    private final Clock clock;

    public InputRetention(M2Properties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Instant csvExpiresAt(Instant acquiredAt) {
        return acquiredAt.plus(properties.getLimits().getCsvRetentionDays(), ChronoUnit.DAYS);
    }

    public Instant expiresAt(InputRef input) {
        if (input.protectedObjectRef == null || !input.protectedObjectRef.startsWith("local-csv:"))
            return input.expiresAt;
        Instant policy = csvExpiresAt(input.acquiredAt);
        return input.expiresAt == null || policy.isBefore(input.expiresAt) ? policy : input.expiresAt;
    }

    public boolean expired(InputRef input) {
        return expired(expiresAt(input));
    }

    public boolean expired(Instant expiry) {
        return expiry != null && !expiry.isAfter(clock.instant());
    }

    public String availability(InputRef input) {
        return expired(input) ? "EXPIRED" : input.availability;
    }
}
