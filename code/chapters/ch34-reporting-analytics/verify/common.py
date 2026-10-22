"""Shared set-up for the chapter 34 experiments: three service databases on a 'source' PostgreSQL (wal_level=logical)
and a reporting database on a second PostgreSQL that copies them by logical replication.
  SRC_PGHOST / SRC_PGPORT   the services' cluster        RPT_PGHOST / RPT_PGPORT   the reporting cluster
Data: 20,000 restaurants in 8 cities, 2,000,000 orders over 90 days ending 14 April 2025 (IST), payments with
captures and ~3% refunds. Restaurant popularity is skewed; order times follow a food-delivery day in IST."""
import os
import time

import wire

SRC = (os.environ.get("SRC_PGHOST", "localhost"), os.environ.get("SRC_PGPORT", "5432"))
RPT = (os.environ.get("RPT_PGHOST", "localhost"), os.environ.get("RPT_PGPORT", "5433"))
SERVICES = ("restaurants_svc", "orders_svc", "payments_svc")
ORDERS = int(os.environ.get("ORDERS", "2000000"))
# share of orders per IST hour (lunch and dinner peaks, some late-night orders)
HOURS = {0: 2, 1: 1, 2: 1, 7: 1, 8: 2, 9: 3, 10: 3, 11: 5, 12: 9, 13: 10, 14: 7, 15: 4, 16: 3, 17: 4, 18: 6,
         19: 9, 20: 11, 21: 10, 22: 6, 23: 3}


def src(db):
    return wire.Pg(db, host=SRC[0], port=SRC[1])


def rpt(db):
    return wire.Pg(db, host=RPT[0], port=RPT[1])


def q(conn_factory, db, sql):
    c = conn_factory(db)
    try:
        return c.query(sql)
    finally:
        c.close()


def conninfo(db):
    host, port = SRC
    return f"host={host} port={port} user={os.environ.get('PGUSER', 'postgres')} dbname={db}"


def build_sources(force=False):
    """The three services' databases, each owned by its service. Skipped if already built."""
    exists = q(src, "postgres", "SELECT count(*) FROM pg_database WHERE datname IN ('restaurants_svc','orders_svc','payments_svc')")
    if exists[0][0] == "3" and not force:
        return
    drop_reporting()                               # its replication slots pin the services' databases
    for db in SERVICES:
        q(src, "postgres", f"DROP DATABASE IF EXISTS {db} WITH (FORCE)")
        q(src, "postgres", f"CREATE DATABASE {db}")
    hours = ",".join(str(h) for h, w in HOURS.items() for _ in range(w))
    q(src, "restaurants_svc", """
        CREATE TABLE restaurants (id bigint PRIMARY KEY, name text NOT NULL, city text NOT NULL);
        INSERT INTO restaurants
        SELECT g, 'Restaurant ' || g,
               (ARRAY['BLR','BLR','BLR','BLR','BLR','BLR','DEL','DEL','DEL','DEL','MUM','MUM','MUM',
                      'HYD','HYD','CHN','CHN','PUN','KOL','AMD'])[1 + floor(random() * 20)::int]
          FROM generate_series(1, 20000) g;""")
    q(src, "orders_svc", f"""
        CREATE TABLE orders (
            id bigint PRIMARY KEY, restaurant_id bigint NOT NULL, customer_id bigint NOT NULL,
            status text NOT NULL, amount_paise bigint NOT NULL, placed_at timestamptz NOT NULL,
            updated_at timestamptz NOT NULL);
        SELECT setseed(0.34);
        INSERT INTO orders
        SELECT g, 1 + floor(20000 * power(random(), 3))::int, 1 + floor(random() * 300000)::int,
               CASE WHEN r < 0.05 THEN 'CANCELLED' ELSE 'DELIVERED' END,
               15000 + floor(random() * 85000)::int, t, t + interval '40 minutes'
          FROM (SELECT g, random() AS r,
                       ((date '2025-04-14' - (floor(random() * 90))::int)::timestamp
                         + make_interval(hours => (ARRAY[{hours}])[1 + floor(random() * {sum(HOURS.values())})::int],
                                         mins => floor(random() * 60)::int))
                         AT TIME ZONE 'Asia/Kolkata' AS t
                  FROM generate_series(1, {ORDERS}) g) s;
        CREATE INDEX orders_restaurant_placed ON orders (restaurant_id, placed_at);
        CREATE INDEX orders_placed ON orders (placed_at);
        CREATE SEQUENCE orders_id_seq START {ORDERS + 1};
        ALTER TABLE orders ALTER COLUMN id SET DEFAULT nextval('orders_id_seq');
        ANALYZE orders;""")
    q(src, "payments_svc", """
        CREATE TABLE payments (id bigserial PRIMARY KEY, order_id bigint NOT NULL, kind text NOT NULL,
                               amount_paise bigint NOT NULL, created_at timestamptz NOT NULL);
        CREATE INDEX payments_order ON payments (order_id);""")
    # the payments service learns orders' amounts from events; here we copy them once
    q(src, "payments_svc", f"""
        CREATE EXTENSION IF NOT EXISTS dblink;
        INSERT INTO payments (order_id, kind, amount_paise, created_at)
        SELECT id, 'CAPTURE', amount_paise, placed_at FROM dblink('{conninfo("orders_svc")}',
               'SELECT id, amount_paise, placed_at FROM orders WHERE status = ''DELIVERED''')
               AS o(id bigint, amount_paise bigint, placed_at timestamptz);
        SELECT setseed(0.35);
        INSERT INTO payments (order_id, kind, amount_paise, created_at)
        SELECT order_id, 'REFUND', amount_paise, created_at + interval '2 hours'
          FROM payments WHERE kind = 'CAPTURE' AND random() < 0.03;
        ANALYZE payments;""")


