"""Chapter 32 experiments: PostgreSQL behind Redis.

  1. what to cache       menu cards (a 43 kB value by primary key) and "top restaurants" (an aggregation)
  2. invalidation        200 menu items read through a cache while prices change: who serves stale prices?
  3. stampede            one expensive key, 100 concurrent clients on 4 instances, a short TTL
  4. write-behind        view counters: write-through vs buffered in Redis, and what a crash loses

  PGHOST/PGPORT/PGUSER (trust auth), REDIS_PORT (this Redis is FLUSHED), RTT_MS (default 0.5: a pause before every
  database and Redis call standing in for the network)
  python3 experiment.py read | invalidation | stampede | writebehind | all"""
import collections
import itertools
import math
import os
import random
import statistics
import sys
import threading
import time

import wire

RTT = float(os.environ.get("RTT_MS", "0.5")) / 1000
DB = "c32"


class Pg:
    def __init__(self):
        self.c = None

    def q(self, sql):
        if RTT:
            time.sleep(RTT)
        if self.c is None:
            self.c = wire.Pg(DB)
        return self.c.query(sql)


class Rd:
    def __init__(self):
        self.c = wire.Redis()

    def __call__(self, *args):
        if RTT:
            time.sleep(RTT)
        return self.c(*args)


def admin(sql, db=DB):
    c = wire.Pg(db)
    try:
        return c.query(sql)
    finally:
        c.close()


def pct(v, p):
    v = sorted(v)
    return v[min(len(v) - 1, int(p / 100 * len(v)))] if v else float("nan")


def setup():
    if not admin(f"SELECT 1 FROM pg_database WHERE datname = '{DB}'", "postgres"):
        admin(f"CREATE DATABASE {DB}", "postgres")
    have = admin("SELECT count(*) FROM pg_tables WHERE tablename IN ('menu_cards', 'reviews')")[0][0]
    if have != "2":
        admin("""DROP TABLE IF EXISTS menu_cards, reviews;
            SELECT setseed(0.32);
            CREATE TABLE menu_cards (restaurant_id int PRIMARY KEY, version int NOT NULL, card text NOT NULL);
            INSERT INTO menu_cards SELECT r, 1, (SELECT string_agg(md5(r::text || g::text), '') FROM generate_series(1, 1340) g)
              FROM generate_series(1, 2000) r;
            CREATE TABLE reviews (restaurant_id int NOT NULL, rating smallint NOT NULL, created_at timestamptz NOT NULL);
            INSERT INTO reviews SELECT 1 + floor(random() * 2000)::int, 1 + floor(random() * 5)::int,
                                       now() - random() * interval '180 days'
              FROM generate_series(1, 1000000);
            CREATE INDEX ON reviews (restaurant_id);""")
        admin("VACUUM ANALYZE menu_cards, reviews")
    admin("""DROP TABLE IF EXISTS items, item_views, flushed_batches;
        CREATE TABLE items (item_id int PRIMARY KEY, price int NOT NULL, version int NOT NULL);
        INSERT INTO items SELECT i, 10000, 1 FROM generate_series(1, 200) i;
        CREATE TABLE item_views (item_id int PRIMARY KEY, views bigint NOT NULL);
        CREATE TABLE flushed_batches (batch_id text PRIMARY KEY, flushed_at timestamptz NOT NULL DEFAULT now());""")
    wire.Redis()("FLUSHALL")


TOP_SQL = """SELECT restaurant_id, round(avg(rating), 2), count(*) FROM reviews
              WHERE created_at > now() - interval '90 days'
              GROUP BY restaurant_id HAVING count(*) >= 200 ORDER BY 2 DESC, 1 LIMIT 20"""


