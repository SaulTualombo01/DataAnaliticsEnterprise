package org.dataanaliticsent.contexto.internal.presentacion;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.contexto.ContextoApi;
import org.dataanaliticsent.contexto.ContextoApi.*;
import org.dataanaliticsent.contexto.ContextoException;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.springframework.web.bind.annotation.*;

@RestController
public class ContextoController {
    public record Alta(String name,String reason) { }
    public record Designacion(String user_id,String reason) { }
    public record Revocacion(String reason) { }
    public record OrganizacionHttp(UUID id,String name,String status,Instant created_at) {
        static OrganizacionHttp from(Organizacion o) { return new OrganizacionHttp(o.id(),o.name(),o.status(),o.createdAt()); }
    }
    public record ProyectoHttp(UUID id,UUID organization_id,String name,String status,Instant created_at) {
        static ProyectoHttp from(Proyecto p) { return new ProyectoHttp(p.id(),p.organizationId(),p.name(),p.status(),p.createdAt()); }
    }
    public record AdministradorHttp(UUID id,UUID user_id,UUID organization_id,UUID assigned_by,String status,Instant assigned_at,Instant revoked_at) {
        static AdministradorHttp from(AdministradorEmpresa a) { return new AdministradorHttp(a.id(),a.userId(),a.organizationId(),a.assignedBy(),a.status(),a.assignedAt(),a.revokedAt()); }
    }
    private final ContextoApi api;
    private final ProblemasHttp salida;
    public ContextoController(ContextoApi api,ProblemasHttp salida) { this.api=api; this.salida=salida; }
    private static UUID id(String value) {
        if(value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        return UUID.fromString(value);
    }
    private static void parametros(HttpServletRequest req,Set<String> allowed) {
        req.getParameterMap().forEach((key,values)->{
            if(!allowed.contains(key) || values.length!=1) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        });
    }
    private static int numero(HttpServletRequest req,String name,int porDefecto) {
        String value=req.getParameter(name);
        if(value==null) return porDefecto;
        if(!value.matches("[1-9][0-9]{0,9}")) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        try { return Integer.parseInt(value); } catch(NumberFormatException invalid) { throw new ContextoException(ContextoException.Tipo.INVALIDO); }
    }
    private <T,R> void pagina(HttpServletRequest req,HttpServletResponse res,Pagina<T> page,Function<T,R> mapper) throws IOException {
        salida.datos(req,res,page.data().stream().map(mapper).toList(),Map.of("page_info",Map.of(
                "page",page.page(),"page_size",page.pageSize(),"returned_count",page.data().size(),"has_next",page.hasNext())));
    }
    @PostMapping("/api/v1/admin/organizations")
    public void crear(@RequestBody Alta body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of()); var o=api.crearOrganizacion(body.name(),body.reason());
        res.setStatus(201); res.setHeader("Location","/api/v1/organizations/"+o.id()); salida.datos(req,res,OrganizacionHttp.from(o));
    }
    @GetMapping("/api/v1/admin/organizations")
    public void organizaciones(HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of("page","page_size")); pagina(req,res,api.listarOrganizaciones(numero(req,"page",1),numero(req,"page_size",25)),OrganizacionHttp::from);
    }
    @GetMapping("/api/v1/organizations/{organizationId}")
    public void organizacion(@PathVariable String organizationId,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of()); salida.datos(req,res,OrganizacionHttp.from(api.consultarOrganizacion(id(organizationId))));
    }
    @PostMapping("/api/v1/organizations/{organizationId}/projects")
    public void crearProyecto(@PathVariable String organizationId,@RequestBody Alta body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of()); var p=api.crearProyecto(id(organizationId),body.name(),body.reason());
        res.setStatus(201); salida.datos(req,res,ProyectoHttp.from(p));
    }
    @GetMapping("/api/v1/organizations/{organizationId}/projects")
    public void proyectos(@PathVariable String organizationId,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of("page","page_size")); pagina(req,res,api.listarProyectos(id(organizationId),numero(req,"page",1),numero(req,"page_size",25)),ProyectoHttp::from);
    }
    @PostMapping("/api/v1/admin/organizations/{organizationId}/administrators")
    public void designar(@PathVariable String organizationId,@RequestBody Designacion body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of()); var a=api.asignarAdministrador(id(organizationId),id(body.user_id()),body.reason());
        res.setStatus(201); salida.datos(req,res,AdministradorHttp.from(a));
    }
    @GetMapping("/api/v1/admin/organizations/{organizationId}/administrators")
    public void administradores(@PathVariable String organizationId,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of("page","page_size")); pagina(req,res,api.listarAdministradores(id(organizationId),numero(req,"page",1),numero(req,"page_size",25)),AdministradorHttp::from);
    }
    @PostMapping("/api/v1/admin/organizations/{organizationId}/administrators/{assignmentId}/revocations")
    public void revocar(@PathVariable String organizationId,@PathVariable String assignmentId,@RequestBody Revocacion body,HttpServletRequest req,HttpServletResponse res) {
        parametros(req,Set.of()); api.revocarAdministrador(id(organizationId),id(assignmentId),body.reason()); res.setStatus(204);
    }
}
