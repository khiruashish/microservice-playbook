---
title: "Data & Caching"
description: "Where FoodFleet's data lives and who owns it, generated from the platform model: PostgreSQL for every service's business data, Redis only for copies and ephemeral state. The consistency each kind of data gets, every cache with its purpose and staleness budget, how schemas change without downtime, how reports are built without touching the services' databases, and what each choice cost or saved when its chapter measured it."
progress: done
---

# Data & Caching

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

FoodFleet's data rules fit in four sentences. Every table has one owner ([ADR-012](../architecture-patterns/ch12-database-per-service.md)). PostgreSQL holds every service's business data; Redis holds only copies and ephemeral state ([ADR-029](../data/ch29-polyglot-persistence.md)). Money is never eventually consistent ([ADR-003](../foundations/ch03-distributed-systems-fundamentals.md)). Nobody reads another service's database, not even for reports ([ADR-034](../data/ch34-reporting-analytics.md)).

## Who stores what

From the [platform model](index.md#how-these-pages-are-made):

--8<-- "code/foodfleet/model/generated/stores.md"

Each service has its own database user and schema on a shared PostgreSQL cluster; payment-service has its own instance ([ADR-012](../architecture-patterns/ch12-database-per-service.md)). Table ownership is recorded in `table-owners.txt` and checked in CI. Before that rule, an audit found 14 of 23 sampled statements touching other services' tables, including three services writing `orders.status`.

Reading the table shows one open question: **two services keep live rider positions in Redis**, `tracking-gateway` (to fan positions out to customers' streams) and `dispatch-optimizer` (to match riders to orders). Both are copies fed from the same `tracking-positions` topic, which is allowed for ephemeral state, but no chapter says which is authoritative when they differ, for instance when a rider's app reconnects to another tracking instance. The answer should be neither: the topic is the source, and each copy is rebuilt from it.

## Why PostgreSQL for almost everything

In [chapter 29](../data/ch29-polyglot-persistence.md), the proposals were MongoDB for menus, Elasticsearch for search, Redis for riders and prices, and a time-series database for history, for a platform team of eight. Measured on PostgreSQL and Redis instead:

| Need | FoodFleet's answer | Measured |
|---|---|---|
| Menu reads | A JSONB menu card per restaurant, rebuilt in the same transaction as the menu edit | 14× cheaper than the joins; cross-restaurant queries still about 1 ms |
| Dish search | Full-text search with prefix matching, and a trigram fallback for typos | Found misspelt dishes in 29 of 29 test queries; typo searches about 50 ms at the median |
| Live rider positions | Redis, silent riders pruned after 30 s | 3.8–5.6× PostgreSQL's write rate at the same durability |
| Rider history | PostgreSQL, partitioned by day with BRIN indexes, kept 7 days | Dropping a day is a metadata operation |
| Prices | PostgreSQL, with a Redis copy fed from the outbox | Zero wrong prices through crashes and outages |

Any other store needs an ADR with its access pattern, the measurements on PostgreSQL and Redis, and its operating cost. A dedicated search engine is expected when relevance tuning, synonyms or facets are needed.

## Consistency by kind of data

| Data | Guarantee | How | Decision |
|---|---|---|---|
| Money, single-use resources (coupons) | Linearizable within the owner | Conditional writes; the owner's synchronous standby | [ADR-003](../foundations/ch03-distributed-systems-fundamentals.md) |
| Anything across services | Eventual, with sagas | Idempotent steps, outbox and inbox | [ADR-014](../architecture-patterns/ch14-saga-pattern.md), [ADR-030](../data/ch30-distributed-transactions.md) |
| A user reading what they just wrote | Read-your-writes | A `Consistency-Token` (the primary's WAL position) sent with reads; a replica serves it only once it has caught up, waiting up to 50 ms, else the primary | [ADR-031](../data/ch31-data-consistency.md) |
| Counters and balances | No lost updates | Atomic or conditional updates only | [ADR-031](../data/ch31-data-consistency.md) |
| Records people edit | No silent overwrite | `If-Match` with the version | [ADR-021](../communication/ch21-rest-api-design.md) |
| The shared cart, edited offline too | Merges without losing items | An OR-Set with hybrid-logical-clock stamps | [ADR-031](../data/ch31-data-consistency.md) |
| Rider wallets | Every balance explainable | Event-sourced, with projections for the views | [ADR-013](../architecture-patterns/ch13-cqrs-event-sourcing.md) |
| Ordering across services | Never by wall clock | Versions and per-key order | [ADR-003](../foundations/ch03-distributed-systems-fundamentals.md), [ADR-028](../communication/ch28-idempotency-correlation.md) |

[Chapter 31](../data/ch31-data-consistency.md) measured why: moving reads to asynchronous replicas produced 15% stale re-reads of a user's own data, 19% of tracking polls going backwards across two replicas, and 15.5% of loyalty points lost to read-modify-write. With the tokens and atomic updates, all three were 0, with 2.9% of reads falling back to the primary.

## Caches

Each cache states its purpose, how stale it may be, and its strategy ([ADR-032](../data/ch32-caching-strategies.md)):

| Data | Strategy | Staleness | Why this one |
|---|---|---|---|
| Restaurant details | `@Cacheable(sync = true)`, evicted after the transaction commits | Until the next commit | "Update, then delete" left stale entries when a read raced the update |
| Item prices | Cache-aside reads, write-through after commit, every write through a set-if-newer script; 30-minute safety TTL | Under 0.05% of reads see a just-changed price late | No stale prices left behind |
| Home-screen rankings | Stale-while-revalidate with a refresh lock | One refresh old | Expiry caused 80 recomputations at once; now one, and no user waits |
| View counters | Write-behind with an atomic claim, on a separate Redis | Up to ~1 s of views lost if that Redis restarts | One database statement a second |
| Menu cards | **No shared cache**; HTTP caching with ETags | — | Redis was slower (2.1–2.2 ms p50) than PostgreSQL's primary-key read (1.7–1.8 ms) |
| Checkout and payments | **No cache**; they read the database | — | Correctness over speed |

Every cache is optional at runtime: if Redis is down, reads go to the database. Hit rates and stale serves are monitored per cache.

## Changing schemas without downtime

[Chapter 33](../data/ch33-database-scaling.md) measured three outages and turned each into a rule ([ADR-033](../data/ch33-database-scaling.md)):

| What went wrong | Measured | The rule |
|---|---|---|
| Deploys exhausted `max_connections` | Little throughput gained past ~16 connections on 2 vCPUs, while latency inside the database grew 13–17× by 256 | Fixed-size pools, budgeted fleet-wide including the deploy surge, checked in CI |
| An "instant" migration froze checkout | An `ADD COLUMN` behind a 12-second query blocked writes for 10 seconds | `lock_timeout` 2 s, retried by the pipeline; indexes built `CONCURRENTLY`; constraints added `NOT VALID` and validated later |
| A column rename failed old instances mid-deploy | Thousands of failed statements; expand/contract, none | Renames, type changes and drops by expand/contract across releases, with throttled keyset backfills |

A rename now takes four releases instead of one. Sharding is deferred until write load needs it; when it does, orders will be sharded by customer through a 4,096-slot map, with the slot embedded in the order id.

## Reports without touching the services

Finance and operations need reports across orders, restaurants and payments. [Chapter 34](../data/ch34-reporting-analytics.md) measured the alternatives:

- Composing a 30-day report from the services' APIs took **2.7–2.9 s** and **620–656 calls**.
- Running reports on the orders primary cut its write throughput by **67–69%**.
- A first projection that counted events overstated revenue by **7.1%**, and using UTC days misdated **4%** of orders.

So reporting-service owns a reporting store fed by events (CDC where a service publishes none), keeps facts by key and version, uses each city's time zone for business days, and returns every report with its as-of time and a stale flag against a 5-minute objective ([ADR-034](../data/ch34-reporting-analytics.md)). It is rebuildable by replay, which sets a retention requirement on the topics it reads ([Kafka topic design](kafka-design.md#retention)).

## Recovery tiers

Not all data needs the same protection ([ADR-046](../resilience/ch46-disaster-recovery.md)):

| Tier | Data | Replication | Failover |
|---|---|---|---|
| 0 | Orders, payments; coupons too, since ADR-003 gives promotions a synchronous standby | Synchronous to a standby in another zone (`ANY 1` of two); asynchronous to the recovery region; WAL streamed there | Automatic between zones; between regions only by runbook, fencing first |
| 1 | Other services' business data | Asynchronous standby in the recovery region | By runbook |
| 2 | Rebuildable data (the reporting store, caches) | Backups only | Restore or rebuild |

In-zone synchronous commits cost 1.3–1.7 ms instead of 0.9–1.0 ms and lost nothing when a zone failed. A lost region costs at most the cross-region replication lag. Only orders and payments are named as Tier 0 by the chapter; the tier of every other service is the model's reading of ADR-046, and each service should declare its own.
