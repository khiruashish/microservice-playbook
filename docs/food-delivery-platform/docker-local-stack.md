---
title: "Docker & Local Stack"
description: "Running FoodFleet on one machine: a Docker Compose stack generated from the platform model, with the infrastructure always on and services started by journey profile (browse, checkout, track or full), what each profile costs in memory (4.6 to 11.2 GiB), and a PostgreSQL init script, also generated, that gives every service its own role and schema: on PostgreSQL 16, 132 of 132 cross-service reads and writes were refused."
progress: done
---

# Docker & Local Stack

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

Nobody needs all 21 FoodFleet services running to work on one of them. A developer changing checkout needs checkout's services and the infrastructure under them; the rest can stay off. The local stack is generated from the [platform model](index.md#how-these-pages-are-made), so it knows which services each journey uses, and starts only those.

```bash
python3 code/foodfleet/local/generate.py          # writes compose.yaml, init/01-schemas.sql, profiles.md
cd code/foodfleet/local
docker compose --profile checkout up              # browse | checkout | track | full
```

## What's in it

| Part | Image | Why it's there |
|---|---|---|
| PostgreSQL 16 | `postgres:16.13` | Every service's business data; one role and schema per service ([ADR-012](../architecture-patterns/ch12-database-per-service.md), [ADR-029](../data/ch29-polyglot-persistence.md)) |
| Kafka | `apache/kafka:4.3.1`, KRaft, one node | Events, state topics and saga commands; topics aren't auto-created, as in production |
| Redis | `redis:7.4` | Copies and ephemeral state: price cache, rate limits, rider positions |
| Keycloak | `quay.io/keycloak/keycloak:26.7.5`, dev mode | The identity provider, with a `foodfleet` realm imported at start ([ADR-037](../security/ch37-spring-security-7.md)) |
| Telemetry | `grafana/otel-lgtm:0.35.0` | An OTLP endpoint with Grafana, Prometheus, Tempo and Loki in one container ([ADR-047](../observability/ch47-observability-fundamentals.md)) |
| Payment provider | `wiremock/wiremock:3.13.1` | A stub that authorizes, captures and voids; checkout and full profiles only |
| Services | `foodfleet/<name>:local` | Built from each service's repository with the shared Dockerfile template ([ADR-053](../deployment/ch53-containerization.md)) |

The infrastructure has no profile, so it always starts. Each service is wired from the model:

- **Database:** a JDBC URL with its own schema and its own credentials, if it stores data in PostgreSQL.
- **Clients:** for each synchronous call, an HTTP service client group (`spring.http.serviceclient.<group>.base-url`, [ADR-022](../communication/ch22-http-clients.md)) or a gRPC channel (`spring.grpc.client.channel.<name>.target`, checked in Spring Boot's source, [ADR-023](../communication/ch23-grpc.md)). Group names drop their hyphens so the environment variables bind.
- **Kafka, Keycloak and OTLP** endpoints for every service; Redis for the ones that use it.

## Profiles by journey

--8<-- "code/foodfleet/local/profiles.md"

How the profiles are chosen:

- **browse** and **track** are every service the journey calls, required or optional, from the model's call graph.
- **checkout** adds the saga's participants (payment, kitchen, delivery, promotions) and what they call, because checkout reaches them through Kafka rather than by calls, so a call graph alone would leave them out.
- **full** is all 21 container services. The two functions run on Lambda in production and aren't in the stack.

Memory limits are 384 MiB per service, enough for a JVM service at `MaxRAMPercentage=75` with one CPU, plus about 3 GiB for the infrastructure (Keycloak, Kafka and the telemetry stack are the heavy parts). So a 16 GB laptop runs any one journey comfortably, and the full stack only just. That is the practical argument for profiles: the full platform doesn't fit a developer's machine well, and doesn't need to.

## Every service gets its own schema

`generate.py` also writes the PostgreSQL init script from the model: for each service that stores data in PostgreSQL, a login role, a schema owned by it, and its search path; `public` is closed to everyone. payment-service gets its own database, standing in for its own instance in production ([ADR-012](../architecture-patterns/ch12-database-per-service.md)).

```sql title="code/foodfleet/local/init/01-schemas.sql (the first service)"
--8<-- "code/foodfleet/local/init/01-schemas.sql:1:11"
```

`verify-schemas.sh` runs the script on a throwaway PostgreSQL 16.13 cluster, creates a table in each service's schema as that service, then tries every other service's table, reading and then writing:

```console
$ PG_BIN=/usr/lib/postgresql/16/bin code/foodfleet/local/verify-schemas.sh
services with a schema: 12; own schema usable: 12 of 12
cross-service reads or writes: 0 allowed, 132 of 132 denied
```

That's the local equivalent of chapter 12's `table-owners.txt` audit, which found 14 of 23 sampled statements touching another service's tables before the rule existed. Locally, a service that reaches into another's tables fails on the developer's machine instead of in review.

## What was checked, and what wasn't

| Check | Result |
|---|---|
| `docker compose config` for each profile (Compose 5.1.3) | Valid: 13, 16, 9 and 27 containers for browse, checkout, track and full |
| The init script on PostgreSQL 16.13 | 12 schemas usable by their owners; 132 of 132 cross-service accesses denied |
| Image versions | Each tag matches a release tag in the project's repository; the images themselves weren't pulled |
| Starting the stack | **Not run here**: this environment can't pull images from Docker Hub or Quay, and the service images need each service's build |

So the compose file is checked for structure, and the database isolation for behaviour, but nobody has watched this exact stack start. Expect to adjust start-up ordering (Keycloak takes several seconds before tokens can be validated) the first time.

## Smaller than a stack: one service

For work on a single service, a full stack is rarely needed:

- **Tests** start PostgreSQL in a container once per run with Testcontainers and `@ServiceConnection` ([ADR-062](../testing/ch62-unit-slice-integration-tests.md)), and stub providers from their contracts ([ADR-063](../testing/ch63-contract-testing.md)).
- **`spring-boot-docker-compose`** starts the infrastructure a service declares in its own `compose.yaml` when it runs from the IDE.
- **[Your first microservice](../getting-started/first-microservice.md)** runs with nothing but a JDK.

The shared stack is for the moments those don't cover: following a request across services, watching the saga's messages, or reproducing a bug that needs several services at once.
