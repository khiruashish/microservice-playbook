#!/usr/bin/env bash
# Two things chapter 13's event store relies on, run on real PostgreSQL 16:
#   1. optimistic concurrency: two writers append version 5 of the same stream at once; one must fail;
#   2. the position gap: an append that commits late must not be skipped by a projection,
#      first with the naive "position > checkpoint" read, then with the transaction-id filter.
# Needs PG_BIN (PostgreSQL 16 binaries). About 5 seconds.
set -euo pipefail
cd "$(dirname "$0")"
PG_BIN=${PG_BIN:-$(pg_config --bindir)}
D=$(mktemp -d); PORT=55435
as_pg() { if [ "$(id -u)" = 0 ]; then su postgres -s /bin/bash -c "$*"; else bash -c "$*"; fi; }
chmod 777 "$D"
as_pg "$PG_BIN/initdb -D $D/data -U postgres -A trust >/dev/null && $PG_BIN/pg_ctl -D $D/data -o '-p $PORT -k $D' -l $D/log start >/dev/null"
trap 'as_pg "$PG_BIN/pg_ctl -D $D/data stop -m fast >/dev/null"; rm -rf "$D"' EXIT
P="$PG_BIN/psql -h $D -p $PORT -X -q -At -v ON_ERROR_STOP=1 -U postgres -d postgres"
sql() { as_pg "$P -c \"$1\""; }
as_pg "$P -f $(pwd)/../sql/event-store-pg.sql" >/dev/null

append() {  # stream version type
  echo "INSERT INTO event_store (stream_id, stream_version, event_type, schema_version, payload)
        VALUES ('$1', $2, '$3', 2, '{}')"
}
for v in 1 2 3 4; do sql "$(append wallet-ravi $v EarningCredited)"; done

echo "-- 1. two writers, both loaded the wallet at version 4"
W1="BEGIN; $(append wallet-ravi 5 PayoutRequested); SELECT pg_sleep(1); COMMIT;"
W2="BEGIN; $(append wallet-ravi 5 AdjustmentApplied); COMMIT;"
( as_pg "$P -c \"$W1\"" >/dev/null 2>$D/w1.err && echo "writer 1 (payout): appended version 5" || echo "writer 1: $(head -1 $D/w1.err)" ) &
sleep 0.3
as_pg "$P -c \"$W2\"" >/dev/null 2>$D/w2.err && echo "writer 2 (adjustment): appended version 5" || echo "writer 2 (adjustment): $(grep -o 'duplicate key.*' $D/w2.err | head -1)"
wait
echo "stream now: $(sql "SELECT string_agg(stream_version || ' ' || event_type, ', ' ORDER BY stream_version) FROM event_store WHERE stream_id = 'wallet-ravi'")"

echo "-- 2. the position gap"
run_gap() {  # name, read query
  sql "DELETE FROM projection_checkpoint; INSERT INTO projection_checkpoint VALUES ('$1', (SELECT max(global_position) FROM event_store))"
  # A: takes the next position, commits 2 s later. B: takes the one after, commits at once.
  as_pg "$P -c \"BEGIN; $(append wallet-asha 1 EarningCredited); SELECT pg_sleep(2); COMMIT;\"" >/dev/null &
  sleep 0.3
  sql "$(append wallet-meena 1 EarningCredited)"
  # The projection polls once while A is still open, and once after.
  local seen=""
  for poll in 1 2; do
    local rows
    rows=$(sql "SELECT global_position || ':' || stream_id FROM event_store
                WHERE global_position > (SELECT position FROM projection_checkpoint WHERE projection = '$1') $2
                ORDER BY global_position")
    for r in $rows; do seen="$seen ${r#*:}"; sql "UPDATE projection_checkpoint SET position = ${r%%:*} WHERE projection = '$1'"; done
    [ $poll = 1 ] && wait
  done
    local missed; missed=$(for s in wallet-asha wallet-meena; do [[ " $seen " == *" $s "* ]] || printf '%s' $s; done)
  echo "$1: projection saw [${seen# }], missed: ${missed:-none}"
  sql "DELETE FROM event_store WHERE stream_id IN ('wallet-asha', 'wallet-meena')"
}
run_gap naive ""
run_gap tx-filter "AND tx_id < pg_snapshot_xmin(pg_current_snapshot())"
