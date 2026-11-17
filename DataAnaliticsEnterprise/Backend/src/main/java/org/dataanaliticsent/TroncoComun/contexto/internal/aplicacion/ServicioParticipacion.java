package org.dataanaliticsent.TroncoComun.contexto.internal.aplicacion;
import java.util.*;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.contexto.*;
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi.Pagina;
import org.dataanaliticsent.TroncoComun.contexto.ParticipacionApi.*;
import org.dataanaliticsent.TroncoComun.contexto.internal.dominio.RepositorioParticipacion;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServicioParticipacion implements ParticipacionApi {
    private static final Set<String> FUNCIONALES=Set.of("GESTOR_CATALOGO","ANALISTA_CALIDAD","RESPONSABLE_GOBIERNO","ANALISTA_NEGOCIO");
    private final IdentidadActualApi identidad;
    private final AutorizacionApi permisos;
    private final RepositorioParticipacion datos;
    private final List<ProveedorRecursosAutorizacion> recursos;
    public ServicioParticipacion(IdentidadActualApi identidad,AutorizacionApi permisos,RepositorioParticipacion datos,List<ProveedorRecursosAutorizacion> recursos) {
        this.identidad=identidad; this.permisos=permisos; this.datos=datos; this.recursos=List.copyOf(recursos);
    }
    private static ContextoException error(ContextoException.Tipo t) { return new ContextoException(t); }
    private static UUID id(UUID id) { if(id==null) throw error(ContextoException.Tipo.INVALIDO); return id; }
    private static String texto(String s,int max) {
        if(s==null || s.isBlank() || s.strip().codePointCount(0,s.strip().length())>max) throw error(ContextoException.Tipo.INVALIDO); return s.strip();
    }
    private UUID actor(UUID org,String permission,boolean write) {
        UUID actor=identidad.usuarioId(); var request=SolicitudAcceso.organizacion(permission,id(org)); permisos.exigir(actor,request);
        if(write) { datos.bloquear(); permisos.exigir(actor,request); if(!datos.empresaActiva(org)) throw error(ContextoException.Tipo.NO_DISPONIBLE); }
        return actor;
    }
    private void protegido(UUID actor,UUID target) {
        if(actor.equals(target) || (datos.administrador(target) && !datos.administradorSistema(actor))) throw error(ContextoException.Tipo.CONFLICTO);
    }
    private static long offset(int page,int size) { if(page<1 || size<1 || size>100) throw error(ContextoException.Tipo.INVALIDO); return ((long)page-1)*size; }
    private static <T> Pagina<T> pagina(List<T> rows,int p,int s) { return new Pagina<>(rows.size()>s?rows.subList(0,s):rows,p,s,rows.size()>s); }
    private Proyecto proyecto(UUID org,UUID project,boolean lock,boolean active) {
        var p=datos.proyecto(org,id(project),lock).orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE));
        if(active && !"active".equals(p.project().status())) throw error(ContextoException.Tipo.CONFLICTO);
        return p;
    }
    private MembresiaProyecto member(UUID org,UUID project,UUID member) {
        return datos.membresia(org,project,id(member)).orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE));
    }
    private MembresiaEmpresa empleado(UUID org,UUID user) {
        var m=datos.empleado(org,user).orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE));
        if(!"active".equals(m.status()) || !datos.usuarioActivo(user) || datos.otraEmpresa(user,org)) throw error(ContextoException.Tipo.CONFLICTO);
        return m;
    }
    @Override @Transactional(readOnly=true) public Pagina<MembresiaEmpresa> listarMembresiasEmpresa(UUID org,int p,int s) {
        actor(org,"tc.company.memberships.read",false); return pagina(datos.miembrosEmpresa(org,s+1,offset(p,s)),p,s);
    }
    @Override public void revocarMembresiaEmpresa(UUID org,UUID member,String reason) {
        UUID actor=actor(org,"tc.company.memberships.revoke",true); reason=texto(reason,500);
        var m=datos.membresiaEmpresa(org,id(member)).orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE)); protegido(actor,m.userId());
        if("inactive".equals(m.status())) return;
        datos.estadoEmpresa(m.id(),"inactive"); datos.auditar(actor,org,null,"contexto.membresia_organizacion",m.id(),"tc.company_membership.revoke",reason,m.userId());
    }
    @Override public MembresiaEmpresa reactivarMembresiaEmpresa(UUID org,UUID member,String reason) {
        UUID actor=actor(org,"tc.company.memberships.reactivate",true); reason=texto(reason,500);
        var m=datos.membresiaEmpresa(org,id(member)).orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE)); protegido(actor,m.userId());
        if(!datos.usuarioActivo(m.userId()) || datos.otraEmpresa(m.userId(),org)) throw error(ContextoException.Tipo.CONFLICTO);
        if("inactive".equals(m.status())) { datos.estadoEmpresa(m.id(),"active"); datos.auditar(actor,org,null,"contexto.membresia_organizacion",m.id(),"tc.company_membership.reactivate",reason,m.userId()); }
        return datos.membresiaEmpresa(org,m.id()).orElseThrow();
    }
    @Override @Transactional(readOnly=true) public Pagina<MembresiaProyecto> listarMembresias(UUID org,UUID project,int p,int s) {
        actor(org,"tc.company.memberships.read",false); proyecto(org,project,false,false); return pagina(datos.miembros(org,project,s+1,offset(p,s)),p,s);
    }
    @Override public MembresiaProyecto incorporar(UUID org,UUID project,UUID user,String reason) {
        UUID actor=actor(org,"tc.company.memberships.assign",true); proyecto(org,project,true,true); id(user); protegido(actor,user); reason=texto(reason,500);
        var mo=empleado(org,user); if(datos.participante(org,project,user).isPresent()) throw error(ContextoException.Tipo.CONFLICTO);
        try {
            var result=datos.incorporar(org,project,user,mo.id(),actor);
            datos.auditar(actor,org,project,"contexto.membresia_proyecto",result.id(),"tc.project_membership.assign",reason,user); return result;
        } catch(DataIntegrityViolationException conflict) { throw error(ContextoException.Tipo.CONFLICTO); }
    }
    @Override public void revocarMembresia(UUID org,UUID project,UUID membership,String reason) {
        UUID actor=actor(org,"tc.company.memberships.revoke",true); proyecto(org,project,true,false); reason=texto(reason,500);
        var m=member(org,project,membership); protegido(actor,m.userId()); if("inactive".equals(m.status())) return;
        datos.estadoProyecto(m.id(),"inactive"); datos.auditar(actor,org,project,"contexto.membresia_proyecto",m.id(),"tc.project_membership.revoke",reason,m.userId());
    }
    @Override public MembresiaProyecto reactivarMembresia(UUID org,UUID project,UUID membership,String reason) {
        UUID actor=actor(org,"tc.company.memberships.reactivate",true); proyecto(org,project,true,true); reason=texto(reason,500);
        var m=member(org,project,membership); protegido(actor,m.userId()); empleado(org,m.userId());
        if("inactive".equals(m.status())) { datos.estadoProyecto(m.id(),"active"); datos.auditar(actor,org,project,"contexto.membresia_proyecto",m.id(),"tc.project_membership.reactivate",reason,m.userId()); }
        return member(org,project,m.id());
    }
    @Override @Transactional(readOnly=true) public List<Rol> listarRoles(UUID org) { actor(org,"tc.company.roles.read",false); return datos.roles(); }
    private List<Alcance> scopes(UUID project,List<Alcance> values) {
        if(values==null || values.isEmpty() || values.size()>50) throw error(ContextoException.Tipo.INVALIDO);
        Set<Alcance> seen=new HashSet<>();
        for(var a:values) {
            if(a==null || a.type()==null || (a.type()==TipoAlcance.PROJECT)!=(a.resourceId()==null) || !seen.add(a)) throw error(ContextoException.Tipo.INVALIDO);
            if(a.type()!=TipoAlcance.PROJECT) {
                var type=a.type()==TipoAlcance.SOURCE?SolicitudAcceso.TipoRecurso.SOURCE:SolicitudAcceso.TipoRecurso.ASSET;
                var r=new SolicitudAcceso.Recurso(type,a.resourceId()); var providers=recursos.stream().filter(p->p.soporta(type)).toList();
                if(providers.size()!=1 || providers.getFirst().resolver(r).filter(j->j.proyectoId().equals(project) && j.corresponde(r)).isEmpty()) throw error(ContextoException.Tipo.NO_DISPONIBLE);
            }
        }
        if(seen.stream().anyMatch(a->a.type()==TipoAlcance.PROJECT) && seen.size()!=1) throw error(ContextoException.Tipo.INVALIDO);
        return values.stream().sorted(Comparator.comparing((Alcance a)->a.type().name()).thenComparing(a->a.resourceId()==null?"":a.resourceId().toString())).toList();
    }
    @Override public Asignacion asignarRol(UUID org,UUID project,UUID membership,String role,List<Alcance> scopes,UUID expected,String reason) {
        UUID actor=actor(org,"tc.company.roles.assign",true); proyecto(org,project,true,true); reason=texto(reason,500);
        var m=member(org,project,membership); protegido(actor,m.userId()); empleado(org,m.userId());
        if(!"active".equals(m.status()) || role==null || !FUNCIONALES.contains(role) || datos.roles().stream().noneMatch(r->r.code().equals(role))) throw error(ContextoException.Tipo.CONFLICTO);
        var validated=scopes(project,scopes); var old=datos.vigente(project,membership);
        if(!Objects.equals(old.map(Asignacion::id).orElse(null),expected)) throw error(ContextoException.Tipo.CONFLICTO);
        if(old.isPresent() && old.get().roleCode().equals(role) && new HashSet<>(old.get().scopes()).equals(new HashSet<>(validated))) return old.get();
        try {
            if(old.isPresent()) datos.revocar(old.get().id());
            var result=datos.asignar(project,membership,m.userId(),role,validated,actor);
            if(old.isPresent()) datos.auditar(actor,org,project,"autorizacion.asignacion_rol",old.get().id(),"tc.functional_role.revoke","Sustitución explícita",m.userId());
            datos.auditar(actor,org,project,"autorizacion.asignacion_rol",result.id(),"tc.functional_role.assign",reason,m.userId()); return result;
        } catch(DataIntegrityViolationException conflict) { throw error(ContextoException.Tipo.CONFLICTO); }
    }
    @Override public void revocarRol(UUID org,UUID project,UUID membership,UUID assignment,String reason) {
        UUID actor=actor(org,"tc.company.roles.revoke",true); proyecto(org,project,true,false); reason=texto(reason,500);
        var m=member(org,project,membership); protegido(actor,m.userId());
        var a=datos.asignacion(project,membership,id(assignment)).orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE));
        if(!"active".equals(a.status()) || a.revokedAt()!=null) return;
        datos.revocar(a.id()); datos.auditar(actor,org,project,"autorizacion.asignacion_rol",a.id(),"tc.functional_role.revoke",reason,m.userId());
    }
    @Override @Transactional(readOnly=true) public Asignacion consultarRol(UUID org,UUID project,UUID member) {
        actor(org,"tc.company.roles.read",false); proyecto(org,project,false,false); member(org,project,member);
        return datos.vigente(project,member).orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE));
    }
    @Override @Transactional(readOnly=true) public Pagina<Asignacion> historialRoles(UUID org,UUID project,UUID member,int p,int s) {
        actor(org,"tc.company.roles.read",false); proyecto(org,project,false,false); member(org,project,member);
        return pagina(datos.historial(project,member,s+1,offset(p,s)),p,s);
    }
    @Override @Transactional(readOnly=true) public Proyecto consultarProyecto(UUID org,UUID project) { actor(org,"tc.company.projects.read",false); return proyecto(org,project,false,false); }
    @Override public Proyecto editarProyecto(UUID org,UUID project,String name,long revision,String reason) {
        UUID actor=actor(org,"tc.company.projects.update",true); var old=proyecto(org,project,true,false); name=texto(name,200); reason=texto(reason,500);
        if(revision<0) throw error(ContextoException.Tipo.INVALIDO); if(old.revision()!=revision) throw error(ContextoException.Tipo.CONFLICTO);
        if(old.project().name().equals(name)) return old;
        try { var result=datos.cambiarProyecto(org,project,name,old.project().status()); datos.auditar(actor,org,project,"contexto.proyecto",project,"tc.project.update",reason,null); return result; }
        catch(DataIntegrityViolationException conflict) { throw error(ContextoException.Tipo.CONFLICTO); }
    }
    private Proyecto estado(UUID org,UUID project,long revision,String reason,boolean archive) {
        UUID actor=actor(org,archive?"tc.company.projects.archive":"tc.company.projects.reactivate",true); var old=proyecto(org,project,true,false); reason=texto(reason,500);
        if(revision<0) throw error(ContextoException.Tipo.INVALIDO); String target=archive?"archived":"active";
        if("inactive".equals(old.project().status())) throw error(ContextoException.Tipo.CONFLICTO);
        if(target.equals(old.project().status())) return old; if(old.revision()!=revision) throw error(ContextoException.Tipo.CONFLICTO);
        var result=datos.cambiarProyecto(org,project,old.project().name(),target);
        datos.auditar(actor,org,project,"contexto.proyecto",project,archive?"tc.project.archive":"tc.project.reactivate",reason,null); return result;
    }
    @Override public Proyecto archivarProyecto(UUID org,UUID project,long revision,String reason) { return estado(org,project,revision,reason,true); }
    @Override public Proyecto reactivarProyecto(UUID org,UUID project,long revision,String reason) { return estado(org,project,revision,reason,false); }
    private UUID propio(String permission) { UUID actor=identidad.usuarioId(); permisos.exigir(actor,SolicitudAcceso.propia(permission,actor)); return actor; }
    @Override @Transactional(readOnly=true) public Pagina<Contexto> misContextos(int p,int s) { UUID actor=propio("tc.self.contexts.read"); return pagina(datos.contextos(actor,null,s+1,offset(p,s)),p,s); }
    private Contexto contexto(UUID actor,UUID project) {
        return datos.contextos(actor,id(project),1,0).stream().findFirst().orElseThrow(()->error(ContextoException.Tipo.NO_DISPONIBLE));
    }
    @Override @Transactional(readOnly=true) public Contexto miContexto(UUID project) { return contexto(propio("tc.self.contexts.read"),project); }
    @Override @Transactional(readOnly=true) public List<PermisoPropio> misPermisos(UUID project) {
        UUID actor=propio("tc.self.permissions.read"); contexto(actor,project);
        return datos.permisosProyecto(actor,project).stream().map(p->new PermisoPropio(p,permisos.alcancesPermitidos(actor,p,project).stream()
            .map(a->new Alcance(TipoAlcance.valueOf(a.tipo().name()),a.recursoId())).toList())).filter(p->!p.scopes().isEmpty()).toList();
    }
}
