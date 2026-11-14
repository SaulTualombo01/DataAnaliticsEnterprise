# DataAnaliticsEnterprise
Página Web que permite el análisis de datos empresariales

## Interfaz web compartida (Q1)

El contenedor React/TypeScript está en [Frontend](Frontend/README.md). Incluye
navegación para M1–M4, lector de catálogo/diccionario y estados comunes. Desde
Frontend, ejecutar `npm ci` y `npm run dev:demo` para la demostración explícita,
o `npm run dev` para utilizar la sesión real y el proxy de API.

La selección real de proyectos y la administración completa pertenecen a Q2.
Consultar catálogo real requiere el backend de M1-04; la rama TC sola no publica
esas rutas. Las vistas especializadas M2/M3/M4 siguen pendientes.

[Contenedor y mocks Q1](docs/contratos/ContenedorWebQ1.md) documenta los límites.
El workflow [.github/workflows/ci.yml](.github/workflows/ci.yml) prepara compilación,
pruebas y verificación del frontend; su ejecución remota requiere publicar los cambios.

## Contrato y mock TC13 (Q1)

David Valencia es responsable técnico provisional de TC13. El candidato
[TC13 0.1.0](docs/contratos/tc13/TC13.md) incluye OpenAPI, JSON Schema, ejemplos y
un mock local para lecturas acotadas, recibos y capturas sintéticas. La revisión
de M2/M3/M4 sigue pendiente; no es un servicio de extracción empresarial real.
Ejecutar `python tools/mock_tc13.py` desde este checkout. El workflow valida
contratos y prueba el mock además de backend y frontend.

## Backend Spring Boot

El backend es un monolito modular con Spring Boot 4.1.2. La clase
`org.dataanaliticsent.DataAnaliticsEnterprise` inicia la aplicación y detecta
los componentes de los módulos que se añadan bajo `org.dataanaliticsent`.
El código, las pruebas y los recursos conservan sus rutas en `Backend/src/`.

### Requisitos

- Maven 3.6.3 o superior.
- Compilación para Java 23. En el equipo de David se utiliza el JDK 24 instalado.
- En IntelliJ, seleccionar JDK 24 para el proyecto y para Maven, y recargar el POM.
- PostgreSQL 18 para el entorno validado. La base de plataforma es independiente de las fuentes catalogadas.

### Base de datos de desarrollo

Se utiliza Spring JDBC y Flyway como mecanismo único de migración. Las migraciones
crean los esquemas `identidad`, `contexto`, `autorizacion`, `catalogo`, `auditoria`
y `versionado`. Usar una base dedicada y vacía para este proyecto.

Con una cuenta de administración de PostgreSQL, preparar un usuario de desarrollo
sin privilegios de superusuario y su base (nombres de ejemplo):

```sql
CREATE ROLE data_analytics_app LOGIN;
-- En psql: \password data_analytics_app
CREATE DATABASE data_analytics OWNER data_analytics_app;
```

El propietario de esta base de desarrollo puede ejecutar las migraciones. En un
despliegue compartido se deben separar las credenciales de migración y ejecución,
con los permisos mínimos; no utilizar la cuenta superusuario para la aplicación.
No hay cuentas de aplicación, contraseñas ni asignaciones de administrador sembradas.

### Ejecutar desde la raíz del repositorio

En PowerShell, seleccionar el JDK de la sesión y arrancar el servicio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:DB_URL = "jdbc:postgresql://localhost:5432/data_analytics"
$env:DB_USER = "data_analytics_app"
$dbPassword = Read-Host "Contraseña PostgreSQL" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password
mvn -f .\DataAnaliticsEnterprise\pom.xml spring-boot:run
```

El servidor escucha en `http://localhost:8080`. La variable de entorno `PORT`
permite cambiar el puerto. Detener con `Ctrl+C`.
Todavía no hay endpoints de negocio ni página de inicio: una petición a `/`
devuelve HTTP 404 aunque el servicio esté funcionando.

