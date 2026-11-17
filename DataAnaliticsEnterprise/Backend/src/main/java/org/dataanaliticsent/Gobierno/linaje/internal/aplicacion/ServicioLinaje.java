package org.dataanaliticsent.Gobierno.linaje.internal.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.EventoNuevo;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.Transacciones;
import org.dataanaliticsent.Gobierno.linaje.ConflictoLinajeException;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi;
import org.dataanaliticsent.Gobierno.linaje.internal.dominio.RecorridoLinaje;
import org.dataanaliticsent.Gobierno.linaje.internal.dominio.RepositorioLinaje;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Previa;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.ResultadoVersion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.SolicitudVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServicioLinaje implements LinajeApi {
    static final String TIPO = "gobierno.relacion_linaje";
    private static final int DESCRIPCION_MAXIMA = 1000;

    private final RepositorioLinaje repositorio;
    private final AuditoriaApi auditoria;
    private final VersionadoApi versionado;
    private final PuertoIdentidad identidad;
    private final PuertoAutorizacion autorizacion;
    private final PuertoCatalogo catalogo;
    private final Transacciones transacciones;
    private final Clock reloj;

    @Autowired
    public ServicioLinaje(RepositorioLinaje repositorio, AuditoriaApi auditoria, VersionadoApi versionado,
            PuertoIdentidad identidad, PuertoAutorizacion autorizacion, PuertoCatalogo catalogo, Transacciones transacciones) {
        this(repositorio, auditoria, versionado, identidad, autorizacion, catalogo, transacciones, Clock.systemUTC());
    }

    ServicioLinaje(RepositorioLinaje repositorio, AuditoriaApi auditoria, VersionadoApi versionado,
            PuertoIdentidad identidad, PuertoAutorizacion autorizacion, PuertoCatalogo catalogo,
            Transacciones transacciones, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.versionado = versionado;
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.catalogo = catalogo;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    @Override
    public Relacion registrar(NuevaRelacion n) {
        if (n.originAssetId().equals(n.targetAssetId())) {
            throw new IllegalArgumentException("Un activo no puede ser origen de sí mismo");
        }
        String descripcion = n.descripcion() == null ? "" : n.descripcion().strip();
        if (descripcion.length() > DESCRIPCION_MAXIMA) {
            throw new IllegalArgumentException("descripcion admite hasta " + DESCRIPCION_MAXIMA + " caracteres");
        }
        String motivo = motivo(n.reason());
        UUID actor = identidad.actorActual();
        autorizarCambio(actor, n.projectId(), "m3.lineage.create", null);
        if (!catalogo.activoExiste(n.projectId(), n.originAssetId()) || !catalogo.activoExiste(n.projectId(), n.targetAssetId())) {
            throw new RecursoNoEncontradoGobiernoException("Algún activo no está disponible en el proyecto");
        }
        return transacciones.ejecutar(() -> {
            repositorio.bloquearProyecto(n.projectId());
            Optional<Relacion> existente = repositorio.vigenteIgual(n.projectId(), n.originAssetId(), n.targetAssetId(), n.tipo());
            if (existente.isPresent()) return existente.get();
            if (RecorridoLinaje.alcanza(n.targetAssetId(), n.originAssetId(), repositorio, n.projectId())) {
                throw new ConflictoLinajeException("La relación crearía un ciclo: el destino ya alimenta al origen");
            }
            UUID id = UUID.randomUUID();
            Relacion borrador = new Relacion(id, n.projectId(), n.originAssetId(), n.targetAssetId(), n.tipo(),
                    descripcion, EstadoRelacion.ACTIVE, null, ahora(), actor, null, null);
            UUID evento = registrarEvento(actor, borrador, "m3.lineage.create");
            ResultadoVersion v = versionado.versionar(new SolicitudVersion(n.projectId(), TIPO, id, Actor.usuario(actor),
                    motivo, estado(borrador), evento, Previa.ninguna()));
            Relacion creada = new Relacion(id, n.projectId(), n.originAssetId(), n.targetAssetId(), n.tipo(), descripcion,
                    EstadoRelacion.ACTIVE, v.versionId(), borrador.createdAt(), actor, null, null);
            repositorio.insertar(creada);
            return creada;
        });
    }

    @Override
    public Relacion retirar(UUID projectId, UUID relationId, String reason) {
        Objects.requireNonNull(relationId, "relationId obligatorio");
        String motivo = motivo(reason);
        UUID actor = identidad.actorActual();
        autorizarCambio(actor, projectId, "m3.lineage.retire", relationId);
        return transacciones.ejecutar(() -> {
            repositorio.bloquearProyecto(projectId);
            Relacion actual = repositorio.buscar(projectId, relationId)
                    .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("Relación de linaje no disponible"));
            if (actual.estado() == EstadoRelacion.RETIRED) return actual;
            Instant ahora = ahora();
            Relacion propuesta = new Relacion(actual.id(), projectId, actual.originAssetId(), actual.targetAssetId(),
                    actual.tipo(), actual.descripcion(), EstadoRelacion.RETIRED, actual.currentVersionId(),
                    actual.createdAt(), actual.createdBy(), ahora, actor);
            UUID evento = registrarEvento(actor, actual, "m3.lineage.retire");
            ResultadoVersion v = versionado.versionar(new SolicitudVersion(projectId, TIPO, actual.id(), Actor.usuario(actor),
                    motivo, estado(propuesta), evento, Previa.de(actual.currentVersionId())));
            Relacion retirada = new Relacion(actual.id(), projectId, actual.originAssetId(), actual.targetAssetId(),
                    actual.tipo(), actual.descripcion(), EstadoRelacion.RETIRED, v.versionId(), actual.createdAt(),
                    actual.createdBy(), ahora, actor);
            repositorio.retirar(retirada);
            return retirada;
        });
    }

    @Override
    public Optional<Relacion> consultar(UUID projectId, UUID relationId) {
        Objects.requireNonNull(relationId, "relationId obligatorio");
        autorizarLectura(projectId);
        return repositorio.buscar(projectId, relationId);
    }

    @Override
    public Grafo grafo(UUID projectId, UUID assetId, Direccion direccion, int profundidad) {
        Objects.requireNonNull(assetId, "assetId obligatorio");
        Objects.requireNonNull(direccion, "direccion obligatoria");
        if (profundidad < 1 || profundidad > RecorridoLinaje.PROFUNDIDAD_MAXIMA) {
            throw new IllegalArgumentException("profundidad debe estar entre 1 y " + RecorridoLinaje.PROFUNDIDAD_MAXIMA);
        }
        autorizarLectura(projectId);
        return RecorridoLinaje.grafo(assetId, direccion, profundidad, repositorio, projectId);
    }

    private static String motivo(String reason) {
        String limpio = reason == null ? "" : reason.strip();
        if (limpio.isEmpty() || limpio.length() > 500) throw new IllegalArgumentException("reason obligatoria de 1 a 500 caracteres");
        return limpio;
    }

    private static Map<String, Object> estado(Relacion r) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("origin_asset_id", r.originAssetId());
        estado.put("target_asset_id", r.targetAssetId());
        estado.put("type", r.tipo());
        estado.put("description", r.descripcion());
        estado.put("status", r.estado());
        return estado;
    }

    private void autorizarLectura(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.LINAJE_LEER, projectId);
    }

    private void autorizarCambio(UUID actor, UUID projectId, String accion, UUID relationId) {
        try {
            autorizacion.exigir(actor, PermisosGobierno.LINAJE_ADMINISTRAR, projectId);
        } catch (AccesoDenegadoGobiernoException e) {
            auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, projectId, TIPO, relationId, accion,
                    Resultado.DENIED, null, Map.of()));
            throw e;
        }
    }

    private UUID registrarEvento(UUID actor, Relacion r, String accion) {
        return auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, r.projectId(), TIPO, r.id(), accion,
                Resultado.SUCCESS, null, Map.of("origin_asset_id", r.originAssetId(),
                        "target_asset_id", r.targetAssetId(), "type", r.tipo())));
    }

    private Instant ahora() { return Instant.now(reloj).truncatedTo(ChronoUnit.MICROS); }
}
