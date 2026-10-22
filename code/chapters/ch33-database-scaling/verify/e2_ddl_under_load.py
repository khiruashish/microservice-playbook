"""Experiment 2: schema changes on a busy table. 2 M orders; pgbench runs updates and inserts on 8 connections for
25 s; at 8 s the change runs. For each change: how long it took, and what the writers saw while it ran (the longest
a write waited, writes over 1 s, throughput in the window against before). Needs pgbench, PGHOST/PGPORT/PGUSER."""
import glob
import os
import subprocess
import sys
import tempfile
import threading
import time

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
DB = "c33"


def q(sql, db=DB):
    c = wire.Pg(db)
    try:
        return c.query(sql)
    finally:
        c.close()


def fresh_table():
    if not q("SELECT 1 FROM pg_database WHERE datname = 'c33'", "postgres"):
        q("CREATE DATABASE c33", "postgres")
    if not q("SELECT 1 FROM pg_tables WHERE tablename = 'orders_template'"):
        q("""CREATE TABLE orders_template AS
             SELECT g AS id, (g % 50000)::int AS customer_id, (g % 2000)::int AS restaurant_id,
                    (10000 + g % 50000)::int AS amount_paise, 'DELIVERED'::text AS status,
                    now() - (g || ' seconds')::interval AS created_at
               FROM generate_series(1, 2000000) g""")
    q("DROP TABLE IF EXISTS orders")
    q("CREATE TABLE orders (LIKE orders_template)")
    q("INSERT INTO orders SELECT * FROM orders_template")
    q("ALTER TABLE orders ADD PRIMARY KEY (id)")
    q("CREATE SEQUENCE IF NOT EXISTS orders_id_seq; SELECT setval('orders_id_seq', 2000000)")
    q("ALTER TABLE orders ALTER COLUMN id SET DEFAULT nextval('orders_id_seq')")
    q("VACUUM ANALYZE orders")
    q("CHECKPOINT")


def retry_with_lock_timeout(sql, timeout="500ms", pause=1.0):
    """What a migration tool should do on a busy table: give up quickly instead of queueing, and try again."""
    attempts = 0
    while True:
        attempts += 1
        c = wire.Pg(DB)
        try:
            c.query(f"SET lock_timeout = '{timeout}'")
            c.query(sql)
            return attempts
        except wire.PgError as e:
            if e.fields.get("C") != "55P03":
                raise
            time.sleep(pause)
        finally:
            c.close()


CASES = [
    ("ADD COLUMN note text", lambda: q("ALTER TABLE orders ADD COLUMN note text")),
    ("ADD COLUMN tip int NOT NULL DEFAULT 0", lambda: q("ALTER TABLE orders ADD COLUMN tip_paise int NOT NULL DEFAULT 0")),
    ("ADD COLUMN ref uuid DEFAULT gen_random_uuid()", lambda: q("ALTER TABLE orders ADD COLUMN ref uuid DEFAULT gen_random_uuid()")),
    ("ALTER COLUMN amount_paise TYPE bigint", lambda: q("ALTER TABLE orders ALTER COLUMN amount_paise TYPE bigint")),
    ("CREATE INDEX", lambda: q("CREATE INDEX orders_restaurant_created ON orders (restaurant_id, created_at)")),
    ("CREATE INDEX CONCURRENTLY", lambda: q("CREATE INDEX CONCURRENTLY orders_restaurant_created ON orders (restaurant_id, created_at)")),
    ("ADD CONSTRAINT CHECK", lambda: q("ALTER TABLE orders ADD CONSTRAINT amount_positive CHECK (amount_paise > 0)")),
    ("ADD CONSTRAINT CHECK NOT VALID, then VALIDATE", lambda: (
        q("ALTER TABLE orders ADD CONSTRAINT amount_positive CHECK (amount_paise > 0) NOT VALID"),
        q("ALTER TABLE orders VALIDATE CONSTRAINT amount_positive"))),
    ("ADD COLUMN behind a 12 s report query", "queue"),
    ("  ...with lock_timeout 500 ms and retries", "queue-timeout"),
]


