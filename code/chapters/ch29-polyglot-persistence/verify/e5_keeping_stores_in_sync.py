"""Experiment 5: keeping a second store in sync. PostgreSQL owns menu prices; Redis holds the copy the
menu pages read. 8 writers make 20,000 price changes to 5,000 items, under the same injected faults
for every strategy:
  - a 0-2 ms pause between the two writes (GC, network), so concurrent writers interleave
  - the process dies between the two writes on 0.5% of changes
  - the cache-first strategy's database transaction fails on 0.5% of changes (after Redis was written)
  - Redis is unreachable during changes 6,000-6,999 (simulated: calls raise ConnectionError)
Every price written is unique, so a Redis value that PostgreSQL never committed can be told apart from a
stale one. At the end, after the projector (if any) has drained, Redis is compared with PostgreSQL."""
import random
import statistics
import sys
import threading
import time
import wire

ITEMS, WRITERS, CHANGES = 5_000, 8, 20_000
GUARD = """local cur = redis.call('HGET', KEYS[1], 'v')
if (not cur) or tonumber(cur) < tonumber(ARGV[1]) then redis.call('HSET', KEYS[1], 'v', ARGV[1], 'p', ARGV[2]) return 1 end
return 0"""


class Faults:
    def __init__(self):
        self.lock = threading.Lock()
        self.count = 0

    def next_change(self):
        with self.lock:
            self.count += 1
            return self.count

    def redis_down(self):
        return 6_000 <= self.count < 7_000


class FlakyRedis:
    def __init__(self, faults):
        self.r, self.faults = wire.Redis(), faults

    def __call__(self, *args):
        if self.faults.redis_down():
            raise ConnectionError("redis unreachable")
        return self.r(*args)


def setup():
    pg, r = wire.Pg(), wire.Redis()
    pg.query(f"""DROP TABLE IF EXISTS catalog_prices, price_events;
        CREATE TABLE catalog_prices (item_id int PRIMARY KEY, price int NOT NULL, version int NOT NULL);
        CREATE TABLE price_events (id bigserial PRIMARY KEY, item_id int NOT NULL, price int NOT NULL,
                                   version int NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp());
        INSERT INTO catalog_prices SELECT i, i, 1 FROM generate_series(1, {ITEMS}) i;""")
    r("FLUSHDB")
    for i in range(1, ITEMS + 1):
        r("HSET", f"price:{i}", "v", 1, "p", i)
    pg.close(), r.close()


def pause(rng):
    time.sleep(rng.random() * 0.002)


# --- strategies: each returns the price PostgreSQL committed (or None) -----------------------------------
def db_then_cache(pg, redis, rng, item, price, guarded, crash_p=0.005):
    version = int(pg.query(f"UPDATE catalog_prices SET price = {price}, version = version + 1 WHERE item_id = {item} RETURNING version")[0][0])
    pause(rng)
    if rng.random() < crash_p:
        return price, "crash"                       # died after the commit, before the cache write
    try:
        if guarded:
            redis("EVAL", GUARD, 1, f"price:{item}", version, price)
        else:
            redis("HSET", f"price:{item}", "v", version, "p", price)
    except ConnectionError:
        return price, "cache write failed, logged"
    return price, None


def cache_then_db(pg, redis, rng, item, price, crash_p=0.005):
    try:
        redis("HSET", f"price:{item}", "p", price)
    except ConnectionError:
        return None, "cache write failed, request failed"
    pause(rng)
    if rng.random() < crash_p:
        return None, "crash"                        # died after the cache write, before the database
    if rng.random() < crash_p:
        pg.query(f"BEGIN; UPDATE catalog_prices SET price = {price}, version = version + 1 WHERE item_id = {item}; ROLLBACK")
        return None, "db transaction failed"
    pg.query(f"UPDATE catalog_prices SET price = {price}, version = version + 1 WHERE item_id = {item}")
    return price, None


def outbox_write(pg, redis, rng, item, price):
    pg.query(f"""WITH u AS (UPDATE catalog_prices SET price = {price}, version = version + 1 WHERE item_id = {item}
                            RETURNING item_id, price, version)
                 INSERT INTO price_events (item_id, price, version) SELECT * FROM u""")
    pause(rng)
    return price, None


