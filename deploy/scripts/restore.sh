#!/usr/bin/env bash
# Restores a backup:  scripts/restore.sh <stamp|latest>
# Takes a safety backup first, stops the backend, restores the database and files, then starts the backend.
set -euo pipefail
cd "$(dirname "$0")/.."
WHICH=${1:-}
if [ -z "$WHICH" ]; then
  docker compose exec -T backup list.sh
  echo; echo "Usage: scripts/restore.sh <stamp|latest>"; exit 2
fi
read -r -p "This replaces ALL current data with backup '$WHICH'. Type YES to continue: " answer
[ "$answer" = "YES" ] || { echo "Cancelled."; exit 1; }

echo "Safety backup of the current data first ..."
docker compose exec -T backup backup.sh
docker compose stop backend
if docker compose exec -T backup restore.sh "$WHICH" --yes; then
  docker compose start backend
  echo "Done. Sign in again on every PC."
else
  docker compose start backend
  echo "Restore FAILED. The backend was started again on the data that was there before."
  exit 1
fi
