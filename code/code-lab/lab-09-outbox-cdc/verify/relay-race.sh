#!/usr/bin/env bash
# Two relays claim batches from the same outbox at the same moment, on real PostgreSQL 16: with FOR UPDATE SKIP LOCKED
# they get disjoint rows; without SKIP LOCKED the second waits for the first. Needs PG_BIN (PostgreSQL 16 binaries).
set -euo pipefail
cd "$(dirname "$0")"
PG_BIN=${PG_BIN:-$(pg_config --bindir)}
D=$(mktemp -d); PORT=55434
as_pg() { if [ "$(id -u)" = 0 ]; then su postgres -s /bin/bash -c "$*"; else bash -c "$*"; fi; }
chmod 777 "$D"
as_pg "$PG_BIN/initdb -D $D/data -U postgres -A trust >/dev/null && $PG_BIN/pg_ctl -D $D/data -o '-p $PORT -k $D' -l $D/log start >/dev/null"
trap 'as_pg "$PG_BIN/pg_ctl -D $D/data stop -m fast >/dev/null"; rm -rf "$D"' EXIT
P="$PG_BIN/psql -h $D -p $PORT -X -q -At -v ON_ERROR_STOP=1 -U postgres -d postgres"
as_pg "$P -f $(pwd)/../src/main/resources/db/migration/V1__orders_outbox.sql" >/dev/null
as_pg "$P -c \"INSERT INTO outbox (id, aggregate_type, aggregate_id, type, payload)
  SELECT gen_random_uuid(), 'order', 'o-' || g, 'OrderPlaced', '{}' FROM generate_series(1, 150) g\""
CLAIM="BEGIN; SELECT position FROM outbox WHERE published_at IS NULL ORDER BY position LIMIT 100 FOR UPDATE SKIP LOCKED;
  SELECT pg_sleep(1); COMMIT;"
as_pg "$P -c \"$CLAIM\"" > $D/a.txt &
sleep 0.3
as_pg "$P -c \"$CLAIM\"" > $D/b.txt
wait
a=$(grep -c '^[0-9]' $D/a.txt); b=$(grep -c '^[0-9]' $D/b.txt)
both=$(sort $D/a.txt $D/b.txt | grep '^[0-9]' | uniq -d | wc -l)
echo "relay A claimed $a rows (positions $(grep '^[0-9]' $D/a.txt | head -1)-$(grep '^[0-9]' $D/a.txt | tail -1)), relay B $b rows (positions $(grep '^[0-9]' $D/b.txt | head -1)-$(grep '^[0-9]' $D/b.txt | tail -1)); claimed by both: $both"
