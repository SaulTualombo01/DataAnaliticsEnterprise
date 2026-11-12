package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Infraestructura.CsvFileStore;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Limpieza acotada con el rol SQL normal y los permisos del ejecutor técnico. */
@Service
public class CsvRetentionService {
    private static final Logger log = LoggerFactory.getLogger(CsvRetentionService.class);
    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;
    private final CsvFileStore store;
    private final RuleService audit;
    private final M2Properties properties;
    private final Clock clock;
    private final UUID worker;

    public CsvRetentionService(QualityRepository repo, ProjectTransaction tx, AccessService access,
                               CsvFileStore store, RuleService audit, M2Properties properties, Clock clock,
                               @Value("${m2.worker.user-id:}") String worker) {
        this.repo = repo; this.tx = tx; this.access = access; this.store = store;
        this.audit = audit; this.properties = properties; this.clock = clock;
        this.worker = worker.isBlank() ? null : UUID.fromString(worker);
    }

    @Scheduled(fixedDelayString = "${m2.csv.cleanup-ms:60000}", initialDelayString = "${m2.csv.cleanup-ms:60000}")
    public void cleanup() {
        if (worker == null) return;
        var projects = repo.roles(worker, null).stream()
                .filter(r -> "QUALITY_MANAGER".equals(r.roleName) && r.assetScopeId == null)
                .map(r -> r.projectId).distinct().toList();
        for (UUID project : projects) {
            try { cleanupProject(project); }
            catch (RuntimeException error) {
                log.warn("CSV_RETENTION_FAILED project_id={} error_type={}", project, error.getClass().getSimpleName());
            }
        }
    }

    public void cleanupProject(UUID project) {
        if (worker == null) throw new IllegalStateException("WORKER_NOT_CONFIGURED");
        tx.run(worker, project, () -> {
            var context = access.require(worker, project, null, AccessService.Action.MANAGE_QUALITY);
            repo.lock(context);
            var now = clock.instant();
            var cutoff = now.minus(properties.getLimits().getCsvRetentionDays(), ChronoUnit.DAYS);
            for (var ref : repo.expiredCsvInputs(project, now, cutoff, 100)) {
                try { store.removeContent(project, ref.assetId); }
                catch (IOException error) { throw new IllegalStateException("CSV_PURGE_FAILED"); }
                ref.availability = "DELETED";
                audit.audit(worker, project, UUID.randomUUID(), "CSV_CONTENT_PURGED", "input_ref", ref.id);
            }
        });
    }
}
