package org.dataanaliticsent.identidad.internal.dominio;

import java.util.UUID;

public interface AuditoriaIdentidad {
    void registrar(UUID actor,UUID recurso,String accion,String resultado,UUID requestId);
}
