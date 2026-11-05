package org.dataanaliticsent.autorizacion.internal.infraestructura;

import java.util.Optional;
import org.dataanaliticsent.autorizacion.ProveedorRecursosAutorizacion;
import org.dataanaliticsent.autorizacion.ProveedorRecursosAutorizacion.Jerarquia;
import org.dataanaliticsent.autorizacion.SolicitudAcceso.Recurso;
import org.dataanaliticsent.autorizacion.internal.dominio.ResolvedorRecursos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class RegistroProveedoresRecursos implements ResolvedorRecursos {
    private final ObjectProvider<ProveedorRecursosAutorizacion> proveedores;
    public RegistroProveedoresRecursos(ObjectProvider<ProveedorRecursosAutorizacion> proveedores) { this.proveedores = proveedores; }
    @Override
    public Optional<Jerarquia> resolver(Recurso recurso) {
        var candidatos = proveedores.orderedStream().filter(p -> p.soporta(recurso.tipo())).toList();
        if (candidatos.isEmpty()) return Optional.empty();
        if (candidatos.size() != 1) throw new IllegalStateException("Más de un proveedor para el mismo tipo de recurso");
        return candidatos.getFirst().resolver(recurso);
    }
}
