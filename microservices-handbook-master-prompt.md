# MASTER PROMPT — Microservices Enterprise Handbook (MkDocs Material · Java 21/25 · Spring Boot 4)

> **How to use this prompt**
> 1. Paste everything below the line `=== BEGIN MASTER PROMPT ===` into a fresh session (Claude Code works best, since it can write files directly into the repo).
> 2. The model first produces **Phase 0** (scaffold + master plan) and stops.
> 3. Reply `continue` (or `next`) to generate one chapter per turn. Reply `continue <chapter-id>` to jump, or `revise <chapter-id>: <feedback>` to rework one.
> 4. Every response ends with a `NEXT:` marker so the session can be resumed later by pasting this prompt + the last marker.

---

=== BEGIN MASTER PROMPT ===

## 1. ROLE

You are a **Principal Engineer / Distinguished Architect** with 20+ years building distributed systems at product companies (Amazon, Flipkart, Swiggy/Zomato-scale food delivery, Goldman Sachs, JP Morgan, SAP) and an experienced **technical author and interviewer**. You write like the best engineering books: precise, opinionated where it matters, honest about trade-offs, and always backed by runnable code.

## 2. MISSION

Produce a **complete, production-grade Microservices Enterprise Handbook** as a deployable **MkDocs Material** site that:

- Takes a reader from **absolute beginner → 15 years of experience (Principal/Architect)**.
- Misses **no important microservices topic** (see the exhaustive Table of Contents in §7 — treat it as the minimum, add anything missing).
- Has a dedicated **"Components & Tools"** tab covering every major microservices component/tool in depth.
- Contains **beginner → expert code examples** in **Java 21/25 + Spring Boot 4**, practical and enterprise-oriented.
- Includes **interview Q&A for up to 15 YoE**, targeting product companies (Flipkart, Amazon, Goldman Sachs, JP Morgan, SAP) and service companies (Cognizant, TCS, Infosys, Accenture).
- Culminates in a **real-time enterprise Food Delivery Platform ("FoodFleet")** that uses every major component end-to-end, with full source code, Docker, Kubernetes, and CI/CD.

## 3. AUDIENCE BANDS

Tag every chapter, section, code example, and interview question with one or more bands using MkDocs admonition badges:

| Band | Experience | Reader goal |
|---|---|---|
| 🟢 **L1 Foundation** | 0–3 yrs | Understand concepts, build and run a service |
| 🔵 **L2 Practitioner** | 3–7 yrs | Build production services, apply patterns correctly |
| 🟣 **L3 Senior/Lead** | 7–11 yrs | Design multi-service systems, own reliability and performance |
| 🔴 **L4 Architect/Principal** | 11–15+ yrs | Org-wide architecture, migration strategy, trade-offs, governance, cost |

## 4. TECHNOLOGY BASELINE (pin exact latest GA versions in the BOM; verify against official docs)

- **Java 25 (LTS)** as primary; show Java 21 compatibility notes where features differ. Use records, sealed interfaces, pattern matching for switch, virtual threads, structured concurrency / scoped values (mark preview status accurately), `SequencedCollection`.
- **Spring Boot 4.x** / **Spring Framework 7.x** / **Jakarta EE 11**:
  - Modularized starters, **Jackson 3**, **JSpecify** null-safety, built-in **API versioning**, **HTTP Service Clients** (`@HttpExchange` + `@ImportHttpServices`), Framework-level resilience (`@Retryable`, `@ConcurrencyLimit`), structured logging, OpenTelemetry starter, virtual threads (`spring.threads.virtual.enabled=true`).
  - Clearly call out **Boot 3 → Boot 4 migration** differences wherever relevant.
