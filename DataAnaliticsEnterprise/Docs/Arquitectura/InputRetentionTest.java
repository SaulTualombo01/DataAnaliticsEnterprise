import org.dataanaliticsent.Calidad.Aplicacion.InputRetention;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.InputRef;
import org.junit.jupiter.api.Test;

import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class InputRetentionTest {
    private final Instant now = Instant.parse("2026-11-09T12:00:00Z");
    private final InputRetention policy = new InputRetention(new M2Properties(), Clock.fixed(now, ZoneOffset.UTC));

    private InputRef csv(Instant acquired) {
        var input = new InputRef();
        input.protectedObjectRef = "local-csv:snapshot";
        input.acquiredAt = acquired;
        input.availability = "AVAILABLE";
        return input;
    }

    @Test void legacyCsvExpiresAtExactlyThirtyDaysWithoutChangingItsImmutableReference() {
        var input = csv(now.minus(Duration.ofDays(30)));
        assertEquals(now, policy.expiresAt(input));
        assertTrue(policy.expired(input));
        assertEquals("EXPIRED", policy.availability(input));
        assertNull(input.expiresAt);
        assertEquals("AVAILABLE", input.availability);
    }

    @Test void earlierRecordedExpiryCannotBeExtendedByPolicy() {
        var input = csv(now.minus(Duration.ofDays(1)));
        input.expiresAt = now.minusSeconds(1);
        assertTrue(policy.expired(input));
        assertEquals(input.expiresAt, policy.expiresAt(input));
    }

    @Test void unexpiredCsvAndExternalReferencesKeepTheirAvailability() {
        assertEquals("AVAILABLE", policy.availability(csv(now.minus(Duration.ofDays(29)))));
        var external = new InputRef();
        external.availability = "DELETED";
        external.acquiredAt = now.minus(Duration.ofDays(100));
        assertEquals("DELETED", policy.availability(external));
        assertNull(policy.expiresAt(external));
    }
}
