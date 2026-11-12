# Consultas HTTP de catálogo — M1-04

Este incremento implementa dos consultas REST sobre CatalogoApi. El contrato del
backend es catalogo-http.openapi.json (OpenAPI 3.1.1, versión 0.3.0); los esquemas
autocontenidos están en catalogo-http.schema.json y los ejemplos en
mocks/catalogo.examples.json. No modifica el candidato 0.2.0 conservado en TIC ni
presume aprobación colectiva del equipo.

## Rutas y respuestas

| Método y ruta | Permiso | Respuesta |
| --- | --- | --- |
| GET /api/v1/projects/{project_id}/assets/{asset_id} | m1.assets.read sobre ASSET | Detalle de activo con contexto, origen y versión actual |
| GET /api/v1/projects/{project_id}/assets/{asset_id}/fields | m1.dictionary.read sobre ASSET | Campos paginados de metadata_version_id |

Se usa la sesión real TICSESSION. No se aceptan actor, rol u organización para
seleccionar la identidad. La organización se deriva del proyecto autorizado en
el servidor. Las etiquetas devueltas son las asociaciones activas reales del
catálogo. No se devuelve configuración de conexión, referencias de credenciales,
contraseñas ni filas empresariales.

Los DTO HTTP usan snake_case, enums en minúsculas y fechas UTC con formato
date-time. Los éxitos conservan data/meta.request_id. La lista añade
meta.page_info con page, page_size, returned_count y has_next. X-Request-Id coincide
con la correlación del cuerpo. Las respuestas se envían con Cache-Control: no-store.

## Campos y versión

metadata_version_id es obligatorio y debe pertenecer al mismo activo/proyecto.
La consulta lee los campos de la versión inmutable seleccionada, no los sustituye
por los campos actuales. Una versión anterior exige además m1.metadata.history.read.
La autorización vigente siempre se evalúa antes de consultar/paginar; conocer un
ID histórico no conserva privilegios retirados.

page empieza en 1 y admite hasta 2147483647; page_size es 25 por defecto y admite
1–100. sort solo admite position,id. Las cifras son valores técnicos iniciales
del contrato implementado. PostgreSQL devuelve como máximo page_size+1 campos
para calcular has_next, usando el orden fijo posición/UUID. No se devuelve un
total global. Una página posterior al final o un diccionario vacío devuelve 200,
data[] y has_next:false; no se ajusta silenciosamente el número solicitado.

Los campos se extraen del payload de una versión de metadatos y se paginan en
SQL antes de transferirlos a Java. No se consulta el origen empresarial durante
estas rutas. La paginación sigue una versión fija, aunque cambie el estado actual.
El proyecto/contexto y los permisos se comprueban en cada petición.

## Errores y entrada

| Estado | Condición |
| --- | --- |
| 401 | No hay sesión válida o identidad vigente |
| 403 | Falta la acción solicitada en un contexto visible |
| 404 | Proyecto/recurso fuera de alcance, ID inexistente o versión no disponible |
| 422 | Parámetro requerido ausente, formato/rango/orden inválido, duplicados o parámetros no admitidos |
| 500 | Error interno con mensaje saneado |
| 503 | Dependencia de identidad o catálogo no disponible |

Los problemas usan application/problem+json, type/title/status/detail/instance/
code/request_id. La correlación común representa instance como urn:request:<uuid>;
validaciones 422 incluyen errors con location/field/code/message. No se reflejan
valores rechazados, SQL ni el motivo interno de autorización.

Un recurso ajeno y uno inexistente tienen el mismo problema 404. Un rol que no
posee la acción recibe 403 sin detalles del recurso. Se exigen UUID canónicos en
minúsculas. Los parámetros repetidos/desconocidos se rechazan; organization_id o
actor_id en query no cambian contexto ni identidad. GET no exige CSRF; el inicio
de sesión sí lo exige según el contrato común. No se habilitan escrituras por
estas rutas. La limitación de intentos de login continúa siendo independiente;
este incremento no promete un limitador general de consultas ni respuestas429.

