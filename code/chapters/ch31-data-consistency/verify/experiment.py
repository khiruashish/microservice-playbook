"""Chapter 31 experiments on a PostgreSQL primary with two streaming replicas (asynchronous, hot standby).

  1. read-your-writes   customers update their profile on the primary and read it back; where do reads go?
  2. monotonic reads    a tracking page polls an order's status through a load balancer over two replicas
  3. lost updates       concurrent read-modify-write of loyalty points on the primary, five ways
  4. concurrent writers two regions accept writes to the same cart and replicate to each other (simulation)

  PGHOST / PGUSER                 the socket directory (or host) and user for all three servers
  PRIMARY_PORT, REPLICA1_PORT, REPLICA2_PORT
  python3 experiment.py ryw | monotonic | lost | conflicts | all
A network round trip is simulated by a pause of RTT_MS (default 1 ms) before every statement. Replica lag is
real replication lag; for the "lagging replica" cases recovery_min_apply_delay is set on the replica (it delays
applying each commit, as a replica busy with a long query or far away would)."""
import os
import random
import statistics
import sys
import threading
import time

import wire

HOST = os.environ.get("PGHOST", "localhost")
PORTS = {"primary": os.environ.get("PRIMARY_PORT", "5432"), "replica1": os.environ.get("REPLICA1_PORT", "5433"),
         "replica2": os.environ.get("REPLICA2_PORT", "5434")}
RTT = float(os.environ.get("RTT_MS", "1.0")) / 1000
DB = "c31"


class Conn:
    def __init__(self, server):
        self.server, self.pg = server, None

    def q(self, sql):
        if RTT:
            time.sleep(RTT)
        if self.pg is None:
            self.pg = wire.Pg(DB, host=HOST, port=PORTS[self.server])
        return self.pg.query(sql)


def admin(server, sql, db=DB):
    pg = wire.Pg(db, host=HOST, port=PORTS[server])
    try:
        return pg.query(sql)
    finally:
        pg.close()


def set_delay(replica, delay):
    admin(replica, f"ALTER SYSTEM SET recovery_min_apply_delay = '{delay}'", "postgres")
    admin(replica, "SELECT pg_reload_conf()", "postgres")
    time.sleep(1.5)


def wait_replicas_caught_up():
    lsn = admin("primary", "SELECT pg_current_wal_lsn()")[0][0]
    for r in ("replica1", "replica2"):
        while not admin(r, f"SELECT pg_last_wal_replay_lsn() >= '{lsn}'::pg_lsn")[0][0] == "t":
            time.sleep(0.05)


def setup():
    if not admin("primary", f"SELECT 1 FROM pg_database WHERE datname = '{DB}'", "postgres"):
        admin("primary", f"CREATE DATABASE {DB}", "postgres")
    admin("primary", """DROP TABLE IF EXISTS profiles, order_status, loyalty, noise;
        CREATE TABLE profiles (customer_id int PRIMARY KEY, address text NOT NULL, version int NOT NULL);
        INSERT INTO profiles SELECT c, 'address ' || c, 1 FROM generate_series(1, 10000) c;
        CREATE TABLE order_status (order_id int PRIMARY KEY, step int NOT NULL);
        INSERT INTO order_status SELECT o, 0 FROM generate_series(1, 100) o;
        CREATE TABLE loyalty (customer_id int PRIMARY KEY, points int NOT NULL, version int NOT NULL);
        INSERT INTO loyalty SELECT c, 0, 0 FROM generate_series(1, 50) c;
        CREATE TABLE noise (id int PRIMARY KEY, payload text);
        INSERT INTO noise SELECT i, repeat('x', 200) FROM generate_series(1, 10000) i;""")
    wait_replicas_caught_up()


def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(p / 100 * len(values)))] if values else float("nan")


def background_writes(stop, threads=4):
    """Other traffic on the primary, so replication has something to do."""
    def run(n):
        c, rng = Conn("primary"), random.Random(100 + n)
        while not stop.is_set():
            c.q(f"UPDATE noise SET payload = md5(random()::text) || payload WHERE id = {rng.randint(1, 10000)}")
            c.q(f"UPDATE noise SET payload = left(payload, 200) WHERE id = {rng.randint(1, 10000)}")
    ts = [threading.Thread(target=run, args=(n,), daemon=True) for n in range(threads)]
    [t.start() for t in ts]
    return ts


