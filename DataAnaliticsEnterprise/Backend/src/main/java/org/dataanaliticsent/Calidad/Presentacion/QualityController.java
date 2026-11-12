package org.dataanaliticsent.Calidad.Presentacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Dominio.CanonicalUuid;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Aplicacion.QualityContracts.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.dataanaliticsent.Calidad.Presentacion.QualityViews.*;
import static org.dataanaliticsent.Calidad.Presentacion.Utilidades.ApiContext.*;

@Tag(name = "Calidad de datos")
@RestController
@RequestMapping("/api/v1/projects/{project}")
public class QualityController {
    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;
    private final RuleService rules;
    private final EvaluationService evaluations;
    private final InputProvider input;
    private final SensitiveDataCipher cipher;
    private final InputRetention retention;
    private final EvidenceService evidence;
    private final IncidentService incidents;
    private final QualityOverviewService overview;

    public QualityController(QualityRepository repo, ProjectTransaction tx, AccessService access, RuleService rules, EvaluationService evaluations, InputProvider input, SensitiveDataCipher cipher, InputRetention retention, EvidenceService evidence, IncidentService incidents, QualityOverviewService overview) {
        this.repo = repo;
        this.tx = tx;
        this.access = access;
        this.rules = rules;
        this.evaluations = evaluations;
        this.input = input;
        this.cipher = cipher;
        this.retention = retention;
        this.evidence = evidence;
        this.incidents = incidents;
        this.overview = overview;
    }

    @GetMapping("/assets")
    @Operation(
            summary = "Consultar activos autorizados",
            description = "CATALOG_MANAGER, QUALITY_MANAGER, GOVERNANCE_MANAGER o BUSINESS_ANALYST.")
    public ApiResponse<?> assets(@PathVariable UUID project, Principal principal, HttpServletRequest request) {
        UUID actor = actor(principal);
        var assignments = repo.roles(actor, project);
        boolean allowed = assignments.stream().anyMatch(r -> permits(r.roleName, r.assetScopeId, r.assetScopeId, Action.CATALOG));
        if (!allowed) throw denied();
        var assets = input.assets(actor, project).stream().filter(a -> assignments.stream().anyMatch(r -> permits(r.roleName, r.assetScopeId, a.id(), Action.CATALOG))).toList();
        Map<String, Long> names = assets.stream().collect(java.util.stream.Collectors.groupingBy(InputProvider.Asset::name, java.util.stream.Collectors.counting()));
        var values = assets.stream().map(a -> tx.execute(actor, project, () -> {
            var context = access.require(actor, project, a.id(), Action.CATALOG);
            return map("id", a.id(), "source_id", a.sourceId(), "metadata_version_id", a.metadataVersionId(), "name", a.name(), "display_name", names.get(a.name()) > 1 ? a.name() + " · " + a.id().toString().substring(0, 8) : a.name(), "fields", a.fields(), "source_kind", a.sourceKind(), "project_id", project, "organization_id", context.organizationId);
        })).toList();
        return response(values, request);
    }

    @GetMapping("/assets/{asset}/quality-overview")
    @Operation(summary = "Consultar últimas mediciones por dimensión",
            description = "READ_RESULTS sobre el activo. Cada dimensión usa su evaluación COMPLETED más reciente por fecha de solicitud e ID. No mezcla reglas de distintas ejecuciones de una misma dimensión ni incluye resultados parciales. latest_evaluation conserva el estado de la última ejecución, incluso pendiente o fallida.")
    public ApiResponse<?> overview(@PathVariable UUID project, @PathVariable UUID asset, Principal principal, HttpServletRequest request) {
        var value = overview.view(actor(principal), project, asset);
        java.util.function.Function<Evaluation, Map<String, Object>> evaluation = e -> {
            var view = of(e);
            if (value.inputs().containsKey(e.inputRefId)) addInput(view, value.inputs().get(e.inputRefId));
            return view;
        };
        Map<String, Object> sources = new LinkedHashMap<>();
        value.dimensionEvaluations().forEach((dimension, e) -> sources.put(dimension, evaluation.apply(e)));
        return response(map("latest_evaluation", value.latestEvaluation() == null ? null : evaluation.apply(value.latestEvaluation()),
                "results", value.results().stream().map(QualityViews::of).toList(),
                "rule_versions", value.ruleVersions().stream().map(QualityViews::of).toList(),
                "dimension_evaluations", sources), request);
    }

