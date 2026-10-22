#!/usr/bin/env bash
# Lab 18 on a throwaway PostgreSQL 16: the service's own migration, then what row-level security does and doesn't
# protect against. Needs PG_BIN (PostgreSQL 16 binaries). About 5 seconds.
set -euo pipefail
cd "$(dirname "$0")"
PG_BIN=${PG_BIN:-$(pg_config --bindir)}
D=$(mktemp -d); PORT=55436
as_pg() { if [ "$(id -u)" = 0 ]; then su postgres -s /bin/bash -c "$*"; else bash -c "$*"; fi; }
chmod 777 "$D"
as_pg "$PG_BIN/initdb -D $D/data -U postgres -A trust >/dev/null && $PG_BIN/pg_ctl -D $D/data -o '-p $PORT -k $D' -l $D/log start >/dev/null"
trap 'as_pg "$PG_BIN/pg_ctl -D $D/data stop -m fast >/dev/null"; rm -rf "$D"' EXIT
psql() { as_pg "$PG_BIN/psql -h $D -p $PORT -X -q -At -v ON_ERROR_STOP=1 -U $1 -d postgres" ; }
as() { local who=$1; shift; printf '%s\n' "$@" | psql "$who" 2>&1; }

as postgres "$(cat ../src/test/resources/db/roles.sql)" "SET ROLE orders_owner;" \
  "$(cat ../src/main/resources/db/migration/V1__orders_rls.sql)" > /dev/null
# 3 tenants x 1,000 orders, written as each tenant: the WITH CHECK clause allows nothing else
for t in spice-route biryani-house dosa-corner; do
  as orders_app "BEGIN;" "SELECT set_config('app.tenant_id', '$t', true);" \
    "INSERT INTO orders SELECT '$t', nextval('order_ids'), 'c-' || g, 25000 + g FROM generate_series(1, 1000) g;" "COMMIT;" > /dev/null
done
q() { echo "$1" | sed 's/^/  /'; }

echo "== as orders_app, in a transaction for one tenant"
q "$(as orders_app "BEGIN;" "SELECT set_config('app.tenant_id', 'spice-route', true) IS NOT NULL;" \
     "SELECT 'rows visible: ' || count(*) || ', tenants: ' || count(DISTINCT tenant_id) FROM orders;" "COMMIT;" | tail -1)"
echo "== a query that forgot its WHERE tenant_id, with no tenant set"
q "$(as orders_app "SELECT 'rows visible: ' || count(*) FROM orders;")"
echo "== writing another tenant's row while acting for spice-route"
q "$(as orders_app "BEGIN;" "SELECT set_config('app.tenant_id', 'spice-route', true) IS NOT NULL;" \
     "INSERT INTO orders VALUES ('dosa-corner', nextval('order_ids'), 'c-x', 100);" | grep -o 'ERROR.*')"
echo "== moving a row to another tenant"
q "$(as orders_app "BEGIN;" "SELECT set_config('app.tenant_id', 'spice-route', true) IS NOT NULL;" \
     "UPDATE orders SET tenant_id = 'dosa-corner' WHERE id = 1;" | grep -o 'ERROR.*')"
echo "== who else sees everything"
q "$(as postgres "SELECT 'superuser: ' || count(*) FROM orders;")"
q "$(as postgres "ALTER ROLE orders_app BYPASSRLS;" "SET ROLE orders_app;" "SELECT 'orders_app with BYPASSRLS: ' || count(*) FROM orders;" "RESET ROLE;" "ALTER ROLE orders_app NOBYPASSRLS;")"
q "$(as postgres "ALTER TABLE orders NO FORCE ROW LEVEL SECURITY;" "SET ROLE orders_owner;" "SELECT 'table owner, RLS not forced: ' || count(*) FROM orders;" "RESET ROLE;" "ALTER TABLE orders FORCE ROW LEVEL SECURITY;")"
q "$(as postgres "SET ROLE orders_owner;" "SELECT 'table owner, RLS forced: ' || count(*) FROM orders;")"
echo "== a pooled connection: the next request forgot to set a tenant"
q "$(as orders_app "SET app.tenant_id = 'spice-route';" "SELECT 1 FROM orders LIMIT 0;" \
     "SELECT 'session-level SET, next request sees: ' || count(*) || ' rows of ' || coalesce(max(tenant_id), '-') FROM orders;")"
q "$(as orders_app "BEGIN;" "SELECT set_config('app.tenant_id', 'spice-route', true) IS NOT NULL;" "COMMIT;" \
     "SELECT 'transaction-local, next request sees: ' || count(*) || ' rows' FROM orders;" | tail -1)"
echo "== the plan for one tenant's recent orders"
as postgres "ANALYZE orders;" > /dev/null
as orders_app "BEGIN;" "SELECT set_config('app.tenant_id', 'spice-route', true) IS NOT NULL;" \
  "EXPLAIN (COSTS OFF) SELECT * FROM orders WHERE tenant_id = 'spice-route' ORDER BY created_at DESC LIMIT 20;" 2>&1 | grep -v "^t$\|ERROR" | sed 's/^/  /'
