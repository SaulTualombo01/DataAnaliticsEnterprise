package org.dataanaliticsent.Gobierno.versionado.internal.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Cambio;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.TipoCambio;
import org.junit.jupiter.api.Test;

class ComparadorVersionesTests {

    @Test
    void detectaCamposModificadosAgregadosYEliminados() {
        Map<String, Object> v1 = new HashMap<>(Map.of("status", "DRAFT", "name", "PII", "asset_ids", List.of()));
        v1.put("owner_id", null);
        Map<String, Object> v2 = Map.of("status", "ACTIVE", "name", "PII", "asset_ids", List.of("a1"), "rules", List.of());

        List<Cambio> cambios = ComparadorVersiones.diferencias(v1, v2);

        assertEquals(List.of(
                new Cambio("asset_ids", TipoCambio.MODIFICADO, "[]", "[\"a1\"]"),
                new Cambio("owner_id", TipoCambio.ELIMINADO, "null", null),
                new Cambio("rules", TipoCambio.AGREGADO, null, "[]"),
                new Cambio("status", TipoCambio.MODIFICADO, "\"DRAFT\"", "\"ACTIVE\"")), cambios);
    }

    @Test
    void estadosIgualesNoTienenCambiosAunqueDifieraElFormatoNumerico() {
        Map<String, Object> a = Map.of("limite", 1, "anidado", Map.of("x", "y", "z", true));
        Map<String, Object> b = Map.of("limite", new BigDecimal("1.0"), "anidado", Map.of("z", true, "x", "y"));

        assertTrue(ComparadorVersiones.diferencias(a, b).isEmpty());
    }
}
