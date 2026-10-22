---
title: "Messaging Fundamentals"
description: "The consumer-side decisions that decide whether messages are lost, duplicated, reordered or stuck, and the event styles that decide what services are coupled to. Measured: committing before processing lost 8 messages in 26 crashes, committing after duplicated 9, after-plus-idempotent neither; a thread pool inside a partition put 19% of events out of order where key-ordered lanes put none; Spring Kafka's old default skipped 16 orders during an outage; and event-carried state kept orders flowing through a catalog outage that failed every synchronous read."
progress: done
---

# Messaging Fundamentals

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | Services communicating through a broker instead of calling each other: commands to one consumer, events to any number |
| **FoodFleet uses** | [Kafka](kafka.md) for domain events and state; [RabbitMQ](rabbitmq.md) for task queues; the [outbox](outbox-implementation.md) to publish; an inbox to consume idempotently |
| **Decided in** | [ADR-025](../communication/ch25-asynchronous-messaging.md#8-enterprise-case-study) (consumers), [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md#8-enterprise-case-study) (event styles), [ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md#8-enterprise-case-study) (outbox and inbox) |
| **Learn it in** | [Chapter 25](../communication/ch25-asynchronous-messaging.md), [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md), [lab 07](../code-lab/lab-07-kafka.md) |
| **Related** | [Kafka](kafka.md), [Debezium CDC](debezium-cdc.md), [Idempotency store](idempotency-store.md), [AsyncAPI](asyncapi.md) |

## Vocabulary

| Term | Meaning |
|---|---|
| **Queue** | Each message goes to one consumer (competing consumers) |
| **Topic** | Each message goes to every subscribing group |
| **Queue broker** | Tracks each message's state, deletes it when acknowledged (RabbitMQ, SQS) |
| **Log** | Keeps messages for a retention period; consumers track their **offset** and can replay (Kafka, Pulsar, RabbitMQ Streams) |
| **At-most-once / at-least-once** | Lost on failure / duplicated on failure. "Exactly once" end to end is at-least-once delivery plus an idempotent consumer |
| **DLQ / DLT** | Where messages go after they can't be processed: an incident queue, not a bin |

## Measured: when to commit

[Chapter 25](../communication/ch25-asynchronous-messaging.md): 100,000 messages, a consumer crash about every 5,000, at a random point.

| Commit | Lost | Duplicate effects |
|---|---:|---:|
| Before processing (at-most-once) | **8** | 0 |
| After each message (at-least-once) | 0 | **9** |
| After every 500 (batch or periodic) | 0 | **6,180** |
| After each, **idempotent consumer** | **0** | **0** |

Commit after processing **and** make the consumer idempotent (an inbox keyed by event id, in the same transaction as the effect). Then batch commits are fine, because duplicates do nothing.

## Measured: order against parallelism

2,000 events of 500 orders on one partition, 2–20 ms each:

| Consumer | Events/s | Out of order |
|---|---:|---:|
| One thread per partition | 200 | 0 |
| Listener hands records to an 8-thread pool | 1,583 | **384 (19%)** |
| 8 lanes by hash(order id) | 1,528 | **0** |

Order is per partition and per key. Parallelise with partitions or key-ordered lanes, never with an unordered pool.

## Measured: retries, poison messages, dead letters

18,000 order events over 180 s; a poison message at 20 s; the dependency down from 60 s to 90 s:

| Strategy | Lost | Stuck at end | p99 latency |
|---|---:|---:|---:|
| Retry forever, blocking | 0 | **16,231** | — |
| 10 quick attempts, then log and skip (Spring Kafka's old default) | **16** | 0 | 28.8 s |
| Blocking backoff, not-retryable straight to the DLT | 0 | 0 | 30.9 s |
| Retry topics 1 s / 10 s / 60 s, classified | 0 | 0 | 28.3 s (8.2 s in a partial outage) |

- **Never retry forever, never skip silently**: classify (a validation failure will never succeed), back off, then dead-letter.
- **Blocking retries for ordered state** (kitchen tickets), **retry topics for independent side effects** (receipts): retry topics keep the partition moving but give up order.

## Three event styles

| Style | Carries | Consumers depend on the producer for | FoodFleet uses it for |
|---|---|---|---|
| **Event notification** | "Something happened" and an id | Reading the details (a callback) | Business facts: `OrderDelivered` → rating request |
| **Event-carried state transfer** | The entity's state, versioned | Nothing at read time | Reference data: menus, restaurants, zones |
| **Event sourcing** | Every change, as the record of truth | — (it's the owner's own store) | Rider wallets ([chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md)) |

[Chapter 19](../architecture-patterns/ch19-event-driven-architecture.md) simulated a lunch rush with the catalog API down for two minutes: synchronous reads failed 100% of orders; notifications with callbacks accepted 13% sold-out items and made 1.18 million calls when catalog returned; state transfer made **0** calls and accepted **0.00%** sold-out items, at a p99 staleness of 79 ms, for about 2.4× the bytes on the bus.

## Rules FoodFleet applies everywhere

- Publish through an **outbox**, never as a second write after the database commit ([outbox](outbox-implementation.md)).
- **Key by the entity** whose order matters; every message carries an **event id**; state events carry a **version**.
- Consumers commit after processing, deduplicate through an **inbox**, and dead-letter what they can't process; every DLT is **alerted and owned**.
- Schemas are registered and checked for compatibility; events carry ids, not personal data.
- Lag is alerted on growth, per partition.
