# Takes a backup right away and lists all backups.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
docker compose exec -T backup backup.sh
docker compose exec -T backup list.sh
