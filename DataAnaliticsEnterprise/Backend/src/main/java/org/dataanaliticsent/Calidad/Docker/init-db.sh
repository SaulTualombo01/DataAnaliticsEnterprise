#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 --file=/m2/schema-local.sql
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 --set app_password="$M2_DB_PASSWORD" <<'SQL'
CREATE ROLE m2_app LOGIN PASSWORD :'app_password' NOBYPASSRLS;
GRANT m2_backend TO m2_app;
SQL
