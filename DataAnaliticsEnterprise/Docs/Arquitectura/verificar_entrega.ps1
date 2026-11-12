[CmdletBinding()]
param([switch]$SkipBuild)
. (Join-Path $PSScriptRoot 'respaldo-comun.ps1')
$suffix = [Guid]::NewGuid().ToString('N').Substring(0,12)
$sourceProject = "m2-delivery-$suffix"
$restoreProject = "m2-restore-$suffix"
$image = "m2-delivery-${suffix}-application"
$work = $null
$sourceCompose = $null
$restoreCompose = $null
$savedEnvironment = @{}
foreach ($name in @('M2_TEST_ISOLATED','M2_TEST_ENV_FILE','M2_TEST_BASE_URL','M2_TEST_PROJECT_ID','M2_TEST_QUALITY_PASSWORD','M2_BACKUP_PASSWORD')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
function New-TestSecret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
function Get-TestPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
Push-Location -LiteralPath $backupRoot
try {
    if (!$SkipBuild) {
        & npm --prefix Frontend ci
        if ($LASTEXITCODE -ne 0) { throw 'Falló npm ci.' }
        & npm --prefix Frontend run build
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación del frontend.' }
        $shell = (Get-Process -Id $PID).Path
        & $shell -NoProfile -ExecutionPolicy Bypass -File Docs/Arquitectura/verificar_revocacion_roles.ps1
        if ($LASTEXITCODE -ne 0) { throw 'Falló la regresión PostgreSQL/HTTP/SMTP.' }
    }
    $work = New-PrivateBackupDirectory (Join-Path $backupRoot "target/backup-work-delivery-$suffix")
    $environment = Join-Path $work 'test.env'
    $port = Get-TestPort
    $restorePort = Get-TestPort
    while ($restorePort -eq $port) { $restorePort = Get-TestPort }
    $project = [Guid]::NewGuid().ToString()
    $qualityPassword = New-TestSecret
    $lines = @("M2_HTTP_PORT=$port", "M2_APPLICATION_IMAGE=$image", "M2_BOOTSTRAP_PROJECT_ID=$project",
        ('M2_POSTGRES_PASSWORD=' + (New-TestSecret)), ('M2_DB_PASSWORD=' + (New-TestSecret)),
        ('M2_DATA_KEY_BASE64=' + (New-TestSecret)), ('M2_EMAIL_LOOKUP_KEY_BASE64=' + (New-TestSecret)),
        ('M2_ADMIN_PASSWORD=' + (New-TestSecret)), "M2_QUALITY_PASSWORD=$qualityPassword", 'M2_RECOVERY_ENABLED=false', 'M2_OPERATIONS_POLL_MS=1000')
    [IO.File]::WriteAllLines($environment, $lines, (New-Object Text.UTF8Encoding $false))
    $sourceCompose = @('compose', '-f', (Join-Path $backupRoot 'compose.yaml'), '--env-file', $environment, '-p', $sourceProject)
    $null = Invoke-DockerChecked ($sourceCompose + @('build', 'application'))
    $null = Invoke-DockerChecked ($sourceCompose + @('up', '-d', '--wait', '--wait-timeout', '180'))
    $env:M2_TEST_ISOLATED = '1'
    $env:M2_TEST_ENV_FILE = $environment
    $env:M2_TEST_BASE_URL = "http://127.0.0.1:$port"
    $env:M2_TEST_PROJECT_ID = $project
    $env:M2_TEST_QUALITY_PASSWORD = $qualityPassword
    & python Docs/Arquitectura/verificar_swagger.py
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compatibilidad de Swagger/OpenAPI.' }
    & python Docs/Arquitectura/verificar_csv.py
    if ($LASTEXITCODE -ne 0) { throw 'Falló el recorrido API de CSV.' }
    & npm --prefix Frontend run test:browser
    if ($LASTEXITCODE -ne 0) { throw 'Falló el recorrido de navegador.' }
    $snapshot = Join-Path $work 'snapshot.json'
    & python Docs/Arquitectura/verificar_restauracion.py snapshot $snapshot
    if ($LASTEXITCODE -ne 0) { throw 'Falló la referencia previa al respaldo.' }
    $env:M2_BACKUP_PASSWORD = New-TestSecret
    $backup = Join-Path $work 'delivery.m2backup'
    & (Join-Path $PSScriptRoot 'respaldar-datos.ps1') -Destination $backup -EnvironmentFile $environment -ProjectName $sourceProject
    $restoreConfig = Join-Path $work 'restored-config'
    & (Join-Path $PSScriptRoot 'restaurar-datos.ps1') -BackupFile $backup -ConfigurationDirectory $restoreConfig -ProjectName $restoreProject -HttpPort $restorePort -ApplicationImage $image
    $restoreCompose = @('compose', '-f', (Join-Path $backupRoot 'compose.yaml'), '--env-file', (Join-Path $restoreConfig 'keys.env'), '-p', $restoreProject)
    $env:M2_TEST_ENV_FILE = Join-Path $restoreConfig 'keys.env'
    $env:M2_TEST_BASE_URL = "http://127.0.0.1:$restorePort"
    & python Docs/Arquitectura/verificar_restauracion.py compare $snapshot
    if ($LASTEXITCODE -ne 0) { throw 'Falló la comparación de trazabilidad/permisos después de restaurar.' }
    & python Docs/Arquitectura/verificar_carga.py
    if ($LASTEXITCODE -ne 0) { throw 'Falló la carga de referencia de cinco usuarios.' }
    $measurements = Invoke-DockerChecked ($restoreCompose + @('logs', '--no-color', 'application'))
    $measurements | Where-Object { $_ -match 'OPERATIONS' } | Set-Content -LiteralPath (Join-Path $backupRoot 'target/security-operations.log')
    if ($measurements -match 'OPERATIONS_MEASUREMENT_FAILED') { throw 'No se pudieron medir los recursos durante la prueba.' }
    @{ image = $image; browser = 'passed'; restore = 'passed'; concurrent_users = 5; rows = 10000 } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backupRoot 'target/security-delivery-summary.json') -Encoding UTF8
    Write-Host 'OK entrega: CSV, navegador, respaldo cifrado, restauración aislada y permisos.'
} finally {
    # Solo proyectos aleatorios creados por esta prueba. Nunca se usa el proyecto de uso diario.
    if ($restoreCompose) { $null = Invoke-DockerChecked ($restoreCompose + @('down', '--volumes')) }
    elseif ($work -and (Test-Path -LiteralPath (Join-Path $work 'restored-config/keys.env'))) {
        $null = Invoke-DockerChecked @('compose', '-f', (Join-Path $backupRoot 'compose.yaml'), '--env-file', (Join-Path $work 'restored-config/keys.env'), '-p', $restoreProject, 'down', '--volumes')
    }
    if ($sourceCompose) { $null = Invoke-DockerChecked ($sourceCompose + @('down', '--volumes')) }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    if ($work) { Remove-BackupWorkDirectory $work }
    Pop-Location
}
