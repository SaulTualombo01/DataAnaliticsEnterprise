import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentServiceTest {
    final UUID actor = UUID.randomUUID(), project = UUID.randomUUID(), request = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-04T15:00:00Z");
    QualityRepository repository; ProjectTransaction transaction; AccessService access;
    SensitiveDataCipher cipher; RuleService audit; Incident incident; IncidentService service;

    @BeforeEach void setup() {
        repository = mock(QualityRepository.class); transaction = mock(ProjectTransaction.class);
        access = mock(AccessService.class); cipher = mock(SensitiveDataCipher.class); audit = mock(RuleService.class);
        when(transaction.execute(any(), any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(2)).get());
        incident = new Incident(); incident.id = UUID.randomUUID(); incident.projectId = project;
        incident.assetId = UUID.randomUUID(); incident.status = "OPEN"; incident.revision = 2L;
        when(repository.find(Incident.class, incident.id, project)).thenReturn(Optional.of(incident));
        when(cipher.encrypt(anyString(), eq("incident_comment"), eq(project.toString()), anyString())).thenReturn("encrypted-comment");
        service = new IncidentService(repository, transaction, access, cipher, audit, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void transitionPersistsEncryptedCommentAndAuditsInsideTheProjectTransaction() {
        var result = service.followUp(actor, project, incident.id, new QualityContracts.FollowUp("IN_PROGRESS", "Revisando"), "\"2\"", request);
        assertSame(incident, result); assertEquals("IN_PROGRESS", result.status);
        var saved = ArgumentCaptor.forClass(IncidentEvent.class);
        verify(repository).save(saved.capture());
        assertEquals("OPEN", saved.getValue().fromStatus);
        assertEquals("IN_PROGRESS", saved.getValue().toStatus);
        assertEquals("encrypted-comment", saved.getValue().commentCiphertext);
        assertEquals(now, saved.getValue().createdAt);
        verify(access).require(actor, project, incident.assetId, AccessService.Action.MANAGE_QUALITY);
        verify(audit).audit(actor, project, request, "INCIDENT_FOLLOW_UP", "incident", incident.id);
    }

    @Test void staleRevisionDoesNotCreateAnEventOrChangeTheIncident() {
        assertThrows(ApiException.class, () -> service.followUp(actor, project, incident.id, new QualityContracts.FollowUp("IN_PROGRESS", "Revisando"), "\"1\"", request));
        assertEquals("OPEN", incident.status); verify(repository, never()).save(any()); verifyNoInteractions(cipher, audit);
    }

    @Test void invalidTransitionDoesNotPersistOrAudit() {
        assertThrows(ApiException.class, () -> service.followUp(actor, project, incident.id, new QualityContracts.FollowUp("INVALID", null), "\"2\"", request));
        assertEquals("OPEN", incident.status); verify(repository, never()).save(any()); verifyNoInteractions(cipher, audit);
    }

    @Test void deniedAssetCannotBeLockedOrModified() {
        when(access.require(actor, project, incident.assetId, AccessService.Action.MANAGE_QUALITY)).thenThrow(AccessService.denied());
        assertThrows(ApiException.class, () -> service.followUp(actor, project, incident.id, new QualityContracts.FollowUp("IN_PROGRESS", null), "\"2\"", request));
        verify(repository, never()).lock(any()); verify(repository, never()).save(any()); verifyNoInteractions(cipher, audit);
    }
}
