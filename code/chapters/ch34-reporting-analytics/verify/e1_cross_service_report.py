"""Experiment 1: one report that needs three services' data. Revenue net of refunds per restaurant in Bengaluru
over 30 days, top 20 with names. Four ways: composing the services' APIs, a federated query (postgres_fdw) over
their databases, the same SQL on a replicated reporting copy, and a pre-aggregated daily table. For each: time
(median of 3), calls made, rows sent over the wire, and rows the services' own databases had to read.
API calls pay a 0.5 ms round trip; the federated and reporting queries run over local sockets."""
import json
import statistics
import sys
import time
from collections import defaultdict

import common
from common import q, rpt, src

FROM, TO = "2025-03-16", "2025-04-14"            # business days, IST
RTT = 0.0005
PAGE = 1000

REPORT_SQL = f"""
    WITH d AS (SELECT o.restaurant_id, count(*) AS delivered, sum(o.amount_paise) AS gross
                 FROM {{orders}} o JOIN {{restaurants}} r ON r.id = o.restaurant_id
                WHERE r.city = 'BLR' AND o.status = 'DELIVERED'
                  AND o.placed_at >= timestamp '{FROM}' AT TIME ZONE 'Asia/Kolkata'
                  AND o.placed_at <  (date '{TO}' + 1) AT TIME ZONE 'Asia/Kolkata'
                GROUP BY o.restaurant_id),
         f AS (SELECT o.restaurant_id, sum(p.amount_paise) AS refunds
                 FROM {{orders}} o JOIN {{restaurants}} r ON r.id = o.restaurant_id
                 JOIN {{payments}} p ON p.order_id = o.id AND p.kind = 'REFUND'
                WHERE r.city = 'BLR' AND o.status = 'DELIVERED'
                  AND o.placed_at >= timestamp '{FROM}' AT TIME ZONE 'Asia/Kolkata'
                  AND o.placed_at <  (date '{TO}' + 1) AT TIME ZONE 'Asia/Kolkata'
                GROUP BY o.restaurant_id)
    SELECT r.name, d.delivered, d.gross, coalesce(f.refunds, 0), d.gross - coalesce(f.refunds, 0) AS net
      FROM d JOIN {{restaurants}} r ON r.id = d.restaurant_id LEFT JOIN f ON f.restaurant_id = d.restaurant_id
     ORDER BY net DESC, r.name LIMIT 20"""


def source_reads():
    total = 0
    for db in common.SERVICES:
        r = q(src, db, "SELECT tup_returned + tup_fetched FROM pg_stat_database WHERE datname = current_database()")
        total += int(r[0][0])
    return total


class Api:
    """Each method stands for one HTTP call to a service: a query on its own database plus a round trip."""

    def __init__(self):
        self.conns = {db: src(db) for db in common.SERVICES}
        self.calls = 0
        self.rows = 0

    def call(self, db, sql):
        time.sleep(RTT)
        self.calls += 1
        rows = self.conns[db].query(sql)
        self.rows += len(rows)
        return rows

    def close(self):
        for c in self.conns.values():
            c.close()


def api_composition():
    api = Api()
    names, ids, last = {}, [], 0
    while True:                                                      # GET /restaurants?city=BLR&after=...
        page = api.call("restaurants_svc", f"SELECT id, name FROM restaurants WHERE city = 'BLR' AND id > {last} "
                                           f"ORDER BY id LIMIT {PAGE}")
        for i, n in page:
            names[int(i)] = n
            ids.append(int(i))
        if len(page) < PAGE:
            break
        last = int(page[-1][0])
    delivered, gross, order_restaurant = defaultdict(int), defaultdict(int), {}
    for k in range(0, len(ids), 100):                                # GET /orders?restaurantIds=...&from&to
        batch = ",".join(map(str, ids[k:k + 100]))
        last = 0
        while True:
            page = api.call("orders_svc", f"""
                SELECT id, restaurant_id, amount_paise FROM orders
                 WHERE restaurant_id IN ({batch}) AND status = 'DELIVERED'
                   AND placed_at >= timestamp '{FROM}' AT TIME ZONE 'Asia/Kolkata'
                   AND placed_at < (date '{TO}' + 1) AT TIME ZONE 'Asia/Kolkata' AND id > {last}
                 ORDER BY id LIMIT {PAGE}""")
            for oid, rid, amount in page:
                rid = int(rid)
                delivered[rid] += 1
                gross[rid] += int(amount)
                order_restaurant[int(oid)] = rid
            if len(page) < PAGE:
                break
            last = int(page[-1][0])
    refunds = defaultdict(int)
    order_ids = list(order_restaurant)
    for k in range(0, len(order_ids), 500):                          # GET /refunds?orderIds=...
        batch = ",".join(map(str, order_ids[k:k + 500]))
        for oid, amount in api.call("payments_svc", f"SELECT order_id, amount_paise FROM payments "
                                                    f"WHERE kind = 'REFUND' AND order_id IN ({batch})"):
            refunds[order_restaurant[int(oid)]] += int(amount)
    rows = sorted(((names[r], delivered[r], gross[r], refunds[r], gross[r] - refunds[r]) for r in delivered),
                  key=lambda x: (-x[4], x[0]))[:20]
    api.close()
    return [tuple(str(v) for v in row) for row in rows], api.calls, api.rows


