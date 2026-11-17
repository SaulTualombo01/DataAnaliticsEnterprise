#!/bin/sh
set -eu
# Solo se ejecuta al inicializar un volumen vacío. Nunca imprimir la contraseña.
APP_DB_PASSWORD="$(cat /run/secrets/app_db_password)"
test -n "$APP_DB_PASSWORD"
export APP_DB_PASSWORD
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
\set ECHO none
\getenv app_password APP_DB_PASSWORD
SELECT format('CREATE ROLE data_analytics_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'app_password') \gexec
CREATE DATABASE data_analytics OWNER data_analytics_app;
\unset app_password
SQL
unset APP_DB_PASSWORD
