package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import org.dataanaliticsent.Calidad.Aplicacion.IdentityService;
import org.dataanaliticsent.Calidad.Aplicacion.RuleService;
import org.dataanaliticsent.Calidad.Aplicacion.QualityContracts.*;
import org.dataanaliticsent.Calidad.Dominio.QualityEngine;
import org.dataanaliticsent.Calidad.Infraestructura.SyntheticInputProvider;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;

import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

/**
 * Aprovisionamiento de un proyecto vacío. Activación explícita; no escribe durante el desarrollo de código.
 */
@Component
@ConditionalOnProperty(name = "m2.bootstrap.enabled", havingValue = "true")
public class InitialProvisioning implements ApplicationRunner {
    private final Environment env;
    private final QualityRepository repo;
    private final IdentityService identity;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RuleService rules;

    public InitialProvisioning(Environment env, QualityRepository repo, IdentityService identity, TransactionTemplate tx, Clock clock, RuleService rules) {
        this.env = env;
        this.repo = repo;
        this.identity = identity;
        this.tx = tx;
        this.clock = clock;
        this.rules = rules;
    }

    public void run(ApplicationArguments arguments) {
        if (env.getProperty("m2.bootstrap.fixture-rules", Boolean.class, false) && !"mock".equals(env.getProperty("m2.integrations.mode")))
            throw new IllegalStateException("Las reglas ficticias solo se permiten en un entorno explícito de pruebas mock.");
        tx.executeWithoutResult(status -> {
            UUID projectId = UUID.fromString(env.getRequiredProperty("m2.bootstrap.project-id"));
            if (repo.project(projectId).isPresent() && env.getProperty("m2.bootstrap.allow-existing", Boolean.class, false)) {
                UUID workerId = UUID.fromString(env.getRequiredProperty("m2.bootstrap.worker-id"));
                if (repo.user(workerId).isEmpty())
                    throw new IllegalStateException("El volumen no contiene la identidad técnica configurada.");
                return;
            }
            if (repo.project(projectId).isPresent())
                throw new IllegalStateException("El proyecto ya existe. Desactiva el aprovisionamiento inicial.");
            Project project = new Project();
            project.id = projectId;
            project.organizationId = UUID.fromString(env.getRequiredProperty("m2.bootstrap.organization-id"));
            project.name = env.getRequiredProperty("m2.bootstrap.project-name");
            project.active = true;
            project.createdAt = clock.instant();

            repo.save(project);
            var admin = identity.create(env.getRequiredProperty("m2.bootstrap.admin-email"), env.getRequiredProperty("m2.bootstrap.admin-password"));
            assign(admin.id, projectId, "SYSTEM_ADMIN");
            var manager = identity.create(env.getRequiredProperty("m2.bootstrap.quality-email"), env.getRequiredProperty("m2.bootstrap.quality-password"));
            assign(manager.id, projectId, "QUALITY_MANAGER");
            // Cuenta interna sin contraseña compartida; configurar el UUID devuelto en m2.worker.user-id.
            UUID workerId = UUID.fromString(env.getProperty("m2.bootstrap.worker-id", UUID.randomUUID().toString()));
            var worker = identity.createWithId(workerId, env.getRequiredProperty("m2.bootstrap.worker-email"), UUID.randomUUID() + "-" + UUID.randomUUID());
            assign(worker.id, projectId, "QUALITY_MANAGER");
            repo.flush();
            if (env.getProperty("m2.bootstrap.fixture-rules", Boolean.class, false)) {
                var required = new Definition(QualityEngine.Kind.REQUIRED, List.of("correo"), true, null, null, null, null, null, null, null, null, null);
                var unique = new Definition(QualityEngine.Kind.UNIQUE_KEY, List.of("id_cliente"), true, null, null, null, null, null, null, null, null, null);
                for (UUID asset : List.of(SyntheticInputProvider.CUSTOMERS, SyntheticInputProvider.EMPTY, SyntheticInputProvider.PARTIAL)) {
                    seed(manager.id, projectId, asset, "Correo obligatorio", QualityEngine.Dimension.COMPLETENESS, required, "98");
                }
                seed(manager.id, projectId, SyntheticInputProvider.CUSTOMERS, "Identificador único", QualityEngine.Dimension.UNIQUENESS, unique, "98");
            }
            org.slf4j.LoggerFactory.getLogger(getClass()).info("Proyecto aprovisionado. M2_WORKER_USER_ID={}. Desactiva m2.bootstrap.enabled.", worker.id);
        });
    }

    private void seed(UUID actor, UUID project, UUID asset, String name, QualityEngine.Dimension dimension, Definition definition, String threshold) {
        rules.create(actor, project, new CreateRule(asset, name, new VersionCommand(dimension, definition, new BigDecimal(threshold), BigDecimal.valueOf(100), false)), UUID.randomUUID());
    }

    private void assign(UUID user, UUID project, String role) {
        RoleAssignment r = new RoleAssignment();
        r.id = UUID.randomUUID();
        r.projectId = project;
        r.userId = user;
        r.roleName = role;
        r.createdAt = clock.instant();
        repo.save(r);
    }
}

