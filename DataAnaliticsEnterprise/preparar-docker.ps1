[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot 'target/docker.env'
if (Test-Path -LiteralPath $configPath) {
    Write-Host 'La configuración local ya existe. Se conservarán las claves y contraseñas.'
    Write-Host 'Consulta target/docker.env para ver las credenciales de acceso.'
    exit 0
}
$null = New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'target') -Force
function New-RandomBase64 {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
function New-RandomPassword { return (New-RandomBase64).Replace('+','x').Replace('/','y').TrimEnd('=') }
$adminPassword = New-RandomPassword
$qualityPassword = New-RandomPassword
$lines = @(
    '# Configuración privada de desarrollo local. target/ está excluido del repositorio.'
    'M2_HTTP_PORT=8080'
    ('M2_POSTGRES_PASSWORD=' + (New-RandomPassword))
    ('M2_DB_PASSWORD=' + (New-RandomPassword))
    ('M2_DATA_KEY_BASE64=' + (New-RandomBase64))
    ('M2_EMAIL_LOOKUP_KEY_BASE64=' + (New-RandomBase64))
    ('M2_ADMIN_PASSWORD=' + $adminPassword)
    ('M2_QUALITY_PASSWORD=' + $qualityPassword)
    '# Recuperación por SMTP de Gmail. Completa correo y contraseña de aplicación antes de habilitarla.'
    'M2_RECOVERY_ENABLED=false'
    'M2_RECOVERY_SMTP_HOST=smtp.gmail.com'
    'M2_RECOVERY_SMTP_PORT=587'
    'M2_RECOVERY_SMTP_SECURITY=STARTTLS'
    'M2_RECOVERY_SMTP_AUTH=true'
    'M2_RECOVERY_FROM='
    'M2_RECOVERY_SMTP_USERNAME='
    'M2_RECOVERY_SMTP_PASSWORD='
)
[System.IO.File]::WriteAllLines($configPath, $lines, (New-Object System.Text.UTF8Encoding $false))
Write-Host 'Configuración creada en target/docker.env. Conserva este archivo junto al volumen de datos.'
Write-Host ('Administrador: admin@example.test | contraseña: ' + $adminPassword)
Write-Host ('Calidad: calidad@example.test | contraseña: ' + $qualityPassword)
exit 0
