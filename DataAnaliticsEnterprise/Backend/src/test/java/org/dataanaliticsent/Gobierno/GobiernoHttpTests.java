package org.dataanaliticsent.Gobierno;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.internal.aplicacion.ServicioAuditoria;
import org.dataanaliticsent.Gobierno.auditoria.internal.presentacion.AuditoriaController;
import org.dataanaliticsent.Gobierno.comun.DoblesGobierno;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.web.ErroresGobiernoAdvice;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.aplicacion.ServicioCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.presentacion.CumplimientoController;
import org.dataanaliticsent.Gobierno.fichas.internal.aplicacion.ServicioFichas;
import org.dataanaliticsent.Gobierno.fichas.internal.presentacion.FichasController;
import org.dataanaliticsent.Gobierno.historial.HistorialActivoApi.EntradaCronologia;
import org.dataanaliticsent.Gobierno.historial.internal.aplicacion.ServicioHistorial;
import org.dataanaliticsent.Gobierno.historial.internal.presentacion.HistorialController;
import org.dataanaliticsent.Gobierno.linaje.internal.aplicacion.ServicioLinaje;
import org.dataanaliticsent.Gobierno.linaje.internal.presentacion.LinajeController;
import org.dataanaliticsent.Gobierno.politicas.internal.aplicacion.ServicioPoliticas;
import org.dataanaliticsent.Gobierno.politicas.internal.presentacion.PoliticasController;
import org.dataanaliticsent.Gobierno.versionado.internal.aplicacion.ServicioVersionado;
import org.dataanaliticsent.Gobierno.versionado.internal.presentacion.VersionesController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Contrato HTTP de M3 con los servicios reales sobre repositorios en memoria. La identidad es de
 * prueba: en la aplicación real, mientras no se integre el tronco común, todo responde 403.
 */
class GobiernoHttpTests {
    private static final UUID P = DoblesGobierno.PROYECTO;
    private static final UUID CLIENTES = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID VENTAS = UUID.fromString("00000000-0000-4000-8000-0000000000d2");
    private static final String BASE = "/api/v1/projects/" + P;
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private final DoblesGobierno.Autorizacion autorizacion = new DoblesGobierno.Autorizacion();
    private MockMvc http;

