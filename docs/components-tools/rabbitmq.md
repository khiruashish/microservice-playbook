---
title: "RabbitMQ"
description: "RabbitMQ for FoodFleet's task queues (rider payout jobs, document generation), where per-message acknowledgement, routing and priorities matter more than replay: exchanges and queues, push with a prefetch, quorum queues with an explicit delivery limit and a dead-letter exchange on every queue (RabbitMQ 4.0 made 20 deliveries the default, after which a message is dropped unless a DLX is set), manual acknowledgement after processing, and when to choose it over Kafka."
progress: done
---

# RabbitMQ

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A queue broker implementing AMQP 0-9-1: producers publish to **exchanges**, which route to **queues**; the broker tracks each message and deletes it once acknowledged |
| **FoodFleet uses** | RabbitMQ 4 with Spring AMQP for task queues: rider payout jobs, document generation |
| **Decided in** | [ADR-025](../communication/ch25-asynchronous-messaging.md#8-enterprise-case-study): the same consumer rules on RabbitMQ (quorum queues, DLX, delivery limit, manual ack) |
| **Learn it in** | [Chapter 25](../communication/ch25-asynchronous-messaging.md) |
| **Related** | [Messaging fundamentals](messaging-fundamentals.md), [Kafka](kafka.md), [Spring Cloud Stream](spring-cloud-stream.md) |

## The model

| Piece | Does |
|---|---|
| **Exchange** | Receives every published message and routes it: `direct` (by routing key), `topic` (by pattern), `fanout` (to every bound queue), `headers` |
| **Queue** | Holds messages until a consumer acknowledges them |
| **Binding** | Connects an exchange to a queue, with a key or pattern |
| **Prefetch** | How many unacknowledged messages the broker pushes to one consumer |
| **Dead-letter exchange (DLX)** | Where rejected, expired or over-delivered messages go |

RabbitMQ **pushes** to consumers up to their prefetch. A consumer **acknowledges** each message, or rejects it with or without requeueing. Acknowledged messages are gone: there is no replay, and ordering is lost as soon as several consumers or requeues are involved.

## Queue broker or log

| Need | RabbitMQ | [Kafka](kafka.md) |
|---|---|---|
| Work distribution, task queues | Natural: competing consumers, per-message ack | Possible; parallelism limited by partitions (share groups add queue semantics) |
| Replay, new consumers reading history | No (RabbitMQ Streams add a log) | Yes, within retention |
| Many independent subscribers | A queue per subscriber, bound to an exchange | Consumer groups, no copies |
| Ordering | Per queue, single consumer | Per partition and key, at scale |
| Routing, priorities, per-message TTL | Rich | Minimal |

FoodFleet uses Kafka for domain events (replay, many subscribers, per-order ordering) and RabbitMQ where each job must be acknowledged individually and some jobs jump the queue.

## FoodFleet's queue rules

```java title="A payout queue with Spring AMQP (chapter 25)"
@Bean
Queue riderPayouts() {
    return QueueBuilder.durable("rider-payouts")
            .quorum()
            .deliveryLimit(5)                          // then dead-lettered, not looped
            .deadLetterExchange("rider-payouts.dlx")
            .build();
}
```

- **Quorum queues** (replicated with Raft) for anything that matters; classic mirrored queues are gone in RabbitMQ 4.
- **An explicit delivery limit.** Quorum queues count deliveries; since RabbitMQ 4.0 the default limit is 20, after which the message is dead-lettered, **or dropped if no DLX is configured**. A poison message no longer loops forever, but it can disappear silently. FoodFleet sets the limit explicitly and a DLX on every queue.
- **Manual acknowledgement after processing**, with the processing idempotent (a payout job keyed by its id): a crash between work and ack redelivers.
- **A sensible prefetch**: high enough to keep a consumer busy, low enough that one slow consumer doesn't hoard messages others could process. Tens, not thousands, for slow jobs.
- **Publisher confirms** so a producer knows the broker has the message; for messages that follow a database change, the outbox still applies.

## Pitfalls

- **`requeue=true` on every failure**: the message comes straight back, at the head of the queue, forever (or until the delivery limit).
- **Auto-ack**: a message is gone the moment it's delivered; a crash loses it.
- **Unbounded queues**: set a length limit or a TTL, and alert on depth, or a slow consumer becomes memory pressure on the broker.
- **Using RabbitMQ for event history**: once acknowledged, it's gone. A new consumer can't catch up.
- **Ordering assumptions with several consumers**: competing consumers process in parallel.
