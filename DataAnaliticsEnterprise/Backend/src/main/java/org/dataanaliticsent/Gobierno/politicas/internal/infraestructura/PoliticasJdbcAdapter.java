package org.dataanaliticsent.Gobierno.politicas.internal.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.politicas.ConflictoPoliticaException;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.FiltroPoliticas;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.internal.dominio.RepositorioPoliticas;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class PoliticasJdbcAdapter implements RepositorioPoliticas {
    /** Activos vigentes de cada política, como arreglo ordenado. */
    private static final String SELECT = """
            SELECT p.*, COALESCE((SELECT array_agg(a.asset_id ORDER BY a.asset_id) FROM gobierno.politica_activo a
                WHERE a.policy_id=p.id AND a.active), '{}'::uuid[]) AS asset_ids
            FROM gobierno.politica p
            """;

    private final JdbcClient jdbc;

    PoliticasJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Politica> buscar(UUID projectId, UUID policyId) {
        return jdbc.sql(SELECT + " WHERE p.project_id=:project AND p.id=:id")
                .param("project", projectId).param("id", policyId).query((rs, fila) -> politica(rs)).optional();
    }

    @Override
    public void insertar(Politica p) {
        try {
            jdbc.sql("""
                    INSERT INTO gobierno.politica(id,project_id,name,description,category,owner_id,status,revision,
                        current_version_id,created_at,created_by,updated_at)
                    VALUES(:id,:project,:name,:description,:category,:owner,:status,:revision,:version,:created,:createdBy,:updated)
                    """)
                    .param("id", p.id()).param("project", p.projectId()).param("name", p.name())
                    .param("description", p.description()).param("category", p.category().name())
                    .param("owner", p.ownerId()).param("status", p.status().name()).param("revision", p.revision())
                    .param("version", p.currentVersionId()).param("created", Timestamp.from(p.createdAt()))
                    .param("createdBy", p.createdBy()).param("updated", Timestamp.from(p.updatedAt())).update();
        } catch (DuplicateKeyException e) {
            throw nombreRepetido();
        }
    }

    @Override
    public void actualizar(Politica p, long revisionLeida) {
        int filas;
        try {
            filas = jdbc.sql("""
                    UPDATE gobierno.politica SET name=:name, description=:description, category=:category, owner_id=:owner,
                        status=:status, revision=:revision, current_version_id=:version, updated_at=:updated
                    WHERE id=:id AND project_id=:project AND revision=:leida
                    """)
                    .param("name", p.name()).param("description", p.description()).param("category", p.category().name())
                    .param("owner", p.ownerId()).param("status", p.status().name()).param("revision", p.revision())
                    .param("version", p.currentVersionId()).param("updated", Timestamp.from(p.updatedAt()))
                    .param("id", p.id()).param("project", p.projectId()).param("leida", revisionLeida).update();
        } catch (DuplicateKeyException e) {
            throw nombreRepetido();
        }
        if (filas != 1) throw new ConflictoPoliticaException("La política cambió mientras se guardaba; vuelva a consultarla");
    }

    @Override
    public void asociarActivo(UUID projectId, UUID policyId, UUID assetId, UUID actor, Instant instante) {
        jdbc.sql("""
                INSERT INTO gobierno.politica_activo(policy_id,project_id,asset_id,active,added_at,added_by)
                VALUES(:policy,:project,:asset,true,:at,:actor)
                ON CONFLICT (policy_id,asset_id) DO UPDATE
                    SET active=true, added_at=EXCLUDED.added_at, added_by=EXCLUDED.added_by, removed_at=NULL, removed_by=NULL
                """).param("policy", policyId).param("project", projectId).param("asset", assetId)
                .param("at", Timestamp.from(instante)).param("actor", actor).update();
    }

    @Override
    public void retirarActivo(UUID projectId, UUID policyId, UUID assetId, UUID actor, Instant instante) {
        jdbc.sql("""
                UPDATE gobierno.politica_activo SET active=false, removed_at=:at, removed_by=:actor
                WHERE policy_id=:policy AND project_id=:project AND asset_id=:asset AND active
                """).param("policy", policyId).param("project", projectId).param("asset", assetId)
                .param("at", Timestamp.from(instante)).param("actor", actor).update();
    }

    @Override
    public List<Politica> listar(UUID projectId, FiltroPoliticas f, int limite, int desplazamiento) {
        // Columnas fijas; los valores del filtro siempre como parámetros.
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE p.project_id=:project");
        String texto = f.texto() == null || f.texto().isBlank() ? null : "%" + escaparLike(f.texto().strip()) + "%";
        if (texto != null) sql.append(" AND (p.name ILIKE :texto ESCAPE '\\' OR p.description ILIKE :texto ESCAPE '\\')");
        if (f.status() != null) sql.append(" AND p.status=:status");
        if (f.category() != null) sql.append(" AND p.category=:category");
        if (f.ownerId() != null) sql.append(" AND p.owner_id=:owner");
        if (f.assetId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM gobierno.politica_activo a")
               .append(" WHERE a.policy_id=p.id AND a.asset_id=:asset AND a.active)");
        }
        sql.append(" ORDER BY p.created_at, p.id LIMIT :limite OFFSET :desplazamiento");

        JdbcClient.StatementSpec consulta = jdbc.sql(sql.toString()).param("project", projectId)
                .param("limite", limite).param("desplazamiento", desplazamiento);
        if (texto != null) consulta = consulta.param("texto", texto);
        if (f.status() != null) consulta = consulta.param("status", f.status().name());
        if (f.category() != null) consulta = consulta.param("category", f.category().name());
        if (f.ownerId() != null) consulta = consulta.param("owner", f.ownerId());
        if (f.assetId() != null) consulta = consulta.param("asset", f.assetId());
        return consulta.query((rs, fila) -> politica(rs)).list();
    }

    @Override
    public List<Politica> deActivo(UUID projectId, UUID assetId) {
        return jdbc.sql(SELECT + """
                 WHERE p.project_id=:project AND EXISTS (SELECT 1 FROM gobierno.politica_activo a
                    WHERE a.policy_id=p.id AND a.asset_id=:asset AND a.active)
                 ORDER BY p.created_at, p.id
                """).param("project", projectId).param("asset", assetId).query((rs, fila) -> politica(rs)).list();
    }

    private static ConflictoPoliticaException nombreRepetido() {
        return new ConflictoPoliticaException("Ya existe una política vigente con ese nombre en el proyecto");
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Politica politica(ResultSet rs) throws SQLException {
        UUID[] activos = (UUID[]) rs.getArray("asset_ids").getArray();
        return new Politica(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getString("name"), rs.getString("description"), CategoriaPolitica.valueOf(rs.getString("category")),
                rs.getObject("owner_id", UUID.class), EstadoPolitica.valueOf(rs.getString("status")),
                rs.getLong("revision"), rs.getObject("current_version_id", UUID.class), List.of(activos),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getObject("created_by", UUID.class),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
