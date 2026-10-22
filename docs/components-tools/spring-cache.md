---
title: "Spring Cache"
description: "Spring's cache abstraction used correctly at FoodFleet (ADR-032): @Cacheable(sync = true) for single flight per instance, a transaction-aware RedisCacheManager so evictions happen after commit, short Redis timeouts so a slow cache is skipped, and what the annotations can't do (set-if-newer writes, stale-while-revalidate, write-behind), which chapter 32 wrote by hand. Measured: update-then-delete left 1–3 stale entries per run; set-if-newer write-through left none."
progress: done
---

# Spring Cache

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | Spring Framework's cache abstraction: `@Cacheable`, `@CachePut`, `@CacheEvict` on methods, backed by a `CacheManager` (Redis, Caffeine, JCache, a map); Spring Boot configures it from `spring.cache.*` |
| **FoodFleet uses** | A transaction-aware `RedisCacheManager` for restaurant details; hand-written caches for prices, rankings and counters |
| **Decided in** | [ADR-032](../data/ch32-caching-strategies.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 32](../data/ch32-caching-strategies.md), [lab 13](../code-lab/lab-13-redis.md) |
| **Related** | [Redis](redis-valkey.md), [Caffeine](caffeine.md), [Multi-level caching](multi-level-caching.md) |

## The annotations, configured correctly

```java title="RestaurantInfoService.java (chapter 32)"
--8<-- "code/chapters/ch32-caching-strategies/src/main/java/dev/foodfleet/ch32/info/RestaurantInfoService.java:11:43"
```

```yaml title="application.yml (chapter 32)"
--8<-- "code/chapters/ch32-caching-strategies/src/main/resources/application.yml"
```

- **`sync = true`** makes concurrent misses for one key load once per instance (single flight); N instances still make N loads.
- **A 200 ms Redis timeout**: a cache slower than the database is worse than none.
- Spring Boot's default Redis serialisation is JDK serialization (hence `Serializable`); configure JSON in a `RedisCacheConfiguration` bean for caches other services or languages read.

## Evict after commit

An eviction inside `@Transactional` runs **before** the commit unless the cache manager is transaction-aware, so a concurrent reader can put the old row straight back. A customizer fixes it:

```java title="CacheConfig.java (chapter 32)"
--8<-- "code/chapters/ch32-caching-strategies/src/main/java/dev/foodfleet/ch32/info/CacheConfig.java:7:19"
```

Even after commit, "update, then delete" leaves a race: a reader that read the old row before the commit fills the cache after the delete. [Chapter 32](../data/ch32-caching-strategies.md) measured it with 10% price changes:

| Strategy | Stale reads | Stale entries left |
|---|---:|---:|
| TTL only (60 s) | 97.00–97.02% | 200 |
| Delete key, then update the database | 1.76–2.18% | 1–3 |
| Update the database, then delete key | 0.48–0.54% | 1–3 |
| **Update, then write through with set-if-newer** | **0.02–0.03%** | **0** |
| Update, delete; fills need a lease | 0.01–0.02% | 0 |

## Where the annotations stop

| Need | Annotations? | FoodFleet |
|---|---|---|
| Rarely changing data, evicted on change | **Yes**: `@Cacheable(sync = true)`, `@CacheEvict` on a transaction-aware manager | Restaurant details |
| Writes conditional on version | No: `@CachePut` overwrites unconditionally | `ItemPriceCache` with a set-if-newer Lua script |
| Stale-while-revalidate | No (Caffeine's `refreshAfterWrite` does it per instance) | `SoftTtlCache` with a refresh lock: 9–10 recomputations instead of 160 |
| Write-behind | No | `ViewCounter` / `ViewFlusher` with batch ids |
| Decisions (checkout price, stock) | Never cached | Read the database |

The cache must be optional: if Redis is down, reads go to the database. Spring's `CacheErrorHandler` turns cache exceptions into misses instead of failed requests.

## Pitfalls

- **Self-invocation**: `@Cacheable` works through the proxy; a call from the same class skips it.
- **Caching cheap primary-key reads**: chapter 32's Redis cache served menu cards slower than PostgreSQL (2.1–2.2 ms vs 1.7–1.8 ms p50).
- **Keys built from `toString()`** or mutable objects; version keys when the cached format changes.
- **One TTL for everything, no jitter**: synchronised expiries.
- **No `CacheErrorHandler`**: a Redis outage becomes a full outage.
- **Caching `Optional.empty()` or nulls** by accident (`unless = "#result == null"`), or never caching misses when lookups of absent keys are the hot path.
