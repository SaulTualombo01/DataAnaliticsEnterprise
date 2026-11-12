import com.fasterxml.jackson.databind.*;
import org.dataanaliticsent.DataAnaliticsEnterprise;
import org.dataanaliticsent.Calidad.Aplicacion.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL, HTTP y SMTP reales, todos aislados de las cuentas y correos de uso diario. */
@SpringBootTest(classes = {DataAnaliticsEnterprise.class, PasswordRecoveryIntegrationTest.TimeConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "M2_ROLE_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordRecoveryIntegrationTest {
    private static final String ORIGINAL = "Original-Test-Password-2026";
    private static final String UPDATED = "Updated-Test-Password-2026";
    private static final UUID WORKER = UUID.fromString("ce763559-26b5-4303-9992-93ae421dd2db");
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired IdentityService identity;
    @Autowired PasswordEncoder encoder;
    @Autowired TestClock clock;

    private static class InboxHolder {
        static final LocalSmtpInbox INBOX = open();
        private static LocalSmtpInbox open() {
            try { return new LocalSmtpInbox(); }
            catch (java.io.IOException failure) { throw new IllegalStateException("No se pudo crear el receptor SMTP de pruebas.", failure); }
        }
    }

    @DynamicPropertySource static void configure(DynamicPropertyRegistry settings) {
        String url = System.getenv("M2_ROLE_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/m2_role_test"))
            throw new IllegalArgumentException("La recuperación requiere la base local aislada m2_role_test.");
        settings.add("spring.datasource.url", () -> url);
        settings.add("spring.datasource.username", () -> "m2_app");
        settings.add("spring.datasource.password", () -> System.getenv("M2_ROLE_TEST_DB_PASSWORD"));
        settings.add("m2.security.data-key-id", () -> "recovery-test");
        settings.add("m2.security.data-key-base64", () -> Base64.getEncoder().encodeToString(new byte[32]));
        byte[] hmac = new byte[32];
        Arrays.fill(hmac, (byte) 3);
        settings.add("m2.security.email-lookup-key-base64", () -> Base64.getEncoder().encodeToString(hmac));
        settings.add("m2.bootstrap.enabled", () -> true);
        settings.add("m2.bootstrap.project-id", () -> "38d0f709-5660-41db-a670-b1261e95d681");
        settings.add("m2.bootstrap.organization-id", () -> "00642131-192f-454f-9a75-9783640003c9");
        settings.add("m2.bootstrap.project-name", () -> "Prueba aislada de recuperación");
        settings.add("m2.bootstrap.admin-email", () -> "admin@recovery.example.test");
        settings.add("m2.bootstrap.admin-password", () -> ORIGINAL);
        settings.add("m2.bootstrap.quality-email", () -> "quality@recovery.example.test");
        settings.add("m2.bootstrap.quality-password", () -> ORIGINAL);
        settings.add("m2.bootstrap.worker-email", () -> "worker@recovery.example.test");
        settings.add("m2.bootstrap.worker-id", WORKER::toString);
        settings.add("m2.worker.user-id", () -> "");
        settings.add("m2.recovery.enabled", () -> true);
        settings.add("m2.recovery.smtp.host", () -> "127.0.0.1");
        settings.add("m2.recovery.smtp.port", () -> InboxHolder.INBOX.port());
        settings.add("m2.recovery.smtp.from", () -> "recovery@example.test");
        settings.add("m2.recovery.smtp.security", () -> "LOCAL");
        settings.add("m2.recovery.smtp.auth", () -> false);
    }

    @BeforeEach void prepare() {
        // Cada caso tiene una ventana nueva del limitador y puede ensayar la caducidad sin esperar.
        clock.advance(Duration.ofMinutes(1));
        InboxHolder.INBOX.reset();
    }

    @AfterAll void closeInbox() throws Exception { InboxHolder.INBOX.close(); }

    @Test void sendsCommittedCodesAndChangingPasswordInvalidatesAllPreviousCodesAndSessions() throws Exception {
        Account account = account();
        Client previousSession = new Client();
        assertEquals(200, previousSession.login(account.email(), ORIGINAL).status());
        AtomicReference<Boolean> committedBeforeDelivery = new AtomicReference<>(false);
        InboxHolder.INBOX.observe(message -> {
            try { committedBeforeDelivery.set(tokenExists(code(message), account.id())); }
            catch (Exception failure) { throw new IllegalStateException("No se pudo comprobar el token confirmado."); }
        });
        Client requester = new Client();
        String first = requestCode(requester, account.email().toUpperCase(Locale.ROOT));
        assertTrue(committedBeforeDelivery.get(), "La entrega SMTP debe ver el token ya confirmado");
        String second = requestCode(requester, account.email());
        assertNotEquals(first, second);
        assertEquals(204, requester.confirm(first, UPDATED).status());
        assertEquals(401, previousSession.send("GET", "/me", null, true).status());
        assertEquals(422, requester.confirm(first, ORIGINAL).status());
        assertEquals(422, requester.confirm(second, ORIGINAL).status());
        Client login = new Client();
        assertEquals(401, login.login(account.email(), ORIGINAL).status());
        assertEquals(200, login.login(account.email(), UPDATED).status());
        assertEquals(0, unusedTokens(account.id()));
    }

    @Test void twoDifferentCodesCannotChangeTheSamePasswordConcurrently() throws Exception {
        Account account = account();
        Client first = new Client();
        Client second = new Client();
        String firstCode = requestCode(first, account.email());
        String secondCode = requestCode(second, account.email());
        try (ExecutorService pool = Executors.newFixedThreadPool(2); Connection blocker = database()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement sql = blocker.prepareStatement("SELECT id FROM m2.users WHERE id=? FOR UPDATE")) {
                sql.setObject(1, account.id());
                sql.execute();
            }
            Future<Reply> firstReply = pool.submit(() -> first.confirm(firstCode, UPDATED));
            Future<Reply> secondReply = pool.submit(() -> second.confirm(secondCode, UPDATED + "-other"));
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                boolean waiting = false;
                try (Connection observer = database(); Statement sql = observer.createStatement()) {
                    while (!waiting && System.nanoTime() < deadline) {
                        try (ResultSet rows = sql.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND usename='m2_app' AND wait_event_type='Lock'")) {
                            rows.next();
                            waiting = rows.getLong(1) >= 2;
                        }
                        if (!waiting) Thread.sleep(50);
                    }
                }
                assertTrue(waiting);
            } finally { blocker.rollback(); }
            assertEquals(List.of(204, 422), List.of(firstReply.get(30, TimeUnit.SECONDS).status(),
                    secondReply.get(30, TimeUnit.SECONDS).status()).stream().sorted().toList());
        }
        assertEquals(0, unusedTokens(account.id()));
        try (Connection connection = database(); PreparedStatement sql = connection.prepareStatement("SELECT auth_version FROM m2.users WHERE id=?")) {
            sql.setObject(1, account.id());
            try (ResultSet rows = sql.executeQuery()) { rows.next(); assertEquals(1, rows.getLong(1)); }
        }
    }

    @Test void unknownInactiveAndTechnicalAccountsReceiveTheSameResponseWithoutEmail() throws Exception {
        Account inactive = account();
        try (Connection connection = database(); PreparedStatement sql = connection.prepareStatement("UPDATE m2.users SET active=false WHERE id=?")) {
            sql.setObject(1, inactive.id());
            sql.executeUpdate();
        }
        Client client = new Client();
        Reply unknown = client.request("unknown@recovery.example.test");
        Reply disabled = client.request(inactive.email());
        Reply technical = client.request("worker@recovery.example.test");
        for (Reply reply : List.of(unknown, disabled, technical)) {
            assertEquals(202, reply.status());
            assertEquals(unknown.json().path("data"), reply.json().path("data"));
        }
        assertNull(InboxHolder.INBOX.next(Duration.ofMillis(100)));
        assertEquals(0, unusedTokens(inactive.id()));
        assertEquals(0, unusedTokens(WORKER));
        try (Connection connection = database(); PreparedStatement sql = connection.prepareStatement("UPDATE m2.users SET password_hash=? WHERE id=?")) {
            sql.setString(1, encoder.encode(ORIGINAL));
            sql.setObject(2, WORKER);
            sql.executeUpdate();
        }
        assertEquals(401, client.login("worker@recovery.example.test", ORIGINAL).status(), "La cuenta técnica rechaza incluso una contraseña correcta");
    }

    @Test void expiredAndDeactivatedAccountCodesCannotChangeThePassword() throws Exception {
        Account account = account();
        Client client = new Client();
        String expired = requestCode(client, account.email());
        clock.advance(Duration.ofMinutes(16));
        assertEquals(422, client.confirm(expired, UPDATED).status());
        String active = requestCode(client, account.email());
        try (Connection connection = database(); PreparedStatement sql = connection.prepareStatement("UPDATE m2.users SET active=false WHERE id=?")) {
            sql.setObject(1, account.id());
            sql.executeUpdate();
        }
        assertEquals(422, client.confirm(active, UPDATED).status());
        try (Connection connection = database(); PreparedStatement sql = connection.prepareStatement("SELECT password_hash FROM m2.users WHERE id=?")) {
            sql.setObject(1, account.id());
            try (ResultSet rows = sql.executeQuery()) { rows.next(); assertTrue(encoder.matches(ORIGINAL, rows.getString(1))); }
        }
    }

    @Test void smtpFailureInvalidatesTheUnsentCodeAndAllowsANewRequest() throws Exception {
        Account account = account();
        Client client = new Client();
        InboxHolder.INBOX.rejectNext();
        Reply failure = client.request(account.email());
        assertEquals(202, failure.status(), "El fallo de SMTP mantiene la respuesta genérica");
        assertFalse(failure.body().contains(account.email()));
        assertFalse(failure.body().contains("451"));
        assertEquals(0, unusedTokens(account.id()));
        assertEquals(204, client.confirm(requestCode(client, account.email()), UPDATED).status());
    }

    @Test void recoveryEndpointsRequireCsrf() throws Exception {
        Account account = account();
        Client client = new Client();
        assertEquals(403, client.send("POST", "/password-resets", Map.of("email", account.email()), false).status());
        assertEquals(403, client.send("POST", "/password-resets/confirm", Map.of("token", "invalid", "password", UPDATED), false).status());
        assertEquals(0, unusedTokens(account.id()));
        assertNull(InboxHolder.INBOX.next(Duration.ofMillis(100)));
    }

    private record Account(UUID id, String email) {}
    private Account account() {
        String email = "recovery-" + UUID.randomUUID() + "@example.test";
        return new Account(identity.create(email, ORIGINAL).id, email);
    }

    private String requestCode(Client client, String email) throws Exception {
        Reply reply = client.request(email);
        assertEquals(202, reply.status(), reply.body());
        LocalSmtpInbox.Message message = InboxHolder.INBOX.next(Duration.ofSeconds(5));
        assertNotNull(message);
        assertEquals(email.toLowerCase(Locale.ROOT), message.recipient());
        String token = code(message);
        assertFalse(reply.body().contains(token));
        return token;
    }

    private static String code(LocalSmtpInbox.Message message) {
        var match = Pattern.compile("(?m)^[A-Za-z0-9_-]{43}\\r?$").matcher(message.text());
        assertTrue(match.find(), "El correo debe incluir un código de recuperación");
        return match.group().trim();
    }

    private Connection database() throws SQLException {
        return DriverManager.getConnection(System.getenv("M2_ROLE_TEST_JDBC_URL"), "postgres", System.getenv("M2_ROLE_TEST_DB_PASSWORD"));
    }

    private boolean tokenExists(String token, UUID user) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        try (Connection connection = database(); PreparedStatement sql = connection.prepareStatement("SELECT count(*) FROM m2.password_reset_tokens WHERE token_hash=? AND user_id=?")) {
            sql.setString(1, hash);
            sql.setObject(2, user);
            try (ResultSet result = sql.executeQuery()) { result.next(); return result.getInt(1) == 1; }
        }
    }

    private long unusedTokens(UUID user) throws Exception {
        try (Connection connection = database(); PreparedStatement sql = connection.prepareStatement("SELECT count(*) FROM m2.password_reset_tokens WHERE user_id=? AND used_at IS NULL")) {
            sql.setObject(1, user);
            try (ResultSet result = sql.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private record Reply(int status, String body, JsonNode json) {}
    private class Client {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Reply login(String email, String password) throws Exception { return send("POST", "/sessions", Map.of("email", email, "password", password), true); }
        Reply request(String email) throws Exception { return send("POST", "/password-resets", Map.of("email", email), true); }
        Reply confirm(String token, String password) throws Exception { return send("POST", "/password-resets/confirm", Map.of("token", token, "password", password), true); }
        Reply send(String method, String path, Object body, boolean csrf) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path)).timeout(Duration.ofSeconds(30));
            if (csrf && !method.equals("GET")) {
                JsonNode token = send("GET", "/session/csrf", null, false).json().path("data");
                request.header(token.path("header_name").asText(), token.path("token").asText());
            }
            if (body != null) request.header("Content-Type", "application/json");
            HttpResponse<String> reply = http.send(request.method(method, HttpRequest.BodyPublishers.ofString(
                    body == null ? "" : mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(reply.statusCode(), reply.body(), reply.body().isEmpty() ? mapper.nullNode() : mapper.readTree(reply.body()));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary TestClock recoveryClock() { return new TestClock(); }
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-09T12:00:00Z"));
        void advance(Duration duration) { now.updateAndGet(instant -> instant.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }
}
