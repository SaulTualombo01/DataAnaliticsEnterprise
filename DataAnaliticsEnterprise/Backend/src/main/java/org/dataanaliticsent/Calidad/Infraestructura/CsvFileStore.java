package org.dataanaliticsent.Calidad.Infraestructura;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.Calidad.Dominio.CsvParser;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Repositorio local de entradas, cifrado y con nombres generados por el servidor.
 */
@Component
public class CsvFileStore {
    public record Metadata(UUID projectId, UUID assetId, UUID sourceId, UUID metadataVersionId, UUID readId,
                           UUID snapshotId,
                           String name, String filename, String separator, List<String> headers,
                           Map<String, CsvParser.ColumnType> columnTypes,
                           String sha256, long byteCount, long rowCount, Instant acquiredAt) {
    }

    private final Path directory;
    private final SensitiveDataCipher cipher;
    private final ObjectMapper mapper;
    private final M2Properties properties;

    public CsvFileStore(@Value("${m2.csv.directory:./target/csv-inputs}") String directory, SensitiveDataCipher cipher, ObjectMapper mapper, M2Properties properties) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.cipher = cipher;
        this.mapper = mapper;
        this.properties = properties;
    }

    public boolean exists(UUID project, UUID asset) {
        return Files.isRegularFile(path(project, asset, "meta"), LinkOption.NOFOLLOW_LINKS);
    }

    public Metadata metadata(UUID project, UUID asset) {
        try {
            Path path = path(project, asset, "meta");
            if (Files.size(path) > 262144) throw new IllegalStateException("CSV_METADATA_INVALID");
            Metadata metadata = mapper.readValue(cipher.decrypt(Files.readString(path), "csv_metadata", project.toString(), asset.toString()), Metadata.class);
            if (!metadata.projectId.equals(project) || !metadata.assetId.equals(asset))
                throw new IllegalStateException("CSV_METADATA_INVALID");
            return metadata;
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    public byte[] bytes(Metadata metadata, long maxBytes) {
        try {
            Path file = path(metadata.projectId, metadata.assetId, "data");
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > maxBytes * 4 + 65536)
                throw new IllegalStateException("CSV_INPUT_UNAVAILABLE");
            return Base64.getDecoder().decode(cipher.decrypt(Files.readString(file), "csv_content", metadata.projectId.toString(), metadata.assetId.toString()));
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    public synchronized void write(Metadata metadata, byte[] bytes) {
        try {
            Files.createDirectories(directory);
            var usage = usage();
            long reserved = Math.addExact(Math.multiplyExact((long) bytes.length, 2), 524288);
            if (usage.storedBytes() + reserved > properties.getLimits().getMaxStoredCsvBytes()
                    || usage.freeBytes() - reserved < properties.getLimits().getMinFreeDiskBytes())
                throw new ApiException(HttpStatus.INSUFFICIENT_STORAGE, "CSV_STORAGE_LIMIT", "Almacenamiento no disponible.", "El almacenamiento de CSV alcanzó el límite o su reserva de espacio. Contacta al administrador.");
            write(path(metadata.projectId, metadata.assetId, "data"), cipher.encrypt(Base64.getEncoder().encodeToString(bytes), "csv_content", metadata.projectId.toString(), metadata.assetId.toString()));
            write(path(metadata.projectId, metadata.assetId, "meta"), cipher.encrypt(mapper.writeValueAsString(metadata), "csv_metadata", metadata.projectId.toString(), metadata.assetId.toString()));
        } catch (ApiException error) {
            throw error;
        } catch (Exception ex) {
            remove(metadata.projectId, metadata.assetId);
            throw unavailable(ex);
        }
    }

    public record Usage(long storedBytes, long freeBytes) {}

    public Usage usage() throws java.io.IOException {
        Path existing = directory;
        while (!Files.exists(existing)) existing = existing.getParent();
        long stored = 0;
        if (Files.isDirectory(directory)) {
            try (var files = Files.list(directory)) {
                for (var file : (Iterable<Path>) files::iterator)
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) stored = Math.addExact(stored, Files.size(file));
            }
        }
        return new Usage(stored, Files.getFileStore(existing).getUsableSpace());
    }

    private void write(Path target, String content) throws java.io.IOException {
        Path temporary = Files.createTempFile(directory, "pending-", ".enc");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void remove(UUID project, UUID asset) {
        try {
            Files.deleteIfExists(path(project, asset, "meta"));
            Files.deleteIfExists(path(project, asset, "data"));
        } catch (java.io.IOException ignored) {
        }
    }

    /** El manifiesto cifrado conserva catálogo y esquema; nunca contiene celdas del CSV. */
    public void removeContent(UUID project, UUID asset) throws java.io.IOException {
        Files.deleteIfExists(path(project, asset, "data"));
    }

    private Path path(UUID project, UUID asset, String kind) {
        return directory.resolve(project + "_" + asset + "." + kind + ".enc");
    }

    private IllegalStateException unavailable(Exception ex) {
        return new IllegalStateException("CSV_INPUT_UNAVAILABLE", ex);
    }
}
