#!/bin/sh
# Lists the available backups (newest first) and the result of the last run.
for kind in daily weekly monthly; do
  echo "== $kind =="
  ls -1d /backups/$kind/20*/ 2>/dev/null | sort -r | while read -r d; do echo "  $(basename "$d")  $(du -sh "$d" | cut -f1)"; done
done
echo "== last run =="
cat /backups/last-backup.txt 2>/dev/null || echo "  no backup yet"
