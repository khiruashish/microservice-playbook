"""Lab 13 on a real Redis: the lab's Lua scripts, exactly as the Spring code loads them.
  1. rate limits   a fixed-window counter and the token bucket under the same steady load; a noisy tenant
  2. locks         workers incrementing a shared counter under a lock that can expire mid-work:
                   DEL release, compare-and-delete release, and fencing tokens
REDIS_PORT (this Redis is FLUSHED). Standard library only; uses chapter 32's RESP client."""
import collections, os, pathlib, random, sys, threading, time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "chapters" / "ch32-caching-strategies" / "verify"))
import wire  # noqa: E402

LUA = {p.stem: p.read_text() for p in (HERE.parent / "src/main/resources/redis").glob("*.lua")}
random.seed(13)


def redis():
    return wire.Redis()


def load(r):
    return {name: r("SCRIPT", "LOAD", src) for name, src in LUA.items()}


def run_load(per_second, seconds, decide, threads=4):
    """Open loop: `per_second` requests spread over `threads`; returns the times of the allowed ones."""
    allowed, lock = [], threading.Lock()
    start = time.monotonic() + 0.2

    def worker(k):
        r = redis()
        n = int(per_second * seconds / threads)
        for i in range(n):
            at = start + (i * threads + k) / per_second
            pause = at - time.monotonic()
            if pause > 0:
                time.sleep(pause)
            if decide(r):
                with lock:
                    allowed.append(time.monotonic() - start)

    ts = [threading.Thread(target=worker, args=(k,)) for k in range(threads)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    return sorted(allowed)


def max_in_any_second(times):
    best, j = 0, 0
    for i, t in enumerate(times):
        while times[j] < t - 1.0:
            j += 1
        best = max(best, i - j + 1)
    return best


def rate_limits():
    r = redis()
    r("FLUSHALL")
    sha = load(r)
    print("== rate limits: 40 requests/s offered for 6 s; the limit is 10/s")

    def fixed_window(c):
        window = int(time.time())
        key = f"rl:fixed:t1:{window}"
        n = c("INCR", key)
        if n == 1:
            c("EXPIRE", key, 2)
        return n <= 10

    def bucket(c, tenant="t1", capacity=10, rate=10):
        return c("EVALSHA", sha["token-bucket"], 1, f"rl:bucket:{tenant}", capacity, rate, 1)[0] == 1

    for name, decide in [("fixed window, 10 per clock second", fixed_window),
                         ("token bucket, capacity 10, 10/s", bucket),
                         ("token bucket, capacity 20, 10/s", lambda c: bucket(c, "t2", 20, 10))]:
        times = run_load(40, 6, decide)
        print(f"  {name:36} allowed {len(times):3} of 240; most in any 1-second span: {max_in_any_second(times)}")

    # The fixed window's weak spot: a burst at the end of one window and another at the start of the next.
    r("FLUSHALL")
    time.sleep(1.9 - time.time() % 1)                  # 0.9 s into a clock second
    t0, allowed = time.monotonic(), 0
    for _ in range(12):
        allowed += fixed_window(r)
    time.sleep(1.0 - time.time() % 1 + 0.01)           # just into the next second
    for _ in range(12):
        allowed += fixed_window(r)
    print(f"  fixed window, burst across a boundary: {allowed} of 24 allowed within {time.monotonic() - t0:.2f} s")
    print("== noisy neighbour: tenant A offers 200/s, tenant B 5/s, for 4 s; each tenant's bucket is 10/s")
    results = {}

    def tenant(name, rps):
        results[name] = run_load(rps, 4, lambda c: bucket(c, "noisy-" + name, 10, 10), threads=4 if rps > 50 else 1)

    ts = [threading.Thread(target=tenant, args=a) for a in (("A", 200), ("B", 5))]
    [t.start() for t in ts]
    [t.join() for t in ts]
    print(f"  tenant A: {len(results['A'])} of 800 allowed; tenant B: {len(results['B'])} of 20 allowed")


def locks(rounds=300, ttl_ms=50):
    print(f"== locks: {rounds} increments by 6 workers; lock TTL {ttl_ms} ms; one in five holds it 60-90 ms (a GC pause)")
    for variant in ("DEL release", "compare-and-delete release", "compare-and-delete + fencing token"):
        r = redis()
        r("FLUSHALL")
        sha = load(r)
        stats = collections.Counter()
        holders, hl = set(), threading.Lock()
        todo = iter(range(rounds))
        tl = threading.Lock()

        def worker(wid):
            c = redis()
            while True:
                with tl:
                    job = next(todo, None)
                if job is None:
                    return
                me = f"w{wid}-{job}"
                while c("SET", "lock:counter", me, "NX", "PX", ttl_ms) != "OK":
                    time.sleep(0.001)
                token = c("INCR", "lock:counter:fence")            # issued with the lock
                with hl:
                    if holders:
                        stats["overlapping holders"] += 1
                    holders.add(me)
                if variant.endswith("fencing token"):
                    read = c("EVALSHA", sha["fenced-read"], 2, "counter", "counter:fence", token)
                    if read is None:                                 # a newer holder already took over
                        stats["stale writes refused"] += 1
                        with hl:
                            holders.discard(me)
                        c("EVALSHA", sha["unlock"], 1, "lock:counter", me)
                        continue
                    value = int(read)
                else:
                    value = int(c("GET", "counter") or 0)
                time.sleep(random.uniform(0.060, 0.090) if random.random() < 0.2 else random.uniform(0.001, 0.005))
                if variant.endswith("fencing token"):
                    if c("EVALSHA", sha["fenced-write"], 2, "counter", "counter:fence", token, value + 1) == 0:
                        stats["stale writes refused"] += 1
                    else:
                        stats["writes"] += 1
                else:
                    c("SET", "counter", value + 1)
                    stats["writes"] += 1
                with hl:
                    holders.discard(me)
                if variant == "DEL release":
                    if c("GET", "lock:counter") not in (me, None):
                        stats["foreign"] += 1
                    c("DEL", "lock:counter")
                else:
                    c("EVALSHA", sha["unlock"], 1, "lock:counter", me)

        ts = [threading.Thread(target=worker, args=(w,)) for w in range(6)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        final = int(r("GET", "counter"))
        lost = stats["writes"] - final
        print(f"  {variant:36} overlaps {stats['overlapping holders']:3}, foreign releases "
              f"{stats['foreign']:3}, writes {stats['writes']:3}, counter {final:3}, "
              f"lost updates {lost:3}, refused {stats['stale writes refused']:3}")
        stats.clear()


if __name__ == "__main__":
    which = sys.argv[1:] or ["rate", "locks"]
    if "rate" in which:
        rate_limits()
    if "locks" in which:
        locks()
