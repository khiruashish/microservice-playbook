---
title: "Kafka Topic Design"
description: "FoodFleet's topics as the chapters decided them, generated from the platform model: events, compacted state topics and saga command channels, with keys, partitions, producers and consumers; the producer, consumer, schema and durability settings every topic shares; and the six names the chapters gave to two topics, with a single naming convention proposed to replace them."
progress: done
---

# Kafka Topic Design

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Kafka carries most of the communication between FoodFleet's services ([communication map](communication-map.md)). This page lists what flows through it, the settings every topic and client shares, and one problem the chapters left behind: they named the same topics in six different ways.

## Three kinds of traffic

| Kind | What it carries | Example | Consumers keep | Decision |
|---|---|---|---|---|
| **Events** | A fact that happened, small, for others to react to | `OrderPlaced` on `order-events` | Whatever their reaction needs | [ADR-025](../communication/ch25-asynchronous-messaging.md) |
| **State** (compacted) | The current state of an entity, keyed by its id; the newest record per key is kept forever | A menu item's name, price and availability | A local replica, newest version wins | [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md) |
| **Commands** | A request to one participant in the place-order saga | `kitchen.accept` | Nothing: they act and reply | [ADR-014](../architecture-patterns/ch14-saga-pattern.md) |

All three are written through a transactional outbox in the producer's database, so a message is sent if and only if the business change commits ([ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md)).

## The topics

