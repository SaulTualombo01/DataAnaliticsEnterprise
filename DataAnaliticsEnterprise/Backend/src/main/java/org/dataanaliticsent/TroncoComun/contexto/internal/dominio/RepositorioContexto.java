package org.dataanaliticsent.TroncoComun.contexto.internal.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi.*;

public interface RepositorioContexto {
    record Membresia(UUID id,String status) { }
    void bloquearEscritura(UUID actor);
    void bloquearOrganizacion(UUID organizacion);
    Optional<Organizacion> organizacion(UUID id);
    Organizacion crearOrganizacion(UUID id,String nombre);
    List<Organizacion> organizaciones(int limite,long offset);
    Proyecto crearProyecto(UUID id,UUID organizacion,String nombre);
    List<Proyecto> proyectos(UUID organizacion,int limite,long offset);
    String bloquearUsuario(UUID usuario);
    boolean otraEmpresaActiva(UUID usuario,UUID organizacion);
    Optional<Membresia> membresia(UUID usuario,UUID organizacion);
    UUID crearMembresia(UUID usuario,UUID organizacion,UUID actor);
    AdministradorEmpresa asignarAdministrador(UUID usuario,UUID organizacion,UUID membresia,UUID actor);
    List<AdministradorEmpresa> administradores(UUID organizacion,int limite,long offset);
    Optional<AdministradorEmpresa> bloquearAdministrador(UUID organizacion,UUID asignacion);
    void revocarAdministrador(UUID asignacion);
    void auditar(UUID actor,UUID organizacion,UUID proyecto,String tipo,UUID recurso,String accion,String motivo,UUID sujeto);
}
