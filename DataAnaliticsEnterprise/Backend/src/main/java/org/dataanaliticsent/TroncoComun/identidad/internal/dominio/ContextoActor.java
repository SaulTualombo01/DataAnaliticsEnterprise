package org.dataanaliticsent.TroncoComun.identidad.internal.dominio;

import java.util.UUID;

public interface ContextoActor {
    UUID exigirUsuario();
    UUID requestId();
}
