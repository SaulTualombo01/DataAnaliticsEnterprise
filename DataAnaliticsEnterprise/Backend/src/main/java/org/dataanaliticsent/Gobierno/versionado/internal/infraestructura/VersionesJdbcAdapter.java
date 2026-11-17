package org.dataanaliticsent.Gobierno.versionado.internal.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.TipoActor;
import org.dataanaliticsent.Gobierno.versionado.ConflictoVersionException;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Version;
import org.dataanaliticsent.Gobierno.versionado.internal.dominio.RepositorioVersiones;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class VersionesJdbcAdapter implements RepositorioVersiones {
    private final JdbcClient jdbc;

    VersionesJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Version> ultima(UUID projectId, String resourceType, UUID resourceId) {
        return jdbc.sql("""
                SELECT * FROM gobierno.version_recurso
                WHERE project_id=:project AND resource_type=:type AND resource_id=:resource
                ORDER BY number DESC LIMIT 1
                """).param("project", projectId).param("type", resourceType).param("resource", resourceId)
                .query((rs, fila) -> version(rs)).optional();
    }

    @Override
    public void guardar(Version v) {
        try {
            jdbc.sql("""
                    INSERT INTO gobierno.version_recurso(id,project_id,resource_type,resource_id,number,previous_version_id,
                        created_at,actor_type,actor_id,reason,representation_version,state_payload,content_hash,audit_event_id)
                    VALUES(:id,:project,:type,:resource,:number,:previous,:at,:actorType,:actorId,:reason,:representation,
                        CAST(:payload AS jsonb),:hash,:event)
                    """)
                    .param("id", v.id()).param("project", v.projectId()).param("type", v.resourceType())
                    .param("resource", v.resourceId()).param("number", v.number())
                    .param("previous", v.previousVersionId()).param("at", Timestamp.from(v.createdAt()))
                    .param("actorType", v.actor().tipo().name()).param("actorId", v.actor().id())
                    .param("reason", v.reason()).param("representation", v.representationVersion())
                    .param("payload", v.estadoJson()).param("hash", v.contentHash()).param("event", v.auditEventId())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ConflictoVersionException("Otra operación creó la misma versión; vuelva a consultar el recurso");
        }
    }

    @Override
    public List<Version> listar(UUID projectId, String resourceType, UUID resourceId, int limite, int desplazamiento) {
        return jdbc.sql("""
                SELECT * FROM gobierno.version_recurso
                WHERE project_id=:project AND resource_type=:type AND resource_id=:resource
                ORDER BY number DESC LIMIT :limite OFFSET :desplazamiento
                """).param("project", projectId).param("type", resourceType).param("resource", resourceId)
                .param("limite", limite).param("desplazamiento", desplazamiento)
                .query((rs, fila) -> version(rs)).list();
    }

    @Override
    public Optional<Version> buscar(UUID projectId, String resourceType, UUID resourceId, UUID versionId) {
        return jdbc.sql("""
                SELECT * FROM gobierno.version_recurso
                WHERE id=:id AND project_id=:project AND resource_type=:type AND resource_id=:resource
                """).param("id", versionId).param("project", projectId).param("type", resourceType)
                .param("resource", resourceId).query((rs, fila) -> version(rs)).optional();
    }

    private static Version version(ResultSet rs) throws SQLException {
        return new Version(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), rs.getLong("number"),
                rs.getObject("previous_version_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                new Actor(TipoActor.valueOf(rs.getString("actor_type")), rs.getObject("actor_id", UUID.class)),
                rs.getString("reason"), rs.getInt("representation_version"), rs.getString("state_payload"),
                rs.getString("content_hash"), rs.getObject("audit_event_id", UUID.class));
    }
}
