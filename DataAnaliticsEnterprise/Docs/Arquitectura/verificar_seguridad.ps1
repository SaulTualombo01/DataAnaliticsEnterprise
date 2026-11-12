[CmdletBinding()]
param([Parameter(Mandatory)][string]$ApplicationImage, [string]$SourceDirectory)
. (Join-Path $PSScriptRoot 'respaldo-comun.ps1')
if (!$SourceDirectory) { $SourceDirectory = $backupRoot }
$source = (Resolve-Path -LiteralPath $SourceDirectory).Path
$reports = Join-Path $backupRoot 'target'
$scanner = 'aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb'
$mounts = @('run','--rm','--mount',"type=bind,source=$source,target=/work,readonly",'--mount',"type=bind,source=$reports,target=/reports",
    '--mount','type=volume,source=m2_trivy_cache,target=/root/.cache')
$skip = @('--skip-dirs','/work/target','--skip-dirs','/work/Frontend/node_modules','--skip-dirs','/work/.git','--skip-dirs','/work/backups',
    '--skip-dirs','**/target','--skip-dirs','**/node_modules','--skip-dirs','**/.git','--skip-dirs','**/backups')
$archive = Join-Path $reports ('security-image-' + [Guid]::NewGuid().ToString('N') + '.tar')
$results = @()
try {
    foreach ($name in @('dependencies','secrets','image')) {
        $previous = Join-Path $reports "security-$name.json"
        if (Test-Path -LiteralPath $previous) { Remove-Item -LiteralPath $previous -Force }
    }
    $null = Invoke-DockerChecked @('image','save','--output',$archive,$ApplicationImage)
    # El JAR real se comprueba dentro de la imagen; evita resolver de nuevo el árbol Maven por red.
    & docker @mounts $scanner fs --quiet --no-progress --scanners vuln --include-dev-deps --severity HIGH,CRITICAL --exit-code 1 @skip --skip-files '/work/pom.xml' --skip-files '**/pom.xml' --format json --output /reports/security-dependencies.json /work
    $results += $LASTEXITCODE
    & docker @mounts $scanner fs --quiet --no-progress --offline-scan --scanners secret --exit-code 1 @skip --format json --output /reports/security-secrets.json /work
    $results += $LASTEXITCODE
    & docker @mounts $scanner image --quiet --no-progress --input "/reports/$([IO.Path]::GetFileName($archive))" --scanners vuln,secret --severity HIGH,CRITICAL --exit-code 1 --format json --output /reports/security-image.json
    $results += $LASTEXITCODE
    foreach ($name in @('dependencies','secrets','image')) {
        $path = Join-Path $reports "security-$name.json"
        if (!(Test-Path -LiteralPath $path)) { Write-Host "Análisis incompleto: $name"; continue }
        $report = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        $vulnerabilities = @($report.Results | ForEach-Object { $_.Vulnerabilities } | Where-Object { $_ })
        $secrets = @($report.Results | ForEach-Object { $_.Secrets } | Where-Object { $_ })
        Write-Host "$name - vulnerabilidades: $($vulnerabilities.Count); secretos: $($secrets.Count)"
        # Nunca se imprime la coincidencia de un secreto ni su valor.
        $vulnerabilities | Select-Object PkgName,InstalledVersion,VulnerabilityID,FixedVersion,Severity | Format-Table -AutoSize
    }
    if (@($results | Where-Object { $_ -ne 0 }).Count) { throw 'Control de seguridad rechazado o incompleto. Revisa los informes privados de target/.' }
    Write-Host 'OK control de dependencias, secretos e imagen.'
} finally {
    if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
}
