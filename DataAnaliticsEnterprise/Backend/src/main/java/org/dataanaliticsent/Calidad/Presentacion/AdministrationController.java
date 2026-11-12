package org.dataanaliticsent.Calidad.Presentacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Infraestructura.QualityRepository;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.ProjectTransaction;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.time.Clock;
import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;
import static org.dataanaliticsent.Calidad.Presentacion.QualityViews.*;
import static org.dataanaliticsent.Calidad.Presentacion.Utilidades.ApiContext.*;

@Tag(name = "Administración")
@RestController
@RequestMapping("/api/v1")
public class AdministrationController {
    private final QualityRepository repo;
    private final ProjectTransaction tx;
    private final AccessService access;
    private final IdentityService identity;
    private final RuleService audit;
    private final Clock clock;

    public AdministrationController(QualityRepository repo, ProjectTransaction tx, AccessService access, IdentityService identity, RuleService audit, Clock clock) {
        this.repo = repo;
        this.tx = tx;
        this.access = access;
        this.identity = identity;
        this.audit = audit;
        this.clock = clock;
    }

    public record CreateUser(@NotBlank @Email @Size(max = 254) String email,
                             @NotBlank @Size(min = 12, max = 128) String password, @NotNull Role role,
                             UUID assetScopeId) {
    }

    public record CreateRole(@NotNull UUID userId, @NotNull Role role, UUID assetScopeId) {
    }

    public record UpdateUser(@NotNull Boolean active) {
    }

