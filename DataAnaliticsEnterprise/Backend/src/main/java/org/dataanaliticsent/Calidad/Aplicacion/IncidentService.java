package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Dominio.Workflow;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.UUID;
import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

@Service
public class IncidentService {
    private final QualityRepository repository;
    private final ProjectTransaction transaction;
    private final AccessService access;
    private final SensitiveDataCipher cipher;
    private final RuleService audit;
    private final Clock clock;

    public IncidentService(QualityRepository repository, ProjectTransaction transaction, AccessService access,
                           SensitiveDataCipher cipher, RuleService audit, Clock clock) {
        this.repository = repository;
        this.transaction = transaction;
        this.access = access;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    public Incident followUp(UUID actor, UUID project, UUID id, QualityContracts.FollowUp command,
                             String expectedRevision, UUID request) {
        return transaction.execute(actor, project, () -> {
            Incident incident = repository.find(Incident.class, id, project).orElseThrow(AccessService::missing);
            access.require(actor, project, incident.assetId, Action.MANAGE_QUALITY);
            repository.lock(incident);
            revision(expectedRevision, incident.revision);
            if (!Workflow.incidentTransition(incident.status, command.status())) {
                throw invalid("Transición de seguimiento no permitida.");
            }
            IncidentEvent event = new IncidentEvent();
            event.id = UUID.randomUUID();
            event.projectId = project;
            event.assetId = incident.assetId;
            event.incidentId = id;
            event.actorId = actor;
            event.fromStatus = incident.status;
            event.toStatus = command.status();
            event.createdAt = clock.instant();
            event.commentCiphertext = command.comment() == null ? null : cipher.encrypt(command.comment(),
                    "incident_comment", project.toString(), event.id.toString());
            repository.save(event);
            incident.status = command.status();
            repository.flush();
            audit.audit(actor, project, request, "INCIDENT_FOLLOW_UP", "incident", id);
            return incident;
        });
    }
}
