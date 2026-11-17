package org.dataanaliticsent.Gobierno.auditoria.internal.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.comun.ReglasAuditoria;
import org.dataanaliticsent.Gobierno.auditoria.internal.dominio.RepositorioAuditoria;
import org.dataanaliticsent.Gobierno.comun.RepresentacionCanonica;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServicioAuditoria implements AuditoriaApi {
    private final RepositorioAuditoria repositorio;
    private final PuertoIdentidad identidad;
    private final PuertoAutorizacion autorizacion;
    private final Clock reloj;

    @Autowired
    public ServicioAuditoria(RepositorioAuditoria repositorio, PuertoIdentidad identidad, PuertoAutorizacion autorizacion) {
        this(repositorio, identidad, autorizacion, Clock.systemUTC());
    }

    ServicioAuditoria(RepositorioAuditoria repositorio, PuertoIdentidad identidad, PuertoAutorizacion autorizacion, Clock reloj) {
        this.repositorio = repositorio;
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.reloj = reloj;
    }

    @Override
    public UUID registrar(EventoNuevo nuevo) {
        ReglasAuditoria.validarTipoRecurso(nuevo.resourceType());
        ReglasAuditoria.validarAccion(nuevo.action());
        ReglasAuditoria.validarSinSecretos(nuevo.safeDetails());
        String detalles = RepresentacionCanonica.json(nuevo.safeDetails());
        // Precisión de microsegundos, la misma que conserva PostgreSQL.
        Instant ahora = Instant.now(reloj).truncatedTo(ChronoUnit.MICROS);
        UUID id = UUID.randomUUID();
        repositorio.guardar(new Evento(id, nuevo.actor(), nuevo.organizationId(), nuevo.projectId(),
                nuevo.resourceType(), nuevo.resourceId(), nuevo.action(), ahora, nuevo.outcome(),
                nuevo.requestId(), detalles));
        return id;
    }

    @Override
    public Pagina<Evento> consultar(Filtro filtro, int page, int pageSize) {
        Pagina.validar(page, pageSize);
        if (filtro.action() != null) ReglasAuditoria.validarAccion(filtro.action());
        if (filtro.resourceType() != null) ReglasAuditoria.validarTipoRecurso(filtro.resourceType());
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.AUDITORIA_LEER, filtro.projectId());
        List<Evento> encontrados = repositorio.buscar(filtro, pageSize + 1, (page - 1) * pageSize);
        boolean hayMas = encontrados.size() > pageSize;
        return new Pagina<>(hayMas ? encontrados.subList(0, pageSize) : encontrados, page, pageSize, hayMas);
    }
}
