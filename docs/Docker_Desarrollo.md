# Docker local para TC

Configuración del16/11/2026 en modulo0_troncoComun. Se reutiliza la imagen local
postgres:16-alpine (16.15 verificada). La base Windows usa5432 y panaderia_db
publica5433; TIC usa127.0.0.1:5434, con volumen/proyecto separados.

## Preparar y levantar solo PostgreSQL

Desde PowerShell, en la raíz:

~~~powershell
cd "C:\Users\DVC\IdeaProjects\DataAnaliticsEnterprise"
powershell -ExecutionPolicy Bypass -File .\tools\Preparar-Docker.ps1
docker compose up -d --wait postgres
docker compose ps
~~~

El script genera dos contraseñas aleatorias locales la primera vez y después
las conserva. Actualiza únicamente DB_URL/DB_USER/DB_PASSWORD del .env del
backend para este entorno Docker. Cambia su contraseña local a la de la nueva
base del contenedor; no altera ninguna cuenta/base de Windows ni otro proyecto.
No imprime contraseñas. La configuración de PostgreSQL nativo sigue siendo
una alternativa manual distinta y puede necesitar restaurar su conexión propia.

Conexión desde IntelliJ/psql local:

| Campo | Valor |
| --- | --- |
| Host | localhost |
| Puerto |5434 |
| Base |data_analytics |
| Usuario |data_analytics_app |
| Contraseña | Valor local de DB_PASSWORD en DataAnaliticsEnterprise/.env |

El puerto dentro de la red Compose es5432 y el hostname del servicio es postgres.
El init del volumen vacío crea una cuenta LOGIN sin superusuario/CREATEDB/
CREATEROLE y su base propia. No hace falta crear el usuario por SQL Shell.
Flyway crea/valida los esquemas al arrancar el backend.

Para abrir psql directamente en el contenedor:

~~~powershell
docker compose exec postgres psql -U data_analytics_app -d data_analytics
~~~

La imagen oficial permite conexiones locales por socket durante este entorno
de desarrollo; una conexión TCP desde Windows sí usa contraseña SCRAM.
No exponer el puerto en todas las interfaces ni usar esta configuración
como despliegue productivo sin revisión.

## Imagen y contenedor del servidor Spring Boot

Dockerfile multietapa en DataAnaliticsEnterprise/Dockerfile: Maven3.9.10/JDK24
compila release23, ejecuta las unitarias sin base y empaqueta el JAR. La etapa
final usa eclipse-temurin:24-jre y UID10001 sin root, raíz de solo lectura y
tmpfs para archivos temporales. No necesita Java/Maven instalados en Windows
para construir la imagen; Docker descarga las imágenes base si faltan.

~~~powershell
docker compose --profile servidor build backend
docker compose --profile servidor up -d --wait
docker compose --profile servidor ps
~~~

También se puede construir/levantar en una sola orden:

~~~powershell
docker compose --profile servidor up -d --build
~~~

El backend escucha en http://127.0.0.1:8080. /api/v1/auth/me devuelve401 sin
sesión: confirma que el servicio responde, no una avería. El frontend continúa
en Frontend con npm run dev y su proxy habitual a8080. No iniciar simultáneamente
otro backend local en ese puerto.

El perfil servidor espera el healthcheck TCP de PostgreSQL. El backend toma
DB_URL/DB_USER de Compose y DB_PASSWORD de un secreto montado vía configtree
de Spring; no lee el .env de Windows dentro de la imagen. La cookie se ajusta
para HTTP local; bootstrap e invitaciones manuales están deshabilitados
explícitamente en Compose. El bootstrap de administrador y claves maestras TC-11 no se generan
automáticamente: habilitarlos requiere configuración adicional del despliegue.
El administrador de PostgreSQL no es una cuenta de acceso a la interfaz.

## Persistencia y credenciales

- Proyecto Compose: tic-data. Volumen: tic-data_postgres_data, formato PostgreSQL16.
- Archivos privados: .docker/secrets/postgres_admin_password y app_db_password.
  .gitignore los excluye, al igual que .env. No copiarlos a la memoria TIC.
- El backend recibe solo su contraseña, no la administrativa. El script de
  inicialización usa la administrativa de la imagen oficial y crea el rol de
  aplicación independiente. El dueño de la base puede migrar sus esquemas;
  en producción separar usuarios de migración y ejecución.
- Dockerfile/.dockerignore limitan el contexto a POM/fuentes, sin .env, secretos,
  .idea, target ni frontend. Ninguna contraseña se pasa como argumento de build.
- Preparar-Docker conserva los secretos existentes y rechaza una pareja parcial
  o un volumen TIC existente sin sus secretos. Conservar/custodiar esos archivos
  junto con los respaldos. No son un KMS ni secretos cifrados en disco por Compose.
- El init se ejecuta exclusivamente con volumen vacío. Cambiar .env o los
  archivos después no cambia la contraseña almacenada en PostgreSQL. No
  regenerar archivos para intentar corregir un error de autenticación.
- No cambiar la versión mayor de la imagen sobre el mismo volumen sin una
  migración/respaldo apropiados. El entorno nativo18 queda intacto y separado.

## Comandos habituales

~~~powershell
docker compose logs --tail 100 postgres
docker compose --profile servidor logs --tail 100 backend
docker compose --profile servidor stop
docker compose --profile servidor down
~~~

stop/down conservan el volumen con datos. down elimina los contenedores/red
del proyecto, no la imagen ni los archivos privados. Evitar down -v: elimina
el volumen y sus datos. No se preparó una orden de reinicio destructivo.

## Referencias

Se siguieron la [imagen oficial PostgreSQL](https://hub.docker.com/_/postgres),
los [secrets de Compose](https://docs.docker.com/compose/how-tos/use-secrets/)
y [psql16](https://www.postgresql.org/docs/16/app-psql.html).
La configuración está pensada para desarrollo local y conserva las ramas
M1/main sin cambios; no equivale a un despliegue productivo ni cierre del sprint.
