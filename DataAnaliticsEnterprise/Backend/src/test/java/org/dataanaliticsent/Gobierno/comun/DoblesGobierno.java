package org.dataanaliticsent.Gobierno.comun;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Filtro;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Version;
import org.dataanaliticsent.Gobierno.auditoria.internal.dominio.RepositorioAuditoria;
import org.dataanaliticsent.Gobierno.versionado.internal.dominio.RepositorioVersiones;

/** Dobles en memoria para probar los servicios sin PostgreSQL ni tronco común. */
public final class DoblesGobierno {
    public static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    private DoblesGobierno() { }

    public static PuertoIdentidad identidad() { return () -> ACTOR; }

    /** Ejecuta el bloque directamente: en memoria no hay base de datos que revertir. */
    public static Transacciones sinTransaccion() {
        return new Transacciones() {
            @Override public <T> T ejecutar(java.util.function.Supplier<T> bloque) { return bloque.get(); }
        };
    }

    /** Catálogo con los activos registrados por la prueba. */
    public static final class Catalogo implements PuertoCatalogo {
        public final Set<String> activos = new HashSet<>();
        public Catalogo registrar(UUID proyecto, UUID activo) { activos.add(proyecto + "/" + activo); return this; }
        @Override public boolean activoExiste(UUID projectId, UUID assetId) { return activos.contains(projectId + "/" + assetId); }
    }

    /** Concede únicamente los pares permiso/proyecto añadidos. */
    public static final class Autorizacion implements PuertoAutorizacion {
        public final Set<String> concedidos = new HashSet<>();
        public Autorizacion conceder(String permiso, UUID proyecto) { concedidos.add(permiso + "@" + proyecto); return this; }
        @Override public void exigir(UUID actorId, String permiso, UUID projectId) {
            if (!ACTOR.equals(actorId) || !concedidos.contains(permiso + "@" + projectId)) {
                throw new AccesoDenegadoGobiernoException("denegado");
            }
        }
    }

    public static final class Auditoria implements RepositorioAuditoria {
        public final List<Evento> eventos = new ArrayList<>();
        @Override public void guardar(Evento evento) { eventos.add(evento); }
        @Override public List<Evento> buscar(Filtro f, int limite, int desplazamiento) {
            return eventos.stream().filter(e -> f.projectId().equals(e.projectId()))
                    .sorted(Comparator.comparing(Evento::occurredAt).thenComparing(Evento::id).reversed())
                    .skip(desplazamiento).limit(limite).toList();
        }
    }

    public static final class Versiones implements RepositorioVersiones {
        public final List<Version> versiones = new ArrayList<>();
        private List<Version> delRecurso(UUID p, String t, UUID r) {
            return versiones.stream().filter(v -> v.projectId().equals(p) && v.resourceType().equals(t) && v.resourceId().equals(r))
                    .sorted(Comparator.comparingLong(Version::number).reversed()).toList();
        }
        @Override public Optional<Version> ultima(UUID p, String t, UUID r) { return delRecurso(p, t, r).stream().findFirst(); }
        @Override public void guardar(Version v) { versiones.add(v); }
        @Override public List<Version> listar(UUID p, String t, UUID r, int limite, int desplazamiento) {
            return delRecurso(p, t, r).stream().skip(desplazamiento).limit(limite).toList();
        }
        @Override public Optional<Version> buscar(UUID p, String t, UUID r, UUID id) {
            return delRecurso(p, t, r).stream().filter(v -> v.id().equals(id)).findFirst();
        }
    }
}
