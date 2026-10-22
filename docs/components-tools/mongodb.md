---
title: "MongoDB"
description: "The document database FoodFleet considered for menus and didn't adopt (ADR-029): what a document store is good at, what chapter 29 measured when the same menus were stored as JSONB documents in PostgreSQL (whole reads 13–14× faster, single-price updates 15–16× the WAL, cross-menu queries about 180× slower), the access patterns that would justify MongoDB, consistency settings to understand first, and how it would fit beside a relational source of truth."
progress: done
---

# MongoDB

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A document database: JSON-like documents (BSON) in collections, flexible schemas, secondary indexes, aggregation pipelines, replica sets and sharding |
| **FoodFleet uses** | **Not adopted.** Proposed for menus; PostgreSQL's JSONB covered the access pattern ([ADR-029](../data/ch29-polyglot-persistence.md#8-enterprise-case-study)) |
| **Learn it in** | [Chapter 29](../data/ch29-polyglot-persistence.md), [chapter 3](../foundations/ch03-distributed-systems-fundamentals.md) (consistency settings) |
| **Related** | [PostgreSQL](postgresql.md), [Debezium](debezium-cdc.md) (supports MongoDB change streams) |

## What documents are good for

A document store fits when the application reads and writes **a whole aggregate by key**, the aggregate's shape varies, and queries rarely cut across aggregates. A restaurant's menu looks like that: sections, items, option groups, read together on every menu screen.

## Measured: the same menus as documents

[Chapter 29](../data/ch29-polyglot-persistence.md) stored 2,000 restaurants' menus (192,000 items) twice in PostgreSQL 16: as five normalised tables, and as one JSONB document per restaurant. This is the document model's trade-off, whichever database holds the document:

| Workload | Tables | Document |
|---|---:|---:|
| Storage | 151 MB | **16 MB** |
| Read a whole menu | 307–308/s, 13 ms | **4,132–4,371/s, 0.9–1.0 ms** |
| Read one item | **29,428–41,913/s** | 15,692–16,085/s |
| Change one price | **5,711–5,986/s, 598–618 B WAL** | 1,173–1,258/s, **9.4–9.5 kB WAL** |
| Cheapest 20 veg items under ₹150 in a city | **0.8–1.0 ms** | 168–169 ms |

Documents won for reading an aggregate whole and lost for everything else: partial updates rewrite the value, concurrent edits to one aggregate serialise, and queries across aggregates read every document. FoodFleet kept the tables as the source of truth and **rebuilds a JSONB menu card in the same transaction**, getting the fast whole-menu read without a second database or a sync path.

## When MongoDB would be the right choice

From chapter 29's table of when to add a specialised store:

| Access pattern | First choice | Add a document database when |
|---|---|---|
| Read or replace a whole aggregate by key | A JSONB column or table beside the relational model | Aggregates are huge, schema-free across tenants, and never queried across |

Other signals: a team that needs horizontal write scaling of document data beyond one primary, or a data model with no cross-aggregate transactions at all. Each additional store also brings, permanently, backups and restore drills, upgrades, monitoring, access control, on-call knowledge and a sync path.

## If you use it

- **Consistency is per operation.** Write concern (`w: majority`), read concern (`majority`, `linearizable`) and read preference (primary or secondaries) decide what a read can see; "is MongoDB CP or AP?" has no single answer ([chapter 3](../foundations/ch03-distributed-systems-fundamentals.md)).
- **Multi-document transactions exist** (since 4.0) but cost more than in a relational database; design aggregates so they aren't needed.
- **Schema still matters**: use JSON Schema validation on collections, and version documents.
- **Spring Data MongoDB** maps documents to records; Testcontainers' MongoDB module runs a replica set for tests.
- **Publishing events**: the outbox works with a collection and change streams; Debezium has a MongoDB connector.

## Pitfalls

- **Choosing a store by category** ("menus are documents") without measuring the access patterns.
- **Unbounded arrays in a document**: documents have a 16 MB limit and every update rewrites the array.
- **Reading from secondaries** and expecting your own writes.
- **A second store without a sync path**: dual writes ([outbox](outbox-implementation.md)).
