package org.dataanaliticsent.autorizacion;

import java.util.Objects;
import java.util.UUID;

/** Parámetros internos: el caso de uso determina la operación y el recurso real. */
public record SolicitudAcceso(String permiso, Ambito ambito, UUID proyectoId, UUID titularId, Recurso recurso) {
    public enum Ambito { SELF, PLATFORM, PROJECT }
    public enum TipoRecurso { SOURCE, ASSET }

    public record Recurso(TipoRecurso tipo, UUID id) {
        public Recurso { Objects.requireNonNull(tipo); Objects.requireNonNull(id); }
    }

    public SolicitudAcceso {
        Objects.requireNonNull(permiso); Objects.requireNonNull(ambito);
        if (permiso.length() > 200 || !permiso.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
            throw new IllegalArgumentException("Código de permiso inválido");
        }
        boolean valido = switch (ambito) {
            case SELF -> titularId != null && proyectoId == null && recurso == null;
            case PLATFORM -> titularId == null && proyectoId == null && recurso == null;
            case PROJECT -> titularId == null && proyectoId != null;
        };
        if (!valido) throw new IllegalArgumentException("Contexto de autorización incoherente");
    }

    public static SolicitudAcceso plataforma(String permiso) {
        return new SolicitudAcceso(permiso, Ambito.PLATFORM, null, null, null);
    }
    public static SolicitudAcceso propia(String permiso, UUID titularId) {
        return new SolicitudAcceso(permiso, Ambito.SELF, null, titularId, null);
    }
    public static SolicitudAcceso proyecto(String permiso, UUID proyectoId) {
        return new SolicitudAcceso(permiso, Ambito.PROJECT, proyectoId, null, null);
    }
    public static SolicitudAcceso recurso(String permiso, UUID proyectoId, TipoRecurso tipo, UUID id) {
        return new SolicitudAcceso(permiso, Ambito.PROJECT, proyectoId, null, new Recurso(tipo, id));
    }
}
