package org.dataanaliticsent.Calidad.Presentacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.dataanaliticsent.Calidad.Aplicacion.CsvImportService;
import org.dataanaliticsent.Calidad.Dominio.CsvParser;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.net.URI;
import java.security.Principal;
import static org.dataanaliticsent.Calidad.Presentacion.Utilidades.ApiContext.*;
import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Presentacion.QualityViews.map;

@Tag(name = "Archivos CSV")
@RestController
@RequestMapping("/api/v1/projects/{project}/csv-inputs")
@ConditionalOnProperty(name = "m2.csv.enabled", havingValue = "true")
public class CsvImportController {
    private final CsvImportService service;
    private final ObjectMapper mapper;

    public CsvImportController(CsvImportService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @Operation(
            summary = "Revisar estructura del CSV",
            description = "QUALITY_MANAGER del proyecto. UTF-8, máximo 10 MB; hasta cinco filas de vista previa.")
    public ApiResponse<?> preview(@PathVariable UUID project, @RequestParam MultipartFile file, @RequestParam(defaultValue = "AUTO") String separator, Principal principal, HttpServletRequest request) throws java.io.IOException {
        service.authorize(actor(principal), project);
        byte[] bytes = file.getBytes();
        var parsed = service.parse(bytes, separator);
        return ApiResponse.of(map("headers", parsed.headers(), "row_count", parsed.records().size(), "byte_count", bytes.length, "sha256", CsvImportService.sha256(bytes), "preview_rows", parsed.records().stream().limit(5).toList()), RequestIdFilter.resolveRequestId(request));
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(
            summary = "Importar CSV o reutilizar captura",
            description = "QUALITY_MANAGER del proyecto. 201 para una entrada nueva; 200 para contenido y tipos ya importados.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Operación completada o aceptada."))
    public ResponseEntity<?> upload(@PathVariable UUID project, @RequestParam MultipartFile file, @RequestParam(required = false) String name, @RequestParam(defaultValue = "AUTO") String separator, @RequestParam(name = "column_types", defaultValue = "{}") String columnTypes, Principal principal, HttpServletRequest request) throws java.io.IOException {
        UUID actor = actor(principal);
        var projectContext = service.authorize(actor, project);
        Map<String, CsvParser.ColumnType> types;
        try {
            types = mapper.readValue(columnTypes, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw invalid("Los tipos de columna deben ser un objeto con valores TEXT o NUMBER.");
        }
        if (types == null || types.values().stream().anyMatch(Objects::isNull))
            throw invalid("Define un tipo válido para cada columna.");
        var result = service.importFile(actor, project, file.getOriginalFilename(), name, separator, types, file.getBytes(), requestId(request));
        var m = result.metadata();
        return ResponseEntity.status(result.reused() ? 200 : 201).location(URI.create("/api/v1/projects/" + project + "/assets?asset_id=" + m.assetId())).body(ApiResponse.of(map("id", m.assetId(), "project_id", project, "organization_id", projectContext.organizationId, "source_id", m.sourceId(), "metadata_version_id", m.metadataVersionId(), "name", m.name(), "fields", m.headers(), "source_kind", "CSV", "snapshot_id", m.snapshotId(), "row_count", m.rowCount(), "sha256", m.sha256(), "import_reused", result.reused()), RequestIdFilter.resolveRequestId(request)));
    }
}

