package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import org.dataanaliticsent.Calidad.Aplicacion.AccessService;
import org.dataanaliticsent.Calidad.Infraestructura.CsvFileStore;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** Mediciones técnicas sin contraseñas, sesiones, tokens o valores de CSV. */
@Component
public class OperationalMonitor {
    private static final Logger log = LoggerFactory.getLogger(OperationalMonitor.class);
    private final CsvFileStore store;
    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;
    private final M2Properties properties;
    private final Clock clock;
    private final UUID worker;

    public OperationalMonitor(CsvFileStore store, QualityRepository repo, ProjectTransaction tx, AccessService access,
                              M2Properties properties, Clock clock, @Value("${m2.worker.user-id:}") String worker) {
        this.store = store; this.repo = repo; this.tx = tx; this.access = access;
        this.properties = properties; this.clock = clock; this.worker = worker.isBlank() ? null : UUID.fromString(worker);
    }

    @Scheduled(fixedDelayString = "${m2.operations.poll-ms:60000}", initialDelayString = "${m2.operations.poll-ms:60000}")
    public void measure() {
        try {
            var runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            var storage = store.usage();
            long anonymous = repo.anonymousSessionCount(clock.millis());
            log.info("OPERATIONS heap_used_bytes={} heap_max_bytes={} csv_bytes={} disk_free_bytes={} anonymous_sessions={}",
                    used, runtime.maxMemory(), storage.storedBytes(), storage.freeBytes(), anonymous);
            if (used > runtime.maxMemory() * 0.85 || storage.storedBytes() > properties.getLimits().getMaxStoredCsvBytes() * 0.85
                    || storage.freeBytes() < properties.getLimits().getMinFreeDiskBytes() || anonymous >= 4000)
                log.warn("OPERATIONS_CAPACITY_ALERT");
            if (worker == null) return;
            var projects = repo.roles(worker, null).stream().filter(r -> "QUALITY_MANAGER".equals(r.roleName) && r.assetScopeId == null)
                    .map(r -> r.projectId).distinct().toList();
            for (var project : projects) tx.run(worker, project, () -> {
                access.require(worker, project, null, AccessService.Action.MANAGE_QUALITY);
                var jobs = repo.unfinished(project);
                long queued = jobs.stream().filter(e -> "QUEUED".equals(e.status)).count();
                log.info("OPERATIONS_QUEUE project_id={} queued={} unfinished={}", project, queued, jobs.size());
                if (jobs.size() >= properties.getLimits().getMaxPendingPerProject() * 0.8) log.warn("OPERATIONS_QUEUE_ALERT project_id={}", project);
            });
        } catch (Exception error) {
            log.warn("OPERATIONS_MEASUREMENT_FAILED error_type={}", error.getClass().getSimpleName());
        }
    }
}
