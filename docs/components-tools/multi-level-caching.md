---
title: "Multi-level Caching"
description: "Caches in layers, from the client and CDN through the gateway and an in-process L1 to Redis as L2 and the database: what each level saves, what it costs in staleness, and how FoodFleet decides per data type (ADR-032). Measured: an in-process level in front of Redis doubled menu-card throughput (13,760–14,152 requests/s, p50 under 0.1 ms); lab 13's two-level cache made one database read for 200 concurrent misses and answered every request with Redis down."
progress: done
---

# Multi-level Caching

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Several caches in sequence, each closer to the caller: HTTP caches (client, CDN), an in-process cache (L1) per instance, a shared cache (L2, e.g. Redis), then the source of truth |
| **FoodFleet uses** | ETags and `Cache-Control` at the HTTP level; Redis as the shared level for details, prices and rankings; local levels where per-pod staleness is acceptable |
| **Decided in** | [ADR-032](../data/ch32-caching-strategies.md#8-enterprise-case-study), [chapter 21](../communication/ch21-rest-api-design.md) (HTTP caching) |
| **Learn it in** | [Chapter 32](../data/ch32-caching-strategies.md), [lab 13](../code-lab/lab-13-redis.md) |
| **Related** | [Caffeine](caffeine.md), [Redis](redis-valkey.md), [Spring Cache](spring-cache.md), [CDN](cdn.md) |

## The levels

| Level | Saves | Staleness | Invalidation |
|---|---|---|---|
| Client, CDN (`Cache-Control`, `ETag`) | The whole request; with `304`, the body | `max-age`, then revalidation | TTL, purge APIs, versioned URLs |
| Gateway / BFF | Calls to services | TTL | TTL |
| **L1, in-process** | The network hop | Per pod, its TTL | TTL, or events to every pod |
| **L2, shared (Redis)** | Database load | One copy for all pods | Write-through after commit, set-if-newer, evict |
| Database | — | Source of truth | — |

Each level multiplies the places a value can be stale. Add a level when a measurement shows it pays, and write down its staleness budget.

## Measured

[Chapter 32](../data/ch32-caching-strategies.md), menu cards:

| Cache | Requests/s | p50 | Database reads/s |
|---|---:|---:|---:|
| none | 7,240–7,768 | 1.7–1.8 ms | 7,240–7,768 |
| Redis only | 6,211–6,422 | 2.1–2.2 ms | 134 |
| **L1 (5 s) + Redis (60 s)** | **13,760–14,152** | **< 0.1 ms** | 135 |

Redis alone offloaded the database but didn't speed anything up; the local level did. ADR-032 then chose **no shared cache for menu cards** in production (they're primary-key reads) and HTTP ETags instead. The two-level pattern is what [lab 13](../code-lab/lab-13-redis.md) builds to show the mechanics:

```java title="MenuCards.java (lab 13)"
--8<-- "code/code-lab/lab-13-redis/src/main/java/dev/foodfleet/lab13/MenuCards.java:40:83"
```

```console
cold start, 200 concurrent requests: database reads 1, Redis reads 1
again within 5 s: database reads 1, Redis reads 1 (all from L1)
6 s later, 200 concurrent requests: database reads 1, Redis reads 2
Redis down, 200 requests: all answered, database reads 2
L2 TTLs over 10,000 writes: 54001-65999 ms
```

- **Single flight at each level**: one load per key, whoever asks.
- **Jittered TTLs** (54–66 s here): entries written together don't expire together.
- **Each level optional**: an L2 outage means slower, not down.

## Keeping levels consistent

- **Per-pod staleness is the L1's TTL** unless every pod hears about changes: publish invalidation events (Redis pub/sub, Kafka, CDC) and evict locally, still with the TTL as a safety net.
- **Write the L2 after commit and only if newer**; L1s then catch up within their TTL.
- **Data that decisions depend on skips the cache**: checkout prices and stock are read from the database.

## Pitfalls

- **Stacked TTLs**: a 60 s CDN + 60 s L1 + 60 s L2 can serve data three minutes old.
- **An L1 for data that must agree across pods**: two pods show two prices.
- **No metrics per level**: you can't tell which level is earning its keep.
- **The cold start after a deploy**: every pod's L1 is empty at once; L2 and single flight absorb it.
