package org.dataanaliticsent.Gobierno.fichas.internal.infraestructura;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.fichas.ConflictoFichaException;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.fichas.internal.dominio.RepositorioFichas;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class FichasJdbcAdapter implements RepositorioFichas {
    private final JdbcClient jdbc;

    FichasJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Ficha> buscar(UUID projectId, UUID assetId) {
        return jdbc.sql("SELECT * FROM gobierno.ficha_activo WHERE project_id=:project AND asset_id=:asset")
                .param("project", projectId).param("asset", assetId)
                .query((rs, fila) -> new Ficha(rs.getObject("project_id", UUID.class), rs.getObject("asset_id", UUID.class),
                        rs.getObject("owner_id", UUID.class),
                        rs.getString("classification") == null ? null : Clasificacion.valueOf(rs.getString("classification")),
                        rs.getLong("revision"), rs.getObject("current_version_id", UUID.class),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getObject("updated_by", UUID.class)))
                .optional();
    }

    @Override
    public void guardar(Ficha f, Long revisionLeida) {
        String clasificacion = f.classification() == null ? null : f.classification().name();
        if (revisionLeida == null) {
            try {
                jdbc.sql("""
                        INSERT INTO gobierno.ficha_activo(project_id,asset_id,owner_id,classification,revision,
                            current_version_id,updated_at,updated_by)
                        VALUES(:project,:asset,:owner,:classification,:revision,:version,:at,:by)
                        """).param("project", f.projectId()).param("asset", f.assetId()).param("owner", f.ownerId())
                        .param("classification", clasificacion).param("revision", f.revision())
                        .param("version", f.currentVersionId()).param("at", Timestamp.from(f.updatedAt()))
                        .param("by", f.updatedBy()).update();
            } catch (DuplicateKeyException e) {
                throw new ConflictoFichaException("Otra operación creó la ficha; vuelva a consultarla");
            }
            return;
        }
        int filas = jdbc.sql("""
                UPDATE gobierno.ficha_activo SET owner_id=:owner, classification=:classification, revision=:revision,
                    current_version_id=:version, updated_at=:at, updated_by=:by
                WHERE project_id=:project AND asset_id=:asset AND revision=:leida
                """).param("owner", f.ownerId()).param("classification", clasificacion).param("revision", f.revision())
                .param("version", f.currentVersionId()).param("at", Timestamp.from(f.updatedAt()))
                .param("by", f.updatedBy()).param("project", f.projectId()).param("asset", f.assetId())
                .param("leida", revisionLeida).update();
        if (filas != 1) throw new ConflictoFichaException("La ficha cambió mientras se guardaba; vuelva a consultarla");
    }
}
