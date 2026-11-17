package org.dataanaliticsent.TroncoComun.identidad.internal.dominio;

public interface HashClaves {
    String codificar(String clave);
    boolean coincide(String clave,String hash);
}
