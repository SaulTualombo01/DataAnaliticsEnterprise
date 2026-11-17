package org.dataanaliticsent.autorizacion.internal.dominio;

import java.util.Optional;
import org.dataanaliticsent.autorizacion.ProveedorRecursosAutorizacion.Jerarquia;
import org.dataanaliticsent.autorizacion.SolicitudAcceso.Recurso;

public interface ResolvedorRecursos {
    Optional<Jerarquia> resolver(Recurso recurso);
}
