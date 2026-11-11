package org.dataanaliticsent.identidad.internal.aplicacion;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.dataanaliticsent.identidad.IdentidadNoVerificadaException;
import org.dataanaliticsent.identidad.internal.dominio.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioIdentidad implements IdentidadActualApi {
    private final ContextoActor actor;
    private final RepositorioIdentidad datos;
    private final AutorizacionApi permisos;
    private final HashClaves claves;
    private final AuditoriaIdentidad auditoria;
    public ServicioIdentidad(ContextoActor actor,RepositorioIdentidad datos,AutorizacionApi permisos,HashClaves claves,AuditoriaIdentidad auditoria) {
        this.actor=actor; this.datos=datos; this.permisos=permisos; this.claves=claves; this.auditoria=auditoria;
    }
    public UUID usuarioId() { return actor.exigirUsuario(); }
    public Perfil perfil() {
        UUID id=usuarioId(); permisos.exigir(id,SolicitudAcceso.propia("tc.self.profile.read",id));
        CuentaLocal cuenta=datos.porId(id).filter(CuentaLocal::activa).orElseThrow(IdentidadNoVerificadaException::new);
        return new Perfil(cuenta.id(),cuenta.email(),cuenta.nombre());
    }
    @Transactional
    public void cambiarClave(String actual,String nueva) {
        UUID id=usuarioId(); permisos.exigir(id,SolicitudAcceso.propia("tc.self.credentials.change",id));
        PoliticaClave.validarNueva(nueva);
        CuentaLocal cuenta=datos.porId(id).filter(CuentaLocal::activa).orElseThrow(IdentidadNoVerificadaException::new);
        if(actual==null || actual.getBytes(StandardCharsets.UTF_8).length>72 || !claves.coincide(actual,cuenta.hash())) throw new IllegalArgumentException("Contraseña actual inválida");
        if(!datos.cambiarClave(id,cuenta.sessionVersion(),claves.codificar(nueva))) throw new IllegalStateException("Cambio concurrente de credenciales");
        auditoria.registrar(id,id,"tc.self.credentials.change","SUCCESS",actor.requestId());
    }
    @Transactional
    public void revocarSesiones(UUID objetivo) {
        UUID id=usuarioId(); permisos.exigir(id,SolicitudAcceso.plataforma("tc.sessions.revoke"));
        if(!datos.revocar(objetivo)) throw new IllegalArgumentException("Cuenta local no disponible");
        auditoria.registrar(id,objetivo,"tc.sessions.revoke","SUCCESS",actor.requestId());
    }
}