def build_reporting(force=False):
    """The reporting database: logical-replication copies of the three services' tables. Returns the seconds the
    initial copy took, or None if it already existed."""
    if q(rpt, "postgres", "SELECT count(*) FROM pg_database WHERE datname = 'reporting'")[0][0] == "1" and not force:
        return None
    drop_reporting()
    q(rpt, "postgres", "CREATE DATABASE reporting")
    q(rpt, "reporting", """
        CREATE TABLE restaurants (id bigint PRIMARY KEY, name text NOT NULL, city text NOT NULL);
        CREATE TABLE orders (id bigint PRIMARY KEY, restaurant_id bigint NOT NULL, customer_id bigint NOT NULL,
                             status text NOT NULL, amount_paise bigint NOT NULL, placed_at timestamptz NOT NULL,
                             updated_at timestamptz NOT NULL);
        CREATE TABLE payments (id bigint PRIMARY KEY, order_id bigint NOT NULL, kind text NOT NULL,
                               amount_paise bigint NOT NULL, created_at timestamptz NOT NULL);""")
    start = time.time()
    for db, table in (("restaurants_svc", "restaurants"), ("orders_svc", "orders"), ("payments_svc", "payments")):
        q(src, db, f"DROP PUBLICATION IF EXISTS reporting_pub; CREATE PUBLICATION reporting_pub FOR TABLE {table}")
        q(rpt, "reporting", f"CREATE SUBSCRIPTION sub_{table} CONNECTION '{conninfo(db)}' PUBLICATION reporting_pub "
                            f"WITH (slot_name = 'reporting_{table}')")
    wait_initial_sync()
    took = time.time() - start
    q(rpt, "reporting", """
        CREATE INDEX orders_restaurant_placed ON orders (restaurant_id, placed_at);
        CREATE INDEX orders_placed ON orders (placed_at);
        CREATE INDEX payments_order ON payments (order_id);
        ANALYZE;""")
    return took


def wait_initial_sync():
    while True:
        states = q(rpt, "reporting", "SELECT srsubstate FROM pg_subscription_rel")
        if states and all(s[0] in ("r", "s") for s in states):
            return
        time.sleep(0.5)


def drop_reporting():
    if q(rpt, "postgres", "SELECT count(*) FROM pg_database WHERE datname = 'reporting'")[0][0] == "1":
        for (name,) in q(rpt, "reporting", "SELECT subname FROM pg_subscription"):
            q(rpt, "reporting", f"ALTER SUBSCRIPTION {name} DISABLE")
            q(rpt, "reporting", f"ALTER SUBSCRIPTION {name} SET (slot_name = NONE)")
            q(rpt, "reporting", f"DROP SUBSCRIPTION {name}")
        q(rpt, "postgres", "DROP DATABASE reporting WITH (FORCE)")
    for db in SERVICES:
        if q(src, "postgres", f"SELECT count(*) FROM pg_database WHERE datname = '{db}'")[0][0] == "1":
            for (slot,) in q(src, "postgres", f"SELECT slot_name FROM pg_replication_slots WHERE database = '{db}'"):
                q(src, "postgres", f"SELECT pg_drop_replication_slot('{slot}')")
