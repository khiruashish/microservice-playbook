#!/usr/bin/env bash
# Experiment 1: the same restaurant menus as normalized tables (R) and as one JSONB document each (D).
# Loads catalog_data.sql into $PGDATABASE (default c29), then runs each workload with pgbench:
# 4 clients, 15 s. The *_hot workloads update items of one restaurant only (a busy kitchen editing its menu).
set -euo pipefail
cd "$(dirname "$0")"
export PGDATABASE=${PGDATABASE:-c29}
DUR=${DUR:-15}
q() { psql -XAtq -c "$1"; }

psql -Xq -v ON_ERROR_STOP=1 -f catalog_data.sql >/dev/null 2>&1
echo "storage, freshly loaded"
q "SELECT '  relational, 5 tables + indexes: ' || pg_size_pretty(sum(pg_total_relation_size(c))) FROM unnest(ARRAY['restaurants','menu_sections','menu_items','option_groups','options']::regclass[]) c"
q "SELECT '  document, 1 table: ' || pg_size_pretty(pg_total_relation_size('restaurant_menus'))
        || '  (a menu: ' || pg_size_pretty(avg(octet_length(menu::text))::bigint) || ' of JSON, '
        || pg_size_pretty(avg(pg_column_size(menu))::bigint) || ' stored compressed)' FROM restaurant_menus"
echo

printf '%-26s %10s %12s %16s\n' workload tps "avg ms" "WAL bytes/tx"
for w in r_read_menu d_read_menu r_read_item d_read_item r_update_price d_update_price r_update_hot d_update_hot; do
  q "CHECKPOINT"
  before=$(q "SELECT pg_current_wal_lsn()")
  out=$(pgbench -n -c 4 -j 2 -T "$DUR" -f "pgbench/$w.sql" 2>&1)
  after=$(q "SELECT pg_current_wal_lsn()")
  tx=$(sed -n 's/^number of transactions actually processed: \([0-9]*\).*/\1/p' <<<"$out")
  tps=$(sed -n 's/^tps = \([0-9.]*\).*/\1/p' <<<"$out")
  lat=$(sed -n 's/^latency average = \([0-9.]*\) ms/\1/p' <<<"$out")
  wal=$(q "SELECT round(pg_wal_lsn_diff('$after', '$before') / $tx)")
  printf '%-26s %10.0f %12s %16s\n' "$w" "$tps" "$lat" "$wal"
done

echo
echo "cross-restaurant query: available veg items under Rs 150 in Bengaluru, cheapest 20 (median of 5 runs)"
q "CREATE INDEX IF NOT EXISTS menu_items_veg_price ON menu_items (price_paise) WHERE is_veg AND available"
q "ANALYZE menu_items"
median() { sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}'; }
run() {
  for _ in 1 2 3 4 5; do
    psql -XAtq -c "EXPLAIN (ANALYZE, FORMAT JSON) $1" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["Execution Time"])'
  done | median
}
R_SQL="SELECT i.name, i.price_paise, r.name FROM menu_items i JOIN restaurants r ON r.id = i.restaurant_id
        WHERE r.city = 'Bengaluru' AND i.is_veg AND i.available AND i.price_paise < 15000
        ORDER BY i.price_paise LIMIT 20"
D_SQL="SELECT item->>'name', (item->>'price')::int AS price, m.menu->>'name' FROM restaurant_menus m,
        jsonb_path_query(m.menu, '\$.sections[*].items[*] ? (@.veg == true && @.available == true && @.price < 15000)') item
        WHERE m.menu->>'city' = 'Bengaluru' ORDER BY price LIMIT 20"
printf '  %-36s %8s ms\n' "relational (partial index)" "$(run "$R_SQL")"
printf '  %-36s %8s ms\n' "document (jsonb_path_query)" "$(run "$D_SQL")"
q "SELECT 'same answer: ' || ((SELECT array_agg(p ORDER BY p) FROM (SELECT i.price_paise p FROM menu_items i JOIN restaurants r ON r.id = i.restaurant_id WHERE r.city = 'Bengaluru' AND i.is_veg AND i.available AND i.price_paise < 15000 ORDER BY 1 LIMIT 20) a)
   = (SELECT array_agg(p ORDER BY p) FROM (SELECT (item->>'price')::int p FROM restaurant_menus m, jsonb_path_query(m.menu, '\$.sections[*].items[*] ? (@.veg == true && @.available == true && @.price < 15000)') item WHERE m.menu->>'city' = 'Bengaluru' ORDER BY 1 LIMIT 20) b))::text"

echo
echo "after the updates"
q "SELECT '  document table: ' || pg_size_pretty(pg_total_relation_size('restaurant_menus')) || ' (old versions of whole menus, until vacuum)'"