# --- 1. read-your-writes --------------------------------------------------------------------------------------
class Client:
    """One customer session: mostly reads its own profile, sometimes updates it."""

    def __init__(self, n, strategy):
        self.rng = random.Random(n)
        self.customer = n + 1
        self.strategy = strategy
        self.primary, self.replica = Conn("primary"), Conn("replica1")
        self.written_version = 0        # the version this customer last wrote (what they expect to see)
        self.written_at = -1e9
        self.token = None               # the primary's WAL position after our last write

    def write(self):
        v = int(self.primary.q(f"UPDATE profiles SET address = 'moved {self.rng.random()}', version = version + 1 "
                               f"WHERE customer_id = {self.customer} RETURNING version")[0][0])
        self.written_version, self.written_at = v, time.monotonic()
        if self.strategy == "token":
            self.token = self.primary.q("SELECT pg_current_wal_lsn()")[0][0]

    def read(self):
        """Returns (version seen, served by primary?)."""
        sql = f"SELECT version FROM profiles WHERE customer_id = {self.customer}"
        if self.strategy == "primary":
            return int(self.primary.q(sql)[0][0]), True
        if self.strategy == "replica":
            return int(self.replica.q(sql)[0][0]), False
        if self.strategy.startswith("window"):
            window = float(self.strategy.split(":")[1])
            if time.monotonic() - self.written_at < window:
                return int(self.primary.q(sql)[0][0]), True
            return int(self.replica.q(sql)[0][0]), False
        if self.strategy == "token":
            if self.token is None:
                return int(self.replica.q(sql)[0][0]), False
            deadline = time.monotonic() + 0.05
            while True:
                # First check that the replica has replayed our write, then read in a new statement: a statement's
                # snapshot is taken before its functions run, so checking and reading in one statement can still
                # read a snapshot from before the write.
                if self.replica.q(f"SELECT pg_last_wal_replay_lsn() >= '{self.token}'::pg_lsn")[0][0] == "t":
                    return int(self.replica.q(sql)[0][0]), False
                if time.monotonic() > deadline:
                    return int(self.primary.q(sql)[0][0]), True      # waited 50 ms: ask the primary
                time.sleep(0.005)
        raise ValueError(self.strategy)


def ryw():
    print("32 customer sessions, 20 s per run: each operation is a profile update (1%) or a read of the own profile")
    print("(99%), 10 ms apart; right after an update the profile is read at once (the page reloads).")
    print("A read is stale if it shows an older version than the customer's own last write.\n")
    print(f"{'replica':30}{'strategy':38}{'reads':>8}{'stale':>8}{'stale %':>9}{'from primary':>14}{'p50 ms':>8}{'p99 ms':>8}{'canceled':>10}")
    for delay, label in (("0", "healthy (no delay)"), ("200ms", "lagging (200 ms apply delay)")):
        set_delay("replica1", delay)
        for strategy, name in (("replica", "always the replica"), ("primary", "always the primary"),
                               ("window:1", "primary for 1 s after own write"),
                               ("window:0.1", "primary for 100 ms after own write"),
                               ("token", "replica once it has the write (LSN)")):
            setup()
            stop = threading.Event()
            noise = background_writes(stop)
            stats = {"reads": 0, "stale": 0, "primary": 0, "canceled": 0}
            lat = []
            lock = threading.Lock()
            t_end = time.monotonic() + 20

            def session(n):
                c = Client(n, strategy)
                while time.monotonic() < t_end:
                    if c.rng.random() < 0.01:
                        c.write()                            # ...and the page reloads at once: no pause
                        continue
                    else:
                        t = time.perf_counter()
                        try:
                            seen, from_primary = c.read()
                        except wire.PgError as e:
                            if e.fields.get("C") != "40001":
                                raise
                            with lock:                       # "canceling statement due to conflict with recovery"
                                stats["canceled"] += 1
                            continue
                        dt = time.perf_counter() - t
                        with lock:
                            stats["reads"] += 1
                            stats["stale"] += seen < c.written_version
                            stats["primary"] += from_primary
                            lat.append(dt)
                    time.sleep(0.01)

            ts = [threading.Thread(target=session, args=(n,)) for n in range(32)]
            [t.start() for t in ts]
            [t.join() for t in ts]
            stop.set()
            [t.join() for t in noise]
            r = stats["reads"]
            print(f"{label:30}{name:38}{r:>8}{stats['stale']:>8}{stats['stale'] / r:>9.2%}{stats['primary'] / r:>14.1%}"
                  f"{pct(lat, 50) * 1000:>8.1f}{pct(lat, 99) * 1000:>8.1f}{stats['canceled']:>10}")
            sys.stdout.flush()
    set_delay("replica1", "0")


