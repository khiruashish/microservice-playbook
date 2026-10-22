"""Chapter 30 experiments: one order = debit a wallet and take a portion of a dish from stock (payments/kitchen
database, cluster B) and record the order (order database, cluster A). Four ways to make that consistent:

  local   everything in one database, one transaction (the monolith baseline)
  2pc     PostgreSQL PREPARE TRANSACTION / COMMIT PREPARED on both databases, coordinated by this client,
          with a decision log (tm_log) as a transaction manager keeps
  saga    orchestrated: order PENDING (A), debit + stock in one local transaction (B), order CONFIRMED (A)
  outbox  the request writes order PENDING + an outbox row (A) and returns; workers apply it to B and confirm

A network round trip is simulated as a pause of RTT_MS (default 1 ms) before every statement, so locks are held
across round trips exactly as they would be across a network. Every B transaction runs with lock_timeout = 2 s.

  PGHOST_A/PGPORT_A, PGHOST_B/PGPORT_B   the two clusters (PGUSER for both; trust authentication)
  B_STOP / B_START / B_RESTART            shell commands controlling cluster B (outage and restart experiments)
  python3 experiment.py throughput | indoubt | outage | all
"""
import itertools
import os
import random
import statistics
import subprocess
import sys
import threading
import time

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
A = dict(host=os.environ.get("PGHOST_A", "localhost"), port=os.environ.get("PGPORT_A", "5432"))
B = dict(host=os.environ.get("PGHOST_B", "localhost"), port=os.environ.get("PGPORT_B", "5433"))
RTT = float(os.environ.get("RTT_MS", "1.0")) / 1000
THREADS = int(os.environ.get("THREADS", "8"))
AMOUNT = 25_000


class Conn:
    """A lazily (re)connected session with the simulated round trip before each statement."""

    def __init__(self, cfg, db):
        self.cfg, self.db, self.pg = cfg, db, None

    def q(self, sql):
        if RTT:
            time.sleep(RTT)
        if self.pg is None:
            self.pg = wire.Pg(self.db, **self.cfg)
            self.pg.query("SET lock_timeout = '2s'")
        try:
            return self.pg.query(sql)
        except (OSError, ConnectionError):
            self.drop()
            raise

    def quiet(self, sql):
        """Best effort (cleanup after a failure)."""
        try:
            return self.q(sql)
        except Exception:
            return None

    def drop(self):
        if self.pg is not None:
            try:
                self.pg.sock.close()
            except OSError:
                pass
        self.pg = None


def admin(cfg, db, sql):
    pg = wire.Pg(db, **cfg)
    try:
        return pg.query(sql)
    finally:
        pg.close()


def wait_for(cfg, seconds=30):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            admin(cfg, "postgres", "SELECT 1")
            return
        except Exception:
            time.sleep(0.2)
    raise RuntimeError("database did not come back")


def setup(strategy):
    for cfg, db in ((A, "c30a"), (B, "c30b"), (A, "c30local")):
        if not admin(cfg, "postgres", f"SELECT 1 FROM pg_database WHERE datname = '{db}'"):
            admin(cfg, "postgres", f"CREATE DATABASE {db}")
    for cfg, db in ((A, "c30a"), (B, "c30b")):
        for (gid,) in admin(cfg, db, "SELECT gid FROM pg_prepared_xacts WHERE database = current_database()"):
            admin(cfg, db, f"ROLLBACK PREPARED '{gid}'")
    if strategy == "local":
        admin(A, "c30local", open(os.path.join(HERE, "schema_local.sql")).read())
    else:
        admin(A, "c30a", open(os.path.join(HERE, "schema_orders.sql")).read())
        admin(B, "c30b", open(os.path.join(HERE, "schema_payments.sql")).read())


# --- the four strategies: each places one order and returns its outcome ----------------------------------------
class Session:
    def __init__(self):
        self.a, self.b, self.tm = Conn(A, "c30a"), Conn(B, "c30b"), Conn(A, "c30a")
        self.local = Conn(A, "c30local")


def b_work(s, oid, c, i):
    """Returns the time the stock row's lock was acquired."""
    s.b.q(f"UPDATE stock SET available = available - 1 WHERE item_id = {i}")
    t = time.perf_counter()
    s.b.q(f"UPDATE wallets SET balance = balance - {AMOUNT} WHERE customer_id = {c}")
    s.b.q(f"INSERT INTO debits (order_id, customer_id, item_id, amount) VALUES ({oid}, {c}, {i}, {AMOUNT})")
    return t


