---
title: "Distributed Locks"
description: "Locks across processes are leases, and a paused holder outlives them. Measured in lab 13 with 300 updates under GC pauses: Redis SET NX released with DEL lost 213–229 updates; compare-and-delete lost 94–103; a fencing token checked by the resource on reads and writes lost none (58 stale attempts refused). What FoodFleet uses instead where it can: database constraints, conditional writes, SKIP LOCKED and advisory locks inside PostgreSQL; and fencing before any database failover (ADR-046)."
progress: done
---

# Distributed Locks

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Mutual exclusion between processes on different machines: a Redis key with a TTL (`SET NX PX`), PostgreSQL row locks and advisory locks, ZooKeeper/etcd leases, Kubernetes Lease objects, ShedLock's lock table |
| **FoodFleet uses** | Redis leases only for efficiency (avoid duplicate work), fenced where correctness depends on them; PostgreSQL's own locking for data; fencing before failover |
| **Decided in** | [ADR-032](../data/ch32-caching-strategies.md#8-enterprise-case-study) (refresh locks), [ADR-046](../resilience/ch46-disaster-recovery.md#8-enterprise-case-study) (fencing) |
| **Learn it in** | [Lab 13](../code-lab/lab-13-redis.md), [chapter 3](../foundations/ch03-distributed-systems-fundamentals.md), [chapter 46](../resilience/ch46-disaster-recovery.md) |
| **Related** | [Redis](redis-valkey.md), [Distributed scheduling](distributed-scheduling.md), [Idempotency store](idempotency-store.md), [PostgreSQL](postgresql.md) |

## A lock is a lease

[Lab 13](../code-lab/lab-13-redis.md): workers increment a value under a Redis lock with a TTL while GC pauses outlast the lease.

| Release | 300 updates |
|---|---|
| `DEL` | **213–229 lost**: a worker that woke after expiry deleted the *next* holder's lock (124–136 times), letting a third in |
| Compare-and-delete (`unlock.lua`) | **94–103 lost**: no foreign deletes, but the paused worker still wrote a value computed from an old read |
| Plus a **fencing token** | **0 lost**; 58 stale attempts refused |

```lua title="redis/fenced-write.lua (lab 13)"
--8<-- "code/code-lab/lab-13-redis/src/main/resources/redis/fenced-write.lua"
```

Every grant increments a counter; the resource records the highest token it has seen and refuses older ones, on reads as well as writes.

## Prefer the database's own tools

| Need | Tool | Where in the handbook |
|---|---|---|
| Do something once per key | Unique constraint + `INSERT … ON CONFLICT DO NOTHING` | Coupons ([chapter 62](../testing/ch62-unit-slice-integration-tests.md)): 300 of 300 rounds with one winner and 0 errors, against 421–449 errors for check-then-insert |
| Don't overwrite a newer change | Version column (optimistic locking), `UPDATE … WHERE version = ?` | [Lab 02](../code-lab/lab-02-persistence.md); caches' set-if-newer ([Redis](redis-valkey.md)) |
| Work queue across instances | `SELECT … FOR UPDATE SKIP LOCKED` | Outbox relay ([chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md)) |
| Serialise per entity in a projector | `pg_advisory_xact_lock(order_id)` | [Chapter 34](../data/ch34-reporting-analytics.md)'s incremental aggregates |
| One migration at a time | Flyway's transaction-level advisory lock | [Flyway](flyway-liquibase.md) |

These hold the lock in the same system as the data, so the "lease expired while paused" gap doesn't exist: the transaction either commits holding it or doesn't commit.

## Fencing beyond locks

The same problem at database scale is **split brain**: a standby promoted while the old primary still accepts writes. [Chapter 46](../resilience/ch46-disaster-recovery.md): with the link lost 2 s before the primary, every commit in those 2 s was lost (17,000–22,700). ADR-046 requires fencing (power, network, credentials) before any promotion, and no automatic promotion across regions.

## Choosing

| | Redis (single instance) | Redlock (several Redis) | etcd / ZooKeeper | PostgreSQL |
|---|---|---|---|---|
| Safety under pauses | Needs fencing | Needs fencing (timing assumptions) | Leases with revision numbers usable as fencing tokens | Transactional |
| Cost | Lowest | Several round trips | A consensus cluster | Already there |

## Pitfalls

- **Locks for correctness without fencing**.
- **`DEL` to release**.
- **TTL shorter than the work**, or no TTL (a crashed holder blocks forever).
- **A lock where a constraint would do**.
- **Holding a lock across a remote call** of unbounded duration.
