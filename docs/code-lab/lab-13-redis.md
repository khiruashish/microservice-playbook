---
title: "Lab 13 — Redis Caching, Locks & Rate Limits"
description: "Three jobs Redis does for FoodFleet, each tried on a real Redis 7.0: a two-level menu cache with single flight (200 concurrent misses, one database read; Redis down, every request still answered); per-tenant token buckets in Lua that let a quiet tenant through all 20 of its requests while a noisy one floods; and locks under GC pauses, where DEL release lost 213–229 of 300 updates, compare-and-delete 94–103, and fencing tokens none. About 75 minutes."
progress: done
---

# Lab 13 — Redis Caching, Locks & Rate Limits

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **You'll build** | menu-service with a two-level cache, per-tenant rate limits and a lock that's safe to rely on |
| **Time** | About 75 minutes |
| **Builds on** | [Lab 01](lab-01-hello-microservice.md) and [lab 02](lab-02-persistence.md) |
| **Chapters** | [32 (caching)](../data/ch32-caching-strategies.md), [40 (API security: rate limits)](../security/ch40-api-security.md), [3 (distributed fundamentals)](../foundations/ch03-distributed-systems-fundamentals.md) |
| **Code** | `code/code-lab/lab-13-redis/` |
| **Needs** | `redis-server` 7+ and Python 3 for `verify/run.sh`; Docker for the Spring tests |

