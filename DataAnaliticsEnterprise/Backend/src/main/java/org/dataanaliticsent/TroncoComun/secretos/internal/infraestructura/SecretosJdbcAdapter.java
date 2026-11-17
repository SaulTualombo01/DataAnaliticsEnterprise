package org.dataanaliticsent.TroncoComun.secretos.internal.infraestructura;
import java.util.*;
import org.dataanaliticsent.TroncoComun.secretos.*;
import org.dataanaliticsent.TroncoComun.secretos.SecretosApi.*;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.RepositorioSecretos;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class SecretosJdbcAdapter implements RepositorioSecretos {
    private final JdbcClient jdbc;
    public SecretosJdbcAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
    private final RowMapper<Entrada> mapper=(r,n)->new Entrada(new Referencia(r.getObject("id",UUID.class),
        r.getObject("project_id",UUID.class),r.getObject("source_id",UUID.class),r.getLong("revision"),Estado.valueOf(r.getString("status")),
        r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()),
        new DestinoPostgres(r.getString("target_host"),r.getInt("target_port"),r.getString("target_database"),r.getString("sslmode")),
        r.getString("key_id")==null?null:new Cifrado(r.getString("key_id"),r.getBytes("nonce"),r.getBytes("ciphertext")));
    public Optional<Entrada> buscar(UUID p,UUID s,UUID ref,boolean bloquear){
        return jdbc.sql("SELECT * FROM secretos.referencia WHERE project_id=:p AND source_id=:s AND id=:id"+(bloquear?" FOR UPDATE":""))
            .param("p",p).param("s",s).param("id",ref).query(mapper).optional();
    }
    public Referencia insertar(UUID p,UUID s,UUID ref,DestinoPostgres d,Cifrado c){
        return jdbc.sql("""
            INSERT INTO secretos.referencia(id,project_id,source_id,status,target_host,target_port,target_database,sslmode,key_id,nonce,ciphertext)
            VALUES(:id,:p,:s,'ACTIVE',:host,:port,:db,:ssl,:key,:nonce,:cipher) RETURNING *
            """).param("id",ref).param("p",p).param("s",s).param("host",d.host()).param("port",d.port()).param("db",d.database())
            .param("ssl",d.sslmode()).param("key",c.keyId()).param("nonce",c.nonce()).param("cipher",c.ciphertext()).query(mapper).single().referencia();
    }
    public Referencia actualizar(Entrada a,Estado estado,DestinoPostgres d,Cifrado c){
        var r=a.referencia();
        return jdbc.sql("""
            UPDATE secretos.referencia SET revision=revision+1,status=:state,target_host=:host,target_port=:port,target_database=:db,
            sslmode=:ssl,key_id=:key,nonce=:nonce,ciphertext=:cipher,updated_at=clock_timestamp()
            WHERE id=:id AND project_id=:p AND source_id=:s AND revision=:rev RETURNING *
            """).param("id",r.id()).param("p",r.projectId()).param("s",r.sourceId()).param("rev",r.revision()).param("state",estado.name())
            .param("host",d.host()).param("port",d.port()).param("db",d.database()).param("ssl",d.sslmode()).param("key",c==null?null:c.keyId())
            .param("nonce",c==null?null:c.nonce()).param("cipher",c==null?null:c.ciphertext()).query(mapper).single().referencia();
    }
    public void auditar(UUID actor,Referencia r,String accion,UUID request){
        jdbc.sql("""
            INSERT INTO auditoria.evento(id,actor_type,actor_id,project_id,resource_type,resource_id,action,occurred_at,outcome,request_id,safe_details)
            VALUES(:id,'USER',:actor,:p,'secretos.referencia',:ref,:action,clock_timestamp(),'SUCCESS',:request,
                jsonb_build_object('source_id',CAST(:source AS text),'revision',CAST(:rev AS bigint),'state',CAST(:state AS text)))
            """).param("id",UUID.randomUUID()).param("actor",actor).param("p",r.projectId()).param("ref",r.id()).param("action",accion)
            .param("request",request).param("source",r.sourceId()).param("rev",r.revision()).param("state",r.status().name()).update();
    }
}