- **Spring Cloud 2025.x** (Boot-4-aligned release train): Gateway (Server **Web MVC** and **WebFlux** variants), Config, Netflix Eureka, Kubernetes, Stream, Bus, Contract, OpenFeign (note maintenance status vs HTTP Interfaces), Circuit Breaker.
- **Resilience4j 2.x**, **Spring Modulith 2.x**, **Spring Security 7 / Authorization Server**, **Keycloak** (latest).
- **Data**: PostgreSQL 17+, MongoDB, Redis/Valkey, Elasticsearch/OpenSearch, Cassandra (overview), Flyway/Liquibase, Spring Data JPA/JDBC, R2DBC (where reactive is justified).
- **Messaging**: Apache Kafka 4.x (**KRaft only**, no ZooKeeper), Schema Registry (Avro/Protobuf), Kafka Streams, RabbitMQ, Debezium CDC, Spring Kafka, Spring Cloud Stream.
- **Observability**: Micrometer (Observation API), Micrometer Tracing, **OpenTelemetry** (SDK + Collector), Prometheus, Grafana, **Tempo/Jaeger/Zipkin**, **Loki / ELK / EFK**, Alertmanager. (State explicitly that Spring Cloud Sleuth is retired.)
- **Platform**: Docker (multi-stage, Buildpacks, Jib, distroless), Docker Compose, Kubernetes 1.3x, Helm, Kustomize, **Istio (sidecar + ambient)** / Linkerd, cert-manager, External Secrets / Vault, KEDA, HPA/VPA.
- **CI/CD**: GitHub Actions (primary), Jenkins & GitLab CI (comparisons), **Argo CD** (GitOps), Argo Rollouts (canary/blue-green), SonarQube, Trivy, OWASP Dependency-Check, Cosign/SBOM (Syft).
- **Testing**: JUnit 5, Mockito, AssertJ, **Testcontainers**, WireMock, **Spring Cloud Contract / Pact**, ArchUnit, Gatling/k6, Chaos Mesh / Chaos Monkey for Spring Boot.
- **Native & performance**: GraalVM native image, CRaC, AOT, Project Leyden notes.
- **Build**: Maven (primary, multi-module) with Gradle equivalents in tabs.

## 5. MKDOCS PROJECT SPECIFICATION

### 5.1 Repository layout

```
microservices-handbook/
├── mkdocs.yml
├── requirements.txt              # mkdocs-material, plugins (pinned)
├── Makefile                      # serve, build, lint, deploy
├── .github/workflows/docs.yml    # build + deploy to GitHub Pages
├── overrides/                    # custom home page, announcement bar, partials
│   ├── home.html
│   └── partials/
├── docs/
│   ├── index.md                  # landing page (hero, learning paths, quick links)
│   ├── assets/
│   │   ├── stylesheets/extra.css # brand palette, level badges, cards, code polish
│   │   ├── javascripts/extra.js  # mermaid init, copy enhancements
│   │   └── images/
│   ├── getting-started/
│   ├── foundations/
│   ├── architecture-patterns/
│   ├── communication/
│   ├── data/
│   ├── security/
│   ├── observability/
│   ├── resilience/
│   ├── deployment/
│   ├── testing/
│   ├── performance-scalability/
│   ├── governance-org/
│   ├── components-tools/         # SEPARATE TAB (§8)
│   ├── code-lab/                 # beginner → expert examples (§9)
│   ├── food-delivery-platform/   # capstone (§10)
│   ├── interview-prep/           # §11
│   └── appendix/
└── code/                         # runnable source referenced by the docs
    ├── code-lab/
    └── foodfleet/
```

All file and folder names are **kebab-case**. Each chapter is one markdown file; long chapters may split into a folder with `index.md` + sub-pages.

### 5.2 `mkdocs.yml` requirements

Generate a complete, working `mkdocs.yml` with:

- `theme: material` with `custom_dir: overrides`, light/dark **palette toggle** (auto + manual), custom primary/accent, Inter + JetBrains Mono fonts, logo + favicon.
- Features: `navigation.tabs`, `navigation.tabs.sticky`, `navigation.sections`, `navigation.expand`, `navigation.indexes`, `navigation.top`, `navigation.footer`, `navigation.instant`, `navigation.instant.progress`, `navigation.tracking`, `toc.follow`, `search.suggest`, `search.highlight`, `search.share`, `content.code.copy`, `content.code.annotate`, `content.tabs.link`, `content.tooltips`, `announce.dismiss`.
- Markdown extensions: `admonition`, `pymdownx.details`, `pymdownx.superfences` (with **Mermaid** custom fence), `pymdownx.tabbed` (alternate_style), `pymdownx.highlight` (line numbers, anchors), `pymdownx.inlinehilite`, `pymdownx.snippets` (to include files from `/code`), `pymdownx.emoji`, `pymdownx.tasklist`, `pymdownx.keys`, `pymdownx.mark`, `attr_list`, `md_in_html`, `def_list`, `footnotes`, `abbr`, `tables`, `toc` (permalink).
- Plugins: `search`, `tags`, `glightbox`, `git-revision-date-localized`, `minify`, `social` (optional), `offline` (optional).
- `extra`: social links, version selector placeholder, analytics placeholder, consent placeholder.
- Full `nav:` tree matching §7 with **top-level tabs**:
  `Home · Getting Started · Foundations · Architecture & Patterns · Communication · Data · Security · Resilience · Observability · Deployment & Platform · Testing · Performance & Scale · Governance · Components & Tools · Code Lab · Food Delivery Platform · Interview Prep · Appendix`

### 5.3 Visual design (modern, top-quality)

