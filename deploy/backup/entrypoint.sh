#!/bin/sh
# Runs backup.sh on the BACKUP_CRON schedule (busybox crond; times are in the container time zone, TZ).
set -eu
mkdir -p /backups
echo "${BACKUP_CRON:-30 21 * * *} /usr/local/bin/backup.sh >> /proc/1/fd/1 2>&1" > /etc/crontabs/root
echo "Store MIS backup service: schedule '${BACKUP_CRON:-30 21 * * *}' (${TZ:-UTC}), keeping ${KEEP_DAILY:-14} daily, ${KEEP_WEEKLY:-8} weekly, ${KEEP_MONTHLY:-12} monthly copies."
exec crond -f -l 8
