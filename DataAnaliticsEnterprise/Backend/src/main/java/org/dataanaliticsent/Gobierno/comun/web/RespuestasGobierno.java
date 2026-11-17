package org.dataanaliticsent.Gobierno.comun.web;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.RevisionObsoletaException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Respuestas HTTP de M3 con la forma común del equipo: {"data": ..., "meta": {"request_id": ...}},
 * cabeceras X-Request-Id y Cache-Control: no-store, y ETag con la revisión cuando corresponde.
 * TEMPORAL en lo que respecta a request_id: al integrar se usará la correlación del tronco común.
 */
public final class RespuestasGobierno {
    public static final String BASE = "/api/v1/projects/{project_id}";
    private static final String ATRIBUTO_REQUEST_ID = RespuestasGobierno.class.getName() + ".request_id";

    private RespuestasGobierno() { }

    /** Identificador de la petición en curso; el mismo en cuerpo, cabecera y errores. */
    public static String requestId() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos == null) return UUID.randomUUID().toString();
        Object actual = atributos.getAttribute(ATRIBUTO_REQUEST_ID, RequestAttributes.SCOPE_REQUEST);
        if (actual instanceof String id) return id;
        String id = UUID.randomUUID().toString();
        atributos.setAttribute(ATRIBUTO_REQUEST_ID, id, RequestAttributes.SCOPE_REQUEST);
        return id;
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data) {
        return respuesta(HttpStatus.OK, data, Map.of(), null, null);
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data, long revision) {
        return respuesta(HttpStatus.OK, data, Map.of(), etag(revision), null);
    }

    public static ResponseEntity<Map<String, Object>> creado(URI ubicacion, Object data, Long revision) {
        return respuesta(HttpStatus.CREATED, data, Map.of(), revision == null ? null : etag(revision), ubicacion);
    }

    public static <T> ResponseEntity<Map<String, Object>> pagina(Pagina<T> pagina, Function<T, Object> aJson) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("page", pagina.page());
        info.put("page_size", pagina.pageSize());
        info.put("returned_count", pagina.items().size());
        info.put("has_next", pagina.hasNext());
        List<Object> datos = pagina.items().stream().map(aJson).toList();
        return respuesta(HttpStatus.OK, datos, Map.of("page_info", info), null, null);
    }

    /** ETag fuerte con la revisión: "3". */
    public static String etag(long revision) { return "\"" + revision + "\""; }

    /** Revisión enviada en If-Match. Ausente: 428. Ilegible: 412 (no coincide con ninguna revisión). */
    public static long revisionEsperada(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) throw new PrecondicionRequeridaException();
        String valor = ifMatch.strip();
        if (valor.startsWith("W/")) valor = valor.substring(2);
        if (valor.length() >= 2 && valor.startsWith("\"") && valor.endsWith("\"")) valor = valor.substring(1, valor.length() - 1);
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            throw new RevisionObsoletaException("El ETag enviado no corresponde a una revisión vigente");
        }
    }

    /**
     * Convierte valores a su forma JSON del contrato: UUID e instantes como texto ISO-8601 UTC,
     * enumeraciones por nombre. Conserva los null.
     */
    public static Object json(Object valor) {
        return switch (valor) {
            case null -> null;
            case UUID u -> u.toString();
            case Instant i -> i.toString();
            case Enum<?> e -> e.name();
            case Map<?, ?> m -> {
                Map<String, Object> copia = new LinkedHashMap<>();
                m.forEach((k, v) -> copia.put(String.valueOf(k), json(v)));
                yield copia;
            }
            case Collection<?> c -> {
                List<Object> copia = new ArrayList<>();
                c.forEach(v -> copia.add(json(v)));
                yield copia;
            }
            default -> valor;
        };
    }

    /** Mapa JSON ordenado a partir de pares clave, valor. */
    public static Map<String, Object> objeto(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) mapa.put((String) pares[i], json(pares[i + 1]));
        return mapa;
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, Object data,
            Map<String, Object> metaExtra, String etag, URI ubicacion) {
        String requestId = requestId();
        Map<String, Object> meta = new LinkedHashMap<>(metaExtra);
        meta.put("request_id", requestId);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("data", data);
        cuerpo.put("meta", meta);
        ResponseEntity.BodyBuilder b = ResponseEntity.status(estado)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Request-Id", requestId);
        if (etag != null) b.eTag(etag);
        if (ubicacion != null) b.location(ubicacion);
        return b.body(cuerpo);
    }
}