def place_local(s, oid, c, i, crash, locks):
    L = s.local
    try:
        L.q("BEGIN")
        L.q(f"UPDATE stock SET available = available - 1 WHERE item_id = {i}")
        t = time.perf_counter()
        L.q(f"UPDATE wallets SET balance = balance - {AMOUNT} WHERE customer_id = {c}")
        L.q(f"INSERT INTO debits (order_id, customer_id, item_id, amount) VALUES ({oid}, {c}, {i}, {AMOUNT})")
        L.q(f"INSERT INTO orders (id, customer_id, item_id, amount, status, confirmed_at) "
            f"VALUES ({oid}, {c}, {i}, {AMOUNT}, 'CONFIRMED', clock_timestamp())")
        L.q("COMMIT")
        locks.append(time.perf_counter() - t)
        return "ok"
    except wire.PgError as e:
        L.quiet("ROLLBACK")
        return "lock timeout" if e.fields.get("C") == "55P03" else "failed"
    except (OSError, ConnectionError):
        return "unreachable"


def place_2pc(s, oid, c, i, crash, locks):
    gid = f"order-{oid}"
    prepared_a = prepared_b = False
    try:
        s.a.q("BEGIN")
        s.a.q(f"INSERT INTO orders (id, customer_id, item_id, amount, status, confirmed_at) "
              f"VALUES ({oid}, {c}, {i}, {AMOUNT}, 'CONFIRMED', clock_timestamp())")
        s.b.q("BEGIN")
        t = b_work(s, oid, c, i)
        s.a.q(f"PREPARE TRANSACTION '{gid}'")
        prepared_a = True
        s.b.q(f"PREPARE TRANSACTION '{gid}'")
        prepared_b = True
        if crash.take():
            return "crashed"                                     # the coordinator dies: both participants in doubt
        s.tm.q(f"INSERT INTO tm_log (gid, decision) VALUES ('{gid}', 'COMMIT')")   # the decision is durable here
    except (wire.PgError, OSError, ConnectionError) as e:
        # before the decision: abort everywhere we can; what we can't reach stays in doubt for recovery
        for conn, prepared in ((s.a, prepared_a), (s.b, prepared_b)):
            conn.quiet(f"ROLLBACK PREPARED '{gid}'" if prepared else "ROLLBACK")
        if isinstance(e, wire.PgError):
            return "lock timeout" if e.fields.get("C") == "55P03" else "failed"
        return "unreachable"
    # after the decision: commit everywhere, retrying for as long as it takes
    for conn in (s.a, s.b):
        while True:
            try:
                conn.q(f"COMMIT PREPARED '{gid}'")
                break
            except wire.PgError as e:
                if e.fields.get("C") == "42704":                  # already resolved by recovery
                    break
                raise
            except (OSError, ConnectionError):
                time.sleep(0.1)
        if conn is s.b:
            locks.append(time.perf_counter() - t)
    s.tm.quiet(f"DELETE FROM tm_log WHERE gid = '{gid}'")
    return "ok"


def place_saga(s, oid, c, i, crash, locks):
    try:
        s.a.q(f"INSERT INTO orders (id, customer_id, item_id, amount, status) VALUES ({oid}, {c}, {i}, {AMOUNT}, 'PENDING')")
    except (wire.PgError, OSError, ConnectionError):
        return "failed"
    try:
        s.b.q("BEGIN")
        t = b_work(s, oid, c, i)
    except (wire.PgError, OSError, ConnectionError) as e:
        s.b.quiet("ROLLBACK")                                  # nothing was committed in B: reject the order
        s.a.quiet(f"UPDATE orders SET status = 'REJECTED' WHERE id = {oid}")
        if isinstance(e, wire.PgError):
            return "lock timeout" if e.fields.get("C") == "55P03" else "failed"
        return "unreachable"
    try:
        s.b.q("COMMIT")
        locks.append(time.perf_counter() - t)
    except (OSError, ConnectionError):
        return "unknown"                                       # the reply to COMMIT was lost: leave it PENDING for recovery
    if crash.take():
        return "crashed"                                         # the orchestrator dies: the order stays PENDING
    s.a.quiet(f"UPDATE orders SET status = 'CONFIRMED', confirmed_at = clock_timestamp() WHERE id = {oid} AND status = 'PENDING'")
    return "ok"


