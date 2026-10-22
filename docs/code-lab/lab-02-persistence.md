---
title: "Lab 02 — Persistence with JPA, Flyway & Testcontainers"
description: "Move menu-service's data into PostgreSQL: a schema the service owns, versioned with Flyway (including an expand-only change), JPA entities kept behind the API, optimistic locking against lost updates, constraints in the database as well as the code, and repository tests on real PostgreSQL 16 with Testcontainers and @ServiceConnection. About an hour."
progress: done
---

# Lab 02 — Persistence with JPA, Flyway & Testcontainers

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **You'll build** | menu-service's data layer on PostgreSQL, with migrations and real-database tests |
| **Time** | About an hour |
| **Builds on** | [Lab 01](lab-01-hello-microservice.md) |
| **Chapters** | [12 (database per service)](../architecture-patterns/ch12-database-per-service.md), [29 (persistence)](../data/ch29-polyglot-persistence.md), [33 (schema changes)](../data/ch33-database-scaling.md), [62 (integration tests)](../testing/ch62-unit-slice-integration-tests.md) |
| **Code** | `code/code-lab/lab-02-persistence/` |
| **Needs** | Docker, for Testcontainers |

!!! note "About the lab code"
    Written for Spring Boot 4.1.1 and checked by import against its libraries' source, not compiled here ([how](index.md#how-the-lab-code-was-checked)). The migrations in this lab *were* run: `tools/check_sql.sh` applies them to PostgreSQL 16.13.

## 1. The service owns its schema

menu-service gets its own schema and database user; no other service reads its tables ([ADR-012](../architecture-patterns/ch12-database-per-service.md)). Flyway owns the schema's history:

```sql title="src/main/resources/db/migration/V1__menus.sql"
--8<-- "code/code-lab/lab-02-persistence/src/main/resources/db/migration/V1__menus.sql"
```

The `CHECK` constraints repeat rules the code already enforces. That's deliberate: the database is the last line of defence against a bug, a script or a hand-written fix that bypasses the code.

## 2. Change it without breaking running instances

During a rolling deploy, old and new versions of the service run at once against the same schema. So a change must work for both: **expand** first (add, with a default), and only **contract** (drop, rename) once no old instance remains ([ADR-033](../data/ch33-database-scaling.md)):

```sql title="V2__menu_item_available.sql"
--8<-- "code/code-lab/lab-02-persistence/src/main/resources/db/migration/V2__menu_item_available.sql"
```

Chapter 33 measured why: renaming a column in place failed thousands of statements from old instances during a deploy; expand/contract, none.

## 3. Entities stay inside the service

```java title="MenuItemEntity.java"
--8<-- "code/code-lab/lab-02-persistence/src/main/java/dev/foodfleet/lab02/MenuItemEntity.java:15:67"
```

The controller still returns the `MenuItem` record. The entity can change shape with the table without changing the API, and JSON can never write to a column.

`@Version` gives each row a version that Hibernate checks on every update.

## 4. Configure for production, not for the demo

```yaml title="application.yml"
--8<-- "code/code-lab/lab-02-persistence/src/main/resources/application.yml"
```

- **`ddl-auto: validate`**: Hibernate never changes the schema; it only checks the entities match what Flyway built.
- **`open-in-view: false`**: no database work while the response is being written.
- **A fixed pool size**, part of a fleet-wide budget: past about 16 connections on 2 vCPUs, PostgreSQL gained little throughput while waits inside it grew 13–17× ([ch. 33](../data/ch33-database-scaling.md)).

## 5. No lost updates

```java title="MenuService.java"
--8<-- "code/code-lab/lab-02-persistence/src/main/java/dev/foodfleet/lab02/MenuService.java:26:39"
```

The caller sends the version it saw (an `If-Match` header carrying the ETag, [ADR-021](../communication/ch21-rest-api-design.md)). If someone else changed the dish in between, the update is refused rather than silently overwriting. Read-modify-write without this lost 15.5% of loyalty points in chapter 31 ([ch. 31](../data/ch31-data-consistency.md)).

## 6. Test on the real database

```java title="MenuItemRepositoryTest.java"
--8<-- "code/code-lab/lab-02-persistence/src/test/java/dev/foodfleet/lab02/MenuItemRepositoryTest.java:21:70"
```

- **PostgreSQL 16, the production engine.** Chapter 62 found H2 failed 8 of 19 PostgreSQL features FoodFleet's services use; in chapter 61, H2 failed 3 of 4 tests on correct code and caught no bugs.
- **`@ServiceConnection`** wires the container's URL, user and password into Spring Boot; no properties to keep in sync.
- **`replace = NONE`** stops the slice from swapping in an embedded database.
- **The migrations run in every test run**, so the tests also prove the migrations and the entities agree.

For a whole test suite, start one container for the run rather than one per class: 4.0–4.7 s instead of 18.0–22.9 s for eight classes in chapter 62.

## Run it

```bash
docker run -d --name pg -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:16.13
docker exec pg psql -U postgres -c "CREATE USER menu_service PASSWORD 'local-menu-service'" \
  -c "CREATE DATABASE foodfleet OWNER menu_service"
cd code/code-lab/lab-02-persistence
mvn verify              # Testcontainers starts its own PostgreSQL for the tests
mvn spring-boot:run     # against the database above
```

## Check yourself

- [ ] `mvn verify` passes: the migrations ran, the entities validated, all five tests passed.
- [ ] `\d menu_service.menu_item` in `psql` shows the `available` column from V2 and both `CHECK` constraints.
- [ ] The `flyway_schema_history` table lists both versions.

## Break it

!!! question "Exercises"
    1. Rename `price_paise` to `price` in V1 and run the tests. Which test fails first, and with what message? (`ddl-auto: validate` is doing its job.)
    2. Write V3 that renames `name` to `title` in one step. Explain what happens to instances still running the V2 code during a rolling deploy, then rewrite it as expand/contract across three migrations.
    3. Remove the version check from `reprice` and make the stale-version test pass by accident. What did the test stop proving?
    4. Replace `@AutoConfigureTestDatabase(replace = NONE)` with the default. What does the slice try to do?

**Next:** [Lab 03](lab-03-two-services.md) adds a second service that calls this one.