def run_case(name, action):
    fresh_table()
    tmp = tempfile.mkdtemp()
    load = subprocess.Popen(["pgbench", "-n", "-c", "8", "-j", "2", "-T", "25", "-l", "--log-prefix=tx",
                             "-f", os.path.join(HERE, "orders_load.sql"), "-d", DB],
                            cwd=tmp, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    t0 = time.time()
    note = ""
    if action in ("queue", "queue-timeout"):
        time.sleep(6)
        report = threading.Thread(target=lambda: q("SELECT count(*), pg_sleep(12) FROM (SELECT 1 FROM orders LIMIT 1) o"))
        report.start()
        time.sleep(2)
        start = time.time()
        if action == "queue":
            q("ALTER TABLE orders ADD COLUMN note text")
        else:
            note = f"{retry_with_lock_timeout('ALTER TABLE orders ADD COLUMN note text')} attempts"
        end = time.time()
        report.join()
    else:
        time.sleep(8)
        start = time.time()
        action()
        end = time.time()
    load.wait()
    tx = []                                        # (completed at, latency s)
    for f in glob.glob(os.path.join(tmp, "tx*")):
        for line in open(f):
            p = line.split()
            tx.append((int(p[4]) + int(p[5]) / 1e6, int(p[2]) / 1e6))
    subprocess.run(["rm", "-rf", tmp])
    before = [l for t, l in tx if t0 + 1 <= t < start]
    during = [l for t, l in tx if t >= start and t - l <= end + 0.5]   # writes in flight at any time during the change
    window = end + 0.5 - start
    base_tps = len(before) / max(start - t0 - 1, 1e-9)
    tps = len(during) / window
    longest = max(during) if during else max((l for t, l in tx if t > end), default=0)
    print(f"{name:46}{end - start:>9.2f} s{longest * 1000:>13.0f} ms{sum(1 for l in during if l > 1):>14}"
          f"{tps / base_tps if base_tps else 0:>12.0%}   {note}")
    sys.stdout.flush()


def scan(plan):
    return next(r[0].strip().lstrip("-> ").split("  (")[0] for r in plan if "Scan" in r[0])


def failed_concurrent_build():
    """CREATE INDEX CONCURRENTLY waits for every transaction holding an older snapshot, anywhere in the database.
    With lock_timeout set (as the service sets it for Flyway) the wait times out and leaves an INVALID index."""
    fresh_table()
    ddl = "CREATE INDEX CONCURRENTLY IF NOT EXISTS orders_restaurant_created ON orders (restaurant_id, created_at)"
    index_state = "SELECT indisvalid FROM pg_index WHERE indexrelid = 'orders_restaurant_created'::regclass"
    plan = "EXPLAIN SELECT * FROM orders WHERE restaurant_id = 7 AND created_at > now() - interval '1 hour'"
    report = wire.Pg(DB)
    report.query("BEGIN ISOLATION LEVEL REPEATABLE READ")      # a long report, a pg_dump, an analyst's open session
    report.query("SELECT 1")
    c = wire.Pg(DB)
    c.query("SET lock_timeout = '2s'")
    start = time.time()
    try:
        c.query(ddl)
        print("  the build finished (unexpected)")
    except wire.PgError as e:
        print(f"  build while a REPEATABLE READ transaction is open: failed after {time.time() - start:.1f} s ({e.fields['C']} {e.fields['M']})")
    report.query("COMMIT")
    report.close()
    print(f"  index left behind, valid: {c.query(index_state)[0][0]}")
    c.query(ddl)
    print(f"  the same migration again (IF NOT EXISTS): succeeds; index valid: {c.query(index_state)[0][0]}; "
          f"plan: {scan(c.query(plan))}")
    c.query("DROP INDEX CONCURRENTLY orders_restaurant_created")
    c.query(ddl.replace(" IF NOT EXISTS", ""))
    print(f"  after DROP INDEX CONCURRENTLY and a fresh build: valid: {c.query(index_state)[0][0]}; "
          f"plan: {scan(c.query(plan))}")
    c.close()


if __name__ == "__main__":
    print("2 M orders; pgbench writes on 8 connections for 25 s; the change runs at 8 s.\n")
    print(f"{'change':46}{'took':>11}{'longest write':>16}{'writes > 1 s':>14}{'throughput':>12}")
    print(f"{'':46}{'':>11}{'':>16}{'':>14}{'vs before':>12}")
    for name, action in CASES:
        run_case(name, action)
    print("\nA concurrent build that cannot finish:")
    failed_concurrent_build()
