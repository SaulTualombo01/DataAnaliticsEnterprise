package org.dataanaliticsent.Gobierno.versionado.internal.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.dataanaliticsent.Gobierno.comun.RepresentacionCanonica;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Cambio;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.TipoCambio;

/**
 * Compara dos estados versionados campo por campo (primer nivel). Las listas y objetos anidados
 * se comparan completos y se informan como un solo cambio. Dos números iguales con distinta
 * representación (1 y 1.0) no cuentan como cambio.
 */
public final class ComparadorVersiones {
    private ComparadorVersiones() { }

    public static List<Cambio> diferencias(Map<String, Object> desde, Map<String, Object> hasta) {
        TreeSet<String> campos = new TreeSet<>(desde.keySet());
        campos.addAll(hasta.keySet());
        List<Cambio> cambios = new ArrayList<>();
        for (String campo : campos) {
            boolean antes = desde.containsKey(campo);
            boolean despues = hasta.containsKey(campo);
            Object a = desde.get(campo);
            Object b = hasta.get(campo);
            if (!antes) {
                cambios.add(new Cambio(campo, TipoCambio.AGREGADO, null, RepresentacionCanonica.valor(b)));
            } else if (!despues) {
                cambios.add(new Cambio(campo, TipoCambio.ELIMINADO, RepresentacionCanonica.valor(a), null));
            } else if (!iguales(a, b)) {
                cambios.add(new Cambio(campo, TipoCambio.MODIFICADO, RepresentacionCanonica.valor(a),
                        RepresentacionCanonica.valor(b)));
            }
        }
        return cambios;
    }

    private static boolean iguales(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
        }
        return Objects.equals(RepresentacionCanonica.valor(a), RepresentacionCanonica.valor(b));
    }
}
