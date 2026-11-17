package org.dataanaliticsent.TroncoComun.contexto.internal.presentacion;
import java.io.IOException;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.*;
import org.dataanaliticsent.TroncoComun.contexto.*;
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi.Pagina;
import org.dataanaliticsent.TroncoComun.contexto.ParticipacionApi.*;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.springframework.web.bind.annotation.*;

@RestController
public class ParticipacionController {
    private static final String SELECTED="tic.selected_context";
    private record Seleccion(UUID actorId,UUID projectId) implements java.io.Serializable { }
    public record Motivo(String reason) { }
    public record Incorporacion(String user_id,String reason) { }
    public record CambioEstado(JsonNode expected_revision,String reason) { }
    public record Edicion(String name,JsonNode expected_revision,String reason) { }
    public record Elegir(String project_id) { }
    public record ScopeHttp(String resource_scope,String resource_id) {
        static ScopeHttp from(Alcance a) { return new ScopeHttp(a.type().name(),a.resourceId()==null?null:a.resourceId().toString()); }
        Alcance domain() {
            try { return new Alcance(TipoAlcance.valueOf(resource_scope),resource_id==null?null:id(resource_id)); }
            catch(IllegalArgumentException|NullPointerException e) { throw invalid(); }
        }
    }
    public record RolRequest(String role_code,List<ScopeHttp> scopes,@JsonProperty(required=true) String expected_assignment_id,String reason) { }
    public record EmpresaHttp(UUID id,UUID user_id,UUID organization_id,String status,UUID assigned_by,Instant assigned_at) {
        static EmpresaHttp from(MembresiaEmpresa m) { return new EmpresaHttp(m.id(),m.userId(),m.organizationId(),m.status(),m.assignedBy(),m.assignedAt()); }
    }
    public record MiembroHttp(UUID id,UUID user_id,UUID organization_id,UUID project_id,String status,UUID assigned_by,Instant assigned_at,UUID current_assignment_id,String role_code) {
        static MiembroHttp from(MembresiaProyecto m) { return new MiembroHttp(m.id(),m.userId(),m.organizationId(),m.projectId(),m.status(),m.assignedBy(),m.assignedAt(),m.currentAssignmentId(),m.roleCode()); }
    }
    public record RolHttp(String code,String name,List<String> permissions) { static RolHttp from(Rol r) { return new RolHttp(r.code(),r.name(),r.permissions()); } }
    public record AsignacionHttp(UUID id,UUID user_id,UUID project_id,UUID membership_id,String role_code,String status,UUID assigned_by,Instant assigned_at,Instant revoked_at,List<ScopeHttp> scopes) {
        static AsignacionHttp from(Asignacion a) { return new AsignacionHttp(a.id(),a.userId(),a.projectId(),a.membershipId(),a.roleCode(),a.status(),a.assignedBy(),a.assignedAt(),a.revokedAt(),a.scopes().stream().map(ScopeHttp::from).toList()); }
    }
    public record ProyectoHttp(ContextoController.ProyectoHttp project,long revision) { static ProyectoHttp from(Proyecto p) { return new ProyectoHttp(ContextoController.ProyectoHttp.from(p.project()),p.revision()); } }
    public record ContextoHttp(UUID organization_id,String organization_name,UUID project_id,String project_name,String project_status,UUID membership_id,String role_code) {
        static ContextoHttp from(Contexto c) { return new ContextoHttp(c.organizationId(),c.organizationName(),c.projectId(),c.projectName(),c.projectStatus(),c.membershipId(),c.roleCode()); }
    }
    public record PermisoHttp(String permission,List<ScopeHttp> scopes) { static PermisoHttp from(PermisoPropio p) { return new PermisoHttp(p.permission(),p.scopes().stream().map(ScopeHttp::from).toList()); } }
    private final ParticipacionApi api; private final ProblemasHttp salida; private final IdentidadActualApi identidad;
    public ParticipacionController(ParticipacionApi api,ProblemasHttp salida,IdentidadActualApi identidad) { this.api=api; this.salida=salida; this.identidad=identidad; }
    private static ContextoException invalid() { return new ContextoException(ContextoException.Tipo.INVALIDO); }
    private static UUID id(String v) {
        if(v==null || !v.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid(); return UUID.fromString(v);
    }
    private static long revision(JsonNode v) { if(v==null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue()<0) throw invalid(); return v.longValue(); }
    private static void parametros(HttpServletRequest req,boolean list) {
        Set<String> allowed=list?Set.of("page","page_size"):Set.of();
        req.getParameterMap().forEach((k,v)->{ if(!allowed.contains(k) || v.length!=1) throw invalid(); });
    }
    private static int numero(HttpServletRequest req,String k,int fallback) {
        String v=req.getParameter(k); if(v==null) return fallback; if(!v.matches("[1-9][0-9]{0,9}")) throw invalid();
        try { return Integer.parseInt(v); } catch(NumberFormatException e) { throw invalid(); }
    }
    private <T,R> void page(HttpServletRequest req,HttpServletResponse res,Pagina<T> p,Function<T,R> f) throws IOException {
        salida.datos(req,res,p.data().stream().map(f).toList(),Map.of("page_info",Map.of("page",p.page(),"page_size",p.pageSize(),"returned_count",p.data().size(),"has_next",p.hasNext())));
    }
    private void vacio(HttpServletRequest req,HttpServletResponse res) { res.setStatus(204); res.setHeader("Cache-Control","no-store"); res.setHeader("X-Request-Id",ProblemasHttp.requestId(req)); }
    @GetMapping("/api/v1/organizations/{org}/memberships")
    public void empresa(@PathVariable String org,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,true); page(req,res,api.listarMembresiasEmpresa(id(org),numero(req,"page",1),numero(req,"page_size",25)),EmpresaHttp::from);
    }
    @PostMapping("/api/v1/organizations/{org}/memberships/{member}/revocations")
    public void retirarEmpresa(@PathVariable String org,@PathVariable String member,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) {
        parametros(req,false); api.revocarMembresiaEmpresa(id(org),id(member),body.reason()); vacio(req,res);
    }
    @PostMapping("/api/v1/organizations/{org}/memberships/{member}/reactivations")
    public void reactivarEmpresa(@PathVariable String org,@PathVariable String member,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,EmpresaHttp.from(api.reactivarMembresiaEmpresa(id(org),id(member),body.reason())));
    }
    @GetMapping("/api/v1/organizations/{org}/functional-roles")
    public void roles(@PathVariable String org,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,api.listarRoles(id(org)).stream().map(RolHttp::from).toList());
    }
    @GetMapping("/api/v1/organizations/{org}/projects/{project}")
    public void proyecto(@PathVariable String org,@PathVariable String project,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,ProyectoHttp.from(api.consultarProyecto(id(org),id(project))));
    }
    @PatchMapping("/api/v1/organizations/{org}/projects/{project}")
    public void editar(@PathVariable String org,@PathVariable String project,@RequestBody Edicion body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,ProyectoHttp.from(api.editarProyecto(id(org),id(project),body.name(),revision(body.expected_revision()),body.reason())));
    }
    @PostMapping("/api/v1/organizations/{org}/projects/{project}/archivals")
    public void archivar(@PathVariable String org,@PathVariable String project,@RequestBody CambioEstado body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,ProyectoHttp.from(api.archivarProyecto(id(org),id(project),revision(body.expected_revision()),body.reason())));
    }
    @PostMapping("/api/v1/organizations/{org}/projects/{project}/reactivations")
    public void reactivarProyecto(@PathVariable String org,@PathVariable String project,@RequestBody CambioEstado body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,ProyectoHttp.from(api.reactivarProyecto(id(org),id(project),revision(body.expected_revision()),body.reason())));
    }
    @GetMapping("/api/v1/organizations/{org}/projects/{project}/memberships")
    public void miembros(@PathVariable String org,@PathVariable String project,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,true); page(req,res,api.listarMembresias(id(org),id(project),numero(req,"page",1),numero(req,"page_size",25)),MiembroHttp::from);
    }
    @PostMapping("/api/v1/organizations/{org}/projects/{project}/memberships")
    public void incorporar(@PathVariable String org,@PathVariable String project,@RequestBody Incorporacion body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); var m=api.incorporar(id(org),id(project),id(body.user_id()),body.reason()); res.setStatus(201); salida.datos(req,res,MiembroHttp.from(m));
    }
    @PostMapping("/api/v1/organizations/{org}/projects/{project}/memberships/{member}/revocations")
    public void retirar(@PathVariable String org,@PathVariable String project,@PathVariable String member,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) {
        parametros(req,false); api.revocarMembresia(id(org),id(project),id(member),body.reason()); vacio(req,res);
    }
    @PostMapping("/api/v1/organizations/{org}/projects/{project}/memberships/{member}/reactivations")
    public void reactivar(@PathVariable String org,@PathVariable String project,@PathVariable String member,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,MiembroHttp.from(api.reactivarMembresia(id(org),id(project),id(member),body.reason())));
    }
    @GetMapping("/api/v1/organizations/{org}/projects/{project}/memberships/{member}/role")
    public void rolActual(@PathVariable String org,@PathVariable String project,@PathVariable String member,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,AsignacionHttp.from(api.consultarRol(id(org),id(project),id(member))));
    }
    @PutMapping("/api/v1/organizations/{org}/projects/{project}/memberships/{member}/role")
    public void asignar(@PathVariable String org,@PathVariable String project,@PathVariable String member,@RequestBody RolRequest body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false);
        List<Alcance> scopes=body.scopes()==null?null:body.scopes().stream().map(s->{ if(s==null) throw invalid(); return s.domain(); }).toList();
        var a=api.asignarRol(id(org),id(project),id(member),body.role_code(),scopes,body.expected_assignment_id()==null?null:id(body.expected_assignment_id()),body.reason());
        salida.datos(req,res,AsignacionHttp.from(a));
    }
    @GetMapping("/api/v1/organizations/{org}/projects/{project}/memberships/{member}/roles")
    public void historial(@PathVariable String org,@PathVariable String project,@PathVariable String member,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,true); page(req,res,api.historialRoles(id(org),id(project),id(member),numero(req,"page",1),numero(req,"page_size",25)),AsignacionHttp::from);
    }
    @PostMapping("/api/v1/organizations/{org}/projects/{project}/memberships/{member}/roles/{assignment}/revocations")
    public void retirarRol(@PathVariable String org,@PathVariable String project,@PathVariable String member,@PathVariable String assignment,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) {
        parametros(req,false); api.revocarRol(id(org),id(project),id(member),id(assignment),body.reason()); vacio(req,res);
    }
    @GetMapping("/api/v1/auth/contexts")
    public void propios(HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,true); page(req,res,api.misContextos(numero(req,"page",1),numero(req,"page_size",25)),ContextoHttp::from);
    }
    @PostMapping("/api/v1/auth/context")
    public void elegir(@RequestBody Elegir body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); var c=api.miContexto(id(body.project_id()));
        req.getSession().setAttribute(SELECTED,new Seleccion(identidad.usuarioId(),c.projectId())); salida.datos(req,res,ContextoHttp.from(c));
    }
    @GetMapping("/api/v1/auth/context")
    public void elegido(HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); var session=req.getSession(false);
        if(session==null || !(session.getAttribute(SELECTED) instanceof Seleccion selected)) throw new ContextoException(ContextoException.Tipo.NO_DISPONIBLE);
        try {
            if(!identidad.usuarioId().equals(selected.actorId())) throw new ContextoException(ContextoException.Tipo.NO_DISPONIBLE);
            salida.datos(req,res,ContextoHttp.from(api.miContexto(selected.projectId())));
        } catch(ContextoException e) { if(e.tipo()==ContextoException.Tipo.NO_DISPONIBLE) session.removeAttribute(SELECTED); throw e; }
    }
    @GetMapping("/api/v1/auth/contexts/{project}/permissions")
    public void permisos(@PathVariable String project,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,false); salida.datos(req,res,api.misPermisos(id(project)).stream().map(PermisoHttp::from).toList());
    }
}