!!! note "About the lab code"
    The Lua scripts, `MenuCards` and the experiments **were run**: the scripts on Redis 7.0.15 through `verify/redis_lab.py`, which loads the same files the Spring code does, and `MenuCards` compiled with JDK 25.0.4 against an in-memory stand-in. The Spring classes around them are written for Spring Boot 4.1.1 and Spring Data Redis 4.1.1 and checked by import against their source, not compiled here ([how](index.md#how-the-lab-code-was-checked)). Three runs; the ranges below cover all three.

## 1. A two-level cache for menu cards

Chapter 32 measured menu cards on Redis alone at 6,211–6,422 requests/s, and with a 5-second in-process cache in front at 13,760–14,152 requests/s with a p50 under 0.1 ms ([ADR-032](../data/ch32-caching-strategies.md#8-enterprise-case-study)). The in-process level removes the network hop for the hottest restaurants.

```java title="MenuCards.java"
--8<-- "code/code-lab/lab-13-redis/src/main/java/dev/foodfleet/lab13/MenuCards.java:40:83"
```

- **Single flight.** When a card is missing, the first request creates a future and loads; every concurrent request for the same card waits on that future. No stampede on Redis or the database.
- **Jittered TTL.** Cards written together, say after a deploy, would otherwise expire together and miss together.
- **Redis down means slower, not broken.** `RedisSharedCache` turns Spring's `DataAccessException` into `SharedCache.Unavailable`, and `MenuCards` reads through to the database. A 200 ms command timeout in `application.yml` keeps a slow Redis from becoming a slow service.

Because the cache logic is plain Java behind a `SharedCache` interface, `verify/MenuCardsCheck.java` runs it with a map for Redis and a 20 ms "query" for the database:

```console
cold start, 200 concurrent requests: database reads 1, Redis reads 1
again within 5 s: database reads 1, Redis reads 1 (all from L1)
6 s later, 200 concurrent requests: database reads 1, Redis reads 2
Redis down, 200 requests: all answered, database reads 2
L2 TTLs over 10,000 writes: 54001-65999 ms
```

The price is staleness per pod: after a menu change, another pod can serve its old copy for up to 5 seconds. FoodFleet accepts that for menus and not for prices, which chapter 32 caches with versioned write-through instead.

## 2. Rate limits per tenant

```lua title="redis/token-bucket.lua"
--8<-- "code/code-lab/lab-13-redis/src/main/resources/redis/token-bucket.lua"
```

```java title="TenantRateLimiter.java"
--8<-- "code/code-lab/lab-13-redis/src/main/java/dev/foodfleet/lab13/TenantRateLimiter.java:36:45"
```

The whole decision runs **inside Redis as one script**: read, refill, take, write. Two pods can't both take the last token, and the time comes from Redis's clock, so pods with drifting clocks agree. `RateLimitInterceptor` answers `429` with `Retry-After` from the script's third value.

The experiment offers 40 requests a second for 6 seconds against a limit of 10 a second:

```console
== rate limits: 40 requests/s offered for 6 s; the limit is 10/s
  fixed window, 10 per clock second    allowed  70 of 240; most in any 1-second span: 20
  token bucket, capacity 10, 10/s      allowed  69 of 240; most in any 1-second span: 19
  token bucket, capacity 20, 10/s      allowed  79 of 240; most in any 1-second span: 30
  fixed window, burst across a boundary: 20 of 24 allowed within 0.11 s
== noisy neighbour: tenant A offers 200/s, tenant B 5/s, for 4 s; each tenant's bucket is 10/s
  tenant A: 49 of 800 allowed; tenant B: 20 of 20 allowed
```

- **A fixed window** (`INCR` a key per clock second) is the simplest limiter, and lets **twice the limit through in 0.11 seconds**: ten at the end of one second, ten at the start of the next.
- **A token bucket** allows a burst of its capacity, then the refill rate. The burst is a decision you make (`capacity`), not an accident of where the clock second falls. FoodFleet's limiter is GCRA, the same behaviour stored as a single timestamp ([ADR-040](../security/ch40-api-security.md)).
- **Per tenant**: tenant A's flood used A's bucket; tenant B got all 20 of its requests.

**When Redis is down**, the limiter falls back to a bucket in each pod's memory. That's ADR-040's *bounded fallback*: at worst the number of pods times the limit, never unlimited, and never refusing everyone. Chapter 40 notes that Spring Cloud Gateway's Redis limiter fails open instead.

## 3. Locks: a lease, not a mutex

A Redis lock **expires**, which is what stops a crashed holder from blocking everyone forever. It also means a holder that pauses longer than the TTL (a GC pause, a slow call) is no longer the holder and doesn't know it.

```java title="RedisLock.java"
--8<-- "code/code-lab/lab-13-redis/src/main/java/dev/foodfleet/lab13/RedisLock.java:31:46"
```

The experiment: six workers share 300 increments of a counter, each holding a 50 ms lock while it reads, computes and writes; one increment in five pauses 60–90 ms in the middle.

```console
== locks: 300 increments by 6 workers; lock TTL 50 ms; one in five holds it 60-90 ms (a GC pause)
  DEL release                          overlaps 297, foreign releases 124, writes 300, counter  82, lost updates 218, refused   0
  compare-and-delete release           overlaps 221, foreign releases   0, writes 300, counter 197, lost updates 103, refused   0
  compare-and-delete + fencing token   overlaps 245, foreign releases   0, writes 242, counter 242, lost updates   0, refused  58
```

| Release | What went wrong (3 runs) |
|---|---|
| `DEL` | A worker that woke after its lock expired deleted the **next** holder's lock (124–136 times), letting a third in. 213–229 updates lost |
| Compare-and-delete (`unlock.lua`) | No foreign deletes, but the paused worker still believed it held the lock and wrote a value computed from an old read: 94–103 updates lost |
| Plus a **fencing token** | Every grant increments a counter; the resource records the highest token it has seen and refuses older ones, on reads and writes (`fenced-read.lua`, `fenced-write.lua`). 0 lost; 58 stale attempts refused, which a real worker retries |

The lesson in the right column of every row: **the lock can't protect the data by itself**. Use a Redis lock to avoid duplicate *work* (one pod rebuilds the search index), and make the *data* safe where it lives: a fencing token, or a version column checked on update, which is what FoodFleet's services do in PostgreSQL ([ch. 3](../foundations/ch03-distributed-systems-fundamentals.md)).

## 4. The Spring tests

```java title="RedisLabTest.java"
--8<-- "code/code-lab/lab-13-redis/src/test/java/dev/foodfleet/lab13/RedisLabTest.java:18:67"
```

## Run it

```bash
code/code-lab/lab-13-redis/verify/run.sh          # a throwaway Redis on port 56379, then MenuCardsCheck; ~30 s
cd code/code-lab/lab-13-redis && mvn verify       # Testcontainers starts redis:7.4
```

## Check yourself

- [ ] `run.sh` shows one database read for 200 concurrent misses, and all requests answered with Redis down.
- [ ] You can explain why a fixed window lets 20 requests through in 0.11 s with a limit of 10 a second.
- [ ] You can say what a fencing token protects against that compare-and-delete doesn't.

## Break it

!!! question "Exercises"
    1. Remove single flight from `MenuCards` (load whenever the entry is missing or expired, no future). How many database reads do 200 concurrent cold requests cause?
    2. Use the callers' `System.currentTimeMillis()` instead of Redis `TIME` in `token-bucket.lua`, and give one caller a clock 2 seconds fast. What happens to its tenant's bucket?
    3. In `redis_lab.py`, fence writes but not reads (call `fenced-write.lua` only). Are any updates lost? Work out the interleaving that loses one.
    4. Raise the lock TTL to 200 ms. Do the lost updates go away? What does that cost when a holder really crashes?

**Next:** [Lab 14](lab-14-virtual-threads-vs-webflux.md) measures virtual threads against WebFlux under load.
