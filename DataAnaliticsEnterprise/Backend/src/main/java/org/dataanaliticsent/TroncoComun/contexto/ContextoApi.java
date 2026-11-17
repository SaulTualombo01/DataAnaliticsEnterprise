package org.dataanaliticsent.TroncoComun.contexto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Administración contextual. El actor procede exclusivamente de la identidad verificada. */
public interface ContextoApi {
    record Organizacion(UUID id,String name,String status,Instant createdAt) { }
    record Proyecto(UUID id,UUID organizationId,String name,String status,Instant createdAt) { }
    record AdministradorEmpresa(UUID id,UUID userId,UUID organizationId,UUID assignedBy,String status,Instant assignedAt,Instant revokedAt) { }
    record Pagina<T>(List<T> data,int page,int pageSize,boolean hasNext) {
        public Pagina { data=List.copyOf(data); }
    }
    Organizacion crearOrganizacion(String nombre,String motivo);
    Pagina<Organizacion> listarOrganizaciones(int pagina,int tamano);
    Organizacion consultarOrganizacion(UUID organizacionId);
    Proyecto crearProyecto(UUID organizacionId,String nombre,String motivo);
    Pagina<Proyecto> listarProyectos(UUID organizacionId,int pagina,int tamano);
    AdministradorEmpresa asignarAdministrador(UUID organizacionId,UUID usuarioId,String motivo);
    Pagina<AdministradorEmpresa> listarAdministradores(UUID organizacionId,int pagina,int tamano);
    void revocarAdministrador(UUID organizacionId,UUID asignacionId,String motivo);
}
