---
title: "Debezium CDC"
description: "Change data capture with Debezium: reading committed changes from PostgreSQL's write-ahead log through a replication slot, turning FoodFleet's outbox rows into Kafka records with the Outbox Event Router, and what it costs: Kafka Connect to run, a slot that holds WAL until it is read, and duplicates after a restart. Measured in chapter 15: events delayed about 80 ms at p99 against about 600 ms for polling, and 178 duplicates against polling's 96 in 10,000 orders, all absorbed by the inbox."
progress: done
---

# Debezium CDC

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A set of Kafka Connect source connectors that stream a database's committed changes (from PostgreSQL's WAL, MySQL's binlog, MongoDB's change streams) as events |
| **FoodFleet uses** | Debezium's PostgreSQL connector with the Outbox Event Router for high-volume services' outboxes; the polling relay everywhere else |
| **Decided in** | [ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md), [lab 09](../code-lab/lab-09-outbox-cdc.md), [chapter 34](../data/ch34-reporting-analytics.md) (CDC into reporting) |
| **Related** | [Outbox implementation](outbox-implementation.md), [Kafka](kafka.md), [PostgreSQL](postgresql.md) |

## How it reads

1. PostgreSQL runs with `wal_level=logical`; Debezium creates a **publication** (which tables) and a **replication slot** (its position in the WAL).
2. The connector decodes committed changes with the built-in `pgoutput` plugin, in commit order, and writes them to Kafka through Kafka Connect.
3. Connect stores the connector's offset; on restart it resumes from the last **flushed** offset, so recent changes can be sent again.

Because it reads commits rather than querying, CDC sees every change, in commit order, without polling load on the database, and without the position gap a "where position > N" reader has ([lab 11](../code-lab/lab-11-cqrs-event-sourcing.md)).

## Outbox, not raw tables

Streaming the `orders` table itself would publish the table's schema as the API, every column change included. FoodFleet streams only the **outbox** and lets the Event Router shape each row into an event:

```json title="debezium/order-outbox-connector.json (lab 09)"
--8<-- "code/code-lab/lab-09-outbox-cdc/debezium/order-outbox-connector.json"
```

| Setting | Does |
|---|---|
| `table.include.list` + `publication.autocreate.mode: filtered` | The publication covers the outbox only |
| `route.by.field` + `route.topic.replacement` | `aggregate_type = order` → `foodfleet.ordering.order-events` |
| `table.field.event.key` | `aggregate_id` becomes the Kafka key: one order's events stay in order |
| `fields.additional.placement` | The event id and type travel as headers for the consumers' inbox |
| `heartbeat.*` | Advances the slot when nothing is written, so idle periods don't hold WAL |
| `${file:...}` | The password comes from a mounted secret file, not the JSON |

## Measured

[Chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md), 10,000 orders with the same injected faults for every design:

| Design | Lost | Phantom | Duplicate messages | Loyalty points off by |
|---|---:|---:|---:|---:|
| Dual write: commit, then publish | **169** | 0 | 0 | −10,959 |
| Outbox + polling + inbox | 0 | 0 | 96 | **0** |
| Outbox + **CDC** + inbox | 0 | 0 | **178** | **0** |

CDC produced more duplicates because its offset is flushed periodically, so a restart replays further back. Every relay is at-least-once; the inbox absorbs it. In exchange, CDC delivered events at about **80 ms p99** against about 600 ms for a polling relay.

## The replication slot is the risk

A slot keeps every WAL segment Debezium hasn't confirmed. If the connector stops, WAL grows until the disk fills, and PostgreSQL stops accepting writes. FoodFleet's guards:

- `max_slot_wal_keep_size` caps what a slot may hold (the slot is invalidated instead of filling the disk; the connector then needs a new snapshot).
- An alert on slot lag (`pg_replication_slots`, `confirmed_flush_lsn` against the current LSN) well before the cap.
- Heartbeats for low-traffic databases.
- After a primary failover, the slot must exist on the new primary (PostgreSQL 17's failover slots, or recreate and resnapshot).

## Pitfalls

- **Streaming business tables as the event API**: consumers couple to the schema.
- **No heartbeat on a quiet database**: WAL accumulates behind the slot even with nothing to publish.
- **Assuming exactly-once**: restarts replay; consumers need an inbox.
- **Large transactions**: a bulk update emits one event per row, all at once.
- **Snapshots**: a new connector (or a lost slot) reads the whole table first; on an outbox table, clean it up regularly so there's little to read.
