# Integración del tronco común con M4 y TC04

La conciliación del 20/11/2026 conserva M0 como entorno principal y ofrece el
Compose anterior de M4 como alternativa explícita. Consultar
[Conciliacion_M0_M4.md](Conciliacion_M0_M4.md) para las dos historias V4.

## Entorno principal

Usar docker-compose.yml y tools/Preparar-Docker.ps1 según
[Docker_Desarrollo.md](Docker_Desarrollo.md). Mantiene PostgreSQL16 en
localhost5434, data_analytics/data_analytics_app y secretos locales.
No requiere el .env.example de la raíz, que pertenece al entorno M4 anterior.

## Entorno anterior de M4 (opcional)

Mantiene proyecto tic-tc-dev, PostgreSQL18, base tic_tc_dev, rol tic_app,
volumen tic_tc_postgres y puerto local55432. No reemplaza ni convierte el volumen
de M0. Desde la raíz, con Docker Desktop y solo si aún no existe el archivo:

~~~powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
~~~

Completar POSTGRES_PASSWORD y DB_PASSWORD con dos contraseñas aleatorias distintas.
TIC_TC_DB_PORT permite cambiar el puerto. No sobrescribir credenciales existentes.

~~~powershell
docker compose -f docker-compose.m4-legacy.yml up -d --wait database
docker compose -f docker-compose.m4-legacy.yml ps database
~~~

inicializar-postgres.sh crea tic_app sin superusuario en la primera inicialización.
Cambiar el .env no cambia contraseñas de un volumen ya inicializado. Para detener
conservando los datos:

~~~powershell
docker compose -f docker-compose.m4-legacy.yml down
~~~

Para ejecutar el backend en este entorno, usar JDK24 y release23. Las variables
del proceso tienen prioridad sobre el .env del módulo:

~~~powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/tic_tc_dev'
$env:DB_USER = 'tic_app'
$clave = Read-Host 'DB_PASSWORD del entorno M4' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $clave).Password
$env:SESSION_COOKIE_SECURE = 'false'
mvn -f .\DataAnaliticsEnterprise\pom.xml spring-boot:run
~~~

Ajustar el puerto si cambió TIC_TC_DB_PORT. Flyway elige automáticamente el
historial M0/M4; nunca desactivar su validación ni ejecutar repair.
Las pruebas completas deben usar python tools/verificar_postgres_tc.py,
que prepara bases temporales exclusivas, incluidas las de actualización.
No ejecutar las pruebas de fixtures contra una base compartida o con datos útiles.

## Integrar el controlador de M4

Esta rama no publica el controlador de negocio. Al incorporarlo desde M4:

1. Importar IdentidadActualApi, AutorizacionApi y SolicitudAcceso desde
   org.dataanaliticsent.TroncoComun; no acceder a paquetes internal ajenos.
2. Obtener el actor de la sesión con IdentidadActualApi.usuarioId(), nunca del cuerpo.
3. Exigir m4.kpis.evaluate sobre el projectId y aplicar alcances antes de leer datos.
4. Respetar revisión/estado del recurso y las validaciones propias de M4.

GET /api/v1/analitica/ping exige sesión. POST
/api/v1/projects/{projectId}/analytics/kpis/evaluate exige sesión y CSRF.
La cuenta necesita membresías vigentes, ANALISTA_NEGOCIO en ese proyecto y un
alcance adecuado. V8 agrega el permiso en M0; la V4 histórica M4 se conserva.
Un privilegio retirado anteriormente en M4 no se restaura al migrar.

Antes del login, solicitar GET /api/v1/auth/csrf con el mismo almacén de cookies.
Enviar el token al POST /api/v1/auth/login como formulario email/password.
Volver a solicitar CSRF después de entrar y conservar la cookie TICSESSION.
Enviar el token en la cabecera indicada por header_name.

Solicitud propuesta de la rama M4, pendiente de incorporar su controlador:

~~~json
{"asset_id":"4cb32a26-45d1-4d19-8521-408bda47138d","kpi_codes":["TICKET_PROMEDIO"]}
~~~

| Caso | Resultado esperado tras integrar el controlador |
| --- | --- |
| Sesión, CSRF y permiso sobre proyecto activo | HTTP200 |
| Sesión sin CSRF | HTTP403 CSRF_INVALID |
| Sesión y CSRF, rol incorrecto/proyecto ajeno/archivado/revocación | HTTP403 |
| Ping sin sesión, o evaluación con CSRF válido sin sesión | HTTP401 |

La suite de M0 valida estas barreras con un sustituto exclusivo de pruebas.
El controlador de M4 descrito originalmente calcula con pedidos sintéticos;
la lectura empresarial real depende de TC13 y su read_id. Contratos especializados
M4/TC04 siguen en sus respectivas ramas. Se mantienen también las rutas
administrativas de TC y la denegación por defecto del resto de rutas.
