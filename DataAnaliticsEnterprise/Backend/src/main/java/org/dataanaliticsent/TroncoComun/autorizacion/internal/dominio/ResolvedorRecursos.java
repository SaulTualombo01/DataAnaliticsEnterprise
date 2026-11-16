package org.dataanaliticsent.TroncoComun.autorizacion.internal.dominio;

import java.util.Optional;
import org.dataanaliticsent.TroncoComun.autorizacion.ProveedorRecursosAutorizacion.Jerarquia;
import org.dataanaliticsent.TroncoComun.autorizacion.SolicitudAcceso.Recurso;

public interface ResolvedorRecursos {
    Optional<Jerarquia> resolver(Recurso recurso);
}