def place_outbox(s, oid, c, i, crash, locks):
    try:
        s.a.q("BEGIN")
        s.a.q(f"INSERT INTO orders (id, customer_id, item_id, amount, status) VALUES ({oid}, {c}, {i}, {AMOUNT}, 'PENDING')")
        s.a.q(f"INSERT INTO order_outbox (order_id, customer_id, item_id, amount) VALUES ({oid}, {c}, {i}, {AMOUNT})")
        s.a.q("COMMIT")
        return "accepted"
    except (wire.PgError, OSError, ConnectionError):
        s.a.quiet("ROLLBACK")
        return "failed"


class OutboxWorker(threading.Thread):
    """Claims up to 20 outbox rows and applies them to B in one transaction: an idempotent insert of the debits,
    then the stock and wallet rows locked in key order (no deadlocks between workers) and updated once per row.
    If the batch fails for a business reason (a CHECK constraint), the orders are applied one by one so only the
    offending one is rejected. Then the orders are confirmed in A, in the transaction that claimed them."""

    def __init__(self, stop, locks):
        super().__init__(daemon=True)
        self.stop, self.locks = stop, locks
        self.s = Session()

    def apply_batch(self, rows):
        s = self.s
        s.b.q("BEGIN")
        values = ",".join(f"({oid}, {c}, {i}, {AMOUNT})" for oid, c, i in rows)
        new = s.b.q(f"INSERT INTO debits (order_id, customer_id, item_id, amount) VALUES {values} "
                    f"ON CONFLICT (order_id) DO NOTHING RETURNING order_id, customer_id, item_id")
        if new:
            per_item, per_customer = {}, {}
            for _, c, i in new:
                per_item[int(i)] = per_item.get(int(i), 0) + 1
                per_customer[int(c)] = per_customer.get(int(c), 0) + AMOUNT
            s.b.q(f"SELECT 1 FROM stock WHERE item_id IN ({','.join(map(str, per_item))}) ORDER BY item_id FOR UPDATE")
            t = time.perf_counter()
            s.b.q("UPDATE stock s SET available = s.available - d.n FROM (VALUES "
                  + ",".join(f"({k}, {v})" for k, v in per_item.items()) + ") d(item_id, n) WHERE s.item_id = d.item_id")
            s.b.q(f"SELECT 1 FROM wallets WHERE customer_id IN ({','.join(map(str, per_customer))}) ORDER BY customer_id FOR UPDATE")
            s.b.q("UPDATE wallets w SET balance = w.balance - d.amount FROM (VALUES "
                  + ",".join(f"({k}, {v})" for k, v in per_customer.items()) + ") d(customer_id, amount) WHERE w.customer_id = d.customer_id")
            s.b.q("COMMIT")
            self.locks.append(time.perf_counter() - t)
        else:
            s.b.q("COMMIT")

    def apply_one(self, oid, c, i):
        s = self.s
        try:
            s.b.q("BEGIN")
            if s.b.q(f"INSERT INTO debits (order_id, customer_id, item_id, amount) VALUES ({oid}, {c}, {i}, {AMOUNT}) "
                     f"ON CONFLICT (order_id) DO NOTHING RETURNING order_id"):
                s.b.q(f"UPDATE stock SET available = available - 1 WHERE item_id = {i}")
                s.b.q(f"UPDATE wallets SET balance = balance - {AMOUNT} WHERE customer_id = {c}")
            s.b.q("COMMIT")
            return True
        except wire.PgError as e:
            s.b.quiet("ROLLBACK")
            if e.fields.get("C") == "55P03":
                raise ConnectionError("lock timeout: retry the batch later")
            return False

    def run(self):
        s = self.s
        while True:
            try:
                s.a.q("BEGIN")
                rows = s.a.q("""DELETE FROM order_outbox WHERE id IN
                                  (SELECT id FROM order_outbox ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED)
                                RETURNING order_id, customer_id, item_id""")
                if not rows:
                    s.a.q("COMMIT")
                    if self.stop.is_set():
                        return
                    time.sleep(0.01)
                    continue
                try:
                    self.apply_batch(rows)
                    confirmed, rejected = [r[0] for r in rows], []
                except wire.PgError as e:
                    s.b.quiet("ROLLBACK")
                    if e.fields.get("C") == "55P03":
                        raise
                    confirmed, rejected = [], []
                    for oid, c, i in rows:
                        (confirmed if self.apply_one(oid, c, i) else rejected).append(oid)
                if confirmed:
                    s.a.q(f"UPDATE orders SET status = 'CONFIRMED', confirmed_at = clock_timestamp() WHERE id IN ({','.join(confirmed)})")
                if rejected:
                    s.a.q(f"UPDATE orders SET status = 'REJECTED' WHERE id IN ({','.join(rejected)})")
                s.a.q("COMMIT")
            except (wire.PgError, OSError, ConnectionError):
                s.a.quiet("ROLLBACK")                           # the claimed rows return to the outbox
                time.sleep(0.2)


