#!/bin/sh
set -eu
psql --host=database --username=postgres --dbname=m2_local --set=ON_ERROR_STOP=1 --file=/m2/migrate-role-revocation.sql
psql --host=database --username=postgres --dbname=m2_local --set=ON_ERROR_STOP=1 --file=/m2/migrate-operational-limits.sql
