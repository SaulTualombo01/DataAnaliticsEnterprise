package org.dataanaliticsent.Calidad.Aplicacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.Calidad.Dominio.QualityEngine;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.*;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Aplicacion.QualityContracts.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

@Service
public class EvaluationService {
    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;
    private final RuleService rules;
    private final InputProvider input;
    private final M2Properties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final UUID worker;
    private final QualityEngine engine = new QualityEngine();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EvaluationService.class);

    public EvaluationService(QualityRepository repo, ProjectTransaction tx, AccessService access, RuleService rules,
                             InputProvider input, M2Properties properties, ObjectMapper mapper, Clock clock, @Value("${m2.worker.user-id:}") String worker) {
        this.repo = repo;
        this.tx = tx;
        this.access = access;
        this.rules = rules;
        this.input = input;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.worker = worker.isBlank() ? null : UUID.fromString(worker);
    }

    public Evaluation submit(UUID actor, UUID project, CreateEvaluation command, UUID key, UUID request) {
        if (new HashSet<>(command.ruleIds()).size() != command.ruleIds().size())
            throw invalid("No repitas reglas en la solicitud.");
        if (command.expectedRuleVersions() != null && !command.expectedRuleVersions().keySet().equals(new HashSet<>(command.ruleIds())))
            throw invalid("Las versiones revisadas deben corresponder exactamente a las reglas seleccionadas.");
        String fingerprint = fingerprint(command);
        Evaluation previous = tx.execute(actor, project, () -> {
            access.require(actor, project, command.assetId(), Action.MANAGE_QUALITY);
            return reuse(project, actor, key, fingerprint);
        });
        if (previous != null) return previous;
        if (worker == null)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WORKER_NOT_CONFIGURED", "Ejecutor pendiente.", "Configura la identidad técnica del ejecutor antes de iniciar evaluaciones.");
        tx.run(worker, project, () -> access.require(worker, project, command.assetId(), Action.MANAGE_QUALITY));
        InputProvider.Capture capture = input.acquire(actor, project, command.assetId(), command.metadataVersionId(), command.snapshotId());
        validateCapture(capture, command.assetId(), command.metadataVersionId());
        return tx.execute(actor, project, () -> {
            Project context = access.require(actor, project, command.assetId(), Action.MANAGE_QUALITY);
            repo.lock(context);
            Evaluation existing = reuse(project, actor, key, fingerprint);
            if (existing != null) return existing;
            var pending = repo.pendingCounts(project);
            if (pending.project() >= properties.getLimits().getMaxPendingPerProject()
                    || pending.user() >= properties.getLimits().getMaxPendingPerUser())
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "QUEUE_FULL", "Cola ocupada.", "Espera a que finalicen las evaluaciones pendientes.");
            List<RuleVersion> selected = new ArrayList<>();
            for (UUID id : command.ruleIds()) {
                QualityRule rule = repo.find(QualityRule.class, id, project).filter(r -> r.assetId.equals(command.assetId()) && Boolean.TRUE.equals(r.enabled)).orElseThrow(AccessService::missing);
                repo.lock(rule);
                RuleVersion version = repo.versions(project, id).stream().findFirst().orElseThrow(AccessService::missing);
                if (command.expectedRuleVersions() != null && !version.id.equals(command.expectedRuleVersions().get(id)))
                    throw new ApiException(HttpStatus.PRECONDITION_FAILED, "RULE_VERSION_CHANGED", "La regla cambió.", "Una regla cambió desde la revisión. Vuelve al paso Reglas y actualiza la selección antes de evaluar.");
                var spec = rules.rule(version);
                if (!capture.input().fields().containsAll(spec.fields()))
                    throw invalid("Los campos de la regla no corresponden a esta entrada.");
                selected.add(version);
            }
            try { QualityEngine.validateWorkload(capture.input(), selected.stream().map(rules::rule).toList(), properties.getLimits().getMaxWorkUnits()); }
            catch (IllegalStateException error) { throw invalid("La evaluación supera el presupuesto de trabajo. Reduce los campos o reglas por ejecución."); }
            InputRef ref = repo.inputByRead(project, capture.readId()).orElse(null);
            if (ref != null && (!ref.assetId.equals(command.assetId()) || !ref.metadataVersionId.equals(capture.metadataVersionId())
                    || !Objects.equals(ref.snapshotId, capture.snapshotId()) || !Objects.equals(ref.contentSha256, capture.sha256())
                    || ref.rowCount != capture.input().rows().size() || ref.byteCount != capture.bytes() || ref.complete != capture.input().complete()
                    || !"AVAILABLE".equals(ref.availability)))
                throw new ApiException(HttpStatus.CONFLICT, "INPUT_REFERENCE_CONFLICT", "La referencia de entrada cambió.", "Solicita una entrada nueva o revisa la disponibilidad de la captura.");
            if (ref == null) {
                ref = new InputRef();
                ref.id = UUID.randomUUID();
                ref.projectId = project;
                ref.assetId = command.assetId();
                ref.readId = capture.readId();
                ref.snapshotId = capture.snapshotId();
                ref.metadataVersionId = capture.metadataVersionId();
                ref.scopeDefinition = mapper.valueToTree(Map.of("kind", "COMPLETE_INPUT", "fields", capture.input().fields().stream().sorted().toList()));
                ref.complete = capture.input().complete();
                ref.rowCount = (long) capture.input().rows().size();
                ref.byteCount = capture.bytes();
                ref.acquiredAt = capture.acquiredAt();
                ref.expiresAt = capture.expiresAt();
                ref.contentSha256 = capture.sha256();
                ref.availability = "AVAILABLE";
                ref.createdAt = clock.instant();
                repo.save(ref);
            }
            Evaluation e = new Evaluation();
            e.id = UUID.randomUUID();
            e.projectId = project;
            e.assetId = command.assetId();
            e.inputRefId = ref.id;
            e.requestedBy = actor;
            e.status = "QUEUED";
            e.qualityOutcome = "NOT_ASSESSED";
            e.requestId = request;
            e.idempotencyKey = key;
            e.requestSha256 = fingerprint;
            e.createdAt = clock.instant();

            repo.save(e);
            for (RuleVersion v : selected) {
                EvaluationRule link = new EvaluationRule();
                link.id = UUID.randomUUID();
                link.projectId = project;
                link.assetId = e.assetId;
                link.evaluationId = e.id;
                link.ruleVersionId = v.id;
                repo.save(link);
            }
            rules.audit(actor, project, request, "EVALUATION_QUEUED", "evaluation", e.id);
            return e;
        });
    }

    private Evaluation reuse(UUID project, UUID actor, UUID key, String fingerprint) {
        Evaluation previous = repo.idempotent(project, actor, key).orElse(null);
        if (previous != null && !Objects.equals(previous.requestSha256, fingerprint))
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "La clave ya se utilizó.", "Usa una clave nueva para una solicitud diferente.");
        return previous;
    }

    public static String fingerprint(CreateEvaluation command) {
        String canonical = command.assetId() + "|" + command.metadataVersionId() + "|" + command.snapshotId() + "|" + command.ruleIds().stream().map(UUID::toString).sorted().toList();
        if (command.expectedRuleVersions() != null)
            canonical += "|" + command.expectedRuleVersions().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + ":" + e.getValue()).toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void validateCapture(InputProvider.Capture c, UUID asset, UUID metadata) {
        if (c == null || !asset.equals(c.assetId()) || !metadata.equals(c.metadataVersionId()) || c.readId() == null || c.acquiredAt() == null || c.sha256() == null || !c.sha256().matches("[0-9a-f]{64}"))
            throw invalid("La entrada no cumple el contrato de identificación e integridad.");
        if (c.bytes() < 0 || c.bytes() > properties.getLimits().getMaxInputBytes() || c.input().rows().size() > properties.getLimits().getMaxInputRows())
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "INPUT_LIMIT_EXCEEDED", "Entrada demasiado grande.", "La entrada supera los límites configurados; no se procesará truncada.");
        if (c.expiresAt() != null && !c.expiresAt().isAfter(clock.instant())) throw invalid("La entrada ha caducado.");
    }

    @Scheduled(fixedDelayString = "${m2.worker.poll-ms:1000}")
    public void tick() {
        if (worker == null) return;
        try {
            Set<UUID> projects = new LinkedHashSet<>();
            repo.roles(worker, null).stream().filter(r -> r.roleName.equals("QUALITY_MANAGER") && r.assetScopeId == null).forEach(r -> projects.add(r.projectId));
            for (UUID project : projects) {
                Evaluation claimed = tx.execute(worker, project, () -> {
                    Project p = access.require(worker, project, null, Action.MANAGE_QUALITY);
                    repo.lock(p);
                    List<Evaluation> jobs = repo.unfinished(project);
                    for (Evaluation e : jobs)
                        if (e.status.equals("RUNNING") && (e.leaseUntil == null || !e.leaseUntil.isAfter(clock.instant())))
                            fail(e, "WORKER_LEASE_EXPIRED");
                    if (jobs.stream().anyMatch(e -> e.status.equals("RUNNING"))) return null;
                    Evaluation next = jobs.stream().filter(e -> e.status.equals("QUEUED")).findFirst().orElse(null);
                    if (next != null) {
                        repo.lock(next);
                        next.status = "RUNNING";
                        next.startedAt = clock.instant();
                        next.leaseUntil = clock.instant().plus(properties.getLimits().getEvaluationTimeout().multipliedBy(2)).plusSeconds(30);
                    }
                    return next;
                });
                if (claimed != null) execute(claimed);
            }
        } catch (Exception ex) {
            log.warn("No se pudo consultar la cola: {}", ex.getClass().getSimpleName());
        }
    }

    private record Work(InputRef ref, List<RuleVersion> versions) {
    }

    private void execute(Evaluation job) {
        try {
            Work work = tx.execute(job.requestedBy, job.projectId, () -> {
                access.require(job.requestedBy, job.projectId, job.assetId, Action.MANAGE_QUALITY);
                InputRef ref = repo.find(InputRef.class, job.inputRefId, job.projectId).orElseThrow(AccessService::missing);
                if (!"AVAILABLE".equals(ref.availability) || ref.expiresAt != null && !ref.expiresAt.isAfter(clock.instant()))
                    throw invalid("Entrada no disponible.");
                return new Work(ref, repo.selectedVersions(job.projectId, job.id));
            });
            InputRef ref = work.ref;
            var capture = input.read(job.requestedBy, job.projectId, job.assetId, ref.metadataVersionId, ref.readId, ref.snapshotId);
            validateCapture(capture, job.assetId, ref.metadataVersionId);
            if (!Objects.equals(capture.sha256(), ref.contentSha256) || capture.input().rows().size() != ref.rowCount || capture.bytes() != ref.byteCount || capture.input().complete() != ref.complete)
                throw new IllegalStateException("INPUT_INTEGRITY_MISMATCH");
            if (!capture.input().complete()) throw new IllegalStateException("INPUT_PARTIAL");
            // Instante fijado por la solicitud: un reintento de publicación no cambia actualidad.
            QualityEngine.Report report = engine.evaluate(capture.input(), work.versions.stream().map(rules::rule).toList(), job.createdAt,
                    properties.getLimits().getEvaluationTimeout(), properties.getLimits().getMaxWorkUnits(), properties.getLimits().getMaxFindings());
            tx.run(job.requestedBy, job.projectId, () -> publish(job, work.versions, report));
        } catch (Exception ex) {
            String code = ex instanceof ApiException api ? api.getCode() : Set.of("INPUT_PARTIAL", "INPUT_INTEGRITY_MISMATCH", "EVALUATION_TIMEOUT", "INPUT_CONTRACT_MISMATCH", "EVALUATION_WORK_LIMIT", "EVALUATION_FINDING_LIMIT").contains(Objects.toString(ex.getMessage(), "")) ? ex.getMessage() : "EVALUATION_FAILED";
            try {
                tx.run(worker, job.projectId, () -> repo.find(Evaluation.class, job.id, job.projectId).ifPresent(e -> {
                    repo.lock(e);
                    if (Set.of("QUEUED", "RUNNING").contains(e.status)) fail(e, code);
                }));
            } catch (Exception recovery) {
                log.error("No se pudo cerrar evaluación {} [request_id={}]", job.id, job.requestId);
            }
        }
    }

    private void publish(Evaluation job, List<RuleVersion> versions, QualityEngine.Report report) {
        access.require(job.requestedBy, job.projectId, job.assetId, Action.MANAGE_QUALITY);
        Evaluation current = repo.find(Evaluation.class, job.id, job.projectId).orElseThrow(AccessService::missing);
        repo.lock(current);
        if (!current.status.equals("RUNNING") || current.leaseUntil == null || !current.leaseUntil.isAfter(clock.instant()))
            throw new IllegalStateException("WORKER_LEASE_EXPIRED");
        InputRef ref = repo.find(InputRef.class, current.inputRefId, current.projectId).orElseThrow(AccessService::missing);
        if (!"AVAILABLE".equals(ref.availability) || ref.expiresAt != null && !ref.expiresAt.isAfter(clock.instant()))
            throw invalid("Entrada no disponible.");
        for (var measured : report.results()) {
            RuleVersion v = versions.stream().filter(x -> x.id.equals(measured.ruleVersionId())).findFirst().orElseThrow();
            RuleResult result = new RuleResult();
            result.id = UUID.randomUUID();
            result.projectId = job.projectId;
            result.assetId = job.assetId;
            result.evaluationId = job.id;
            result.ruleVersionId = v.id;
            result.measurementState = measured.measurementState();
            result.candidateUnits = measured.candidateUnits();
            result.applicableUnits = measured.applicableUnits();
            result.numerator = measured.numerator();
            result.denominator = measured.denominator();
            result.threshold = v.threshold;
            result.minimumCoverage = v.minimumCoverage;
            result.createdAt = clock.instant();
            repo.save(result);
            for (var finding : measured.findings()) {
                Incident i = new Incident();
                i.id = UUID.randomUUID();
                i.projectId = job.projectId;
                i.assetId = job.assetId;
                i.evaluationId = job.id;
                i.ruleResultId = result.id;
                i.fieldName = finding.field();
                i.recordReference = Long.toString(finding.rowNumber());
                i.problemCode = finding.code();
                i.severity = v.critical ? "CRITICAL" : "MEDIUM";
                i.status = "OPEN";
                i.createdAt = clock.instant();

                repo.save(i);
            }
        }
        // PostgreSQL exige insertar todos los resultados mientras la evaluación sigue RUNNING.
        repo.flush();
        current.status = "COMPLETED";
        current.qualityOutcome = report.qualityOutcome();
        current.finishedAt = clock.instant();
        current.leaseUntil = null;
        rules.audit(job.requestedBy, job.projectId, job.requestId, "EVALUATION_COMPLETED", "evaluation", job.id);
    }

    private void fail(Evaluation e, String code) {
        e.status = "FAILED";
        e.qualityOutcome = "INCOMPLETE";
        e.failureCode = code;
        e.finishedAt = clock.instant();
        e.leaseUntil = null;
        AuditEvent a = new AuditEvent();
        a.id = UUID.randomUUID();
        a.projectId = e.projectId;
        a.actorId = worker;
        a.requestId = e.requestId;
        a.action = "EVALUATION_FAILED";
        a.resourceType = "evaluation";
        a.resourceId = e.id;
        a.outcome = "FAILURE";
        a.reasonCode = code;
        a.createdAt = clock.instant();
        repo.save(a);
    }
}

