#!/usr/bin/env bash
# Dumps the database to BACKUP_DIR, checks the dump is readable, deletes local dumps older than
# KEEP_DAYS and, if RCLONE_REMOTE is set (e.g. storagebox:chatshop), copies the new dump off the
# server and prunes remote dumps older than REMOTE_KEEP_DAYS. Run nightly from cron - see
# docs/DEPLOY_HETZNER.md.  Usage: scripts/backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BACKUP_DIR=${BACKUP_DIR:-/var/backups/chatshop}
KEEP_DAYS=${KEEP_DAYS:-14}
RCLONE_REMOTE=${RCLONE_REMOTE:-}
REMOTE_KEEP_DAYS=${REMOTE_KEEP_DAYS:-60}

# Dumps hold customer chats and orders: readable by this user only
umask 077
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/chatbot_saas-$(date +%F-%H%M).dump"
trap 'rm -f "$FILE.partial"' EXIT

docker compose exec -T postgres pg_dump -U postgres -Fc chatbot_saas > "$FILE.partial"
# Reads the dump's table of contents - fails on a truncated or empty file
docker compose exec -T postgres pg_restore --list < "$FILE.partial" > /dev/null
mv "$FILE.partial" "$FILE"

find "$BACKUP_DIR" -name 'chatbot_saas-*.dump' -mtime +"$KEEP_DAYS" -delete

if [ -n "$RCLONE_REMOTE" ]; then
  rclone copy "$FILE" "$RCLONE_REMOTE"
  rclone delete --min-age "${REMOTE_KEEP_DAYS}d" --include 'chatbot_saas-*.dump' "$RCLONE_REMOTE"
fi

echo "Backup OK: $FILE ($(du -h "$FILE" | cut -f1))"
