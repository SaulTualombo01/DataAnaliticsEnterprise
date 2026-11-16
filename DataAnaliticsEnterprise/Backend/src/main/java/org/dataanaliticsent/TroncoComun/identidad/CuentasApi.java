package org.dataanaliticsent.TroncoComun.identidad;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Administración de cuentas. La identidad de administración procede del servidor. */
public interface CuentasApi {
    record Cuenta(UUID id,String email,String displayName,String status,Instant createdAt) { }
    record Invitacion(UUID id,UUID organizationId,String email,String displayName,UUID invitedBy,
                      String status,Instant createdAt,Instant expiresAt,UUID acceptedUserId) { }
    record EntregaInvitacion(Invitacion invitation,String activationToken) {
        @Override public String toString() { return "EntregaInvitacion[protegido]"; }
    }
    record Pagina<T>(List<T> data,int page,int pageSize,boolean hasNext) {
        public Pagina { data=List.copyOf(data); }
    }
    EntregaInvitacion invitar(UUID organizacion,String email,String nombre,String motivo);
    Pagina<Invitacion> listarInvitaciones(UUID organizacion,int pagina,int tamano);
    void revocarInvitacion(UUID organizacion,UUID invitacion,String motivo);
    Cuenta activar(String token,String nuevaClave);
    Pagina<Cuenta> listarUsuarios(int pagina,int tamano);
    Pagina<Cuenta> listarEmpleados(UUID organizacion,int pagina,int tamano);
    void desactivarUsuario(UUID usuario,String motivo);
    void desactivarEmpleado(UUID organizacion,UUID usuario,String motivo);
}
