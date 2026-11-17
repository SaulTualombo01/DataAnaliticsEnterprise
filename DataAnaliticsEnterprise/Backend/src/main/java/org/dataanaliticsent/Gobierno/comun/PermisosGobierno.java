package org.dataanaliticsent.Gobierno.comun;

/**
 * Códigos de permiso de M3, con el formato del catálogo RBAC del tronco común.
 * Pendiente al integrar: darlos de alta en autorizacion.permiso y asignarlos al rol
 * RESPONSABLE_GOBIERNO (esa tabla no existe en esta rama).
 */
public final class PermisosGobierno {
    public static final String AUDITORIA_LEER = "m3.audit.read";
    public static final String HISTORIAL_LEER = "m3.history.read";
    public static final String POLITICAS_LEER = "m3.policies.read";
    public static final String POLITICAS_CREAR = "m3.policies.create";
    public static final String POLITICAS_ACTUALIZAR = "m3.policies.update";
    public static final String POLITICAS_CICLO_VIDA = "m3.policies.lifecycle";
    public static final String POLITICAS_ACTIVOS = "m3.policies.assets.manage";

    private PermisosGobierno() { }
}
