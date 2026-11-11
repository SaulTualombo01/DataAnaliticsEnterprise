package org.dataanaliticsent.identidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.dataanaliticsent.identidad.internal.dominio.LimitadorIntentos;
import org.dataanaliticsent.identidad.internal.dominio.PoliticaClave;
import org.dataanaliticsent.identidad.internal.presentacion.AutenticacionController.CambioClave;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoliticasIdentidadTests {
    @Test void validaLongitudSinTruncarUnicodeNiExigirComposicionArbitraria() {
        assertThrows(IllegalArgumentException.class,()->PoliticaClave.validarNueva("corta"));
        assertThrows(IllegalArgumentException.class,()->PoliticaClave.validarNueva("😀".repeat(19)));
        assertThrows(IllegalArgumentException.class,()->PoliticaClave.validarNueva("a".repeat(73)));
        assertDoesNotThrow(()->PoliticaClave.validarNueva("una frase larga para entrar"));
        assertDoesNotThrow(()->PoliticaClave.validarNueva("😀".repeat(18)));
    }
    @Test void limitePorCuentaPersisteHastaFinalizarLaVentana() {
        Reloj reloj=new Reloj();
        var limite=new LimitadorIntentos(reloj,Duration.ofMinutes(15),2,100,20);
        assertTrue(limite.permitir("cuenta","ip")); assertTrue(limite.permitir("cuenta","ip"));
        assertFalse(limite.permitir("cuenta","ip2"));
        reloj.now=reloj.now.plus(Duration.ofMinutes(15));
        assertTrue(limite.permitir("cuenta","ip2"));
    }
    @Test void unExitoReiniciaCuentaPeroNoElLimitePorIp() {
        var limite=new LimitadorIntentos(new Reloj(),Duration.ofMinutes(15),1,2,20);
        assertTrue(limite.permitir("a","ip")); limite.exito("a");
        assertTrue(limite.permitir("a","ip")); limite.exito("a");
        assertFalse(limite.permitir("b","ip"));
    }
    @Test void memoriaDeIntentosEstaAcotadaYCaduca() {
        Reloj reloj=new Reloj();
        var limite=new LimitadorIntentos(reloj,Duration.ofMinutes(1),10,100,2);
        assertTrue(limite.permitir("a","ip")); assertFalse(limite.permitir("b","ip"));
        reloj.now=reloj.now.plusSeconds(61);
        assertTrue(limite.permitir("b","otra"));
    }
    @Test void dtoNoImprimeContrasenas() {
        assertFalse(new CambioClave("actual-secreta","nueva-secreta").toString().contains("secreta"));
    }
    private static class Reloj extends Clock {
        Instant now=Instant.parse("2026-11-05T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