    @GetMapping("/users")
    @Operation(
            summary = "Consultar cuentas del proyecto",
            description = "SYSTEM_ADMIN de todo el proyecto.")
    public ApiResponse<?> users(@RequestParam(name = "project_id") UUID project, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            access.require(actor(principal), project, null, Action.ADMINISTER);
            var users = repo.projectRoles(project).stream().map(r -> r.userId).distinct().map(repo::user).flatMap(Optional::stream).map(QualityViews::of).toList();
            return response(users, request);
        });
    }

    @PostMapping("/users")
    @Operation(
            summary = "Crear cuenta y rol inicial",
            description = "SYSTEM_ADMIN; CSRF.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Operación completada o aceptada."))
    public ResponseEntity<?> create(@RequestParam(name = "project_id") UUID project, @Valid @RequestBody CreateUser command, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            UUID actor = actor(principal);
            lockAdministrationAccounts(actor, project, actor);
            lockProjects(actor, List.of(project));
            validateScope(command.role(), command.assetScopeId());
            var user = identity.create(command.email(), command.password());
            assign(user.id, project, command.role(), command.assetScopeId());
            audit.audit(actor, project, requestId(request), "USER_CREATED", "user", user.id);
            return ResponseEntity.created(URI.create("/api/v1/users/" + user.id + "?project_id=" + project)).body(response(of(user), request));
        });
    }

    @PatchMapping("/users/{id}")
    @Operation(
            summary = "Cambiar activación de cuenta",
            description = "SYSTEM_ADMIN; CSRF e If-Match.")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestParam(name = "project_id") UUID project, @Valid @RequestBody UpdateUser command, @RequestHeader(name = "If-Match", required = false) String match, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            UUID actor = actor(principal);
            lockAdministrationAccounts(actor, project, id);
            var memberships = repo.roles(id, null);
            if (memberships.stream().noneMatch(r -> r.projectId.equals(project))) throw missing();
            lockProjects(actor, memberships.stream().map(r -> r.projectId).toList());
            if (actor.equals(id) && !command.active())
                throw invalid("No puedes desactivar tu propia cuenta desde esta operación.");
            var user = repo.lockAndRefresh(repo.user(id).orElseThrow(AccessService::missing));
            revision(match, user.revision);
            if (!command.active())
                memberships.stream().filter(r -> r.roleName.equals("SYSTEM_ADMIN"))
                        .forEach(r -> requireAnotherAdministrator(r.projectId, id));
            user.active = command.active();
            user.authVersion++;
            repo.flush();
            audit.audit(actor, project, requestId(request), "USER_UPDATED", "user", id);
            return ResponseEntity.ok().eTag("\"" + user.revision + "\"").body(response(of(user), request));
        });
    }

    @GetMapping("/projects/{project}/roles")
    @Operation(
            summary = "Consultar roles",
            description = "SYSTEM_ADMIN de todo el proyecto.")
    public ApiResponse<?> roles(@PathVariable UUID project, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            access.require(actor(principal), project, null, Action.ADMINISTER);
            return response(repo.projectRoles(project).stream().map(QualityViews::of).toList(), request);
        });
    }

    @PostMapping("/projects/{project}/roles")
    @Operation(
            summary = "Asignar rol",
            description = "SYSTEM_ADMIN; CSRF.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Operación completada o aceptada."))
    public ResponseEntity<?> createRole(@PathVariable UUID project, @Valid @RequestBody CreateRole command, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            UUID actor = actor(principal);
            lockAdministrationAccounts(actor, project, command.userId());
            lockProjects(actor, List.of(project));
            validateScope(command.role(), command.assetScopeId());
            var user = repo.lockAndRefresh(repo.user(command.userId()).orElseThrow(AccessService::missing));
            if (repo.roles(user.id, project).stream().anyMatch(r -> r.roleName.equals(command.role().name()) && Objects.equals(r.assetScopeId, command.assetScopeId())))
                throw invalid("La asignación ya existe.");
            var role = assign(user.id, project, command.role(), command.assetScopeId());
            user.authVersion++;
            audit.audit(actor, project, requestId(request), "ROLE_ASSIGNED", "role_assignment", role.id);
            return ResponseEntity.created(URI.create("/api/v1/projects/" + project + "/roles")).body(response(of(role), request));
        });
    }

    @DeleteMapping("/projects/{project}/roles/{id}")
    @Operation(
            summary = "Revocar rol",
            description = "SYSTEM_ADMIN; conserva al menos un administrador activo.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Operación completada o aceptada."))
    public ResponseEntity<Void> revoke(@PathVariable UUID project, @PathVariable UUID id, Principal principal, HttpServletRequest request) {
        return tx.execute(actor(principal), project, () -> {
            UUID actor = actor(principal);
            access.require(actor, project, null, Action.ADMINISTER);
            UUID target = repo.find(RoleAssignment.class, id, project).orElseThrow(AccessService::missing).userId;
            lockAdministrationAccounts(actor, project, target);
            lockProjects(actor, List.of(project));
            var role = repo.find(RoleAssignment.class, id, project).orElseThrow(AccessService::missing);
            var user = repo.lockAndRefresh(repo.user(role.userId).orElseThrow(AccessService::missing));
            if (role.roleName.equals("SYSTEM_ADMIN")) requireAnotherAdministrator(project, role.userId);
            user.authVersion++;
            repo.removeRole(role);
            audit.audit(actor, project, requestId(request), "ROLE_REVOKED", "role_assignment", id);
            return ResponseEntity.noContent().build();
        });
    }

    private void lockAdministrationAccounts(UUID actor, UUID project, UUID target) {
        access.require(actor, project, null, Action.ADMINISTER);
        repo.lockAdministrationAccounts(actor, target);
        // El actor pudo perder acceso mientras esperaba. Actualiza también la caché JPA.
        repo.refresh(repo.user(actor).orElseThrow(AccessService::denied));
        access.require(actor, project, null, Action.ADMINISTER);
    }

    private void lockProjects(UUID actor, Collection<UUID> projects) {
        // El orden común evita interbloqueos cuando una cuenta participa en varios proyectos.
        projects.stream().distinct().sorted(Comparator.comparing(UUID::toString))
                .forEach(project -> repo.lock(access.require(actor, project, null, Action.ADMINISTER)));
    }

    private void requireAnotherAdministrator(UUID project, UUID user) {
        if (!repo.hasOtherActiveAdministrator(project, user))
            throw invalid("Conserva al menos un administrador activo del proyecto.");
    }

    private RoleAssignment assign(UUID user, UUID project, Role role, UUID scope) {
        RoleAssignment r = new RoleAssignment();
        r.id = UUID.randomUUID();
        r.userId = user;
        r.projectId = project;
        r.roleName = role.name();
        r.assetScopeId = scope;
        r.createdAt = clock.instant();
        return repo.save(r);
    }

    private void validateScope(Role role, UUID scope) {
        if (role == Role.SYSTEM_ADMIN && scope != null)
            throw invalid("La administración se asigna al proyecto completo.");
    }

}
