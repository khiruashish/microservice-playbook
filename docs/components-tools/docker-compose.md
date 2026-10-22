---
title: "Docker Compose"
description: "Compose for FoodFleet's local development: one generated compose file with PostgreSQL 16, Kafka in KRaft mode, Redis, Keycloak and grafana/otel-lgtm always on, and services started by journey profile (browse, checkout, track, full: 13, 16, 9 and 27 containers), each service with its own schema and role. What was checked (compose config valid for every profile; 132 of 132 cross-service database accesses denied) and what wasn't, plus spring-boot-docker-compose and Testcontainers for single-service work."
progress: done
---

# Docker Compose

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | A YAML file describing several containers (services, networks, volumes, health checks, dependencies) and a CLI that starts them together; profiles start subsets |
| **FoodFleet uses** | A generated local stack for following requests across services; per-service `compose.yaml` with `spring-boot-docker-compose` from the IDE; never for production |
| **Learn it in** | [Local stack](../food-delivery-platform/docker-local-stack.md), [chapter 62](../testing/ch62-unit-slice-integration-tests.md) (Testcontainers instead) |
| **Related** | [Docker](docker.md), [Testcontainers](testcontainers.md), [Kubernetes](kubernetes.md) |

## FoodFleet's local stack

```bash
python3 code/foodfleet/local/generate.py          # writes compose.yaml, init/01-schemas.sql, profiles.md
docker compose --profile checkout up              # browse | checkout | track | full
```

The file is generated from the platform model, so it can't drift from the services that exist. Infrastructure always starts; services by journey:

```yaml title="compose.yaml (generated): infrastructure"
--8<-- "code/foodfleet/local/compose.yaml:1:42"
```

```yaml title="compose.yaml (generated): one service"
--8<-- "code/foodfleet/local/compose.yaml:173:196"
```

- **One schema and role per service** in one PostgreSQL, created by `init/01-schemas.sql`, as in production ([ADR-012](../architecture-patterns/ch12-database-per-service.md#8-enterprise-case-study)).
- **Kafka with auto-creation off**, as in production: a missing topic fails locally.
- **`depends_on` with `condition: service_healthy`** for PostgreSQL; services still retry their own connections.
- **Memory limits and the production JVM flags** (`MaxRAMPercentage=75`, `ActiveProcessorCount=1`), so local behaviour resembles a pod.
- **Telemetry to `grafana/otel-lgtm`**: Grafana on port 3000, OTLP on 4317/4318.

## What was checked

| Check | Result |
|---|---|
| `docker compose config` per profile (Compose 5.1.3) | Valid: 13, 16, 9 and 27 containers for browse, checkout, track and full |
| The init script on PostgreSQL 16.13 | 12 of 12 schemas usable by their owners; **132 of 132** cross-service accesses denied |
| Starting the stack | **Not run here**: images couldn't be pulled in this environment |

Expect to adjust start-up ordering the first time (Keycloak takes several seconds before tokens can be validated).

## Smaller than a stack

| Need | Use |
|---|---|
| Tests | [Testcontainers](testcontainers.md) with `@ServiceConnection`; providers stubbed from contracts |
| Running one service from the IDE | `spring-boot-docker-compose`: Boot starts the service's own `compose.yaml` and wires connection details |
| Following a request across services | The shared stack |

## Pitfalls

- **Compose as a production orchestrator**: no rescheduling across hosts, no rolling updates with readiness, no autoscaling.
- **`depends_on` without health conditions**: "started" isn't "ready".
- **A hand-maintained file per developer**: drift; generate or share it.
- **One database user for all services locally**: cross-service queries that work on laptops and fail in production.
- **Real secrets in the file**: FoodFleet's are visibly `local-only`.
