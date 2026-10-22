### Part 1 · Foundations

<a id="adr-001"></a>

??? decision "ADR-001 · 2026-01-12 · Start FoodFleet as a modular monolith with extraction seams"
    **Decision in full:** Start FoodFleet as a modular monolith with extraction seams

    **Status:** Accepted · **Date:** 2026-01-12 · **Deciders:** Principal engineer, engineering manager, product lead

    **Context.** One team of eight engineers. The domain (dispatch rules, kitchen workflow) is still changing weekly. No Kafka, tracing or per-service pipelines exist yet. Launch target is 4 months.

    **Decision.** Build one Spring Boot 4 application with Spring Modulith modules (`order`, `restaurant`, `kitchen`, `delivery`, `payment`, `notification`), schema-per-module ownership, the event publication registry, and module verification in CI. Synchronous cross-module needs go through **ports owned by the caller** so that any module can be extracted by switching adapters.

    **Consequences.**
    ✅ One pipeline, ACID where needed, refactoring boundaries stays cheap while the domain settles.
    ✅ Extraction is a planned, low-risk change (adapter swap plus event externalisation), not a rewrite.
    ⚠️ All modules share one release cadence and one scaling unit until extracted.
    ⚠️ Discipline required: idempotent listeners, no cross-schema queries, no remote calls in transactions.

    **Revisit when** a second team forms, payment integration starts (PCI scope), or any module's load profile diverges by 10× or more.

    From [chapter 1](../foundations/ch01-architecture-styles.md).

<a id="adr-002"></a>

??? decision "ADR-002 · 2026-02-03 · Every synchronous dependency declares a degradation mode"
    **Decision in full:** Every synchronous dependency declares a degradation mode

    **Status:** Accepted · **Date:** 2026-02-03

    **Context.** Two incidents in Q4 were caused by optional enrichments (recommendations, ETA) on the critical path without timeouts or fallbacks.

    **Decision.** Every outbound synchronous call must (1) use a declarative HTTP client group with explicit connect and read timeouts; (2) retry only idempotent calls, on transient errors, with jitter, at one layer; (3) declare **fail open** or **fail closed** in the service charter; (4) emit a degradation metric. ArchUnit rules in `foodfleet-architecture-rules` reject `RestTemplate` and ad-hoc `HttpClient` usage.

    **Consequences.** ✅ Partial failures stay partial; behaviour during incidents is predictable. ⚠️ More code paths to test, and charters must be kept current (reviewed quarterly).

    From [chapter 2](../foundations/ch02-characteristics-principles.md).

<a id="adr-003"></a>

??? decision "ADR-003 · 2026-02-10 · Consistency is chosen per data type, and money is never eventually consistent"
    **Decision in full:** Consistency is chosen per data type, and money is never eventually consistent

    **Status:** Accepted · **Date:** 2026-02-10

    **Context.** Early prototypes used one replicated store with eventual consistency everywhere. Coupon double-redemption and order-status regressions followed.

    **Decision.** Each service charter lists its data types with a consistency model from the map above. Money movement and single-use resources are linearizable within their owning service and use conditional writes. Cross-service workflows use sagas with idempotent steps. Wall-clock timestamps are never used to order writes.

    **Consequences.** ✅ Correctness where it matters, speed elsewhere. ⚠️ Engineers must understand the models, so this chapter is required onboarding. ⚠️ Payment and promotion services pay latency for a synchronous standby.

    From [chapter 3](../foundations/ch03-distributed-systems-fundamentals.md).

<a id="adr-004"></a>

??? decision "ADR-004 · 2026-02-17 · Immutable images, promoted by digest; all variation through configuration"
    **Decision in full:** Immutable images, promoted by digest; all variation through configuration

    **Status:** Accepted · **Date:** 2026-02-17

    **Context.** Rebuilding per environment produced untested production artifacts (Incident 1). City launches required code changes for city-specific values.

    **Decision.** Every service builds one OCI image per commit. Environments and city cells receive the **same digest**. Configuration comes from ConfigMaps (non-secret) and Vault or mounted Secret files (secret). Services validate configuration at startup and must not ship defaults for business-critical values. One-off operations run as Kubernetes Jobs from the service image.

    **Consequences.** ✅ What was tested is what runs; rollbacks are instant; new cities need no code. ⚠️ Config becomes a first-class artifact needing review, schema validation and drift detection. ⚠️ Startup validation can block a rollout on a typo, which is intended.

    From [chapter 4](../foundations/ch04-twelve-factor.md).

<a id="adr-005"></a>

??? decision "ADR-005 · 2026-02-24 · Ordering protects its model with an anti-corruption layer towards Catalog"
    **Decision in full:** Ordering protects its model with an anti-corruption layer towards Catalog

    **Status:** Accepted · **Date:** 2026-02-24

    **Context.** Catalog (a supporting subdomain) serves many consumers (customer app, search, partner portal) and evolves quickly. Its model is shaped by restaurant onboarding needs: stock codes, photos, allergens, text prices. Ordering is a core subdomain whose correctness directly affects revenue.

    **Decision.** Catalog provides an Open Host Service with a published language (versioned REST API). Ordering consumes it **only** through an anti-corruption layer that translates into Ordering's `OrderableItem` and `RestaurantTerms`. Unknown or malformed upstream values fail safe (not orderable) or are reported as contract breaches (502). An ArchUnit rule forbids Catalog types outside the ACL.

    **Consequences.** ✅ Catalog can evolve without coordinating with Checkout; Ordering's rules stay expressed in its own language. ⚠️ Translation code and contract tests must be maintained. ⚠️ Some duplication of concepts (price, availability) between contexts, which is intended.

    From [chapter 5](../foundations/ch05-domain-driven-design.md).

<a id="adr-006"></a>

