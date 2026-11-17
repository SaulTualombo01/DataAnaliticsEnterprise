# Preparar credenciales locales y conexión del backend para la base Docker TIC.
# No arranca contenedores ni cambia contraseñas de volúmenes/bases existentes.
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$secretDir = Join-Path $repoRoot '.docker\secrets'
$envPath = Join-Path $repoRoot 'DataAnaliticsEnterprise\.env'
$templatePath = Join-Path $repoRoot 'DataAnaliticsEnterprise\.env.example'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$secretPaths = @(
    (Join-Path $secretDir 'postgres_admin_password'),
    (Join-Path $secretDir 'app_db_password')
)
$existingCount = @($secretPaths | Where-Object { Test-Path -LiteralPath $_ }).Count
if ($existingCount -eq 1) {
    throw 'Falta uno de los dos secretos locales. Recupera el archivo faltante antes de continuar; no se regeneran claves parcialmente.'
}
$envContent = if (Test-Path -LiteralPath $envPath) { [IO.File]::ReadAllText($envPath, $utf8) } else { [IO.File]::ReadAllText($templatePath, $utf8) }
foreach ($key in @('DB_URL','DB_USER','DB_PASSWORD')) {
    $keyPattern = '(?m)^[ \t]*' + [regex]::Escape($key) + '[ \t]*[:=]'
    if ([regex]::Matches($envContent, $keyPattern).Count -gt 1) { throw "Variable duplicada en .env: $key" }
}
[IO.Directory]::CreateDirectory($secretDir) | Out-Null
if ($existingCount -eq 0) {
    $knownVolumes = & docker volume ls --quiet --filter 'name=^tic-data_postgres_data$' 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Inicia Docker Desktop antes de preparar las credenciales.' }
    if ($knownVolumes) { throw 'Existe un volumen TIC pero faltan ambos secretos. Recupera sus archivos originales; no se generan contraseñas distintas para una base existente.' }
    foreach ($path in $secretPaths) {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $password = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
        [Array]::Clear($bytes, 0, $bytes.Length)
        [IO.File]::WriteAllText($path, $password, $utf8)
    }
}
foreach ($path in $secretPaths) {
    $private = [IO.File]::ReadAllText($path, $utf8)
    if ($private -notmatch '^[A-Za-z0-9_-]{43}$') { throw 'Formato de secreto local inesperado. Recupera el original; no se sobrescribe.' }
}
$appPassword = [IO.File]::ReadAllText($secretPaths[1], $utf8)
$settings = [ordered]@{
    DB_URL = 'jdbc:postgresql://localhost:5434/data_analytics'
    DB_USER = 'data_analytics_app'
    DB_PASSWORD = $appPassword
}
foreach ($key in $settings.Keys) {
    $pattern = '(?m)^[ \t]*' + [regex]::Escape($key) + '[ \t]*[:=][^\r\n]*'
    $line = $key + '=' + $settings[$key]
    if ([regex]::IsMatch($envContent, $pattern)) { $envContent = [regex]::Replace($envContent, $pattern, $line) }
    else { $envContent += [Environment]::NewLine + $line + [Environment]::NewLine }
}
[IO.File]::WriteAllText($envPath, $envContent, $utf8)
$appPassword = $null
$password = $null
$private = $null
Write-Output 'Credenciales locales preparadas. Backend local: localhost:5434/data_analytics.'
Write-Output 'Los secretos existentes se conservaron. Ninguna contraseña se imprime ni se añade a Git.'
