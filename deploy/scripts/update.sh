#!/usr/bin/env bash
# Updates Store MIS to the latest version: backup, pull the code, rebuild, restart.
# Database changes are applied automatically when the backend starts (Flyway).
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Backup before updating ..."
docker compose exec -T backup backup.sh
git -C .. pull --ff-only
docker compose up -d --build
docker image prune -f >/dev/null
docker compose ps
