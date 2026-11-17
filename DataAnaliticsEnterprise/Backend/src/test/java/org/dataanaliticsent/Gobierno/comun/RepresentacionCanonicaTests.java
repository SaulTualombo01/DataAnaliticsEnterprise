package org.dataanaliticsent.Gobierno.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepresentacionCanonicaTests {

    @Test
    void ordenaClavesSinImportarElOrdenDeConstruccion() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("b", 1); a.put("a", Map.of("y", true, "x", "texto"));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("a", Map.of("x", "texto", "y", true)); b.put("b", 1);

        assertEquals("{\"a\":{\"x\":\"texto\",\"y\":true},\"b\":1}", RepresentacionCanonica.json(a));
        assertEquals(RepresentacionCanonica.json(a), RepresentacionCanonica.json(b));
    }

    @Test
    void normalizaNumerosEscapaTextoYAceptaTiposComunes() {
        UUID id = UUID.fromString("f7c83e25-073f-4ddb-b72b-e4669d242d37");
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("decimal", new BigDecimal("1.50"));
        estado.put("id", id);
        estado.put("lista", List.of(1, "dos"));
        estado.put("nulo", null);
        estado.put("texto", "comillas \" y salto\n");

        assertEquals("{\"decimal\":1.5,\"id\":\"" + id + "\",\"lista\":[1,\"dos\"],\"nulo\":null,"
                + "\"texto\":\"comillas \\\" y salto\\n\"}", RepresentacionCanonica.json(estado));
    }

    @Test
    void rechazaValoresQueNoSonJsonDeterminista() {
        assertThrows(IllegalArgumentException.class, () -> RepresentacionCanonica.json(Map.of("x", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> RepresentacionCanonica.json(Map.of("x", new Object())));
    }

    @Test
    void calculaSha256EnHexadecimalMinuscula() {
        assertEquals("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                RepresentacionCanonica.sha256("{}"));
    }
}
