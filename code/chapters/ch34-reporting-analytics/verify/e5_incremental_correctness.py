"""Experiment 5: keeping daily revenue right from a stream of order events. 20,000 orders over a week (IST
order times); each publishes its state on every change (PLACED, then DELIVERED or CANCELLED; some delivered
orders are later corrected to a new amount or refunded). The stream is delivered the way brokers deliver it: 5% of
events twice, 5% of orders with an older event arriving after a newer one. Four projections, each through SQL on
the reporting cluster, compared with the true totals from the orders' final states:
  counters           add on DELIVERED, subtract on REFUNDED
  counters + dedup   the same, skipping event ids already seen
  state + version    the chapter's projector (OrderFactProjector.APPLY), business day in IST
  ... day in UTC     the same projector with the business day taken in UTC"""
import os
import random
import time
from collections import defaultdict
from datetime import datetime, timedelta, timezone

import common
from common import q, rpt
from check_service_sql import Projector, block, sql

IST = timezone(timedelta(hours=5, minutes=30))
ORDERS = int(os.environ.get("E5_ORDERS", "20000"))


def make_events(seed=34):
    rng = random.Random(seed)
    hours = [h for h, w in common.HOURS.items() for _ in range(w)]
    events, final = [], {}
    for oid in range(1, ORDERS + 1):
        day = datetime(2025, 4, 8, tzinfo=IST) + timedelta(days=rng.randrange(7))
        placed = day + timedelta(hours=rng.choice(hours), minutes=rng.randrange(60))
        rest = 1 + int(500 * rng.random() ** 3)
        amount = rng.randrange(15000, 100000)
        states = [("PLACED", amount, placed)]
        if rng.random() < 0.05:
            states.append(("CANCELLED", amount, placed + timedelta(minutes=10)))
        else:
            states.append(("DELIVERED", amount, placed + timedelta(minutes=40)))
            if rng.random() < 0.02:                          # an item was missing: corrected amount
                amount = amount - rng.randrange(5000, 15000)
                states.append(("DELIVERED", amount, placed + timedelta(hours=2)))
            if rng.random() < 0.03:
                states.append(("REFUNDED", amount, placed + timedelta(hours=3)))
        for v, (status, amt, at) in enumerate(states, 1):
            events.append(dict(id=f"{oid}-{v}", order=oid, version=v, restaurant=rest, status=status, amount=amt,
                               placed=placed, at=at))
        final[oid] = (rest, placed, states[-1][0], states[-1][1])
    events.sort(key=lambda e: e["at"])
    # broker realities: redeliveries, and a newer event overtaking an older one of the same order
    stream = []
    for e in events:
        stream.append(e)
        if rng.random() < 0.05:
            stream.insert(len(stream) + rng.randrange(0, 50), dict(e))          # delivered again, a bit later
    by_order = defaultdict(list)
    for i, e in enumerate(stream):
        by_order[e["order"]].append(i)
    for oid, idx in by_order.items():
        if len(idx) >= 2 and rng.random() < 0.05:
            i, j = idx[-2], idx[-1]
            stream[i], stream[j] = stream[j], stream[i]
    return stream, final


def truth(final, zone):
    days = defaultdict(lambda: [0, 0])
    for rest, placed, status, amount in final.values():
        if status == "DELIVERED":
            d = placed.astimezone(zone).date().isoformat()
            days[(d, str(rest))][0] += 1
            days[(d, str(rest))][1] += amount
    return days


def fresh(pg):
    pg.query("DROP TABLE IF EXISTS order_facts, daily_restaurant_revenue, projection_status, seen_events CASCADE")
    pg.query("DROP MATERIALIZED VIEW IF EXISTS city_monthly_revenue")
    pg.query(open(os.path.join(common.__file__.rsplit("/", 1)[0], "..", "src", "main", "resources", "db", "migration",
                               "V1__reporting_store.sql")).read())
    pg.query("CREATE TABLE seen_events (event_id text PRIMARY KEY)")


