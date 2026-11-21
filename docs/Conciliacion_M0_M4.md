# Conciliación de M0 con los cambios compartidos de M4

Fecha: 20 de noviembre de 2026. Base M0: 49a91de; cambios compartidos:
origin/main 6e63659, incluido 8692132. No incorpora el módulo de negocio M4.

## Acceso y límites

Se conservan las rutas exactas compartidas por M4: GET /api/v1/analitica/ping
y POST /api/v1/projects/{projectId}/analytics/kpis/evaluate. Exigen sesión;
la escritura exige CSRF. No se habilita un prefijo completo ni se elimina
anyRequest().denyAll().

El permiso m4.kpis.evaluate es PROJECT, disponible inicialmente para
ANALISTA_NEGOCIO y sin lectura histórica. El total inicial pasa a 78 permisos
y mantiene 6 roles. ADMIN_SISTEMA no hereda acceso a los proyectos.

M4 debe obtener el actor desde IdentidadActualApi, comprobar AutorizacionApi
en cada ejecución y aplicar los alcances a sus lecturas. Ambas fachadas se
importan desde org.dataanaliticsent.TroncoComun. La autenticación del filtro
no sustituye la autorización del caso de uso. Un proyecto archivado no admite
esta evaluación; la revocación se aplica sin renovar la cookie.

Los controladores reales y el cálculo de KPIs siguen en la rama de M4.
Esta rama solo prepara su integración. La suite HTTP usa un sustituto exclusivo
de pruebas, que no entra en el JAR. TC13 sigue siendo contrato/mock.

## Dos historias V4 ya publicadas

Las dos V4 tenían la misma versión y funciones distintas. Se conservan los
nombres y contenidos originales, sin editar SQL aplicado ni reparar historial:

| Historia | Recursos además de db/migration | Actualización |
| --- | --- | --- |
| Nueva o V4 de M0 | db/migration-m0 | V4 administración empresarial original; V5–V7; V8 permiso M4 |
| V4 de M4 | db/migration-m4 | V4 permiso M4 original; nueva V4.1 administración empresarial; V5–V8 |

V4_1__administracion_de_empresa.sql tiene el contenido de la V4 original de M0
y se aplica únicamente al historial M4. V8 añade el permiso donde falta y no
reactiva una definición o una vinculación al rol previamente retirada en M4.
Si UUID, código o ámbito son incompatibles, aborta para revisión.

ConfiguracionMigraciones consulta la fila V4 del historial antes de que Flyway
migre. Una base sin V4 elige M0; los nombres históricos reconocidos eligen su
ruta. Un historial desconocido, fallido o no consultable bloquea el arranque.
Flyway sigue validando nombres/checksums y mantiene clean deshabilitado.
No se modifica flyway_schema_history manualmente y no se utiliza repair.

El arranque Spring Boot selecciona las ubicaciones automáticamente. No configurar
un escaneo recursivo de las tres carpetas: contiene dos versiones V4.
Los programas que construyan Flyway directamente deben llamar a
ConfiguracionMigraciones.preparar(configuracion) antes de load(), o seleccionar
explícitamente la ruta correcta después de verificar el historial.
No se admite baseline automático ni outOfOrder como solución a esta colisión.

Consulta de diagnóstico de solo lectura, antes de actualizar una base existente:

~~~sql
SELECT version, script, checksum, success
FROM public.flyway_schema_history ORDER BY installed_rank;
~~~

Respaldar y verificar la restauración de la base correspondiente antes de un
despliegue con datos. Un historial desconocido requiere revisión; nunca sustituir
el volumen o editar checksums para forzar el arranque.

## Entornos Docker separados

docker-compose.yml conserva el entorno M0: proyecto tic-data, PostgreSQL16,
localhost5434, data_analytics/data_analytics_app, secretos locales y backend
opcional. Se siguen usando tools/Preparar-Docker.ps1 y la guía Docker_Desarrollo.md.
No se cambian credenciales ni volúmenes ya inicializados.

docker-compose.m4-legacy.yml conserva el Compose anterior de M4: proyecto
tic-tc-dev, PostgreSQL18, localhost55432, tic_tc_dev/tic_app y su volumen propio.
Se utiliza explícitamente con -f; consultar INTEGRACION_M4_TC04.md.
No montar un volumen de PostgreSQL16 en PostgreSQL18 ni intercambiarlos.
Las credenciales reales permanecen excluidas de Git.

## Verificación reproducible

Con JDK24, Maven y PostgreSQL18 local, desde la raíz:

~~~powershell
python tools/verificar_postgres_tc.py
~~~

El verificador crea su propio clúster y bases temporales. Comprueba instalación
nueva, actualización M0 V4/V7, actualización M4 V4, preservación de cuenta y
auditoría, no reactivación de privilegios, rechazo por checksum modificado,
rechazo de V4 desconocida y de ubicaciones mezcladas. La suite también verifica
sesión/CSRF, rol/proyecto, revocación y archivo en las rutas M4 con su sustituto.
No limpia ni modifica las bases instaladas.

La evidencia de ejecución y las limitaciones del entorno se registran en el
contexto TIC de David. La integración de este M0 en M1 y la adaptación de sus
imports/manuales Flyway son el siguiente trabajo; M1 no cambia en esta conciliación.

Resultado local del20/11/2026:165 pruebas backend, cero fallos/errores/omisiones;
27 pruebas frontend, compilación real/demo y5 pruebas de navegación correctas.
El recorrido TC-10 con JAR, API real, Chrome y PostgreSQL temporal también pasó;
contratos y ambas configuraciones Compose validados. Los procesos temporales
se detuvieron al terminar. Docker no se reconstruyó ni se migraron bases existentes.
