---
title: "Spring Cloud Stream"
description: "Spring Cloud Stream's functional model, where a Supplier, Function or Consumer bean is bound to a destination by configuration and a binder (Kafka, RabbitMQ) does the rest: how chapter 19's menu replica consumes a compacted topic with keyed publishing, a consumer group, retries and a dead-letter queue in a dozen lines of YAML, testing with the in-memory test binder, and why FoodFleet's services mostly use Spring for Apache Kafka directly."
progress: done
---

# Spring Cloud Stream

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A framework that binds plain Java functions to message destinations through a **binder** (Kafka, Kafka Streams, RabbitMQ, and others), so the code doesn't name the broker |
| **FoodFleet uses** | It in [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md)'s menu replica; most services use Spring for Apache Kafka directly, through the platform's consumer starter ([ADR-025](../communication/ch25-asynchronous-messaging.md#8-enterprise-case-study)) |
| **Version** | Spring Cloud Stream 5.0 (Spring Cloud 2025.1) |
| **Learn it in** | [Chapter 19](../architecture-patterns/ch19-event-driven-architecture.md) |
| **Related** | [Kafka](kafka.md), [RabbitMQ](rabbitmq.md), [Messaging fundamentals](messaging-fundamentals.md) |

## Functions, bound by configuration

```java title="MenuEventsConsumer.java (chapter 19)"
--8<-- "code/chapters/ch19-event-driven-architecture/src/main/java/dev/foodfleet/ch19/app/ordering/MenuEventsConsumer.java:8:19"
```

```yaml title="application.yml (chapter 19)"
--8<-- "code/chapters/ch19-event-driven-architecture/src/main/resources/application.yml"
```

| Bean type | Becomes | Binding name |
|---|---|---|
| `Supplier<T>` | A producer, polled (or reactive) | `<name>-out-0` |
| `Function<T, R>` | A consumer that publishes its result | `<name>-in-0`, `<name>-out-0` |
| `Consumer<T>` | A consumer | `<name>-in-0` |
| `StreamBridge.send("menuItems-out-0", event)` | Publishing from ordinary code (a controller, an outbox relay) | Any output binding |

Everything a broker needs is configuration: the destination (`foodfleet.catalog.menu-items`), the **group** (one consumer group per service, so each service sees each event once), the message key (`payload.itemId`, so per-item order holds and compaction works), retries (`max-attempts`), and the dead-letter queue (`enable-dlq`).

## Testing without a broker

The test binder runs the same functions, bindings and serialization in memory. With the Kafka binder also on the classpath, `spring.cloud.stream.default-binder=test` selects it:

```java title="MenuEventsStreamTest.java (chapter 19)"
--8<-- "code/chapters/ch19-event-driven-architecture/src/test/java/dev/foodfleet/ch19/app/MenuEventsStreamTest.java"
```

For a real broker, Testcontainers' Kafka module ([Testcontainers](testcontainers.md)).

## Stream or Spring for Apache Kafka

| | Spring Cloud Stream | Spring for Apache Kafka |
|---|---|---|
| Code | Functions; no broker types | `@KafkaListener`, `KafkaTemplate`, records and headers |
| Switching brokers | A binder change | A rewrite |
| Kafka-specific control | Through binder properties | Direct: error handlers, `DefaultErrorHandler` classification, `@RetryableTopic`, batch listeners, transactions |
| FoodFleet | Simple state replicas | Everything else: ordered consumers with blocking backoff, retry topics, batch sinks ([ADR-025](../communication/ch25-asynchronous-messaging.md#8-enterprise-case-study), [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md#8-enterprise-case-study)) |

The abstraction pays off when the broker might change or the logic is a pure transformation. FoodFleet's consumer rules (classify failures, back off for ordered state, retry topics for side effects, batch writes per poll) need the Kafka-specific knobs, so most services skip the abstraction.

## Pitfalls

- **Forgetting the group**: an anonymous group per instance, so every instance processes every message.
- **No key on output bindings**: per-entity order and compaction are lost.
- **`enable-dlq` without anyone owning the DLQ**: dead letters pile up unseen.
- **Mixing `@KafkaListener` and Stream bindings** in one service: two sets of error handling to reason about.
