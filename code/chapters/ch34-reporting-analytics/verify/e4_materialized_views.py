"""Experiment 4: keeping a pre-aggregated table current. A per-restaurant, per-day summary of 2 M+ orders is read
by 4 dashboard clients (point lookups) while 20,000 new delivered orders are folded in, three ways: REFRESH
MATERIALIZED VIEW, REFRESH ... CONCURRENTLY, and an incremental summary table updated by deltas (what the chapter's
projector does per event). For each: how long the update took, and what the readers saw meanwhile."""
import os
import random
import threading
import time

import common
from common import q, rpt

DB = "reporting"
SUMMARY = """SELECT restaurant_id, (placed_at AT TIME ZONE 'Asia/Kolkata')::date AS business_day,
                    count(*) AS delivered, sum(amount_paise) AS revenue
               FROM orders_e4 WHERE status = 'DELIVERED' GROUP BY 1, 2"""


def setup():
    q(rpt, DB, f"""
        DROP MATERIALIZED VIEW IF EXISTS restaurant_day_mv; DROP TABLE IF EXISTS orders_e4, restaurant_day_inc;
        CREATE TABLE orders_e4 AS SELECT * FROM orders WHERE customer_id <> 0;
        ALTER TABLE orders_e4 ADD PRIMARY KEY (id);
        CREATE MATERIALIZED VIEW restaurant_day_mv AS {SUMMARY};
        CREATE UNIQUE INDEX ON restaurant_day_mv (restaurant_id, business_day);
        CREATE TABLE restaurant_day_inc AS {SUMMARY};
        ALTER TABLE restaurant_day_inc ADD PRIMARY KEY (restaurant_id, business_day);""")
    q(rpt, DB, "VACUUM ANALYZE orders_e4, restaurant_day_mv, restaurant_day_inc")      # not in a transaction
    return int(q(rpt, DB, "SELECT count(*) FROM restaurant_day_mv")[0][0])


def new_orders(n=20000):
    q(rpt, DB, f"""
        INSERT INTO orders_e4 (id, restaurant_id, customer_id, status, amount_paise, placed_at, updated_at)
        SELECT (SELECT max(id) FROM orders_e4) + g, 1 + floor(20000 * power(random(), 3))::int, -2, 'DELIVERED',
               15000 + floor(random() * 85000)::int, timestamptz '2025-04-14 12:00+05:30', now()
          FROM generate_series(1, {n}) g""")


INCREMENT = """
    INSERT INTO restaurant_day_inc AS s (restaurant_id, business_day, delivered, revenue)
    SELECT restaurant_id, (placed_at AT TIME ZONE 'Asia/Kolkata')::date, 1, amount_paise
      FROM orders_e4 WHERE id = {id}
    ON CONFLICT (restaurant_id, business_day)
    DO UPDATE SET delivered = s.delivered + 1, revenue = s.revenue + EXCLUDED.revenue"""


def readers(table, stop, out, n=4):
    def run(seed):
        rng = random.Random(seed)
        c = rpt(DB)
        while not stop.is_set():
            r = 1 + int(20000 * rng.random() ** 3)
            t = time.perf_counter()
            c.query(f"SELECT delivered, revenue FROM {table} WHERE restaurant_id = {r} "
                    f"AND business_day = date '2025-04-14' - {rng.randrange(90)}")
            out.append((time.perf_counter(), time.perf_counter() - t))
        c.close()
    threads = [threading.Thread(target=run, args=(i,)) for i in range(n)]
    for t in threads:
        t.start()
    return threads


def case(name, table, action):
    stop, reads = threading.Event(), []
    threads = readers(table, stop, reads)
    time.sleep(3)
    start = time.perf_counter()
    action()
    end = time.perf_counter()
    time.sleep(1)
    stop.set()
    for t in threads:
        t.join()
    during = [lat for t, lat in reads if t >= start and t - lat <= end]
    longest = max(during) if during else 0
    print(f"{name:44}{end - start:>9.2f} s{len(during):>10,}{longest * 1000:>12.0f} ms"
          f"{sum(1 for l in during if l > 0.1):>16,}")


def main():
    os.sched_setaffinity(0, {0})                   # the reporting cluster has CPU 1
    common.build_sources()
    common.build_reporting()
    rows = setup()
    print(f"Summary of {rows:,} restaurant-days; 4 readers do point lookups; 20,000 new delivered orders are folded in.\n")
    print(f"{'update':44}{'took':>11}{'reads':>10}{'longest read':>15}{'reads > 100 ms':>16}")

    new_orders()
    case("REFRESH MATERIALIZED VIEW", "restaurant_day_mv",
         lambda: q(rpt, DB, "REFRESH MATERIALIZED VIEW restaurant_day_mv"))
    new_orders()
    case("REFRESH MATERIALIZED VIEW CONCURRENTLY", "restaurant_day_mv",
         lambda: q(rpt, DB, "REFRESH MATERIALIZED VIEW CONCURRENTLY restaurant_day_mv"))

    first = int(q(rpt, DB, "SELECT max(id) FROM orders_e4")[0][0]) + 1
    new_orders()
    last = int(q(rpt, DB, "SELECT max(id) FROM orders_e4")[0][0])

    def incremental():
        c = rpt(DB)
        for oid in range(first, last + 1):          # one small transaction per change, as events arrive
            c.query(INCREMENT.format(id=oid))
        c.close()
    case("incremental table: one delta per order", "restaurant_day_inc", incremental)
    # the incremental table has seen only the last batch; fold in the first two to compare with the view
    q(rpt, DB, f"""
        INSERT INTO restaurant_day_inc AS s (restaurant_id, business_day, delivered, revenue)
        SELECT restaurant_id, (placed_at AT TIME ZONE 'Asia/Kolkata')::date, count(*), sum(amount_paise)
          FROM orders_e4 WHERE customer_id = -2 AND id < {first}
         GROUP BY 1, 2
        ON CONFLICT (restaurant_id, business_day)
        DO UPDATE SET delivered = s.delivered + EXCLUDED.delivered, revenue = s.revenue + EXCLUDED.revenue""")
    q(rpt, DB, "REFRESH MATERIALIZED VIEW restaurant_day_mv")
    diff = q(rpt, DB, """SELECT count(*) FROM (SELECT * FROM restaurant_day_mv EXCEPT
                                               SELECT * FROM restaurant_day_inc) d""")[0][0]
    print(f"\nincremental summary vs a full recomputation: {diff} rows differ")
    idle = q(rpt, DB, "SELECT count(*) FROM restaurant_day_mv")
    print(f"a view refresh recomputes all {int(idle[0][0]):,} rows however few changed;"
          f" its data is as old as the last refresh")


if __name__ == "__main__":
    main()
