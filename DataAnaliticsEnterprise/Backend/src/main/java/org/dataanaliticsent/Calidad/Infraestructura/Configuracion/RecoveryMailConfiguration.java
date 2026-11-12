package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import jakarta.validation.constraints.*;
import org.dataanaliticsent.Calidad.Aplicacion.IdentityService.RecoveryDelivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Configuration
@ConditionalOnProperty(name = "m2.recovery.enabled", havingValue = "true")
@EnableConfigurationProperties(RecoveryMailConfiguration.Smtp.class)
public class RecoveryMailConfiguration {
    public enum Security {STARTTLS, SMTPS, LOCAL}

    @Validated
    @ConditionalOnProperty(name = "m2.recovery.enabled", havingValue = "true")
    @ConfigurationProperties(prefix = "m2.recovery.smtp")
    public record Smtp(@NotBlank @Pattern(regexp = "[A-Za-z0-9.:-]+") String host,
                       @Min(1) @Max(65535) int port, @NotBlank @Email String from,
                       @NotNull Security security, boolean auth, String username, String password) {
        @AssertTrue(message = "LOCAL solo se admite con un servidor SMTP de loopback.")
        public boolean isTransportSecure() {
            return security != Security.LOCAL || host != null && Set.of("127.0.0.1", "::1", "localhost").contains(host);
        }

        @AssertTrue(message = "SMTP con autenticación requiere usuario y clave.")
        public boolean isAuthenticationConfigured() {
            return !auth || username != null && !username.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "Smtp[host=" + host + ", port=" + port + ", security=" + security + "]";
        }
    }

    @Bean
    public RecoveryDelivery recoveryDelivery(Smtp settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setDefaultEncoding("UTF-8");
        sender.setUsername(settings.username());
        sender.setPassword(settings.password());
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(settings.auth()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(settings.security() == Security.STARTTLS));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(settings.security() == Security.STARTTLS));
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(settings.security() == Security.SMTPS));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        return (email, token) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(settings.from());
            message.setTo(email);
            message.setSubject("Recuperación de acceso a Calidad M2");
            message.setText("Recibimos una solicitud para cambiar tu contraseña.\n\n"
                    + "Abre Calidad M2, selecciona Olvidé mi contraseña y después Ya tengo un código de recuperación.\n"
                    + "Introduce este código y una nueva contraseña.\n\n"
                    + token + "\n\nEl código caduca en 15 minutos y solo puede usarse una vez.\n"
                    + "Si no solicitaste el cambio, puedes ignorar este correo.\n");
            sender.send(message);
        };
    }
}
