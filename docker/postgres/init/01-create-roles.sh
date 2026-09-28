#!/bin/sh
# Runs once, when the Postgres data volume is first initialized (docker-entrypoint-initdb.d).
# Creates two least-privilege roles instead of letting everything connect as the superuser:
#   - APP_DB_USER: owns the database schema; Flyway migrations run as this role
#   - directus:    only what the Directus catalog UI needs; its table grants are applied by
#                  the app's db/postgres/R__directus_grants.sql migration once the tables exist
set -eu

: "${APP_DB_USER:?APP_DB_USER must be set}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD must be set}"
: "${DIRECTUS_DB_PASSWORD:?DIRECTUS_DB_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_user="$APP_DB_USER" -v app_password="$APP_DB_PASSWORD" \
     -v directus_password="$DIRECTUS_DB_PASSWORD" -v db_name="$POSTGRES_DB" <<'EOSQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
ALTER DATABASE :"db_name" OWNER TO :"app_user";
ALTER SCHEMA public OWNER TO :"app_user";

CREATE ROLE directus LOGIN PASSWORD :'directus_password';
GRANT CONNECT ON DATABASE :"db_name" TO directus;
-- CREATE lets Directus install its own directus_* system tables; it can't touch app tables
-- it doesn't own beyond the explicit grants
GRANT USAGE, CREATE ON SCHEMA public TO directus;
EOSQL
