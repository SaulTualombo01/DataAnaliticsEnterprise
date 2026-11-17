package org.dataanaliticsent.TroncoComun.contexto.internal.dominio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.contexto.AdministracionActualApi.Empresa;
public interface RepositorioAdministracionActual {
    List<String> permisos(String scope);
    List<Empresa> empresasDelegadas(UUID user);
    Optional<Empresa> empresa(UUID id);
}
