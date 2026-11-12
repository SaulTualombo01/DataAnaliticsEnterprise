import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import java.util.*;
import java.util.function.Supplier;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QualityOverviewTest {
    final UUID actor = UUID.randomUUID(), project = UUID.randomUUID(), asset = UUID.randomUUID();
    QualityRepository repo;
    AccessService access;
    QualityOverviewService service;
    Evaluation uniqueness, validity;
    RuleVersion uniqueVersion, validVersion;
    RuleResult uniqueResult, validResult;

    @BeforeEach void setup() {
        repo = mock(QualityRepository.class);
        access = mock(AccessService.class);
        var tx = mock(ProjectTransaction.class);
        when(tx.execute(any(), any(), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(2)).get());
        service = new QualityOverviewService(repo, tx, access);
        uniqueness = evaluation("COMPLETED"); validity = evaluation("COMPLETED");
        uniqueVersion = version("UNIQUENESS"); validVersion = version("VALIDITY");
        uniqueResult = result(uniqueness, uniqueVersion); validResult = result(validity, validVersion);
        when(repo.evaluations(eq(project), any(), eq(0), eq(1))).thenReturn(List.of(validity));
        when(repo.latestDimensionResults(project, asset)).thenReturn(List.of(uniqueResult, validResult));
        when(repo.find(Evaluation.class, uniqueness.id, project)).thenReturn(Optional.of(uniqueness));
        when(repo.selectedVersions(project, uniqueness.id)).thenReturn(List.of(uniqueVersion));
        when(repo.selectedVersions(project, validity.id)).thenReturn(List.of(validVersion));
        when(repo.inputs(eq(project), any())).thenReturn(Map.of());
    }

    @Test void dimensionsFromSeparateEvaluationsRemainVisibleWithTheirOwnSource() {
        var view = service.view(actor, project, asset);
        assertSame(validity, view.latestEvaluation());
        assertEquals(Map.of("UNIQUENESS", uniqueness, "VALIDITY", validity), view.dimensionEvaluations());
        assertEquals(Set.of(uniqueResult, validResult), new HashSet<>(view.results()));
        assertEquals(Set.of(uniqueVersion, validVersion), new HashSet<>(view.ruleVersions()));
    }

    @Test void pendingOrFailedLatestExecutionDoesNotEraseCompletedDimensions() {
        for (String status : List.of("QUEUED", "RUNNING", "FAILED")) {
            var latest = evaluation(status);
            when(repo.evaluations(eq(project), any(), eq(0), eq(1))).thenReturn(List.of(latest));
            when(repo.find(Evaluation.class, validity.id, project)).thenReturn(Optional.of(validity));
            var view = service.view(actor, project, asset);
            assertSame(latest, view.latestEvaluation());
            assertEquals(2, view.dimensionEvaluations().size());
            assertSame(validity, view.dimensionEvaluations().get("VALIDITY"));
        }
    }

    @Test void unusedVersionsFromSourceExecutionAreNotPresentedAsMeasurements() {
        var other = version("ACCURACY");
        when(repo.selectedVersions(project, validity.id)).thenReturn(List.of(validVersion, other));
        var view = service.view(actor, project, asset);
        assertFalse(view.ruleVersions().contains(other));
        assertFalse(view.dimensionEvaluations().containsKey("ACCURACY"));
    }

    @Test void emptyHistoryHasNoInventedDimensions() {
        when(repo.evaluations(eq(project), any(), eq(0), eq(1))).thenReturn(List.of());
        when(repo.latestDimensionResults(project, asset)).thenReturn(List.of());
        var view = service.view(actor, project, asset);
        assertNull(view.latestEvaluation());
        assertTrue(view.results().isEmpty());
        assertTrue(view.dimensionEvaluations().isEmpty());
    }

    @Test void deniedAssetIsRejectedBeforeReadingItsHistory() {
        when(access.require(actor, project, asset, AccessService.Action.READ_RESULTS)).thenThrow(AccessService.denied());
        assertThrows(org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException.class,
                () -> service.view(actor, project, asset));
        verifyNoInteractions(repo);
    }

    private Evaluation evaluation(String status) {
        var e = new Evaluation(); e.id = UUID.randomUUID(); e.projectId = project; e.assetId = asset;
        e.inputRefId = UUID.randomUUID(); e.status = status; return e;
    }
    private RuleVersion version(String dimension) {
        var v = new RuleVersion(); v.id = UUID.randomUUID(); v.dimension = dimension; return v;
    }
    private RuleResult result(Evaluation e, RuleVersion v) {
        var r = new RuleResult(); r.id = UUID.randomUUID(); r.evaluationId = e.id; r.ruleVersionId = v.id; return r;
    }
}
