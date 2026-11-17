# M3 — Pruebas con PostgreSQL (fase 5, parte C)

## Resultado

17 de noviembre de 2026, rama `modulo3_gobierno`, PostgreSQL **18.7**:

- **78 pruebas aprobadas, 0 fallos**: 66 sin base de datos y 12 con PostgreSQL real.
- Flyway aplicó `V30` a `V33` en una base vacía con el usuario `tic_app` (sin superusuario).
- Clases con PostgreSQL: `GobiernoPostgresTests` (3), `PoliticasPostgresTests` (4),
  `CumplimientoPostgresTests` (2), `LinajePostgresTests` (1) y `HistorialPostgresTests` (2).
  Cubren:
  - inmutabilidad de auditoría, versiones y evaluaciones (triggers);
  - unicidad de nombres y reversión completa de la transacción;
  - búsqueda con `%` literal;
  - lectura del detalle `jsonb`;
  - bloqueo por proyecto y ciclos de linaje;
  - búsqueda de la cronología en `jsonb`;
  - comparación de versiones leídas de la base.

## Entorno: el mismo PostgreSQL de `main`

Se usa el `docker-compose.yml` de `main` (imagen `postgres:18`, usuario `tic_app` sin
superusuario, puerto 55432), igual que el resto del equipo. M3 usa **su propia base**,
`m3_pruebas`, en ese servidor. No usa `tic_tc_dev`, porque Flyway rechaza arrancar sobre una base
con migraciones de otra rama (`main` tiene V1–V4; esta rama, V30–V33).

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

Levantar la base y crear la de M3 (solo la primera vez):

```powershell
docker compose up -d --wait database
docker compose exec -T database psql -U postgres -d tic_tc_dev -c "CREATE DATABASE m3_pruebas OWNER tic_app;"
```

Ejecutar todas las pruebas desde la raíz del repositorio:

```powershell
$env:DB_URL = "jdbc:postgresql://127.0.0.1:55432/m3_pruebas"
$env:DB_USER = "tic_app"
$env:DB_PASSWORD = "<DB_PASSWORD del .env>"
mvn -f .\DataAnaliticsEnterprise\pom.xml -Ppostgres-it clean verify
```

Sin `-Ppostgres-it` solo se ejecutan las 66 pruebas que no necesitan base de datos.

Las pruebas crean datos con proyectos aleatorios y no los borran (los históricos no se pueden
borrar). Usar `m3_pruebas` solo para pruebas. `docker compose down` detiene el contenedor y
conserva el volumen.
