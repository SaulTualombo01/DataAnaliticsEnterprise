package org.dataanaliticsent.catalogo.internal.infraestructura;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CatalogoJdbcAdapter implements RepositorioCatalogo {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public CatalogoJdbcAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void registrarFuente(FuenteDatos fuente) {
        comprobarContextoActivo(fuente.projectId(), fuente.createdBy());
        jdbc.sql("""
                INSERT INTO catalogo.fuente(id,project_id,name,type,status,connection_configuration,
                    credential_ref,created_by,created_at,updated_at,revision)
                VALUES(:id,:project,:name,:type,:status,CAST(:configuration AS jsonb),
                    :credential,:actor,:created,:updated,:revision)
                """)
                .paramSource(new MapSqlParameterSource()
                    .addValue("id", fuente.id()).addValue("project", fuente.projectId())
                    .addValue("name", fuente.name()).addValue("type", code(fuente.type()))
                    .addValue("status", code(fuente.status())).addValue("configuration", json.writeValueAsString(fuente.configuration()))
                    .addValue("credential", fuente.credentialRef()).addValue("actor", fuente.createdBy())
                    .addValue("created", Timestamp.from(fuente.createdAt())).addValue("updated", Timestamp.from(fuente.updatedAt()))
                    .addValue("revision", fuente.revision())).update();
        jdbc.sql("""
                INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,occurred_at,outcome)
                VALUES(:id,'USER',:actor,:project,'catalogo.fuente',:source,'m1.sources.create',:instant,'SUCCESS')
                """).param("id", UUID.randomUUID()).param("actor", fuente.createdBy()).param("project", fuente.projectId())
                .param("source", fuente.id()).param("instant", Timestamp.from(fuente.updatedAt())).update();
    }

    @Override
    public Optional<FuenteDatos> buscarFuente(UUID projectId, UUID sourceId) {
        return jdbc.sql("SELECT * FROM catalogo.fuente WHERE project_id=:project AND id=:id")
                .param("project", projectId).param("id", sourceId)
                .query((rs, row) -> new FuenteDatos(uuid(rs,"id"), uuid(rs,"project_id"), rs.getString("name"),
                    FuenteDatos.Tipo.valueOf(rs.getString("type").toUpperCase(Locale.ROOT)),
                    FuenteDatos.Estado.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                    json.readValue(rs.getString("connection_configuration"), new TypeReference<Map<String,String>>() { }),
                    rs.getString("credential_ref"), uuid(rs,"created_by"), instant(rs,"created_at"), instant(rs,"updated_at"),
                    rs.getLong("revision"))).optional();
    }

    @Override
    @Transactional
    public void registrarActivo(EstadoCatalogo estado, UUID actorId, UUID requestId) {
        publicarLote(List.of(estado),actorId,requestId);
    }

    @Override
    @Transactional
    public void registrarDescubrimiento(FuenteDatos esperada, List<String> schemas, List<EstadoCatalogo> estados,
            UUID actorId, UUID requestId, Instant instante) {
        jdbc.sql("SELECT id FROM catalogo.fuente WHERE id=:id AND project_id=:project FOR UPDATE")
                .param("id",esperada.id()).param("project",esperada.projectId()).query(UUID.class).optional()
                .orElseThrow(() -> new IllegalStateException("Fuente no disponible"));
        if (!buscarFuente(esperada.projectId(),esperada.id()).filter(esperada::equals).isPresent()) {
            throw new IllegalStateException("La fuente cambió durante el descubrimiento");
        }
        if (esperada.status()!=FuenteDatos.Estado.ENABLED || estados.stream().anyMatch(e ->
                !e.activo().sourceId().equals(esperada.id()) || !e.activo().projectId().equals(esperada.projectId()))) {
            throw new IllegalArgumentException("Publicación fuera de la fuente habilitada");
        }
        publicarLote(estados,actorId,requestId);
        jdbc.sql("UPDATE catalogo.fuente SET last_discovery_at=:instant WHERE id=:id")
                .param("instant",Timestamp.from(instante)).param("id",esperada.id()).update();
        jdbc.sql("""
                INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,
                    occurred_at,outcome,request_id,safe_details)
                VALUES(:id,'USER',:actor,:project,'catalogo.fuente',:source,'m1.discovery.complete',
                    :instant,'SUCCESS',:request,CAST(:details AS jsonb))
                """).param("id",UUID.randomUUID()).param("actor",actorId).param("project",esperada.projectId())
                .param("source",esperada.id()).param("instant",Timestamp.from(instante)).param("request",requestId)
                .param("details",json.writeValueAsString(Map.of("schemas",schemas,"table_count",estados.size(),"metadata_only",true))).update();
    }

    private void publicarLote(List<EstadoCatalogo> estados, UUID actorId, UUID requestId) {
        // Primero existen todos los activos/campos; luego se resuelven FK incluso cíclicas.
        for (EstadoCatalogo estado : estados) insertarActivoYCampos(estado,actorId);
        for (EstadoCatalogo estado : estados) insertarRestricciones(estado);
        for (EstadoCatalogo estado : estados) registrarHistoria(estado,actorId,requestId);
    }

    private void insertarActivoYCampos(EstadoCatalogo estado, UUID actorId) {
        ActivoDatos a = estado.activo();
        if (a.revision() != 0 || a.status() != ActivoDatos.Estado.ACTIVE) {
            throw new IllegalArgumentException("El alta exige activo nuevo, revisión cero y estado active");
        }
        comprobarContextoActivo(a.projectId(), actorId);
        String sourceStatus = jdbc.sql("SELECT status FROM catalogo.fuente WHERE id=:source AND project_id=:project FOR UPDATE")
                .param("source", a.sourceId()).param("project", a.projectId()).query(String.class)
                .optional().orElseThrow(() -> new IllegalArgumentException("Fuente fuera del proyecto"));
        if (!"enabled".equals(sourceStatus)) throw new IllegalArgumentException("Fuente no habilitada");

        jdbc.sql("""
                INSERT INTO catalogo.activo(id,project_id,source_id,name,kind,origin_namespace,origin_object_path,
                    status,structure_origin,business_description,synonyms,current_metadata_version_id,last_discovery_at,updated_at,revision)
                VALUES(:id,:project,:source,:name,:kind,:namespace,:path,:status,:origin,:description,:synonyms,:version,:discovery,:updated,0)
                """).paramSource(new MapSqlParameterSource()
                    .addValue("id", a.id()).addValue("project", a.projectId()).addValue("source", a.sourceId())
                    .addValue("name", a.name()).addValue("kind", code(a.kind())).addValue("namespace", a.originNamespace())
                    .addValue("path", a.originObjectPath()).addValue("status", code(a.status()))
                    .addValue("origin", code(a.structureOrigin())).addValue("description", a.businessDescription())
                    .addValue("synonyms", a.synonyms().toArray(String[]::new)).addValue("version", a.metadataVersionId())
                    .addValue("discovery", Timestamp.from(a.lastDiscoveryAt())).addValue("updated", Timestamp.from(a.updatedAt()))).update();

        for (CampoDatos c : estado.campos()) {
            jdbc.sql("""
                    INSERT INTO catalogo.campo(id,project_id,asset_id,name,path,position,native_type,logical_type,nullable,business_description,synonyms,status)
                    VALUES(:id,:project,:asset,:name,:path,:position,:native,:logical,:nullable,:description,:synonyms,:status)
                    """).paramSource(new MapSqlParameterSource()
                        .addValue("id", c.id()).addValue("project", c.projectId()).addValue("asset", c.assetId())
                        .addValue("name", c.name()).addValue("path", c.path()).addValue("position", c.position())
                        .addValue("native", c.nativeType()).addValue("logical", code(c.logicalType())).addValue("nullable", c.nullable())
                        .addValue("description", c.businessDescription()).addValue("synonyms", c.synonyms().toArray(String[]::new))
                        .addValue("status", code(c.status()))).update();
        }
    }

    private void insertarRestricciones(EstadoCatalogo estado) {
        for (RestriccionDatos c : estado.restricciones()) {
            jdbc.sql("""
                    INSERT INTO catalogo.restriccion(id,asset_id,project_id,name,kind,target_asset_id,target_locator,resolution_status)
                    VALUES(:id,:asset,:project,:name,:kind,:target,:locator,:resolution)
                    """).param("id",c.id()).param("asset",c.assetId()).param("project",c.projectId()).param("name",c.name())
                    .param("kind",c.kind().name()).param("target",c.targetAssetId())
                    .param("locator",c.targetLocator()==null ? null : json.writeValueAsString(c.targetLocator()))
                    .param("resolution",code(c.resolution())).update();
            for (RestriccionDatos.Miembro m : c.members()) {
                jdbc.sql("""
                        INSERT INTO catalogo.miembro_restriccion(constraint_id,asset_id,project_id,position,source_field_id,target_field_name)
                        VALUES(:constraint,:asset,:project,:position,:field,:target)
                        """).param("constraint",c.id()).param("asset",c.assetId()).param("project",c.projectId())
                        .param("position",m.position()).param("field",m.sourceFieldId()).param("target",m.targetFieldName()).update();
            }
        }
    }

    private void registrarHistoria(EstadoCatalogo estado, UUID actorId, UUID requestId) {
        ActivoDatos a = estado.activo();
        UUID eventId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,occurred_at,outcome,request_id)
                VALUES(:id,'USER',:actor,:project,'catalogo.activo',:asset,'m1.assets.register',:instant,'SUCCESS',:request)
                """).param("id", eventId).param("actor", actorId).param("project", a.projectId())
                .param("asset", a.id()).param("instant", Timestamp.from(a.updatedAt())).param("request", requestId).update();
        String payload = json.writeValueAsString(estado);
        jdbc.sql("""
                INSERT INTO versionado.version_recurso(id,project_id,resource_type,resource_id,number,created_at,
                    actor_type,actor_id,reason,representation_version,state_payload,content_hash,audit_event_id)
                VALUES(:id,:project,'catalogo.activo',:asset,1,:instant,'USER',:actor,'Registro inicial de metadatos',1,CAST(:payload AS jsonb),:hash,:event)
                """).param("id", a.metadataVersionId()).param("project", a.projectId()).param("asset", a.id())
                .param("instant", Timestamp.from(a.updatedAt())).param("actor", actorId).param("payload", payload)
                .param("hash", hash(payload)).param("event", eventId).update();
    }

    @Override
    public Optional<ActivoDatos> buscarActivo(UUID projectId, UUID assetId) {
        return jdbc.sql("SELECT * FROM catalogo.activo WHERE project_id=:project AND id=:asset")
                .param("project", projectId).param("asset", assetId).query((rs, row) -> new ActivoDatos(
                    uuid(rs,"id"), uuid(rs,"project_id"), uuid(rs,"source_id"), rs.getString("name"),
                    ActivoDatos.Tipo.valueOf(rs.getString("kind").toUpperCase(Locale.ROOT)), rs.getString("origin_namespace"),
                    rs.getString("origin_object_path"), ActivoDatos.Estado.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                    ActivoDatos.Origen.valueOf(rs.getString("structure_origin").toUpperCase(Locale.ROOT)),
                    rs.getString("business_description"), strings(rs,"synonyms"), uuid(rs,"current_metadata_version_id"),
                    instant(rs,"last_discovery_at"), instant(rs,"updated_at"), rs.getLong("revision"))).optional();
    }

    @Override
    public List<CampoDatos> listarCampos(UUID projectId, UUID assetId) {
        return jdbc.sql("SELECT * FROM catalogo.campo WHERE project_id=:project AND asset_id=:asset ORDER BY position,id")
                .param("project", projectId).param("asset", assetId).query((rs, row) -> new CampoDatos(
                    uuid(rs,"id"), uuid(rs,"project_id"), uuid(rs,"asset_id"), rs.getString("name"), rs.getString("path"),
                    rs.getInt("position"), rs.getString("native_type"), CampoDatos.TipoLogico.valueOf(rs.getString("logical_type").toUpperCase(Locale.ROOT)),
                    rs.getObject("nullable", Boolean.class), rs.getString("business_description"), strings(rs,"synonyms"),
                    CampoDatos.Estado.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)))).list();
    }

    @Override
    public List<RestriccionDatos> listarRestricciones(UUID projectId, UUID assetId) {
        var miembros = jdbc.sql("""
                SELECT constraint_id,position,source_field_id,target_field_name FROM catalogo.miembro_restriccion
                WHERE project_id=:project AND asset_id=:asset ORDER BY constraint_id,position
                """).param("project",projectId).param("asset",assetId).query((rs,row) -> Map.entry(uuid(rs,"constraint_id"),
                    new RestriccionDatos.Miembro(rs.getInt("position"),uuid(rs,"source_field_id"),rs.getString("target_field_name"))))
                .list().stream().collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
                    java.util.stream.Collectors.mapping(Map.Entry::getValue,java.util.stream.Collectors.toList())));
        return jdbc.sql("""
                SELECT * FROM catalogo.restriccion WHERE project_id=:project AND asset_id=:asset ORDER BY kind,name,id
                """).param("project",projectId).param("asset",assetId).query((rs,row) -> new RestriccionDatos(
                    uuid(rs,"id"),projectId,assetId,rs.getString("name"),RestriccionDatos.Tipo.valueOf(rs.getString("kind")),
                    uuid(rs,"target_asset_id"),rs.getString("target_locator")==null ? null :
                        json.readValue(rs.getString("target_locator"),LocalizadorTabla.class),
                    RestriccionDatos.Resolucion.valueOf(rs.getString("resolution_status").toUpperCase(Locale.ROOT)),
                    miembros.getOrDefault(uuid(rs,"id"),List.of()))).list();
    }

    @Override
    public UUID organizacionDelProyecto(UUID projectId) {
        return jdbc.sql("SELECT organization_id FROM contexto.proyecto WHERE id=:id")
                .param("id",projectId).query(UUID.class).single();
    }

    @Override
    public List<String> etiquetasActivo(UUID projectId,UUID assetId) {
        return jdbc.sql("""
                SELECT e.name FROM catalogo.activo_etiqueta a
                JOIN catalogo.etiqueta e ON e.id=a.tag_id AND e.project_id=a.project_id
                WHERE a.project_id=:project AND a.asset_id=:asset AND e.status='active'
                ORDER BY e.normalized_name,e.id
                """).param("project",projectId).param("asset",assetId).query(String.class).list();
    }

    @Override
    public boolean existeVersion(UUID projectId,UUID assetId,UUID versionId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM versionado.version_recurso WHERE project_id=:project
                    AND resource_id=:asset AND id=:version AND resource_type='catalogo.activo' AND representation_version=1)
                """).param("project",projectId).param("asset",assetId).param("version",versionId).query(Boolean.class).single();
    }

    @Override
    public List<CampoDatos> paginaCamposVersion(UUID projectId,UUID assetId,UUID versionId,long offset,int limit) {
        return jdbc.sql("""
                SELECT campo::text FROM versionado.version_recurso v
                CROSS JOIN LATERAL jsonb_array_elements(COALESCE(v.state_payload->'campos','null'::jsonb)) AS campos(campo)
                WHERE v.project_id=:project AND v.resource_id=:asset AND v.id=:version
                    AND v.resource_type='catalogo.activo' AND v.representation_version=1
                ORDER BY (campo->>'position')::integer,(campo->>'id')::uuid LIMIT :limit OFFSET :offset
                """).param("project",projectId).param("asset",assetId).param("version",versionId)
                .param("limit",limit).param("offset",offset)
                .query((rs,row)->json.readValue(rs.getString(1),CampoDatos.class)).list();
    }

    @Override
    public Optional<EstadoCatalogo> consultarVersion(UUID projectId, UUID assetId, UUID metadataVersionId) {
        return jdbc.sql("""
                SELECT state_payload::text FROM versionado.version_recurso
                WHERE project_id=:project AND resource_id=:asset AND id=:version
                    AND resource_type='catalogo.activo' AND representation_version=1
                """).param("project", projectId).param("asset", assetId).param("version", metadataVersionId)
                .query((rs, row) -> json.readValue(rs.getString(1), EstadoCatalogo.class)).optional();
    }

    private void comprobarContextoActivo(UUID projectId, UUID actorId) {
        boolean valid = jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM contexto.proyecto p JOIN contexto.organizacion o ON o.id=p.organization_id
                    CROSS JOIN identidad.usuario u WHERE p.id=:project AND u.id=:actor
                    AND p.status='active' AND o.status='active' AND u.status='active')
                """).param("project", projectId).param("actor", actorId).query(Boolean.class).single();
        if (!valid) throw new IllegalArgumentException("Actor o contexto no activo");
    }

    private static String code(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static UUID uuid(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    private static Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
    private static List<String> strings(ResultSet rs, String column) throws SQLException { return Arrays.asList((String[]) rs.getArray(column).getArray()); }
    private static String hash(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
