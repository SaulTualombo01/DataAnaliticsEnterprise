# M3 — Políticas de gobierno (fase 2)

Rama `modulo3_gobierno`. Describe lo implementado; si cambia el código, se actualiza este
archivo. Cubre RF-M3.1.1 a RF-M3.1.5 y RF-M3.2.1 del Borrador Componente 3.

## API Java pública

`org.dataanaliticsent.Gobierno.politicas.PoliticasApi`. El actor sale siempre de la identidad
verificada, nunca de los argumentos.

| Operación | Permiso | Resultado |
| --- | --- | --- |
| `crear(NuevaPolitica)` | `m3.policies.create` | Política en `DRAFT`, revisión 0, versión 1 |
| `consultar(proyecto, política)` | `m3.policies.read` | `Optional` vacío si no existe en el proyecto |
| `buscar(proyecto, filtro, page, pageSize)` | `m3.policies.read` | Página por creación e id; filtros: texto, estado, categoría, responsable, activo |
| `actualizar(proyecto, política, revisión, cambios, motivo)` | `m3.policies.update` | Nombre, descripción, categoría o responsable |
| `cambiarEstado(proyecto, política, revisión, estado, motivo)` | `m3.policies.lifecycle` | Transición del ciclo de vida |
| `asociarActivo(proyecto, política, revisión, activo, motivo)` | `m3.policies.assets.manage` | Asocia un activo del catálogo |
| `retirarActivo(proyecto, política, revisión, activo, motivo)` | `m3.policies.assets.manage` | Quita la asociación |
| `politicasDeActivo(proyecto, activo)` | `m3.policies.read` | Políticas asociadas y si aplican (`ACTIVE`) |

## Ciclo de vida

```
DRAFT ──► ACTIVE ◄──► INACTIVE
  │          │            │
  └──────────┴────────────┴──► RETIRED (terminal)
```

- Una transición no permitida, editar una política `RETIRED` o asociarle activos produce
  `ConflictoPoliticaException` (409).
- Pedir el estado que ya tiene no hace nada.

## Reglas

- **Nombre**: 1 a 200 caracteres, sin espacios al inicio ni al final. Único por proyecto entre
  las políticas no retiradas, sin distinguir mayúsculas.
- **Descripción**: hasta 2000 caracteres. **Motivo** de cada cambio: 1 a 500 caracteres.
- **Categorías**: `CLASIFICACION`, `PRIVACIDAD`, `RETENCION`, `CALIDAD`, `ACCESO`, `OTRA`.
- **Responsable** (`owner_id`) obligatorio.
- **Concurrencia**: cada edición envía la `revision` que leyó. Si otro usuario la cambió antes,
  se rechaza con 409 y no se sobrescribe nada. Cada cambio efectivo sube la revisión en 1.
- **Sin cambios efectivos** (mismos valores, activo ya asociado o ya retirado) no se sube la
  revisión ni se crea versión ni evento.
- **Activos**: solo se asocian activos que el catálogo de M1 confirma en el proyecto. M3 no crea
  un segundo catálogo.

## Auditoría y versiones

Cada cambio efectivo, en una sola transacción:

1. Registra un evento `SUCCESS` con `resource_type = gobierno.politica` y la acción:
   `m3.policies.create`, `m3.policies.update`, `m3.policies.lifecycle` (detalles `from`/`to`),
   `m3.policies.assets.add` o `m3.policies.assets.remove` (detalle `asset_id`).
2. Crea una versión con el estado: nombre, descripción, categoría, responsable, estado y lista
   ordenada de activos. Así el historial muestra también qué activos tenía en cada momento.
3. Guarda la política con la nueva revisión y su `current_version_id`.

Si algo falla, no queda nada a medias. Un intento de cambio **sin permiso** se audita como
`DENIED` y se rechaza.

## Persistencia

Migración `V31__gobierno_politicas.sql`:

- `gobierno.politica`: datos, estado, revisión y versión vigente (FK a `gobierno.version_recurso`).
- `gobierno.politica_activo`: asociación vigente o retirada, con quién y cuándo. Reasociar un
  activo reactiva la fila.

Proyecto, responsable y activo son UUID sin FK, porque esas tablas no existen en esta rama.

## Sustitutos temporales

Además de identidad y autorización (ver `AuditoriaVersionado.md`), `CatalogoSinModulo1`
responde que ningún activo existe. Hasta integrar M1 no se puede asociar ningún activo fuera
de las pruebas.

## Pendiente al integrar

1. Adaptador de `PuertoCatalogo` sobre `CatalogoApi` de M1.
2. Alta de los cinco permisos `m3.policies.*` y asignación al rol `RESPONSABLE_GOBIERNO`;
   `m3.policies.read` también a los roles que deban consultar políticas.
3. Comprobar que `owner_id` sea un usuario con membresía en el proyecto.
4. Políticas de ámbito organización (ruta `/organizations/{id}/governance-policies` de las
   convenciones): por ahora todas son de proyecto.
5. Rutas HTTP y correlación (`request_id`) del tronco común.

## Pruebas

- `ServicioPoliticasTests` (10, sin base de datos): alta, denegación auditada, validaciones,
  sin cambios efectivos, revisión obsoleta, nombre único, transiciones, asociación idempotente,
  aplicabilidad y búsqueda.
- `PoliticasPostgresTests` (etiqueta `postgres`, no ejecutadas aún): ciclo completo, unicidad,
  reversión de la transacción y búsqueda con `%` literal.
