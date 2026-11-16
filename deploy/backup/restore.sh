#!/bin/sh
# Restores a backup over the current database and uploaded files.
#   restore.sh <stamp|latest> --yes     (stamp as shown by list.sh; searched in daily, weekly and monthly)
# The backend must be stopped first; the host scripts (scripts/restore.sh / restore.ps1) do that for you.
set -eu
WHICH=${1:-}
[ -n "$WHICH" ] || { echo "Usage: restore.sh <stamp|latest> --yes"; exit 2; }
if [ "$WHICH" = "latest" ]; then
  WHICH=$(ls -1d /backups/daily/20*/ /backups/weekly/20*/ /backups/monthly/20*/ 2>/dev/null | xargs -n1 basename 2>/dev/null | sort -r | head -n1)
  [ -n "$WHICH" ] || { echo "No backups found."; exit 1; }
fi
DIR=""
for kind in daily weekly monthly; do
  if [ -d "/backups/$kind/$WHICH" ]; then DIR="/backups/$kind/$WHICH"; break; fi
done
[ -n "$DIR" ] || { echo "Backup $WHICH not found."; exit 1; }
[ "${2:-}" = "--yes" ] || { echo "This REPLACES all current data with backup $WHICH. Run again with --yes to continue."; exit 2; }

echo "Checking $DIR ..."
(cd "$DIR" && sha256sum -c SHA256SUMS) || { echo "Checksum mismatch: this backup is damaged."; exit 1; }
pg_restore -l "$DIR/storemis.dump" > /dev/null

echo "Restoring the database ..."
psql -v ON_ERROR_STOP=1 -q -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid();" > /dev/null
psql -v ON_ERROR_STOP=1 -q -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
pg_restore --no-owner --exit-on-error -d "$PGDATABASE" "$DIR/storemis.dump"

if [ -f "$DIR/files.tar.gz" ] && [ -d /data/files ]; then
  echo "Restoring uploaded files ..."
  OWNER=$(stat -c %u:%g /data/files)
  find /data/files -mindepth 1 -delete
  tar -xzf "$DIR/files.tar.gz" -C /data/files
  chown -R "$OWNER" /data/files
fi
echo "Restore of $WHICH finished."
