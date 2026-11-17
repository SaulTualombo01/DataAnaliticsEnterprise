package org.dataanaliticsent.Gobierno.cumplimiento.internal.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.EstadoCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoPolitica;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.dominio.RepositorioEvaluaciones;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class EvaluacionesJdbcAdapter implements RepositorioEvaluaciones {
    /** Mapper propio: el formato guardado no depende de la configuración JSON de la web. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    EvaluacionesJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void guardar(Evaluacion e) {
        jdbc.sql("""
                INSERT INTO gobierno.evaluacion_cumplimiento(id,project_id,asset_id,overall_status,evaluated_at,
                    evaluated_by,profile_version_id,results,audit_event_id)
                VALUES(:id,:project,:asset,:status,:at,:by,:profile,CAST(:results AS jsonb),:event)
                """).param("id", e.id()).param("project", e.projectId()).param("asset", e.assetId())
                .param("status", e.estado().name()).param("at", Timestamp.from(e.evaluatedAt()))
                .param("by", e.evaluatedBy()).param("profile", e.profileVersionId())
                .param("results", aJson(e.politicas())).param("event", e.auditEventId()).update();
    }

    static String aJson(List<ResultadoPolitica> politicas) { return JSON.writeValueAsString(politicas); }

    static List<ResultadoPolitica> desdeJson(String json) { return List.of(JSON.readValue(json, ResultadoPolitica[].class)); }

    @Override
    public List<Evaluacion> listar(UUID projectId, UUID assetId, int limite, int desplazamiento) {
        return jdbc.sql("""
                SELECT * FROM gobierno.evaluacion_cumplimiento WHERE project_id=:project AND asset_id=:asset
                ORDER BY evaluated_at DESC, id DESC LIMIT :limite OFFSET :desplazamiento
                """).param("project", projectId).param("asset", assetId).param("limite", limite)
                .param("desplazamiento", desplazamiento).query((rs, fila) -> evaluacion(rs)).list();
    }

    private static Evaluacion evaluacion(ResultSet rs) throws SQLException {
        return new Evaluacion(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("asset_id", UUID.class), EstadoCumplimiento.valueOf(rs.getString("overall_status")),
                rs.getObject("evaluated_at", OffsetDateTime.class).toInstant(), rs.getObject("evaluated_by", UUID.class),
                rs.getObject("profile_version_id", UUID.class), desdeJson(rs.getString("results")),
                rs.getObject("audit_event_id", UUID.class));
    }
}