class Projector(threading.Thread):
    """Applies outbox rows to Redis. mode='claim': delete-and-apply in one transaction with SKIP LOCKED,
    so a row that commits late is simply picked up by a later poll. mode='offset': remember the highest id
    applied and read 'id > offset' -- the shortcut that misses rows whose transactions commit out of order."""

    def __init__(self, faults, mode, stop):
        super().__init__(daemon=True)
        self.faults, self.mode, self.stop = faults, mode, stop
        self.lags, self.crashes, self.offset = [], 0, 0

    def run(self):
        pg, redis, rng = wire.Pg(), FlakyRedis(self.faults), random.Random(7)
        while True:
            drained = self.poll(pg, redis, rng)
            if drained and self.stop.is_set():
                break
            if drained:
                time.sleep(0.02)

    def poll(self, pg, redis, rng):
        if self.mode == "claim":
            pg.query("BEGIN")
            rows = pg.query("""DELETE FROM price_events WHERE id IN (SELECT id FROM price_events ORDER BY id LIMIT 500 FOR UPDATE SKIP LOCKED)
                               RETURNING id, item_id, price, version, extract(epoch FROM clock_timestamp() - created_at) * 1000""")
        else:
            rows = pg.query(f"""SELECT id, item_id, price, version, extract(epoch FROM clock_timestamp() - created_at) * 1000
                                  FROM price_events WHERE id > {self.offset} ORDER BY id LIMIT 500""")
        if not rows:
            if self.mode == "claim":
                pg.query("COMMIT")
            return True
        try:
            for _, item, price, version, _ in sorted(rows, key=lambda x: int(x[0])):
                redis("EVAL", GUARD, 1, f"price:{item}", version, price)
        except ConnectionError:
            if self.mode == "claim":
                pg.query("ROLLBACK")                  # rows stay; the next poll retries them
            time.sleep(0.05)
            return False
        if rng.random() < 0.05:                       # died after applying, before recording progress
            self.crashes += 1
            if self.mode == "claim":
                pg.query("ROLLBACK")                  # the rows come back and are applied again (the guard makes it harmless)
            return False
        self.lags.extend(float(r[4]) for r in rows)
        if self.mode == "claim":
            pg.query("COMMIT")
        else:
            self.offset = max(int(r[0]) for r in rows)
        return False


def run(name, strategy, projector_mode=None):
    setup()
    faults = Faults()
    stop = threading.Event()
    projector = Projector(faults, projector_mode, stop) if projector_mode else None
    if projector:
        projector.start()
    committed = set(range(1, ITEMS + 1))
    events = {}
    lock = threading.Lock()

    def writer(w):
        pg, redis, rng = wire.Pg(), FlakyRedis(faults), random.Random(1000 + w)
        for k in range(CHANGES // WRITERS):
            n = faults.next_change()
            item = rng.randint(1, ITEMS)
            price = 100_000 + n                       # unique per change
            done, event = strategy(pg, redis, rng, item, price)
            with lock:
                if done is not None:
                    committed.add(done)
                if event:
                    events[event] = events.get(event, 0) + 1
        pg.close()

    t0 = time.monotonic()
    threads = [threading.Thread(target=writer, args=(w,)) for w in range(WRITERS)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    write_s = time.monotonic() - t0
    if projector:
        stop.set()
        projector.join()

    pg, r = wire.Pg(), wire.Redis()
    truth = {int(i): int(p) for i, p in pg.query("SELECT item_id, price FROM catalog_prices")}
    stale = phantom = 0
    for item, price in truth.items():
        cached = int(r("HGET", f"price:{item}", "p"))
        if cached != price:
            if cached in committed:
                stale += 1
            else:
                phantom += 1
    pg.close(), r.close()
    lag = ""
    if projector and projector.lags:
        q = statistics.quantiles(projector.lags, n=100)
        lag = f"{q[49]:.0f} / {q[98]:.0f} / {max(projector.lags):.0f}"
    ev = ", ".join(f"{k} {v}" for k, v in sorted(events.items()))
    if projector:
        ev = (ev + ", " if ev else "") + f"projector crashes {projector.crashes}"
    print(f"{name:44}{stale:>7}{phantom:>9}{stale + phantom:>8}{CHANGES / write_s:>9.0f}{lag:>21}   {ev}")
    sys.stdout.flush()


def race(name, strategy, rounds=1_000):
    """No crashes, no outage: two writers change the same item at the same moment, 1,000 times."""
    setup()
    faults = Faults()
    barrier = threading.Barrier(2)
    conns = [(wire.Pg(), FlakyRedis(faults), random.Random(50 + w)) for w in range(2)]

    def one(w, item, price):
        pg, redis, rng = conns[w]
        barrier.wait()
        strategy(pg, redis, rng, item, price)

    wrong = 0
    check = wire.Pg()
    for k in range(rounds):
        item = 1 + k % ITEMS
        ts = [threading.Thread(target=one, args=(w, item, 200_000 + 2 * k + w)) for w in range(2)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        truth = int(check.query(f"SELECT price FROM catalog_prices WHERE item_id = {item}")[0][0])
        if int(conns[0][1]("HGET", f"price:{item}", "p")) != truth:
            wrong += 1
    print(f"{name:44}{wrong:>7} of {rounds:,} rounds left Redis wrong")


def main():
    print(f"{ITEMS} items, {WRITERS} writers, {CHANGES:,} price changes; items where Redis != PostgreSQL at the end\n")
    print(f"{'strategy':44}{'stale':>7}{'phantom':>9}{'wrong':>8}{'chg/s':>9}{'lag p50/p99/max ms':>21}   events")
    run("dual write: database, then Redis", lambda *a: db_then_cache(*a, guarded=False))
    run("dual write: database, then Redis + version", lambda *a: db_then_cache(*a, guarded=True))
    run("dual write: Redis, then database", cache_then_db)
    run("outbox, projector reads 'id > offset'", outbox_write, "offset")
    run("outbox, projector claims rows (SKIP LOCKED)", outbox_write, "claim")
    print("\nthe race alone: two writers, same item, same moment (0-2 ms pause between their two writes)")
    race("dual write: database, then Redis", lambda *a: db_then_cache(*a, guarded=False, crash_p=0))
    race("dual write: database, then Redis + version", lambda *a: db_then_cache(*a, guarded=True, crash_p=0))
    race("dual write: Redis, then database", lambda *a: cache_then_db(*a, crash_p=0))


if __name__ == "__main__":
    main()