    @GetMapping("/quality-rules")
    @Operation(
            summary = "Consultar reglas y versión vigente",
            description = "QUALITY_MANAGER, GOVERNANCE_MANAGER o BUSINESS_ANALYST. Filtros aplicados antes de paginar.")
    public ApiResponse<?> listRules(@PathVariable UUID project, @RequestParam(name = "asset_id", required = false) UUID asset, @RequestParam(name = "q", required = false) String search, @RequestParam(required = false) Boolean enabled, @RequestParam(defaultValue = "1") int page, @RequestParam(name = "page_size", defaultValue = "25") int size, Principal principal, HttpServletRequest request) {
        var paging = new PageQuery(page, size);
        if (search != null && search.length() > 160) throw invalid("La búsqueda admite hasta 160 caracteres.");
        return tx.execute(actor(principal), project, () -> {
            var context = access.require(actor(principal), project, asset, Action.READ_RESULTS);
            var rows = repo.rules(project, asset, search, enabled, paging.offset(), size + 1);
            var versions = repo.latestVersions(project, rows.stream().limit(size).map(r -> r.id).toList());
            var values = rows.stream().limit(size).map(r -> {
                var view = of(r);
                view.put("organization_id", context.organizationId);
                view.put("current_version", versions.containsKey(r.id) ? of(versions.get(r.id)) : null);
                return view;
            }).toList();
            return ApiResponse.ofPage(values, RequestIdFilter.resolveRequestId(request), paging.info(values.size(), rows.size() > size));
        });
    }

