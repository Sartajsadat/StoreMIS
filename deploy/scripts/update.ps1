# Updates Store MIS to the latest version: backup, pull the code, rebuild, restart.
# Database changes are applied automatically when the backend starts (Flyway).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
Write-Host 'Backup before updating ...'
docker compose exec -T backup backup.sh
if ($LASTEXITCODE -ne 0) { Write-Host 'Backup failed; update stopped.'; exit 1 }
git -C .. pull --ff-only
docker compose up -d --build
docker image prune -f | Out-Null
docker compose ps
