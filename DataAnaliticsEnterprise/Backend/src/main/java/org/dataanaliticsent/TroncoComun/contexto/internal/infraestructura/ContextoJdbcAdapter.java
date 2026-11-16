package org.dataanaliticsent.TroncoComun.contexto.internal.infraestructura;

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
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi.*;
import org.dataanaliticsent.TroncoComun.contexto.ContextoException;
import org.dataanaliticsent.TroncoComun.contexto.internal.dominio.RepositorioContexto;
import org.dataanaliticsent.TroncoComun.errores.CorrelacionOperacionApi;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ContextoJdbcAdapter implements RepositorioContexto {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final CorrelacionOperacionApi correlacion;
    public ContextoJdbcAdapter(JdbcClient jdbc,ObjectMapper json,Clock clock,CorrelacionOperacionApi correlacion) { this.jdbc=jdbc; this.json=json; this.clock=clock; this.correlacion=correlacion; }
    private static Instant instante(ResultSet row,String column) throws SQLException {
        var timestamp=row.getTimestamp(column); return timestamp==null?null:timestamp.toInstant();
    }
    private static Organizacion org(ResultSet row,int n) throws SQLException {
        return new Organizacion(row.getObject("id",UUID.class),row.getString("name"),row.getString("status"),instante(row,"created_at"));
    }
    private static Proyecto proyecto(ResultSet row,int n) throws SQLException {
        return new Proyecto(row.getObject("id",UUID.class),row.getObject("organization_id",UUID.class),row.getString("name"),row.getString("status"),instante(row,"created_at"));
    }
    private static AdministradorEmpresa admin(ResultSet row,int n) throws SQLException {
        return new AdministradorEmpresa(row.getObject("id",UUID.class),row.getObject("user_id",UUID.class),row.getObject("organization_id",UUID.class),
                row.getObject("assigned_by",UUID.class),row.getString("status"),instante(row,"assigned_at"),instante(row,"revoked_at"));
    }
    @Override public void bloquearEscritura(UUID actor) {
        jdbc.sql("SELECT pg_advisory_xact_lock(937245813)").query((r,n)->true).single();
        jdbc.sql("SELECT id FROM identidad.usuario WHERE id=:actor FOR SHARE").param("actor",actor).query(UUID.class).optional();
    }
    @Override public Optional<Organizacion> organizacion(UUID id) {
        return jdbc.sql("SELECT id,name,status,created_at FROM contexto.organizacion WHERE id=:id").param("id",id).query(ContextoJdbcAdapter::org).optional();
    }
    @Override public void bloquearOrganizacion(UUID id) {
        jdbc.sql("SELECT id FROM contexto.organizacion WHERE id=:id FOR SHARE").param("id",id).query(UUID.class).optional();
    }
    @Override public Organizacion crearOrganizacion(UUID id,String nombre) {
        return jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,:name) RETURNING id,name,status,created_at")
                .param("id",id).param("name",nombre).query(ContextoJdbcAdapter::org).single();
    }
    @Override public List<Organizacion> organizaciones(int limite,long offset) {
        return jdbc.sql("SELECT id,name,status,created_at FROM contexto.organizacion ORDER BY created_at,id LIMIT :limit OFFSET :offset")
                .param("limit",limite).param("offset",offset).query(ContextoJdbcAdapter::org).list();
    }
    @Override public Proyecto crearProyecto(UUID id,UUID organizacion,String nombre) {
        return jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,:name) RETURNING id,organization_id,name,status,created_at")
                .param("id",id).param("org",organizacion).param("name",nombre).query(ContextoJdbcAdapter::proyecto).single();
    }
    @Override public List<Proyecto> proyectos(UUID organizacion,int limite,long offset) {
        return jdbc.sql("SELECT id,organization_id,name,status,created_at FROM contexto.proyecto WHERE organization_id=:org ORDER BY created_at,id LIMIT :limit OFFSET :offset")
                .param("org",organizacion).param("limit",limite).param("offset",offset).query(ContextoJdbcAdapter::proyecto).list();
    }
    @Override public String bloquearUsuario(UUID usuario) {
        return jdbc.sql("SELECT status FROM identidad.usuario WHERE id=:id FOR UPDATE").param("id",usuario).query(String.class).optional()
                .orElseThrow(()->new ContextoException(ContextoException.Tipo.NO_DISPONIBLE));
    }
    @Override public boolean otraEmpresaActiva(UUID usuario,UUID organizacion) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM contexto.membresia_organizacion WHERE user_id=:user AND organization_id<>:org AND status='active')")
                .param("user",usuario).param("org",organizacion).query(Boolean.class).single();
    }
    @Override public Optional<Membresia> membresia(UUID usuario,UUID organizacion) {
        return jdbc.sql("SELECT id,status FROM contexto.membresia_organizacion WHERE user_id=:user AND organization_id=:org")
                .param("user",usuario).param("org",organizacion).query((r,n)->new Membresia(r.getObject("id",UUID.class),r.getString("status"))).optional();
    }
    @Override public UUID crearMembresia(UUID usuario,UUID organizacion,UUID actor) {
        UUID id=UUID.randomUUID();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:actor)")
                .param("id",id).param("user",usuario).param("org",organizacion).param("actor",actor).update();
        return id;
    }
    @Override public AdministradorEmpresa asignarAdministrador(UUID usuario,UUID organizacion,UUID membresia,UUID actor) {
        return jdbc.sql("""
                INSERT INTO autorizacion.administrador_empresa(id,user_id,organization_id,organization_membership_id,role_id,assigned_by)
                SELECT :id,:user,:org,:member,id,:actor FROM autorizacion.rol WHERE code='ADMIN_EMPRESA' AND status='active'
                RETURNING *
                """).param("id",UUID.randomUUID()).param("user",usuario).param("org",organizacion).param("member",membresia).param("actor",actor)
                .query(ContextoJdbcAdapter::admin).optional().orElseThrow(()->new ContextoException(ContextoException.Tipo.CONFLICTO));
    }
    @Override public List<AdministradorEmpresa> administradores(UUID organizacion,int limite,long offset) {
        return jdbc.sql("SELECT * FROM autorizacion.administrador_empresa WHERE organization_id=:org ORDER BY assigned_at,id LIMIT :limit OFFSET :offset")
                .param("org",organizacion).param("limit",limite).param("offset",offset).query(ContextoJdbcAdapter::admin).list();
    }
    @Override public Optional<AdministradorEmpresa> bloquearAdministrador(UUID organizacion,UUID asignacion) {
        return jdbc.sql("SELECT * FROM autorizacion.administrador_empresa WHERE organization_id=:org AND id=:id FOR UPDATE")
                .param("org",organizacion).param("id",asignacion).query(ContextoJdbcAdapter::admin).optional();
    }
    @Override public void revocarAdministrador(UUID asignacion) {
        jdbc.sql("UPDATE autorizacion.administrador_empresa SET status='inactive',revoked_at=now() WHERE id=:id AND revoked_at IS NULL")
                .param("id",asignacion).update();
    }
    @Override public void auditar(UUID actor,UUID organizacion,UUID proyecto,String tipo,UUID recurso,String accion,String motivo,UUID sujeto) {
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("organization_id",organizacion); details.put("reason",motivo);
        if(sujeto!=null) details.put("subject_user_id",sujeto);
        jdbc.sql("""
                INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,occurred_at,outcome,request_id,safe_details)
                VALUES(:id,'USER',:actor,:project,:type,:resource,:action,:at,'SUCCESS',:request,CAST(:details AS jsonb))
                """).param("id",UUID.randomUUID()).param("actor",actor).param("project",proyecto).param("type",tipo).param("resource",recurso)
                .param("action",accion).param("at",Timestamp.from(clock.instant())).param("request",correlacion.actual()).param("details",json.writeValueAsString(details)).update();
    }
}
