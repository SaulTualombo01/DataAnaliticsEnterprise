package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.util.UUID;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadNoVerificadaException;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.ContextoActor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ContextoSeguridad implements ContextoActor {
    public UUID exigirUsuario() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof PrincipalLocal principal) return principal.usuarioId();
        throw new IdentidadNoVerificadaException();
    }
    public UUID requestId() {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return UUID.fromString(ProblemasHttp.requestId(attributes.getRequest()));
        }
        return UUID.randomUUID();
    }
}
