#!/usr/bin/env bash
# Runs init/01-schemas.sql on a throwaway PostgreSQL 16 cluster and checks ADR-012's isolation: every service can
# create and use tables in its own schema, and can neither read nor write any other service's schema.
# Needs PostgreSQL 16 server binaries (PG_BIN, default: pg_config --bindir). As root, it runs as the postgres user.
set -euo pipefail
cd "$(dirname "$0")"
PG_BIN=${PG_BIN:-$(pg_config --bindir)}
D=$(mktemp -d); PORT=55432
as_pg() { if [ "$(id -u)" = 0 ]; then su postgres -s /bin/bash -c "$*"; else bash -c "$*"; fi; }
chmod 777 "$D"
as_pg "$PG_BIN/initdb -D $D/data -U postgres -A trust >/dev/null && $PG_BIN/pg_ctl -D $D/data -o '-p $PORT -k $D' -l $D/log start >/dev/null"
trap 'as_pg "$PG_BIN/pg_ctl -D $D/data stop -m fast >/dev/null"; rm -rf "$D"' EXIT
P="$PG_BIN/psql -h $D -p $PORT -X -q -v ON_ERROR_STOP=1"
as_pg "$P -U postgres -d postgres -f $(pwd)/init/01-schemas.sql" >/dev/null
roles=$(grep -oP '^CREATE ROLE \K\w+' init/01-schemas.sql)
db_of() { [ "$1" = payment_service ] && echo payments || echo foodfleet; }
own=0; foreign=0; denied=0; total=0
for r in $roles; do
  db=$(db_of $r)
  # own schema: create, write, read
  as_pg "$P -U $r -d $db -c 'CREATE TABLE t (id int primary key); INSERT INTO t VALUES (1); SELECT * FROM t;'" >/dev/null && own=$((own+1))
done
for r in $roles; do
  for o in $roles; do
    [ $r = $o ] && continue
    db=$(db_of $o); total=$((total+1))
    if as_pg "$P -U $r -d $db -c 'SELECT * FROM $o.t'" >/dev/null 2>&1; then foreign=$((foreign+1))
    elif as_pg "$P -U $r -d $db -c 'INSERT INTO $o.t VALUES (2)'" >/dev/null 2>&1; then foreign=$((foreign+1))
    else denied=$((denied+1)); fi
  done
done
n=$(echo $roles | wc -w)
echo "services with a schema: $n; own schema usable: $own of $n"
echo "cross-service reads or writes: $foreign allowed, $denied of $total denied"
[ $own = $n ] && [ $foreign = 0 ]
