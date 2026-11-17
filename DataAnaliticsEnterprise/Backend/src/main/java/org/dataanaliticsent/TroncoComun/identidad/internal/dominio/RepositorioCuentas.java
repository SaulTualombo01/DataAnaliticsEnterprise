package org.dataanaliticsent.TroncoComun.identidad.internal.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.identidad.CuentasApi.*;

public interface RepositorioCuentas {
    record Pendiente(Invitacion invitation,String status) { }
    DetalleCuenta detalle(UUID user);
    DetalleCuenta editarNombre(UUID user,String name);
    void bloquearAdministracion();
    boolean bloquearEmpresaActiva(UUID org);
    Cuenta bloquearCuenta(UUID user);
    boolean existeCorreo(String email);
    List<Invitacion> pendientes(UUID org,String email);
    Invitacion crearInvitacion(UUID id,UUID org,String email,String name,UUID actor,String hash,Instant at,Instant expires);
    Optional<Pendiente> porToken(String hash);
    Optional<Invitacion> invitacion(UUID org,UUID id);
    void revocarInvitacion(UUID id,Instant at);
    Cuenta crearCuenta(UUID id,String email,String name,String hash,Instant at);
    void incorporar(UUID user,UUID org,UUID actor);
    void aceptar(UUID invitation,UUID user,Instant at);
    List<Invitacion> invitaciones(UUID org,int limit,long offset);
    List<Cuenta> cuentas(UUID org,int limit,long offset);
    boolean pertenece(UUID user,UUID org);
    boolean otraEmpresaActiva(UUID user,UUID org);
    boolean administrador(UUID user);
    boolean administradorAccesible(UUID user);
    long administradoresAccesibles();
    void desactivar(UUID user);
    void auditar(UUID actor,UUID org,UUID recurso,String accion,String motivo,UUID sujeto);
}
