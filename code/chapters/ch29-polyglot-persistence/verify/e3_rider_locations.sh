#!/usr/bin/env bash
# Experiment 3: 50,000 riders' latest positions (Bengaluru, ~25 km across). PostgreSQL row per rider vs a
# Redis geo set, at matched durability. pgbench and redis-benchmark: both C clients, 8 connections, TCP to
# localhost, no pipelining. Needs REDIS_PORT (default 6379); FLUSHES that Redis.
set -euo pipefail
cd "$(dirname "$0")"
export PGDATABASE=${PGDATABASE:-c29}
DUR=${DUR:-15}
RP=${REDIS_PORT:-6379}
q() { psql -XAtq -c "$1"; }
rc() { redis-cli -p "$RP" "$@" >/dev/null; }

q "CREATE EXTENSION IF NOT EXISTS cube; CREATE EXTENSION IF NOT EXISTS earthdistance"
q "DROP TABLE IF EXISTS rider_positions; CREATE TABLE rider_positions (rider_id int PRIMARY KEY,
   lat float8 NOT NULL, lon float8 NOT NULL, updated_at timestamptz NOT NULL)"
q "SELECT setseed(0.3); INSERT INTO rider_positions SELECT r, 12.97 + (random() - 0.5) / 4, 77.59 + (random() - 0.5) / 4, now()
   FROM generate_series(1, 50000) r"
q "VACUUM ANALYZE rider_positions"

pg() {  # label, script, extra PGOPTIONS
  q "CHECKPOINT"
  local before after out tps lat tx
  before=$(q "SELECT pg_current_wal_lsn()")
  out=$(PGOPTIONS="${3:-}" pgbench -h 127.0.0.1 -n -c 8 -j 2 -T "$DUR" -f "pgbench/$2.sql" 2>&1)
  after=$(q "SELECT pg_current_wal_lsn()")
  tx=$(sed -n 's/^number of transactions actually processed: \([0-9]*\).*/\1/p' <<<"$out")
  tps=$(sed -n 's/^tps = \([0-9.]*\).*/\1/p' <<<"$out")
  lat=$(sed -n 's/^latency average = \([0-9.]*\) ms/\1/p' <<<"$out")
  printf '  %-58s %9.0f/s %8s ms %9s WAL B/op\n' "$1" "$tps" "$lat" "$(q "SELECT round(pg_wal_lsn_diff('$after', '$before') / $tx)")"
}
rb() {  # label, requests, command...
  local label=$1 n=$2; shift 2
  local out
  out=$(redis-benchmark -p "$RP" -c 8 -n "$n" -r 50000 --csv "$@" 2>/dev/null | tail -1)
  # "test","rps","avg_latency_ms",...
  printf '  %-58s %9.0f/s %8s ms\n' "$label" "$(cut -d, -f2 <<<"$out" | tr -d '"')" "$(cut -d, -f3 <<<"$out" | tr -d '"')"
}

echo "position updates"
pg "PostgreSQL, synchronous_commit=on" rider_update
rc FLUSHALL; rc CONFIG SET appendonly yes; rc CONFIG SET appendfsync always; sleep 1
rb "Redis GEOADD, AOF appendfsync always" 300000 GEOADD riders 77.59 12.97 "rider:__rand_int__"
pg "PostgreSQL, synchronous_commit=off" rider_update "-c synchronous_commit=off"
rc CONFIG SET appendfsync everysec
rb "Redis GEOADD, AOF appendfsync everysec" 1000000 GEOADD riders 77.59 12.97 "rider:__rand_int__"
rc CONFIG SET appendonly no
rb "Redis GEOADD, no persistence" 1000000 GEOADD riders 77.59 12.97 "rider:__rand_int__"

q "SELECT n_tup_hot_upd * 100 / greatest(n_tup_upd, 1) FROM pg_stat_user_tables WHERE relname = 'rider_positions'" \
  | xargs printf '  (PostgreSQL: %s%% of updates were HOT, touching no index)\n'
q "CREATE INDEX rider_positions_geo ON rider_positions USING gist (ll_to_earth(lat, lon)); SELECT pg_stat_reset()"
pg "PostgreSQL + GiST geo index, synchronous_commit=off" rider_update "-c synchronous_commit=off"
q "SELECT n_tup_hot_upd * 100 / greatest(n_tup_upd, 1) FROM pg_stat_user_tables WHERE relname = 'rider_positions'" \
  | xargs printf '  (PostgreSQL with the geo index: %s%% HOT)\n'

echo
echo "nearest 10 riders within 3 km of a random point"
q "VACUUM ANALYZE rider_positions"
pg "PostgreSQL, earthdistance + GiST, KNN order" rider_nearby
# Redis: the same 50,000 random positions as the PostgreSQL table
rc DEL riders
q "SELECT 'GEOADD riders ' || string_agg(lon || ' ' || lat || ' rider:' || rider_id, ' ') FROM rider_positions" \
  | redis-cli -p "$RP" >/dev/null
rb "Redis GEOSEARCH BYRADIUS 3 km ASC COUNT 10" 20000 GEOSEARCH riders FROMLONLAT 77.59 12.97 BYRADIUS 3 km ASC COUNT 10
rb "Redis GEOSEARCH BYRADIUS 3 km COUNT 10 ANY (unsorted)" 200000 GEOSEARCH riders FROMLONLAT 77.59 12.97 BYRADIUS 3 km COUNT 10 ANY
echo "  (Redis searches one fixed point, the city centre; redis-benchmark cannot randomize coordinates)"
q "SELECT count(*) FROM rider_positions WHERE earth_distance(ll_to_earth(lat, lon), ll_to_earth(12.97, 77.59)) < 3000" \
  | xargs printf '  riders within 3 km of the centre: %s\n'
printf '  Redis memory for 50,000 riders: %s\n' "$(redis-cli -p "$RP" MEMORY USAGE riders)B in the geo set"
