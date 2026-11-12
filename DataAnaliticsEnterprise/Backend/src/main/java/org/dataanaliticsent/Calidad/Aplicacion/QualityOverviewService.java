package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

@Service
public class QualityOverviewService {
    public record Overview(Evaluation latestEvaluation, List<RuleResult> results, List<RuleVersion> ruleVersions,
                           Map<String, Evaluation> dimensionEvaluations, Map<UUID, InputRef> inputs) {}

    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;

    public QualityOverviewService(QualityRepository repo, ProjectTransaction tx, AccessService access) {
        this.repo = repo;
        this.tx = tx;
        this.access = access;
    }

    public Overview view(UUID actor, UUID project, UUID asset) {
        return tx.execute(actor, project, () -> {
            access.require(actor, project, asset, AccessService.Action.READ_RESULTS);
            var latest = repo.evaluations(project, new QualityRepository.EvaluationFilter(asset, null, null, null, null), 0, 1)
                    .stream().findFirst().orElse(null);
            var results = repo.latestDimensionResults(project, asset);
            Map<UUID, Evaluation> evaluations = new LinkedHashMap<>();
            if (latest != null) evaluations.put(latest.id, latest);
            Map<UUID, RuleVersion> versions = new LinkedHashMap<>();
            for (UUID id : results.stream().map(r -> r.evaluationId).distinct().toList()) {
                evaluations.computeIfAbsent(id, key -> repo.find(Evaluation.class, key, project).orElseThrow(AccessService::missing));
                for (var version : repo.selectedVersions(project, id)) versions.put(version.id, version);
            }
            Map<String, Evaluation> sources = new LinkedHashMap<>();
            Set<UUID> usedVersions = new HashSet<>();
            for (var result : results) {
                var version = versions.get(result.ruleVersionId);
                if (version == null) throw AccessService.missing();
                usedVersions.add(version.id);
                sources.put(version.dimension, evaluations.get(result.evaluationId));
            }
            var inputs = repo.inputs(project, evaluations.values().stream().map(e -> e.inputRefId).distinct().toList());
            return new Overview(latest, results, versions.values().stream().filter(v -> usedVersions.contains(v.id)).toList(), sources, inputs);
        });
    }
}
