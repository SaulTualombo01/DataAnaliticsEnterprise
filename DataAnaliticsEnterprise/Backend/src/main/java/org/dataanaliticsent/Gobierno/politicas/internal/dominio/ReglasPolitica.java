package org.dataanaliticsent.Gobierno.politicas.internal.dominio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.politicas.ConflictoPoliticaException;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;

/** Reglas de negocio de una política, independientes de la base de datos y de Spring. */
public final class ReglasPolitica {
    public static final String TIPO_RECURSO = "gobierno.politica";
    public static final int NOMBRE_MAXIMO = 200;
    public static final int DESCRIPCION_MAXIMA = 2000;
    public static final int MOTIVO_MAXIMO = 500;
    public static final int TEXTO_BUSQUEDA_MAXIMO = 100;

    private static final Map<EstadoPolitica, Set<EstadoPolitica>> TRANSICIONES = Map.of(
            EstadoPolitica.DRAFT, Set.of(EstadoPolitica.ACTIVE, EstadoPolitica.RETIRED),
            EstadoPolitica.ACTIVE, Set.of(EstadoPolitica.INACTIVE, EstadoPolitica.RETIRED),
            EstadoPolitica.INACTIVE, Set.of(EstadoPolitica.ACTIVE, EstadoPolitica.RETIRED),
            EstadoPolitica.RETIRED, Set.of());

    private ReglasPolitica() { }

    public static String nombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty() || limpio.length() > NOMBRE_MAXIMO) {
            throw new IllegalArgumentException("name obligatorio de 1 a " + NOMBRE_MAXIMO + " caracteres");
        }
        return limpio;
    }

    public static String descripcion(String descripcion) {
        String limpia = descripcion == null ? "" : descripcion.strip();
        if (limpia.length() > DESCRIPCION_MAXIMA) {
            throw new IllegalArgumentException("description admite hasta " + DESCRIPCION_MAXIMA + " caracteres");
        }
        return limpia;
    }

    public static String motivo(String motivo) {
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.isEmpty() || limpio.length() > MOTIVO_MAXIMO) {
            throw new IllegalArgumentException("reason obligatoria de 1 a " + MOTIVO_MAXIMO + " caracteres");
        }
        return limpio;
    }

    public static void exigirTransicion(EstadoPolitica actual, EstadoPolitica nuevo) {
        if (!TRANSICIONES.get(actual).contains(nuevo)) {
            throw new ConflictoPoliticaException("Transición no permitida: " + actual + " → " + nuevo);
        }
    }

    public static void exigirEditable(Politica politica) {
        if (politica.status() == EstadoPolitica.RETIRED) {
            throw new ConflictoPoliticaException("Una política retirada no se modifica");
        }
    }

    public static void exigirRevision(Politica politica, long esperada) {
        if (politica.revision() != esperada) {
            throw new ConflictoPoliticaException("La política cambió desde la revisión " + esperada + "; vuelva a consultarla");
        }
    }

    /** Estado que se versiona: lo que define la política, sin fechas ni revisión técnica. */
    public static Map<String, Object> estadoVersionable(Politica p) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("name", p.name());
        estado.put("description", p.description());
        estado.put("category", p.category());
        estado.put("owner_id", p.ownerId());
        estado.put("status", p.status());
        List<String> activos = p.assetIds().stream().map(UUID::toString).sorted().toList();
        estado.put("asset_ids", activos);
        return estado;
    }
}
