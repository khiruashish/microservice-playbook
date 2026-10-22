---
title: "Caffeine"
description: "Caffeine as the in-process (L1) cache: W-TinyLFU eviction, expireAfterWrite and refreshAfterWrite, async loading with single flight per key, and Spring Boot's spring.cache.type=caffeine. Why an in-process level matters (chapter 32: 13,760–14,152 menu-card requests/s with a 5 s local cache in front of Redis, against 6,211–6,422 with Redis alone) and its price, staleness per pod. Lab 13 builds the same L1 by hand to show the mechanics."
progress: done
---

# Caffeine

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A high-performance in-process Java cache (the successor to Guava's cache): bounded size or weight, time-based expiry, refresh, async loading, statistics; Spring Boot's preferred local cache provider |
| **FoodFleet uses** | Caffeine as L1 in front of Redis where per-pod staleness is acceptable ([chapter 3](../foundations/ch03-distributed-systems-fundamentals.md)'s consistency table lists "Redis L2 + Caffeine L1" for restaurant data, refreshed by TTL and invalidation events) |
| **Decided in** | [ADR-032](../data/ch32-caching-strategies.md#8-enterprise-case-study) (cache by purpose) |
| **Learn it in** | [Chapter 32](../data/ch32-caching-strategies.md), [lab 13](../code-lab/lab-13-redis.md), [chapter 4](../foundations/ch04-twelve-factor.md) (local caches and statelessness) |
| **Related** | [Spring Cache](spring-cache.md), [Multi-level caching](multi-level-caching.md), [Redis](redis-valkey.md) |

## Why a local level

[Chapter 32](../data/ch32-caching-strategies.md), menu cards, Zipf-distributed requests over 2,000 restaurants:

| Cache | Requests/s | p50 | Hit rate |
|---|---:|---:|---:|
| none | 7,240–7,768 | 1.7–1.8 ms | 0% |
| Redis, cache-aside, TTL 60 s | 6,211–6,422 | 2.1–2.2 ms | 97.8–97.9% |
| **in-process (5 s) + Redis (60 s)** | **13,760–14,152** | **< 0.1 ms** | 99.0% |

A network cache costs a round trip, much like the database's primary-key read. A local cache costs a hash lookup. The price is **staleness per instance** (5 s here) and memory in every pod; [chapter 4](../foundations/ch04-twelve-factor.md) allows local caches as long as losing them is harmless.

## What Caffeine gives you

| Feature | Use |
|---|---|
| `maximumSize` / `maximumWeight` | Bound memory; **W-TinyLFU** eviction keeps frequently used entries better than plain LRU under scans |
| `expireAfterWrite` | The staleness budget per pod |
| `refreshAfterWrite` | Stale-while-revalidate per instance: the old value is served while one reload runs |
| `AsyncCache` / `AsyncLoadingCache` | One load per key per instance: concurrent misses share a future (single flight) |
| `Expiry` (variable expiry) | Jittered or per-entry TTLs |
| `recordStats()` | Hit rate, load time, evictions, exported through Micrometer |

In Spring Boot: `spring.cache.type=caffeine` with `spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=5s`, or a `CaffeineCacheManager` bean for different settings per cache. `@Cacheable(sync = true)` then loads once per key.

## The same mechanics, by hand

[Lab 13](../code-lab/lab-13-redis.md) writes its L1 with a `ConcurrentHashMap` of futures so each step is visible:

```java title="MenuCards.java (lab 13)"
--8<-- "code/code-lab/lab-13-redis/src/main/java/dev/foodfleet/lab13/MenuCards.java:40:83"
```

Checked with 200 concurrent requests: one database read on a cold start, none within 5 s, and every request answered with Redis down. With Caffeine, an `AsyncCache` with `expireAfterWrite(5s)` replaces the map, the futures and the expiry check.

## Pitfalls

- **Unbounded local caches**: a `ConcurrentHashMap` that grows with every key ever seen.
- **Local caches for data that must agree across pods** (prices, permissions): use versioned write-through in a shared cache, or invalidation events.
- **Caching mutable objects**: a caller changes the cached instance; cache immutable values (records).
- **Expecting cluster-wide single flight**: it is per instance; N pods, N loads.
- **No stats**: a local cache with a 5% hit rate is just memory.