# --- 1. what to cache ------------------------------------------------------------------------------------------
def read():
    print(f"16 clients for 15 s; requests follow a Zipf distribution over 2,000 restaurants; round trip {RTT * 1000:.1f} ms\n")
    weights = list(itertools.accumulate(1 / (k ** 1.0) for k in range(1, 2001)))
    print(f"{'what':16}{'cache':36}{'req/s':>8}{'p50 ms':>8}{'p99 ms':>8}{'DB reads/s':>12}{'hit rate':>10}")
    for what in ("menu card", "top restaurants"):
        for mode, name in (("none", "none"), ("redis", "Redis, cache-aside, TTL 60 s"),
                           ("l1", "in-process (5 s) + Redis (60 s)")):
            if what == "top restaurants" and mode == "l1":
                continue
            setup()
            stats = collections.Counter()
            lat = []
            lock = threading.Lock()
            end = time.monotonic() + 15
            instances = [(collections.OrderedDict(), threading.Lock()) for _ in range(4)]

            def client(n):
                rng, pg, rd = random.Random(n), Pg(), Rd()
                l1, l1_lock = instances[n % 4]
                while time.monotonic() < end:
                    r = rng.choices(range(1, 2001), cum_weights=weights)[0] if what == "menu card" else 0
                    key = f"card:{r}" if what == "menu card" else "top:BLR"
                    t = time.perf_counter()
                    value, src = None, "db"
                    if mode == "l1":
                        with l1_lock:
                            hit = l1.get(key)
                            if hit and hit[1] > time.monotonic():
                                l1.move_to_end(key)
                                value, src = hit[0], "l1"
                    if value is None and mode != "none":
                        value = rd("GET", key)
                        src = "redis" if value is not None else "db"
                    if value is None:
                        rows = pg.q(f"SELECT card FROM menu_cards WHERE restaurant_id = {r}" if what == "menu card" else TOP_SQL)
                        value = rows[0][0] if what == "menu card" else repr(rows)
                        if mode != "none":
                            rd("SET", key, value, "EX", 60)
                    if mode == "l1" and src != "l1":
                        with l1_lock:
                            l1[key] = (value, time.monotonic() + 5)
                            l1.move_to_end(key)
                            if len(l1) > 200:
                                l1.popitem(last=False)
                    dt = time.perf_counter() - t
                    with lock:
                        stats[src] += 1
                        lat.append(dt)

            ts = [threading.Thread(target=client, args=(n,)) for n in range(16)]
            [t.start() for t in ts]
            [t.join() for t in ts]
            total = sum(stats.values())
            hits = total - stats["db"]
            print(f"{what:16}{name:36}{total / 15:>8.0f}{pct(lat, 50) * 1000:>8.1f}{pct(lat, 99) * 1000:>8.1f}"
                  f"{stats['db'] / 15:>12.1f}{hits / total:>10.1%}")
            sys.stdout.flush()


# --- 2. invalidation -------------------------------------------------------------------------------------------
SET_IF_NEWER = """local cur = redis.call('GET', KEYS[1])
if cur and tonumber(string.match(cur, '^(%d+)')) >= tonumber(ARGV[1]) then return 0 end
redis.call('SET', KEYS[1], ARGV[1] .. ':' .. ARGV[2], 'EX', ARGV[3]) return 1"""

FILL_IF_LEASE = """if redis.call('GET', KEYS[2]) == ARGV[3] then
  redis.call('SET', KEYS[1], ARGV[1] .. ':' .. ARGV[2], 'EX', ARGV[4]) redis.call('DEL', KEYS[2]) return 1 end
return 0"""

INVALIDATION = {
    "ttl": "TTL only (60 s), no invalidation",
    "delete-before": "delete key, then update the database",
    "delete-after": "update the database, then delete key",
    "double-delete": "update, delete, delete again 500 ms later",
    "versioned": "update, then write-through set-if-newer",
    "lease": "update, delete; fills need a lease",
}


