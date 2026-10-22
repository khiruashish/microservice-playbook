---
title: "Flyway & Liquibase"
description: "Versioned schema migrations shipped with each service: FoodFleet uses Flyway, with lock_timeout 2 s on its connection, CREATE INDEX CONCURRENTLY in a migration of its own (without IF NOT EXISTS), constraints added NOT VALID and validated in the next migration, and renames done by expand/contract across releases. Measured on PostgreSQL 16 under load: a plain index build stalled writes for up to 1.2 s and a concurrent one for 53 ms; renaming in place failed thousands of operations during a rolling deploy. Liquibase compared."
progress: done
---

# Flyway & Liquibase

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | Schema migration tools: an ordered history of changes in the repository, applied once each and recorded in the database. **Flyway** uses plain SQL files (`V1__…sql`); **Liquibase** uses changelogs (XML, YAML, JSON or SQL) of changesets with rollbacks and preconditions |
| **FoodFleet uses** | Flyway, run by Spring Boot at start-up; Hibernate `ddl-auto: validate` |
| **Decided in** | [ADR-012](../architecture-patterns/ch12-database-per-service.md#8-enterprise-case-study) (each service owns its schema), [ADR-033](../data/ch33-database-scaling.md#8-enterprise-case-study) (lock-safe migrations) |
| **Learn it in** | [Lab 02](../code-lab/lab-02-persistence.md), [chapter 33](../data/ch33-database-scaling.md) |
| **Related** | [PostgreSQL](postgresql.md), [Testcontainers](testcontainers.md), [Argo Rollouts](argo-rollouts.md) |

## A schema the service owns

Each service's migrations live with its code and run against its own schema ([lab 02](../code-lab/lab-02-persistence.md)):

```sql title="db/migration/V1__menus.sql (lab 02)"
--8<-- "code/code-lab/lab-02-persistence/src/main/resources/db/migration/V1__menus.sql"
```

```sql title="db/migration/V2__menu_item_available.sql (lab 02): an expand-only change"
--8<-- "code/code-lab/lab-02-persistence/src/main/resources/db/migration/V2__menu_item_available.sql"
```

Hibernate never changes the schema (`ddl-auto: validate`): it only checks that the entities match what Flyway built. Repository tests run the same migrations on real PostgreSQL with Testcontainers.

## A migration's cost is its lock

[Chapter 33](../data/ch33-database-scaling.md) ran each change on 2 M orders under write load:

| Change | Longest write | Throughput vs before |
|---|---:|---:|
| `ADD COLUMN note text` | 11–16 ms | 105–111% |
| `ADD COLUMN ref uuid DEFAULT gen_random_uuid()` (rewrite) | 3,288–5,093 ms | 8–11% |
| `CREATE INDEX` | 820–1,195 ms | 29–36% |
| **`CREATE INDEX CONCURRENTLY`** | **49–53 ms** | 57–63% |
| `ADD CONSTRAINT … CHECK` | 117–122 ms | 71–86% |
| **`… NOT VALID`, then `VALIDATE`** | **10–14 ms** | 94–97% |
| `ADD COLUMN` behind a 12 s report query | 10,017–10,022 ms (all writers) | 4–5% |
| **… with `lock_timeout` and retries** | **504–506 ms** | 71–76% |

Even a catalogue-only change must get its lock first, and everything after it queues behind. So Flyway's connection sets a lock timeout before migrating:

```yaml title="application.yml (chapter 33)"
--8<-- "code/chapters/ch33-database-scaling/src/main/resources/application.yml:13:15"
```

The deploy pipeline retries a migration that timed out.

## Migrations that keep writes flowing

```sql title="V3__orders_restaurant_created_index.sql (chapter 33)"
--8<-- "code/chapters/ch33-database-scaling/src/main/resources/db/migration/V3__orders_restaurant_created_index.sql"
```

```sql title="V4 and V5: add NOT VALID, validate separately (chapter 33)"
--8<-- "code/chapters/ch33-database-scaling/src/main/resources/db/migration/V4__orders_amount_positive_not_valid.sql"

--8<-- "code/chapters/ch33-database-scaling/src/main/resources/db/migration/V5__orders_amount_positive_validate.sql"
```

- Flyway runs a script containing `CREATE INDEX CONCURRENTLY` **outside a transaction**, so it goes in a file of its own (`spring.flyway.mixed=true` otherwise).
- Flyway's own lock is a transaction-level advisory lock in `READ COMMITTED`; it does not block the concurrent build (checked in chapter 33).
- **No `IF NOT EXISTS`** on concurrent builds: a failed build leaves an INVALID index, and a rerun would "succeed" around it.

## Expand/contract across releases

A rolling deploy runs old and new versions together, so every release must work with the previous schema:

| Migration (4 instances, rolling deploy) | Errors | Wrong reads |
|---|---:|---:|
| Rename the column, then roll out | 3,947–3,973 | 0 |
| Roll out, then rename | 4,867–4,894 | 0 |
| Expand/contract, backfill only empty rows | 0 | 10–12 |
| **Expand/contract, backfill rows that differ** | **0** | **0** |

Expand (add the new column, write both), backfill in throttled batches (1,000 rows, 20 ms pause kept 88% of throughput), read the new column, then contract (drop the old) in a later release. A rename takes four releases instead of one. Chapter 33 keeps the contract migration in `db/later/` until every instance has moved.

## Flyway or Liquibase

| | Flyway | Liquibase |
|---|---|---|
| Format | SQL files (Java migrations possible) | Changelogs in XML/YAML/JSON, or formatted SQL |
| Database-specific DDL (`CONCURRENTLY`, `NOT VALID`) | Written directly | Raw SQL changesets, or `runInTransaction="false"` |
| Rollback | Write a new forward migration (undo is a paid feature) | `rollback` blocks per changeset |
| Preconditions, contexts, labels | Placeholders and callbacks | Built in |
| Spring Boot | `spring.flyway.*` | `spring.liquibase.*` |

FoodFleet prefers Flyway's plain SQL because the hard part of a migration is the PostgreSQL lock it takes, which SQL shows directly. In production, roll **forward**: a rollback script rarely survives the data written since.

## Pitfalls

- **Editing an applied migration**: the checksum fails on every environment that ran it.
- **`ddl-auto: update`** in any environment that matters.
- **DDL and a long backfill in one transaction**: its locks last until the backfill commits.
- **Migrations run by every replica at start-up** without a lock (Flyway takes one; custom scripts often don't), or with a start-up probe shorter than the migration.
- **A migration the previous release can't live with**.
