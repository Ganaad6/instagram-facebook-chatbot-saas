#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# setup-db.sh  –  Create the local PostgreSQL database for chatbot-saas
#
# Usage:
#   ./setup-db.sh                   # uses defaults from .env.example
#   DB_PASSWORD=secret ./setup-db.sh
#
# Prerequisites: PostgreSQL client tools (psql) must be installed and the
#                server must be running on localhost:5432.
# ---------------------------------------------------------------------------
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-chatbot_saas}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-password}"

export PGPASSWORD="$DB_PASSWORD"

echo "==> Checking PostgreSQL connection (${DB_HOST}:${DB_PORT}) ..."
if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -c '\q' postgres 2>/dev/null; then
  echo ""
  echo "ERROR: Cannot connect to PostgreSQL at ${DB_HOST}:${DB_PORT} as user '${DB_USER}'."
  echo ""
  echo "  Option A – start PostgreSQL via Docker (no local install required):"
  echo "    docker-compose up -d"
  echo ""
  echo "  Option B – install PostgreSQL locally:"
  echo "    macOS:  brew install postgresql && brew services start postgresql"
  echo "    Ubuntu: sudo apt install postgresql && sudo service postgresql start"
  echo ""
  exit 1
fi

echo "==> Creating database '${DB_NAME}' (skipped if it already exists) ..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" postgres \
  -c "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" \
  | grep -q 1 \
  || psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" postgres \
       -c "CREATE DATABASE ${DB_NAME};"

echo "==> Database '${DB_NAME}' is ready."
echo ""
echo "  Flyway will apply all migrations automatically when you start the app."
echo "  Run the app:"
echo "    cp .env.example .env   # then fill in your Meta credentials"
echo "    mvn spring-boot:run"
echo ""
echo "Done!"