# --- running a workload --------------------------------------------------------------------------------------
class Crash:
    def __init__(self, at=None):
        self.at, self.lock, self.done, self.t0 = at, threading.Lock(), False, None

    def take(self):
        if self.at is None or self.done:
            return False
        with self.lock:
            if not self.done and time.monotonic() - self.t0 >= self.at:
                self.done = True
                return True
        return False


STRATEGIES = {"local": place_local, "2pc": place_2pc, "saga": place_saga, "outbox": place_outbox}


def run(strategy, seconds, items, crash_at=None, events=()):
    """events: [(t, fn)] run on the timeline. Returns [(t_done, outcome, latency)], lock holds, the start time."""
    setup(strategy)
    ids = itertools.count(1)
    id_lock = threading.Lock()
    results, locks = [], []
    stop = threading.Event()
    crash = Crash(crash_at)
    t0 = crash.t0 = time.monotonic()
    workers = []
    if strategy == "outbox":
        workers = [OutboxWorker(stop, locks) for _ in range(4)]
        [w.start() for w in workers]

    def client(n):
        rng = random.Random(n)
        s = Session()
        while time.monotonic() - t0 < seconds:
            with id_lock:
                oid = next(ids)
            start = time.monotonic()
            try:
                out = STRATEGIES[strategy](s, oid, rng.randint(1, 10_000), rng.randint(1, items), crash, locks)
            except Exception as e:                    # never lose a client thread silently
                out = f"error {type(e).__name__}: {e}"
                s = Session()
            end = time.monotonic()
            results.append((end - t0, out, end - start))
            if out in ("unreachable", "failed"):
                time.sleep(0.05)

    threads = [threading.Thread(target=client, args=(n,)) for n in range(THREADS)]
    [t.start() for t in threads]
    for at, fn in sorted(events, key=lambda e: e[0]):
        time.sleep(max(0.0, t0 + at - time.monotonic()))
        fn()
    [t.join() for t in threads]
    stop.set()
    [w.join() for w in workers]
    return results, locks, t0


def recover(strategy):
    """What an operator or a recovery job does afterwards. Returns a description."""
    if strategy == "2pc":
        decided = {g for (g,) in admin(A, "c30a", "SELECT gid FROM tm_log")}
        n = {"committed": 0, "rolled back": 0}
        for cfg, db in ((A, "c30a"), (B, "c30b")):
            # only transactions prepared more than 5 s ago: a live coordinator may be between PREPARE and its log write
            for (gid,) in admin(cfg, db, "SELECT gid FROM pg_prepared_xacts WHERE database = current_database() "
                                         "AND prepared < now() - interval '5 seconds'"):
                if gid in decided:
                    admin(cfg, db, f"COMMIT PREPARED '{gid}'")
                    n["committed"] += 1
                else:
                    admin(cfg, db, f"ROLLBACK PREPARED '{gid}'")       # presumed abort: no decision was logged
                    n["rolled back"] += 1
        admin(A, "c30a", "DELETE FROM tm_log")
        return f"in-doubt transactions resolved: {n['committed']} committed, {n['rolled back']} rolled back"
    if strategy == "saga":
        # only orders pending for more than 5 s: younger ones still have a live orchestrator
        pending = admin(A, "c30a", "SELECT id FROM orders WHERE status = 'PENDING' AND created_at < now() - interval '5 seconds'")
        done = 0
        for (oid,) in pending:
            debited = admin(B, "c30b", f"SELECT 1 FROM debits WHERE order_id = {oid} AND status = 'DEBITED'")
            admin(A, "c30a", f"UPDATE orders SET status = '{'CONFIRMED' if debited else 'REJECTED'}', "
                             f"confirmed_at = clock_timestamp() WHERE id = {oid} AND status = 'PENDING'")
            done += 1
        return f"saga recovery finished {done} PENDING orders"
    return ""


