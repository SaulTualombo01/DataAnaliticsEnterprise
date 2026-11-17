package org.dataanaliticsent.Gobierno.comun.web;

import static org.dataanaliticsent.Gobierno.comun.web.RespuestasGobierno.objeto;

import java.util.Map;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.Evaluacion;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoPolitica;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.EntradaCronologia;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.ResumenActivo;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Grafo;
import org.dataanaliticsent.Gobierno.linaje.LinajeApi.Relacion;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.PoliticaAplicable;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Cambio;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Comparacion;
import org.dataanaliticsent.Gobierno.versionado.VersionadoApi.Version;

/**
 * Forma JSON (snake_case) de cada recurso de M3 en el contrato HTTP. Un solo lugar para que el
 * contrato y el código no se contradigan. Ver docs/contratos/m3/gobierno-http.openapi.json.
 */
public final class JsonGobierno {
    private JsonGobierno() { }

    public static Map<String, Object> politica(Politica p) {
        return objeto("id", p.id(), "project_id", p.projectId(), "name", p.name(), "description", p.description(),
                "category", p.category(), "owner_id", p.ownerId(), "status", p.status(), "revision", p.revision(),
                "current_version_id", p.currentVersionId(), "asset_ids", p.assetIds(),
                "rules", p.reglas().stream().map(r -> objeto("id", r.id(), "type", r.tipo(), "parameter", r.parametro())).toList(),
                "created_at", p.createdAt(), "created_by", p.createdBy(), "updated_at", p.updatedAt());
    }

    public static Map<String, Object> politicaAplicable(PoliticaAplicable a) {
        return objeto("policy", politica(a.politica()), "applicable", a.aplicable());
    }

    public static Map<String, Object> ficha(Ficha f) {
        return objeto("project_id", f.projectId(), "asset_id", f.assetId(), "owner_id", f.ownerId(),
                "classification", f.classification(), "revision", f.revision(),
                "current_version_id", f.currentVersionId(), "updated_at", f.updatedAt(), "updated_by", f.updatedBy());
    }

    public static Map<String, Object> evaluacion(Evaluacion e) {
        return objeto("id", e.id(), "project_id", e.projectId(), "asset_id", e.assetId(), "status", e.estado(),
                "evaluated_at", e.evaluatedAt(), "evaluated_by", e.evaluatedBy(),
                "profile_version_id", e.profileVersionId(), "audit_event_id", e.auditEventId(),
                "policies", e.politicas().stream().map(JsonGobierno::resultado).toList());
    }

    private static Map<String, Object> resultado(ResultadoPolitica p) {
        return objeto("policy_id", p.policyId(), "policy_name", p.policyName(), "policy_version_id", p.policyVersionId(),
                "status", p.estado(), "reason", p.motivo(),
                "rules", p.reglas().stream().map(r -> objeto("rule_id", r.ruleId(), "type", r.tipo(),
                        "parameter", r.parametro(), "status", r.estado(), "reason", r.motivo())).toList());
    }

    public static Map<String, Object> relacion(Relacion r) {
        return objeto("id", r.id(), "project_id", r.projectId(), "origin_asset_id", r.originAssetId(),
                "target_asset_id", r.targetAssetId(), "type", r.tipo(), "description", r.descripcion(),
                "status", r.estado(), "current_version_id", r.currentVersionId(), "created_at", r.createdAt(),
                "created_by", r.createdBy(), "retired_at", r.retiredAt(), "retired_by", r.retiredBy());
    }

    public static Map<String, Object> grafo(Grafo g) {
        return objeto("asset_id", g.assetId(), "direction", g.direccion(), "depth", g.profundidad(),
                "nodes", g.nodos().stream().map(n -> objeto("asset_id", n.assetId(), "distance", n.distancia())).toList(),
                "relations", g.relaciones().stream().map(JsonGobierno::relacion).toList(), "truncated", g.truncado());
    }

    /** safe_details se entrega como texto JSON tal como se conservó. */
    public static Map<String, Object> evento(Evento e) {
        return objeto("id", e.id(), "actor_type", e.actor().tipo(), "actor_id", e.actor().id(),
                "organization_id", e.organizationId(), "project_id", e.projectId(), "resource_type", e.resourceType(),
                "resource_id", e.resourceId(), "action", e.action(), "occurred_at", e.occurredAt(),
                "outcome", e.outcome(), "request_id", e.requestId(), "safe_details", e.safeDetailsJson());
    }

    /** state se entrega como texto JSON tal como se conservó. */
    public static Map<String, Object> version(Version v) {
        return objeto("id", v.id(), "project_id", v.projectId(), "resource_type", v.resourceType(),
                "resource_id", v.resourceId(), "number", v.number(), "previous_version_id", v.previousVersionId(),
                "created_at", v.createdAt(), "actor_type", v.actor().tipo(), "actor_id", v.actor().id(),
                "reason", v.reason(), "representation_version", v.representationVersion(), "state", v.estadoJson(),
                "content_hash", v.contentHash(), "audit_event_id", v.auditEventId());
    }

    public static Map<String, Object> comparacion(Comparacion c) {
        return objeto("from", version(c.desde()), "to", version(c.hasta()),
                "changes", c.cambios().stream().map(JsonGobierno::cambio).toList());
    }

    private static Map<String, Object> cambio(Cambio c) {
        return objeto("field", c.campo(), "change_type", c.tipo(), "old_value", c.valorAnterior(), "new_value", c.valorNuevo());
    }

    public static Map<String, Object> resumen(ResumenActivo r) {
        return objeto("project_id", r.projectId(), "asset_id", r.assetId(),
                "governance_profile", r.ficha() == null ? null : ficha(r.ficha()),
                "policies", r.politicas().stream().map(JsonGobierno::politicaAplicable).toList(),
                "latest_compliance", r.ultimaEvaluacion() == null ? null : evaluacion(r.ultimaEvaluacion()),
                "direct_lineage", r.linajeDirecto() == null ? null : grafo(r.linajeDirecto()),
                "unauthorized_sections", r.seccionesNoAutorizadas());
    }

    public static Map<String, Object> entrada(EntradaCronologia e) {
        return objeto("event", evento(e.evento()), "version_id", e.versionId(), "created_version", e.generoVersion());
    }
}
