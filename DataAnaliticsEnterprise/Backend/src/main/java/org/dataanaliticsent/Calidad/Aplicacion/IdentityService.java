package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;

import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;

@Service
public class IdentityService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdentityService.class);
    /**
     * Puerto de entrega de recuperación. Nunca devolver tokens en la API ni en logs.
     */
    public interface RecoveryDelivery {
        void deliver(String email, String token);
    }

    private final QualityRepository repo;
    private final SensitiveDataCipher cipher;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final ObjectProvider<RecoveryDelivery> delivery;
    private final String dummyHash;
    private final TransactionTemplate resetTransactions;
    private final Set<UUID> serviceUsers;

    public IdentityService(QualityRepository repo, SensitiveDataCipher cipher, PasswordEncoder encoder, Clock clock,
                           ObjectProvider<RecoveryDelivery> delivery, TransactionTemplate transactions,
                           @Value("${m2.worker.user-id:}") String worker,
                           @Value("${m2.bootstrap.worker-id:}") String bootstrapWorker) {
        this.repo = repo;
        this.cipher = cipher;
        this.encoder = encoder;
        this.clock = clock;
        this.delivery = delivery;
        resetTransactions = new TransactionTemplate(transactions.getTransactionManager());
        resetTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        serviceUsers = java.util.stream.Stream.of(worker, bootstrapWorker).filter(id -> !id.isBlank())
                .map(UUID::fromString).collect(java.util.stream.Collectors.toUnmodifiableSet());
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public UserAccount authenticate(String email, String password) {
        UserAccount user = repo.userByEmailHash(cipher.computeEmailLookupHmac(email)).orElse(null);
        boolean matches = encoder.matches(password, user == null ? dummyHash : user.passwordHash);
        if (user == null || !matches || !Boolean.TRUE.equals(user.active) || serviceUsers.contains(user.id))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "No se pudo iniciar sesión.", "Revisa tus credenciales e inténtalo de nuevo.");
        return user;
    }

    @Transactional
    public UserAccount create(String email, String password) {
        return createWithId(UUID.randomUUID(), email, password);
    }

    @Transactional
    public UserAccount createWithId(UUID id, String email, String password) {
        validatePassword(password);
        String hash = cipher.computeEmailLookupHmac(email);
        if (repo.userByEmailHash(hash).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CONFLICT", "No se pudo crear la cuenta.", "Revisa la información de la cuenta.");
        UserAccount user = new UserAccount();
        user.id = id;
        user.emailLookupHash = hash;
        user.emailCiphertext = cipher.encrypt(email.trim().toLowerCase(Locale.ROOT), "email", null, user.id.toString());
        user.passwordHash = encoder.encode(password);
        user.active = true;
        user.authVersion = 0L;
        
        user.createdAt = clock.instant();
        return repo.save(user);
    }

    public void requestReset(String email) {
        RecoveryDelivery sender = delivery.getIfAvailable();
        if (sender == null)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RECOVERY_UNAVAILABLE", "Recuperación no disponible.", "El administrador debe configurar el canal de recuperación.");
        PreparedReset message = resetTransactions.execute(status -> prepareReset(email));
        if (message == null) return;
        // El token ya está confirmado en PostgreSQL antes de entregar el correo a SMTP.
        try {
            sender.deliver(message.email(), message.token());
        } catch (RuntimeException failure) {
            resetTransactions.executeWithoutResult(status -> repo.reset(hash(message.token()))
                    .filter(reset -> reset.usedAt == null).ifPresent(reset -> reset.usedAt = clock.instant()));
            log.warn("RECOVERY_DELIVERY_FAILED [reset_id={}]", message.id());
            // Conserva la respuesta genérica también si SMTP falla.
        }
    }

    private PreparedReset prepareReset(String email) {
        var candidate = repo.userByEmailHash(cipher.computeEmailLookupHmac(email)).orElse(null);
        if (candidate == null) return null;
        repo.lockAdministrationAccounts(candidate.id, candidate.id);
        var user = repo.lockAndRefresh(candidate);
        if (!Boolean.TRUE.equals(user.active) || serviceUsers.contains(user.id)) return null;
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordResetToken reset = new PasswordResetToken();
        reset.id = UUID.randomUUID();
        reset.userId = user.id;
        reset.tokenHash = hash(token);
        reset.createdAt = clock.instant();
        reset.expiresAt = clock.instant().plusSeconds(900);
        repo.save(reset);
        String recipient = cipher.decrypt(user.emailCiphertext, "email", null, user.id.toString());
        return new PreparedReset(reset.id, recipient, token);
    }

    private record PreparedReset(UUID id, String email, String token) {
        @Override public String toString() { return "PreparedReset[id=" + id + "]"; }
    }

    @Transactional
    public void confirmReset(String token, String password) {
        validatePassword(password);
        String tokenHash = hash(token);
        UUID userId = repo.resetUserId(tokenHash).orElseThrow(IdentityService::invalidReset);
        // El orden compartido con administración evita carreras con la desactivación y con otros códigos.
        repo.lockAdministrationAccounts(userId, userId);
        var user = repo.lockAndRefresh(repo.user(userId).orElseThrow(IdentityService::invalidReset));
        if (!Boolean.TRUE.equals(user.active) || serviceUsers.contains(user.id)) throw invalidReset();
        var reset = repo.reset(tokenHash).filter(t -> t.usedAt == null && t.expiresAt.isAfter(clock.instant()))
                .orElseThrow(IdentityService::invalidReset);
        user.passwordHash = encoder.encode(password);
        user.authVersion++;
        reset.usedAt = clock.instant();
        repo.flush();
        repo.consumeResetTokens(user.id, reset.usedAt);
    }

    private static ApiException invalidReset() {
        return invalid("El código no es válido o ha caducado. Solicita uno nuevo.");
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128)
            throw invalid("La contraseña debe tener entre 12 y 128 caracteres.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

