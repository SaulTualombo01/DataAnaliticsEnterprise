package org.dataanaliticsent.Calidad.Aplicacion;

import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

@Service
public class AccessService {
    public enum Role {SYSTEM_ADMIN, CATALOG_MANAGER, QUALITY_MANAGER, GOVERNANCE_MANAGER, BUSINESS_ANALYST}

    public enum Action {ADMINISTER, CATALOG, READ_RESULTS, MANAGE_QUALITY, AUDIT}

    private final QualityRepository repository;

    public AccessService(QualityRepository repository) {
        this.repository = repository;
    }

    public Project require(UUID actor, UUID project, UUID asset, Action action) {
        UserAccount user = repository.user(actor).filter(u -> Boolean.TRUE.equals(u.active)).orElseThrow(AccessService::denied);
        Project context = repository.project(project).filter(p -> Boolean.TRUE.equals(p.active)).orElseThrow(AccessService::denied);
        if (repository.roles(user.id, project).stream().noneMatch(r -> permits(r.roleName, r.assetScopeId, asset, action)))
            throw denied();
        return context;
    }

    public static boolean permits(String role, UUID scope, UUID asset, Action action) {
        if (scope != null && !scope.equals(asset)) return false;
        return switch (action) {
            case ADMINISTER -> role.equals("SYSTEM_ADMIN") && scope == null;
            case CATALOG ->
                    Set.of("CATALOG_MANAGER", "QUALITY_MANAGER", "GOVERNANCE_MANAGER", "BUSINESS_ANALYST").contains(role);
            case READ_RESULTS -> Set.of("QUALITY_MANAGER", "GOVERNANCE_MANAGER", "BUSINESS_ANALYST").contains(role);
            case MANAGE_QUALITY -> role.equals("QUALITY_MANAGER");
            case AUDIT -> Set.of("SYSTEM_ADMIN", "GOVERNANCE_MANAGER").contains(role) && scope == null;
        };
    }

    public Project requireAnyScope(UUID actor, UUID project, Action action) {
        var assignment = repository.roles(actor, project).stream().filter(r -> permits(r.roleName, r.assetScopeId, r.assetScopeId, action)).findFirst().orElseThrow(AccessService::denied);
        return require(actor, project, assignment.assetScopeId, action);
    }

    /** Una lista null conserva el contrato del repositorio: permiso sobre todo el proyecto. */
    public record ProjectScope(Project project, List<UUID> assetIds) {}

    public ProjectScope scope(UUID actor, UUID project, UUID asset, Action action) {
        Project context = asset == null ? requireAnyScope(actor, project, action) : require(actor, project, asset, action);
        var assignments = repository.roles(actor, project).stream()
                .filter(role -> permits(role.roleName, role.assetScopeId, role.assetScopeId, action)).toList();
        List<UUID> assets = assignments.stream().anyMatch(role -> role.assetScopeId == null) ? null
                : assignments.stream().map(role -> role.assetScopeId).distinct().toList();
        return new ProjectScope(context, assets);
    }

    public static ApiException denied() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Acceso denegado.", "No tienes permiso para esta operación en el ámbito solicitado.");
    }

    public static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recurso no disponible.", "El recurso no está disponible para esta consulta.");
    }

    public static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Solicitud no válida.", detail);
    }

    public static void revision(String expected, Long actual) {
        if (expected == null)
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "Falta la revisión.", "Envía If-Match con la revisión actual del recurso.");
        if (!expected.equals("\"" + actual + "\""))
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", "El recurso cambió.", "Consulta la revisión actual antes de guardar.");
    }
}
