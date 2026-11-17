package org.dataanaliticsent.Gobierno.politicas.internal.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.EventoNuevo;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.Transacciones;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi;
import org.dataanaliticsent.Gobierno.politicas.internal.dominio.ReglasPolitica;
import org.dataanaliticsent.Gobierno.politicas.internal.dominio.RepositorioPoliticas;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Previa;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.ResultadoVersion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.SolicitudVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServicioPoliticas implements PoliticasApi {
    private static final String TIPO = ReglasPolitica.TIPO_RECURSO;

    private final RepositorioPoliticas repositorio;
    private final AuditoriaApi auditoria;
    private final VersionadoApi versionado;
    private final PuertoIdentidad identidad;
    private final PuertoAutorizacion autorizacion;
    private final PuertoCatalogo catalogo;
    private final Transacciones transacciones;
    private final Clock reloj;

    @Autowired
    public ServicioPoliticas(RepositorioPoliticas repositorio, AuditoriaApi auditoria, VersionadoApi versionado,
            PuertoIdentidad identidad, PuertoAutorizacion autorizacion, PuertoCatalogo catalogo, Transacciones transacciones) {
        this(repositorio, auditoria, versionado, identidad, autorizacion, catalogo, transacciones, Clock.systemUTC());
    }

    ServicioPoliticas(RepositorioPoliticas repositorio, AuditoriaApi auditoria, VersionadoApi versionado,
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
    public Politica crear(NuevaPolitica nueva) {
        String nombre = ReglasPolitica.nombre(nueva.name());
        String descripcion = ReglasPolitica.descripcion(nueva.description());
        UUID actor = identidad.actorActual();
        autorizarCambio(actor, PermisosGobierno.POLITICAS_CREAR, nueva.projectId(), "m3.policies.create", null);
        return transacciones.ejecutar(() -> {
            Instant ahora = ahora();
            UUID id = UUID.randomUUID();
            Politica borrador = new Politica(id, nueva.projectId(), nombre, descripcion, nueva.category(), nueva.ownerId(),
                    EstadoPolitica.DRAFT, 0, null, List.of(), ahora, actor, ahora);
            UUID evento = registrar(actor, borrador, "m3.policies.create",
                    Map.of("name", nombre, "category", nueva.category()));
            ResultadoVersion v = versionado.versionar(new SolicitudVersion(nueva.projectId(), TIPO, id,
                    Actor.usuario(actor), "Alta de la política", ReglasPolitica.estadoVersionable(borrador), evento,
                    Previa.ninguna()));
            Politica creada = conVersion(borrador, v.versionId());
            repositorio.insertar(creada);
            return creada;
        });
    }

    @Override
    public Optional<Politica> consultar(UUID projectId, UUID policyId) {
        Objects.requireNonNull(policyId, "policyId obligatorio");
        autorizarLectura(projectId);
        return repositorio.buscar(projectId, policyId);
    }

    @Override
    public Pagina<Politica> buscar(UUID projectId, FiltroPoliticas filtro, int page, int pageSize) {
        Pagina.validar(page, pageSize);
        FiltroPoliticas f = filtro == null ? FiltroPoliticas.ninguno() : filtro;
        if (f.texto() != null && f.texto().strip().length() > ReglasPolitica.TEXTO_BUSQUEDA_MAXIMO) {
            throw new IllegalArgumentException("texto admite hasta " + ReglasPolitica.TEXTO_BUSQUEDA_MAXIMO + " caracteres");
        }
        autorizarLectura(projectId);
        List<Politica> encontradas = repositorio.listar(projectId, f, pageSize + 1, (page - 1) * pageSize);
        boolean hayMas = encontradas.size() > pageSize;
        return new Pagina<>(hayMas ? encontradas.subList(0, pageSize) : encontradas, page, pageSize, hayMas);
    }

    @Override
    public Politica actualizar(UUID projectId, UUID policyId, long expectedRevision, CambiosPolitica cambios, String reason) {
        Objects.requireNonNull(cambios, "cambios obligatorios");
        String motivo = ReglasPolitica.motivo(reason);
        String nombre = cambios.name() == null ? null : ReglasPolitica.nombre(cambios.name());
        String descripcion = cambios.description() == null ? null : ReglasPolitica.descripcion(cambios.description());
        UUID actor = identidad.actorActual();
        autorizarCambio(actor, PermisosGobierno.POLITICAS_ACTUALIZAR, projectId, "m3.policies.update", policyId);
        return transacciones.ejecutar(() -> {
            Politica actual = vigente(projectId, policyId, expectedRevision);
            ReglasPolitica.exigirEditable(actual);
            Politica nueva = new Politica(actual.id(), projectId,
                    nombre == null ? actual.name() : nombre,
                    descripcion == null ? actual.description() : descripcion,
                    cambios.category() == null ? actual.category() : cambios.category(),
                    cambios.ownerId() == null ? actual.ownerId() : cambios.ownerId(),
                    actual.status(), actual.revision(), actual.currentVersionId(), actual.assetIds(),
                    actual.createdAt(), actual.createdBy(), actual.updatedAt());
            return guardarCambio(actor, actual, nueva, "m3.policies.update", Map.of(), motivo);
        });
    }

    @Override
    public Politica cambiarEstado(UUID projectId, UUID policyId, long expectedRevision, EstadoPolitica nuevo, String reason) {
        Objects.requireNonNull(nuevo, "estado obligatorio");
        String motivo = ReglasPolitica.motivo(reason);
        UUID actor = identidad.actorActual();
        autorizarCambio(actor, PermisosGobierno.POLITICAS_CICLO_VIDA, projectId, "m3.policies.lifecycle", policyId);
        return transacciones.ejecutar(() -> {
            Politica actual = vigente(projectId, policyId, expectedRevision);
            if (actual.status() == nuevo) return actual;
            ReglasPolitica.exigirTransicion(actual.status(), nuevo);
            Politica nueva = conEstado(actual, nuevo);
            return guardarCambio(actor, actual, nueva, "m3.policies.lifecycle",
                    Map.of("from", actual.status(), "to", nuevo), motivo);
        });
    }

    @Override
    public Politica asociarActivo(UUID projectId, UUID policyId, long expectedRevision, UUID assetId, String reason) {
        Objects.requireNonNull(assetId, "assetId obligatorio");
        String motivo = ReglasPolitica.motivo(reason);
        UUID actor = identidad.actorActual();
        autorizarCambio(actor, PermisosGobierno.POLITICAS_ACTIVOS, projectId, "m3.policies.assets.add", policyId);
        return transacciones.ejecutar(() -> {
            Politica actual = vigente(projectId, policyId, expectedRevision);
            ReglasPolitica.exigirEditable(actual);
            if (actual.assetIds().contains(assetId)) return actual;
            if (!catalogo.activoExiste(projectId, assetId)) {
                throw new RecursoNoEncontradoGobiernoException("El activo no está disponible en el proyecto");
            }
            List<UUID> activos = new ArrayList<>(actual.assetIds());
            activos.add(assetId);
            Politica guardada = guardarCambio(actor, actual, conActivos(actual, activos), "m3.policies.assets.add",
                    Map.of("asset_id", assetId), motivo);
            repositorio.asociarActivo(projectId, policyId, assetId, actor, guardada.updatedAt());
            return guardada;
        });
    }

    @Override
    public Politica retirarActivo(UUID projectId, UUID policyId, long expectedRevision, UUID assetId, String reason) {
        Objects.requireNonNull(assetId, "assetId obligatorio");
        String motivo = ReglasPolitica.motivo(reason);
        UUID actor = identidad.actorActual();
        autorizarCambio(actor, PermisosGobierno.POLITICAS_ACTIVOS, projectId, "m3.policies.assets.remove", policyId);
        return transacciones.ejecutar(() -> {
            Politica actual = vigente(projectId, policyId, expectedRevision);
            ReglasPolitica.exigirEditable(actual);
            if (!actual.assetIds().contains(assetId)) return actual;
            List<UUID> activos = new ArrayList<>(actual.assetIds());
            activos.remove(assetId);
            Politica guardada = guardarCambio(actor, actual, conActivos(actual, activos), "m3.policies.assets.remove",
                    Map.of("asset_id", assetId), motivo);
            repositorio.retirarActivo(projectId, policyId, assetId, actor, guardada.updatedAt());
            return guardada;
        });
    }

    @Override
    public List<PoliticaAplicable> politicasDeActivo(UUID projectId, UUID assetId) {
        Objects.requireNonNull(assetId, "assetId obligatorio");
        autorizarLectura(projectId);
        return repositorio.deActivo(projectId, assetId).stream()
                .map(p -> new PoliticaAplicable(p, p.status() == EstadoPolitica.ACTIVE)).toList();
    }

    /** Audita, versiona y guarda un cambio efectivo; sin diferencias devuelve la política tal cual. */
    private Politica guardarCambio(UUID actor, Politica actual, Politica propuesta, String accion,
            Map<String, Object> detalles, String motivo) {
        if (ReglasPolitica.estadoVersionable(actual).equals(ReglasPolitica.estadoVersionable(propuesta))) return actual;
        Instant ahora = ahora();
        UUID evento = registrar(actor, actual, accion, detalles);
        ResultadoVersion v = versionado.versionar(new SolicitudVersion(actual.projectId(), TIPO, actual.id(),
                Actor.usuario(actor), motivo, ReglasPolitica.estadoVersionable(propuesta), evento,
                Previa.de(actual.currentVersionId())));
        Politica nueva = new Politica(propuesta.id(), propuesta.projectId(), propuesta.name(), propuesta.description(),
                propuesta.category(), propuesta.ownerId(), propuesta.status(), actual.revision() + 1, v.versionId(),
                propuesta.assetIds(), actual.createdAt(), actual.createdBy(), ahora);
        repositorio.actualizar(nueva, actual.revision());
        return nueva;
    }

    private Politica vigente(UUID projectId, UUID policyId, long expectedRevision) {
        Objects.requireNonNull(policyId, "policyId obligatorio");
        Politica actual = repositorio.buscar(projectId, policyId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("Política no disponible"));
        ReglasPolitica.exigirRevision(actual, expectedRevision);
        return actual;
    }

    private void autorizarLectura(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.POLITICAS_LEER, projectId);
    }

    /** Un intento de cambio denegado queda auditado como DENIED antes de propagar el rechazo. */
    private void autorizarCambio(UUID actor, String permiso, UUID projectId, String accion, UUID policyId) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        try {
            autorizacion.exigir(actor, permiso, projectId);
        } catch (AccesoDenegadoGobiernoException e) {
            auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, projectId, TIPO, policyId, accion,
                    Resultado.DENIED, null, Map.of()));
            throw e;
        }
    }

    private UUID registrar(UUID actor, Politica politica, String accion, Map<String, Object> detalles) {
        return auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, politica.projectId(), TIPO,
                politica.id(), accion, Resultado.SUCCESS, null, detalles));
    }

    private Instant ahora() { return Instant.now(reloj).truncatedTo(ChronoUnit.MICROS); }

    private static Politica conVersion(Politica p, UUID versionId) {
        return new Politica(p.id(), p.projectId(), p.name(), p.description(), p.category(), p.ownerId(), p.status(),
                p.revision(), versionId, p.assetIds(), p.createdAt(), p.createdBy(), p.updatedAt());
    }

    private static Politica conEstado(Politica p, EstadoPolitica estado) {
        return new Politica(p.id(), p.projectId(), p.name(), p.description(), p.category(), p.ownerId(), estado,
                p.revision(), p.currentVersionId(), p.assetIds(), p.createdAt(), p.createdBy(), p.updatedAt());
    }

    private static Politica conActivos(Politica p, List<UUID> activos) {
        return new Politica(p.id(), p.projectId(), p.name(), p.description(), p.category(), p.ownerId(), p.status(),
                p.revision(), p.currentVersionId(), activos, p.createdAt(), p.createdBy(), p.updatedAt());
    }
}
