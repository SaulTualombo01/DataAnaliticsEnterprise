package org.dataanaliticsent.TroncoComun.contexto.internal.aplicacion;
import java.util.*;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.contexto.*;
import org.dataanaliticsent.TroncoComun.contexto.internal.dominio.RepositorioAdministracionActual;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ServicioAdministracionActual implements AdministracionActualApi {
    private final IdentidadActualApi identidad; private final AutorizacionApi permisos; private final RepositorioAdministracionActual datos;
    public ServicioAdministracionActual(IdentidadActualApi identidad,AutorizacionApi permisos,RepositorioAdministracionActual datos) { this.identidad=identidad; this.permisos=permisos; this.datos=datos; }
    private List<String> organizacion(UUID actor,UUID org,List<String> candidates) {
        return candidates.stream().filter(p->permisos.evaluar(actor,SolicitudAcceso.organizacion(p,org)).permitido()).toList();
    }
    public Acceso consultar(UUID org) {
        UUID actor=identidad.usuarioId(); permisos.exigir(actor,SolicitudAcceso.propia("tc.self.permissions.read",actor));
        var platform=datos.permisos("PLATFORM").stream().filter(p->permisos.evaluar(actor,SolicitudAcceso.plataforma(p)).permitido()).toList();
        var candidates=datos.permisos("ORGANIZATION");
        var companies=datos.empresasDelegadas(actor).stream().filter(o->!organizacion(actor,o.id(),candidates).isEmpty()).toList();
        if(org==null) return new Acceso(platform,companies,null,List.of());
        var allowed=organizacion(actor,org,candidates);
        if(allowed.isEmpty() && !platform.contains("tc.organizations.read")) throw new ContextoException(ContextoException.Tipo.NO_DISPONIBLE);
        var selected=datos.empresa(org).orElseThrow(()->new ContextoException(ContextoException.Tipo.NO_DISPONIBLE));
        return new Acceso(platform,companies,selected,allowed);
    }
}
