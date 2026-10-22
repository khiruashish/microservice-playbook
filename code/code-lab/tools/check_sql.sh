#!/usr/bin/env bash
# Applies every lab's Flyway migrations, in version order, to a throwaway PostgreSQL 16 cluster, each lab in its own
# database and schema, as Flyway would, after any roles the lab needs (src/test/resources/db/roles.sql). Fails on the
# first SQL error. Needs PostgreSQL 16 server binaries (PG_BIN).
set -euo pipefail
cd "$(dirname "$0")/.."
PG_BIN=${PG_BIN:-$(pg_config --bindir)}
D=$(mktemp -d); PORT=55433
as_pg() { if [ "$(id -u)" = 0 ]; then su postgres -s /bin/bash -c "$*"; else bash -c "$*"; fi; }
chmod 777 "$D"
as_pg "$PG_BIN/initdb -D $D/data -U postgres -A trust >/dev/null && $PG_BIN/pg_ctl -D $D/data -o '-p $PORT -k $D' -l $D/log start >/dev/null"
trap 'as_pg "$PG_BIN/pg_ctl -D $D/data stop -m fast >/dev/null"; rm -rf "$D"' EXIT
P="$PG_BIN/psql -h $D -p $PORT -X -q -v ON_ERROR_STOP=1 -U postgres"
n=0
for lab in lab-*/; do
  dir=${lab}src/main/resources/db/migration
  [ -d "$dir" ] || continue
  db=$(basename "$lab" | tr - _)
  schema=$(sed -n 's/^ *schemas: *//p' ${lab}src/main/resources/application.yml | head -1)
  schema=${schema:-public}
  as_pg "$P -d postgres -c 'CREATE DATABASE $db'"
  as_pg "$P -d $db -c 'CREATE SCHEMA IF NOT EXISTS $schema'"
  # cluster-wide roles a lab's migration grants to (lab 18), created as the platform would
  [ -f ${lab}src/test/resources/db/roles.sql ] && as_pg "$P -d $db -f $(pwd)/${lab}src/test/resources/db/roles.sql" >/dev/null
  for f in $(ls "$dir"/V*__*.sql | sort -t V -k2 -n); do
    as_pg "PGOPTIONS='-c search_path=$schema' $P -d $db -f $(pwd)/$f" >/dev/null
    n=$((n+1))
  done
  echo "$(basename $lab): $(ls $dir/V*__*.sql | wc -l) migrations applied in schema $schema"
done
echo "$n migrations applied without error"