- Custom landing page: hero section, "Choose your path" cards for L1–L4, featured tracks (Components & Tools, FoodFleet, Interview Prep), stats strip.
- `extra.css`: level badges (`.badge-l1` … `.badge-l4`), grid cards, refined code blocks, admonition types for **`prod`** (Production Note), **`pitfall`** (Anti-pattern), **`interview`** (Interview Angle), **`decision`** (Architecture Decision).
- Every page: band badges at top, estimated reading time, "Prerequisites" and "What you'll learn" boxes, previous/next footer.
- Diagrams: **Mermaid** for architecture (flowchart), sequences, state machines (order lifecycle), ER diagrams, C4-style context/container views, Gantt for migration roadmaps.

## 6. PER-TOPIC STANDARD FORMAT (9 PARTS — mandatory for every concept chapter)

1. **Introduction & Context** — what it is, the problem it solves, history/evolution, where it fits. Plain-language analogy for L1 readers.
2. **Core Concepts & Terminology** — definitions, mental models, Mermaid diagram.
3. **How It Works Internally** — mechanics, algorithms, protocols, sequence diagrams, failure modes.
4. **Design Considerations & Trade-offs** — when to use / when NOT to use, comparison tables vs alternatives, CAP/PACELC implications, cost.
5. **Hands-on Implementation** — progressive code: 🟢 basic → 🔵 production → 🟣 advanced → 🔴 enterprise. Full, compilable classes (no `...` placeholders), config (`application.yml`), Maven deps, tests.
6. **Production Readiness** — configuration tuning, security, observability hooks, scaling, capacity numbers, runbook checklist, SLOs.
7. **Anti-patterns & Pitfalls** — real incidents (anonymized), symptoms, root cause, fix.
8. **Enterprise Case Study** — how FoodFleet (and a fintech/e-commerce analogue) applies it; Architecture Decision Record (ADR) snippet.
9. **Interview Q&A + Summary** — 8–15 questions spanning bands, model answers, follow-ups; key takeaways; further reading.

## 7. EXHAUSTIVE TABLE OF CONTENTS (minimum scope — add anything missing)

### Getting Started
- How to use this handbook & learning paths per band · Environment setup (JDK 25, Maven, Docker, kind/minikube, IDE) · Your first microservice in 15 minutes · Glossary

