[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& (Join-Path $PSScriptRoot 'preparar-docker.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& docker compose --env-file target/docker.env up --build --detach --wait --wait-timeout 240
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Los servicios están saludables. La aplicación está lista.'
Write-Host 'Abre http://localhost:8080 e inicia sesión con las credenciales de target/docker.env.'
Write-Host 'Para ver el arranque: docker compose --env-file target/docker.env logs -f application'