??? decision "ADR-006 · 2026-03-10 · FoodFleet decomposes by business capability, verified by scoring and change coupling"
    **Decision in full:** FoodFleet decomposes by business capability, verified by scoring and change coupling

    **Status:** Accepted · **Date:** 2026-03-10

    **Context.** The 2023 entity-service split (decomposition A) is being consolidated. Four candidates were scored against the checkout and browse-menu use cases and against 40 commits of monolith history. Six teams own fourteen capabilities.

    **Decision.** Adopt decomposition **B** (nine services along chapter 5's bounded contexts). Two deviations from the co-change evidence are deliberate: `restaurant-profile` stays in catalog (same team, read together on every menu browse), and `zone` stays a separate deployable from `delivery` because of its very different scaling and latency profile, while both remain with the Logistics team in one repository. Ordering is consolidated first, as a modular monolith step, before re-extraction.

    **Consequences.** ✅ Checkout sync hops fall from 16 to 9 and services on the sync path from 11 to 6; every service has one owning team, and no team owns more than two. ⚠️ Checkout availability (99.40% at 99.9% per service) still needs timeouts, menu caching and a coupon fallback to meet its SLO. ⚠️ `zone`–`delivery` change coupling is watched; if it stays above 60% for two more quarters, they merge.

    From [chapter 6](../foundations/ch06-service-decomposition.md).

<a id="adr-007"></a>

??? decision "ADR-007 · 2024-03-15 · FoodFleet organises around streams of change (Team Topologies)"
    **Decision in full:** FoodFleet organises around streams of change (Team Topologies)

    **Status:** Accepted · **Date:** 2024-03-15 · **Amended:** 2026-06-10

    **Context.** Six layer teams; features needed 3.4 teams on average; four teams over cognitive-load budget; schema changes queued for a DBA team; chapter 6's decomposition (ADR-006) defines nine capability services.

    **Decision.** Four stream-aligned teams (Checkout, Restaurant Partner, Logistics, Growth), two complicated-subsystem teams (Payments; Dispatch, added 2025), one platform team, one enabling team (SRE, formed from the DBA and Ops specialists). Every deployable and its data has exactly one owner; CODEOWNERS and the Backstage catalog are generated from the topology model and checked in CI. **Amendment (2026):** observability moves to a managed service so Platform owns one complicated domain; Dispatch is onboarded to the platform; the Logistics–Dispatch collaboration ends in Q3 with a versioned dispatch API.

    **Consequences.** ✅ Teams per feature fell from 3.38 to 1.75; congruence rose from 62% to 92%; no team is above nine people. ⚠️ Some engineers changed teams and domains, with a temporary productivity dip. ⚠️ Cross-team features still exist (6 handoffs per quarter's roadmap) and need X-as-a-Service APIs that are actually good.

    From [chapter 7](../foundations/ch07-conways-law-team-topologies.md).

<a id="adr-008"></a>

??? decision "ADR-008 · 2026-08-25 · Spring Boot 4.1 baseline for all FoodFleet services"
    **Decision in full:** Spring Boot 4.1 baseline for all FoodFleet services

    **Status:** Accepted · **Date:** 2026-08-25

    **Context.** Services ran a mix of Boot 3.3–3.5 with three HTTP client styles, two retry libraries and Jackson 2. Boot 3.5 is the last 3.x line, and the platform team wants one golden path.

    **Decision.** All services move to Spring Boot 4.1 and Java 25 using the order above, one stream-aligned team at a time. The golden-path template ([chapter 7](../foundations/ch07-conways-law-team-topologies.md)'s platform) provides: modular starters; MVC with virtual threads; HTTP service client groups with timeouts; built-in API versioning via header with 1.0 default; `@Retryable`/`@ConcurrencyLimit` for simple cases and Resilience4j circuit breakers where needed; Jackson 3 with contract tests; `@NullMarked` packages with NullAway; AOT cache in the image.

    **Consequences.** ✅ One way to call a service, version an API and build an image; less code per service. ✅ Faster container starts help autoscaling during dinner peaks. ⚠️ Jackson 3 default changes need contract tests per public API. ⚠️ Teams must declare limits explicitly once virtual threads are on.

    From [chapter 8](../foundations/ch08-spring-boot-4-essentials.md).

### Part 2 · Architecture & Patterns

<a id="adr-009"></a>

??? decision "ADR-009 · 2025-06-02 · payment-service uses ports and adapters; provider integrations are adapters"
    **Decision in full:** payment-service uses ports and adapters; provider integrations are adapters

    **Status:** Accepted · **Date:** 2025-06-02

    **Context.** Payments are a complicated subsystem (chapter 7) with many integrations: two providers (with a third under evaluation), PostgreSQL, events from kitchen and ordering, and nightly reconciliation. Provider vocabulary leaking into business rules caused a refund incident.

    **Decision.** Hexagonal structure with `application.port.in`/`out` packages, one adapter per provider behind `PaymentGateway`, hand-written mapping, translators with fail-safe defaults, contract tests for every driven port, and ArchUnit's onion rule in CI. Provider selection per payment method is a configuration and routing concern in the gateway adapter layer.

    **Consequences.** ✅ Adding the second provider took one sprint, with no change to payment rules. ✅ The core's tests run in under a second. ⚠️ More types and explicit mapping; new joiners need a short guide (this chapter). ⚠️ Reporting queries go to a separate read model instead of the repository port.

    From [chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md).

<a id="adr-010"></a>

??? decision "ADR-010 · 2024-05-20 · one API gateway for routing and protection; one BFF per client experience"
    **Decision in full:** one API gateway for routing and protection; one BFF per client experience

    **Status:** Accepted · **Date:** 2024-05-20

    **Context.** Fourteen publicly exposed services, inconsistent auth and CORS, a tracking screen that made four sequential calls, and app releases required for backend restructuring.

    **Decision.** A single `edge-gateway` (Spring Cloud Gateway Server Web MVC, owned by Platform) is the only public entry point: routing, token validation, header hygiene, request ids, rate limits, size limits, retries for idempotent reads and circuit breakers with fallbacks. No business logic and no aggregation in the gateway. Three BFFs (customer app, partner portal, rider app), each owned by its client team, expose per-screen endpoints with partial-failure handling. Services lose their public DNS names.

    **Consequences.** ✅ One place for edge security and observability; services can split and merge behind stable public paths. ✅ The tracking screen makes one call and still renders when one part fails. ⚠️ The gateway is critical infrastructure: three replicas across zones, strict change review. ⚠️ Some duplication between BFFs is accepted.

    From [chapter 10](../architecture-patterns/ch10-api-gateway-bff.md).

<a id="adr-011"></a>

??? decision "ADR-011 · 2024-02-12 · Kubernetes Services are FoodFleet's discovery mechanism"
    **Decision in full:** Kubernetes Services are FoodFleet's discovery mechanism

    **Status:** Accepted · **Date:** 2024-02-12

    **Context.** Services moved from VMs with Eureka to Kubernetes. Eureka's default staleness caused minutes of errors after crashes; running Eureka on Kubernetes would duplicate what the platform already does.

    **Decision.** Services call each other by Kubernetes Service DNS name (ClusterIP) with no client-side discovery library. gRPC traffic uses headless Services with client-side round robin. Every service has a readiness probe that checks only essential dependencies, `server.shutdown=graceful`, and a 5 s `preStop` pause. Idempotent calls retry once on another instance. Spring Cloud LoadBalancer with `SimpleDiscoveryClient` remains for local development and tests. Eureka is decommissioned after the last VM service moves.

    **Consequences.** ✅ Crashed instances leave rotation in seconds; no registry to run. ✅ Works the same for Java, Python and Go services. ⚠️ Hung instances depend on good readiness probes. ⚠️ Request-level policies (zone preference, weights) need a mesh or Gateway API features rather than a library.

    From [chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md).

<a id="adr-012"></a>

??? decision "ADR-012 · 2024-01-22 · one owner per table; schema per service by default; composition for display joins"
    **Decision in full:** one owner per table; schema per service by default; composition for display joins

    **Status:** Accepted · **Date:** 2024-01-22

    **Context.** Five services and a reporting job shared one PostgreSQL schema; the audit found 14 of 23 sampled statements touching other services' tables, including three services writing `orders.status`.

    **Decision.** Every table has one owner (recorded in `table-owners.txt` and checked in CI with the audit). Each service gets its own database user and schema on the shared cluster; payment-service gets its own database instance. Foreign writes are removed first (events to the owner), then foreign reads (owner APIs with batch endpoints, local copies from events, or composition). Order history is served by API composition; cross-owner analytical queries move to the warehouse; restaurant dashboards use a CQRS read model (chapter 13).

    **Consequences.** ✅ Schema changes are local; ordering alone enforces the order lifecycle; reporting can't slow checkout. ⚠️ Joins became API calls and events; batch endpoints had to be added to catalog and delivery. ⚠️ Eventual consistency between services is now explicit and must be designed for (chapters 14–15).

    From [chapter 12](../architecture-patterns/ch12-database-per-service.md).

<a id="adr-013"></a>

??? decision "ADR-013 · 2024-03-04 · Rider wallets are event-sourced; read models via projections"
    **Decision in full:** Rider wallets are event-sourced; read models via projections

    **Status:** Accepted · **Date:** 2024-03-04

    **Context.** Wallet disputes took days to investigate; a penalty bug affected 1,800 riders with no way to reconstruct past balances. Riders, support, finance and ops need different views of the same data.

    **Decision.** The wallet is an event-sourced aggregate (decide/evolve) in a relational event store with per-stream optimistic concurrency and serialised appends per partition. Read models (today's earnings, payout queue, weekly incentives, finance export) are projections with transactional checkpoints. Snapshots every 50 events. Integration events (`PayoutCompleted` for notifications and finance) are published separately through the outbox. Restaurant, catalog and customer data stay CRUD.

    **Consequences.** ✅ Any balance at any time is explainable event by event; disputes are resolved in minutes. ✅ New incentive programmes were built from two years of history in a day. ⚠️ Event schemas are long-lived contracts with upcasters. ⚠️ Read models lag; the app shows "processing" for payouts until the projection confirms.

    From [chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md).

<a id="adr-014"></a>

??? decision "ADR-014 · 2024-04-08 · Place-order is an orchestrated saga with the kitchen as pivot"
    **Decision in full:** Place-order is an orchestrated saga with the kitchen as pivot

    **Status:** Accepted · **Date:** 2024-04-08

    **Context.** Placing an order spans ordering, promotions, payment, kitchen and delivery, each with its own database (ADR-012). Failures after payment left money held; choreography made the flow hard to monitor.

    **Decision.** An orchestrator inside ordering-service runs the saga: create order (PENDING, a semantic lock) → reserve coupon → authorize payment → **kitchen accepts (pivot)** → capture → request rider → approve. Steps before the pivot have compensations; steps after it are retried until they succeed, with STUCK sagas on a dashboard. The saga log and outgoing commands are written through an outbox; every participant is idempotent per (saga id, step) and returns stored outcomes for repeats. Long-running flows with humans (refund review, onboarding) use a workflow engine.

    **Consequences.** ✅ No money held for uncooked food; every order's progress is visible by saga id. ✅ Hundreds of orchestrator restarts per day are harmless. ⚠️ Customers see "confirming with restaurant" while the saga runs. ⚠️ Every participant team owns idempotency and compensation correctness, enforced with contract tests.

    From [chapter 14](../architecture-patterns/ch14-saga-pattern.md).

<a id="adr-015"></a>

??? decision "ADR-015 · 2024-04-22 · Every service publishes through a transactional outbox; every consumer uses an inbox"
    **Decision in full:** Every service publishes through a transactional outbox; every consumer uses an inbox

    **Status:** Accepted · **Date:** 2024-04-22

    **Context.** Dual writes lost or invented about 1–2% of events under failure (missing loyalty points, phantom orders in analytics). The saga orchestrator (ADR-014) needs its commands sent reliably with its log writes.

    **Decision.** Services never call the broker from transactional code. They append events to an `outbox` table in the same transaction as the change; the platform library provides `Outbox.append` (requires a transaction) and a polling relay (`SKIP LOCKED`, single active instance, stop on failure). High-volume services use Debezium with the Outbox Event Router instead. Message id and aggregate key are mandatory. Every consumer records processed message ids in an `inbox` table in the same transaction as its effect.

    **Consequences.** ✅ No lost or phantom events; duplicates are absorbed. ✅ The saga log and its outgoing commands commit together. ⚠️ Events are delayed by the relay (p99 ~600 ms polling, ~80 ms CDC). ⚠️ Two more tables per service and their cleanup; Kafka Connect to operate for CDC services.

    From [chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md).

<a id="adr-016"></a>

??? decision "ADR-016 · 2024-05-06 · Pricing leaves the monolith by strangler fig, with shadowing and cohort canaries"
    **Decision in full:** Pricing leaves the monolith by strangler fig, with shadowing and cohort canaries

    **Status:** Accepted · **Date:** 2024-05-06

    **Context.** The "Pricing 2.0" rewrite (single cut-over) was cancelled after eight months. Pricing is called externally (`/api/pricing`) and in-process by checkout, cart and refunds.

    **Decision.** A `PricingService` interface in the monolith with the legacy code behind an adapter (branch by abstraction, enforced by ArchUnit); a facade (in-process and at the gateway) with LEGACY → SHADOW → CANARY → NEW, read from one flag. Shadow until mismatches are zero, with differences of ≤1 paisa on the surcharge and total accepted: the monolith's `double` arithmetic doesn't round half-up consistently, and pricing-service rounds half-up. Canary steps 2 → 5 → 25 → 50 → 100% by customer cohort, with an automated guard on errors, p99 latency and average basket value, and fallback to the monolith for reads. Legacy pricing code deleted 30 days after 100%.

    **Consequences.** ✅ No customer saw a wrong price; seven weeks from first shadow to 100%. ✅ The same facade and playbook are reused for the next capabilities (search, notifications). ⚠️ For 1.2% of late-night orders the surcharge changed by one paisa; finance and support were told in advance. ⚠️ The facade and flags are temporary code that must be removed on schedule.

    From [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md).

<a id="adr-017"></a>

??? decision "ADR-017 · 2024-05-20 · Payment calls go through a platform-owned ambassador; vendor metrics through adapters"
    **Decision in full:** Payment calls go through a platform-owned ambassador; vendor metrics through adapters

    **Status:** Accepted · **Date:** 2024-05-20

    **Context.** Three languages call the payment provider with three different retry and credential implementations; a vendor tablet gateway can't be scraped by Prometheus; a legacy library logs only to files.

    **Decision.** Every service calling the payment provider uses the platform's payment ambassador as a native sidecar (loopback only, token from a mounted secret, 400 ms per try, 3 attempts for idempotent requests, circuit breaker, metrics). Apps send an `Idempotency-Key` on every payment POST and do not retry payment calls themselves. Vendor systems are scraped through adapters. File logging is allowed only with the log-shipper sidecar until the library is replaced. Mesh adoption for mTLS is evaluated separately ([chapter 57](../deployment/ch57-service-mesh.md)).

    **Consequences.** ✅ Payment success during provider incidents rose from ~90% to >99.5% for every language at once. ✅ Token rotation and provider changes are platform releases. ⚠️ +35m CPU and +112 MiB per pod for the three sidecars. ⚠️ The platform team is on the critical path for payment-call behaviour.

    From [chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md).

<a id="adr-018"></a>

??? decision "ADR-018 · 2024-06-03 · Every remote call is timed out, bulkheaded and observable; retries and breakers by policy"
    **Decision in full:** Every remote call is timed out, bulkheaded and observable; retries and breakers by policy

    **Status:** Accepted · **Date:** 2024-06-03

    **Context.** A maps brownout took down order status, an endpoint unrelated to maps; a retry storm across three layers prolonged another incident. Virtual threads (ADR-008) removed the thread pool's implicit concurrency cap.

    **Decision.** The platform's client starter sets connect and read timeouts on every HTTP service client group (no defaults allowed in review), and a `@ConcurrencyLimit` bulkhead per dependency. Retries live only in the service closest to the dependency: at most 2 attempts, jittered, idempotent operations only; gateways and mobile apps don't retry server errors. Dependencies that can brown out get a Resilience4j breaker (count-based, 50 calls, 50% failure or 80% slow calls, 5 s open, 3 trials) and a product-approved fallback. Breaker states, bulkhead waits, retries and fallbacks are dashboarded per dependency; latency injection is part of the monthly game day.

    **Consequences.** ✅ The next maps brownout showed approximate ETAs for two minutes; status stayed at 100%. ✅ Load on struggling providers falls instead of rising. ⚠️ Fallbacks need product decisions and tests for every dependency. ⚠️ Tuning requires latency data per dependency, which the platform now collects by default.

    From [chapter 18](../architecture-patterns/ch18-resilience-patterns.md).

<a id="adr-019"></a>

??? decision "ADR-019 · 2024-06-17 · Reference data is shared by event-carried state transfer on compacted topics"
    **Decision in full:** Reference data is shared by event-carried state transfer on compacted topics

    **Status:** Accepted · **Date:** 2024-06-17

    **Context.** Synchronous menu reads failed every order during a catalog outage; thin events with callbacks overloaded catalog after outages. Menu, restaurant, zone and rider-status data are read far more often than they change, by many services.

    **Decision.** Owners publish integration events carrying the entity's consumer-relevant state, an owner-assigned version and an event id, keyed by entity id, through the outbox, to compacted topics (`foodfleet.<context>.<entity>`). Consumers keep local replicas updated newest-version-wins, and bootstrap from the compacted topic. Reactions to business facts (`OrderDelivered` → rating request, loyalty) use small notification events. Schemas are registered with backward-compatibility checks; events carry no customer personal data beyond ids.

    **Consequences.** ✅ Orders keep flowing, on fresh data, through catalog outages. ✅ No callback load on owners. ⚠️ ~2.4× the bytes on the bus for menu events. ⚠️ Every consumer holds a copy; payload changes need the compatibility process.

    From [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md).

<a id="adr-020"></a>

??? decision "ADR-020 · 2024-07-01 · Architecture anti-patterns are detected from evidence and gated in the build"
    **Decision in full:** Architecture anti-patterns are detected from evidence and gated in the build

    **Status:** Accepted · **Date:** 2024-07-01

    **Context.** The 2023 entity-service architecture had lockstep releases, synchronous cycles, chatty calls, shared tables and a shared domain library (16 findings, 7 high). Decomposition B (ADR-006) and chapters 12–19 removed all but one. Without a guard, the same pressures would bring them back.

    **Decision.** The platform team maintains an architecture model generated weekly from traces (calls per request), database audit logs (table access), dependency manifests (libraries) and CI/CD history (deploys), with hand-maintained service metadata. Detectors run in the architecture repository's build with these thresholds: lockstep ≥ 70% co-deploys over ≥ 5 deploys; chatty ≥ 5 sync calls per request; fan-out ≥ 6 sync dependencies; chains ≥ 3 sync hops; nano-services ≤ 2 endpoints and ≤ 3,000 lines with one caller or release partner; any non-owner table access; any shared domain library; > 2 versions or mixed majors of a shared library. New findings fail the build; accepted findings need an owner and a ticket, and are removed when fixed.

    **Consequences.** ✅ New coupling is visible the week it appears, with evidence, not at the next incident. ✅ Reviews discuss causes, not opinions. ⚠️ The model is only as true as its sources; trace sampling and audit-log coverage are monitored. ⚠️ Thresholds will produce some false positives; the baseline is where judgment is recorded.

    From [chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md).

### Part 3 · Communication

<a id="adr-021"></a>

??? decision "ADR-021 · 2024-07-15 · FoodFleet REST APIs use keyset pagination, conditional updates and RFC 9457 errors"
    **Decision in full:** FoodFleet REST APIs use keyset pagination, conditional updates and RFC 9457 errors

    **Status:** Accepted · **Date:** 2024-07-15

    **Context.** Deep offset pages timed out, live lists showed duplicates and skipped items, errors had a different shape per service, and concurrent edits lost data.

    **Decision.** Collections that can grow beyond a few hundred items use opaque, versioned keyset cursors (`limit` default 20, maximum 100) with a `next` link in the body and a `Link: rel="next"` header; the sort key ends with a unique id and is backed by a matching index. Single resources return a strong ETag from a version column; updates require `If-Match` (428 without, 412 when stale). All errors are RFC 9457 problem documents with `type` URIs under `https://docs.foodfleet.example/problems/`, documented per API; `spring.mvc.problemdetails.enabled` is on everywhere. Versioning follows ADR-008. Hypermedia is limited to pagination, `self` and state-dependent action links.

    **Consequences.** ✅ Deep pages cost the same as the first; no duplicates or skips; the app handles errors by `type`; no more lost updates. ⚠️ No "jump to page N"; UIs offer search and date filters instead. ⚠️ Clients must handle 412 with a re-read.

    From [chapter 21](../communication/ch21-rest-api-design.md).

<a id="adr-022"></a>

??? decision "ADR-022 · 2024-07-29 · Service calls use HTTP service clients on RestClient, configured per group"
    **Decision in full:** Service calls use HTTP service clients on RestClient, configured per group

    **Status:** Accepted · **Date:** 2024-07-29

    **Context.** Services used RestTemplate, WebClient with `.block()`, OpenFeign and hand-written JDK clients, with inconsistent timeouts and error handling. Virtual threads (ADR-008) removed the main reason for WebClient in MVC services. Spring announced RestTemplate's deprecation; Spring Cloud OpenFeign is feature-complete.

    **Decision.** MVC services call other services through `@HttpExchange` interfaces in groups (`@ImportHttpServices`), one group per destination, configured with `spring.http.serviceclient.<group>.*` (base URL, connect and read timeouts are mandatory) and the platform's group configurer (RFC 9457 error mapping, auth, user agent). WebClient is used only in WebFlux services and for streaming. No new RestTemplate or Feign code; existing usages migrate by Q4.

    **Consequences.** ✅ Consistent timeouts, errors, metrics and auth across services. ✅ Clients are interfaces, easy to fake in tests. ⚠️ A migration effort for ~60 Feign clients and ~40 RestTemplate call sites. ⚠️ Teams must understand virtual-thread pinning and concurrency limits ([chapter 8](../foundations/ch08-spring-boot-4-essentials.md)).

    From [chapter 22](../communication/ch22-http-clients.md).

<a id="adr-023"></a>

??? decision "ADR-023 · 2024-09-16 · Internal latency-sensitive and streaming APIs use gRPC; the edge stays REST"
    **Decision in full:** Internal latency-sensitive and streaming APIs use gRPC; the edge stays REST

    **Status:** Accepted · **Date:** 2024-09-16

    **Context.** The tracking screen polled every 5 seconds (360 requests per order); the tracking → eta → maps chain had independent 1 s timeouts and kept working after the app gave up; the tracking/eta JSON contract was undocumented and broke on a field rename. Partner and browser clients need plain HTTP, caching and familiar tooling.

    **Decision.** Service-to-service APIs on latency-sensitive paths (tracking, eta, dispatch) and all streaming APIs use gRPC, with `.proto` files in the `foodfleet-apis` repository (`buf lint` and `buf breaking` in CI) and generated artifacts per language. The mobile BFF ([chapter 10](../architecture-patterns/ch10-api-gateway-bff.md)) sets a deadline per screen and calls gRPC services; public and partner APIs remain REST (ADR-021). Load balancing per request through the mesh (ADR-057); until then, headless Services with `round_robin`. Every server rejects calls without a deadline after a migration period.

    **Consequences.** ✅ One stream per tracking screen instead of 360 polls; deadlines and cancellation end at the slow dependency. ✅ Contracts reviewed and checked by tooling. ⚠️ A schema repository and generation pipeline to own. ⚠️ Debugging needs grpcurl and reflection; proxies, WAF rules and dashboards had to learn HTTP/2. ⚠️ Two API styles to support, with clear rules for which to use.

    From [chapter 23](../communication/ch23-grpc.md).

<a id="adr-024"></a>

??? decision "ADR-024 · 2024-10-21 · The customer apps use a GraphQL API in the mobile BFF; services keep their own protocols"
    **Decision in full:** The customer apps use a GraphQL API in the mobile BFF; services keep their own protocols

    **Status:** Accepted · **Date:** 2024-10-21

    **Context.** The customer BFF had 140 screen-specific REST endpoints, versioned per app release, and the web team was building a second BFF with overlapping endpoints. Most changes requested by the apps were new fields or new combinations of existing data. Partner APIs are REST (ADR-021); services talk REST, gRPC (ADR-023) and events (ADR-019).

    **Decision.** The customer BFF exposes one GraphQL schema for iOS, Android and web, built with Spring for GraphQL. Fields that cross to another service use batch loading; a test per screen query asserts its backend call count. Production accepts only persisted queries registered by app builds; complexity limit 2,000, depth 8, page size at most 50; introspection and GraphiQL are disabled outside development. The customer and tenant come from the gateway's verified headers. Metrics and alerts are per operation and per field error. Services do not expose GraphQL to each other. Federation is deferred until more than two teams need to contribute types (expected with the grocery vertical).

    **Consequences.** ✅ New screens and redesigns mostly ship without backend changes; one schema replaces two BFFs. ✅ Measured on the orders screen: 1 request instead of 12, 8× fewer bytes. ⚠️ The BFF team owns batching, limits and the schema review process. ⚠️ HTTP caching at the CDN is mostly lost; client-side caches and service-side caches replace it. ⚠️ Monitoring had to move from HTTP status to GraphQL operations.

    From [chapter 24](../communication/ch24-graphql.md).

<a id="adr-025"></a>

??? decision "ADR-025 · 2024-11-04 · Consumers commit after processing, are idempotent, and dead-letter instead of skipping or looping"
    **Decision in full:** Consumers commit after processing, are idempotent, and dead-letter instead of skipping or looping

    **Status:** Accepted · **Date:** 2024-11-04

    **Context.** Three incidents in two months: a poison message stopped a partition for 40 minutes; the default error handler skipped orders during a partner outage; in-listener thread pools reordered order events. Consumers of `order-events` include ordered state machines (kitchen tickets, rider assignment) and independent side effects (receipts, push notifications, search indexing).

    **Decision.** All consumers use the platform consumer starter: container-managed commits after processing, an inbox for idempotency, `ErrorHandlingDeserializer`, and an explicit error handler. Ordered consumers use blocking exponential backoff (at most 31 s) and then `<topic>-dlt`; independent consumers use `@RetryableTopic` (1 s, 10 s, 60 s) and their own DLT. Validation failures are not retryable. No unordered parallelism inside a partition: `order-events` has 24 partitions keyed by order id. Every DLT message pages the owning team during business hours and is replayed through the DLT tool after the fix. Lag alerts fire on 2 minutes of growth.

    **Consequences.** ✅ No lost or stuck orders since; poison messages cost one DLT entry, not an outage. ✅ Receipt delivery no longer delayed by one customer's mail server. ⚠️ Retry topics and DLTs multiply topics (3–4 per consumer group). ⚠️ Teams must own their DLTs; replay requires idempotent consumers, which some legacy consumers lacked.

    From [chapter 25](../communication/ch25-asynchronous-messaging.md).

<a id="adr-026"></a>

??? decision "ADR-026 · 2024-12-02 · Customers receive tracking over SSE, riders send positions over WebSocket, through a dedicated tracking …"
    **Decision in full:** Customers receive tracking over SSE, riders send positions over WebSocket, through a dedicated tracking gateway

    **Status:** Accepted · **Date:** 2024-12-02

    **Context.** Tracking by polling produced 60,000 requests a second at peak, maps that jumped every 5 s, and rider battery drain. Customer updates are one-way; rider updates are a continuous inbound stream. Mobile apps go to the background often.

    **Decision.** A separate `tracking-gateway` service on Spring MVC with virtual threads. Customers: `GET /orders/{id}/tracking` as SSE, with event ids, a 50-event replay buffer, a random `retry:` of 1–10 s, 15 s heartbeats, and 503 when an instance reaches 20,000 streams. Riders: one WebSocket per shift at `/ws/rider`, authenticated at the handshake by the gateway's `X-Rider-Id`, validated, rate-limited to one position per second. Positions flow through Kafka (`tracking-positions`, keyed by order) and per-order Redis pub/sub channels to the instance holding each stream. Deploys drain streams in batches over 60 s. Push notifications (FCM/APNs) carry key moments when the app is in the background.

    **Consequences.** ✅ Tracking traffic fell from 60,000 requests a second to a few hundred connects a second; the map moves smoothly. ✅ One instance holds ~20,000 streams in about 1 GB. ⚠️ A stateful connection tier to operate: draining, fan-out, connection metrics. ⚠️ Reconnect behaviour must be engineered (jitter, shedding) or a network blip becomes an outage.

    From [chapter 26](../communication/ch26-websockets-sse.md).

<a id="adr-027"></a>

??? decision "ADR-027 · 2025-01-13 · Contracts are documents in the repo, checked by diff and by consumers' contracts; consumers are tolerant …"
    **Decision in full:** Contracts are documents in the repo, checked by diff and by consumers' contracts; consumers are tolerant readers

    **Status:** Accepted · **Date:** 2025-01-13

    **Context.** Eleven of twelve changes to the Order API in 2024 broke a consumer, including three customer-facing incidents. APIs and events were documented after the fact, if at all; the Android app was a strict reader; a meaning change went unnoticed for two days.

    **Decision.** Every HTTP API has an OpenAPI 3.1 document and every topic an AsyncAPI 3.0 document, in the producing service's repository, listing consumers in `x-consumers`. CI lints them, runs oasdiff (or the schema registry check, `FULL_TRANSITIVE`, for event schemas) and fails on breaking changes outside a new major version. Consumers whose failure is customer-facing (apps, notifications, payments) publish Spring Cloud Contract contracts that run in the provider's build. All consumers are tolerant readers: unknown fields ignored, unknown enum values mapped to a default (Jackson's `@JsonEnumDefaultValue`, Kotlin `ignoreUnknownKeys` and custom enum serializers). Meaning changes are new fields. Breaking changes follow expand–migrate–contract with usage tracking per consumer.

    **Consequences.** ✅ In the model of 2024's history, tolerant readers alone remove three of the eleven breaking changes, and the tools then catch all but the meaning change. ✅ Removing unused fields is safe when contracts show nobody uses them. ⚠️ Teams maintain documents and contracts; reviews include consumers. ⚠️ Mobile apps need a release before the API can rely on tolerance, because old versions stay installed.

    From [chapter 27](../communication/ch27-api-contracts.md).

<a id="adr-028"></a>

??? decision "ADR-028 · 2025-02-03 · Keys for side effects, versions for state, and three ids on every message"
    **Decision in full:** Keys for side effects, versions for state, and three ids on every message

    **Status:** Accepted · **Date:** 2025-02-03

    **Context.** Duplicate payments under peak load, duplicate notifications after deploys, and investigations misled by timestamps. Clients and gateways retry by design; Kafka delivers at least once; DLT replays are routine.

    **Decision.** Every non-idempotent API operation with side effects requires an `Idempotency-Key` (IETF draft semantics: 400/409/422, replayed responses), implemented with an atomic claim and lease in the service's database, response stored with the business write, and the key passed to downstream providers; keys are kept 24 h. Consumers deduplicate on event id in a shared store for 1 h, and state-changing consumers additionally apply events only if the entity version is newer, making replays safe. Every message carries `event-id`, `correlation-id`, `causation-id` and `traceparent`; logs carry trace and correlation ids. Timestamps are never used to order events across services.

    **Consequences.** ✅ Duplicate charges went to zero (measured: one charge per key under 4× concurrent duplicates). ✅ Deploys and replays no longer repeat notifications. ✅ Support can show an order's causal tree. ⚠️ Every service with side effects owns an idempotency table and its purge job. ⚠️ Clients must generate and persist keys per action; old app versions without keys are served by a gateway fallback (key derived from order id and amount) until they age out.

    From [chapter 28](../communication/ch28-idempotency-correlation.md).

### Part 4 · Data

<a id="adr-029"></a>

??? decision "ADR-029 · 2025-02-17 · PostgreSQL by default; Redis for copies and ephemeral state; nothing else without measurements"
    **Decision in full:** PostgreSQL by default; Redis for copies and ephemeral state; nothing else without measurements

    **Status:** Accepted · **Date:** 2025-02-17

    **Context.** Menu reads, dish search, cart pricing, rider positions and history have very different access patterns. Proposals were on the table for MongoDB (menus), Elasticsearch (search), Redis (riders, prices) and a time-series database (history), for a platform team of eight.

    **Decision.** PostgreSQL is the source of truth for every service that owns business data. The catalog keeps menus in tables and rebuilds a JSONB menu card per restaurant in the same transaction; dish search uses full-text search with prefix matching (partial words stemmed and as typed) and a trigram fallback for typos. Redis holds only copies and ephemeral state: the price cache, fed from the catalog's outbox by a claiming projector with version-guarded writes, and live rider positions, with silent riders pruned after 30 s. Rider history is in PostgreSQL, partitioned by day with BRIN, kept 7 days and dropped by partition. A further store needs an ADR with the access pattern, measurements on PostgreSQL and Redis, and its operating cost; a dedicated search engine is expected when relevance tuning, synonyms or facets are needed.

    **Consequences.** ✅ Menu reads 14× cheaper than joins, with cross-restaurant queries still at 1 ms. ✅ Search finds misspelt dishes (29 of 29 test queries). ✅ Position writes at 3.8–5.6× PostgreSQL's rate at the same durability, history retention a metadata operation. ✅ Zero wrong prices in the sync experiment under crashes and outages. ⚠️ Typo searches cost ~50 ms at the median; a search engine is on the roadmap. ⚠️ The menu card rebuild serialises edits per restaurant (~500 edits/s per menu). ⚠️ Live positions can lose about a second on a Redis crash, by design.

    From [chapter 29](../data/ch29-polyglot-persistence.md).

<a id="adr-030"></a>

??? decision "ADR-030 · 2025-03-03 · No distributed transactions between services; outbox-driven sagas with idempotent participants"
    **Decision in full:** No distributed transactions between services; outbox-driven sagas with idempotent participants

    **Status:** Accepted · **Date:** 2025-03-03

    **Context.** Placing an order changes the order (order-service) and the wallet and stock (payment-service). An XA proposal promised atomicity without code changes. Measured on two PostgreSQL clusters: 2PC took 2.4× the latency, half the throughput on hot dishes, collapsed to 2–6 orders/s for as long as one transaction was in doubt (surviving a restart), and accepted no orders while payments was down.

    **Decision.** Services never share a transaction. Placing an order is one order-service transaction (order `PENDING` + outbox row) answered with `202 Accepted`; a relay delivers it to payment-service, which applies it in its own transaction, idempotently, storing a decision per order; the relay records `CONFIRMED` or `REJECTED` with a reason. Unknown outcomes stay `PENDING` and are reconciled by a recovery job with a 5 s grace period. `max_prepared_transactions` is 0 on all service databases; any future 2PC use needs an ADR, a decision log and an in-doubt monitor.

    **Consequences.** ✅ Orders are accepted while payments is down and confirmed when it returns (measured: within 10.2 s, after a 10 s outage). ✅ Hot dishes are locked per batch, not per order. ✅ No in-doubt transactions to recover. ⚠️ Customers see `PENDING` for ~20–30 ms normally, longer during incidents; the app shows "confirming". ⚠️ Every participant must be idempotent and store decisions. ⚠️ Rejections happen after acceptance, so the app and notifications handle them.

    From [chapter 30](../data/ch30-distributed-transactions.md).

<a id="adr-031"></a>

??? decision "ADR-031 · 2025-03-17 · Session guarantees with consistency tokens; atomic updates; CRDTs for multi-writer data"
    **Decision in full:** Session guarantees with consistency tokens; atomic updates; CRDTs for multi-writer data

    **Status:** Accepted · **Date:** 2025-03-17

    **Context.** Moving reads to asynchronous replicas caused stale profiles, tracking pages that went backwards, and lost loyalty points. Measured: 15% stale re-reads from a lagging replica, 19% backwards polls over two replicas, 15.5% of points lost to read-modify-write.

    **Decision.** Every read endpoint declares its guarantee. Writes return a `Consistency-Token` (the primary's WAL position after the commit); reads that need read-your-writes or monotonic reads send the newest token they hold, and the data-access library serves them from a replica that has replayed that far (checked before the query, in a separate statement), waits up to 50 ms, else the primary. Counters and balances change only through atomic or conditional updates; records people edit require `If-Match`. The shared cart, which the mobile app also edits offline, is an OR-Set with HLC-stamped fields.

    **Consequences.** ✅ No stale own-data reads and no backwards tracking (measured: 0), with 2.9% of reads on the primary. ✅ No lost points. ✅ Offline cart edits merge without losing items. ⚠️ Clients must store and send tokens; the BFF does it for the apps. ⚠️ Reads may wait up to 50 ms when a replica lags; the fallback rate is monitored. ⚠️ OR-Set tombstones need compaction.

    From [chapter 31](../data/ch31-data-consistency.md).

<a id="adr-032"></a>

??? decision "ADR-032 · 2025-03-31 · Cache by purpose: evict-after-commit for details, versioned write-through for prices, stale-while-revalidate …"
    **Decision in full:** Cache by purpose: evict-after-commit for details, versioned write-through for prices, stale-while-revalidate for rankings, write-behind for counters

    **Status:** Accepted · **Date:** 2025-03-31

    **Context.** One cache-aside pattern for everything gave no speedup on primary-key reads, stale prices after changes, and a database spike at every expiry of the home-screen ranking. Measured on PostgreSQL and Redis: Redis was at 2.1–2.2 ms p50 against 1.7–1.8 ms than the database for menu cards; "update, then delete" left stale entries; 80 recomputations per expiry for the ranking.

    **Decision.** Each cache states its purpose, staleness budget and strategy. Restaurant details: `@Cacheable(sync = true)` on a transaction-aware RedisCacheManager, evicted after commit. Item prices: cache-aside reads and write-through after commit, every cache write through a set-if-newer script, 30-minute safety TTL. Rankings and dashboards: stale-while-revalidate with a refresh lock. View counters: write-behind with an atomic claim and batch ids, on a Redis instance separate from the caches. Menu cards: no shared cache (primary-key reads), HTTP caching with ETags instead (chapter 21). Checkout and payments read the database. All caches are optional at runtime and monitored for hit rate and stale serves.

    **Consequences.** ✅ No stale prices left behind; under 0.05% of reads see a just-changed price late. ✅ The ranking costs one query per expiry, and no user waits for it. ✅ View counting costs the database one statement per second. ⚠️ Up to ~1 s of views can be lost if the counter Redis restarts. ⚠️ Rankings can be one refresh old. ⚠️ More than one caching pattern to learn; the platform library hides most of it.

    From [chapter 32](../data/ch32-caching-strategies.md).

<a id="adr-033"></a>

??? decision "ADR-033 · 2025-04-14 · Fleet-wide pool budgets, lock-safe migrations with expand/contract, and slot-based sharding only when one …"
    **Decision in full:** Fleet-wide pool budgets, lock-safe migrations with expand/contract, and slot-based sharding only when one primary is not enough

    **Status:** Accepted · **Date:** 2025-04-14

    **Context.** Deploys exhausted `max_connections`; an instant migration froze checkout behind a long query; a column rename failed every old instance during a rolling deploy. Measured on PostgreSQL 16 under write load: little throughput gained past ~16 connections on 2 vCPUs while latency inside the database grew 13–17× by 256; an `ADD COLUMN` behind a 12 s query blocked writes for 10 s; renaming in place failed thousands of statements during a deploy, expand/contract none.

    **Decision.** Each service's pool is fixed-size and budgeted fleet-wide, including the deploy surge; CI checks the budget against each database's `max_connections` and cores. Migrations run with `lock_timeout` 2 s and are retried by the deploy pipeline; table rewrites are forbidden in review; indexes are built `CONCURRENTLY` in their own migration, without `IF NOT EXISTS`; constraints are added `NOT VALID` and validated in a later migration; services report invalid indexes. Renames, type changes and drops use expand/contract across releases, with phases as configuration and throttled keyset backfills that copy rows that differ. Reports run on replicas. Sharding is deferred until write load requires it; when it does, orders are sharded by customer through a 4,096-slot map, with the slot embedded in order ids.

    **Consequences.** ✅ Deploys no longer touch the connection limit. ✅ Schema changes run during peak hours; a blocked migration fails fast instead of blocking traffic. ✅ No release depends on all instances switching at once. ⚠️ A rename takes four releases instead of one. ⚠️ Backfills on large tables take hours and must be watched for replica lag. ⚠️ The future slot map and cross-shard queries are new operational surface.

    From [chapter 33](../data/ch33-database-scaling.md).

<a id="adr-034"></a>

??? decision "ADR-034 · 2025-04-28 · Reports come from a reporting store built from change events, never from the services' databases"
    **Decision in full:** Reports come from a reporting store built from change events, never from the services' databases

    **Status:** Accepted · **Date:** 2025-04-28

    **Context.** Finance and operations need cross-service reports (orders, restaurants, payments). Composing APIs was slow and loaded every service; reading the services' databases slowed checkout and bloated tables; the first event-based projection overstated revenue and misdated late-night orders. Measured on PostgreSQL: API composition 2.7–2.9 s for one 30-day report with 620–656 calls; reports on the primary cut its writes by 67–69%; counting events +7.1%, UTC days 4% of orders misdated; state with versions exact.

    **Decision.** A reporting service owns a reporting store fed by order, payment and restaurant events from Kafka (outbox events where published; CDC for the rest, with capped and monitored slots). The projector keeps facts by key and version and adjusts aggregates by deltas; business days use the city's time zone. Every report returns its as-of time and a stale flag against a 5-minute objective. Monthly summaries are materialized views refreshed `CONCURRENTLY`. The store is rebuildable by replay and reconciled daily with payments. No report or person reads the services' production databases. Long-history analysis moves to a lake fed by the same topics when needed; datasets there have owners and contracts.

    **Consequences.** ✅ Cross-service reports in under a second, with no load on the services. ✅ Totals that reconcile, on the right day. ✅ Services keep their schemas private. ⚠️ Reports are minutes behind, and say so. ⚠️ One more service, pipeline and set of slots to run. ⚠️ Event contracts must carry everything reports need (amounts, versions, times), which shapes the producers' events.

    From [chapter 34](../data/ch34-reporting-analytics.md).

### Part 5 · Security

<a id="adr-035"></a>

??? decision "ADR-035 · 2025-05-05 · Threat models as code per system; authorization in services with per-use-case request and response types …"
    **Decision in full:** Threat models as code per system; authorization in services with per-use-case request and response types; OWASP API Top 10 controls as tested defaults

    **Status:** Accepted · **Date:** 2025-05-05

    **Context.** An order-visibility incident traced to missing object-level authorization, entity serialisation and entity binding. Threat models existed as workshop diagrams, last updated a year earlier. Measured on the redesigned order service: the policy agreed with an independently written rule table on all 18,600 decisions; PBKDF2 at 600,000 iterations made each password guess 1.7–1.9 million times costlier than SHA-256; login throttling cut checks against one account from ~26,700 an hour to 18.

    **Decision.** Every system keeps a STRIDE-per-element threat model as code; the build fails on open threats and on expired acceptances; architecture changes update it in the same pull request. Every service authorizes every operation with the authenticated caller and the stored object, in one policy component per aggregate, tested exhaustively against independently written rules; objects the caller may not read answer 404. Request and response types are per use case; entities are never bound or serialised; unknown fields are rejected. Pages and payloads are bounded. User-supplied URLs are checked by resolved address with redirects refused. Passwords live only in the identity provider (chapter 37).

    **Consequences.** ✅ The most common API vulnerability classes are designed out and tested. ✅ Threat models stay current because the build depends on them. ✅ Security reviews start from the model and its evidence. ⚠️ More types (views, patches) per endpoint. ⚠️ The exhaustive authorization tests need maintaining as roles and states grow. ⚠️ Acceptances need owners who actually review them.

    From [chapter 35](../security/ch35-security-fundamentals.md).

<a id="adr-036"></a>

??? decision "ADR-036 · 2025-05-19 · OAuth 2.1 flows only; audience-checked short-lived JWT access tokens; staged key rotation; token exchange for …"
    **Decision in full:** OAuth 2.1 flows only; audience-checked short-lived JWT access tokens; staged key rotation; token exchange for calls on a user's behalf

    **Status:** Accepted · **Date:** 2025-05-19

    **Context.** Tokens from one application were accepted by every API; key rotations caused bursts of 401s; relayed customer tokens leaked from a partner's logs and couldn't be revoked. Measured with the JDK: local ES256 verification ~0.6 ms (RS256 ~45 µs) against ~1.4 ms for introspection on loopback; a revoked 15-minute JWT keeps working 450 s on average; sudden rotation rejected 29 requests, staged 0; token exchange narrowed a 4-scope, 15-minute token to 1 scope and 5 minutes.

    **Decision.** Users sign in with authorization code + PKCE; services use client credentials. Access tokens are JWTs (`at+jwt`) with an audience per API and 15-minute lifetimes; refresh tokens rotate and are revocable. Resource servers allow RS256 and ES256 only, check issuer, audience, type, time and scopes, and cache JWKS with rate-limited refresh. The identity provider rotates keys in stages. Calls on a user's behalf use token exchange (one audience, minimum scopes, 5 minutes, `act`). Payment and admin APIs also introspect, with a 30-second cache. Browser apps use a BFF. Chapter 37 implements this with Spring Security and Keycloak.

    **Consequences.** ✅ A token is good for one API, for minutes. ✅ Rotations are invisible. ✅ Downstream leaks are contained. ⚠️ Token exchange adds a call per user per downstream service (cached). ⚠️ Introspection makes the identity provider a dependency of payments. ⚠️ Revocation for most APIs still waits up to 15 minutes.

    From [chapter 36](../security/ch36-oauth2-oidc-jwt.md).

<a id="adr-037"></a>

??? decision "ADR-037 · 2025-06-02 · Keycloak as the identity provider; every service a Spring Security resource server with deny-by-default rules …"
    **Decision in full:** Keycloak as the identity provider; every service a Spring Security resource server with deny-by-default rules and method security; Spring's authorization server for local development only

    **Status:** Accepted · **Date:** 2025-06-02

    **Context.** Services accepted any authenticated token for any operation; a migration to Spring's authorization server failed on audiences; a review found token exchange could widen scopes. Verified against the Spring Security 7.1.1 sources: the authorization server addresses access tokens to the client, reuses refresh tokens by default and checks exchanged scopes against the requesting client. Measured with Spring's own encoders: bcrypt 12 costs ~290 ms per login per core; the PBKDF2 v5.8 default is 310,000 iterations.

    **Decision.** Keycloak is the identity provider for customers, restaurants, riders and staff; the realm is versioned and applied by the pipeline, with audience mappers per scope, PKCE for public clients, no password grant, rotating refresh tokens and token exchange for services. Every service is a Spring Security resource server configured by `issuer-uri`, `audiences` and `jws-algorithms`, with a shared claims converter mapping only FoodFleet roles, explicit URL rules ending in `denyAll()`, and method security for object-level decisions. The built-in authorization server runs only in local development, with tokens shaped like Keycloak's. Passwords exist only in Keycloak.

    **Consequences.** ✅ One identity model, one way to configure services. ✅ New endpoints are closed until a rule opens them. ✅ Object-level checks are testable without Spring. ⚠️ Keycloak is a critical dependency (highly available, monitored). ⚠️ Realm changes need the same review as code. ⚠️ Two identity setups (Keycloak, local) must stay aligned; the MockMvc tests use Keycloak-shaped claims to keep them honest.

    From [chapter 37](../security/ch37-spring-security-7.md).

<a id="adr-038"></a>

??? decision "ADR-038 · 2025-06-16 · SPIRE-issued SPIFFE identities for every workload; mutual TLS with a per-route peer policy in every service …"
    **Decision in full:** SPIRE-issued SPIFFE identities for every workload; mutual TLS with a per-route peer policy in every service; client-credentials or exchanged tokens for what the call may do

    **Status:** Accepted · **Date:** 2025-06-16

    **Context.** A test pod captured payments; a logged token was replayable; an expired internal CA caused an outage. Measured with real TLS 1.3: mutual TLS costs a 3.9–4.0 ms handshake and 56–57 µs per request on a kept connection; a standard trust manager accepted certificates that are not SVIDs; resumed TLS sessions kept an expired certificate working (19 of 19) until validity was checked per request; rotation at half-life gave 0 failures, a CA switch without overlap 77–78; Spring's client-credentials interceptor keyed tokens per user inside user requests.

    **Decision.** SPIRE issues one-hour X.509-SVIDs to every workload (Kubernetes PSAT node attestation, namespace and service-account workload attestation, CA keys in KMS, DNS SANs for service names), delivered by spiffe-helper to Spring Boot SSL bundles with reload-on-update. Every service requires mutual TLS and runs `PeerPolicyFilter` before token authentication: SVID rules and validity per request, a deny-by-default peer policy per route. Clients are rebuilt on bundle updates; connection age is capped at 30 minutes. Service-only calls use one client-credentials token per service; calls for a user use token exchange (chapter 36). Payment and refund calls use certificate-bound tokens. Namespaces running Istio use STRICT mTLS and allow-nothing AuthorizationPolicies as well.

    **Consequences.** ✅ Every call names its workload; a stolen token or a pod in the wrong namespace gets 403. ✅ No certificates to distribute or renew by hand. ✅ Revocation by expiry actually expires. ⚠️ SPIRE is critical infrastructure (HA servers, monitored agents, rehearsed CA rollover). ⚠️ Peer policies must be maintained with the call graph (generated from the service catalogue, chapter 72). ⚠️ About 4 ms per new connection: connection pools sized and kept warm.

    From [chapter 38](../security/ch38-service-to-service-security.md).

<a id="adr-039"></a>

??? decision "ADR-039 · 2025-06-30 · OpenBao as FoodFleet's secrets store; Kubernetes auth per service; dynamic, rotating database credentials …"
    **Decision in full:** OpenBao as FoodFleet's secrets store; Kubernetes auth per service; dynamic, rotating database credentials; static secrets via External Secrets as volumes; revocation terminates sessions

    **Status:** Accepted · **Date:** 2025-06-30

    **Context.** A heap dump and a debug session exposed secrets; the shared orders password was three years old because rotating it caused outages. Measured against OpenBao 2.7.1 and PostgreSQL 16: environment variables were inherited and readable through `/proc`; in-place password rotation failed 14–16% of logins while the change propagated, two accounts 0%; renew-only dynamic credentials failed at max_ttl, renew-then-rotate never; password changes, expiry and `NOLOGIN` left open sessions working.

    **Decision.** OpenBao (highly available, auto-unsealed with KMS, audited) holds every secret. Services authenticate with their Kubernetes service accounts; one role and one policy per service. Database access uses dynamic credentials per pod (`default_ttl` 1 h, `max_ttl` 24 h) through a rotating Spring Vault lease and HikariCP's credentials provider; revocation SQL terminates sessions; the engine's root credential is rotated by OpenBao. Third-party API keys live in KV v2, synced by ESO (`refreshInterval` 5 m) into Secrets mounted read-only, rotated with two valid versions. Kubernetes Secrets are encrypted with KMS v2. Heap dumps are classified secret.

    **Consequences.** ✅ No long-lived shared database password; a compromised pod's credentials expire within the hour and can be revoked in seconds. ✅ Every secret read is audited. ⚠️ OpenBao is on the critical path for start-up and renewal: highly available, with alerting. ⚠️ One PostgreSQL role per pod: role churn monitored. ⚠️ Two-version rotation needs provider support or a procedure per secret.

    From [chapter 39](../security/ch39-secrets-management.md).

<a id="adr-040"></a>

??? decision "ADR-040 · 2025-07-14 · Rate limits per client and tenant in Redis (GCRA) with a bounded fallback; typed DTOs and FreeText …"
    **Decision in full:** Rate limits per client and tenant in Redis (GCRA) with a bounded fallback; typed DTOs and FreeText validation; exact-origin CORS on /api, Fetch Metadata and csrf.spa() on /bff; CRS at PL2 tuned for JSON; PostgreSQL row-level security for pooled tenants

    **Status:** Accepted · **Date:** 2025-07-14

    **Context.** One tenant's integration loop starved the others; per-pod limits multiplied; a user-content subdomain read order histories through a suffix-matching CORS rule; a query without a tenant predicate exposed fifty tenants. Measured: a global limit left other tenants 0.2% of their requests, per-tenant limits 100%; local limits let four times the budget through, Redis-shared ones the budget; in Chromium, SameSite did not stop a sibling subdomain, Fetch Metadata and the CSRF token did; the CRS as shipped blocked 15% of legitimate requests at PL1 and 49% at PL2, tuned 0% and 10%; row-level security reduced a forgotten predicate's result from 2,000 rows to 40.

    **Decision.** Rate limits are GCRA in Redis, per client within a tenant and per tenant, enforced after authentication; when Redis is unreachable each instance enforces its share and an alert fires. Request bodies bind to DTOs with Bean Validation; free text goes through `FreeText`; the gateway caps bodies at 256 KB. `/api` accepts bearer tokens only, with CORS for an exact list of HTTPS origins; `/bff` uses a `SameSite=Lax` session cookie with Fetch Metadata and Spring Security's `csrf.spa()`. User content is served from a separate registrable domain. The WAF runs the CRS at PL2 with FoodFleet's API settings and field-level exclusions. Shared tables use row-level security, forced, with a non-owner application role and a transaction-local tenant; tenants that require it get their own database.

    **Consequences.** ✅ One tenant can't take the platform's capacity; a forgotten predicate returns nothing instead of everything. ✅ Forged writes from other sites, and from our own other subdomains, are refused before application code runs. ⚠️ Redis is on the request path (0.1–0.4 ms per decision), so it is highly available and its timeout is short. ⚠️ Four legitimate request types in the corpus still trip PL2; each needs an exclusion with a reason, or a change to the client. ⚠️ Row-level security doesn't apply to superusers, owners without `FORCE`, or `BYPASSRLS` roles: migrations and admin tools are reviewed for that.

    From [chapter 40](../security/ch40-api-security.md).

<a id="adr-041"></a>

??? decision "ADR-041 · 2025-07-28 · SBOMs per artifact with daily rescans; keyless signing and SLSA provenance enforced at admission; compliance …"
    **Decision in full:** SBOMs per artifact with daily rescans; keyless signing and SLSA provenance enforced at admission; compliance rules as OPA policies over declared data classes; crypto-shredding for personal data

    **Status:** Accepted · **Date:** 2025-07-28

    **Context.** Log4Shell took nine days to scope; a compromised GitHub Action leaked a registry token; an erasure request couldn't reach backups or topics; an analytics job copied personal data to Europe. Measured: a release with 0 known findings had 196 two and a half years later; accepting any GitHub identity verified the wrong project's file; with a leaked key, only provenance stopped a laptop rebuild; an OPA policy found 9 violations in 2 of 4 deployments; crypto-shredding left 0 of 9,000 erased fields readable.

    **Decision.** Every build produces a CycloneDX SBOM (in the jar and as an image attestation); CI blocks fixable critical/high findings, with waivers that expire; an inventory rescans every deployed digest daily. Images are signed keylessly in GitHub Actions by digest, with SLSA provenance; Kyverno enforces signer identity, provenance and digests in production namespaces. Actions are pinned by SHA; workflow tokens are per job and least-privilege. Services declare data class, region and retention; OPA policies enforce RBI payment data storage, the PCI namespace, DPDP retention and registry rules in CI and at admission. Personal fields in events, exports and backups are encrypted with per-customer keys.

    **Consequences.** ✅ "Where do we run X?" is a query; audits are evidence exports. ✅ A stolen CI credential can't produce an image that runs. ⚠️ Admission depends on the signature infrastructure: Kyverno's failure policy is Fail, so its availability is monitored, and a break-glass procedure exists. ⚠️ Dependency upgrades are continuous work (Renovate, weekly). ⚠️ Crypto-shredded fields can't be searched directly; a keyed hash index is kept for lookups.

    From [chapter 41](../security/ch41-supply-chain-compliance.md).

### Part 6 · Resilience

<a id="adr-042"></a>

??? decision "ADR-042 · 2025-08-11 · Deadlines on every hop; retries at one layer or within a 10% budget, always with full jitter; explicit …"
    **Decision in full:** Deadlines on every hop; retries at one layer or within a 10% budget, always with full jitter; explicit concurrency limits that reject; queue-age shedding below callers' timeouts; P2C balancing

    **Status:** Accepted · **Date:** 2025-08-11

    **Context.** A five-minute provider slowdown became a forty-minute outage; a gateway restart took minutes to settle; a gray-failing pod doubled checkout p99. Measured on the lab: three-layer retries sent 27 calls per request to a dead dependency, a budget 1.4; ordinary retries kept a service at 0% goodput for 30 s after its trigger ended, shedding at 150 ms recovered it at once; jitter cut a reconnect herd from 42 s to 6 s; P2C halved the users hit by a gray failure at fan-out 10.

    **Decision.** Every incoming request gets a deadline (the caller's, capped by the service's own budget) and every outgoing call carries it. Only the edge retries user-facing calls; services retry dependencies only through `BudgetedRetry` (10%, 3 attempts, full jitter). Every service bounds concurrency explicitly and rejects beyond it (virtual threads included) and sheds work that has waited longer than half its callers' timeout. Client-side balancing is least-outstanding / P2C, with latency outlier ejection in the mesh. Capacity is planned for ≤ 80% at peak with one zone lost.

    **Consequences.** ✅ Overload degrades into fast, partial failure and recovers when the trigger ends. ✅ Retry storms are bounded by design. ⚠️ Shedding produces 503s that must be visible and alerting; users see "try again" during overload instead of spinners. ⚠️ Deadline propagation needs every service and library to cooperate, so it is part of the platform's HTTP client.

    From [chapter 42](../resilience/ch42-failure-modes.md).

<a id="adr-043"></a>

??? decision "ADR-043 · 2025-08-18 · Resilience4j for breakers, bulkheads and rate limits with FoodFleet's measured defaults; Spring Framework 7 …"
    **Decision in full:** Resilience4j for breakers, bulkheads and rate limits with FoodFleet's measured defaults; Spring Framework 7 annotations for simple retries and limits; no library defaults in production

    **Status:** Accepted · **Date:** 2025-08-18

    **Context.** A forty-second restaurant-service failure became a minute of "menu unavailable" (60 s open state); quiet endpoints took about ten seconds to open (100-call window); a misplaced fallback disabled retries; a blocking `@ConcurrencyLimit` queued checkout requests. Measured: default breakers opened in 0.5–10 s depending on traffic and refused every user after recovery; a 5-second time window opened in 2–4 s at both 100/s and 5/s and closed within 5 s; a default breaker never noticed a 3-second dependency; `CompletableFuture` timeouts left all work running; BLOCK queued about 4,000 callers.

    **Decision.** Every synchronous dependency gets a Resilience4j breaker from `ResiliencePolicies` (5 s time window, 20-call minimum, 50% failures or 50% calls over 1 s, open 5 s, 5 trial calls, 4xx ignored) and a bulkhead sized by Little's law with no wait; retries (3 attempts, exponential 100 ms ×2, factor 1.0, cause-chain predicate, refusals ignored) in Spring Boot's aspect order with the fallback on `@Retry`. HTTP read timeouts are the timeout that stops work; `TimeLimiter` only on async calls. Spring Framework 7's `@Retryable` (with multiplier, jitter ≥ delay, timeout) and `@ConcurrencyLimit(REJECT)` are allowed where no breaker is needed. `check_apis.sh` and the experiments run on every library upgrade.

    **Consequences.** ✅ Quiet and busy dependencies are protected the same way, and recover within seconds. ✅ The policy is code with tests, not copied YAML. ⚠️ Two libraries to know; the rule for which one is in the platform guide. ⚠️ A 5-second window reacts to short incidents too: per-dependency overrides need an ADR.

    From [chapter 43](../resilience/ch43-resilience4j-deep-dive.md).

<a id="adr-044"></a>

??? decision "ADR-044 · 2025-08-25 · Deadline budgets per hop; hedges and retries within budgets; AIMD admission control partitioned by priority …"
    **Decision in full:** Deadline budgets per hop; hedges and retries within budgets; AIMD admission control partitioned by priority at every service; brownouts for optional features; bounded buffers everywhere

    **Status:** Accepted · **Date:** 2025-08-25

    **Context.** A lunch spike in browsing starved checkout and left recommendations slow long after it passed; an unbudgeted hedge saturated the search index at dinner; a default Vegas limiter rejected healthy traffic. Measured: at 85% load, unbudgeted hedges and 100 ms retries served 18–68% of users, a 5% hedge budget 99%; through a 2.5× slowdown, no limit served 4% and didn't recover, AIMD with "slow" at half the deadline served 53–54% of a possible 57% and 100% when healthy; partitions served 79–81% of checkouts at 2× overload against 49–50%; a self-limiting dependency showed recommendations on 65–66% of pages in a spike, timeouts alone on 1–2%.

    **Decision.** Each hop's timeout is min(ceiling, remaining deadline − reserve). Idempotent reads may hedge after the dependency's p95 within a 5% budget. Every service puts `AdmissionFilter` first: AIMD (initial 20, min 10, max 200, ×0.9, "slow" at half the callers' deadline), partitioned 90% critical / 10% sheddable, 503 + `Retry-After: 1`. The edge marks priority by route. Optional features sit behind a brownout dimmer; services that others depend on for optional features limit themselves. Stream consumers use bounded buffers and choose blocking or dropping per topic.

    **Consequences.** ✅ Overload costs browsing first and recovers when load drops. ✅ Tail-cutting is safe at any load. ⚠️ Shed requests are visible 503s: dashboards and alerts by priority are part of the change. ⚠️ Priority marking is a product decision; the default for new routes is sheddable until classified.

    From [chapter 44](../resilience/ch44-timeouts-retries-backpressure.md).

<a id="adr-045"></a>

??? decision "ADR-045 · 2025-09-01 · A catalogue of chaos experiments per critical journey, each with an SLO hypothesis, an automatic guard and …"
    **Decision in full:** A catalogue of chaos experiments per critical journey, each with an SLO hypothesis, an automatic guard and the smallest blast radius; weekly in staging, quarterly game days, production only for experiments that hold

    **Status:** Accepted · **Date:** 2025-09-01

    **Context.** A lost read timeout reached staging unnoticed; a first, unguarded experiment in production became an incident; a typo kept a fault running for a weekend. Measured on the lab: three of four common client configurations hung on a trickle; without a read timeout a breaker never saw a black hole (about 2,000 calls stuck, 19 of 20 windows failing); a guard of three windows cut the users hurt from about 1,500 to 150–180; a misspelled `duration` passes schema validation.

    **Decision.** Each critical journey (browse, order, pay, track) owns experiments in a catalogue, run by `Experiment` (Toxiproxy) in CI and as Chaos Mesh workflows in staging: steady state checked first, a `StatusCheck`/guard on the journey's SLO aborting after three bad seconds, `mode: one` until the hypothesis has held three times. Faults include black holes on requests and slow trickles, not only latency and errors. Every manifest is validated strictly in CI and has a duration or deadline. Experiments that once found a weakness run weekly. Game days each quarter; production experiments only for hypotheses that held in staging, in business hours, with on-call present.

    **Consequences.** ✅ Regressions in resilience configuration are found within a week. ✅ Experiments cost seconds of degraded service, not minutes. ⚠️ The SLO probe and the catalogue need owners and upkeep. ⚠️ Staging must carry representative traffic, or the steady state means nothing; a synthetic load generator runs there continuously.

    From [chapter 45](../resilience/ch45-chaos-engineering.md).

<a id="adr-046"></a>

??? decision "ADR-046 · 2025-09-08 · Recovery tiers; zero-loss quorum replication inside the region, asynchronous across regions with fencing …"
    **Decision in full:** Recovery tiers; zero-loss quorum replication inside the region, asynchronous across regions with fencing before any regional promotion; multi-host clients; WAL streamed to the other region; cells by city

    **Status:** Accepted · **Date:** 2025-09-08

    **Context.** A partition between regions followed by an unfenced promotion produced two order histories; clients that followed DNS outlived a fast promotion by a minute; a silently failing WAL archive left a week-old restore point. Measured on the lab: in-zone synchronous commits cost 1.3–1.7 ms instead of 0.9–1.0 ms and lost nothing; a lost region cost 85–278 commits with asynchronous replication, a 2-second partition before it 17,000–22,700; promotion took 0.1 s, clients on a 30 s DNS TTL resumed after 24–33 s; a 60 s archive timeout lost 298–317 orders, streamed WAL none; 8 cells limited a poison tenant to 12.7% of tenants, shuffle shards to 1.7–3.6%.

    **Decision.** Every service declares a tier (`Tier`). Tier 0 (orders, payments): `synchronous_standby_names = 'ANY 1 (zone_b, zone_c)'`, `synchronous_commit = on`; an asynchronous standby in ap-south-2; WAL streamed to object storage there; automatic zone failover (detection 3 × 1 s) with fencing; regional failover only by runbook, fence first. Tier 1: asynchronous cross-region standby. Tier 2: backups only. All clients list every host with `targetServerType=primary` and bounded timeouts. Archive age and replication lag are alerted. Tenants are placed in cells by city; shared fleets use shuffle sharding of 2 of 8. Rehearsals: Tier 0 monthly, Tier 1 quarterly, Tier 2 twice a year, each timed against the tier's RPO/RTO.

    **Consequences.** ✅ Zone failures lose nothing and recover in seconds. ✅ A regional disaster loses at most the cross-region lag, and never forks history. ⚠️ Tier 0 commits pay a zone round trip; losing two zones stops Tier 0 writes until an operator intervenes. ⚠️ Regional failover needs a human decision, so its RTO is minutes, not seconds.

    From [chapter 46](../resilience/ch46-disaster-recovery.md).

### Part 7 · Observability

<a id="adr-047"></a>

??? decision "ADR-047 · 2025-09-15 · All three signals over OTLP to a gateway collector; 100% of spans sent and tail-sampled after RED is derived …"
    **Decision in full:** All three signals over OTLP to a gateway collector; 100% of spans sent and tail-sampled after RED is derived; wide events per request; metrics with a cardinality budget; change events and continuous profiling

    **Status:** Accepted · **Date:** 2025-09-15

    **Context.** A canary's latency regression was visible on dashboards within seconds but couldn't be explained, because per-request data was a field-less log line and traces were head-sampled at 1%. Measured on the lab: metrics showed the canary's p99 (720–732 ms) within one window but not why; 11,000 log lines said nothing; 1% head sampling kept 2–4 of 248–285 slow traces, tail sampling all of them for 18–19% of the bytes; fields on spans or wide events isolated the cause exactly; a restaurant label would multiply series a thousandfold (Prometheus 100 → 670 MB); the agent cost 38–56% CPU on thin services and carried a token from the command line into telemetry.

    **Decision.** Every service exports traces, metrics and logs over OTLP (Spring Boot's OpenTelemetry support; the Java agent where code can't change) to a gateway collector, which removes the command line from resources, derives RED metrics from 100% of spans, and keeps every error, every trace slower than 300 ms and 1% of the rest. Each service writes one wide event per request with its trace ID. Metric labels are limited to service, route, status and pod, and new labels need review. Deploys, flags and config changes are sent as events. JFR runs on every JVM with its default settings.

    **Consequences.** ✅ Any question about requests can be asked of data already collected. ✅ Slow and failed requests are always traceable. ⚠️ The collector tier is critical infrastructure (memory for tail sampling, routing by trace ID). ⚠️ The agent costs CPU and startup time; Spring Boot's instrumentation is measured in chapters 48 and 49.

    From [chapter 47](../observability/ch47-observability-fundamentals.md).

<a id="adr-048"></a>

??? decision "ADR-048 · 2025-09-22 · Percentile histograms with SLO boundaries for every request timer; percentiles only from summed buckets; uri …"
    **Decision in full:** Percentile histograms with SLO boundaries for every request timer; percentiles only from summed buckets; uri tags from templates with a cap that is alerted on; identifiers never as tags; business meters in binders; RED, USE and business recording rules

    **Status:** Accepted · **Date:** 2025-09-22

    **Context.** The checkout p99 panel averaged per-pod percentiles and under-reported a slow pod for a month; one client call created a timer per restaurant. Measured on the lab: averaged client-side p99 137 ms vs a true 322 ms (summed buckets: 317–318 ms); client-side p99 remembered a 10-second spike for 50–60 s and max for 130–140 s; raw paths and concatenated client URIs made 15,954–19,918 meters from 10,000 requests (with histograms up to 707,089 series and a 135 MB scrape), template forms 2–24; a cap stopped it and denied a new endpoint entirely; a histogram costs 84–97 ns per record, client-side percentiles up to 1.5 µs under contention.

    **Decision.** Every service uses the shared metrics starter: `MetricsPolicy`'s filters, Boot's histogram and SLO properties for `http.server.requests` and `http.client.requests` (100, 250, 500 ms, 1 s), `max-uri-tags: 200` with an alert on its warning, long-task timers off for request Observations, the Actuator excluded from request metrics. Percentiles on dashboards and in SLOs come only from `histogram_quantile` over summed buckets in recording rules. Business metrics are `MeterBinder`s with enum tags; business operations are Observations with their key values declared. A CI check scrapes each service after its integration tests and fails above its series budget.

    **Consequences.** ✅ Fleet percentiles and SLO shares that are correct by construction. ✅ Series per service bounded and visible. ⚠️ Histograms add tens of series per timer and tag set; the budget pays for them by forbidding high-cardinality tags. ⚠️ Recording rules are code to maintain.

    From [chapter 48](../observability/ch48-metrics-micrometer.md).

<a id="adr-049"></a>

??? decision "ADR-049 · 2025-09-29 · W3C propagation produced everywhere (B3 consumed); parent-based sampling with the head decision at the edge …"
    **Decision in full:** W3C propagation produced everywhere (B3 consumed); parent-based sampling with the head decision at the edge and tail sampling in the collector; every asynchronous hand-off carries context; edge strips incoming context; baggage allow-listed and never sent to partners

    **Status:** Accepted · **Date:** 2025-09-29

    **Context.** Order traces stopped at the broker and at hand-rolled worker threads; a downstream sampler broke traces; client-supplied baggage reached a payment partner. Measured on the lab: bare and virtual threads and own queues lost the trace on 200 of 200 hand-offs while executors kept it on 200 of 200; a pull consumer continued 0 of 220 orders' traces (220 with manual extraction); non-parent-based samplers dropped payment from 43–47 sampled traces or stored 400–904 fragments, B3-only broke all traces; client `traceparent` forced 100% sampling; client baggage reached a partner on 50 of 50 calls.

    **Decision.** Spring Boot tracing (Micrometer Tracing over OpenTelemetry) in every service, the Java agent where code can't change; `management.tracing.propagation.produce: [w3c]`, consume all; sampling probability 1.0 in services with the collector's tail sampling (ADR-047); the ingress strips `traceparent`, `tracestate` and `baggage` from internet traffic. Request work runs on executors; hand-rolled queues enqueue `Context.wrap`ped tasks; pull consumers extract context from headers; outbox rows store `traceparent`; batch jobs link to the traces they consume. Baggage: tenant, city tier, experiment and priority only, at most 512 bytes, removed before any third-party call. Spring Kafka observations on for templates and listeners. A synthetic order in staging must produce one trace containing every service.

    **Consequences.** ✅ An order is one trace from the app to the kitchen. ✅ No customer data leaves in headers. ⚠️ Every new asynchronous path needs a review of how it carries context. ⚠️ Stripping trace context at the edge means a partner's trace and FoodFleet's can only be joined by a link, not a shared trace.

    From [chapter 49](../observability/ch49-distributed-tracing-otel.md).

<a id="adr-050"></a>

??? decision "ADR-050 · 2025-10-06 · ECS JSON lines on stdout; the trace ID as the correlation field; MDC only through try-with-resources and …"
    **Decision in full:** ECS JSON lines on stdout; the trace ID as the correlation field; MDC only through try-with-resources and wrapped tasks; a masking encoder on every appender; AsyncAppender that never discards silently

    **Status:** Accepted · **Date:** 2025-10-06

    **Context.** On-call couldn't follow a request across threads. Searches by request ID returned other customers' lines. Logs had holes under load, and card and phone numbers appeared in the log store. Measured in the lab:

    - Request IDs in the MDC were missing on every thread hand-off, and wrong on 99 of 200 lines after a leak.
    - The agent's trace ID was right on every executor hop.
    - `AsyncAppender` defaults delivered 29–30% of INFO under a burst.
    - Source-only masking leaked from message text, the MDC and exception messages.
    - A regex masker cost 69–77 µs per line and masked timestamps.

    **Decision.** Every service logs ECS JSON to stdout (`logging.structured.format.console: ecs`) with service name, version, environment and region.

    - **Correlation:** the trace ID from Micrometer Tracing (`traceId`). Services under the agent rename `trace_id` to match. Request-scoped fields travel as baggage copied into the MDC (`correlation.fields`).
    - **MDC:** set only with try-with-resources; Spring executors get `MdcTaskDecorator`; bare threads aren't used for request work.
    - **Masking:** `MaskingEncoder` wraps every encoder, customer fields use `Pii` types, and a CI test logs fake PII through every path.
    - **Async appender:** queue 8192, `discardingThreshold` 0, `neverBlock` false, with shipper lag alerted.
    - **Volume:** JSON trimmed of unused fields, and stack traces capped at depth 10.

    **Consequences:**

    - ✅ One query by trace ID finds a request's lines in every service and thread.
    - ✅ No customer contact or card data in logs.
    - ⚠️ A slow log pipeline slows requests instead of losing lines, so the pipeline needs capacity and an alert.
    - ⚠️ The masking scan costs under 1 µs on an ordinary line, and it can't recognize names or free text.

    From [chapter 50](../observability/ch50-structured-logging.md).

<a id="adr-051"></a>

??? decision "ADR-051 · 2025-10-13 · SLOs per user journey with 30-day error budgets; burn-rate alerts generated from SLO definitions; static …"
    **Decision in full:** SLOs per user journey with 30-day error budgets; burn-rate alerts generated from SLO definitions; static error and latency pages removed; an error budget policy agreed with product

    **Status:** Accepted · **Date:** 2025-10-13

    **Context.** Forty alerts per service, mostly noise. A 0.6% checkout failure went unnoticed for a weekend, and nobody could say how reliable checkout should be. Measured in the lab:

    - A static 1% alert missed slow burns that spent up to 43% of the budget.
    - An alert at the SLO value fired 3 times a week on a healthy service and stayed on for days.
    - Multi-window burn-rate alerts paged within 1–43 minutes for fast incidents and ticketed slow ones at 8% of the budget.
    - Low-traffic services got a page per failure without a minimum-request guard.
    - A latency SLI without a bucket at the objective read 86% of the budget spent instead of 59%.

    **Decision.**

    - **SLOs, each with a 30-day window:**
        - Browse, 99.9% available.
        - Checkout, 99.9% available, and 99% of successful requests within 300 ms.
        - Order tracking, 99.5% available.
        - Each is defined in `CheckoutSlos`-style code, with rules generated by `PrometheusSloRules` and tested in CI.
    - **Alerting:**
        - Page at 14.4× (1h/5m) or 6× (6h/30m); ticket at 3× (1d/2h) or 1× (3d/6h).
        - A 100-request minimum on every SLO.
        - Alertmanager routes pages to on-call and tickets to the owning team, and inhibits tickets while a page fires for the same SLO.
        - Every alert links a runbook.
    - **Metrics:** Spring Boot services add an `slo` bucket at every latency objective.
    - **Removed:** static error-ratio and latency pages. Cause metrics stay on dashboards.
    - **Policy:** half the budget spent means caution (rollback plans, a second reviewer). All of it means a release freeze except fixes, until the window recovers. SLOs are reviewed quarterly with product.

    **Consequences:**

    - ✅ Pages fell from several a week to the incidents that threatened the month.
    - ✅ "How reliable is checkout?" has a number and a trend.
    - ⚠️ Slow burns are tickets, so they rely on the team working its queue.
    - ⚠️ Brief incidents that cost under 1% of the budget page nobody, by design. They show up in the weekly SLO review instead.

    From [chapter 51](../observability/ch51-slo-alerting.md).

<a id="adr-052"></a>

??? decision "ADR-052 · 2025-10-20 · liveness and readiness on Spring Boot's probe groups only; startup probes; graceful shutdown with a preStop …"
    **Decision in full:** liveness and readiness on Spring Boot's probe groups only; startup probes; graceful shutdown with a preStop drain; Actuator on a separate port behind a NetworkPolicy, with access denied by default

    **Status:** Accepted · **Date:** 2025-10-20

    **Context.** A database failover restarted every order-service pod. A release with a slower start never became ready. Deploys produced connection errors. Actuator, with heap dumps, was reachable in-cluster. Measured on the lab cluster:

    - Liveness on the database: 6 restarts in a 60-second outage, half of `/menu` failing, and orders back 14–95 s late.
    - Readiness on the database: 0 ready pods, new connections refused, and orders back 1–41 s late.
    - Neither: no restarts, `/menu` at 100%, and orders back at once.
    - A 40 s start under a 10 s liveness delay never started.
    - Rolling restarts failed 79–96 requests without a drain, and 0 with a `preStop` sleep.
    - A heap dump held the database password.

    **Decision.** Every Spring Boot service:

    - **Liveness** on `/actuator/health/liveness` (10 s period, 3 failures).
    - **Readiness** on `/actuator/health/readiness` (5 s, 3 failures). Shared dependencies are never in a probe group; they appear in `/actuator/health` and SLO alerts.
    - **Startup probe** with a 2-minute budget.
    - **Shutdown:** graceful, with a 20 s shutdown phase, `preStop: sleep: 5` and a 30 s grace period.
    - **Actuator** on 8081, with `access.default: none` and `health`, `info` and `prometheus` read-only. `show-details` when authorized; no `heapdump`, and no `env` values.
    - **NetworkPolicy** admitting 8081 only from the monitoring namespace.
    - **Platform checks:** a policy check in CI rejects Deployments whose liveness probe points at `/actuator/health`.

    **Consequences:**

    - ✅ Dependency outages degrade the endpoints that need the dependency, nothing else.
    - ✅ Deploys are invisible to clients.
    - ⚠️ A pod that can't reach its database stays "ready" and answers 503 quickly. Dashboards and alerts on `/actuator/health` must make that visible.
    - ⚠️ Heap dumps need `kubectl exec` and `jcmd`, under change control.

    From [chapter 52](../observability/ch52-health-checks-actuator.md).

### Part 8 · Deployment & Platform

<a id="adr-053"></a>

??? decision "ADR-053 · 2025-10-27 · multi-stage builds to jlink'd runtimes on distroless, layered with Spring Boot's extract; exec-form …"
    **Decision in full:** multi-stage builds to jlink'd runtimes on distroless, layered with Spring Boot's extract; exec-form entrypoints; MaxRAMPercentage 75 and ExitOnOutOfMemoryError; non-root, read-only; AOT caches for scale-out services; SBOM, scan and signature per image

    **Status:** Accepted · **Date:** 2025-10-27

    **Context.** 470 MB images slowed rollouts and scale-outs. Pods were OOMKilled with no Java error. Deploys took the full grace period per pod. Measured in the lab:

    - 469 MB with the JDK against 88 MB with jlink.
    - 1.6 MB against 23–26 KB re-pushed per one-class change.
    - `-Xmx1g` in 512 MB OOMKilled silently.
    - SerialGC below about 1.8 GB or 2 CPUs.
    - Shell-form entrypoints killed after 10 s with no shutdown.
    - The AOT cache took first response from 1.2–1.3 s to about 0.5 s at 1 CPU.

    **Decision.** One shared Dockerfile template, owned by the platform team:

    - **Build:** Maven in a JDK 25 build stage; Boot's `extract --layers --launcher`; jlink with jdeps' modules plus `jdk.crypto.ec` and `jdk.jcmd`.
    - **Runtime image:** distroless base-debian12:nonroot, with layers ordered dependencies → loader → snapshots → application.
    - **JVM flags:** `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`; G1 set explicitly for services under 2 GB.
    - **Entrypoint:** exec form.
    - **Security:** read-only root filesystem with a `/tmp` emptyDir, all capabilities dropped, non-root.
    - **AOT:** a cache trained with `spring.context.exit=onRefresh` for services that scale out under load.
    - **Supply chain:** images tagged with the commit and deployed by digest; SBOM (syft), scan and cosign signature in CI (chapter 41); weekly rebuilds to pick up base updates.

    **Consequences:**

    - ✅ Images 80–120 MB, kilobyte deploys, no silent OOM kills, sub-second shutdowns.
    - ⚠️ jlink'd runtimes must be rebuilt when a service starts using a new JDK module. A missing module fails at runtime, so the CI smoke test starts the image.
    - ⚠️ No shell in production images: debugging uses ephemeral debug containers (`kubectl debug`).

    From [chapter 53](../deployment/ch53-containerization.md).

<a id="adr-054"></a>

??? decision "ADR-054 · 2025-11-03 · CPU requests without CPU limits for JVM services, ActiveProcessorCount from the request, memory limit equal …"
    **Decision in full:** CPU requests without CPU limits for JVM services, ActiveProcessorCount from the request, memory limit equal to request; configuration as watched mounts or restart-on-change; PDB maxUnavailable 1 with zone spread; connection lifetime caps in every HTTP client

    **Status:** Accepted · **Date:** 2025-11-03

    **Context.** A template-wide 500m CPU limit doubled tail latency, configuration changes reached some pods and not others, scale-outs left new pods idle, and a node drain took a service down. Measured on the lab cluster:

    - At 250m, startup took 3.9–4.0 s instead of 0.7–0.8 s, and p99 was 93–97 ms instead of 10–16 ms.
    - Only watched mounts updated (in 1.4–76 s).
    - Keep-alive clients stayed on the old pod for 50 s.
    - Without a PDB, a drain failed 295–331 requests; with one, 0–1.

    **Decision.**

    - **Resources:** CPU requests from the last 30 days' p95 use, with no CPU limits. `-XX:ActiveProcessorCount` equals the request, rounded up. Memory limit equals the request, with the heap at 75%.
    - **Configuration:** values that change live (discounts, feature flags) go in a watched mounted directory. Everything else triggers a rollout through a ConfigMap hash annotation. `subPath` is forbidden for configuration by admission policy.
    - **Secrets:** mounted from the external secrets operator (chapter 39).
    - **Disruption:** every multi-replica service has a PDB with `maxUnavailable: 1` and zone topology spread.
    - **Clients:** HTTP client pools cap connection lifetime at 30 s; gRPC goes through the mesh.
    - **Edge:** external traffic through Gateway API `HTTPRoute`s.

    **Consequences:**

    - ✅ Tail latency and startup no longer depend on quota periods.
    - ✅ A config change reaches every pod, either live or by rollout.
    - ⚠️ Without CPU limits, a misbehaving pod can use idle CPU on its node. Requests still guarantee everyone's share, and namespace quotas cap totals.
    - ⚠️ Connection lifetime caps add a TLS handshake every 30 s per connection.

    From [chapter 54](../deployment/ch54-kubernetes-for-spring.md).

<a id="adr-055"></a>

??? decision "ADR-055 · 2025-11-10 · one shared Helm chart for services, kustomize overlays for platform components; promotion by digest through …"
    **Decision in full:** one shared Helm chart for services, kustomize overlays for platform components; promotion by digest through an environment repository; server-side apply, no controller-owned fields; config tied to the pod template; rollouts awaited with automatic rollback

    **Status:** Accepted · **Date:** 2025-11-10

    **Context.** Per-environment YAML copies drifted. Deploys reset autoscaled replicas, config changes didn't reach pods, a failed release sat half-applied, and a re-pushed tag put two builds into prod. Measured on the lab cluster:

    - Client-side applies reset an autoscaled 5 replicas to 2; Helm 4's server-side apply refused with a conflict.
    - Config changes rolled pods only with a checksum annotation or a hashed name (5–9 s); otherwise none did.
    - A plain apply of a crashing release reported success and left a crash-looping pod.
    - A moved tag ran two versions behind one Service.

    **Decision.**

    - **Templates:** every service deploys with the shared `foodfleet-service` chart. Teams own only a values file per environment. Platform components (ingress, operators) use kustomize overlays over upstream manifests.
    - **Promotion:** CI builds and signs once, then writes the digest into the dev values. Promotion to staging and prod is a pull request that copies the digest, and the promotion policy requires it to match what the previous environment ran.
    - **Apply:** Helm 4 with server-side apply. `replicas` is omitted when autoscaling. Conflicts fail the pipeline and are resolved by a human.
    - **Config:** the checksum annotation in the chart; hashed generator names in overlays; old ConfigMaps pruned after 7 days.
    - **Rollout:** `--rollback-on-failure`, with a timeout of twice the service's normal rollout time; `maxUnavailable: 0`.

    **Consequences:**

    - ✅ Environments differ only where a values file says so, and reviews read the rendered diff.
    - ✅ What runs in prod is byte-for-byte what staging tested.
    - ⚠️ A shared chart is a platform product: changes need versioning and a migration path for every team.
    - ⚠️ Helm's release record and Git can disagree after a manual rollback. Chapter 58 moves applies to a GitOps controller.

    From [chapter 55](../deployment/ch55-helm-kustomize.md).

<a id="adr-056"></a>

??? decision "ADR-056 · 2025-11-17 · CPU-based HPAs at 60% of measured requests with readiness gated on warm-up; explicit behavior with the …"
    **Decision in full:** CPU-based HPAs at 60% of measured requests with readiness gated on warm-up; explicit behavior with the default five-minute scale-down window; minimums sized for normal traffic and raised on a schedule before lunch and dinner; KEDA for queue consumers

    **Status:** Accepted · **Date:** 2025-11-17

    **Context.** HPAs over-scaled on JVM warm-up, flapped under bursty callers, and changed size when requests were edited. Measured on the lab cluster:

    - Ready-at-once pods drove the HPA to its maximum of 8 for four pods' worth of load; readiness gated on warm-up stopped it at 5.
    - Without a scale-down window, one-minute bursts caused 10–11 scaling changes and 325–432 failed requests in eight minutes; with the default window, 2 and 97–192.
    - Scale-up took 29–34 s to start; getting back to one pod took 331–333 s.

    **Decision.**

    - **Signal:** CPU utilization for request/response services, at 60% of requests set from VPA recommendations and reviewed quarterly. KEDA on consumer lag for Kafka consumers (chapter 25).
    - **Readiness:** every service's readiness fails until warm-up completes, with a startup probe for slow starters.
    - **Behavior:** written out in the shared chart (chapter 55): default scale-up policies, a 300 s scale-down window, down by at most 50% per minute.
    - **Capacity:** minimums carry normal traffic at the target. A scheduled job raises them 15 minutes before lunch and dinner peaks. Node over-provisioning keeps two pods' worth of room per zone.

    **Consequences:**

    - ✅ Pod counts track load without warm-up spikes or flapping.
    - ✅ Peaks start on warm pods, not on a scale-up.
    - ⚠️ After a peak, pods stay for five minutes: paid for in idle capacity.
    - ⚠️ Scheduled minimums need updating when traffic patterns change.

    From [chapter 56](../deployment/ch56-autoscaling.md).

<a id="adr-057"></a>

??? decision "ADR-057 · 2025-11-24 · Istio in ambient mode for all namespaces with strict mTLS and identity-based authorization; waypoint proxies …"
    **Decision in full:** Istio in ambient mode for all namespaces with strict mTLS and identity-based authorization; waypoint proxies (L7) for services that need per-request balancing, retries and outlier detection; client libraries keep timeouts but drop retries

    **Status:** Accepted · **Date:** 2025-11-24

    **Context.** Keep-alive clients ignored new pods, retry logic differed per team, and nothing authenticated callers. Measured in the lab with Envoy sidecars:

    - Per-request balancing split traffic evenly within 10 s of a scale-out; kube-proxy left it all on one pod.
    - mTLS admitted only the allowed identity; an app listening on all interfaces was reachable around it.
    - Retries alone added ~1,500 attempts per 3,000 requests; with outlier detection, 35 and no failures. The default ejection limit never ejected in a three-pod service.
    - Each sidecar used 208–217m of CPU at 500 requests/s, twice what the app used.

    **Decision.**

    - **Mesh:** Istio in ambient mode. ztunnel on every node gives mTLS and L4 authorization without a proxy per pod.
    - **Identity:** strict mTLS in every namespace after a two-week permissive phase. AuthorizationPolicies name allowed service accounts per service.
    - **L7:** waypoint proxies for the gateway's upstreams, order-service and payment-service, with per-request balancing, two retries on 5xx with a budget, and outlier detection (5 consecutive 5xx, 50% maximum ejection).
    - **Clients:** keep timeouts and circuit breakers (chapter 43); remove retries.

    **Consequences:**

    - ✅ Identity-based access control, and encryption, everywhere.
    - ✅ No per-pod proxy CPU for services that don't need L7 features.
    - ⚠️ Waypoints are a shared hop: sized and scaled like any other service.
    - ⚠️ Ambient mode is newer than sidecars; upgrades are rehearsed in staging first.

    From [chapter 57](../deployment/ch57-service-mesh.md).

<a id="adr-058"></a>

??? decision "ADR-058 · 2025-12-01 · CI builds and signs once and opens pull requests to an environment repository; Argo CD with automated sync …"
    **Decision in full:** CI builds and signs once and opens pull requests to an environment repository; Argo CD with automated sync, prune and self-heal applies it; customer-facing services release through Argo Rollouts canaries on Gateway API weights with per-version analysis; blue/green only for releases with a data cut-over

    **Status:** Accepted · **Date:** 2025-12-01

    **Context.** Push deploys left drift and orphaned objects, CI held admin credentials, and a partial failure reached every pod before anyone noticed. Measured on the lab cluster:

    - A pull-based reconciler applied commits within one 10 s interval, reverted scale and env drift, and pruned deleted files; a hand-added variable not in Git survived.
    - A canary stopped a release failing 5% of requests after 7–10 failed requests; the same release as a rolling update failed 944–953 before its two-minute rollback.
    - A healthy one-pod canary was once aborted at 50% on latency.
    - A blue/green switch moved new connections in under a second; keep-alive clients never moved.

    **Decision.**

    - **CI:** build once with Jib, test, scan, sign and attest by digest (chapter 41); open a pull request to the environment repository. No cluster credentials.
    - **CD:** Argo CD per cluster, automated sync with `prune` and `selfHeal`, webhooks from Git. Promotion to staging and prod by approved pull request (chapter 55).
    - **Releases:** Argo Rollouts canaries at 10, 25, 50, 100% through the Gateway API, canary scaled with weight, five-minute analysis per step on per-version error rate (vs stable) and p99 (vs stable, +20%), early abort on 10 errors.
    - **Drift:** admission policies reject changes by humans to production namespaces outside break-glass; break-glass changes are committed within a day.

    **Consequences:**

    - ✅ Every production change is a reviewed commit; rollbacks are reverts.
    - ✅ Bad releases reach a slice of traffic for seconds.
    - ⚠️ A release takes 20+ minutes to reach 100%.
    - ⚠️ Per-version metrics are now a hard dependency of deploying.

    From [chapter 58](../deployment/ch58-cicd-gitops.md).

<a id="adr-059"></a>

??? decision "ADR-059 · 2025-12-08 · EKS with Karpenter as the main platform; EKS Pod Identity for every service with one role per service …"
    **Decision in full:** EKS with Karpenter as the main platform; EKS Pod Identity for every service with one role per service account; standard retry mode by default, adaptive for clients of shared AWS APIs; MSK and Aurora PostgreSQL as managed services; Lambda with SnapStart for small event-driven functions

    **Status:** Accepted · **Date:** 2025-12-08

    **Context.** Access keys leaked, a scale-out throttled itself on the secrets service, and quiet services ran around the clock. Measured in the lab:

    - Federated tokens were refused for the wrong audience, account or signature; projected tokens lived 600 s, default ones a year; a deleted pod's token stayed valid for an offline check until it expired.
    - At twice an API's limit, adaptive retries had 141–161 failed calls against 766–914 for legacy and standard modes; the retry quota limited outage load to one attempt per call after the first seconds.
    - A JVM cold start took about 1.4 s; for quiet services, functions cost a few cents a day against $2.37 for two always-on containers.

    **Decision.**

    - **Platform:** EKS, with Karpenter for nodes and the shared chart (chapter 55) for workloads.
    - **Identity:** EKS Pod Identity for every service; no access keys; Service Control Policies block creating them. Sessions last one hour.
    - **SDKs:** standard retry mode, set explicitly; adaptive for clients of Secrets Manager, SQS and DynamoDB that run in many pods. Secrets read once and refreshed in the background.
    - **Managed services:** MSK for Kafka, Aurora PostgreSQL for databases, both multi-AZ.
    - **Functions:** Lambda with SnapStart for event-driven work under a few requests a second; containers for everything on the request path.

    **Consequences:**

    - ✅ No long-lived credentials; access is reviewed per service.
    - ✅ Quiet workloads cost cents.
    - ⚠️ EKS-specific identity and managed services make a move between clouds work; Kubernetes, Kafka and PostgreSQL keep it possible.
    - ⚠️ Adaptive retries add client-side latency under throttling.

    From [chapter 59](../deployment/ch59-cloud-native-platforms.md).

<a id="adr-060"></a>

??? decision "ADR-060 · 2025-12-15 · every JVM service image carries a JDK 25 AOT cache from a training run through its HTTP API, checked in CI …"
    **Decision in full:** every JVM service image carries a JDK 25 AOT cache from a training run through its HTTP API, checked in CI with AOTMode=on; readiness after a warm-up capped at 10 s; native images for functions, jobs and scale-to-zero services, tested as executables; CRaC not adopted until it runs on our nodes

    **Status:** Accepted · **Date:** 2025-12-15

    **Context.** Lunch scale-outs and function cold starts made startup a cost. Measured on menu-service:

    - At 1 CPU the JVM answered after 1,139–1,246 ms, with an AOT cache after 611–732 ms, a native image after 246–266 ms; at 0.25 CPU 4.3–4.4 s, 2.0–2.1 s and 280–289 ms.
    - A cache trained without the HTTP path saved about half as much; a full warm-up before readiness took 17.5 s at 0.25 CPU.
    - After a minute of load the JVM served 1,411–1,701 requests a second and the native image 994–1,026; the native image used 7.5–7.8 MiB idle against about 40 MiB, and took 205 s and 1.73 GB to build.
    - CRIU couldn't checkpoint in our VMs.

    **Decision.**

    - **JVM services:** the shared Dockerfile runs a `Trainer` (or, for Spring Boot, `-Dspring.context.exit=onRefresh` plus recorded traffic) and writes an AOT cache; a CI smoke test starts the image with `-XX:AOTMode=on`.
    - **Readiness:** after up to 10 s of warm-up through the request path, then ready regardless.
    - **Native images:** for Lambda functions, batch jobs, CLIs and scale-to-zero services; the test suite runs against the executable; `-Xmx` set explicitly.
    - **CRaC and SnapStart:** SnapStart where AWS Lambda offers it, with `Resource` hooks for secrets and connections; CRaC on Kubernetes revisited when CRIU runs on our nodes.

    **Consequences:**

    - ✅ Scale-outs ready in about half the time, with no change to libraries or code.
    - ✅ Functions and jobs start in a quarter-second and fit in small memory.
    - ⚠️ Images grow by the cache (about 40 MB), and the training run adds seconds to builds.
    - ⚠️ Native builds take minutes and gigabytes per commit, and reachability metadata is code to maintain.

    From [chapter 60](../deployment/ch60-native-images-startup.md).

### Part 9 · Testing

<a id="adr-061"></a>

??? decision "ADR-061 · 2025-12-22 · every service has unit tests for its rules, repository tests on its production database engine, contract …"
    **Decision in full:** every service has unit tests for its rules, repository tests on its production database engine, contract tests for its clients, and component tests through its API; H2 is not used to test SQL; suites are tagged and run in stages; incidents are replayed as planted bugs

    **Status:** Accepted · **Date:** 2025-12-22

    **Context.** Three incidents in a quarter passed 2,100 green tests. Measured on order-service with 20 planted bugs:

    - Unit tests caught 7 of 7 logic bugs and none of the 13 others; the component suite caught 12 across every kind; all five suites together, 19.
    - H2 failed 3 of 4 tests on correct code and caught nothing.
    - The one bug that escaped every suite lacked an assertion, not a layer.

    **Decision.**

    - **Every service:** unit tests for rules at their edges; repository tests on PostgreSQL in a container; contract tests for each client; three to ten component tests through the API.
    - **No H2** for SQL tests; Docker is required for the `postgres` and `component` suites.
    - **Stages:** `unit` and `contract` on every commit (under a minute); everything before merge; end-to-end tests (chapter 64) before release.
    - **Incidents:** each postmortem adds a planted bug to the service's mutant list, and the test that catches it.

    **Consequences:**

    - ✅ Each kind of bug has a suite that sees it.
    - ✅ The mix follows each service's bugs, not a diagram.
    - ⚠️ Builds need Docker; chapter 62 keeps the cost down.
    - ⚠️ Mutant lists need maintenance as code changes.

    From [chapter 61](../testing/ch61-test-strategy.md).

<a id="adr-062"></a>

??? decision "ADR-062 · 2025-12-29 · repository and integrated tests run on PostgreSQL in a container started once per test run with …"
    **Decision in full:** repository and integrated tests run on PostgreSQL in a container started once per test run with @ServiceConnection; H2 removed; rollback isolation in data slices, truncation in @SpringBootTest tests; concurrent tests for every once-only rule; test schemas from Flyway migrations; container reuse allowed on developer machines only

    **Status:** Accepted · **Date:** 2025-12-29

    **Context.** A coupon rule rewritten for H2 failed under concurrency in production. Measured:

    - Check-then-insert raised 421–449 errors in 300 concurrent rounds and, without the primary key, redeemed twice in 172–186; `ON CONFLICT` had neither. Every single-threaded test passed.
    - H2 failed 8 of 19 PostgreSQL features the services use.
    - A container per class cost 18.0–22.9 s for 8 classes; one per run 4.0–4.7 s; a reused one 1.5–2.5 s.
    - Rollback isolation cost 0.3 ms per test but leaked when code committed on its own connection; truncation cost 8 ms and didn't.

    **Decision.**

    - **Database tests:** PostgreSQL 16 (the production major version) via Testcontainers, one container per run from a shared base class, wired with `@ServiceConnection`; `fsync=off` as Testcontainers sets it.
    - **H2:** removed from every service.
    - **Isolation:** `@JdbcTest`/`@DataJpaTest` keep their rollback; `@SpringBootTest` tests truncate after each test.
    - **Concurrency:** every once-only rule has a test with concurrent callers asserting one winner and no errors.
    - **Schema:** tests apply the Flyway migrations; `ddl-auto` is off.
    - **Reuse:** allowed locally; CI always starts fresh containers.

    **Consequences:**

    - ✅ Tests run the SQL production runs, races included.
    - ✅ Database suites in seconds per service rather than minutes.
    - ⚠️ Docker is a hard requirement for builds.
    - ⚠️ Concurrent tests are slower and need care to stay deterministic.

    From [chapter 62](../testing/ch62-unit-slice-integration-tests.md).

<a id="adr-063"></a>

??? decision "ADR-063 · 2026-01-05 · every HTTP consumer publishes Pact contracts to a Pact Broker; providers verify them in their builds with …"
    **Decision in full:** every HTTP consumer publishes Pact contracts to a Pact Broker; providers verify them in their builds with realistic provider states; can-i-deploy gates every deployment; contracts use type matchers with formats for meaning; OpenAPI diffs run on provider pull requests for review; E2E reduced to a few journeys

    **Status:** Accepted · **Date:** 2026-01-05

    **Context.** Three releases in a month broke consumers with green builds. Measured on twelve menu-service changes:

    - Consumer contracts caught 7 of 9 breaking changes with no false alarms, each naming the consumer affected.
    - An OpenAPI diff caught 5, with 2 false alarms. It was the only check to catch a new enum value, and it missed a change of units.
    - An E2E suite caught the same 7 as the contracts.
    - Recorded examples as contracts flagged all 5 harmless data changes.

    **Decision.**

    - **Contracts:** Pact for HTTP between FoodFleet services; Spring Cloud Contract stays where teams already use it. Consumers write them from their client tests, using type matchers and formats.
    - **Provider builds:** verify all consumers' contracts for the main branch and for each deployed version, with provider states covering every enum value and long lists.
    - **Gate:** `can-i-deploy --to-environment` before every deployment; deployments recorded in the broker.
    - **Diff:** oasdiff on provider pull requests; ERRs need a reviewer's sign-off.
    - **E2E:** a handful of journeys (chapter 64), not the main safety net.

    **Consequences:**

    - ✅ Breaking changes stop in the provider's build, with the affected consumer named.
    - ✅ Releases no longer wait for a shared environment.
    - ⚠️ A broker to run, and contracts to keep current as consumers change.
    - ⚠️ Contracts only check the data they're verified against; provider states need care.

    From [chapter 63](../testing/ch63-contract-testing.md).

<a id="adr-064"></a>

??? decision "ADR-064 · 2026-01-12 · every service has component tests run on every commit; E2E journeys run in a fresh environment per pull …"
    **Decision in full:** every service has component tests run on every commit; E2E journeys run in a fresh environment per pull request, created by an Argo CD ApplicationSet from the production manifests and deleted on merge; tests create their own data and never sleep; shared staging remains for manual and exploratory testing only

    **Status:** Accepted · **Date:** 2026-01-12

    **Context.** The shared staging E2E suite was red most afternoons, and the team had stopped reading it. Measured on k3s:

    - An environment (PostgreSQL and three services) was answering in 6.8–7.5 s, 14–20 s four at once.
    - Four pipelines on one shared staging passed 1–5 of 12 suite runs; with fresh environments, 12 of 12.
    - Fixed sleeps failed 1–28% of asynchronous checks; polling none of 400, faster on average.
    - Five journeys took 1.9–2.3 s as component tests and 19–28 s as E2E.

    **Decision.**

    - **Component tests:** per service, on every commit, with contract stubs (chapter 63) and PostgreSQL in a container (chapter 62).
    - **Preview environments:** an ApplicationSet pull-request generator deploys `pr-<number>` from the shared chart (chapter 55), with the pull request's image digests.
    - **E2E:** at most a dozen journeys per domain, run in the preview environment before merge.
    - **Tests:** create their own data; Awaitility for asynchronous checks, 10 s or more; flaky tests quarantined within a day.
    - **Staging:** kept for manual testing and demos; no pipeline gates on it.

    **Consequences:**

    - ✅ E2E failures are about the pull request, so people read them again.
    - ✅ Most behaviour is checked in seconds, on every commit.
    - ⚠️ Cluster capacity for preview environments at peak (about one per open pull request).
    - ⚠️ Preview environments need seed data and secrets of their own.

    From [chapter 64](../testing/ch64-component-e2e-testing.md).

<a id="adr-065"></a>

??? decision "ADR-065 · 2026-01-19 · load tests use open (arrival-rate) models with latency from due time and report p99, p99.9 and max; capacity …"
    **Decision in full:** load tests use open (arrival-rate) models with latency from due time and report p99, p99.9 and max; capacity is measured per release by stepped arrival rates; chaos experiments run with SLO-based hypotheses and automatic abort, in staging then on a small share of production; every service has ArchUnit fitness functions with a committed freeze store and a rule against reflection outside configuration

    **Status:** Accepted · **Date:** 2026-01-19

    **Context.** A sale-day slowdown passed a load test, a chaos experiment passed while checkout slowed, and a layer violation hid in a constant. Measured:

    - Against a periodically freezing service, a closed model reported a p99 of 15 ms; the open model 942 ms; the corrected closed model 898–901 ms.
    - A closed model never overloaded the service; an open model at 108% of capacity built a 1.5–1.7 s p99 in 20 s.
    - With a dependency slow or hung, only a timeout with a fallback kept the hypothesis; the abort condition limited bad experiments to 7 s.
    - Five fitness functions caught 8 of 10 planted violations in under a millisecond; an inlined constant and reflection escaped.

    **Decision.**

    - **Load:** k6 with `constant-arrival-rate` (and `ramping-arrival-rate` for capacity) in the release pipeline for critical services.
    - **Chaos:** monthly experiments per critical dependency: hypotheses from SLOs, faults through the mesh (chapter 57), abort on one bad window, an owner present.
    - **Architecture:** ArchUnit layers, cycles and coding rules in every build; freeze stores committed; `Class.forName` allowed only in configuration; `jdeps` checks between modules.

    **Consequences:**

    - ✅ Load-test numbers mean what users will see.
    - ✅ Resilience decisions (fallbacks, timeouts) come from experiments, not assumptions.
    - ⚠️ Open-model tests need generators sized for the rate, and they do overload things.
    - ⚠️ Freeze stores must shrink over time, or they become permanent exceptions.

    From [chapter 65](../testing/ch65-performance-chaos-arch-tests.md).

### Part 10 · Performance & Scalability

<a id="adr-066"></a>

??? decision "ADR-066 · 2026-01-26 · request/response services run Spring MVC on virtual threads on JDK 25, each endpoint behind a concurrency …"
    **Decision in full:** request/response services run Spring MVC on virtual threads on JDK 25, each endpoint behind a concurrency limit sized from Little's law and measured capacity; latency objectives are split into per-hop budgets; fan-out endpoints are designed against their dependencies' p99.9, and hedging is used only for idempotent reads with a p95 delay and a 5% budget; WebFlux stays for streaming and the gateway

    **Status:** Accepted · **Date:** 2026-01-26

    **Context.** The quote endpoint queued behind 200 threads, then fell over without them, and a JDK 21 service slowed down on virtual threads. Measured:

    - Below capacity, all four models served the quote at a p50 of 153–155 ms.
    - 200 platform threads capped it at 1,060–1,100/s; virtual threads and an event loop reached about 1,490/s with 25 OS threads.
    - Past capacity, the unbounded models failed 86–93% of requests and the event loop ran out of memory; with a limit of 300, 1,510–1,588/s were served at p99 ≤ 303 ms.
    - Fan-out to 50 calls made 39–41% of requests slow; hedging cut p99 to 53–86 ms with spare capacity, and made it worse on a saturated backend.
    - On JDK 21, `synchronized` around blocking took 25 s for what JDK 25 did in 0.1 s.

    **Decision.**

    - **Model:** Spring MVC with `spring.threads.virtual.enabled=true` on JDK 25. WebFlux for live tracking (chapter 26) and the gateway (chapter 10) only.
    - **Limits:** every endpoint and outbound dependency has a concurrency limit (chapter 18's bulkheads, chapter 44's adaptive limits at the edge), set from the capacity test in chapter 65.
    - **Budgets:** each journey's objective is divided among hops; independent calls are made in parallel; each hop's timeout comes from the budget (chapter 44).
    - **Tails:** dependencies publish p99 and p99.9; fan-out endpoints hedge idempotent reads after the dependency's p95, within 5% of calls.

    **Consequences:**

    - ✅ Capacity is set by CPUs and dependencies, not by a pool size nobody chose.
    - ✅ Overload turns into fast 503s instead of a heap full of waiting requests.
    - ⚠️ Limits need retuning as latency and hardware change; adaptive limits help.
    - ⚠️ Libraries with native frames still pin on JDK 25; JFR checks stay in the capacity test.

    From [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md).

<a id="adr-067"></a>

??? decision "ADR-067 · 2026-02-02 · every service sets its collector explicitly (G1 by default, generational ZGC for latency-critical services …"
    **Decision in full:** every service sets its collector explicitly (G1 by default, generational ZGC for latency-critical services with at least 2 CPUs, Parallel for batch); heap via MaxRAMPercentage from the measured live set with at least 2.5× headroom; container memory = heap + measured non-heap + 15%, request = limit; ExitOnOutOfMemoryError; continuous JFR with default settings and GC logs in every container

    **Status:** Accepted · **Date:** 2026-02-02

    **Context.** A service got Serial in a 1 GiB pod; another was OOMKilled with a heap at 90%; a third made p99 worse with ZGC on one CPU. Measured:

    - Ergonomics gave a 2-CPU, 1 GiB container Serial and a 256 MB heap.
    - Serial's longest pause was 655–704 ms; G1's 85–112 ms; ZGC's 0.1 ms for about 30% more CPU and 1.3 GB RSS.
    - In 1 GiB, heaps of 50–75% fell back to full collections (p99 0.5–0.7 s); 90% worked, and was OOMKilled once 150 MB of direct buffers was added.
    - ZGC's native structures were 238–266 MB for a 1 GB heap; G1's 54 MB.
    - Continuous JFR cost 2–7% CPU with default settings.

    **Decision.**

    - **Collectors:** G1 by default, explicitly. ZGC for menu and search, in pods of 2+ CPUs. Parallel for batch jobs.
    - **Memory:** each service's load test records its live set and NMT total; the Helm chart computes MaxRAMPercentage and the limit from them.
    - **Diagnostics:** JFR continuous recording and GC logs to an `emptyDir`; `jcmd JFR.dump` in the runbook (chapter 46).
    - **JDK 25:** compact object headers trialled per service.

    **Consequences:**

    - ✅ Pause times are a choice, not an accident of pod size.
    - ✅ OOMKilled becomes rare, and when it happens NMT numbers show what grew.
    - ⚠️ Every service needs a load test to set its memory, and it must be repeated as caches grow.
    - ⚠️ ZGC services cost more CPU and memory per pod.

    From [chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md).

<a id="adr-068"></a>

??? decision "ADR-068 · 2026-02-09 · services keep no session state in memory (Spring Session JDBC or the database); Services use no affinity …"
    **Decision in full:** services keep no session state in memory (Spring Session JDBC or the database); Services use no affinity; calls between services are balanced per request by the mesh and HTTP clients recycle connections; every service documents its shared bottlenecks and autoscales on the signal that limits it; partition keys are chosen for ordering and skew, with consistent hashing (256 virtual nodes) or rendezvous for caches

    **Status:** Accepted · **Date:** 2026-02-09

    **Context.** A scale-out before a cricket final lost carts, left new copies idle and didn't add throughput; a topic keyed by restaurant had one partition carrying a quarter of the traffic. Measured:

    - Carts in memory: 0–2 of 200 intact without affinity; with ClientIP affinity, 100% of requests on one copy and 0 of 200 after it was replaced. In PostgreSQL: 200 of 200.
    - Keep-alive connections through a Service left 1–2 of 4 copies idle, and new copies at 0% after scaling.
    - 4 copies served 121–132 requests a second alone, and 50 behind a 50-per-second dependency.
    - mod n moved 80–91% of keys on resize; consistent hashing with 256 points per node, rendezvous and jump moved the minimum.
    - Keying by restaurant put 19–28% of orders on one of 12 partitions.

    **Decision.**

    - **State:** Spring Session JDBC for web sessions; carts and other user state in the owning service's database.
    - **Balancing:** mesh per-request balancing for all service-to-service traffic; HTTP clients with a 5-minute maximum connection age.
    - **Scaling:** each service's README names its shared limits; KEDA scales on queue length where a dependency limits (chapter 56).
    - **Partitioning:** `orders` keyed by order ID; `kitchen-tickets` by restaurant with the top 20 restaurants salted over 4 sub-keys; 48 partitions from the start.

    **Consequences:**

    - ✅ Rollouts, evictions and scale-in lose nothing; any copy can serve any request.
    - ✅ New copies take traffic as soon as they're ready.
    - ⚠️ Every request that needs session data pays a round trip to the store.
    - ⚠️ Salted keys spread a restaurant's tickets over 4 partitions, so the kitchen display merges them.

    From [chapter 68](../performance-scalability/ch68-horizontal-scaling.md).

<a id="adr-069"></a>

??? decision "ADR-069 · 2026-02-16 · event producers use linger.ms=20, batch.size=131072, lz4, acks=all and idempotence; request/response …"
    **Decision in full:** event producers use linger.ms=20, batch.size=131072, lz4, acks=all and idempotence; request/response producers keep low linger; topics get partitions for the slowest consumer's parallelism plus growth; consumers that write to databases use batch listeners with one transaction per poll, reWriteBatchedInserts and idempotent inserts, committing offsets after the write; slow per-record work uses key-ordered concurrency inside the consumer

    **Status:** Accepted · **Date:** 2026-02-16

    **Context.** Brokers ran hot at 10,000 events a second, the analytics consumer lagged by an hour every dinner, and scaling a consumer past its partitions did nothing. Measured:

    - At 10,000 records a second, linger.ms=0 sent 66,000–69,000 requests (5.8–6.0 s of broker CPU); 20 ms with 128 KB batches sent 920–953 (0.24–0.27 s), for 11 ms more p50.
    - lz4 and snappy compressed order events 4.4–4.5×, zstd 6.75×; gzip ran at an eighth of the speed.
    - One request in flight lost 58–60% of throughput over a 1 ms round trip.
    - 12 consumers on 6 partitions: 6 idle. Key-ordered lanes: 16,600–16,800 records a second from one consumer.
    - PostgreSQL: 1,384–1,554 rows a second with a commit per record; 45,000–59,000 batched.

    **Decision.**

    - **Producers:** `KafkaProfiles.producerThroughput()` for events, `producerLatency()` for commands awaiting replies; both in the platform's Spring Boot starter.
    - **Topics:** partition counts reviewed in design (24 for `orders`), never changed for keyed topics without a migration plan.
    - **Consumers:** batch listeners for sinks; Confluent Parallel Consumer (or a keyed executor) for slow per-record work; lag per partition alerted.
    - **Analytics:** micro-batches into PostgreSQL every poll; heavy aggregates by a scheduled batch job, since an hour's freshness is enough.

    **Consequences:**

    - ✅ Brokers handle requests in the hundreds, not tens of thousands; the same cluster carries several times the traffic.
    - ✅ Consumers catch up after peaks in minutes.
    - ⚠️ Event latency rises by about the linger (11 ms at the median).
    - ⚠️ Batch sinks must handle partial failures in a poll (retry the poll, dead-letter the bad record).

    From [chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md).

<a id="adr-070"></a>

??? decision "ADR-070 · 2026-02-23 · requests are set from VPA recommendations (recommendation mode) and reviewed monthly; latency-sensitive …"
    **Decision in full:** requests are set from VPA recommendations (recommendation mode) and reviewed monthly; latency-sensitive services have no CPU limits and memory limits equal to requests; costs are allocated by team with OpenCost, charging max(request, usage), with idle capacity owned by the platform team; spot-tolerant services run an on-demand base sized to carry their peak alone with spot copies on top, across diversified instance pools; commitments cover the right-sized base; cost per 1,000 orders is reported with SLOs

    **Status:** Accepted · **Date:** 2026-02-23

    **Context.** The bill doubled in a year with orders up a third; teams couldn't see their costs; a cost cut caused a latency incident and a spot move an outage. Measured:

    - Workloads used 3–10% of their requested CPU and 5–21% of their memory; a bursty job needed its full request.
    - VPA's rules took the fleet from 97 to 30 CPUs and 32 to 8–9 nodes: $4,537 to $1,134–1,276 a month at OpenCost's default prices.
    - A CPU limit at the recommendation raised p99 from 5 ms to 78–204 ms; a request alone didn't.
    - By usage, 93% of the bill was idle; by max(request, usage), 34%.
    - Spot reclaimed under load: all-spot served 0–1% for a minute; a 3 + 3 split served 55–61%.

    **Decision.**

    - **Sizing:** VPA recommendations for all workloads; Helm values updated monthly; no CPU limits for services; memory limit = request.
    - **Allocation:** OpenCost with `team` labels; showback per team monthly, with cost per 1,000 orders and requested/used.
    - **Capacity:** Karpenter; on-demand base per service for its peak; spot above it across instance types and zones; batch and CI on spot; commitments for the base after right-sizing.
    - **Resilience on spot:** graceful shutdown, PodDisruptionBudgets, concurrency limits (chapter 66).

    **Consequences:**

    - ✅ The cluster shrank to a third, and teams see their own numbers.
    - ✅ Cost per order is a tracked metric beside latency.
    - ⚠️ Recommendations lag behind growth; a service's request must be raised before a known peak (a sale), not after.
    - ⚠️ Without CPU limits, a noisy neighbour can take spare CPU; requests still guarantee each pod its share.

    From [chapter 70](../performance-scalability/ch70-finops.md).

### Part 11 · Governance & Organization

<a id="adr-071"></a>

??? decision "ADR-071 · 2026-03-02 · every API has an OpenAPI document in its repository, linted in CI with FoodFleet's Spectral ruleset …"
    **Decision in full:** every API has an OpenAPI document in its repository, linted in CI with FoodFleet's Spectral ruleset (spectral:oas recommended plus house rules from Zalando's guidelines), errors blocking new findings and baselines for legacy APIs; an API guild reviews only new APIs and breaking changes; deprecations carry x-sunset and are served with Deprecation, Sunset and Link headers, and an operation is removed only when the gateway's log shows no callers; catalog-info.yaml in every repository, checked in CI

    **Status:** Accepted · **Date:** 2026-03-02

    **Context.** Six API styles across sixty services, a two-week review queue, and an endpoint removal that broke an undeclared consumer. Measured:

    - Spectral's recommended rules caught 10 of 33 planted problems; house rules 16 more; 7 needed a reviewer; no false findings.
    - A ratchet judged a change on its 3 new findings out of 9.
    - Of 4 callers of a deprecated operation, 2 used plain HTTP and saw no warning, and those 2 weren't in the catalog.
    - 15 of 31 catalog entities failed a governance check; only 3 failures broke Backstage's required fields.

    **Decision.**

    - **Style:** the ruleset lives in the platform repository; rule changes by pull request with a cited reason; errors block new findings only.
    - **Review:** the API guild reviews new APIs and breaking changes within two working days, using a checklist for what rules can't check.
    - **Versioning:** `API-Version` header; breaking changes only; old versions supported for 6 months internally and 12 for partners.
    - **Deprecation:** gateway headers from the document; FoodFleet's client libraries warn and export a metric; usage by `X-Client-Id` on the portal; brownouts in the final month.
    - **Catalog:** Backstage, with CI checks for owners, lifecycles, API references, lint status and runbooks.

    **Consequences:**

    - ✅ Most review comments arrive in seconds, from the build.
    - ✅ Sunsets are decided by data, not by a mailing list.
    - ⚠️ The ruleset needs an owner; stale rules get ignored like stale checklists.
    - ⚠️ Every caller must send a client identity, which the gateway enforces.

    From [chapter 71](../governance-org/ch71-api-governance.md).

<a id="adr-072"></a>

??? decision "ADR-072 · 2026-03-09 · services start from a versioned template through the developer portal (Backstage scaffolder), which records …"
    **Decision in full:** services start from a versioned template through the developer portal (Backstage scaffolder), which records the template version; every platform decision ships as a template change plus a conformance check; template releases open three-way-merge update pull requests in every generated service; CI runs the conformance checks and a server-side dry run, with results in the portal's scorecards; hand-written services are migrated onto the template

    **Status:** Accepted · **Date:** 2026-03-09

    **Context.** Seventy services configured seventy ways, decisions that reached only new services, and two weeks for a new team to set up. Measured:

    - From a name to two ready pods answering in 6.5–8.0 s, passing 12 of 12 checks.
    - Fleet conformance: 96% from the current template, 42% hand-written, 8% from the previous template.
    - A three-way merge took four old services from 4 to 40 of 48 checks, with 4 conflicts where teams had changed the same settings.
    - A manifest passing every text check was rejected by the API server.

    **Decision.**

    - **Templates:** Java service, Kafka consumer and batch job, versioned in the platform repository; the version written into each service's catalog entry.
    - **Changes:** each platform decision is a template change plus a check, released with a changelog; update PRs opened automatically.
    - **Visibility:** scorecards per service and team in the portal; only invalid manifests block a build.
    - **Migration:** the platform team pairs with owners to move hand-written services onto the template this quarter.

    **Consequences:**

    - ✅ New decisions reach every service within a release cycle, not only new ones.
    - ✅ Teams see their drift and decide about it.
    - ⚠️ The platform team owns templates as a product: releases, support, deprecations.
    - ⚠️ Conflicted update PRs need team time; unmerged ones are a backlog to watch.

    From [chapter 72](../governance-org/ch72-platform-engineering.md).

<a id="adr-073"></a>

??? decision "ADR-073 · 2026-03-16 · decisions are recorded as ADRs in each repository (adr-tools or MADR format), numbered in the order decided …"
    **Decision in full:** decisions are recorded as ADRs in each repository (adr-tools or MADR format), numbered in the order decided and never edited after acceptance; the log is linted in CI; every decision with a checkable consequence names its fitness functions and each function names its ADR; static functions run in every build, dynamic ones in the pipeline against a recent baseline, best of three, beside the absolute budget; the technology radar is published as data with functions for its Hold ring; a failing function is fixed or accepted by a new decision

    **Status:** Accepted · **Date:** 2026-03-16

    **Context.** Measured:

    - FoodFleet's own log of 72 decisions failed its checks on 12: eleven dated out of order, and one edited after acceptance.
    - Only 7 of the 72 decisions were checked by any code.
    - Five functions traced to ADRs caught one problem in each of six releases out of eight: static ones in under 5 ms, dynamic ones in about 8 s.
    - A fixed latency budget passed a fourfold regression, and single runs raised false alarms on unchanged code.

    **Decision.**

    - **The log:** adr-tools or MADR files, linted for sections, status, numbering, chronology, references and supersession. Changes supersede or amend.
    - **Functions:** a `@Decision("ADR-NNN")`-style reference in each; a list of functions in each ADR; static ones in the build, dynamic ones in the pipeline.
    - **Performance:** relative to a rolling baseline (+50%, best of three) plus the absolute budget from ADR-066.
    - **The radar:** a BYOR CSV reviewed twice a year; Hold blips mapped to packages and checked.
    - **Exceptions:** a failing function is accepted only by a new ADR, shown in the portal.

    **Consequences:**

    - ✅ Decisions keep their order and their evidence; a failure points at its reason.
    - ✅ Regressions are caught at the release that causes them, not at the next incident.
    - ⚠️ Dynamic functions add about 8 s per service per pipeline run, and baselines need upkeep.
    - ⚠️ Changes smaller than about half the median still need microbenchmarks.

    From [chapter 73](../governance-org/ch73-adrs-fitness-functions.md).

<a id="adr-074"></a>

??? decision "ADR-074 · 2026-03-23 · migrations start from measurements of the monolith's import coupling, cycles and history; the monolith first …"
    **Decision in full:** migrations start from measurements of the monolith's import coupling, cycles and history; the monolith first becomes a modular monolith (no cycles between contexts, no platform-to-business dependencies, held by fitness functions); contexts are extracted in order of independent changes gained per remote dependency added, re-planned after each step, and only from outside every cycle; the migration stops when the next extraction buys little; maturity levels are judged by measured gates

    **Status:** Accepted · **Date:** 2026-03-23

    **Context.** The grocery platform, measured on a commerce monolith of its shape (Broadleaf Commerce 7.1.0):

    - 9 business contexts in one cycle; 157 upward edges from layers (217 with the platform); 60 platform-to-business edges.
    - 84.6% of commits within one context, but an independence ceiling of 49% and 15.4% coordinated changes even when fully split.
    - Three extractions freed 31.8% of changes for 340 remote dependencies by value per edge, against 1,083 by most-changed-first.

    **Decision.**

    - **Phase 1 (L1):** break the cycles, starting with the largest upward edges (order → offer, catalog → search, catalog → order), mostly with domain events and interfaces owned by the lower context. Invert the platform's 60 edges. Fitness functions for both.
    - **Phase 2 (L2):** extract admin, content and search through the strangler facade; re-measure after each.
    - **Phase 3:** re-plan from the new measurements; redraw boundaries where coordinated changes stay high.
    - **Stop rule:** no extraction that gains less than 2% of changes independent.

    **Consequences:**

    - ✅ Each step is justified by numbers that can be re-measured.
    - ✅ The monolith gets better even if the migration stops early.
    - ⚠️ Phase 1 delivers no services, only a modular monolith, and needs explaining as progress.
    - ⚠️ Import counts miss configuration and reflection; history catches some (catalog–content).

    From [chapter 74](../governance-org/ch74-maturity-migration-roadmap.md).

<a id="adr-075"></a>

??? decision "ADR-075 · 2026-03-30 · restaurant chains are tenants on pooled, shared tables with tenant_id in every key and a forced row-level …"
    **Decision in full:** restaurant chains are tenants on pooled, shared tables with tenant_id in every key and a forced row-level security policy, accessed through a non-owner role with the tenant set per transaction; enterprise-tier chains get a dedicated database or cell; per-tenant quotas and bulkheads protect neighbours; every tenant has a home region that receives all its personal data, and only aggregates leave; personal fields are crypto-shredded with keys in a KMS, and erasure deletes the key

    **Status:** Accepted · **Date:** 2026-03-30

    **Context.** Measured on PostgreSQL 16:

    - Pooled tables provisioned a tenant in under 1 ms and migrated 1,000 tenants in one statement. Databases per tenant cost 52–105 ms, 7.5 MB and a connection each.
    - RLS stopped a forgotten predicate except for owners (unforced) and superusers. A per-session tenant leaked in 177 of 193 forgotten requests; per transaction, none.
    - One chain's reports raised others' p99 from 4–5 ms to 25–38 ms; a bulkhead held it at 8–11 ms.
    - DELETE left an erased email in WAL and backups; crypto-shredding left only unreadable ciphertext.

    **Decision.**

    - **Isolation:** pool by default; silo for the enterprise tier; schema per tenant not used.
    - **Access:** `TenantScope` in the data layer, the `NULLIF` policy, a fitness function per table, audited bypass roles.
    - **Fairness:** quotas at the gateway, a bulkhead per tenant for reports, statement timeouts only against runaway queries.
    - **Residency:** a tenant registry with home region, tier and cell; routing at the gateway; DR within the jurisdiction.
    - **Erasure:** crypto-shredded personal fields with keys in a KMS; backup retention of 30 days, stated in the privacy notice.

    **Consequences:**

    - ✅ One migration path for most tenants; the expensive model only where it's paid for.
    - ✅ A forgotten predicate or tenant setting fails closed.
    - ⚠️ Encrypted fields can't be searched or indexed by value; lookups use a keyed hash (blind index).
    - ⚠️ The KMS becomes part of every read of personal data: its latency and availability matter.

    From [chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md).

<a id="adr-076"></a>

??? decision "ADR-076 · 2026-04-06 · event-driven, scheduled and bursty workloads run as functions (Spring Cloud Function beans on AWS Lambda …"
    **Decision in full:** event-driven, scheduled and bursty workloads run as functions (Spring Cloud Function beans on AWS Lambda, adapters at the edge), packaged with a JDK AOT cache from a representative training run and run with C1 and Serial GC; steady request/response APIs stay on containers; scale-to-zero containers serve workloads that need one image for both; each workload's platform is chosen from its measured rate, service time and latency objective, and re-checked quarterly against the cost crossover

    **Status:** Accepted · **Date:** 2026-04-06

    **Context.** Measured:

    - The JVM's part of a cold start fell from 246–329 ms to 113–196 ms with an AOT cache, C1 and Serial GC.
    - Over a simulated day at list prices, webhooks and photo bursts cost under a cent a day as functions, against $1.05–2.37 on containers.
    - A light function call cost 8× a call on a busy container; two containers became cheaper above 106 (light) or 28–31 (heavy) requests a second.
    - Sparse traffic with a 1-minute keep-alive had 25% cold starts.

    **Decision.**

    - **Functions:** partner webhooks, menu photo processing, scheduled reports, and event handlers off Kafka bridges, with acknowledge-then-process for any caller with a tight timeout.
    - **Containers:** checkout, orders, tracking and every API above the crossover.
    - **Packaging:** a training run in CI produces the AOT cache; a missing cache fails the build.
    - **Review:** a quarterly report of each function's rate and cost, and each small container service's utilisation.

    **Consequences:**

    - ✅ Idle services stop costing the price of two containers each.
    - ✅ Business code stays portable between functions and containers.
    - ⚠️ Two operational models (functions and Kubernetes) to support.
    - ⚠️ Keep-alive and sandbox start are the platform's; cold-start rates must be watched in production.

    From [chapter 76](../governance-org/ch76-serverless-microservices.md).

<a id="adr-077"></a>

??? decision "ADR-077 · 2026-04-13 · all model calls go through FoodFleet's LLM gateway, which holds provider credentials and applies stream-aware …"
    **Decision in full:** all model calls go through FoodFleet's LLM gateway, which holds provider credentials and applies stream-aware deadlines (first token, idle, total), backoff that honours retry-after, per-feature concurrency limits that keep batch work below interactive work, exact and normalised response caching keyed by model, template version, tenant and user, and per-feature token and cost accounting; semantic caching is used only where a decision and an evaluation on near-miss pairs allow it; tools for assistants are MCP servers on the stateless Streamable HTTP transport (protocol 2026-07-28), catalogued, OAuth-protected, validating headers against bodies, and taking identity only from the token

    **Status:** Accepted · **Date:** 2026-04-13

    **Context.** Measured against a stub provider and on the real gateway, caches and MCP server:

    - Total deadlines held stalled streams for 10–30 s; first-token and idle deadlines retried them and kept p99 at 5.1–5.3 s. Abandoned streams still held provider capacity.
    - A batch job without limits left 19–32 of 60 chats succeeding; one queue delayed chats to a 9.3 s median; a per-feature limit kept them at 2.0 s.
    - Exact and normalised caching reused 97% of 5,000 support questions with no wrong answers; similarity added 1.4 points and, from 0.70 down, wrong dietary answers.
    - A tool taking identity from its arguments served injected requests for another customer's order 30 times out of 30.

    **Decision.**

    - **Gateway:** deadlines 3 s to first token, 1 s idle, 20 s total for interactive features; batch features capped at half the provider limit; fallback to a second provider for 529s.
    - **Caching:** exact and normalised by default; the cache key includes the prompt template version and, for personal answers, tenant and user.
    - **MCP:** one server per domain (orders, menus); OAuth with audience checks; tools wrap existing APIs and their authorization; injection tests in CI.
    - **Cost:** budgets per feature, with alerts at 80%.

    **Consequences:**

    - ✅ One place to change providers, limits and policies.
    - ✅ Assistants get tools with the same security as the APIs behind them.
    - ⚠️ The gateway is on every model call's path: it runs as a highly available service with its own SLO.
    - ⚠️ Per-feature limits make batch work slower by design.

    From [chapter 77](../governance-org/ch77-ai-native-microservices.md).
