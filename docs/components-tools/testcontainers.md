---
title: "Testcontainers"
description: "Real PostgreSQL 16 in tests at FoodFleet (ADR-062): one container per test run from a shared base class, wired with @ServiceConnection, Flyway migrations applied, H2 removed. Measured: a container per class made the suite 18–23 s against 4–5 s for one per run and 1.5–2.5 s reused; H2 in PostgreSQL mode failed 8 of 19 production SQL features; rollback isolation broke when code committed on its own connection (299 of 300 tests failed); a portable check-then-insert passed every single-threaded test and gave 421–449 errors under concurrency."
progress: done
---

# Testcontainers

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | A library that starts throwaway Docker containers (databases, brokers, browsers, any image) from tests, with modules for PostgreSQL, Kafka, Redis, Keycloak and more; Spring Boot's `@ServiceConnection` wires their connection details automatically |
| **FoodFleet uses** | PostgreSQL 16 for repository and component tests in every service; Kafka, Redis and Keycloak where a test needs them |
| **Decided in** | [ADR-062](../testing/ch62-unit-slice-integration-tests.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 62](../testing/ch62-unit-slice-integration-tests.md), [lab 02](../code-lab/lab-02-persistence.md), [chapter 61](../testing/ch61-test-strategy.md) |
| **Related** | [WireMock](wiremock.md), [Contract testing](contract-testing-tools.md), [Docker Compose](docker-compose.md), [PostgreSQL](postgresql.md) |

## One container per run

```java title="PostgresContainer.java (chapter 62)"
--8<-- "code/chapters/ch62-unit-slice-integration-tests/src/main/java/dev/foodfleet/ch62/testing/PostgresContainer.java:32:52"
```

[Chapter 62](../testing/ch62-unit-slice-integration-tests.md), 8 test classes × 6 tests:

| Database for the suite | Suite time | Containers |
|---|---:|---:|
| PostgreSQL container per test class | **17,981–22,866 ms** | 8 |
| One container for the run | 4,046–4,748 ms | 1 |
| Reused container, next run | **1,497–2,508 ms** | 0 |
| H2 in memory | 405–734 ms | 0 |

Starting containers is the cost (about 2–2.7 s each, mostly `initdb`); tests ran in 19–28 ms each. Static `@Container` fields give one per class; a singleton in a shared base class gives one per run. Reuse is allowed locally; CI always starts fresh.

## Why not H2

| Feature | H2 2.5, PostgreSQL mode |
|---|---|
| `INSERT … ON CONFLICT (cols) DO NOTHING`, upserts | **syntax error** |
| `jsonb`, partial unique indexes, `text[]`, `RETURNING` | **syntax error** |
| `timestamptz + interval`, `pg_try_advisory_lock` | **error** |
| `FOR UPDATE SKIP LOCKED`, `DISTINCT ON`, `generate_series` | ok |

8 of 19 features failed. Worse, rewriting a coupon rule into portable check-then-insert passed every single-threaded test; with 16 concurrent callers it gave **421–449 errors** (300 rounds) on PostgreSQL, and **doubles in more than half the rounds** when the primary key was missing. H2 hid most of the race. [Chapter 61](../testing/ch61-test-strategy.md)'s H2 suite caught none of 20 planted bugs.

## Isolation between tests

| Between tests | Failed (of 300) | Cleanup per test |
|---|---:|---:|
| Roll back the test's transaction | 0 | 0.30–0.34 ms |
| …but the code commits on its own connection | **299** | — |
| Truncate | 0 | 7.8–7.9 ms |
| New database from a template | 0 | 51–73 ms |

`@JdbcTest`/`@DataJpaTest` keep their rollback; `@SpringBootTest` tests (HTTP in, other threads and connections) truncate after each test.

## Pitfalls

- **A container per class**.
- **`ddl-auto` instead of the real migrations**: constraints missing, and races pass.
- **A different major version** from production.
- **Tests that pass alone and fail together**: rollback that doesn't cover the code's own connections.
- **No concurrency test for once-only rules**: every such rule gets one with concurrent callers asserting one winner and no errors.
- **Docker unavailable in CI** silently skipping tests: fail instead (`disabledWithoutDocker` only locally).
