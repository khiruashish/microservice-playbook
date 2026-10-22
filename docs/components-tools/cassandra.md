---
title: "Apache Cassandra"
description: "The wide-column database FoodFleet doesn't use (ADR-029): what Cassandra is for (huge key-partitioned data and writes accepted in every region), why its consistency is a per-operation choice (QUORUM vs ONE, chapter 3), query-first data modelling with partition keys, and the signals that would justify it. FoodFleet's volumes fit PostgreSQL with partitioning and BRIN (a time index of 168 kB instead of 79 MB; dropping a day 82–83 kB of WAL instead of 171 MB)."
progress: done
---

# Apache Cassandra

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A distributed, masterless wide-column database (Dynamo-style replication, Bigtable-style storage): data partitioned by key across a ring, replicated per data centre, consistency chosen per query, writes on a log-structured merge tree |
| **FoodFleet uses** | **Not adopted.** No data set needs it: history fits partitioned PostgreSQL tables and regions are handled per tenant ([chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)) |
| **Decided in** | [ADR-029](../data/ch29-polyglot-persistence.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 3](../foundations/ch03-distributed-systems-fundamentals.md) (CAP, PACELC, tunable consistency), [chapter 29](../data/ch29-polyglot-persistence.md) |
| **Related** | [PostgreSQL](postgresql.md), [MongoDB](mongodb.md), [Kafka](kafka.md) |

## Where it fits

From chapter 29's table of when to add a specialised store:

| Access pattern | First choice | Add a store when |
|---|---|---|
| Huge key-partitioned data, multi-region writes | — | Cassandra, DynamoDB, with access patterns designed up front |

Cassandra is strong when:

- Data is **far larger than one primary** can hold or write: billions of rows, sustained high write rates.
- Every read is by **a known key**: a device's readings, a user's timeline, a session.
- Writes must be accepted **in several regions at once**, with no single primary to fail over.

It's weak at ad-hoc queries, joins, transactions across partitions and anything not designed for in advance.

## Consistency is per operation

[Chapter 3](../foundations/ch03-distributed-systems-fundamentals.md) classifies Dynamo-style stores, Cassandra among them, as **PA/EL**: available under partitions and low-latency otherwise, tunable per operation. "Is Cassandra CP?" has no single answer:

| Read + write level (replication factor 3) | Behaviour |
|---|---|
| `ONE` + `ONE` | Fastest; a read may miss a just-acknowledged write |
| `QUORUM` + `QUORUM` | 2 + 2 > 3: a read overlaps the latest write's replicas |
| `LOCAL_QUORUM` | Quorum within the local data centre: the usual choice across regions |
| `ALL` | Any replica down fails the operation |

Concurrent writes to one cell resolve by **last write wins** on timestamps, so clock skew can drop writes; **lightweight transactions** (`IF NOT EXISTS`, Paxos) give compare-and-set at several times the cost.

## Model by query

Tables are designed per query, and the **partition key** decides where data lives:

```sql title="One table per query (illustrative CQL)"
CREATE TABLE rider_positions_by_day (
  rider_id   uuid,
  day        date,
  at         timestamp,
  lat        double,
  lon        double,
  PRIMARY KEY ((rider_id, day), at)        -- partition by rider and day, sorted by time
) WITH CLUSTERING ORDER BY (at DESC)
  AND default_time_to_live = 7776000;      -- 90 days
```

Bucketing by day keeps partitions bounded. A second access pattern ("all riders in a zone") means a second table, written alongside.

## FoodFleet's equivalent

Rider position history, the obvious candidate, was measured on PostgreSQL in chapter 29: daily partitions with BRIN on time gave a **168 kB** time index instead of 79 MB, and retention by dropping a partition wrote **82–83 kB** of WAL instead of 171 MB. Chapter 29 puts the next step at hundreds of millions of rows a day, and even then a time-series or columnar store (TimescaleDB, ClickHouse) before a wide-column one.

## Pitfalls

- **Modelling it like a relational database**: secondary indexes and `ALLOW FILTERING` scan the cluster.
- **Unbounded partitions**: one rider for all time in one partition; bucket by time.
- **Tombstones**: deletes and TTLs leave markers that slow reads until compaction; avoid queue-like delete patterns.
- **`QUORUM` across regions** where `LOCAL_QUORUM` was meant: every write waits for another continent.
- **Repairs not scheduled**: replicas drift.
- **Adopting it for scale you don't have**: a cluster to operate for data PostgreSQL holds comfortably.
