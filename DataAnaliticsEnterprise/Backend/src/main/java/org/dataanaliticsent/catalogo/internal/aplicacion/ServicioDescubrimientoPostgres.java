package org.dataanaliticsent.catalogo.internal.aplicacion;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.catalogo.DescubrimientoPostgresApi;
import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.springframework.stereotype.Service;

@Service
public class ServicioDescubrimientoPostgres implements DescubrimientoPostgresApi {
    private final IdentidadActualApi identidad;
    private final AutorizacionApi autorizacion;
    private final RepositorioCatalogo datos;
    private final ExtractorPostgres extractor;
    private final PublicadorDescubrimiento publicador;
    private final Clock reloj;
    private final LimitesDescubrimiento limites;
    public ServicioDescubrimientoPostgres(IdentidadActualApi identidad, AutorizacionApi autorizacion,
            RepositorioCatalogo datos, ExtractorPostgres extractor, PublicadorDescubrimiento publicador,
            Clock reloj, LimitesDescubrimiento limites) {
        this.identidad=identidad; this.autorizacion=autorizacion; this.datos=datos;
        this.extractor=extractor; this.publicador=publicador; this.reloj=reloj; this.limites=limites;
    }

    @Override
    public Resultado descubrir(Solicitud solicitud) {
        UUID actor = identidad.usuarioId();
        autorizacion.exigir(actor,SolicitudAcceso.recurso("m1.discovery.execute",solicitud.projectId(),
                SolicitudAcceso.TipoRecurso.SOURCE,solicitud.sourceId()));
        if (solicitud.schemas().isEmpty() || solicitud.schemas().size() > limites.getMaxSchemas()
                || new HashSet<>(solicitud.schemas()).size() != solicitud.schemas().size()
                || solicitud.schemas().stream().anyMatch(s -> s.isBlank() || s.length()>500
                    || s.startsWith("pg_") || s.equals("information_schema"))) {
            throw new IllegalArgumentException("Esquemas de descubrimiento inválidos");
        }
        FuenteDatos fuente = datos.buscarFuente(solicitud.projectId(),solicitud.sourceId()).orElseThrow();
        if (fuente.type() != FuenteDatos.Tipo.POSTGRESQL || fuente.status() != FuenteDatos.Estado.ENABLED) {
            throw new IllegalArgumentException("El descubrimiento exige fuente PostgreSQL habilitada");
        }
        // El extractor cierra su transacción de solo lectura antes de publicar en la plataforma.
        List<ExtractorPostgres.Tabla> tablas = extractor.extraer(fuente,solicitud.schemas());
        UUID requestId = UUID.randomUUID();
        var ahora = reloj.instant();
        Map<LocalizadorTabla,UUID> ids = new HashMap<>();
        for (var tabla : tablas) {
            if (ids.put(tabla.locator(),UUID.randomUUID()) != null) throw new IllegalArgumentException("Tabla repetida en metadatos");
        }
        List<EstadoCatalogo> estados = new ArrayList<>();
        List<TablaRegistrada> recibos = new ArrayList<>();
        for (var tabla : tablas) {
            UUID asset = ids.get(tabla.locator()), version = UUID.randomUUID();
            var activo = new ActivoDatos(asset,fuente.projectId(),fuente.id(),tabla.locator().name(),ActivoDatos.Tipo.TABLE,
                    tabla.locator().namespace(),tabla.locator().name(),ActivoDatos.Estado.ACTIVE,ActivoDatos.Origen.DECLARED,
                    null,List.of(),version,ahora,ahora,0);
            List<CampoDatos> campos = tabla.columns().stream().map(c -> new CampoDatos(UUID.randomUUID(),fuente.projectId(),asset,
                    c.name(),c.name(),c.position(),c.nativeType(),c.logicalType(),c.nullable(),null,List.of(),CampoDatos.Estado.ACTIVE)).toList();
            Map<String,UUID> fieldIds = new HashMap<>();
            campos.forEach(c -> fieldIds.put(c.name(),c.id()));
            List<RestriccionDatos> restricciones = tabla.keys().stream().map(k -> {
                UUID target = k.targetAccessible() ? ids.get(k.target()) : null;
                var resolution = k.kind()!=RestriccionDatos.Tipo.FK || target!=null ? RestriccionDatos.Resolucion.RESOLVED
                        : k.targetAccessible() ? RestriccionDatos.Resolucion.UNRESOLVED : RestriccionDatos.Resolucion.NOT_ACCESSIBLE;
                return new RestriccionDatos(UUID.randomUUID(),fuente.projectId(),asset,k.name(),k.kind(),target,k.target(),resolution,
                        k.members().stream().map(m -> new RestriccionDatos.Miembro(m.position(),fieldIds.get(m.sourceFieldName()),m.targetFieldName())).toList());
            }).toList();
            estados.add(new EstadoCatalogo(activo,campos,restricciones));
            recibos.add(new TablaRegistrada(asset,version,tabla.locator().namespace(),tabla.locator().name(),campos.size(),restricciones.size()));
        }
        publicador.publicar(actor,fuente,solicitud.schemas(),List.copyOf(estados),requestId,ahora);
        return new Resultado(requestId,fuente.projectId(),fuente.id(),ahora,recibos);
    }
}
