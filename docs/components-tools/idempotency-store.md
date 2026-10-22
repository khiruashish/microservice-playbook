---
title: "Idempotency Store"
description: "Making retries safe at FoodFleet (ADR-028): an Idempotency-Key on every non-idempotent API operation with side effects, claimed atomically with a lease in the service's own database, the response stored with the business write and replayed, the key passed to providers, kept 24 h; consumers deduplicate on event id in a shared store for 1 h and apply events only if the entity's version is newer. Measured on PostgreSQL 16 with 800 concurrent requests: a claimed key charged 200 of 200; check-then-insert charged 315–346; a unique constraint on payments still called the provider 800 times."
progress: done
---

# Idempotency Store

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A table or key-value store recording which requests or messages have been handled, so repeats return the stored outcome instead of repeating the side effect: API idempotency keys, consumer dedup windows, inbox tables |
| **FoodFleet uses** | A key table in each service's PostgreSQL for API operations; a shared 1-hour dedup store plus entity versions for consumers |
| **Decided in** | [ADR-028](../communication/ch28-idempotency-correlation.md#8-enterprise-case-study), [ADR-014](../architecture-patterns/ch14-saga-pattern.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 28](../communication/ch28-idempotency-correlation.md), [chapter 14](../architecture-patterns/ch14-saga-pattern.md), [chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md) |
| **Related** | [Outbox](outbox-implementation.md), [Distributed locks](distributed-locks.md), [Kafka](kafka.md), [Redis](redis-valkey.md) |

## API: claim the key first

```sql title="V1__payments_idempotency.sql (chapter 28)"
--8<-- "code/chapters/ch28-idempotency-correlation/src/main/resources/db/migration/V1__payments_idempotency.sql"
```

```java title="IdempotencyKeys.java (chapter 28)"
--8<-- "code/chapters/ch28-idempotency-correlation/src/main/java/dev/foodfleet/ch28/payments/IdempotencyKeys.java:8:41"
```

[Chapter 28](../communication/ch28-idempotency-correlation.md): 200 payment keys, each requested 4 times concurrently; "charges" are calls to the provider:

| Strategy | Charges (of 200) | Keys charged more than once |
|---|---:|---:|
| No key | 800 | 200 |
| Check, then insert | 315–346 | 83–94 |
| **Idempotency key, claimed first** | **200** | **0** |
| Check-then-insert, SERIALIZABLE | 457–487 | 135–145 |
| Unique constraint on payments | **800** | **200** (600 duplicate-key errors) |

SERIALIZABLE and constraints protect the database, not the provider: the side effect happens before the conflict is detected. The claim (insert the key with a lease, then call) comes first; repeats get the stored response, concurrent ones 409; a request with the same key and a different body gets 422. The key is passed on to the payment provider, whose own idempotency covers the gap between its charge and FoodFleet's commit.

## Consumers: how long to remember

notification-service, 9,500 duplicates from four sources:

| Dedup store | Rebalance (1–30 s) | Replay (1–3 days) | Applied twice | Ids kept |
|---|---:|---:|---:|---:|
| In-memory LRU per instance | **2,513** | 1,000 | 3,513 | 600,000 |
| Shared cache, 1 h TTL | 0 | **1,000** | 1,000 | 1.8 M |
| Inbox table, 7-day retention | 0 | 0 | 0 | **302 M** |
| **1 h cache + version on the order row** | 0 | 0 | **0** | 1.8 M |

The window must cover the slowest duplicate source; for replays days later, the entity deduplicates itself: events carry the order's version, and the consumer records the last version it acted on.

## In sagas

Participants are idempotent per (saga id, step) and return stored outcomes for repeats. Without that in one participant, chapter 14's simulation produced 165 double charges in 10,000 orders ([Temporal & Camunda](temporal-camunda.md)).

## Pitfalls

- **Check-then-insert**.
- **Relying on a unique constraint** when the side effect is outside the database.
- **Per-instance memory** for consumer dedup.
- **Keys without a lease**: a crashed request blocks the key forever; with a lease, a retry can take over.
- **Not storing the response**: a retry gets "already done" instead of the original answer.
- **Not passing the key downstream**.
