---
title: "Connection Pooling"
description: "HikariCP and PgBouncer at FoodFleet: small, fixed-size pools budgeted across the fleet and its deploy surge (checked in CI), because on 2 vCPUs most throughput was there by 16 connections while p99 inside PostgreSQL grew from 6 ms to 76–98 ms at 256, and a new connection per transaction managed only 428–461 tps. Plus HikariCP 7's credentials provider for rotating dynamic credentials, transaction-local settings on pooled connections, and where PgBouncer fits."
progress: done
---

# Connection Pooling

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Keeping database connections open and reusing them. **HikariCP** is the in-process JDBC pool (Spring Boot's default); **PgBouncer** is an external pooler between many clients and PostgreSQL |
| **FoodFleet uses** | HikariCP in every service, fixed-size and budgeted fleet-wide; PgBouncer in transaction mode in front of batch jobs |
| **Decided in** | [ADR-033](../data/ch33-database-scaling.md#8-enterprise-case-study), [ADR-039](../security/ch39-secrets-management.md#8-enterprise-case-study) (rotating credentials) |
| **Learn it in** | [Chapter 33](../data/ch33-database-scaling.md), [chapter 39](../security/ch39-secrets-management.md), [lab 18](../code-lab/lab-18-multi-tenancy.md) |
| **Related** | [PostgreSQL](postgresql.md), [Vault](vault.md), [Resilience4j](resilience4j.md) (bulkheads) |

## Pools are sized for the database

Each PostgreSQL connection is a process. [Chapter 33](../data/ch33-database-scaling.md) held demand at 256 concurrent requests and varied the pool:

| Connections | Transactions/s | p99 in the database | Latency at 256 requests |
|---:|---:|---:|---:|
| 2 | 2,905–3,052 | 1.3–2.0 ms | 83.9–88.1 ms |
| 16 | 6,470–6,748 | 5.7–6.0 ms | 37.9–39.6 ms |
| 64 | 7,351–7,699 | 17.4–21.4 ms | 33.3–34.8 ms |
| 256 | 6,154–7,319 | **76.2–98.2 ms** | 35.0–41.6 ms |
| 16, a new connection per transaction | **428–461** | — | 555–598 ms |

Past the knee, requests wait inside PostgreSQL instead of in the pool, holding a process and its memory. Too few connections leave the CPUs idle between round trips; no pool at all is an order of magnitude slower.

```yaml title="application.yml (chapter 33)"
--8<-- "code/chapters/ch33-database-scaling/src/main/resources/application.yml:4:12"
```

## Budget the fleet, including deploys

Chapter 33's incident: 40 instances with HikariCP's default pool of 10, `max_connections` 400; every rolling deploy's surge asked for 500 and stalled. The fix was pools of 4 per instance (160 total, 200 during deploys), checked in CI:

```java title="PoolSizing.java (chapter 33)"
--8<-- "code/chapters/ch33-database-scaling/src/main/java/dev/foodfleet/ch33/pool/PoolSizing.java:38:64"
```

The budget counts every service on the database, the deploy surge, and connections reserved for administrators, migrations, replication and monitoring.

## Settings that matter

| HikariCP setting | FoodFleet | Why |
|---|---|---|
| `maximum-pool-size` = `minimum-idle` | small, from the budget | A fixed pool: no connection churn under load |
| `connection-timeout` | 2 s | Fail fast when the pool is exhausted, inside the caller's deadline |
| `max-lifetime` | 30 min | Below any proxy or firewall idle timeout; also how rotated credentials take effect |
| Credentials provider | HikariCP 7's `HikariCredentialsProvider`, fed by a rotating Spring Vault lease | New connections use the current dynamic credentials; on rotation the pool soft-evicts its connections ([Vault](vault.md)) |

```java title="DataSourceConfig.java (chapter 39)"
--8<-- "code/chapters/ch39-secrets-management/src/main/java/dev/foodfleet/ch39/web/DataSourceConfig.java:17:29"
```

## Pooled connections carry state

A connection outlives the request that used it. In [lab 18](../code-lab/lab-18-multi-tenancy.md), setting the tenant with a session-level `SET` leaked **1,000 rows** of one tenant to the next request on the same connection; `set_config(…, true)` (transaction-local) leaked 0. The same applies to `search_path`, roles and temporary tables.

## PgBouncer

| Mode | Connection given back | Breaks |
|---|---|---|
| Session | When the client disconnects | Nothing; little multiplexing |
| **Transaction** | At each commit | Session state: `SET`, advisory locks held across transactions, `LISTEN`; prepared statements only with PgBouncer 1.21+ `max_prepared_statements` |
| Statement | After each statement | Multi-statement transactions |

FoodFleet puts PgBouncer in transaction mode in front of batch jobs, whose many short-lived workers would otherwise each open connections. Services keep HikariCP's own pools; stacking both is fine as long as the budget counts PgBouncer's server connections.

## Pitfalls

- **HikariCP's default of 10** multiplied by every instance and the deploy surge.
- **A pool bigger than the threads that use it**, or, with virtual threads, more concurrent requests than connections and no bound: requests queue on `connection-timeout`; bound them with a bulkhead ([Spring Framework resilience](spring-framework-resilience.md)).
- **Session `SET` on pooled connections**.
- **`max-lifetime` longer than a load balancer's idle timeout**: connections die silently.
- **Long transactions** (a remote call inside one) holding a connection: one slow dependency drains the pool.