def consistency(strategy):
    if strategy == "local":
        db = "c30local"
        conf = {r[0] for r in admin(A, db, "SELECT id FROM orders WHERE status = 'CONFIRMED'")}
        deb = {r[0] for r in admin(A, db, "SELECT order_id FROM debits WHERE status = 'DEBITED'")}
        money = admin(A, db, f"SELECT 10000::bigint * 1000000000 - sum(balance) FROM wallets")[0][0]
        pending = 0
    else:
        conf = {r[0] for r in admin(A, "c30a", "SELECT id FROM orders WHERE status = 'CONFIRMED'")}
        deb = {r[0] for r in admin(B, "c30b", "SELECT order_id FROM debits WHERE status = 'DEBITED'")}
        money = admin(B, "c30b", "SELECT 10000::bigint * 1000000000 - sum(balance) FROM wallets")[0][0]
        pending = int(admin(A, "c30a", "SELECT count(*) FROM orders WHERE status = 'PENDING'")[0][0])
    charged_ok = int(money) == len(deb) * AMOUNT
    return len(conf), len(conf - deb), len(deb - conf), pending, charged_ok


def pct(values, p):
    if not values:
        return float("nan")
    values = sorted(values)
    return values[min(len(values) - 1, int(p / 100 * len(values)))]


# --- experiment 1: throughput and lock holding ---------------------------------------------------------------
def throughput():
    print(f"{THREADS} clients, 15 s per run, simulated round trip {RTT * 1000:.1f} ms before every statement\n")
    print(f"{'strategy':10}{'dishes':>8}{'orders/s':>10}{'p50 ms':>9}{'p99 ms':>9}{'failed':>8}"
          f"{'B lock held p50':>17}{'confirmed after p50/p99 ms':>32}   consistent")
    for items, label in ((10_000, "10,000"), (2, "2 hot")):
        for strategy in ("local", "2pc", "saga", "outbox"):
            results, locks, t0 = run(strategy, 15, items)
            wall0 = time.time() - (time.monotonic() - t0)
            ok = [r for r in results if r[1] in ("ok", "accepted")]
            failed = len(results) - len(ok)
            confirm = ""
            if strategy == "outbox":
                lat = [float(x[0]) for x in admin(A, "c30a", "SELECT extract(epoch FROM confirmed_at - created_at) * 1000 "
                                                              "FROM orders WHERE confirmed_at IS NOT NULL")]
                in_time = admin(A, "c30a", f"SELECT count(*) FROM orders WHERE confirmed_at <= to_timestamp({wall0 + 15})")[0][0]
                confirm = f"{pct(lat, 50):.0f} / {pct(lat, 99):.0f} ({int(in_time) / 15:.0f}/s)"
            conf, no_debit, no_order, pending, money = consistency(strategy)
            consistent = "yes" if (no_debit, no_order, pending, money) == (0, 0, 0, True) else \
                f"NO: {no_debit} without debit, {no_order} debits without order, {pending} pending"
            print(f"{strategy:10}{label:>8}{len(ok) / 15:>10.0f}{pct([r[2] for r in ok], 50) * 1000:>9.1f}"
                  f"{pct([r[2] for r in ok], 99) * 1000:>9.1f}{failed:>8}{statistics.median(locks) * 1000 if locks else 0:>14.1f} ms"
                  f"{confirm:>32}   {consistent}")
            sys.stdout.flush()


