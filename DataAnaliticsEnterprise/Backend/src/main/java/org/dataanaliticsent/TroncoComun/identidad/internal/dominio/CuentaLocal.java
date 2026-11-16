package org.dataanaliticsent.TroncoComun.identidad.internal.dominio;

import java.util.UUID;

public record CuentaLocal(UUID id,String email,String nombre,boolean activa,String hash,long sessionVersion) {
    @Override public String toString() { return "CuentaLocal[id="+id+"]"; }
}
