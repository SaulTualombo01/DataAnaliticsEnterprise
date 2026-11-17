package org.dataanaliticsent.TroncoComun.errores.internal;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.errores.CorrelacionOperacionApi;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CorrelacionOperacionActual implements CorrelacionOperacionApi {
    public UUID actual() {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
            return UUID.fromString(ProblemasHttp.requestId(attrs.getRequest()));
        return UUID.randomUUID();
    }
}