COUNT = """INSERT INTO daily_restaurant_revenue AS d (business_day, restaurant_id, city, delivered_orders, revenue_paise)
           VALUES ((timestamptz '{placed}' AT TIME ZONE 'Asia/Kolkata')::date, {rest}, 'BLR', {n}, {amount})
           ON CONFLICT (business_day, restaurant_id) DO UPDATE
           SET delivered_orders = d.delivered_orders + EXCLUDED.delivered_orders,
               revenue_paise = d.revenue_paise + EXCLUDED.revenue_paise"""


def run_counters(pg, stream, dedup):
    for e in stream:
        sign = {"DELIVERED": 1, "REFUNDED": -1}.get(e["status"])
        pg.query("BEGIN")
        if dedup and not pg.query(f"INSERT INTO seen_events VALUES ('{e['id']}') ON CONFLICT DO NOTHING RETURNING 1"):
            pg.query("ROLLBACK")
            continue
        if sign:
            pg.query(COUNT.format(placed=e["placed"].isoformat(), rest=e["restaurant"], n=sign,
                                  amount=sign * e["amount"]))
        pg.query("COMMIT")


def run_projector(pg, stream, zone):
    p = Projector(pg, zone=zone)
    for e in stream:
        p.apply(e["order"], e["version"], e["status"], e["amount"], e["placed"].isoformat(), e["at"].isoformat(),
                restaurant=e["restaurant"], city="BLR")


def compare(pg, expected):
    got = {(d, r): [int(n), int(v)] for d, r, n, v in
           pg.query("SELECT business_day, restaurant_id, delivered_orders, revenue_paise FROM daily_restaurant_revenue")}
    keys = set(expected) | {k for k, v in got.items() if v != [0, 0]}
    wrong = sum(1 for k in keys if got.get(k, [0, 0]) != expected.get(k, [0, 0]))
    total_true = sum(v[1] for v in expected.values())
    total_got = sum(v[1] for v in got.values())
    day_true, day_got = defaultdict(int), defaultdict(int)
    for (d, _), v in expected.items():
        day_true[d] += v[1]
    for (d, _), v in got.items():
        day_got[d] += v[1]
    worst_day = max(abs(day_got[d] - day_true[d]) / day_true[d] for d in day_true)
    return wrong, len(keys), (total_got - total_true) / total_true, worst_day


def main():
    os.sched_setaffinity(0, {0})
    stream, final = make_events()
    dups = len(stream) - len({e["id"] for e in stream})
    print(f"{ORDERS:,} orders, {len(stream):,} events delivered ({dups:,} redeliveries, some out of order).\n")
    print(f"{'projection':22}{'events/s':>10}{'restaurant-days wrong':>24}{'total revenue':>15}{'worst day':>11}")
    q(rpt, "postgres", "DROP DATABASE IF EXISTS c34e5 WITH (FORCE)")
    q(rpt, "postgres", "CREATE DATABASE c34e5")
    ist = truth(final, IST)
    for name, fn in (("counters", lambda pg: run_counters(pg, stream, False)),
                     ("counters + dedup", lambda pg: run_counters(pg, stream, True)),
                     ("state + version", lambda pg: run_projector(pg, stream, "Asia/Kolkata")),
                     ("... day in UTC", lambda pg: run_projector(pg, stream, "UTC"))):
        pg = rpt("c34e5")
        fresh(pg)
        t = time.perf_counter()
        fn(pg)
        rate = len(stream) / (time.perf_counter() - t)
        wrong, keys, total, worst = compare(pg, ist)
        print(f"{name:22}{rate:>10,.0f}{wrong:>14,} of {keys:<7,}{total:>+15.2%}{worst:>11.1%}")
        pg.close()
    late = sum(1 for _, placed, _, _ in final.values() if placed.astimezone(timezone.utc).date() != placed.date())
    print(f"\norders placed between 00:00 and 05:30 IST (a different UTC date): {late / len(final):.1%}")


if __name__ == "__main__":
    main()