# --- experiment 2: the coordinator dies after PREPARE ----------------------------------------------------------
def b_ctl(var):
    cmd = os.environ.get(var)
    if not cmd:
        raise SystemExit(f"set {var} to a shell command that controls cluster B")
    subprocess.run(cmd, shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def buckets(results, edges):
    rows = []
    for lo, hi in zip(edges, edges[1:]):
        part = [r for r in results if lo <= r[0] < hi]
        ok = sum(1 for r in part if r[1] in ("ok", "accepted"))
        lt = sum(1 for r in part if r[1] == "lock timeout")
        un = len(part) - ok - lt
        rows.append((lo, hi, ok / (hi - lo), lt, un))
    return rows


def indoubt():
    print("2 hot dishes, 30 s. At 5 s the coordinator (2PC) or orchestrator (saga) dies right after the prepare / "
          "the payment step of one order.\nAt 12 s cluster B restarts. At 20 s the recovery job runs.\n")
    notes = []

    def show_prepared(label):
        rows = admin(B, "c30b", "SELECT gid, prepared FROM pg_prepared_xacts")
        locks = admin(B, "c30b", "SELECT count(*) FROM pg_locks WHERE pid IS NULL")      # locks of prepared transactions
        notes.append(f"  {label}: {len(rows)} prepared transaction(s) on B {[r[0] for r in rows]}, holding {locks[0][0]} locks")

    for strategy in ("2pc", "saga"):
        notes.clear()
        events = [(11.5, lambda: show_prepared("before the restart")), (12, lambda: b_ctl("B_RESTART")),
                  (14, lambda: show_prepared("after the restart")),
                  (20, lambda: notes.append("  recovery: " + recover(strategy)))]
        if strategy == "saga":
            events = [(12, lambda: b_ctl("B_RESTART")), (20, lambda: notes.append("  recovery: " + recover(strategy)))]
        results, _, _ = run(strategy, 30, 2, crash_at=5, events=events)
        print(f"{strategy}: orders/s, lock timeouts, unreachable per 5 s")
        for lo, hi, rate, lt, un in buckets(results, [0, 5, 10, 15, 20, 25, 30]):
            print(f"  {lo:>2}-{hi:<2} s {rate:>8.0f}/s {lt:>8} lock timeouts {un:>6} unreachable")
        for n in notes:
            print(n)
        unknown = sum(1 for r in results if r[1] == "unknown")
        errors = sum(1 for r in results if r[1].startswith("error"))
        if unknown or errors:
            print(f"  outcome unknown (connection lost at COMMIT, left PENDING for recovery): {unknown}; client errors: {errors}")
        conf, no_debit, no_order, pending, money = consistency(strategy)
        print(f"  afterwards: {conf} confirmed, {no_debit} without a debit, {no_order} debits without an order, "
              f"{pending} pending, money matches debits: {money}\n")
        sys.stdout.flush()


# --- experiment 3: the payments database is down for 10 s ------------------------------------------------------
def outage():
    print("10,000 dishes, 30 s; cluster B is stopped at 10 s and started at 20 s\n")
    for strategy in ("2pc", "saga", "outbox"):
        marks = {}

        def down():
            b_ctl("B_STOP")
            marks["down"] = time.monotonic()

        def up():
            b_ctl("B_START")
            wait_for(B)
            marks["up"] = time.monotonic()

        results, _, t0 = run(strategy, 30, 10_000, events=[(10, down), (20, up)])
        prepared_left = admin(B, "c30b", "SELECT count(*) FROM pg_prepared_xacts")[0][0]
        rec = recover(strategy)
        print(f"{strategy}:")
        for lo, hi, rate, lt, un in buckets(results, [0, 10, 20, 30]):
            print(f"  {lo:>2}-{hi:<2} s {rate:>8.0f} orders/s accepted {un:>7} refused or failed")
        stuck = [r for r in results if r[1] == "ok" and r[2] > 1.0]
        if stuck:
            print(f"  {len(stuck)} orders took over 1 s: committed in A, waiting to commit in B (max {max(r[2] for r in stuck):.1f} s)")
        if strategy == "2pc":
            print(f"  prepared transactions on B after the outage: {prepared_left}; {rec}")
        if strategy == "saga" and rec:
            print(f"  {rec}")
        if strategy == "outbox":
            last = admin(A, "c30a", "SELECT extract(epoch FROM max(confirmed_at)) FROM orders")[0][0]
            backlog_conf = admin(A, "c30a", "SELECT count(*), max(extract(epoch FROM confirmed_at - created_at)) FROM orders "
                                            f"WHERE created_at >= to_timestamp({time.time() - (time.monotonic() - t0) + 10}) "
                                            f"AND created_at < to_timestamp({time.time() - (time.monotonic() - t0) + 20})")
            print(f"  orders taken during the outage: {backlog_conf[0][0]}, all confirmed within {float(backlog_conf[0][1]):.1f} s of being placed")
        conf, no_debit, no_order, pending, money = consistency(strategy)
        print(f"  afterwards: {conf} confirmed, {no_debit} without a debit, {no_order} debits without an order, "
              f"{pending} pending, money matches debits: {money}\n")
        sys.stdout.flush()


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("throughput", "all"):
        print("== 1. throughput and lock holding")
        throughput()
    if what in ("indoubt", "all"):
        print("\n== 2. the coordinator dies after PREPARE")
        indoubt()
    if what in ("outage", "all"):
        print("\n== 3. the payments database is down for 10 s")
        outage()
