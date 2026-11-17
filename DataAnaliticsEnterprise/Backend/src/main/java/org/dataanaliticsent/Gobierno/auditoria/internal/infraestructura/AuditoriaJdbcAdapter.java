package org.dataanaliticsent.Gobierno.auditoria.internal.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Filtro;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.TipoActor;
import org.dataanaliticsent.Gobierno.auditoria.internal.dominio.RepositorioAuditoria;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class AuditoriaJdbcAdapter implements RepositorioAuditoria {
    private final JdbcClient jdbc;

    AuditoriaJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void guardar(Evento e) {
        jdbc.sql("""
                INSERT INTO gobierno.evento_auditoria(id,actor_type,actor_id,organization_id,project_id,resource_type,
                    resource_id,action,occurred_at,outcome,request_id,safe_details)
                VALUES(:id,:actorType,:actorId,:org,:project,:resourceType,:resourceId,:action,:at,:outcome,:request,
                    CAST(:details AS jsonb))
                """)
                .param("id", e.id()).param("actorType", e.actor().tipo().name()).param("actorId", e.actor().id())
                .param("org", e.organizationId()).param("project", e.projectId())
                .param("resourceType", e.resourceType()).param("resourceId", e.resourceId())
                .param("action", e.action()).param("at", Timestamp.from(e.occurredAt()))
                .param("outcome", e.outcome().name()).param("request", e.requestId())
                .param("details", e.safeDetailsJson()).update();
    }

    @Override
    public List<Evento> buscar(Filtro f, int limite, int desplazamiento) {
        // Solo columnas fijas; los valores del filtro van siempre como parámetros.
        StringBuilder sql = new StringBuilder("SELECT * FROM gobierno.evento_auditoria WHERE project_id=:project");
        if (f.actorId() != null) sql.append(" AND actor_id=:actor");
        if (f.action() != null) sql.append(" AND action=:action");
        if (f.resourceType() != null) sql.append(" AND resource_type=:resourceType");
        if (f.resourceId() != null) sql.append(" AND resource_id=:resourceId");
        if (f.outcome() != null) sql.append(" AND outcome=:outcome");
        if (f.desde() != null) sql.append(" AND occurred_at>=:desde");
        if (f.hasta() != null) sql.append(" AND occurred_at<:hasta");
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limite OFFSET :desplazamiento");

        JdbcClient.StatementSpec consulta = jdbc.sql(sql.toString())
                .param("project", f.projectId()).param("limite", limite).param("desplazamiento", desplazamiento);
        if (f.actorId() != null) consulta = consulta.param("actor", f.actorId());
        if (f.action() != null) consulta = consulta.param("action", f.action());
        if (f.resourceType() != null) consulta = consulta.param("resourceType", f.resourceType());
        if (f.resourceId() != null) consulta = consulta.param("resourceId", f.resourceId());
        if (f.outcome() != null) consulta = consulta.param("outcome", f.outcome().name());
        if (f.desde() != null) consulta = consulta.param("desde", Timestamp.from(f.desde()));
        if (f.hasta() != null) consulta = consulta.param("hasta", Timestamp.from(f.hasta()));
        return consulta.query((rs, fila) -> evento(rs)).list();
    }

    private static Evento evento(ResultSet rs) throws SQLException {
        TipoActor tipo = TipoActor.valueOf(rs.getString("actor_type"));
        return new Evento(rs.getObject("id", UUID.class), new Actor(tipo, rs.getObject("actor_id", UUID.class)),
                rs.getObject("organization_id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), rs.getString("action"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), Resultado.valueOf(rs.getString("outcome")),
                rs.getObject("request_id", UUID.class), rs.getString("safe_details"));
    }
}
