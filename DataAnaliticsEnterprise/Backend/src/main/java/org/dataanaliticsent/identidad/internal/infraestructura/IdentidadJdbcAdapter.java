package org.dataanaliticsent.identidad.internal.infraestructura;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.dataanaliticsent.identidad.internal.dominio.CuentaLocal;
import org.dataanaliticsent.identidad.internal.dominio.RepositorioIdentidad;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentidadJdbcAdapter implements RepositorioIdentidad {
    private final JdbcClient jdbc;
    public IdentidadJdbcAdapter(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Optional<CuentaLocal> porCorreo(String normalizedEmail) {
        return buscar("u.normalized_email=:valor",normalizedEmail);
    }
    public Optional<CuentaLocal> porId(UUID id) { return buscar("u.id=:valor",id); }
    private Optional<CuentaLocal> buscar(String condicion,Object valor) {
        return jdbc.sql("SELECT u.id,u.email,u.display_name,u.status,c.password_hash,c.session_version FROM identidad.usuario u JOIN identidad.credencial_local c ON c.user_id=u.id WHERE "+condicion)
                .param("valor",valor).query((r,n)->new CuentaLocal(r.getObject(1,UUID.class),r.getString(2),r.getString(3),
                        "active".equals(r.getString(4)),r.getString(5),r.getLong(6))).optional();
    }
    public boolean cambiarClave(UUID id,long version,String hash) {
        return jdbc.sql("UPDATE identidad.credencial_local SET password_hash=:hash WHERE user_id=:id AND session_version=:version")
                .param("hash",hash).param("id",id).param("version",version).update()==1;
    }
    public boolean revocar(UUID id) {
        return jdbc.sql("UPDATE identidad.credencial_local SET session_version=session_version+1 WHERE user_id=:id")
                .param("id",id).update()==1;
    }
    public OptionalLong versionActiva(UUID id) {
        var version=jdbc.sql("SELECT c.session_version FROM identidad.credencial_local c JOIN identidad.usuario u ON u.id=c.user_id WHERE u.id=:id AND u.status='active'")
                .param("id",id).query(Long.class).optional();
        return version.isPresent()?OptionalLong.of(version.get()):OptionalLong.empty();
    }
}
