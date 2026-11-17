package org.dataanaliticsent.TroncoComun.contexto;
import java.util.List;
import java.util.UUID;
public interface AdministracionActualApi {
    record Empresa(UUID id,String name,String status) { }
    record Acceso(List<String> platformPermissions,List<Empresa> companies,Empresa selectedOrganization,List<String> organizationPermissions) {
        public Acceso { platformPermissions=List.copyOf(platformPermissions); companies=List.copyOf(companies); organizationPermissions=List.copyOf(organizationPermissions); }
    }
    Acceso consultar(UUID organizacion);
}
