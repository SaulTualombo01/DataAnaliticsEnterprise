package org.dataanaliticsent.Calidad.Aplicacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.dataanaliticsent.Calidad.Dominio.QualityEngine;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Aplicacion.QualityContracts.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

@Service
public class RuleService {
    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;
    private final InputProvider input;
    private final ObjectMapper mapper;
    private final Clock clock;

    public RuleService(QualityRepository repo, ProjectTransaction tx, AccessService access, InputProvider input, ObjectMapper mapper, Clock clock) {
        this.repo = repo;
        this.tx = tx;
        this.access = access;
        this.input = input;
        this.mapper = mapper;
        this.clock = clock;
    }

    public QualityRule create(UUID actor, UUID project, CreateRule command, UUID request) {
        tx.run(actor, project, () -> access.require(actor, project, command.assetId(), Action.MANAGE_QUALITY));
        var asset = input.assets(actor, project).stream().filter(a -> a.id().equals(command.assetId())).findFirst().orElseThrow(AccessService::missing);
        var checked = rule(UUID.randomUUID(), command.version());
        if (!asset.fields().containsAll(checked.fields()))
            throw invalid("La regla contiene campos ajenos a los metadatos del activo.");
        return tx.execute(actor, project, () -> {
            access.require(actor, project, command.assetId(), Action.MANAGE_QUALITY);
            ensureAsset(project, asset);
            QualityRule r = new QualityRule();
            r.id = UUID.randomUUID();
            r.projectId = project;
            r.assetId = command.assetId();
            r.name = command.name();
            r.enabled = true;
            r.createdBy = actor;
            r.createdAt = clock.instant();
            
            repo.save(r);
            addVersion(actor, project, r, command.version());
            audit(actor, project, request, "RULE_CREATED", "quality_rule", r.id);
            return r;
        });
    }

    public QualityRule patch(UUID actor, UUID project, UUID id, JsonNode patch, String revision, UUID request) {
        if (!patch.isObject() || patch.isEmpty()) throw invalid("Envía name o enabled.");
        patch.fieldNames().forEachRemaining(f -> {
            if (!Set.of("name", "enabled").contains(f)) throw invalid("La propiedad no es editable.");
        });
        if (patch.has("name") && (!patch.get("name").isTextual() || patch.get("name").asText().isBlank() || patch.get("name").asText().length() > 160))
            throw invalid("El nombre es obligatorio y admite hasta 160 caracteres.");
        if (patch.has("enabled") && !patch.get("enabled").isBoolean()) throw invalid("enabled debe ser booleano.");
        return tx.execute(actor, project, () -> {
            QualityRule r = repo.find(QualityRule.class, id, project).orElseThrow(AccessService::missing);
            access.require(actor, project, r.assetId, Action.MANAGE_QUALITY);
            repo.lock(r);
            revision(revision, r.revision);
            if (patch.has("name")) r.name = patch.get("name").asText();
            if (patch.has("enabled")) r.enabled = patch.get("enabled").asBoolean();
            repo.flush();
            audit(actor, project, request, "RULE_UPDATED", "quality_rule", r.id);
            return r;
        });
    }

    public RuleVersion version(UUID actor, UUID project, UUID id, VersionCommand command, String revision, UUID request) {
        rule(UUID.randomUUID(), command);
        return tx.execute(actor, project, () -> {
            QualityRule r = repo.find(QualityRule.class, id, project).orElseThrow(AccessService::missing);
            access.require(actor, project, r.assetId, Action.MANAGE_QUALITY);
            repo.lock(r);
            revision(revision, r.revision);
            var result = addVersion(actor, project, r, command);
            // La revisión de la identidad también cambia al agregar una versión.
            r.revision = r.revision + 1;
            audit(actor, project, request, "RULE_VERSION_CREATED", "rule_version", result.id);
            return result;
        });
    }

    private RuleVersion addVersion(UUID actor, UUID project, QualityRule rule, VersionCommand c) {
        RuleVersion v = new RuleVersion();
        v.id = UUID.randomUUID();
        v.projectId = project;
        v.assetId = rule.assetId;
        v.ruleId = rule.id;
        v.versionNumber = repo.versions(project, rule.id).stream().mapToInt(x -> x.versionNumber).max().orElse(0) + 1;
        v.dimension = c.dimension().name();
        v.definition = mapper.valueToTree(c.definition());
        v.formulaVersion = "m2-1";
        v.threshold = c.threshold();
        v.minimumCoverage = c.minimumCoverage() == null ? java.math.BigDecimal.valueOf(100) : c.minimumCoverage();
        v.critical = c.critical();
        v.createdBy = actor;
        v.createdAt = clock.instant();
        return repo.save(v);
    }

    public QualityEngine.Rule rule(UUID id, VersionCommand c) {
        Definition d = c.definition();
        try {
            return new QualityEngine.Rule(id, c.dimension(), d.kind(), d.fields(), c.threshold(), c.minimumCoverage(), c.critical(),
                    d.blankIsMissing() == null || d.blankIsMissing(), d.minimum(), d.maximum(), d.allowedValues(), d.referenceId(), d.referenceVersion(), d.referenceKey(), d.referenceField(), d.tolerance(), d.maxAgeSeconds());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw invalid(ex.getMessage() == null ? "Configuración incompleta." : ex.getMessage());
        }
    }

    public QualityEngine.Rule rule(RuleVersion v) {
        if (!"m2-1".equals(v.formulaVersion)) throw invalid("Versión de fórmula no soportada.");
        return rule(v.id, new VersionCommand(QualityEngine.Dimension.valueOf(v.dimension), mapper.convertValue(v.definition, Definition.class), v.threshold, v.minimumCoverage, Boolean.TRUE.equals(v.critical)));
    }

    public void ensureAsset(UUID project, InputProvider.Asset asset) {
        if (repo.list(AssetRef.class, project, asset.id(), 0, 1).isEmpty()) {
            AssetRef ref = new AssetRef();
            ref.id = UUID.randomUUID();
            ref.projectId = project;
            ref.assetId = asset.id();
            ref.sourceId = asset.sourceId();
            ref.createdAt = clock.instant();
            repo.save(ref);
        }
    }

    public void audit(UUID actor, UUID project, UUID request, String action, String resource, UUID id) {
        AuditEvent event = new AuditEvent();
        event.id = UUID.randomUUID();
        event.projectId = project;
        event.actorId = actor;
        event.requestId = request;
        event.action = action;
        event.resourceType = resource;
        event.resourceId = id;
        event.outcome = "SUCCESS";
        event.createdAt = clock.instant();
        repo.save(event);
    }
}

