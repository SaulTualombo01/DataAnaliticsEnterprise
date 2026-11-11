package org.dataanaliticsent.identidad.internal.infraestructura;

import java.util.UUID;
import org.dataanaliticsent.identidad.internal.dominio.AuditoriaIdentidad;
import org.dataanaliticsent.identidad.internal.dominio.PoliticaClave;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="tic.bootstrap.enabled",havingValue="true")
public class InicializadorAdministrador implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final AuditoriaIdentidad auditoria;
    private final String email,nombre,clave;
    public InicializadorAdministrador(JdbcClient jdbc,PasswordEncoder encoder,AuditoriaIdentidad auditoria,
            @Value("${tic.bootstrap.email}") String email,@Value("${tic.bootstrap.name}") String nombre,
            @Value("${tic.bootstrap.password}") String clave) {
        this.jdbc=jdbc; this.encoder=encoder; this.auditoria=auditoria; this.email=email; this.nombre=nombre; this.clave=clave;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.sql("SELECT pg_advisory_xact_lock(937245812)").query((r,n)->Boolean.TRUE).single();
        boolean existe=jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM autorizacion.asignacion_rol ar JOIN autorizacion.rol r ON r.id=ar.role_id
                JOIN identidad.usuario u ON u.id=ar.user_id WHERE r.code='ADMIN_SISTEMA' AND ar.scope_type='PLATFORM'
                  AND ar.status='active' AND ar.revoked_at IS NULL AND r.status='active' AND u.status='active')
                """).query(Boolean.class).single();
        if(existe) return;
        if(jdbc.sql("SELECT count(*) FROM identidad.usuario").query(Long.class).single()!=0) {
            throw new IllegalStateException("La inicialización solo se permite en una base sin cuentas; requiere recuperación administrativa explícita");
        }
        String correo=FiltroLimiteLogin.normalizar(email);
        if(correo.length()>254||!correo.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||nombre.isBlank()||nombre.length()>200) {
            throw new IllegalArgumentException("Configura correo y nombre válidos para la inicialización");
        }
        PoliticaClave.validarNueva(clave);
        UUID user=UUID.randomUUID();
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,:name)")
                .param("id",user).param("email",correo).param("name",nombre).update();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)")
                .param("id",user).param("hash",encoder.encode(clave)).update();
        int asignados=jdbc.sql("""
                INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by)
                SELECT :assignment,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA' AND status='active'
                """).param("assignment",UUID.randomUUID()).param("user",user).update();
        if(asignados!=1) throw new IllegalStateException("El catálogo no contiene un administrador habilitado");
        auditoria.registrar(user,user,"auth.bootstrap","SUCCESS",UUID.randomUUID());
    }
}
