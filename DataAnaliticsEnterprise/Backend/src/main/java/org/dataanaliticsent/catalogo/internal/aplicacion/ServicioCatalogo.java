package org.dataanaliticsent.catalogo.internal.aplicacion;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.catalogo.CatalogoApi;
import org.dataanaliticsent.catalogo.ConflictoCatalogoException;
import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServicioCatalogo implements CatalogoApi {
    private final IdentidadActualApi identidad;
    private final AutorizacionApi autorizacion;
    private final RepositorioCatalogo datos;
    private final Clock reloj;

    public ServicioCatalogo(IdentidadActualApi identidad, AutorizacionApi autorizacion,
            RepositorioCatalogo datos, Clock reloj) {
        this.identidad = Objects.requireNonNull(identidad);
        this.autorizacion = Objects.requireNonNull(autorizacion);
        this.datos = Objects.requireNonNull(datos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    @Transactional
    public RegistroActivo registrarActivo(AltaActivo solicitud) {
        UUID actor = identidad.usuarioId();
        Objects.requireNonNull(solicitud);
        autorizacion.exigir(actor, SolicitudAcceso.recurso("m1.discovery.execute", solicitud.projectId(),
                SolicitudAcceso.TipoRecurso.SOURCE, solicitud.sourceId()));
        FuenteDatos fuente = datos.buscarFuente(solicitud.projectId(), solicitud.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("Fuente no disponible en el proyecto"));
        if (fuente.status() != FuenteDatos.Estado.ENABLED) {
            throw new IllegalArgumentException("Fuente no habilitada para registrar metadatos");
        }
        TipoActivo esperado = switch (fuente.type()) {
            case POSTGRESQL, MYSQL -> TipoActivo.TABLE;
            case CSV -> TipoActivo.CSV_DATASET;
            case JSON -> TipoActivo.JSON_COLLECTION;
            case REST -> TipoActivo.API_COLLECTION;
        };
        if (solicitud.kind() != esperado) throw new IllegalArgumentException("Tipo de activo incompatible con la fuente");

        UUID id = UUID.randomUUID(), version = UUID.randomUUID(), requestId = UUID.randomUUID();
        var ahora = reloj.instant();
        var activo = new ActivoDatos(id, solicitud.projectId(), solicitud.sourceId(), solicitud.name(),
                ActivoDatos.Tipo.valueOf(solicitud.kind().name()), solicitud.originNamespace(), solicitud.originObjectPath(),
                ActivoDatos.Estado.ACTIVE, ActivoDatos.Origen.valueOf(solicitud.structureOrigin().name()),
                null, List.of(), version, ahora, ahora, 0);
        var campos = solicitud.fields().stream().map(c -> new CampoDatos(UUID.randomUUID(), solicitud.projectId(),
                id, c.name(), c.path(), c.position(), c.nativeType(), CampoDatos.TipoLogico.valueOf(c.logicalType().name()),
                c.nullable(), null, List.of(), CampoDatos.Estado.ACTIVE)).toList();
        var estado = new EstadoCatalogo(activo, campos);
        try {
            datos.registrarActivo(estado, actor, requestId);
        } catch (DataIntegrityViolationException conflicto) {
            throw new ConflictoCatalogoException();
        }
        return new RegistroActivo(id, solicitud.projectId(), solicitud.sourceId(), version, ahora, requestId);
    }

    @Override
    public Optional<DetalleActivo> consultarActivo(UUID proyectoId, UUID activoId) {
        UUID actor = identidad.usuarioId();
        autorizacion.exigir(actor, SolicitudAcceso.recurso("m1.assets.read", proyectoId,
                SolicitudAcceso.TipoRecurso.ASSET, activoId));
        return datos.buscarActivo(proyectoId, activoId).map(a -> new DetalleActivo(a.id(), a.projectId(), a.sourceId(),
                a.name(), TipoActivo.valueOf(a.kind().name()), a.originNamespace(), a.originObjectPath(),
                EstadoActivo.valueOf(a.status().name()), OrigenEstructura.valueOf(a.structureOrigin().name()),
                a.businessDescription(), a.synonyms(), a.metadataVersionId(), a.lastDiscoveryAt(), a.updatedAt(), a.revision(),
                datos.organizacionDelProyecto(proyectoId), datos.etiquetasActivo(proyectoId,activoId)));
    }

    @Override
    public List<CampoCatalogo> listarCampos(UUID proyectoId, UUID activoId) {
        UUID actor = identidad.usuarioId();
        autorizacion.exigir(actor, SolicitudAcceso.recurso("m1.dictionary.read", proyectoId,
                SolicitudAcceso.TipoRecurso.ASSET, activoId));
        return datos.listarCampos(proyectoId, activoId).stream().map(c -> new CampoCatalogo(c.id(), c.projectId(), c.assetId(),
                c.name(), c.path(), c.position(), c.nativeType(), TipoLogico.valueOf(c.logicalType().name()), c.nullable(),
                c.businessDescription(), c.synonyms(), EstadoCampo.valueOf(c.status().name()))).toList();
    }
    @Override
    public Optional<PaginaCampos> consultarCamposVersion(UUID proyectoId, UUID activoId, UUID versionId, int page, int pageSize) {
        UUID actor=identidad.usuarioId();
        autorizacion.exigir(actor,SolicitudAcceso.recurso("m1.dictionary.read",proyectoId,
                SolicitudAcceso.TipoRecurso.ASSET,activoId));
        Objects.requireNonNull(versionId);
        if(page<1 || pageSize<1 || pageSize>100) throw new IllegalArgumentException("Paginación inválida");
        var activo=datos.buscarActivo(proyectoId,activoId);
        if(activo.isEmpty()) return Optional.empty();
        if(!versionId.equals(activo.get().metadataVersionId())) {
            autorizacion.exigir(actor,SolicitudAcceso.recurso("m1.metadata.history.read",proyectoId,
                    SolicitudAcceso.TipoRecurso.ASSET,activoId));
        }
        if(!datos.existeVersion(proyectoId,activoId,versionId)) return Optional.empty();
        var filas=datos.paginaCamposVersion(proyectoId,activoId,versionId,(long)(page-1)*pageSize,pageSize+1);
        boolean hasNext=filas.size()>pageSize;
        var campos=filas.stream().limit(pageSize).map(c -> new CampoCatalogo(c.id(),c.projectId(),c.assetId(),
                c.name(),c.path(),c.position(),c.nativeType(),TipoLogico.valueOf(c.logicalType().name()),c.nullable(),
                c.businessDescription(),c.synonyms(),EstadoCampo.valueOf(c.status().name()))).toList();
        return Optional.of(new PaginaCampos(datos.organizacionDelProyecto(proyectoId),proyectoId,activoId,versionId,
                page,pageSize,campos,hasNext));
    }

    @Override
    public List<RestriccionCatalogo> listarRestricciones(UUID proyectoId, UUID activoId) {
        UUID actor = identidad.usuarioId();
        autorizacion.exigir(actor, SolicitudAcceso.recurso("m1.dictionary.read", proyectoId,
                SolicitudAcceso.TipoRecurso.ASSET, activoId));
        return datos.listarRestricciones(proyectoId,activoId).stream().map(c -> new RestriccionCatalogo(
                c.id(),c.projectId(),c.assetId(),c.name(),TipoRestriccion.valueOf(c.kind().name()),OrigenEstructura.DECLARED,
                c.targetAssetId(),c.targetLocator()==null ? null : new ReferenciaTabla(c.targetLocator().namespace(),c.targetLocator().name()),
                ResolucionReferencia.valueOf(c.resolution().name()),c.members().stream().map(m ->
                    new MiembroRestriccion(m.position(),m.sourceFieldId(),m.targetFieldName())).toList())).toList();
    }

}
