import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.Calidad.Dominio.CsvParser;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.dataanaliticsent.Calidad.Infraestructura.CsvFileStore;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyRotationTest {
    @TempDir Path directory;

    private M2Properties settings(String id, byte key) {
        var settings = new M2Properties();
        settings.getSecurity().setDataKeyId(id);
        settings.getSecurity().setDataKeyBase64(encoded(key));
        settings.getSecurity().setEmailLookupKeyBase64(encoded((byte) 9));
        return settings;
    }

    private String encoded(byte value) {
        byte[] bytes = new byte[32]; Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test void newWritesUseNewKeyWhileOldCsvAndManifestRemainReadableAfterRestore() throws Exception {
        var old = settings("old-v1", (byte) 1);
        var original = new CsvFileStore(directory.resolve("original").toString(), new SensitiveDataCipher(old), new ObjectMapper().findAndRegisterModules(), new M2Properties());
        UUID project = UUID.randomUUID(), asset = UUID.randomUUID();
        var meta = new CsvFileStore.Metadata(project, asset, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Clientes", "clientes.csv", "COMMA", List.of("id"), Map.of("id", CsvParser.ColumnType.TEXT), "a".repeat(64), 5, 1, Instant.now());
        byte[] content = "id\n1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        original.write(meta, content);
        var rotated = settings("new-v2", (byte) 2);
        rotated.getSecurity().setPreviousDataKeys(Map.of("old-v1", encoded((byte) 1)));
        rotated.validateCryptographicKeys();
        var cipher = new SensitiveDataCipher(rotated);
        assertTrue(cipher.encrypt("nuevo", "test", project.toString(), asset.toString()).startsWith("v1:new-v2:"));
        Path restoredDirectory = Files.createDirectory(directory.resolve("restored"));
        try (var files = Files.list(directory.resolve("original"))) {
            for (Path file : files.toList()) Files.copy(file, restoredDirectory.resolve(file.getFileName()));
        }
        var restored = new CsvFileStore(restoredDirectory.toString(), cipher, new ObjectMapper().findAndRegisterModules(), new M2Properties());
        assertEquals(meta, restored.metadata(project, asset));
        assertArrayEquals(content, restored.bytes(meta, 100));
        assertThrows(SecurityException.class, () -> cipher.decrypt(
                new SensitiveDataCipher(old).encrypt("dato", "csv_content", project.toString(), asset.toString()),
                "csv_content", UUID.randomUUID().toString(), asset.toString()));
        restored.removeContent(project, asset);
        assertTrue(restored.exists(project, asset), "El catálogo conserva solo los metadatos");
        assertThrows(IllegalStateException.class, () -> restored.bytes(meta, 100));
    }

    @Test void duplicateActiveIdAndMalformedKeysAreRejectedWithoutDisclosingSecret() {
        var settings = settings("active", (byte) 1);
        settings.getSecurity().setPreviousDataKeys(Map.of("active", encoded((byte) 2)));
        assertThrows(IllegalStateException.class, settings::validateCryptographicKeys);
        settings.getSecurity().setPreviousDataKeys(Map.of("bad:id", "secret-invalid"));
        var error = assertThrows(IllegalStateException.class, settings::validateCryptographicKeys);
        assertFalse(error.toString().contains("secret-invalid"));
    }
}
