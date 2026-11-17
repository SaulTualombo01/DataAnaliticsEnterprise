package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.junit.jupiter.api.Test;

/** Los permisos que exige el código son exactamente los que da de alta la migración V34. */
class ContratoPermisosTests {
    private static final Path V34 = Path.of("Backend", "src", "main", "resources", "db", "migration", "V34__gobierno_permisos.sql");

    @Test
    void permisosDelCodigoIgualesALosDeLaMigracion() throws Exception {
        Set<String> codigo = new TreeSet<>();
        for (Field f : PermisosGobierno.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) codigo.add((String) f.get(null));
        }
        Set<String> migracion = new TreeSet<>();
        Matcher m = Pattern.compile("INSERT INTO autorizacion\\.permiso\\([^)]*\\) VALUES\\('[^']*','(m3\\.[^']+)'")
                .matcher(Files.readString(V34));
        while (m.find()) migracion.add(m.group(1));

        assertEquals(codigo, migracion);
    }
}