## Conciliación con el candidato

- Se conservan las rutas, campos principales, envolturas, versión obligatoria,
  paginación, orden del diccionario y ocultación de recursos del candidato 0.2.0.
- La seguridad provisional tic_session se sustituye por TICSESSION, mecanismo
  confirmado por David e implementado en el tronco común.
- El contrato0.3.0 describe solo las dos consultas implementadas. El listado de
  fuentes, escrituras y pantallas no se consideran disponibles.
- Se incluye status de campo y se conserva native_type íntegro, sin aplicar el
  límite propuesto de200 caracteres que el dominio/persistencia no imponían.
- Los esquemas de errores respetan el formato común real; URI de instancia por
  correlación y errores422 son compatibles con las definiciones seleccionadas.

## Pruebas y demostración real

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
python .\tools\verificar_postgres.py
```

El verificador usa plataforma y origen PostgreSQL temporales separados. La prueba
HTTP prepara una cuenta/contexto ficticios, ejecuta el descubrimiento real como
operación interna autenticada del servidor y después inicia sesión por HTTP con
cookie/CSRF reales para consultar el activo y sus campos. No añade una ruta de
descubrimiento de prueba a producción. Los fixtures de otros casos se publican
mediante CatalogoApi con contexto de servidor controlado.

Se prueban aislamiento, cambios de permisos con la misma cookie, paginación,
vacío, versión ajena, historial, parámetros inválidos y rechazo de POST. Las
respuestas HTTP se guardan en target/contract-evidence/m104.json, sin cookies ni
contraseñas, para validarlas contra los esquemas. No se configura una cuenta o
fuente empresarial real por ejecutar el verificador.

Con backend, cuenta y contexto propios ya configurados:

```powershell
python .\tools\consumir_catalogo.py --base-url http://127.0.0.1:8080 --project <project_id> --asset <asset_id>
```

El consumidor solicita correo/contraseña sin colocarlos en argumentos, conserva
cookies en memoria, obtiene CSRF para el login y consulta todas las páginas de
una versión fija. Comprueba contexto, correlación y paginación. La URL base es la
raíz del servidor, sin /api/v1. Para HTTP local configurar SESSION_COOKIE_SECURE=false;
fuera del equipo local el consumidor exige HTTPS. No se guardan credenciales.

## Mock para consumidores

```powershell
python .\tools\mock_catalogo.py --port 8089
```

El servidor escucha solo en 127.0.0.1 y devuelve X-Mock-Response:true. Usa datos
sintéticos, no inicia sesiones ni demuestra seguridad real. Opcionalmente
--allow-origin permite un origen frontend explícito; no hay CORS ni cookies por
defecto. Mantenerlo separado de cualquier servicio real.

Los IDs de ejemplo están en mocks/catalogo.examples.json. Una demostración con
los IDs de ese archivo:

```powershell
python .\tools\consumir_catalogo.py --mock --base-url http://127.0.0.1:8089 --project 2f66e241-dd8b-508f-b5e1-85db372e9f2c --asset f5671a70-c3d1-58ad-b14d-5ec5501146bc --page-size 2
```

--scenario empty comprueba vacío; --scenario denied, denegación. También hay
unauthenticated, not_found y unavailable. Los escenarios son exclusivos de --mock
y el consumidor verifica el marcador del servidor. El mock conserva los filtros
de versión y paginación de su fixture, pero no sustituye las pruebas reales de RBAC.

El criterio técnico M1-04 es demostración de catálogo/diccionario y contrato/mocks
de consumo. No incorpora frontend React ni cierra por sí mismo las tareas comunes
de interfaz, CI, decisiones de TC13 o aceptación de Q1.

Referencia técnica: [operadores y funciones JSON de PostgreSQL](https://www.postgresql.org/docs/18/functions-json.html).
