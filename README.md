# DataAnaliticsEnterprise
Página Web que permite el análisis de datos empresariales

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

Ese perfil incluye migraciones reales, servidor HTTP, persistencia, aislamiento
por proyecto, rollback e inmutabilidad histórica. Sin el perfil, las pruebas
etiquetadas `postgres` se excluyen expresamente. Para ejecutar el JAR, seleccionar
la base de desarrollo y mantener las variables de conexión:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\DataAnaliticsEnterprise\target\DataAnaliticsEnterprise-1.0-SNAPSHOT.jar
```

El empaquetado genera un JAR ejecutable con sus dependencias. Flyway valida y
aplica migraciones pendientes al arrancar; no editar una migración ya aplicada.

### Ramas de trabajo

La base compartida se mantiene en `modulo0_troncoComun`. Esta rama M1 incorpora
su commit común y añade únicamente el trabajo específico de catálogo. Las
migraciones compartidas son V1, V1_1 y V1_2; M1 añade V2 y sus claves foráneas
para alcances sobre fuentes/activos. Usar bases dedicadas por conjunto de
migraciones. La numeración se reorganizó antes de publicar estas migraciones;
si se hubiera aplicado la versión preliminar V3 a una base de pruebas, conservar
su respaldo y preparar una base nueva: no reparar checksums automáticamente.

### Alcance del primer incremento del modelo

- Base SQL de identidad/contexto/RBAC, auditoría/versionado y catálogo M1.
- Catálogo inicial de cinco roles y 55 permisos, con el diagrama ER opcional deshabilitado.
- Dominio de M1 sin dependencias de Spring ni JDBC; puerto de persistencia interno.
- Adaptador PostgreSQL para registrar/recuperar fuentes y el primer estado de activo/campos.
- Registro de activo, campos, evento y versión de metadatos en una sola transacción.
- Consultas con proyecto explícito y claves compuestas que impiden relaciones entre proyectos incompatibles.

Esto todavía no implementa login, evaluación RBAC, administración web, conectores
de descubrimiento, edición/redescubrimiento versionado o TC13. No se publican
endpoints de negocio; el repositorio interno no sustituye la autorización del caso
de uso. Las relaciones futuras de gobierno M3 se incorporarán en su incremento.
La base puede representar varias asignaciones, pero no habilita por sí misma una
política de múltiples roles/proyectos por cuenta.

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
