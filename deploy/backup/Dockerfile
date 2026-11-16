# Nightly PostgreSQL + uploads backups with daily/weekly/monthly retention, and the restore tool.
FROM postgres:16-alpine
RUN apk add --no-cache tzdata
COPY backup.sh restore.sh list.sh entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/list.sh /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