From the platform model ([how](index.md#how-these-pages-are-made)). "Also" lists the other names chapters used for the same topic:

--8<-- "code/foodfleet/model/generated/topics.md"

Three topics are assumed: the model needs them to be complete, but no chapter built them. `foodfleet.zones.zone` and `foodfleet.delivery.rider-status` follow ADR-019's pattern for reference data; `payment-events` is how the saga and the reporting store learn of payment outcomes.

## Keys and partitions

**The key decides the order.** Kafka keeps records with the same key in one partition, in order, and nothing else is ordered. So each topic is keyed by the entity whose events must not be reordered:

- `order-events` by **order id**: an order's `OrderPlaced`, `OrderAccepted` and `OrderDelivered` arrive in that order, while different orders are processed in parallel ([ADR-025](../communication/ch25-asynchronous-messaging.md)).
- State topics by **entity id**: compaction keeps the latest record per key, so the key must be the entity ([ADR-019](../architecture-patterns/ch19-event-driven-architecture.md)).
- Saga commands by **saga id**, one per order.

!!! pitfall "Two keys for one stream"
    [Chapter 1](../foundations/ch01-architecture-styles.md)'s modular monolith publishes `OrderPlaced` keyed by **restaurant id**, which keeps a restaurant's orders in order but puts all of a busy restaurant's orders on one partition. [ADR-025](../communication/ch25-asynchronous-messaging.md) keys `order-events` by **order id**. The later decision holds, and moving from one key to the other changes which records are ordered relative to each other, so consumers that relied on per-restaurant order (a kitchen display, say) need their own ordering before the switch.

**Partitions are sized for the slowest consumer.** A consumer group can't use more consumers than there are partitions, so a topic needs at least as many partitions as its slowest consumer needs instances at peak, plus room to grow ([ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md)). Chapter 69 measured 12 consumers on 6 partitions: 6 sat idle. `order-events` has 24. Adding partitions to a keyed topic later moves keys to new partitions and breaks their order, so the count is set in design review and changed only with a migration plan.

## Settings every topic shares

These come with the platform's Kafka starter, so services don't choose them:

| Where | Setting | Why | Decision |
|---|---|---|---|
| Producers of events | `linger.ms=20`, `batch.size=131072`, `compression.type=lz4` | 920–953 requests a second instead of 66,000–69,000 at 10,000 records a second, for 11 ms more median latency | [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| Producers awaiting a reply | Low linger | Commands are latency-sensitive | [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| All producers | `acks=all`, idempotence on | No acknowledged record lost to a broker failure; no duplicates from producer retries | [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| Topics | `min.insync.replicas=2` | With `acks=all`, an acknowledged record is on at least two brokers | [chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| Consumers | Commit after processing; an inbox for duplicates; `ErrorHandlingDeserializer` | At-least-once delivery made safe | [ADR-025](../communication/ch25-asynchronous-messaging.md) |
| Ordered consumers | Blocking exponential backoff up to 31 s, then `<topic>-dlt` | Order is kept: nothing overtakes a failing record | [ADR-025](../communication/ch25-asynchronous-messaging.md) |
| Independent consumers | `@RetryableTopic` at 1 s, 10 s and 60 s, then their own DLT | A failing record doesn't block the rest | [ADR-025](../communication/ch25-asynchronous-messaging.md) |
| Consumers writing to a database | Batch listeners, one transaction per poll, idempotent inserts | 45,000–59,000 rows a second instead of 1,384–1,554 | [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| Slow per-record work | Key-ordered concurrency inside the consumer | 16,600–16,800 records a second from one consumer, order kept per key | [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| Alerts | Lag growing for 2 minutes; any message in a DLT | DLT messages page the owning team and are replayed after the fix | [ADR-025](../communication/ch25-asynchronous-messaging.md) |
| Every message | `event-id`, `correlation-id`, `causation-id`, `traceparent` headers | Deduplication, tracing, debugging | [ADR-028](../communication/ch28-idempotency-correlation.md) |

## Schemas

Every topic has an AsyncAPI 3.0 document in the producer's repository, listing its consumers, and every schema is registered in the schema registry ([ADR-027](../communication/ch27-api-contracts.md)). The registry checks compatibility as `FULL_TRANSITIVE`: a new schema must be readable by every consumer of every earlier version, and old data readable by the new one. [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md) asked only for backward compatibility; ADR-027 is later and stricter, so it applies.

Consumers are tolerant readers: they ignore unknown fields and map unknown enum values to a default. A change of meaning is a new field, never a changed one.

Events carry no personal data beyond ids ([ADR-019](../architecture-patterns/ch19-event-driven-architecture.md)). That keeps compacted topics, which keep records forever, out of the reach of erasure requests: personal data stays in the owning service, where it can be crypto-shredded ([ADR-075](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)).

## Retention

- **State topics** are compacted: the latest record per key is kept indefinitely, so a new consumer can rebuild its replica from the beginning.
- **Events and commands**: no chapter decided their retention. Two constraints set the minimum: longer than any consumer might be down and still need to catch up, and longer than a DLT message might wait to be replayed after a fix. Kafka's default of seven days covers both for consumers that alert after two minutes of lag.
- **Topics the reporting store is built from** need more. [ADR-034](../data/ch34-reporting-analytics.md) makes the reporting store rebuildable by replaying the order, payment and restaurant events, so those topics must keep their whole history, or at least as much as any report covers: long retention with Kafka's tiered storage, or a lake fed by the same topics, which ADR-034 already expects for long-history analysis. No chapter set the period; it's a decision to make before the first rebuild is needed, not during it.

## Naming: six names for two topics

A search of the chapters' code and pages finds the order and menu topics under these names:

| Name | Where | Pattern |
|---|---|---|
| `foodfleet.order.placed.v1` | [Chapter 1](../foundations/ch01-architecture-styles.md), the modular monolith's externalised event | domain, event, version |
| `foodfleet.order.events` | [Chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md), the outbox relay and Debezium's routing | domain, "events" |
| `order-events` | [Chapter 25](../communication/ch25-asynchronous-messaging.md) and ADR-025 | entity-events |
| `orders` | ADR-069's partition count | plural entity |
| `foodfleet.menu.item-availability-changed.v1` | [Chapter 2](../foundations/ch02-characteristics-principles.md), menu-service's published events | domain, event, version |
| `foodfleet.catalog.menu-items` | [Chapter 19](../architecture-patterns/ch19-event-driven-architecture.md) and ADR-019 | context, entity |

Each chapter's choice was reasonable on its own. Together they mean a new team can't guess a topic's name, and access rules, quotas and dashboards can't be written by pattern.

### A proposal

This is a proposal, not one of the 77 decisions; FoodFleet would record it as a new ADR that supersedes the naming parts of ADR-019 and ADR-025:

| Kind | Pattern | Example |
|---|---|---|
| State | `foodfleet.<context>.<entity>` (ADR-019, unchanged) | `foodfleet.catalog.menu-items` |
| Events | `foodfleet.<context>.<entity>-events` | `foodfleet.ordering.order-events` |
| Commands to a participant | `foodfleet.<context>.commands` | `foodfleet.kitchen.commands` |
| Replies to the orchestrator | `foodfleet.<context>.saga-replies` | `foodfleet.ordering.saga-replies` |
| Dead letters | `<topic>-dlt` (ADR-025, unchanged) | `foodfleet.ordering.order-events-dlt` |

The reasoning:

- **The context comes first**, because ownership, access control and quotas follow the bounded context: `foodfleet.kitchen.*` belongs to the restaurant-partner team.
- **One events topic per entity, not one per event type.** Kafka orders records only within a partition of one topic. `OrderPlaced` and `OrderCancelled` on separate topics could be read in the wrong order; on one topic keyed by order id, they can't.
- **No version in the name.** The schema registry versions schemas and enforces compatibility. A topic gets a new name (`-v2`) only for a change no consumer can read, which `FULL_TRANSITIVE` makes a deliberate act.
- **Commands go to the participant's topic**, with the operation (`authorize`, `capture`, `void`) in the message type, so each participant consumes one topic.

Renaming a live topic means running both for a while: producers write to both, consumers move one by one, and the old topic is retired when its consumer groups show no lag and no members, the same evidence-based retirement [ADR-071](../governance-org/ch71-api-governance.md) uses for HTTP APIs.
