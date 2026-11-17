package org.dataanaliticsent.TroncoComun.contexto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi.Pagina;

/** Participación administrativa. Actor y jerarquía se resuelven en servidor. */
public interface ParticipacionApi {
    enum TipoAlcance { PROJECT,SOURCE,ASSET }
    record Alcance(TipoAlcance type,UUID resourceId) { }
    record Rol(String code,String name,List<String> permissions) { public Rol { permissions=List.copyOf(permissions); } }
    record MembresiaEmpresa(UUID id,UUID userId,UUID organizationId,String status,UUID assignedBy,Instant assignedAt) { }
    record MembresiaProyecto(UUID id,UUID userId,UUID organizationId,UUID projectId,String status,UUID assignedBy,Instant assignedAt,UUID currentAssignmentId,String roleCode) { }
    record Asignacion(UUID id,UUID userId,UUID projectId,UUID membershipId,String roleCode,String status,UUID assignedBy,Instant assignedAt,Instant revokedAt,List<Alcance> scopes) {
        public Asignacion { scopes=List.copyOf(scopes); }
    }
    record Proyecto(ContextoApi.Proyecto project,long revision) { }
    record Contexto(UUID organizationId,String organizationName,UUID projectId,String projectName,String projectStatus,UUID membershipId,String roleCode) { }
    record PermisoPropio(String permission,List<Alcance> scopes) { public PermisoPropio { scopes=List.copyOf(scopes); } }
    Pagina<MembresiaEmpresa> listarMembresiasEmpresa(UUID org,int page,int size);
    void revocarMembresiaEmpresa(UUID org,UUID member,String reason);
    MembresiaEmpresa reactivarMembresiaEmpresa(UUID org,UUID member,String reason);
    Pagina<MembresiaProyecto> listarMembresias(UUID org,UUID project,int page,int size);
    MembresiaProyecto incorporar(UUID org,UUID project,UUID user,String reason);
    void revocarMembresia(UUID org,UUID project,UUID member,String reason);
    MembresiaProyecto reactivarMembresia(UUID org,UUID project,UUID member,String reason);
    List<Rol> listarRoles(UUID org);
    Asignacion asignarRol(UUID org,UUID project,UUID member,String role,List<Alcance> scopes,UUID expectedAssignment,String reason);
    void revocarRol(UUID org,UUID project,UUID member,UUID assignment,String reason);
    Asignacion consultarRol(UUID org,UUID project,UUID member);
    Pagina<Asignacion> historialRoles(UUID org,UUID project,UUID member,int page,int size);
    Proyecto consultarProyecto(UUID org,UUID project);
    Proyecto editarProyecto(UUID org,UUID project,String name,long expectedRevision,String reason);
    Proyecto archivarProyecto(UUID org,UUID project,long expectedRevision,String reason);
    Proyecto reactivarProyecto(UUID org,UUID project,long expectedRevision,String reason);
    Pagina<Contexto> misContextos(int page,int size);
    Contexto miContexto(UUID project);
    List<PermisoPropio> misPermisos(UUID project);
}
