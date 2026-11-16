package org.dataanaliticsent.TroncoComun.identidad;
import java.time.Duration;
import java.util.HashSet;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.TokensInvitacion;
import org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura.ParametrosCuentas;
import org.dataanaliticsent.TroncoComun.identidad.internal.presentacion.CuentasController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InvitacionesUnitTests {
    @Test void tokensSonAleatoriosCanonicosYElHashNoRevelaElOriginal() {
        var tokens=new HashSet<String>();
        for(int i=0;i<100;i++) { String t=TokensInvitacion.nuevo(); assertEquals(43,t.length()); assertTrue(tokens.add(t));
            String hash=TokensInvitacion.hash(t); assertTrue(hash.matches("[0-9a-f]{64}")); assertEquals(hash,TokensInvitacion.hash(t)); assertNotEquals(t,hash); }
    }
    @Test void rechazaTokenNuloCortoNoCanonicoYSobredimensionado() {
        for(String t:new String[]{"", "x", "A".repeat(42)+"B", "A".repeat(44), "!".repeat(43)}) assertThrows(CuentasException.class,()->TokensInvitacion.hash(t));
        assertThrows(CuentasException.class,()->TokensInvitacion.hash(null));
    }
    @Test void objetosConSecretosNoLosIncluyenEnToString() {
        assertFalse(new CuentasController.Activacion("TOKEN_SENSIBLE","CLAVE_SENSIBLE").toString().contains("SENSIBLE"));
        assertFalse(new CuentasApi.EntregaInvitacion(null,"TOKEN_SENSIBLE").toString().contains("SENSIBLE"));
        assertFalse(new CuentasController.EntregaHttp(null,"manual","TOKEN_SENSIBLE").toString().contains("SENSIBLE"));
    }
    @Test void configuracionLimitaLaVidaDelTokenYElPresupuestoAnonimo() {
        assertThrows(IllegalArgumentException.class,()->new ParametrosCuentas(Duration.ofMinutes(4),true,Duration.ofMinutes(15),30));
        assertThrows(IllegalArgumentException.class,()->new ParametrosCuentas(Duration.ofDays(8),true,Duration.ofMinutes(15),30));
        assertThrows(IllegalArgumentException.class,()->new ParametrosCuentas(Duration.ofHours(24),true,Duration.ZERO,30));
        assertThrows(IllegalArgumentException.class,()->new ParametrosCuentas(Duration.ofHours(24),true,Duration.ofMinutes(15),0));
        assertFalse(new ParametrosCuentas(Duration.ofHours(24),false,Duration.ofMinutes(15),30).manualDeliveryEnabled());
    }
}
