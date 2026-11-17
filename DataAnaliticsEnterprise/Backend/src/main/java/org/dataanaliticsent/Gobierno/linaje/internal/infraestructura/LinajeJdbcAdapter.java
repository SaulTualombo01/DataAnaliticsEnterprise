package org.dataanaliticsent.Gobierno.linaje.internal.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.linaje.ConflictoLinajeException;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.EstadoRelacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.TipoRelacion;
import org.dataanaliticsent.Gobierno.linaje.internal.dominio.RepositorioLinaje;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class LinajeJdbcAdapter implements RepositorioLinaje {
    private final JdbcClient jdbc;

    LinajeJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void bloquearProyecto(UUID projectId) {
        // Bloqueo transaccional por proyecto: dos altas simultáneas no pueden cerrar un ciclo entre ambas.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('gobierno.linaje:' || :project, 0))")
                .param("project", projectId.toString()).query(Object.class).optional();
    }

    @Override
    public Optional<Relacion> buscar(UUID projectId, UUID relationId) {
        return jdbc.sql("SELECT * FROM gobierno.relacion_linaje WHERE project_id=:project AND id=:id")
                .param("project", projectId).param("id", relationId).query((rs, fila) -> relacion(rs)).optional();
    }

    @Override
    public Optional<Relacion> vigenteIgual(UUID projectId, UUID originAssetId, UUID targetAssetId, TipoRelacion tipo) {
        return jdbc.sql("""
                SELECT * FROM gobierno.relacion_linaje WHERE project_id=:project AND origin_asset_id=:origin
                    AND target_asset_id=:target AND relation_type=:type AND status='ACTIVE'
                """).param("project", projectId).param("origin", originAssetId).param("target", targetAssetId)
                .param("type", tipo.name()).query((rs, fila) -> relacion(rs)).optional();
    }

    @Override
    public void insertar(Relacion r) {
        jdbc.sql("""
                INSERT INTO gobierno.relacion_linaje(id,project_id,origin_asset_id,target_asset_id,relation_type,description,
                    status,current_version_id,created_at,created_by)
                VALUES(:id,:project,:origin,:target,:type,:description,'ACTIVE',:version,:at,:by)
                """).param("id", r.id()).param("project", r.projectId()).param("origin", r.originAssetId())
                .param("target", r.targetAssetId()).param("type", r.tipo().name()).param("description", r.descripcion())
                .param("version", r.currentVersionId()).param("at", Timestamp.from(r.createdAt()))
                .param("by", r.createdBy()).update();
    }

    @Override
    public void retirar(Relacion r) {
        int filas = jdbc.sql("""
                UPDATE gobierno.relacion_linaje SET status='RETIRED', current_version_id=:version, retired_at=:at, retired_by=:by
                WHERE id=:id AND project_id=:project AND status='ACTIVE'
                """).param("version", r.currentVersionId()).param("at", Timestamp.from(r.retiredAt()))
                .param("by", r.retiredBy()).param("id", r.id()).param("project", r.projectId()).update();
        if (filas != 1) throw new ConflictoLinajeException("La relación ya no estaba vigente");
    }

    @Override
    public List<Relacion> entrantes(UUID projectId, Collection<UUID> activos) {
        return vecinas("target_asset_id", projectId, activos);
    }

    @Override
    public List<Relacion> salientes(UUID projectId, Collection<UUID> activos) {
        return vecinas("origin_asset_id", projectId, activos);
    }

    /** columna es siempre una de dos constantes internas, nunca un valor del cliente. */
    private List<Relacion> vecinas(String columna, UUID projectId, Collection<UUID> activos) {
        if (activos.isEmpty()) return List.of();
        return jdbc.sql("SELECT * FROM gobierno.relacion_linaje WHERE project_id=:project AND status='ACTIVE' AND "
                        + columna + " IN (:activos) ORDER BY created_at, id")
                .param("project", projectId).param("activos", List.copyOf(activos))
                .query((rs, fila) -> relacion(rs)).list();
    }

    private static Relacion relacion(ResultSet rs) throws SQLException {
        OffsetDateTime retirada = rs.getObject("retired_at", OffsetDateTime.class);
        return new Relacion(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("origin_asset_id", UUID.class), rs.getObject("target_asset_id", UUID.class),
                TipoRelacion.valueOf(rs.getString("relation_type")), rs.getString("description"),
                EstadoRelacion.valueOf(rs.getString("status")), rs.getObject("current_version_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getObject("created_by", UUID.class),
                retirada == null ? null : retirada.toInstant(), rs.getObject("retired_by", UUID.class));
    }
}
