#!/bin/sh
# Store MIS backup: database (pg_dump custom format) + uploaded files, with checksums.
# Layout: /backups/daily/<stamp>/{storemis.dump,files.tar.gz,SHA256SUMS}. Fridays are also kept as weekly and
# the 1st of the month as monthly. Every backup is copied to /external too when that folder is writable.
set -eu
STAMP=$(date +%Y-%m-%d_%H%M%S)
ROOT=/backups
DIR="$ROOT/daily/$STAMP"
mkdir -p "$DIR" "$ROOT/weekly" "$ROOT/monthly"
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') [backup] $*"; }

# Deletes one backup folder, but only a dated folder directly under a known backup root.
remove_backup() {
  case "$1" in
    /backups/daily/20*|/backups/weekly/20*|/backups/monthly/20*|/external/daily/20*) rm -r -- "$1" ;;
    *) log "refusing to remove unexpected path: $1" ;;
  esac
}

fail() {
  log "FAILED: $*"
  echo "FAILED $STAMP: $*" > "$ROOT/last-backup.txt"
  remove_backup "$DIR"
  exit 1
}

log "starting $STAMP"
pg_dump -Fc --no-owner -f "$DIR/storemis.dump" || fail "pg_dump"
pg_restore -l "$DIR/storemis.dump" > /dev/null || fail "the dump cannot be read back"
if [ -d /data/files ]; then
  tar -czf "$DIR/files.tar.gz" -C /data/files . || fail "uploads archive"
fi
(cd "$DIR" && sha256sum storemis.dump $( [ -f files.tar.gz ] && echo files.tar.gz ) > SHA256SUMS) || fail "checksums"

# Weekly (Friday) and monthly (1st) copies.
if [ "$(date +%u)" = "5" ]; then cp -r "$DIR" "$ROOT/weekly/$STAMP"; fi
if [ "$(date +%d)" = "01" ]; then cp -r "$DIR" "$ROOT/monthly/$STAMP"; fi

# Keep the newest N folders of one kind.
prune() {
  ls -1d "$1"/20*/ 2>/dev/null | sed 's:/$::' | sort -r | tail -n +"$(( $2 + 1 ))" | while read -r old; do
    remove_backup "$old" && log "removed old backup $old"
  done
}
prune "$ROOT/daily" "${KEEP_DAILY:-14}"
prune "$ROOT/weekly" "${KEEP_WEEKLY:-8}"
prune "$ROOT/monthly" "${KEEP_MONTHLY:-12}"

# Second copy (USB disk / network share) when one is mounted and writable.
if [ -d /external ] && [ -w /external ]; then
  mkdir -p /external/daily
  if cp -r "$DIR" "/external/daily/$STAMP"; then
    prune /external/daily "${KEEP_DAILY:-14}"
    log "copied to the external folder"
  else
    log "WARNING: copy to the external folder failed"
  fi
fi

SIZE=$(du -sh "$DIR" | cut -f1)
echo "OK $STAMP $SIZE" > "$ROOT/last-backup.txt"
log "done: $DIR ($SIZE)"
