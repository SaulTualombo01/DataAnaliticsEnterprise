package org.dataanaliticsent.Gobierno.versionado.internal.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.versionado.ConflictoVersionException;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.comun.ReglasAuditoria;
import org.dataanaliticsent.Gobierno.versionado.internal.dominio.ComparadorVersiones;
import org.dataanaliticsent.Gobierno.versionado.internal.dominio.RepositorioVersiones;
import org.dataanaliticsent.Gobierno.comun.RepresentacionCanonica;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ServicioVersionado implements VersionadoApi {
    /** Versión del formato de state_payload; subirla si cambia la forma del estado guardado. */
    static final int REPRESENTACION = 1;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RepositorioVersiones repositorio;
    private final PuertoIdentidad identidad;
    private final PuertoAutorizacion autorizacion;
    private final Clock reloj;

    @Autowired
    public ServicioVersionado(RepositorioVersiones repositorio, PuertoIdentidad identidad, PuertoAutorizacion autorizacion) {
        this(repositorio, identidad, autorizacion, Clock.systemUTC());
    }

    ServicioVersionado(RepositorioVersiones repositorio, PuertoIdentidad identidad, PuertoAutorizacion autorizacion, Clock reloj) {
        this.repositorio = repositorio;
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public ResultadoVersion versionar(SolicitudVersion s) {
        ReglasAuditoria.validarTipoRecurso(s.resourceType());
        ReglasAuditoria.validarSinSecretos(s.estado());
        String estado = RepresentacionCanonica.json(s.estado());
        String hash = RepresentacionCanonica.sha256(estado);

        Optional<Version> vigente = repositorio.ultima(s.projectId(), s.resourceType(), s.resourceId());
        UUID vigenteId = vigente.map(Version::id).orElse(null);
        if (s.previaEsperada() != null && !Objects.equals(s.previaEsperada().versionId(), vigenteId)) {
            throw new ConflictoVersionException("El recurso cambió desde la versión leída; vuelva a consultarlo");
        }
        if (vigente.isPresent() && vigente.get().contentHash().equals(hash)) {
            Version v = vigente.get();
            return new ResultadoVersion(v.id(), v.number(), false, v.contentHash());
        }

        long numero = vigente.map(v -> v.number() + 1).orElse(1L);
        Version nueva = new Version(UUID.randomUUID(), s.projectId(), s.resourceType(), s.resourceId(), numero,
                vigenteId, Instant.now(reloj).truncatedTo(ChronoUnit.MICROS), s.actor(), s.reason().strip(),
                REPRESENTACION, estado, hash, s.auditEventId());
        repositorio.guardar(nueva);
        return new ResultadoVersion(nueva.id(), numero, true, hash);
    }

    @Override
    public Pagina<Version> historial(UUID projectId, String resourceType, UUID resourceId, int page, int pageSize) {
        Pagina.validar(page, pageSize);
        validarRecurso(projectId, resourceType, resourceId);
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.HISTORIAL_LEER, projectId);
        List<Version> encontradas = repositorio.listar(projectId, resourceType, resourceId, pageSize + 1, (page - 1) * pageSize);
        boolean hayMas = encontradas.size() > pageSize;
        return new Pagina<>(hayMas ? encontradas.subList(0, pageSize) : encontradas, page, pageSize, hayMas);
    }

    @Override
    public Optional<Version> version(UUID projectId, String resourceType, UUID resourceId, UUID versionId) {
        validarRecurso(projectId, resourceType, resourceId);
        Objects.requireNonNull(versionId, "versionId obligatorio");
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.HISTORIAL_LEER, projectId);
        return repositorio.buscar(projectId, resourceType, resourceId, versionId);
    }

    @Override
    public Comparacion comparar(UUID projectId, String resourceType, UUID resourceId, UUID desdeVersionId, UUID hastaVersionId) {
        validarRecurso(projectId, resourceType, resourceId);
        Objects.requireNonNull(desdeVersionId, "desdeVersionId obligatorio");
        Objects.requireNonNull(hastaVersionId, "hastaVersionId obligatorio");
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.HISTORIAL_LEER, projectId);
        Version desde = repositorio.buscar(projectId, resourceType, resourceId, desdeVersionId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("Versión 'desde' no disponible"));
        Version hasta = repositorio.buscar(projectId, resourceType, resourceId, hastaVersionId)
                .orElseThrow(() -> new RecursoNoEncontradoGobiernoException("Versión 'hasta' no disponible"));
        return new Comparacion(desde, hasta,
                ComparadorVersiones.diferencias(estado(desde.estadoJson()), estado(hasta.estadoJson())));
    }

    /** El estado guardado es un objeto JSON; se lee a mapa para comparar campo por campo. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> estado(String json) {
        return JSON.readValue(json, Map.class);
    }

    private static void validarRecurso(UUID projectId, String resourceType, UUID resourceId) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        Objects.requireNonNull(resourceType, "resourceType obligatorio");
        Objects.requireNonNull(resourceId, "resourceId obligatorio");
        ReglasAuditoria.validarTipoRecurso(resourceType);
    }
}
