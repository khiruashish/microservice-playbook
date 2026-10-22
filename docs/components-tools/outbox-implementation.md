---
title: "Outbox Implementation"
description: "The transactional outbox as FoodFleet builds it: the business change and its event as two rows in one local transaction; a polling relay that claims rows with FOR UPDATE SKIP LOCKED, publishes in order, waits for acknowledgement and stops at the first failure; Debezium as the high-volume alternative; the inbox that makes at-least-once harmless; and cleanup. Measured: dual writes lost or invented about 1.6% of events, outbox plus inbox none; two relays racing on PostgreSQL 16 claimed 100 and 50 rows with none in common."
progress: done
---

# Outbox Implementation

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Publishing events reliably by writing them to an `outbox` table in the same transaction as the change, then relaying them to the broker |
| **FoodFleet uses** | The platform library's `Outbox.append` (requires a transaction) and polling relay; Debezium with the Outbox Event Router for high-volume services; an `inbox` table in every consumer |
| **Decided in** | [ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md), [lab 09](../code-lab/lab-09-outbox-cdc.md), [lab 07](../code-lab/lab-07-kafka.md) (the inbox) |
| **Related** | [Debezium CDC](debezium-cdc.md), [Kafka](kafka.md), [Idempotency store](idempotency-store.md), [Temporal & Camunda](temporal-camunda.md) (saga commands go through the outbox too) |

## Why: the dual write

Saving an order and then sending its event are two writes with no transaction between them. A crash between them loses the event; a failed commit after a successful send invents one. [Chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md) injected the same faults into 10,000 orders for every design:

| Design | Lost | Phantom | Loyalty points off by |
|---|---:|---:|---:|
| Dual write: commit, then publish | **169** | 0 | −10,959 |
| Dual write: publish, then commit | 0 | **164** | +11,393 |
| Outbox + polling, consumer **without** inbox | 0 | 0 | **+12,519** |
| Outbox + polling + inbox | 0 | 0 | **0** |
| Outbox + CDC + inbox | 0 | 0 | **0** |

The outbox fixes the producer; only outbox **and** inbox are exact, because every relay is at-least-once.

## The table and the write

```sql title="V1__orders_outbox.sql (lab 09)"
--8<-- "code/code-lab/lab-09-outbox-cdc/src/main/resources/db/migration/V1__orders_outbox.sql"
```

```java title="PlaceOrder.java (lab 09)"
--8<-- "code/code-lab/lab-09-outbox-cdc/src/main/java/dev/foodfleet/lab09/PlaceOrder.java:10:41"
```

Mandatory columns: the **event id** (consumers deduplicate on it), the **aggregate id** (the Kafka key, so one order's events stay in order), the **type**, and the **payload** as the event's public shape, not the table's.

## The polling relay

```java title="OutboxRelay.java (lab 09)"
--8<-- "code/code-lab/lab-09-outbox-cdc/src/main/java/dev/foodfleet/lab09/OutboxRelay.java:16:82"
```

- **`FOR UPDATE SKIP LOCKED`** lets several relays run without waiting on or double-publishing each other's rows. Two relays racing on PostgreSQL 16 claimed 100 and 50 of 150 rows, none in common ([lab 09](../code-lab/lab-09-outbox-cdc.md)).
- **Stop at the first failure**, so an order's later event never overtakes an earlier one.
- **Mark only what the broker acknowledged** (`acks=all`). A crash between send and commit republishes: at-least-once.
- **Unpublished rows, not "after position N"**: a row whose transaction commits late is picked up on a later run instead of being skipped, which is the trap a checkpoint-based reader falls into ([lab 11](../code-lab/lab-11-cqrs-event-sourcing.md)).

## Polling or CDC

| | Polling relay | [Debezium](debezium-cdc.md) |
|---|---|---|
| Delay | Up to one poll interval (p99 about 600 ms in chapter 15) | About 80 ms p99, after commit |
| Database load | A query per interval per relay | Reading the WAL |
| Moving parts | Code in the service | Kafka Connect, a replication slot to monitor |
| FoodFleet | Most services | High-volume services |

## The inbox

The consumer records each processed event id in an `inbox` table **in the same transaction as its effect**; a redelivered id is skipped ([lab 07](../code-lab/lab-07-kafka.md)). An effect that is naturally idempotent (an upsert keyed by the event id, `ON CONFLICT DO NOTHING`) can replace it.

## Housekeeping

- **Delete published rows** after a retention window long enough to debug a publishing problem, in small batches, so the table and its partial index stay small. Partitioning the outbox by day makes this a `DROP`.
- **Clean the inbox** after longer than any redelivery can happen: the broker's retention plus the longest DLT replay.
- **Alert on the oldest unpublished row's age**: a stuck relay shows there first.

## Pitfalls

- **Publishing from an `@TransactionalEventListener(AFTER_COMMIT)`**: still a dual write, with a crash window after the commit.
- **The table's schema as the event**: consumers couple to internal columns.
- **One relay per instance without `SKIP LOCKED`**: duplicates or contention.
- **Skipping a failed row to keep going**: events overtake each other.
- **No inbox because "the relay is reliable"**: chapter 15's +12,519 points.
