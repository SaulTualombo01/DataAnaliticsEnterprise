package org.dataanaliticsent.TroncoComun.secretos.internal.aplicacion;
import java.util.*;
import java.util.function.*;
import org.dataanaliticsent.TroncoComun.autorizacion.*;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
import org.dataanaliticsent.TroncoComun.errores.CorrelacionOperacionApi;
import org.dataanaliticsent.TroncoComun.secretos.*;
import org.dataanaliticsent.TroncoComun.secretos.SecretosApi.*;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.*;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.RepositorioSecretos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static org.dataanaliticsent.TroncoComun.secretos.SecretosException.Codigo.*;
@Service
public class ServicioSecretos implements SecretosApi {
    private final IdentidadActualApi identidad;private final AutorizacionApi permisos;
    private final List<ProveedorFuentesSecretos> fuentes;private final RepositorioSecretos repo;private final CifradorSecretos crypto;
    private final boolean localInseguro;private final CorrelacionOperacionApi correlacion;
    public ServicioSecretos(IdentidadActualApi identidad,AutorizacionApi permisos,List<ProveedorFuentesSecretos> fuentes,
        RepositorioSecretos repo,CifradorSecretos crypto,CorrelacionOperacionApi correlacion,@Value("${tic.secrets.allow-local-insecure:false}") boolean localInseguro) {
        this.identidad=identidad;this.permisos=permisos;this.fuentes=List.copyOf(fuentes);this.repo=repo;this.crypto=crypto;this.localInseguro=localInseguro;this.correlacion=correlacion;
    }
    private static SecretosException error(SecretosException.Codigo code){return new SecretosException(code);}
    private static UUID id(UUID id){if(id==null)throw error(INVALIDO);return id;}
    private static void motivo(String reason){if(reason==null||reason.isBlank()||reason.length()>500)throw error(INVALIDO);}
    private <T>T datos(Supplier<T> task){
        try{return task.get();}catch(DataIntegrityViolationException e){throw error(CONFLICTO);}catch(DataAccessException e){throw error(NO_DISPONIBLE);}
    }
    private ProveedorFuentesSecretos.Fuente fuente(UUID actor,UUID p,UUID s,String permission,boolean conectar){
        permisos.exigir(actor,SolicitudAcceso.recurso(permission,id(p),SolicitudAcceso.TipoRecurso.SOURCE,id(s)));
        var found=fuentes.stream().map(f->f.consultar(p,s)).flatMap(Optional::stream).toList();
        if(found.size()!=1)throw error(NO_DISPONIBLE);
        var f=found.getFirst();
        if(!p.equals(f.projectId())||!s.equals(f.sourceId())||(conectar&&(!f.habilitada()||f.retirada())))throw error(NO_DISPONIBLE);
        return f;
    }
    private void transporte(DestinoPostgres d){
        if(d==null)throw error(INVALIDO);
        if(d.sslmode().equals("verify-full"))return;
        if(localInseguro&&d.sslmode().equals("disable")&&Set.of("localhost","127.0.0.1").contains(d.host()))return;
        throw error(INVALIDO);
    }
    private Entrada entrada(UUID p,UUID s,UUID ref,boolean lock){
        return datos(()->repo.buscar(id(p),id(s),id(ref),lock).orElseThrow(()->error(NO_DISPONIBLE)));
    }
    private static void revision(Entrada e,long expected){
        if(expected<1)throw error(INVALIDO);
        if(e.referencia().revision()!=expected)throw error(CONFLICTO);
    }
    private static CifradorSecretos.Vinculo vinculo(Referencia r,DestinoPostgres d,long rev){
        return new CifradorSecretos.Vinculo(r.id(),r.projectId(),r.sourceId(),rev,d);
    }
    private void auditar(UUID actor,Referencia ref,String action){
        datos(()->{repo.auditar(actor,ref,action,correlacion.actual());return null;});
    }
    @Override @Transactional
    public Referencia registrar(UUID p,UUID s,DestinoPostgres d,CredencialPostgres material,String reason){
        if(material==null)throw error(INVALIDO);
        try(material){
            UUID actor=identidad.usuarioId();var f=fuente(actor,p,s,"m1.sources.credentials.write",false);motivo(reason);transporte(d);
            if(f.retirada()||!d.equals(f.destino()))throw error(NO_DISPONIBLE);
            UUID ref=UUID.randomUUID();var c=crypto.cifrar(new CifradorSecretos.Vinculo(ref,p,s,1,d),material);
            var current=fuente(actor,p,s,"m1.sources.credentials.write",false);
            if(current.retirada()||!current.destino().equals(d))throw error(NO_DISPONIBLE);
            var r=datos(()->repo.insertar(p,s,ref,d,c));auditar(actor,r,"tc.secrets.register");return r;
        }
    }
    @Override @Transactional(readOnly=true)
    public Referencia consultar(UUID p,UUID s,UUID ref){
        fuente(identidad.usuarioId(),p,s,"m1.sources.read",false);return entrada(p,s,ref,false).referencia();
    }
    @Override @Transactional
    public Referencia reemplazar(UUID p,UUID s,UUID ref,long expected,DestinoPostgres d,CredencialPostgres material,String reason){
        if(material==null)throw error(INVALIDO);
        try(material){
            UUID actor=identidad.usuarioId();var f=fuente(actor,p,s,"m1.sources.credentials.write",false);motivo(reason);transporte(d);
            var e=entrada(p,s,ref,true);revision(e,expected);
            if(e.referencia().status()==Estado.RETIRED)throw error(NO_DISPONIBLE);
            if(f.retirada()||!f.destino().equals(d))throw error(NO_DISPONIBLE);
            var c=crypto.cifrar(vinculo(e.referencia(),d,expected+1),material);
            var current=fuente(actor,p,s,"m1.sources.credentials.write",false);
            if(current.retirada()||!current.destino().equals(d))throw error(NO_DISPONIBLE);
            var r=datos(()->repo.actualizar(e,Estado.ACTIVE,d,c));auditar(actor,r,"tc.secrets.replace");return r;
        }
    }
    private Referencia cambiar(UUID p,UUID s,UUID ref,long expected,String reason,Estado state,boolean recifrar){
        UUID actor=identidad.usuarioId();fuente(actor,p,s,"m1.sources.credentials.write",false);motivo(reason);
        var e=entrada(p,s,ref,true);revision(e,expected);
        if(e.referencia().status()==Estado.RETIRED)throw error(NO_DISPONIBLE);
        Cifrado next=null;
        if(state!=Estado.RETIRED){
            try(var material=crypto.abrir(vinculo(e.referencia(),e.destino(),expected),e.cifrado())){
                next=crypto.cifrar(vinculo(e.referencia(),e.destino(),expected+1),material);
            }
        }
        var cifrado=next;fuente(actor,p,s,"m1.sources.credentials.write",false);
        var r=datos(()->repo.actualizar(e,state,e.destino(),cifrado));
        auditar(actor,r,recifrar?"tc.secrets.reencrypt":state==Estado.RETIRED?"tc.secrets.retire":"tc.secrets.disable");return r;
    }
    @Override @Transactional
    public Referencia deshabilitar(UUID p,UUID s,UUID ref,long expected,String reason){return cambiar(p,s,ref,expected,reason,Estado.DISABLED,false);}
    @Override @Transactional
    public Referencia retirar(UUID p,UUID s,UUID ref,long expected,String reason){return cambiar(p,s,ref,expected,reason,Estado.RETIRED,false);}
    @Override @Transactional
    public Referencia recifrar(UUID p,UUID s,UUID ref,long expected,String reason){
        fuente(identidad.usuarioId(),p,s,"m1.sources.credentials.write",false);
        return cambiar(p,s,ref,expected,reason,entrada(p,s,ref,true).referencia().status(),true);
    }
    private void comprobarUso(UUID actor,Entrada e,DestinoPostgres d,String permission){
        var r=e.referencia();var f=fuente(actor,r.projectId(),r.sourceId(),permission,true);
        var now=entrada(r.projectId(),r.sourceId(),r.id(),false).referencia();
        if(now.status()!=Estado.ACTIVE||now.revision()!=r.revision()||!e.destino().equals(d)||!f.destino().equals(d))throw error(NO_DISPONIBLE);
        transporte(d);
    }
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public <T>T usarPostgres(UUID p,UUID s,UUID ref,DestinoPostgres d,Uso uso,Function<CredencialPostgres,T> conector){
        if(uso==null||conector==null)throw error(INVALIDO);
        String permission=switch(uso){case PROBAR_CONEXION->"m1.sources.connection.test";case DESCUBRIR_METADATOS->"m1.discovery.execute";};
        UUID actor=identidad.usuarioId();fuente(actor,p,s,permission,true);
        var e=entrada(p,s,ref,false);comprobarUso(actor,e,d,permission);
        try(var material=crypto.abrir(vinculo(e.referencia(),e.destino(),e.referencia().revision()),e.cifrado())){
            comprobarUso(actor,e,d,permission);
            auditar(actor,e.referencia(),"tc.secrets.resolve."+uso.name().toLowerCase(Locale.ROOT));
            T result;
            try{result=conector.apply(material);}catch(RuntimeException failure){throw error(CONECTOR_FALLIDO);}
            comprobarUso(actor,e,d,permission);return result;
        }
    }
}
