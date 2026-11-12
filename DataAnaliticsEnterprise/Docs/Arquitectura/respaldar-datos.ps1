[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Destination,
    [string]$EnvironmentFile = 'target/docker.env',
    [ValidatePattern('^[a-z0-9][a-z0-9_-]+$')][string]$ProjectName = 'm2-calidad',
    [string]$KeyConfigFile
)
. (Join-Path $PSScriptRoot 'respaldo-comun.ps1')
$beforePassword = $env:M2_BACKUP_PASSWORD
$work = $null
$stopped = $false
Push-Location -LiteralPath $backupRoot
try {
    $destinationPath = [IO.Path]::GetFullPath($Destination)
    if (Test-Path -LiteralPath $destinationPath) { throw 'El respaldo de destino ya existe.' }
    if (!(Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($destinationPath)))) { throw 'Crea primero el directorio de destino en almacenamiento protegido.' }
    $environment = (Resolve-Path -LiteralPath $EnvironmentFile).Path
    $compose = @('compose', '-f', (Join-Path $backupRoot 'compose.yaml'), '--env-file', $environment, '-p', $ProjectName)
    $app = (Invoke-DockerChecked ($compose + @('ps', '--quiet', 'application')) | Out-String).Trim()
    if (!$app) { throw 'La aplicación debe estar levantada con su configuración original.' }
    $image = (Invoke-DockerChecked @('inspect', '--format', '{{.Image}}', $app) | Out-String).Trim()
    $volume = Get-InputVolume $app
    if (!$volume) { throw 'No se encontró el volumen de CSV.' }
    Set-BackupPassword
    $work = New-PrivateBackupDirectory (Join-Path $backupRoot ('target/backup-work-' + [Guid]::NewGuid().ToString('N')))
    $files = New-Item -ItemType Directory -Path (Join-Path $work 'files')
    Copy-Item -LiteralPath $environment -Destination (Join-Path $files.FullName 'keys.env')
    if ($KeyConfigFile) { Copy-Item -LiteralPath $KeyConfigFile -Destination (Join-Path $files.FullName 'keys.properties') }
    Write-Host 'Deteniendo brevemente la aplicación para conservar un respaldo coherente de base y archivos.'
    $null = Invoke-DockerChecked ($compose + @('stop', '--timeout', '90', 'application'))
    $stopped = $true
    $dump = '/tmp/m2-backup-' + [Guid]::NewGuid().ToString('N') + '.dump'
    try {
        $null = Invoke-DockerChecked ($compose + @('exec', '-T', 'database', 'pg_dump', '-U', 'postgres', '-d', 'm2_local', '-Fc', '-f', $dump))
        $null = Invoke-DockerChecked ($compose + @('cp', "database:$dump", (Join-Path $files.FullName 'database.dump')))
    } finally {
        # Archivo concreto generado aquí, dentro del contenedor de base indicado.
        $null = Invoke-DockerChecked ($compose + @('exec', '-T', 'database', 'rm', '-f', $dump))
    }
    $null = Invoke-DockerChecked @('run', '--rm', '--network', 'none', '--user', '0:0', '--mount', "type=volume,source=$volume,target=/source,readonly",
        '--mount', "type=bind,source=$($files.FullName),target=/backup", '--entrypoint', 'tar', $image, '-czf', '/backup/inputs.tar.gz', '-C', '/source', '.')
    $null = Invoke-DockerChecked ($compose + @('start', 'application'))
    $stopped = $false
    $hashes = @{}
    Get-ChildItem -LiteralPath $files.FullName -File | ForEach-Object { $hashes[$_.Name] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
    @{ format = 1; created_at = [DateTime]::UtcNow.ToString('o'); image = $image; files = $hashes } |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $files.FullName 'manifest.json') -Encoding UTF8
    Compress-Archive -Path (Join-Path $files.FullName '*') -DestinationPath (Join-Path $work 'plain.zip')
    Invoke-BackupCipher $image $work 'encrypt' 'plain.zip' 'archive.m2backup'
    $inputStream = [IO.File]::OpenRead((Join-Path $work 'archive.m2backup'))
    try {
        $outputStream = [IO.File]::Open($destinationPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $inputStream.CopyTo($outputStream); $outputStream.Flush() } finally { $outputStream.Dispose() }
    } finally { $inputStream.Dispose() }
    Write-Host 'Respaldo cifrado creado. Conserva la contraseña por separado y ensaya su restauración.'
} finally {
    try {
        if ($stopped) { $null = Invoke-DockerChecked ($compose + @('start', 'application')) }
    } finally {
        $env:M2_BACKUP_PASSWORD = $beforePassword
        try { if ($work) { Remove-BackupWorkDirectory $work } } finally { Pop-Location }
    }
}
