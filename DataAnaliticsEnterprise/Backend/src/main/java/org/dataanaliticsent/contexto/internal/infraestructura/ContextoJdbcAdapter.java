package org.dataanaliticsent.contexto.internal.infraestructura;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.Membresia;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.Proyecto;
import org.dataanaliticsent.contexto.internal.dominio.RepositorioContexto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistencia sobre las tablas contexto.* de la migración V1. No hay borrado físico. */
@Repository
public class ContextoJdbcAdapter implements RepositorioContexto {
    private static final String ORGANIZACION = "id,name,status,created_at,updated_at";
    private static final String PROYECTO = "id,organization_id,name,status,created_at,updated_at";
    private static final String MEMBRESIA = "id,user_id,project_id,organization_id,status,assigned_by,assigned_at";

    private static final RowMapper<Organizacion> A_ORGANIZACION = (rs, fila) -> new Organizacion(
            rs.getObject("id", UUID.class), rs.getString("name"), Estado.de(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    private static final RowMapper<Proyecto> A_PROYECTO = (rs, fila) -> new Proyecto(
            rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getString("name"),
            Estado.de(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    private static final RowMapper<Membresia> A_MEMBRESIA = (rs, fila) -> new Membresia(
            rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getObject("project_id", UUID.class), rs.getObject("organization_id", UUID.class),
            Estado.de(rs.getString("status")), rs.getObject("assigned_by", UUID.class),
            rs.getObject("assigned_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public ContextoJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Organizacion> organizaciones(Pagina pagina) {
        return jdbc.query(
                "SELECT " + ORGANIZACION + " FROM contexto.organizacion ORDER BY created_at,id LIMIT ? OFFSET ?",
                A_ORGANIZACION, pagina.limit(), pagina.offset());
    }

    @Override
    public Optional<Organizacion> organizacion(UUID id) {
        return jdbc.query("SELECT " + ORGANIZACION + " FROM contexto.organizacion WHERE id=?", A_ORGANIZACION, id)
                .stream().findFirst();
    }

    @Override
    public Organizacion crearOrganizacion(UUID id, String nombre) {
        return jdbc.query(
                "INSERT INTO contexto.organizacion(id,name) VALUES(?,?) RETURNING " + ORGANIZACION,
                A_ORGANIZACION, id, nombre).getFirst();
    }

    @Override
    public Optional<Organizacion> renombrarOrganizacion(UUID id, String nombre) {
        return jdbc.query(
                "UPDATE contexto.organizacion SET name=?,updated_at=now() WHERE id=? RETURNING " + ORGANIZACION,
                A_ORGANIZACION, nombre, id).stream().findFirst();
    }

    @Override
    public Optional<Organizacion> cambiarEstadoOrganizacion(UUID id, Estado estado) {
        return jdbc.query(
                "UPDATE contexto.organizacion SET status=?,updated_at=now() WHERE id=? RETURNING " + ORGANIZACION,
                A_ORGANIZACION, estado.valor(), id).stream().findFirst();
    }

    @Override
    public List<Proyecto> proyectos(UUID organizacionId, Pagina pagina) {
        return jdbc.query(
                "SELECT " + PROYECTO + " FROM contexto.proyecto WHERE organization_id=?"
                        + " ORDER BY created_at,id LIMIT ? OFFSET ?",
                A_PROYECTO, organizacionId, pagina.limit(), pagina.offset());
    }

    @Override
    public Optional<Proyecto> proyecto(UUID id) {
        return jdbc.query("SELECT " + PROYECTO + " FROM contexto.proyecto WHERE id=?", A_PROYECTO, id)
                .stream().findFirst();
    }

    @Override
    public Proyecto crearProyecto(UUID id, UUID organizacionId, String nombre) {
        return jdbc.query(
                "INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(?,?,?) RETURNING " + PROYECTO,
                A_PROYECTO, id, organizacionId, nombre).getFirst();
    }

    @Override
    public Optional<Proyecto> renombrarProyecto(UUID id, String nombre) {
        return jdbc.query(
                "UPDATE contexto.proyecto SET name=?,updated_at=now() WHERE id=? RETURNING " + PROYECTO,
                A_PROYECTO, nombre, id).stream().findFirst();
    }

    @Override
    public Optional<Proyecto> cambiarEstadoProyecto(UUID id, Estado estado) {
        return jdbc.query(
                "UPDATE contexto.proyecto SET status=?,updated_at=now() WHERE id=? RETURNING " + PROYECTO,
                A_PROYECTO, estado.valor(), id).stream().findFirst();
    }

    @Override
    public List<Membresia> membresias(UUID proyectoId, Pagina pagina) {
        return jdbc.query(
                "SELECT " + MEMBRESIA + " FROM contexto.membresia_proyecto WHERE project_id=?"
                        + " ORDER BY assigned_at,id LIMIT ? OFFSET ?",
                A_MEMBRESIA, proyectoId, pagina.limit(), pagina.offset());
    }

    /** La membresía de proyecto exige una membresía de organización; ambas se crean o reactivan juntas. */
    @Override
    @Transactional
    public Membresia vincular(UUID usuarioId, Proyecto proyecto, UUID actorId) {
        UUID membresiaOrganizacionId = jdbc.queryForObject("""
                INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by)
                VALUES(?,?,?,?)
                ON CONFLICT (user_id,organization_id) DO UPDATE SET status='active'
                RETURNING id
                """, UUID.class, UUID.randomUUID(), usuarioId, proyecto.organizationId(), actorId);
        return jdbc.query("""
                INSERT INTO contexto.membresia_proyecto
                    (id,organization_membership_id,user_id,organization_id,project_id,assigned_by)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT (user_id,project_id) DO UPDATE SET status='active'
                RETURNING\s""" + MEMBRESIA,
                A_MEMBRESIA, UUID.randomUUID(), membresiaOrganizacionId, usuarioId, proyecto.organizationId(),
                proyecto.id(), actorId).getFirst();
    }

    @Override
    public Optional<Membresia> revocar(UUID usuarioId, UUID proyectoId) {
        return jdbc.query(
                "UPDATE contexto.membresia_proyecto SET status='inactive' WHERE user_id=? AND project_id=?"
                        + " RETURNING " + MEMBRESIA,
                A_MEMBRESIA, usuarioId, proyectoId).stream().findFirst();
    }
}
