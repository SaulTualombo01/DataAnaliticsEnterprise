package org.dataanaliticsent.TroncoComun.identidad.internal.aplicacion;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.identidad.*;
import org.dataanaliticsent.TroncoComun.identidad.CuentasApi.*;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServicioCuentas implements CuentasApi {
    private final IdentidadActualApi identidad;
    private final AutorizacionApi permisos;
    private final RepositorioCuentas datos;
    private final HashClaves claves;
    private final Clock clock;
    private final PoliticaInvitaciones parametros;
    public ServicioCuentas(IdentidadActualApi identidad,AutorizacionApi permisos,RepositorioCuentas datos,HashClaves claves,Clock clock,PoliticaInvitaciones parametros) {
        this.identidad=identidad; this.permisos=permisos; this.datos=datos; this.claves=claves; this.clock=clock; this.parametros=parametros;
    }
    private UUID actor(SolicitudAcceso s,boolean escritura) {
        UUID actor=identidad.usuarioId(); permisos.exigir(actor,s);
        if(escritura) { datos.bloquearAdministracion(); permisos.exigir(actor,s); }
        return actor;
    }
    private static UUID id(UUID v) { if(v==null) throw error(CuentasException.Tipo.INVALIDO); return v; }
    private static CuentasException error(CuentasException.Tipo t) { return new CuentasException(t); }
    private static String texto(String v,int max) {
        if(v==null || v.isBlank() || v.codePointCount(0,v.length())>max) throw error(CuentasException.Tipo.INVALIDO);
        return v.strip();
    }
    private static String correo(String value) {
        String v=texto(value,254).toLowerCase(Locale.ROOT);
        if(!v.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}")) throw error(CuentasException.Tipo.INVALIDO);
        String local=v.substring(0,v.indexOf('@'));
        if(local.length()>64 || local.startsWith(".") || local.endsWith(".") || local.contains("..")) throw error(CuentasException.Tipo.INVALIDO);
        return v;
    }
    private void empresa(UUID org) { if(!datos.bloquearEmpresaActiva(org)) throw error(CuentasException.Tipo.NO_DISPONIBLE); }
    private static long offset(int page,int size) {
        if(page<1 || size<1 || size>100) throw error(CuentasException.Tipo.INVALIDO);
        return ((long)page-1)*size;
    }
    private static <T> Pagina<T> pagina(List<T> rows,int page,int size) {
        return new Pagina<>(rows.size()>size?rows.subList(0,size):rows,page,size,rows.size()>size);
    }
    @Override @Transactional(readOnly=true) public DetalleCuenta consultarUsuario(UUID user) {
        actor(SolicitudAcceso.plataforma("tc.users.read"),false); return datos.detalle(id(user));
    }
    @Override @Transactional(readOnly=true) public DetalleCuenta consultarEmpleado(UUID org,UUID user) {
        actor(SolicitudAcceso.organizacion("tc.company.users.read",id(org)),false);
        if(!datos.pertenece(id(user),org)) throw error(CuentasException.Tipo.NO_DISPONIBLE); return datos.detalle(user);
    }
    private DetalleCuenta editar(UUID actor,UUID org,UUID user,String nombre,long revision,String motivo) {
        nombre=texto(nombre,200); motivo=texto(motivo,500); if(revision<0) throw error(CuentasException.Tipo.INVALIDO);
        var old=datos.bloquearCuenta(id(user)); var detail=datos.detalle(user);
        if(detail.revision()!=revision) throw error(CuentasException.Tipo.CONFLICTO);
        if(old.displayName().equals(nombre)) return detail;
        var result=datos.editarNombre(user,nombre); datos.auditar(actor,org,user,"tc.user.update",motivo,user); return result;
    }
    @Override public DetalleCuenta editarUsuario(UUID user,String nombre,long revision,String motivo) {
        var s=SolicitudAcceso.plataforma("tc.users.update"); UUID actor=actor(s,true);
        var result=editar(actor,null,user,nombre,revision,motivo); permisos.exigir(actor,s); return result;
    }
    @Override public DetalleCuenta editarEmpleado(UUID org,UUID user,String nombre,long revision,String motivo) {
        var s=SolicitudAcceso.organizacion("tc.company.users.update",id(org)); UUID actor=actor(s,true); empresa(org);
        if(!datos.pertenece(id(user),org)) throw error(CuentasException.Tipo.NO_DISPONIBLE);
        if(actor.equals(user) || datos.administrador(user) || datos.otraEmpresaActiva(user,org)) throw error(CuentasException.Tipo.CONFLICTO);
        var result=editar(actor,org,user,nombre,revision,motivo); permisos.exigir(actor,s); return result;
    }
    @Override public EntregaInvitacion invitar(UUID org,String email,String nombre,String motivo) {
        var s=SolicitudAcceso.organizacion("tc.company.users.invite",id(org)); UUID actor=actor(s,true); empresa(org);
        email=correo(email); nombre=texto(nombre,200); motivo=texto(motivo,500);
        if(!parametros.manualDeliveryEnabled()) throw error(CuentasException.Tipo.ENTREGA_NO_CONFIGURADA);
        if(datos.existeCorreo(email)) throw error(CuentasException.Tipo.CONFLICTO);
        var now=clock.instant();
        for(var old:datos.pendientes(org,email)) {
            datos.revocarInvitacion(old.id(),now);
            datos.auditar(actor,org,old.id(),"tc.invitation.revoke","Reemplazo por nueva invitación",null);
        }
        String token=TokensInvitacion.nuevo();
        var result=datos.crearInvitacion(UUID.randomUUID(),org,email,nombre,actor,TokensInvitacion.hash(token),now,now.plus(parametros.invitationTtl()));
        datos.auditar(actor,org,result.id(),"tc.invitation.create",motivo,null); permisos.exigir(actor,s);
        return new EntregaInvitacion(result,token);
    }
    @Override @Transactional(readOnly=true) public Pagina<Invitacion> listarInvitaciones(UUID org,int page,int size) {
        actor(SolicitudAcceso.organizacion("tc.company.invitations.read",id(org)),false);
        return pagina(datos.invitaciones(org,size+1,offset(page,size)),page,size);
    }
    @Override public void revocarInvitacion(UUID org,UUID invitation,String motivo) {
        var s=SolicitudAcceso.organizacion("tc.company.invitations.revoke",id(org)); UUID actor=actor(s,true); empresa(org);
        motivo=texto(motivo,500);
        var old=datos.invitacion(org,id(invitation)).orElseThrow(()->error(CuentasException.Tipo.NO_DISPONIBLE));
        if("revoked".equals(old.status())) return;
        if("accepted".equals(old.status())) throw error(CuentasException.Tipo.CONFLICTO);
        datos.revocarInvitacion(old.id(),clock.instant());
        datos.auditar(actor,org,old.id(),"tc.invitation.revoke",motivo,null); permisos.exigir(actor,s);
    }
    @Override public Cuenta activar(String token,String nuevaClave) {
        String hash=TokensInvitacion.hash(token);
        try { PoliticaClave.validarNueva(nuevaClave); } catch(IllegalArgumentException invalid) { throw error(CuentasException.Tipo.INVALIDO); }
        datos.bloquearAdministracion();
        var row=datos.porToken(hash).orElseThrow(()->error(CuentasException.Tipo.INVITACION_INVALIDA));
        var invitation=row.invitation(); var now=clock.instant();
        if(!"pending".equals(row.status()) || !invitation.expiresAt().isAfter(now)
            || !datos.bloquearEmpresaActiva(invitation.organizationId()) || datos.existeCorreo(invitation.email())) throw error(CuentasException.Tipo.INVITACION_INVALIDA);
        if(!"active".equals(datos.bloquearCuenta(invitation.invitedBy()).status())) throw error(CuentasException.Tipo.INVITACION_INVALIDA);
        try { permisos.exigir(invitation.invitedBy(),SolicitudAcceso.organizacion("tc.company.users.invite",invitation.organizationId())); }
        catch(AccesoDenegadoException denied) { throw error(CuentasException.Tipo.INVITACION_INVALIDA); }
        // La identidad/correo/empresa están fijados por la invitación, nunca por el solicitante.
        try {
            String encoded=claves.codificar(nuevaClave);
            if(!invitation.expiresAt().isAfter(clock.instant())) throw error(CuentasException.Tipo.INVITACION_INVALIDA);
            Cuenta user=datos.crearCuenta(UUID.randomUUID(),invitation.email(),invitation.displayName(),encoded,now);
            datos.incorporar(user.id(),invitation.organizationId(),invitation.invitedBy());
            datos.aceptar(invitation.id(),user.id(),now);
            datos.auditar(user.id(),invitation.organizationId(),invitation.id(),"tc.invitation.accept","Activación mediante invitación",user.id());
            return user;
        } catch(DataIntegrityViolationException conflict) { throw error(CuentasException.Tipo.INVITACION_INVALIDA); }
    }
    @Override @Transactional(readOnly=true) public Pagina<Cuenta> listarUsuarios(int page,int size) {
        actor(SolicitudAcceso.plataforma("tc.users.read"),false);
        return pagina(datos.cuentas(null,size+1,offset(page,size)),page,size);
    }
    @Override @Transactional(readOnly=true) public Pagina<Cuenta> listarEmpleados(UUID org,int page,int size) {
        actor(SolicitudAcceso.organizacion("tc.company.users.read",id(org)),false);
        return pagina(datos.cuentas(org,size+1,offset(page,size)),page,size);
    }
    @Override public void desactivarUsuario(UUID user,String motivo) {
        var s=SolicitudAcceso.plataforma("tc.users.deactivate"); UUID actor=actor(s,true);
        desactivar(actor,null,id(user),motivo); if(!actor.equals(user)) permisos.exigir(actor,s);
    }
    @Override public void desactivarEmpleado(UUID org,UUID user,String motivo) {
        var s=SolicitudAcceso.organizacion("tc.company.users.deactivate",id(org)); UUID actor=actor(s,true); empresa(org); id(user);
        if(!datos.pertenece(user,org)) throw error(CuentasException.Tipo.NO_DISPONIBLE);
        if(actor.equals(user) || datos.administrador(user) || datos.otraEmpresaActiva(user,org)) throw error(CuentasException.Tipo.CONFLICTO);
        desactivar(actor,org,user,motivo); permisos.exigir(actor,s);
    }
    private void desactivar(UUID actor,UUID org,UUID user,String motivo) {
        motivo=texto(motivo,500); var cuenta=datos.bloquearCuenta(user);
        if("inactive".equals(cuenta.status())) return;
        if(datos.administradorAccesible(user) && datos.administradoresAccesibles()<=1) throw error(CuentasException.Tipo.CONFLICTO);
        datos.desactivar(user); datos.auditar(actor,org,user,"tc.user.deactivate",motivo,user);
    }
}
