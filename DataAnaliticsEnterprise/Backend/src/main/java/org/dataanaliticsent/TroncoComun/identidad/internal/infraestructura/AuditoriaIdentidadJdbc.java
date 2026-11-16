package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.util.UUID;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.AuditoriaIdentidad;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AuditoriaIdentidadJdbc implements AuditoriaIdentidad {
    private final JdbcClient jdbc;
    public AuditoriaIdentidadJdbc(JdbcClient jdbc) { this.jdbc=jdbc; }
    public void registrar(UUID actor,UUID recurso,String accion,String resultado,UUID requestId) {
        jdbc.sql("""
                INSERT INTO auditoria.evento(id,actor_type,actor_id,resource_type,resource_id,action,occurred_at,outcome,request_id)
                VALUES(:id,:tipo,:actor,'identidad.usuario',:recurso,:accion,now(),:resultado,:request)
                """).param("id",UUID.randomUUID()).param("tipo",actor==null?"ANONYMOUS":"USER")
                .param("actor",actor).param("recurso",recurso).param("accion",accion).param("resultado",resultado).param("request",requestId).update();
    }
}