    @GetMapping("/assets/{asset}/input")
    @Operation(
            summary = "Consultar metadatos de entrada sin filas",
            description = "QUALITY_MANAGER del activo.")
    public ApiResponse<?> inputMetadata(@PathVariable UUID project, @PathVariable UUID asset, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            access.require(actor(principal), project, asset, Action.MANAGE_QUALITY);
            var ref = repo.latestInput(project, asset).orElseThrow(AccessService::missing);
            var view = map("asset_id", asset, "input_ref_id", ref.id);
            addInput(view, ref);
            return response(view, request);
        });
    }

    @PostMapping("/quality-rules")
    @Operation(
            summary = "Crear regla y primera versión",
            description = "QUALITY_MANAGER del activo.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Operación completada o aceptada."))
    public ResponseEntity<?> create(@PathVariable UUID project, @Valid @RequestBody CreateRule command, Principal principal, HttpServletRequest request) {
        var value = rules.create(actor(principal), project, command, requestId(request));
        return ResponseEntity.created(URI.create(base(project) + "/quality-rules/" + value.id)).eTag("\"" + value.revision + "\"").body(response(of(value), request));
    }

    @GetMapping("/quality-rules/{id}")
    @Operation(
            summary = "Consultar una regla y sus versiones",
            description = "Lectores autorizados del activo.")
    public ResponseEntity<?> rule(@PathVariable UUID project, @PathVariable UUID id, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            var r = repo.find(QualityRule.class, id, project).orElseThrow(AccessService::missing);
            access.require(actor(principal), project, r.assetId, Action.READ_RESULTS);
            var view = of(r);
            view.put("versions", repo.versions(project, id).stream().map(QualityViews::of).toList());
            return ResponseEntity.ok().eTag("\"" + r.revision + "\"").body(response(view, request));
        });
    }

    @PatchMapping(value = "/quality-rules/{id}", consumes = "application/merge-patch+json")
    @Operation(
            summary = "Cambiar nombre o activación de regla",
            description = "QUALITY_MANAGER; If-Match obligatorio.")
    public ResponseEntity<?> patch(@PathVariable UUID project, @PathVariable UUID id, @RequestBody JsonNode body, @RequestHeader(name = "If-Match", required = false) String match, Principal principal, HttpServletRequest request) {
        var r = rules.patch(actor(principal), project, id, body, match, requestId(request));
        return ResponseEntity.ok().eTag("\"" + r.revision + "\"").body(response(of(r), request));
    }

    @PostMapping("/quality-rules/{id}/versions")
    @Operation(
            summary = "Crear una versión inmutable",
            description = "QUALITY_MANAGER; If-Match obligatorio.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Operación completada o aceptada."))
    public ResponseEntity<?> version(@PathVariable UUID project, @PathVariable UUID id, @Valid @RequestBody VersionCommand command, @RequestHeader(name = "If-Match", required = false) String match, Principal principal, HttpServletRequest request) {
        var v = rules.version(actor(principal), project, id, command, match, requestId(request));
        return ResponseEntity.created(URI.create(base(project) + "/quality-rules/" + id)).body(response(of(v), request));
    }

    @PostMapping("/quality-evaluations")
    @Operation(
            summary = "Solicitar evaluación idempotente",
            description = "QUALITY_MANAGER. Fija versiones revisadas; 412 si cambiaron.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Operación completada o aceptada."))
    public ResponseEntity<?> evaluate(@PathVariable UUID project, @Valid @RequestBody CreateEvaluation body, @RequestHeader(name = "Idempotency-Key", required = false) String key, Principal principal, HttpServletRequest request) {
        UUID parsed;
        try {
            parsed = CanonicalUuid.parse(key);
        } catch (IllegalArgumentException ex) {
            throw invalid("Envía Idempotency-Key como UUID en minúsculas.");
        }
        var e = evaluations.submit(actor(principal), project, body, parsed, requestId(request));
        return ResponseEntity.accepted().location(URI.create(base(project) + "/quality-evaluations/" + e.id)).body(response(evaluationView(actor(principal), project, e.id), request));
    }

    @GetMapping("/quality-evaluations")
    @Operation(
            summary = "Consultar historial de evaluaciones",
            description = "Respeta la unión de activos autorizados y las fechas inclusivas de Ecuador.")
    public ApiResponse<?> history(@PathVariable UUID project, @RequestParam(name = "asset_id", required = false) UUID asset, @RequestParam(name = "date_from", required = false) String from, @RequestParam(name = "date_to", required = false) String to, @RequestParam(required = false) String state, @RequestParam(defaultValue = "1") int page, @RequestParam(name = "page_size", defaultValue = "25") int size, Principal principal, HttpServletRequest request) {
        var paging = new PageQuery(page, size);
        org.dataanaliticsent.Calidad.Dominio.EvaluationDates.Range range;
        try {
            range = org.dataanaliticsent.Calidad.Dominio.EvaluationDates.range(from, to);
        } catch (IllegalArgumentException ex) {
            throw invalid(ex.getMessage());
        }
        if (state != null && !Set.of("QUEUED", "RUNNING", "COMPLETED", "FAILED").contains(state))
            throw invalid("Estado de ejecución no válido.");
        return tx.execute(actor(principal), project, () -> {
            UUID actor = actor(principal);
            var scope = access.scope(actor, project, asset, Action.READ_RESULTS);
            var filter = new QualityRepository.EvaluationFilter(asset, range.from(), range.until(), state, scope.assetIds());
            var rows = repo.evaluations(project, filter, paging.offset(), size);
            long total = repo.evaluationCount(project, filter);
            var inputs = repo.inputs(project, rows.stream().map(e -> e.inputRefId).distinct().toList());
            var values = rows.stream().map(e -> {
                var view = of(e);
                view.put("organization_id", scope.project().organizationId);
                if (inputs.containsKey(e.inputRefId)) addInput(view, inputs.get(e.inputRefId));
                return view;
            }).toList();
            return ApiResponse.ofPage(values, RequestIdFilter.resolveRequestId(request), paging.info(values.size(), total));
        });
    }

    @GetMapping("/quality-evaluations/{id}")
    @Operation(
            summary = "Consultar ejecución",
            description = "QUEUED, RUNNING, COMPLETED o FAILED; completar no implica aprobar.")
    public ApiResponse<?> evaluation(@PathVariable UUID project, @PathVariable UUID id, Principal principal, HttpServletRequest request) {
        return response(evaluationView(actor(principal), project, id), request);
    }

    private Map<String, Object> evaluationView(UUID actor, UUID project, UUID id) {
        return tx.execute(actor, project, () -> {
            var e = repo.find(Evaluation.class, id, project).orElseThrow(AccessService::missing);
            var context = access.require(actor, project, e.assetId, Action.READ_RESULTS);
            var result = of(e);
            result.put("organization_id", context.organizationId);
            repo.find(InputRef.class, e.inputRefId, project).ifPresent(r -> addInput(result, r));
            return result;
        });
    }

    @GetMapping("/quality-evaluations/{id}/results")
    @Operation(
            summary = "Consultar indicadores y versiones aplicadas",
            description = "Conserva resultados aunque la entrada caduque.")
    public ApiResponse<?> results(@PathVariable UUID project, @PathVariable UUID id, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            var e = repo.find(Evaluation.class, id, project).orElseThrow(AccessService::missing);
            access.require(actor(principal), project, e.assetId, Action.READ_RESULTS);
            var view = of(e);
            repo.find(InputRef.class, e.inputRefId, project).ifPresent(r -> addInput(view, r));
            return response(map("evaluation", view, "results", repo.results(project, id).stream().map(QualityViews::of).toList(), "rule_versions", repo.selectedVersions(project, id).stream().map(QualityViews::of).toList()), request);
        });
    }

    @GetMapping("/quality-incidents")
    @Operation(
            summary = "Consultar incidencias filtradas",
            description = "QUALITY_MANAGER; no incluye valores de evidencia.")
    public ApiResponse<?> incidents(@PathVariable UUID project, @RequestParam(name = "asset_id", required = false) UUID asset, @RequestParam(name = "evaluation_id", required = false) UUID evaluation, @RequestParam(required = false) String status, @RequestParam(required = false) String severity, @RequestParam(required = false) String dimension, @RequestParam(name = "q", required = false) String search, @RequestParam(defaultValue = "1") int page, @RequestParam(name = "page_size", defaultValue = "25") int size, Principal principal, HttpServletRequest request) {
        var paging = new PageQuery(page, size);
        if (status != null && !Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "DISMISSED").contains(status))
            throw invalid("Estado de incidencia no válido.");
        if (severity != null && !Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severity))
            throw invalid("Gravedad no válida.");
        if (dimension != null && Arrays.stream(org.dataanaliticsent.Calidad.Dominio.QualityEngine.Dimension.values()).noneMatch(d -> d.name().equals(dimension)))
            throw invalid("Dimensión no válida.");
        if (search != null && search.length() > 160) throw invalid("La búsqueda admite hasta 160 caracteres.");
        return tx.execute(actor(principal), project, () -> {
            UUID actor = actor(principal);
            var scope = access.scope(actor, project, asset, Action.MANAGE_QUALITY);
            var filter = new QualityRepository.IncidentFilter(asset, evaluation, status, severity, dimension, search, scope.assetIds());
            var rows = repo.incidents(project, filter, paging.offset(), size);
            long total = repo.incidentCount(project, filter);
            var context = repo.incidentRules(project, rows.stream().map(i -> i.id).toList());
            var values = rows.stream().map(i -> {
                var view = of(i);
                view.put("organization_id", scope.project().organizationId);
                view.putAll(context.getOrDefault(i.id, Map.of()));
                return view;
            }).toList();
            return ApiResponse.ofPage(values, RequestIdFilter.resolveRequestId(request), paging.info(values.size(), total));
        });
    }

    @GetMapping("/quality-incidents/{id}/events")
    @Operation(
            summary = "Consultar seguimiento",
            description = "QUALITY_MANAGER del activo.")
    public ApiResponse<?> events(@PathVariable UUID project, @PathVariable UUID id, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            var incident = repo.find(Incident.class, id, project).orElseThrow(AccessService::missing);
            access.require(actor(principal), project, incident.assetId, Action.MANAGE_QUALITY);
            return response(repo.events(project, id).stream().map(e -> {
                var v = of(e);
                v.put("comment", e.commentCiphertext == null ? null : cipher.decrypt(e.commentCiphertext, "incident_comment", project.toString(), e.id.toString()));
                return v;
            }).toList(), request);
        });
    }

    @PostMapping("/quality-incidents/{id}/events")
    @Operation(
            summary = "Registrar seguimiento",
            description = "QUALITY_MANAGER; If-Match obligatorio. No altera la medición.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Operación completada o aceptada."))
    public ResponseEntity<?> followUp(@PathVariable UUID project, @PathVariable UUID id, @Valid @RequestBody FollowUp body, @RequestHeader(name = "If-Match", required = false) String match, Principal principal, HttpServletRequest request) {
        var incident = incidents.followUp(actor(principal), project, id, body, match, requestId(request));
        return ResponseEntity.created(URI.create(base(project) + "/quality-incidents/" + id + "/events"))
                .eTag("\"" + incident.revision + "\"").body(response(of(incident), request));
    }

    @GetMapping("/audit-events")
    @Operation(
            summary = "Consultar auditoría",
            description = "SYSTEM_ADMIN o GOVERNANCE_MANAGER de todo el proyecto.")
    public ApiResponse<?> audit(@PathVariable UUID project, @RequestParam(defaultValue = "1") int page, @RequestParam(name = "page_size", defaultValue = "25") int size, Principal principal, HttpServletRequest request) {
        return list(AuditEvent.class, actor(principal), project, null, page, size, Action.AUDIT, request);
    }

    @GetMapping("/quality-incidents/{id}/evidence")
    @Operation(
            summary = "Consultar evidencia explícita",
            description = "QUALITY_MANAGER; solo campos de la versión histórica. Cache-Control: no-store; acceso auditado.")
    public ResponseEntity<?> evidence(@PathVariable UUID project, @PathVariable UUID id, Principal principal, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(response(evidence.view(actor(principal), project, id, requestId(request)), request));
    }

    private <T> ApiResponse<?> list(Class<T> type, UUID actor, UUID project, UUID asset, int page, int size, Action permission, HttpServletRequest request) {
        var paging = new PageQuery(page, size);
        return tx.execute(actor, project, () -> {
            var context = access.require(actor, project, asset, permission);
            var values = repo.list(type, project, asset, paging.offset(), size + 1);
            var view = values.stream().limit(size).map(QualityViews::of).peek(v -> v.put("organization_id", context.organizationId)).toList();
            return ApiResponse.ofPage(view, RequestIdFilter.resolveRequestId(request), paging.info(view.size(), values.size() > size));
        });
    }

    private void addInput(Map<String, Object> view, InputRef ref) {
        view.put("metadata_version_id", ref.metadataVersionId);
        view.put("snapshot_id", ref.snapshotId);
        view.put("read_id", ref.readId);
        view.put("input_availability", retention.availability(ref));
        view.put("scope", ref.scopeDefinition);
        view.put("input_row_count", ref.rowCount);
        view.put("input_byte_count", ref.byteCount);
        view.put("input_acquired_at", ref.acquiredAt);
        view.put("input_expires_at", retention.expiresAt(ref));
        view.put("input_complete", ref.complete);
    }

    private static String base(UUID p) {
        return "/api/v1/projects/" + p;
    }

}


