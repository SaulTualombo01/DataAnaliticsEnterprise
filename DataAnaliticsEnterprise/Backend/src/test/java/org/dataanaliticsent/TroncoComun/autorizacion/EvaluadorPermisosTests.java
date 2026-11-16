package org.dataanaliticsent.TroncoComun.autorizacion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.TroncoComun.autorizacion.ProveedorRecursosAutorizacion.Jerarquia;
import org.dataanaliticsent.TroncoComun.autorizacion.internal.dominio.EvaluadorPermisos;
import org.dataanaliticsent.TroncoComun.autorizacion.internal.dominio.RepositorioPermisos;
import org.dataanaliticsent.TroncoComun.autorizacion.internal.infraestructura.RegistroProveedoresRecursos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.*;

class EvaluadorPermisosTests {
    private final UUID actor = UUID.randomUUID(), project = UUID.randomUUID();
    private Datos datos;
    private EvaluadorPermisos api;
    private final Map<SolicitudAcceso.Recurso,Jerarquia> recursos = new HashMap<>();

    @BeforeEach
    void preparar() {
        datos = new Datos();
        datos.permisos.put("m1.assets.read", new RepositorioPermisos.Permiso(SolicitudAcceso.Ambito.PROJECT, true));
        datos.permisos.put("tc.users.read", new RepositorioPermisos.Permiso(SolicitudAcceso.Ambito.PLATFORM, true));
        datos.permisos.put("tc.self.profile.read", new RepositorioPermisos.Permiso(SolicitudAcceso.Ambito.SELF, true));
        api = new EvaluadorPermisos(datos, r -> Optional.ofNullable(recursos.get(r)));
    }

