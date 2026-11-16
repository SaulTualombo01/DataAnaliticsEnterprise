package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.TroncoComun.identidad.CuentasApi.*;
import org.dataanaliticsent.TroncoComun.identidad.CuentasException;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.RepositorioCuentas;
import org.dataanaliticsent.TroncoComun.errores.CorrelacionOperacionApi;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CuentasJdbcAdapter implements RepositorioCuentas {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final CorrelacionOperacionApi correlacion;
    private final ObjectMapper json;
    public CuentasJdbcAdapter(JdbcClient jdbc,Clock clock,CorrelacionOperacionApi correlacion,ObjectMapper json) {
        this.jdbc=jdbc; this.clock=clock; this.correlacion=correlacion; this.json=json;
    }
    private static Cuenta cuenta(ResultSet r,int n) throws SQLException {
        return new Cuenta(r.getObject("id",UUID.class),r.getString("email"),r.getString("display_name"),r.getString("status"),r.getTimestamp("created_at").toInstant());
    }
    private Invitacion invitacion(ResultSet r,int n) throws SQLException {
        String status=r.getString("status"); Instant expires=r.getTimestamp("expires_at").toInstant();
        if("pending".equals(status) && !expires.isAfter(clock.instant())) status="expired";
        return new Invitacion(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getString("email"),r.getString("display_name"),
            r.getObject("invited_by",UUID.class),status,r.getTimestamp("created_at").toInstant(),expires,r.getObject("accepted_user_id",UUID.class));
    }
    @Override public void bloquearAdministracion() { jdbc.sql("SELECT pg_advisory_xact_lock(937245813)").query((r,n)->true).single(); }
    @Override public boolean bloquearEmpresaActiva(UUID org) {
        return jdbc.sql("SELECT status FROM contexto.organizacion WHERE id=:id FOR SHARE").param("id",org).query(String.class).optional().filter("active"::equals).isPresent();
    }
    @Override public Cuenta bloquearCuenta(UUID user) {
        return jdbc.sql("SELECT id,email,display_name,status,created_at FROM identidad.usuario WHERE id=:id FOR UPDATE").param("id",user).query(CuentasJdbcAdapter::cuenta)
            .optional().orElseThrow(()->new CuentasException(CuentasException.Tipo.NO_DISPONIBLE));
    }
    @Override public boolean existeCorreo(String email) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM identidad.usuario WHERE normalized_email=:email)").param("email",email).query(Boolean.class).single();
    }
    @Override public List<Invitacion> pendientes(UUID org,String email) {
        return jdbc.sql("SELECT * FROM identidad.invitacion WHERE organization_id=:org AND email=:email AND status='pending' FOR UPDATE")
            .param("org",org).param("email",email).query(this::invitacion).list();
    }
    @Override public Invitacion crearInvitacion(UUID id,UUID org,String email,String name,UUID actor,String hash,Instant at,Instant expires) {
        return jdbc.sql("""
            INSERT INTO identidad.invitacion(id,organization_id,email,display_name,invited_by,token_hash,created_at,expires_at)
            VALUES(:id,:org,:email,:name,:actor,:hash,:at,:expires) RETURNING *
            """).param("id",id).param("org",org).param("email",email).param("name",name).param("actor",actor).param("hash",hash)
            .param("at",Timestamp.from(at)).param("expires",Timestamp.from(expires)).query(this::invitacion).single();
    }
    @Override public Optional<Pendiente> porToken(String hash) {
        return jdbc.sql("SELECT * FROM identidad.invitacion WHERE token_hash=:hash FOR UPDATE").param("hash",hash)
            .query((r,n)->new Pendiente(invitacion(r,n),r.getString("status"))).optional();
    }
    @Override public Optional<Invitacion> invitacion(UUID org,UUID id) {
        return jdbc.sql("SELECT * FROM identidad.invitacion WHERE organization_id=:org AND id=:id FOR UPDATE").param("org",org).param("id",id).query(this::invitacion).optional();
    }
    @Override public void revocarInvitacion(UUID id,Instant at) {
        jdbc.sql("UPDATE identidad.invitacion SET status='revoked',revoked_at=:at WHERE id=:id AND status='pending'").param("id",id).param("at",Timestamp.from(at)).update();
    }
    @Override public Cuenta crearCuenta(UUID id,String email,String name,String hash,Instant at) {
        var user=jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name,created_at,updated_at) VALUES(:id,:email,:name,:at,:at) RETURNING id,email,display_name,status,created_at")
            .param("id",id).param("email",email).param("name",name).param("at",Timestamp.from(at)).query(CuentasJdbcAdapter::cuenta).single();
        jdbc.sql("INSERT INTO identidad.credencial_local(user_id,password_hash) VALUES(:id,:hash)").param("id",id).param("hash",hash).update(); return user;
    }
    @Override public void incorporar(UUID user,UUID org,UUID actor) {
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:actor)")
            .param("id",UUID.randomUUID()).param("user",user).param("org",org).param("actor",actor).update();
    }
    @Override public void aceptar(UUID invitation,UUID user,Instant at) {
        int count=jdbc.sql("UPDATE identidad.invitacion SET status='accepted',accepted_user_id=:user,accepted_at=:at WHERE id=:id AND status='pending'")
            .param("user",user).param("at",Timestamp.from(at)).param("id",invitation).update();
        if(count!=1) throw new CuentasException(CuentasException.Tipo.INVITACION_INVALIDA);
    }
    @Override public List<Invitacion> invitaciones(UUID org,int limit,long offset) {
        return jdbc.sql("SELECT * FROM identidad.invitacion WHERE organization_id=:org ORDER BY created_at,id LIMIT :limit OFFSET :offset")
            .param("org",org).param("limit",limit).param("offset",offset).query(this::invitacion).list();
    }
    @Override public List<Cuenta> cuentas(UUID org,int limit,long offset) {
        String filter=org==null?"":" WHERE EXISTS(SELECT 1 FROM contexto.membresia_organizacion mo WHERE mo.user_id=u.id AND mo.organization_id=:org)";
        var sql=jdbc.sql("SELECT u.id,u.email,u.display_name,u.status,u.created_at FROM identidad.usuario u"+filter+" ORDER BY u.created_at,u.id LIMIT :limit OFFSET :offset")
            .param("limit",limit).param("offset",offset);
        if(org!=null) sql=sql.param("org",org); return sql.query(CuentasJdbcAdapter::cuenta).list();
    }
    @Override public boolean pertenece(UUID user,UUID org) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM contexto.membresia_organizacion WHERE user_id=:user AND organization_id=:org)").param("user",user).param("org",org).query(Boolean.class).single();
    }
    @Override public boolean otraEmpresaActiva(UUID user,UUID org) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM contexto.membresia_organizacion WHERE user_id=:user AND organization_id<>:org AND status='active')").param("user",user).param("org",org).query(Boolean.class).single();
    }
    @Override public boolean administrador(UUID user) {
        return jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM autorizacion.administrador_empresa WHERE user_id=:user AND status='active' AND revoked_at IS NULL)
            OR EXISTS(SELECT 1 FROM autorizacion.asignacion_rol ar JOIN autorizacion.rol r ON r.id=ar.role_id
              WHERE ar.user_id=:user AND ar.status='active' AND ar.revoked_at IS NULL AND ar.scope_type='PLATFORM' AND r.code='ADMIN_SISTEMA')
            """).param("user",user).query(Boolean.class).single();
    }
    private static final String ACCESIBLES="""
        FROM identidad.usuario u JOIN identidad.credencial_local c ON c.user_id=u.id
        WHERE u.status='active' AND EXISTS(SELECT 1 FROM autorizacion.asignacion_rol ar
          JOIN autorizacion.rol r ON r.id=ar.role_id
          WHERE ar.user_id=u.id AND ar.scope_type='PLATFORM' AND ar.project_id IS NULL AND ar.status='active'
          AND ar.revoked_at IS NULL AND r.code='ADMIN_SISTEMA' AND r.status='active')
        """;
    @Override public boolean administradorAccesible(UUID user) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 "+ACCESIBLES+" AND u.id=:user)").param("user",user).query(Boolean.class).single();
    }
    @Override public long administradoresAccesibles() { return jdbc.sql("SELECT count(*) "+ACCESIBLES).query(Long.class).single(); }
    @Override public void desactivar(UUID user) {
        jdbc.sql("UPDATE identidad.usuario SET status='inactive',updated_at=:at WHERE id=:id AND status='active'").param("id",user).param("at",Timestamp.from(clock.instant())).update();
    }
    @Override public void auditar(UUID actor,UUID org,UUID recurso,String accion,String motivo,UUID sujeto) {
        Map<String,Object> details=new LinkedHashMap<>(); details.put("reason",motivo);
        if(org!=null) details.put("organization_id",org); if(sujeto!=null) details.put("subject_user_id",sujeto);
        jdbc.sql("""
            INSERT INTO auditoria.evento(id,actor_type,actor_id,resource_type,resource_id,action,occurred_at,outcome,request_id,safe_details)
            VALUES(:id,'USER',:actor,:type,:resource,:action,:at,'SUCCESS',:request,CAST(:details AS jsonb))
            """).param("id",UUID.randomUUID()).param("actor",actor).param("type",accion.startsWith("tc.invitation.")?"identidad.invitacion":"identidad.usuario")
            .param("resource",recurso).param("action",accion).param("at",Timestamp.from(clock.instant())).param("request",correlacion.actual())
            .param("details",json.writeValueAsString(details)).update();
    }
}
