package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.CuentaLocal;
import org.springframework.security.core.userdetails.User;

public class PrincipalLocal extends User {
    private static final long serialVersionUID=1L;
    private final UUID usuarioId;
    private final long sessionVersion;
    private final Instant autenticadoEn;
    public PrincipalLocal(CuentaLocal cuenta,Instant ahora) {
        super(cuenta.email(),cuenta.hash(),cuenta.activa(),true,true,true,List.of());
        usuarioId=cuenta.id(); sessionVersion=cuenta.sessionVersion(); autenticadoEn=ahora;
    }
    public UUID usuarioId() { return usuarioId; }
    public long sessionVersion() { return sessionVersion; }
    public Instant autenticadoEn() { return autenticadoEn; }
    @Override public String toString() { return "PrincipalLocal[id="+usuarioId+"]"; }
}
