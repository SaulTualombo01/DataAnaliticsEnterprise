package org.dataanaliticsent.TroncoComun.contexto.internal.infraestructura;
import java.util.*;
import org.dataanaliticsent.TroncoComun.contexto.AdministracionActualApi.Empresa;
import org.dataanaliticsent.TroncoComun.contexto.internal.dominio.RepositorioAdministracionActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class AdministracionActualJdbcAdapter implements RepositorioAdministracionActual {
    private final JdbcClient jdbc;
    public AdministracionActualJdbcAdapter(JdbcClient jdbc) { this.jdbc=jdbc; }
    public List<String> permisos(String scope) {
        return jdbc.sql("SELECT code FROM autorizacion.permiso WHERE operation_scope=:s AND available ORDER BY code").param("s",scope).query(String.class).list();
    }
    public List<Empresa> empresasDelegadas(UUID user) {
        return jdbc.sql("""
            SELECT o.id,o.name,o.status FROM autorizacion.administrador_empresa a
            JOIN contexto.membresia_organizacion m ON m.id=a.organization_membership_id
            JOIN contexto.organizacion o ON o.id=a.organization_id JOIN autorizacion.rol r ON r.id=a.role_id
            JOIN identidad.usuario u ON u.id=a.user_id
            WHERE a.user_id=:u AND a.status='active' AND a.revoked_at IS NULL AND m.status='active'
              AND o.status='active' AND u.status='active' AND r.status='active' AND r.code='ADMIN_EMPRESA' ORDER BY o.id
            """).param("u",user).query((r,n)->new Empresa(r.getObject(1,UUID.class),r.getString(2),r.getString(3))).list();
    }
    public Optional<Empresa> empresa(UUID id) {
        return jdbc.sql("SELECT id,name,status FROM contexto.organizacion WHERE id=:id").param("id",id).query((r,n)->new Empresa(r.getObject(1,UUID.class),r.getString(2),r.getString(3))).optional();
    }
}
