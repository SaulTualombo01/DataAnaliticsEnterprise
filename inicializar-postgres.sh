#!/bin/sh
set -eu

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
\getenv app_password DB_PASSWORD
CREATE ROLE tic_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'app_password';
ALTER DATABASE tic_tc_dev OWNER TO tic_app;
SQL
