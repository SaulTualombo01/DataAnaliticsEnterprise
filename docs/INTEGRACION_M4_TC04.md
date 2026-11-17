# Integración del tronco común con M4 y TC04

El Compose levanta PostgreSQL 18 con la base local `tic_tc_dev`. Flyway administra el esquema cuando arranca Spring Boot o cuando se ejecuta el perfil Maven `postgres-it`.

## Levantar la base

Desde la raíz del repositorio en PowerShell, con Docker Desktop, Maven y JDK 23 configurados:

```powershell
Copy-Item .env.example .env
```

Completa `POSTGRES_PASSWORD` y `DB_PASSWORD` en `.env` con dos contraseñas locales distintas. Usa valores alfanuméricos largos y aleatorios para evitar problemas de interpolación. Configura `TIC_TC_DB_PORT` si el puerto 55432 está ocupado.

```powershell
docker compose up -d --wait database
docker compose ps database
```

La primera inicialización crea el rol SQL `tic_app` con acceso a la base de desarrollo y capacidad para aplicar sus migraciones. Si reutilizas el volumen, las contraseñas pertenecen a la inicialización original. Para una ejecución de pruebas dedica este Compose a datos de prueba. `docker compose down` detiene el entorno y conserva el volumen.

## Ejecutar las pruebas de integración

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/tic_tc_dev'
$env:DB_USER = 'tic_app'
$clave = Read-Host 'DB_PASSWORD del archivo .env' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $clave).Password
mvn -f .\DataAnaliticsEnterprise\pom.xml -Ppostgres-it verify
```

Ajusta el puerto de la URL si cambiaste `TIC_TC_DB_PORT`. En IntelliJ, importa `DataAnaliticsEnterprise/pom.xml`, selecciona JDK 23 para Maven y ejecuta `verify -Ppostgres-it` con las mismas tres variables en la configuración de ejecución.

## Probar el POST de M4

Una vez incorporado el controlador de M4 al tronco común, inicia sesión con la identidad compartida. Solicita CSRF en `GET /api/v1/auth/csrf`, usa ese token para `POST /api/v1/auth/login` con formulario y vuelve a solicitarlo antes de enviar el POST de analítica. Conserva la cookie de sesión y envía el token en la cabecera indicada por `header_name`.

`POST /api/v1/projects/{projectId}/analytics/kpis/evaluate`

```json
{
  "asset_id": "4cb32a26-45d1-4d19-8521-408bda47138d",
  "kpi_codes": ["TICKET_PROMEDIO"]
}
```

Sustituye el proyecto y el activo de ejemplo por identificadores de tu entorno. `V4__permiso_evaluar_kpis_m4.sql` concede `m4.kpis.evaluate` a `ANALISTA_NEGOCIO`. En la cuenta que ejecuta la prueba deben existir una membresía activa, una asignación de ese rol y un alcance vigente que permita el proyecto. La asignación inicial a ese rol es la propuesta de integración. El servicio M4 comprueba el permiso en cada solicitud.

| Caso | Resultado esperado |
| --- | --- |
| Sesión, CSRF y permiso sobre el proyecto | HTTP 200 |
| Sesión sin CSRF | HTTP 403 `CSRF_INVALID` |
| Sesión y CSRF con rol sin permiso o proyecto ajeno | HTTP 403 |
| Ping o evaluación con CSRF válido y sin sesión autenticada | HTTP 401 |

El controlador de la rama M4 todavía calcula sobre pedidos sintéticos. El contrato de datos reales requiere la lectura de TC13 y su `read_id`.

## Archivos para compartir

- `docker-compose.yml`, `inicializar-postgres.sh`, `.gitattributes` y `.env.example` preparan PostgreSQL. `.gitignore` excluye las credenciales locales.
- `DataAnaliticsEnterprise/Backend/src/main/resources/db/migration/V4__permiso_evaluar_kpis_m4.sql` registra el permiso.
- `DataAnaliticsEnterprise/Backend/src/main/java/org/dataanaliticsent/identidad/internal/infraestructura/SeguridadWebConfig.java` protege las rutas exactas de M4 con sesión.
- `docs/INTEGRACION_M4_TC04.md` reúne las instrucciones de uso. Los contratos de M4 y TC04 están en `docs/contratos/m4` y `docs/contratos/tc04` de sus respectivas ramas.

Al integrar TC04 conserva también sus reglas de acceso para las rutas administrativas. Las rutas de analítica y contexto deben quedar antes de `anyRequest().denyAll()`.
