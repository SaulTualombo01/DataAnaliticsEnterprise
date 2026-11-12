package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

/**
 * Lectura explícita de las columnas implicadas, nunca de la fila completa por defecto.
 */
@Service
public class EvidenceService {
    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;
    private final InputProvider input;
    private final RuleService audit;
    private final InputRetention retention;

    public EvidenceService(QualityRepository repo, ProjectTransaction tx, AccessService access, InputProvider input, RuleService audit, InputRetention retention) {
        this.repo = repo;
        this.tx = tx;
        this.access = access;
        this.input = input;
        this.audit = audit;
        this.retention = retention;
    }

    private record Context(Incident incident, InputRef input, List<String> fields) {
    }

    public record Evidence(boolean available, String reason, Map<String, Object> values, String recordReference,
                           UUID snapshotId, UUID readId, java.time.Instant capturedAt) {
    }

    public Evidence view(UUID actor, UUID project, UUID id, UUID request) {
        var context = tx.execute(actor, project, () -> {
            var incident = repo.find(Incident.class, id, project).orElseThrow(AccessService::missing);
            access.require(actor, project, incident.assetId, Action.MANAGE_QUALITY);
            var evaluation = repo.find(Evaluation.class, incident.evaluationId, project).orElseThrow(AccessService::missing);
            var ref = repo.find(InputRef.class, evaluation.inputRefId, project).orElseThrow(AccessService::missing);
            var result = repo.find(RuleResult.class, incident.ruleResultId, project).orElseThrow(AccessService::missing);
            var version = repo.find(RuleVersion.class, result.ruleVersionId, project).orElseThrow(AccessService::missing);
            List<String> fields = new ArrayList<>();
            if ("REQUIRED".equals(version.definition.path("kind").asText())) fields.add(incident.fieldName);
            else version.definition.path("fields").forEach(f -> fields.add(f.asText()));
            return new Context(incident, ref, List.copyOf(fields));
        });
        var ref = context.input();
        if (!"AVAILABLE".equals(retention.availability(ref)))
            return new Evidence(false, "La entrada de origen no está disponible. Se conserva la detección y sus resultados históricos.", null, null, null, null, null);
        var capture = input.read(actor, project, ref.assetId, ref.metadataVersionId, ref.readId, ref.snapshotId);
        if (retention.expired(capture.expiresAt()))
            return new Evidence(false, "La entrada de origen ha caducado.", null, null, null, null, null);
        int row;
        try {
            row = capture.input().sourceRowNumbers().indexOf(Long.parseLong(context.incident().recordReference));
        } catch (NumberFormatException ex) {
            row = -1;
        }
        if (row < 0)
            return new Evidence(false, "No se pudo localizar la fila en la captura conservada.", null, null, null, null, null);
        Map<String, Object> values = new LinkedHashMap<>();
        var record = capture.input().rows().get(row);
        for (String field : context.fields())
            if (capture.input().fields().contains(field)) values.put(field, record.get(field));
        tx.run(actor, project, () -> {
            access.require(actor, project, ref.assetId, Action.MANAGE_QUALITY);
            audit.audit(actor, project, request, "INCIDENT_EVIDENCE_VIEWED", "incident", id);
        });
        return new Evidence(true, null, values, context.incident().recordReference, ref.snapshotId, ref.readId, ref.acquiredAt);
    }
}