    @Test void identidadAusenteEsDenegada() {
        assertEquals(Motivo.SIN_IDENTIDAD, api.evaluar(null, solicitud()).motivo());
    }
    @Test void organizacionNoSeConfundeConProyectoOPlataforma() {
        assertThrows(IllegalArgumentException.class,()->SolicitudAcceso.organizacion("tc.company.projects.create",null));
        assertThrows(IllegalArgumentException.class,()->new SolicitudAcceso("tc.users.read",SolicitudAcceso.Ambito.PLATFORM,null,null,null,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->new SolicitudAcceso("m1.assets.read",SolicitudAcceso.Ambito.PROJECT,project,null,null,UUID.randomUUID()));
    }
    @Test void adaptadorSinSoporteOrganizacionalDeniegaPorDefecto() {
        datos.permisos.put("tc.company.projects.create",new RepositorioPermisos.Permiso(SolicitudAcceso.Ambito.ORGANIZATION,true));
        assertFalse(api.evaluar(actor,SolicitudAcceso.organizacion("tc.company.projects.create",UUID.randomUUID())).permitido());
        assertEquals(Motivo.AMBITO_INCORRECTO,api.evaluar(actor,SolicitudAcceso.plataforma("tc.company.projects.create")).motivo());
    }
    @Test void cuentaInactivaEsDenegada() {
        datos.activa = false;
        assertEquals(Motivo.CUENTA_NO_ACTIVA, api.evaluar(actor, solicitud()).motivo());
    }
    @Test void permisoDesconocidoEsDenegado() {
        assertEquals(Motivo.PERMISO_DESCONOCIDO, api.evaluar(actor, SolicitudAcceso.proyecto("m1.inexistente.read",project)).motivo());
    }
    @Test void permisoOpcionalDeshabilitadoEsDenegado() {
        datos.permisos.put("m1.assets.read",new RepositorioPermisos.Permiso(SolicitudAcceso.Ambito.PROJECT,false));
        assertEquals(Motivo.PERMISO_NO_DISPONIBLE,api.evaluar(actor,solicitud()).motivo());
    }
    @Test void proyectoNoSeConvierteEnPlataforma() {
        assertEquals(Motivo.AMBITO_INCORRECTO,api.evaluar(actor,SolicitudAcceso.plataforma("m1.assets.read")).motivo());
    }
    @Test void selfSoloAutorizaAlTitularSinExigirRoles() {
        assertTrue(api.evaluar(actor,SolicitudAcceso.propia("tc.self.profile.read",actor)).permitido());
        assertEquals(Motivo.TITULAR_INCORRECTO,api.evaluar(actor,SolicitudAcceso.propia("tc.self.profile.read",UUID.randomUUID())).motivo());
    }
    @Test void plataformaNecesitaConcesionExplicita() {
        assertFalse(api.evaluar(actor,SolicitudAcceso.plataforma("tc.users.read")).permitido());
        datos.plataforma = true;
        assertTrue(api.evaluar(actor,SolicitudAcceso.plataforma("tc.users.read")).permitido());
    }
    @Test void contextoNoVigenteEsDenegado() {
        datos.contexto = false;
        assertEquals(Motivo.CONTEXTO_NO_AUTORIZADO,api.evaluar(actor,solicitud()).motivo());
    }
    @Test void sinAsignacionNoSeConcedeAcceso() {
        assertEquals(Motivo.PERMISO_NO_CONCEDIDO,api.evaluar(actor,solicitud()).motivo());
    }
    @Test void alcanceProyectoNoEvitaValidarPertenenciaDelRecurso() {
        datos.alcances=List.of(new AlcanceAutorizado(project,AlcanceAutorizado.Tipo.PROJECT,null));
        var recurso = new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.ASSET,UUID.randomUUID());
        var solicitud=SolicitudAcceso.recurso("m1.assets.read",project,recurso.tipo(),recurso.id());
        assertEquals(Motivo.RECURSO_NO_VALIDADO,api.evaluar(actor,solicitud).motivo());
        recursos.put(recurso,new Jerarquia(UUID.randomUUID(),UUID.randomUUID(),recurso.id()));
        assertFalse(api.evaluar(actor,solicitud).permitido());
        recursos.put(recurso,new Jerarquia(project,UUID.randomUUID(),UUID.randomUUID()));
        assertFalse(api.evaluar(actor,solicitud).permitido());
    }
    @Test void alcanceDeFuenteCubreSuActivoPeroNoOtroOrigen() {
        UUID source=UUID.randomUUID(),asset=UUID.randomUUID();
        datos.alcances=List.of(new AlcanceAutorizado(project,AlcanceAutorizado.Tipo.SOURCE,source));
        var solicitud=SolicitudAcceso.recurso("m1.assets.read",project,SolicitudAcceso.TipoRecurso.ASSET,asset);
        recursos.put(solicitud.recurso(),new Jerarquia(project,source,asset));
        assertTrue(api.evaluar(actor,solicitud).permitido());
        recursos.put(solicitud.recurso(),new Jerarquia(project,UUID.randomUUID(),asset));
        assertFalse(api.evaluar(actor,solicitud).permitido());
    }
    @Test void alcanceActivoNoAsciendeAFuenteNiHermanos() {
        UUID source=UUID.randomUUID(),asset=UUID.randomUUID(),sibling=UUID.randomUUID();
        datos.alcances=List.of(new AlcanceAutorizado(project,AlcanceAutorizado.Tipo.ASSET,asset));
        var exacto=SolicitudAcceso.recurso("m1.assets.read",project,SolicitudAcceso.TipoRecurso.ASSET,asset);
        var padre=SolicitudAcceso.recurso("m1.assets.read",project,SolicitudAcceso.TipoRecurso.SOURCE,source);
        var hermano=SolicitudAcceso.recurso("m1.assets.read",project,SolicitudAcceso.TipoRecurso.ASSET,sibling);
        recursos.put(exacto.recurso(),new Jerarquia(project,source,asset));
        recursos.put(padre.recurso(),new Jerarquia(project,source,null));
        recursos.put(hermano.recurso(),new Jerarquia(project,source,sibling));
        assertTrue(api.evaluar(actor,exacto).permitido());
        assertFalse(api.evaluar(actor,padre).permitido());
        assertFalse(api.evaluar(actor,hermano).permitido());
        assertFalse(api.evaluar(actor,solicitud()).permitido());
    }
    @Test void listadosRecibenSoloAlcancesDelContextoYRecursosResueltos() {
        UUID source=UUID.randomUUID();
        var valido=new AlcanceAutorizado(project,AlcanceAutorizado.Tipo.SOURCE,source);
        datos.alcances=List.of(valido,valido,new AlcanceAutorizado(project,AlcanceAutorizado.Tipo.ASSET,UUID.randomUUID()),
                new AlcanceAutorizado(UUID.randomUUID(),AlcanceAutorizado.Tipo.PROJECT,null));
        recursos.put(new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.SOURCE,source),new Jerarquia(project,source,null));
        assertEquals(List.of(valido),api.alcancesPermitidos(actor,"m1.assets.read",project));
    }
    @Test void exigirInterrumpeUnaOperacionDenegada() {
        var error=assertThrows(AccesoDenegadoException.class,()->api.exigir(actor,solicitud()));
        assertEquals(Motivo.PERMISO_NO_CONCEDIDO,error.motivo());
    }
    @Test void erroresDeDependenciaNoSeTransformanEnPermiso() {
        datos.fallar=true;
        assertThrows(IllegalStateException.class,()->api.evaluar(actor,solicitud()));
    }
    @Test void solicitudesMalformadasNoCreanContextosAmplios() {
        assertThrows(IllegalArgumentException.class,()->SolicitudAcceso.plataforma("m1.*"));
        assertThrows(IllegalArgumentException.class,()->new SolicitudAcceso("m1.assets.read",SolicitudAcceso.Ambito.PLATFORM,project,null,null));
    }
    @Test void registroSinProveedorNoResuelveRecursosYDuplicadosFallanCerrados() {
        var factory=new DefaultListableBeanFactory();
        var registro=new RegistroProveedoresRecursos(factory.getBeanProvider(ProveedorRecursosAutorizacion.class));
        var ref=new SolicitudAcceso.Recurso(SolicitudAcceso.TipoRecurso.SOURCE,UUID.randomUUID());
        assertTrue(registro.resolver(ref).isEmpty());
        ProveedorRecursosAutorizacion proveedor=new ProveedorRecursosAutorizacion() {
            public boolean soporta(SolicitudAcceso.TipoRecurso tipo) { return true; }
            public Optional<Jerarquia> resolver(SolicitudAcceso.Recurso recurso) { return Optional.empty(); }
        };
        factory.registerSingleton("primero",proveedor); factory.registerSingleton("segundo",proveedor);
        assertThrows(IllegalStateException.class,()->registro.resolver(ref));
    }

    private SolicitudAcceso solicitud() { return SolicitudAcceso.proyecto("m1.assets.read",project); }
    private static class Datos implements RepositorioPermisos {
        boolean activa=true,contexto=true,plataforma=false,fallar=false;
        final Map<String,Permiso> permisos=new HashMap<>();
        List<AlcanceAutorizado> alcances=List.of();
        public boolean cuentaActiva(UUID user) { if(fallar)throw new IllegalStateException("Dependencia no disponible"); return activa; }
        public Optional<Permiso> buscarPermiso(String code) { return Optional.ofNullable(permisos.get(code)); }
        public boolean contextoVigente(UUID user,UUID project) { return contexto; }
        public boolean permisoPlataforma(UUID user,String code) { return plataforma; }
        public List<AlcanceAutorizado> alcances(UUID user,String code,UUID project) { return alcances; }
    }
}
