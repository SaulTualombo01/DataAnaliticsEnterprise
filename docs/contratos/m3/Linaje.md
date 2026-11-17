# M3 — Linaje de datos (fase 4)

Rama `modulo3_gobierno`. Describe lo implementado; si cambia el código, se actualiza este
archivo. Cubre RF-M3.5.1 (registro), RF-M3.5.2 (consulta) y los datos para RF-M3.5.3
(visualización) del Borrador Componente 3.

## Qué es una relación de linaje

Indica que los datos de un activo **llegan** a otro: `origen → destino`. Ejemplo con el dataset
de comercio electrónico:

```
pedidos ──TRANSFORMA──► ventas ──AGREGA──► reporte_ventas
clientes ─TRANSFORMA──┘
```

Tipos: `COPIA`, `TRANSFORMA`, `AGREGA`, `ALIMENTA`.

Las relaciones se registran de forma explícita, porque son relaciones conocidas o documentadas.
**No se infieren** por nombres parecidos, y el descubrimiento automático queda fuera de alcance,
como dice el borrador.

## API Java pública

`org.dataanaliticsent.Gobierno.linaje.LinajeApi`:

| Operación | Permiso | Resultado |
| --- | --- | --- |
| `registrar(NuevaRelacion)` | `m3.lineage.manage` | Relación `ACTIVE`, auditada y versionada |
| `retirar(proyecto, relación, motivo)` | `m3.lineage.manage` | Relación `RETIRED`; se conserva |
| `consultar(proyecto, relación)` | `m3.lineage.read` | Vigente o retirada |
| `grafo(proyecto, activo, dirección, profundidad)` | `m3.lineage.read` | Nodos y relaciones vigentes alrededor del activo |

## Reglas

- Ambos activos deben existir en el catálogo de M1 del proyecto. Un activo no puede ser origen
  de sí mismo.
- **Sin ciclos.** Si el destino ya alimenta al origen, directa o indirectamente, la relación se
  rechaza con `ConflictoLinajeException` (409). Ejemplo: con `pedidos → ventas → reporte` no se
  admite `reporte → pedidos`. Los cambios de linaje de un proyecto se serializan con un bloqueo
  de PostgreSQL, para que dos altas simultáneas no cierren un ciclo entre ambas.
- Registrar una relación igual a una vigente (mismo origen, destino y tipo) la devuelve sin
  duplicarla. Retirar una ya retirada no hace nada.
- Descripción de hasta 1000 caracteres y motivo de 1 a 500.
- Cada alta (`m3.lineage.create`) y retiro (`m3.lineage.retire`) se audita y crea una versión
  (`resource_type = gobierno.relacion_linaje`). Los intentos sin permiso quedan como `DENIED`.

## Grafo

- `direccion`: `ORIGENES` (de dónde viene), `DESTINOS` (a qué alimenta) o `AMBOS`.
- `profundidad`: de 1 a 5 niveles. Fuera de ese rango se rechaza.
- Cada nodo tiene una `distancia`: 0 es el activo consultado, negativa son sus orígenes
  (-1 directo, -2 el de antes…) y positiva sus destinos.
- Solo usa relaciones **vigentes**. El recorrido hace una consulta por nivel, no por nodo.
- Hasta 200 nodos. Si se alcanza el límite, `truncado = true`: nunca se corta en silencio.

La pantalla de linaje (fase 6) dibujará este grafo. La API ya entrega nodos con su nivel y
relaciones con su tipo.

## Persistencia

Migración `V33__gobierno_linaje.sql`: tabla `gobierno.relacion_linaje` con una sola relación
vigente por origen, destino y tipo, e índices para recorrer hacia orígenes y hacia destinos.
Activos y proyecto son UUID sin FK, porque esas tablas no existen en esta rama.

## Pendiente al integrar

1. Alta de `m3.lineage.read` y `m3.lineage.manage`. Según la matriz de roles, `read` también va
   a Gestor de catálogo, Calidad y Analista de negocio.
2. Registrar linaje automáticamente desde los eventos de la plataforma: lectura de TC13 →
   evaluación de M2 o KPI de M4 → reporte. Se haría con los `read_id` del esquema de Marcelo
   (`tc13_snapshots`), una vez acordado con él.
3. Linaje a nivel de columna: fuera del alcance actual, que es por activo.

## Pruebas

- `ServicioLinajeTests` (7): alta sin duplicar, ciclos y auto-relación, activo inexistente,
  grafo de orígenes con distancias, profundidad y dirección, retiro y permisos.
- `LinajePostgresTests` (etiqueta `postgres`, no ejecutadas aún): V33, bloqueo, ciclos y retiro
  en PostgreSQL real.
