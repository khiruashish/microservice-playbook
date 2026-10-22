---
title: "Part 4 — Data Management"
description: "FoodFleet's data ownership map and a decision guide for data in microservices: choosing stores, transactions across services, consistency guarantees, caching, scaling and migrating databases, and reporting across services, each measured on PostgreSQL and Redis."
progress: done
---

# Part 4 — Data Management

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Part 2 gave every table one owner, and Part 3 decided how services talk. Part 4 deals with the consequences: each service now chooses its own store, no transaction spans two services, copies of data are always a little behind, caches go stale, databases have to grow and change while traffic keeps flowing, and the questions finance asks still span every service.

Every chapter runs its claims against real PostgreSQL 16 clusters (with streaming and logical replicas where it matters) and Redis 7, with the verified output in the text and the code to reproduce it.

## FoodFleet's data ownership map

Who owns each piece of data, where it lives, and how everyone else gets it. **Owner** is the only service that writes it; everything in the last column is a copy, and every copy says how stale it may be.

| Data | Owner | Store | Others get it by | Chapters |
|---|---|---|---|---|
| Menus, dishes, prices | catalog-service | PostgreSQL (tables + JSONB menu cards), search in PostgreSQL | Events (state transfer); Redis copies with set-if-newer; HTTP caching with ETags | [29](ch29-polyglot-persistence.md), [32](ch32-caching-strategies.md) |
| Restaurants, opening hours | restaurant-service | PostgreSQL | `@Cacheable` reads evicted after commit; events | [32](ch32-caching-strategies.md) |
| Orders | order-service | PostgreSQL | Outbox events; the reporting store; never a shared table | [30](ch30-distributed-transactions.md), [33](ch33-database-scaling.md), [34](ch34-reporting-analytics.md) |
| Payments, refunds | payment-service | PostgreSQL | Outbox events; saga replies | [30](ch30-distributed-transactions.md) |
| Rider positions | rider-location-service | Redis (live), PostgreSQL BRIN + partitions (history) | Redis GEO search; batched history | [29](ch29-polyglot-persistence.md) |
| Customers, addresses, profiles | customer-service (profile-service in chapter 31) | PostgreSQL | API; events; read-your-writes via consistency tokens | [31](ch31-data-consistency.md), [33](ch33-database-scaling.md) |
| Loyalty points | loyalty-service | PostgreSQL, atomic updates | API | [31](ch31-data-consistency.md) |
| Carts | cart-service | Two regions, merged as OR-Sets | API | [31](ch31-data-consistency.md) |
| Rankings, view counts | catalog-service | Redis (stale-while-revalidate, write-behind) | API | [32](ch32-caching-strategies.md) |
| Revenue, operational reports | reporting-service | PostgreSQL reporting store (facts, deltas, views) | Reports with an as-of time; later a lake | [34](ch34-reporting-analytics.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| Which database for this service? | PostgreSQL by default; a second store only for a measured need | [29](ch29-polyglot-persistence.md) | A JSONB menu read 14× faster than five joins, price changes 5× slower; Redis 3.8–5.6× PostgreSQL's write rate for positions |
| One business change across two services | Outbox-driven saga, not 2PC | [30](ch30-distributed-transactions.md) | One in-doubt 2PC transaction cut a hot dish to 2–6 orders/s; the outbox design took every order through a 10 s outage |
| Users seeing old data after their own writes | Session guarantees with consistency tokens | [31](ch31-data-consistency.md) | Stale reads after one's own write: 15% from a replica, 0 with a token |
| Lost updates under concurrency | Atomic updates, versions, CRDTs | [31](ch31-data-consistency.md) | Read-modify-write lost 15.5% of loyalty points; an atomic update none |
| Slow reads, hot keys | Caching by purpose | [32](ch32-caching-strategies.md) | TTL-only caches stale in 97% of reads; set-if-newer under 0.05%; stale-while-revalidate stopped stampedes |
| Too many connections, slow migrations | Fleet-wide pool budgets, lock-safe DDL, expand/contract | [33](ch33-database-scaling.md) | An instant DDL behind a report blocked writes for 10 s; a rename failed thousands of statements in a rolling deploy, expand/contract none |
| One primary can't take the writes | Sharding by the key queries start from, through slots | [33](ch33-database-scaling.md) | `hash mod N` moved 80% of data going from 16 to 20 shards; fixed slots 19.5% |
| Reports that need several services' data | A reporting store fed by events or CDC | [34](ch34-reporting-analytics.md) | Reports on the primary cut its writes by 67–69%; counting events overstated revenue by 7.1%, state with versions was exact |

## The chapters

<div class="grid cards" markdown>

-   **[29. Polyglot Persistence](ch29-polyglot-persistence.md)**

    ---

    Choosing a store per access pattern, and what keeping two in sync costs. *JSONB vs tables, search, Redis GEO, BRIN.*

-   **[30. Distributed Transactions](ch30-distributed-transactions.md)**

    ---

    2PC with `PREPARE TRANSACTION` vs sagas vs the outbox. *Two PostgreSQL clusters, in-doubt transactions.*

-   **[31. Data Consistency](ch31-data-consistency.md)**

    ---

    Session guarantees, lost updates, multi-region merges. *Streaming replicas, OR-Sets, hybrid logical clocks.*

-   **[32. Caching Strategies](ch32-caching-strategies.md)**

    ---

    What to cache, invalidation races, stampedes, write-behind. *PostgreSQL + Redis under load.*

-   **[33. Database Scaling & Zero-Downtime Migrations](ch33-database-scaling.md)**

    ---

    Pools, DDL locks, backfills, expand/contract, shard keys. *pgbench against live schema changes.*

-   **[34. Reporting & Analytics Across Services](ch34-reporting-analytics.md)**

    ---

    Reporting stores, CDC, materialized views, correct incremental aggregates. *Logical replication between two clusters.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Building services already** (🔵 L2) | 29 → 32 → 33 | [Part 5: Security](../security/index.md) |
| **Leading a team** (🟣 L3) | 31 → 30 → 32 → 33 → 34 | [Part 7: Observability](../observability/index.md) |
| **Designing a platform** (🔴 L4) | 29 → 30 → 31 → 33 → 34 | [Part 10: Performance & Scalability](../performance-scalability/index.md) |
| **Preparing for interviews** | 30, 31, 32, 33 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-029 · Chapter 29</span><span class="what">PostgreSQL by default; Redis for copies and ephemeral state; nothing else without measurements.</span></li>
<li><span class="when">ADR-030 · Chapter 30</span><span class="what">No distributed transactions between services; outbox-driven sagas with idempotent participants.</span></li>
<li><span class="when">ADR-031 · Chapter 31</span><span class="what">Session guarantees with consistency tokens; atomic updates; CRDTs for multi-writer data.</span></li>
<li><span class="when">ADR-032 · Chapter 32</span><span class="what">Cache by purpose: evict after commit, versioned write-through, stale-while-revalidate, write-behind for counters.</span></li>
<li><span class="when">ADR-033 · Chapter 33</span><span class="what">Fleet-wide pool budgets, lock-safe migrations with expand/contract, slot-based sharding only when needed.</span></li>
<li><span class="when">ADR-034 · Chapter 34</span><span class="what">Reports come from a reporting store built from change events, never from the services' databases.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch29-polyglot-persistence,chapters/ch30-distributed-transactions,chapters/ch31-data-consistency,chapters/ch32-caching-strategies,chapters/ch33-database-scaling,chapters/ch34-reporting-analytics -am verify
    ```
    JDK 25 and Maven 3.9+; the container tests need Docker (skipped without it). Each chapter's `verify/` folder holds its experiments, which need PostgreSQL 16 (and Redis 7 for chapters 29 and 32) and python3 with the standard library only.
