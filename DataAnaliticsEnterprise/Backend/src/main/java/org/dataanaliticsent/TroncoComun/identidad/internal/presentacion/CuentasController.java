package org.dataanaliticsent.TroncoComun.identidad.internal.presentacion;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.CuentasApi;
import org.dataanaliticsent.TroncoComun.identidad.CuentasApi.*;
import org.dataanaliticsent.TroncoComun.identidad.CuentasException;
import org.springframework.web.bind.annotation.*;

@RestController
public class CuentasController {
    public record NuevaInvitacion(String email,String display_name,String reason) { }
    public record Motivo(String reason) { }
    public record Activacion(String activation_token,String new_password) {
        @Override public String toString() { return "Activacion[protegido]"; }
    }
    public record CuentaHttp(UUID id,String email,String display_name,String status,Instant created_at) {
        static CuentaHttp from(Cuenta c) { return new CuentaHttp(c.id(),c.email(),c.displayName(),c.status(),c.createdAt()); }
    }
    public record InvitacionHttp(UUID id,UUID organization_id,String email,String display_name,UUID invited_by,String status,Instant created_at,Instant expires_at,UUID accepted_user_id) {
        static InvitacionHttp from(Invitacion i) { return new InvitacionHttp(i.id(),i.organizationId(),i.email(),i.displayName(),i.invitedBy(),i.status(),i.createdAt(),i.expiresAt(),i.acceptedUserId()); }
    }
    public record EntregaHttp(InvitacionHttp invitation,String delivery_mode,String activation_token) {
        @Override public String toString() { return "EntregaHttp[protegido]"; }
    }
    private final CuentasApi api;
    private final ProblemasHttp salida;
    public CuentasController(CuentasApi api,ProblemasHttp salida) { this.api=api; this.salida=salida; }
    private static UUID id(String value) {
        if(value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid();
        return UUID.fromString(value);
    }
    private static CuentasException invalid() { return new CuentasException(CuentasException.Tipo.INVALIDO); }
    private static void parametros(HttpServletRequest req,Set<String> allowed) {
        req.getParameterMap().forEach((k,v)->{ if(!allowed.contains(k) || v.length!=1) throw invalid(); });
    }
    private static int numero(HttpServletRequest req,String key,int fallback) {
        String v=req.getParameter(key); if(v==null) return fallback;
        if(!v.matches("[1-9][0-9]{0,9}")) throw invalid();
        try { return Integer.parseInt(v); } catch(NumberFormatException e) { throw invalid(); }
    }
    private <T,R> void pagina(HttpServletRequest req,HttpServletResponse res,Pagina<T> p,Function<T,R> f) throws IOException {
        salida.datos(req,res,p.data().stream().map(f).toList(),Map.of("page_info",Map.of("page",p.page(),"page_size",p.pageSize(),"returned_count",p.data().size(),"has_next",p.hasNext())));
    }
    private void vacio(HttpServletRequest req,HttpServletResponse res) {
        res.setStatus(204); res.setHeader("Cache-Control","no-store"); res.setHeader("X-Request-Id",ProblemasHttp.requestId(req));
    }
    @PostMapping("/api/v1/organizations/{organizationId}/invitations")
    public void invitar(@PathVariable String organizationId,@RequestBody NuevaInvitacion body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of()); var e=api.invitar(id(organizationId),body.email(),body.display_name(),body.reason());
        res.setStatus(201); salida.datos(req,res,new EntregaHttp(InvitacionHttp.from(e.invitation()),"manual",e.activationToken()));
    }
    @GetMapping("/api/v1/organizations/{organizationId}/invitations")
    public void invitaciones(@PathVariable String organizationId,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of("page","page_size")); pagina(req,res,api.listarInvitaciones(id(organizationId),numero(req,"page",1),numero(req,"page_size",25)),InvitacionHttp::from);
    }
    @PostMapping("/api/v1/organizations/{organizationId}/invitations/{invitationId}/revocations")
    public void revocar(@PathVariable String organizationId,@PathVariable String invitationId,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) {
        parametros(req,Set.of()); api.revocarInvitacion(id(organizationId),id(invitationId),body.reason()); vacio(req,res);
    }
    @PostMapping("/api/v1/auth/invitations/accept")
    public void aceptar(@RequestBody Activacion body,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of()); var c=api.activar(body.activation_token(),body.new_password());
        res.setStatus(201); salida.datos(req,res,CuentaHttp.from(c));
    }
    @GetMapping("/api/v1/admin/users")
    public void usuarios(HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of("page","page_size")); pagina(req,res,api.listarUsuarios(numero(req,"page",1),numero(req,"page_size",25)),CuentaHttp::from);
    }
    @GetMapping("/api/v1/organizations/{organizationId}/users")
    public void empleados(@PathVariable String organizationId,HttpServletRequest req,HttpServletResponse res) throws IOException {
        parametros(req,Set.of("page","page_size")); pagina(req,res,api.listarEmpleados(id(organizationId),numero(req,"page",1),numero(req,"page_size",25)),CuentaHttp::from);
    }
    @PostMapping("/api/v1/admin/users/{userId}/deactivations")
    public void desactivar(@PathVariable String userId,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) {
        parametros(req,Set.of()); api.desactivarUsuario(id(userId),body.reason()); vacio(req,res);
    }
    @PostMapping("/api/v1/organizations/{organizationId}/users/{userId}/deactivations")
    public void desactivarEmpleado(@PathVariable String organizationId,@PathVariable String userId,@RequestBody Motivo body,HttpServletRequest req,HttpServletResponse res) {
        parametros(req,Set.of()); api.desactivarEmpleado(id(organizationId),id(userId),body.reason()); vacio(req,res);
    }
}
