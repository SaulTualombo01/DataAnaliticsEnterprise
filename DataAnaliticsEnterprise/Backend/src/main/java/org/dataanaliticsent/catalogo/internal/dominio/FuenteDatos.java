package org.dataanaliticsent.catalogo.internal.dominio;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record FuenteDatos(UUID id, UUID projectId, String name, Tipo type, Estado status,
        Map<String, String> configuration, String credentialRef, UUID createdBy,
        Instant createdAt, Instant updatedAt, long revision) {

    public enum Tipo { POSTGRESQL, MYSQL, CSV, JSON, REST }
    public enum Estado { ENABLED, DISABLED, RETIRED }

    public FuenteDatos {
        Objects.requireNonNull(id); Objects.requireNonNull(projectId);
        Validacion.texto(name, "Nombre de fuente", 200);
        Objects.requireNonNull(type); Objects.requireNonNull(status);
        Objects.requireNonNull(createdBy); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (revision < 0 || updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Revisión o fechas inválidas");
        if (credentialRef != null) Validacion.texto(credentialRef, "Referencia de credencial", 128);
        configuration = Map.copyOf(configuration);
        Set<String> allowed = switch (type) {
            case POSTGRESQL, MYSQL -> Set.of("host", "port", "database");
            case CSV -> Set.of("file_ref", "encoding", "delimiter");
            case JSON -> Set.of("file_ref", "records_pointer");
            case REST -> Set.of("base_url", "records_pointer");
        };
        if (!allowed.containsAll(configuration.keySet())) {
            throw new IllegalArgumentException("Configuración no soportada o con secretos; utilizar credentialRef");
        }
        switch (type) {
            case POSTGRESQL, MYSQL -> {
                required(configuration, "host"); required(configuration, "database");
                int port = Integer.parseInt(required(configuration, "port"));
                if (port < 1 || port > 65535) throw new IllegalArgumentException("Puerto inválido");
            }
            case CSV -> {
                UUID.fromString(required(configuration, "file_ref"));
                if (!"utf-8".equals(required(configuration, "encoding"))) throw new IllegalArgumentException("Encoding no soportado");
                if (required(configuration, "delimiter").length() != 1) throw new IllegalArgumentException("Delimitador inválido");
            }
            case JSON -> {
                UUID.fromString(required(configuration, "file_ref"));
                Objects.requireNonNull(configuration.get("records_pointer"), "records_pointer");
            }
            case REST -> {
                URI uri = URI.create(required(configuration, "base_url"));
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getQuery() != null || uri.getFragment() != null) {
                    throw new IllegalArgumentException("URL REST inválida o con credenciales");
                }
                Objects.requireNonNull(configuration.get("records_pointer"), "records_pointer");
            }
        }
    }

    private static String required(Map<String, String> values, String key) {
        return Validacion.texto(values.get(key), key, 1000);
    }

    @Override
    public String toString() {
        return "FuenteDatos[id=" + id + ", projectId=" + projectId + ", type=" + type + ", status=" + status + "]";
    }
}
