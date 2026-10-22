---
title: "Pattern Selection Decision Trees"
description: "Ten decision trees for the choices that come up in every microservice design: whether to split at all, how services talk, which protocol, how a change spans services, which store, which cache strategy, what consistency a read needs, which resilience patterns a call needs, functions or containers, and how to isolate tenants. Each follows the chapter that measured the options, with the number that decides the close calls."
progress: done
---

# Pattern Selection Decision Trees

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Each tree turns one chapter's "Choosing" section into questions. They're starting points: the chapter has the measurements and the cases where the obvious branch is wrong.

## 1. Should this be a separate service?

```mermaid
flowchart TD
    A["A new capability, or a module to extract"] --> B{"Does another team need to<br/>release it independently?"}
    B -- yes --> S["A separate service"]
    B -- no --> C{"Does it have a different load shape,<br/>scaling or availability need?"}
    C -- yes --> S
    C -- no --> D{"A different compliance<br/>or security boundary?"}
    D -- yes --> S
    D -- no --> M["A module in a modular monolith,<br/>with enforced boundaries"]
    S --> E{"Is it outside every<br/>dependency cycle?"}
    E -- no --> F["Break the cycle first"]
    E -- yes --> G["Extract by strangler fig,<br/>with shadow traffic"]
```

From [chapters 1](../foundations/ch01-architecture-styles.md), [6](../foundations/ch06-service-decomposition.md), [16](../architecture-patterns/ch16-strangler-fig-migration.md) and [74](../governance-org/ch74-maturity-migration-roadmap.md). The close call is extraction order: in chapter 74's real monolith, choosing by independent changes per new remote dependency freed 32% of changes for 340 remote dependencies; choosing the most-changed module first, 33% for 1,083.

## 2. Synchronous or asynchronous?

```mermaid
flowchart TD
    A["Service A needs something from service B"] --> B{"Does A need B's answer<br/>to respond to its caller?"}
    B -- no --> E["Event or command through Kafka,<br/>with an outbox"]
    B -- yes --> C{"Is B's data reference data<br/>A could keep a copy of?"}
    C -- yes --> R["Event-carried state transfer:<br/>a local replica from a compacted topic"]
    C -- no --> D{"Can A answer without B<br/>if B is down?"}
    D -- yes --> F["Synchronous call<br/>with a fallback"]
    D -- no --> G["Synchronous call on the<br/>critical path: budget its availability"]
```

From [chapters 3](../foundations/ch03-distributed-systems-fundamentals.md), [19](../architecture-patterns/ch19-event-driven-architecture.md) and [25](../communication/ch25-asynchronous-messaging.md). In chapter 19, synchronous calls failed 100% of orders during a catalog outage; state transfer, none. Every required call multiplies availability: four at 99.9% give 99.6%.

## 3. Which protocol for a synchronous call?

```mermaid
flowchart TD
    A["A synchronous API"] --> B{"Who calls it?"}
    B -- "partners, the public" --> REST["REST + OpenAPI"]
    B -- "apps with many screens" --> C{"Do screens need different<br/>shapes of the same data?"}
    C -- yes --> GQL["GraphQL in a BFF,<br/>with batch loading"]
    C -- no --> REST
    B -- "other services" --> D{"Latency-sensitive, streaming,<br/>or deadlines across hops?"}
    D -- yes --> GRPC["gRPC"]
    D -- no --> REST2["REST through HTTP service clients"]
    B -- "live updates to apps" --> E{"Server to client only?"}
    E -- yes --> SSE["Server-sent events"]
    E -- no --> WS["WebSocket"]
```

From [Part 3](../communication/index.md). GraphQL only with batching: without it, one screen made 19 backend calls instead of REST's 3 ([ch. 24](../communication/ch24-graphql.md)).

## 4. A change that spans services

```mermaid
flowchart TD
    A["One business operation changes<br/>data in several services"] --> B{"Can the boundaries move so<br/>one service owns it all?"}
    B -- yes --> L["Move them: a local transaction"]
    B -- no --> C{"How many steps,<br/>and is money involved?"}
    C -- "two or three, no money" --> CH["Choreography: services<br/>react to each other's events"]
    C -- "many, or money" --> O["Orchestrated saga"]
    O --> D{"Long-running, with timers<br/>or people in the loop?"}
    D -- yes --> W["A workflow engine"]
    D -- no --> H["An orchestrator with a saga log"]
```

From [chapters 14](../architecture-patterns/ch14-saga-pattern.md) and [30](../data/ch30-distributed-transactions.md). Never two-phase commit across services: one in-doubt transaction cut a hot dish to 2–6 orders a second. Whatever you choose, participants must be idempotent: 165 double charges in 10,000 sagas without it.

## 5. Which store?

