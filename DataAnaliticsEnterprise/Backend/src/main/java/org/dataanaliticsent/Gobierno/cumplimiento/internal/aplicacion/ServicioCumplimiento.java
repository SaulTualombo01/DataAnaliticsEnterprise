package org.dataanaliticsent.Gobierno.cumplimiento.internal.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Actor;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.EventoNuevo;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.PuertoAutorizacion;
import org.dataanaliticsent.Gobierno.comun.PuertoCalidad;
import org.dataanaliticsent.Gobierno.comun.PuertoCalidad.EstadoCalidad;
import org.dataanaliticsent.Gobierno.comun.PuertoCatalogo;
import org.dataanaliticsent.Gobierno.comun.PuertoIdentidad;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.Transacciones;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.dominio.MotorCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.dominio.RepositorioEvaluaciones;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.PoliticaAplicable;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.TipoRegla;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServicioCumplimiento implements CumplimientoApi {
    static final String TIPO = "gobierno.evaluacion_cumplimiento";
    private static final String ACCION = "m3.compliance.evaluate";

    private final RepositorioEvaluaciones repositorio;
    private final PoliticasApi politicas;
    private final FichasGobiernoApi fichas;
    private final AuditoriaApi auditoria;
    private final PuertoIdentidad identidad;
    private final PuertoAutorizacion autorizacion;
    private final PuertoCatalogo catalogo;
    private final PuertoCalidad calidad;
    private final Transacciones transacciones;
    private final Clock reloj;

    @Autowired
    public ServicioCumplimiento(RepositorioEvaluaciones repositorio, PoliticasApi politicas, FichasGobiernoApi fichas,
            AuditoriaApi auditoria, PuertoIdentidad identidad, PuertoAutorizacion autorizacion, PuertoCatalogo catalogo,
            PuertoCalidad calidad, Transacciones transacciones) {
        this(repositorio, politicas, fichas, auditoria, identidad, autorizacion, catalogo, calidad, transacciones,
                Clock.systemUTC());
    }

    ServicioCumplimiento(RepositorioEvaluaciones repositorio, PoliticasApi politicas, FichasGobiernoApi fichas,
            AuditoriaApi auditoria, PuertoIdentidad identidad, PuertoAutorizacion autorizacion, PuertoCatalogo catalogo,
            PuertoCalidad calidad, Transacciones transacciones, Clock reloj) {
        this.repositorio = repositorio;
        this.politicas = politicas;
        this.fichas = fichas;
        this.auditoria = auditoria;
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.catalogo = catalogo;
        this.calidad = calidad;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    @Override
    public Evaluacion evaluar(UUID projectId, UUID assetId) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        Objects.requireNonNull(assetId, "assetId obligatorio");
        UUID actor = identidad.actorActual();
        try {
            autorizacion.exigir(actor, PermisosGobierno.CUMPLIMIENTO_EVALUAR, projectId);
        } catch (AccesoDenegadoGobiernoException e) {
            auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, projectId, TIPO, null, ACCION,
                    Resultado.DENIED, null, Map.of("asset_id", assetId)));
            throw e;
        }
        if (!catalogo.activoExiste(projectId, assetId)) {
            throw new RecursoNoEncontradoGobiernoException("El activo no está disponible en el proyecto");
        }

        List<Politica> aplicables = politicas.politicasDeActivo(projectId, assetId).stream()
                .filter(PoliticaAplicable::aplicable).map(PoliticaAplicable::politica).toList();
        Optional<Ficha> ficha = fichas.consultar(projectId, assetId);
        boolean usaCalidad = aplicables.stream().flatMap(p -> p.reglas().stream())
                .anyMatch(r -> r.tipo() == TipoRegla.CALIDAD_APROBADA);
        Optional<EstadoCalidad> estadoCalidad = usaCalidad ? calidad.estadoCalidad(projectId, assetId) : Optional.empty();

        List<ResultadoPolitica> resultados = aplicables.stream()
                .map(p -> MotorCumplimiento.evaluarPolitica(p, ficha, estadoCalidad)).toList();
        EstadoCumplimiento global = MotorCumplimiento.global(resultados);

        return transacciones.ejecutar(() -> {
            UUID id = UUID.randomUUID();
            UUID evento = auditoria.registrar(new EventoNuevo(Actor.usuario(actor), null, projectId, TIPO, id, ACCION,
                    Resultado.SUCCESS, null, Map.of("asset_id", assetId, "status", global,
                            "policies", resultados.size())));
            Evaluacion evaluacion = new Evaluacion(id, projectId, assetId, global,
                    Instant.now(reloj).truncatedTo(ChronoUnit.MICROS), actor,
                    ficha.map(Ficha::currentVersionId).orElse(null), resultados, evento);
            repositorio.guardar(evaluacion);
            return evaluacion;
        });
    }

    @Override
    public Optional<Evaluacion> ultima(UUID projectId, UUID assetId) {
        autorizarLectura(projectId, assetId);
        return repositorio.listar(projectId, assetId, 1, 0).stream().findFirst();
    }

    @Override
    public Pagina<Evaluacion> historial(UUID projectId, UUID assetId, int page, int pageSize) {
        Pagina.validar(page, pageSize);
        autorizarLectura(projectId, assetId);
        List<Evaluacion> encontradas = repositorio.listar(projectId, assetId, pageSize + 1, (page - 1) * pageSize);
        boolean hayMas = encontradas.size() > pageSize;
        return new Pagina<>(hayMas ? encontradas.subList(0, pageSize) : encontradas, page, pageSize, hayMas);
    }

    private void autorizarLectura(UUID projectId, UUID assetId) {
        Objects.requireNonNull(projectId, "projectId obligatorio");
        Objects.requireNonNull(assetId, "assetId obligatorio");
        autorizacion.exigir(identidad.actorActual(), PermisosGobierno.CUMPLIMIENTO_LEER, projectId);
    }
}
