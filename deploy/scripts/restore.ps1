# Restores a backup:  powershell -ExecutionPolicy Bypass -File deploy\scripts\restore.ps1 <stamp|latest>
# Takes a safety backup first, stops the backend, restores the database and files, then starts the backend.
param([string]$Which)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not $Which) {
    docker compose exec -T backup list.sh
    Write-Host ''; Write-Host 'Usage: restore.ps1 <stamp|latest>'; exit 2
}
$answer = Read-Host "This replaces ALL current data with backup '$Which'. Type YES to continue"
if ($answer -ne 'YES') { Write-Host 'Cancelled.'; exit 1 }

Write-Host 'Safety backup of the current data first ...'
docker compose exec -T backup backup.sh
docker compose stop backend
docker compose exec -T backup restore.sh $Which --yes
$ok = $LASTEXITCODE -eq 0
docker compose start backend
if ($ok) { Write-Host 'Done. Sign in again on every PC.' }
else { Write-Host 'Restore FAILED. The backend was started again on the data that was there before.'; exit 1 }
