# M3 — API HTTP (fase 5, parte B)

Rama `modulo3_gobierno`. El contrato completo es [gobierno-http.openapi.json](gobierno-http.openapi.json)
(OpenAPI 3.1.1, JSON Schema 2020-12) y hay ejemplos para mocks en
[gobierno.examples.json](gobierno.examples.json). **`ContratoHttpTests` falla si una ruta del
código no está en el contrato o al revés**, así que el contrato no puede quedar desactualizado.

Para regenerar el contrato después de cambiar rutas o campos:

```bash
node docs/contratos/m3/generar-openapi.js docs/contratos/m3/gobierno-http.openapi.json
```

## Rutas

Todas bajo `/api/v1/projects/{project_id}`.

| Recurso | Método y ruta | Uso |
| --- | --- | --- |
| Políticas | `GET /governance-policies` | Buscar (`q`, `status`, `category`, `owner_id`, `asset_id`) |
| | `POST /governance-policies` | Crear (201, `Location`, `ETag`) |
| | `GET /governance-policies/{policy_id}` | Consultar (`ETag`) |
| | `PATCH /governance-policies/{policy_id}` | Editar (JSON Merge Patch, `If-Match`) |
| | `POST .../{policy_id}/status-changes` | Cambiar estado (`If-Match`) |
| | `POST .../{policy_id}/assets` y `.../asset-removals` | Asociar o quitar activo (`If-Match`) |
| | `POST .../{policy_id}/rules` y `.../rule-removals` | Añadir o quitar regla (`If-Match`) |
| | `GET /assets/{asset_id}/governance-policies` | Políticas del activo y si aplican |
| Ficha | `GET/PUT /assets/{asset_id}/governance-profile` | Responsable y clasificación (PUT sin `If-Match` crea) |
| Cumplimiento | `POST /assets/{asset_id}/compliance-evaluations` | Evaluar ahora (201) |
| | `GET /assets/{asset_id}/compliance-evaluations` | Evaluaciones anteriores |
| | `GET /assets/{asset_id}/compliance` | **Estado vigente: la ruta que usará M4** |
| Linaje | `POST /lineage-relations` | Registrar relación (409 si crea ciclo) |
| | `GET /lineage-relations/{relation_id}` | Consultar |
| | `POST /lineage-relations/{relation_id}/retirements` | Retirar |
| | `GET /assets/{asset_id}/lineage?direction=&depth=` | Grafo (`ORIGENES`, `DESTINOS`, `AMBOS`; 1–5) |
| Auditoría | `GET /audit-events` | Eventos (`actor_id`, `action`, `resource_type`, `resource_id`, `outcome`, `from`, `to`) |
| Versiones | `GET /versions/{resource_type}/{resource_id}` | Historial de versiones |
| | `GET /versions/{resource_type}/{resource_id}/{version_id}` | Una versión |
| | `GET /versions/{resource_type}/{resource_id}/comparison?from=&to=` | Comparar dos versiones |
| Historial | `GET /assets/{asset_id}/governance-summary` | Resumen del activo |
| | `GET /assets/{asset_id}/governance-history` | Cronología del activo |

Registrar eventos de auditoría y crear versiones no tiene ruta HTTP: es solo para los módulos,
mediante la API Java (`AuditoriaApi`, `VersionadoApi`).

## Convenciones aplicadas

Las de `Convenciones_API_JSON_Schema_IDs_Errores` y `Decisiones_Contratos_v1` del equipo:

- Campos en `snake_case`, UUID en minúsculas, fechas UTC terminadas en `Z`.
- Éxito: `{"data": ..., "meta": {"request_id": ...}}`; las listas añaden `meta.page_info` con
  `page`, `page_size`, `returned_count` y `has_next` (sin total inventado).
- Cabeceras `X-Request-Id` (igual a `request_id`) y `Cache-Control: no-store`.
- Errores Problem Details (`application/problem+json`) con `code` y `request_id`:

| HTTP | `code` | Cuándo |
| --- | --- | --- |
| 400 | `INVALID_JSON` | Cuerpo ilegible |
| 403 | `ACCESS_DENIED` | Sin permiso en el proyecto |
| 404 | `RESOURCE_NOT_FOUND` | No existe en ese proyecto (no revela si existe en otro) |
| 409 | `CONFLICT` | Nombre repetido, transición no permitida, ciclo de linaje |
| 412 | `PRECONDITION_FAILED` | `If-Match` no coincide con la revisión vigente |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` distinto de JSON |
| 422 | `VALIDATION_ERROR` | Campos, query o ruta inválidos; `errors[]` con `location`, `field` y `code` |
| 428 | `PRECONDITION_REQUIRED` | Falta `If-Match` en una edición |
| 500 | `INTERNAL_ERROR` | Fallo inesperado; el detalle queda solo en el log del servidor |

- Los cuerpos rechazan campos desconocidos (`UNKNOWN_FIELD`) y los errores se informan todos
  juntos. `page_size` mayor que 100 o `depth` fuera de 1–5 se rechazan; nunca se corrigen solos.
- Valores de enumeración tal como están en el código (por ejemplo `PRIVACIDAD`, `ORIGENES`), para
  que contrato y código usen los mismos nombres.

## Seguridad

Seguridad real del tronco común: sesión `TICSESSION`, CSRF en las escrituras y permisos `m3.*` por
proyecto (`V34`). Sin sesión, la respuesta es 401 `AUTHENTICATION_REQUIRED`; sin el permiso, 403
`ACCESS_DENIED`; sin CSRF en una escritura, 403 `CSRF_INVALID`.

Las rutas de M3 se abrieron con sesión en `SeguridadWebConfig` del tronco, un cambio de un solo
bloque (ver `IntegracionTroncoComun.md`). `GobiernoHttpTests` sigue usando una identidad de prueba
sin base de datos; `GobiernoSeguridadPostgresTests` prueba las mismas rutas con usuarios reales.

## Pendiente al integrar

1. Usar la correlación del tronco (`ProblemasHttp`, `CorrelacionHttpFilter`) en lugar del
   `request_id` propio de `RespuestasGobierno`, y pasarlo a los eventos de auditoría.

## Pruebas

- `GobiernoHttpTests` (7): creación con `Location` y `ETag`, errores de validación agrupados,
  JSON ilegible, `If-Match` (428/412/200), conflicto, 404, 403, flujo de cumplimiento para M4,
  linaje con ciclo y grafo, auditoría paginada, versiones y comparación, resumen y cronología.
- `ContratoHttpTests` (1): rutas del código = rutas del contrato.
