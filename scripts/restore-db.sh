#!/usr/bin/env bash

set -euo pipefail

if [ "${1:-}" = "" ]; then
  echo "Usage: scripts/restore-db.sh <backup-file.sql.gz|backup-file.sql> [target-db-name]"
  exit 1
fi

BACKUP_FILE="$1"
TARGET_DB_NAME="${2:-${DB_NAME:-hisaablite_restore}}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-hisaab}"
DB_PASSWORD="${DB_PASSWORD:-hisaab123}"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Backup file not found: $BACKUP_FILE"
  exit 1
fi

echo "Restoring '$BACKUP_FILE' into database '$TARGET_DB_NAME'..."

MYSQL_PWD="$DB_PASSWORD" mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  -e "CREATE DATABASE IF NOT EXISTS \`$TARGET_DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

if [[ "$BACKUP_FILE" == *.gz ]]; then
  gunzip -c "$BACKUP_FILE" | MYSQL_PWD="$DB_PASSWORD" mysql \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    "$TARGET_DB_NAME"
else
  MYSQL_PWD="$DB_PASSWORD" mysql \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    "$TARGET_DB_NAME" < "$BACKUP_FILE"
fi

echo "Restore completed successfully into '$TARGET_DB_NAME'."
