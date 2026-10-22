# Chapter 62 — Unit, Slice & Testcontainers Tests

Code and measurements for [chapter 62](../../../docs/testing/ch62-unit-slice-integration-tests.md).

| Path | What |
|---|---|
| `src/main/java/.../coupon/` | `CouponRedemptions`: FIRST20 once per customer, with `ON CONFLICT` and with the portable check-then-insert |
| `src/main/java/.../testing/` | `PostgresContainer` (start on a random port, wait for a connection, JDBC URL, remove, reuse: what Testcontainers' `PostgreSQLContainer` does), `Isolation` (truncate, a database per test from a template) |
| `src/test/java/` | `PostgresExtension` (one container per run in JUnit's root store, a database per test), `CouponRedemptionsPostgresTest` (3 tests, `postgres`, including 8 concurrent callers × 20 rounds), `H2CompatibilityTest` (2 tests, `h2`, pinning what H2 can't run) |
| `verify/` | `build-libs.sh` (chapter 61's libraries; a PostgreSQL 16 image from the host's binaries; source clones), `run.sh`, `check_apis.sh` (20 checks), `postgres-image/`, `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh                     # about 6 minutes; Docker needed
C62_IMAGE=postgres:16 JAVA_HOME=/path/to/jdk-25 verify/run.sh   # with the official image
```

## Findings (PostgreSQL 16.13 in Docker 29.4, H2 2.5.252, JDK 25.0.4, JUnit 6.1.3, two runs)

**lifecycle**: 8 test classes × 6 tests.

| Database | Suite | Containers | Per test |
|---|---|---|---|
| container per class | 17,981–22,866 ms | 8 | 27–28 ms |
| one container per run | 4,046–4,748 ms | 1 | 23 ms |
| reused container, next run | 1,497–2,508 ms | 0 | 19–22 ms |
| H2 in memory | 405–734 ms | 0 | 3.4–3.7 ms (portable SQL only) |

**isolation**: 300 tests, each expecting to be the first to redeem FIRST20 for one customer.

| Between tests | Failed | Cleanup per test |
|---|---|---|
| nothing | 299 | — |
| rollback | 0 | 0.30–0.34 ms |
| rollback, code commits on its own connection | 299 | 0.01–0.02 ms |
| truncate | 0 | 7.8–7.9 ms |
| new database from a template | 0 | 51–73 ms (+15–16 s to drop 300) |

**h2gap**: 8 of 19 PostgreSQL features failed on H2 in PostgreSQL mode (`ON CONFLICT (…)`, upsert, `jsonb`, partial index, `text[]`, `RETURNING`, `timestamptz + interval`, `pg_try_advisory_lock`); one more printed booleans differently. Under 16 concurrent redemptions × 300 rounds:

| Code | Database | Rounds with 2+ winners | Errors |
|---|---|---|---|
| `ON CONFLICT DO NOTHING` | PostgreSQL | 0 | 0 |
| check-then-insert | PostgreSQL | 0 | 421–449 |
| check-then-insert | H2 | 0 | 33–45 |
| check-then-insert, no primary key | PostgreSQL | 172–186 | 0 |
| check-then-insert, no primary key | H2 | 16–22 | 0 |

**suites**: `postgres` 3/3 and `h2` 2/2 passed. **apis**: 20 of 20 found in Spring Boot 4.1.1, Spring Framework 7.0.9, Testcontainers 2.0.5, JUnit 6.1.3 and H2 2.5.252.

## Notes

- Testcontainers itself wasn't used: Maven Central wasn't reachable where these runs were made. `PostgresContainer` does the same steps through the Docker CLI, with `fsync=off` as `PostgreSQLContainer` sets it; `check_apis.sh` checks Testcontainers' and Spring Boot's behaviours in their sources. The `pom.xml` uses the usual dependencies.
- Docker Hub wasn't reachable either, so the image is built from the host's PostgreSQL 16.13 binaries on an empty base (`verify/postgres-image/`); like the official image it runs `initdb` on first start, so container start times are comparable. `C62_IMAGE=postgres:16` runs the experiments on the official image.
- The rollback strategy hands the code under test the test's own connection with `close()` disabled, as Spring's transaction synchronisation does; "code commits on its own connection" opens a second one.
- The template strategy's "rows left" is the last test's own database, which held its own redemption.
