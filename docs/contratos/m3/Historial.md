# M3 — Historial del activo y comparación de versiones (fase 5, parte A)

Rama `modulo3_gobierno`. Describe lo implementado; si cambia el código, se actualiza este
archivo. Cubre RF-M3.4.3 (seguimiento de modificaciones), RF-M3.6.1 (estado e historial de un
activo) y la comparación de versiones que el tronco común dejó a criterio de M3.

## Comparar dos versiones

`VersionadoApi.comparar(proyecto, tipo, recurso, desde, hasta)`, permiso `m3.history.read`.

- Sirve para cualquier recurso versionado: política (`gobierno.politica`), ficha
  (`gobierno.ficha_activo`), relación de linaje (`gobierno.relacion_linaje`) o recursos de otros
  módulos cuando usen el servicio.
- Devuelve las dos versiones y la lista de **cambios** por campo, ordenados por nombre:
  - `AGREGADO`: el campo no existía en `desde`.
  - `ELIMINADO`: el campo no existe en `hasta`.
  - `MODIFICADO`: cambió el valor.
- Los valores van en JSON canónico (`"DRAFT"`, `["a1"]`, `null`). Listas y objetos se comparan
  completos. `1` y `1.0` se consideran iguales.
- Comparar una versión consigo misma da una lista vacía. Una versión de otro recurso o proyecto
  se trata como inexistente (404).

Ejemplo, ficha de `clientes` v1 → v2: `classification` `MODIFICADO`, de `null` a `"RESTRINGIDA"`.

## Resumen del activo

`HistorialActivoApi.resumen(proyecto, activo)`, permiso `m3.history.read`. Reúne en una
consulta:

| Sección | Contenido | Permiso propio |
| --- | --- | --- |
| `FICHA` | Responsable y clasificación vigentes | `m3.asset_profiles.read` |
| `POLITICAS` | Políticas asociadas y si aplican | `m3.policies.read` |
| `CUMPLIMIENTO` | Última evaluación | `m3.compliance.read` |
| `LINAJE` | Orígenes y destinos directos (1 nivel) | `m3.lineage.read` |

Si el actor no tiene el permiso de una sección, esa sección queda vacía y aparece en
`secciones_no_autorizadas`. El resumen nunca muestra algo que el actor no podría ver por
separado. El activo debe existir en el catálogo de M1.

## Cronología del activo

`HistorialActivoApi.cronologia(proyecto, activo, page, pageSize)`, permisos `m3.history.read` y
`m3.audit.read`.

- Lista los eventos de auditoría relacionados con el activo, del más reciente al más antiguo:
  - cambios de su ficha (el recurso es el activo);
  - políticas que lo asociaron o retiraron (`asset_id` en los detalles);
  - sus evaluaciones de cumplimiento;
  - relaciones de linaje donde es origen o destino.
- Cada entrada indica si el hecho **generó una versión** (`version_id`) o fue solo una operación
  registrada, como una evaluación o un intento denegado. Así se distinguen las operaciones
  registradas de los cambios efectivos, como pide RF-M3.4.3.
- Paginación común: `page` desde 1 y `page_size` de 1 a 100.

## Pruebas

- `ComparadorVersionesTests` (2): tipos de cambio y valores numéricos equivalentes.
- `ServicioVersionadoTests`: 1 prueba nueva de comparación.
- `ServicioHistorialTests` (4): resumen completo, secciones sin permiso, permisos y activo
  inexistente, cronología.
- `HistorialPostgresTests` (etiqueta `postgres`, aprobadas en PostgreSQL 18.7 el 2026-11-17; ver `PruebasPostgres.md`): búsqueda en `jsonb` de la
  cronología y comparación de versiones leídas de PostgreSQL.
