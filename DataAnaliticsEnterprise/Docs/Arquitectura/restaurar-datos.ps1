[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackupFile,
    [Parameter(Mandatory)][string]$ConfigurationDirectory,
    [ValidatePattern('^m2-restore-[a-z0-9-]+$')][string]$ProjectName = ('m2-restore-' + [Guid]::NewGuid().ToString('N')),
    [ValidateRange(1024,65535)][int]$HttpPort = 8088,
    [string]$ApplicationImage = 'm2-calidad-application'
)
. (Join-Path $PSScriptRoot 'respaldo-comun.ps1')
$beforePassword = $env:M2_BACKUP_PASSWORD
$work = $null
Push-Location -LiteralPath $backupRoot
try {
    $existing = Invoke-DockerChecked @('volume', 'ls', '--quiet', '--filter', "label=com.docker.compose.project=$ProjectName")
    if ($existing) { throw 'La restauración solo admite un proyecto nuevo, sin volúmenes existentes.' }
    $existingContainers = Invoke-DockerChecked @('ps', '--all', '--quiet', '--filter', "label=com.docker.compose.project=$ProjectName")
    if ($existingContainers) { throw 'El proyecto de destino ya contiene contenedores.' }
    Set-BackupPassword
    $work = New-PrivateBackupDirectory (Join-Path $backupRoot ('target/backup-work-' + [Guid]::NewGuid().ToString('N')))
    Copy-Item -LiteralPath $BackupFile -Destination (Join-Path $work 'archive.m2backup')
    # La imagen actual debe haberse construido antes; no se ejecuta una imagen indicada por el respaldo.
    $image = (Invoke-DockerChecked @('image', 'inspect', '--format', '{{.Id}}', $ApplicationImage) | Out-String).Trim()
    Invoke-BackupCipher $image $work 'decrypt' 'archive.m2backup' 'plain.zip'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $files = New-Item -ItemType Directory -Path (Join-Path $work 'files')
    $zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $work 'plain.zip'))
    try {
        $names = @('database.dump', 'inputs.tar.gz', 'keys.env', 'keys.properties', 'manifest.json')
        $seen = @{}
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notin $names -or $seen.ContainsKey($entry.FullName)) { throw 'Contenido de respaldo inválido.' }
            $seen[$entry.FullName] = $true
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $files.FullName $entry.FullName))
        }
    } finally { $zip.Dispose() }
    $manifest = Get-Content -LiteralPath (Join-Path $files.FullName 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.format -ne 1) { throw 'Versión de respaldo no admitida.' }
    foreach ($required in @('database.dump', 'inputs.tar.gz', 'keys.env')) {
        if (!$manifest.files.PSObject.Properties[$required]) { throw 'El respaldo está incompleto.' }
    }
    foreach ($entry in $manifest.files.PSObject.Properties) {
        if ($entry.Name -notin @('database.dump', 'inputs.tar.gz', 'keys.env', 'keys.properties')) { throw 'Manifiesto inválido.' }
        if ((Get-FileHash -LiteralPath (Join-Path $files.FullName $entry.Name) -Algorithm SHA256).Hash -ne $entry.Value) { throw 'Falló la verificación de integridad.' }
    }
    $config = New-PrivateBackupDirectory $ConfigurationDirectory
    $environment = Join-Path $config 'keys.env'
    $lines = Get-Content -LiteralPath (Join-Path $files.FullName 'keys.env')
    $lines = @($lines | Where-Object { $_ -notmatch '^M2_HTTP_PORT=' }) + "M2_HTTP_PORT=$HttpPort"
    [IO.File]::WriteAllLines($environment, $lines, (New-Object Text.UTF8Encoding $false))
    $compose = @('compose', '-f', (Join-Path $backupRoot 'compose.yaml'), '--env-file', $environment, '-p', $ProjectName)
    if (Test-Path -LiteralPath (Join-Path $files.FullName 'keys.properties')) {
        Copy-Item -LiteralPath (Join-Path $files.FullName 'keys.properties') -Destination (Join-Path $config 'keys.properties')
        $override = Join-Path $config 'compose.keys.yaml'
        @{ services = @{ application = @{ environment = @{ SPRING_CONFIG_ADDITIONAL_LOCATION = 'file:/run/m2/keys.properties' }; volumes = @("$($config.Replace('\','/'))/keys.properties:/run/m2/keys.properties:ro") } } } |
            ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $override -Encoding UTF8
        $compose = @('compose', '-f', (Join-Path $backupRoot 'compose.yaml'), '-f', $override, '--env-file', $environment, '-p', $ProjectName)
    }
    $null = Invoke-DockerChecked ($compose + @('up', '-d', '--wait', 'database'))
    $null = Invoke-DockerChecked ($compose + @('cp', (Join-Path $files.FullName 'database.dump'), 'database:/tmp/restore.dump'))
    $null = Invoke-DockerChecked ($compose + @('exec', '-T', 'database', 'pg_restore', '-U', 'postgres', '-d', 'm2_local', '--clean', '--if-exists', '--no-owner', '--exit-on-error', '/tmp/restore.dump'))
    $null = Invoke-DockerChecked ($compose + @('exec', '-T', 'database', 'rm', '-f', '/tmp/restore.dump'))
    # Un respaldo no debe resucitar sesiones o códigos de recuperación anteriores.
    $sql = @'
BEGIN;
DELETE FROM m2.spring_session;
UPDATE m2.users SET auth_version=auth_version+1;
UPDATE m2.password_reset_tokens SET used_at=coalesce(used_at,now());
INSERT INTO m2.audit_events(project_id,request_id,action,resource_type,resource_id,outcome,reason_code)
SELECT id,gen_random_uuid(),'DATABASE_RESTORED','project',id,'SUCCESS','SESSION_REVOCATION_AFTER_RESTORE' FROM m2.projects;
COMMIT;
'@
    $null = Invoke-DockerChecked ($compose + @('exec', '-T', 'database', 'psql', '-U', 'postgres', '-d', 'm2_local', '-v', 'ON_ERROR_STOP=1', '-c', $sql))
    $null = Invoke-DockerChecked ($compose + @('create', '--no-build', 'application'))
    $app = (Invoke-DockerChecked ($compose + @('ps', '--all', '--quiet', 'application')) | Out-String).Trim()
    $volume = Get-InputVolume $app
    if (!$volume) { throw 'No se encontró el volumen aislado de destino.' }
    $null = Invoke-DockerChecked @('run', '--rm', '--network', 'none', '--user', '0:0', '--mount', "type=volume,source=$volume,target=/restore",
        '--mount', "type=bind,source=$($files.FullName),target=/backup,readonly", '--entrypoint', 'tar', $image, '-xzf', '/backup/inputs.tar.gz', '-C', '/restore')
    $null = Invoke-DockerChecked ($compose + @('up', '-d', '--no-build', '--wait'))
    Write-Host "Restauración aislada disponible en http://localhost:$HttpPort. Proyecto: $ProjectName"
    Write-Host "Conserva la configuración privada de restauración en: $config"
} finally {
    $env:M2_BACKUP_PASSWORD = $beforePassword
    try { if ($work) { Remove-BackupWorkDirectory $work } } finally { Pop-Location }
}
