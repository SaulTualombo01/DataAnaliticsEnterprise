package org.dataanaliticsent.TroncoComun.contexto.internal.infraestructura;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import org.dataanaliticsent.TroncoComun.contexto.ContextoApi;
import org.dataanaliticsent.TroncoComun.contexto.ParticipacionApi.*;
import org.dataanaliticsent.TroncoComun.contexto.internal.dominio.RepositorioParticipacion;
import org.dataanaliticsent.TroncoComun.errores.CorrelacionOperacionApi;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ParticipacionJdbcAdapter implements RepositorioParticipacion {
    private final JdbcClient jdbc; private final Clock clock; private final CorrelacionOperacionApi correlacion; private final ObjectMapper json;
    public ParticipacionJdbcAdapter(JdbcClient jdbc,Clock clock,CorrelacionOperacionApi correlacion,ObjectMapper json) { this.jdbc=jdbc; this.clock=clock; this.correlacion=correlacion; this.json=json; }
    @Override public void bloquear() { jdbc.sql("SELECT pg_advisory_xact_lock(937245813)").query((r,n)->true).single(); }
    @Override public boolean empresaActiva(UUID org) { return jdbc.sql("SELECT status FROM contexto.organizacion WHERE id=:id FOR SHARE").param("id",org).query(String.class).optional().filter("active"::equals).isPresent(); }
    @Override public boolean usuarioActivo(UUID user) { return jdbc.sql("SELECT status FROM identidad.usuario WHERE id=:id FOR UPDATE").param("id",user).query(String.class).optional().filter("active"::equals).isPresent(); }
    @Override public boolean administradorSistema(UUID user) { return jdbc.sql("SELECT EXISTS(SELECT 1 FROM autorizacion.asignacion_rol a JOIN autorizacion.rol r ON r.id=a.role_id WHERE a.user_id=:u AND a.scope_type='PLATFORM' AND a.status='active' AND a.revoked_at IS NULL AND r.code='ADMIN_SISTEMA' AND r.status='active')").param("u",user).query(Boolean.class).single(); }
    @Override public boolean administrador(UUID user) { return administradorSistema(user) || jdbc.sql("SELECT EXISTS(SELECT 1 FROM autorizacion.administrador_empresa WHERE user_id=:u AND status='active' AND revoked_at IS NULL)").param("u",user).query(Boolean.class).single(); }
    @Override public boolean otraEmpresa(UUID user,UUID org) { return jdbc.sql("SELECT EXISTS(SELECT 1 FROM contexto.membresia_organizacion WHERE user_id=:u AND organization_id<>:o AND status='active')").param("u",user).param("o",org).query(Boolean.class).single(); }
    private static Proyecto proyecto(ResultSet r,int n) throws SQLException { return new Proyecto(new ContextoApi.Proyecto(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getString("name"),r.getString("status"),r.getTimestamp("created_at").toInstant()),r.getLong("admin_revision")); }
    @Override public Optional<Proyecto> proyecto(UUID org,UUID id,boolean lock) { return jdbc.sql("SELECT * FROM contexto.proyecto WHERE organization_id=:o AND id=:id"+(lock?" FOR UPDATE":"")).param("o",org).param("id",id).query(ParticipacionJdbcAdapter::proyecto).optional(); }
    @Override public Proyecto cambiarProyecto(UUID org,UUID id,String name,String status) { return jdbc.sql("UPDATE contexto.proyecto SET name=:name,status=:status WHERE organization_id=:o AND id=:id RETURNING *").param("name",name).param("status",status).param("o",org).param("id",id).query(ParticipacionJdbcAdapter::proyecto).single(); }
    private static MembresiaEmpresa empresa(ResultSet r,int n) throws SQLException { return new MembresiaEmpresa(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getObject("organization_id",UUID.class),r.getString("status"),r.getObject("assigned_by",UUID.class),r.getTimestamp("assigned_at").toInstant()); }
    @Override public Optional<MembresiaEmpresa> membresiaEmpresa(UUID org,UUID id) { return jdbc.sql("SELECT * FROM contexto.membresia_organizacion WHERE organization_id=:o AND id=:id").param("o",org).param("id",id).query(ParticipacionJdbcAdapter::empresa).optional(); }
    @Override public Optional<MembresiaEmpresa> empleado(UUID org,UUID user) { return jdbc.sql("SELECT * FROM contexto.membresia_organizacion WHERE organization_id=:o AND user_id=:u").param("o",org).param("u",user).query(ParticipacionJdbcAdapter::empresa).optional(); }
    @Override public List<MembresiaEmpresa> miembrosEmpresa(UUID org,int limit,long offset) { return jdbc.sql("SELECT * FROM contexto.membresia_organizacion WHERE organization_id=:o ORDER BY assigned_at,id LIMIT :l OFFSET :off").param("o",org).param("l",limit).param("off",offset).query(ParticipacionJdbcAdapter::empresa).list(); }
    @Override public void estadoEmpresa(UUID member,String status) { jdbc.sql("UPDATE contexto.membresia_organizacion SET status=:s WHERE id=:id").param("s",status).param("id",member).update(); }
    private static final String MEMBERS="""
        SELECT m.*,a.id current_assignment_id,r.code role_code FROM contexto.membresia_proyecto m
        LEFT JOIN autorizacion.asignacion_rol a ON a.project_membership_id=m.id AND a.status='active' AND a.revoked_at IS NULL
          AND a.scope_type='PROJECT' AND a.role_id IN (SELECT id FROM autorizacion.rol WHERE code IN ('GESTOR_CATALOGO','ANALISTA_CALIDAD','RESPONSABLE_GOBIERNO','ANALISTA_NEGOCIO'))
        LEFT JOIN autorizacion.rol r ON r.id=a.role_id
        """;
    private static MembresiaProyecto member(ResultSet r,int n) throws SQLException { return new MembresiaProyecto(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getObject("organization_id",UUID.class),r.getObject("project_id",UUID.class),r.getString("status"),r.getObject("assigned_by",UUID.class),r.getTimestamp("assigned_at").toInstant(),r.getObject("current_assignment_id",UUID.class),r.getString("role_code")); }
    @Override public Optional<MembresiaProyecto> membresia(UUID org,UUID project,UUID id) { return jdbc.sql(MEMBERS+" WHERE m.organization_id=:o AND m.project_id=:p AND m.id=:id").param("o",org).param("p",project).param("id",id).query(ParticipacionJdbcAdapter::member).optional(); }
    @Override public Optional<MembresiaProyecto> participante(UUID org,UUID project,UUID user) { return jdbc.sql(MEMBERS+" WHERE m.organization_id=:o AND m.project_id=:p AND m.user_id=:u").param("o",org).param("p",project).param("u",user).query(ParticipacionJdbcAdapter::member).optional(); }
    @Override public List<MembresiaProyecto> miembros(UUID org,UUID project,int limit,long offset) { return jdbc.sql(MEMBERS+" WHERE m.organization_id=:o AND m.project_id=:p ORDER BY m.assigned_at,m.id LIMIT :l OFFSET :off").param("o",org).param("p",project).param("l",limit).param("off",offset).query(ParticipacionJdbcAdapter::member).list(); }
    @Override public MembresiaProyecto incorporar(UUID org,UUID project,UUID user,UUID companyMember,UUID actor) {
        UUID id=UUID.randomUUID(); jdbc.sql("INSERT INTO contexto.membresia_proyecto(id,organization_membership_id,user_id,organization_id,project_id,assigned_by) VALUES(:id,:om,:u,:o,:p,:a)")
            .param("id",id).param("om",companyMember).param("u",user).param("o",org).param("p",project).param("a",actor).update(); return membresia(org,project,id).orElseThrow();
    }
    @Override public void estadoProyecto(UUID member,String status) { jdbc.sql("UPDATE contexto.membresia_proyecto SET status=:s WHERE id=:id").param("s",status).param("id",member).update(); }
    @Override public List<Rol> roles() { return jdbc.sql("""
        SELECT r.code,r.name,COALESCE((SELECT jsonb_agg(p.code ORDER BY p.code) FROM autorizacion.rol_permiso rp
          JOIN autorizacion.permiso p ON p.id=rp.permission_id WHERE rp.role_id=r.id AND p.available AND p.operation_scope='PROJECT'),'[]')::text permissions
        FROM autorizacion.rol r WHERE r.status='active' AND r.code IN ('GESTOR_CATALOGO','ANALISTA_CALIDAD','RESPONSABLE_GOBIERNO','ANALISTA_NEGOCIO') ORDER BY r.code
        """).query((r,n)->new Rol(r.getString(1),r.getString(2),json.readValue(r.getString(3),new TypeReference<List<String>>(){}))).list(); }
    private static final String ASSIGNMENTS="""
        SELECT a.*,r.code role_code,COALESCE((SELECT jsonb_agg(jsonb_build_object('type',s.resource_scope,'resourceId',COALESCE(s.source_id,s.asset_id)) ORDER BY s.resource_scope,COALESCE(s.source_id,s.asset_id))
          FROM autorizacion.alcance_asignacion s WHERE s.role_assignment_id=a.id),'[]')::text scopes
        FROM autorizacion.asignacion_rol a JOIN autorizacion.rol r ON r.id=a.role_id
        WHERE a.project_id=:p AND a.project_membership_id=:m AND a.scope_type='PROJECT'
          AND r.code IN ('GESTOR_CATALOGO','ANALISTA_CALIDAD','RESPONSABLE_GOBIERNO','ANALISTA_NEGOCIO')
        """;
    private Asignacion assignment(ResultSet r,int n) throws SQLException {
        var revoked=r.getTimestamp("revoked_at"); return new Asignacion(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getObject("project_id",UUID.class),r.getObject("project_membership_id",UUID.class),r.getString("role_code"),r.getString("status"),r.getObject("assigned_by",UUID.class),r.getTimestamp("assigned_at").toInstant(),revoked==null?null:revoked.toInstant(),json.readValue(r.getString("scopes"),new TypeReference<List<Alcance>>(){}));
    }
    @Override public Optional<Asignacion> asignacion(UUID p,UUID m,UUID id) { return jdbc.sql(ASSIGNMENTS+" AND a.id=:id").param("p",p).param("m",m).param("id",id).query(this::assignment).optional(); }
    @Override public Optional<Asignacion> vigente(UUID p,UUID m) { return jdbc.sql(ASSIGNMENTS+" AND a.status='active' AND a.revoked_at IS NULL").param("p",p).param("m",m).query(this::assignment).optional(); }
    @Override public Asignacion asignar(UUID p,UUID m,UUID user,String role,List<Alcance> scopes,UUID actor) {
        UUID id=UUID.randomUUID();
        jdbc.sql("INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by) SELECT :id,:u,id,'PROJECT',:m,:p,:a FROM autorizacion.rol WHERE code=:r AND status='active'")
            .param("id",id).param("u",user).param("m",m).param("p",p).param("a",actor).param("r",role).update();
        for(var s:scopes) jdbc.sql("INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope,source_id,asset_id) VALUES(:id,:a,:p,:s,:source,:asset)")
            .param("id",UUID.randomUUID()).param("a",id).param("p",p).param("s",s.type().name())
            .param("source",s.type()==TipoAlcance.SOURCE?s.resourceId():null,Types.OTHER).param("asset",s.type()==TipoAlcance.ASSET?s.resourceId():null,Types.OTHER).update();
        return asignacion(p,m,id).orElseThrow();
    }
    @Override public void revocar(UUID id) { jdbc.sql("UPDATE autorizacion.asignacion_rol SET status='inactive',revoked_at=now() WHERE id=:id AND status='active' AND revoked_at IS NULL").param("id",id).update(); }
    @Override public List<Asignacion> historial(UUID p,UUID m,int limit,long offset) { return jdbc.sql(ASSIGNMENTS+" ORDER BY a.assigned_at,a.id LIMIT :l OFFSET :off").param("p",p).param("m",m).param("l",limit).param("off",offset).query(this::assignment).list(); }
    @Override public List<Contexto> contextos(UUID user,UUID project,int limit,long offset) {
        var sql=jdbc.sql("""
            SELECT o.id organization_id,o.name organization_name,p.id project_id,p.name project_name,p.status project_status,m.id membership_id,
              (SELECT r.code FROM autorizacion.asignacion_rol a JOIN autorizacion.rol r ON r.id=a.role_id WHERE a.project_membership_id=m.id AND a.status='active' AND a.revoked_at IS NULL AND r.status='active'
                AND r.code IN ('GESTOR_CATALOGO','ANALISTA_CALIDAD','RESPONSABLE_GOBIERNO','ANALISTA_NEGOCIO')) role_code
            FROM contexto.membresia_proyecto m JOIN contexto.membresia_organizacion om ON om.id=m.organization_membership_id
            JOIN contexto.proyecto p ON p.id=m.project_id JOIN contexto.organizacion o ON o.id=p.organization_id
            JOIN identidad.usuario u ON u.id=m.user_id WHERE m.user_id=:u AND m.status='active' AND om.status='active' AND u.status='active'
            AND o.status='active' AND p.status IN ('active','archived')
            """+(project==null?"":" AND p.id=:p")+" ORDER BY o.id,p.created_at,p.id LIMIT :l OFFSET :off").param("u",user).param("l",limit).param("off",offset);
        if(project!=null) sql=sql.param("p",project);
        return sql.query((r,n)->new Contexto(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getObject(6,UUID.class),r.getString(7))).list();
    }
    @Override public List<String> permisosProyecto(UUID user,UUID project) { return jdbc.sql("""
        SELECT DISTINCT p.code FROM autorizacion.asignacion_rol a JOIN autorizacion.rol r ON r.id=a.role_id
        JOIN autorizacion.rol_permiso rp ON rp.role_id=r.id JOIN autorizacion.permiso p ON p.id=rp.permission_id
        WHERE a.user_id=:u AND a.project_id=:p AND a.scope_type='PROJECT' AND a.status='active' AND a.revoked_at IS NULL AND r.status='active' AND p.available AND p.operation_scope='PROJECT' ORDER BY p.code
        """).param("u",user).param("p",project).query(String.class).list(); }
    @Override public void auditar(UUID actor,UUID org,UUID project,String type,UUID resource,String action,String reason,UUID subject) {
        Map<String,Object> details=new LinkedHashMap<>(); details.put("organization_id",org); details.put("reason",reason); if(subject!=null) details.put("subject_user_id",subject);
        jdbc.sql("INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,occurred_at,outcome,request_id,safe_details) VALUES(:id,'USER',:a,:p,:t,:r,:action,:at,'SUCCESS',:request,CAST(:details AS jsonb))")
            .param("id",UUID.randomUUID()).param("a",actor).param("p",project).param("t",type).param("r",resource).param("action",action).param("at",Timestamp.from(clock.instant())).param("request",correlacion.actual()).param("details",json.writeValueAsString(details)).update();
    }
}
