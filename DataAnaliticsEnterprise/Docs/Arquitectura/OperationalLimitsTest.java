import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.Calidad.Dominio.QualityEngine;
import org.dataanaliticsent.Calidad.Infraestructura.CsvFileStore;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.*;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationalLimitsTest {
    @TempDir Path directory;

    private QualityEngine.Rule required(List<String> fields) {
        return new QualityEngine.Rule(UUID.randomUUID(), QualityEngine.Dimension.COMPLETENESS, QualityEngine.Kind.REQUIRED,
                fields, BigDecimal.valueOf(100), BigDecimal.valueOf(100), false, true,
                null,null,null,null,null,null,null,null,null);
    }

    @Test void workloadCountsEveryFieldAndRuleAndRejectsBeforeReturningPartialMetrics() {
        var data = new QualityEngine.Input(List.of(Map.of(), Map.of()), Set.of("a", "b"), true, Map.of());
        var rules = List.of(required(List.of("a","b")), required(List.of("a","b")));
        var error = assertThrows(IllegalStateException.class, () -> new QualityEngine().evaluate(data, rules, Instant.now(), Duration.ofSeconds(10), 7, 100));
        assertEquals("EVALUATION_WORK_LIMIT", error.getMessage());
        error = assertThrows(IllegalStateException.class, () -> new QualityEngine().evaluate(data, rules, Instant.now(), Duration.ofSeconds(10), 8, 7));
        assertEquals("EVALUATION_FINDING_LIMIT", error.getMessage());
        assertEquals(8, new QualityEngine().evaluate(data, rules, Instant.now(), Duration.ofSeconds(10), 8, 8).results().stream().mapToInt(r -> r.findings().size()).sum());
    }

    @Test void anonymousSessionLimitIsSeparateFromLoginAndAlsoHasAGlobalBound() {
        var limiter = new LoginRateLimiter(Clock.fixed(Instant.now(), ZoneOffset.UTC));
        for (int i=0; i<20; i++) limiter.checkAnonymousSession("one-source");
        assertThrows(ApiException.class, () -> limiter.checkAnonymousSession("one-source"));
        for (int i=0; i<10; i++) limiter.check("one-source");
        assertThrows(ApiException.class, () -> limiter.check("one-source"));
        for (int i=0; i<980; i++) limiter.checkAnonymousSession("source-" + i);
        assertThrows(ApiException.class, () -> limiter.checkAnonymousSession("another-source"));
    }

    @Test void storageQuotaAndDiskReserveRejectBeforeWritingOrRemovingExistingContent() throws Exception {
        Path original = Files.writeString(directory.resolve("existing.data.enc"), "existing encrypted file");
        byte[] data = new byte[32], hmac = new byte[32]; Arrays.fill(hmac, (byte) 2);
        var properties = new M2Properties();
        var store = new CsvFileStore(directory.toString(), new SensitiveDataCipher("test", Map.of("test", data), hmac), new ObjectMapper(), properties);
        var meta = new CsvFileStore.Metadata(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "CSV", "data.csv", "COMMA", List.of("a"), Map.of(), "a".repeat(64), 1, 0, Instant.now());
        properties.getLimits().setMaxStoredCsvBytes(1);
        assertEquals(507, assertThrows(ApiException.class, () -> store.write(meta, new byte[]{1})).getStatus().value());
        properties.getLimits().setMaxStoredCsvBytes(1000000000);
        properties.getLimits().setMinFreeDiskBytes(Long.MAX_VALUE);
        assertEquals(507, assertThrows(ApiException.class, () -> store.write(meta, new byte[]{1})).getStatus().value());
        assertEquals("existing encrypted file", Files.readString(original));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