def setup_federated():
    q(rpt, "postgres", "DROP DATABASE IF EXISTS federated WITH (FORCE)")
    q(rpt, "postgres", "CREATE DATABASE federated")
    host, port = common.SRC
    stmts = ["CREATE EXTENSION postgres_fdw"]
    for db in common.SERVICES:
        stmts += [f"CREATE SERVER {db} FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host '{host}', port '{port}', "
                  f"dbname '{db}', use_remote_estimate 'true', fetch_size '10000')",
                  f"CREATE USER MAPPING FOR CURRENT_USER SERVER {db} OPTIONS (user 'postgres')"]
    stmts += ["IMPORT FOREIGN SCHEMA public LIMIT TO (restaurants) FROM SERVER restaurants_svc INTO public",
              "IMPORT FOREIGN SCHEMA public LIMIT TO (orders) FROM SERVER orders_svc INTO public",
              "IMPORT FOREIGN SCHEMA public LIMIT TO (payments) FROM SERVER payments_svc INTO public"]
    q(rpt, "federated", ";".join(stmts))


def foreign_rows(sql):
    """Rows the federated query pulled from the services' databases: the Foreign Scans' actual rows."""
    plan = json.loads(q(rpt, "federated", "EXPLAIN (ANALYZE, FORMAT JSON) " + sql)[0][0])

    def walk(node):
        own = node["Actual Rows"] * node.get("Actual Loops", 1) if node["Node Type"] == "Foreign Scan" else 0
        return own + sum(walk(c) for c in node.get("Plans", []))
    return walk(plan[0]["Plan"])


def setup_preaggregated():
    q(rpt, "reporting", f"""
        DROP TABLE IF EXISTS daily_restaurant_net;
        CREATE TABLE daily_restaurant_net AS
        SELECT (o.placed_at AT TIME ZONE 'Asia/Kolkata')::date AS business_day, o.restaurant_id, r.city,
               count(*) AS delivered, sum(o.amount_paise) AS gross, coalesce(sum(f.refunds), 0) AS refunds
          FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
          LEFT JOIN (SELECT order_id, sum(amount_paise) AS refunds FROM payments WHERE kind = 'REFUND'
                      GROUP BY order_id) f ON f.order_id = o.id
         WHERE o.status = 'DELIVERED'
         GROUP BY 1, 2, 3;
        ALTER TABLE daily_restaurant_net ADD PRIMARY KEY (business_day, restaurant_id);
        CREATE INDEX ON daily_restaurant_net (city, business_day);
        ANALYZE daily_restaurant_net;""")


PREAGG_SQL = f"""
    SELECT r.name, sum(d.delivered), sum(d.gross), sum(d.refunds), sum(d.gross) - sum(d.refunds) AS net
      FROM daily_restaurant_net d JOIN restaurants r ON r.id = d.restaurant_id
     WHERE d.city = 'BLR' AND d.business_day BETWEEN '{FROM}' AND '{TO}'
     GROUP BY r.name ORDER BY net DESC, r.name LIMIT 20"""


def timed(fn, runs=3):
    """Median time, last result, and rows read by the services' databases in the last run (net of the
    statistics queries' own reads)."""
    before = source_reads()
    overhead = source_reads() - before
    times, out = [], None
    for _ in range(runs):
        before = source_reads()
        t = time.perf_counter()
        out = fn()
        times.append(time.perf_counter() - t)
        reads = max(source_reads() - before - overhead, 0)
    return statistics.median(times), out, reads


def main():
    common.build_sources()
    common.build_reporting()
    setup_federated()
    setup_preaggregated()
    sql = REPORT_SQL.format(orders="orders", restaurants="restaurants", payments="payments")
    results = []
    t, (rows, calls, sent), reads = timed(api_composition)
    results.append(("compose the three services' APIs", t, calls, sent, reads, rows))
    t, rows, reads = timed(lambda: q(rpt, "federated", sql))
    results.append(("federated query (postgres_fdw)", t, 1, foreign_rows(sql), reads, rows))
    t, rows, reads = timed(lambda: q(rpt, "reporting", sql))
    results.append(("same SQL on the reporting copy", t, 1, 20, reads, rows))
    t, rows, reads = timed(lambda: q(rpt, "reporting", PREAGG_SQL))
    results.append(("pre-aggregated daily table", t, 1, 20, reads, rows))
    truth = results[2][5]
    print(f"Net revenue per restaurant, Bengaluru, {FROM} to {TO} (IST), top 20 with names.\n")
    print(f"{'approach':36}{'time':>10}{'calls':>8}{'rows sent':>12}{'rows read by services':>24}  same answer")
    for name, t, calls, sent, reads, rows in results:
        sent = "" if sent is None else f"{sent:,}"
        print(f"{name:36}{t * 1000:>8.0f} ms{calls:>8,}{sent:>12}{reads:>24,}  {rows == truth}")
    print(f"\ntop row: {truth[0]}")
    sys.stdout.flush()


if __name__ == "__main__":
    main()
