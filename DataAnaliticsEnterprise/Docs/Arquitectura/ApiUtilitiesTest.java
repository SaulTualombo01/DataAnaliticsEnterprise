import org.dataanaliticsent.Calidad.Dominio.CanonicalUuid;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.PageQuery;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiProblem;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ApiUtilitiesTest {
    @Test void paginationRejectsInvalidAndOverflowingOffsets() {
        for (int[] pair : new int[][]{{0, 25}, {-1, 25}, {1, 0}, {1, 101}, {Integer.MAX_VALUE, 100}}) {
            assertThrows(ApiException.class, () -> new PageQuery(pair[0], pair[1]));
        }
    }

    @Test void paginationCalculatesBoundaryAndTotalWithoutIntegerOverflow() {
        var query = new PageQuery(3, 25);
        assertEquals(50, query.offset());
        assertFalse(query.info(25, 75L).hasNext());
        assertTrue(query.info(25, 76L).hasNext());
        assertEquals(76L, query.info(25, 76L).totalCount());
        assertNull(query.info(25, true).totalCount());
        assertEquals(Integer.MAX_VALUE - 1, new PageQuery(Integer.MAX_VALUE, 1).offset());
    }

    @Test void canonicalUuidRejectsUppercaseShortenedWhitespaceAndNull() {
        String canonical = "12345678-abcd-4123-8123-123456789abc";
        assertEquals(UUID.fromString(canonical), CanonicalUuid.parse(canonical));
        for (String value : new String[]{null, "", canonical.toUpperCase(), "1-1-1-1-1", " " + canonical}) {
            assertFalse(CanonicalUuid.isValid(value));
            assertThrows(IllegalArgumentException.class, () -> CanonicalUuid.parse(value));
        }
    }

    @Test void errorsShareCorrelationStatusAndTypeAcrossHttpLayers() {
        var problem = ApiProblem.of(403, "ACCESS_DENIED", "Acceso denegado", "Sin permiso", "request-1", java.util.List.of());
        assertEquals(403, problem.status());
        assertEquals("urn:saulongo:problem:access-denied", problem.type());
        assertEquals("urn:request:request-1", problem.instance());
        assertEquals("request-1", problem.requestId());
        assertNull(problem.errors());
    }
}
