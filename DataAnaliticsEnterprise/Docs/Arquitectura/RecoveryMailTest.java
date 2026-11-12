import org.dataanaliticsent.Calidad.Aplicacion.IdentityService.RecoveryDelivery;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.RecoveryMailConfiguration;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.RecoveryMailConfiguration.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailException;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryMailTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RecoveryMailConfiguration.class);

    @Test void recoveryIsDisabledUntilExplicitlyConfigured() {
        context.run(app -> assertEquals(0, app.getBeansOfType(RecoveryDelivery.class).size()));
    }

    @Test void enabledRecoveryRequiresSenderAndAuthentication() {
        context.withPropertyValues("m2.recovery.enabled=true", "m2.recovery.smtp.host=smtp.gmail.com",
                "m2.recovery.smtp.port=587", "m2.recovery.smtp.security=STARTTLS", "m2.recovery.smtp.auth=true")
                .run(app -> assertNotNull(app.getStartupFailure()));
    }

    @Test void plaintextTransportIsRejectedOutsideLoopback() {
        context.withPropertyValues("m2.recovery.enabled=true", "m2.recovery.smtp.host=smtp.gmail.com",
                "m2.recovery.smtp.port=587", "m2.recovery.smtp.security=LOCAL", "m2.recovery.smtp.auth=false",
                "m2.recovery.smtp.from=recovery@example.test").run(app -> assertNotNull(app.getStartupFailure()));
    }

    @Test void mandatoryStarttlsDoesNotSendToAServerWithoutTls() throws Exception {
        try (LocalSmtpInbox inbox = new LocalSmtpInbox()) {
            Smtp settings = new Smtp("127.0.0.1", inbox.port(), "recovery@example.test", Security.STARTTLS, false, "", "");
            RecoveryDelivery delivery = new RecoveryMailConfiguration().recoveryDelivery(settings);
            assertThrows(MailException.class, () -> delivery.deliver("user@example.test", "test-code"));
            assertNull(inbox.next(java.time.Duration.ofMillis(100)));
        }
    }

    @Test void configurationDoesNotPrintCredentials() {
        Smtp settings = new Smtp("smtp.gmail.com", 587, "smtp-test@gmail.com", Security.STARTTLS, true,
                "smtp-test@gmail.com", "smtp-test-secret");
        assertFalse(settings.toString().contains("smtp-test@gmail.com"));
        assertFalse(settings.toString().contains("smtp-test-secret"));
    }
}
