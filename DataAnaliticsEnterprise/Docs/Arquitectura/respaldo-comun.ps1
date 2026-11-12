$ErrorActionPreference = 'Stop'
$backupRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))

function Invoke-DockerChecked {
    param([string[]]$Arguments)
    $result = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Falló una operación de Docker del respaldo/restauración.' }
    return $result
}

function Get-InputVolume {
    param([string]$Container)
    $mounts = Invoke-DockerChecked @('inspect','--format','{{json .Mounts}}',$Container) | ConvertFrom-Json
    return @($mounts | Where-Object { $_.Destination -eq '/app/data' })[0].Name
}

function New-PrivateBackupDirectory {
    param([string]$Path)
    $full = [IO.Path]::GetFullPath($Path)
    if (Test-Path -LiteralPath $full) { throw 'El directorio debe ser nuevo para evitar sobrescribir datos.' }
    $null = New-Item -ItemType Directory -Path $full
    if ($env:OS -eq 'Windows_NT') {
        $acl = Get-Acl -LiteralPath $full
        $acl.SetAccessRuleProtection($true, $false)
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
        Set-Acl -LiteralPath $full -AclObject $acl
    } else {
        & chmod 700 $full
        if ($LASTEXITCODE -ne 0) { throw 'No se pudieron restringir los permisos del directorio.' }
    }
    return $full
}

function Set-BackupPassword {
    if ($env:M2_BACKUP_PASSWORD -and $env:M2_BACKUP_PASSWORD.Length -ge 20) { return }
    $secure = Read-Host 'Contraseña del respaldo (mínimo 20 caracteres; guárdala fuera del respaldo)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:M2_BACKUP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
    if ($env:M2_BACKUP_PASSWORD.Length -lt 20) { throw 'La contraseña debe tener al menos 20 caracteres.' }
}

function Invoke-BackupCipher {
    param([string]$Image, [string]$Directory, [string]$Mode, [string]$Source, [string]$Target)
    $null = Invoke-DockerChecked @('run', '--rm', '--network', 'none', '--user', '0:0', '--env', 'M2_BACKUP_PASSWORD',
        '--mount', "type=bind,source=$Directory,target=/backup", '--entrypoint', 'java', $Image,
        '-cp', '/app/health', 'org.dataanaliticsent.Calidad.Docker.BackupCipher', $Mode, "/backup/$Source", "/backup/$Target")
}

function Remove-BackupWorkDirectory {
    param([string]$Path)
    $full = [IO.Path]::GetFullPath($Path)
    $allowed = [IO.Path]::GetFullPath((Join-Path $backupRoot 'target')) + [IO.Path]::DirectorySeparatorChar
    if (!$full.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($full) -notlike 'backup-work-*') {
        throw 'Se rechazó la limpieza fuera del directorio temporal de respaldo.'
    }
    if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Recurse -Force }
}
