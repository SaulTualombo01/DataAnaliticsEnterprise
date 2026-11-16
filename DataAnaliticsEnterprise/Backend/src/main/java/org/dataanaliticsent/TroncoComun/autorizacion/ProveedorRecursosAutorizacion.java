package org.dataanaliticsent.TroncoComun.autorizacion;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SPI del propietario del recurso. Debe consultar datos del servidor, nunca jerarquías del cliente. */
public interface ProveedorRecursosAutorizacion {
    boolean soporta(SolicitudAcceso.TipoRecurso tipo);
    Optional<Jerarquia> resolver(SolicitudAcceso.Recurso recurso);

    record Jerarquia(UUID proyectoId, UUID fuenteId, UUID activoId) {
        public Jerarquia { Objects.requireNonNull(proyectoId); Objects.requireNonNull(fuenteId); }
        public boolean corresponde(SolicitudAcceso.Recurso recurso) {
            return switch (recurso.tipo()) {
                case SOURCE -> activoId == null && fuenteId.equals(recurso.id());
                case ASSET -> activoId != null && activoId.equals(recurso.id());
            };
        }
    }
}
