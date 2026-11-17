package org.dataanaliticsent.autorizacion.internal.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AlcanceAutorizado;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.autorizacion.internal.dominio.RepositorioPermisos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PermisosJdbcAdapter implements RepositorioPermisos {
    private final JdbcClient jdbc;
    public PermisosJdbcAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean cuentaActiva(UUID usuarioId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM identidad.usuario WHERE id=:user AND status='active')")
                .param("user", usuarioId).query(Boolean.class).single();
    }

    @Override
    public Optional<Permiso> buscarPermiso(String codigo) {
        return jdbc.sql("SELECT operation_scope,available FROM autorizacion.permiso WHERE code=:code")
                .param("code", codigo).query((r,n) -> new Permiso(SolicitudAcceso.Ambito.valueOf(r.getString(1)),r.getBoolean(2))).optional();
    }

    @Override
    public boolean contextoVigente(UUID usuarioId, UUID proyectoId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM contexto.membresia_proyecto mp
                JOIN contexto.membresia_organizacion mo ON mo.id=mp.organization_membership_id
                JOIN contexto.proyecto p ON p.id=mp.project_id JOIN contexto.organizacion o ON o.id=p.organization_id
                JOIN identidad.usuario u ON u.id=mp.user_id
                WHERE mp.user_id=:user AND mp.project_id=:project AND mp.status='active'
                  AND mo.status='active' AND p.status='active' AND o.status='active' AND u.status='active')
                """).param("user",usuarioId).param("project",proyectoId).query(Boolean.class).single();
    }

    @Override
    public boolean permisoPlataforma(UUID usuarioId, String permiso) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM autorizacion.asignacion_rol ar
                JOIN identidad.usuario u ON u.id=ar.user_id JOIN autorizacion.rol r ON r.id=ar.role_id
                JOIN autorizacion.rol_permiso rp ON rp.role_id=r.id JOIN autorizacion.permiso p ON p.id=rp.permission_id
                WHERE ar.user_id=:user AND ar.scope_type='PLATFORM' AND ar.project_id IS NULL
                  AND ar.status='active' AND ar.revoked_at IS NULL AND u.status='active' AND r.status='active'
                  AND r.code='ADMIN_SISTEMA' AND p.code=:permission AND p.operation_scope='PLATFORM' AND p.available)
                """).param("user",usuarioId).param("permission",permiso).query(Boolean.class).single();
    }

    @Override
    public List<AlcanceAutorizado> alcances(UUID usuarioId, String permiso, UUID proyectoId) {
        return jdbc.sql("""
                SELECT DISTINCT aa.project_id,aa.resource_scope,aa.source_id,aa.asset_id
                FROM autorizacion.asignacion_rol ar JOIN autorizacion.rol r ON r.id=ar.role_id
                JOIN autorizacion.rol_permiso rp ON rp.role_id=r.id JOIN autorizacion.permiso pe ON pe.id=rp.permission_id
                JOIN autorizacion.alcance_asignacion aa ON aa.role_assignment_id=ar.id AND aa.project_id=ar.project_id
                JOIN contexto.membresia_proyecto mp ON mp.id=ar.project_membership_id AND mp.project_id=ar.project_id
                JOIN contexto.membresia_organizacion mo ON mo.id=mp.organization_membership_id
                JOIN contexto.proyecto pr ON pr.id=ar.project_id JOIN contexto.organizacion o ON o.id=pr.organization_id
                JOIN identidad.usuario u ON u.id=ar.user_id
                WHERE ar.user_id=:user AND ar.project_id=:project AND ar.scope_type='PROJECT'
                  AND ar.status='active' AND ar.revoked_at IS NULL AND r.status='active' AND pe.available
                  AND pe.code=:permission AND pe.operation_scope='PROJECT' AND mp.status='active' AND mo.status='active'
                  AND pr.status='active' AND o.status='active' AND u.status='active'
                ORDER BY aa.resource_scope,aa.source_id,aa.asset_id
                """).param("user",usuarioId).param("project",proyectoId).param("permission",permiso)
                .query((r,n) -> new AlcanceAutorizado(r.getObject("project_id",UUID.class),
                    AlcanceAutorizado.Tipo.valueOf(r.getString("resource_scope")),
                    switch(r.getString("resource_scope")) {
                        case "SOURCE" -> r.getObject("source_id",UUID.class);
                        case "ASSET" -> r.getObject("asset_id",UUID.class);
                        default -> null;
                    })).list();
    }
}