# --- 2. monotonic reads ---------------------------------------------------------------------------------------
def monotonic():
    print("For 20 s the kitchen advances the status of 100 orders (~800 changes/s on the primary). 16 tracking pages")
    print("each poll one of 10 orders every 10 ms through a load balancer over replica1 (no delay) and replica2")
    print("(300 ms apply delay).")
    print("A read goes backwards if it shows an earlier step of an order than that page has already shown.\n")
    set_delay("replica1", "0")
    set_delay("replica2", "300ms")
    print(f"{'routing':42}{'reads':>8}{'went backwards':>16}{'%':>8}{'avg steps behind':>18}{'from primary':>14}{'canceled':>10}")
    for routing, name in (("random", "random replica per request"), ("sticky", "sticky replica per session"),
                          ("token", "random replica, at least the seen LSN")):
        setup()
        stop = threading.Event()

        def advance():
            c, rng = Conn("primary"), random.Random(7)
            while not stop.is_set():
                c.q(f"UPDATE order_status SET step = step + 1 WHERE order_id = {rng.randint(1, 100)}")
                time.sleep(0.0002)

        writer = threading.Thread(target=advance, daemon=True)
        writer.start()
        stats = {"reads": 0, "back": 0, "behind": 0, "primary": 0, "canceled": 0}
        lock = threading.Lock()
        t_end = time.monotonic() + 20

        def page(n):
            rng = random.Random(n)
            conns = {"replica1": Conn("replica1"), "replica2": Conn("replica2"), "primary": Conn("primary")}
            sticky = rng.choice(["replica1", "replica2"])
            seen, token = {}, "0/0"
            while time.monotonic() < t_end:
                o = rng.randint(1, 10)                     # a page follows a handful of orders
                if routing == "sticky":
                    choices = [sticky]
                else:
                    choices = rng.sample(["replica1", "replica2"], 2)
                step = None
                from_primary = False
                for server in choices:
                    try:
                        if routing == "token":
                            if conns[server].q(f"SELECT pg_last_wal_replay_lsn() >= '{token}'::pg_lsn")[0][0] != "t":
                                continue                   # this replica hasn't caught up with what we've seen: try the other
                            # the position read after the snapshot is at or past it: a safe token for the next read
                            row = conns[server].q(f"SELECT step, pg_last_wal_replay_lsn() FROM order_status WHERE order_id = {o}")
                            step, token = int(row[0][0]), max_lsn(token, row[0][1])
                        else:
                            step = int(conns[server].q(f"SELECT step FROM order_status WHERE order_id = {o}")[0][0])
                    except wire.PgError as e:
                        if e.fields.get("C") != "40001":
                            raise
                        with lock:
                            stats["canceled"] += 1
                        continue
                    break
                if step is None:
                    row = conns["primary"].q(f"SELECT step, pg_current_wal_lsn() FROM order_status WHERE order_id = {o}")
                    step, token, from_primary = int(row[0][0]), max_lsn(token, row[0][1]), True
                with lock:
                    stats["reads"] += 1
                    stats["primary"] += from_primary
                    if step < seen.get(o, 0):
                        stats["back"] += 1
                        stats["behind"] += seen[o] - step
                seen[o] = max(seen.get(o, 0), step)
                time.sleep(0.01)

        ts = [threading.Thread(target=page, args=(n,)) for n in range(16)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        stop.set()
        writer.join()
        r = stats["reads"]
        avg = stats["behind"] / stats["back"] if stats["back"] else 0
        print(f"{name:42}{r:>8}{stats['back']:>16}{stats['back'] / r:>8.2%}{avg:>18.1f}{stats['primary'] / r:>14.1%}{stats['canceled']:>10}")
        sys.stdout.flush()
    set_delay("replica2", "0")


def max_lsn(a, b):
    def n(x):
        hi, lo = x.split("/")
        return (int(hi, 16) << 32) + int(lo, 16)
    return a if n(a) >= n(b) else b


# --- 3. lost updates ------------------------------------------------------------------------------------------
def lost():
    print("16 threads add 10 loyalty points at a time to 50 customers for 10 s, on the primary.\n")
    print(f"{'method':54}{'adds/s':>8}{'points lost':>13}{'retries':>9}{'p99 ms':>8}")
    methods = {
        "read, add in the app, write (READ COMMITTED)": "rmw",
        "atomic UPDATE points = points + 10": "atomic",
        "SELECT ... FOR UPDATE, then write": "for_update",
        "optimistic: UPDATE ... WHERE version = v, retry": "optimistic",
        "read, add, write in REPEATABLE READ, retry": "repeatable",
    }
    for name, method in methods.items():
        setup()
        done = {"adds": 0, "retries": 0}
        lat = []
        lock = threading.Lock()
        t_end = time.monotonic() + 10

        def worker(n):
            c, rng = Conn("primary"), random.Random(n)
            while time.monotonic() < t_end:
                cust = rng.randint(1, 50)
                t = time.perf_counter()
                retries = add_points(c, cust, method)
                with lock:
                    done["adds"] += 1
                    done["retries"] += retries
                    lat.append(time.perf_counter() - t)

        ts = [threading.Thread(target=worker, args=(n,)) for n in range(16)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        total = int(admin("primary", "SELECT sum(points) FROM loyalty")[0][0])
        lost_points = done["adds"] * 10 - total
        print(f"{name:54}{done['adds'] / 10:>8.0f}{lost_points:>13}{done['retries']:>9}{pct(lat, 99) * 1000:>8.1f}")
        sys.stdout.flush()


def add_points(c, cust, method):
    retries = 0
    if method == "atomic":
        c.q(f"UPDATE loyalty SET points = points + 10 WHERE customer_id = {cust}")
        return 0
    if method == "rmw":
        p = int(c.q(f"SELECT points FROM loyalty WHERE customer_id = {cust}")[0][0])
        c.q(f"UPDATE loyalty SET points = {p + 10} WHERE customer_id = {cust}")
        return 0
    if method == "for_update":
        c.q("BEGIN")
        p = int(c.q(f"SELECT points FROM loyalty WHERE customer_id = {cust} FOR UPDATE")[0][0])
        c.q(f"UPDATE loyalty SET points = {p + 10} WHERE customer_id = {cust}")
        c.q("COMMIT")
        return 0
    if method == "optimistic":
        while True:
            p, v = map(int, c.q(f"SELECT points, version FROM loyalty WHERE customer_id = {cust}")[0])
            if c.q(f"UPDATE loyalty SET points = {p + 10}, version = {v + 1} "
                   f"WHERE customer_id = {cust} AND version = {v} RETURNING 1"):
                return retries
            retries += 1
    if method == "repeatable":
        while True:
            try:
                c.q("BEGIN ISOLATION LEVEL REPEATABLE READ")
                p = int(c.q(f"SELECT points FROM loyalty WHERE customer_id = {cust}")[0][0])
                c.q(f"UPDATE loyalty SET points = {p + 10} WHERE customer_id = {cust}")
                c.q("COMMIT")
                return retries
            except wire.PgError as e:
                c.q("ROLLBACK")
                if e.fields.get("C") != "40001":
                    raise
                retries += 1
    raise ValueError(method)


# --- 4. two regions writing the same cart (simulation) ----------------------------------------------------------
def conflicts():
    print("Two regions accept writes to 2,000 shared family carts (20 operations each, family members a few hundred")
    print("ms apart, in either region) and replicate to each other with 50-500 ms lag; region B's clock is off by up")
    print("to the skew. Counted once everything has arrived everywhere:")
    print("  lost add: an added item is missing, though nobody who had seen that add removed it")
    print("  resurrected: a removed item is back, though every add of it had been seen by a remove")
    print("  stale note: the delivery note shown is one that a later writer had already seen and replaced\n")
    print(f"{'clock skew':>11}  {'merge':48}{'regions agree':>15}{'lost adds':>11}{'resurrected':>13}{'stale note':>12}")
    for skew in (0.0, 0.25):
        for strategy, name in (("lww-cart", "last writer wins on the whole cart (wall clock)"),
                               ("lww-item", "last writer wins per item (wall clock)"),
                               ("crdt", "OR-Set per item, note by hybrid logical clock")):
            agree, carts, lost_adds, resurrected, stale = simulate(strategy, skew)
            print(f"{skew * 1000:>9.0f}ms  {name:48}{f'{agree}/{carts}':>15}{lost_adds:>11}{resurrected:>13}{stale:>12}")


def simulate(strategy, skew, carts=2000, ops=20, seed=31):
    rng = random.Random(seed)
    agree = lost_adds = resurrected = stale = 0
    for _ in range(carts):
        offset = {"A": 0.0, "B": rng.uniform(-skew, skew)}
        events, t = [], 0.0
        for k in range(ops):
            t += rng.uniform(0.05, 0.6)
            events.append(dict(t=t, origin=rng.choice("AB"), kind=rng.choices(["add", "remove", "note"], [0.55, 0.3, 0.15])[0],
                               item=f"dish-{rng.randint(1, 6)}", lag=rng.uniform(0.05, 0.5), seq=k))

        def arrival(e, here):
            return e["t"] if e["origin"] == here else e["t"] + e["lag"]

        # Writers act on what their region shows, so events are created in time order and each one records what its
        # writer had seen (the events that had arrived in its region before it).
        for e in events:
            e["seen"] = [x for x in events if x is not e and arrival(x, e["origin"]) < e["t"]]
            e["payload"] = make_payload(strategy, e, offset)
        finals = {}
        for here in "AB":
            arrived = sorted(events, key=lambda e: (arrival(e, here), e["seq"]))
            finals[here] = merge(strategy, arrived)
        agree += finals["A"] == finals["B"]
        items, note = finals["A"]
        for item in {e["item"] for e in events if e["kind"] == "add"}:
            adds = [e for e in events if e["kind"] == "add" and e["item"] == item]
            removes = [e for e in events if e["kind"] == "remove" and e["item"] == item]
            unremoved = [a for a in adds if not any(a in r["seen"] for r in removes)]
            if unremoved and item not in items:
                lost_adds += 1
            if not unremoved and item in items:
                resurrected += 1
        if note is not None:
            chosen = next(e for e in events if e["seq"] == note)
            if any(e["kind"] == "note" and chosen in e["seen"] for e in events):
                stale += 1
    return agree, carts, lost_adds, resurrected, stale


def view(strategy, e):
    """The state the writer of e saw: the events that had arrived in its region, merged."""
    return merge(strategy, sorted(e["seen"], key=lambda x: x["seq"]))


def make_payload(strategy, e, offset):
    stamp = e["t"] + offset[e["origin"]]                         # the writer's wall clock
    if e["kind"] == "note":
        if strategy == "crdt":                                   # hybrid logical clock: never behind what was seen
            seen = [x["payload"]["hlc"] for x in e["seen"] if x["kind"] == "note"]
            top = max(seen) if seen else (-1e9, 0)
            hlc = (stamp, 0) if stamp > top[0] else (top[0], top[1] + 1)
            return {"hlc": hlc + (e["origin"],)}
        return {"stamp": (stamp, e["origin"])}
    if strategy == "lww-cart":                                   # the whole cart as the writer saw it, changed
        items, _ = view(strategy, e)
        items = set(items)
        (items.add if e["kind"] == "add" else items.discard)(e["item"])
        return {"cart": frozenset(items), "stamp": (stamp, e["origin"])}
    if strategy == "lww-item":
        return {"present": e["kind"] == "add", "stamp": (stamp, e["origin"])}
    if strategy == "crdt":                                       # add: a unique tag; remove: the tags it had seen
        if e["kind"] == "add":
            return {"tag": e["seq"]}
        return {"tags": {x["seq"] for x in e["seen"] if x["kind"] == "add" and x["item"] == e["item"]}}
    raise ValueError(strategy)


def merge(strategy, arrived):
    """Applies events in the given order; returns (items, seq of the note shown)."""
    note, note_key = None, None
    if strategy == "lww-cart":
        cart, cart_key = frozenset(), None
    elif strategy == "lww-item":
        state = {}
    else:
        adds, removed = {}, set()
    for e in arrived:
        p = e["payload"]
        if e["kind"] == "note":
            key = p["hlc"] if strategy == "crdt" else p["stamp"]
            if note_key is None or key > note_key:
                note, note_key = e["seq"], key
        elif strategy == "lww-cart":
            if cart_key is None or p["stamp"] > cart_key:
                cart, cart_key = p["cart"], p["stamp"]
        elif strategy == "lww-item":
            if e["item"] not in state or p["stamp"] > state[e["item"]][1]:
                state[e["item"]] = (p["present"], p["stamp"])
        elif e["kind"] == "add":
            adds.setdefault(e["item"], set()).add(p["tag"])
        else:
            removed |= p["tags"]
    if strategy == "lww-cart":
        items = set(cart)
    elif strategy == "lww-item":
        items = {i for i, (present, _) in state.items() if present}
    else:
        items = {i for i, tags in adds.items() if tags - removed}
    return frozenset(items), note


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    for name, fn in (("ryw", ryw), ("monotonic", monotonic), ("lost", lost), ("conflicts", conflicts)):
        if what in (name, "all"):
            print(f"\n== {name}")
            fn()
