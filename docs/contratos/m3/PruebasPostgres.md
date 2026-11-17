# M3 — Pruebas con PostgreSQL

## Resultado vigente (rama integrada con el tronco común)

17 de noviembre de 2026, PostgreSQL **18.7**, base vacía `m3_integrada`:

- **216 pruebas aprobadas, 0 fallos**: las del tronco común y las de M3.
- Flyway aplicó `V1`–`V6` (tronco) y `V30`–`V34` (M3) con el usuario `tic_app`, sin
  superusuario.
- Clases de M3 con PostgreSQL:
  - `GobiernoPostgresTests` (4), con los permisos de `V34`;
  - `PoliticasPostgresTests` (4);
  - `CumplimientoPostgresTests` (2);
  - `LinajePostgresTests` (1);
  - `HistorialPostgresTests` (2);
  - `GobiernoSeguridadPostgresTests` (3), con sesión, CSRF y roles reales.
- Cubren:
  - inmutabilidad de auditoría, versiones y evaluaciones (triggers);
  - unicidad de nombres y reversión completa de la transacción;
  - búsqueda con `%` literal y en `jsonb`;
  - bloqueo por proyecto y ciclos de linaje;
  - comparación de versiones leídas de la base;
  - permisos por rol.

Antes de integrar (fase 5, parte C) se habían aprobado 78 pruebas sobre `m3_pruebas`, con solo
`V30`–`V33`. Esa base ya no sirve para esta rama: le faltan las migraciones del tronco.

## Entorno: el mismo PostgreSQL de `main`

Se usa el `docker-compose.yml` de `main` (imagen `postgres:18`, usuario `tic_app` sin
superusuario, puerto 55432), igual que el resto del equipo. M3 usa **sus propias bases** en ese
servidor, no `tic_tc_dev`, porque Flyway rechaza arrancar sobre una base con migraciones de otra
rama (`main` tiene otra `V4`).

- `m3_integrada`: la base de las pruebas.
- `m3_integrada_upgrade`: la usa `MigracionEmpresaPostgresTests` del tronco para probar una
  actualización desde V3. **Debe estar vacía en cada ejecución**; si no, esa prueba falla.

Para no añadir archivos de `main` a esta rama, el compose se copia a una carpeta aparte.

## Cómo repetirlo

En una carpeta fuera del repositorio, desde PowerShell:

```powershell
git -C D:\EPN\TIC\DataAnaliticsEnterprise show origin/main:docker-compose.yml > docker-compose.yml
git -C D:\EPN\TIC\DataAnaliticsEnterprise show origin/main:inicializar-postgres.sh > inicializar-postgres.sh
```

Crear un `.env` en esa carpeta con dos contraseñas locales distintas (no se suben al repositorio):

```
POSTGRES_PASSWORD=<una contraseña larga>
DB_PASSWORD=<otra contraseña larga>
TIC_TC_DB_PORT=55432
```

Levantar el servidor y preparar las bases. La de actualización se recrea antes de cada ejecución:

```powershell
docker compose up -d --wait database
docker compose exec -T database psql -U postgres -d tic_tc_dev -c "CREATE DATABASE m3_integrada OWNER tic_app;"
docker compose exec -T database psql -U postgres -d tic_tc_dev -c "DROP DATABASE IF EXISTS m3_integrada_upgrade;" -c "CREATE DATABASE m3_integrada_upgrade OWNER tic_app;"
```

Ejecutar todas las pruebas desde la raíz del repositorio:

```powershell
$env:DB_URL = "jdbc:postgresql://127.0.0.1:55432/m3_integrada"
$env:DB_UPGRADE_URL = "jdbc:postgresql://127.0.0.1:55432/m3_integrada_upgrade"
$env:DB_USER = "tic_app"
$env:DB_PASSWORD = "<DB_PASSWORD del .env>"
mvn -f .\DataAnaliticsEnterprise\pom.xml -Ppostgres-it clean verify
```

Sin `-Ppostgres-it` solo se ejecutan las pruebas que no necesitan base de datos (98).

Las pruebas crean datos con proyectos aleatorios y no los borran (los históricos no se pueden
borrar). Usar estas bases solo para pruebas. `docker compose down` detiene el contenedor y
conserva el volumen.
