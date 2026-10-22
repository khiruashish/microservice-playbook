---
title: "Redis / Valkey"
description: "Redis 7 at FoodFleet: never the source of truth, only copies and short-lived state (ADR-029, ADR-032). Live rider positions in a geo set (3.8–5.6× PostgreSQL's write rate at matched durability), caches chosen by purpose (set-if-newer write-through left 0.02–0.03% stale reads; stale-while-revalidate, one recomputation per expiry), write-behind counters on a separate instance, per-tenant token buckets in Lua, and locks that need fencing tokens (DEL release lost 213–229 of 300 updates, fencing none). Where Valkey fits."
progress: done
---

# Redis / Valkey

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | An in-memory data-structure server: strings, hashes, sorted sets, geo sets, streams; single-threaded command execution, atomic Lua scripts, optional persistence (RDB, AOF), replication, Cluster. **Valkey** is the Linux Foundation fork with the same protocol and commands |
| **FoodFleet uses** | Redis 7 for live rider positions, shared caches, rate-limit buckets, write-behind counters and leases; never as the only copy of business data |
| **Decided in** | [ADR-029](../data/ch29-polyglot-persistence.md#8-enterprise-case-study) (positions; copies only), [ADR-032](../data/ch32-caching-strategies.md#8-enterprise-case-study) (cache by purpose) |
| **Learn it in** | [Chapter 29](../data/ch29-polyglot-persistence.md), [chapter 32](../data/ch32-caching-strategies.md), [lab 13](../code-lab/lab-13-redis.md) |
| **Related** | [PostgreSQL](postgresql.md), [Spring Cache](spring-cache.md), [Multi-level caching](multi-level-caching.md), [Rate limiting](rate-limiting.md), [Distributed locks](distributed-locks.md) |

## Redis or Valkey

Redis changed its licence in 2024 (to RSALv2/SSPL), and the Linux Foundation forked the last BSD-licensed version as **Valkey**; Redis later added the AGPL as an option. Both speak the same protocol, so Lettuce, Spring Data Redis and the Lua scripts on this page work unchanged against either. The chapters measured Redis 7; check the licence and your cloud provider's managed offering when choosing.

## Rider positions: where Redis earned its place

[Chapter 29](../data/ch29-polyglot-persistence.md) compared position updates at **matched durability**, because comparisons without it are meaningless:

| Store and setting | Updates/s | p50 |
|---|---:|---:|
| Redis, AOF `appendfsync always` | 30,590–32,007 | 0.23–0.24 ms |
| Redis, AOF `appendfsync everysec` (may lose ~1 s) | 90,033–95,039 | 0.06 ms |
| PostgreSQL + GiST geo index, `synchronous_commit=off` | 7,526–7,632 | 1.05–1.06 ms |

| Query | Queries/s | p50 |
|---|---:|---:|
| `GEOSEARCH … ASC COUNT 10` (city centre, ~1,900 riders in the circle) | 939–952 | 8.4–8.5 ms |
| `GEOSEARCH … COUNT 10 ANY` (any 10 in the circle, unsorted) | 8,333–14,537 | 0.5–0.9 ms |

A geo set is a sorted set of geohashes; with `ASC`, Redis sorts **every** member in the circle before returning ten. Positions are a copy: losing a second of them on restart is acceptable because riders send new ones every few seconds. History goes to partitioned PostgreSQL tables.

## Caches, by purpose

ADR-032 replaced "cache-aside everywhere" with a strategy per cache ([chapter 32](../data/ch32-caching-strategies.md)):

| Data | Strategy | Why, measured |
|---|---|---|
| Menu cards (primary-key reads) | **No shared cache**; HTTP ETags | Redis cache-aside was *slower* than the database: 2.1–2.2 ms p50 vs 1.7–1.8 ms. An in-process layer in front reached 13,760–14,152 requests/s |
| Item prices | Write-through after commit, through a **set-if-newer** script; 30-minute safety TTL | Stale reads 0.02–0.03% and 0 stale entries left, against 0.48–0.54% and 1–3 left by "update, then delete" |
| Restaurant details | `@Cacheable(sync = true)`, evicted after commit | Transaction-aware cache manager |
| Rankings, dashboards | **Stale-while-revalidate** with a refresh lock | 9–10 recomputations, 0 requests over 100 ms; unprotected, 160 recomputations, 80 at once |
| View counters | **Write-behind** (`HINCRBY`, flushed every second with a batch id), separate Redis | 12,212–12,271 views/s with one database statement a second; a restart without persistence lost about one flush (9,991–10,651 views) |

Checkout and payments read the database. Every cache is optional at runtime: in [lab 13](../code-lab/lab-13-redis.md), 200 concurrent misses made **one** database read, and with Redis stopped every request was still answered.

## Rate limits: a token bucket in Lua

One script per request: Redis runs it atomically and uses its own clock, so pods don't disagree.

```lua title="redis/token-bucket.lua (lab 13)"
--8<-- "code/code-lab/lab-13-redis/src/main/resources/redis/token-bucket.lua"
```

In lab 13, a fixed window let 20 of 24 requests through within 0.11 s across its boundary; with a bucket per tenant, a quiet tenant got all 20 of its requests while another flooded. If Redis is down, the limiter falls back to a bounded local bucket.

## Locks are leases

| Release | 300 updates under GC pauses (lab 13) |
|---|---|
| `DEL` | **213–229 lost**: a worker woke after expiry and deleted the next holder's lock |
| Compare-and-delete (`unlock.lua`) | **94–103 lost**: the paused worker still wrote from an old read |
| Plus a **fencing token** checked by the resource on reads and writes | **0 lost**; 58 stale attempts refused |

A Redis lock is fine for efficiency (avoid duplicate work). For correctness, the protected resource must check a fencing token, or use the database's own locks ([Distributed locks](distributed-locks.md)).

## Running it

- **Persistence matches the data**: AOF `everysec` for positions and counters; caches need none.
- **Separate instances by failure tolerance**: write-behind counters don't share an instance (or an eviction policy) with caches.
- **`maxmemory` and an eviction policy** (`allkeys-lru` for caches, `noeviction` for state you can't lose silently).
- **Sentinel or a managed service** for failover; Redis Cluster when one primary isn't enough, keeping multi-key scripts on one hash slot (`{tenant}` hash tags).
- **Lettuce** (Spring Data Redis's default) with timeouts shorter than the caller's budget.

## Pitfalls

- **Redis as the source of truth** for anything you can't rebuild.
- **Caching cheap reads**: measure both paths first.
- **`KEYS *`, big `ASC` geo queries, huge values**: one slow command blocks every client.
- **Delete-then-update invalidation**: the worst of the deletes, 1.76–2.18% stale reads.
- **Write-behind for money**.
- **No TTL on per-tenant keys**: memory grows with every tenant and endpoint ever seen.
