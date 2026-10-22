"""Offset vs keyset pagination on the 2M-row orders table (run pagination-setup.sql first).

Usage: python3 pagination_benchmark.py "psql -X -h HOST -p PORT -U USER -d bench"
For each page depth it runs both queries 7 times with EXPLAIN (ANALYZE, BUFFERS) and reports the
median execution time and the shared buffers touched. Page size 20, newest first.
"""
import re, statistics, subprocess, sys

PSQL = sys.argv[1].split()
PAGE = 20

def psql(sql):
    return subprocess.run(PSQL + ["-At", "-c", sql], capture_output=True, text=True, check=True).stdout

def explain(sql):
    out = psql("explain (analyze, buffers) " + sql)
    ms = float(re.search(r"Execution Time: ([\d.]+) ms", out).group(1))
    buffers = re.search(r"Buffers: shared hit=(\d+)(?: read=(\d+))?", out)
    touched = int(buffers.group(1)) + int(buffers.group(2) or 0) if buffers else 0
    return ms, touched

def measure(sql, runs=7):
    results = [explain(sql) for _ in range(runs)]
    return statistics.median(r[0] for r in results), results[-1][1]

print(f"{'page':>7} {'rows skipped':>13} | {'OFFSET ms':>10} {'buffers':>9} | {'KEYSET ms':>10} {'buffers':>9} | {'ratio':>7}")
for page in [1, 10, 100, 1_000, 10_000, 50_000, 99_999]:
    skip = (page - 1) * PAGE
    offset_sql = f"select id, placed_at, total_paise from orders order by placed_at desc, id desc limit {PAGE} offset {skip}"
    if skip == 0:
        keyset_sql = f"select id, placed_at, total_paise from orders order by placed_at desc, id desc limit {PAGE}"
    else:
        last = psql(f"select placed_at, id from orders order by placed_at desc, id desc limit 1 offset {skip - 1}").strip()
        placed_at, last_id = last.split("|")
        keyset_sql = (f"select id, placed_at, total_paise from orders where (placed_at, id) < ('{placed_at}', '{last_id}') "
                      f"order by placed_at desc, id desc limit {PAGE}")
    assert psql(offset_sql) == psql(keyset_sql), f"different rows at page {page}"   # same page, two ways
    o_ms, o_buf = measure(offset_sql)
    k_ms, k_buf = measure(keyset_sql)
    print(f"{page:>7,} {skip:>13,} | {o_ms:>10.3f} {o_buf:>9,} | {k_ms:>10.3f} {k_buf:>9,} | {o_ms / k_ms:>6.0f}x")
