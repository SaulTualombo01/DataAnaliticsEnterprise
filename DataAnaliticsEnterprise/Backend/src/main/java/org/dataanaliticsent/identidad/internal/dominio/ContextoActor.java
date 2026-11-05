package org.dataanaliticsent.identidad.internal.dominio;

import java.util.UUID;

public interface ContextoActor {
    UUID exigirUsuario();
    UUID requestId();
}
