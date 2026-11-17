package org.dataanaliticsent.Gobierno.comun;

/**
 * Códigos de permiso de M3, con el formato del catálogo RBAC del tronco común.
 * Dados de alta en V34__gobierno_permisos.sql (ContratoPermisosTests comprueba que coincidan);
 * RESPONSABLE_GOBIERNO recibe todos y los demás roles funcionales solo sus lecturas.
 */
public final class PermisosGobierno {
    public static final String AUDITORIA_LEER = "m3.audit.read";
    public static final String HISTORIAL_LEER = "m3.history.read";
    public static final String POLITICAS_LEER = "m3.policies.read";
    public static final String POLITICAS_CREAR = "m3.policies.create";
    public static final String POLITICAS_ACTUALIZAR = "m3.policies.update";
    public static final String POLITICAS_CICLO_VIDA = "m3.policies.lifecycle";
    public static final String POLITICAS_ACTIVOS = "m3.policies.assets.manage";
    public static final String POLITICAS_REGLAS = "m3.policies.rules.manage";
    public static final String FICHAS_LEER = "m3.asset_profiles.read";
    public static final String FICHAS_ACTUALIZAR = "m3.asset_profiles.update";
    public static final String CUMPLIMIENTO_LEER = "m3.compliance.read";
    public static final String CUMPLIMIENTO_EVALUAR = "m3.compliance.evaluate";
    public static final String LINAJE_LEER = "m3.lineage.read";
    public static final String LINAJE_ADMINISTRAR = "m3.lineage.manage";

    private PermisosGobierno() { }
}
