import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Infraestructura.*;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.*;
import org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CsvRetentionServiceTest {
    @Test void purgeIsScopedAuditedAndRetriedWhenStorageFails() throws Exception {
        var repo = mock(QualityRepository.class); var tx = mock(ProjectTransaction.class);
        var access = mock(AccessService.class); var store = mock(CsvFileStore.class); var audit = mock(RuleService.class);
        var properties = new M2Properties();
        UUID worker = UUID.randomUUID(), project = UUID.randomUUID();
        Instant now = Instant.now();
        var service = new CsvRetentionService(repo, tx, access, store, audit, properties, Clock.fixed(now, ZoneOffset.UTC), worker.toString());
        doAnswer(i -> { ((Runnable) i.getArgument(2)).run(); return null; }).when(tx).run(eq(worker), eq(project), any());
        var context = new Project(); when(access.require(worker,project,null,AccessService.Action.MANAGE_QUALITY)).thenReturn(context);
        var ref = new InputRef(); ref.id = UUID.randomUUID(); ref.assetId = UUID.randomUUID(); ref.availability = "AVAILABLE";
        when(repo.expiredCsvInputs(project, now, now.minus(Duration.ofDays(30)), 100)).thenReturn(List.of(ref));
        doThrow(new IOException("private-file-details")).doNothing().when(store).removeContent(project,ref.assetId);
        assertThrows(IllegalStateException.class, () -> service.cleanupProject(project));
        assertEquals("AVAILABLE",ref.availability);
        verifyNoInteractions(audit);
        service.cleanupProject(project);
        assertEquals("DELETED",ref.availability);
        verify(audit).audit(eq(worker),eq(project),any(),eq("CSV_CONTENT_PURGED"),eq("input_ref"),eq(ref.id));
        verify(repo,times(2)).lock(context);
    }
}
