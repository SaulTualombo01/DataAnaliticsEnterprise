import org.dataanaliticsent.Calidad.Aplicacion.AccessService;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessScopeTest {
    final UUID actor = UUID.randomUUID(), projectId = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
    QualityRepository repository;
    AccessService service;

    @BeforeEach void setup() {
        repository = mock(QualityRepository.class);
        var user = new UserAccount(); user.id = actor; user.active = true;
        var project = new Project(); project.id = projectId; project.active = true;
        when(repository.user(actor)).thenReturn(Optional.of(user));
        when(repository.project(projectId)).thenReturn(Optional.of(project));
        service = new AccessService(repository);
    }

    RoleAssignment role(String name, UUID asset) {
        var role = new RoleAssignment(); role.roleName = name; role.assetScopeId = asset; return role;
    }

    @Test void combinesOnlyAssetsAllowedForTheRequestedAction() {
        when(repository.roles(actor, projectId)).thenReturn(List.of(role("QUALITY_MANAGER", first), role("QUALITY_MANAGER", second), role("BUSINESS_ANALYST", UUID.randomUUID())));
        assertEquals(Set.of(first, second), new HashSet<>(service.scope(actor, projectId, null, AccessService.Action.MANAGE_QUALITY).assetIds()));
    }

    @Test void projectWidePermissionUsesTheExistingUnrestrictedScopeContract() {
        when(repository.roles(actor, projectId)).thenReturn(List.of(role("QUALITY_MANAGER", null)));
        assertNull(service.scope(actor, projectId, null, AccessService.Action.READ_RESULTS).assetIds());
    }

    @Test void administratorDoesNotImplicitlyReadBusinessData() {
        when(repository.roles(actor, projectId)).thenReturn(List.of(role("SYSTEM_ADMIN", null)));
        assertThrows(ApiException.class, () -> service.scope(actor, projectId, null, AccessService.Action.READ_RESULTS));
    }

    @Test void explicitAssetOutsideTheAssignedScopeIsRejected() {
        when(repository.roles(actor, projectId)).thenReturn(List.of(role("QUALITY_MANAGER", first)));
        assertThrows(ApiException.class, () -> service.scope(actor, projectId, second, AccessService.Action.MANAGE_QUALITY));
    }
}
