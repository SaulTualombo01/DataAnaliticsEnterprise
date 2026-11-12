package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Dominio.CsvParser;
import org.dataanaliticsent.Calidad.Infraestructura.*;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.*;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

@Service
@ConditionalOnProperty(name = "m2.csv.enabled", havingValue = "true")
public class CsvImportService {
    public record ImportResult(CsvFileStore.Metadata metadata, boolean reused) {
    }

    private final ProjectTransaction tx;
    private final AccessService access;
    private final QualityRepository repo;
    private final CsvFileStore store;
    private final M2Properties properties;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final RuleService rules;
    private final InputRetention retention;

    public CsvImportService(ProjectTransaction tx, AccessService access, QualityRepository repo, CsvFileStore store, M2Properties properties, Clock clock, ObjectMapper mapper, RuleService rules, InputRetention retention) {
        this.tx = tx;
        this.access = access;
        this.repo = repo;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.mapper = mapper;
        this.rules = rules;
        this.retention = retention;
    }

    public Project authorize(UUID actor, UUID project) {
        return tx.execute(actor, project, () -> access.require(actor, project, null, Action.MANAGE_QUALITY));
    }

    public CsvParser.Parsed parse(byte[] bytes, String separator) {
        try {
            return new CsvParser().parse(bytes, separator, properties.getLimits().getMaxInputRows(), properties.getLimits().getMaxInputBytes());
        } catch (CsvParser.LimitException ex) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV_LIMIT_EXCEEDED", "CSV demasiado grande.", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw invalid(ex.getMessage());
        }
    }

    public ImportResult importFile(UUID actor, UUID project, String filename, String name, String separator, Map<String, CsvParser.ColumnType> types, byte[] bytes, UUID request) {
        authorize(actor, project);
        if (filename == null || filename.length() > 200 || !filename.toLowerCase(Locale.ROOT).endsWith(".csv") || filename.contains("/") || filename.contains("\\") || filename.codePoints().anyMatch(Character::isISOControl))
            throw invalid("Selecciona un archivo con extensión .csv y nombre válido.");
        String title = name == null || name.isBlank() ? filename : name.trim();
        if (title.length() > 160 || title.codePoints().anyMatch(Character::isISOControl))
            throw invalid("El nombre del conjunto admite hasta 160 caracteres.");
        var parsed = parse(bytes, separator);
        try {
            parsed.input(types);
        } catch (IllegalArgumentException ex) {
            throw invalid(ex.getMessage());
        }
        String actualSeparator = parsed.delimiter() == ',' ? "COMMA" : parsed.delimiter() == ';' ? "SEMICOLON" : "TAB";
        String hash = sha256(bytes);
        Map<String, CsvParser.ColumnType> canonicalTypes = new LinkedHashMap<>();
        parsed.headers().forEach(h -> canonicalTypes.put(h, types.getOrDefault(h, CsvParser.ColumnType.TEXT)));
        // Serializa la comprobación y creación también entre varias instancias del backend.
        UUID asset = UUID.randomUUID();
        boolean[] written = {false};
        try {
            return tx.execute(actor, project, () -> {
                var context = access.require(actor, project, null, Action.MANAGE_QUALITY);
                repo.lock(context);
                for (var existing : repo.csvInputsByHash(project, hash)) {
                    if (retention.expired(existing) || !store.exists(project, existing.assetId))
                        continue;
                    var previous = store.metadata(project, existing.assetId);
                    boolean sameTypes = parsed.headers().stream().allMatch(h -> canonicalTypes.get(h) == previous.columnTypes().getOrDefault(h, CsvParser.ColumnType.TEXT));
                    if (previous.separator().equals(actualSeparator) && previous.headers().equals(parsed.headers()) && sameTypes) {
                        rules.audit(actor, project, request, "CSV_REUSED", "input_ref", existing.id);
                        return new ImportResult(previous, true);
                    }
                }
                UUID metadata = UUID.randomUUID(), read = UUID.randomUUID(), snapshot = UUID.randomUUID();
                var manifest = new CsvFileStore.Metadata(project, asset, UUID.nameUUIDFromBytes(("m2-local-csv:" + project).getBytes(StandardCharsets.UTF_8)), metadata, read, snapshot, title, filename, actualSeparator, parsed.headers(), Map.copyOf(canonicalTypes), hash, bytes.length, parsed.records().size(), clock.instant());
                store.write(manifest, bytes);
                written[0] = true;
                AssetRef assetRef = new AssetRef();
                assetRef.id = UUID.randomUUID();
                assetRef.projectId = project;
                assetRef.assetId = asset;
                assetRef.sourceId = manifest.sourceId();
                assetRef.createdAt = clock.instant();
                repo.save(assetRef);
                InputRef input = new InputRef();
                input.id = UUID.randomUUID();
                input.projectId = project;
                input.assetId = asset;
                input.readId = read;
                input.snapshotId = snapshot;
                input.metadataVersionId = metadata;
                input.scopeDefinition = mapper.valueToTree(Map.of("kind", "CSV_FULL_INPUT", "filename", filename, "headers", parsed.headers(), "separator", actualSeparator, "column_types", types, "row_reference", "PHYSICAL_FILE_LINE"));
                input.complete = true;
                input.rowCount = manifest.rowCount();
                input.byteCount = manifest.byteCount();
                input.acquiredAt = manifest.acquiredAt();
                input.expiresAt = retention.csvExpiresAt(manifest.acquiredAt());
                input.contentSha256 = manifest.sha256();
                input.protectedObjectRef = "local-csv:" + snapshot;
                input.availability = "AVAILABLE";
                input.createdAt = clock.instant();
                repo.save(input);
                rules.audit(actor, project, request, "CSV_IMPORTED", "input_ref", input.id);
                return new ImportResult(manifest, false);
            });
        } catch (RuntimeException ex) {
            if (written[0]) store.remove(project, asset);
            throw ex;
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

