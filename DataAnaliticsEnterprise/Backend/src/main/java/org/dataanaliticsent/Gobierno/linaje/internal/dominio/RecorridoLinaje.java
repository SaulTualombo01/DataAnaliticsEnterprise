package org.dataanaliticsent.Gobierno.linaje.internal.dominio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Direccion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Grafo;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Nodo;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;

/**
 * Recorridos del grafo de linaje por niveles (una consulta por nivel, no por nodo). Los límites
 * evitan recorrer grafos enormes y se informan con truncado=true en vez de cortar en silencio.
 */
public final class RecorridoLinaje {
    public static final int PROFUNDIDAD_MAXIMA = 5;
    public static final int NODOS_MAXIMOS = 200;
    /** Límite para comprobar ciclos; por encima no se puede afirmar que no haya ciclo. */
    public static final int NODOS_MAXIMOS_CICLO = 5000;

    private RecorridoLinaje() { }

    public static Grafo grafo(UUID activo, Direccion direccion, int profundidad, RepositorioLinaje repo, UUID proyecto) {
        Map<UUID, Integer> nodos = new LinkedHashMap<>();
        Map<UUID, Relacion> relaciones = new LinkedHashMap<>();
        nodos.put(activo, 0);
        boolean truncado = false;
        if (direccion != Direccion.DESTINOS) {
            truncado |= recorrer(activo, profundidad, ids -> repo.entrantes(proyecto, ids), Relacion::originAssetId, -1, nodos, relaciones);
        }
        if (direccion != Direccion.ORIGENES) {
            truncado |= recorrer(activo, profundidad, ids -> repo.salientes(proyecto, ids), Relacion::targetAssetId, 1, nodos, relaciones);
        }
        List<Nodo> lista = nodos.entrySet().stream().map(e -> new Nodo(e.getKey(), e.getValue())).toList();
        return new Grafo(activo, direccion, profundidad, lista, new ArrayList<>(relaciones.values()), truncado);
    }

    /**
     * ¿Desde "desde" se llega a "hasta" siguiendo relaciones vigentes hacia los destinos?
     * Si es así, añadir hasta → desde cerraría un ciclo.
     */
    public static boolean alcanza(UUID desde, UUID hasta, RepositorioLinaje repo, UUID proyecto) {
        Set<UUID> visitados = new LinkedHashSet<>(List.of(desde));
        Set<UUID> frontera = Set.of(desde);
        while (!frontera.isEmpty()) {
            Set<UUID> siguiente = new LinkedHashSet<>();
            for (Relacion r : repo.salientes(proyecto, frontera)) {
                if (r.targetAssetId().equals(hasta)) return true;
                if (visitados.add(r.targetAssetId())) siguiente.add(r.targetAssetId());
            }
            if (visitados.size() > NODOS_MAXIMOS_CICLO) {
                throw new IllegalStateException("El grafo de linaje es demasiado grande para comprobar ciclos");
            }
            frontera = siguiente;
        }
        return false;
    }

    private static boolean recorrer(UUID activo, int profundidad, Function<Collection<UUID>, List<Relacion>> vecinos,
            Function<Relacion, UUID> otroExtremo, int signo, Map<UUID, Integer> nodos, Map<UUID, Relacion> relaciones) {
        Set<UUID> frontera = Set.of(activo);
        for (int nivel = 1; nivel <= profundidad && !frontera.isEmpty(); nivel++) {
            Set<UUID> siguiente = new LinkedHashSet<>();
            for (Relacion r : vecinos.apply(frontera)) {
                UUID otro = otroExtremo.apply(r);
                if (!nodos.containsKey(otro)) {
                    if (nodos.size() >= NODOS_MAXIMOS) return true;
                    nodos.put(otro, signo * nivel);
                    siguiente.add(otro);
                }
                relaciones.putIfAbsent(r.id(), r);
            }
            frontera = siguiente;
        }
        return false;
    }
}
