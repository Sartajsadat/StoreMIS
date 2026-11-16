#!/usr/bin/env bash
# First-time setup on a Linux store server (Ubuntu 24.04 + Docker). Creates deploy/.env with random secrets
# (unless it exists), builds the images and starts Store MIS.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null || { echo "Docker is not installed. See docs/phase-9/deployment-guide.md, section 2."; exit 1; }
docker compose version >/dev/null || { echo "Docker Compose v2 is required."; exit 1; }

# `tr` is stopped by SIGPIPE once `head` has enough characters; that is expected, not an error.
rand() { local s; s=$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom 2>/dev/null | head -c "$1" || true); printf '%s' "$s"; }

if [ ! -f .env ]; then
  ADMIN_PASSWORD="Admin$(rand 8)7"
  sed -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(rand 32)|" \
      -e "s|^JWT_SECRET=.*|JWT_SECRET=$(rand 64)|" \
      -e "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=${ADMIN_PASSWORD}|" .env.example > .env
  chmod 600 .env
  echo "Created deploy/.env with random secrets."
  echo "First sign-in:  user 'admin'  password '${ADMIN_PASSWORD}'  (you will be asked to change it)"
else
  echo "deploy/.env already exists; keeping it."
fi

mkdir -p backups backups-external
docker compose up -d --build
echo
echo "Waiting for Store MIS to start ..."
for _ in $(seq 1 60); do
  if [ "$(docker inspect -f '{{.State.Health.Status}}' storemis-web-1 2>/dev/null)" = "healthy" ]; then break; fi
  sleep 5
done
docker compose ps
PORT=$(grep -E '^HTTP_PORT=' .env | cut -d= -f2); PORT=${PORT:-80}
IP=$(hostname -I 2>/dev/null | awk '{print $1}')
echo
echo "Store MIS is running. Open http://${IP:-<server-ip>}$( [ "$PORT" = "80" ] || echo ":$PORT" )/ on the shop PCs."
