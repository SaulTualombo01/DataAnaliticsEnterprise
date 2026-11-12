[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$testContainer = 'm2-role-test-' + [Guid]::NewGuid().ToString('N')
$testPassword = [Guid]::NewGuid().ToString('N')
$jdbcBefore = $env:M2_ROLE_TEST_JDBC_URL
$passwordBefore = $env:M2_ROLE_TEST_DB_PASSWORD
$testExit = 1
Push-Location -LiteralPath $root
try {
    & docker run --detach --name $testContainer --publish '127.0.0.1::5432' --env 'POSTGRES_DB=m2_role_test' --env "POSTGRES_PASSWORD=$testPassword" postgres:17 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL de pruebas.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $testContainer pg_isready --host=127.0.0.1 --username=postgres --dbname=m2_role_test *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'PostgreSQL de pruebas no quedó listo.' }
    $schema = Join-Path $root 'Backend/src/main/java/org/dataanaliticsent/Calidad/Docker/schema-local.sql'
    & docker cp $schema "${testContainer}:/tmp/schema.sql"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar el esquema de pruebas.' }
    & docker exec $testContainer psql --username=postgres --dbname=m2_role_test --set=ON_ERROR_STOP=1 --file=/tmp/schema.sql
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el esquema de pruebas.' }
    $migration = Join-Path $root 'Backend/src/main/java/org/dataanaliticsent/Calidad/Docker/migrate-operational-limits.sql'
    & docker cp $migration "${testContainer}:/tmp/operational.sql"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar la migración operativa.' }
    & docker exec $testContainer psql --username=postgres --dbname=m2_role_test --set=ON_ERROR_STOP=1 --file=/tmp/operational.sql
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo aplicar la migración operativa.' }
    "CREATE ROLE m2_app LOGIN PASSWORD '$testPassword' NOBYPASSRLS; GRANT m2_backend TO m2_app;" |
        & docker exec --interactive $testContainer psql --username=postgres --dbname=m2_role_test --set=ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el usuario de pruebas.' }
    $published = & docker port $testContainer 5432/tcp
    if ($LASTEXITCODE -ne 0 -or $published -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Puerto local de pruebas inesperado.' }
    $env:M2_ROLE_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$($Matches[1])/m2_role_test"
    $env:M2_ROLE_TEST_DB_PASSWORD = $testPassword
    & mvn --batch-mode --no-transfer-progress test
    $testExit = $LASTEXITCODE
}
finally {
    $env:M2_ROLE_TEST_JDBC_URL = $jdbcBefore
    $env:M2_ROLE_TEST_DB_PASSWORD = $passwordBefore
    & docker rm --force --volumes $testContainer | Out-Null
    Pop-Location
}
exit $testExit
