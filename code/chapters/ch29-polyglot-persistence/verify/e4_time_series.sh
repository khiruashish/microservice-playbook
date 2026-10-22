#!/usr/bin/env bash
# Experiment 4: rider position history, 7 days x 1,000 riders x one ping a minute = 10.08 M rows, written in
# time order. One plain table with B-tree indexes vs a table partitioned by day with a BRIN index on time.
set -euo pipefail
cd "$(dirname "$0")"
export PGDATABASE=${PGDATABASE:-c29}
q() { psql -XAtq -c "$1"; }
median() { sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}'; }
exec_ms() { for _ in 1 2 3 4 5; do psql -XAtq -c "EXPLAIN (ANALYZE, FORMAT JSON) $1" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["Execution Time"])'; done | median; }
timed() { local t0 t1; t0=$(date +%s%N); q "$1" >/dev/null; t1=$(date +%s%N); echo $(( (t1 - t0) / 1000000 )); }
wal_of() { local b a; b=$(q "SELECT pg_current_wal_lsn()"); q "$1" >/dev/null; a=$(q "SELECT pg_current_wal_lsn()");
  q "SELECT pg_size_pretty(pg_wal_lsn_diff('$a', '$b'))"; }

START="2026-09-01 00:00:00+00"
GEN="SELECT r, ts, 12.97 + (random() - 0.5) / 4, 77.59 + (random() - 0.5) / 4, (random() * 40)::real
       FROM generate_series(timestamptz '$START', timestamptz '$START' + interval '7 days' - interval '1 minute', interval '1 minute') ts,
            generate_series(1, 1000) r"

q "DROP TABLE IF EXISTS pings_plain, pings_part"
q "CREATE TABLE pings_plain (rider_id int NOT NULL, recorded_at timestamptz NOT NULL, lat float8, lon float8, speed real)"
q "CREATE INDEX ON pings_plain (recorded_at)"
q "CREATE INDEX ON pings_plain (rider_id, recorded_at)"
q "CREATE TABLE pings_part (rider_id int NOT NULL, recorded_at timestamptz NOT NULL, lat float8, lon float8, speed real)
   PARTITION BY RANGE (recorded_at)"
for d in 0 1 2 3 4 5 6; do
  q "CREATE TABLE pings_part_d$d PARTITION OF pings_part FOR VALUES FROM (timestamptz '$START' + interval '$d days')
     TO (timestamptz '$START' + interval '$((d + 1)) days')"
done
q "CREATE INDEX ON pings_part USING brin (recorded_at)"
q "CREATE INDEX ON pings_part (rider_id, recorded_at)"

echo "ingest 10.08 M rows in time order"
printf '  %-44s %8s ms\n' "plain table, B-tree on time" "$(timed "INSERT INTO pings_plain $GEN")"
printf '  %-44s %8s ms\n' "partitioned by day, BRIN on time" "$(timed "INSERT INTO pings_part $GEN")"
q "VACUUM ANALYZE pings_plain"; q "VACUUM ANALYZE pings_part"

echo
echo "size"
printf '  %-44s %s\n' "B-tree index on recorded_at" "$(q "SELECT pg_size_pretty(pg_relation_size('pings_plain_recorded_at_idx'))")"
printf '  %-44s %s\n' "BRIN index on recorded_at (7 partitions)" "$(q "SELECT pg_size_pretty(sum(pg_relation_size(indexrelid))) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE c.relname LIKE 'pings_part_d%recorded_at_idx' AND c.relam = (SELECT oid FROM pg_am WHERE amname = 'brin')")"
printf '  %-44s %s\n' "plain table, total" "$(q "SELECT pg_size_pretty(pg_total_relation_size('pings_plain'))")"
printf '  %-44s %s\n' "partitioned table, total" "$(q "SELECT pg_size_pretty(sum(pg_total_relation_size(inhrelid))) FROM pg_inherits WHERE inhparent = 'pings_part'::regclass")"

echo
echo "queries (median of 5)"
W="recorded_at >= timestamptz '$START' + interval '3 days 18 hours' AND recorded_at < timestamptz '$START' + interval '3 days 18 hours 10 minutes'"
printf '  %-44s %8s ms\n' "10-minute window, all riders: plain/B-tree" "$(exec_ms "SELECT count(*), avg(speed) FROM pings_plain WHERE $W")"
printf '  %-44s %8s ms\n' "10-minute window, all riders: part/BRIN" "$(exec_ms "SELECT count(*), avg(speed) FROM pings_part WHERE $W")"
T="rider_id = 417 AND recorded_at >= timestamptz '$START' + interval '6 days 23 hours'"
printf '  %-44s %8s ms\n' "one rider, last hour: plain" "$(exec_ms "SELECT lat, lon FROM pings_plain WHERE $T ORDER BY recorded_at")"
printf '  %-44s %8s ms\n' "one rider, last hour: partitioned" "$(exec_ms "SELECT lat, lon FROM pings_part WHERE $T ORDER BY recorded_at")"

echo
echo "retention: remove the oldest day (1.44 M rows)"
D0="recorded_at < timestamptz '$START' + interval '1 day'"
q "CHECKPOINT"
t0=$(date +%s%N); wal=$(wal_of "DELETE FROM pings_plain WHERE $D0"); t1=$(date +%s%N)
printf '  %-44s %8s ms, %s of WAL; table still %s until VACUUM reuses the space\n' "DELETE ... WHERE recorded_at < day 1" \
  "$(( (t1 - t0) / 1000000 ))" "$wal" "$(q "SELECT pg_size_pretty(pg_total_relation_size('pings_plain'))")"
q "CHECKPOINT"
t0=$(date +%s%N); wal=$(wal_of "DROP TABLE pings_part_d0"); t1=$(date +%s%N)
printf '  %-44s %8s ms, %s of WAL\n' "DROP TABLE pings_part_d0 (one partition)" "$(( (t1 - t0) / 1000000 ))" "$wal"
