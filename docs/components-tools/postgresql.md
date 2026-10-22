---
title: "PostgreSQL"
description: "PostgreSQL 16 as the source of truth for every FoodFleet service that owns business data: schema per service on a shared cluster (a database of its own for payments), JSONB beside tables (whole-menu reads 13–14× faster, everything else slower), full-text plus trigram search that found 29 of 29 test queries, partitions with BRIN for history, row-level security for pooled tenants, and the operational rules measured under load: small fixed pools, lock_timeout on every migration, CONCURRENTLY and NOT VALID, expand/contract."
progress: done
---

# PostgreSQL

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | The open-source relational database: transactions, constraints, SQL, plus JSONB, full-text search, extensions (pg_trgm, PostGIS), partitioning, logical replication |
| **FoodFleet uses** | PostgreSQL 16 for every service's own data; schema per service on a shared cluster, a database of its own for payment-service and services that need isolation |
| **Decided in** | [ADR-012](../architecture-patterns/ch12-database-per-service.md#8-enterprise-case-study) (ownership), [ADR-029](../data/ch29-polyglot-persistence.md#8-enterprise-case-study) (PostgreSQL by default), [ADR-033](../data/ch33-database-scaling.md#8-enterprise-case-study) (pools and migrations), [ADR-040](../security/ch40-api-security.md#8-enterprise-case-study) / [ADR-075](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#8-enterprise-case-study) (row-level security) |
| **Learn it in** | [Chapter 29](../data/ch29-polyglot-persistence.md), [chapter 33](../data/ch33-database-scaling.md), [lab 02](../code-lab/lab-02-persistence.md), [lab 18](../code-lab/lab-18-multi-tenancy.md) |
| **Related** | [Flyway & Liquibase](flyway-liquibase.md), [Connection pooling](connection-pooling.md), [Redis](redis-valkey.md), [Debezium](debezium-cdc.md) |

## PostgreSQL covers more than expected

[Chapter 29](../data/ch29-polyglot-persistence.md) was asked to justify MongoDB for menus, Elasticsearch for search, Redis for riders and a time-series database for history. Measured on PostgreSQL first:

| Need | PostgreSQL feature | Result |
|---|---|---|
| Read a whole menu | A JSONB menu card rebuilt in the same transaction as the tables | **4,132–4,371 reads/s, 0.9–1.0 ms** vs 307–308/s from joins (13–14×); but one price change wrote 15–16× the WAL, so tables stay the source |
| Dish search, with typos | Full-text with prefix matching, then trigram similarity | **29 of 29** queries found, 14 ms median; typos about 50 ms |
| Position history | Daily partitions, BRIN on time | Time index 168 kB instead of 79 MB; dropping a day 82 kB of WAL instead of 171 MB |
| Live rider positions | — | Redis took 3.8–5.6× the write rate at matched durability: positions went to Redis |

Result: PostgreSQL plus Redis, not five stores. Any further store needs an ADR with the access pattern, measurements on both, and its operating cost.

## Ownership

Each service owns its tables; nobody else reads or writes them ([ADR-012](../architecture-patterns/ch12-database-per-service.md#8-enterprise-case-study)). Most services get a **schema** and a role on a shared cluster; payment-service gets its own database (compliance and failure isolation). Other services get the data through APIs or events, never a shared table.

## Operating rules, measured

| Rule | Measured in [chapter 33](../data/ch33-database-scaling.md) |
|---|---|
| **Small, fixed pools**, budgeted across the fleet and its deploy surge | On 2 vCPUs most throughput was there by 16 connections; p99 inside the database grew from 5.7–6.0 ms at 16 to 76–98 ms at 256 |
| **`lock_timeout` 2 s** on migrations, retried by the pipeline | An `ADD COLUMN` behind a 12 s report blocked every writer for 10 s; with a 500 ms lock timeout and retries, the longest write was 504–506 ms |
| **`CREATE INDEX CONCURRENTLY`**, in its own migration | Longest write 49–53 ms instead of 820–1,195 ms; a failed build leaves an invalid index, so services report them |
| **Constraints `NOT VALID`, then `VALIDATE`** | Longest write 10–14 ms instead of 117–122 ms |
| **No table rewrites in review** | A volatile default rewrote the table: throughput 8–11% for 3–5 s |
| **Expand/contract** for renames and type changes | Renaming in place failed 3,947–4,894 operations during a rolling deploy; expand/contract, 0 |
| **Throttled keyset backfills** that copy rows that differ | One `UPDATE` took throughput to 1–2%; batches of 1,000 with a 20 ms pause kept 88% |

## Features FoodFleet relies on

- **Row-level security, forced**, for tables shared by tenants, with a non-owner role and the tenant set per transaction ([lab 18](../code-lab/lab-18-multi-tenancy.md)).
- **`FOR UPDATE SKIP LOCKED`** for outbox relays and work queues ([lab 09](../code-lab/lab-09-outbox-cdc.md)).
- **Logical replication** for CDC, with slots capped and alerted ([Debezium](debezium-cdc.md)).
- **Optimistic locking** with a version column ([lab 02](../code-lab/lab-02-persistence.md)); `INSERT … ON CONFLICT` for idempotent consumers.
- **Read replicas** for reports ([chapter 34](../data/ch34-reporting-analytics.md)).

## Pitfalls

- **A pool per pod sized for peak traffic**, multiplied by pods and a deploy surge: `max_connections` exhausted.
- **DDL without `lock_timeout`**: a waiting `ALTER` blocks every query queued behind it.
- **`pgjdbc` without `reWriteBatchedInserts=true`** for batch inserts: chapter 69 measured 29,000–34,000 rows/s without it, 45,000–46,000 with.
- **Session-level `SET` for per-request state** on pooled connections ([lab 18](../code-lab/lab-18-multi-tenancy.md)).
- **An unmonitored replication slot**: WAL grows until the disk fills.
- **Superuser connections from services**: they bypass row-level security.
