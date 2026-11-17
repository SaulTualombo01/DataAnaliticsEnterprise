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
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.dominio.RepositorioEvaluaciones;
import org.dataanaliticsent.Gobierno.fichas.ConflictoFichaException;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.fichas.internal.dominio.RepositorioFichas;
import org.dataanaliticsent.Gobierno.politicas.ConflictoPoliticaException;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.FiltroPoliticas;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.ReglaGobierno;
import org.dataanaliticsent.Gobierno.politicas.internal.dominio.RepositorioPoliticas;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.EstadoRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;
import org.dataanaliticsent.Gobierno.linaje.internal.dominio.RepositorioLinaje;

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

    /** Repositorio de políticas en memoria con las mismas reglas de unicidad y revisión que PostgreSQL. */
    public static final class Politicas implements RepositorioPoliticas {
        public final List<Politica> datos = new ArrayList<>();

        @Override public Optional<Politica> buscar(UUID projectId, UUID policyId) {
            return datos.stream().filter(p -> p.projectId().equals(projectId) && p.id().equals(policyId)).findFirst();
        }
        @Override public void insertar(Politica p) { exigirNombreLibre(p); datos.add(p); }
        @Override public void actualizar(Politica p, long revisionLeida) {
            Politica actual = buscar(p.projectId(), p.id()).orElseThrow();
            if (actual.revision() != revisionLeida) throw new ConflictoPoliticaException("revisión");
            exigirNombreLibre(p);
            datos.set(datos.indexOf(actual), p);
        }
        @Override public void asociarActivo(UUID pr, UUID po, UUID a, UUID actor, Instant t) { }
        @Override public void retirarActivo(UUID pr, UUID po, UUID a, UUID actor, Instant t) { }
        @Override public void insertarRegla(UUID pr, UUID po, ReglaGobierno r, UUID actor, Instant t) { }
        @Override public void borrarRegla(UUID pr, UUID po, UUID r) { }
        @Override public List<Politica> listar(UUID projectId, FiltroPoliticas f, int limite, int desplazamiento) {
            return datos.stream().filter(p -> p.projectId().equals(projectId))
                    .filter(p -> f.category() == null || p.category() == f.category())
                    .filter(p -> f.status() == null || p.status() == f.status())
                    .sorted(Comparator.comparing(Politica::createdAt).thenComparing(Politica::id))
                    .skip(desplazamiento).limit(limite).toList();
        }
        @Override public List<Politica> deActivo(UUID projectId, UUID assetId) {
            return datos.stream().filter(p -> p.projectId().equals(projectId) && p.assetIds().contains(assetId))
                    .sorted(Comparator.comparing(Politica::createdAt).thenComparing(Politica::name)).toList();
        }
        private void exigirNombreLibre(Politica p) {
            boolean repetido = datos.stream().anyMatch(o -> !o.id().equals(p.id()) && o.projectId().equals(p.projectId())
                    && o.status() != EstadoPolitica.RETIRED && o.name().equalsIgnoreCase(p.name()));
            if (repetido && p.status() != EstadoPolitica.RETIRED) throw new ConflictoPoliticaException("nombre");
        }
    }

    public static final class Fichas implements RepositorioFichas {
        public final List<Ficha> datos = new ArrayList<>();
        @Override public Optional<Ficha> buscar(UUID projectId, UUID assetId) {
            return datos.stream().filter(f -> f.projectId().equals(projectId) && f.assetId().equals(assetId)).findFirst();
        }
        @Override public void guardar(Ficha ficha, Long revisionLeida) {
            Optional<Ficha> actual = buscar(ficha.projectId(), ficha.assetId());
            if (!java.util.Objects.equals(actual.map(Ficha::revision).orElse(null), revisionLeida)) {
                throw new ConflictoFichaException("revisión");
            }
            actual.ifPresent(datos::remove);
            datos.add(ficha);
        }
    }

    public static final class Evaluaciones implements RepositorioEvaluaciones {
        public final List<Evaluacion> datos = new ArrayList<>();
        @Override public void guardar(Evaluacion e) { datos.add(e); }
        @Override public List<Evaluacion> listar(UUID projectId, UUID assetId, int limite, int desplazamiento) {
            List<Evaluacion> propias = new ArrayList<>(datos.stream()
                    .filter(e -> e.projectId().equals(projectId) && e.assetId().equals(assetId)).toList());
            java.util.Collections.reverse(propias);
            return propias.stream().skip(desplazamiento).limit(limite).toList();
        }
    }

    public static final class Linaje implements RepositorioLinaje {
        public final List<Relacion> datos = new ArrayList<>();
        @Override public void bloquearProyecto(UUID projectId) { }
        @Override public Optional<Relacion> buscar(UUID projectId, UUID id) {
            return datos.stream().filter(r -> r.projectId().equals(projectId) && r.id().equals(id)).findFirst();
        }
        @Override public Optional<Relacion> vigenteIgual(UUID p, UUID o, UUID t, TipoRelacion tipo) {
            return datos.stream().filter(r -> r.projectId().equals(p) && r.originAssetId().equals(o)
                    && r.targetAssetId().equals(t) && r.tipo() == tipo && r.estado() == EstadoRelacion.ACTIVE).findFirst();
        }
        @Override public void insertar(Relacion r) { datos.add(r); }
        @Override public void retirar(Relacion r) {
            Relacion actual = buscar(r.projectId(), r.id()).orElseThrow();
            datos.set(datos.indexOf(actual), r);
        }
        @Override public List<Relacion> entrantes(UUID p, java.util.Collection<UUID> activos) {
            return datos.stream().filter(r -> r.projectId().equals(p) && r.estado() == EstadoRelacion.ACTIVE
                    && activos.contains(r.targetAssetId())).toList();
        }
        @Override public List<Relacion> salientes(UUID p, java.util.Collection<UUID> activos) {
            return datos.stream().filter(r -> r.projectId().equals(p) && r.estado() == EstadoRelacion.ACTIVE
                    && activos.contains(r.originAssetId())).toList();
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
