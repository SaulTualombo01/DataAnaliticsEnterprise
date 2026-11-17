package org.dataanaliticsent.Gobierno.fichas.internal.aplicacion;

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
import org.dataanaliticsent.Gobierno.comun.RevisionObsoletaException;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi;
import org.dataanaliticsent.Gobierno.fichas.internal.dominio.RepositorioFichas;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Previa;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.ResultadoVersion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.SolicitudVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServicioFichas implements FichasGobiernoApi {
    static final String TIPO = "gobierno.ficha_activo";
    private static final String ACCION = "m3.asset_profiles.update";

    private final RepositorioFichas repositorio;
    private final AuditoriaApi auditoria;
    private final VersionadoApi versionado;
    private final PuertoIdentidad identidad;
    private final PuertoAutorizacion autorizacion;
    private final PuertoCatalogo catalogo;
    private final Transacciones transacciones;
    private final Clock reloj;

    @Autowired
    public ServicioFichas(RepositorioFichas repositorio, AuditoriaApi auditoria, VersionadoApi versionado,
            PuertoIdentidad identidad, PuertoAutorizacion autorizacion, PuertoCatalogo catalogo, Transacciones transacciones) {
        this(repositorio, auditoria, versionado, identidad, autorizacion, catalogo, transacciones, Clock.systemUTC());
    }

    ServicioFichas(RepositorioFichas repositorio, AuditoriaApi auditoria, VersionadoApi versionado,
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
    public Optional<Ficha> consultar(UUID projectId, UUID assetId) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        Objects.requireNonNull(assetId, "assetId obligatorio");
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.FICHAS_LEER, projectId);
        return repositorio.buscar(projectId, assetId);
    }

    @Override
    public Ficha guardar(DatosFicha d) {
        String motivo = d.reason() == null ? "" : d.reason().strip();
        if (motivo.isEmpty() || motivo.length() > 500) throw new IllegalArgumentException("reason obligatoria de 1 a 500 caracteres");
        UUID actor = identidad.actorActual();
        try {
            autorizacion.exigir(actor, PermisosGobierno.FICHAS_ACTUALIZAR, d.projectId());
        } catch (AccesoDenegadoGobiernoException e) {
            auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, d.projectId(), TIPO, d.assetId(), ACCION,
                    Resultado.DENIED, null, Map.of()));
            throw e;
        }
        return transacciones.ejecutar(() -> {
            Optional<Ficha> actual = repositorio.buscar(d.projectId(), d.assetId());
            Long vigente = actual.map(Ficha::revision).orElse(null);
            if (!Objects.equals(vigente, d.expectedRevision())) {
                throw new RevisionObsoletaException(vigente == null
                        ? "La ficha no existe; envíe expectedRevision nulo para crearla"
                        : "La ficha cambió desde la revisión leída; vuelva a consultarla");
            }
            if (actual.isEmpty() && !catalogo.activoExiste(d.projectId(), d.assetId())) {
                throw new RecursoNoEncontradoGobiernoException("El activo no está disponible en el proyecto");
            }
            Map<String, Object> estado = estado(d.ownerId(), d.classification());
            if (actual.isPresent() && estado.equals(estado(actual.get().ownerId(), actual.get().classification()))) {
                return actual.get();
            }
            UUID evento = auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, d.projectId(), TIPO,
                    d.assetId(), ACCION, Resultado.SUCCESS, null, estado));
            ResultadoVersion v = versionado.versionar(new SolicitudVersion(d.projectId(), TIPO, d.assetId(),
                    Actor.usuario(actor), motivo, estado, evento,
                    actual.map(f -> Previa.de(f.currentVersionId())).orElse(Previa.ninguna())));
            Instant ahora = Instant.now(reloj).truncatedTo(ChronoUnit.MICROS);
            Ficha nueva = new Ficha(d.projectId(), d.assetId(), d.ownerId(), d.classification(),
                    vigente == null ? 0 : vigente + 1, v.versionId(), ahora, actor);
            repositorio.guardar(nueva, vigente);
            return nueva;
        });
    }

    private static Map<String, Object> estado(UUID ownerId, Clasificacion clasificacion) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("owner_id", ownerId);
        estado.put("classification", clasificacion);
        return estado;
    }
}
