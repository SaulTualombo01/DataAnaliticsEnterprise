# M3 — Auditoría y versiones (fase 1)

Rama `modulo3_gobierno`. Describe lo implementado en el código; si cambia el código, se
actualiza este archivo. Esta rama todavía **no integra el tronco común**.

## Estructura del módulo

Igual que el tronco común: un subcomponente por carpeta, con su API pública en la raíz y
su implementación privada en `internal/{aplicacion,dominio,infraestructura,presentacion}`.

```
org.dataanaliticsent.Gobierno
├── auditoria/   AuditoriaApi            + internal/
├── versionado/  VersionadoApi, ConflictoVersionException + internal/
└── comun/       Pagina, PermisosGobierno, AccesoDenegadoGobiernoException,
                 PuertoIdentidad, PuertoAutorizacion, RepresentacionCanonica,
                 ReglasAuditoria + internal/infraestructura/sustitutos
```

`comun` reúne lo que comparten los subcomponentes de M3. Ningún subcomponente importa el
`internal` de otro.

## API Java pública

Los demás módulos usan solo estas interfaces y sus records; nada de `internal`.

| Interfaz | Operación | Permiso |
| --- | --- | --- |
| `AuditoriaApi` | `registrar(EventoNuevo)` → UUID del evento | Ninguno: el módulo que llama ya autorizó su operación |
| `AuditoriaApi` | `consultar(Filtro, page, pageSize)` → `Pagina<Evento>` | `m3.audit.read` en el proyecto |
| `VersionadoApi` | `versionar(SolicitudVersion)` → `ResultadoVersion` | Ninguno, igual que `registrar` |
| `VersionadoApi` | `historial(proyecto, tipo, recurso, page, pageSize)` | `m3.history.read` |
| `VersionadoApi` | `version(proyecto, tipo, recurso, versionId)` | `m3.history.read` |

## Reglas implementadas

**Auditoría**
- El servidor genera `id` y `occurred_at` (UTC, microsegundos). El llamador no los elige.
- Actor `USER` o `SERVICE` con id; `ANONYMOUS` sin id. Nunca se inventa un actor.
- `resource_type`: minúsculas separadas por puntos (`gobierno.politica`).
  `action`: código con al menos un punto (`m3.policies.create`).
- `outcome`: `SUCCESS`, `FAILURE` o `DENIED`.
- `safe_details` se guarda como JSON canónico. Se rechazan claves que parecen secretos
  (password, clave, secret, token, credential…), en cualquier nivel.
- La consulta exige `project_id` (no hay consultas globales) y admite filtros por actor,
  acción, tipo e id de recurso, resultado y rango `[desde, hasta)`. Orden: más reciente primero.

**Versiones**
- El estado se convierte a JSON canónico (claves ordenadas) y se calcula su SHA-256.
- Si el hash es igual al de la versión vigente, **no se crea versión** (`creada=false`) y se
  devuelve la vigente. Leer, fallar o guardar lo mismo no versiona.
- Numeración 1, 2, 3… por recurso; cada versión apunta a la anterior.
- Concurrencia: `previaEsperada` es la versión que el llamador leyó. Si ya no es la vigente,
  se lanza `ConflictoVersionException` (HTTP 409 cuando haya rutas). `Previa.ninguna()`
  espera crear la primera; `null` omite la comprobación.
- Exige actor identificado (`USER` o `SERVICE`) y un motivo de 1 a 500 caracteres.
- `estadoJson` devuelto es el texto que PostgreSQL conserva en `jsonb`: mismo contenido,
  formato posiblemente distinto. La comparación usa siempre `content_hash`.

**Paginación**: `page` desde 1; `page_size` de 1 a 100 (25 sugerido). Fuera de rango se
rechaza, no se corrige. `has_next` sin total calculado.

**Inmutabilidad**: un trigger rechaza `UPDATE` y `DELETE` sobre eventos y versiones.

## Persistencia

Migración `V30__gobierno_auditoria_y_versionado.sql`, esquema `gobierno`:

- `gobierno.evento_auditoria`: mismas columnas que `auditoria.evento` del tronco (V1)
  más `organization_id`, para hechos de nivel empresa.
- `gobierno.version_recurso`: mismas columnas que `versionado.version_recurso` del tronco.

Proyecto, organización, actor y recurso son UUID **sin clave foránea**, porque esas tablas
no existen en esta rama. M3 no crea tablas de TC13: las lecturas y capturas pertenecen a la
migración V7 de Marcelo (rama `tc13_snapshots`).

## Identidad y autorización

Desde la integración con el tronco común (ver `IntegracionTroncoComun.md`):

- `IdentidadTroncoComun` toma el actor de la sesión verificada (`IdentidadActualApi`). Sin
  sesión, la respuesta es 401.
- `AutorizacionTroncoComun` evalúa cada permiso `m3.*` con `AutorizacionApi` en ámbito PROJECT.
  Comprueba cuenta activa, membresía vigente, rol y proyecto activo; en un proyecto archivado
  solo se permiten lecturas. No hay caché de privilegios.
- Los permisos se dan de alta en `V34__gobierno_permisos.sql`.
- Las pruebas sin base de datos siguen usando dobles que conceden permisos de forma explícita.

## Pendiente al integrar con el tronco común

1. Decidir con el equipo: usar `auditoria.evento` y `versionado.version_recurso` del tronco
   (añadiéndoles `organization_id`) o conservar las tablas de M3 y migrar los datos.
2. Renumerar las migraciones V30–V34 si el equipo acuerda otro esquema de versiones de Flyway.
3. Que identidad, contexto y catálogo registren mediante `AuditoriaApi` y `VersionadoApi`
   en lugar de SQL directo (cambio de cada dueño).

## Pruebas

- Unitarias (sin base de datos): representación canónica, reglas de auditoría, versionado
  sin cambios, conflictos, permisos y paginación.
- `GobiernoPostgresTests` (etiqueta `postgres`): migración real, aislamiento por proyecto,
  enlace versión–evento e inmutabilidad. Requiere `-Ppostgres-it` y una base dedicada.