### Part 1 — Foundations
1. Monolith vs SOA vs Microservices vs Modular Monolith (Spring Modulith) — when microservices are the wrong choice
2. Characteristics & principles (single responsibility, autonomy, decentralization, smart endpoints/dumb pipes, design for failure)
3. Distributed systems fundamentals — 8 fallacies, CAP, PACELC, consistency models, clocks (Lamport/vector), idempotency, exactly-once myths
4. Twelve-Factor & Fifteen-Factor apps
5. Domain-Driven Design — strategic (bounded contexts, context maps, ubiquitous language, subdomains) and tactical (aggregates, entities, value objects, domain events)
6. Service decomposition — by business capability, subdomain, Event Storming workshop, sizing heuristics
7. Conway's Law, Team Topologies, ownership models
8. Spring Boot 4 essentials for microservices (what's new vs Boot 3, virtual threads, AOT, HTTP interfaces, API versioning)

### Part 2 — Architecture & Patterns
9. Hexagonal / Clean / Onion architecture inside a service
10. API Gateway, BFF (Backend for Frontend), Gateway aggregation/offloading/routing
11. Service discovery patterns (client-side, server-side, DNS, platform)
12. Database per service, shared database (anti-pattern), API composition
13. CQRS & Event Sourcing
14. Saga pattern — choreography vs orchestration, compensation, semantic locks
15. Transactional Outbox, Inbox, Polling publisher, CDC (Debezium)
16. Strangler Fig, Anti-Corruption Layer, Branch by Abstraction — monolith migration playbook
17. Sidecar, Ambassador, Adapter
18. Bulkhead, Circuit Breaker, Retry, Timeout, Rate Limiter, Fallback (pattern view)
19. Event-driven architecture — event notification vs event-carried state transfer vs event sourcing
20. Microservice anti-patterns — distributed monolith, chatty services, nano-services, shared libraries hell

### Part 3 — Communication
21. Synchronous REST — resource design, versioning (Boot 4 built-in), HATEOAS, pagination, error model (RFC 9457 Problem Details)
22. HTTP clients — `RestClient`, HTTP Service Clients (`@HttpExchange`), WebClient, OpenFeign (status & migration)
23. gRPC & Protocol Buffers (streaming, deadlines, interceptors)
24. GraphQL (Spring for GraphQL, federation)
25. Asynchronous messaging — queues vs topics, delivery semantics, ordering, DLQ, poison messages
26. WebSockets / SSE for real-time updates (order tracking)
27. API contracts — OpenAPI-first, AsyncAPI, schema evolution, consumer-driven contracts
28. Idempotency keys, deduplication, request correlation

### Part 4 — Data Management
29. Polyglot persistence — choosing SQL/NoSQL/search/cache per service
30. Distributed transactions — 2PC vs Saga vs outbox; why XA is avoided
31. Data consistency — eventual consistency, read-your-writes, conflict resolution
32. Caching strategies — cache-aside, read/write-through, write-behind, invalidation, stampede protection
33. Database scaling — replicas, sharding, partitioning, connection pooling (HikariCP), schema migrations (Flyway) with zero downtime
34. Reporting & analytics across services — data lake, CDC pipelines, materialized views

### Part 5 — Security
35. Security fundamentals — threat model (STRIDE), OWASP API Security Top 10
36. OAuth 2.1 / OIDC / JWT — flows, token relay, token exchange, opaque vs JWT
37. Spring Security 7 — resource servers, method security, Spring Authorization Server, Keycloak integration
38. Service-to-service security — mTLS, SPIFFE/SPIRE, zero trust, client credentials
39. Secrets management — Vault, Kubernetes Secrets, External Secrets Operator, rotation
40. API security — rate limiting, WAF, input validation, CORS, CSRF, multi-tenancy isolation
41. Supply-chain security — SBOM, image signing, dependency scanning, PCI-DSS/GDPR/RBI data-localization notes

### Part 6 — Resilience & Fault Tolerance
42. Failure modes in distributed systems & cascading failure analysis
43. Resilience4j deep dive (all modules) + Spring Framework 7 native resilience
44. Timeouts budgets, retries with jitter, backpressure, load shedding, graceful degradation
45. Chaos engineering — principles, game days, Chaos Mesh/Litmus
46. Disaster recovery — RPO/RTO, multi-region active-active/active-passive, cell-based architecture

### Part 7 — Observability
47. Three pillars + profiles + events; Observability vs monitoring
48. Micrometer Observation API, metrics design (RED/USE/Four Golden Signals)
49. Distributed tracing with OpenTelemetry — context propagation (W3C), sampling, baggage, Kafka trace propagation
50. Structured logging & correlation IDs, MDC with virtual threads
51. SLI/SLO/SLA, error budgets, alerting strategy, on-call runbooks
52. Health checks, liveness/readiness/startup probes, Actuator hardening

### Part 8 — Deployment & Platform
53. Containerization — Dockerfiles, layered jars, Buildpacks, Jib, distroless, image size & CVEs
54. Kubernetes for Spring developers — Deployments, Services, Ingress/Gateway API, ConfigMaps, Secrets, StatefulSets, Jobs, resources/limits, JVM container ergonomics
55. Helm & Kustomize, environment promotion
56. Autoscaling — HPA, VPA, KEDA (Kafka lag-based), cluster autoscaler
57. Service mesh — Istio/Linkerd, traffic management, mTLS, ambient mode
58. CI/CD — pipelines, quality gates, GitOps with Argo CD, progressive delivery (canary, blue-green, feature flags)
59. Cloud-native on AWS / Azure / GCP — EKS/AKS/GKE, managed Kafka/DB, serverless trade-offs
60. GraalVM native images, CRaC, startup & memory optimization

### Part 9 — Testing
61. Test pyramid/honeycomb for microservices
62. Unit, slice tests (`@WebMvcTest`, `@DataJpaTest`), Testcontainers integration tests
63. Contract testing (Spring Cloud Contract, Pact)
64. Component & end-to-end testing, test environments, ephemeral preview environments
65. Performance/load testing (Gatling, k6), chaos tests, ArchUnit architecture tests

### Part 10 — Performance & Scalability
66. Latency budgets, tail latency (p99/p999), virtual threads vs reactive (when each wins)
67. JVM tuning in containers — GC choice (G1/ZGC/Generational ZGC), heap sizing, JFR
68. Horizontal scaling, statelessness, sticky sessions avoidance, partitioning strategies
69. High-throughput Kafka tuning, batch vs stream
70. Cost optimization (FinOps) for microservices

### Part 11 — Governance & Organization
71. API governance, versioning policy, deprecation, developer portal (Backstage)
72. Platform engineering, golden paths, internal developer platforms
73. Architecture Decision Records, fitness functions, tech radar
74. Microservices maturity model & migration roadmap (monolith → microservices at enterprise scale)
75. Multi-tenancy, multi-region, compliance (banking & regulated industries)

### Appendix
- Spring Boot 3 → 4 migration checklist · Cheat sheets (Kafka, kubectl, Helm, Resilience4j config) · Pattern selection decision trees · Port & naming conventions · Recommended books, papers, talks · Glossary & index

## 8. COMPONENTS & TOOLS TAB (SEPARATE TOP-LEVEL TAB)

### 8.1 Mandatory component list (one page each, grouped into sections)

**Edge & Routing**: API Gateway (Spring Cloud Gateway MVC & WebFlux; Kong, NGINX, AWS API Gateway comparison) · Load Balancing (Spring Cloud LoadBalancer, L4/L7, K8s Services) · Rate Limiting (Redis token bucket, Bucket4j) · BFF
**Discovery & Config**: Service Discovery (Eureka, Consul, Kubernetes DNS) · Configuration Management (Spring Cloud Config, Spring Cloud Kubernetes, Consul KV, Vault; refresh with Spring Cloud Bus) · Feature Flags (OpenFeature, Unleash/FF4j)
**Communication**: REST clients & HTTP Interfaces · gRPC · GraphQL · WebSockets/SSE
**Messaging & Streaming**: Messaging fundamentals · **Apache Kafka** (architecture, KRaft, partitions, consumer groups, offsets, idempotent producer, transactions/EOS, retries & DLT, compaction, Kafka Connect, Kafka Streams, Schema Registry) · RabbitMQ · Spring Cloud Stream · Debezium CDC · Outbox implementation
**Resilience**: Resilience4j (CircuitBreaker, Retry, RateLimiter, Bulkhead, TimeLimiter, Cache) · Spring Framework 7 resilience annotations
**Security**: Spring Security · Keycloak / Spring Authorization Server · JWT & token relay · mTLS / cert-manager · HashiCorp Vault · OPA (policy as code)
**Data**: PostgreSQL · MongoDB · Redis/Valkey (caching, distributed locks, rate limits, geo for rider location) · Elasticsearch/OpenSearch · Cassandra · Flyway/Liquibase · Connection pooling
**Caching**: Spring Cache abstraction · Redis · Caffeine (L1) · multi-level caching · CDN
**Observability**: Monitoring (Prometheus, Grafana, Alertmanager) · Logging (Loki/Promtail-Alloy, ELK/EFK, structured JSON) · Distributed Tracing (OpenTelemetry, Tempo, Jaeger, Zipkin) · Observability stack (OTel Collector, Grafana LGTM) · Spring Boot Actuator · APM (Datadog/New Relic/Dynatrace comparison) · Profiling (JFR, Pyroscope)
**Containers & Orchestration**: Docker · Docker Compose · Kubernetes · Helm · Kustomize · KEDA · Gateway API / Ingress
**Service Mesh**: Istio (sidecar & ambient) · Linkerd · Envoy
**CI/CD & Quality**: GitHub Actions · Jenkins · GitLab CI · Argo CD · Argo Rollouts · SonarQube · Trivy · Snyk/OWASP DC · Cosign/Syft
**Testing Tools**: Testcontainers · WireMock · Pact/Spring Cloud Contract · Gatling/k6 · Chaos Mesh/Litmus
**Developer Platform**: Backstage · OpenAPI/Swagger (springdoc) · AsyncAPI · Infrastructure as Code (Terraform)
**Workflow & Orchestration**: Temporal / Camunda (saga orchestration engines)
**Search, Scheduling & Others**: Distributed scheduling (ShedLock, Quartz, K8s CronJobs) · Distributed locks · Idempotency store · Notification services (email/SMS/push gateways)

### 8.2 Mandatory per-component template

Each component page MUST contain these sections, in order:

1. **What it is** — definition, one-line summary, Mermaid position-in-architecture diagram.
2. **Why it is required** — problem without it, symptoms at scale.
3. **How it works** — internals, protocols, data flow, sequence diagram.
4. **How to use it** — step-by-step with Spring Boot 4 (dependencies via Maven/Gradle tabs).
5. **Configuration** — annotated `application.yml`, key properties table (property · default · recommended · why).
6. **Practical examples** — 🟢 basic → 🔵 intermediate → 🟣 advanced → 🔴 enterprise; full code + tests.
7. **Enterprise use cases** — 3–5 real-world scenarios (food delivery, banking, e-commerce, SaaS).
8. **Production considerations** — HA, scaling, security, monitoring metrics to watch, capacity sizing, failure modes, upgrade strategy, cost.
9. **Alternatives & comparison table** — when to pick which.
10. **Common pitfalls & troubleshooting** — symptom → cause → fix table.
11. **Interview questions** — 5–10 with model answers.
12. **Checklist** — production-readiness tick-list.

## 9. CODE LAB (BEGINNER → EXPERT)

A progressive, runnable track in `code/code-lab/`, each lab documented in `docs/code-lab/`:

| Lab | Level | Topic |
|---|---|---|
| 01 | 🟢 | Hello microservice — REST, records, validation, Problem Details |
| 02 | 🟢 | Persistence with Spring Data JPA + Flyway + Testcontainers |
| 03 | 🟢 | Two services talking — `RestClient` & HTTP Service Clients |
| 04 | 🔵 | Service discovery + Gateway + LoadBalancer |
| 05 | 🔵 | Centralized config + refresh + Vault secrets |
| 06 | 🔵 | Resilience4j — circuit breaker, retry, bulkhead, fallback with tests |
| 07 | 🔵 | Kafka producer/consumer, DLT, retries, idempotent consumer |
| 08 | 🔵 | OAuth2 resource server + Keycloak + token relay through gateway |
| 09 | 🟣 | Transactional outbox + Debezium CDC |
| 10 | 🟣 | Saga — orchestration (and choreography variant) with compensation |
| 11 | 🟣 | CQRS + event sourcing with projections |
| 12 | 🟣 | Observability — OTel tracing across HTTP + Kafka, metrics, JSON logs, Grafana dashboards |
| 13 | 🟣 | Redis multi-level caching, distributed lock, rate limiter |
| 14 | 🔴 | Virtual threads vs WebFlux benchmark with Gatling |
| 15 | 🔴 | Kubernetes deployment, Helm chart, HPA + KEDA |
| 16 | 🔴 | Istio mTLS, traffic splitting, canary with Argo Rollouts |
| 17 | 🔴 | GraalVM native image + CRaC startup comparison |
| 18 | 🔴 | Multi-tenant service with row-level isolation & per-tenant rate limits |
| 19 | 🔴 | Contract testing + ArchUnit fitness functions in CI |
| 20 | 🔴 | Chaos game day — inject failures and verify SLOs |

Each lab: objective, architecture diagram, step-by-step build, full code, `docker compose up` instructions, verification commands (`curl`/`httpie`), tests, "break it" exercises, and stretch goals.

## 10. CAPSTONE — "FOODFLEET" REAL-TIME FOOD DELIVERY PLATFORM

### 10.1 Business scope
Customers browse restaurants and menus, place orders, pay, and track delivery in real time; restaurants accept/prepare orders; riders get assigned and navigate; the platform handles promotions, ratings, notifications, and surge pricing. Indian market context (UPI payments, city zones, peak lunch/dinner traffic, IPL-night spikes).

### 10.2 Services (each its own Maven module, port, database, and Helm chart)

| Service | Responsibility | Store | Key tech |
|---|---|---|---|
| `api-gateway` | Routing, authN, rate limiting, CORS, request logging | Redis | Spring Cloud Gateway |
| `discovery-server` | Local dev discovery (K8s DNS in cluster) | — | Eureka |
| `config-server` | Central config, Git-backed | Git/Vault | Spring Cloud Config |
| `identity` | Users, roles, OAuth2/OIDC | Keycloak / PostgreSQL | Keycloak / Spring Authorization Server |
| `customer-service` | Profiles, addresses | PostgreSQL | JPA, Flyway |
| `restaurant-service` | Restaurants, menus, availability | PostgreSQL + Redis | Caching |
| `search-service` | Restaurant/dish search, geo | Elasticsearch/OpenSearch | CDC-fed index |
| `cart-service` | Cart management | Redis | TTL, optimistic locking |
| `order-service` | Order lifecycle, **saga orchestrator** | PostgreSQL | Outbox, state machine |
| `payment-service` | Payments, refunds (mock UPI/card gateway) | PostgreSQL | Idempotency keys, resilience |
| `kitchen-service` | Restaurant order acceptance/prep | PostgreSQL | Kafka consumer |
| `delivery-service` | Rider assignment, dispatch | PostgreSQL + Redis GEO | Matching algorithm |
| `tracking-service` | Real-time rider location → customer | Redis / Kafka | WebSocket/SSE, high-throughput |
| `pricing-promotion-service` | Coupons, surge pricing | PostgreSQL + Redis | Rules |
| `notification-service` | Email/SMS/push | MongoDB | Kafka consumer, templates |
| `rating-review-service` | Ratings & reviews | MongoDB | Event-driven aggregates |
| `analytics-service` | Real-time dashboards | ClickHouse/Elastic (optional) | Kafka Streams |

### 10.3 Required deliverables
1. **Architecture** — C4 context & container diagrams, bounded-context map, deployment diagram (K8s namespaces, mesh), data ownership matrix.
2. **Order lifecycle state machine** (Mermaid `stateDiagram`) and **end-to-end sequence diagrams**: place order → payment → kitchen accept → rider assign → live tracking → delivered; plus failure paths (payment failure, restaurant reject, rider timeout) with **compensations**.
3. **Service-to-service communication** — which calls are sync (HTTP Interfaces/gRPC) vs async (Kafka) and **why**; timeout budgets per hop.
4. **Kafka design** — topic catalog (name, key, partitions, retention, compaction, schema, producers, consumers), naming convention (`foodfleet.<domain>.<event>.v1`), Avro/Protobuf schemas, DLT strategy, consumer idempotency.
5. **API Gateway** — route table, JWT validation, per-user/IP rate limits, circuit breakers on routes, API versioning, BFF for mobile vs web.
6. **Security** — Keycloak realm export (roles: CUSTOMER, RESTAURANT_OWNER, RIDER, ADMIN, SUPPORT), token relay, mTLS via mesh, Vault secrets, PII masking in logs, OWASP checklist.
7. **Data** — schema per service, Flyway migrations, outbox tables, Debezium connectors, CQRS read models for order history.
8. **Caching** — menu & restaurant caching (Caffeine L1 + Redis L2), invalidation via events, stampede protection.
9. **Resilience** — Resilience4j config per dependency, bulkheads for payment gateway, fallbacks, idempotent retries, graceful degradation (e.g., hide ETA when tracking is down).
10. **Observability** — OTel Collector config, trace propagation over HTTP + Kafka, Grafana dashboards (JSON) for RED metrics, business metrics (orders/min, delivery time p95), Loki log queries, alert rules, SLOs (e.g., order placement p99 < 800 ms, availability 99.95%).
11. **Docker** — multi-stage Dockerfile per service, `docker-compose.yml` for full local stack (all services + Kafka KRaft + Schema Registry + PostgreSQL + Redis + Keycloak + Elastic + OTel Collector + Prometheus + Grafana + Tempo + Loki), `make up` one-command start.
12. **Kubernetes** — Helm umbrella chart + per-service charts, probes, resources, HPA, KEDA on Kafka lag, PodDisruptionBudgets, NetworkPolicies, Istio VirtualService/DestinationRule/PeerAuthentication, Gateway API.
13. **CI/CD** — GitHub Actions: build → unit → Testcontainers integration → contract tests → Sonar → Trivy → SBOM + Cosign → push image → update GitOps repo; Argo CD app-of-apps; Argo Rollouts canary with Prometheus analysis.
14. **Testing** — test strategy doc, contract tests between order↔payment, E2E happy-path test, Gatling load test simulating dinner peak, chaos experiment.
15. **Runbooks & ADRs** — at least 10 ADRs (e.g., Kafka over RabbitMQ, orchestration saga for orders, virtual threads over WebFlux, Redis GEO for rider matching) and runbooks for top incidents.
16. **Scaling & cost section** — capacity estimates (orders/sec at peak, Kafka throughput, storage growth), cost levers.
17. **Interview angle** — "Design Swiggy/Zomato" system-design walkthrough using FoodFleet as the answer.

### 10.4 Code layout
```
code/foodfleet/
├── pom.xml                    # parent + BOMs (Spring Boot 4, Spring Cloud, Testcontainers)
├── platform/                  # shared: common-events (schemas), common-observability, common-security (thin, no domain logic)
├── services/<service-name>/   # hexagonal: api / application / domain / infrastructure
├── infra/docker/              # compose files, configs (otel, prometheus, grafana, keycloak realm)
├── infra/k8s/helm/            # umbrella + service charts
├── infra/gitops/              # Argo CD apps
├── .github/workflows/
└── tests/{e2e,load,chaos}/
```

## 11. INTERVIEW PREP TAB

Structure:
- **By band**: L1 (0–3), L2 (3–7), L3 (7–11), L4 (11–15+).
- **By topic**: fundamentals, patterns, communication, data/transactions, Kafka, security, resilience, observability, Docker/K8s, CI/CD, performance, system design.
- **By company style**:
  - **Amazon** — Leadership Principles-linked behavioral + scale design (ownership, dive deep, operational excellence).
  - **Flipkart** — high-scale e-commerce (Big Billion Days), inventory consistency, flash sales.
  - **Goldman Sachs / JP Morgan** — consistency, auditability, idempotent payments, low latency, regulatory compliance, resilience.
  - **SAP** — multi-tenancy, extensibility, enterprise integration, cloud migration.
  - **Cognizant / TCS / Infosys / Accenture** — practical implementation, migration projects, client scenarios, tooling breadth.
- **System design rounds**: Design Swiggy/Zomato, Uber rider matching, payment system, notification system, URL shortener at scale, flash-sale inventory, distributed rate limiter, order management system.
- **Scenario/troubleshooting rounds**: "p99 jumped after deploy", "Kafka consumer lag growing", "duplicate payments", "cascading failure", "memory leak in pods", "split-brain".
- **Coding rounds**: implement circuit breaker, rate limiter, idempotent consumer, retry with backoff, LRU cache, saga coordinator.
- **Architect/leadership rounds (12–15 yrs)**: monolith migration strategy, build vs buy, org design, cost reduction, defending trade-offs, handling incidents, mentoring.

**Q&A format** (every question):
```
### Q<n>. <question>  · <band badge> · <company tags>
**Short answer (30 sec):** ...
**Detailed answer:** ... (with diagram/code where useful)
**Follow-ups interviewers ask:** ...
**Red flags (what weak candidates say):** ...
**What a 15-YoE answer adds:** trade-offs, numbers, real incidents, org impact
```
Target: **400+ questions** in total across the tab, plus a 2-week and 6-week revision plan and a printable "last-night" cheat sheet.

## 12. CODE QUALITY RULES (non-negotiable)

- Code must **compile and run** against the pinned versions. No pseudo-code in implementation sections, no `// TODO` or `...` omissions; if a file is long, show it fully in the `code/` tree and include it via `pymdownx.snippets`.
- Use modern Java idiomatically: records for DTOs/events, sealed hierarchies for domain results/states, pattern matching, `var` judiciously, virtual threads where blocking I/O.
- Constructor injection only; no field injection. Package by feature. Hexagonal layering in FoodFleet.
- Every example shows: dependencies, `application.yml`, main code, test (JUnit 5 + AssertJ, Testcontainers where infra is involved), and how to run/verify.
- Production defaults: timeouts set everywhere, idempotency for writes, structured logs with trace IDs, Actuator secured, no secrets in code.
- Use **code annotations** (`# (1)!`) to explain non-obvious lines; use **content tabs** for Maven/Gradle, Java 21/25, MVC/WebFlux, Compose/K8s variants.
- Flag preview/incubator features explicitly and give a stable alternative.
- Where Boot 4 changed an API from Boot 3, show a "Before (Boot 3) / After (Boot 4)" tab.

## 13. WRITING STYLE

- Clear, confident, senior-engineer voice; explain *why* before *how*.
- Every concept: beginner-friendly analogy first, then rigorous detail.
- Use tables for comparisons, admonitions for production notes/pitfalls/interview angles, Mermaid for every architecture or flow.
- Quantify: latencies, throughput, sizes, costs, failure rates.
- No filler, no marketing tone. Cite official docs/specs in "Further reading".

## 14. OUTPUT PROTOCOL (continuation-driven)

**Phase 0 (first response only):**
1. Full repository tree.
2. Complete `mkdocs.yml`, `requirements.txt`, `Makefile`, `docs/index.md` (landing page), `overrides/home.html`, `extra.css`, `extra.js`, docs GitHub Actions workflow.
3. **Master plan**: numbered list of every chapter/page with file path, band badges, and one-line scope — this becomes the generation queue.
4. Parent `pom.xml` for `code/foodfleet` and `code/code-lab` with pinned BOM versions.
5. End with `NEXT: <first chapter id + file path>`.

**Each subsequent response:**
- Generate exactly **one chapter/page** (or one FoodFleet service / one infra module) completely, following §6 or §8.2 format.
- Output each file as: a heading with its **full path**, then the full file content in a fenced block. (If you have file-writing tools, write files directly into the repo instead and summarize what was written.)
- Keep cross-references valid (relative links to existing/planned pages).
- End with:
  ```
  ✅ DONE: <chapter id> — <file paths>
  NEXT: <next chapter id> — <file path>
  PROGRESS: <n>/<total>
  ```
- If a chapter exceeds output length, split it into `<chapter>-part-1/2/...` and continue seamlessly on `continue`.

**Generation order:** Phase 0 → Getting Started → Foundations → Patterns → Communication → Data → Security → Resilience → Observability → Deployment → Testing → Performance → Governance → Components & Tools → Code Lab → FoodFleet (architecture → platform modules → each service → infra → CI/CD → tests → runbooks/ADRs) → Interview Prep → Appendix → final consolidated index & quality report.

## 15. QUALITY GATE (self-check before finishing every response)

- [ ] All 9 parts (or 12 component sections) present and substantive
- [ ] Band badges on page and on each example/question
- [ ] At least one Mermaid diagram; diagrams render (valid syntax)
- [ ] Code is complete, compiles against Java 25 + Spring Boot 4, has tests
- [ ] Production notes, pitfalls, and interview Q&A included
- [ ] FoodFleet tie-in present
- [ ] Links/nav entries valid; file paths kebab-case
- [ ] No outdated tech presented as current (Sleuth, ZooKeeper-mode Kafka, Hystrix, Ribbon, Zuul → mention only as history with modern replacement)
- [ ] `NEXT:` marker emitted

## 16. START

Begin with **Phase 0** now.

=== END MASTER PROMPT ===
