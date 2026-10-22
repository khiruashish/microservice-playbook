#!/usr/bin/env python3
"""
Offset vs keyset (seek) pagination on PostgreSQL, measured with EXPLAIN (ANALYZE, BUFFERS).

  python3 pagination_bench.py "psql -h localhost -U postgres -d ch21"

Needs only psql. For each page depth it times, as the median of 5 runs on a warm cache:
  offset:  ... ORDER BY placed_at DESC, id DESC LIMIT 20 OFFSET n
  keyset:  ... AND (placed_at, id) < (:last_placed_at, :last_id) ORDER BY ... LIMIT 20
and reports buffers touched (8 KB pages read from cache or disk).
"""
import json, shlex, statistics, subprocess, sys

PSQL = shlex.split(sys.argv[1] if len(sys.argv) > 1 else "psql -X -q -At -d ch21")
RESTAURANT = 42
PAGE = 20


def psql(sql):
    out = subprocess.run(PSQL + ["-X", "-q", "-At", "-c", sql], check=True, capture_output=True, text=True)
    return out.stdout.strip()


def explain(sql):
    plan = json.loads(psql("explain (analyze, buffers, format json) " + sql))[0]
    buffers = plan["Plan"].get("Shared Hit Blocks", 0) + plan["Plan"].get("Shared Read Blocks", 0)
    return plan["Execution Time"], buffers


def timed(sql, runs=5):
    explain(sql)                                   # warm up
    samples = [explain(sql) for _ in range(runs)]
    return statistics.median(s[0] for s in samples), samples[-1][1]


SELECT = ("select id, placed_at, status, total_paise from restaurant_orders "
          f"where restaurant_id = {RESTAURANT} ")
ORDER = f" order by placed_at desc, id desc limit {PAGE}"

total = int(psql(f"select count(*) from restaurant_orders where restaurant_id = {RESTAURANT}"))
print(f"restaurant {RESTAURANT}: {total:,} orders, {PAGE} per page, {total // PAGE:,} pages\n")
print(f"{'page':>7} {'rows skipped':>13} {'offset ms':>10} {'offset bufs':>12} {'keyset ms':>10} {'keyset bufs':>12}")
for page in [1, 10, 100, 1_000, 5_000, 9_999]:
    offset = (page - 1) * PAGE
    o_ms, o_buf = timed(SELECT + ORDER + f" offset {offset}")
    if offset == 0:
        k_sql = SELECT + ORDER
    else:
        # the cursor a client would hold: the last row of the previous page
        last = psql(SELECT + f" order by placed_at desc, id desc limit 1 offset {offset - 1}").split("|")
        k_sql = SELECT + f" and (placed_at, id) < ('{last[1]}', {last[0]})" + ORDER
    k_ms, k_buf = timed(k_sql)
    print(f"{page:>7,} {offset:>13,} {o_ms:>10.3f} {o_buf:>12,} {k_ms:>10.3f} {k_buf:>12,}")

c_ms, c_buf = timed(f"select count(*) from restaurant_orders where restaurant_id = {RESTAURANT}")
print(f"\n'total' for the response (count(*)): {c_ms:.3f} ms, {c_buf:,} buffers, on every first page")
