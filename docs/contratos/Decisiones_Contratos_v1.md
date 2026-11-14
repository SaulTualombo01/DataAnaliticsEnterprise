# Base técnica M1 y TC13, versión 1.0.0

Fecha: 13 de noviembre de 2026. David delegó explícitamente la elección de
configuraciones y pidió cerrar los contratos y continuar las tareas. Estas son
decisiones de diseño adoptadas para implementar; no se inventa aprobación de
Saúl, Claudio o Marcelo ni integración real de sus componentes.

## Convenciones fijadas

- OpenAPI3.1.1 y JSON Schema2020-12; API /api/v1; snake_case, UUID canónicos en
  minúsculas, UTC con Z; identificadores/contexto derivados y verificados en servidor.
- Éxito data/meta.request_id; errores Problem Details, sin SQL ni datos privados.
  X-Request-Id igual al cuerpo y Cache-Control:no-store para estas operaciones.
- Cuenta local y cookie HttpOnly TICSESSION; CSRF en escrituras, sin actor/roles
  enviados por el cliente. 401 sin sesión,403 por acción denegada en contexto
  visible y404 para recurso/contexto ajeno o no disponible. 410 para captura
  vencida después de comprobar autorización. 422 para entrada inválida.
- En el monolito, interfaces Java/DTO públicas; HTTP es borde de integración,
  no requisito de llamadas de red entre componentes.

## M1

Se fija el contrato de intercambio de detalle de activo y campos, las dos rutas
implementadas en M1-04. page inicia en1, page_size25 por defecto y máximo100,
sort=position,id; metadata_version_id obligatorio en campos y fijo al paginar.
Una versión anterior exige permiso de historial. Sin total fiable no se inventa
total global. Metadatos no habilitan contenido empresarial.

Las claves/relaciones están disponibles por la interfaz Java pública de M1-03;
no se finge un endpoint de restricciones inexistente. Fuente/activo/estructura
son propiedad M1; responsabilidad de negocio y clasificación formal son de M3,
referenciadas por IDs y sus interfaces cuando se implementen. created_by no
equivale a propietario de negocio y tags no son políticas de autorización.

Los RF de CRUD de fuentes/diccionario, búsqueda y otros conectores conservan
su backlog y deberán añadir contratos al implementarse. Cerrar este núcleo
intermódulos no declara esos RF ya desarrollados ni inventa rutas operativas.
El diagrama ER sigue opcional.

M1 conserva los archivos públicos en sus rutas existentes. docs/contratos/m1
del tronco común distribuye una copia de la base1.0.0 para consumidores/CI;
el validador detecta diferencias al integrar ambas carpetas en M1.

## TC13

Se fija raw_cells_v1 para transporte: orden de filas y celdas explícito;
NULL/MISSING/PARSE_ERROR diferenciados, VALUE sin limpiar/corregir anomalías.
Los conectores deben documentar cómo traducen sus valores a esta representación.

Lectura síncrona acotada. POSTreads crea recibo201 y devuelve las filas del
alcance obtenido. execution_state COMPLETED/FAILED es independiente del HTTP.
Un parcial lleva FAILED y scope.complete=false; M2 no produce resultados finales
sobre él. Vacío puede completar con coverage_ratio=null cuando el total es cero.
Total esperado/cobertura desconocidos se representan null. No equivale a calidad.

Mode live no conserva captura. Mode capture necesita permiso adicional y
lectura completa para producir una captura. GETreads no entrega filas; GETcaptures
requiere permiso de contenido y retención vigente. Una captura vencida no
desencadena una lectura nueva; se conserva la referencia administrativa autorizada.
read_id, execution_id, metadata_version_id y capture_id tienen significados distintos.

Límites iniciales de servidor: hasta10.000 filas,10MiB y60s por lectura, hasta100
campos. El cliente debe enviar límites explícitos menores o iguales. Recomendación
inicial de consumidor:1.000 filas,1MiB y15s, sin convertir esta muestra en población
completa. Retención inicial de capturas:7días; configuración del servidor, nunca
extensión arbitraria por el cliente. Estos valores se adoptan como configuración
inicial de desarrollo, no como SLA ni rendimiento medido. Revisarlos antes de
datos reales con los responsables de gobierno y operación.

La futura persistencia pertenece a TC13 dentro del repositorio de plataforma,
con metadatos/contexto y contenido de captura separado e inmutable durante su
retención. Una sola copia referenciada por M2, sin duplicarla por evaluación.
No se implementa ni migra ese almacenamiento en el cierre contractual.

content_hash_encoding=json_utf8_compact_v1 es normativo: UTF-8, sin BOM/espacios,
sin ASCII-escape de Unicode y sin valores no finitos; filas en orden recibido,
propiedades row_number/values y celdas field_id/state/raw_value en ese orden.
SHA-256 lowercase de esos bytes. tools/tc13_codec.py es referencia ejecutable.
No depende del orden arbitrario del objeto JSON de un consumidor ni de los
bytes originales del CSV. No se pierde nulo/ausencia al reconstruir el codec.

Los permisos TC13 quedan definidos pero sin concesiones automáticas. El mock
es solo una implementación de pruebas, sin sesión/RBAC/conectores reales; sus
capturas son sintéticas y volátiles. Las rutas futuras deben comprobar pertenencia
y alcances antes de extraer y entregar, y revalidar tras operaciones largas.

## Evolución y verificación

Las versiones0.3.0 M1 y0.1.0 TC13 se conservan en archive. Cambios incompatibles
posteriores requieren nueva versión mayor de contrato y plan de transición;
nuevos endpoints compatibles pueden añadirse con versión menor. Mantener pruebas
de consumidores y evidencias. Esta versión fija decisiones técnicas; la revisión
del equipo y la aceptación de entregables se registran aparte.

Referencias: [OpenAPI3.1.1](https://spec.openapis.org/oas/v3.1.1.html),
[JSON Schema2020-12](https://json-schema.org/draft/2020-12/json-schema-validation),
[HTTP201](https://www.rfc-editor.org/rfc/rfc9110.html#name-201-created).
