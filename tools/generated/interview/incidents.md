### Foundations

??? bug "1 · the 40-service MVP (chapter 1)"
    **Context.** A 12-engineer startup built its first product as 40 microservices "to be ready for scale".

    **Symptoms.** Feature lead time grew from days to weeks. Every feature touched 4–6 services, and local development needed 30 containers. Three engineers spent their time on infrastructure.

    ??? success "Root cause and fix"
        **Root cause.** Boundaries were guessed before the domain was understood. Most services changed together.

        **Fix.** Consolidated into a modular monolith of 7 modules plus 2 genuinely independent services (payments for PCI scope, media processing for scaling). Lead time returned to days.

        From [chapter 1](../foundations/ch01-architecture-styles.md#7-anti-patterns-pitfalls).

??? bug "2 · the distributed monolith release train (chapter 1)"
    **Context.** 14 services shared one database schema and a common "domain model" JAR.

    **Symptoms.** Every change to a shared entity required rebuilding and redeploying all 14 services in a fixed order every two weeks. One bad deploy took down nine services.

    ??? success "Root cause and fix"
        **Root cause.** Data coupling (shared tables) and code coupling (shared domain library). The services were physically split and logically one.

        **Fix.** Assigned table ownership, replaced shared reads with APIs and events, shrank the shared library to technical utilities only, and added contract tests so services could deploy independently.

        From [chapter 1](../foundations/ch01-architecture-styles.md#7-anti-patterns-pitfalls).

??? bug "3 · the ESB that knew too much (chapter 1)"
    **Context.** A bank's integration bus contained pricing rules, routing logic and data transformations for 60 applications.

    **Symptoms.** Any product change needed a ticket to the central integration team. Lead time was about six weeks.

    ??? success "Root cause and fix"
        **Root cause.** Smart pipes: business logic lived in the middleware.

        **Fix.** Strangler-fig migration ([ch 16](../architecture-patterns/ch16-strangler-fig-migration.md)). New capabilities exposed APIs owned by product teams, and the bus was reduced to transport and protocol bridging.

        From [chapter 1](../foundations/ch01-architecture-styles.md#7-anti-patterns-pitfalls).

??? bug "4 · a database connection held across a network call (chapter 1)"
    **Context.** After extracting a service, the team kept `@Transactional` on the method that now made a remote call.

    **Symptoms.** At dinner peak the downstream service slowed to 2 s. The Hikari pool (20 connections) was exhausted, and *unrelated* endpoints that needed the database started failing.

    ??? success "Root cause and fix"
        **Root cause.** Each request held a connection while waiting on the network.

        **Fix.** Make remote calls before or after the transaction (exactly what `OrderManagement` does), add timeouts, and size the pool deliberately ([ct: connection pooling](../components-tools/connection-pooling.md)).

        From [chapter 1](../foundations/ch01-architecture-styles.md#7-anti-patterns-pitfalls).

??? bug "5 · the retry storm (chapter 2)"
    **Context.** Checkout → order → inventory → database, with each layer configured to "retry 3 times on any error".

    **Symptoms.** A 30-second database failover turned into a 20-minute outage. Database CPU stayed at 100% after it recovered.

    ??? success "Root cause and fix"
        **Root cause.** Retry amplification: one click became up to 4 × 4 × 4 = 64 database calls, and none had jitter, so they arrived in synchronized waves.

        **Fix.** Retry at the innermost layer only, on transient errors only, with exponential backoff and jitter. Add circuit breakers and a retry budget.

        From [chapter 2](../foundations/ch02-characteristics-principles.md#7-anti-patterns-pitfalls).

??? bug "6 · the strict reader (chapter 2)"
    **Context.** A partner integration deserialized responses with "fail on unknown properties" enabled.

    **Symptoms.** The provider added an optional `deliveryInstructions` field in a minor release. The partner's order sync failed for 6 hours.

    ??? success "Root cause and fix"
        **Root cause.** The consumer treated any change as an error.

        **Fix.** Tolerant readers by default, contract tests that include "provider adds a field", and a schema compatibility check in the provider's pipeline.

        From [chapter 2](../foundations/ch02-characteristics-principles.md#7-anti-patterns-pitfalls).

??? bug "7 · the optional dependency that took down checkout (chapter 2)"
    **Context.** The cart page called recommendation-service synchronously, with no timeout, to render "frequently bought together".

    **Symptoms.** A slow recommendation-service deploy made cart and checkout time out across the platform.

    ??? success "Root cause and fix"
        **Root cause.** An enrichment sat on the critical path with no timeout and no degradation mode.

        **Fix.** A 100 ms timeout, omit-on-failure, and later an asynchronous prefetch into a cache.

        From [chapter 2](../foundations/ch02-characteristics-principles.md#7-anti-patterns-pitfalls).

??? bug "8 · the shared domain model JAR (chapter 2)"
    **Context.** Twenty services depended on `foodfleet-common-model`, which contained `Order`, `Restaurant` and `Customer`.

    **Symptoms.** Adding one enum value to `OrderStatus` required releasing all twenty services. Services pinned to old versions failed to deserialize events containing the new value.

    ??? success "Root cause and fix"
        **Root cause.** Domain coupling through a shared library: a distributed monolith in disguise.

        **Fix.** Each service owns its model. Events are documented contracts (AsyncAPI, schema registry). Shared libraries are limited to technical concerns.

        From [chapter 2](../foundations/ch02-characteristics-principles.md#7-anti-patterns-pitfalls).

??? bug "9 · the clock that ran ahead (chapter 3)"
    **Context.** Order-status updates from several service instances were merged last-write-wins using `System.currentTimeMillis()`.

    **Symptoms.** For about 40 minutes some orders "went back in time" (DELIVERED → OUT_FOR_DELIVERY) and riders were re-dispatched.

    ??? success "Root cause and fix"
        **Root cause.** One VM's clock was 2 minutes ahead after a host migration, so its stale writes won every comparison.

        **Fix.** Status transitions became conditional updates on a per-order version (`WHERE version = ?`), and clock-offset alerts were added.

        From [chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "10 · \'Kafka is exactly-once\' (chapter 3)"
    **Context.** A consumer read payment events with Kafka transactions enabled and called the payment gateway's capture API.

    **Symptoms.** During a consumer-group rebalance, a few hundred customers were charged twice.

    ??? success "Root cause and fix"
        **Root cause.** The gateway call was outside Kafka's transaction. Offsets weren't committed before the rebalance, so the events were reprocessed.

        **Fix.** The gateway capture uses an idempotency key derived from the payment id, plus an inbox table checked before calling.

        From [chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "11 · timeout treated as failure (chapter 3)"
    **Context.** The support tool retried refunds after a 5-second timeout.

    **Symptoms.** Customers received two refunds whenever the gateway was slow.

    ??? success "Root cause and fix"
        **Root cause.** A timeout means **unknown**, not failed.

        **Fix.** Idempotency keys on refund requests, and a reconciliation job comparing the gateway's ledger with FoodFleet's.

        From [chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "12 · split brain without fencing (chapter 3)"
    **Context.** A scheduler used a lease in Redis to ensure only one instance ran payouts.

    **Symptoms.** After a long GC pause, two instances believed they held the lease and both paid restaurants.

    ??? success "Root cause and fix"
        **Root cause.** The paused instance's lease had expired, and it kept acting on it.

        **Fix.** Fencing tokens: every write carries the lease's monotonically increasing token, and storage rejects older tokens ([distributed locks](../components-tools/distributed-locks.md)).

        From [chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "13 · the production-only build (chapter 4)"
    **Context.** Each environment had its own Maven profile that baked URLs and fees into the jar at build time.

    **Symptoms.** A release passed staging, then failed in production with a serialization error.

    ??? success "Root cause and fix"
        **Root cause.** The production build pulled a different transitive dependency version, so the artifact in production had never been tested.

        **Fix.** Build once, promote the same image digest, and inject config at release time (factors III and V).

        From [chapter 4](../foundations/ch04-twelve-factor.md#7-anti-patterns-pitfalls).

??? bug "14 · the disk that filled up (chapter 4)"
    **Context.** A service logged to a rolling file inside the container *and* to stdout.

    **Symptoms.** After a busy IPL weekend, pods were evicted for disk pressure, taking healthy neighbours on the node with them.

    ??? success "Root cause and fix"
        **Root cause.** Container filesystems aren't log storage, and rotation was misconfigured.

        **Fix.** stdout only (factor XI), with the node agent handling shipping and retention.

        From [chapter 4](../foundations/ch04-twelve-factor.md#7-anti-patterns-pitfalls).

??? bug "15 · H2 in tests, PostgreSQL in production (chapter 4)"
    **Context.** Integration tests used H2 in PostgreSQL compatibility mode.

    **Symptoms.** An `on conflict … do update` upsert and a partial index behaved differently in production. The import silently skipped rows.

    ??? success "Root cause and fix"
        **Root cause.** Dev/prod disparity in the backing service.

        **Fix.** Testcontainers with the production PostgreSQL major version (factor X), exactly as in `ZoneServiceContainerTest`.

        From [chapter 4](../foundations/ch04-twelve-factor.md#7-anti-patterns-pitfalls).

??? bug "16 · 502s on every deploy (chapter 4)"
    **Context.** The app ignored SIGTERM's grace period and had no `preStop` delay.

    **Symptoms.** Every rolling deploy produced a burst of 502s at the ingress.

    ??? success "Root cause and fix"
        **Root cause.** Pods stopped accepting connections before load balancers removed them, and in-flight requests were cut.

        **Fix.** `server.shutdown=graceful`, a phase timeout, a short `preStop` sleep and a grace period longer than both (factor IX).

        From [chapter 4](../foundations/ch04-twelve-factor.md#7-anti-patterns-pitfalls).

??? bug "17 · the god aggregate (chapter 5)"
    **Context.** `Restaurant` was modelled as an aggregate containing its menu *and* its active orders, so it could enforce "max 40 active orders".

    **Symptoms.** At 8 pm on a Friday, popular restaurants saw constant optimistic-locking failures. Checkout latency p99 hit 9 s.

    ??? success "Root cause and fix"
        **Root cause.** Every order placement updated the same Restaurant aggregate, so a whole restaurant's orders were serialized through one row.

        **Fix.** Order became its own aggregate. The capacity rule moved to Kitchen as a counter with eventual consistency, and orders over capacity are rejected by Kitchen and compensated.

        From [chapter 5](../foundations/ch05-domain-driven-design.md#7-anti-patterns-pitfalls).

??? bug "18 · the shared Order library (chapter 5)"
    **Context.** `foodfleet-domain-model.jar` contained one `Order` used by ordering, kitchen, delivery and finance.

    **Symptoms.** Finance added GST fields, kitchen added prep timers, and the class reached 70 fields. Every release needed four teams' approval.

    ??? success "Root cause and fix"
        **Root cause.** Four bounded contexts were forced into one model.

        **Fix.** Each context owns its own model: `Order` (Ordering), `KitchenTicket` (Kitchen), `DeliveryJob` (Delivery), `Invoice` (Finance), linked by `OrderId` and events.

        From [chapter 5](../foundations/ch05-domain-driven-design.md#7-anti-patterns-pitfalls).

??? bug "19 · accidental conformist (chapter 5)"
    **Context.** Ordering used Catalog's REST DTOs directly in its domain code.

    **Symptoms.** Catalog renamed `price` to `priceInr` and changed it to a string. Ordering's checkout broke in production.

    ??? success "Root cause and fix"
        **Root cause.** No translation boundary: Catalog's model had leaked everywhere.

        **Fix.** An ACL (exactly the `CatalogTranslator` pattern), contract tests, and an ArchUnit rule that only the ACL may import Catalog types.

        From [chapter 5](../foundations/ch05-domain-driven-design.md#7-anti-patterns-pitfalls).

??? bug "20 · the entity services (chapter 6)"
    **Context.** FoodFleet's 2023 split created one service per table (decomposition A).

    **Symptoms.** Checkout p99 rose from 280 ms to 1.9 s; monthly checkout availability fell below 99%; a "note to restaurant" field needed changes in `order-service`, `order-line-service` and `kitchen-ticket-service`, released in that order.

    ??? success "Root cause and fix"
        **Root cause.** Boundaries followed nouns in the schema, not capabilities. Order and order lines share an invariant ("at most 15 dishes, total ≥ minimum order") that now crossed a network.

        **Fix.** Merged back into capability services (B), behind a modular monolith step for ordering. Sync hops per checkout went from 16 to 9, and the order invariant became one aggregate again (chapter 5).

        From [chapter 6](../foundations/ch06-service-decomposition.md#7-anti-patterns-pitfalls).

??? bug "21 · the shared notification templates (chapter 6)"
    **Context.** `notification-service` owned every message template: order confirmations, rider alerts, refund emails, marketing.

    **Symptoms.** Every team's feature queued behind the notification team's backlog. A refund-copy change waited three weeks.

    ??? success "Root cause and fix"
        **Root cause.** A **generic technical capability** ("send messages") was treated as a business capability, and it absorbed business logic from every other domain.

        **Fix.** Notification became a platform service that sends; each domain owns its templates and decides *what* to say. The service's backlog dropped by 70%.

        From [chapter 6](../foundations/ch06-service-decomposition.md#7-anti-patterns-pitfalls).

??? bug "22 · decomposing the unknown (chapter 6)"
    **Context.** A new "grocery delivery" line was launched with eight microservices designed upfront.

    **Symptoms.** Within six months three services had been merged and two split differently; each change needed data migrations.

    ??? success "Root cause and fix"
        **Root cause.** Boundaries were drawn before the domain was understood. The Event Storming board still had a dozen red hotspots.

        **Fix.** The next new line (pharmacy) started as **one modular service**, with modules extracted only once the boundaries had stopped moving. This matches the "monolith first" advice of [chapter 1](../foundations/ch01-architecture-styles.md).

        From [chapter 6](../foundations/ch06-service-decomposition.md#7-anti-patterns-pitfalls).

??? bug "23 · the DBA queue (chapter 7)"
    **Context.** In 2023 all schema changes went through a three-person DBA team.

    **Symptoms.** Median wait for a migration review was 6 working days; teams batched changes to "save tickets", making each migration riskier. A batched migration locked the `orders` table for 11 minutes during dinner peak.

    ??? success "Root cause and fix"
        **Root cause.** Data ownership was organised as a separate function, so every feature had a mandatory handoff, and the shared database grew one schema for everyone.

        **Fix.** Database per service ([chapter 12](../architecture-patterns/ch12-database-per-service.md)) with each team owning its migrations; the DBAs became an **enabling** team (query tuning, migration safety, Flyway practices) and part of the platform (managed PostgreSQL).

        From [chapter 7](../foundations/ch07-conways-law-team-topologies.md#7-anti-patterns-pitfalls).

??? bug "24 · the platform that collaborated with everyone (chapter 7)"
    **Context.** After the reorganisation, the new Platform team said yes to every request and paired with each team on its deployment pipeline.

    **Symptoms.** Platform had nine active collaborations. Lead time for platform changes reached five weeks, and stream-aligned teams built their own workarounds.

    ??? success "Root cause and fix"
        **Root cause.** A platform delivered through collaboration doesn't scale: every team's extraneous load moved to the platform team.

        **Fix.** Golden-path templates and self-service onboarding (X-as-a-Service), a public roadmap, and collaboration only for discovering new platform features, time-boxed to one quarter.

        From [chapter 7](../foundations/ch07-conways-law-team-topologies.md#7-anti-patterns-pitfalls).

??? bug "25 · the reorg that changed only the org chart (chapter 7)"
    **Context.** A sister business renamed its layer teams to "squads" and "tribes" without changing what they owned.

    **Symptoms.** Same handoffs, same shared database, same release train, plus a new layer of coordination meetings.

    ??? success "Root cause and fix"
        **Root cause.** The inverse Conway maneuver applied to the org chart but not to the architecture or the data. Conway's Law pulled communication back to the old shape.

        **Fix.** Move ownership of services, data and on-call with the teams, one capability at a time ([chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md)).

        From [chapter 7](../foundations/ch07-conways-law-team-topologies.md#7-anti-patterns-pitfalls).

??? bug "26 · the starter that only existed in tests (chapter 8)"
    **Context.** A team migrated menu-service to Boot 4 and moved to modular starters.

    **Symptoms.** All tests passed. The first canary pod crashed at startup: no `RestClient.Builder` bean for the HTTP service client.

    ??? success "Root cause and fix"
        **Root cause.** The RestClient module came transitively through the webmvc **test** starter, so it was on the test classpath only.

        **Fix.** `spring-boot-starter-restclient` in `main` scope, and a CI step that starts the packaged jar (`java -jar … --spring.context.exit=onRefresh`) after the tests.

        From [chapter 8](../foundations/ch08-spring-boot-4-essentials.md#7-anti-patterns-pitfalls).

??? bug "27 · `null` stopped meaning zero (chapter 8)"
    **Context.** After moving to Jackson 3, a partner integration started receiving 400 errors.

    **Symptoms.** The partner's POS system sent `"packagingFeePaise": null` for restaurants without a packaging fee. Jackson 2 had quietly turned it into 0.

    ??? success "Root cause and fix"
        **Root cause.** Jackson 3 enables `FAIL_ON_NULL_FOR_PRIMITIVES`.

        **Fix.** The field became `Integer` with an explicit default in the anti-corruption layer (chapter 5): the *business* decision "no fee means zero" now lives in code, not in a library default. A contract test with the partner's real payloads was added.

        From [chapter 8](../foundations/ch08-spring-boot-4-essentials.md#7-anti-patterns-pitfalls).

??? bug "28 · virtual threads removed the accidental limit (chapter 8)"
    **Context.** order-service switched on virtual threads during a sale.

    **Symptoms.** Throughput rose, then PostgreSQL connection acquisition timeouts appeared, and the payment provider started rate limiting.

    ??? success "Root cause and fix"
        **Root cause.** The 200-thread Tomcat pool had been acting as an unplanned concurrency limit. Without it, thousands of requests reached the 30-connection pool and the provider at once.

        **Fix.** Explicit limits: `@ConcurrencyLimit` on the payment client, a bounded wait for DB connections with fast 503s, and a rate limiter at the gateway ([chapter 18](../architecture-patterns/ch18-resilience-patterns.md)).

        From [chapter 8](../foundations/ch08-spring-boot-4-essentials.md#7-anti-patterns-pitfalls).


### Architecture & Patterns

??? bug "29 · the provider SDK everywhere (chapter 9)"
    **Context.** Before 2025, payment code passed the provider SDK's response objects through every layer.

    **Symptoms.** A provider SDK upgrade renamed a status from `captured` to `CAPTURED`. Refund eligibility checks silently returned false, and about 300 customers were told their refunds were "not possible" over a weekend.

    ??? success "Root cause and fix"
        **Root cause.** No boundary: provider vocabulary had reached the business rules.

        **Fix.** The `PaymentGateway` port in FoodFleet's vocabulary, a translator with tests per provider status, and an ArchUnit rule that provider types stay in `adapter.out.gateway`.

        From [chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#7-anti-patterns-pitfalls).

??? bug "30 · a transaction around a phone call (chapter 9)"
    **Context.** An earlier version put `@Transactional` on the whole authorize use case.

    **Symptoms.** During a provider slowdown (5 s per authorization), the database connection pool (20 connections) was exhausted within seconds; even `GET /payments/{id}` timed out.

    ??? success "Root cause and fix"
        **Root cause.** Each request held a DB connection and transaction open while waiting for the provider.

        **Fix.** Short transactions per repository call; record PENDING first, call the provider, record the outcome; reconciliation for the gap.

        From [chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#7-anti-patterns-pitfalls).

??? bug "31 · the port that mirrored the database (chapter 9)"
    **Context.** A team's repository port grew methods like `findByStatusAndCreatedAtBetweenOrderByAmountDesc` and `executeNativeQuery(String sql)`.

    **Symptoms.** The in-memory fake couldn't keep up; tests switched to mocks; mocks drifted from the real behaviour; a production bug passed all tests.

    ??? success "Root cause and fix"
        **Root cause.** The port described the *adapter's* capabilities, not the *core's* needs.

        **Fix.** Ports in use-case language (`findActiveByOrderId`), queries for reporting moved to a separate read model ([chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md)), and contract tests for every port.

        From [chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#7-anti-patterns-pitfalls).

??? bug "32 · duplicate orders from a helpful retry (chapter 10)"
    **Context.** A gateway-wide retry policy (3 attempts on 5xx and timeouts) was applied to all routes.

    **Symptoms.** During a database failover, ordering-service was slow; the gateway retried `POST /api/orders` after timeouts. About 200 customers were charged for two or three identical orders.

    ??? success "Root cause and fix"
        **Root cause.** The first attempt had succeeded slowly; the retries created new orders. POST is not idempotent without a key.

        **Fix.** Retries only on `GET` and only where safe; `Idempotency-Key` required on order and payment creation; services deduplicate (chapter 3).

        From [chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#7-anti-patterns-pitfalls).

??? bug "33 · the gateway that knew about coupons (chapter 10)"
    **Context.** To launch a campaign quickly, coupon validation was added as a gateway filter ("it sees every order anyway").

    **Symptoms.** Six months later the gateway had 40 business filters, a release train every two weeks, and every team needed the platform team's approval to ship.

    ??? success "Root cause and fix"
        **Root cause.** Business logic in shared infrastructure.

        **Fix.** Coupon logic moved back to promotions-service; the gateway's filter list is reviewed quarterly against a rule: "no filter may know a domain concept".

        From [chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#7-anti-patterns-pitfalls).

??? bug "34 · one BFF for everyone (chapter 10)"
    **Context.** The "mobile BFF" also served the partner portal and the rider app.

    **Symptoms.** Endpoints accumulated flags (`?client=partner&compact=true`); the three app teams queued for one backlog; a change for riders broke the partner portal.

    ??? success "Root cause and fix"
        **Root cause.** A BFF shared by several clients is a general-purpose API with extra steps.

        **Fix.** One BFF per client experience, each owned by its client team.

        From [chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#7-anti-patterns-pitfalls).

??? bug "35 · four minutes of errors after every crash (chapter 11)"
    **Context.** In 2022, FoodFleet's services on VMs used Eureka with default settings.

    **Symptoms.** Every instance crash produced about four minutes of 5xx errors for its callers; on-call engineers learned to restart callers to "refresh" them.

    ??? success "Root cause and fix"
        **Root cause.** The stacked caches of section 3: lease (up to 180 s), eviction sweep, server response cache, client fetch and load-balancer cache.

        **Fix.** Tuned timers, health propagation, timeouts of 1 s and a retry on another instance for reads. The move to Kubernetes later made most of this unnecessary.

        From [chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#7-anti-patterns-pitfalls).

??? bug "36 · one pod doing all the gRPC work (chapter 11)"
    **Context.** dispatch-service called the dispatch optimizer over gRPC through a ClusterIP Service and scaled the optimizer from 3 to 12 pods for a cricket-final evening.

    **Symptoms.** Latency didn't improve; three pods ran at 100% CPU while nine were idle.

    ??? success "Root cause and fix"
        **Root cause.** HTTP/2 connections are long-lived; kube-proxy balanced the initial connections and nothing else.

        **Fix.** A headless Service with gRPC client-side round robin and periodic re-resolution; later a mesh.

        From [chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#7-anti-patterns-pitfalls).

??? bug "37 · the readiness probe that checked everything (chapter 11)"
    **Context.** order-service's readiness probe called payment-service, kitchen-service and the database.

    **Symptoms.** A short payment-service outage made **every** order-service pod unready; Kubernetes removed them all from the Service, and the app showed "service unavailable" for browsing orders, which didn't need payments at all.

    ??? success "Root cause and fix"
        **Root cause.** Readiness depended on non-essential downstream services, so one failure cascaded into total removal.

        **Fix.** Readiness checks only what the instance needs for most of its traffic (its own database pool); downstream failures are handled per request with timeouts and fallbacks.

        From [chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#7-anti-patterns-pitfalls).

??? bug "38 · the column rename that broke four services (chapter 12)"
    **Context.** ordering-service renamed `orders.total` to `orders.total_paise` in a routine migration.

    **Symptoms.** kitchen, delivery, payment and the reporting job failed at once in production; three of them had no test against the real schema.

    ??? success "Root cause and fix"
        **Root cause.** The table was a shared, unversioned contract that its owner didn't know it had.

        **Fix.** The ownership audit, one database user per service, and an end to foreign reads of `orders` within two quarters.

        From [chapter 12](../architecture-patterns/ch12-database-per-service.md#7-anti-patterns-pitfalls).

??? bug "39 · the report that locked checkout (chapter 12)"
    **Context.** A weekly finance report ran a large aggregation over `orders`, `payment` and `refund` on the primary database at 7:30 pm.

    **Symptoms.** Checkout p99 latency rose from 300 ms to 12 s for 20 minutes; connection pools were exhausted.

    ??? success "Root cause and fix"
        **Root cause.** Analytical load on the operational database, during peak, with locks and I/O contention.

        **Fix.** CDC into a warehouse; reporting users have no access to operational databases ([chapter 34](../data/ch34-reporting-analytics.md)).

        From [chapter 12](../architecture-patterns/ch12-database-per-service.md#7-anti-patterns-pitfalls).

??? bug "40 · the N+1 composer (chapter 12)"
    **Context.** The first order-history endpoint looped over orders and called catalog and delivery per row.

    **Symptoms.** Customers with long histories saw 3–4 s load times; catalog-service's request rate tripled during dinner; one slow catalog instance made the whole page fail.

    ??? success "Root cause and fix"
        **Root cause.** 1 + 2N sequential calls with no budget and no partial results.

        **Fix.** Batch endpoints on catalog and delivery, parallel calls, a 500 ms budget, partial results (this chapter's composer).

        From [chapter 12](../architecture-patterns/ch12-database-per-service.md#7-anti-patterns-pitfalls).

??? bug "41 · CRUD events (chapter 13)"
    **Context.** A team's first event-sourced service emitted `WalletUpdated { balance: 58500 }` on every change.

    **Symptoms.** The history said *that* the balance changed, never *why*; the dispute problem was unsolved, and projections had to diff states to guess what happened.

    ??? success "Root cause and fix"
        **Root cause.** State changes recorded as events, instead of business facts.

        **Fix.** Events named after what happened (`EarningCredited`, `AdjustmentApplied`), with the reason inside.

        From [chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#7-anti-patterns-pitfalls).

??? bug "42 · the missing event (chapter 13)"
    **Context.** The first wallet store used a database sequence for global positions, with concurrent writers.

    **Symptoms.** A few riders' daily-earnings cards permanently missed one delivery; a full rebuild fixed them.

    ??? success "Root cause and fix"
        **Root cause.** The position-gap problem: a projection advanced past a position whose transaction committed later.

        **Fix.** Appends serialised per partition, and projections wait for gaps up to a short timeout before skipping.

        From [chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#7-anti-patterns-pitfalls).

??? bug "43 · event sourcing everything (chapter 13)"
    **Context.** After the wallet's success, a team event-sourced restaurant profiles (name, address, opening hours).

    **Symptoms.** Twice the code for the same behaviour, projections for every screen, and confused new joiners; no one ever asked for profile history.

    ??? success "Root cause and fix"
        **Root cause.** A pattern applied where its benefits weren't needed.

        **Fix.** Back to a state table with an audit table; event sourcing kept for ledgers and workflows.

        From [chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#7-anti-patterns-pitfalls).

??? bug "44 · money held for food never cooked (chapter 14)"
    **Context.** The first place-order flow called payment, then kitchen, with no compensation.

    **Symptoms.** About 0.4% of orders over a weekend: customers charged, no food, no refund; support spent two days voiding authorizations by hand.

    ??? success "Root cause and fix"
        **Root cause.** No saga: a failure after a successful step left the earlier step in place.

        **Fix.** The orchestrated saga with compensations and a reconciliation job against the provider.

        From [chapter 14](../architecture-patterns/ch14-saga-pattern.md#7-anti-patterns-pitfalls).

??? bug "45 · the retry that was declined (chapter 14)"
    **Context.** payment-service re-ran its fraud rules on every request, including retries with the same idempotency key.

    **Symptoms.** A handful of customers per day had ₹500 held after their order was "cancelled".

    ??? success "Root cause and fix"
        **Root cause.** The first authorization succeeded but its reply was lost; the retry was declined, so the saga compensated only earlier steps and never voided the first authorization.

        **Fix.** Idempotency means returning the **stored** result for a repeated key; the simulation's check now guards against regressions.

        From [chapter 14](../architecture-patterns/ch14-saga-pattern.md#7-anti-patterns-pitfalls).

??? bug "46 · the implicit flow nobody owned (chapter 14)"
    **Context.** Refunds were choreographed across ordering, payment, wallet, notification and loyalty services.

    **Symptoms.** When loyalty-service dead-lettered events for a day, customers got refunds but kept loyalty points for cancelled orders; nobody noticed for a week.

    ??? success "Root cause and fix"
        **Root cause.** No component knew the whole flow or its completion state.

        **Fix.** Refunds moved to an orchestrated workflow with explicit completion and a dashboard of incomplete refunds.

        From [chapter 14](../architecture-patterns/ch14-saga-pattern.md#7-anti-patterns-pitfalls).

??? bug "47 · missing loyalty points (chapter 15)"
    **Context.** ordering-service committed the order, then called `kafkaTemplate.send()`.

    **Symptoms.** Around 40 customers a week had no points for an order; no errors in loyalty-service.

    ??? success "Root cause and fix"
        **Root cause.** Pods killed during deploys between commit and send, and short Kafka outages, lost events. Nobody had a list of what was lost.

        **Fix.** Transactional outbox with a polling relay; a one-off reconciliation job compared orders with the loyalty ledger.

        From [chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#7-anti-patterns-pitfalls).

??? bug "48 · double points after the fix (chapter 15)"
    **Context.** The outbox shipped, and the team assumed "the outbox gives exactly once".

    **Symptoms.** Some customers had double points, clustered around relay restarts and a Kafka broker failover.

    ??? success "Root cause and fix"
        **Root cause.** At-least-once delivery: the relay republished rows whose acks were lost, and consumers re-processed messages after crashes. loyalty-service had no deduplication.

        **Fix.** Inbox table in loyalty-service, written in the same transaction as the points update; a contract test that sends every message twice.

        From [chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#7-anti-patterns-pitfalls).

??? bug "49 · the disk that filled on a quiet Sunday (chapter 15)"
    **Context.** ordering-service moved to Debezium. On Sundays the outbox was quiet but other tables in the same database were busy.

    **Symptoms.** The database disk filled; writes failed for 25 minutes.

    ??? success "Root cause and fix"
        **Root cause.** The replication slot only advanced when Debezium received changes it was subscribed to, so PostgreSQL retained WAL for the busy tables. No alert on slot lag.

        **Fix.** Debezium heartbeat with an action query, `max_slot_wal_keep_size` as a safety limit, and an alert on retained WAL size.

        From [chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#7-anti-patterns-pitfalls).

??? bug "50 · the rewrite that chased a moving target (chapter 16)"
    **Context.** Pricing 2.0 was built from scratch, to replace pricing in a single cut-over.

    **Symptoms.** Eight months in, still not matching the monolith; the business kept changing monolith pricing in the meantime; cut-over postponed twice, then cancelled.

    ??? success "Root cause and fix"
        **Root cause.** No production feedback until the last day; the specification was the legacy behaviour, and nobody had it written down.

        **Fix.** Strangler fig with shadow traffic: three bugs and one undocumented behaviour found in two weeks, with no customer impact.

        From [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#7-anti-patterns-pitfalls).

??? bug "51 · customers who flipped between prices (chapter 16)"
    **Context.** An early canary routed 10% of *requests* (random per request) to the new service.

    **Symptoms.** Customers saw one price on the cart page and another at checkout; support tickets and abandoned carts.

    ??? success "Root cause and fix"
        **Root cause.** Cohorts weren't sticky, and the two implementations differed slightly (the rounding found later).

        **Fix.** Cohorts by stable customer-id hash, at the gateway and in the monolith's facade, sharing one function.

        From [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#7-anti-patterns-pitfalls).

??? bug "52 · the monolith that never got smaller (chapter 16)"
    **Context.** Three capabilities moved to new services over a year; the monolith code for all three stayed "just in case".

    **Symptoms.** Fixes had to be made twice; a bug fixed in the new service reappeared when a flag was flipped back during an unrelated incident.

    ??? success "Root cause and fix"
        **Root cause.** No deletion step; flags and old paths lived forever.

        **Fix.** "Done" means deleted: a deadline per capability (30 days at 100%), tracked on the migration dashboard; flags have owners and expiry dates.

        From [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#7-anti-patterns-pitfalls).

??? bug "53 · double retries, triple load (chapter 17)"
    **Context.** kitchen-service retried payment calls 3 times (Spring `@Retryable`); the new ambassador also retried 3 times.

    **Symptoms.** When the provider slowed down, it received up to 9 calls per payment; its rate limiter then rejected FoodFleet entirely for 10 minutes.

    ??? success "Root cause and fix"
        **Root cause.** Retries at two layers multiply; nobody owned the total.

        **Fix.** Retries in exactly one layer (the ambassador), a retry budget, and the app's timeout set above the ambassador's total.

        From [chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#7-anti-patterns-pitfalls).

??? bug "54 · the app that started before its ambassador (chapter 17)"
    **Context.** The ambassador was an ordinary container in the pod.

    **Symptoms.** After every deploy, the first few seconds of payment calls failed with `Connection refused`; Jobs using the same pod template never completed.

    ??? success "Root cause and fix"
        **Root cause.** Ordinary containers start in parallel and the sidecar never exits.

        **Fix.** Native sidecars with a startup probe on the ambassador.

        From [chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#7-anti-patterns-pitfalls).

??? bug "55 · the token that never rotated (chapter 17)"
    **Context.** The ambassador read the provider token once at start-up.

    **Symptoms.** After the scheduled key rotation, all payments failed with `401` until pods were restarted, 25 minutes later.

    ??? success "Root cause and fix"
        **Root cause.** Kubernetes updates mounted secrets in place; the process didn't notice.

        **Fix.** Re-read the file when its modification time changes (tested in `picksUpARotatedToken`), plus an alert on upstream `401`s.

        From [chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#7-anti-patterns-pitfalls).

??? bug "56 · the status page that died for an ETA (chapter 18)"
    **Context.** tracking-service called maps with the HTTP client's default (no read timeout).

    **Symptoms.** A maps brownout took the order-status endpoint to ~2% availability; it never calls maps.

    ??? success "Root cause and fix"
        **Root cause.** Request workers exhausted by calls waiting on a slow dependency; no timeout, no bulkhead.

        **Fix.** 300 ms read timeout, a bulkhead of 20 on maps calls, fallback to the last known ETA. Reproduced and verified in the simulation.

        From [chapter 18](../architecture-patterns/ch18-resilience-patterns.md#7-anti-patterns-pitfalls).

??? bug "57 · the retry storm that kept a provider down (chapter 18)"
    **Context.** Three layers each retried: the mobile app, the gateway, and tracking-service (3 attempts each).

    **Symptoms.** A 30-second maps hiccup became a 25-minute outage; the provider rate-limited FoodFleet's key.

    ??? success "Root cause and fix"
        **Root cause.** Up to 27 calls per user request during exactly the period maps was overloaded; no jitter, so retries arrived in synchronized waves.

        **Fix.** Retries only in tracking-service, 2 attempts with full jitter, a circuit breaker, and an agreed per-key rate limit.

        From [chapter 18](../architecture-patterns/ch18-resilience-patterns.md#7-anti-patterns-pitfalls).

??? bug "58 · the breaker that never closed (chapter 18)"
    **Context.** A breaker with a 60-second open period and a single trial call.

    **Symptoms.** After maps recovered, ETA stayed on fallback for many minutes; a single slow trial call kept reopening it.

    ??? success "Root cause and fix"
        **Root cause.** Open period too long, one trial too few, and slow successes counted as failures with a tight threshold.

        **Fix.** 5 s open, 3 trial calls, slow-call threshold set from healthy latency data; alerts on time spent open.

        From [chapter 18](../architecture-patterns/ch18-resilience-patterns.md#7-anti-patterns-pitfalls).

??? bug "59 · the thundering herd after a catalog outage (chapter 19)"
    **Context.** search and recommendations consumed thin `MenuItemChanged` events and called catalog for every one, retrying each second.

    **Symptoms.** When catalog came back after a two-minute outage it was hit with ten times its normal load and fell over again, twice.

    ??? success "Root cause and fix"
        **Root cause.** Every pending event in every consumer retried in lockstep; no coalescing, no jitter. The simulation reproduced it: 1.18 million calls in ten minutes.

        **Fix.** Menu events moved to state transfer (no callbacks); where callbacks remain, one fetch in flight per entity and jittered backoff.

        From [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#7-anti-patterns-pitfalls).

??? bug "60 · a field rename that broke four services (chapter 19)"
    **Context.** catalog renamed `price` to `pricePaise` in `MenuItemUpdated` "to be clearer".

    **Symptoms.** ordering, search, recommendations and analytics failed to deserialize; messages piled up in dead-letter topics; ordering kept stale prices for 40 minutes.

    ??? success "Root cause and fix"
        **Root cause.** The event was treated as catalog's internal model, not a published contract; no compatibility check.

        **Fix.** A schema registry with backward-compatibility checks in catalog's build, consumer contract tests, and a policy: add fields, never rename; breaking changes as a new event type.

        From [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#7-anti-patterns-pitfalls).

??? bug "61 · the nine-hour bootstrap (chapter 19)"
    **Context.** A new fraud-scoring service needed restaurant and menu state; the only source was a topic with seven days' retention of every change.

    **Symptoms.** Its first deployment replayed millions of events for nine hours, and its data was still incomplete: anything not changed in seven days was missing.

    ??? success "Root cause and fix"
        **Root cause.** A delete-retention topic is not a state store; there was no compacted topic or snapshot.

        **Fix.** State topics compacted (latest per key forever); bootstrap from them, then follow the live stream.

        From [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#7-anti-patterns-pitfalls).

??? bug "62 · the Thursday release train (chapter 20)"
    **Context.** Six order-related services were "independently deployable" in principle.

    **Symptoms.** Every change shipped on Thursdays, all six together, after a two-hour coordination call; a bug in one rolled back all six.

    ??? success "Root cause and fix"
        **Root cause.** A shared domain library, a table written by two services, and a synchronous cycle: changes rippled across all six.

        **Fix.** Decomposition B merged the cluster into ordering (a modular monolith inside, chapter 1) and customer; the library was split into per-service models plus published contracts.

        From [chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#7-anti-patterns-pitfalls).

??? bug "63 · the menu page that got slower after a 'performance' split (chapter 20)"
    **Context.** Pricing was split out of menu-service "so it could scale independently".

    **Symptoms.** Menu page p99 rose from 40 ms to 180 ms; menu availability dropped measurably.

    ??? success "Root cause and fix"
        **Root cause.** Ten synchronous pricing calls per menu page (N+1), each a chance to be slow or fail; pricing never needed different scaling.

        **Fix.** Merged back into catalog. Where a remote call per item is unavoidable elsewhere, a batch endpoint is now required by API review.

        From [chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#7-anti-patterns-pitfalls).

??? bug "64 · the jar that pinned ten services (chapter 20)"
    **Context.** `foodfleet-domain` held `Order`, `Customer` and `MenuItem` classes for everyone.

    **Symptoms.** A security fix in a transitive dependency took seven weeks to reach all services; six versions of the jar were in production.

    ??? success "Root cause and fix"
        **Root cause.** Domain classes shared across bounded contexts: every upgrade was a business-logic change for someone.

        **Fix.** Each service owns its model; contracts are shared as OpenAPI and event schemas; a small `foodfleet-platform` utility library with automated upgrades.

        From [chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#7-anti-patterns-pitfalls).


### Communication

??? bug "65 · the dashboard that timed out on old orders (chapter 21)"
    **Context.** The support dashboard paged with `?page=N&size=20` over all orders.

    **Symptoms.** Pages beyond a few thousand took seconds; agents' clicks queued database work that ran on after they gave up; the primary's CPU spiked during audits.

    ??? success "Root cause and fix"
        **Root cause.** OFFSET reads and discards every skipped row; cost grows with the page number (550 ms for the last page of 2 million rows, measured).

        **Fix.** Keyset cursors with an index on `(placed_at, id)`; "jump to date" replaced "jump to page 9000".

        From [chapter 21](../communication/ch21-rest-api-design.md#7-anti-patterns-pitfalls).

??? bug "66 · the order nobody saw (chapter 21)"
    **Context.** Agents worked through open orders page by page with offset pagination.

    **Symptoms.** Some escalations were never picked up; others were handled twice.

    ??? success "Root cause and fix"
        **Root cause.** Orders closing above the current offset shifted items into pages already read (skipped); new orders shifted items into the next page again (duplicated).

        **Fix.** Keyset pagination; the simulation reproduces both effects (45 skipped, 147 duplicates).

        From [chapter 21](../communication/ch21-rest-api-design.md#7-anti-patterns-pitfalls).

??? bug "67 · the lost delivery instructions (chapter 21)"
    **Context.** `PUT /orders/{id}` replaced the whole order representation, unconditionally.

    **Symptoms.** Customers' instructions ("gate code 4412") vanished; the last device to save won.

    ??? success "Root cause and fix"
        **Root cause.** No optimistic concurrency: a stale copy overwrote a newer one.

        **Fix.** A `PATCH` on the instructions sub-resource requiring `If-Match`; 412 with the current ETag on conflict; the app shows both versions and asks.

        From [chapter 21](../communication/ch21-rest-api-design.md#7-anti-patterns-pitfalls).

??? bug "68 · the client created per request (chapter 22)"
    **Context.** tracking-service built a new `RestTemplate` (with a new HttpClient underneath) inside each request handler.

    **Symptoms.** p50 latency to order-history several times what the server reported; order-history's CPU showed TLS handshakes as a top consumer; ephemeral ports in TIME_WAIT on the nodes.

    ??? success "Root cause and fix"
        **Root cause.** No connection reuse: a TCP and TLS handshake per call (measured: 15.8 ms vs 2.1 ms at p50 on loopback).

        **Fix.** HTTP service client beans in a group; one pool per destination.

        From [chapter 22](../communication/ch22-http-clients.md#7-anti-patterns-pitfalls).

??? bug "69 · the failover that took tracking down with it (chapter 22)"
    **Context.** order-history's database failed over; queries stalled for 40 s.

    **Symptoms.** tracking-service stopped answering, including endpoints that don't call order-history; Kubernetes restarted it in a loop.

    ??? success "Root cause and fix"
        **Root cause.** No read timeout on the client: every request waited on order-history (chapter 18's cascading failure).

        **Fix.** Group timeouts (500 ms connect, 2 s read) and a bulkhead; the screen shows cached history when order-history is slow.

        From [chapter 22](../communication/ch22-http-clients.md#7-anti-patterns-pitfalls).

??? bug "70 · 'invalid request' with no explanation (chapter 22)"
    **Context.** The app sent malformed cursors after a client update.

    **Symptoms.** Logs showed `400 Bad Request` thousands of times; nobody knew why for a day.

    ??? success "Root cause and fix"
        **Root cause.** The client discarded response bodies on errors; the server's problem document said exactly what was wrong.

        **Fix.** A status handler mapping problems to `ApiProblemException` with `type` and `detail`, logged and alerted by type.

        From [chapter 22](../communication/ch22-http-clients.md#7-anti-patterns-pitfalls).

??? bug "71 · ten pods, one doing all the work (chapter 23)"
    **Context.** eta-service moved to gRPC behind its existing ClusterIP Service and was scaled from 3 to 10 pods for a cricket final.

    **Symptoms.** Three pods at 95% CPU and rising latency; seven pods nearly idle. HPA kept adding pods that received nothing.

    ??? success "Root cause and fix"
        **Root cause.** HTTP/2 connections are long-lived; the layer-4 Service balanced connections, which tracking-service had opened hours earlier to the original pods.

        **Fix.** Headless Service with client-side `round_robin`, plus a server max connection age; later, the mesh balanced requests (ADR-057).

        From [chapter 23](../communication/ch23-grpc.md#7-anti-patterns-pitfalls).

??? bug "72 · the field number reused (chapter 23)"
    **Context.** A developer removed `promo_banner = 7` from `OrderStatus` and, a sprint later, added `delivery_fee_paise = 7` (an int64).

    **Symptoms.** Old app versions showed garbage promo banners; some crashed decoding a varint as a string.

    ??? success "Root cause and fix"
        **Root cause.** Field numbers are the contract; old clients still decoded field 7 with its old meaning.

        **Fix.** `reserved 7;` (and the old name) in the schema, and `buf breaking` in CI, which flags exactly this change.

        From [chapter 23](../communication/ch23-grpc.md#7-anti-patterns-pitfalls).

??? bug "73 · the stream that never ended (chapter 23)"
    **Context.** `WatchOrder` subscribed to order events and only unsubscribed on delivery.

    **Symptoms.** tracking-service memory grew all evening; heap dumps showed hundreds of thousands of listeners for cancelled orders and closed apps.

    ??? success "Root cause and fix"
        **Root cause.** No cancel handler and no end condition for cancelled orders; clients that went away left subscriptions behind.

        **Fix.** `setOnCancelHandler` to unsubscribe, completion on `DELIVERED` and `CANCELLED`, a maximum stream duration, and a gauge of active streams.

        From [chapter 23](../communication/ch23-grpc.md#7-anti-patterns-pitfalls).

??? bug "74 · the home screen that multiplied restaurant traffic (chapter 24)"
    **Context.** The new home screen listed 30 nearby restaurants, each with its 3 best dishes and current offers, via the new GraphQL BFF.

    **Symptoms.** At the evening peak restaurant-service and offer-service ran at 5× their usual request rate; the BFF's own latency looked fine at first, then p99 hit 4 s.

    ??? success "Root cause and fix"
        **Root cause.** `dishes` and `offers` were `@SchemaMapping` resolvers calling services per restaurant: 1 + 30 + 30 calls per screen load (N+1).

        **Fix.** `@BatchMapping` for both, a test asserting the number of backend calls per screen query, and a dashboard of backend calls per GraphQL operation.

        From [chapter 24](../communication/ch24-graphql.md#7-anti-patterns-pitfalls).

??? bug "75 · one query, eleven thousand calls (chapter 24)"
    **Context.** The BFF's GraphQL endpoint was reachable from the internet with introspection on and no limits.

    **Symptoms.** review-service saturated within minutes; logs showed a single client sending deeply nested `restaurant → reviews → restaurant` queries.

    ??? success "Root cause and fix"
        **Root cause.** No depth or complexity limit; each nesting level multiplied the work by the page size (this chapter measured 14,443 fields for four levels).

        **Fix.** Complexity limit 2,000 and depth 8, maximum page sizes, introspection off, and persisted queries only in production: unregistered queries are rejected.

        From [chapter 24](../communication/ch24-graphql.md#7-anti-patterns-pitfalls).

??? bug "76 · green dashboards, broken screen (chapter 24)"
    **Context.** rider-service was failing for 40 minutes.

    **Symptoms.** The BFF's HTTP dashboards showed 100% 200 responses; customers saw orders without rider details and complained on social media.

    ??? success "Root cause and fix"
        **Root cause.** GraphQL returned partial data with `errors` and HTTP 200; monitoring looked only at HTTP status.

        **Fix.** Metrics per operation and per field error (Spring for GraphQL's observations), alerts on error rate inside responses, and clients that show a clear "rider details unavailable" state.

        From [chapter 24](../communication/ch24-graphql.md#7-anti-patterns-pitfalls).

??? bug "77 · one bad event, forty minutes of missing orders (chapter 25)"
    **Context.** kitchen-tickets retried failed events in a loop inside the listener until they succeeded.

    **Symptoms.** At 19:42 tablets for a sixth of the city's restaurants stopped receiving orders; consumer lag on partition 3 grew steadily; the logs repeated the same `NullPointerException`.

    ??? success "Root cause and fix"
        **Root cause.** A poison message (missing restaurant id) retried in place forever; every later event on the partition waited behind it (measured: 16,231 of 18,000 events never processed).

        **Fix.** Validation throwing a not-retryable exception, `DefaultErrorHandler` with backoff and a DLT recoverer, and a lag alert per partition.

        From [chapter 25](../communication/ch25-asynchronous-messaging.md#7-anti-patterns-pitfalls).

??? bug "78 · the outage that skipped sixteen orders (chapter 25)"
    **Context.** The custom retry loop was removed and nothing configured instead.

    **Symptoms.** After a 30-second outage of the tablet API, sixteen restaurants never received orders; the only trace was `ERROR ... Backoff ... exhausted for order-events-3@...` in the logs.

    ??? success "Root cause and fix"
        **Root cause.** Spring Kafka's default error handler: 10 attempts with no delay, then log and skip (measured: 16 good messages lost).

        **Fix.** An explicit recoverer to the DLT, exponential backoff, and an alert on every DLT message.

        From [chapter 25](../communication/ch25-asynchronous-messaging.md#7-anti-patterns-pitfalls).

??? bug "79 · ACCEPTED before PLACED (chapter 25)"
    **Context.** To handle the dinner peak, the listener submitted each record to an 8-thread executor and returned.

    **Symptoms.** Tablets showed orders as accepted before they were placed; some orders reverted to an earlier status; after a restart, a few events were never processed at all.

    ??? success "Root cause and fix"
        **Root cause.** Parallel processing within a partition (measured: 19% of events out of order), and offsets committed for records still being processed in the pool.

        **Fix.** More partitions, keyed by order id, with container concurrency; the listener processes synchronously.

        From [chapter 25](../communication/ch25-asynchronous-messaging.md#7-anti-patterns-pitfalls).

??? bug "80 · the streams that died every 60 seconds (chapter 26)"
    **Context.** The first SSE release sent events only when the rider moved.

    **Symptoms.** Waiting customers' maps froze; logs showed every stream reconnecting about once a minute, and positions published during the gap never appeared.

    ??? success "Root cause and fix"
        **Root cause.** The load balancer's 60 s idle timeout closed silent streams; the server didn't assign event ids, so reconnecting clients couldn't resume.

        **Fix.** A comment heartbeat every 15 s, event ids with a 50-event replay buffer, and `Last-Event-ID` handling.

        From [chapter 26](../communication/ch26-websockets-sse.md#7-anti-patterns-pitfalls).

??? bug "81 · the deploy that took tracking down for half a minute (chapter 26)"
    **Context.** A rolling restart of the load balancers dropped 200,000 streams at once.

    **Symptoms.** Handshake errors and 5 s timeouts for about 20 s; gateway CPU at 100% on TLS; the gateway's own health checks failed, triggering more restarts.

    ??? success "Root cause and fix"
        **Root cause.** Every client reconnected immediately and in step; the gateways spent most of their capacity on handshakes whose clients had already given up (measured: 209,000 wasted, 20.4 s to recover).

        **Fix.** Randomised `retry:` per client, full-jitter backoff in the apps, deadline-aware shedding in the gateway, and draining in batches on deploys.

        From [chapter 26](../communication/ch26-websockets-sse.md#7-anti-patterns-pitfalls).

??? bug "82 · one slow phone, every rider delayed (chapter 26)"
    **Context.** Rider positions were published to customer streams on the rider's WebSocket handler thread.

    **Symptoms.** Riders' acknowledgements arrived seconds late in some areas; heap grew with buffered output.

    ??? success "Root cause and fix"
        **Root cause.** Writing to a customer on a poor connection blocked the publishing thread, and output for slow clients was buffered without limit.

        **Fix.** Per-stream bounded queues that keep only the latest position (conflation), sends on the stream's own thread, and send-time and buffer limits that close hopeless connections.

        From [chapter 26](../communication/ch26-websockets-sse.md#7-anti-patterns-pitfalls).

??? bug "83 · the field that crashed Android (chapter 27)"
    **Context.** order-service added an optional `promisedBy` field; the diff tool reported no breaking change.

    **Symptoms.** Crash rate on the Android tracking screen jumped from 0.1% to 31% within minutes of the deploy; iOS and web were fine.

    ??? success "Root cause and fix"
        **Root cause.** The Android app's Kotlin serialization was configured without `ignoreUnknownKeys`: a strict reader. An additive change broke it, and old app versions stay installed for months.

        **Fix.** Server-side rollback, then a tolerant decoder in the next app release; additive changes held back until 98% of active installs had the fix; "tolerant reader" added to the mobile release checklist.

        From [chapter 27](../communication/ch27-api-contracts.md#7-anti-patterns-pitfalls).

??? bug "84 · the ETA that meant something else (chapter 27)"
    **Context.** To support a new pickup screen, `etaMinutes` was changed to count to pickup instead of delivery. Same name, same type; every check passed.

    **Symptoms.** Customers were told their food would arrive 15 minutes earlier than it did; complaints and refunds rose for two days before anyone connected them to the release.

    ??? success "Root cause and fix"
        **Root cause.** A change of meaning, invisible to schema diffs and to contract tests that only check types.

        **Fix.** Reverted; added `etaToPickupMinutes` as a new field; the API review checklist now treats any description change as potentially breaking, with sign-off from listed consumers.

        From [chapter 27](../communication/ch27-api-contracts.md#7-anti-patterns-pitfalls).

??? bug "85 · the empty 200 (chapter 27)"
    **Context.** A refactor made `GET /orders/{id}` return 200 with an empty body for unknown orders, to "simplify clients".

    **Symptoms.** The tracking screen showed a blank order instead of "Order not found"; notification-service sent notifications for orders that didn't exist.

    ??? success "Root cause and fix"
        **Root cause.** A behaviour change outside the schema. The OpenAPI document still declared the 404.

        **Fix.** The tracking-service team's contract for the 404 interaction (now in the build) failed on the next attempt; the refactor was reverted.

        From [chapter 27](../communication/ch27-api-contracts.md#7-anti-patterns-pitfalls).

??? bug "86 · the retry that charged twice (chapter 28)"
    **Context.** The app retried `POST /payments` after its 1.5 s timeout; payment-service checked for an existing payment for the order, then called the provider.

    **Symptoms.** Around 0.4% of payments during peak hours were duplicated; refunds and complaints followed.

    ??? success "Root cause and fix"
        **Root cause.** Check-then-act with the provider call in between: concurrent attempts all saw "no payment yet" (measured: 83–94 of 200 keys double-charged under contention).

        **Fix.** Idempotency keys claimed atomically before the provider call, the key passed on to the provider, stored responses for retries.

        From [chapter 28](../communication/ch28-idempotency-correlation.md#7-anti-patterns-pitfalls).

??? bug "87 · duplicate push notifications after a deploy (chapter 28)"
    **Context.** notification-service deduplicated with an in-memory set per instance.

    **Symptoms.** After each deploy, a few thousand customers got "Your order is on the way" twice.

    ??? success "Root cause and fix"
        **Root cause.** The rolling deploy caused rebalances; partitions moved to instances that had never seen those event ids (measured: 2,513 of 3,000 rebalance duplicates slipped through).

        **Fix.** A shared dedup store with a 1-hour window, and the order row's "last notified version" to make DLT replays no-ops.

        From [chapter 28](../communication/ch28-idempotency-correlation.md#7-anti-patterns-pitfalls).

??? bug "88 · the order that was accepted before it was paid (chapter 28)"
    **Context.** Support investigated a disputed order by sorting the events of all services by timestamp.

    **Symptoms.** The timeline showed the kitchen accepting before payment, suggesting a bypass of payment checks; a week of investigation followed.

    ??? success "Root cause and fix"
        **Root cause.** One kitchen instance's clock was 250 ms behind (measured: 39% of orders appear misordered with such a clock); events had no causation ids, so the true sequence couldn't be shown.

        **Fix.** Causation ids on all events, an NTP drift alert, and investigation tools that build the causal tree instead of sorting by time.

        From [chapter 28](../communication/ch28-idempotency-correlation.md#7-anti-patterns-pitfalls).


### Data

??? bug "89 · the cart that charged yesterday's price (chapter 29)"
    **Context.** catalog-service updated PostgreSQL, then Redis, in the request; cart pricing read Redis.

    **Symptoms.** A restaurant raised prices for a festival; for some items customers kept paying the old price for days, and some items showed a price the restaurant had never saved.

    ??? success "Root cause and fix"
        **Root cause.** Dual writes: Redis timeouts during a failover were logged and dropped, and a code path that wrote Redis first left prices from failed transactions behind (measured: 43–77 of 5,000 items wrong after 20,000 changes under faults).

        **Fix.** Outbox in the price change's transaction; a projector that claims rows and applies them only if newer; a nightly sampled comparison of Redis against PostgreSQL.

        From [chapter 29](../data/ch29-polyglot-persistence.md#7-anti-patterns-pitfalls).

??? bug "90 · biryani disappears from autocomplete (chapter 29)"
    **Context.** Dish search used full-text search with a prefix on the last word.

    **Symptoms.** Typing "chicken b", "chicken bi" and "chicken bir" showed biryani; "chicken biry" showed nothing; "chicken biryani" showed it again.

    ??? success "Root cause and fix"
        **Root cause.** The English stemmer turned the half-typed "biry" into `biri`, which isn't a prefix of the indexed `biryani` (measured: half-typed queries 4/5 found with stemmed prefixes, 5/5 with the partial word also matched as typed).

        **Fix.** The partial last word matched both stemmed and unstemmed; a regression suite of real typed queries.

        From [chapter 29](../data/ch29-polyglot-persistence.md#7-anti-patterns-pitfalls).

??? bug "91 · the nightly delete that lagged the replicas (chapter 29)"
    **Context.** Rider history was trimmed every night with `DELETE … WHERE recorded_at < now() - interval '7 days'`.

    **Symptoms.** Every night at 02:00, read replicas fell minutes behind; the table never got smaller; autovacuum ran for hours.

    ??? success "Root cause and fix"
        **Root cause.** Deleting a day of rows writes WAL for every row (measured: 171 MB for 1.44 M rows), replicas must replay it, and the space is only reusable after vacuum.

        **Fix.** Daily partitions with retention by `DROP` (measured: 82–83 kB of WAL), created ahead by a scheduled job.

        From [chapter 29](../data/ch29-polyglot-persistence.md#7-anti-patterns-pitfalls).

??? bug "92 · tonight's special froze (chapter 30)"
    **Context.** Order placement used XA across the order and payment databases.

    **Symptoms.** At 20:10 on a Friday, orders for the two dinner specials started timing out; within seconds the whole order service was slow. Restarting the payments database didn't help.

    ??? success "Root cause and fix"
        **Root cause.** An order-service instance was killed by the orchestrator (memory limit) between the prepares and the decision; one transaction stayed prepared, holding the stock row of a special. Measured: throughput on two hot dishes fell from ~160 to 2–6 orders/s, and the prepared transaction survived the restart.

        **Fix.** The transaction manager's recovery and an in-doubt alert in the short term; the outbox-driven saga in the next release.

        From [chapter 30](../data/ch30-distributed-transactions.md#7-anti-patterns-pitfalls).

??? bug "93 · maintenance closed the restaurant (chapter 30)"
    **Context.** Payment-service's database was upgraded in a 10-minute maintenance window; order placement called payments synchronously.

    **Symptoms.** No orders could be placed for the whole window, at dinner time in one city.

    ??? success "Root cause and fix"
        **Root cause.** Synchronous coupling: measured, 2PC and a synchronous saga both accepted ~0 orders while payments was down.

        **Fix.** Orders accepted as `PENDING` through the outbox; measured, all orders during a 10 s outage were accepted and confirmed within 10.2 s.

        From [chapter 30](../data/ch30-distributed-transactions.md#7-anti-patterns-pitfalls).

??? bug "94 · rejected orders, debited wallets (chapter 30)"
    **Context.** The saga treated any error from payments as "payment failed" and rejected the order.

    **Symptoms.** After payments restarts, the odd order was rejected while the customer's wallet was debited.

    ??? success "Root cause and fix"
        **Root cause.** The connection broke while `COMMIT` was in flight: the debit may have committed, the reply was lost. Measured: up to one order per restart had an unknown outcome at 8 concurrent clients; at production concurrency, more.

        **Fix.** Only errors before `COMMIT` reject; a lost reply leaves the order `PENDING`, and recovery settles it from the debit records (older than a grace period).

        From [chapter 30](../data/ch30-distributed-transactions.md#7-anti-patterns-pitfalls).

??? bug "95 · the address that changed back (chapter 31)"
    **Context.** Profile reads were moved to a replica to relieve the primary.

    **Symptoms.** Customers updated their address, saw the old one, updated again; a few orders went to old addresses.

    ??? success "Root cause and fix"
        **Root cause.** The page reloaded from a replica that hadn't replayed the update. Measured: 0.07–0.08% of immediate re-reads stale even with a healthy replica, 15% with a lagging one.

        **Fix.** Consistency tokens: the update returns the primary's WAL position, reads wait for the replica to reach it or go to the primary. Measured: 0 stale, 2.9% of reads on the primary.

        From [chapter 31](../data/ch31-data-consistency.md#7-anti-patterns-pitfalls).

??? bug "96 · delivery going backwards (chapter 31)"
    **Context.** The tracking page polled every few seconds through a load balancer over two replicas.

    **Symptoms.** Customers saw *Out for delivery*, then *Preparing*, then *Out for delivery* again, and called support.

    ??? success "Root cause and fix"
        **Root cause.** One replica lagged the other; each poll could land on either. Measured: 19% of polls went backwards with one replica 300 ms behind.

        **Fix.** The page sends the newest position it has seen; replicas behind it are skipped.

        From [chapter 31](../data/ch31-data-consistency.md#7-anti-patterns-pitfalls).

??? bug "97 · the vanishing loyalty points (chapter 31)"
    **Context.** Order-service and promotions both added points by reading the balance, adding in Java and saving the entity.

    **Symptoms.** Customers' points didn't add up; audits found missing credits, mostly during campaigns.

    ??? success "Root cause and fix"
        **Root cause.** Lost updates under READ COMMITTED. Measured: 15.5% of all points lost with 16 concurrent writers.

        **Fix.** `UPDATE loyalty SET points = points + :n`; redemption as a conditional update.

        From [chapter 31](../data/ch31-data-consistency.md#7-anti-patterns-pitfalls).

??? bug "98 · the price that wouldn't update (chapter 32)"
    **Context.** Menu prices were cached with cache-aside; updates deleted the key after committing.

    **Symptoms.** After a restaurant raised prices, a few items showed the old price on the menu for up to an hour (the TTL), while checkout charged the new one.

    ??? success "Root cause and fix"
        **Root cause.** A reader that missed just before the update filled the cache with the old row just after the delete. Measured: "update, then delete" left 1–3 stale entries behind in a 15 s run.

        **Fix.** Write-through after commit with a set-if-newer script; readers' fills go through the same script. Measured: no stale entries left, under 0.05% stale reads.

        From [chapter 32](../data/ch32-caching-strategies.md#7-anti-patterns-pitfalls).

??? bug "99 · the 60-second heartbeat of doom (chapter 32)"
    **Context.** The home screen's "Top rated near you" was cached for 60 s with cache-aside.

    **Symptoms.** Database CPU hit 100% for a few seconds every minute; home-screen p99 latency spiked in step.

    ??? success "Root cause and fix"
        **Root cause.** A cache stampede: every request in the gap recomputed the aggregation. Measured: 80 concurrent recomputations per expiry with 80 clients.

        **Fix.** Stale-while-revalidate with a refresh lock. Measured: one recomputation per expiry, no request over 100 ms.

        From [chapter 32](../data/ch32-caching-strategies.md#7-anti-patterns-pitfalls).

??? bug "100 · views counted twice after a deploy (chapter 32)"
    **Context.** View counts were buffered in Redis and flushed every second.

    **Symptoms.** After a deploy, popular items' view counts jumped; analytics flagged an impossible spike.

    ??? success "Root cause and fix"
        **Root cause.** The flusher was killed after writing a batch to PostgreSQL but before deleting it from Redis; the new instance flushed it again. Measured: 12,182–12,817 views counted twice.

        **Fix.** A batch id stored with the claimed batch and recorded in the same statement as the counts. Measured: 0 doubled.

        From [chapter 32](../data/ch32-caching-strategies.md#7-anti-patterns-pitfalls).

??? bug "101 · the deploy that exhausted the database (chapter 33)"
    **Context.** 40 instances of the order service, HikariCP's default pool of 10 each; `max_connections` 400.

    **Symptoms.** During each rolling deploy, new instances failed to start with "too many clients already"; the deploy stalled with half the fleet replaced.

    ??? success "Root cause and fix"
        **Root cause.** 400 connections in steady state and 500 during the deploy's surge, on a database with 16 cores that did its best work at a few dozen. Measured on 2 vCPUs: little throughput gained past 16 connections, and p99 inside the database 13–17× higher at 256.

        **Fix.** Pools of 4 per instance (160 total, 200 during deploys), checked by `PoolSizing` in CI; PgBouncer in transaction mode in front for the batch jobs.

        From [chapter 33](../data/ch33-database-scaling.md#7-anti-patterns-pitfalls).

??? bug "102 · the instant migration that froze checkout (chapter 33)"
    **Context.** A release added a nullable column to `orders`, a catalogue-only change.

    **Symptoms.** Checkout timed out for about a minute, right as the migration ran; no errors in the migration log.

    ??? success "Root cause and fix"
        **Root cause.** An analyst's query had been reading `orders` for minutes. The `ALTER TABLE` waited for its lock, and every order write queued behind the `ALTER TABLE`. Measured: an instant `ADD COLUMN` behind a 12 s report blocked writes for 10 s.

        **Fix.** `lock_timeout` for migrations with retries (measured: no write waited over 0.5 s), reports moved to a replica, and `idle_in_transaction_session_timeout` on the primary.

        From [chapter 33](../data/ch33-database-scaling.md#7-anti-patterns-pitfalls).

??? bug "103 · the index that was there and wasn't (chapter 33)"
    **Context.** A migration created an index with `CREATE INDEX CONCURRENTLY IF NOT EXISTS` under a 2 s `lock_timeout`.

    **Symptoms.** The first deploy failed on the migration; the retry succeeded. Weeks later, a dashboard query was still doing sequential scans, and writes to the table had become slower.

    ??? success "Root cause and fix"
        **Root cause.** The first build waited for a long `REPEATABLE READ` export, timed out, and left an INVALID index. The retry's `IF NOT EXISTS` saw it and did nothing. Measured: the rerun "succeeded", the index stayed invalid, the planner kept scanning.

        **Fix.** No `IF NOT EXISTS` on concurrent builds; a start-up check (`InvalidIndexCheck`) and an alert on `pg_index.indisvalid = false`; drop and rebuild.

        From [chapter 33](../data/ch33-database-scaling.md#7-anti-patterns-pitfalls).

??? bug "104 · the afternoon checkout slowdown (chapter 34)"
    **Context.** Analysts had read access to the order service's primary for "just a few queries".

    **Symptoms.** Checkout latency rose every afternoon; the orders table and its indexes grew week after week although VACUUM ran.

    ??? success "Root cause and fix"
        **Root cause.** Large analytical queries competed with order writes, and long-open transactions kept VACUUM from removing dead rows. Measured: two report loops cut write throughput by 67–69%; a report held open for 30 s left 94,000–97,000 dead rows behind.

        **Fix.** Revoked access to production databases; reports moved to the reporting store fed by events. Measured on a separate copy: no change in write throughput, nothing pinned on the primary.

        From [chapter 34](../data/ch34-reporting-analytics.md#7-anti-patterns-pitfalls).

??? bug "105 · the slot that filled the disk (chapter 34)"
    **Context.** A Debezium connector fed the analytics pipeline from the payments database. The connector was decommissioned when the pipeline moved; its replication slot was not dropped.

    **Symptoms.** Weeks later the payments database ran out of disk and stopped accepting writes.

    ??? success "Root cause and fix"
        **Root cause.** The orphaned slot made PostgreSQL keep every WAL segment since the connector stopped. Measured: 132–158 MB in one minute at our write rate, without bound.

        **Fix.** `max_slot_wal_keep_size` on every database with slots, alerts on retained WAL per slot, and slot drop as part of decommissioning. Measured with a 64 MB cap: the slot was invalidated instead, and the consumer rebuilt its copy from a snapshot.

        From [chapter 34](../data/ch34-reporting-analytics.md#7-anti-patterns-pitfalls).

??? bug "106 · revenue 7% too high, and on the wrong day (chapter 34)"
    **Context.** The first reporting projection added each DELIVERED event's amount to the day's total and subtracted refunds.

    **Symptoms.** Finance's reconciliation showed revenue several percent above captured payments; the "midnight" orders appeared on the previous day.

    ??? success "Root cause and fix"
        **Root cause.** Redelivered events and amount corrections were counted again, and days were computed in UTC. Measured: +7.1% total revenue counting events, +1.9% with deduplication by event id, and 4% of orders on the wrong day with UTC days.

        **Fix.** Facts kept by order id and version, totals changed by deltas between old and new state, business day computed in the city's zone. Measured: exact.

        From [chapter 34](../data/ch34-reporting-analytics.md#7-anti-patterns-pitfalls).


### Security

??? bug "107 · one increment away from every order (chapter 35)"
    **Context.** `GET /orders/{id}` checked authentication only; order ids were sequential.

    **Symptoms.** A customer reported seeing a stranger's order, address and phone number after editing a link.

    ??? success "Root cause and fix"
        **Root cause.** No object-level authorization (API1). The returned entity also included internal fields (API3).

        **Fix.** `OrderAccessPolicy` on every operation, 404 for others' orders, `OrderView` per audience. Verified: 18,600 decisions, 0 disagreements with the rule table, 0 cross-customer access.

        From [chapter 35](../security/ch35-security-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "108 · the patch that could change the price (chapter 35)"
    **Context.** The "update delivery note" endpoint bound the request body to the JPA entity for convenience.

    **Symptoms.** A security review found that the same endpoint accepted `status` and `amountPaise`; nothing in the logs showed abuse, which was the worrying part.

    ??? success "Root cause and fix"
        **Root cause.** Mass assignment (API3): the entity's setters were the API. With Jackson 3's defaults, unknown fields would also have been silently ignored after a partial fix, hiding probing.

        **Fix.** `OrderPatch` with only the changeable fields, validated; unknown fields rejected; tests asserting each protected field is refused.

        From [chapter 35](../security/ch35-security-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "109 · the credential table that leaked (chapter 35)"
    **Context.** A legacy partner portal stored passwords as unsalted SHA-256; a backup was exposed.

    **Symptoms.** Accounts were taken over within days, mostly those with common passwords.

    ??? success "Root cause and fix"
        **Root cause.** A fast, unsalted hash: measured here at 11.7–13.0 million guesses a second on one core, with one precomputed dictionary covering every account at once.

        **Fix.** PBKDF2 at 600,000 iterations with per-password salts (7 guesses a second), forced resets, throttled logins, and migration of the portal to the company identity provider.

        From [chapter 35](../security/ch35-security-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "110 · any token from the right issuer (chapter 36)"
    **Context.** All services trusted the company identity provider and checked signature, issuer and expiry.

    **Symptoms.** An audit found that a support tool's tokens, minted for that tool, were accepted by the payments API.

    ??? success "Root cause and fix"
        **Root cause.** No audience check: every token the issuer signed was valid everywhere. The same gap would have accepted ID tokens as access tokens.

        **Fix.** Audience per API, `typ: at+jwt` required. Tested: tokens for another audience, another issuer or of another type are rejected.

        From [chapter 36](../security/ch36-oauth2-oidc-jwt.md#7-anti-patterns-pitfalls).

??? bug "111 · a minute of 401s at every key rotation (chapter 36)"
    **Context.** The identity provider rotated its signing key monthly and started using the new key immediately.

    **Symptoms.** For up to a minute after each rotation, a share of API calls failed with 401; retries made it worse.

    ??? success "Root cause and fix"
        **Root cause.** Resource servers' JWKS caches hadn't seen the new key; their refresh limit delayed picking it up. Measured: a sudden rotation rejected 29 requests at one request a second.

        **Fix.** Staged rotation: publish the next key well before it signs. Measured: 0 rejected. Refresh on unknown kid stays rate-limited (1,000 made-up kids caused 0 fetches instead of 1,000).

        From [chapter 36](../security/ch36-oauth2-oidc-jwt.md#7-anti-patterns-pitfalls).

??? bug "112 · the relayed token in a partner's logs (chapter 36)"
    **Context.** order-service relayed customers' tokens to downstream services and to a partner's delivery API.

    **Symptoms.** A token from the partner's leaked logs was used against FoodFleet's profile API. Revoking it at the provider had no effect.

    ??? success "Root cause and fix"
        **Root cause.** The relayed token carried every scope the app had, was valid for an hour, and was verified locally everywhere. Measured: a revoked 60-minute JWT keeps working 30 minutes on average.

        **Fix.** Token exchange for downstream calls (one audience, one scope, 5 minutes, `act` claim); partners get their own client credentials; access tokens shortened to 15 minutes; introspection for the profile and payment APIs.

        From [chapter 36](../security/ch36-oauth2-oidc-jwt.md#7-anti-patterns-pitfalls).

??? bug "113 · authenticated is not authorized (chapter 37)"
    **Context.** The resource server was configured with `anyRequest().authenticated()`.

    **Symptoms.** A restaurant tablet's token could call the refund endpoint; a debug endpoint added in a hurry was reachable by any customer.

    ??? success "Root cause and fix"
        **Root cause.** No rule looked at scopes or roles, and new endpoints were open by default.

        **Fix.** Scope and role rules per URL, `denyAll()` last, `@PreAuthorize` with object checks in the service. Tested: refunds need `ROLE_SUPPORT`; an endpoint without a rule answers 403.

        From [chapter 37](../security/ch37-spring-security-7.md#7-anti-patterns-pitfalls).

??? bug "114 · the authorization server that broke every API (chapter 37)"
    **Context.** Resource servers checked `audiences: order-service`; the team moved to Spring's authorization server.

    **Symptoms.** Every request failed with 401 `invalid_token` after the switch.

    ??? success "Root cause and fix"
        **Root cause.** The authorization server's default audience is the requesting client's id (read from `JwtGenerator` in 7.1.1). The tokens were valid, just not for these APIs.

        **Fix.** An `OAuth2TokenCustomizer` setting `aud` from the granted scopes (and from the request for token exchange), plus `typ: at+jwt` and `client_id`.

        From [chapter 37](../security/ch37-spring-security-7.md#7-anti-patterns-pitfalls).

??? bug "115 · the service that could get any scope (chapter 37)"
    **Context.** order-service was registered with every payments scope "to be safe" and used token exchange for refunds.

    **Symptoms.** A security review found order-service could obtain `payments:refund` tokens for any customer whose token it held, although customers' apps never had that scope.

    ??? success "Root cause and fix"
        **Root cause.** Spring's token exchange validates the requested scopes against the requesting client's registration, not against the user's token (`validateRequestedScopes(registeredClient, ...)` in 7.1.1).

        **Fix.** Register services only with the downstream scopes they need; payment-service checks the user's own entitlements as well; chapter 36's stricter exchange policy where the provider allows it.

        From [chapter 37](../security/ch37-spring-security-7.md#7-anti-patterns-pitfalls).

??? bug "116 · the debugging pod that captured payments (chapter 38)"
    **Context.** Services trusted anything that reached them inside the cluster.

    **Symptoms.** Payments captured by an engineer's test pod in another namespace; found in reconciliation two days later.

    ??? success "Root cause and fix"
        **Root cause.** No workload authentication: network location was the only control.

        **Fix.** Mutual TLS with SPIFFE IDs, peer policy per route: rider-service's valid SVID now gets 403 on capture. Istio namespaces start from allow-nothing.

        From [chapter 38](../security/ch38-service-to-service-security.md#7-anti-patterns-pitfalls).

??? bug "117 · the revoked workload that kept calling (chapter 38)"
    **Context.** A compromised batch job was removed from SPIRE's registrations; its one-hour SVID would expire within the hour.

    **Symptoms.** The job's calls kept succeeding hours after its SVID expired.

    ??? success "Root cause and fix"
        **Root cause.** It kept resuming TLS sessions; the resumed sessions carried the original certificate and the trust manager was never called. Measured: 19 of 19 new connections accepted after expiry on JDK 21 defaults.

        **Fix.** Validity checked on every request in `PeerPolicyFilter` (0 accepted afterwards), connection age capped, and a deny rule for the job's SPIFFE ID while it is investigated.

        From [chapter 38](../security/ch38-service-to-service-security.md#7-anti-patterns-pitfalls).

??? bug "118 · the client that expired an hour after deploy (chapter 38)"
    **Context.** order-service built its payment-service `RestClient` with `RestClientSsl.fromBundle("svid")` at start-up; the SSL bundle had `reload-on-update: true`.

    **Symptoms.** Every capture failed with `certificate_unknown` about an hour after each deployment; restarting "fixed" it.

    ??? success "Root cause and fix"
        **Root cause.** Spring Boot reloads the bundle and the embedded server follows; the request factory built for the client kept the original key material (`AutoConfiguredRestClientSsl` builds it once). Measured with a 4 s lifetime: failures from 3.3 s on.

        **Fix.** Rebuild the client on bundle updates (`PaymentsClient`): 0 failures with rotation.

        From [chapter 38](../security/ch38-service-to-service-security.md#7-anti-patterns-pitfalls).

??? bug "119 · the heap dump in the ticket (chapter 39)"
    **Context.** An out-of-memory heap dump was attached to a vendor support ticket.

    **Symptoms.** A security review found the orders database password and payment gateway key in the ticket system.

    ??? success "Root cause and fix"
        **Root cause.** Secrets in use are in memory; a heap dump contains them. Measured: a `String` configuration value was in every dump; even a `Secret` still in use was.

        **Fix.** Heap dumps classified as secret: written to encrypted storage, analysed in place, never attached; credentials rotated after any dump leaves the cluster.

        From [chapter 39](../security/ch39-secrets-management.md#7-anti-patterns-pitfalls).

??? bug "120 · the database that disappeared on day two (chapter 39)"
    **Context.** order-service moved to `spring.cloud.vault.database` dynamic credentials with `max_ttl=24h`.

    **Symptoms.** A day after the release, every new database connection failed with `password authentication failed`; pods that had restarted in the meantime were fine.

    ??? success "Root cause and fix"
        **Root cause.** Spring Cloud Vault 5.0.2 requests database credentials as a renewable secret: renewed until max_ttl, never replaced. Measured: renew-only clients failed from 18.1 s with an 18 s max_ttl.

        **Fix.** A rotating Spring Vault lease feeding HikariCP's credentials provider; 0 failures.

        From [chapter 39](../security/ch39-secrets-management.md#7-anti-patterns-pitfalls).

??? bug "121 · the rotated password that didn't lock anyone out (chapter 39)"
    **Context.** After a laptop was stolen, the team rotated the shared orders database password.

    **Symptoms.** Audit logs later showed queries from the stolen credential's sessions for hours after the rotation.

    ??? success "Root cause and fix"
        **Root cause.** PostgreSQL checks passwords at login; open sessions continue. Measured: after a password change, an expiry or `NOLOGIN`, the open session's next query worked.

        **Fix.** Revocation terminates sessions; per-pod dynamic credentials so one compromised credential is one role to revoke.

        From [chapter 39](../security/ch39-secrets-management.md#7-anti-patterns-pitfalls).

??? bug "122 · the limit that was six limits (chapter 40)"
    **Context.** order-service enforced "100 requests a minute per client" with an in-memory limiter in each of its six pods.

    **Symptoms.** A coupon-farming script kept 600 requests a minute flowing; the per-client dashboard, read per pod, showed every pod within limits.

    ??? success "Root cause and fix"
        **Root cause.** A per-instance limit multiplies by the number of instances. Measured: four instances let 1,036 requests through a 260 budget.

        **Fix.** A shared GCRA in Redis (258–260 through), per-instance limits only as a fallback, and a one-per-customer business rule on the coupon itself.

        From [chapter 40](../security/ch40-api-security.md#7-anti-patterns-pitfalls).

??? bug "123 · the user-content subdomain (chapter 40)"
    **Context.** Reviews with photos were served from `reviews.foodfleet.in`. The API's CORS configuration accepted any origin ending in `foodfleet.in`, and the session cookie was `SameSite=Lax`.

    **Symptoms.** Customers' order histories, with addresses, appeared on a paste site; the requests came from customers' own browsers.

    ??? success "Root cause and fix"
        **Root cause.** A stored XSS on the review page ran with an origin the API trusted and on the same site as the session cookie. Measured in Chromium: the same-site page received even `SameSite=Strict` cookies and read the orders from a server that reflected its Origin.

        **Fix.** User content moved to a separate registrable domain; CORS to an exact allow-list; Fetch Metadata refusing same-site requests from untrusted origins.

        From [chapter 40](../security/ch40-api-security.md#7-anti-patterns-pitfalls).

??? bug "124 · the tenant that stayed on the connection (chapter 40)"
    **Context.** A team added row-level security and set the tenant with `SET app.tenant_id = ...` in a request interceptor.

    **Symptoms.** Rarely, a restaurant's dashboard showed another restaurant's orders; never reproducible in tests.

    ??? success "Root cause and fix"
        **Root cause.** A session-level `SET` stays on a pooled connection. A request on a code path that skipped the interceptor (an async export job) borrowed the connection and ran as the previous tenant. Measured: 4,000 rows of the previous tenant.

        **Fix.** `set_config('app.tenant_id', ?, true)` inside the transaction (`TenantTransactions`), and a policy that returns nothing when the tenant is unset: 0 rows.

        From [chapter 40](../security/ch40-api-security.md#7-anti-patterns-pitfalls).

??? bug "125 · nine days to answer 'do we use it?' (chapter 41)"
    **Context.** A critical library vulnerability was announced on a Friday.

    **Symptoms.** Teams searched repositories by hand; shaded copies inside fat jars and a vendor image were missed; "done" was declared three times.

    ??? success "Root cause and fix"
        **Root cause.** No inventory of what was deployed, and scanning only at build time. Measured: Keycloak 24.0.0 went from 0 known findings to 14 in three months, 196 today, without changing a byte.

        **Fix.** An SBOM per artifact, attached to the image; a daily rescan of every deployed digest; the question became a query.

        From [chapter 41](../security/ch41-supply-chain-compliance.md#7-anti-patterns-pitfalls).

??? bug "126 · the action that printed the secrets (chapter 41)"
    **Context.** The release workflow used third-party actions by tag and had a registry token with write access to every repository.

    **Symptoms.** Build logs for a week contained the token, base64-encoded twice.

    ??? success "Root cause and fix"
        **Root cause.** A compromised maintainer account moved the tag to malicious code (the pattern of the March 2025 `tj-actions/changed-files` compromise). Tags are mutable; the token's scope made one leak a platform-wide one.

        **Fix.** Actions pinned by SHA and updated by Renovate with review; per-job `permissions`; keyless signing with OIDC, so there was no signing key to steal.

        From [chapter 41](../security/ch41-supply-chain-compliance.md#7-anti-patterns-pitfalls).

??? bug "127 · the erasure that couldn't reach the backups (chapter 41)"
    **Context.** A customer exercised their right to erasure.

    **Symptoms.** Rows were deleted from order-service, but the data survived in a Kafka topic, 90 days of backups and an analytics export in another region.

    ??? success "Root cause and fix"
        **Root cause.** Personal data copied in plaintext into stores that can't be edited, and an export nobody had classified (the OPA policy flags this: personal data in `eu-west-1`, retention 3,650 days).

        **Fix.** Personal fields encrypted with per-customer keys before they leave the service; erasure deletes the key. Measured: 0 of 9,000 erased fields readable in any copy.

        From [chapter 41](../security/ch41-supply-chain-compliance.md#7-anti-patterns-pitfalls).


### Resilience

??? bug "128 · the forty-minute recovery (chapter 42)"
    **Context.** The payment provider slowed for five minutes on a Saturday evening.

    **Symptoms.** Order placement collapsed and stayed collapsed for forty minutes after the provider recovered; every service was healthy.

    ??? success "Root cause and fix"
        **Root cause.** Three layers of retries kept the arrival rate above capacity; FIFO queues served requests whose callers had left. Measured: with retries, 0% of users served for 30 s after a 5 s trigger.

        **Fix.** A retry budget per dependency, deadlines on every hop, and shedding of queued work older than half the caller's timeout: full recovery the moment the trigger ended.

        From [chapter 42](../resilience/ch42-failure-modes.md#7-anti-patterns-pitfalls).

??? bug "129 · the restart that took an hour to finish (chapter 42)"
    **Context.** The rider app's live-tracking gateway was restarted during a deploy; 80,000 riders' apps reconnected.

    **Symptoms.** Reconnects came in waves every few seconds, growing apart; some riders were offline for minutes.

    ??? success "Root cause and fix"
        **Root cause.** Exponential backoff without jitter: every app had failed at the same moment, so every app retried at the same moments. Measured: 42 s to reconnect 600 clients, against 6 s with full jitter.

        **Fix.** Full jitter in the app's reconnect loop; the gateway sheds reconnects it can't serve within 250 ms.

        From [chapter 42](../resilience/ch42-failure-modes.md#7-anti-patterns-pitfalls).

??? bug "130 · green dashboards, slow checkout (chapter 42)"
    **Context.** One of eight menu-service pods landed on a node with a failing disk.

    **Symptoms.** Checkout p99 doubled; every health check and every pod's average latency looked normal.

    ??? success "Root cause and fix"
        **Root cause.** A gray failure (slow on some requests) amplified by fan-out: checkout calls menu-service for every item. Measured: 5% slow calls became 41–42% slow requests at fan-out 10.

        **Fix.** Least-outstanding-request balancing and latency-based outlier ejection; the node was found by comparing per-pod p99, not averages.

        From [chapter 42](../resilience/ch42-failure-modes.md#7-anti-patterns-pitfalls).

??? bug "131 · menus back, breaker still open (chapter 43)"
    **Context.** restaurant-service failed for about forty seconds during a database failover.

    **Symptoms.** "Menu unavailable" for twenty seconds after restaurant-service was healthy again; quiet admin endpoints showed raw errors for about ten seconds before their breakers opened.

    ??? success "Root cause and fix"
        **Root cause.** Library defaults: a 60 s open state, and a 100-call window that a 5-calls-a-second endpoint takes 20 s to fill. Measured: the default breaker refused 100% of users for the rest of the run after recovery, and took 7.5–10 s to open at 5 calls/s.

        **Fix.** A 5-second time window with a 20-call minimum, open for 5 s: opened in 2–4 s at both rates, closed within 5 s of recovery.

        From [chapter 43](../resilience/ch43-resilience4j-deep-dive.md#7-anti-patterns-pitfalls).

??? bug "132 · the retries that never ran (chapter 43)"
    **Context.** Menu calls were annotated `@Retry @CircuitBreaker(fallbackMethod = "cached")`.

    **Symptoms.** Customers saw stale menus during routine network blips; retry metrics showed zero retries.

    ??? success "Root cause and fix"
        **Root cause.** The fallback ran at the breaker's layer, inside the retry: every first failure was already a successful (cached) answer. Measured: 68–70% fresh menus against 97–98% with the fallback on `@Retry`.

        **Fix.** Fallback on the outermost annotation; a test that a transient failure produces a fresh result.

        From [chapter 43](../resilience/ch43-resilience4j-deep-dive.md#7-anti-patterns-pitfalls).

??? bug "133 · the payment queue nobody configured (chapter 43)"
    **Context.** payment-service's client used `@ConcurrencyLimit(16)` to protect the provider; order-service ran on virtual threads.

    **Symptoms.** During a provider slowdown, checkout requests hung for tens of seconds and memory climbed; no errors were logged.

    ??? success "Root cause and fix"
        **Root cause.** `@ConcurrencyLimit` blocks by default, without a timeout; with virtual threads nothing else bounded the waiting callers. Measured at 2× overload: a p99 of 10 s and about 4,000 callers queued.

        **Fix.** `policy = REJECT`, a 503 with `Retry-After` at the edge, and the provider timeout below the checkout deadline.

        From [chapter 43](../resilience/ch43-resilience4j-deep-dive.md#7-anti-patterns-pitfalls).

??? bug "134 · the hedge that took down search (chapter 44)"
    **Context.** Menu search hedged reads to its index after 50 ms to cut p99, tested at normal load.

    **Symptoms.** During the dinner peak, search latency jumped to the timeout for most users; the index cluster was saturated with twice its usual query rate.

    ??? success "Root cause and fix"
        **Root cause.** Unbudgeted hedging near capacity: more load, more slow calls, more hedges. Measured at 85% load: 18–43% of users served, up to 1.84 calls per user.

        **Fix.** A 5% hedge budget (99.2–99.5% served at the same load); hedging disabled automatically when the index's own queue time rises.

        From [chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#7-anti-patterns-pitfalls).

??? bug "135 · checkout behind the menus (chapter 44)"
    **Context.** order-service served menus and checkout on virtual threads with no admission control.

    **Symptoms.** A browsing spike made checkout time out; after the spike, latency stayed high for minutes.

    ??? success "Root cause and fix"
        **Root cause.** One FIFO queue for all requests, no limit: measured at 2× overload, 3.3% of checkouts served in time and a p99 of 15 s; through a slowdown, no recovery while the backlog drained.

        **Fix.** `AdmissionFilter` with an AIMD limit partitioned 90/10 for critical requests: 79–81% of checkouts served at 2× overload.

        From [chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#7-anti-patterns-pitfalls).

??? bug "136 · the limiter that rejected a healthy service (chapter 44)"
    **Context.** A team added concurrency-limits with its default (Vegas) limit to restaurant-service.

    **Symptoms.** Steady 503s at ordinary load; partners saw failed menu updates on a quiet afternoon.

    ??? success "Root cause and fix"
        **Root cause.** Vegas read the normal spread of the service's latency as queueing and held the limit at 8–9: measured, 52–53% of healthy traffic rejected.

        **Fix.** AIMD with "slow" at half the callers' deadline: 0% rejected at normal load, and the same protection in a slowdown.

        From [chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#7-anti-patterns-pitfalls).

??? bug "137 · the experiment that became the outage (chapter 45)"
    **Context.** A team ran its first latency experiment against all restaurant-service pods in production, watching dashboards by eye.

    **Symptoms.** Menu errors for every user; it took the engineers two minutes to notice and another to find the command to stop it.

    ??? success "Root cause and fix"
        **Root cause.** No automatic guard and no blast-radius limit. Measured on the lab: with no guard, about 1,500 users hurt in 30 s; with a 3-window guard 150–180; with the fault on every instance, 93–94% of users during the experiment.

        **Fix.** `Experiment` with `abortAfter(3)`, `mode: one`, staging first, and the guard's probe on the real SLO.

        From [chapter 45](../resilience/ch45-chaos-engineering.md#7-anti-patterns-pitfalls).

??? bug "138 · the breaker that passed a test it should have failed (chapter 45)"
    **Context.** A team tested its circuit breaker with a black hole on responses in Toxiproxy; the experiment passed.

    **Symptoms.** In a real network partition, menu calls hung, threads and memory climbed, and the breaker never opened.

    ??? success "Root cause and fix"
        **Root cause.** Low-fidelity fault: the test server's connection closes reached the client through the response-side black hole and failed calls fast. The client had no read timeout. Measured: the response-side black hole nearly passed (2–3 bad windows of 20), the request-side one failed 19 of 20 with about 2,000 calls stuck.

        **Fix.** Black holes swallow requests; a read timeout below the breaker's slow-call threshold; the experiment rerun weekly.

        From [chapter 45](../resilience/ch45-chaos-engineering.md#7-anti-patterns-pitfalls).

??? bug "139 · the delay that ran all weekend (chapter 45)"
    **Context.** A NetworkChaos manifest applied by the deploy pipeline on a Friday; `durations: 30s` was a typo.

    **Symptoms.** One restaurant-service pod was 3 s slow until Monday; retries hid it, costs rose.

    ??? success "Root cause and fix"
        **Root cause.** The pipeline's client didn't request strict field validation, the unknown field was pruned, and Chaos Mesh treats a missing duration as "until deleted". Measured: the typo passes schema validation and fails strict validation.

        **Fix.** Strict validation of every manifest in CI; experiments only through the workflow with a deadline.

        From [chapter 45](../resilience/ch45-chaos-engineering.md#7-anti-patterns-pitfalls).

??? bug "140 · two histories (chapter 46)"
    **Context.** The cross-region link failed; the Mumbai primary kept accepting orders; ten minutes later Hyderabad was promoted.

    **Symptoms.** When Mumbai returned, four minutes of paid orders existed only there, and some IDs had been reused in Hyderabad.

    ??? success "Root cause and fix"
        **Root cause.** Asynchronous replication during a partition, and promotion without fencing. Measured: with the link lost 2 s before the primary, every commit in those 2 s was lost (17,000–22,700 here), against 85–278 when both went together.

        **Fix.** Fence the old region before promoting (stop its writers, withdraw its DNS); IDs from per-region ranges; regional failover only by runbook.

        From [chapter 46](../resilience/ch46-disaster-recovery.md#7-anti-patterns-pitfalls).

??? bug "141 · the failover that took a minute longer than the database (chapter 46)"
    **Context.** A zone failure; the standby was promoted in under 15 seconds.

    **Symptoms.** order-service kept failing for another 30 to 60 seconds.

    ??? success "Root cause and fix"
        **Root cause.** Clients found the primary through a DNS name with a 60 s TTL, and pgJDBC's `socketTimeout` of 0 kept some connections waiting on the dead primary. Measured: promotion took about 0.1 s; clients following DNS with a 30 s TTL resumed 24–33 s after the crash, those asking the hosts directly 0.3 s after promotion.

        **Fix.** Every host in the JDBC URL with `targetServerType=primary`, `socketTimeout` set, `hostRecheckSeconds=2`.

        From [chapter 46](../resilience/ch46-disaster-recovery.md#7-anti-patterns-pitfalls).

??? bug "142 · the backup nobody had restored (chapter 46)"
    **Context.** A migration deleted a column's data on every replica within a second.

    **Symptoms.** The nightly backup restored, but WAL archiving had been failing silently for a week; the restore stopped at last week.

    ??? success "Root cause and fix"
        **Root cause.** No monitoring of archive age, no restore rehearsal. Archiving with the default `archive_timeout` of 0 also leaves quiet databases' last segment unshipped indefinitely.

        **Fix.** Archive age as an alert; `archive_timeout` bounded (or `pg_receivewal`); a monthly timed restore to a point in time.

        From [chapter 46](../resilience/ch46-disaster-recovery.md#7-anti-patterns-pitfalls).


### Observability

??? bug "143 · the dashboard that knew where but not why (chapter 47)"
    **Context.** The canary of payment-service 1.5.

    **Symptoms.** checkout p99 up nearly thirtyfold, on one pod, with no errors; logs at INFO said nothing new; 1.5 was rolled back without a cause.

    ??? success "Root cause and fix"
        **Root cause.** The only per-request data was a log line with no fields, and traces were head-sampled at 1%. Measured: the per-pod p99 showed the canary within one 10-second window (720–732 ms against 10–21 ms); 11,000 log lines after the deploy said nothing; 1% head sampling kept 2–4 of 248–285 slow traces; spans or wide events with method, bank and pod isolated the cause with 100% precision and recall.

        **Fix.** Wide events per request; 100% of spans to the collector with tail sampling; request context (method, bank, tenant) on spans.

        From [chapter 47](../observability/ch47-observability-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "144 · the restaurant label (chapter 47)"
    **Context.** A developer added `restaurant_id` to the order latency histogram "to debug one restaurant".

    **Symptoms.** Prometheus's memory climbed until it was killed; dashboards timed out.

    ??? success "Root cause and fix"
        **Root cause.** One label multiplied every histogram's series by the number of restaurants. Measured: 1,000 values turned 265 series into 260,005; Prometheus went from about 100 MB to 670 MB, a scrape from 10 ms to over 700 ms and a p99 query from 5 ms to over 400 ms.

        **Fix.** Per-restaurant questions go to traces and wide events; metric labels are reviewed against a cardinality budget.

        From [chapter 47](../observability/ch47-observability-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "145 · the token in the telemetry (chapter 47)"
    **Context.** A payment gateway token was passed as `-Dpayments.gateway.token=…` for one urgent release.

    **Symptoms.** A security review found the token in the trace backend, and in every Prometheus series of the service once someone enabled "all resource attributes as labels".

    ??? success "Root cause and fix"
        **Root cause.** The agent puts the full JVM command line in the resource and masks only properties named `*password*` or `*secret*`. Measured: a `-D…password` property was masked; a `-D…token` property appeared in the exported spans' resource and, with all resource attributes converted to labels, in Prometheus. The command line was 983 of the resource's 1,645 characters, sent with every batch.

        **Fix.** Secrets from a secret store, never flags ([chapter 39](../security/ch39-secrets-management.md)); `otel.resource.disabled.keys` and the collector's `resource` processor drop the command line.

        From [chapter 47](../observability/ch47-observability-fundamentals.md#7-anti-patterns-pitfalls).

??? bug "146 · the averaged p99 (chapter 48)"
    **Context.** Checkout's latency SLO panel.

    **Symptoms.** The panel said 140 ms for a month while one city's customers waited seconds.

    ??? success "Root cause and fix"
        **Root cause.** `avg()` of per-pod client-side percentiles. Measured with one slow pod in four: averaged client-side p99 137 ms, true fleet p99 322 ms, `histogram_quantile` over summed buckets 317–318 ms.

        **Fix.** Percentile histograms, `histogram_quantile(0.99, sum by (le) (rate(..._bucket[1m])))`, and an SLO bucket at 250 ms.

        From [chapter 48](../observability/ch48-metrics-micrometer.md#7-anti-patterns-pitfalls).

??? bug "147 · a timer per restaurant (chapter 48)"
    **Context.** A new menu-prefetch feature called restaurant-service with `uri("/restaurants/" + id + "/menu")`.

    **Symptoms.** order-service's scrape grew to tens of megabytes and took over a second; Prometheus started missing scrapes; heap usage crept up.

    ??? success "Root cause and fix"
        **Root cause.** RestClient uses the string given to `uri(...)` as the template, so every restaurant was a new uri tag value. Measured: 10,000 calls made 19,918 meters (a timer and a long-task timer per restaurant), a 10.4 MB scrape taking 301–365 ms, and with histograms 707,089 series, 135 MB and 1.35 s per scrape; the template form made 2 meters.

        **Fix.** `uri("/restaurants/{id}/menu", id)`; `max-uri-tags` for clients too; a series-count check in CI.

        From [chapter 48](../observability/ch48-metrics-micrometer.md#7-anti-patterns-pitfalls).

??? bug "148 · the endpoint with no metrics (chapter 48)"
    **Context.** A team overrode the server convention to tag the raw path "for more detail", then turned on Boot's uri cap when the series exploded.

    **Symptoms.** Weeks later, a new cancel endpoint had no metrics at all; its error-rate alert could never fire.

    ??? success "Root cause and fix"
        **Root cause.** The cap is first come, first served: the first 100 raw paths filled it, and every later value, including the new route, was denied, with one warning in the log at startup. Measured: with raw paths capped at 100 values, a new endpoint's 100 requests produced 0 timers.

        **Fix.** Fix the tag (path patterns), not just cap it; alert on the cap's warning.

        From [chapter 48](../observability/ch48-metrics-micrometer.md#7-anti-patterns-pitfalls).

??? bug "149 · the kitchen in its own trace (chapter 49)"
    **Context.** The kitchen service polled the broker in a loop.

    **Symptoms.** Every order's trace ended at order-service; kitchen spans existed but each in a new trace.

    ??? success "Root cause and fix"
        **Root cause.** Pull-style consumption: the context is in the message's headers, but no instrumentation extracts it for `nextMessage()`. Measured on NATS: a handler-style consumer continued the order's trace 220 of 220 times, a `nextMessage()` loop 0 of 220, and the loop with the context extracted from the headers 220 of 220.

        **Fix.** A handler-style consumer, or extract from the headers and make the context current (or link to it) around the processing.

        From [chapter 49](../observability/ch49-distributed-tracing-otel.md#7-anti-patterns-pitfalls).

??? bug "150 · half-traces from a service that knew better (chapter 49)"
    **Context.** payment-service's team configured `traceidratio` 0.05 "to save money"; another team's service still spoke only B3.

    **Symptoms.** Many checkout traces had no payment spans; payment-service's own traces had no parents.

    ??? success "Root cause and fix"
        **Root cause.** A sampler that isn't parent-based re-decides and drops spans of sampled traces; a service that can't read `traceparent` starts new traces. Measured: with payment-service on `traceidratio` 0.05, 43–47 of 74–85 sampled orders had no payment spans; with B3 only, all 1,000 payment requests started new traces and no sampled order had payment spans.

        **Fix.** Parent-based everywhere; consume W3C and B3; sample in the collector.

        From [chapter 49](../observability/ch49-distributed-tracing-otel.md#7-anti-patterns-pitfalls).

??? bug "151 · the email in the partner's logs (chapter 49)"
    **Context.** A mobile app release started sending `baggage` with the user's email and session token "for debugging".

    **Symptoms.** A payment partner reported receiving them on every charge request.

    ??? success "Root cause and fix"
        **Root cause.** Services extract baggage from any caller and propagate it on every outgoing call. Measured: a client's email and session token in `baggage` reached the partner on 50 of 50 calls; an allow-list at the gateway reduced the header to 2 keys and 30 bytes, and `withoutBaggage` around the partner call removed it.

        **Fix.** An allow-list at the edge, no baggage to third parties, and the edge stripping trace headers from the internet.

        From [chapter 49](../observability/ch49-distributed-tracing-otel.md#7-anti-patterns-pitfalls).

??? bug "152 · the request ID that pointed at someone else (chapter 50)"
    **Context.** A payment retry worker on a shared pool put the request ID into the MDC with `MDC.put` and returned early on an error path.

    **Symptoms.** Searching one customer's request showed another customer's payment failures. A support agent quoted them to the wrong customer.

    ??? success "Root cause and fix"
        **Root cause.** The MDC is a thread-local, and pool threads are reused. Measured: a task that left `request.id` set made the next task on that thread log another request's ID **99 times in 200**, with no error.

        **Fix.** Use try-with-resources (`RequestMdc`, `MDC.putCloseable`), and run pooled work through `MdcPropagation.wrap`, which restores the worker's own map. Correlate on the trace ID, which was right on all 200.

        From [chapter 50](../observability/ch50-structured-logging.md#7-anti-patterns-pitfalls).

??? bug "153 · the sale-day hole in the logs (chapter 50)"
    **Context.** order-service used `AsyncAppender` with its defaults, in front of a log shipper that slowed under load.

    **Symptoms.** INFO lines for ten minutes of the sale were mostly missing; WARN and ERROR were all there. Nothing reported a problem.

    ??? success "Root cause and fix"
        **Root cause.** With less than 20% of the queue free, Logback drops TRACE to INFO without counting them. Measured: **29–30% of INFO** lines delivered under a burst into a slow sink. With `neverBlock`, only 9% of WARN were delivered.

        **Fix.** Set `discardingThreshold` 0, use a larger queue and keep `neverBlock` false. Alert on shipper lag, and reduce volume rather than dropping lines at random.

        From [chapter 50](../observability/ch50-structured-logging.md#7-anti-patterns-pitfalls).

??? bug "154 · the card number in an exception (chapter 50)"
    **Context.** Customer data in arguments was wrapped in `Pii` types; everything else was trusted.

    **Symptoms.** A security review found full card numbers and phone numbers in the log store, from a partner SDK's exception messages and from MDC values set by a filter.

    ??? success "Root cause and fix"
        **Root cause.** Masking at the source covers only wrapped values. Measured: message concatenation, MDC values and exception messages each leaked **5 of 5** secrets with `Pii` alone. A masking encoder on the finished line leaked none.

        **Fix.** Put a masking encoder on every appender, keep `Pii` types, and run a CI leak test. Purge and rotate the affected data, and report it under the data-protection process.

        From [chapter 50](../observability/ch50-structured-logging.md#7-anti-patterns-pitfalls).

??? bug "155 · the Saturday nobody was paged (chapter 51)"
    **Context.** order-service alerted on "5xx ratio > 1% for 5 minutes".

    **Symptoms.** A payment provider rejected one card network for most of a Saturday; 0.6% of checkouts failed. Nobody was alerted. By Monday a fifth of the month's budget was gone.

    ??? success "Root cause and fix"
        **Root cause.** A static threshold above the failure rate. Measured: 0.6% for 18 hours (14.5% of the budget) never crossed "> 1%". Nor did 0.25% for 4 days or 0.15% for 10 days, which spent 30.7% and 43.3%.

        **Fix.** Burn-rate alerts. The multi-window ticket fired after 9.9 hours, with 8% of the budget spent.

        From [chapter 51](../observability/ch51-slo-alerting.md#7-anti-patterns-pitfalls).

??? bug "156 · the pager that cried wolf (chapter 51)"
    **Context.** After incident 1, the team lowered the threshold to the SLO itself: "error ratio > 0.1% for 5 minutes".

    **Symptoms.** Pages several times a week, some lasting days. On-call started silencing it for 24 hours at a time.

    ??? success "Root cause and fix"
        **Root cause.** Random variation crosses the SLO line, and any burn above 1× keeps the alert on for as long as it lasts. Measured: 3 false firings in a week on a healthy service, and 5,756 minutes of continuous firing during a 4-day slow burn.

        **Fix.** Page only on fast burns over two windows, and ticket slow ones.

        From [chapter 51](../observability/ch51-slo-alerting.md#7-anti-patterns-pitfalls).

??? bug "157 · refund-service paged for every refund that failed (chapter 51)"
    **Context.** The internal refund service handles about one request a minute and got the same generated alerts as checkout.

    **Symptoms.** A page for each failed refund, a few times a week, each resolving itself within minutes.

    ??? success "Root cause and fix"
        **Root cause.** At low traffic a single failure is a large error ratio. Measured: 16 pages in 30 days on a healthy service, one per failure.

        **Fix.** Use a minimum request count in the long window (100 removed all 16), a longer window, or synthetic traffic, and check whether the service needs a page at all.

        From [chapter 51](../observability/ch51-slo-alerting.md#7-anti-patterns-pitfalls).

??? bug "158 · the failover that restarted everything (chapter 52)"
    **Context.** order-service's liveness probe pointed at `/actuator/health`, which includes the database.

    **Symptoms.** A 50-second database failover became a two-minute outage of the whole service, including endpoints that don't use the database.

    ??? success "Root cause and fix"
        **Root cause.** Liveness failed with the database, so Kubernetes restarted every pod, repeatedly. Measured: 6 restarts in a 60-second outage, `/menu` at 39–47% while pods restarted, and orders back 14–95 seconds after the database.

        **Fix.** Liveness on the liveness group only. With that, `/menu` stayed at 100% and orders recovered immediately.

        From [chapter 52](../observability/ch52-health-checks-actuator.md#7-anti-patterns-pitfalls).

??? bug "159 · the release that never started (chapter 52)"
    **Context.** A release added a cache warm-up; startup went from 12 to 40 seconds. The liveness probe had a 10-second initial delay.

    **Symptoms.** New pods restarted every 25 seconds, then went into CrashLoopBackOff. The rollout stalled, and the old pods kept serving.

    ??? success "Root cause and fix"
        **Root cause.** Liveness started failing before the application listened. Measured: never ready, 4–5 restarts in 3 minutes.

        **Fix.** A startup probe. Measured: ready in 43–44 seconds, no restarts.

        From [chapter 52](../observability/ch52-health-checks-actuator.md#7-anti-patterns-pitfalls).

??? bug "160 · errors on every deploy (chapter 52)"
    **Context.** Graceful shutdown was enabled, so the team expected clean deploys.

    **Symptoms.** A handful of connection-refused errors in every deploy, from clients opening new connections.

    ??? success "Root cause and fix"
        **Root cause.** Pods stopped listening before every node's kube-proxy had removed them. Measured: 79–96 failed requests per rolling restart with graceful shutdown alone.

        **Fix.** `preStop: sleep: 5`. Measured: 0 failures.

        From [chapter 52](../observability/ch52-health-checks-actuator.md#7-anti-patterns-pitfalls).


### Deployment & Platform

??? bug "161 · OOMKilled with no OutOfMemoryError (chapter 53)"
    **Context.** order-service ran with `JAVA_OPTS=-Xmx1g` in containers limited to 768 MB.

    **Symptoms.** Pods restarted every few hours with `OOMKilled`. There were no Java errors and no heap dumps.

    ??? success "Root cause and fix"
        **Root cause.** The heap was allowed to grow past the container's limit, so the kernel killed the process. Measured: `-Xmx1g` in a 512 MB container holding 600 MB died with exit 137 and `OOMKilled: true`, silently.

        **Fix.** Use `MaxRAMPercentage=75` and `ExitOnOutOfMemoryError`. The same pressure then gives a Java OOM and a clean exit, with code 3.

        From [chapter 53](../deployment/ch53-containerization.md#7-anti-patterns-pitfalls).

??? bug "162 · every deploy took 30 seconds a pod (chapter 53)"
    **Context.** The entrypoint was `sh -c "java $JAVA_OPTS -jar app.jar"`.

    **Symptoms.** Pods always took the full grace period to terminate. No shutdown logs appeared, and requests were cut mid-flight.

    ??? success "Root cause and fix"
        **Root cause.** PID 1 was the shell, which doesn't forward SIGTERM. Measured: 10.2 seconds and SIGKILL with shell form or a wrapper script; 157–171 ms with exec form or `exec java`.

        **Fix.** Use exec form, or `exec` in the script. For `JAVA_OPTS`, use `JAVA_TOOL_OPTIONS`, which the JVM reads itself.

        From [chapter 53](../deployment/ch53-containerization.md#7-anti-patterns-pitfalls).

??? bug "163 · slow scale-out on sale day (chapter 53)"
    **Context.** A fat-jar image on a JDK base, about 470 MB unpacked, deployed twenty times a day.

    **Symptoms.** New nodes took minutes to start pods during a scale-out. Registry egress costs grew.

    ??? success "Root cause and fix"
        **Root cause.** Every deploy produced a new fat-jar layer, and every new node pulled a full JDK. Measured: 469 MB against 88 MB with a jlink'd runtime; 1.6 MB against 23–26 KB re-pushed per one-class change.

        **Fix.** Use a jlink'd runtime on distroless with layered dependencies, and pre-pull base layers on node images.

        From [chapter 53](../deployment/ch53-containerization.md#7-anti-patterns-pitfalls).

??? bug "164 · the latency CPU graphs couldn't explain (chapter 54)"
    **Context.** Every service had `limits.cpu: 500m` from a shared template.

    **Symptoms.** p99 doubled while average CPU sat at 30%. Deploys got slower too.

    ??? success "Root cause and fix"
        **Root cause.** CFS quotas throttle bursts: the JIT, GC and startup spend the period's quota, and requests wait. Measured at 250m: startup in 3.9–4.0 s instead of 0.7–0.8 s, p99 93–97 ms instead of 10–16 ms for 5 ms of work, and up to 2.2 s throttled in 30 s.

        **Fix.** Remove CPU limits for latency-sensitive JVM services and keep requests. Set `ActiveProcessorCount`. Alert on `container_cpu_cfs_throttled_periods_total`.

        From [chapter 54](../deployment/ch54-kubernetes-for-spring.md#7-anti-patterns-pitfalls).

??? bug "165 · half the pods ran the old discount (chapter 54)"
    **Context.** The discount lived in a ConfigMap, read by different services as an env var, a `subPath` file and a mounted directory.

    **Symptoms.** After an update, some pods applied it and most didn't, until a deploy days later.

    ??? success "Root cause and fix"
        **Root cause.** Only mounted directories are updated. Measured: a watched directory saw changes after 1.4–76 s; the env var, `configtree:` and `subPath` never did.

        **Fix.** Live values go in a watched mount or a config service. Everything else restarts on change, through a ConfigMap hash annotation.

        From [chapter 54](../deployment/ch54-kubernetes-for-spring.md#7-anti-patterns-pitfalls).

??? bug "166 · the autoscaler added pods nobody used (chapter 54)"
    **Context.** The gateway called order-service through a pooled keep-alive HTTP client.

    **Symptoms.** During a spike, the autoscaler added pods and they stayed idle while the original pods were saturated.

    ??? success "Root cause and fix"
        **Root cause.** kube-proxy balances connections, and the pool's connections were all on the original pods. Measured: after 1 → 3 replicas, 250 of 250 keep-alive requests per window stayed on pod 1 for 50 seconds, while new connections spread within 10 seconds.

        **Fix.** Cap connection lifetime in client pools, or balance per request with a mesh (chapter 57).

        From [chapter 54](../deployment/ch54-kubernetes-for-spring.md#7-anti-patterns-pitfalls).

??? bug "167 · every deploy cut capacity on sale day (chapter 55)"
    **Context.** menu-service had an HPA, and its chart still set `replicas: 3`. Upgrades used Helm 3.

    **Symptoms.** During the sale, every deploy dropped menu-service to 3 pods, and latency spiked until the HPA scaled back up a minute later.

    ??? success "Root cause and fix"
        **Root cause.** A client-side three-way merge resets fields that differ from the manifest. Measured: after a scale to 5, client-side kubectl and Helm 3-style upgrades both set `replicas` back to 2. Helm 4's server-side apply refused with a conflict instead.

        **Fix.** Omit `replicas` when autoscaling is enabled; with that, the measured apply kept 5. Move to server-side apply so the next field like this is a conflict, not a surprise.

        From [chapter 55](../deployment/ch55-helm-kustomize.md#7-anti-patterns-pitfalls).

??? bug "168 · the discount nobody received (chapter 55)"
    **Context.** Product changed the discount in the chart's values and deployed. The release succeeded.

    **Symptoms.** No pod applied the new discount until a code release the next week restarted them.

    ??? success "Root cause and fix"
        **Root cause.** A ConfigMap change doesn't change the pod template, so nothing rolls. Measured: 0 of 2 pods replaced, and the environment variable kept the old value.

        **Fix.** The `checksum/config` annotation, or kustomize's hashed generator names: both replaced the pods within 5–9 s.

        From [chapter 55](../deployment/ch55-helm-kustomize.md#7-anti-patterns-pitfalls).

??? bug "169 · two builds of one version (chapter 55)"
    **Context.** payment-service ran `payment-service:1.8.2` with `imagePullPolicy: Always`. A developer rebuilt and re-pushed `1.8.2` to fix a typo in a log line.

    **Symptoms.** On Saturday, the cluster autoscaler added a node, and some payments followed slightly different code paths. Dashboards by version showed nothing, since every pod reported 1.8.2.

    ??? success "Root cause and fix"
        **Root cause.** A tag is a mutable pointer. Measured: after moving a tag, a scale-out ran 2 × 1.4.0 and 2 × 1.4.1 behind one Service whenever a node pulled.

        **Fix.** Digests in every environment but dev, enforced by policy at CI and admission; registries configured with immutable tags as well.

        From [chapter 55](../deployment/ch55-helm-kustomize.md#7-anti-patterns-pitfalls).

??? bug "170 · lunchtime at the maximum (chapter 56)"
    **Context.** menu-service's HPA targeted CPU with a 2–8 range. The JVM spent its first 30 s compiling, and the readiness probe passed as soon as the port was open.

    **Symptoms.** Every lunchtime, the HPA went to 8 pods and stayed there for the rest of the peak, while dashboards showed most pods nearly idle.

    ??? success "Root cause and fix"
        **Root cause.** Ready pods' warm-up CPU counted as load, so each scale-up justified another. Measured: ready-at-once pods drove the HPA to its maximum of 8; with readiness failing until warm it stopped at 5.

        **Fix.** Readiness fails until warm-up (or a startup probe), and the warm-up itself is shortened (class data sharing, chapter 60).

        From [chapter 56](../deployment/ch56-autoscaling.md#7-anti-patterns-pitfalls).

??? bug "171 · the batch job that made the API slow (chapter 56)"
    **Context.** A batch job called menu-service for a minute every two minutes. The HPA had `scaleDown.stabilizationWindowSeconds: 0`, copied from a blog post about saving money.

    **Symptoms.** Every two minutes, a burst of slow and failed requests for all of menu-service's callers.

    ??? success "Root cause and fix"
        **Root cause.** With no window, the HPA scaled down in every gap and back up in every burst, and each scale-up started JVMs next to the pods serving. Measured: 10–11 scaling changes and 325–432 failed requests in eight minutes, against 2 changes and 97–192 failures with the default window.

        **Fix.** The default window, and a minimum that carries the batch job's load.

        From [chapter 56](../deployment/ch56-autoscaling.md#7-anti-patterns-pitfalls).

??? bug "172 · the request that halved the fleet (chapter 56)"
    **Context.** A cost review doubled menu-service's CPU request "to give it room", leaving the HPA's 60% target unchanged.

    **Symptoms.** Next peak, menu-service ran half as many pods as before, with higher latency.

    ??? success "Root cause and fix"
        **Root cause.** Utilization is usage divided by request: doubling the request halved the measured utilization, so the HPA kept half the pods.

        **Fix.** Change requests and targets together, and review both from measured use (VPA recommendations).

        From [chapter 56](../deployment/ch56-autoscaling.md#7-anti-patterns-pitfalls).

??? bug "173 · the mesh that retried a pod to death (chapter 57)"
    **Context.** order-service had a client retry (3 attempts), and the mesh added its own (2 retries). One pod's database connection pool was exhausted.

    **Symptoms.** Error rates on that pod climbed, and traffic to it climbed too, until its liveness probe failed.

    ??? success "Root cause and fix"
        **Root cause.** Retries in two layers multiply, and without ejection they keep going back to the bad endpoint. Measured: retries alone produced ~1,500 extra attempts for 3,000 requests, half of them to the bad pod.

        **Fix.** Retries in the mesh only, with a budget; outlier detection on. Measured with both: 0 failures and 35 retries.

        From [chapter 57](../deployment/ch57-service-mesh.md#7-anti-patterns-pitfalls).

??? bug "174 · outlier detection that never ejected (chapter 57)"
    **Context.** The platform enabled outlier detection with `consecutive_5xx: 5` for every service, leaving the other fields at their defaults.

    **Symptoms.** A three-pod service with one broken pod kept failing a third of requests, and the mesh dashboards showed no ejections.

    ??? success "Root cause and fix"
        **Root cause.** `max_ejection_percent` defaults to 10%, which rounds down to zero pods for a three-pod service. Measured: 1,000 of 3,000 failed and 0 ejections, against 26 failed with 50%.

        **Fix.** Set `max_ejection_percent` (or `always_eject_one_host`) explicitly in the platform's defaults.

        From [chapter 57](../deployment/ch57-service-mesh.md#7-anti-patterns-pitfalls).

??? bug "175 · strict mTLS, and a port nobody covered (chapter 57)"
    **Context.** A team ran Envoy as an explicit sidecar on a VM-style service. mTLS was strict on the sidecar's port.

    **Symptoms.** A penetration test reached the service's admin endpoints in plaintext.

    ??? success "Root cause and fix"
        **Root cause.** The app listened on all interfaces; only traffic sent to the sidecar was checked. Measured: plaintext to the app's port returned 200 when it listened on 0.0.0.0, and was refused when it listened on 127.0.0.1.

        **Fix.** Traffic redirection (the mesh's iptables or ambient mode), apps on localhost, and NetworkPolicies admitting only the sidecar port.

        From [chapter 57](../deployment/ch57-service-mesh.md#7-anti-patterns-pitfalls).

??? bug "176 · the scale nobody committed (chapter 58)"
    **Context.** During an incident, an engineer ran `kubectl scale --replicas=8` on order-service. CI applied manifests only on deploys.

    **Symptoms.** A month later, a capacity review couldn't explain why order-service ran 8 pods while Git said 3.

    ??? success "Root cause and fix"
        **Root cause.** Push-based deploys don't look at the cluster between deploys. Measured with a pull-based reconciler: the scale was reverted to Git's value within one 10 s interval.

        **Fix.** GitOps with self-heal; incident changes go through a pull request, or are explicitly paused in the controller and committed afterwards.

        From [chapter 58](../deployment/ch58-cicd-gitops.md#7-anti-patterns-pitfalls).

??? bug "177 · the flag that wouldn't die (chapter 58)"
    **Context.** A feature flag lived in a ConfigMap. The team deleted the file from the repository when the feature launched.

    **Symptoms.** Months later, a new service read the old flag's value and enabled dead code.

    ??? success "Root cause and fix"
        **Root cause.** Applying manifests never deletes what they no longer contain. Measured: with an inventory, the deleted ConfigMap was pruned within 7–8 s of the commit.

        **Fix.** Pruning on, from the controller's inventory; Argo CD's `prune: true`.

        From [chapter 58](../deployment/ch58-cicd-gitops.md#7-anti-patterns-pitfalls).

??? bug "178 · the 5% bug in every pod (chapter 58)"
    **Context.** A release broke checkout for one payment method, about 5% of requests. Readiness probes passed. The rollout was a plain rolling update.

    **Symptoms.** Error alerts fired after the error budget burn rate crossed its threshold; the rollback took eleven minutes from release.

    ??? success "Root cause and fix"
        **Root cause.** Nothing between the probe and the users was judging the release. Measured: as a rolling update undone after two minutes, 944–953 requests failed; as a canary, 7–10.

        **Fix.** Canary steps with automated analysis on per-version error rates, aborting on early bursts.

        From [chapter 58](../deployment/ch58-cicd-gitops.md#7-anti-patterns-pitfalls).

??? bug "179 · the key that outlived its owner (chapter 59)"
    **Context.** Services reached the cloud's APIs with access keys in Secrets. One leaked through a debug endpoint.

    **Symptoms.** Calls from an unknown address, months later, with a key nobody remembered creating.

    ??? success "Root cause and fix"
        **Root cause.** Long-lived keys have no expiry and no binding to a workload. Measured, a projected token lasted 600 s, and a token for the wrong audience or service account was refused outright.

        **Fix.** Workload identity for every service; keys deleted; an organisation policy that forbids creating new ones.

        From [chapter 59](../deployment/ch59-cloud-native-platforms.md#7-anti-patterns-pitfalls).

??? bug "180 · forty pods, one secrets service (chapter 59)"
    **Context.** order-service fetched its secrets from the secrets service at startup. A sale started forty pods at once. The SDK used legacy retries.

    **Symptoms.** Pods failed to start with throttling errors, restarted, and throttled again.

    ??? success "Root cause and fix"
        **Root cause.** At twice the API's limit, legacy retries made the most attempts per call: measured, 7,367–7,448 throttled responses for the same ~100 successful calls a second.

        **Fix.** Adaptive retry mode (measured: 141–161 failed calls instead of 766–914), jittered startup, and secrets cached and refreshed in the background (chapter 39).

        From [chapter 59](../deployment/ch59-cloud-native-platforms.md#7-anti-patterns-pitfalls).

??? bug "181 · the token copied out of a pod (chapter 59)"
    **Context.** An engineer copied a pod's projected token to debug a permission problem from a laptop, and deleted the pod afterwards.

    **Symptoms.** None, which was the problem: the token kept working.

    ??? success "Root cause and fix"
        **Root cause.** The cloud verifies tokens offline. Measured: the API server rejected the deleted pod's token within 30 s; the offline check accepted it until it expired.

        **Fix.** Short token lifetimes, short cloud sessions, narrow roles, and an alarm on credential use from outside the cluster's network.

        From [chapter 59](../deployment/ch59-cloud-native-platforms.md#7-anti-patterns-pitfalls).

??? bug "182 · the cache that wasn't used (chapter 60)"
    **Context.** An image shipped an AOT cache. A later change added a jar to the class path in the entrypoint but not in the training run.

    **Symptoms.** Startup went back to its old time; nothing failed.

    ??? success "Root cause and fix"
        **Root cause.** The JVM validates the cache against the class path, and with the default `AOTMode=auto` starts without one that doesn't match.

        **Fix.** Train and run with the same class path from the same Dockerfile; a CI smoke test with `-XX:AOTMode=on`, which fails instead; `-Xlog:aot` in startup logs.

        From [chapter 60](../deployment/ch60-native-images-startup.md#7-anti-patterns-pitfalls).

??? bug "183 · the training run that skipped the hot path (chapter 60)"
    **Context.** The team trained the cache with a script that called the service's logic directly, not through HTTP.

    **Symptoms.** Startup improved less than the blog posts promised, and the first requests were as slow as before.

    ??? success "Root cause and fix"
        **Root cause.** A cache only knows what its training run did. Measured: a cache trained without the HTTP server started in 907–942 ms against 611–732 ms for one trained through HTTP, and in 3.1 s against 2.0–2.1 s at a quarter of a CPU.

        **Fix.** Train the real service through its real API.

        From [chapter 60](../deployment/ch60-native-images-startup.md#7-anti-patterns-pitfalls).

??? bug "184 · the native image that ran out of reflection (chapter 60)"
    **Context.** A service moved to a native image; tests ran on the JVM.

    **Symptoms.** In production, one endpoint failed with `ClassNotFoundException` for a class only reached by reflection from a JSON library.

    ??? success "Root cause and fix"
        **Root cause.** The agent recorded only the paths the training run took.

        **Fix.** Run the test suite against the native executable (Spring Boot's `nativeTest`), and keep reachability metadata in the repository.

        From [chapter 60](../deployment/ch60-native-images-startup.md#7-anti-patterns-pitfalls).


### Testing

??? bug "185 · the coupon cap (chapter 61)"
    **Context.** order-service's tests were mostly component tests, written per user story. A refactoring of `Pricing` dropped the cap on FIRST20.

    **Symptoms.** Large orders got 20% off with no limit for two days before finance noticed.

    ??? success "Root cause and fix"
        **Root cause.** No test priced an order above the cap. Measured: the component suite missed P1, as it missed every boundary bug; only unit tests caught it.

        **Fix.** Unit tests at every edge of every rule; a review checklist item for new rules.

        From [chapter 61](../testing/ch61-test-strategy.md#7-anti-patterns-pitfalls).

??? bug "186 · the green H2 build (chapter 61)"
    **Context.** Repository tests ran on H2 so the build needed no Docker. When idempotency moved into SQL (`ON CONFLICT`), the H2 tests failed, and were marked `@Disabled` "until H2 supports it".

    **Symptoms.** A later change dropped the conflict clause; retries created duplicate orders in production.

    ??? success "Root cause and fix"
        **Root cause.** The only tests of the SQL couldn't run it. Measured: H2 failed 3 of 4 tests on correct code and caught 0 of 20 bugs; PostgreSQL caught R2, the dropped clause.

        **Fix.** Repository tests on PostgreSQL in a container (chapter 62); no disabled test without an owner and a date.

        From [chapter 61](../testing/ch61-test-strategy.md#7-anti-patterns-pitfalls).

??? bug "187 · the renamed field (chapter 61)"
    **Context.** A developer renamed `total` to `orderTotal` in the response, updated the unit tests that built the JSON, and shipped.

    **Symptoms.** The app showed blank totals for an hour.

    ??? success "Root cause and fix"
        **Root cause.** Only a test that reads the HTTP response as a client would could catch it. Measured: A1–A3 were caught by the component suite and nothing else.

        **Fix.** Component tests that assert on the response a client sees; a provider contract test for every consumer (chapter 63).

        From [chapter 61](../testing/ch61-test-strategy.md#7-anti-patterns-pitfalls).

??? bug "188 · the 500s on sale day (chapter 62)"
    **Context.** The coupon rule was rewritten as check-then-insert so the H2 tests would pass.

    **Symptoms.** Users who double-tapped "apply coupon" got an error; support saw a spike of 500s.

    ??? success "Root cause and fix"
        **Root cause.** Concurrent requests both passed the check and both inserted; the primary key rejected the second. Measured: 421–449 errors in 300 rounds of 16 concurrent requests, and none with `ON CONFLICT`.

        **Fix.** `ON CONFLICT` back in the code; repository tests on PostgreSQL; a concurrent test that asserts one winner and no exceptions.

        From [chapter 62](../testing/ch62-unit-slice-integration-tests.md#7-anti-patterns-pitfalls).

??? bug "189 · the tests that passed alone (chapter 62)"
    **Context.** order-service's tests were `@Transactional` and rolled back. The code under test saved an audit record in a `REQUIRES_NEW` transaction.

    **Symptoms.** Tests passed one by one in the IDE and failed in CI in a different order, each time a different one.

    ??? success "Root cause and fix"
        **Root cause.** The audit records were committed on their own connection, survived the rollback, and leaked into later tests. Measured: 299 of 300 tests failed once the code committed on its own connection.

        **Fix.** Truncate after each integrated test; assert on an empty database at the start of tests that depend on it.

        From [chapter 62](../testing/ch62-unit-slice-integration-tests.md#7-anti-patterns-pitfalls).

??? bug "190 · the twelve-minute build (chapter 62)"
    **Context.** Each repository test class declared its own static `@Container`, and many had a `@MockitoBean` or two.

    **Symptoms.** The build took twelve minutes; most of it was containers starting and contexts loading.

    ??? success "Root cause and fix"
        **Root cause.** A container per class: measured, 18.0–22.9 s for 8 classes against 4.0–4.7 s with one. And a context per distinct configuration.

        **Fix.** One container in a shared base class; mocks moved into shared test configuration; the suite went from dozens of contexts to a handful.

        From [chapter 62](../testing/ch62-unit-slice-integration-tests.md#7-anti-patterns-pitfalls).

??? bug "191 · the price in paise (chapter 63)"
    **Context.** menu-service moved prices to paise for precision, kept the field a string, and updated its OpenAPI description.

    **Symptoms.** The app showed dishes at a hundred times their price for the hour before the release was rolled back.

    ??? success "Root cause and fix"
        **Root cause.** No schema rule sees meaning. Measured: the OpenAPI diff reported no breaking change; contracts with type matchers only passed; contracts with a two-decimal format failed.

        **Fix.** Formats in consumer contracts where the value's meaning matters; a new field (`pricePaise`) instead of a new meaning for an old one.

        From [chapter 63](../testing/ch63-contract-testing.md#7-anti-patterns-pitfalls).

??? bug "192 · the contracts nobody trusted (chapter 63)"
    **Context.** A team generated consumer contracts by recording the provider's responses in staging.

    **Symptoms.** The provider's build failed whenever a restaurant changed a price, and the team turned contract verification into a warning.

    ??? success "Root cause and fix"
        **Root cause.** Recorded examples with no matchers are snapshots. Measured: they flagged all 5 harmless data changes and 2 harmless API changes.

        **Fix.** Contracts written by consumers with type matchers and a few formats; measured, no false alarms and 7 of 9 breaks caught.

        From [chapter 63](../testing/ch63-contract-testing.md#7-anti-patterns-pitfalls).

??? bug "193 · the spice level nobody tested (chapter 63)"
    **Context.** menu-service added `EXTRA_HOT`. All contracts passed; the OpenAPI diff reported an ERR, which the team overrode as "additive".

    **Symptoms.** The app's menu screen failed for one restaurant.

    ??? success "Root cause and fix"
        **Root cause.** The contracts verified restaurant 7, which had no extra-hot dish. Measured: only the diff caught C5.

        **Fix.** Provider states that include every enum value; tolerant enum parsing in consumers; diff ERRs reviewed, not overridden by default.

        From [chapter 63](../testing/ch63-contract-testing.md#7-anti-patterns-pitfalls).

??? bug "194 · the coupon that only worked once a day (chapter 64)"
    **Context.** The E2E suite used a fixed customer, `e2e-customer`, in shared staging.

    **Symptoms.** The FIRST20 journey passed for the first pipeline each morning and failed for everyone else.

    ??? success "Root cause and fix"
        **Root cause.** The rule worked: once per customer. Measured: 11 of 12 suite runs failed that journey in shared staging.

        **Fix.** A new customer per test run, and an environment per pipeline run.

        From [chapter 64](../testing/ch64-component-e2e-testing.md#7-anti-patterns-pitfalls).

??? bug "195 · the biryani that sold out in CI (chapter 64)"
    **Context.** After moving to unique customers, the suite was still red in staging every afternoon.

    **Symptoms.** "The biryani of the day can be ordered" failed with 409.

    ??? success "Root cause and fix"
        **Root cause.** Stock is shared by the environment, not owned by a test. Measured: with unique test data, 7 of 12 runs still failed that journey; with fresh environments, none did.

        **Fix.** Fresh environments for E2E; staging's daily stock raised and reset nightly for manual testing.

        From [chapter 64](../testing/ch64-component-e2e-testing.md#7-anti-patterns-pitfalls).

??? bug "196 · the sleep that was long enough on a laptop (chapter 64)"
    **Context.** The courier journey slept 1 s before checking; it always passed locally.

    **Symptoms.** About one CI run in ten failed, then passed on rerun.

    ??? success "Root cause and fix"
        **Root cause.** Courier assignment has a long tail. Measured: a 1 s sleep failed 9.5–12.5% of runs, 2 s still 1–3.5%.

        **Fix.** Polling with a 10 s timeout: measured, no failures, and faster on average than the 1 s sleep.

        From [chapter 64](../testing/ch64-component-e2e-testing.md#7-anti-patterns-pitfalls).

??? bug "197 · the 15-millisecond p99 (chapter 65)"
    **Context.** menu-service's load test used 50 looping virtual users.

    **Symptoms.** The test passed with a p99 of 15 ms; on sale day, menus took seconds every few minutes.

    ??? success "Root cause and fix"
        **Root cause.** Coordinated omission. Measured: against a service freezing 1 s every 10 s, a closed model reported a 15 ms p99 where the open model measured 942 ms.

        **Fix.** Arrival-rate load tests; HdrHistogram correction for old closed-model scripts; max always reported.

        From [chapter 65](../testing/ch65-performance-chaos-arch-tests.md#7-anti-patterns-pitfalls).

??? bug "198 · the chaos experiment that passed (chapter 65)"
    **Context.** A chaos experiment injected 2 s of latency into menu-service with the hypothesis "order errors stay under 1%".

    **Symptoms.** It passed. Users still complained about checkout taking seconds.

    ??? success "Root cause and fix"
        **Root cause.** The hypothesis counted errors, not latency. Measured: with no timeout, every order succeeded and 15 of 25 windows broke the latency objective.

        **Fix.** Hypotheses from the SLOs, latency included; a 300 ms timeout and a last-known-price fallback, measured at 100% success and a 302–303 ms p99.

        From [chapter 65](../testing/ch65-performance-chaos-arch-tests.md#7-anti-patterns-pitfalls).

??? bug "199 · the constant that crossed a layer (chapter 65)"
    **Context.** ArchUnit's layer rules passed; a review found the domain using an infrastructure class's constant for a table limit.

    **Symptoms.** None until the infrastructure class was moved to another module, and the domain module stopped compiling.

    ??? success "Root cause and fix"
        **Root cause.** Inlined constants leave no trace in the code. Measured: no rule caught V8; `jdeps` did, from the constant-pool entry javac leaves.

        **Fix.** Constants that belong to the domain live in the domain; a `jdeps`-based check between modules in the build.

        From [chapter 65](../testing/ch65-performance-chaos-arch-tests.md#7-anti-patterns-pitfalls).


### Performance & Scalability

??? bug "200 · the 200 threads nobody chose (chapter 66)"
    **Context.** The quote endpoint ran on Tomcat's defaults; each request waited 150 ms on three downstream calls.

    **Symptoms.** At dinner peaks, p99 went from 200 ms to seconds with CPU below half.

    ??? success "Root cause and fix"
        **Root cause.** The thread pool, not the CPU, was the capacity. Measured: 200 threads served at most 1,060–1,100 quotes a second; at 1,500/s the p50 was 3.7–4.4 s, while virtual threads served 1,490/s on the same machine.

        **Fix.** Virtual threads (ADR-008), sized and limited explicitly.

        From [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#7-anti-patterns-pitfalls).

??? bug "201 · the limit that went away (chapter 66)"
    **Context.** After the switch to virtual threads, a promotion doubled the quote's traffic.

    **Symptoms.** Heap at its limit, long GC pauses, timeouts on almost every request, the pod restarting in a loop.

    ??? success "Root cause and fix"
        **Root cause.** Nothing limited requests in progress any more. Measured: at 2,500/s, unbounded virtual threads failed 91–93% of requests and peaked at 640 MB with a 512 MB heap; the event loop ran out of memory.

        **Fix.** A limit of 300 in progress, from Little's law. Measured: 1,510–1,588 served a second at a p99 of 261–303 ms; the rest rejected at once and retried by the edge with backoff.

        From [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#7-anti-patterns-pitfalls).

??? bug "202 · virtual threads that made it slower (chapter 66)"
    **Context.** A team on JDK 21 enabled virtual threads on a service whose cache client wrapped its network calls in `synchronized`.

    **Symptoms.** Throughput fell to a fraction of the platform-thread version; thread dumps showed few carriers, all busy.

    ??? success "Root cause and fix"
        **Root cause.** Pinning: each blocked virtual thread held a carrier. Measured: 1,000 tasks of 50 ms took 25.2 s inside `synchronized` on JDK 21, 0.10–0.11 s on JDK 25.

        **Fix.** JDK 25; until then, the client's lock replaced by a `ReentrantLock`, found with `jdk.VirtualThreadPinned` in JFR.

        From [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#7-anti-patterns-pitfalls).

??? bug "203 · Serial in production (chapter 67)"
    **Context.** menu-service ran in pods of 2 CPUs and 1 GiB with no JVM options.

    **Symptoms.** p99 jumped above half a second every few minutes during peaks.

    ??? success "Root cause and fix"
        **Root cause.** Ergonomics chose Serial, since 1 GiB is below the 1,792 MB "server class" threshold. Measured: Serial's full collection of a 350 MB live set took 655–704 ms; G1 on the same load paused at most 85–112 ms.

        **Fix.** `-XX:+UseG1GC` in the base image, and memory sized from measurements.

        From [chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#7-anti-patterns-pitfalls).

??? bug "204 · the heap that fitted and the pod that didn't (chapter 67)"
    **Context.** To give the heap room, the team set `-Xmx` to 90% of the container's memory. A later release added a Netty-based client with pooled direct buffers.

    **Symptoms.** Pods restarted with `OOMKilled`, exit 137, no stack trace and no heap dump.

    ??? success "Root cause and fix"
        **Root cause.** The heap plus native memory exceeded the limit. Measured: 90% heap + 150 MB of direct buffers was killed by the kernel in one run of two; at 90% alone, peak RSS was already 894–946 MB of 1,024.

        **Fix.** Container memory from heap + NMT's measured non-heap + 15%; `MaxDirectMemorySize` set explicitly.

        From [chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#7-anti-patterns-pitfalls).

??? bug "205 · a low-pause collector on a small pod (chapter 67)"
    **Context.** The tracking service switched to ZGC to remove GC pauses, keeping its 1-CPU pods.

    **Symptoms.** GC pauses disappeared from the dashboards and p99 got worse.

    ??? success "Root cause and fix"
        **Root cause.** ZGC's concurrent threads competed with the service for its only CPU. Measured on 1 CPU: ZGC's pauses were 0.1 ms, its p99 106–136 ms; G1's p99 was 68–92 ms.

        **Fix.** 2 CPUs per pod for ZGC, or G1 on small pods.

        From [chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#7-anti-patterns-pitfalls).

??? bug "206 · the carts that left with the pod (chapter 68)"
    **Context.** order-service kept carts in the HTTP session in memory, with sticky sessions to keep users on one copy.

    **Symptoms.** During every rollout, a slice of users found their carts empty; support tickets spiked after deployments.

    ??? success "Root cause and fix"
        **Root cause.** Affinity keeps in-memory state reachable only while the copy lives. Measured: replacing the sticky copy lost 200 of 200 carts; without affinity, 0–2 of 200 survived even without a rollout.

        **Fix.** Carts in PostgreSQL: 200 of 200 kept through a replacement.

        From [chapter 68](../performance-scalability/ch68-horizontal-scaling.md#7-anti-patterns-pitfalls).

??? bug "207 · twelve copies, three busy (chapter 68)"
    **Context.** order-service was scaled from 3 to 12 copies before a cricket final; its main caller used keep-alive connections through a Kubernetes Service.

    **Symptoms.** The 3 old copies ran at full CPU; the 9 new ones were nearly idle.

    ??? success "Root cause and fix"
        **Root cause.** kube-proxy balances connections. Measured: after scaling 2 → 4 with keep-alive, the new copies got 0% of requests; with a keep-alive client on 4 copies, 1 or 2 got nothing.

        **Fix.** The mesh's per-request balancing (chapter 57), and a maximum connection age in the callers' HTTP clients.

        From [chapter 68](../performance-scalability/ch68-horizontal-scaling.md#7-anti-patterns-pitfalls).

??? bug "208 · more pods, same orders per second (chapter 68)"
    **Context.** The payment step was slow during a sale, so order-service was autoscaled on CPU.

    **Symptoms.** Copies went from 4 to 16; orders per second didn't change; payment latency rose.

    ??? success "Root cause and fix"
        **Root cause.** The payment provider's concurrency limit was the bottleneck. Measured: behind a 50-per-second dependency, 4 copies served 50 a second, each at a third of its capacity.

        **Fix.** A bulkhead on the provider (chapter 18), autoscaling on the payment queue, and a higher limit negotiated with the provider.

        From [chapter 68](../performance-scalability/ch68-horizontal-scaling.md#7-anti-patterns-pitfalls).

??? bug "209 · tens of thousands of tiny requests (chapter 69)"
    **Context.** Order producers ran with `linger.ms=0` from an old template.

    **Symptoms.** Broker CPU at 80% for 10,000 events a second; produce request rates in the tens of thousands.

    ??? success "Root cause and fix"
        **Root cause.** At a steady rate, no linger meant about one record per request. Measured: 66,000–69,000 requests and 5.8–6.0 s of broker CPU for 10 s of traffic, against 920–953 requests and 0.24–0.27 s with 20 ms.

        **Fix.** The throughput profile for event producers; Kafka 4's default of 5 ms for the rest.

        From [chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#7-anti-patterns-pitfalls).

??? bug "210 · a commit per event (chapter 69)"
    **Context.** The analytics consumer wrote each order event to PostgreSQL in its own transaction.

    **Symptoms.** Lag grew every dinner peak and took hours to clear; adding consumers didn't help past the partition count.

    ??? success "Root cause and fix"
        **Root cause.** The database commit per record set the pace. Measured: 1,384–1,554 rows a second per record; 29,000–59,000 batched by poll.

        **Fix.** A batch listener, one transaction per poll, `reWriteBatchedInserts=true`; at-least-once with an idempotent insert.

        From [chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#7-anti-patterns-pitfalls).

??? bug "211 · twelve consumers, six partitions (chapter 69)"
    **Context.** To catch up on lag, the dispatch consumer was scaled from 6 to 12 copies.

    **Symptoms.** Lag didn't move; six copies reported no assigned partitions.

    ??? success "Root cause and fix"
        **Root cause.** A group's parallelism is the partition count. Measured: 12 consumers on 6 partitions gave the same 4,800–4,900 records a second as 6, with 6 idle.

        **Fix.** Key-ordered concurrency inside each consumer (measured 16,600–16,800 a second from one), and 24 partitions for the next topic.

        From [chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#7-anti-patterns-pitfalls).

??? bug "212 · the template that bought a cluster (chapter 70)"
    **Context.** Every service started from a template requesting 1 CPU and 2 GiB, and teams raised them "to be safe".

    **Symptoms.** The cluster grew to 32 nodes running at a few percent utilisation; costs grew faster than orders.

    ??? success "Root cause and fix"
        **Root cause.** Requests were never compared with usage. Measured: services used 3–10% of their CPU and 5–21% of their memory requests; right-sized, the fleet fitted on 8–9 nodes instead of 32.

        **Fix.** VPA in recommendation mode, a monthly report per team, requests in Helm values reviewed like code.

        From [chapter 70](../performance-scalability/ch70-finops.md#7-anti-patterns-pitfalls).

??? bug "213 · right-sized into a latency incident (chapter 70)"
    **Context.** A cost initiative set CPU requests and limits to VPA's target on menu-service.

    **Symptoms.** Dinner-peak p99 went from 5 ms to around 100 ms; no errors, no saturation on the nodes.

    ??? success "Root cause and fix"
        **Root cause.** CFS throttling at the limit. Measured: a 0.2 CPU limit (the p90 plus 15%) gave a p99 of 78–204 ms; the same request without a limit, 4.6–5.3 ms.

        **Fix.** Requests from the recommendation, no CPU limit.

        From [chapter 70](../performance-scalability/ch70-finops.md#7-anti-patterns-pitfalls).

??? bug "214 · the cheapest minute of the year (chapter 70)"
    **Context.** To cut costs before a sale, rider-tracking moved entirely to spot.

    **Symptoms.** During the sale, the spot pool was reclaimed; tracking was down for over a minute and slow for another.

    ??? success "Root cause and fix"
        **Root cause.** No on-demand base. Measured: all-spot served 0–1% of requests while reclaimed; 3 on-demand + 3 spot served 55–61%, overloaded.

        **Fix.** An on-demand base sized to carry the peak alone, spot spread across instance types, and a concurrency limit to shed load cleanly.

        From [chapter 70](../performance-scalability/ch70-finops.md#7-anti-patterns-pitfalls).


### Governance & Organization

??? bug "215 · the endpoint removed by mailing list (chapter 71)"
    **Context.** payment-service deprecated `GET /payment-methods`, announced the removal by email to the teams listed as consumers, and removed it after three months.

    **Symptoms.** The support console's payment screen broke the next morning.

    ??? success "Root cause and fix"
        **Root cause.** The consumer list was declared by hand and incomplete. Measured: of four applications still calling the deprecated operation, two weren't declared in the catalog, and both called with plain HTTP, so they never saw a warning.

        **Fix.** Sunset by usage: the gateway logs callers of deprecated operations by client identity, and an operation is removed only when the log is empty.

        From [chapter 71](../governance-org/ch71-api-governance.md#7-anti-patterns-pitfalls).

??? bug "216 · the linter everyone ignored (chapter 71)"
    **Context.** The platform team enabled Spectral's recommended ruleset as a blocking check across all services.

    **Symptoms.** Builds failed everywhere on the first day; within a week the check was made optional, and nobody looked at it again.

    ??? success "Root cause and fix"
        **Root cause.** A blocking check on existing APIs with no baseline, and rules that checked completeness rather than design. Measured: errors would have stopped 4 of 6 documents, and the design problems were caught by house rules or needed a reviewer, never by the recommended rules.

        **Fix.** House rules with reasons, a baseline per legacy API, errors blocking only new findings.

        From [chapter 71](../governance-org/ch71-api-governance.md#7-anti-patterns-pitfalls).

??? bug "217 · the API that passed every check (chapter 71)"
    **Context.** promotions-service's API linted clean and went live.

    **Symptoms.** Mobile clients showed "success" for invalid coupon codes; discounts were applied as rupees where the backend meant percent.

    ??? success "Root cause and fix"
        **Root cause.** Errors returned as 200 with `{"ok": false}`, and a bare number for the discount. Measured: no rule caught any of promotions-service's three problems.

        **Fix.** A review checklist for new APIs (status modelling, units, idempotency, sensitive data), and a `Money`/`Percentage` type in the shared schemas.

        From [chapter 71](../governance-org/ch71-api-governance.md#7-anti-patterns-pitfalls).

??? bug "218 · the template that aged in place (chapter 72)"
    **Context.** FoodFleet's first service template was used for forty services, then improved for new services only.

    **Symptoms.** A year later, the oldest services had CPU limits, a single health endpoint, no PDB, and nobody knew which ones.

    ??? success "Root cause and fix"
        **Root cause.** No update path and no checks. Measured: services from the previous template passed 4 of 48 checks, below hand-written ones (20 of 48).

        **Fix.** Template versions recorded per service, updates by three-way merge as pull requests (4 → 40 of 48), and scorecards.

        From [chapter 72](../governance-org/ch72-platform-engineering.md#7-anti-patterns-pitfalls).

??? bug "219 · the update that overwrote a team's fix (chapter 72)"
    **Context.** To roll out new defaults, the platform team regenerated services from the latest template and pushed the result.

    **Symptoms.** invoice-service started getting OOMKilled: its memory had been raised to 1 GiB months earlier for a reason.

    ??? success "Root cause and fix"
        **Root cause.** Regeneration discards the team's changes. Measured: a three-way merge kept the team's changes in 2 files and raised conflicts in 4, including exactly this memory setting.

        **Fix.** Merge, never regenerate; conflicts go to the team as a pull request.

        From [chapter 72](../governance-org/ch72-platform-engineering.md#7-anti-patterns-pitfalls).

??? bug "220 · green scorecard, broken deploy (chapter 72)"
    **Context.** zone-planner's scorecard showed 10 of 12 checks, including probes.

    **Symptoms.** Its deployment failed at the API server on the next release.

    ??? success "Root cause and fix"
        **Root cause.** Text checks confirm decisions, not validity. Measured: the probe check passed while a server-side dry run rejected the missing port.

        **Fix.** A server-side dry run (or schema validation) in CI beside the conformance checks.

        From [chapter 72](../governance-org/ch72-platform-engineering.md#7-anti-patterns-pitfalls).

??? bug "221 · the log that rewrote history (chapter 73)"
    **Context.** FoodFleet's ADRs were numbered by handbook chapter and fixed up in place as later chapters changed things.

    **Symptoms.** An engineer tracing why gRPC load balancing went through the mesh found ADR-023 citing ADR-057, which was decided 14 months later. Eleven records' dates didn't fit their order.

    ??? success "Root cause and fix"
        **Root cause.** Records edited after acceptance, and numbers assigned by topic rather than by time. Measured: 12 of 72 records failed the log checks.

        **Fix.** Lint the log in CI; amend or supersede instead of editing; number records in the order they're decided.

        From [chapter 73](../governance-org/ch73-adrs-fitness-functions.md#7-anti-patterns-pitfalls).

??? bug "222 · the budget that was never close (chapter 73)"
    **Context.** order-service's latency function checked p99 against the ADR's 20 ms hop budget.

    **Symptoms.** A release that quadrupled p99, from 5 ms to about 20, passed the function and shipped; checkout slowed down at the next peak.

    ??? success "Root cause and fix"
        **Root cause.** A fixed budget with fourfold headroom isn't a regression check. Measured: a fourfold regression passed 9 of 12 single runs and 4 of 4 best-of-threes.

        **Fix.** A ratchet against a recent baseline, +50% best of three, beside the absolute budget.

        From [chapter 73](../governance-org/ch73-adrs-fitness-functions.md#7-anti-patterns-pitfalls).

??? bug "223 · the flaky check that got switched off (chapter 73)"
    **Context.** A team tightened the latency function to +10% over baseline, one run per build.

    **Symptoms.** Builds failed on unchanged code; within a month the check was marked "allowed to fail".

    ??? success "Root cause and fix"
        **Root cause.** One run's p99 on shared machines is noisy. Measured: 3–9 false alarms in 12 at +10%; single runs as bad as 104 ms with nothing changed.

        **Fix.** Best of three, a +50% threshold, and microbenchmarks for regressions smaller than that.

        From [chapter 73](../governance-org/ch73-adrs-fitness-functions.md#7-anti-patterns-pitfalls).

??? bug "224 · the hub extracted first (chapter 74)"
    **Context.** The grocery team started with order, the busiest and most visible context.

    **Symptoms.** Within a quarter, order-service called five services and was called by four; every release needed three others to be released with it.

    ??? success "Root cause and fix"
        **Root cause.** Order was the hub of a nine-context cycle. Measured: extracting it among the first three created 1,083 remote dependencies, against 340 for an order that freed as many changes.

        **Fix.** Break the cycle inside the monolith first; extract by value per remote dependency.

        From [chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#7-anti-patterns-pitfalls).

??? bug "225 · the easy wins that changed nothing (chapter 74)"
    **Context.** To show progress, the team extracted the smallest, least-coupled contexts: rating, inventory, content.

    **Symptoms.** Three services in production, and lead time unchanged.

    ??? success "Root cause and fix"
        **Root cause.** They rarely changed. Measured: cheapest-first orders freed 5% of changes after three extractions.

        **Fix.** Measure what each extraction buys (independently shippable changes) as well as what it costs.

        From [chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#7-anti-patterns-pitfalls).

??? bug "226 · the shared library that needed the services (chapter 74)"
    **Context.** The common platform module became a shared library for the new services.

    **Symptoms.** The library couldn't be built without order and payment classes; every service ended up depending on them.

    ??? success "Root cause and fix"
        **Root cause.** The platform imported business contexts. Measured: 60 platform-to-service edges.

        **Fix.** Invert those dependencies (interfaces in the platform, implementations in the contexts) before the first extraction, and add a fitness function that keeps them out.

        From [chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#7-anti-patterns-pitfalls).

??? bug "227 · the report that saw every chain (chapter 75)"
    **Context.** A support tool queried the orders table to investigate a chain's complaint.

    **Symptoms.** The export contained orders from every chain on the platform.

    ??? success "Root cause and fix"
        **Root cause.** The tool connected as the table's owner; RLS was enabled but not forced. Measured: as owner, a forgotten predicate returned all 20 tenants' rows.

        **Fix.** `FORCE ROW LEVEL SECURITY` on every tenant table; tools use the application role, and break-glass access is audited.

        From [chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#7-anti-patterns-pitfalls).

??? bug "228 · yesterday's tenant on today's connection (chapter 75)"
    **Context.** A new endpoint for loyalty points forgot to set the tenant; the code used `SET app.tenant_id` per session.

    **Symptoms.** Customers of one chain occasionally saw another chain's offers.

    ??? success "Root cause and fix"
        **Root cause.** The pooled connection still carried the previous request's tenant. Measured: 177 of 193 forgotten requests read another tenant's rows.

        **Fix.** `TenantScope` with `SET LOCAL` in every data access path; a policy with `NULLIF`; an alert on tenant-less transactions.

        From [chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#7-anti-patterns-pitfalls).

??? bug "229 · forgotten, except in the backups (chapter 75)"
    **Context.** A customer exercised their right to erasure; support deleted their rows and closed the ticket.

    **Symptoms.** A restore test three weeks later brought the customer's data back.

    ??? success "Root cause and fix"
        **Root cause.** DELETE doesn't reach WAL archives or backups. Measured: after DELETE and VACUUM FULL, the email was still in the WAL twice and in the backup.

        **Fix.** Crypto-shredding for personal fields, with keys in a KMS; documented backup retention.

        From [chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#7-anti-patterns-pitfalls).

??? bug "230 · the checkout API that moved to Lambda (chapter 76)"
    **Context.** To "go serverless", a team moved a steady, busy pricing API to functions.

    **Symptoms.** The monthly bill for that service rose severalfold, with no traffic change.

    ??? success "Root cause and fix"
        **Root cause.** Sustained high rates are where per-request pricing loses. Measured: a light call cost 8× a call on a 70%-busy container, and two containers became cheaper above 106 requests a second.

        **Fix.** Keep steady APIs on containers; use functions for the events around them.

        From [chapter 76](../governance-org/ch76-serverless-microservices.md#7-anti-patterns-pitfalls).

??? bug "231 · the partner webhook that timed out (chapter 76)"
    **Context.** A partner's webhook client used a 150 ms timeout; FoodFleet's receiver was a Java function.

    **Symptoms.** A fraction of deliveries failed and were retried, mostly at quiet hours.

    ??? success "Root cause and fix"
        **Root cause.** Sparse traffic plus a short keep-alive meant frequent cold starts. Measured: 25% cold at a 1-minute keep-alive, with p99 of 137–173 ms for the JVM's part alone.

        **Fix.** An AOT cache with C1 and Serial GC, acknowledge-then-process (return 202 and queue the work), and provisioned instances if the objective still isn't met.

        From [chapter 76](../governance-org/ch76-serverless-microservices.md#7-anti-patterns-pitfalls).

??? bug "232 · the AOT cache that never got built (chapter 76)"
    **Context.** A team added `-XX:AOTCacheOutput` to their function's build.

    **Symptoms.** The cache never appeared, and cold starts didn't change.

    ??? success "Root cause and fix"
        **Root cause.** The training run had the application on a class directory, which writing a cache refuses ("Cannot have non-empty directory in paths"); and the process was killed instead of exiting.

        **Fix.** Train from the packaged JAR, exit normally, and fail the build if the cache is missing.

        From [chapter 76](../governance-org/ch76-serverless-microservices.md#7-anti-patterns-pitfalls).

??? bug "233 · the 30-second order summary (chapter 77)"
    **Context.** Checkout called a model for an order summary with the SDK's default 30 s timeout.

    **Symptoms.** During a provider slowdown, a share of checkouts hung for exactly 30 s.

    ??? success "Root cause and fix"
        **Root cause.** Stalled streams wait out a total deadline. Measured: stalled calls took 30 s and failed; with first-token and idle deadlines, p99 was 5.1–5.3 s and stalls were retried.

        **Fix.** The gateway's stream-aware deadlines; the summary isn't on checkout's critical path at all.

        From [chapter 77](../governance-org/ch77-ai-native-microservices.md#7-anti-patterns-pitfalls).

??? bug "234 · the backfill that silenced support (chapter 77)"
    **Context.** A one-off job regenerated 40,000 menu descriptions using the same provider account as the support assistant.

    **Symptoms.** Support chats failed for an hour with "something went wrong".

    ??? success "Root cause and fix"
        **Root cause.** No per-feature limit. Measured: with a batch at 16 concurrent against a limit of 8, only 19–32 of 60 chats succeeded.

        **Fix.** Per-feature concurrency limits in the gateway, batch below interactive; a separate account or provisioned capacity for large backfills.

        From [chapter 77](../governance-org/ch77-ai-native-microservices.md#7-anti-patterns-pitfalls).

??? bug "235 · the review that read someone else's order (chapter 77)"
    **Context.** The support assistant had a tool `get_order_status(order_id, customer_id)`.

    **Symptoms.** A customer saw another customer's order status after the assistant summarised a restaurant's reviews.

    ??? success "Root cause and fix"
        **Root cause.** A review contained instructions; the model followed them; the tool trusted its arguments. Measured: 30 of 30 injected calls returned the other customer's order.

        **Fix.** Identity from the token; no customer parameter; object-level authorization; injection tests in CI.

        From [chapter 77](../governance-org/ch77-ai-native-microservices.md#7-anti-patterns-pitfalls).

