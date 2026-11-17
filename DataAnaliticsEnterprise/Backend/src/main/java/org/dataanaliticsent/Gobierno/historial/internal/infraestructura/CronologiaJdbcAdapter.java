package org.dataanaliticsent.Gobierno.historial.internal.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.TipoActor;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.EntradaCronologia;
import org.dataanaliticsent.Gobierno.historial.internal.dominio.RepositorioCronologia;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class CronologiaJdbcAdapter implements RepositorioCronologia {
    private final JdbcClient jdbc;

    CronologiaJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public List<EntradaCronologia> buscar(UUID projectId, UUID assetId, int limite, int desplazamiento) {
        return jdbc.sql("""
                SELECT e.*, v.id AS version_id
                FROM gobierno.evento_auditoria e
                LEFT JOIN gobierno.version_recurso v ON v.audit_event_id = e.id
                WHERE e.project_id = :project
                  AND (e.resource_id = :asset
                       OR e.safe_details->>'asset_id' = :assetTexto
                       OR e.safe_details->>'origin_asset_id' = :assetTexto
                       OR e.safe_details->>'target_asset_id' = :assetTexto)
                ORDER BY e.occurred_at DESC, e.id DESC
                LIMIT :limite OFFSET :desplazamiento
                """).param("project", projectId).param("asset", assetId).param("assetTexto", assetId.toString())
                .param("limite", limite).param("desplazamiento", desplazamiento)
                .query((rs, fila) -> new EntradaCronologia(evento(rs), rs.getObject("version_id", UUID.class))).list();
    }

    private static Evento evento(ResultSet rs) throws SQLException {
        return new Evento(rs.getObject("id", UUID.class),
                new Actor(TipoActor.valueOf(rs.getString("actor_type")), rs.getObject("actor_id", UUID.class)),
                rs.getObject("organization_id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), rs.getString("action"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), Resultado.valueOf(rs.getString("outcome")),
                rs.getObject("request_id", UUID.class), rs.getString("safe_details"));
    }
}