```mermaid
flowchart TD
    A["A service needs to store data"] --> B{"Is it the source of truth<br/>for business data?"}
    B -- yes --> PG["PostgreSQL"]
    B -- no --> C{"A copy, or ephemeral<br/>(sessions, positions, counters)?"}
    C -- yes --> RD["Redis"]
    C -- no --> D{"Has PostgreSQL been measured<br/>and found insufficient?"}
    D -- no --> PG
    D -- yes --> X["The specialised store,<br/>with an ADR and its operating cost"]
```

From [chapter 29](../data/ch29-polyglot-persistence.md). PostgreSQL covered menus (JSONB, 14× cheaper than joins), dish search (29 of 29 misspellings found) and history; Redis took positions at 3.8–5.6× PostgreSQL's write rate.

## 6. Which caching strategy?

```mermaid
flowchart TD
    A["Reads are too slow or too costly"] --> B{"Is it a primary-key read<br/>from the service's own database?"}
    B -- yes --> N["Probably no shared cache:<br/>HTTP ETags instead"]
    B -- no --> C{"May a reader see<br/>a stale value?"}
    C -- "never (money, stock at checkout)" --> DB["Read the database"]
    C -- "briefly, after a change" --> D{"Must the cache never hold<br/>an older value than the database?"}
    D -- yes --> V["Write-through with<br/>version-guarded writes"]
    D -- no --> E["Cache-aside, evicted<br/>after commit"]
    C -- "yes, it's a ranking or a dashboard" --> SWR["Stale-while-revalidate<br/>with a refresh lock"]
```

From [chapter 32](../data/ch32-caching-strategies.md). For menu cards, Redis (2.1–2.2 ms p50) was slower than PostgreSQL's primary-key read (1.7–1.8 ms).

## 7. What consistency does this read need?

```mermaid
flowchart TD
    A["A read endpoint"] --> B{"Is the reader the one<br/>who just wrote?"}
    B -- yes --> RYW["Read-your-writes:<br/>a consistency token"]
    B -- no --> C{"Would going backwards<br/>confuse the reader?"}
    C -- yes --> MON["Monotonic reads:<br/>send the newest token seen"]
    C -- no --> D{"Is the read followed by<br/>a write based on it?"}
    D -- yes --> ATOM["An atomic or conditional<br/>update, not read-then-write"]
    D -- no --> EV["Any replica"]
```

From [chapter 31](../data/ch31-data-consistency.md): 15% stale re-reads, 19% backwards polls and 15.5% lost points without these; 0 with them, at 2.9% of reads on the primary.

## 8. Which resilience patterns does this call need?

```mermaid
flowchart TD
    A["A remote call"] --> T["Always: a timeout from the<br/>deadline budget, and a bulkhead"]
    T --> B{"Is it idempotent?"}
    B -- yes --> R["Retry at one layer,<br/>within a budget, with jitter"]
    B -- no --> K["No retry, or an<br/>idempotency key first"]
    R --> C{"Can the dependency fail<br/>for longer than a blip?"}
    K --> C
    C -- yes --> CB["A circuit breaker,<br/>time-based window"]
    C -- no --> D
    CB --> D{"Can the caller do<br/>something useful without it?"}
    D -- yes --> FB["A fallback, approved by product"]
    D -- no --> FF["Fail fast with a clear error"]
```

From [chapters 18](../architecture-patterns/ch18-resilience-patterns.md), [42](../resilience/ch42-failure-modes.md) and [43](../resilience/ch43-resilience4j-deep-dive.md). Retries at three layers sent 27 calls per request to a dead dependency; one layer within a 10% budget, 1.4.

## 9. Function or container?

```mermaid
flowchart TD
    A["A workload"] --> B{"Steady traffic above the<br/>cost crossover?"}
    B -- yes --> C["Containers"]
    B -- no --> D{"Event-driven, scheduled<br/>or bursty?"}
    D -- yes --> E{"Can it tolerate a<br/>cold start?"}
    E -- yes --> F["A function, with an AOT cache"]
    E -- no --> G["Scale-to-zero container,<br/>or keep one warm"]
    D -- no --> C
```

From [chapter 76](../governance-org/ch76-serverless-microservices.md): functions were cheaper below about 30 thumbnails or 100 fee quotes a second, sustained; the AOT cache cut the JVM part of a cold start from 246–329 ms to 113–196 ms.

## 10. How to isolate tenants?

```mermaid
flowchart TD
    A["Many customers on one platform"] --> B{"Regulation or contract<br/>requiring physical separation?"}
    B -- yes --> SILO["Silo: their own database or cell"]
    B -- no --> C{"Very large, or noisy?"}
    C -- yes --> SILO
    C -- no --> POOL["Pool: shared tables,<br/>forced row-level security,<br/>tenant set per transaction"]
    POOL --> D["Per-tenant rate limits<br/>and bulkheads"]
    SILO --> D
    D --> E["A home region per tenant<br/>for personal data"]
```

From [chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md): a per-session tenant leaked into 177 of 193 forgotten requests, a per-transaction one into none; bulkheads kept other tenants' p99 at 8–11 ms instead of 25–38 ms.
