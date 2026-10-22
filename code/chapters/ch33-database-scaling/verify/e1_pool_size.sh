#!/usr/bin/env bash
# Experiment 1: how many connections? pgbench's simple-update workload (-N: no hot branch/teller rows) at scale 20
# (2 M accounts) with 2-256 connections, 15 s each, and the cost of opening a connection per transaction (-C).
# The demand is the same in every case: 256 concurrent requests. With fewer connections the rest wait in the
# application's pool, so their latency is 256 / throughput (Little's law), shown as "latency at 256 requests".
set -euo pipefail
export PGDATABASE=${PGDATABASE:-c33pool}
DUR=${DUR:-15}
psql -XAtq -d postgres -c "DROP DATABASE IF EXISTS $PGDATABASE" -c "CREATE DATABASE $PGDATABASE"
pgbench -q -i -s 20 >/dev/null 2>&1
printf '%-34s %8s %10s %12s %22s\n' connections tps "avg ms" "p99 ms" "latency at 256 requests"
for c in 2 4 8 16 32 64 128 256; do
  dir=$(mktemp -d)
  (cd "$dir" && pgbench -n -N -c "$c" -j 2 -T "$DUR" -l --log-prefix=tx >/dev/null 2>&1)
  python3 - "$dir" "$c" "$DUR" <<'PY'
import glob, sys
d, c, dur = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
lat = []
for f in glob.glob(d + "/tx*"):
    for line in open(f):
        lat.append(int(line.split()[2]) / 1000)
lat.sort()
tps = len(lat) / dur
print(f"{c:<34} {tps:8.0f} {sum(lat) / len(lat):10.2f} {lat[int(0.99 * len(lat))]:12.2f} {256 / tps * 1000:19.1f} ms")
PY
  rm -rf "$dir"
done
out=$(pgbench -n -N -c 16 -j 2 -T "$DUR" -C 2>&1)
printf '%-34s %8.0f %10.2f\n' "16, new connection per transaction" "$(sed -n 's/^tps = \([0-9.]*\).*/\1/p' <<<"$out")" \
  "$(sed -n 's/^latency average = \([0-9.]*\) ms/\1/p' <<<"$out")"
