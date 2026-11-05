package org.dataanaliticsent.identidad;

import java.util.UUID;

/** Fachada interna: el actor se obtiene del contexto de seguridad, nunca de una petición libre. */
public interface IdentidadActualApi {
    record Perfil(UUID id,String email,String display_name) { }
    UUID usuarioId();
    Perfil perfil();
    void cambiarClave(String claveActual,String claveNueva);
    void revocarSesiones(UUID usuarioObjetivo);
}
