# Chapter 4 code — Twelve-Factor & Fifteen-Factor zone-service

Docs: `docs/foundations/ch04-twelve-factor.md`

FoodFleet **zone-service** (port 8085): "Do we deliver to this pincode, and what does delivery cost?"

| Factor | Where in this module |
|---|---|
| I Codebase | One module = one deployable |
| II Dependencies | `pom.xml`: explicit starters, versions from BOMs |
| III Config | `ZoneProperties` (validated, no defaults), env vars, `configtree` secrets, `k8s/zone-service.yaml` |
| IV Backing services | `ZoneStore` port, `JdbcZoneStore`; PostgreSQL located by `SPRING_DATASOURCE_URL` |
| V Build, release, run | `Dockerfile` (layers + Java 25 AOT cache), `build-info`, image pinned by digest in `k8s/` |
| VI Processes | Stateless `ServiceabilityService` |
| VII Port binding | `server.port: ${PORT:8085}` |
| VIII Concurrency | `replicas: 3`, virtual threads |
| IX Disposability | Graceful shutdown, 20 s phase timeout, probes |
| X Dev/prod parity | `compose.yaml` and Testcontainers use PostgreSQL 17, same as production |
| XI Logs | ECS JSON to stdout (`logging.structured.format.console`) |
| XII Admin processes | `ZoneImportTask` run as a Kubernetes Job from the same image |
| 13 API first | `ServiceabilityController` contract |
| 14 Telemetry | `foodfleet.serviceability.checks{result}` counter, Actuator |
| 15 AuthN/AuthZ | Deferred to chapter 37 (resource server) |

```bash
cd code
mvn -B -pl chapters/ch04-twelve-factor -am verify        # container test skips itself without Docker

# run locally: compose.yaml's PostgreSQL starts automatically
mvn -pl chapters/ch04-twelve-factor spring-boot:run -Dspring-boot.run.profiles=local
curl -s localhost:8085/api/v1/serviceability/560034 | jq

# admin process: same code and config, one-off task, exits when done
mvn -pl chapters/ch04-twelve-factor spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --foodfleet.admin.task=import-zones --foodfleet.admin.file=data/zones.csv"
```
