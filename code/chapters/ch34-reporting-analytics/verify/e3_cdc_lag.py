"""Experiment 3: change data capture by logical replication (the mechanism Debezium's PostgreSQL connector also
uses: a publication and a replication slot). The order service writes (pgbench, 8 clients) while probe orders
measure how long a change takes to reach the reporting copy. Then the consumer stops: what the slot retains on the
order service's disk, how stale the copy gets, and how long it takes to catch up. Finally, with a cap on retained
WAL (max_slot_wal_keep_size), what happens when the consumer stays away too long."""
import os
import statistics
import threading
import time

import common
from common import q, rpt, src
from e2_reports_on_primary import pgbench, latencies


def probe_lags(seconds, every=0.25):
    lags = []
    s, r = src("orders_svc"), rpt("reporting")
    end = time.time() + seconds
    while time.time() < end:
        oid = s.query("INSERT INTO orders (restaurant_id, customer_id, status, amount_paise, placed_at, updated_at) "
                      "VALUES (1, 0, 'PROBE', 0, now(), now()) RETURNING id")[0][0]
        t0 = time.time()
        while not r.query(f"SELECT 1 FROM orders WHERE id = {oid}"):
            time.sleep(0.002)
        lags.append(time.time() - t0)
        time.sleep(every)
    s.close(), r.close()
    return sorted(lags)


def retained_mb():
    r = q(src, "orders_svc", "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) / 1048576, wal_status "
                             "FROM pg_replication_slots WHERE slot_name = 'reporting_orders'")
    return float(r[0][0] or 0), r[0][1]


def copy_behind_s():
    newest_src = q(src, "orders_svc", "SELECT extract(epoch FROM max(placed_at)) FROM orders")[0][0]
    newest_rpt = q(rpt, "reporting", "SELECT extract(epoch FROM max(placed_at)) FROM orders")[0][0]
    return float(newest_src) - float(newest_rpt)


def caught_up():
    return q(src, "orders_svc", "SELECT max(id) FROM orders")[0][0] == q(rpt, "reporting", "SELECT max(id) FROM orders")[0][0]


def keep_wal(size):
    q(src, "postgres", f"ALTER SYSTEM SET max_slot_wal_keep_size = '{size}'")    # not in a transaction block
    q(src, "postgres", "SELECT pg_reload_conf()")


def main():
    os.sched_setaffinity(0, {1})
    common.build_sources()
    common.build_reporting()
    keep_wal("1GB")

    print("Steady state: pgbench writes for 30 s; a probe order every 250 ms, timed until it is in the copy.\n")
    p, tmp = pgbench(30)
    lags = probe_lags(28)
    p.wait()
    writes = len(latencies(tmp)) / 30
    print(f"  writes/s {writes:,.0f}; probes {len(lags)}; lag p50 {statistics.median(lags) * 1000:.0f} ms, "
          f"p99 {lags[int(len(lags) * 0.99)] * 1000:.0f} ms, max {lags[-1] * 1000:.0f} ms")

    print("\nThe consumer stops for 60 s while writes continue:\n")
    print(f"  {'after':>6}{'WAL retained for the slot':>28}{'copy behind by':>17}")
    q(rpt, "reporting", "ALTER SUBSCRIPTION sub_orders DISABLE")
    p, tmp = pgbench(60)
    for k in range(6):
        time.sleep(10)
        mb, status = retained_mb()
        print(f"  {(k + 1) * 10:>4} s{mb:>24,.0f} MB{copy_behind_s():>15.0f} s")
    p.wait()
    latencies(tmp)
    t0 = time.time()
    q(rpt, "reporting", "ALTER SUBSCRIPTION sub_orders ENABLE")
    while not caught_up():
        time.sleep(0.2)
    print(f"  re-enabled: caught up in {time.time() - t0:.1f} s; WAL retained afterwards {retained_mb()[0]:,.0f} MB")

    print("\nWith max_slot_wal_keep_size = 64MB, the consumer stops again:\n")
    keep_wal("64MB")
    q(rpt, "reporting", "ALTER SUBSCRIPTION sub_orders DISABLE")
    p, tmp = pgbench(90)
    t0 = time.time()
    status, peak, lost_at = None, 0, None
    while time.time() - t0 < 85:
        time.sleep(2)
        q(src, "postgres", "CHECKPOINT")                 # slots are invalidated when a checkpoint removes WAL
        mb, status = retained_mb()
        peak = max(peak, mb)
        if status == "lost":
            lost_at = time.time() - t0
            break
    p.wait()
    latencies(tmp)
    print(f"  slot status: {status}" + (f" after {lost_at:.0f} s, at most {peak:,.0f} MB retained "
                                        f"(the order service's disk was protected)" if lost_at else ""))
    q(rpt, "reporting", "ALTER SUBSCRIPTION sub_orders ENABLE")
    time.sleep(15)
    print(f"  consumer re-enabled, 15 s later: caught up {caught_up()}, copy behind by {copy_behind_s():.0f} s "
          f"(the consumer keeps failing: the slot can no longer return changes)")
    t0 = time.time()
    q(rpt, "reporting", "ALTER SUBSCRIPTION sub_orders DISABLE; ALTER SUBSCRIPTION sub_orders SET (slot_name = NONE);"
                        "DROP SUBSCRIPTION sub_orders")
    q(src, "postgres", "SELECT pg_drop_replication_slot('reporting_orders')")
    q(rpt, "reporting", "TRUNCATE orders")
    q(rpt, "reporting", f"CREATE SUBSCRIPTION sub_orders CONNECTION '{common.conninfo('orders_svc')}' "
                        f"PUBLICATION reporting_pub WITH (slot_name = 'reporting_orders')")
    common.wait_initial_sync()
    rows = q(rpt, "reporting", "SELECT count(*) FROM orders")[0][0]
    print(f"  rebuilt by a fresh initial copy: {int(rows):,} orders in {time.time() - t0:.1f} s")
    keep_wal("1GB")


if __name__ == "__main__":
    main()
