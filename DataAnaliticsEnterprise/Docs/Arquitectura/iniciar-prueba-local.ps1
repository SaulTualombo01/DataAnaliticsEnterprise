param([switch]$Bootstrap, [string]$WorkerId = '')
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $root
# Solo contenedor local de pruebas. Nunca usa variables de conexión reales del usuario.
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:M2_DB_JDBC_URL = 'jdbc:postgresql://127.0.0.1:55432/m2_test'
$env:M2_DB_USERNAME = 'm2_test_app'
$env:M2_DB_PASSWORD = 'm2_app_local_test_only'
$env:M2_DATA_KEY_ID = 'local-test'
$env:M2_DATA_KEY_BASE64 = 'AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ='
$env:M2_EMAIL_LOOKUP_KEY_BASE64 = 'AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI='
$env:M2_WORKER_USER_ID = $WorkerId
$env:M2_BOOTSTRAP_ENABLED = $Bootstrap.ToString().ToLowerInvariant()
if ($Bootstrap) {
    $env:M2_BOOTSTRAP_PROJECT_ID = 'b19b98b7-e715-42ca-b5af-02d665db48ad'
    $env:M2_BOOTSTRAP_ORGANIZATION_ID = 'a53e651d-b2fe-4c71-9051-0dd92b09b102'
    $env:M2_BOOTSTRAP_PROJECT_NAME = 'Proyecto ficticio local'
    $env:M2_BOOTSTRAP_ADMIN_EMAIL = 'admin@example.test'
    $env:M2_BOOTSTRAP_ADMIN_PASSWORD = 'Admin-Local-Test-2026'
    $env:M2_BOOTSTRAP_QUALITY_EMAIL = 'calidad@example.test'
    $env:M2_BOOTSTRAP_QUALITY_PASSWORD = 'Quality-Local-Test-2026'
    $env:M2_BOOTSTRAP_WORKER_EMAIL = 'worker@example.test'
}
& java -jar (Join-Path $root 'target/DataAnaliticsEnterprise-1.0-SNAPSHOT.jar')
exit $LASTEXITCODE
