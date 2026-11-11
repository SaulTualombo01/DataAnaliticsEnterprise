package org.dataanaliticsent.catalogo.internal.infraestructura;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.ProveedorRecursosAutorizacion;
import org.dataanaliticsent.autorizacion.SolicitudAcceso.Recurso;
import org.dataanaliticsent.autorizacion.SolicitudAcceso.TipoRecurso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Publica solo la pertenencia del recurso; sus reglas de estado corresponden al caso de uso. */
@Repository
public class RecursosCatalogoJdbcAdapter implements ProveedorRecursosAutorizacion {
    private final JdbcClient jdbc;

    public RecursosCatalogoJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean soporta(TipoRecurso tipo) {
        return tipo == TipoRecurso.SOURCE || tipo == TipoRecurso.ASSET;
    }

    @Override
    public Optional<Jerarquia> resolver(Recurso recurso) {
        Objects.requireNonNull(recurso);
        String sql = switch (recurso.tipo()) {
            case SOURCE -> """
                    SELECT project_id, id AS source_id, NULL::uuid AS asset_id
                    FROM catalogo.fuente WHERE id=:id
                    """;
            case ASSET -> """
                    SELECT a.project_id, a.source_id, a.id AS asset_id
                    FROM catalogo.activo a
                    JOIN catalogo.fuente f ON f.id=a.source_id AND f.project_id=a.project_id
                    WHERE a.id=:id
                    """;
        };
        return jdbc.sql(sql).param("id", recurso.id())
                .query((rs, row) -> new Jerarquia(rs.getObject("project_id", UUID.class),
                        rs.getObject("source_id", UUID.class), rs.getObject("asset_id", UUID.class)))
                .optional();
    }
}
