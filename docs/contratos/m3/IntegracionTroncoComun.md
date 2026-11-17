# M3 — Integración con el tronco común

17 de noviembre de 2026. Se integró `origin/modulo0_troncoComun` en `modulo3_gobierno`, como hizo
M4 en `modulo4_analitica`, para usar la sesión y los permisos reales y poder montar la pantalla
de Gobierno en el contenedor web.

## Qué cambió en M3

| Antes (sustituto) | Ahora |
| --- | --- |
| `IdentidadSinTroncoComun`: negaba todo | `IdentidadTroncoComun` sobre `IdentidadActualApi` (sesión `TICSESSION`) |
| `AutorizacionSinTroncoComun`: negaba todo | `AutorizacionTroncoComun` sobre `AutorizacionApi`, ámbito PROJECT |
| Permisos solo en `PermisosGobierno` | Alta en `V34__gobierno_permisos.sql` (14 permisos `m3.*`) |
| Sin sesión respondía 403 | 401 `AUTHENTICATION_REQUIRED` |

Siguen simulados: `CatalogoSinModulo1`, porque M1 aún no está en el tronco, y
`CalidadSinModulo2`, porque M2 aún no está integrado.

### Permisos por rol (`V34`)

| Rol | Permisos `m3.*` |
| --- | --- |
| `RESPONSABLE_GOBIERNO` | Los 14 |
| `GESTOR_CATALOGO`, `ANALISTA_CALIDAD`, `ANALISTA_NEGOCIO` | Solo lectura: `policies.read`, `asset_profiles.read`, `compliance.read`, `lineage.read` |
| `ADMIN_SISTEMA`, `ADMIN_EMPRESA` | Ninguno: administrar no da acceso funcional |

Las lecturas siguen disponibles en un proyecto archivado (`archived_read`); las escrituras no.
`ContratoPermisosTests` exige que los códigos de `PermisosGobierno` y los de `V34` sean los mismos.

## Cambios en archivos de otros (para revisar con David)

Son cambios mínimos, del mismo tipo que hizo la integración de M4:

1. **`TroncoComun/identidad/internal/infraestructura/SeguridadWebConfig.java`**: un bloque
   `requestMatchers(...).authenticated()` con las rutas de M3, antes de `anyRequest().denyAll()`.
   Sin él, el tronco rechaza toda ruta de M3.
2. **`TroncoComun/TroncoComunPostgresTests.java`**: la prueba que cuenta 77 permisos ahora cuenta
   los que **no** son `m3.*`. Así su comprobación sigue intacta y no hay que cambiar el número
   cada vez que un módulo añade permisos (con M4 se cambió de 55 a 56). Los permisos de M3 se
   comprueban en `GobiernoPostgresTests`.
3. **`application.properties`**: era el único conflicto de la unión; se dejó la versión del tronco.

## Migraciones en esta rama

`V1`, `V1_1`, `V1_2`, `V3`, `V4`, `V5`, `V6` (tronco) y `V30`–`V34` (M3). Flyway las aplica en
ese orden. Al unir con `main` habrá que resolver la `V4` duplicada de `main`
(`V4__permiso_evaluar_kpis_m4`), que no es de M3.

## Pruebas

216 aprobadas con PostgreSQL 18.7 (ver `PruebasPostgres.md`): las del tronco común sin cambios de
comportamiento, las de M3 y `GobiernoSeguridadPostgresTests`, que usa usuarios, sesión, CSRF y roles
reales:

- sin sesión: 401;
- el responsable de gobierno crea, audita, define fichas y evalúa; sin CSRF, 403;
- el analista de negocio lee el cumplimiento (lo que necesita M4) pero no crea políticas ni ve la
  auditoría;
- un usuario de otro proyecto no ve nada.