### Probar y empaquetar

Las pruebas unitarias no necesitan PostgreSQL:

```powershell
mvn -f .\DataAnaliticsEnterprise\pom.xml verify
```

Las pruebas de integración crean datos ficticios. Ejecutarlas **solo en una base
dedicada de pruebas**, configurando DB_URL, DB_USER y DB_PASSWORD para esa base:

```powershell
mvn -f .\DataAnaliticsEnterprise\pom.xml -Ppostgres-it verify
```

Ese perfil incluye migraciones reales, servidor HTTP, integridad de membresías
y alcances, catálogo RBAC e inmutabilidad histórica. Sin el perfil, las pruebas
etiquetadas `postgres` se excluyen expresamente. Para ejecutar el JAR, seleccionar
la base de desarrollo y mantener las variables de conexión:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\DataAnaliticsEnterprise\target\DataAnaliticsEnterprise-1.0-SNAPSHOT.jar
```

El empaquetado genera un JAR ejecutable con sus dependencias. Flyway valida y
aplica migraciones pendientes al arrancar; no editar una migración ya aplicada.

### Rama del tronco común

`modulo0_troncoComun` contiene la base compartida, con Spring Boot, JDBC/Flyway,
contexto, identidad, roles/permisos, alcances, auditoría y versionado. El esquema
vacío `catalogo` se reserva desde V1 para conservar esa migración base; aquí no
se crean tablas, entidades Java ni adaptadores funcionales de M1.

- V1: identidad, contexto, roles/asignaciones y mecanismos históricos.
- V1_1: catálogo de cinco roles y 55 permisos; no crea usuarios ni administrador.
- V1_2: alcances PROJECT/SOURCE/ASSET. Los IDs SOURCE/ASSET se validan por su
  módulo propietario; M1 añade sus claves foráneas al incorporar V2.
- V3: credenciales locales protegidas y versión para revocar sesiones.
- Pruebas del tronco común independientes de las tablas de catálogo.

Los permisos M1 están declarados como operaciones del catálogo común RBAC; no
implican implementar el módulo ni conceder administración funcional al administrador.
La autenticación local con sesión/cookie y el contenedor web están implementados.
La administración completa de usuarios sigue pendiente. El evaluador RBAC interno
se describe en `docs/contratos/AutorizacionInterna.md`.

### Autorización interna

Los casos de uso pueden inyectar `AutorizacionApi` para evaluar o exigir un permiso
vigente. La cuenta, membresías, rol, permiso y alcance se consultan en PostgreSQL
en cada evaluación, sin conservar privilegios en una caché o token local.
El UUID del actor procede de IdentidadActualApi y la identidad verificada en
servidor; esta API de autorización no autentica a una persona ni acepta peticiones HTTP.

Se distinguen SELF, PLATFORM y PROJECT. El administrador global no hereda acceso
a proyectos. Las funciones propias usan titularidad; los recursos SOURCE/ASSET
exigen un proveedor del módulo propietario que resuelva su jerarquía real.
Sin proveedor, con pertenencia incoherente o con múltiples proveedores ambiguos,
la operación no se autoriza. No hay accesos universales para componentes internos.

Las búsquedas y conteos deben aplicar `alcancesPermitidos` a sus consultas; una
lista vacía implica no mostrar datos. La comprobación de un recurso concreto
no habilita listar todo el proyecto. Estados del recurso y reglas funcionales
siguen siendo responsabilidad del caso de uso propietario.

No se crean endpoints de negocio M1 ni endpoints para cambiar roles. Los cambios de roles/permisos deben realizarse
mediante el futuro servicio administrativo autorizado y auditado. Las pruebas
modifican fixtures SQL para comprobar que el evaluador refleja esos cambios.

### Autenticación y sesiones

Contrato: `docs/contratos/AutenticacionSesiones.md` y su OpenAPI adyacente.
El actor de los casos de uso se obtiene de `IdentidadActualApi.usuarioId()`;
no debe tomarse de una cabecera o cuerpo enviado por el cliente.

La cookie `TICSESSION` es HttpOnly, SameSite=Lax y Secure por defecto. Usar HTTPS
en el despliegue compartido. Solo para desarrollo local mediante HTTP:

```powershell
$env:SESSION_COOKIE_SECURE = "false"
```

La sesión reside en el servidor de esta instancia: reiniciar la aplicación cierra
las sesiones. Inactividad: 30 minutos; duración absoluta: 8 horas. Se pueden ajustar
con SESSION_TIMEOUT y SESSION_ABSOLUTE_TIMEOUT. No hay remember-me ni JWT.
El cambio de contraseña, la revocación administrativa y los cambios de estado de
cuenta invalidan sesiones anteriores en su siguiente solicitud. Los permisos RBAC
se siguen consultando, por lo que no hay roles obsoletos dentro de la cookie.

Antes de entrar, pedir GET /api/v1/auth/csrf con el mismo almacén de cookies que
se usará después. Enviar el token y la cabecera devueltos al hacer POST login
(formulario URL-encoded con email/password). Después de entrar, volver a pedir
CSRF antes de otra escritura. GET /api/v1/auth/me devuelve solo el perfil propio;
POST logout cierra la sesión. Los errores usan Problem Details con request_id.

La limitación de intentos es local y acotada a esta instancia: 5 por cuenta y 50
por IP en ventana fija de 15 minutos. Parámetros LOGIN_ACCOUNT_LIMIT, LOGIN_IP_LIMIT
y LOGIN_WINDOW. No se confía en X-Forwarded-For libre. Un despliegue con múltiples
instancias o proxy necesita revisar almacenamiento de sesiones y límites compartidos.

#### Inicializar el primer administrador

Solo para una base nueva sin cuentas. Es optativo, no hay contraseña predeterminada:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host "Correo del primer administrador"
$env:BOOTSTRAP_ADMIN_NAME = Read-Host "Nombre"
$adminPassword = Read-Host "Contraseña inicial" -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $adminPassword).Password
mvn -f .\DataAnaliticsEnterprise\pom.xml spring-boot:run
```

