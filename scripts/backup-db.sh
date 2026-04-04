#!/usr/bin/env bash

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-hisaablite}"
DB_USER="${DB_USER:-hisaab}"
DB_PASSWORD="${DB_PASSWORD:-hisaab123}"
TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
OUTPUT_FILE="${1:-$BACKUP_DIR/hisaablite_${TIMESTAMP}.sql.gz}"

mkdir -p "$(dirname "$OUTPUT_FILE")"

echo "Creating backup for database '$DB_NAME' at '$OUTPUT_FILE'..."

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  "$DB_NAME" | gzip > "$OUTPUT_FILE"

echo "Backup created successfully."
echo "Backup file: $OUTPUT_FILE"
