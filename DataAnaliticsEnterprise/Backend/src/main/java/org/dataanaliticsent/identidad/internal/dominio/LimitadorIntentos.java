package org.dataanaliticsent.identidad.internal.dominio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Ventana fija de la instancia actual, con memoria acotada. Las claves no contienen contraseñas. */
public final class LimitadorIntentos {
    private record Ventana(Instant inicio,int intentos) { }
    private final Map<String,Ventana> datos=new HashMap<>();
    private final Clock clock;
    private final Duration duracion;
    private final int limiteCuenta,limiteIp,maximoClaves;
    public LimitadorIntentos(Clock clock,Duration duracion,int limiteCuenta,int limiteIp,int maximoClaves) {
        if(duracion.isZero()||duracion.isNegative()||limiteCuenta<1||limiteIp<1||maximoClaves<2) throw new IllegalArgumentException("Límites inválidos");
        this.clock=clock; this.duracion=duracion; this.limiteCuenta=limiteCuenta; this.limiteIp=limiteIp; this.maximoClaves=maximoClaves;
    }
    public synchronized boolean permitir(String cuentaHash,String ipHash) {
        Instant ahora=clock.instant();
        return consumir("ip:"+ipHash,limiteIp,ahora) && consumir("cuenta:"+cuentaHash,limiteCuenta,ahora);
    }
    private boolean consumir(String key,int limite,Instant ahora) {
        Ventana anterior=datos.get(key);
        if(anterior!=null && !anterior.inicio().plus(duracion).isAfter(ahora)) { datos.remove(key); anterior=null; }
        if(anterior==null) {
            if(datos.size()>=maximoClaves) datos.entrySet().removeIf(e -> !e.getValue().inicio().plus(duracion).isAfter(ahora));
            if(datos.size()>=maximoClaves) return false;
            datos.put(key,new Ventana(ahora,1)); return true;
        }
        if(anterior.intentos()>=limite) return false;
        datos.put(key,new Ventana(anterior.inicio(),anterior.intentos()+1)); return true;
    }
    public synchronized void exito(String cuentaHash) { datos.remove("cuenta:"+cuentaHash); }
}
