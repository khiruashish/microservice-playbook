---
title: "Apache Kafka"
description: "Kafka as FoodFleet's event backbone: topics keyed by entity, partitions sized for the slowest consumer, producer settings by purpose (linger.ms 20 and lz4 cut produce requests 70× and broker CPU 22–24× for 11 ms of latency), consumers that commit after processing and dead-letter, batch sinks (1,400–1,550 rows a second with a commit per record, 45,000–59,000 batched), compacted topics for state, and the topic naming, ACLs and lag alerts around it. Kafka 4.2 with Spring for Apache Kafka 4.1."
progress: done
---

# Apache Kafka

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A partitioned, replicated log: producers append, consumers read at their own offset, records are kept for a retention period (or compacted to the latest per key) |
| **FoodFleet uses** | Kafka 4.2 (KRaft, no ZooKeeper) with Spring for Apache Kafka 4.1; domain events, state topics, saga commands; the platform's producer and consumer profiles in its Spring Boot starter |
| **Decided in** | [ADR-025](../communication/ch25-asynchronous-messaging.md#8-enterprise-case-study) (consumers), [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md#8-enterprise-case-study) (throughput settings), [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md#8-enterprise-case-study) (compacted state topics) |
| **Learn it in** | [Chapter 25](../communication/ch25-asynchronous-messaging.md), [chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md), [lab 07](../code-lab/lab-07-kafka.md), [FoodFleet's topic design](../food-delivery-platform/kafka-design.md) |
| **Related** | [Messaging fundamentals](messaging-fundamentals.md), [Debezium](debezium-cdc.md), [Spring Cloud Stream](spring-cloud-stream.md), [KEDA](keda.md) |

## Topics and keys

- **Names**: `foodfleet.<context>.<entity>` for state (compacted), `foodfleet.<context>.<entity>-events` for events, `<topic>-dlt` for dead letters ([conventions](../appendix/conventions.md)).
- **Keys decide order**: one key's records stay in order on one partition. `order-events` is keyed by order id, so an order's `OrderPlaced` can't be read after its `OrderCancelled`. Keying by restaurant would also keep order, but chapter 68 found one partition carrying 19–28% of orders that way.
- **Partitions are sized for the slowest consumer** at peak, plus growth: a consumer group can't use more consumers than partitions. `order-events` has 24. Adding partitions to a keyed topic moves keys and breaks their order, so the count is set in design review.
- **Compaction** keeps the latest record per key forever: state topics (menus, restaurants) that a new consumer bootstraps from.

## Producers: settings by purpose

```java title="KafkaProfiles.java (chapter 69)"
--8<-- "code/chapters/ch69-kafka-throughput-tuning/src/main/java/dev/foodfleet/ch69/settings/KafkaProfiles.java:17:41"
```

[Chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md), 10,000 records a second for 10 s:

| Settings | p50 | Produce requests | Broker CPU |
|---|---:|---:|---:|
| `linger.ms=0`, 16 KB batches (Kafka 3.x defaults) | 0.4 ms | **66,433–69,432** | **5.75–6.02 s** |
| `linger.ms=5`, 16 KB (Kafka 4.x defaults) | 3.7–3.8 ms | 3,259–3,343 | 0.69–0.75 s |
| `linger.ms=20`, 128 KB | 11.5 ms | **920–953** | **0.24–0.27 s** |

| Compression (order events) | Ratio | Throughput vs none |
|---|---:|---|
| lz4 | 4.53× | about 70% |
| snappy | 4.38–4.39× | about 90% |
| zstd | 6.75× | about 35–45% |
| gzip | 7.31× | **about an eighth** |

- **`acks=all` and idempotence** (Kafka 4's defaults): an acknowledged event survives a broker failure, and producer retries don't duplicate it.
- **One request in flight** lost 58–60% of throughput over a 1 ms round trip; idempotence keeps order with up to 5.
- **Never send from business code after a database commit**: that's a dual write; publish through the [outbox](outbox-implementation.md).

## Consumers

```yaml title="application.yml (lab 07)"
--8<-- "code/code-lab/lab-07-kafka/src/main/resources/application.yml:10:30"
```

- **Commit after processing** (`enable-auto-commit: false`, container-managed acks), **deduplicate** with an inbox, **classify and dead-letter** failures ([messaging fundamentals](messaging-fundamentals.md)).
- **Consumers scale with partitions and stop there**: 12 consumers on 6 partitions left 6 idle. One consumer with 32 key-ordered lanes handled 16,600–16,800 records a second, more than 6 consumers one record at a time.
- **The sink sets the ceiling**: writing to PostgreSQL with a commit per record managed 1,384–1,554 rows a second; one transaction per poll with `reWriteBatchedInserts=true`, 45,000–46,000; COPY with big polls, up to 58,500. Commit offsets after the write and make the insert idempotent.

## Operating it

| Concern | FoodFleet's rule |
|---|---|
| Lag | Alerted on 2 minutes of growth, per partition; KEDA scales consumers on it ([KEDA](keda.md)) |
| ACLs | Each service's principal may write only its own topics and read what the platform model says it consumes (generated, `code/foodfleet/model/generated/policies/kafka-acls.txt`) |
| Schemas | Registered, backward-compatible checks; events carry ids, not personal data |
| Retention | No chapter decided it: Kafka's 7-day default covers consumer outages and DLT replays; topics the reporting store rebuilds from need their whole history ([topic design](../food-delivery-platform/kafka-design.md#retention)); state topics compacted |
| Auto-creation | Off: topics are created by the platform with their partitions and DLTs |

## Pitfalls

- **An unordered thread pool inside a listener**: 19% of events out of order in chapter 25.
- **The old "log and skip" default**: orders silently lost during an outage.
- **More consumers than partitions**: idle consumers, no extra throughput.
- **Re-partitioning a keyed topic** without a migration plan.
- **Using Kafka as a task queue** where per-message acknowledgement and priorities matter: that's [RabbitMQ](rabbitmq.md)'s job (or Kafka 4's share groups, still new).