La contraseña debe tener al menos 15 caracteres y hasta 72 bytes UTF-8; admite
espacios y Unicode. Se guarda con BCrypt coste 12, nunca en claro. Retirar las
variables BOOTSTRAP_ADMIN_* después de inicializar. Si ya existe un administrador
activo, no se cambia su cuenta ni contraseña; si existen cuentas sin administrador,
se exige un procedimiento de recuperación explícito, no una promoción automática.
Todavía no se ofrece recuperación por correo ni CRUD web de cuentas.

`modulo1_catalogo` incorpora esta base por historial Git y añade dominio,
persistencia y migración V2 de M1. Después de integrar futuros cambios comunes,
ejecutar las pruebas de la rama consumidora. Usar bases dedicadas distintas para
ramas con diferentes conjuntos de migraciones; no desactivar la validación Flyway.
Después de cambiar de rama, ejecutar `mvn -f .\DataAnaliticsEnterprise\pom.xml clean verify`
para retirar clases compiladas de la rama anterior; usar `-Ppostgres-it` y una base
de pruebas dedicada para la verificación PostgreSQL completa.

### Evolución y recuperación de migraciones

Flyway mantiene su historial y valida checksums. Las migraciones de este primer
incremento son transaccionales en PostgreSQL; un error revierte la migración que
está fallando, no las versiones anteriores ya confirmadas. Corregir mediante una
nueva migración o un procedimiento revisado según el entorno. `clean` está
deshabilitado. Para una reversión operativa, restaurar el respaldo validado de la
base dedicada; no se incluye borrado automático de esquemas ni un script destructivo.

El estado histórico se almacena como representación tipada versión 1 en JSONB.
Su hash SHA-256 verifica la representación escrita; el comparador semántico para
decidir cambios en un redescubrimiento se desarrollará con ese caso de uso.