def invalidation():
    print("200 menu items, 16 clients for 15 s, 90% reads through the cache, 10% price changes, 2 ms apart.")
    print("A reader that misses spends 0-5 ms between reading the database and filling the cache (serialisation,")
    print("a GC pause). A read is stale if it returns an older price than one committed before the read started;")
    print("stale entries are cache keys still older than the database after the run (they live until their TTL).\n")
    print(f"{'strategy':44}{'reads':>8}{'stale reads':>13}{'stale %':>9}{'hit rate':>10}{'stale entries':>15}")
    for strategy, name in INVALIDATION.items():
        setup()
        committed = collections.defaultdict(list)            # item -> [(time committed, version)]
        clock = threading.Lock()
        stats = collections.Counter()
        end = time.monotonic() + 15
        timers = []

        def required(item, t0):
            with clock:
                vs = [v for (t, v) in committed[item] if t < t0]
            return max(vs) if vs else 1

        def reader(rng, pg, rd, item):
            key, lease_key = f"item:{item}", f"lease:item:{item}"
            for _ in range(50):
                cached = rd("GET", key)
                if cached is not None:
                    return int(cached.split(":")[0]), True
                token = None
                if strategy == "lease":
                    token = str(rng.random())
                    if rd("SET", lease_key, token, "NX", "PX", 2000) is None:
                        time.sleep(0.005)                     # someone else is filling: wait for them
                        continue
                version, price = map(int, pg.q(f"SELECT version, price FROM items WHERE item_id = {item}")[0])
                time.sleep(rng.random() * 0.005)
                if strategy == "versioned":
                    rd("EVAL", SET_IF_NEWER, 1, key, version, price, 60)
                elif strategy == "lease":
                    rd("EVAL", FILL_IF_LEASE, 2, key, lease_key, version, price, token, 60)
                else:
                    rd("SET", key, f"{version}:{price}", "EX", 60)
                return version, False
            version = int(pg.q(f"SELECT version FROM items WHERE item_id = {item}")[0][0])
            return version, False

        def writer(rng, pg, rd, item):
            key = f"item:{item}"
            if strategy == "delete-before":
                rd("DEL", key)
            version, price = map(int, pg.q(f"UPDATE items SET price = {rng.randint(5000, 20000)}, version = version + 1 "
                                           f"WHERE item_id = {item} RETURNING version, price")[0])
            with clock:
                committed[item].append((time.monotonic(), version))
            if strategy in ("delete-after", "double-delete"):
                rd("DEL", key)
            if strategy == "double-delete":
                tm = threading.Timer(0.5, lambda: wire.Redis()("DEL", key))
                tm.start()
                timers.append(tm)
            if strategy == "versioned":
                rd("EVAL", SET_IF_NEWER, 1, key, version, price, 60)
            if strategy == "lease":
                rd("DEL", key, f"lease:{key}")

        def client(n):
            rng, pg, rd = random.Random(n), Pg(), Rd()
            while time.monotonic() < end:
                item = rng.randint(1, 200)
                if rng.random() < 0.1:
                    writer(rng, pg, rd, item)
                else:
                    t0 = time.monotonic()
                    seen, hit = reader(rng, pg, rd, item)
                    stale = seen < required(item, t0)
                    with clock:
                        stats["reads"] += 1
                        stats["hits"] += hit
                        stats["stale"] += stale
                time.sleep(0.002)

        ts = [threading.Thread(target=client, args=(n,)) for n in range(16)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        [tm.join() for tm in timers]
        rd = wire.Redis()
        truth = {int(i): int(v) for i, v in admin("SELECT item_id, version FROM items")}
        stale_entries = 0
        for item, v in truth.items():
            cached = rd("GET", f"item:{item}")
            if cached is not None and int(cached.split(":")[0]) < v:
                stale_entries += 1
        r = stats["reads"]
        print(f"{name:44}{r:>8}{stats['stale']:>13}{stats['stale'] / r:>9.2%}{stats['hits'] / r:>10.1%}{stale_entries:>15}")
        sys.stdout.flush()


# --- 3. stampede -----------------------------------------------------------------------------------------------
def stampede():
    t = time.perf_counter()
    admin(TOP_SQL)
    print(f"'Top restaurants' (an aggregation over 1 M reviews, ~{(time.perf_counter() - t) * 1000:.0f} ms) cached for 3 s;")
    print("The value is in the cache when the run starts; 4 app instances x 20 clients ask for it every 5 ms for 20 s.\n")
    print(f"{'protection':46}{'recomputes':>11}{'max at once':>12}{'req/s':>8}{'p50 ms':>8}{'p99 ms':>8}{'>100 ms':>9}")
    for mode, name in (("naive", "none (cache-aside)"), ("singleflight", "single-flight per instance"),
                       ("lock", "distributed lock (SET NX), others wait"),
                       ("xfetch", "probabilistic early refresh (XFetch)"),
                       ("xfetch-lock", "XFetch + lock: one early refresher"),
                       ("swr", "stale-while-revalidate (soft TTL) + lock")):
        setup()
        wire.Redis()("DEL", "top:BLR")
        stats = collections.Counter()
        lat = []
        lock = threading.Lock()
        running = [0]
        end = time.monotonic() + 20
        flights = [threading.Lock() for _ in range(4)]           # instance n % 4
        refreshing = [False]

        def compute(pg):
            with lock:
                running[0] += 1
                stats["max"] = max(stats["max"], running[0])
                stats["recomputes"] += 1
            t = time.perf_counter()
            value = repr(pg.q(TOP_SQL))
            delta = time.perf_counter() - t
            with lock:
                running[0] -= 1
            return value, delta

        def store(rd, value, delta):
            soft = time.time() + 3
            if mode == "swr":
                rd("SET", "top:BLR", f"{soft}|{delta}|{value}", "EX", 60)
            else:
                rd("SET", "top:BLR", f"{soft}|{delta}|{value}", "PX", 3000)

        refresher = Pg()                                  # stale-while-revalidate refreshes on one spare connection
        refresher_lock = threading.Lock()

        def get(n, rng, pg, rd):
            for _ in range(200):
                raw = rd("GET", "top:BLR")
                if raw is not None:
                    soft, delta, value = raw.split("|", 2)
                    soft, delta = float(soft), float(delta)
                    early = time.time() - delta * 1.0 * math.log(rng.random()) >= soft    # XFetch, beta = 1
                    if mode == "xfetch" and early:
                        store(rd, *compute(pg))                  # refresh early, before everyone else misses
                    elif mode == "xfetch-lock" and early:
                        if rd("SET", "lock:top:BLR", "1", "NX", "PX", 5000) is not None:
                            store(rd, *compute(pg))
                            rd("DEL", "lock:top:BLR")
                    elif mode == "swr" and time.time() >= soft:
                        if rd("SET", "lock:top:BLR", "1", "NX", "PX", 5000) is not None:
                            def refresh():
                                with refresher_lock:
                                    r = Rd()
                                    store(r, *compute(refresher))
                                    r("DEL", "lock:top:BLR")
                            threading.Thread(target=refresh, daemon=True).start()
                    return value                                 # (swr: possibly stale, by at most one refresh)
                if mode == "singleflight":
                    with flights[n % 4]:
                        if rd("GET", "top:BLR") is None:
                            store(rd, *compute(pg))
                    continue
                if mode in ("lock", "xfetch-lock", "swr"):
                    if rd("SET", "lock:top:BLR", "1", "NX", "PX", 5000) is not None:
                        store(rd, *compute(pg))
                        rd("DEL", "lock:top:BLR")
                    else:
                        time.sleep(0.01)
                    continue
                value, delta = compute(pg)
                store(rd, value, delta)
                return value
            raise RuntimeError("gave up")

        def client(n):
            rng, pg, rd = random.Random(n), Pg(), Rd()
            while time.monotonic() < end:
                t = time.perf_counter()
                get(n, rng, pg, rd)
                dt = time.perf_counter() - t
                with lock:
                    lat.append(dt)
                time.sleep(0.005)

        warm, rd0 = Pg(), Rd()
        store(rd0, *compute(warm))
        stats.clear()
        ts = [threading.Thread(target=client, args=(n,)) for n in range(80)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        slow = sum(1 for x in lat if x > 0.1)
        print(f"{name:46}{stats['recomputes']:>11}{stats['max']:>12}{len(lat) / 20:>8.0f}{pct(lat, 50) * 1000:>8.1f}"
              f"{pct(lat, 99) * 1000:>8.1f}{slow:>9}")
        sys.stdout.flush()


# --- 4. write-behind -------------------------------------------------------------------------------------------
FLUSH_CLAIM = """if redis.call('EXISTS', KEYS[2]) == 1 then return redis.call('HGETALL', KEYS[2]) end
if redis.call('EXISTS', KEYS[1]) == 0 then return {} end
redis.call('RENAME', KEYS[1], KEYS[2]) return redis.call('HGETALL', KEYS[2])"""


def flush(pg, rd, crash=None, remember_batch=True):
    """Moves buffered counts to PostgreSQL. The counts are renamed to views:flushing first, so new views keep
    arriving in a fresh hash; a flusher that died leaves views:flushing behind and the next one finishes it. The
    batch id, kept in Redis until the batch is cleaned up and recorded in PostgreSQL in the same transaction as
    the counts, makes finishing a batch that was already committed a no-op."""
    pairs = rd("EVAL", FLUSH_CLAIM, 2, "views", "views:flushing")
    if not pairs:
        return 0
    batch_id = (rd("GET", "views:batch") if remember_batch else None) or str(time.time_ns())
    if remember_batch:
        rd("SET", "views:batch", batch_id)
    if crash == "before-commit":
        return -1
    counts = list(zip(pairs[0::2], pairs[1::2]))
    values = ",".join(f"({i}, {n})" for i, n in counts)
    pg.q(f"""WITH b AS (INSERT INTO flushed_batches (batch_id) VALUES ('{batch_id}') ON CONFLICT DO NOTHING RETURNING 1)
             INSERT INTO item_views (item_id, views) SELECT i, n FROM (VALUES {values}) v(i, n), b
             ON CONFLICT (item_id) DO UPDATE SET views = item_views.views + EXCLUDED.views""")
    if crash == "after-commit":
        return -1
    rd("DEL", "views:flushing", "views:batch")
    return len(counts)


def writebehind():
    print("16 clients count item views (2,000 items) for 10 s.\n")
    print(f"{'write path':54}{'views/s':>9}{'DB statements/s':>17}{'counted':>10}{'in DB':>9}{'lost':>7}{'doubled':>9}")
    cases = (("through", None, "write-through: UPDATE per view"),
             ("behind", None, "write-behind: HINCRBY, flush every 1 s"),
             ("behind", "redis-restart", "write-behind, Redis restarts (no persistence) at 7 s"),
             ("behind", "before-commit", "write-behind, flusher dies before its DB commit"),
             ("behind", "after-commit", "write-behind, flusher dies after commit, before cleanup"),
             ("behind", "after-commit-naive", "  ...without the batch id"))
    for path, crash, name in cases:
        setup()
        counted = [0]
        lock = threading.Lock()
        db_writes = [0]
        end = time.monotonic() + 10
        stop = threading.Event()

        def client(n):
            rng, pg, rd = random.Random(n), Pg(), Rd()
            while time.monotonic() < end:
                item = rng.randint(1, 2000)
                try:
                    if path == "through":
                        pg.q(f"INSERT INTO item_views VALUES ({item}, 1) ON CONFLICT (item_id) DO UPDATE SET views = item_views.views + 1")
                        with lock:
                            db_writes[0] += 1
                    else:
                        rd("HINCRBY", "views", item, 1)
                except (OSError, ConnectionError, RuntimeError):
                    rd = Rd() if wait_redis() else rd
                    continue
                with lock:
                    counted[0] += 1

        def flusher():
            pg, rd = Pg(), Rd()
            crashed = False
            while not stop.is_set() or rd("EXISTS", "views") or rd("EXISTS", "views:flushing"):
                time.sleep(1)
                try:
                    mode = None
                    if not crashed and time.monotonic() > end - 5 and crash in ("before-commit", "after-commit", "after-commit-naive"):
                        mode = "before-commit" if crash == "before-commit" else "after-commit"
                        crashed = True                          # this flush dies; the next loop is the restarted flusher
                    n = flush(pg, rd, mode, remember_batch=crash != "after-commit-naive")
                    if n:
                        with lock:
                            db_writes[0] += 1
                except (OSError, ConnectionError, RuntimeError):
                    if wait_redis():
                        rd = Rd()

        ts = [threading.Thread(target=client, args=(n,)) for n in range(16)]
        f = threading.Thread(target=flusher)
        [t.start() for t in ts]
        if path == "behind":
            f.start()
        if crash == "redis-restart":
            time.sleep(7)
            os.system(os.environ.get("REDIS_RESTART", "true"))
        [t.join() for t in ts]
        stop.set()
        if path == "behind":
            f.join()
        in_db = int(admin("SELECT coalesce(sum(views), 0) FROM item_views")[0][0])
        lost, doubled = max(0, counted[0] - in_db), max(0, in_db - counted[0])
        print(f"{name:54}{counted[0] / 10:>9.0f}{db_writes[0] / 10:>17.1f}{counted[0]:>10}{in_db:>9}{lost:>7}{doubled:>9}")
        sys.stdout.flush()


def wait_redis(seconds=10):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            wire.Redis()("PING")
            return True
        except OSError:
            time.sleep(0.1)
    return False


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    for name, fn in (("read", read), ("invalidation", invalidation), ("stampede", stampede), ("writebehind", writebehind)):
        if what in (name, "all"):
            print(f"\n== {name}")
            fn()
