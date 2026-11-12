# API Java pública del catálogo — M1-02

`org.dataanaliticsent.catalogo.CatalogoApi` es la entrada autorizada al módulo
en el monolito. Sus comandos y respuestas son records públicos propios, sin tipos
de `catalogo.internal`, de JDBC o de Spring en las firmas. Los otros módulos deben
inyectar esta interfaz y usar sus DTO, sin acceder al repositorio o al dominio interno.

## Operaciones y permisos

| Operación | Permiso y recurso | Resultado |
| --- | --- | --- |
| registrarActivo(AltaActivo) | m1.discovery.execute sobre SOURCE, en projectId | RegistroActivo con IDs, versión, fecha y correlación generados por el servidor |
| consultarActivo(proyectoId, activoId) | m1.assets.read sobre ASSET | Optional de DetalleActivo sin credenciales ni configuración de conexión |
| listarCampos(proyectoId, activoId) | m1.dictionary.read sobre ASSET | Lista de CampoCatalogo ordenada por posición/ID |
| listarRestricciones(proyectoId, activoId) | m1.dictionary.read sobre ASSET | Claves declaradas y miembros ordenados; destino resuelto o referencia explícita |
| consultarCamposVersion(proyectoId, activoId, versionId, page, pageSize) | m1.dictionary.read; además m1.metadata.history.read si la versión no es actual | Optional de PaginaCampos, con contexto real y campos de la versión solicitada |

La identidad procede de `IdentidadActualApi.usuarioId()`. Las firmas no aceptan
un actor ni un rol. Autorización valida cuenta, contexto, asignación y pertenencia
real del recurso antes de leer el repositorio. No existe un actor técnico universal.
Cada llamada vuelve a evaluar los permisos vigentes. Un permiso de escritura no
concede automáticamente lectura; leer el detalle tampoco concede el diccionario.

Se reutiliza el permiso de descubrimiento porque el alta publica metadatos técnicos
de una fuente autorizada. No se añade un permiso genérico para crear activos por
fuera de esa fuente. Un alcance ASSET no permite crear un hermano; SOURCE permite
publicar en su fuente. ADMIN_SISTEMA no recibe acceso funcional por defecto.

## Alta inicial

AltaActivo recibe projectId/sourceId, nombre, tipo, namespace opcional, localizador,
procedencia declarada/inferida y campos técnicos. Cada campo incluye nombre, ruta,
posición, tipo nativo/lógico y nulabilidad conocida o desconocida. Las colecciones
son copias inmutables; el dominio valida nombres, posiciones y rutas duplicadas.

El servidor crea UUID de activo/campos/versión, revisión cero, estado inicial activo,
fecha UTC y requestId de la operación. Ese requestId se devuelve en el recibo y se
conserva en la auditoría; no es todavía una envoltura HTTP ni sustituye el futuro
contrato de correlación de las rutas web. No se aceptan IDs nuevos, estados, fechas,
autor o versión arbitrarios del consumidor.

La fuente debe existir en el proyecto y estar habilitada. El tipo de activo debe
corresponder a su fuente: PostgreSQL/MySQL → TABLE; CSV → CSV_DATASET;
JSON → JSON_COLLECTION; REST → API_COLLECTION. Esto valida metadatos recibidos;
no implementa conectores ni demuestra que se haya consultado una fuente real.

Las descripciones y sinónimos empiezan vacíos: su edición requiere el futuro caso
de enriquecimiento con su permiso específico. No se introduce un camino alternativo
para modificar el diccionario mediante el alta técnica.

Activo, campos, versión inicial y evento se guardan en una sola transacción. El
evento `m1.assets.register` conserva al actor autenticado y la correlación; ese nombre
es de auditoría, no un permiso nuevo. Un localizador ya catalogado produce conflicto,
sin publicar un segundo activo ni sobrescribir su versión. El redescubrimiento y
la idempotencia de ejecuciones se implementarán en su incremento específico.

## Consulta y errores

DetalleActivo y CampoCatalogo contienen solo metadatos. No incluyen configuración,
credentialRef, secretos ni filas empresariales. Se conservan la versión y los estados
reales, incluida nulabilidad desconocida. La lista de campos es una operación Java;
la paginación HTTP del contrato candidato no queda implementada por esta lista.

Un actor ausente produce IdentidadNoVerificadaException; una operación sin permiso,
fuera de alcance o sobre recurso no validado produce AccesoDenegadoException. El
proveedor rechaza también IDs inexistentes, sin confirmar al consumidor recursos
ajenos. El Optional vacío solo cubre la ausencia detectada por persistencia después
de una comprobación de autorización satisfactoria; no sustituye esa comprobación.

Un estado/tipo/estructura inválidos produce IllegalArgumentException. Los conflictos
de integridad de publicación producen ConflictoCatalogoException con mensaje seguro,
sin SQL ni valores privados. Otros fallos de infraestructura interrumpen la operación;
no se convierten en éxito ni se conceden permisos. Este contrato Java no fija estados
HTTP para rutas que todavía no existen.

## Ejemplo de consumidor

```java
// Dentro de una solicitud con identidad verificada en servidor.
CatalogoApi.RegistroActivo registro = catalogo.registrarActivo(
    new CatalogoApi.AltaActivo(proyectoId, fuenteId, "clientes",
        CatalogoApi.TipoActivo.TABLE, "public", "clientes",
        CatalogoApi.OrigenEstructura.DECLARED,
        List.of(new CatalogoApi.AltaCampo("id", "id", 1, "integer",
            CatalogoApi.TipoLogico.INTEGER, false))));
CatalogoApi.DetalleActivo detalle = catalogo
    .consultarActivo(proyectoId, registro.id()).orElseThrow();
List<CatalogoApi.CampoCatalogo> campos = catalogo.listarCampos(proyectoId, registro.id());
```

Los nombres Java usan camelCase. La futura serialización HTTP debe conciliarse
con snake_case y los esquemas M1 candidatos; no se modifican esos documentos ni se
presentan como aprobados por el equipo. M1-03 incorpora DescubrimientoPostgresApi
(ver DescubrimientoPostgres.md); M1-04 incorpora las consultas web, contrato/mocks y demostración; ver CatalogoHttp.md.
DetalleActivo incluye organización derivada y etiquetas activas reales. PaginaCampos
incluye contexto, versión y hasNext; la persistencia pagina el snapshot inmutable
antes de devolverlo, sin mezclar sus campos con los actuales.

## Verificación

Las pruebas unitarias comprueban identidad, permisos antes de persistencia,
validaciones, generación en servidor y conflictos seguros. El consumidor PostgreSQL
usa únicamente CatalogoApi/DTO públicos para operar, con fixtures SQL aislados;
sustituye el puerto público de identidad para seleccionar actores y conserva reales
el evaluador RBAC, el proveedor de recursos, las transacciones y la persistencia.
La autenticación HTTP con cookies reales mantiene sus pruebas independientes.
