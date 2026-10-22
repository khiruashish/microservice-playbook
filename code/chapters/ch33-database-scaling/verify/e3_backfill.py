"""Experiment 3: filling a new column on 2 M busy rows. pgbench writes on 8 connections throughout; the backfill
starts at 5 s. One UPDATE for the whole table, against batches by primary-key range, each its own transaction."""
import glob
import os
import subprocess
import sys
import tempfile
import time

import wire
from e2_ddl_under_load import fresh_table, q, DB, HERE


def backfill(kind):
    c = wire.Pg(DB)
    try:
        if kind == "single":
            c.query("UPDATE orders SET delivery_zone = restaurant_id % 50 WHERE delivery_zone IS NULL")
            return 1
        size, pause = (10_000, 0.0) if kind == "batch-10k" else (1_000, 0.02)
        # rows inserted from now on get the value from the application, so the backfill stops at today's last id
        upto = int(c.query("SELECT max(id) FROM orders")[0][0])
        last, batches = 0, 0
        while last < upto:
            # keyset batches: the next range of ids, each batch committed on its own
            top = c.query(f"""WITH b AS (SELECT id FROM orders WHERE id > {last} AND id <= {upto} ORDER BY id LIMIT {size}),
                                   u AS (UPDATE orders o SET delivery_zone = o.restaurant_id % 50 FROM b
                                          WHERE o.id = b.id AND o.delivery_zone IS NULL RETURNING 1)
                              SELECT max(id), (SELECT count(*) FROM u) FROM b""")[0][0]
            batches += 1
            if top is None:
                break
            last = int(top)
            if pause:
                time.sleep(pause)
        return batches
    finally:
        c.close()


def run(kind, name):
    fresh_table()
    q("ALTER TABLE orders ADD COLUMN delivery_zone int")
    size0 = int(q("SELECT pg_total_relation_size('orders')")[0][0])
    tmp = tempfile.mkdtemp()
    load = subprocess.Popen(["pgbench", "-n", "-c", "8", "-j", "2", "-T", "80", "-l", "--log-prefix=tx",
                             "-f", os.path.join(HERE, "orders_load.sql"), "-d", DB],
                            cwd=tmp, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    t0 = time.time()
    time.sleep(5)
    lsn0 = q("SELECT pg_current_wal_lsn()")[0][0]
    start = time.time()
    batches = backfill(kind)
    end = time.time()
    wal = int(q(f"SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), '{lsn0}')")[0][0])
    load.wait()                                   # pgbench runs its full 80 s, so writes blocked by the backfill are logged
    tx = []
    for f in glob.glob(os.path.join(tmp, "tx*")):
        for line in open(f):
            p = line.split()
            if len(p) >= 6:
                tx.append((int(p[4]) + int(p[5]) / 1e6, int(p[2]) / 1e6))
    subprocess.run(["rm", "-rf", tmp])
    before = [l for t, l in tx if t0 + 1 <= t < start]
    during = [l for t, l in tx if t >= start and t - l <= end]      # writes in flight at any time during the backfill
    ratio = (len(during) / (end - start)) / (len(before) / (start - t0 - 1))
    missing = int(q("SELECT count(*) FROM orders WHERE delivery_zone IS NULL AND id <= 2000000")[0][0])
    size1 = int(q("SELECT pg_total_relation_size('orders')")[0][0])
    print(f"{name:40}{end - start:>8.1f} s{batches:>9}{max(during) * 1000:>13.0f} ms{sum(1 for l in during if l > 1):>14}"
          f"{ratio:>12.0%}{wal / 2**20:>7.0f} MB{(size1 - size0) / 2**20:>11.0f} MB{missing:>8}")
    sys.stdout.flush()


if __name__ == "__main__":
    print("2 M orders, a new nullable column delivery_zone; pgbench writes on 8 connections; the backfill starts at 5 s.\n")
    print(f"{'backfill':40}{'took':>10}{'batches':>9}{'longest write':>16}{'writes > 1 s':>14}{'throughput':>12}{'WAL':>10}{'table growth':>14}{'missed':>8}")
    for kind, name in (("single", "one UPDATE for all rows"), ("batch-10k", "batches of 10,000, no pause"),
                       ("batch-1k", "batches of 1,000, 20 ms pause")):
        run(kind, name)
