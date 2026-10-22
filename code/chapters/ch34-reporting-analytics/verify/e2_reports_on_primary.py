"""Experiment 2: where should reports run? The order service's write load (pgbench, 8 clients) runs for 30 s while
two analysts' queries (daily revenue and active restaurants over all orders) loop either on the order service's own
database or on the reporting copy. The services' cluster and pgbench share one CPU, the reporting cluster has the
other, standing in for separate machines. Then: what a long-running report on the primary does to VACUUM."""
import glob
import os
import re
import subprocess
import tempfile
import threading
import time

import common
from common import q, rpt, src

HERE = os.path.dirname(os.path.abspath(__file__))
REPORT = """SELECT (placed_at AT TIME ZONE 'Asia/Kolkata')::date AS day, count(DISTINCT restaurant_id),
                   count(*), sum(amount_paise)
              FROM orders WHERE status = 'DELIVERED'
             GROUP BY 1"""


def pgbench(seconds, clients=8):
    tmp = tempfile.mkdtemp()
    env = dict(os.environ, PGHOST=common.SRC[0], PGPORT=str(common.SRC[1]))
    p = subprocess.Popen(["taskset", "-c", "0", "pgbench", "-n", "-c", str(clients), "-j", "1", "-T", str(seconds),
                          "-l", "--log-prefix=tx", "-f", os.path.join(HERE, "orders_load.sql"), "orders_svc"],
                         cwd=tmp, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return p, tmp


def latencies(tmp):
    lat = []
    for f in glob.glob(os.path.join(tmp, "tx*")):
        for line in open(f):
            lat.append(int(line.split()[2]) / 1000)
    subprocess.run(["rm", "-rf", tmp])
    lat.sort()
    return lat


def report_loop(target, stop, done):
    c = src("orders_svc") if target == "primary" else rpt("reporting")
    while not stop.is_set():
        c.query(REPORT)
        done.append(1)
    c.close()


def phase(name, target, seconds=30):
    stop, done = threading.Event(), []
    threads = [threading.Thread(target=report_loop, args=(target, stop, done)) for _ in range(2)] if target else []
    for t in threads:
        t.start()
    p, tmp = pgbench(seconds)
    p.wait()
    stop.set()
    for t in threads:
        t.join()
    lat = latencies(tmp)
    print(f"{name:44}{len(lat) / seconds:>10,.0f}{lat[len(lat) // 2]:>10.1f} ms{lat[int(len(lat) * 0.99)]:>10.1f} ms"
          f"{len(done) if target else '':>10}")


def vacuum_case(name, hold_on):
    """A REPEATABLE READ report held open for the whole write burst, on the primary or on the reporting copy;
    then VACUUM on the primary."""
    q(src, "orders_svc", "VACUUM orders")
    holder = src("orders_svc") if hold_on == "primary" else rpt("reporting")
    holder.query("BEGIN ISOLATION LEVEL REPEATABLE READ")
    holder.query("SELECT count(*) FROM orders")
    p, tmp = pgbench(30)
    p.wait()
    latencies(tmp)
    c = src("orders_svc")
    c.query("VACUUM orders")                       # n_dead_tup afterwards: the dead rows it could not remove
    dead = q(src, "orders_svc", "SELECT n_dead_tup FROM pg_stat_user_tables WHERE relname = 'orders'")[0][0]
    holder.query("COMMIT")
    holder.close()
    c.query("VACUUM orders")
    after = q(src, "orders_svc", "SELECT n_dead_tup FROM pg_stat_user_tables WHERE relname = 'orders'")[0][0]
    c.close()
    print(f"{name:44}{int(dead):>22,}{int(after):>20,}")


def main():
    os.sched_setaffinity(0, {1})                   # this driver (and the report loops' clients) off the services' CPU
    common.build_sources()
    common.build_reporting()
    print("Order service writes (pgbench, 8 clients, 30 s) while two report loops run elsewhere or not at all.\n")
    print(f"{'reports run on':44}{'writes/s':>10}{'p50':>13}{'p99':>13}{'reports':>10}")
    phase("nowhere (baseline)", None)
    phase("the order service's primary", "primary")
    phase("the reporting copy (other machine)", "reporting")
    phase("nowhere (baseline, again)", None)
    print("\nA report transaction held open for 30 s of writes, then VACUUM on the order service's database.\n")
    print(f"{'report held open on':44}{'dead rows left by VACUUM':>22}{'after it ends':>20}")
    vacuum_case("the order service's primary", "primary")
    vacuum_case("the reporting copy", "reporting")


if __name__ == "__main__":
    main()
