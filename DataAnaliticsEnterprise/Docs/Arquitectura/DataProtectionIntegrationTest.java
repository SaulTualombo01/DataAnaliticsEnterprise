import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.DataAnaliticsEnterprise;
import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Infraestructura.*;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.junit.jupiter.api.Assertions.*;

/** Conserva historial, caduca evidencia y purga bytes con la cuenta SQL m2_app y RLS reales. */
@SpringBootTest(classes = {DataAnaliticsEnterprise.class, DataProtectionIntegrationTest.TimeConfiguration.class})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="M2_ROLE_TEST_JDBC_URL",matches=".+")
class DataProtectionIntegrationTest {
    private static final UUID PROJECT = UUID.fromString("16f6d9ae-f114-4244-a72f-1097d201a246");
    private static final UUID WORKER = UUID.fromString("03029547-65f4-40df-a24b-6c1c379ec97e");
    private static final String PASSWORD = "Protection-Test-Password-2026";
    private static final Path DIRECTORY = temporaryDirectory();
    @Autowired IdentityService identity;
    @Autowired CsvImportService csv;
    @Autowired RuleService rules;
    @Autowired EvaluationService evaluations;
    @Autowired EvidenceService evidence;
    @Autowired CsvRetentionService retention;
    @Autowired CsvFileStore store;
    @Autowired QualityRepository repo;
    @Autowired ProjectTransaction tx;
    @Autowired ObjectMapper mapper;
    @Autowired TestClock clock;

    private static Path temporaryDirectory() {
        try { return Files.createTempDirectory("m2-protection-test-"); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    @DynamicPropertySource static void settings(DynamicPropertyRegistry p) {
        String url = System.getenv("M2_ROLE_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/m2_role_test")) throw new IllegalArgumentException("Solo base aislada de pruebas.");
        p.add("spring.datasource.url", () -> url);
        p.add("spring.datasource.username", () -> "m2_app");
        p.add("spring.datasource.password", () -> System.getenv("M2_ROLE_TEST_DB_PASSWORD"));
        p.add("m2.security.data-key-id", () -> "protection-test");
        p.add("m2.security.data-key-base64", () -> Base64.getEncoder().encodeToString(new byte[32]));
        byte[] hmac = new byte[32]; Arrays.fill(hmac, (byte) 4);
        p.add("m2.security.email-lookup-key-base64", () -> Base64.getEncoder().encodeToString(hmac));
        p.add("m2.csv.directory", DIRECTORY::toString);
        p.add("m2.bootstrap.enabled", () -> true);
        p.add("m2.bootstrap.project-id", PROJECT::toString);
        p.add("m2.bootstrap.organization-id", UUID.randomUUID()::toString);
        p.add("m2.bootstrap.project-name", () -> "Protección aislada");
        p.add("m2.bootstrap.admin-email", () -> "admin@protection.example.test");
        p.add("m2.bootstrap.admin-password", () -> PASSWORD);
        p.add("m2.bootstrap.quality-email", () -> "quality@protection.example.test");
        p.add("m2.bootstrap.quality-password", () -> PASSWORD);
        p.add("m2.bootstrap.worker-email", () -> "worker@protection.example.test");
        p.add("m2.bootstrap.worker-id", WORKER::toString);
        p.add("m2.worker.user-id", WORKER::toString);
        p.add("m2.worker.poll-ms", () -> 3600000);
        p.add("m2.limits.max-pending-per-user", () -> 1);
    }

    @Test void expiredCsvPreservesEvaluationAndIncidentsButNeverDisclosesOriginalValues() {
        UUID actor = identity.authenticate("quality@protection.example.test", PASSWORD).id;
        UUID admin = identity.authenticate("admin@protection.example.test", PASSWORD).id;
        var imported = csv.importFile(actor, PROJECT, "clientes.csv", "Clientes prueba aislada", "COMMA", Map.of(),
                "id,correo\n1,\n2,dos@example.test\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), UUID.randomUUID());
        var meta = imported.metadata();
        var command = mapper.convertValue(Map.of("asset_id",meta.assetId(),"name","Correo requerido", "version",
                Map.of("dimension","COMPLETENESS","definition",Map.of("kind","REQUIRED","fields",List.of("correo"),"blank_is_missing",true),
                        "threshold",100,"minimum_coverage",100,"critical",false)), QualityContracts.CreateRule.class);
        var rule = rules.create(actor, PROJECT, command, UUID.randomUUID());
        var job = evaluations.submit(actor, PROJECT, new QualityContracts.CreateEvaluation(meta.assetId(), meta.metadataVersionId(), List.of(rule.id), meta.snapshotId()), UUID.randomUUID(), UUID.randomUUID());
        assertEquals(1,tx.execute(actor,PROJECT,()->repo.pendingCounts(PROJECT)).user());
        var limited = assertThrows(org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException.class, () ->
                evaluations.submit(actor, PROJECT, new QualityContracts.CreateEvaluation(meta.assetId(),meta.metadataVersionId(),List.of(rule.id),meta.snapshotId()),UUID.randomUUID(),UUID.randomUUID()));
        assertEquals(429,limited.getStatus().value());
        assertThrows(RuntimeException.class, () -> tx.execute(actor,UUID.randomUUID(),()->repo.pendingCounts(PROJECT)));
        evaluations.tick();
        var resultBefore = tx.execute(actor, PROJECT, () -> repo.results(PROJECT,job.id));
        assertEquals(1, resultBefore.size());
        assertEquals(1, resultBefore.getFirst().numerator);
        var incidents = tx.execute(actor, PROJECT, () -> repo.list(Incident.class, PROJECT, meta.assetId(),0,100));
        assertEquals(1, incidents.size());
        UUID incidentId = incidents.getFirst().id;
        assertTrue(evidence.view(actor,PROJECT,incidentId,UUID.randomUUID()).available());
        assertThrows(org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException.class, () -> evidence.view(admin,PROJECT,incidentId,UUID.randomUUID()));
        clock.advance(Duration.ofDays(30));
        assertFalse(evidence.view(actor,PROJECT,incidentId,UUID.randomUUID()).available(), "Caduca incluso antes de la limpieza física");
        retention.cleanupProject(PROJECT);
        retention.cleanupProject(PROJECT);
        assertTrue(store.exists(PROJECT,meta.assetId()));
        assertThrows(IllegalStateException.class, () -> store.bytes(meta,10000));
        assertEquals("COMPLETED", tx.execute(actor,PROJECT,() -> repo.find(Evaluation.class,job.id,PROJECT).orElseThrow()).status);
        assertEquals(resultBefore.getFirst().id, tx.execute(actor,PROJECT,() -> repo.results(PROJECT,job.id)).getFirst().id);
        assertEquals(incidentId,tx.execute(actor,PROJECT,() -> repo.list(Incident.class,PROJECT,meta.assetId(),0,100)).getFirst().id);
        assertEquals("DELETED",tx.execute(actor,PROJECT,() -> repo.latestInput(PROJECT,meta.assetId()).orElseThrow()).availability);
    }

    @AfterAll static void cleanupFiles() throws Exception {
        try (var paths = Files.walk(DIRECTORY)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary TestClock protectionClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        private Instant at = Instant.parse("2026-11-09T12:00:00Z");
        void advance(Duration duration) { at = at.plus(duration); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return at; }
    }
}
