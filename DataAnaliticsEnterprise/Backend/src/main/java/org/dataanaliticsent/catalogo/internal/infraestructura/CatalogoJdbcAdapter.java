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
