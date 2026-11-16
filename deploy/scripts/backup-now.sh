#!/usr/bin/env bash
# Takes a backup right away and lists all backups.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose exec -T backup backup.sh
docker compose exec -T backup list.sh
