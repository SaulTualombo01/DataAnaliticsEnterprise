import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.DataAnaliticsEnterprise;
import org.dataanaliticsent.Calidad.Aplicacion.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP y PostgreSQL reales. El script de verificación crea y destruye la base aislada. */
@SpringBootTest(classes = DataAnaliticsEnterprise.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "M2_ROLE_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleRevocationIntegrationTest {
    private static final UUID PROJECT = UUID.fromString("68fba609-193c-422e-b2fb-ab4919e23b30");
    private static final UUID OTHER_PROJECT = UUID.fromString("59e734eb-249e-4b78-a15d-418af8225bfe");
    private static final String PASSWORD = "Role-Revocation-Test-2026";
    private static final Path SQL_ROOT = Path.of("Backend/src/main/java/org/dataanaliticsent/Calidad/Docker");
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired IdentityService identity;
    private Client admin;
    private UUID adminId;
    private boolean migrationPreservedData;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        String url = System.getenv("M2_ROLE_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/m2_role_test"))
            throw new IllegalArgumentException("La regresión requiere la base local aislada m2_role_test.");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "m2_app");
        properties.add("spring.datasource.password", () -> System.getenv("M2_ROLE_TEST_DB_PASSWORD"));
        byte[] hmacKey = new byte[32];
        Arrays.fill(hmacKey, (byte) 2);
        properties.add("m2.security.data-key-id", () -> "role-test");
        properties.add("m2.security.data-key-base64", () -> Base64.getEncoder().encodeToString(new byte[32]));
        properties.add("m2.security.email-lookup-key-base64", () -> Base64.getEncoder().encodeToString(hmacKey));
        properties.add("m2.bootstrap.enabled", () -> true);
        properties.add("m2.bootstrap.project-id", PROJECT::toString);
        properties.add("m2.bootstrap.organization-id", UUID.randomUUID()::toString);
        properties.add("m2.bootstrap.project-name", () -> "Prueba aislada de revocación");
        properties.add("m2.bootstrap.admin-email", () -> "admin@role-test.example");
        properties.add("m2.bootstrap.admin-password", () -> PASSWORD);
        properties.add("m2.bootstrap.quality-email", () -> "quality@role-test.example");
        properties.add("m2.bootstrap.quality-password", () -> PASSWORD);
        properties.add("m2.bootstrap.worker-email", () -> "worker@role-test.example");
        properties.add("m2.worker.user-id", () -> "");
    }

    @BeforeAll
    void migrateExistingDatabase() throws Exception {
        admin = new Client();
        admin.login("admin@role-test.example");
        adminId = UUID.fromString(admin.send("GET", "/api/v1/me", null, true).json().path("data").path("id").asText());
        String previousEmail = "legacy@role-test.example";
        UUID previousUser = createUser(previousEmail);
        UUID previousRole = roleOf(previousUser);
        Client previousSession = new Client();
        previousSession.login(previousEmail);
        try (Connection connection = database("postgres"); Statement sql = connection.createStatement()) {
            // Reproduce exactamente la política y el privilegio anteriores sobre datos existentes.
            sql.execute("REVOKE DELETE ON m2.role_assignments FROM m2_backend");
            sql.execute("DROP POLICY backend_identity_read ON m2.role_assignments");
            sql.execute("DROP POLICY backend_identity_insert ON m2.role_assignments");
            sql.execute("DROP POLICY backend_identity_update ON m2.role_assignments");
            sql.execute("DROP POLICY admin_role_delete ON m2.role_assignments");
            sql.execute("CREATE POLICY backend_identity ON m2.role_assignments FOR ALL TO m2_backend USING (true) WITH CHECK (true)");
            assertFalse(booleanQuery(connection, "SELECT has_table_privilege('m2_app', 'm2.role_assignments', 'DELETE')"));
            assertEquals(500, admin.send("DELETE", roles(PROJECT) + "/" + previousRole, null, true).status(), "El esquema anterior reproduce el fallo");
            assertEquals(200, previousSession.send("GET", evaluations(PROJECT), null, true).status(), "La revocación fallida deja el acceso vigente");
            List<String> before = assignments(connection);
            String migration = Files.readString(SQL_ROOT.resolve("migrate-role-revocation.sql"));
            sql.execute(migration);
            sql.execute(migration);
            migrationPreservedData = before.equals(assignments(connection));
            assertTrue(booleanQuery(connection, "SELECT has_table_privilege('m2_app', 'm2.role_assignments', 'DELETE')"));
            assertEquals(204, admin.send("DELETE", roles(PROJECT) + "/" + previousRole, null, true).status(), "La misma revocación funciona tras migrar");
            assertEquals(401, previousSession.send("GET", evaluations(PROJECT), null, true).status());
            sql.execute("INSERT INTO m2.projects (id, organization_id, name, active, created_at, revision) VALUES ('" + OTHER_PROJECT + "', '" + UUID.randomUUID() + "', 'Otro proyecto de prueba', true, now(), 0)");
        }
    }

    @Test
    void migrationIsRepeatableAndPreservesExistingAssignments() {
        assertTrue(migrationPreservedData);
    }

    @Test
    void revocationInvalidatesPreviousSessionAndAuditsOnlyTheRequestedRole() throws Exception {
        String email = "revoked-" + UUID.randomUUID() + "@role-test.example";
        UUID userId = createUser(email);
        UUID roleId = roleOf(userId);
        UUID otherRole = UUID.randomUUID();
        try (Connection connection = database("postgres"); PreparedStatement sql = connection.prepareStatement(
                "INSERT INTO m2.role_assignments (id, project_id, user_id, role_name, created_at) VALUES (?, ?, ?, 'BUSINESS_ANALYST', now())")) {
            sql.setObject(1, otherRole);
            sql.setObject(2, OTHER_PROJECT);
            sql.setObject(3, userId);
            sql.executeUpdate();
        }
        Client manager = new Client();
        manager.login(email);
        assertEquals(200, manager.send("GET", evaluations(PROJECT), null, true).status());
        String route = roles(PROJECT) + "/" + roleId;
        assertEquals(403, admin.send("DELETE", route, null, false).status(), "Revocar exige CSRF");
        assertEquals(200, manager.send("GET", evaluations(PROJECT), null, true).status());
        assertEquals(204, admin.send("DELETE", route, null, true).status());
        assertEquals(401, manager.send("GET", evaluations(PROJECT), null, true).status(), "La cookie previa deja de autenticar");
        manager.login(email);
        assertEquals(403, manager.send("GET", evaluations(PROJECT), null, true).status(), "Reautenticarse no recupera el rol");
        assertEquals(200, manager.send("GET", evaluations(OTHER_PROJECT), null, true).status(), "Conserva el rol de otro proyecto");
        assertEquals(404, admin.send("DELETE", route, null, true).status(), "Repetir no duplica auditoría");
        try (Connection connection = database("postgres"); PreparedStatement sql = connection.prepareStatement(
                "SELECT actor_id, outcome FROM m2.audit_events WHERE action='ROLE_REVOKED' AND project_id=? AND resource_id=?")) {
            sql.setObject(1, PROJECT);
            sql.setObject(2, roleId);
            try (ResultSet events = sql.executeQuery()) {
                assertTrue(events.next());
                assertEquals(adminId, events.getObject(1, UUID.class));
                assertEquals("SUCCESS", events.getString(2));
                assertFalse(events.next());
            }
            try (PreparedStatement version = connection.prepareStatement("SELECT auth_version FROM m2.users WHERE id=?")) {
                version.setObject(1, userId);
                try (ResultSet result = version.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getLong(1));
                }
            }
        }
    }

    @Test
    void httpAndDatabaseRejectRevocationWithoutProjectAdministration() throws Exception {
        String email = "restricted-" + UUID.randomUUID() + "@role-test.example";
        UUID userId = createUser(email);
        UUID roleId = roleOf(userId);
        Client manager = new Client();
        manager.login(email);
        assertEquals(403, manager.send("DELETE", roles(PROJECT) + "/" + roleId, null, true).status());
        assertEquals(403, admin.send("DELETE", roles(OTHER_PROJECT) + "/" + roleId, null, true).status());
        assertEquals(0, deleteAs(userId, PROJECT, roleId), "RLS rechaza el gestor de calidad aun con DELETE de tabla");
        assertEquals(0, deleteAs(adminId, OTHER_PROJECT, roleId), "RLS rechaza el contexto de otro proyecto");
        assertEquals(roleId, roleOf(userId));
    }

    @Test
    void cannotRevokeTheLastAdministrator() throws Exception {
        UUID roleId = roleOf(adminId);
        assertEquals(422, admin.send("DELETE", roles(PROJECT) + "/" + roleId, null, true).status());
        assertEquals(roleId, roleOf(adminId));
    }

    @Test
    void simultaneousCrossProjectDeactivationPreservesAnActiveAdministrator() throws Exception {
        Administrators admins = administrators(2, true);
        UUID firstProject = admins.projects().get(0);
        UUID secondProject = admins.projects().get(1);
        String firstRevision = revisionOf(admins.firstClient(), firstProject, admins.first());
        String secondRevision = revisionOf(admins.secondClient(), secondProject, admins.second());

        List<Reply> replies = contend(admins,
                () -> deactivate(admins.firstClient(), firstProject, admins.second(), secondRevision),
                () -> deactivate(admins.secondClient(), secondProject, admins.first(), firstRevision));

        assertEquals(List.of(200, 403), replies.stream().map(Reply::status).sorted().toList(),
                "Solo el primer administrador autorizado puede confirmar el cambio");
        for (UUID project : admins.projects()) assertEquals(1, activeAdministrators(project));
        assertEquals(1, administrationEvents(admins, "USER_UPDATED"));
    }

    @Test
    void inactiveAssignmentDoesNotAllowTheLastActiveAdministratorToRevokeItself() throws Exception {
        Administrators admins = administrators(1, false);
        UUID project = admins.projects().getFirst();
        UUID role = administratorRole(project, admins.first());
        assertEquals(422, admins.firstClient().send("DELETE", roles(project) + "/" + role, null, true).status());
        assertEquals(1, activeAdministrators(project));
        assertEquals(role, administratorRole(project, admins.first()));
        assertEquals(0, administrationEvents(admins, "ROLE_REVOKED"));
    }

    @Test
    void simultaneousDeactivationAndRevocationRecheckTheWaitingActorsPermission() throws Exception {
        Administrators admins = administrators(2, true);
        UUID project = admins.projects().getFirst();
        UUID firstRole = administratorRole(project, admins.first());
        String secondRevision = revisionOf(admins.firstClient(), project, admins.second());

        List<Reply> replies = contend(admins,
                () -> deactivate(admins.firstClient(), project, admins.second(), secondRevision),
                () -> admins.secondClient().send("DELETE", roles(project) + "/" + firstRole, null, true));

        assertEquals(1, replies.stream().filter(reply -> reply.status() == 403).count());
        assertEquals(1, replies.stream().filter(reply -> reply.status() == 200 || reply.status() == 204).count());
        for (UUID context : admins.projects()) assertTrue(activeAdministrators(context) >= 1);
        assertEquals(1, administrationEvents(admins, "USER_UPDATED") + administrationEvents(admins, "ROLE_REVOKED"));
    }

    private Administrators administrators(int projectCount, boolean secondActive) throws Exception {
        String firstEmail = "admin-first-" + UUID.randomUUID() + "@role-test.example";
        String secondEmail = "admin-second-" + UUID.randomUUID() + "@role-test.example";
        UUID first = identity.create(firstEmail, PASSWORD).id;
        UUID second = identity.create(secondEmail, PASSWORD).id;
        List<UUID> projects = new ArrayList<>();
        try (Connection connection = database("postgres")) {
            for (int i = 0; i < projectCount; i++) {
                UUID project = UUID.randomUUID();
                projects.add(project);
                try (PreparedStatement sql = connection.prepareStatement(
                        "INSERT INTO m2.projects (id, organization_id, name, active, created_at, revision) VALUES (?, ?, 'Prueba de administradores', true, now(), 0)")) {
                    sql.setObject(1, project);
                    sql.setObject(2, UUID.randomUUID());
                    sql.executeUpdate();
                }
                try (PreparedStatement sql = connection.prepareStatement(
                        "INSERT INTO m2.role_assignments (id, project_id, user_id, role_name, created_at) VALUES (?, ?, ?, 'SYSTEM_ADMIN', now())")) {
                    for (UUID user : List.of(first, second)) {
                        sql.setObject(1, UUID.randomUUID());
                        sql.setObject(2, project);
                        sql.setObject(3, user);
                        sql.executeUpdate();
                    }
                }
            }
            if (!secondActive) {
                try (PreparedStatement sql = connection.prepareStatement("UPDATE m2.users SET active=false WHERE id=?")) {
                    sql.setObject(1, second);
                    sql.executeUpdate();
                }
            }
        }
        Client firstClient = new Client();
        firstClient.login(firstEmail);
        Client secondClient = new Client();
        if (secondActive) secondClient.login(secondEmail);
        return new Administrators(first, second, projects, firstClient, secondClient);
    }

    private record Administrators(UUID first, UUID second, List<UUID> projects, Client firstClient, Client secondClient) {}

    private String revisionOf(Client client, UUID project, UUID user) throws Exception {
        Reply reply = client.send("GET", "/api/v1/users?project_id=" + project, null, true);
        assertEquals(200, reply.status(), reply.body());
        for (JsonNode account : reply.json().path("data"))
            if (user.toString().equals(account.path("id").asText()))
                return "\"" + account.path("revision").asText() + "\"";
        throw new AssertionError("No aparece la cuenta esperada");
    }

    private Reply deactivate(Client client, UUID project, UUID user, String revision) throws Exception {
        return client.send("PATCH", "/api/v1/users/" + user + "?project_id=" + project,
                Map.of("active", false), true, Map.of("If-Match", revision));
    }

    private UUID administratorRole(UUID project, UUID user) throws Exception {
        try (Connection connection = database("postgres"); PreparedStatement sql = connection.prepareStatement(
                "SELECT id FROM m2.role_assignments WHERE project_id=? AND user_id=? AND role_name='SYSTEM_ADMIN'")) {
            sql.setObject(1, project);
            sql.setObject(2, user);
            try (ResultSet result = sql.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private long activeAdministrators(UUID project) throws Exception {
        try (Connection connection = database("postgres"); PreparedStatement sql = connection.prepareStatement(
                "SELECT count(*) FROM m2.role_assignments r JOIN m2.users u ON u.id=r.user_id WHERE r.project_id=? AND r.role_name='SYSTEM_ADMIN' AND u.active")) {
            sql.setObject(1, project);
            try (ResultSet result = sql.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private long administrationEvents(Administrators admins, String action) throws Exception {
        try (Connection connection = database("postgres"); PreparedStatement sql = connection.prepareStatement(
                "SELECT count(*) FROM m2.audit_events WHERE actor_id IN (?, ?) AND action=?")) {
            sql.setObject(1, admins.first());
            sql.setObject(2, admins.second());
            sql.setString(3, action);
            try (ResultSet result = sql.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private List<Reply> contend(Administrators admins, Callable<Reply> first, Callable<Reply> second) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2);
             Connection blocker = database("postgres")) {
            blocker.setAutoCommit(false);
            try (PreparedStatement sql = blocker.prepareStatement("SELECT id FROM m2.users WHERE id IN (?, ?) ORDER BY id FOR UPDATE")) {
                sql.setObject(1, admins.first());
                sql.setObject(2, admins.second());
                sql.execute();
            }
            Future<Reply> firstReply = executor.submit(first);
            Future<Reply> secondReply = executor.submit(second);
            try {
                // Ambos endpoints deben estar esperando dentro de sus transacciones antes de liberar las cuentas.
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                boolean waiting = false;
                try (Connection observer = database("postgres"); Statement sql = observer.createStatement()) {
                    while (!waiting && System.nanoTime() < deadline) {
                        try (ResultSet rows = sql.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND usename='m2_app' AND wait_event_type='Lock'")) {
                            rows.next();
                            waiting = rows.getLong(1) >= 2;
                        }
                        if (!waiting) Thread.sleep(50);
                    }
                }
                assertTrue(waiting, "Las dos solicitudes deben coincidir dentro del flujo de escritura");
            } finally {
                blocker.rollback();
            }
            return List.of(firstReply.get(30, TimeUnit.SECONDS), secondReply.get(30, TimeUnit.SECONDS));
        }
    }

    private UUID createUser(String email) throws Exception {
        Reply reply = admin.send("POST", "/api/v1/users?project_id=" + PROJECT,
                Map.of("email", email, "password", PASSWORD, "role", "QUALITY_MANAGER"), true);
        assertEquals(201, reply.status(), reply.body());
        return UUID.fromString(reply.json().path("data").path("id").asText());
    }

    private UUID roleOf(UUID user) throws Exception {
        Reply reply = admin.send("GET", roles(PROJECT), null, true);
        assertEquals(200, reply.status(), reply.body());
        for (JsonNode role : reply.json().path("data"))
            if (user.toString().equals(role.path("user_id").asText()))
                return UUID.fromString(role.path("id").asText());
        throw new AssertionError("No aparece el rol esperado");
    }

    private int deleteAs(UUID actor, UUID project, UUID role) throws Exception {
        try (Connection connection = database("m2_app")) {
            connection.setAutoCommit(false);
            try (PreparedStatement context = connection.prepareStatement("SELECT set_config('app.user_id', ?, true), set_config('app.project_id', ?, true)")) {
                context.setString(1, actor.toString());
                context.setString(2, project.toString());
                context.execute();
            }
            try (PreparedStatement sql = connection.prepareStatement("DELETE FROM m2.role_assignments WHERE id=?")) {
                sql.setObject(1, role);
                int affected = sql.executeUpdate();
                connection.rollback();
                return affected;
            }
        }
    }

    private Connection database(String username) throws SQLException {
        return DriverManager.getConnection(System.getenv("M2_ROLE_TEST_JDBC_URL"), username,
                System.getenv("M2_ROLE_TEST_DB_PASSWORD"));
    }

    private boolean booleanQuery(Connection connection, String query) throws SQLException {
        try (Statement sql = connection.createStatement(); ResultSet result = sql.executeQuery(query)) {
            assertTrue(result.next());
            return result.getBoolean(1);
        }
    }

    private List<String> assignments(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery("SELECT row_to_json(r)::text FROM m2.role_assignments r ORDER BY id")) {
            List<String> result = new ArrayList<>();
            while (rows.next()) result.add(rows.getString(1));
            return result;
        }
    }

    private static String roles(UUID project) { return "/api/v1/projects/" + project + "/roles"; }
    private static String evaluations(UUID project) { return "/api/v1/projects/" + project + "/quality-evaluations"; }

    private record Reply(int status, String body, JsonNode json) {}

    private class Client {
        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        void login(String email) throws Exception {
            Reply reply = send("POST", "/api/v1/sessions", Map.of("email", email, "password", PASSWORD), true);
            assertEquals(200, reply.status(), reply.body());
        }

        Reply send(String method, String path, Object body, boolean csrf) throws Exception {
            return send(method, path, body, csrf, Map.of());
        }

        Reply send(String method, String path, Object body, boolean csrf, Map<String, String> headers) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(30));
            headers.forEach(request::header);
            if (csrf && !method.equals("GET")) {
                JsonNode token = send("GET", "/api/v1/session/csrf", null, false).json().path("data");
                request.header(token.path("header_name").asText(), token.path("token").asText());
            }
            String json = body == null ? "" : mapper.writeValueAsString(body);
            if (body != null) request.header("Content-Type", "application/json");
            HttpResponse<String> reply = http.send(request.method(method, HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(reply.statusCode(), reply.body(), reply.body().isEmpty() ? mapper.nullNode() : mapper.readTree(reply.body()));
        }
    }
}
