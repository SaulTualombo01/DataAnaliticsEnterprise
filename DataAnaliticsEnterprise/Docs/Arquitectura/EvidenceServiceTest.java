import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Dominio.QualityEngine;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvidenceServiceTest {
    final UUID actor=UUID.randomUUID(),project=UUID.randomUUID(),request=UUID.randomUUID();
    final Instant now=Instant.parse("2026-10-29T12:00:00Z");
    QualityRepository repo;ProjectTransaction tx;AccessService access;InputProvider input;RuleService audit;EvidenceService service;Incident incident;InputRef ref;
    @BeforeEach void setup() throws Exception {
        repo=mock(QualityRepository.class);tx=mock(ProjectTransaction.class);access=mock(AccessService.class);input=mock(InputProvider.class);audit=mock(RuleService.class);
        when(tx.execute(any(),any(),any())).thenAnswer(i -> ((Supplier<?>)i.getArgument(2)).get());
        doAnswer(i -> {((Runnable)i.getArgument(2)).run();return null;}).when(tx).run(any(),any(),any());
        incident=new Incident();incident.id=UUID.randomUUID();incident.projectId=project;incident.assetId=UUID.randomUUID();incident.evaluationId=UUID.randomUUID();incident.ruleResultId=UUID.randomUUID();incident.fieldName="correo";incident.recordReference="8";
        ref=new InputRef();ref.id=UUID.randomUUID();ref.projectId=project;ref.assetId=incident.assetId;ref.metadataVersionId=UUID.randomUUID();ref.readId=UUID.randomUUID();ref.snapshotId=UUID.randomUUID();ref.availability="AVAILABLE";ref.acquiredAt=now;
        var evaluation=new Evaluation();evaluation.id=incident.evaluationId;evaluation.inputRefId=ref.id;
        var result=new RuleResult();result.id=incident.ruleResultId;result.ruleVersionId=UUID.randomUUID();
        var version=new RuleVersion();version.id=result.ruleVersionId;version.definition=new ObjectMapper().readTree("{\"kind\":\"EMAIL\",\"fields\":[\"correo\"]}");
        when(repo.find(Incident.class,incident.id,project)).thenReturn(Optional.of(incident));when(repo.find(Evaluation.class,evaluation.id,project)).thenReturn(Optional.of(evaluation));
        when(repo.find(InputRef.class,ref.id,project)).thenReturn(Optional.of(ref));when(repo.find(RuleResult.class,result.id,project)).thenReturn(Optional.of(result));when(repo.find(RuleVersion.class,version.id,project)).thenReturn(Optional.of(version));
        var data=new QualityEngine.Input(List.of(Map.of("correo","incorrecto","nombre","No debe mostrarse")),Set.of("correo","nombre"),true,Map.of(),List.of(8L));
        when(input.read(actor,project,ref.assetId,ref.metadataVersionId,ref.readId,ref.snapshotId)).thenReturn(new InputProvider.Capture(ref.readId,ref.snapshotId,ref.assetId,ref.metadataVersionId,now,null,"a".repeat(64),100,data));
        service=new EvidenceService(repo,tx,access,input,audit,new InputRetention(new org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties(),Clock.fixed(now,ZoneOffset.UTC)));
    }
    @Test void revealsOnlyRuleFieldsAndAuditsExplicitView(){var evidence=service.view(actor,project,incident.id,request);assertTrue(evidence.available());assertEquals(Map.of("correo","incorrecto"),evidence.values());verify(audit).audit(actor,project,request,"INCIDENT_EVIDENCE_VIEWED","incident",incident.id);verify(access,times(2)).require(actor,project,ref.assetId,AccessService.Action.MANAGE_QUALITY);}
    @Test void expiredInputDoesNotLoadOriginalContent(){ref.expiresAt=now.minusSeconds(1);var evidence=service.view(actor,project,incident.id,request);assertFalse(evidence.available());assertNull(evidence.values());verifyNoInteractions(input);}
    @Test void legacyCsvBeyondRetentionNeverReadsTheFile(){ref.protectedObjectRef="local-csv:legacy";ref.acquiredAt=now.minus(Duration.ofDays(30));var evidence=service.view(actor,project,incident.id,request);assertFalse(evidence.available());assertNull(evidence.values());verifyNoInteractions(input);}
    @Test void deniedScopeNeverReadsTheFile(){when(access.require(actor,project,incident.assetId,AccessService.Action.MANAGE_QUALITY)).thenThrow(AccessService.denied());assertThrows(org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException.class,() -> service.view(actor,project,incident.id,request));verifyNoInteractions(input);}
    @Test void unmatchedPhysicalLineDoesNotExposeAnotherRecord(){incident.recordReference="999";var evidence=service.view(actor,project,incident.id,request);assertFalse(evidence.available());assertNull(evidence.values());verifyNoInteractions(audit);}
}
