"""Runs the reporting service's migration and SQL, as written in the Java sources, against PostgreSQL: the
projector's APPLY (duplicates, late events, refunds, business days), the reports, the materialized view refresh.
Uses database c34svc (recreated).   PGHOST=... PGPORT=... PGUSER=postgres python3 check_service_sql.py"""
import os
import re
import sys

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main")
JAVA = os.path.join(SRC, "java", "dev", "foodfleet", "ch34")
failures = 0


def check(label, ok, detail=""):
    global failures
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'} {label}{'' if ok else '  -> ' + str(detail)}")


def block(path, name):
    text = open(os.path.join(JAVA, path)).read()
    one_line = re.search(r'static final String ' + name + r' = "([^"\n]*)";', text)
    if one_line:
        return one_line.group(1)
    body = re.search(r'static final String ' + name + r' = """\n(.*?)"""', text, re.S).group(1)
    lines = body.split("\n")
    indent = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
    return "\n".join(l[indent:] for l in lines).rstrip()


def sql(statement, **params):
    def literal(v):
        return repr(v) if isinstance(v, int) else "'" + str(v).replace("'", "''") + "'"
    return re.sub(r"(?<!:):([A-Za-z]\w*)", lambda m: literal(params[m.group(1)]), statement)


class Projector:
    """OrderFactProjector.apply: one transaction, the advisory lock, APPLY, PROGRESS."""

    def __init__(self, pg, zone="Asia/Kolkata"):
        self.pg, self.zone = pg, zone
        self.lock = block("projection/OrderFactProjector.java", "LOCK_ORDER")
        self.apply_sql = block("projection/OrderFactProjector.java", "APPLY")
        self.progress = block("projection/OrderFactProjector.java", "PROGRESS")

    def apply(self, order, version, status, amount, placed_at, occurred_at=None, restaurant=7, city="BLR"):
        self.pg.query("BEGIN")
        self.pg.query(sql(self.lock, orderId=order))
        changed, days = self.pg.query(sql(self.apply_sql, orderId=order, version=version, restaurantId=restaurant,
                                          city=city, status=status, amountPaise=amount, placedAt=placed_at,
                                          zone=self.zone))[0]
        self.pg.query(sql(self.progress, occurredAt=occurred_at or placed_at))
        self.pg.query("COMMIT")
        return int(changed)


def main():
    admin = wire.Pg("postgres")
    admin.query("DROP DATABASE IF EXISTS c34svc")
    admin.query("CREATE DATABASE c34svc")
    admin.close()
    pg = wire.Pg("c34svc")
    pg.query("BEGIN")
    pg.query(open(os.path.join(SRC, "resources", "db", "migration", "V1__reporting_store.sql")).read())
    pg.query("COMMIT")
    check("Flyway migration V1 applies", True)
    p = Projector(pg)
    daily = block("report/RevenueReports.java", "DAILY")
    top = block("report/RevenueReports.java", "TOP_RESTAURANTS")
    monthly = block("report/RevenueReports.java", "MONTHLY")

    def day(d0, d1=None, city="BLR"):
        return pg.query(sql(daily, city=city, **{"from": d0, "to": d1 or d0}))

    check("a new order is applied", p.apply(1, 1, "PLACED", 45000, "2025-04-14T07:00:00Z") == 1)
    check("a placed order adds no revenue", day("2025-04-14") == [])
    p.apply(1, 3, "DELIVERED", 45000, "2025-04-14T07:00:00Z")
    check("delivery adds the order", day("2025-04-14") == [("2025-04-14", "1", "45000")], day("2025-04-14"))
    check("a redelivered event is ignored", p.apply(1, 3, "DELIVERED", 45000, "2025-04-14T07:00:00Z") == 0)
    check("an event older than the stored version is ignored", p.apply(1, 2, "PREPARING", 45000, "2025-04-14T07:00:00Z") == 0)
    check("totals unchanged by the duplicate and the late event", day("2025-04-14") == [("2025-04-14", "1", "45000")])

    p.apply(2, 1, "DELIVERED", 30000, "2025-04-14T08:00:00Z", restaurant=9)
    p.apply(2, 2, "REFUNDED", 30000, "2025-04-14T08:00:00Z", restaurant=9)
    check("a refund takes the order back out", day("2025-04-14") == [("2025-04-14", "1", "45000")], day("2025-04-14"))
    check("the refunded restaurant's row is back to zero",
          pg.query("SELECT delivered_orders, revenue_paise FROM daily_restaurant_revenue WHERE restaurant_id = 9") == [("0", "0")])

    p.apply(3, 1, "DELIVERED", 50000, "2025-04-13T20:00:00Z")          # 01:30 on 14 April in Bengaluru
    check("an order at 01:30 IST counts on the IST day, not the UTC one",
          day("2025-04-13", "2025-04-14") == [("2025-04-14", "2", "95000")], day("2025-04-13", "2025-04-14"))

    p.apply(4, 1, "DELIVERED", 70000, "2025-04-14T09:00:00Z", restaurant=8)
    check("top restaurants ranks by revenue",
          pg.query(sql(top, city="BLR", limit=2, **{"from": "2025-04-14", "to": "2025-04-14"})) ==
          [("7", "2", "95000"), ("8", "1", "70000")])

    pg.query("BEGIN")
    pg.query("SET LOCAL lock_timeout = '5s'")
    pg.query("REFRESH MATERIALIZED VIEW CONCURRENTLY city_monthly_revenue")
    pg.query("COMMIT")
    check("the monthly view refreshes CONCURRENTLY", pg.query(monthly) == [("BLR", "2025-04-01", "3", "165000")],
          pg.query(monthly))
    check("freshness is the newest event time applied",
          pg.query("SELECT last_event_at AT TIME ZONE 'UTC' FROM projection_status") == [("2025-04-14 09:00:00",)],
          pg.query("SELECT last_event_at FROM projection_status"))
    pg.close()
    print(f"\n{'all checks passed' if failures == 0 else str(failures) + ' check(s) failed'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
