# M3 — Fichas de gobierno y cumplimiento (fase 3)

Rama `modulo3_gobierno`. Describe lo implementado; si cambia el código, se actualiza este
archivo. Cubre RF-M3.2.2 (evaluación de políticas), las reglas verificables de la matriz de
permisos y la responsabilidad de negocio y clasificación formal asignada a M3 en
`Decisiones_Contratos_v1.md`.

## Tres piezas

| Subcomponente | Qué hace |
| --- | --- |
| `politicas` (ampliado) | Cada política puede tener **reglas verificables**; forman parte de su versión |
| `fichas` | **Ficha de gobierno** de un activo: responsable del dato y clasificación formal |
| `cumplimiento` | **Evalúa** un activo contra sus políticas activas y guarda el resultado |

## Reglas verificables

`PoliticasApi.agregarRegla` / `quitarRegla`, permiso `m3.policies.rules.manage`.

| Tipo | Parámetro | Cumple si… |
| --- | --- | --- |
| `REQUIERE_RESPONSABLE` | — | la ficha del activo tiene responsable |
| `REQUIERE_CLASIFICACION` | — | la ficha tiene clasificación |
| `CLASIFICACION_MINIMA` | `PUBLICA`, `INTERNA`, `CONFIDENCIAL` o `RESTRINGIDA` | la clasificación es igual o más sensible |
| `CALIDAD_APROBADA` | — | M2 informa que el activo está aprobado |

- Añadir una regla igual a una existente no hace nada. Un parámetro faltante, sobrante o
  desconocido se rechaza.
- Agregar o quitar una regla audita (`m3.policies.rules.add` / `.remove`), sube la revisión y crea
  una versión de la política.

## Ficha de gobierno

`FichasGobiernoApi`, permisos `m3.asset_profiles.read` y `m3.asset_profiles.update`.

- Una ficha por activo y proyecto. Responsable (`owner_id`) y clasificación pueden quedar sin
  definir (`null`).
- Clasificación de menor a mayor sensibilidad: `PUBLICA < INTERNA < CONFIDENCIAL < RESTRINGIDA`.
- Crear exige `expectedRevision = null`; editar exige la revisión leída. Si no coincide, `RevisionObsoletaException` (412).
- Solo se crea para activos que el catálogo de M1 confirma.
- Cada cambio efectivo se audita (`m3.asset_profiles.update`) y crea una versión
  (`resource_type = gobierno.ficha_activo`, `resource_id = asset_id`). Sin cambios no hace nada.

## Evaluación de cumplimiento

`CumplimientoApi`:

| Operación | Permiso |
| --- | --- |
| `evaluar(proyecto, activo)` | `m3.compliance.evaluate`, más `m3.policies.read` y `m3.asset_profiles.read` |
| `ultima(proyecto, activo)` | `m3.compliance.read` |
| `historial(proyecto, activo, page, pageSize)` | `m3.compliance.read` |

**Cómo decide** (solo metadatos de gobierno; nunca lee filas de negocio):

1. Toma las políticas **ACTIVE** asociadas al activo. Las demás no se evalúan.
2. Evalúa cada regla con la ficha del activo y, si hace falta, el estado de calidad de M2.
3. Resultado por regla:
   - `COMPLIANT`: cumple.
   - `NON_COMPLIANT`: falta el responsable, falta la clasificación, la clasificación es
     insuficiente o M2 no aprobó la calidad.
   - `INDETERMINATE`: falta evidencia. Puede ser que no exista ficha (`SIN_FICHA`) o que M2 no
     tenga resultado (`CALIDAD_SIN_EVALUAR`). Nunca se inventa una aprobación.
4. Una política **sin reglas** es solo documental y queda `INDETERMINATE` con motivo `SIN_REGLAS`.
5. El resultado de la política y del activo es el **peor** de sus partes, en este orden de
   gravedad: `NON_COMPLIANT > INDETERMINATE > COMPLIANT`.
6. Si el activo no tiene políticas activas, el resultado es `NOT_APPLICABLE`.

**Qué se guarda:** cada evaluación con su estado global, quién y cuándo, la versión de la ficha
usada (`profile_version_id`), la versión de cada política evaluada y el detalle por regla con su
motivo. No se edita ni se borra. Además se audita (`m3.compliance.evaluate`, recurso
`gobierno.evaluacion_cumplimiento`). Un intento sin permiso se audita como `DENIED`.

**Para M4:** `ultima(proyecto, activo).estado()` es el estado de cumplimiento que pide RF-M4.2
(`COMPLIANT` / `NON_COMPLIANT`), con la versión exacta de política y ficha que lo sustentó.

## Persistencia

Migración `V32__gobierno_cumplimiento.sql`:

- `gobierno.regla_politica`: reglas de cada política, sin repetir tipo y parámetro.
- `gobierno.ficha_activo`: responsable, clasificación, revisión y versión vigente.
- `gobierno.evaluacion_cumplimiento`: evaluaciones inmutables. El detalle va en `results` (jsonb).

## Sustitutos temporales

`CalidadSinModulo2` responde que no hay resultado de calidad, así que `CALIDAD_APROBADA`
queda `INDETERMINATE` hasta integrar M2. `CatalogoSinModulo1` sigue simulando el catálogo.
Identidad y autorización ya son las reales del tronco común.

## Pendiente al integrar

1. Adaptador de `PuertoCalidad` sobre la API de M2 (estado de aprobación del activo).
2. Que `owner_id` de la ficha sea un usuario con membresía en el proyecto.
3. Reevaluar automáticamente cuando cambie una política, una ficha o la calidad; por ahora la
   evaluación es bajo demanda.

Los permisos ya están dados de alta en `V34`. `m3.compliance.read` también se concedió a Gestor de
catálogo, Calidad y Analista de negocio, según la matriz de roles.

## Pruebas

- `MotorCumplimientoTests` (6): cada tipo de regla, falta de evidencia y combinación de resultados.
- `ServicioCumplimientoTests` (8): flujo completo con ficha, clasificación insuficiente,
  calidad de M2, auditoría, permiso denegado, activo inexistente y versiones de la ficha.
- `ServicioPoliticasTests`: 2 pruebas nuevas de reglas.
- `ResultadosJsonTests` (1): el detalle se guarda y se lee igual en JSON.
- `CumplimientoPostgresTests` (etiqueta `postgres`, aprobadas en PostgreSQL 18.7 el 2026-11-17; ver `PruebasPostgres.md`): V32 real e inmutabilidad.
