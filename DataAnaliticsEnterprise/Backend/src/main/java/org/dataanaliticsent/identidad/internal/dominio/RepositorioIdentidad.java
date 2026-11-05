package org.dataanaliticsent.identidad.internal.dominio;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface RepositorioIdentidad {
    Optional<CuentaLocal> porCorreo(String normalizedEmail);
    Optional<CuentaLocal> porId(UUID id);
    boolean cambiarClave(UUID id,long version,String hash);
    boolean revocar(UUID id);
    OptionalLong versionActiva(UUID id);
}