    @BeforeEach
    void montar() throws IllegalAccessException {
        for (Field f : PermisosGobierno.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) autorizacion.conceder((String) f.get(null), P);
        }
        var catalogo = new DoblesGobierno.Catalogo().registrar(P, CLIENTES).registrar(P, VENTAS);
        var identidad = DoblesGobierno.identidad();
        var tx = DoblesGobierno.sinTransaccion();
        var auditoria = new ServicioAuditoria(new DoblesGobierno.Auditoria(), identidad, autorizacion);
        var versionado = new ServicioVersionado(new DoblesGobierno.Versiones(), identidad, autorizacion);
        var politicas = new ServicioPoliticas(new DoblesGobierno.Politicas(), auditoria, versionado, identidad, autorizacion, catalogo, tx);
        var fichas = new ServicioFichas(new DoblesGobierno.Fichas(), auditoria, versionado, identidad, autorizacion, catalogo, tx);
        var cumplimiento = new ServicioCumplimiento(new DoblesGobierno.Evaluaciones(), politicas, fichas, auditoria,
                identidad, autorizacion, catalogo, (p, a) -> Optional.empty(), tx);
        var linaje = new ServicioLinaje(new DoblesGobierno.Linaje(), auditoria, versionado, identidad, autorizacion, catalogo, tx);
        var historial = new ServicioHistorial((p, a, l, d) -> java.util.List.<EntradaCronologia>of(),
                fichas, politicas, cumplimiento, linaje, identidad, autorizacion, catalogo);
        http = MockMvcBuilders.standaloneSetup(new PoliticasController(politicas), new FichasController(fichas),
                        new CumplimientoController(cumplimiento), new LinajeController(linaje),
                        new AuditoriaController(auditoria), new VersionesController(versionado),
                        new HistorialController(historial))
                .setControllerAdvice(new ErroresGobiernoAdvice()).build();
    }

    private ResultActions crearPolitica(String nombre) throws Exception {
        return http.perform(post(BASE + "/governance-policies").contentType(JSON).content("""
                {"name":"%s","description":"Clientes con PII","category":"PRIVACIDAD","owner_id":"%s"}
                """.formatted(nombre, DoblesGobierno.ACTOR)));
    }

    private String idDe(MvcResult r) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.data.id");
    }

    @Test
    void crearPoliticaDevuelve201ConLocationEtagYFormaComun() throws Exception {
        MvcResult r = crearPolitica("Datos personales")
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Location", startsWith(BASE + "/governance-policies/")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.name").value("Datos personales"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.owner_id").value(DoblesGobierno.ACTOR.toString()))
                .andExpect(jsonPath("$.data.asset_ids", hasSize(0)))
                .andReturn();
        String requestId = com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.meta.request_id");
        assertEquals(r.getResponse().getHeader("X-Request-Id"), requestId);
    }

    @Test
    void erroresDeValidacionSeInformanJuntosPorCampo() throws Exception {
        http.perform(post(BASE + "/governance-policies").contentType(JSON)
                        .content("{\"category\":\"INVENTADA\",\"owner_id\":\"no-uuid\",\"extra\":1}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[?(@.field == '/extra')].code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors[?(@.field == '/name')].code").value("REQUIRED"));
        http.perform(post(BASE + "/governance-policies").contentType(JSON).content("{no es json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void edicionExigeIfMatchVigente() throws Exception {
        String id = idDe(crearPolitica("Retención").andReturn());
        String ruta = BASE + "/governance-policies/" + id;
        String cuerpo = "{\"description\":\"Cinco años\",\"reason\":\"Aclarar\"}";

        http.perform(patch(ruta).contentType("application/merge-patch+json").content(cuerpo))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        http.perform(patch(ruta).contentType("application/merge-patch+json").header("If-Match", "\"7\"").content(cuerpo))
                .andExpect(status().isPreconditionFailed());
        http.perform(patch(ruta).contentType("application/merge-patch+json").header("If-Match", "\"0\"").content(cuerpo))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.description").value("Cinco años"));
        http.perform(post(ruta + "/status-changes").contentType(JSON).header("If-Match", "\"1\"")
                        .content("{\"status\":\"INACTIVE\",\"reason\":\"Saltar\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void recursoInexistenteYSinPermiso() throws Exception {
        http.perform(get(BASE + "/governance-policies/" + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        http.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/governance-policies"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        http.perform(get(BASE + "/governance-policies/no-es-uuid"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void flujoDeCumplimientoParaM4() throws Exception {
        String id = idDe(crearPolitica("PII").andReturn());
        String ruta = BASE + "/governance-policies/" + id;
        String ficha = BASE + "/assets/" + CLIENTES + "/governance-profile";
        String cuerpoFicha = "{\"owner_id\":\"%s\",\"classification\":\"CONFIDENCIAL\",\"reason\":\"Alta\"}".formatted(DoblesGobierno.ACTOR);

        http.perform(put(ficha).contentType(JSON).content(cuerpoFicha))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""));
        http.perform(put(ficha).contentType(JSON).content(cuerpoFicha)).andExpect(status().isPreconditionFailed());
        http.perform(post(ruta + "/rules").contentType(JSON).header("If-Match", "\"0\"")
                .content("{\"type\":\"CLASIFICACION_MINIMA\",\"parameter\":\"CONFIDENCIAL\",\"reason\":\"PII\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rules[0].type").value("CLASIFICACION_MINIMA"));
        http.perform(post(ruta + "/assets").contentType(JSON).header("If-Match", "\"1\"")
                .content("{\"asset_id\":\"%s\",\"reason\":\"Clientes\"}".formatted(CLIENTES))).andExpect(status().isOk());
        http.perform(post(ruta + "/status-changes").contentType(JSON).header("If-Match", "\"2\"")
                .content("{\"status\":\"ACTIVE\",\"reason\":\"Aprobada\"}")).andExpect(status().isOk());

        http.perform(post(BASE + "/assets/" + CLIENTES + "/compliance-evaluations"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("COMPLIANT"));
        http.perform(get(BASE + "/assets/" + CLIENTES + "/compliance"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLIANT"))
                .andExpect(jsonPath("$.data.policies[0].rules[0].reason").value("CUMPLE"));
        http.perform(get(BASE + "/assets/" + CLIENTES + "/governance-policies"))
                .andExpect(jsonPath("$.data[0].applicable").value(true));
        http.perform(get(BASE + "/assets/" + CLIENTES + "/governance-summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.governance_profile.classification").value("CONFIDENCIAL"))
                .andExpect(jsonPath("$.data.unauthorized_sections", hasSize(0)));
    }

    @Test
    void linajeYGrafo() throws Exception {
        String relacion = "{\"origin_asset_id\":\"%s\",\"target_asset_id\":\"%s\",\"type\":\"TRANSFORMA\",\"reason\":\"ETL\"}";
        http.perform(post(BASE + "/lineage-relations").contentType(JSON).content(relacion.formatted(CLIENTES, VENTAS)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        http.perform(post(BASE + "/lineage-relations").contentType(JSON).content(relacion.formatted(VENTAS, CLIENTES)))
                .andExpect(status().isConflict());
        http.perform(get(BASE + "/assets/" + VENTAS + "/lineage").param("direction", "ORIGENES").param("depth", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodes", hasSize(2)))
                .andExpect(jsonPath("$.data.nodes[1].distance").value(-1));
        http.perform(get(BASE + "/assets/" + VENTAS + "/lineage").param("depth", "6"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("depth"));
    }

    @Test
    void auditoriaVersionesYComparacion() throws Exception {
        String id = idDe(crearPolitica("Calidad").andReturn());
        http.perform(patch(BASE + "/governance-policies/" + id).contentType(JSON).header("If-Match", "\"0\"")
                .content("{\"name\":\"Calidad mínima\",\"reason\":\"Renombrar\"}")).andExpect(status().isOk());

        http.perform(get(BASE + "/audit-events").param("page_size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.meta.page_info.has_next").value(true));
        http.perform(get(BASE + "/audit-events").param("page_size", "101")).andExpect(status().isUnprocessableContent());

        MvcResult versiones = http.perform(get(BASE + "/versions/gobierno.politica/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2))).andReturn();
        String json = versiones.getResponse().getContentAsString();
        String v2 = com.jayway.jsonpath.JsonPath.read(json, "$.data[0].id");
        String v1 = com.jayway.jsonpath.JsonPath.read(json, "$.data[1].id");
        http.perform(get(BASE + "/versions/gobierno.politica/" + id + "/comparison").param("from", v1).param("to", v2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changes[0].field").value("name"))
                .andExpect(jsonPath("$.data.changes[0].new_value").value("\"Calidad mínima\""));
        http.perform(get(BASE + "/versions/gobierno.politica/" + id + "/comparison").param("from", v1))
                .andExpect(status().isUnprocessableContent());
        http.perform(get(BASE + "/assets/" + CLIENTES + "/governance-history")).andExpect(status().isOk());
    }
}
