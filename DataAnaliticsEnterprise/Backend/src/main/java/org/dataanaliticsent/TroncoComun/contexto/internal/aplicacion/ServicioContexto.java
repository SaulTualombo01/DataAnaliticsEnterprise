package org.dataanaliticsent.TroncoComun.contexto.internal.aplicacion;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.autorizacion.AutorizacionApi;
import org.dataanaliticsent.TroncoComun.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.TroncoComun.contexto.*;
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi.*;
import org.dataanaliticsent.TroncoComun.contexto.internal.dominio.RepositorioContexto;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServicioContexto implements ContextoApi {
    private final IdentidadActualApi identidad;
    private final AutorizacionApi permisos;
    private final RepositorioContexto datos;
    public ServicioContexto(IdentidadActualApi identidad,AutorizacionApi permisos,RepositorioContexto datos) {
        this.identidad=identidad; this.permisos=permisos; this.datos=datos;
    }
    private UUID actor(SolicitudAcceso solicitud,boolean escritura) {
        UUID actor=identidad.usuarioId(); permisos.exigir(actor,solicitud);
        if(escritura) { datos.bloquearEscritura(actor); permisos.exigir(actor,solicitud); }
        return actor;
    }
    private static String texto(String valor,int max) {
        if(valor==null || valor.isBlank()) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        String result=valor.strip();
        if(result.codePointCount(0,result.length())>max) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        return result;
    }
    private static UUID id(UUID value) {
        if(value==null) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        return value;
    }
    private Organizacion organizacion(UUID id,boolean activa) {
        if(id==null) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        var org=datos.organizacion(id).orElseThrow(()->new ContextoException(ContextoException.Tipo.NO_DISPONIBLE));
        if(activa && !"active".equals(org.status())) throw new ContextoException(ContextoException.Tipo.CONFLICTO);
        return org;
    }
    private static long offset(int pagina,int tamano) {
        if(pagina<1 || tamano<1 || tamano>100) throw new ContextoException(ContextoException.Tipo.INVALIDO);
        return ((long)pagina-1)*tamano;
    }
    private static <T> Pagina<T> pagina(List<T> filas,int pagina,int tamano) {
        return new Pagina<>(filas.size()>tamano?filas.subList(0,tamano):filas,pagina,tamano,filas.size()>tamano);
    }
    @Override public Organizacion crearOrganizacion(String nombre,String motivo) {
        var solicitud=SolicitudAcceso.plataforma("tc.organizations.create"); UUID actor=actor(solicitud,true);
        nombre=texto(nombre,200); motivo=texto(motivo,500);
        var result=datos.crearOrganizacion(UUID.randomUUID(),nombre);
        datos.auditar(actor,result.id(),null,"contexto.organizacion",result.id(),"tc.organization.create",motivo,null);
        permisos.exigir(actor,solicitud); return result;
    }
    @Override @Transactional(readOnly=true) public Pagina<Organizacion> listarOrganizaciones(int pagina,int tamano) {
        actor(SolicitudAcceso.plataforma("tc.organizations.read"),false);
        return pagina(datos.organizaciones(tamano+1,offset(pagina,tamano)),pagina,tamano);
    }
    @Override @Transactional(readOnly=true) public Organizacion consultarOrganizacion(UUID org) {
        actor(SolicitudAcceso.organizacion("tc.company.organizations.read",id(org)),false);
        return organizacion(org,true);
    }
    @Override public Proyecto crearProyecto(UUID org,String nombre,String motivo) {
        var solicitud=SolicitudAcceso.organizacion("tc.company.projects.create",id(org)); UUID actor=actor(solicitud,true);
        datos.bloquearOrganizacion(org); organizacion(org,true); nombre=texto(nombre,200); motivo=texto(motivo,500);
        try {
            var result=datos.crearProyecto(UUID.randomUUID(),org,nombre);
            datos.auditar(actor,org,result.id(),"contexto.proyecto",result.id(),"tc.project.create",motivo,null);
            permisos.exigir(actor,solicitud); return result;
        } catch(DataIntegrityViolationException conflict) { throw new ContextoException(ContextoException.Tipo.CONFLICTO); }
    }
    @Override @Transactional(readOnly=true) public Pagina<Proyecto> listarProyectos(UUID org,int pagina,int tamano) {
        actor(SolicitudAcceso.organizacion("tc.company.projects.read",id(org)),false);
        return pagina(datos.proyectos(org,tamano+1,offset(pagina,tamano)),pagina,tamano);
    }
    @Override public AdministradorEmpresa asignarAdministrador(UUID org,UUID usuario,String motivo) {
        var solicitud=SolicitudAcceso.plataforma("tc.company_administrators.assign"); UUID actor=actor(solicitud,true);
        datos.bloquearOrganizacion(id(org)); organizacion(org,true); id(usuario); motivo=texto(motivo,500);
        if(!"active".equals(datos.bloquearUsuario(usuario)) || datos.otraEmpresaActiva(usuario,org)) throw new ContextoException(ContextoException.Tipo.CONFLICTO);
        var previa=datos.membresia(usuario,org);
        if(previa.isPresent() && !"active".equals(previa.get().status())) throw new ContextoException(ContextoException.Tipo.CONFLICTO);
        UUID membresia=previa.isPresent()?previa.get().id():datos.crearMembresia(usuario,org,actor);
        try {
            var result=datos.asignarAdministrador(usuario,org,membresia,actor);
            datos.auditar(actor,org,null,"autorizacion.administrador_empresa",result.id(),"tc.company_administrator.assign",motivo,usuario);
            permisos.exigir(actor,solicitud); return result;
        } catch(DataIntegrityViolationException conflict) { throw new ContextoException(ContextoException.Tipo.CONFLICTO); }
    }
    @Override @Transactional(readOnly=true) public Pagina<AdministradorEmpresa> listarAdministradores(UUID org,int pagina,int tamano) {
        actor(SolicitudAcceso.plataforma("tc.company_administrators.read"),false); organizacion(org,false);
        return pagina(datos.administradores(org,tamano+1,offset(pagina,tamano)),pagina,tamano);
    }
    @Override public void revocarAdministrador(UUID org,UUID asignacion,String motivo) {
        var solicitud=SolicitudAcceso.plataforma("tc.company_administrators.revoke"); UUID actor=actor(solicitud,true);
        datos.bloquearOrganizacion(id(org)); organizacion(org,false); motivo=texto(motivo,500);
        var vigente=datos.bloquearAdministrador(org,id(asignacion))
                .orElseThrow(()->new ContextoException(ContextoException.Tipo.NO_DISPONIBLE));
        if("inactive".equals(vigente.status())) return;
        datos.revocarAdministrador(asignacion);
        datos.auditar(actor,org,null,"autorizacion.administrador_empresa",asignacion,"tc.company_administrator.revoke",motivo,vigente.userId());
        permisos.exigir(actor,solicitud);
    }
}
