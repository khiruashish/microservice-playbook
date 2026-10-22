### Architecture & Patterns


**[9. Hexagonal, Clean & Onion Architecture](../architecture-patterns/ch09-hexagonal-clean-architecture.md)**

??? question "Hexagonal vs onion vs clean architecture?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    Same core idea (inward-pointing dependencies, framework-free core) drawn differently. Hexagonal emphasises the symmetry of inbound and outbound adapters; onion emphasises concentric layers inside the core; clean architecture adds use cases as explicit objects and names the dependency rule. Most teams mix the vocabulary.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q3-hexagonal-vs-onion-vs-clean-architecture-l3-sap-goldman-sachs)

??? question "How does this differ from controller–service–repository layering?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    In layering, the service depends on the persistence layer. In hexagonal, the core defines the repository interface and persistence depends on the core (dependency inversion). That lets the core run with a fake, keeps persistence types out of business rules, and supports several entry points on the same use cases.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q4-how-does-this-differ-from-controllerservicerepository-layering-l2l3-flipkart-amazon)

??? question "Where do you put `@Transactional` in a hexagonal service?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    Not on domain code. Options: a transactional decorator around the application service in the composition root, `@Transactional` on the driving adapter, or short transactions inside repository adapters. If the use case calls a remote system, never hold a DB transaction across the call: record intent, call, record outcome, reconcile the gap.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q5-where-do-you-put-transactional-in-a-hexagonal-service-l3-jp-morgan-goldman-sachs)

??? question "How do you test a hexagonal service?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Domain unit tests; use-case tests through driving ports with fake driven adapters (in-memory repository, recording gateway); adapter tests against real infrastructure (embedded DB or Testcontainers, `@WebMvcTest`, translator tests); a few assembled-service tests. Keep fakes honest with **contract tests** that both the fake and the real adapter must pass.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q6-how-do-you-test-a-hexagonal-service-l3-amazon-flipkart)

??? question "Should JPA entities be your domain model?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    For core domains, usually not: JPA requires no-arg constructors, mutable fields and proxies, and lazy loading leaks persistence concerns into rules. Map between a clean domain model and persistence in the adapter, or use Spring Data JDBC, which respects aggregate boundaries. For simple CRUD services, JPA entities as the model are fine.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q7-should-jpa-entities-be-your-domain-model-l3-goldman-sachs-sap)

??? question "What's an anti-corruption layer in a hexagonal service?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    The translator inside a driven adapter that converts an external model (a payment provider's statuses and error codes) into the core's model. It's where interpretation decisions live, with fail-safe defaults for unknown values.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q8-whats-an-anti-corruption-layer-in-a-hexagonal-service-l3-jp-morgan-sap)

??? question "Isn't this over-engineering for microservices, which are small anyway?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    For thin CRUD services, yes. For services with real rules and several integrations (payments, pricing, dispatch), the core is where the value is, and keeping it independent pays back in testability and change safety. Decide per service based on rule complexity and number of integrations, not as a company-wide mandate.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q9-isnt-this-over-engineering-for-microservices-which-are-small-anyway-l3l4-flipkart-amazon)

??? question "How do you enforce the architecture over time?"
    <span class="badge badge-l3">L3</span> any Spring shop

    Package structure that names the parts, ArchUnit rules in the build (`onionArchitecture()`, framework-free core, driving adapters use ports only), code review checklists, and templates for new services. Rules in CI catch violations on day one rather than in a review six months later.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q10-how-do-you-enforce-the-architecture-over-time-l3-any-spring-shop)

??? question "How would you add a new payment provider?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A new driven adapter implementing `PaymentGateway`: an HTTP client in the provider's vocabulary, a translator with tests for every status and error code (unknown means "not approved"), idempotency keys per call, error mapping to the port's exceptions. Run it against the gateway contract test and the provider's sandbox, then route a small percentage of traffic to it by configuration.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q11-how-would-you-add-a-new-payment-provider-l3l4-goldman-sachs-flipkart)

??? question "Hexagonal or vertical slice architecture?"
    <span class="badge badge-l3">L3</span> startups · consulting firms

    They answer different questions. Vertical slices organise code by feature, minimising coupling between features; hexagonal organises the boundary between logic and technology. Many teams slice by feature and use ports where a slice touches infrastructure.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q12-hexagonal-or-vertical-slice-architecture-l3-startups-consulting-firms)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "API gateway vs load balancer vs service mesh?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A load balancer distributes connections (L4/L7) across instances. An API gateway is an application-level edge for **north–south** traffic with API policies (auth, quotas, routing by path and version). A service mesh handles **east–west** traffic between services (mTLS, retries, telemetry) through sidecars or ambient proxies. They are complementary.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q2-api-gateway-vs-load-balancer-vs-service-mesh-l2l3-amazon-flipkart)

??? question "What is the BFF pattern and when would you use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Swiggy-/Zomato-style product companies

    A backend built for one client experience and owned by that client's team, exposing screen-shaped endpoints that aggregate and format data from domain services. Use it when clients differ significantly or when chatty client-to-service calls hurt latency and coupling. It must not hold domain rules.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q3-what-is-the-bff-pattern-and-when-would-you-use-it-l2l3-flipkart-swiggy-zomato-style-product-companies)

??? question "Should the gateway aggregate responses?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    Usually not. Aggregation is client-specific, so it belongs in a BFF owned by the client team. A shared gateway that aggregates becomes a monolith that every team must change. Keep the gateway generic.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q4-should-the-gateway-aggregate-responses-l3-amazon-sap)

??? question "How do you implement rate limiting, and what are the pitfalls?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs · Flipkart

    Token bucket per key (user, API key or IP): capacity for bursts, steady refill, 429 with `Retry-After` when empty. Pitfalls: in-memory limits are per instance (use Redis or a distributed bucket for exact quotas); keying by IP punishes users behind carrier NAT; unbounded per-key state is a memory attack; limits must exist before you need them.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q5-how-do-you-implement-rate-limiting-and-what-are-the-pitfalls-l3-amazon-goldman-sachs-flipkart)

??? question "Should the gateway retry failed requests?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    Only idempotent ones (GET, or POST with an idempotency key the backend honours), with backoff and a small retry count, and never on top of client and service retries without a budget, or retries multiply. Pair with circuit breakers and fast fallbacks for non-critical reads.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q6-should-the-gateway-retry-failed-requests-l3-jp-morgan-amazon)

??? question "How does the gateway handle authentication?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    It validates tokens (signature, expiry, audience) and rejects unauthenticated calls early, then forwards the token (token relay) or a derived identity to services. Services still check authorization on their own resources. Client-supplied identity headers are stripped. [Chapter 36](../security/ch36-oauth2-oidc-jwt.md) covers the flows.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q7-how-does-the-gateway-handle-authentication-l3-goldman-sachs-sap)

??? question "What happens when the gateway goes down?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Everything public goes down, which is why it must be stateless, horizontally scaled across zones, protected by disruption budgets, deployed with canaries, and kept simple (no business logic, few dependencies). Internal traffic must not route through it, so services keep working with each other during an edge incident.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q8-what-happens-when-the-gateway-goes-down-l3l4-amazon-flipkart)

??? question "Spring Cloud Gateway MVC or WebFlux?"
    <span class="badge badge-l3">L3</span> any Spring shop

    Web MVC fits teams on the servlet stack and virtual threads, with simple blocking custom filters. WebFlux suits streaming, WebSockets and very high connection counts, at the cost of reactive programming. Both offer the same core routing and filter concepts.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q9-spring-cloud-gateway-mvc-or-webflux-l3-any-spring-shop)

??? question "How do you version APIs at the edge?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Public paths are stable; versions are carried in a header or path segment and routed by the gateway or handled by the service's own API versioning (chapter 8). BFF endpoints are often versioned by path to match app releases, because old app versions live for months.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q10-how-do-you-version-apis-at-the-edge-l2l3-sap-cognizant)

??? question "GraphQL instead of BFFs?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · startups

    GraphQL lets each client ask for exactly the fields it needs from one schema, which removes much of the shaping work BFFs do. It still needs per-field authorization, query cost limits and careful resolver fan-out. Many organisations combine them: a GraphQL layer per client experience, owned by that client team.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q11-graphql-instead-of-bffs-l3l4-flipkart-startups)


**[11. Service Discovery Patterns](../architecture-patterns/ch11-service-discovery-patterns.md)**

??? question "Client-side vs server-side discovery?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Client-side: the caller queries the registry and chooses an instance itself (Eureka + Spring Cloud LoadBalancer, gRPC over a headless Service): rich policies, a library per language, client caches. Server-side: the caller uses a stable address and the platform or a proxy chooses (Kubernetes ClusterIP, cloud load balancers, meshes): language-neutral, simpler clients.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q2-client-side-vs-server-side-discovery-l2l3-amazon-flipkart)

??? question "How does service discovery work in Kubernetes?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · JP Morgan

    A Service selects pods by label; ready pods' IPs go into EndpointSlices; kube-proxy on each node routes connections to the Service's ClusterIP to a ready pod; CoreDNS resolves the Service name to the ClusterIP (or to all pod IPs for a headless Service). Readiness probes decide membership.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q4-how-does-service-discovery-work-in-kubernetes-l2l3-amazon-flipkart-jp-morgan)

??? question "Do you need Eureka on Kubernetes?"
    <span class="badge badge-l3">L3</span> any Spring shop

    Usually not. Kubernetes already provides registration, health-based membership and DNS. Adding Eureka adds another stale cache and another system to run. Use Spring Cloud Kubernetes or a mesh only if you need client-side policies.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q5-do-you-need-eureka-on-kubernetes-l3-any-spring-shop)

??? question "An instance crashes. How long until callers stop sending it requests?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    It depends on every cache between the failure and the caller. Eureka defaults can take several minutes (lease, eviction, server cache, client fetch, load-balancer cache); Kubernetes removes a crashed pod in seconds but a hung one only after readiness probes fail (30 s with defaults). Mitigate with short timeouts and a retry on another instance for idempotent calls.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q6-an-instance-crashes-how-long-until-callers-stop-sending-it-requests-l3-amazon-goldman-sachs)

??? question "Why does gRPC load-balance badly behind a Kubernetes Service?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    kube-proxy balances connections, and gRPC multiplexes all requests over one long-lived HTTP/2 connection, so each client sticks to one pod. Use a headless Service with client-side round robin, a mesh that balances per request, or limit connection age.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q7-why-does-grpc-load-balance-badly-behind-a-kubernetes-service-l3-flipkart-amazon)

??? question "What's Eureka's self-preservation mode?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    When renewals drop below a threshold (85% of expected by default), the Eureka server assumes a network problem and stops evicting instances, protecting against mass eviction during partitions. The trade-off: dead instances remain listed until renewals recover, so clients need timeouts and retries.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q8-whats-eurekas-self-preservation-mode-l3-jp-morgan-sap)

??? question "How do you avoid errors during rolling deployments?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Readiness gates new pods until warm; on termination, a `preStop` pause lets endpoint removal propagate while the pod still serves, then graceful shutdown finishes in-flight requests; `maxUnavailable` and PodDisruptionBudgets keep enough capacity; callers retry idempotent requests elsewhere.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q10-how-do-you-avoid-errors-during-rolling-deployments-l3-flipkart-goldman-sachs)

??? question "What does Spring Cloud LoadBalancer do with HTTP service clients?"
    <span class="badge badge-l3">L3</span> any Spring shop

    Since Spring Cloud 2025.1, an HTTP service group without a base URL (or with an `lb://` URL) is load-balanced automatically, using the group name as the service id. Instances come from the `DiscoveryClient`, cached (35 s by default), and are chosen round-robin unless another configuration is set.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q11-what-does-spring-cloud-loadbalancer-do-with-http-service-clients-l3-any-spring-shop)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

??? question "Why is a shared database an anti-pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    The schema becomes an unversioned contract for every service that touches it: renames break others, locks and heavy queries interfere, nobody owns the rules for the data, and services can't be deployed or scaled independently. It's the tightest coupling a system can have.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q2-why-is-a-shared-database-an-anti-pattern-l2l3-amazon-flipkart)

??? question "How do you join data across services?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    API composition for display queries: the owner of the list is queried first, then other owners are called in batch, in parallel, and joined in memory. For queries that filter or sort across owners or are heavy, build a CQRS read model from events. For analytics, a warehouse fed by events or CDC.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q3-how-do-you-join-data-across-services-l2l3-flipkart-sap)

??? question "What replaces foreign keys between services?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Ids stored as values; validation through the owner's API when the reference is created; events to react when the referenced entity changes or is deleted; and reconciliation jobs for critical relationships. Often you also store a snapshot of the referenced data at the business moment.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q4-what-replaces-foreign-keys-between-services-l3-goldman-sachs-amazon)

??? question "How do you keep data consistent across services without distributed transactions?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    Sagas: a sequence of local transactions with compensating actions, driven by events or an orchestrator, with reliable publishing through an outbox. Accept eventual consistency and design the user experience around it (chapters 14 and 15).

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q5-how-do-you-keep-data-consistent-across-services-without-distributed-transactions-l3-jp-morgan-amazon)

??? question "How would you split a shared database in a running system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP · Flipkart

    Map table ownership; audit real statements per service (one DB user each); remove foreign writes first, then foreign reads (APIs, local copies, composition); drop cross-domain foreign keys; move tables into per-service schemas with grants; separate instances where needed. Each step is small and measurable.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q6-how-would-you-split-a-shared-database-in-a-running-system-l3l4-goldman-sachs-sap-flipkart)

??? question "What are the problems with API composition?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Latency and availability (every source is on the path), N+1 calls without batch endpoints, no efficient filtering or sorting across sources, and inconsistent snapshots between sources. Mitigate with batching, parallel calls, budgets, partial results and caching; switch to CQRS when queries cross owners.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q7-what-are-the-problems-with-api-composition-l3-amazon-flipkart)

??? question "Schema per service or database per service?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    Schema per service with one user each gives enforced ownership at low cost. Separate databases when a service needs different technology, failure isolation, compliance separation, independent scaling or its own upgrade schedule.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q8-schema-per-service-or-database-per-service-l3-jp-morgan-sap)

??? question "Is it OK for a service to keep a copy of another service's data?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Yes, if the copy is read-only and its freshness is deliberate: a snapshot at a business moment (the price when ordered) or a replica kept current from the owner's events. Two services writing the same data is the problem, not copies.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q9-is-it-ok-for-a-service-to-keep-a-copy-of-another-services-data-l3-flipkart-goldman-sachs)

??? question "How do you handle reporting across microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · Accenture · Amazon

    Not on operational databases. Stream changes through events or CDC into a warehouse or lakehouse, model the data there, and give analysts access only to that store.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q10-how-do-you-handle-reporting-across-microservices-l2l3-cognizant-accenture-amazon)

??? question "How do you stop teams from sneaking back to cross-service queries?"
    <span class="badge badge-l3">L3</span> any company mid-migration

    Database users per service with grants limited to their schema; an ownership file checked in CI; the statement audit run regularly against production statistics; and platform templates that provision one schema and user per service by default.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q12-how-do-you-stop-teams-from-sneaking-back-to-cross-service-queries-l3-any-company-mid-migration)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "What is CQRS?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Separating the model that handles commands (enforcing rules on writes) from the model(s) that answer queries. Read models are shaped per query and can live in different stores, kept up to date from the write side, usually through events and therefore eventually consistent.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q1-what-is-cqrs-l2l3-cognizant-sap-flipkart)

??? question "What is event sourcing?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Persisting every state change as an immutable event in an append-only store; current state is derived by replaying an aggregate's events (or a snapshot plus later events). It gives full history, auditability, temporal queries and the ability to build new read models from past data.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q2-what-is-event-sourcing-l3-goldman-sachs-jp-morgan-amazon)

??? question "Are CQRS and event sourcing the same thing?"
    <span class="badge badge-l3">L3</span> any

    No. CQRS separates read and write models and works with ordinary tables. Event sourcing is a storage approach; it almost always needs CQRS because querying by replaying events is impractical, but CQRS doesn't need event sourcing.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q3-are-cqrs-and-event-sourcing-the-same-thing-l3-any)

??? question "How do you handle concurrent commands on the same aggregate?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Optimistic concurrency: append with the expected stream version; if another writer appended first, the store rejects it. Reload, decide again (rules re-checked against the newer state), and retry a bounded number of times.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q4-how-do-you-handle-concurrent-commands-on-the-same-aggregate-l3-amazon-goldman-sachs)

??? question "How do read models stay up to date, and what about consistency?"
    <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    Projections subscribe to the event log from a checkpoint and update read models; checkpoint and read model are updated atomically, or handlers are idempotent. Read models are eventually consistent; measure lag, and for the user's own latest change return the command result or read from the write side.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q5-how-do-read-models-stay-up-to-date-and-what-about-consistency-l3-flipkart-jp-morgan)

??? question "What are snapshots and when do you need them?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    Cached aggregate state at a version, so loading replays only later events. Needed for long streams; they're an optimisation that can be deleted and rebuilt, and must be versioned with the code that reads them.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q6-what-are-snapshots-and-when-do-you-need-them-l3-amazon-sap)

??? question "When would you not use event sourcing?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    For CRUD-like data where nobody needs history, for teams without experience and without a clear benefit, and where simple audit tables suffice. It's a tool for ledgers, workflows and domains with disputes and many read views.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q9-when-would-you-not-use-event-sourcing-l3l4-flipkart-amazon)

??? question "How do you rebuild or add a read model?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Reset the read model and its checkpoint, replay the log from the start. For large logs, build the new version alongside the old one (blue/green), switch reads when it has caught up, then drop the old one.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q11-how-do-you-rebuild-or-add-a-read-model-l3-goldman-sachs-flipkart)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

??? question "What is a saga?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A sequence of local transactions across services, where each step has a compensating action. If a step fails, the compensations of completed steps run in reverse order. It provides eventual atomicity without distributed locks.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q1-what-is-a-saga-l2l3-cognizant-sap-flipkart)

??? question "Why not use two-phase commit across microservices?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    It needs every participant and the coordinator available to finish, holds locks across network calls, requires XA-capable resources (brokers and HTTP APIs aren't), and couples services to a shared transaction manager. External systems like payment providers never participate.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q2-why-not-use-two-phase-commit-across-microservices-l3-goldman-sachs-amazon)

??? question "Orchestration vs choreography?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · SAP

    Orchestration: a central coordinator tells participants what to do and tracks state; easier to understand, monitor and change. Choreography: services react to each other's events; looser coupling, but the flow is implicit and compensation is spread out. Orchestrate flows with many steps or money; choreograph simple reactions.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q3-orchestration-vs-choreography-l3-amazon-flipkart-sap)

??? question "Why must saga participants be idempotent?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Retries after timeouts and orchestrator recovery after crashes deliver the same request more than once; replies can be lost after the work was done. Without idempotency, retries double-charge or double-book. And a repeated request must return the original outcome, not be re-evaluated.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q5-why-must-saga-participants-be-idempotent-l3-amazon-jp-morgan)

??? question "What happens if the orchestrator crashes mid-saga?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Its progress is in a durable saga log; another instance finds unfinished sagas and resumes from the last recorded step, re-sending the in-flight request (at-least-once). Idempotent participants make the repeat harmless. The log write and outgoing message use an outbox so they can't diverge.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q7-what-happens-if-the-orchestrator-crashes-mid-saga-l3-amazon-flipkart)

??? question "What if a compensation fails?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Compensations are designed never to fail for business reasons; technical failures are retried until they succeed (they're idempotent). If they keep failing, the saga is marked stuck and escalated, with alerting and a manual procedure, plus reconciliation against the external system.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q8-what-if-a-compensation-fails-l3l4-jp-morgan-goldman-sachs)

??? question "Would you use a workflow engine like Temporal?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · startups

    Yes for long-running flows with timers, human steps and many branches: durable execution removes most hand-written state handling. For a short, latency-sensitive saga a small orchestrator with a saga log may be simpler. Either way participants must still be idempotent.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q10-would-you-use-a-workflow-engine-like-temporal-l3l4-flipkart-startups)

??? question "How do you test sagas?"
    <span class="badge badge-l3">L3</span> any

    Unit-test the orchestrator against fake participants with scripted faults (rejections, timeouts, lost replies, crashes at every step), check invariants (money and stock add up) over randomized simulations, contract-test each participant's idempotency and compensations, and run chaos experiments in staging.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q11-how-do-you-test-sagas-l3-any)

??? question "How do you show a saga's progress to users?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> product companies

    Expose the semantic lock as a state ("confirming with restaurant"), push updates as the saga progresses (WebSocket/SSE), and design copy for compensation outcomes ("restaurant couldn't accept; your payment was released").

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q12-how-do-you-show-a-sagas-progress-to-users-l2l3-product-companies)


**[15. Transactional Outbox, Inbox & CDC](../architecture-patterns/ch15-outbox-inbox-cdc.md)**

??? question "What is the dual-write problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant · SAP

    Writing to two systems (a database and a broker) without a transaction covering both. A failure between the writes leaves them inconsistent: commit-then-publish loses events, publish-then-commit invents them.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q1-what-is-the-dual-write-problem-l2l3-flipkart-cognizant-sap)

??? question "How does the transactional outbox work?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    The event is inserted into an outbox table in the same local transaction as the business change. A relay reads the outbox (by polling or from the transaction log) and publishes to the broker, retrying until acknowledged, then marks or deletes the row.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q2-how-does-the-transactional-outbox-work-l3-amazon-flipkart-goldman-sachs)

??? question "Does the outbox give exactly-once delivery?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    No. It gives at-least-once: the relay can crash after publishing and before marking, or lose the broker's ack, and republish. Exactly-once *processing* needs an idempotent consumer, usually an inbox table keyed by message id.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q3-does-the-outbox-give-exactly-once-delivery-l3-amazon-jp-morgan)

??? question "Polling publisher vs CDC?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP · Goldman Sachs

    Polling is simple, in your code, works with any database, adds query load and latency of the poll interval. CDC (Debezium) reads the WAL: low latency, commit order, no table queries, but needs Kafka Connect, replication slots, monitoring of WAL retention and schema-change handling.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q4-polling-publisher-vs-cdc-l3l4-flipkart-sap-goldman-sachs)

??? question "Why `FOR UPDATE SKIP LOCKED`?"
    <span class="badge badge-l3">L3</span> JP Morgan · startups

    Several relay instances can poll the same table without blocking on each other or publishing the same row concurrently: each locks a batch and the others skip locked rows. It improves throughput but can reorder events across instances, so combine it with partitioning or a single active relay.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q6-why-for-update-skip-locked-l3-jp-morgan-startups)

??? question "What is an inbox and why must it be in the same transaction?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A table of processed message ids in the consumer's database. Insert the id and apply the effect in one transaction: a duplicate fails the unique key and is skipped. If they were separate, a crash between them would lose the effect or apply it twice.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q7-what-is-an-inbox-and-why-must-it-be-in-the-same-transaction-l3-amazon-flipkart)

??? question "How would you make a consumer idempotent without an inbox?"
    <span class="badge badge-l3">L3</span> any

    Design the effect to be idempotent: upserts by natural key, "set state to X" instead of "add N", version checks (apply only if event version > stored version). Inboxes are the general solution when effects aren't naturally idempotent, such as incrementing a balance.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q8-how-would-you-make-a-consumer-idempotent-without-an-inbox-l3-any)

??? question "What would you monitor?"
    <span class="badge badge-l3">L3</span> any

    Outbox backlog and age of the oldest unpublished row, relay errors and publish rate, end-to-end latency from created_at to consumption, replication slot lag (CDC), inbox duplicate rate, dead-letter topic volume.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q12-what-would-you-monitor-l3-any)


**[16. Strangler Fig, ACL & Monolith Migration](../architecture-patterns/ch16-strangler-fig-migration.md)**

??? question "What is the strangler fig pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Incrementally replacing a legacy system by routing one capability at a time from the old system to a new one through a facade, until the old code can be deleted. Each step is small, reversible and gets production feedback, unlike a big-bang rewrite.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q1-what-is-the-strangler-fig-pattern-l2l3-cognizant-sap-flipkart)

??? question "What is an anti-corruption layer and where does it live?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    A translation layer between a new model and a legacy (or external) one, so the new model isn't shaped by the old one's codes and quirks. It lives in the new service at its boundary with the legacy system, often in both directions (reading legacy data, calling legacy functions). Untranslatable data should be quarantined, not guessed.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q2-what-is-an-anti-corruption-layer-and-where-does-it-live-l3-sap-goldman-sachs)

??? question "Branch by abstraction vs feature branches?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Branch by abstraction makes a large change on trunk: add an interface, move callers onto it, add the new implementation behind it, switch with a toggle, delete the old one. Every step is releasable. A long-lived feature branch defers integration and conflicts to the end, the same problem a big-bang rewrite has.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q3-branch-by-abstraction-vs-feature-branches-l3-amazon-flipkart)

??? question "How do you know the new service behaves like the old one?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Shadow traffic: call both on real production requests, return the old answer, compare field by field, and fix or explicitly accept every difference (with documented tolerances). Contract and characterization tests help, but production traffic finds the cases nobody thought of.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q4-how-do-you-know-the-new-service-behaves-like-the-old-one-l3l4-goldman-sachs-jp-morgan)

??? question "Canary by request or by customer?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    By customer (or session, or tenant), using a stable hash, so a customer sees consistent behaviour and raising the percentage keeps existing cohorts in. Per-request splits are fine only for stateless, invisible changes.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q5-canary-by-request-or-by-customer-l3-flipkart-amazon)

??? question "Which capability would you extract first?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Cognizant · Flipkart

    One with a clear boundary, few dependencies, its own data, mostly reads or calculations (so it can be shadowed), frequent change (quick payback), and a team to own it. Not the core entity everything joins against.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q8-which-capability-would-you-extract-first-l3l4-sap-cognizant-flipkart)

??? question "Where should the routing logic live?"
    <span class="badge badge-l3">L3</span> any

    At the gateway for external calls, inside the monolith (behind an interface and a flag) for in-process calls, or in a service mesh for service-to-service traffic. Usually more than one, all driven by the same flag so cohorts agree.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q11-where-should-the-routing-logic-live-l3-any)

??? question "When is a migration done?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    When the old code, its data, the flags and the facade routes for that capability are deleted, not when the new service takes 100% of traffic. Set a deadline for deletion and track it.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q12-when-is-a-migration-done-l3l4-any)


**[17. Sidecar, Ambassador & Adapter](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)**

??? question "Ambassador vs adapter?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Both are sidecars. An ambassador is an outbound proxy representing a remote service to the app (credentials, retries, routing). An adapter presents the app or a legacy system to the outside world in a standard form (metrics format, API shape).

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q2-ambassador-vs-adapter-l2l3-sap-cognizant)

??? question "What are native sidecar containers in Kubernetes?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Init containers with `restartPolicy: Always`. They start before app containers (startup probes can gate the app), run for the pod's life, are restarted if they exit, and stop after the app containers. They fix start-order races, lost last logs and Jobs that never complete.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q3-what-are-native-sidecar-containers-in-kubernetes-l3-amazon-flipkart)

??? question "Library or sidecar for resilience in a Java service?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    A library when the logic needs business context (which operations are safe to retry, fallbacks) or the fleet is one language; a sidecar when it must be uniform across languages, owned by the platform, or changed without redeploying apps. Never both for the same concern, or retries multiply.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q4-library-or-sidecar-for-resilience-in-a-java-service-l3-goldman-sachs-jp-morgan)

??? question "What does an extra sidecar hop cost?"
    <span class="badge badge-l3">L3</span> Amazon

    Latency per call (fractions of a millisecond on loopback for a lightweight proxy; measure yours) and, more significantly, CPU and memory per pod, multiplied across the fleet, plus operational overhead (images, restarts, probes).

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q5-what-does-an-extra-sidecar-hop-cost-l3-amazon)

??? question "Why can a proxy retry a GET but not a POST?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    GETs are safe and idempotent; a POST may have taken effect even if the response was lost, so repeating it could duplicate the effect (a second charge). A POST can be retried only if the API guarantees idempotency, for example with an Idempotency-Key the server deduplicates on.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q6-why-can-a-proxy-retry-a-get-but-not-a-post-l2l3-flipkart-amazon)

??? question "How do service meshes relate to these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A sidecar mesh puts an ambassador-style proxy (usually Envoy) in every pod for all traffic: mTLS, retries, traffic splitting, telemetry. Ambient mode moves that out of the pod to per-node and per-namespace proxies to cut per-pod cost.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q7-how-do-service-meshes-relate-to-these-patterns-l3l4-goldman-sachs-flipkart)

??? question "What is Dapr?"
    <span class="badge badge-l3">L3</span> startups · SAP

    A sidecar runtime that exposes infrastructure building blocks (state stores, pub/sub, bindings, secrets, workflows) through HTTP/gRPC APIs, so apps in any language use one API and the sidecar talks to the actual backend.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q8-what-is-dapr-l3-startups-sap)

??? question "How should timeouts be set between app and ambassador?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    The app's timeout must exceed the ambassador's total budget (per-try timeout × attempts + backoff), otherwise the app gives up while the ambassador is still retrying, wasting work and possibly causing duplicate effects.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q9-how-should-timeouts-be-set-between-app-and-ambassador-l3-jp-morgan-amazon)

??? question "How would you ship logs from an app that only writes files?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    Share an emptyDir volume with a log-shipper sidecar that tails the file, keeps its position durably, handles rotation and only advances after the collector accepts a batch. Better long term: log to stdout and let a node-level agent collect.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q10-how-would-you-ship-logs-from-an-app-that-only-writes-files-l2l3-any)

??? question "What are the security considerations for sidecars?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Least privilege per container (secrets mounted only where needed), non-root and read-only filesystems, loopback binding for proxies holding credentials, image provenance and patching for every sidecar image, and network policy around anything that reaches external systems.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q11-what-are-the-security-considerations-for-sidecars-l3l4-goldman-sachs-jp-morgan)

??? question "When would you not use a sidecar?"
    <span class="badge badge-l3">L3</span> any

    When a library does the job in a single-language fleet, when the per-pod cost is significant relative to small pods, when the concern needs business context, or when a node-level agent (log collection, ambient mesh) does it once per node instead of once per pod.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q12-when-would-you-not-use-a-sidecar-l3-any)


**[18. Resilience Patterns](../architecture-patterns/ch18-resilience-patterns.md)**

??? question "What is a cascading failure and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Cognizant

    A failure (often slowness) in one dependency exhausting resources in its callers, which then fail their callers. Prevent it with timeouts on every call, bulkheads per dependency, bounded queues and load shedding, circuit breakers, and fallbacks.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q1-what-is-a-cascading-failure-and-how-do-you-prevent-it-l2l3-amazon-flipkart-cognizant)

??? question "When do retries make things worse?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    When the dependency is failing due to overload (retries add load), when several layers retry (multiplication), when retries aren't jittered (synchronized waves), and when operations aren't idempotent (duplicates). In the chapter's simulation, retries tripled load for no gain.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q3-when-do-retries-make-things-worse-l3-amazon-goldman-sachs)

??? question "What is a bulkhead, and how is it different from a rate limiter?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    A bulkhead caps concurrent calls to a dependency, isolating its slowness from the rest of the service. A rate limiter caps calls per time unit, for quotas or to protect a service from callers. Concurrency limits adapt to latency automatically; rate limits don't.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q4-what-is-a-bulkhead-and-how-is-it-different-from-a-rate-limiter-l2l3-flipkart-jp-morgan)

??? question "How do you choose a timeout value?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    From data: above the dependency's healthy high percentile (p99.9), below what your caller will wait, and consistent with retries so attempts × timeout + backoff fits the caller's budget. Budgets should shrink as calls go deeper.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q5-how-do-you-choose-a-timeout-value-l3-amazon-goldman-sachs)

??? question "Why are timeouts more important with virtual threads?"
    <span class="badge badge-l3">L3</span> any Java shop

    Virtual threads remove the thread pool's implicit concurrency limit, so a slow dependency can accumulate thousands of waiting requests and exhaust memory, connections or the dependency itself. Timeouts and explicit concurrency limits (`@ConcurrencyLimit`, semaphores) replace the limit the pool used to give.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q6-why-are-timeouts-more-important-with-virtual-threads-l3-any-java-shop)

??? question "What makes a good fallback?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · product companies

    Local and cheap (cache, last known value, static default), clearly marked as degraded to the user, agreed with product, and tested. Not another remote call, and not something that hides data correctness problems (never fall back to "payment succeeded").

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q7-what-makes-a-good-fallback-l2l3-flipkart-product-companies)

??? question "In what order do you compose these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Commonly fallback outermost, then bulkhead, retry, circuit breaker, timeout innermost (Resilience4j's Spring annotations apply Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(call)))))). Whether the breaker sees attempts or operations depends on whether it is inside or outside retry; choose deliberately.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q8-in-what-order-do-you-compose-these-patterns-l3l4-goldman-sachs-sap)

??? question "Hystrix is in maintenance. What do you use in Spring today?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Spring Framework 7's `@Retryable` and `@ConcurrencyLimit` for retries and concurrency limits; Resilience4j (directly or via Spring Cloud CircuitBreaker) for breakers, rate limiters and time limiters; client-level timeouts on HTTP service clients.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q9-hystrix-is-in-maintenance-what-do-you-use-in-spring-today-l2l3-cognizant-sap)

??? question "How do you know your resilience settings work?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Measure: dashboards of timeouts, retries, breaker states and fallbacks per dependency; load tests with injected latency; chaos experiments in staging and production (latency, not just errors); and simulations like this chapter's to reason about settings before an incident.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q10-how-do-you-know-your-resilience-settings-work-l3l4-amazon-flipkart)

??? question "What is load shedding?"
    <span class="badge badge-l3">L3</span> Amazon · Google-style rounds

    Rejecting work early (503, or dropping low-priority requests) when a service can't complete it in time, instead of queueing it. It keeps latency bounded for the work that is accepted and shortens recovery, because no backlog of abandoned requests is left.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q11-what-is-load-shedding-l3-amazon-google-style-rounds)


**[19. Event-Driven Architecture](../architecture-patterns/ch19-event-driven-architecture.md)**

??? question "Event notification vs event-carried state transfer?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Notification is small and keeps data with the owner, but consumers must call back, so the owner must be available and carries the load. ECST carries the state, so consumers work when the owner is down and never call it, at the cost of larger events, local copies, eventual consistency and schema coupling.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q3-event-notification-vs-event-carried-state-transfer-l3-flipkart-goldman-sachs)

??? question "How do you handle duplicates and out-of-order events?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Key by entity so order holds per entity within a partition; include an owner-assigned version and apply newest-wins (idempotent and order-insensitive); for effects that aren't state updates, record processed event ids in an inbox.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q4-how-do-you-handle-duplicates-and-out-of-order-events-l3-amazon-jp-morgan)

??? question "How does a new consumer get the current state?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    From a compacted topic (latest event per key), then it follows the live stream; or from a snapshot or bulk API plus the stream from a known position. Replaying a delete-retention topic is incomplete and slow.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q5-how-does-a-new-consumer-get-the-current-state-l3l4-flipkart-goldman-sachs)

??? question "What is log compaction?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A Kafka retention policy that keeps at least the most recent message for each key, discarding older ones, so the topic represents current state per key. A null-value message (tombstone) deletes a key.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q6-what-is-log-compaction-l3-amazon-flipkart)

??? question "How do you evolve event schemas?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · JP Morgan

    Treat events as contracts: add optional fields only, never rename or repurpose; enforce compatibility with a schema registry and consumer contract tests; publish breaking changes as a new event type or topic alongside the old one until consumers migrate.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q7-how-do-you-evolve-event-schemas-l3l4-sap-jp-morgan)

??? question "What are the downsides of event-driven architecture?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Eventual consistency, harder debugging and tracing, hidden dependencies (who consumes what?), duplicate and ordering handling, schema governance, data copies (including personal data), and operational load (brokers, lag, dead letters).

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q8-what-are-the-downsides-of-event-driven-architecture-l3-goldman-sachs-amazon)

??? question "When would you still call synchronously?"
    <span class="badge badge-l3">L3</span> any

    When the caller needs an answer now that must be authoritative (payment authorisation, a uniqueness check), when data changes faster than it's read, or when the data is sensitive or large and needed rarely. Then protect the call with timeouts and resilience patterns (chapter 18).

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q9-when-would-you-still-call-synchronously-l3-any)

??? question "Kafka, RabbitMQ, or a cloud bus?"
    <span class="badge badge-l3">L3</span> any

    Kafka (or Pulsar, Redpanda) when you need a durable, replayable, partitioned log, compaction and high throughput; RabbitMQ for flexible routing, per-message acknowledgement and work queues; cloud services when you want them managed. Chapter 25 compares them.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q12-kafka-rabbitmq-or-a-cloud-bus-l3-any)


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "What is a distributed monolith?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A system split into services that still can't be developed, deployed or fail independently, because of shared databases, shared domain libraries, synchronous chains or cycles. It has the costs of distribution without the benefits. The clearest evidence is services that are always released together.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q1-what-is-a-distributed-monolith-l2l3-cognizant-sap-flipkart)

??? question "How would you detect a distributed monolith in an existing system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    From evidence: deploy history (co-deployment ratios), distributed traces (synchronous depth, cycles, fan-out), database audit logs (non-owner table access), dependency manifests (shared domain libraries, version skew), and change coupling in version control.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q2-how-would-you-detect-a-distributed-monolith-in-an-existing-system-l3l4-amazon-goldman-sachs)

??? question "What is the N+1 problem between services and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Calling another service once per item in a loop. Latency and failure probability grow with N. Parallelising helps latency but not failure rate; a batch endpoint fixes both; a local replica of the data removes the calls.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q3-what-is-the-n1-problem-between-services-and-how-do-you-fix-it-l2l3-flipkart-amazon)

??? question "When is a service too small?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    When its distribution costs (network hops, a pipeline, on-call, monitoring) exceed its value: typically when it is always changed or released with a neighbour, has a single caller that owns the use case, and has no different scaling, security or ownership needs. Merge it into the neighbour as a module.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q4-when-is-a-service-too-small-l3-sap-goldman-sachs)

??? question "Why are shared libraries risky in microservices?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    A library with domain classes couples every service to one model and one release cadence; upgrades become coordinated releases and versions drift. Share contracts (API and event schemas) instead; keep shared code to small, stable utilities and automate their upgrades.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q5-why-are-shared-libraries-risky-in-microservices-l3-jp-morgan-sap)

??? question "Why are cyclic dependencies between services a problem?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Neither service can be deployed, tested, scaled or failed independently; a slowdown in either propagates around the loop; startup ordering becomes fragile. Break the cycle by moving the shared responsibility into one service or by turning one direction into events or replicated data.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q6-why-are-cyclic-dependencies-between-services-a-problem-l3-amazon-flipkart)

??? question "Is a shared database always an anti-pattern?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Non-owner writes almost always are. Non-owner reads are a weaker smell that can be a deliberate, temporary step (for example during migration) if they are tracked and burned down. Separate schemas on one database server, with no cross-access, are fine.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q7-is-a-shared-database-always-an-anti-pattern-l3l4-sap-goldman-sachs)

??? question "Microservices or modular monolith for a new product?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> startups · any

    Usually a modular monolith first, with clear module boundaries and owned data, extracting a service when there is a concrete reason (independent scaling, deployment cadence, team autonomy, isolation). Starting with fine-grained services before boundaries are known is the most common route to a distributed monolith.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q9-microservices-or-modular-monolith-for-a-new-product-l3l4-startups-any)

??? question "What does deep synchronous chaining do to availability?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    Availabilities multiply: six services at 99.9% each give at most 99.4% for the chain, and latencies add. Shorten chains with replicated data and asynchronous steps, and protect the remaining calls with timeouts and fallbacks.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q10-what-does-deep-synchronous-chaining-do-to-availability-l2l3-any)

??? question "Your team wants to split a service 'for performance'. What do you ask?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Which part has a different load or latency profile, with data? How many calls per request will cross the new boundary? Will the parts change and release independently? Who owns each? If the answers are "same profile, many calls, always together, same team", the split will make performance worse.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q11-your-team-wants-to-split-a-service-for-performance-what-do-you-ask-l3l4-amazon-goldman-sachs)

??? question "Name anti-patterns you've seen and how you fixed them."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Use a concrete story with evidence and outcome: e.g., a lockstep cluster caused by a shared domain library and a table written by two services, found from deploy logs and database audit logs, fixed by merging the services that always changed together and replacing the library with published contracts, then guarded by a fitness function.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q12-name-anti-patterns-youve-seen-and-how-you-fixed-them-l3l4-any)


### Communication


**[21. Synchronous REST](../communication/ch21-rest-api-design.md)**

??? question "Offset vs cursor pagination?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    Offset skips N rows each time, so cost grows with depth and live inserts/deletes cause duplicates and skips. Keyset/cursor pagination seeks to the last seen sort key using an index: constant cost and stable under changes, but no random page jumps. Use a unique sort key (add the id as tie-breaker) and opaque cursors.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q4-offset-vs-cursor-pagination-l2l3-amazon-flipkart-goldman-sachs)

??? question "How do you prevent lost updates in a REST API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · SAP

    Optimistic concurrency: return an ETag (from a version column), require If-Match on updates, update with `where version = :expected`, return 412 when stale and 428 when If-Match is missing.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q5-how-do-you-prevent-lost-updates-in-a-rest-api-l2l3-jp-morgan-sap)

??? question "How do ETags help performance?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Clients and caches revalidate with If-None-Match; an unchanged resource returns 304 with no body, saving bandwidth and, if the ETag comes from a version column checked cheaply, server work too.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q8-how-do-etags-help-performance-l2l3-amazon-flipkart)

??? question "How do you version a REST API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Avoid versions for additive changes (clients ignore unknown fields). For breaking changes, version by header, media type or URI path, run versions side by side, announce deprecation with Deprecation/Sunset headers, and watch usage before removal. Spring Boot 4 supports this natively.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q9-how-do-you-version-a-rest-api-l2l3-sap-jp-morgan)

??? question "Is HATEOAS worth it?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    Partly. Links for pagination, self and state-dependent actions spare clients from building URLs and encoding business rules; full hypermedia-driven clients make sense mostly for long-lived public APIs with many unknown consumers.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q10-is-hateoas-worth-it-l3-sap-goldman-sachs)

??? question "How would you design an order-history API for 50 million customers?"
    <span class="badge badge-l3">L3</span> Swiggy-/Zomato-style rounds

    `GET /customers/{id}/orders` with keyset cursors on `(placed_at, id)` backed by a `(customer_id, placed_at desc, id desc)` index, limit ≤ 100, `next` links, no counts; single orders with ETags and private caching; problem details for errors; read replicas or a read model for scale.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q11-how-would-you-design-an-order-history-api-for-50-million-customers-l3-swiggy-zomato-style-rounds)

??? question "What would you put in a REST API style guide?"
    <span class="badge badge-l3">L3</span> any

    Naming, method semantics, status codes, error format (RFC 9457 with type URIs), pagination (cursors, limits, links), concurrency (ETag/If-Match), idempotency keys for POST, caching headers, versioning and deprecation, request ids, and how contracts are published and tested.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q12-what-would-you-put-in-a-rest-api-style-guide-l3-any)


**[22. HTTP Clients](../communication/ch22-http-clients.md)**

??? question "Is a blocking HTTP client a scalability problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    On platform threads, yes: concurrency is capped by the pool. On virtual threads, blocking parks cheaply; measured here, 1,000 concurrent 200 ms calls took 0.56 s on virtual threads vs 4.2 s on a 50-thread pool. Still bound concurrency explicitly to protect the dependency.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q5-is-a-blocking-http-client-a-scalability-problem-l2l3-flipkart-goldman-sachs)

??? question "When would you still use WebClient?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    In WebFlux applications, for streaming responses (SSE, large NDJSON), and where reactive composition and backpressure across many concurrent streams are genuinely needed.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q6-when-would-you-still-use-webclient-l2l3-any)

??? question "How would you migrate from Feign?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Replace `@FeignClient` with an `@HttpExchange` interface in an HTTP service group; Spring MVC annotations map to `@GetExchange` etc.; ErrorDecoder → status handler, RequestInterceptor → request interceptor, Retryer → `@Retryable`; timeouts move to `spring.http.serviceclient.<group>.*`. Migrate per client with contract tests.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q8-how-would-you-migrate-from-feign-l2l3-cognizant-sap)

??? question "Sync or async API with virtual threads?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Prefer synchronous calls on virtual threads for readability and debuggability; use structured concurrency or parallel calls for fan-out. Async/reactive APIs remain useful for streaming and backpressure, not for basic concurrency.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q10-sync-or-async-api-with-virtual-threads-l3-goldman-sachs-amazon)

??? question "How do ETags help a client?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    The client keeps the last representation and its ETag and sends If-None-Match; a 304 means no body to transfer or parse. For updates, it sends If-Match so it never overwrites changes it hasn't seen.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q12-how-do-etags-help-a-client-l2l3-any)


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "Is gRPC always faster or smaller than JSON?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Smaller per message (2.5× in this chapter) and cheaper to parse, but not always smaller once compressed: a gzipped JSON stream was smaller than gzipped protobuf here, because gzip removes repeated field names. Choose gRPC for contracts, streaming and deadlines, and measure if size is the argument.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q3-is-grpc-always-faster-or-smaller-than-json-l2l3-flipkart-amazon)

??? question "How do deadlines work, and why are they better than timeouts?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    The client sets an absolute deadline, sent as `grpc-timeout`; servers that make further calls inside the request pass the remaining time on, and cancellation propagates when it expires. Fixed per-hop timeouts know nothing about the caller's budget, so work continues after the caller has given up: 639 ms of wasted work per request vs 2 ms in this chapter's simulation.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q5-how-do-deadlines-work-and-why-are-they-better-than-timeouts-l2l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you evolve a protobuf schema without breaking clients?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    Add fields with new numbers; never change a field's number or (incompatibly) its type; `reserved` removed numbers and names; keep a zero `UNSPECIFIED` enum value and handle unknown values; put a version in the package for breaking changes; enforce with a breaking-change checker in CI.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q7-how-do-you-evolve-a-protobuf-schema-without-breaking-clients-l2l3-flipkart-jp-morgan)

??? question "Why does gRPC load balancing behave oddly on Kubernetes?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    gRPC multiplexes calls over a long-lived HTTP/2 connection, and a ClusterIP Service balances connections, not requests, so one client sticks to one pod. Use client-side balancing (headless Service, `round_robin`), an L7 proxy or service mesh, and max connection age on servers.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q8-why-does-grpc-load-balancing-behave-oddly-on-kubernetes-l3-amazon-flipkart)

??? question "How would a browser call a gRPC service?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Browsers can't use raw gRPC (no control of HTTP/2 framing and trailers), so use gRPC-Web through a proxy (such as Envoy), the Connect protocol, or JSON transcoding at the gateway; or keep a REST/BFF layer at the edge, which is often simplest.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q9-how-would-a-browser-call-a-grpc-service-l2l3-sap-cognizant)

??? question "How does streaming handle a slow client?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    HTTP/2 flow control limits how much a sender can have in flight per stream. In grpc-java, a server checks `isReady()` and uses `setOnReadyHandler` to send only when the client can take more, and coalesces or drops intermediate updates (like old rider positions) rather than buffering without bound.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q10-how-does-streaming-handle-a-slow-client-l3-goldman-sachs-amazon)

??? question "When would you not use gRPC?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Public or partner APIs, browser-facing endpoints, cacheable resource reads, small teams with HTTP-only tooling, and simple request/response integrations where the benefits (streaming, deadlines, generated contracts) won't be used. The cost is real: schema tooling, HTTP/2-aware infrastructure and harder debugging.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q12-when-would-you-not-use-grpc-l3l4-any)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "What is the N+1 problem in GraphQL and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Resolvers run per parent object, so a list of N items whose field calls a service makes 1 + N calls. Fix it with batch loading: DataLoader collects keys at one execution level and loads them in one call (Spring's `@BatchMapping` or `BatchLoaderRegistry`). In this chapter a screen went from 19 backend calls to 3, with identical data.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q3-what-is-the-n1-problem-in-graphql-and-how-do-you-fix-it-l2l3-flipkart-amazon-goldman-sachs)

??? question "How do you protect a GraphQL API from expensive queries?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Limit depth and computed complexity before execution (graphql-java instrumentations, with a cost function that multiplies by page sizes), cap page sizes, time out execution, and in production accept only persisted queries from your own apps. Batching alone doesn't help: it reduces calls, not the amount of work.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q5-how-do-you-protect-a-graphql-api-from-expensive-queries-l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you cache GraphQL responses?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    HTTP caching is hard because queries are POSTs to one URL. Options: normalised client caches (Apollo, Relay) keyed by type and ID, persisted queries sent as GET so CDNs can cache them, caching inside the services, and per-request DataLoader caching for repeated keys.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q7-how-do-you-cache-graphql-responses-l3-flipkart-amazon)

??? question "Should microservices talk to each other in GraphQL?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Usually not. Between services you want fixed contracts, predictable cost, deadlines and simple tooling: REST, gRPC or events. GraphQL's strength is letting many changing client screens choose their data, so it belongs at the client-facing edge, typically in a BFF or a federated gateway.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q8-should-microservices-talk-to-each-other-in-graphql-l3l4-any)

??? question "What is GraphQL federation and when is it worth it?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    A supergraph composed from subgraphs owned by different services, with a router planning each query across them; entities like `Restaurant` can be extended by several subgraphs. It is worth it when many teams contribute to one client-facing graph; otherwise a single BFF graph is simpler to run and debug.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q9-what-is-graphql-federation-and-when-is-it-worth-it-l3l4-amazon-sap)

??? question "How do you secure a GraphQL endpoint?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    Authenticate at the gateway and pass identity in context (never as arguments), authorise per field or object, disable introspection in production for first-party apps, use persisted queries as an allow-list, limit depth, complexity, aliases and page sizes, and avoid leaking internals in error messages.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q10-how-do-you-secure-a-graphql-endpoint-l2l3-jp-morgan-goldman-sachs)

??? question "How do you evolve a GraphQL schema without versioning the API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Add fields and types freely (clients only get what they ask for); deprecate fields with `@deprecated(reason:)`, track their usage per client operation, and remove them when usage drops to zero. Changing a field's type or nullability is breaking; run schema breaking-change checks in CI.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q11-how-do-you-evolve-a-graphql-schema-without-versioning-the-api-l2l3-sap-flipkart)

??? question "GraphQL or a BFF with REST endpoints: how do you decide?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    A BFF endpoint per screen is simple, cacheable and predictable, and was as fast as GraphQL for one screen here. GraphQL wins when there are many screens and clients that change often, because one schema replaces many endpoints, at the cost of batching discipline, query limits, new monitoring and weaker HTTP caching.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q12-graphql-or-a-bff-with-rest-endpoints-how-do-you-decide-l3l4-any)


**[25. Asynchronous Messaging](../communication/ch25-asynchronous-messaging.md)**

??? question "How does Kafka guarantee ordering, and how can you lose it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Within a partition, records are stored and delivered in order; a key sends all records of an entity to one partition. You lose it by processing a partition's records in parallel, by non-blocking retries, by changing the partition count (keys move), or with producer retries without idempotence. Use enough partitions and key-ordered concurrency.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q4-how-does-kafka-guarantee-ordering-and-how-can-you-lose-it-l2l3-flipkart-amazon)

??? question "Blocking or non-blocking retries in Spring Kafka?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Blocking (`DefaultErrorHandler` with a BackOff) keeps per-partition order but stalls the partition while retrying. Non-blocking (`@RetryableTopic`) moves failed records to delayed retry topics so the main topic keeps flowing, at the cost of ordering. Use blocking for sequential state, non-blocking for independent side effects. Measured: with one partner down, retry topics cut p99 from 30.9 s to 8.2 s.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q6-blocking-or-non-blocking-retries-in-spring-kafka-l3-amazon-goldman-sachs)

??? question "What does Spring Kafka do by default when a listener throws?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    `DefaultErrorHandler` retries the record up to 10 times with no delay, then logs it and commits past it: the message is skipped. Always configure a recoverer (usually `DeadLetterPublishingRecoverer`), a backoff, and not-retryable exceptions.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q7-what-does-spring-kafka-do-by-default-when-a-listener-throws-l2l3-any)

??? question "How would you design dead-letter handling for a payments system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Classify errors; retry transient ones with bounded backoff in order (per account); dead-letter the rest with full context (headers, original offset, error); alert immediately; give operations a tool to inspect, fix and replay with approval and audit; make consumers idempotent so replays are safe; never drop.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q10-how-would-you-design-dead-letter-handling-for-a-payments-system-l3l4-jp-morgan-goldman-sachs)

??? question "When would you choose RabbitMQ over Kafka?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    For task queues and work distribution with per-message acknowledgement, priorities, TTLs, delayed or routed delivery, and no need for replay or long retention; or when consumers need to scale independently of a partition count. Kafka for event streams with many subscribers, replay, high throughput and per-key ordering.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q11-when-would-you-choose-rabbitmq-over-kafka-l3-sap-cognizant)

??? question "What goes wrong if a consumer marks a message as processed before applying it?"
    <span class="badge badge-l3">L3</span> Amazon · any

    If the effect fails, the retry is treated as a duplicate and skipped: the message is silently lost despite at-least-once delivery. The processed marker and the effect must commit together (one database transaction), or the marker must be removed when the effect fails.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q12-what-goes-wrong-if-a-consumer-marks-a-message-as-processed-before-applying-it-l3-amazon-any)


**[26. WebSockets & Server-Sent Events](../communication/ch26-websockets-sse.md)**

??? question "How do you scale WebSocket or SSE connections across many servers?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    Keep connections in a dedicated, horizontally scaled tier; get events to the right instance by broadcast (simple, N× work), routing via a registry, per-key pub/sub channels, or partitioned routing at the load balancer; use a shared broker (Kafka, Redis, or a STOMP broker relay). Size instances by connections and memory, not requests per second.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q5-how-do-you-scale-websocket-or-sse-connections-across-many-servers-l3-amazon-flipkart-goldman-sachs)

??? question "How many concurrent connections can one Java server hold?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    With a blocked platform thread per connection, about as many as the thread pool (measured: 200 of 10,000). With virtual threads or a reactive server, tens of thousands per instance; here 10,000 SSE streams used ~50 KB each. File descriptors, memory, the load balancer and broadcast CPU become the limits.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q6-how-many-concurrent-connections-can-one-java-server-hold-l3-goldman-sachs-jp-morgan)

??? question "What is a reconnect storm and how do you prevent it?"
    <span class="badge badge-l3">L3</span> Amazon · any

    Many clients losing connections at once and reconnecting in step, overloading handshakes; timed-out attempts waste capacity and trigger retries. Prevent it with jittered reconnect delays (randomised SSE `retry:`, full-jitter backoff), gradual draining on deploys, and servers that drop work for clients that have already given up. Measured: 20.4 s and 209,000 wasted handshakes without jitter, 9.9 s and none with.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q7-what-is-a-reconnect-storm-and-how-do-you-prevent-it-l3-amazon-any)

??? question "How do you avoid losing updates when a connection drops?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Give every event an id, keep a short replay buffer per stream, and have the client send its last id on reconnect (`Last-Event-ID` for SSE, a resume message for WebSocket). For state like positions, sending the latest state on reconnect may be enough.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q8-how-do-you-avoid-losing-updates-when-a-connection-drops-l2l3-sap-flipkart)

??? question "How do you authenticate WebSocket connections?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · SAP

    At the handshake: headers from native clients (or verified by the gateway), cookies or a short-lived ticket for browsers (which can't set custom headers on WebSocket). Store the identity in the session, check origins for browsers, authorise each subscription, and close or refresh sessions when tokens expire.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q10-how-do-you-authenticate-websocket-connections-l2l3-jp-morgan-sap)

??? question "What happens with a slow consumer, and how do you handle it?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Its output buffers grow or the sending thread blocks, which can delay other clients and exhaust memory. Bound per-connection buffers and send time, conflate (keep only the latest value per key), send asynchronously per connection, and close connections that can't keep up.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q11-what-happens-with-a-slow-consumer-and-how-do-you-handle-it-l3-goldman-sachs-amazon)


**[27. API Contracts & Schema Evolution](../communication/ch27-api-contracts.md)**

??? question "What are consumer-driven contracts, and how do they differ from a schema diff?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Consumers publish executable expectations (requests and the response parts they use) that run against the provider in its build. A diff compares schema versions: broad, but blind to behaviour and usage, so it raises false alarms and misses behaviour changes. Contracts are precise but cover only consumers who write them. Here each caught breaks the other missed.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q5-what-are-consumer-driven-contracts-and-how-do-they-differ-from-a-schema-diff-l2l3-goldman-sachs-flipkart)

??? question "Spring Cloud Contract or Pact?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Spring Cloud Contract: contracts usually live with the provider, generate provider tests and WireMock stubs for consumers; natural for Spring-only shops. Pact: consumers generate contracts from their tests, a broker stores them and answers "can I deploy?"; polyglot and consumer-led. Both test examples, not every possible response.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q6-spring-cloud-contract-or-pact-l3-sap-jp-morgan)

??? question "How would you rename a field without breaking consumers?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    Expand and contract: add the new field and populate both, migrate consumers (contracts and usage data show who's left), then remove the old field. Never rename in one step.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q8-how-would-you-rename-a-field-without-breaking-consumers-l2l3-flipkart-any)

??? question "Which schema registry compatibility mode would you choose for domain events, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    `FULL_TRANSITIVE`: any consumer version can read any event ever written, which matters for replays, new consumers reading history, and deploys in any order. The cost is that only optional fields (with defaults) can be added or removed; anything else is a new event type or topic.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q10-which-schema-registry-compatibility-mode-would-you-choose-for-domain-events-and-why-l3l4-amazon-jp-morgan)

??? question "What can't contract tests and schema checks catch?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Changes of meaning or units with the same name and type, behaviour not covered by examples, performance changes, and consumers that publish no contracts. Prevent these with treating meaning changes as new fields, consumer review of semantic changes, and usage tracking.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q11-what-cant-contract-tests-and-schema-checks-catch-l3l4-any)


**[28. Idempotency, Deduplication & Correlation](../communication/ch28-idempotency-correlation.md)**

??? question "How do you implement idempotency keys?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    The client sends a unique key per action. The server atomically claims it (insert with a unique constraint) before any side effect, stores a request fingerprint, performs the operation, and stores the response with the business write. Duplicates get the stored response, 409 if still in progress, 422 if the fingerprint differs. Add a lease for crashes and pass the key downstream. Measured: exactly one charge per key under 4× concurrent duplicates.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q2-how-do-you-implement-idempotency-keys-l2l3-amazon-flipkart-goldman-sachs)

??? question "Why isn't a unique constraint on the payments table enough?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    It deduplicates rows, not side effects. If the external call happens before the insert, every duplicate still calls the provider; the constraint just rejects the extra rows. Measured: 200 rows, 800 charges.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q3-why-isnt-a-unique-constraint-on-the-payments-table-enough-l2l3-jp-morgan-amazon)

??? question "Does SERIALIZABLE isolation solve duplicate requests?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    Only for effects inside the database. Conflicting transactions are aborted, but an external call made inside them already happened; here SERIALIZABLE made more provider charges than plain check-then-insert, plus charges with no recorded payment. Claim ownership first, commit, then call.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q4-does-serializable-isolation-solve-duplicate-requests-l3-goldman-sachs-jp-morgan)

??? question "How long should you keep idempotency keys or processed message ids?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    Longer than the slowest source of duplicates you care about, and published to clients. For API keys, longer than any client retry (24 h is common). For consumers, longer than redeliveries and re-publishes; for replays days later, use version checks on the entity instead of a huge store. Measured: a 1-hour window missed every replay; a 7-day table needed 302 M ids.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q6-how-long-should-you-keep-idempotency-keys-or-processed-message-ids-l2l3-flipkart-sap)

??? question "What's the difference between correlation id, causation id and trace id?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    The correlation id groups everything in one business flow, possibly over days and many traces. The causation id points to the message that directly caused this one, giving the exact sequence. The trace id (W3C `traceparent`) groups the spans of one request tree for latency analysis and is often sampled. The event id identifies one message for deduplication.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q8-whats-the-difference-between-correlation-id-causation-id-and-trace-id-l2l3-sap-goldman-sachs)

??? question "Why not order events by timestamp?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Clocks on different machines differ by milliseconds even with NTP, and occasionally by much more; causally related events often happen milliseconds apart. Measured: 8% of orders misordered with good clocks, 39% with one drifting instance; causation ids had no errors. Use causation ids or per-entity sequence numbers.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q9-why-not-order-events-by-timestamp-l2l3-amazon-jp-morgan)

??? question "What happens if the server crashes after claiming a key?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Without a lease, the key stays in progress and the client gets 409 forever. With a lease, a retry of the same request takes over after it expires and re-asks the provider with the same downstream key, which returns the original result if the first attempt did charge. A unique constraint on the provider's reference catches the rare overlap of two owners.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q11-what-happens-if-the-server-crashes-after-claiming-a-key-l3-goldman-sachs-flipkart)

??? question "How do you make a consumer of 'points += 10' events idempotent?"
    <span class="badge badge-l3">L3</span> Amazon · any

    Record the event id in an inbox table in the same transaction as the increment, skipping it if already present; or change the model so applying an event is a set ("points from event X = 10") or conditional on the entity's version. Replays must go through the same path.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q12-how-do-you-make-a-consumer-of-points-10-events-idempotent-l3-amazon-any)


### Data


**[29. Polyglot Persistence](../data/ch29-polyglot-persistence.md)**

??? question "How do you choose a database for a microservice?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    From its access patterns with numbers (operations, rates, latency, consistency, loss tolerance), then the default store measured against them, then the operating cost of anything extra. The source of truth usually needs transactions and constraints (relational); specialised stores are justified by a measured gap, e.g. Redis for 12,500 position writes/s where PostgreSQL managed 7,100–8,000 with every commit synced (Redis: 30,000+).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q2-how-do-you-choose-a-database-for-a-microservice-l2l3-amazon-flipkart)

??? question "Document store or relational for a restaurant menu?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    It depends on the operations. Measured: a JSONB document read the whole menu 14× faster, but one price change was 5× slower with 15× the WAL, concurrent edits to one menu serialised (11× slower), and a cross-restaurant query was 180× slower. Best of both: write to tables, keep a document per menu rebuilt in the same transaction for reads.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q3-document-store-or-relational-for-a-restaurant-menu-l2l3-flipkart-amazon)

??? question "Why are dual writes dangerous?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    There is no transaction across two stores, so a crash between the writes, an outage of the second store or two concurrent writers leave them disagreeing, silently. Measured: 43–77 of 5,000 items wrong after 20,000 changes under faults; writing the cache first even produced prices that were never committed. Use an outbox or CDC with a projector.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q4-why-are-dual-writes-dangerous-l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you keep a cache or search index in sync with the database?"
    <span class="badge badge-l3">L3</span> Flipkart · SAP

    Make the database the source of truth, record changes in the same transaction (outbox) or read the change log (CDC), and apply them with a projector that retries until it succeeds and writes only if the version is newer. Monitor lag and drift, and be able to rebuild the copy. Measured: zero wrong items, median lag 3 ms.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q5-how-do-you-keep-a-cache-or-search-index-in-sync-with-the-database-l3-flipkart-sap)

??? question "What's wrong with a projector that remembers the last outbox id it processed?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Amazon

    Sequence values are assigned at insert but become visible at commit, in a different order. A transaction that took id 1,000 can commit after the projector has read 1,001, and is never read. Measured: 29–48 of 5,000 items left wrong. Claim rows with `FOR UPDATE SKIP LOCKED` and delete them when applied, or follow the log position (logical replication, Debezium).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q6-whats-wrong-with-a-projector-that-remembers-the-last-outbox-id-it-processed-l3l4-goldman-sachs-amazon)

??? question "Is Redis faster than PostgreSQL?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    For small writes, yes, and the honest comparison is at the same durability: about 31,000 vs 7,100–8,000 updates/s with every write synced, 90,000+ vs 16,800 with up to a second of possible loss. Not for everything: Redis's sorted radius search computes and sorts every member in the circle (~950/s with 1,900 riders inside) where PostgreSQL's KNN index stopped after ten (~3,650/s).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q7-is-redis-faster-than-postgresql-l2l3-flipkart-any)

??? question "When do you need Elasticsearch instead of database full-text search?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · SAP

    When search is a product feature: relevance tuning, synonyms, facets and aggregations, many languages, typo-tolerant typeahead in milliseconds over millions of documents. Measured: PostgreSQL full-text + trigram fallback found 29 of 29 test queries at a 14 ms median over 192,000 items, but typos cost ~50 ms at the median. Then it's a derived store, fed from the source of truth.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q8-when-do-you-need-elasticsearch-instead-of-database-full-text-search-l2l3-amazon-sap)

??? question "Why can adding an index slow writes down so much?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    Every index must be updated on every write that changes its columns, and in PostgreSQL an indexed column change also prevents HOT updates, so all indexes get new entries. Measured: a GiST geo index took position updates from 98% HOT to 0%, cut the rate from about 16,800 to 7,600 per second and more than doubled WAL per update (217 → 517 B).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q9-why-can-adding-an-index-slow-writes-down-so-much-l3-jp-morgan-goldman-sachs)

??? question "How do you store time-series data in a relational database?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Append in time order into tables partitioned by time, with a BRIN index on the timestamp and B-trees only for the lookups you need (e.g. by device and time). Retention drops old partitions. Measured: the time index went from 79 MB to 168 kB, and removing a day took 83 kB of WAL instead of 171 MB. Move to a time-series or columnar store for compression and downsampling at much larger volumes.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q10-how-do-you-store-time-series-data-in-a-relational-database-l2l3-sap-flipkart)

??? question "What does every additional database cost?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Provisioning, backups and restore drills, monitoring and alerting, upgrades and security patches, access control, capacity planning, on-call expertise, local and CI setup, and a sync path from the source of truth with its own failure modes and lag. These costs are permanent and usually exceed the licence or hosting bill.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q11-what-does-every-additional-database-cost-l3l4-any)


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "What is two-phase commit?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    A protocol for atomic commit across several resources. Phase 1: the coordinator asks each participant to prepare (persist the work and promise to commit); phase 2: if all agreed, it logs COMMIT and tells everyone to commit, otherwise to abort. Between the phases the participants are in doubt and hold their locks.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q1-what-is-two-phase-commit-l2l3-cognizant-sap)

??? question "Why is 2PC avoided between microservices?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    It holds locks across network round trips (measured: 2.8× as long as a saga step, half the throughput on hot rows), it couples availability (no orders while payments was down), it blocks if the coordinator fails (one in-doubt transaction cut a hot path to 2–6 orders/s), and it needs XA-capable resources, which brokers, HTTP APIs and external providers aren't. It also couples teams to a shared transaction manager.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q2-why-is-2pc-avoided-between-microservices-l3-amazon-flipkart-goldman-sachs)

??? question "What happens if the coordinator crashes after the participants prepared?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    The participants stay prepared, holding locks, across restarts, until the coordinator recovers and reads its log or someone resolves them. With presumed abort, no logged decision means roll back. Measured: the prepared transaction survived a database restart and kept its locks until recovery ran.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q3-what-happens-if-the-coordinator-crashes-after-the-participants-prepared-l3l4-goldman-sachs-jp-morgan)

??? question "How does a saga differ from a distributed transaction?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    Each step commits locally and at once; failures are undone by compensating steps, not rollback. It gives up isolation (intermediate states are visible) in exchange for no cross-service locks and independent availability. Measured: the saga's payment step held its lock 4.3–4.5 ms vs 2PC's 11–12.5 ms.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q6-how-does-a-saga-differ-from-a-distributed-transaction-l2l3-flipkart-sap)

??? question "Why should order placement return 202 Accepted?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Because the order exists but its payment isn't decided yet. Saying so lets the request be one local transaction, keeps ordering available when payments isn't (measured: all orders accepted during a 10 s outage, confirmed within 10.2 s), and the client follows a status resource or receives a push.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q7-why-should-order-placement-return-202-accepted-l3-amazon-flipkart)

??? question "What do you do when you don't know whether a remote commit happened?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Don't guess. Leave the operation pending and reconcile from the other side's records (or retry idempotently with the same key). Rejecting on any error charged customers for rejected orders in incident 3; measured, a database restart left up to one order (of 8 in flight) with an unknown outcome.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q8-what-do-you-do-when-you-dont-know-whether-a-remote-commit-happened-l3-goldman-sachs-amazon)

??? question "How do you make a saga participant safe to retry?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Key the work by the request's id and store the decision in the same local transaction (an inbox or decision row); a repeat returns the stored decision. Lock rows in a consistent order, and for batches fall back to one by one so business rejections stay per request.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q9-how-do-you-make-a-saga-participant-safe-to-retry-l3-amazon-jp-morgan)

??? question "Is the transactional outbox a distributed transaction?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    No. It makes "change state and publish" atomic by writing both into one local database transaction; delivery happens afterwards, at least once, so consumers must be idempotent. It replaces XA between a database and a broker.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q10-is-the-transactional-outbox-a-distributed-transaction-l3-sap-flipkart)


**[31. Data Consistency](../data/ch31-data-consistency.md)**

??? question "What is read-your-writes and how do you provide it with read replicas?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    After a client writes, its reads reflect that write. Return a token with the write (in PostgreSQL, the WAL position after commit); route the client's reads to a replica whose replay position is at least the token, or wait briefly, or use the primary. Measured: 0 stale reads with 2.9% of reads on the primary, versus 15% stale from a lagging replica.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q2-what-is-read-your-writes-and-how-do-you-provide-it-with-read-replicas-l3-amazon-flipkart)

??? question "Why not just read from the primary for a few seconds after a write?"
    <span class="badge badge-l3">L3</span> Flipkart · any

    The window is a guess. Shorter than the lag and reads are stale (100 ms against 200 ms of lag: 6.7–6.8% stale); long enough to be safe and many reads hit the primary (1 s: 55%). A token adapts to the actual lag.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q3-why-not-just-read-from-the-primary-for-a-few-seconds-after-a-write-l3-flipkart-any)

??? question "What are monotonic reads?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    A client never sees older data after it has seen newer. Load-balancing reads across replicas with different lag breaks it (measured: 19% of polls went backwards). Fix with sticky routing or a token of the newest position seen.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q4-what-are-monotonic-reads-l3-amazon-sap)

??? question "What is a lost update and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Two read-modify-write cycles interleave and one overwrites the other. Prevent with an atomic update (`SET x = x + 1`), a conditional update, `SELECT ... FOR UPDATE`, optimistic versions with retry, or REPEATABLE READ/SERIALIZABLE with retry. Measured: read-modify-write lost 15.5% of points; the atomic update lost none and was the fastest.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q5-what-is-a-lost-update-and-how-do-you-prevent-it-l2l3-goldman-sachs-jp-morgan-amazon)

??? question "Optimistic or pessimistic locking?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    Optimistic when conflicts are rare and the work between read and write is long (user edits: ETag + If-Match); pessimistic when conflicts are frequent and the critical section is short. Best of all, avoid both by making the change in the database in one statement. Under heavy contention optimistic retries dominate (thousands in the experiment).

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q6-optimistic-or-pessimistic-locking-l3-jp-morgan-flipkart)

??? question "How does PostgreSQL report replication lag, and what can go wrong reading from a replica?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    `pg_stat_replication` on the primary (write/flush/replay lag), `pg_last_wal_replay_lsn()` and `pg_last_xact_replay_timestamp()` on the replica. Pitfalls: stale reads, queries cancelled by replay conflicts (SQLSTATE 40001), and a statement's snapshot preceding the functions in it, so check the position before reading, in a separate statement.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q7-how-does-postgresql-report-replication-lag-and-what-can-go-wrong-reading-from-a-replica-l3l4-goldman-sachs-sap)

??? question "Last-writer-wins: when is it acceptable?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · any

    For single values where losing a concurrent write is acceptable (a display name, a note), with timestamps from a hybrid logical clock so causally later writes win. Not for collections or counters: measured, LWW on whole carts lost 562–578 added items across 2,000 carts.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q8-last-writer-wins-when-is-it-acceptable-l3l4-amazon-any)

??? question "Explain PACELC with an example from your system."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    If partitioned, choose availability or consistency; else, latency or consistency. FoodFleet's restaurant listing chooses availability and latency (replicas, caches); the wallet chooses consistency (primary only) and accepts the latency; profiles use tokens to get read-your-writes at near-replica latency.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q11-explain-pacelc-with-an-example-from-your-system-l3l4-amazon-flipkart)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "What should you cache?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    What is expensive to produce or very hot, and may be slightly stale. Measured: Redis didn't speed up a primary-key read of a menu card (at 2.1–2.2 ms p50 against 1.7–1.8 ms), but turned a 130 ms aggregation into a 1.1 ms read. Never cache the inputs of decisions like payment or stock checks.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q2-what-should-you-cache-l2l3-flipkart-amazon)

??? question "'Update the database, then delete the cache key': is that safe?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Mostly, not always. A reader that missed just before the update can fill the old value just after the delete, and it stays until the TTL. Measured: 1–3 stale entries left in a 15 s run. Make cache writes conditional on version (set-if-newer), use leases, or delete again shortly after.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q3-update-the-database-then-delete-the-cache-key-is-that-safe-l3-amazon-goldman-sachs)

??? question "How do you prevent a cache stampede?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon · JP Morgan

    Coalesce misses (single-flight per instance or a distributed lock), refresh before expiry (probabilistic early expiration, with a lock), or serve stale while one caller refreshes. Add TTL jitter. Measured: 160 recomputations unprotected; 9–10 with stale-while-revalidate, and no request over 100 ms.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q4-how-do-you-prevent-a-cache-stampede-l3-flipkart-amazon-jp-morgan)

??? question "What does @Cacheable(sync = true) do, and what doesn't it do?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Concurrent misses for the same key on one instance wait for a single load. It does nothing across instances (N instances, N loads: measured 4 at once with 4 instances) and it doesn't serve stale values; combine with stale-while-revalidate for expensive keys.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q5-what-does-cacheablesync-true-do-and-what-doesnt-it-do-l2l3-sap-cognizant)

??? question "Why might a cache eviction in a @Transactional method not work?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · any

    Unless the cache manager is transaction-aware, the eviction happens before the commit, and a concurrent reader can reload the old row and cache it. Make the RedisCacheManager transaction-aware (Spring Boot: a `RedisCacheManagerBuilderCustomizer` calling `transactionAware()`), or evict after commit.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q6-why-might-a-cache-eviction-in-a-transactional-method-not-work-l3-goldman-sachs-any)

??? question "When is write-behind appropriate?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    For high-rate data that may be approximate: counters, impressions, last-seen times. It cuts database writes to batches (measured: ~7,000 statements/s to 1) but loses unflushed data if the cache loses it (measured: 9,991–10,651 views on a Redis restart). Flushes must be idempotent (atomic claim, batch ids). Never for money.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q7-when-is-write-behind-appropriate-l3-amazon-flipkart)

??? question "What happens to your service when Redis is down?"
    <span class="badge badge-l3">L3</span> Amazon · any

    It should fall back to the source of truth, slower but correct, with Redis timeouts shorter than the database's latency so a sick cache doesn't make things worse. Capacity planning must assume the database can take the cache's load for a while, or the cache becomes a hidden single point of failure.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q9-what-happens-to-your-service-when-redis-is-down-l3-amazon-any)

??? question "Local (in-process) cache or distributed cache?"
    <span class="badge badge-l3">L3</span> Flipkart · SAP

    Local caches are fastest (no network; measured p50 under 0.1 ms) but stale per instance and multiply memory; distributed caches are shared and consistent across instances but cost a network hop. Use local for small, hot, slowly-changing data with a short TTL, often in front of a distributed one.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q10-local-in-process-cache-or-distributed-cache-l3-flipkart-sap)

??? question "How do you choose a TTL?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    From the staleness the business accepts and how the value is invalidated. With explicit invalidation, the TTL is a safety net and can be long; without it, the TTL is the staleness bound. Add jitter to avoid synchronised expiries; use soft and hard TTLs for stale-while-revalidate.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q11-how-do-you-choose-a-ttl-l2l3-any)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

??? question "How big should a connection pool be?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    Small, and sized for the database, not the instance: a few times the database's cores across the whole fleet, then measured. Measured on 2 vCPUs: 16 to 256 connections added at most 10–19% throughput and raised p99 inside the database from 6 ms to 76–98 ms. Extra demand should wait in the pool, not in the database.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q1-how-big-should-a-connection-pool-be-l2l3-flipkart-cognizant)

??? question "Your migration only adds a nullable column, yet checkout froze. Why?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    The lock queue. The `ALTER TABLE` waited for a long query holding a lock on the table, and every later query waited behind the `ALTER TABLE`. Measured: 10 s of blocked writes behind a 12 s report. Use `lock_timeout` and retry, and keep long queries off the primary.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q3-your-migration-only-adds-a-nullable-column-yet-checkout-froze-why-l3-amazon-goldman-sachs)

??? question "Which schema changes are safe on a large, busy PostgreSQL table?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Catalogue-only ones: nullable columns, constant defaults (PostgreSQL 11+), `NOT VALID` constraints, dropping NOT NULL. Not: volatile defaults, most type changes (rewrites), plain index builds, validated constraints in one step. Use `CONCURRENTLY` and `NOT VALID` + `VALIDATE`.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q4-which-schema-changes-are-safe-on-a-large-busy-postgresql-table-l3-amazon-jp-morgan)

??? question "What can go wrong with CREATE INDEX CONCURRENTLY?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    It can't run in a transaction, it waits for every older snapshot in the database, and if it fails it leaves an INVALID index that is maintained but unused. With `IF NOT EXISTS`, a rerun keeps the invalid index (measured). Check `pg_index.indisvalid`, drop and rebuild.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q5-what-can-go-wrong-with-create-index-concurrently-l3-goldman-sachs-sap)

??? question "How do you rename a column with zero downtime?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Expand/contract: add the new column; deploy code that writes both and reads the old; backfill; deploy code that reads the new; deploy code that writes only the new; drop the old in a later release. Measured: in-place renames failed thousands of statements during a rolling deploy; expand/contract none.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q6-how-do-you-rename-a-column-with-zero-downtime-l3-flipkart-amazon)

??? question "How do you backfill a column on a billion-row table?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    In keyset batches by primary key, each its own short transaction, throttled, resumable, bounded by the max id at start, watching replica lag and autovacuum. Measured on 2 M rows: one `UPDATE` made writes wait up to 5.5–6.8 s; batches of 1,000 kept them at or under 60 ms.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q7-how-do-you-backfill-a-column-on-a-billion-row-table-l3-amazon-jp-morgan)

??? question "Read replicas or sharding?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Replicas scale reads that tolerate lag and are cheap to add; sharding scales writes and data size but makes cross-shard queries, transactions and operations hard. Exhaust indexing, caching, replicas, partitioning and write reduction first; shard when one primary can't take the writes.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q8-read-replicas-or-sharding-l3l4-flipkart-goldman-sachs)

??? question "Why not hash(key) mod N?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Changing N remaps almost every key. Measured: 80% of orders moved going from 16 to 20 shards, against 19.5% with 4,096 fixed slots and 18.6% with a consistent-hash ring. Slots also give a small, explicit map and a unit of migration.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q10-why-not-hashkey-mod-n-l3l4-amazon-sap)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "How do you build a report that needs data from several microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    Not by joining their databases. For small, rare queries, compose their APIs; for real reports, build a reporting store owned by a reporting service and fed by their events or CDC, shaped for the queries. Measured: API composition 2.7–2.9 s with hundreds of calls, a reporting copy under a second, a pre-aggregated table about 0.14 s.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q1-how-do-you-build-a-report-that-needs-data-from-several-microservices-l2l3-flipkart-cognizant)

??? question "Outbox events or CDC for feeding analytics?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Outbox events for data other teams depend on: deliberate, in the domain's language, versioned. CDC when you need everything without changing the source, or for internal pipelines, accepting that it exposes table structure. Many platforms use both into the same Kafka backbone.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q3-outbox-events-or-cdc-for-feeding-analytics-l3-amazon-goldman-sachs)

??? question "What is a replication slot, and how can it take a database down?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    The source's bookmark for a logical (or physical) replication consumer; WAL is kept until the consumer confirms it. A stopped or orphaned consumer makes WAL accumulate until the disk is full. Measured: 132–158 MB a minute at our write rate. Cap it with `max_slot_wal_keep_size`, alert on retained WAL, drop slots with their consumers.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q4-what-is-a-replication-slot-and-how-can-it-take-a-database-down-l3-goldman-sachs-jp-morgan)

??? question "How do you keep aggregates correct when events are duplicated or out of order?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Keep the latest state per entity, replaced only by a higher version, and change aggregates by the delta between old and new state in the same transaction. Measured: counting events +7.1%, counting with dedup +1.9% (corrections), state with versions exact.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q5-how-do-you-keep-aggregates-correct-when-events-are-duplicated-or-out-of-order-l3-amazon-flipkart)

??? question "Materialized view or summary table?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    A materialized view is simple but recomputes everything on refresh; a plain refresh blocks readers (measured: all readers waited 3.0–3.4 s), `CONCURRENTLY` doesn't but took 7× longer. An incremental summary table is always current and never blocks, at the cost of per-change logic. Views for slow-moving summaries, incremental tables for live dashboards.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q6-materialized-view-or-summary-table-l3-sap-cognizant)

??? question "What's the most common bug in daily reports?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    Days in the wrong time zone. `date(placed_at)` on a UTC server put 4% of Bengaluru orders (00:00–05:30 IST) on the previous day in our data. Compute the business day in the business's zone once and store it.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q7-whats-the-most-common-bug-in-daily-reports-l2l3-flipkart-any)

??? question "How do you know a report is up to date?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    You don't, unless it tells you. Track the event time of the newest change applied, show it as "as of", compare it with a freshness objective, alert when exceeded. Measured: with the CDC consumer stopped, the copy fell 60 s behind in 60 s and nothing in the data showed it.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q8-how-do-you-know-a-report-is-up-to-date-l3-jp-morgan-amazon)

??? question "Data warehouse, data lake, lakehouse: what's the difference?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    A warehouse stores modelled, cleaned data in a managed engine with schema on write. A lake stores raw and refined files cheaply in open formats, schema on read. A lakehouse adds table formats (Iceberg, Delta, Hudi) to the lake for transactions, schema evolution and time travel, so warehouse-style queries run on lake storage.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q9-data-warehouse-data-lake-lakehouse-whats-the-difference-l3-amazon-sap)

??? question "A reporting store disagrees with the payments system. What do you do?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    The system of record wins. Reconcile daily per day and entity, find the class of difference (missed events, duplicates, corrections, time zones, late data), fix the projection, and rebuild the affected range by replay. Reports carry their as-of time so known gaps are explainable.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q11-a-reporting-store-disagrees-with-the-payments-system-what-do-you-do-l3l4-jp-morgan-goldman-sachs)


### Deployment & Platform


**[53. Containerization](../deployment/ch53-containerization.md)**

??? question "Dockerfile, Jib or Buildpacks?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Atlassian · Zomato

    

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q4-dockerfile-jib-or-buildpacks-l2l3-atlassian-zomato)

??? question "Why was my pod OOMKilled with no OutOfMemoryError?"
    <span class="badge badge-l3">L3</span> Uber · PhonePe

    The process exceeded the cgroup limit, usually because `-Xmx` (plus off-heap memory) was above it, and the kernel killed it. Measured: `-Xmx1g` in 512 MB gave exit 137, `OOMKilled: true` and no Java output. Size the heap as a percentage of the limit and use `ExitOnOutOfMemoryError`.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q6-why-was-my-pod-oomkilled-with-no-outofmemoryerror-l3-uber-phonepe)

??? question "Which GC does the JVM choose in a container?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    G1 only on a "server-class machine", meaning 2 or more CPUs and about 1.8 GB or more; otherwise Serial. Measured: Serial at 1 GB with 2 CPUs and at 4 GB with 1 CPU. Set the collector explicitly for small pods.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q7-which-gc-does-the-jvm-choose-in-a-container-l3-goldman-sachs)

??? question "How do you speed up JVM startup in containers?"
    <span class="badge badge-l3">L3</span> Swiggy · Meesho

    Use CDS or the JDK 24+ AOT cache from a training run, a smaller runtime, lazy initialization where safe, and enough CPU during startup. Measured: an AOT cache took first response from 1.2–1.3 s to 0.49–0.51 s at 1 CPU, for 24 MB of image. Native images and CRaC are chapter 60.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q9-how-do-you-speed-up-jvm-startup-in-containers-l3-swiggy-meesho)

??? question "How do you harden a container?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks

    Run as a non-root user with a read-only root filesystem (a tmpfs for `/tmp`), drop all capabilities, set `no-new-privileges`, and use no shell or package manager in the image. Measured: menu-service ran unchanged under all of these.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q10-how-do-you-harden-a-container-l2l3-banks)

??? question "What does an SBOM miss?"
    <span class="badge badge-l3">L3</span> Razorpay · security rounds

    Anything without package metadata. syft found the Java runtime and jars in the lab's images but not glibc, which was copied in without the package database. A scanner can't flag what the SBOM doesn't list. Use bases that ship metadata (distroless, Buildpacks), and check the SBOM's coverage.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q11-what-does-an-sbom-miss-l3-razorpay-security-rounds)


**[54. Kubernetes for Spring Developers](../deployment/ch54-kubernetes-for-spring.md)**

??? question "Should JVM services have CPU limits?"
    <span class="badge badge-l3">L3</span> Swiggy · Atlassian

    Often not for latency-sensitive ones. Requests guarantee the share, and limits throttle bursts from startup, JIT and GC. If a limit is policy, keep it at 1–2 CPUs or more, and alert on throttling. Without a limit, set `-XX:ActiveProcessorCount`, or the JVM sizes itself for the whole node.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q2-should-jvm-services-have-cpu-limits-l3-swiggy-atlassian)

??? question "Why don't new pods get traffic after scaling out?"
    <span class="badge badge-l3">L3</span> Flipkart · Uber

    kube-proxy balances connections, and existing keep-alive connections stay on the pods they were opened to. Measured: all pooled requests stayed on the original pod for 50 s, while new connections spread at once. Cap connection lifetime, or balance per request with a mesh or a client-side balancer.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q4-why-dont-new-pods-get-traffic-after-scaling-out-l3-flipkart-uber)

??? question "Env vars or mounted files for secrets?"
    <span class="badge badge-l3">L3</span> banks

    Mounted files. Env vars are visible in `/proc`, inherited by child processes, printed by debug endpoints and captured in heap dumps (chapter 52), and they never rotate. Mounted Secrets update and can be read on demand.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q6-env-vars-or-mounted-files-for-secrets-l3-banks)

??? question "Ingress or Gateway API?"
    <span class="badge badge-l3">L3</span> platform rounds

    The Gateway API for new work. It separates the platform team's Gateways from application teams' routes, supports weighted backends, header matching and gRPC routes natively, and is portable across implementations. Ingress remains supported but limited, with annotations for anything beyond host and path.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q9-ingress-or-gateway-api-l3-platform-rounds)

??? question "What makes a JVM pod OOMKilled on Kubernetes?"
    <span class="badge badge-l3">L3</span> PhonePe

    Total process memory above the memory limit: heap plus metaspace, threads, direct buffers and code cache. Use `MaxRAMPercentage=75` with limit = request, and `ExitOnOutOfMemoryError`. Chapter 53 measured `-Xmx` above the limit as a silent kernel kill.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q11-what-makes-a-jvm-pod-oomkilled-on-kubernetes-l3-phonepe)


**[55. Helm, Kustomize & Environment Promotion](../deployment/ch55-helm-kustomize.md)**

??? question "How do you promote a build from staging to production?"
    <span class="badge badge-l3">L3</span> Razorpay · Atlassian

    Build once, push, and refer to the image by digest. Promotion is a change to prod's values or overlay that copies the digest staging ran, checked by policy. Never rebuild for prod and never promote a tag. Measured: after a tag moved, a scale-out ran two versions behind one Service; pinned by digest, every pod ran the same build.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q2-how-do-you-promote-a-build-from-staging-to-production-l3-razorpay-atlassian)

??? question "Why did our deploy reset the HPA's replica count?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    The manifest sets `replicas`, and a client-side apply (kubectl or Helm 3) resets fields that differ. Measured: 5 → 2. Server-side apply refuses with a conflict instead, and Helm 4 fails the release. The fix is to omit `replicas` when an HPA owns it; then the apply kept 5.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q3-why-did-our-deploy-reset-the-hpas-replica-count-l3-swiggy-flipkart)

??? question "What is server-side apply?"
    <span class="badge badge-l3">L3</span> platform rounds

    The API server merges your intent into the live object and records which manager owns each field. Setting a field someone else owns is a conflict unless forced. It makes shared ownership (you, the HPA, a mesh injector) explicit. Helm 4 uses it by default, and its `upgrade` keeps whatever method the release was installed with.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q4-what-is-server-side-apply-l3-platform-rounds)

??? question "Why did nothing fail while the bad release was crash-looping?"
    <span class="badge badge-l3">L3</span> Uber

    With 3 replicas, the default 25% `maxUnavailable` rounds down to 0, so the Deployment adds a new pod and waits for it before removing an old one. The new pod never became ready, so all three old pods kept serving. With `maxUnavailable: 1`, one old pod went away and capacity stayed at two-thirds.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q7-why-did-nothing-fail-while-the-bad-release-was-crash-looping-l3-uber)

??? question "How do you keep a Helm chart reviewable?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Keep logic in helpers and out of templates, fail loudly on invalid values (`fail`, a values schema), and review the rendered output, not the template diff: CI posts `helm template` diffs for every environment on each PR. Version the chart like a library.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q8-how-do-you-keep-a-helm-chart-reviewable-l3-thoughtworks)

??? question "How do you detect drift between Git and the cluster?"
    <span class="badge badge-l3">L3</span> PhonePe

    Diff rendered manifests against live objects (`kubectl diff`, `helm diff`), or run a GitOps controller that reconciles continuously and reports drift. Note what applying doesn't fix: measured, a hand-added environment variable survived every apply method, because no manifest mentioned it.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q11-how-do-you-detect-drift-between-git-and-the-cluster-l3-phonepe)


**[56. Autoscaling](../deployment/ch56-autoscaling.md)**

??? question "How do JVMs fool the HPA?"
    <span class="badge badge-l3">L3</span> Atlassian · Uber

    Warm-up CPU (class loading, JIT) looks like load. If pods are ready at once, the HPA counts it and scales further. Measured: 8 pods (the maximum) instead of 5. The HPA ignores samples from unready pods and from before a pod became ready within its first five minutes, so fail readiness until warm.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q4-how-do-jvms-fool-the-hpa-l3-atlassian-uber)

??? question "HPA, VPA and cluster autoscaler: how do they fit?"
    <span class="badge badge-l3">L3</span> Walmart · platform rounds

    The HPA changes the number of pods; VPA changes their requests; the cluster autoscaler (or Karpenter) changes the number of nodes. Don't let HPA and VPA both act on CPU for the same workload; use VPA in recommendation mode to set requests, and the HPA to scale.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q6-hpa-vpa-and-cluster-autoscaler-how-do-they-fit-l3-walmart-platform-rounds)

??? question "When would you use KEDA?"
    <span class="badge badge-l3">L3</span> PhonePe

    For event-driven work: Kafka lag, queue depth, cron windows, or anything that should scale to zero. It feeds external metrics to an HPA it manages. CPU lags a backlog; lag doesn't.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q7-when-would-you-use-keda-l3-phonepe)

??? question "What does `behavior` let you tune?"
    <span class="badge badge-l3">L3</span> Razorpay

    Per direction: a stabilization window, and policies limiting change per period (pods or percent), with `selectPolicy` choosing the most or least permissive. Defaults: up by 4 pods or 100% per 15 s with no window; down by up to 100% per 15 s after a 300 s window.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q8-what-does-behavior-let-you-tune-l3-razorpay)

??? question "Should scale-up be as fast as possible?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Only with somewhere for the pods to go. Measured on one busy node, starting four JVMs at once starved the pod already serving and 150–181 requests failed. Fast scale-ups need node headroom, and a minimum that doesn't make every peak start from one pod.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q9-should-scale-up-be-as-fast-as-possible-l3-thoughtworks)

??? question "How do you scale on requests per second instead of CPU?"
    <span class="badge badge-l3">L3</span> Uber

    Expose the metric (Micrometer to Prometheus, chapter 48), serve it through a custom metrics adapter (Prometheus Adapter, or KEDA's Prometheus scaler), and use a `Pods` or `Object` metric with an `AverageValue` target. Useful for I/O-bound services whose CPU stays low.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q10-how-do-you-scale-on-requests-per-second-instead-of-cpu-l3-uber)


**[57. Service Mesh](../deployment/ch57-service-mesh.md)**

??? question "How does mTLS in a mesh authenticate a service?"
    <span class="badge badge-l3">L3</span> Razorpay · banks

    Each workload's certificate carries its identity as a SPIFFE URI SAN, issued by the mesh CA and rotated within hours. Both sides verify the chain and the peer's identity. Measured: the right CA with the wrong service account was refused, as was the right name from another CA.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q3-how-does-mtls-in-a-mesh-authenticate-a-service-l3-razorpay-banks)

??? question "Why would requests still reach a pod around the mesh?"
    <span class="badge badge-l3">L3</span> security rounds

    If traffic isn't redirected and the app listens on all interfaces. Measured: plaintext to the app's port returned 200. Meshes redirect with iptables or ambient mode; with explicit sidecars, the app must listen on localhost, plus NetworkPolicies.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q4-why-would-requests-still-reach-a-pod-around-the-mesh-l3-security-rounds)

??? question "Where should retries live?"
    <span class="badge badge-l3">L3</span> Uber · Swiggy

    In one layer, with a budget, for idempotent requests only, and on a different endpoint. Measured: mesh retries alone hid a bad pod but cost ~1,500 retries per 3,000 requests; with outlier detection, 35. Two layers multiply.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q5-where-should-retries-live-l3-uber-swiggy)

??? question "What does a sidecar cost?"
    <span class="badge badge-l3">L3</span> platform rounds

    Latency per hop and resources per pod. Measured: about half a millisecond at the median per proxy, larger tails, and 208–217m of CPU per Envoy at 500 requests/s, twice the app's. Hence ambient mode and Linkerd's lighter proxy.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q7-what-does-a-sidecar-cost-l3-platform-rounds)

??? question "What is Istio's ambient mode?"
    <span class="badge badge-l3">L3</span> Atlassian

    A sidecarless data plane: a per-node ztunnel does mTLS and L4 policy for every pod, and optional per-namespace or per-service waypoint proxies (Envoy) do L7 features only where needed. It cuts per-pod overhead and removes sidecar injection and restarts.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q8-what-is-istios-ambient-mode-l3-atlassian)

??? question "Mesh or client library?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Client libraries are lighter and see application context; a mesh is uniform across languages and adds identity and mTLS. Many shops do both: timeouts and circuit breakers in the client, balancing, retries and mTLS in the mesh.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q10-mesh-or-client-library-l3-thoughtworks)

??? question "How do you roll out strict mTLS without an outage?"
    <span class="badge badge-l3">L3</span> PhonePe

    Enable the mesh in permissive mode (accepting both), watch telemetry for plaintext callers, fix them, then switch namespaces to strict one at a time, with an exception list that has an expiry date.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q11-how-do-you-roll-out-strict-mtls-without-an-outage-l3-phonepe)


**[58. CI/CD, GitOps & Progressive Delivery](../deployment/ch58-cicd-gitops.md)**

??? question "Push-based or pull-based deployment?"
    <span class="badge badge-l3">L3</span> Atlassian · banks

    Pull keeps credentials in the cluster, corrects drift and prunes deletions; push deploys immediately but forgets the cluster between deploys. Most teams combine CI (push to the registry and the environment repo) with GitOps (pull into clusters).

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q2-push-based-or-pull-based-deployment-l3-atlassian-banks)

??? question "What can't GitOps drift detection see?"
    <span class="badge badge-l3">L3</span> platform rounds

    Fields no manifest declares. Measured: a hand-added environment variable survived three reconcile intervals, because the server-side diff covers only fields the controller owns. Close it with admission policies, or manifests that replace whole lists.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q3-what-cant-gitops-drift-detection-see-l3-platform-rounds)

??? question "Canary or blue/green?"
    <span class="badge badge-l3">L3</span> Flipkart · Walmart

    Canary limits exposure and needs per-version metrics; blue/green switches everything at once but switches back instantly, at double capacity. Measured: the blue/green switch took under a second for new connections, but keep-alive clients stayed on the old slot.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q5-canary-or-bluegreen-l3-flipkart-walmart)

??? question "Why did a healthy canary get aborted?"
    <span class="badge badge-l3">L3</span> Uber

    Measured: a one-pod canary at 50% weight took as much traffic as three stable pods, and its p99 crossed the absolute threshold. Scale the canary with its weight, warm it, and compare latency to stable rather than a fixed number.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q6-why-did-a-healthy-canary-get-aborted-l3-uber)

??? question "Why prune, and why is it off by default in Argo CD?"
    <span class="badge badge-l3">L3</span> PhonePe

    Without pruning, objects deleted from Git live on (measured: pruned in 7–8 s with it). It's off by default because a bad commit or a mis-rendered manifest could delete production objects; teams enable it with protection like sync windows and prune confirmation for critical kinds.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q9-why-prune-and-why-is-it-off-by-default-in-argo-cd-l3-phonepe)

??? question "What signals belong in canary analysis?"
    <span class="badge badge-l3">L3</span> Razorpay

    Per-version request error rate and latency from the edge or mesh, plus business signals where available (checkout conversion). Compare to stable over the same window, require a minimum sample, and keep thresholds tied to SLOs (chapter 51).

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q10-what-signals-belong-in-canary-analysis-l3-razorpay)

??? question "How do keep-alive connections affect releases?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    They stay on the pods they were opened to. Measured: after a blue/green switch, every keep-alive request kept going to the old slot. Drain old pods (graceful shutdown closes connections), cap connection lifetimes, or switch at a per-request proxy.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q11-how-do-keep-alive-connections-affect-releases-l3-thoughtworks)


**[59. Cloud-Native on AWS, Azure & GCP](../deployment/ch59-cloud-native-platforms.md)**

??? question "How does IRSA or Workload Identity actually work?"
    <span class="badge badge-l3">L3</span> Atlassian · Razorpay

    The cluster is an OIDC issuer that signs tokens and publishes its keys. The cloud is configured to trust that issuer, and a role trusts a subject (`system:serviceaccount:ns:name`) and audience. The cloud verifies tokens offline with the published keys and issues credentials for an hour or so.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q2-how-does-irsa-or-workload-identity-actually-work-l3-atlassian-razorpay)

??? question "What happens to a token when its pod is deleted?"
    <span class="badge badge-l3">L3</span> security rounds

    The API server rejects it (measured: within 30 s, after a 10-second cache), but a cloud verifying offline accepts it until it expires. Measured: still accepted with 475 s left. So keep lifetimes short.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q3-what-happens-to-a-token-when-its-pod-is-deleted-l3-security-rounds)

??? question "Why is the default service account token dangerous outside the cluster?"
    <span class="badge badge-l3">L3</span> banks

    Its audience is the API server, and for compatibility it's valid for a year (measured: 31,536,000 s), though the kubelet refreshes it hourly. Never send it anywhere; mint projected tokens with a specific audience instead.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q4-why-is-the-default-service-account-token-dangerous-outside-the-cluster-l3-banks)

??? question "A cloud API is throttling us. What do you do?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    Stop calling it as often: cache, batch, and spread startup calls. Then use adaptive or standard retries with jitter and keep the retry quota. Measured at twice the limit: adaptive mode cut failed calls by over 80% compared with standard, and throttled responses by more than half. Ask for a quota increase only after that.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q5-a-cloud-api-is-throttling-us-what-do-you-do-l3-swiggy-flipkart)

??? question "What is a retry quota?"
    <span class="badge badge-l3">L3</span> Uber

    A token bucket per client: each retry costs tokens (5 of 500 in the AWS SDK), each success returns some. When it's empty, failures return immediately. Measured in an outage: attempts tripled at first, then fell back to one per call once the quota ran out.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q6-what-is-a-retry-quota-l3-uber)

??? question "Functions or containers?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    Functions for spiky, low-volume, event-driven work where cold starts don't matter; containers for steady, latency-sensitive, high-volume services. Measured and priced: in our model functions cost cents a day where two always-on containers cost $2.37, and stayed cheaper than containers sized for the peak even at 1,000 requests a second; only containers scaling minute by minute with the load beat them, between 200 and 1,000 requests a second. A JVM cold start was about 1.4 s.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q7-functions-or-containers-l3-zomato-phonepe)

??? question "How do you make Java functions start fast?"
    <span class="badge badge-l3">L3</span> Accenture

    SnapStart (a CRaC-style snapshot) on Lambda, GraalVM native images, an AOT cache, smaller dependency graphs, and provisioned concurrency for latency-critical paths. Chapter 60 measures native images and AOT caches.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q8-how-do-you-make-java-functions-start-fast-l3-accenture)

??? question "Managed Kafka or self-run?"
    <span class="badge badge-l3">L3</span> Walmart

    Managed (MSK, Event Hubs' Kafka endpoint, Google's managed Kafka, Confluent Cloud) unless you need a feature or scale they don't offer, or the cost difference at your volume pays for a platform team. You still own topic design, partitions, client configuration and consumer lag (chapters 25 and 69).

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q9-managed-kafka-or-self-run-l3-walmart)

??? question "EKS, AKS or GKE: what actually differs?"
    <span class="badge badge-l3">L3</span> platform rounds

    Mostly the surroundings: identity integration, networking, node management (Karpenter, node auto-provisioning, Autopilot), control-plane pricing and upgrade cadence. Workloads written for Kubernetes move with little change; identity, load balancers and storage classes are what you rewrite.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q10-eks-aks-or-gke-what-actually-differs-l3-platform-rounds)


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

??? question "What does the JDK 25 AOT cache add over CDS?"
    <span class="badge badge-l3">L3</span> Oracle · Atlassian

    Classes are stored loaded **and linked** (JEP 483), the training run is one step with `-XX:AOTCacheOutput` (JEP 514), and method profiles are recorded so the JIT starts compiling sooner (JEP 515). Measured: 611–732 ms at 1 CPU and 2.0–2.1 s at 0.25 CPU, the fastest JVM variant.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q3-what-does-the-jdk-25-aot-cache-add-over-cds-l3-oracle-atlassian)

??? question "What makes a good training run?"
    <span class="badge badge-l3">L3</span> Flipkart

    The real service doing its real work through its real entry points. Measured: a cache trained without the HTTP server saved about half as much, at 1 CPU and at 0.25 CPU. With Spring Boot, `-Dspring.context.exit=onRefresh` covers the context; add recorded traffic for the request path.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q4-what-makes-a-good-training-run-l3-flipkart)

??? question "What happens if the cache doesn't match?"
    <span class="badge badge-l3">L3</span> Red Hat

    With the default `-XX:AOTMode=auto`, the JVM starts without it, and the only sign is a slower start. With `-XX:AOTMode=on` an unusable cache stops the JVM, which is what a CI smoke test should use. Train and run with the same JDK and class path.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q5-what-happens-if-the-cache-doesnt-match-l3-red-hat)

??? question "GraalVM native image: what do you gain and lose?"
    <span class="badge badge-l3">L3</span> Swiggy · Zomato

    Gain: startup in tens of milliseconds (246–289 ms here, mostly Docker), memory in single-digit MiB (7.5–7.8 idle), small images (50.5 MB). Lose: peak throughput (994–1,026 against 1,411–1,701 requests a second on the JVM, with CE's Serial GC and no profile-guided optimisation), a 205 s, 1.73 GB build, and the closed-world assumption: reflection and resources must be declared.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q6-graalvm-native-image-what-do-you-gain-and-lose-l3-swiggy-zomato)

??? question "How does a native image know about reflection?"
    <span class="badge badge-l3">L3</span> Uber

    From reachability metadata: shipped by libraries (the GraalVM metadata repository), generated by frameworks (Spring AOT), or recorded by the native-image agent during a training run. Anything the agent didn't see fails at runtime, so tests should run against the executable.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q7-how-does-a-native-image-know-about-reflection-l3-uber)

??? question "What is CRaC, and how is it different?"
    <span class="badge badge-l3">L3</span> Azul · AWS

    Coordinated Restore at Checkpoint snapshots a running, warmed-up JVM with CRIU and restores it later, so both startup and JIT warm-up are skipped. Code implements `jdk.crac.Resource` to close and reopen connections around the checkpoint; Spring does it for its lifecycle beans (`spring.context.checkpoint=onRefresh`). It needs CRIU and Linux privileges, which is why it didn't run in this lab's VM. SnapStart is the same idea managed by Lambda.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q8-what-is-crac-and-how-is-it-different-l3-azul-aws)

??? question "Should a pod warm up before reporting ready?"
    <span class="badge badge-l3">L3</span> PhonePe

    Briefly, with a time budget. Measured: a 3,000-request warm-up gave 6–10 ms first requests instead of 27–48 ms, but took 4.3 s at 1 CPU and 17.5 s at 0.25 CPU. Chapter 56 showed the other side: pods that are ready before they're warm over-scale the service.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q9-should-a-pod-warm-up-before-reporting-ready-l3-phonepe)

??? question "Why does the CPU limit matter so much for startup?"
    <span class="badge badge-l3">L3</span> Amazon

    JVM startup and JIT compilation are CPU work. At a quarter of a CPU every JVM variant took about three and a half times longer to start; the native image didn't change. Give pods more CPU at startup (no CPU limit, or in-place resize) or cut the startup work.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q10-why-does-the-cpu-limit-matter-so-much-for-startup-l3-amazon)


### Foundations


**[1. Monolith vs SOA vs Microservices vs Modular Monolith](../foundations/ch01-architecture-styles.md)**

??? question "What is a distributed monolith and how do you detect one?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Services that are physically separate but logically coupled, so they must change and deploy together. You pay all the costs of distribution and get none of the independence.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q3-what-is-a-distributed-monolith-and-how-do-you-detect-one-l2l3-flipkart-amazon)

??? question "When would you not use microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    With few teams, a domain still being discovered, no platform maturity (CI/CD, observability, on-call), heavy cross-entity transactional needs, or uniform load. Start with a modular monolith and extract when a module earns it.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q5-when-would-you-not-use-microservices-l2l3-amazon-goldman-sachs)

??? question "How do you decide which module to extract first?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon · SAP

    Extract where independence creates measurable value: a distinct scaling profile, a distinct compliance scope, a team that will own it, failure isolation needs, and low synchronous coupling with clean data ownership. Avoid modules that need ACID transactions with others.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q6-how-do-you-decide-which-module-to-extract-first-l3l4-flipkart-amazon-sap)

??? question "Order and kitchen are separate modules or services. How do you keep them consistent?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Flipkart

    Each side commits locally. The producer records the event in the same transaction as its state change (event publication registry or outbox). Consumers process at least once and idempotently. Multi-step business workflows use a saga with compensations.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q7-order-and-kitchen-are-separate-modules-or-services-how-do-you-keep-them-consistent-l3-goldman-sachs-jp-morgan-flipkart)

??? question "A method call becomes a remote call during extraction. What changes in your code?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Everything about failure. You need timeouts, a policy for "couldn't ask" versus "the answer was no", retries only for idempotent operations, circuit breakers, no database transaction held across the call, versioned contracts, and tracing.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q8-a-method-call-becomes-a-remote-call-during-extraction-what-changes-in-your-code-l3-amazon-flipkart)

??? question "Can a monolith scale to Flipkart or Swiggy traffic?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style product companies

    Stateless monoliths scale horizontally, and many very large sites ran (and some still run) on well-modularised monoliths. The real limits are database write throughput, differing scaling profiles, and organisational scaling (many teams in one codebase), not request throughput as such.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q9-can-a-monolith-scale-to-flipkart-or-swiggy-traffic-l2l3-flipkart-swiggy-style-product-companies)


**[2. Characteristics & Principles](../foundations/ch02-characteristics-principles.md)**

??? question "What does 'design for failure' mean in code?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Timeouts on every remote call. Retries only for idempotent operations and transient errors, with backoff and jitter. Bounded concurrency. Circuit breakers. A defined degradation mode per dependency. And tests for each failure path.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q5-what-does-design-for-failure-mean-in-code-l2l3-amazon-goldman-sachs)

??? question "Your service calls three downstream services. How do you set timeouts?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Start from the caller's latency SLO and allocate a budget per hop, using each dependency's measured p99 plus headroom. Make sure retries fit inside the budget. Propagate a deadline so downstream services stop work the caller has abandoned.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q6-your-service-calls-three-downstream-services-how-do-you-set-timeouts-l3-flipkart-amazon)

??? question "Fail open or fail closed?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    It depends on the cost of being wrong. Enrichments (recommendations, surge display) fail open. Money movement, fraud checks and irreversible actions fail closed into a safe, recoverable state. The choice is documented per dependency.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q7-fail-open-or-fail-closed-l3-goldman-sachs-jp-morgan)

??? question "Is code duplication across services acceptable?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · SAP

    Often yes. DRY across service boundaries creates coupling through shared libraries. Share technical concerns (logging, tracing setup), never domain models. Duplicate small DTOs and own them.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q8-is-code-duplication-across-services-acceptable-l2l3-amazon-sap)

??? question "How does decentralized data management affect reporting?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    You can't join across service databases, so you replicate. Services publish events or CDC streams into an analytics store (warehouse or lake) where reporting joins happen. Operational queries across services use API composition or dedicated read models.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q10-how-does-decentralized-data-management-affect-reporting-l3-goldman-sachs-flipkart)

??? question "Spring Framework 7 has `@Retryable`. Do you still need Resilience4j?"
    <span class="badge badge-l3">L3</span> any Spring shop

    For simple retries and concurrency throttling, the built-in annotations are enough and need no extra dependency. Resilience4j still provides circuit breakers, rate limiters, time limiters, fail-fast bulkheads and rich metrics, which most production services need for their critical dependencies ([ch 43](../resilience/ch43-resilience4j-deep-dive.md)).

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q11-spring-framework-7-has-retryable-do-you-still-need-resilience4j-l3-any-spring-shop)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Explain the CAP theorem precisely."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    During a network partition, a replicated system must choose between linearizable consistency and availability. Partition tolerance isn't optional, and the choice only applies while partitioned.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q2-explain-the-cap-theorem-precisely-l2l3-amazon-flipkart-goldman-sachs)

??? question "What is PACELC and why is it more useful day to day?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    If there's a Partition, choose Availability or Consistency; Else, choose Latency or Consistency. Partitions are rare but latency is paid on every request, so the E side drives most design decisions, such as synchronous versus asynchronous replication.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q3-what-is-pacelc-and-why-is-it-more-useful-day-to-day-l3-goldman-sachs-sap)

??? question "Explain consistency models. Which would you choose for an order-status page?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style product companies

    From linearizable through causal and session guarantees to eventual. For an order-status page: linearizable writes inside order-service (versioned transitions), and **monotonic reads** for the customer so status never goes backwards. Read-your-writes if the customer can change something on that page.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q4-explain-consistency-models-which-would-you-choose-for-an-order-status-page-l3-flipkart-swiggy-style-product-companies)

??? question "Why can't we order events by timestamp? What do we use instead?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Clocks drift and can jump backwards, so cross-machine timestamps can't establish order. Use Lamport clocks for a total order consistent with causality, vector clocks to detect concurrency, HLC for causality close to real time, or the database's own commit order and versions.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q5-why-cant-we-order-events-by-timestamp-what-do-we-use-instead-l3-amazon-jp-morgan)

??? question "Lamport clocks versus vector clocks?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Lamport: if a → b then L(a) < L(b), but not the converse, so it can't detect concurrency. Vector clocks capture causality exactly (before, after, concurrent) at the cost of one counter per node or writer.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q6-lamport-clocks-versus-vector-clocks-l3-goldman-sachs-amazon)

??? question "What is 'exactly-once' and is it possible?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Exactly-once *delivery* over an unreliable network isn't achievable. Exactly-once *effects* are: at-least-once delivery plus idempotent processing, with the dedup record committed atomically with the effect. Kafka's EOS covers read-process-write within Kafka. External side effects still need idempotency.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q7-what-is-exactly-once-and-is-it-possible-l2l3-flipkart-amazon-goldman-sachs)

??? question "Design an idempotency-key mechanism for a payments API."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · Amazon

    The client generates a unique key per logical operation. The server atomically claims it (insert-if-absent with a unique constraint), stores a fingerprint of the request, runs the operation, and stores the response. Same key and body → replay the stored response. Same key while running → 409. Same key with a different body → 422. Failures release the key. Keys expire after a retention period longer than any retry horizon.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q8-design-an-idempotency-key-mechanism-for-a-payments-api-l3l4-jp-morgan-goldman-sachs-amazon)

??? question "With N = 3, which R and W give strong consistency? Trade-offs?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    Any R + W > N: (2, 2) is balanced; (3, 1) gives fast reads but writes fail if any replica is down; (1, 3) is the reverse. Even with R + W > N, concurrent writes need conflict resolution, and "sloppy quorums" with hinted handoff weaken the guarantee during failures.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q9-with-n-3-which-r-and-w-give-strong-consistency-trade-offs-l3-amazon-sap)

??? question "A customer updates their address and immediately sees the old one. Why, and how do you fix it?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style

    The read went to an asynchronously replicated replica that hadn't caught up. Provide read-your-writes: route that user's reads to the primary for a short window, or return a version token on write and require a replica at least that fresh.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q10-a-customer-updates-their-address-and-immediately-sees-the-old-one-why-and-how-do-you-fix-it-l3-flipkart-swiggy-style)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "What does 'stateless' mean for a service that keeps WebSocket connections?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style

    Connections are inherently tied to a pod, but *state* must not be. Fan out updates through Redis or Kafka so any pod can push to its connected clients. Clients reconnect to any pod after a restart and resume from a cursor.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q4-what-does-stateless-mean-for-a-service-that-keeps-websocket-connections-l3-flipkart-swiggy-style)

??? question "Explain graceful shutdown on Kubernetes."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Kubernetes removes the pod from endpoints and sends SIGTERM at about the same time. The app stops accepting new connections, finishes in-flight work within a timeout, releases resources and exits. Add a short `preStop` sleep because endpoint removal is asynchronous, and set `terminationGracePeriodSeconds` higher than the app's shutdown timeout.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q5-explain-graceful-shutdown-on-kubernetes-l2l3-amazon-jp-morgan)

??? question "How do you run database migrations in a twelve-factor way?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    Versioned migrations (Flyway or Liquibase) in the codebase, applied by the release: on startup for simple services, or as a pre-deploy Job for large changes. Use expand/contract so old and new versions run against the same schema during rolling deploys. Never apply them by hand.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q6-how-do-you-run-database-migrations-in-a-twelve-factor-way-l3-goldman-sachs-sap)

??? question "How do you make a Spring Boot service start fast?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Measure first. Then use a layered image, and on Java 25 an AOT cache trained at build time (JEP 483 and 514). Consider CRaC for checkpoint/restore, or GraalVM native images when startup and memory dominate and their build constraints are acceptable. Avoid heavy work in the startup path, and lazily initialise optional components.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q8-how-do-you-make-a-spring-boot-service-start-fast-l3l4-amazon-sap)

??? question "Your scheduled job runs in every replica after scaling to 5 pods. What's wrong and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any company

    Factors VIII and XII. The process model assumes replicas are interchangeable, so in-process schedulers multiply. Use a Kubernetes CronJob, a distributed lock (ShedLock) with fencing where correctness matters, or a dedicated single-replica worker.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q9-your-scheduled-job-runs-in-every-replica-after-scaling-to-5-pods-whats-wrong-and-how-do-you-fix-it-l2l3-any-company)

??? question "What did 'Beyond the Twelve-Factor App' add, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    API first (contracts enable parallel teams), telemetry (you can't operate what you can't see), and authentication and authorization (security can't be bolted on in a zero-trust, many-service world). It also reinterpreted some factors for containers and microservices.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q10-what-did-beyond-the-twelve-factor-app-add-and-why-l3l4-sap-goldman-sachs)


**[5. Domain-Driven Design](../foundations/ch05-domain-driven-design.md)**

??? question "What is an aggregate and how do you choose its boundary?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    A cluster of objects treated as one consistency unit, accessed through its root. The boundary comes from **invariants that must hold at every commit**. Keep aggregates small, reference others by id, and use eventual consistency between them.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q3-what-is-an-aggregate-and-how-do-you-choose-its-boundary-l3-amazon-flipkart-goldman-sachs)

??? question "Why only one aggregate per transaction?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    It keeps locks small and transactions short, avoids contention, and keeps each aggregate independently storable and scalable, which is a prerequisite for splitting contexts into services. Changes elsewhere follow via domain events in their own transactions.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q4-why-only-one-aggregate-per-transaction-l3-amazon-jp-morgan)

??? question "Domain service vs application service?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · service companies

    A domain service holds domain logic that doesn't fit one entity (`PricingPolicy`). An application service orchestrates a use case: load, call the domain, save, publish. It contains no business rules.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q5-domain-service-vs-application-service-l2l3-sap-service-companies)

??? question "What is an anti-corruption layer? When do you need one?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs · SAP

    A translation layer that converts an upstream model into the downstream's own model, protecting the downstream from upstream quirks and changes. Use it with legacy systems, external vendors, volatile upstreams, or whenever the upstream model doesn't fit your domain.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q6-what-is-an-anti-corruption-layer-when-do-you-need-one-l3-jp-morgan-goldman-sachs-sap)

??? question "Explain the context map patterns."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Amazon

    Partnership, Shared Kernel, Customer–Supplier, Conformist, Anti-Corruption Layer, Open Host Service, Published Language, Separate Ways. They describe both the technical integration and the **power relationship** between teams: who adapts to whom.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q7-explain-the-context-map-patterns-l3l4-sap-amazon)

??? question "How do you find bounded contexts in a new domain?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · consulting firms

    Talk to domain experts. Run Event Storming: domain events on a timeline, then commands, actors, policies and aggregates. Look for language changes ("order" becomes "ticket" becomes "delivery job"), different rules and lifecycles, and team and process boundaries. Validate with the context map and the expected rate of change. [Chapter 6](../foundations/ch06-service-decomposition.md) walks through it.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q9-how-do-you-find-bounded-contexts-in-a-new-domain-l3l4-flipkart-consulting-firms)

??? question "Bounded context vs microservice: always one-to-one?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    No. A context is a model boundary, a service a deployment boundary. One context can be several services (for scaling), and several contexts can live in one modular monolith. One service spanning several contexts is a smell.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q10-bounded-context-vs-microservice-always-one-to-one-l3l4-amazon-sap)

??? question "What's wrong with an anemic domain model?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    The rules live outside the objects, so invalid states are possible and rules get duplicated and drift. It's fine for CRUD-like supporting subdomains, and costly for core domains with real invariants.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q11-whats-wrong-with-an-anemic-domain-model-l2l3-goldman-sachs-flipkart)

??? question "How do domain events relate to integration events?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Domain events are facts inside a context, and can evolve with the model. Integration events are the **published language** for other contexts: versioned, documented, backward-compatible, often a reduced projection. Many teams translate domain events into integration events at the boundary (an outbox writer).

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q12-how-do-domain-events-relate-to-integration-events-l3-amazon-flipkart)

??? question "How do you keep the domain model independent of Spring and JPA?"
    <span class="badge badge-l3">L3</span> any Spring shop

    Plain Java domain and application layers; ports defined in the domain; adapters (persistence, messaging, HTTP) in infrastructure; wiring in a composition root (`@Configuration`); ArchUnit rules that fail the build if the domain imports a framework. Map to persistence explicitly or use Spring Data JDBC, which respects aggregate boundaries.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q13-how-do-you-keep-the-domain-model-independent-of-spring-and-jpa-l3-any-spring-shop)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

??? question "How do you decide service boundaries?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Start from business capabilities and bounded contexts, discovered with domain experts (Event Storming). Group things that change together and must be consistent together; separate things with different owners, scaling needs, volatility or compliance requirements. Validate candidates against real use cases (how many calls per checkout?) and against change history, and start as modules before making them services.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q1-how-do-you-decide-service-boundaries-l2l3-cognizant-sap-flipkart)

??? question "What is Event Storming and what do you get out of it?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart · consulting firms

    A workshop where domain experts and engineers place domain events (orange) on a timeline, then add commands, actors, policies, aggregates, external systems and hotspots. You get a shared understanding of the process, a list of open questions, aggregate candidates, and boundary candidates where the language changes and at pivotal events. Policies between lanes usually become asynchronous integration events.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q3-what-is-event-storming-and-what-do-you-get-out-of-it-l3-sap-flipkart-consulting-firms)

??? question "How big should a microservice be?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · TCS · Infosys

    Not a line count. Owned by one team, changed for one reason, deployable on its own, owning its data, and understandable by a new team member in a couple of weeks. Too small shows up as chatty call chains and shotgun changes; too large shows up as several teams negotiating releases.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q4-how-big-should-a-microservice-be-l2l3-amazon-tcs-infosys)

??? question "What goes wrong when you decompose by entity?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Business operations span many services, so use cases become long synchronous call chains: more latency, lower availability (0.999<sup>11</sup> ≈ 98.9% for 11 services on a path), and invariants that used to be one transaction now need sagas. Features need coordinated changes across services. It's a distributed monolith.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q5-what-goes-wrong-when-you-decompose-by-entity-l3-flipkart-amazon-goldman-sachs)

??? question "What are granularity disintegrators and integrators?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan · Amazon

    From *Software Architecture: The Hard Parts*. Disintegrators justify splitting: scope and function, code volatility, scalability, fault tolerance, security, extensibility. Integrators justify keeping together: database transactions, workflow coupling, shared code, data relationships. A boundary is right when the disintegrators clearly outweigh the integrators, and you write down which ones.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q6-what-are-granularity-disintegrators-and-integrators-l3l4-goldman-sachs-jp-morgan-amazon)

??? question "A checkout path has 6 services at 99.9% each. What's its availability, and what do you do about it?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    0.999<sup>6</sup> ≈ 99.4%, about 4.3 hours of downtime a month if failures are independent. Options: fewer services on the sync path (merge, or make steps async), higher availability per service, and fallbacks so non-critical dependencies (coupon, recommendations) degrade instead of failing checkout.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q8-a-checkout-path-has-6-services-at-999-each-whats-its-availability-and-what-do-you-do-about-it-l3-amazon-flipkart)

??? question "What are the signs that a decomposition is wrong?"
    <span class="badge badge-l3">L3</span> Flipkart · Cognizant · Amazon

    Shotgun changes across services, lock-step deploys, chatty sync chains, proposals for distributed transactions, shared tables, generic "common" or "manager" services, cyclic calls, and services whose releases need several teams' sign-off.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q10-what-are-the-signs-that-a-decomposition-is-wrong-l3-flipkart-cognizant-amazon)

??? question "How do you split the database when you split a service?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · SAP

    Give each table one owning service; others use the owner's API or subscribe to its events and keep a local read model. Do it in steps: split schemas inside one database, remove cross-schema joins and foreign keys, then move schemas to separate databases. [Chapter 12](../architecture-patterns/ch12-database-per-service.md) covers the patterns and [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md) the migration.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q11-how-do-you-split-the-database-when-you-split-a-service-l3l4-jp-morgan-sap)


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "What is Conway's Law?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · TCS · SAP

    Systems end up mirroring the communication structure of the organisation that builds them (Melvin Conway, 1968). Teams that talk easily produce tightly integrated parts; teams that rarely talk produce formal interfaces between their parts.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q1-what-is-conways-law-l2l3-cognizant-tcs-sap)

??? question "What is the inverse Conway maneuver?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Deliberately shape teams to match the architecture you want, so that Conway's Law produces it. In practice teams, ownership, data and architecture must change together; changing only the org chart doesn't work.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q2-what-is-the-inverse-conway-maneuver-l3-flipkart-amazon)

??? question "Explain the four team types in Team Topologies."
    <span class="badge badge-l3">L3</span> SAP · Amazon · JP Morgan

    **Stream-aligned** (owns a stream of change end to end; the default), **platform** (self-service internal services that reduce others' load), **complicated-subsystem** (owns something needing rare specialist skills), **enabling** (coaches teams in a capability, then leaves). Most teams should be stream-aligned; the others exist to make stream-aligned teams fast.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q3-explain-the-four-team-types-in-team-topologies-l3-sap-amazon-jp-morgan)

??? question "What are the interaction modes, and how do you choose?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    **Collaboration** (close joint work, for discovery, time-boxed), **X-as-a-Service** (consume an API or platform with little coordination, for well-understood boundaries), **facilitating** (an enabling team coaching). Choose by how well the boundary is understood; expect modes to evolve from collaboration to X-as-a-Service.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q4-what-are-the-interaction-modes-and-how-do-you-choose-l3l4-goldman-sachs-sap)

??? question "What is cognitive load and why does it matter for microservices?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    The amount a team must keep in mind to do its work. Microservices multiply what a team has to know (deployments, observability, contracts, on-call). If a team's load exceeds its capacity, quality and speed drop. Limit domains per team, and use platforms to remove extraneous load.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q5-what-is-cognitive-load-and-why-does-it-matter-for-microservices-l3-amazon-flipkart)

??? question "Strong vs weak vs collective code ownership?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Strong: only the owner changes the code. Weak: anyone may change it, the owner reviews. Collective: no owner. For production services, one owning team per service (strong or weak with mandatory owner review); collective ownership only inside a single team.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q7-strong-vs-weak-vs-collective-code-ownership-l2l3-sap-cognizant)

??? question "What makes a good platform team?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    It treats the platform as a product for internal users: self-service, documented, with a roadmap and satisfaction metrics; it builds the thinnest viable platform (buy what you can); it's consumed as a service, and collaborates only to discover new features. A platform that needs tickets for routine work is a bottleneck, not a platform.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q9-what-makes-a-good-platform-team-l3l4-amazon-jp-morgan)

??? question "Who should own the shared database during a migration to microservices?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · SAP

    In the end, nobody: each service owns its data. During migration, assign each table to the team owning the capability that writes it, even while it physically lives in the shared database; other teams read through APIs or views, and DBA expertise moves to an enabling or platform role. That is the first step of database per service.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q11-who-should-own-the-shared-database-during-a-migration-to-microservices-l3l4-jp-morgan-sap)

??? question "Is the Spotify model a good template?"
    <span class="badge badge-l3">L3</span> startups · consulting firms

    It's a snapshot of how one company described its teams around 2012, and people who worked there have said it wasn't fully implemented even then. Copying the labels (squads, tribes, chapters, guilds) doesn't reproduce the outcome. Start from your own streams of change, cognitive load and interactions; Team Topologies gives vocabulary for that, not a template.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q12-is-the-spotify-model-a-good-template-l3-startups-consulting-firms)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "How do virtual threads work, and when should you not use them?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    They're JVM-scheduled threads that unmount from a carrier platform thread when they block on I/O, so blocking code scales to many concurrent requests. They don't help CPU-bound work. Don't pool them, avoid heavy `ThreadLocal` caches, and put explicit limits on shared resources. On Java 21–23, long `synchronized` blocks around I/O pin the carrier; Java 24 fixed that (JEP 491).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q2-how-do-virtual-threads-work-and-when-should-you-not-use-them-l2l3-amazon-flipkart-goldman-sachs)

??? question "Virtual threads or WebFlux for a new service?"
    <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    For request/response services that call databases and other services, Spring MVC with virtual threads: simpler code, same scalability for I/O-bound work, every blocking library works. WebFlux when you need streaming, back-pressure, or a fully non-blocking gateway.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q3-virtual-threads-or-webflux-for-a-new-service-l3-flipkart-jp-morgan)

??? question "How does Spring Boot 4's API versioning work?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    Configure where the version comes from (header, query parameter, path segment or media type parameter) with `spring.mvc.apiversion.*`, then add `version = "1.0"` or `"2.0+"` to request mappings. Unsupported versions get 400. A default version can serve old clients. An `ApiVersionDeprecationHandler` bean adds `Deprecation`, `Sunset` and `Link` headers to deprecated versions. Clients (RestClient, HTTP service clients) can send the version automatically.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q4-how-does-spring-boot-4s-api-versioning-work-l2l3-sap-amazon)

??? question "What changed in Jackson 3 that can break a running system?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    New packages (`tools.jackson`), immutable `JsonMapper`, unchecked exceptions, and new defaults: ISO-8601 dates instead of timestamps, errors for `null` into primitives and for trailing tokens, alphabetical property order, enums read and written with `toString()`. Each can change your API's output or reject input that used to work. Pin output with contract tests and switch defaults deliberately.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q5-what-changed-in-jackson-3-that-can-break-a-running-system-l3-goldman-sachs-jp-morgan)

??? question "What are `@Retryable` and `@ConcurrencyLimit` in Spring Framework 7, and when are they not enough?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Annotations in `org.springframework.resilience.annotation`, enabled with `@EnableResilientMethods`. `@Retryable` retries selected exceptions with backoff and jitter; `@ConcurrencyLimit` caps concurrent invocations (a simple bulkhead). They don't provide circuit breakers, rate limiters or time limiters: use Resilience4j (Spring Cloud CircuitBreaker) for those (chapter 18).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q7-what-are-retryable-and-concurrencylimit-in-spring-framework-7-and-when-are-they-not-enough-l3-amazon-flipkart)

??? question "What is JSpecify and why did Spring adopt it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · any Spring shop

    A standard set of nullness annotations (`@NullMarked`, `@Nullable`, `@NonNull`, `@NullUnmarked`) agreed by several vendors. Spring Framework 7 and Boot 4 annotate their APIs with it, so tools like NullAway, IntelliJ and Kotlin see consistent nullness from the framework down to your code. `@NullMarked` on a package makes non-null the default.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q8-what-is-jspecify-and-why-did-spring-adopt-it-l2l3-sap-any-spring-shop)

??? question "How do you make a Spring Boot service start faster in Kubernetes?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Measure first. Then: trim the classpath (modular starters), lazy-initialize only where safe, use the **AOT cache** (Java 25: `-XX:AOTCacheOutput` training run in the Dockerfile, `-XX:AOTCache` at runtime), optionally Spring AOT on the JVM, and GraalVM native images for scale-to-zero. Tune CPU requests: JIT and startup are CPU-hungry. Use startup probes so slow starts don't trigger restarts.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q9-how-do-you-make-a-spring-boot-service-start-faster-in-kubernetes-l3l4-amazon-goldman-sachs)


### Governance & Organization


**[71. API Governance & Developer Portals](../governance-org/ch71-api-governance.md)**

??? question "What is API governance?"
    <span class="badge badge-l3">L3</span> TCS · Infosys

    The rules for designing, changing and retiring APIs, and how they're enforced. Effective governance is mostly automated: a style guide as linting rules in CI, a versioning and deprecation policy, a catalog of APIs and owners, and human review only where rules can't judge.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q1-what-is-api-governance-l3-tcs-infosys)

??? question "What does Spectral do, and what can't it do?"
    <span class="badge badge-l3">L3</span> Postman · Atlassian

    It runs rulesets over OpenAPI and AsyncAPI documents and reports findings with severities. Its recommended rules check completeness; design rules must be added. Measured: the recommended rules caught 10 of 33 planted problems, house rules 16 more, and 7 (idempotency, sensitive data in URLs, errors as 200, units) needed a reviewer.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q2-what-does-spectral-do-and-what-cant-it-do-l3-postman-atlassian)

??? question "How do you introduce lint rules to existing APIs?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Record each API's current findings as a baseline and fail only on new ones; shrink the baseline over time. Measured: order-service's 7 findings were baselined; a change was judged on its 3 new ones and credited with 1 fix.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q3-how-do-you-introduce-lint-rules-to-existing-apis-l3-thoughtworks)

??? question "How should FoodFleet version its APIs?"
    <span class="badge badge-l3">L3</span> Swiggy

    Additive changes need no version (consumers ignore unknown fields); breaking changes get a new version in the `API-Version` header (Spring Framework 7 supports header versioning), running side by side with the old one until its sunset. No versions in URLs.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q4-how-should-foodfleet-version-its-apis-l3-swiggy)

??? question "How do you deprecate an endpoint safely?"
    <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Mark it in the document (`deprecated`, `x-sunset`), serve `Deprecation`, `Sunset` and `Link` headers, have client libraries warn, track usage by caller, contact the remaining callers, brown out near the date, and remove only when usage is zero. Measured: two of four callers saw nothing because they used plain HTTP.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q5-how-do-you-deprecate-an-endpoint-safely-l3-flipkart-razorpay)

??? question "How do you know who calls your API?"
    <span class="badge badge-l3">L3</span> Uber

    From traffic, not declarations: a client identity on every call (`X-Client-Id`, mTLS identity, OAuth client ID) logged at the gateway or mesh. Measured: the catalog declared 6 consumers of payment-service; the log saw 8, and both undeclared ones used the deprecated operation.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q7-how-do-you-know-who-calls-your-api-l3-uber)

??? question "What is Backstage, and what makes a catalog useful?"
    <span class="badge badge-l3">L3</span> Spotify · Red Hat

    An open-source developer portal around a software catalog of components, APIs, systems and owners, described in files kept with the code. A catalog is useful when it's correct, so check it in CI. Measured: 15 of 31 entities failed a governance check, and only 3 of those broke Backstage's required fields.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q8-what-is-backstage-and-what-makes-a-catalog-useful-l3-spotify-red-hat)

??? question "Should an architecture board review every API?"
    <span class="badge badge-l3">L3</span> Atlassian

    No; it becomes a queue. Automate what rules can check (26 of 33 problems here), and review only new APIs and breaking changes, with a checklist for the rest (the 7 that needed a person).

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q9-should-an-architecture-board-review-every-api-l3-atlassian)

??? question "How do you govern event schemas?"
    <span class="badge badge-l3">L3</span> Confluent

    The same way: AsyncAPI documents or schemas in a registry with compatibility modes (chapter 27), linted in CI, owned in the catalog, versioned semantically (Zalando's events guideline), with consumers tracked by consumer group.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q11-how-do-you-govern-event-schemas-l3-confluent)


**[72. Platform Engineering & Golden Paths](../governance-org/ch72-platform-engineering.md)**

??? question "What is platform engineering?"
    <span class="badge badge-l3">L3</span> Thoughtworks · Infosys

    Building and running an internal developer platform (self-service tools, templates, infrastructure and documentation) as a product for the organisation's developers, so stream-aligned teams can deliver without tickets or deep infrastructure knowledge.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q1-what-is-platform-engineering-l3-thoughtworks-infosys)

??? question "Why do service templates drift, and what do you do about it?"
    <span class="badge badge-l3">L3</span> Atlassian

    Teams change their services, and the platform changes the template. Without an update path, old services fall behind (measured: 8% conformance for the previous template version against 96% for the current one). Record each service's template version and merge template changes into services.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q3-why-do-service-templates-drift-and-what-do-you-do-about-it-l3-atlassian)

??? question "How do you update services generated from an older template?"
    <span class="badge badge-l3">L3</span> Red Hat

    A three-way merge per file: old template as base, the service as ours, the new template as theirs (Copier's `update`, using `git merge-file`). Measured: 26 of 32 files replaced or added, 2 merged with teams' changes, 4 conflicts, and conformance from 4 to 40 of 48. Never regenerate; it discards teams' fixes.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q4-how-do-you-update-services-generated-from-an-older-template-l3-red-hat)

??? question "What goes into a service template?"
    <span class="badge badge-l3">L3</span> Swiggy

    Everything a service needs that isn't its business logic: build, Dockerfile with JVM flags, manifests (requests, probes, PDB), autoscaling and VPA, catalog entry, API description, CI with linting, dashboards and alerts. Here: 90 lines of platform files to 45 of starter code.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q5-what-goes-into-a-service-template-l3-swiggy)

??? question "Should the platform be mandatory?"
    <span class="badge badge-l3">L3</span> Netflix

    Make it the easiest choice, not the only one. Teams leave it for good reasons; scorecards show the difference, and the team owns what it takes on. Gate only on validity and security essentials. Mandated platforms tend to grow shadow alternatives.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q6-should-the-platform-be-mandatory-l3-netflix)

??? question "How do you know where every service stands?"
    <span class="badge badge-l3">L3</span> Uber

    Conformance checks per decision, run in each repository's CI and shown as scorecards in the portal (Backstage Tech Insights, Cortex, OpsLevel). Measured across 12 services: from 0 to 12 of 12, with averages of 96%, 42% and 8% by origin.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q7-how-do-you-know-where-every-service-stands-l3-uber)

??? question "What is Backstage's scaffolder?"
    <span class="badge badge-l3">L3</span> Spotify

    Backstage's template engine: a `Template` entity (`scaffolder.backstage.io/v1beta3`, with a type and steps) whose steps fetch and render a skeleton (`fetch:template`), publish a repository and register it in the catalog. It creates services; updating them needs something else, such as Copier-style merges.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q9-what-is-backstages-scaffolder-l3-spotify)

??? question "What should the platform team not own?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    The services. The platform owns the paths (templates, tools, infrastructure, checks) and their updates; stream-aligned teams own their services, including decisions to differ. A platform team that ends up running services becomes a bottleneck.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q11-what-should-the-platform-team-not-own-l3-thoughtworks)


**[73. ADRs, Fitness Functions & Tech Radar](../governance-org/ch73-adrs-fitness-functions.md)**

??? question "How do you keep a decision log healthy?"
    <span class="badge badge-l3">L3</span> Atlassian

    Lint it in CI: required sections, valid statuses, unique and contiguous numbers, dates that rise with numbers, references that resolve and don't point forward, supersession recorded on both sides. Measured: adr-tools' own log passed 9 of 9; this handbook's passed 60 of 72.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q3-how-do-you-keep-a-decision-log-healthy-l3-atlassian)

??? question "What is a fitness function?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    An automated, objective check that an architectural characteristic still holds: dependencies, compatibility, latency, startup, security settings. Atomic or holistic; static (reads code) or dynamic (measures the running system); triggered on change or continual.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q4-what-is-a-fitness-function-l3-thoughtworks)

??? question "How do ADRs and fitness functions relate?"
    <span class="badge badge-l3">L3</span> Swiggy

    The ADR says what and why; the function checks it. Each function names its ADR and each ADR lists its functions, so a failure explains itself and a decision has evidence. Measured: before this chapter, code checked 7 of FoodFleet's 72 decisions.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q5-how-do-adrs-and-fitness-functions-relate-l3-swiggy)

??? question "Static vs dynamic fitness functions: where does each run?"
    <span class="badge badge-l3">L3</span> Uber

    Static ones (dependencies, API compatibility, banned technologies) in every build: under 5 ms each here. Dynamic ones (latency, startup, resilience) in the pipeline against a deployment: about 8 s per release here, and noisy, so they need repeats.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q6-static-vs-dynamic-fitness-functions-where-does-each-run-l3-uber)

??? question "How do you make performance checks in CI not flaky?"
    <span class="badge badge-l3">L3</span> Flipkart

    Repeat and aggregate (best of three), use relative thresholds wide enough for the environment's noise, refresh the baseline in the same environment, and push small regressions to microbenchmarks. Measured: single runs at +10% raised 3–9 false alarms in 12.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q8-how-do-you-make-performance-checks-in-ci-not-flaky-l3-flipkart)

??? question "What is a technology radar, and how do you enforce it?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    A periodic view of technologies in Adopt, Trial, Assess and Hold rings (Build Your Own Radar now calls Hold "Caution"), with a reason for each. Publish it as data and map Hold blips to packages or artifacts that a build check rejects; here it caught `SimpleDateFormat` in one release.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q9-what-is-a-technology-radar-and-how-do-you-enforce-it-l3-thoughtworks)

??? question "Is adding a field to a Java record in an API a breaking change?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    For anyone who constructs it, yes: the canonical constructor's signature changes and the old one is gone. Readers are unaffected. A compatibility function over bytecode reported exactly that removed constructor.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q10-is-adding-a-field-to-a-java-record-in-an-api-a-breaking-change-l3-goldman-sachs)


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "Which service do you extract first?"
    <span class="badge badge-l3">L3</span> Amazon

    One that changes often, sits outside the dependency cycles and adds few remote dependencies. Measured on Broadleaf: admin, content and search freed 31.8% of changes for 340 remote edges; starting with the hub (order) cost 1,083 for about the same.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q2-which-service-do-you-extract-first-l3-amazon)

??? question "Why not start with the easiest service?"
    <span class="badge badge-l3">L3</span> Flipkart

    The easiest are usually the ones that rarely change, so extracting them buys little. Measured: fewest-dependants-first freed 5% of changes after three extractions.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q3-why-not-start-with-the-easiest-service-l3-flipkart)

??? question "What is a modular monolith, and why go there first?"
    <span class="badge badge-l3">L3</span> Swiggy

    One deployable whose contexts have explicit boundaries and no cycles, with shared code that doesn't depend on business code. Splitting a tangled monolith distributes its cycles: every extraction order passed through 12–16 pairs of services depending on each other.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q4-what-is-a-modular-monolith-and-why-go-there-first-l3-swiggy)

??? question "How do you know how tangled a codebase is?"
    <span class="badge badge-l3">L3</span> SAP

    Map packages to contexts, count imports between them, find strongly connected components, and compute the fewest edges that stop a layering. Broadleaf: one cycle of 9 contexts and 157 edges in the way.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q5-how-do-you-know-how-tangled-a-codebase-is-l3-sap)

??? question "What does change coupling add to import analysis?"
    <span class="badge badge-l3">L3</span> Atlassian

    It shows what actually changes together, including couplings with no import (configuration, templates, conventions). Catalog and content changed together in 19 commits without importing each other.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q6-what-does-change-coupling-add-to-import-analysis-l3-atlassian)

??? question "What's wrong with a shared library in a migration?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    Nothing, if it depends on no business code. Broadleaf's platform imported order, payment, customer and checkout 60 times; as a library, that would make every service depend on them. Invert those edges first.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q8-whats-wrong-with-a-shared-library-in-a-migration-l3-goldman-sachs)

??? question "How do you keep a migration from sliding back?"
    <span class="badge badge-l3">L3</span> Uber

    Fitness functions for each gate in every build: no new cycles between contexts, no platform → business imports, no new remote dependencies without an ADR. Track the numbers on a dashboard.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q11-how-do-you-keep-a-migration-from-sliding-back-l3-uber)


**[75. Multi-Tenancy, Multi-Region & Compliance](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)**

??? question "How do you choose between them?"
    <span class="badge badge-l3">L3</span> Salesforce

    By operations, isolation requirements and tenant size, not query speed (every model answered in about a millisecond here). Pool by default, silo for the largest or most regulated tenants, and a tested way to move tenants between tiers.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q2-how-do-you-choose-between-them-l3-salesforce)

??? question "How does PostgreSQL row-level security work, and where does it fail?"
    <span class="badge badge-l3">L3</span> Atlassian

    Policies add a condition to every query on a table. Superusers and BYPASSRLS roles always bypass it, owners do unless the table is forced. Measured: a forgotten predicate returned all 20 tenants' rows to the owner (unforced) and to a superuser.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q3-how-does-postgresql-row-level-security-work-and-where-does-it-fail-l3-atlassian)

??? question "How do you pass the tenant to the database safely with a connection pool?"
    <span class="badge badge-l3">L3</span> Zomato

    Per transaction: `SET LOCAL` (or `set_config(..., true)`) at the start of each transaction, read by the policy with `NULLIF(current_setting(..., true), '')`. Per session, 177 of 193 forgotten requests read another tenant's data; per transaction, none.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q4-how-do-you-pass-the-tenant-to-the-database-safely-with-a-connection-pool-l3-zomato)

??? question "What is a noisy neighbour, and how do you handle it?"
    <span class="badge badge-l3">L3</span> Swiggy

    One tenant's load degrading others'. Per-tenant quotas and bulkheads limit concurrency; the heaviest tenants get their own capacity. Measured: six concurrent reports raised others' p99 from 4–5 ms to 25–38 ms; a bulkhead of one held it at 8–11 ms for 4–11% fewer reports.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q5-what-is-a-noisy-neighbour-and-how-do-you-handle-it-l3-swiggy)

??? question "Why isn't a statement timeout enough for fairness?"
    <span class="badge badge-l3">L3</span> Uber

    It cancels work after spending the CPU. With a 100 ms timeout on a 135 ms report, all 525–529 reports were cancelled and others' p99 still rose to 34–40 ms. Use timeouts for runaway queries, bulkheads for fairness.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q6-why-isnt-a-statement-timeout-enough-for-fairness-l3-uber)

??? question "How do you test tenant isolation?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Fitness functions (every tenant table has a forced policy; no service connects as owner), tests that run queries without a tenant and expect nothing, and periodic cross-tenant probes in staging.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q11-how-do-you-test-tenant-isolation-l3-thoughtworks)


**[76. Serverless & Function-Based Services](../governance-org/ch76-serverless-microservices.md)**

??? question "When is it the wrong choice?"
    <span class="badge badge-l3">L3</span> Amazon

    For steady, high-rate APIs, and for sparse but latency-critical calls. A light call cost 8× a call on a busy container; two containers were cheaper above 106 (light) or 28–31 (heavy) requests a second. Sparse traffic risks cold starts.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q2-when-is-it-the-wrong-choice-l3-amazon)

??? question "What makes up a Java cold start, and how do you shorten it?"
    <span class="badge badge-l3">L3</span> Flipkart

    Platform sandbox, JVM start, class loading and linking, framework start and the first request. An AOT cache from a representative training run, C1 and Serial GC cut the JVM's part from 246–329 ms to 113–196 ms here; SnapStart or a native image go further.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q3-what-makes-up-a-java-cold-start-and-how-do-you-shorten-it-l3-flipkart)

??? question "Why does the first request of a function take longer than later ones?"
    <span class="badge badge-l3">L3</span> Swiggy

    Classes it uses are loaded on first use and the code is interpreted. The thumbnail's first call took 56–110 ms against 20–26 ms warm; a training run that exercises the function moves that work into the AOT cache.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q4-why-does-the-first-request-of-a-function-take-longer-than-later-ones-l3-swiggy)

??? question "How do Lambda and a scale-to-zero container platform differ?"
    <span class="badge badge-l3">L3</span> Google

    Lambda gives one request per instance and bills per millisecond of work. Knative and Cloud Run serve many requests per instance and bill instance time, including idle time until scale-down (60 s window, 30 s grace by default). At low rates, scale-to-zero containers still paid for 21 instance-hours a day for 2,000 webhooks.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q5-how-do-lambda-and-a-scale-to-zero-container-platform-differ-l3-google)

??? question "What changed in Lambda billing in 2025?"
    <span class="badge badge-l3">L3</span> AWS-heavy shops

    From 1 August 2025, the INIT phase of on-demand functions on managed runtimes is billed as part of duration. Slow initialisation now costs money as well as latency.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q7-what-changed-in-lambda-billing-in-2025-l3-aws-heavy-shops)

??? question "How do you handle a caller with a tight timeout on a function?"
    <span class="badge badge-l3">L3</span> Zomato

    Acknowledge quickly and process asynchronously (202 plus a queue), optimise cold starts, and add provisioned instances only if the objective still isn't met. A short keep-alive gave 25% cold starts on sparse traffic.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q9-how-do-you-handle-a-caller-with-a-tight-timeout-on-a-function-l3-zomato)

??? question "Does memory size matter for a Java function?"
    <span class="badge badge-l3">L3</span> AWS-heavy shops

    Yes: Lambda allocates CPU in proportion to memory, one vCPU at 1,769 MB. Too little memory slows JVM start and every request; the GB-second price means the right size is found by measuring duration × memory.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q10-does-memory-size-matter-for-a-java-function-l3-aws-heavy-shops)


**[77. AI-Native Microservices](../governance-org/ch77-ai-native-microservices.md)**

??? question "Why put an LLM gateway between services and model providers?"
    <span class="badge badge-l3">L3</span> Swiggy · Zomato

    To own credentials, deadlines, retries, limits, caching, routing and cost accounting in one place, instead of in every service's SDK configuration. Measured: per-feature limits alone decided whether 19–32 or 60 of 60 chats succeeded during a batch job.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q1-why-put-an-llm-gateway-between-services-and-model-providers-l3-swiggy-zomato)

??? question "How do you set timeouts for streamed LLM responses?"
    <span class="badge badge-l3">L3</span> Flipkart

    A first-token deadline, an idle deadline between chunks, and a total deadline. A total deadline alone held stalled streams for 10–30 s; idle deadlines detected stalls after 1 s and retried, keeping p99 at 5.1–5.3 s.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q2-how-do-you-set-timeouts-for-streamed-llm-responses-l3-flipkart)

??? question "How should a gateway retry 429 and 529?"
    <span class="badge badge-l3">L3</span> Amazon

    With backoff and jitter, honouring `retry-after` for 429 (your limit) and retrying or failing over for 529 (provider capacity), with a cap. Abandoned streams may still count against your limit for a while; 3–5 retries met 429 for that reason here.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q3-how-should-a-gateway-retry-429-and-529-l3-amazon)

??? question "How do you stop a batch job from starving interactive AI features?"
    <span class="badge badge-l3">L3</span> Uber

    Per-feature concurrency (or token-rate) limits, with batch capped below the provider limit, or separate capacity. A single fair queue removed errors but made chats wait 9.3 s; a cap of 4 of 8 kept them at 2.0 s.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q4-how-do-you-stop-a-batch-job-from-starving-interactive-ai-features-l3-uber)

??? question "What goes into an LLM cache key?"
    <span class="badge badge-l3">L3</span> Atlassian

    Everything the answer depends on: model and parameters, the prompt template version, retrieved context version, and the tenant and user for personal answers. Otherwise one customer's answer is served to another.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q6-what-goes-into-an-llm-cache-key-l3-atlassian)

??? question "What is MCP, and what changed in 2026?"
    <span class="badge badge-l3">L3</span> any AI platform team

    A JSON-RPC protocol for exposing tools, resources and prompts to AI applications. Revision 2026-07-28 made it stateless: no initialize handshake or sessions; every request carries its version and capabilities in `_meta`; `server/discover` advertises capabilities; HTTP requests carry `Mcp-Method`/`Mcp-Name` headers that must match the body; results carry `resultType`.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q7-what-is-mcp-and-what-changed-in-2026-l3-any-ai-platform-team)

??? question "What is prompt injection, and why doesn't a better prompt fix it?"
    <span class="badge badge-l3">L3</span> Microsoft

    Instructions in content the model reads (reviews, emails, pages) that the model follows. The model can always be persuaded, so the defence is in the tools: least privilege, identity from the session, confirmation for side effects, and output checks.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q9-what-is-prompt-injection-and-why-doesnt-a-better-prompt-fix-it-l3-microsoft)


### Observability


**[47. Observability Fundamentals](../observability/ch47-observability-fundamentals.md)**

??? question "What is cardinality, and how do you control it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Atlassian · PhonePe

    The number of distinct values of a label (and, multiplied, of label combinations). Control it with a budget per service, reviewed labels, route templates instead of raw paths, and dropping or aggregating labels in the collector before storage.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q4-what-is-cardinality-and-how-do-you-control-it-l2l3-atlassian-phonepe)

??? question "Head sampling or tail sampling?"
    <span class="badge badge-l3">L3</span> Uber · Razorpay

    Head sampling decides at the start, by trace ID: cheap and stateless, but it keeps rare slow or failed requests only at the sampling rate. Tail sampling decides after the whole trace: it keeps every error and slow request, at the cost of buffering traces in a collector tier that routes by trace ID. Measured: 1% head sampling kept 2–4 of 248–285 slow traces; tail sampling (errors, slower than 300 ms, 1% baseline) kept all of them for 18–19% of the bytes.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q5-head-sampling-or-tail-sampling-l3-uber-razorpay)

??? question "What is a wide event?"
    <span class="badge badge-l3">L3</span> Atlassian · Stripe-style rounds

    One structured record per request per service, written at the end, with every field that might explain it: version, pod, tenant, inputs, decisions, timings, counts, trace ID. Stored unsampled or with errors and slow ones kept, it lets you group by anything after the fact.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q7-what-is-a-wide-event-l3-atlassian-stripe-style-rounds)

??? question "What does the OpenTelemetry Collector do?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Microsoft · Amazon

    It receives telemetry (OTLP and other formats), processes it (memory limits, batching, attribute changes, redaction, sampling, deriving metrics from spans) and exports it to backends. It decouples services from backends and is where tail sampling and cost controls live.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q8-what-does-the-opentelemetry-collector-do-l2l3-microsoft-amazon)

??? question "What does instrumentation cost?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    CPU, memory, startup time and network, plus storage downstream. Measured on three thin services: the Java agent added 38–56% CPU per order (25–36% with nothing exported), 110–125 MB per JVM and 13–15 s of startup; JFR 4–7% CPU. Measure it per service, and budget it like any other dependency.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q9-what-does-instrumentation-cost-l3-flipkart-goldman-sachs)

??? question "When do you need a profiler instead of a trace?"
    <span class="badge badge-l3">L3</span> Netflix-style rounds

    When a span is slow but has no children: the time is inside the service's own code. A trace shows *where*; a CPU profile shows *which method*. Measured: a 7 ms payment span had no children; JFR put 63–64% of the service's CPU samples in the method that hashed idempotency keys with PBKDF2.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q10-when-do-you-need-a-profiler-instead-of-a-trace-l3-netflix-style-rounds)

??? question "What should never go into telemetry?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks · PayPal

    Secrets, credentials, card numbers and other personal data, in logs, span attributes, metric labels and resource attributes alike. Measured: the Java agent's default resource includes the JVM command line and masks only properties named *password* or *secret*; a token passed with `-D` appeared in spans and in Prometheus labels. Redact at the source and again in the collector.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q11-what-should-never-go-into-telemetry-l2l3-banks-paypal)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "Why can't you average percentiles across pods?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    A percentile isn't additive: the fleet's p99 depends on how every pod's whole distribution combines, which the per-pod p99s don't contain. Measured with one slow pod in four: the average of the pods' p99s was 137 ms, the true fleet p99 322 ms, `histogram_quantile` over summed buckets 317–318 ms. Publish histogram buckets and compute the percentile from their sum.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q2-why-cant-you-average-percentiles-across-pods-l2l3-swiggy-flipkart)

??? question "publishPercentiles or publishPercentileHistogram?"
    <span class="badge badge-l3">L3</span> Atlassian · PhonePe

    Histograms, for anything you aggregate or alert on: buckets add up across pods and time windows, and any percentile can be computed later. Client-side percentiles are fixed numbers per process. In Micrometer's Prometheus registry, enabling the histogram drops the client-side percentiles from the output anyway.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q3-publishpercentiles-or-publishpercentilehistogram-l3-atlassian-phonepe)

??? question "What is the Observation API, and why use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Spring-heavy shops

    Instrument once with an Observation; handlers turn it into a timer, a span and anything else. Low-cardinality key values become metric tags and span attributes; high-cardinality ones only span attributes. In the tests, the checkout Observation's timer carried payment.method, city.tier and outcome, never order.id or restaurant.id; measured, an Observation with the meter handler costs 0.7–1.3 µs.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q5-what-is-the-observation-api-and-why-use-it-l2l3-spring-heavy-shops)

??? question "How does Spring decide the uri tag, and how does it go wrong?"
    <span class="badge badge-l3">L3</span> Walmart · Zomato

    On the server, the matched path pattern (or NOT_FOUND / REDIRECTION / UNKNOWN); on the client, the URI template given to RestClient. It goes wrong when code concatenates IDs into the template, or overrides the convention with the raw path. Measured: 10,000 requests gave 24 meters with path patterns, 15,954 with raw paths and 19,918 with concatenated client URIs.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q6-how-does-spring-decide-the-uri-tag-and-how-does-it-go-wrong-l3-walmart-zomato)

??? question "What does max-uri-tags do?"
    <span class="badge badge-l3">L3</span> Razorpay · Uber

    It caps the distinct uri values per meter name (100 by default), first come, first served: once full, new values are denied, with one warning. It stops the explosion but can hide new endpoints; fix the source of values and alert on the warning.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q7-what-does-max-uri-tags-do-l3-razorpay-uber)

??? question "Why does my timer's max stay high after an incident?"
    <span class="badge badge-l3">L3</span> Netflix-style rounds

    Max is kept over a sliding window: in Micrometer's Prometheus registry it rotates once per step (1 minute) through 3 buffers. Measured: after a 10-second spike, max stayed at 3,544 ms for 130–140 s; client-side p99 recovered after 50–60 s, a 30-second bucket rate after 30 s. Alert on percentiles from short-window bucket rates instead.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q8-why-does-my-timers-max-stay-high-after-an-incident-l3-netflix-style-rounds)

??? question "What does recording a metric cost?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Tens of nanoseconds for a counter or plain timer, about a hundred with a histogram; client-side percentiles and Observations cost more, especially under contention. Measured: 12–13 ns for a counter, 51–52 ns for a timer, 84–97 ns with a histogram, about 200 ns for client-side percentiles (1.4–1.5 µs contended), 0.7–1.3 µs for an Observation. Register meters once; turn off long-task timers you don't use.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q9-what-does-recording-a-metric-cost-l3-goldman-sachs-flipkart)

??? question "How do you keep metric cardinality under control?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Atlassian · Amazon

    Tag values from enums and templates; `MeterFilter.ignoreTags` for identifiers; caps as a backstop with alerts; a series budget per service checked in CI; recording rules that aggregate away pod-level detail; per-entity questions in traces and events.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q11-how-do-you-keep-metric-cardinality-under-control-l3l4-atlassian-amazon)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "How does a trace continue across threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · PhonePe

    The context is bound to the thread; it must be captured when work is handed over and restored when it runs. The agent does this for JDK executors and async callbacks; bare threads and own queues need `Context.wrap`. Measured: executors, `CompletableFuture`, scheduled and virtual-thread executors kept the trace 200 of 200 times; `new Thread()`, `Thread.ofVirtual().start()` and a hand-rolled queue lost it 200 of 200.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q2-how-does-a-trace-continue-across-threads-l2l3-swiggy-phonepe)

??? question "How is trace context carried through Kafka?"
    <span class="badge badge-l3">L3</span> Flipkart · Walmart

    The producer's instrumentation writes `traceparent` (and baggage) into record headers; the consumer's instrumentation wraps the records iterator and makes a `process` span current per record, as a child of the producer's span, from `next()` until the next `hasNext()`. In Spring Kafka, enable template and listener observations (off by default).

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q3-how-is-trace-context-carried-through-kafka-l3-flipkart-walmart)

??? question "Parent or link for a consumer span?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Uber · Zomato

    Parent when the message is handled promptly, one by one, as part of the same user action; a new trace with links when messages are batched, delayed for a long time, or many producers feed one consumer operation. OpenTelemetry's instrumentation calls the latter "receive telemetry".

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q4-parent-or-link-for-a-consumer-span-l3l4-uber-zomato)

??? question "What breaks a trace?"
    <span class="badge badge-l3">L3</span> Atlassian · Razorpay

    Any hand-off nobody carries context across (bare threads, own queues, pull consumers, outbox tables, schedulers), a propagation format the next service can't read, and samplers that don't follow the parent. Measured: a pull consumer continued 0 of 220 orders' traces; a B3-only service broke all 1,000; a downstream ratio of 0.05 dropped payment from about half of the sampled traces.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q5-what-breaks-a-trace-l3-atlassian-razorpay)

??? question "What does parent-based sampling mean, and why use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · PayPal

    Follow the caller's sampled flag; apply a ratio only to new roots. Every service then keeps or drops the same traces, so sampled traces are complete. Measured: parent-based everywhere gave 105–108 complete traces from 1,000 orders at 10% and nothing else; one service re-deciding produced 400–904 orphan fragments or 43–47 incomplete traces.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q6-what-does-parent-based-sampling-mean-and-why-use-it-l2l3-amazon-paypal)

??? question "What is baggage, and what can go wrong?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · banks

    Key-value context propagated with the trace to every downstream call. It is extracted from any caller and sent to any callee, so it can carry injected or personal data to places it shouldn't go, and it isn't on spans unless copied. Measured: a client's email and session token in baggage reached a third-party partner on 50 of 50 calls until the gateway filtered it and the partner call was made without baggage.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q7-what-is-baggage-and-what-can-go-wrong-l3-goldman-sachs-banks)

??? question "Should an internet-facing service trust an incoming traceparent?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Cloudflare-style rounds

    No. Parent-based sampling honours a sampled flag from anyone, so clients can force sampling (and costs), choose trace IDs and inject baggage. Strip trace headers at the edge and start new traces there; link to a partner's trace ID if you need it.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q8-should-an-internet-facing-service-trust-an-incoming-traceparent-l3l4-cloudflare-style-rounds)

??? question "How do you carry trace context through an outbox?"
    <span class="badge badge-l3">L3</span> Swiggy-style rounds

    Inject the current context into a `traceparent` (and optional `baggage`) column when the outbox row is written; the relay extracts it and either continues the trace or links to it when publishing, so the event's consumers join the order's trace.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q11-how-do-you-carry-trace-context-through-an-outbox-l3-swiggy-style-rounds)


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "How do you correlate logs across services and threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Put the trace ID on every line, from Micrometer Tracing or the OpenTelemetry agent. Trace context is propagated across HTTP, messaging and executors, so it follows the work. Measured: the agent's `trace_id` was right on every executor hop, including a thread with a leaked MDC. It was missing only on bare threads.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q3-how-do-you-correlate-logs-across-services-and-threads-l2l3-flipkart-razorpay)

??? question "How does the OpenTelemetry agent get the trace ID into Logback's output, even with an async appender?"
    <span class="badge badge-l3">L3</span> Uber-style rounds

    It instruments `Logger.callAppenders` to store the current context on the event, and `getMDCPropertyMap` to add `trace_id`, `span_id` and `trace_flags` from that stored context. The values reflect the logging thread at the time of the call, even when the encoding happens later on the async appender's thread. Baggage can be added with `logback-mdc.add-baggage`.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q4-how-does-the-opentelemetry-agent-get-the-trace-id-into-logbacks-output-even-with-an-async-appender-l3-uber-style-rounds)

??? question "What does Logback's AsyncAppender do when the queue fills?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Below 20% free capacity (`discardingThreshold`, default queue/5), it silently drops TRACE, DEBUG and INFO. When the queue is full, it blocks the caller, or with `neverBlock` drops any event. Measured under a burst: 29–30% of INFO delivered with the defaults; with `neverBlock`, 9% of WARN.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q5-what-does-logbacks-asyncappender-do-when-the-queue-fills-l3-amazon-walmart)

??? question "How do you keep personal data out of logs?"
    <span class="badge badge-l3">L3</span> Paytm · banks

    Use layers: don't log what you don't need; use types that print masked (`Pii`); put a masking encoder over every finished line; and run a CI test that pushes fake PII through every path. Measured: source masking alone leaked from concatenated messages, MDC values and exception messages; the line-level encoder leaked nothing.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q6-how-do-you-keep-personal-data-out-of-logs-l3-paytm-banks)

??? question "Why not mask with regexes?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Atlassian

    It is slow, and it can be wrong. Six regexes cost 69–77 µs per 500-character line against 3.8–12.6 µs for a single-pass scanner. They also masked epoch-millisecond timestamps as card numbers, because 13-digit numbers often pass Luhn. Validate candidates (length, card prefix, Luhn) and scan once.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q7-why-not-mask-with-regexes-l3-goldman-sachs-atlassian)

??? question "How do you control logging cost?"
    <span class="badge badge-l3">L3</span> Flipkart · Meesho

    Fewer bytes, not faster encoders. JSON costs about 1–2 µs per line in CPU. Volume costs storage and ingest money. Exclude unused fields, cap stack traces, sample INFO for successful requests on hot paths, use metrics for counts instead of a line per event, and set retention by data class.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q10-how-do-you-control-logging-cost-l3-flipkart-meesho)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "What is an error budget, and what is it for?"
    <span class="badge badge-l3">L3</span> Swiggy · Razorpay

    1 − SLO: the failures you can afford, 43.2 minutes a month at 99.9%. It turns reliability into a shared currency. While there is budget, ship. When it is running out, slow down and fix reliability. The error budget policy writes that down in advance, agreed with product.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q2-what-is-an-error-budget-and-what-is-it-for-l3-swiggy-razorpay)

??? question "What is a burn rate?"
    <span class="badge badge-l3">L3</span> Flipkart · PhonePe

    The error ratio divided by the budget: how many times faster than "exactly on budget" you are failing. Burn rate 1 spends the budget in the window; 14.4 spends 2% of a 30-day budget in an hour.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q3-what-is-a-burn-rate-l3-flipkart-phonepe)

??? question "Why multi-window, multi-burn-rate alerts?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google-style rounds

    The long window makes an alert significant, and the short window makes it stop when the problem does. Several burn rates separate "page now" from "ticket". Measured: they paged fast incidents within 1–43 minutes with at most 2.3% of the budget gone, ticketed slow ones at 8%, and stopped 4–29 minutes after recovery. A 1-hour-only alert held on for up to 59 minutes.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q4-why-multi-window-multi-burn-rate-alerts-l3l4-google-style-rounds)

??? question "Why not alert when the error ratio exceeds the SLO?"
    <span class="badge badge-l3">L3</span> Uber · Atlassian

    Noise and duration. Random variation crosses the line on healthy services (3 times a week here), and any burn over 1× keeps it on for as long as it lasts (more than 5,700 minutes for a 4-day burn). That trains people to silence it.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q6-why-not-alert-when-the-error-ratio-exceeds-the-slo-l3-uber-atlassian)

??? question "How do you alert for a low-traffic service?"
    <span class="badge badge-l3">L3</span> PayPal · banks

    One failure is a big ratio, so burn-rate alerts page per failure: 16 pages in 30 days here, on a healthy service. Require a minimum number of events in the long window, lengthen the window, add synthetic probes, or combine small services into one SLO.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q7-how-do-you-alert-for-a-low-traffic-service-l3-paypal-banks)

??? question "How do you define a latency SLI with Prometheus?"
    <span class="badge badge-l3">L3</span> Spring-heavy shops

    As a ratio: requests under the threshold, from the histogram bucket at exactly that threshold, divided by all requests. In Spring Boot add `management.metrics.distribution.slo.<meter>=300ms`. Without that bucket, Prometheus interpolates; measured, 99.14% instead of 99.41%, which made the budget look 86% spent instead of 59%.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q8-how-do-you-define-a-latency-sli-with-prometheus-l3-spring-heavy-shops)

??? question "Percentile or ratio for latency SLOs?"
    <span class="badge badge-l3">L3</span> Walmart · Myntra

    A ratio. Percentiles can't be averaged across windows or instances and only say pass or fail. A ratio gives a budget and a burn rate: 96.96% within 300 ms is 3× the budget; 89.85% is 10×.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q9-percentile-or-ratio-for-latency-slos-l3-walmart-myntra)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "And readiness?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> PhonePe · Razorpay

    Not for dependencies every replica shares: they all go not-ready together and the Service has no endpoints. Measured: new connections refused for the whole outage and 10–12 seconds after it. Answer 503 fast from the endpoints that need the dependency instead. Readiness on a dependency makes sense only when it is per-pod.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q3-and-readiness-l2l3-phonepe-razorpay)

??? question "Why do deploys fail requests even with graceful shutdown?"
    <span class="badge badge-l3">L3</span> Uber · Zomato

    Endpoint removal is asynchronous. SIGTERM arrives while kube-proxies and load balancers still route new connections to the pod. Graceful shutdown finishes in-flight work but refuses new connections. Measured: 79–96 failures per rolling restart with graceful shutdown alone, 0 with a 5-second `preStop` sleep.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q5-why-do-deploys-fail-requests-even-with-graceful-shutdown-l3-uber-zomato)

??? question "How do you secure Actuator?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks · Goldman Sachs

    

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q7-how-do-you-secure-actuator-l2l3-banks-goldman-sachs)

??? question "What timeouts do health checks need?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Each check needs its own timeout below the probe's (1 second by default), running in parallel, with a timeout counted as DOWN. Otherwise one slow dependency makes the endpoint slow, the probe times out, and that counts as a failure. Cache expensive checks for a few seconds when many callers poll.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q9-what-timeouts-do-health-checks-need-l3-amazon-walmart)

??? question "How should `terminationGracePeriodSeconds` be set?"
    <span class="badge badge-l3">L3</span> Atlassian

    Above the `preStop` sleep plus Spring's `timeout-per-shutdown-phase` plus some margin, for example 5 + 20 + 5 = 30 seconds, the default. Otherwise SIGKILL arrives mid-request.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q10-how-should-terminationgraceperiodseconds-be-set-l3-atlassian)

??? question "Who should alert on health?"
    <span class="badge badge-l3">L3</span> Swiggy-style rounds

    Monitoring should: Prometheus scrapes `/actuator/health` components and SLO alerts (chapter 51) page on user impact. Probes are for Kubernetes' automation, not for people. A pod that's ready but has no database shows in health and in the SLO, not as restarts.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q11-who-should-alert-on-health-l3-swiggy-style-rounds)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "Virtual threads or WebFlux?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    For request/response services with blocking I/O, virtual threads: the same capacity and tail (measured: both about 1,490/s, p99 279–411 ms) with plain code, stack traces and blocking libraries. WebFlux for streaming, backpressure, WebSockets and gateways, or when the stack is already reactive end to end.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q2-virtual-threads-or-webflux-l3-swiggy-flipkart)

??? question "What is pinning, and how do you find it?"
    <span class="badge badge-l3">L3</span> Oracle · any Java shop

    A virtual thread that can't unmount while blocked, so it holds its carrier. On JDK 21, blocking inside `synchronized` pins (measured: 1,000 50 ms tasks took 25 s instead of 0.1 s); JDK 24+ fixed monitors, and native frames still pin. Find it with JFR's `jdk.VirtualThreadPinned` event; fix it with `ReentrantLock` or a newer JDK.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q3-what-is-pinning-and-how-do-you-find-it-l3-oracle-any-java-shop)

??? question "What replaces the thread pool's limit when you move to virtual threads?"
    <span class="badge badge-l3">L3</span> Zomato

    An explicit one: a bulkhead, `@ConcurrencyLimit` or an adaptive limit per endpoint and dependency, sized by Little's law. Measured: unbounded, 86–93% of requests failed at 2,500/s and the event loop ran out of memory; a limit of 300 served 1,510–1,588/s at a p99 of 261–303 ms.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q4-what-replaces-the-thread-pools-limit-when-you-move-to-virtual-threads-l3-zomato)

??? question "How does Little's law size a pool or a limit?"
    <span class="badge badge-l3">L3</span> Amazon

    In-flight = throughput × latency. 200 threads at 150 ms per request finish at most 1,333 a second (measured: 1,060–1,100 with overhead). To serve 1,500/s at about 0.18 s, you need about 270 in progress, so a limit of 300.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q5-how-does-littles-law-size-a-pool-or-a-limit-l3-amazon)

??? question "Why is fan-out bad for tail latency?"
    <span class="badge badge-l3">L3</span> Google · Uber

    The request waits for the slowest call, so it's slow if any call is: 1 − (1 − p)ⁿ. Measured with 1% slow calls: 5% of requests slow at 5 calls, 17–19% at 20, 39–41% at 50; at 20 calls, the p90 was the slow path.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q6-why-is-fan-out-bad-for-tail-latency-l3-google-uber)

??? question "What are hedged requests, and when do they backfire?"
    <span class="badge badge-l3">L3</span> Google · LinkedIn

    A second copy of a slow call, first answer wins. With spare capacity, hedging after 20 ms cut a 50-call fan-out's p99 from 208–210 ms to 53–86 ms for 8–10% more calls. On a saturated backend, the fast path passed 20 ms, 99.9% of calls were hedged, and p99 rose to 362–407 ms. Use a p95-based delay, a budget, and only idempotent reads.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q7-what-are-hedged-requests-and-when-do-they-backfire-l3-google-linkedin)

??? question "How do you build a latency budget?"
    <span class="badge badge-l3">L3</span> Razorpay

    Start from the objective (the quote's p99 under 500 ms), subtract what the calls need (three sequential 50 ms calls: 150 ms), and give each hop a timeout from what's left. Parallelise independent calls: the budget then pays for the slowest, not the sum.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q8-how-do-you-build-a-latency-budget-l3-razorpay)

??? question "Your service on virtual threads ran out of memory under load. Why?"
    <span class="badge badge-l3">L3</span> PhonePe

    Nothing limited how many requests waited, so each one's stack and buffers stayed on the heap until the heap filled. Measured: unbounded virtual threads peaked at 640 MB with a 512 MB heap at 2,500/s; the event loop ran out of memory. Limit in-progress requests and time out calls.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q10-your-service-on-virtual-threads-ran-out-of-memory-under-load-why-l3-phonepe)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "Which collector does the JVM choose by default?"
    <span class="badge badge-l3">L3</span> Swiggy

    G1 on a "server-class machine": at least 2 CPUs and 2 GB less 256 MB, so 1,792 MB. Otherwise Serial. Measured: a 2-CPU, 1 GiB container got Serial and a 256 MB heap. Set the collector explicitly.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q2-which-collector-does-the-jvm-choose-by-default-l3-swiggy)

??? question "How big should the heap be in a container?"
    <span class="badge badge-l3">L3</span> Flipkart

    Big enough for the live set with headroom for churn, small enough that heap + native memory fit. Measured in 1 GiB with a 350 MB live set: 25% failed at startup, 50–75% caused full GCs (p99 0.5–0.7 s), 90% worked until 150 MB of direct buffers got the container OOMKilled.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q3-how-big-should-the-heap-be-in-a-container-l3-flipkart)

??? question "G1, ZGC or Parallel?"
    <span class="badge badge-l3">L3</span> Atlassian · Uber

    G1 as the balanced default (longest pause 85–112 ms here). ZGC when pauses must be under a millisecond and the pod has CPU and memory to spare (measured: about 30% more CPU, 1.3 GB RSS against 0.95). Parallel for throughput-only batch work (lowest CPU). Never Serial for a service with a large live set (655–704 ms pauses).

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q5-g1-zgc-or-parallel-l3-atlassian-uber)

??? question "Why did ZGC make latency worse?"
    <span class="badge badge-l3">L3</span> Zomato

    Its collection is concurrent, so it needs CPU while the application runs. On one CPU it competed with requests: pauses of 0.1 ms, p99 106–136 ms, against G1's 68–92 ms. Concurrent collectors trade CPU for pauses.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q6-why-did-zgc-make-latency-worse-l3-zomato)

??? question "What uses memory in a JVM besides the heap?"
    <span class="badge badge-l3">L3</span> Red Hat

    GC data structures, metaspace and class data, the JIT's code cache, thread stacks, direct and mapped buffers, JFR's buffers, and the C heap of native libraries. Measured with NMT: G1's structures 54 MB, ZGC's 238–266 MB for a 1 GB heap. Measure with `-XX:NativeMemoryTracking=summary` and `jcmd VM.native_memory`.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q7-what-uses-memory-in-a-jvm-besides-the-heap-l3-red-hat)

??? question "What is an evacuation failure?"
    <span class="badge badge-l3">L3</span> Oracle

    G1 copies live objects out of the regions it collects; when there are no free regions to copy into, the evacuation fails and G1 falls back to a full, compacting collection. Measured: with 1.5–2.2× the live set as heap, 12–14 evacuation-failure events led to full collections of 650–1,030 ms; at 2.6× there were none.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q8-what-is-an-evacuation-failure-l3-oracle)

??? question "What are compact object headers?"
    <span class="badge badge-l3">L3</span> any Java shop

    JEP 519 (a product option in JDK 25) shrinks each object's header from 12–16 bytes to 8. For heaps of many small objects that's a real saving: measured, the menu cache took 303 MB instead of 348 MB, and RSS fell by 108–126 MB. It's off by default; enable it with `-XX:+UseCompactObjectHeaders` after a load test.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q10-what-are-compact-object-headers-l3-any-java-shop)

??? question "Should you set CPU limits on JVM containers?"
    <span class="badge badge-l3">L3</span> Razorpay

    Whatever the limit, the JVM sizes itself from it: GC threads, JIT threads, the ForkJoin pool. A limit of 1 means one GC thread and, for ZGC, competition with the application. If you set limits, set them to at least 2 for G1 or ZGC, or tell the JVM explicitly with `-XX:ActiveProcessorCount`.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q11-should-you-set-cpu-limits-on-jvm-containers-l3-razorpay)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "How does a Kubernetes Service balance load?"
    <span class="badge badge-l3">L3</span> Google · Red Hat

    kube-proxy picks an endpoint at random when a connection opens (iptables `statistic --mode random`, or nftables `numgen random`), so every request on a keep-alive connection goes to the same pod. Measured: a keep-alive client left 1–2 of 4 copies idle; a new connection per request spread 23–27% each.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q3-how-does-a-kubernetes-service-balance-load-l3-google-red-hat)

??? question "You scaled a deployment and the new pods get no traffic. Why?"
    <span class="badge badge-l3">L3</span> Swiggy

    Callers reuse connections opened before the new pods existed, and an L4 Service never moves them. Measured: after scaling 2 → 4 with keep-alive, the new copies got 0%. Use per-request balancing (mesh, gRPC client-side) and a maximum connection age.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q4-you-scaled-a-deployment-and-the-new-pods-get-no-traffic-why-l3-swiggy)

??? question "Why didn't adding pods increase throughput?"
    <span class="badge badge-l3">L3</span> Flipkart

    A shared dependency was saturated. Measured: alone, 1, 2 and 4 copies served 35, 62–66 and 121–132 requests a second; behind a dependency allowing 50 a second, 4 copies served 50, each at a third of capacity. Find and scale the bottleneck, and autoscale on the signal it produces.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q5-why-didnt-adding-pods-increase-throughput-l3-flipkart)

??? question "What is consistent hashing, and why virtual nodes?"
    <span class="badge badge-l3">L3</span> Amazon · Uber

    Nodes and keys on a hash ring; a key belongs to the next node. Adding a node moves only the keys it takes over (measured: 20% going 4 → 5, against mod n's 80%). With one point per node the arcs are uneven (busiest node 2.26× the average); 256 points per node brought that to 1.10×.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q6-what-is-consistent-hashing-and-why-virtual-nodes-l3-amazon-uber)

??? question "How does Kafka assign a keyed record to a partition, and what happens when you add partitions?"
    <span class="badge badge-l3">L3</span> Confluent · PhonePe

    `murmur2(key) mod partitions`. Adding partitions changes the modulus, so most keys move (measured for mod n: 80–91%), and per-key ordering isn't kept across the change. Size partition counts for growth from the start.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q8-how-does-kafka-assign-a-keyed-record-to-a-partition-and-what-happens-when-you-add-partitions-l3-confluent-phonepe)

??? question "How do you handle a hot key?"
    <span class="badge badge-l3">L3</span> Zomato

    Hashing can't split one key. Use a finer key if ordering allows (order ID: perfectly even), or salt the hottest keys over sub-keys. Measured at Zipf s = 1.0 over 12 partitions: by restaurant 19.0% on one partition; with the top 10 salted over 4, 12.2%; by order ID, 8.4%.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q9-how-do-you-handle-a-hot-key-l3-zomato)

??? question "Range or hash partitioning?"
    <span class="badge badge-l3">L3</span> Atlassian

    Hash spreads evenly but scatters ranges; range keeps neighbours together for scans but puts sequential keys (time, IDs) on the newest range. Use hash for writes keyed by IDs, range for time-ordered data you scan, often both: partition by time, then hash within.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q10-range-or-hash-partitioning-l3-atlassian)


**[69. High-Throughput Kafka Tuning](../performance-scalability/ch69-kafka-throughput-tuning.md)**

??? question "Why did Kafka 4 change linger.ms's default to 5?"
    <span class="badge badge-l3">L3</span> Confluent

    Because a few milliseconds of waiting batches records much better at moderate rates, saving broker CPU, at a latency cost most applications don't notice (KIP-1030). Measured: 20× fewer requests than linger 0 for 3.4 ms more median latency.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q2-why-did-kafka-4-change-lingermss-default-to-5-l3-confluent)

??? question "Which compression codec?"
    <span class="badge badge-l3">L3</span> Flipkart

    lz4 or snappy for speed, zstd when storage and network matter more, never gzip at volume. Measured on JSON order events: lz4 and snappy 4.4–4.5×, zstd 6.75× at about 1.5× lz4's CPU, gzip 7.31× at an eighth of the throughput. Compression also cuts broker work, since the broker stores batches as they are.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q3-which-compression-codec-l3-flipkart)

??? question "Is max.in.flight.requests.per.connection=1 needed for ordering?"
    <span class="badge badge-l3">L3</span> PhonePe

    No, not with idempotence (the default since 3.0), which keeps order with up to 5. One in flight costs throughput whenever there's latency: measured over a 1 ms round trip, 268,000–269,000 records a second against 641,000–674,000.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q4-is-maxinflightrequestsperconnection1-needed-for-ordering-l3-phonepe)

??? question "How do you process faster without more partitions?"
    <span class="badge badge-l3">L3</span> Zomato

    Concurrency inside the consumer, keyed so each key stays in order (Confluent Parallel Consumer, or a keyed executor), or batching the work per poll. Measured: 32 key-ordered lanes gave 16,600–16,800 records a second from one consumer; one write per poll gave 62,000–63,000.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q6-how-do-you-process-faster-without-more-partitions-l3-zomato)

??? question "How should a consumer write to a database?"
    <span class="badge badge-l3">L3</span> Razorpay

    A poll per transaction with batched inserts, an idempotent key, and offsets committed afterwards. Measured on PostgreSQL: 1,384–1,554 rows a second with a commit per record, 29,000–34,000 with JDBC batching, 45,000–46,000 with `reWriteBatchedInserts=true`, and 55,000–59,000 with COPY in polls of 5,000.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q7-how-should-a-consumer-write-to-a-database-l3-razorpay)

??? question "At-least-once, at-most-once or exactly-once?"
    <span class="badge badge-l3">L3</span> Paytm

    At-least-once (commit after processing) with an idempotent sink is the usual choice; at-most-once loses events on crashes; exactly-once needs Kafka transactions end to end, or a sink that stores offsets with the data. Measured: replaying a fifth of the events left every sink's row count unchanged.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q8-at-least-once-at-most-once-or-exactly-once-l3-paytm)

??? question "What does acks=all cost?"
    <span class="badge badge-l3">L3</span> Uber

    Waiting for every in-sync replica, so latency rises by the slowest follower's fetch, typically a few milliseconds within a region. It isn't measured here (the test broker had no replicas). With `min.insync.replicas=2` it's what makes an acknowledged record survive a broker loss, so FoodFleet keeps it.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q9-what-does-acksall-cost-l3-uber)

??? question "Batch or stream processing?"
    <span class="badge badge-l3">L3</span> Atlassian

    Stream when results must be seconds fresh (dispatch, fraud, live dashboards); batch when hours are fine (settlement, reports), since it's cheaper and simpler. Micro-batching sits between: per-poll writes here ran 10–42× faster than per-record writes, at the cost of a poll's latency.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q10-batch-or-stream-processing-l3-atlassian)

??? question "How many partitions should a topic have?"
    <span class="badge badge-l3">L3</span> LinkedIn

    Enough for the most parallel consumer at peak, plus growth: the peak rate divided by what one consumer instance handles per partition. Add margin up front, since adding partitions to a keyed topic remaps keys (chapter 68). Too many costs broker memory, file handles and longer rebalances.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q11-how-many-partitions-should-a-topic-have-l3-linkedin)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "Why are Kubernetes clusters usually over-provisioned?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    Nodes are bought for requests, and requests are guesses padded "to be safe" and copied from templates. Measured: workloads used 3–10% of their requested CPU and 5–21% of their memory; right-sized, the fleet needed 8–9 nodes instead of 32.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q2-why-are-kubernetes-clusters-usually-over-provisioned-l3-swiggy-flipkart)

??? question "How does VPA recommend requests?"
    <span class="badge badge-l3">L3</span> Google

    From usage histograms: the CPU target is the 90th percentile of usage and the memory target the 90th percentile of per-interval peaks (24-hour intervals by default), each plus a 15% margin, at least 25 millicores and 250 MiB, with bounds at the 50th and 95th percentiles. Use it in recommendation mode alongside the HPA (chapter 56).

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q3-how-does-vpa-recommend-requests-l3-google)

??? question "Should you set CPU limits?"
    <span class="badge badge-l3">L3</span> Atlassian · Razorpay

    Not on latency-sensitive services. A limit throttles bursts even when the node is idle. Measured: a limit at VPA's target (0.2 CPU) raised p99 from 5 ms to 78–204 ms; the same request without a limit kept 4.6–5.3 ms. Set requests well and keep memory limits.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q4-should-you-set-cpu-limits-l3-atlassian-razorpay)

??? question "Why can't a bursty job be right-sized like a service?"
    <span class="badge badge-l3">L3</span> Uber

    Its p90 is its burst. Measured: a job averaging 0.37 CPU used both cores 20% of the time; VPA recommended 2.12–2.13 CPUs. Save by scheduling it (spot, off-peak, a shared batch pool), not by shrinking it.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q5-why-cant-a-bursty-job-be-right-sized-like-a-service-l3-uber)

??? question "How should cluster costs be allocated to teams?"
    <span class="badge badge-l3">L3</span> Atlassian

    By the larger of request and usage per container (OpenCost's rule), so teams pay for what they reserve and for bursts above it; the rest is idle and belongs to whoever owns the cluster. Measured: by usage, 93% of the bill was idle; by requests or max, 34%.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q6-how-should-cluster-costs-be-allocated-to-teams-l3-atlassian)

??? question "When is spot capacity safe?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    For work that can lose copies without losing users: stateless services above an on-demand base that carries the load alone, batch jobs with checkpoints, CI. Measured: all-spot served 0–1% of requests during a reclaim; 3 on-demand + 3 spot served 55–61% because the remaining copies were overloaded.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q7-when-is-spot-capacity-safe-l3-zomato-phonepe)

??? question "What are unit economics, and which unit?"
    <span class="badge badge-l3">L3</span> Swiggy

    Cost per unit of business value: per 1,000 orders, per delivery, per active restaurant. It shows whether costs grow faster than the business and lets teams compare designs. Track it per team beside SLOs.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q8-what-are-unit-economics-and-which-unit-l3-swiggy)

??? question "Commitments or right-sizing first?"
    <span class="badge badge-l3">L3</span> Amazon

    Right-size first. A commitment pays for capacity for one to three years; committing before right-sizing locks in the waste (here, about three quarters of the nodes).

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q9-commitments-or-right-sizing-first-l3-amazon)

??? question "How do you pick node shapes?"
    <span class="badge badge-l3">L3</span> Red Hat

    From the fleet's CPU:memory ratio after right-sizing, and let a provisioner such as Karpenter choose among several. Measured: after right-sizing, 89–97% of CPU and 35–38% of memory was allocated, so CPU-heavier nodes would pack better.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q10-how-do-you-pick-node-shapes-l3-red-hat)

??? question "How does right-sizing interact with the HPA?"
    <span class="badge badge-l3">L3</span> Google

    The HPA scales on utilization of requests, so smaller requests make the same load look busier and the HPA adds copies sooner. Re-tune targets after right-sizing, and don't let VPA and HPA act on the same metric (chapter 56).

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q11-how-does-right-sizing-interact-with-the-hpa-l3-google)


### Resilience


**[42. Failure Modes & Cascading Failures](../resilience/ch42-failure-modes.md)**

??? question "Why does latency jump near capacity?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Queueing: waiting time grows like 1/(1−ρ). Measured: p99 was 4.5× the service time at 52% utilisation and 12× at 97%; mean latency tripled between 80% and 97%. Keep headroom so that losing a zone or a slow dependency doesn't push you past ~80%.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q2-why-does-latency-jump-near-capacity-l2l3-amazon-flipkart)

??? question "Why are retries dangerous?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    They multiply load exactly when a dependency is struggling, and they multiply across layers: three attempts at three layers sent 27 calls per user request to a dead service (measured). Retry at one layer or within a budget (10% sent 1.39), only idempotent operations, with jittered backoff.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q3-why-are-retries-dangerous-l3-amazon-goldman-sachs)

??? question "What is a metastable failure?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google-/Amazon-style rounds · Flipkart

    A failure kept alive by the system's own reactions after the trigger is gone. A brief slowdown grows queues; requests time out and are retried; the extra load keeps queues long, so every request times out. Measured: 0% of users served for 30 s after a 5-second trigger. Breaking the loop takes shedding stale work, budgets and deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q4-what-is-a-metastable-failure-l3l4-google-amazon-style-rounds-flipkart)

??? question "Does jitter fix retry storms?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    It fixes synchronization, not volume. It turned a reconnect herd that took 42 s into one that took 6 s, but in the metastable experiment jittered retries still stayed at 0% goodput: the retries still arrived. Volume needs a budget or shedding.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q5-does-jitter-fix-retry-storms-l3-amazon-sap)

??? question "How should a server shed load?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    Cheaply and early, by what predicts failure: queue time and the caller's deadline. Bounded concurrency with fast rejection, then drop queued work that has waited longer than the callers can wait. Measured: shedding at the client's timeout (300 ms) didn't recover; at 150 ms the service recovered the moment the trigger ended.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q6-how-should-a-server-shed-load-l3-flipkart-swiggy)

??? question "What is deadline propagation?"
    <span class="badge badge-l3">L3</span> Google-style rounds · JP Morgan

    Sending the absolute time the original caller stops waiting on every hop (gRPC does it natively), so each service refuses or abandons work that can't finish in time and never extends the budget. It removes wasted work deep in call chains.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q7-what-is-deadline-propagation-l3-google-style-rounds-jp-morgan)

??? question "What is a gray failure?"
    <span class="badge badge-l3">L3</span> Microsoft-/Amazon-style rounds · SAP

    A degradation some requests see and health checks don't: a slow disk, a noisy neighbour. Detect it from the callers' side (per-instance latency, outstanding requests) and route around it: P2C sent the slow instance half its share and halved slow users at fan-out 10 (measured).

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q9-what-is-a-gray-failure-l3-microsoft-amazon-style-rounds-sap)

??? question "Why does fan-out hurt tail latency?"
    <span class="badge badge-l3">L3</span> Google-style rounds · Flipkart

    A request that waits for *k* calls is slow if any is slow: 1 − (1 − p)ᵏ. With 5% slow calls, fan-out 10 made 41–42% of requests slow (measured). Reduce fan-out, balance by load, hedge idempotent reads, and set per-call deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q10-why-does-fan-out-hurt-tail-latency-l3-google-style-rounds-flipkart)

??? question "What changes with virtual threads?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    The thread pool used to be an accidental bulkhead: 200 Tomcat threads capped concurrent work. With `spring.threads.virtual.enabled`, each request gets a virtual thread and that cap disappears, so a slow dependency can accumulate unbounded concurrent requests. Add explicit limits (`@ConcurrencyLimit(REJECT)`, connection pool sizes, semaphores) and deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q11-what-changes-with-virtual-threads-l3-sap-jp-morgan)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "Count-based or time-based window?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    A count window reacts in "N calls", which is fast for busy endpoints and slow for quiet ones: measured, the 100-call default opened in 0.5–0.6 s at 100/s and 7.5–10 s at 5/s. A time window judges the same span at any rate (5 s opened in 2–4 s at both); add a minimum number of calls so a few failures on a quiet endpoint don't open it.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q2-count-based-or-time-based-window-l3-flipkart-swiggy)

??? question "Why doesn't my circuit breaker open when the dependency is slow?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Slow successes are only counted as slow above `slowCallDurationThreshold`, which defaults to 60 s, and the slow-call rate threshold defaults to 100%. Measured: 20 seconds of 3-second answers never opened a default breaker. Set the slow-call threshold below your callers' timeout (FoodFleet: 1 s at 50%).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q3-why-doesnt-my-circuit-breaker-open-when-the-dependency-is-slow-l3-amazon-walmart)

??? question "In what order do Resilience4j's annotations apply?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Retry outermost, then CircuitBreaker, RateLimiter, TimeLimiter, Bulkhead innermost, regardless of the order written (configurable with `*-aspect-order`). So each attempt is counted by the breaker and needs a bulkhead permit, and the retry must ignore the breaker's `CallNotPermittedException`, or an open breaker costs every user the retry delays (1 s, measured).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q4-in-what-order-do-resilience4js-annotations-apply-l3-sap-jp-morgan)

??? question "Where should the fallback go?"
    <span class="badge badge-l3">L3</span> Flipkart · Myntra

    On the outermost annotation (`@Retry`), so it runs only when everything else has given up. On `@CircuitBreaker` it catches the first failure inside the retry, and the retry never runs: measured 68–70% fresh results against 97–98%.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q5-where-should-the-fallback-go-l3-flipkart-myntra)

??? question "Does a TimeLimiter stop the slow call?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Atlassian

    It stops waiting. `cancel(true)` on a `CompletableFuture` doesn't interrupt anything: 40 of 40 tasks ran on (measured). An `ExecutorService` future is interrupted, which stops sleeps and the JDK `HttpClient`, not `HttpURLConnection` reads or CPU loops. The timeout that stops work belongs in the client: read timeouts, query timeouts.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q6-does-a-timelimiter-stop-the-slow-call-l3-goldman-sachs-atlassian)

??? question "Why doesn't my Resilience4j retry retry RestClient failures?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Infosys · Wipro

    `retryExceptions` matches the exception's own class, and RestClient throws `ResourceAccessException` wrapping the `IOException`. Use `retryOnException` with a predicate that walks the cause chain (or Spring's `@Retryable(includes = ...)`, which does).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q7-why-doesnt-my-resilience4j-retry-retry-restclient-failures-l2l3-infosys-wipro)

??? question "Resilience4j or Spring Framework 7's `@Retryable`?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Spring 7 covers retries and concurrency limits with no extra dependency; set a multiplier, jitter (at least the delay: it never goes below the initial delay) and a timeout. For circuit breakers, slow-call detection, rate limits, metrics and per-instance configuration, use Resilience4j. They coexist.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q8-resilience4j-or-spring-framework-7s-retryable-l2l3-sap-cognizant)

??? question "What does `@ConcurrencyLimit` do when the limit is reached?"
    <span class="badge badge-l3">L3</span> any Spring 7 shop

    By default it blocks, with no timeout. At 2× overload that served everyone at a 10 s p99 with about 4,000 callers queued (measured). `policy = REJECT` throws `InvocationRejectedException` at once, which a controller turns into 503: half the users served at normal latency instead of all of them late.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q9-what-does-concurrencylimit-do-when-the-limit-is-reached-l3-any-spring-7-shop)

??? question "What's wrong with the default RateLimiter?"
    <span class="badge badge-l3">L3</span> Razorpay · PhonePe

    50 permits every 500 ns is 100 million a second: it allowed about 11 million calls a second on two cores. Its 5 s wait turns over-limit traffic into a queue (over 900 callers waiting, measured). And fixed periods allow twice the limit across a period boundary: use short periods or a token bucket where bursts matter.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q10-whats-wrong-with-the-default-ratelimiter-l3-razorpay-phonepe)

??? question "How do you size a bulkhead?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Little's law: concurrent calls = throughput × latency. 400 calls/s at 50 ms needs 20; add headroom for the latency you accept, not the latency of a broken dependency. Reject when full (wait 0). Measured: a bulkhead of 32 held a slow dependency to 32 calls in progress where an unbounded caller reached 146–166.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q11-how-do-you-size-a-bulkhead-l3l4-amazon-flipkart)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "How do you choose a timeout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    From two numbers: the dependency's latency distribution (a ceiling a little above its p99.9, so healthy calls aren't cut off) and the caller's remaining deadline (never wait longer than the caller will). Use the smaller, minus a reserve for your own work, and skip the call if what's left is too small to succeed.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q1-how-do-you-choose-a-timeout-l2l3-amazon-flipkart)

??? question "What is a timeout budget?"
    <span class="badge badge-l3">L3</span> Google-style rounds · Swiggy

    The original caller's deadline, shared out along the call chain: each hop gets what is left, so inner timeouts are always shorter than outer ones and retries come out of the same pot. It stops services working for callers who have already given up.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q2-what-is-a-timeout-budget-l3-google-style-rounds-swiggy)

??? question "What is a hedged request, and when is it dangerous?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google · Amazon

    A duplicate request sent when the first hasn't answered by about the p95; the first answer wins. It cut p99 from the full 1 s budget to 88 ms for 10% more calls with spare capacity (measured). Near capacity, hedges make calls slower, which triggers more hedges: at 85% load only 18–43% of users were served. Budget them (5% kept 99%) and use them only for idempotent reads.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q3-what-is-a-hedged-request-and-when-is-it-dangerous-l3l4-google-amazon)

??? question "Why not just retry with a short timeout?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    The abandoned attempt keeps running, so a short timeout multiplies load exactly when the dependency is slow. At 85% load, 100 ms timeouts with retries sent 5.6–8.2 calls per user and served 20–49% (measured); a 300 ms timeout that only fired on real stalls served 100%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q4-why-not-just-retry-with-a-short-timeout-l3-flipkart-goldman-sachs)

??? question "What is adaptive concurrency limiting?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Netflix-style rounds · Uber

    A limit on in-flight requests that moves with latency, like TCP congestion control: AIMD adds one while healthy and cuts 10% when a request is dropped or too slow; Vegas and Gradient infer queueing from latency trends. Requests over the limit get an immediate 503 instead of joining a queue.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q5-what-is-adaptive-concurrency-limiting-l3l4-netflix-style-rounds-uber)

??? question "Why is 'no limit' worse than any limit?"
    <span class="badge badge-l3">L3</span> Amazon · Zomato

    Without a limit the excess waits in a queue: everyone gets slow, callers time out, and the service keeps working for them after they leave. Measured: 4% of users served in a slowdown, and only 2–7% after it ended, because the backlog had to drain. With any limit, half were served and recovery was immediate.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q7-why-is-no-limit-worse-than-any-limit-l3-amazon-zomato)

??? question "How do you shed load by priority?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    Mark requests at the edge (checkout critical, browsing sheddable), and give each class a guaranteed share of the admission limit that applies only when the limit is reached, so spare capacity is still shared. Measured at 2× overload: 79–81% of checkouts served instead of about 50%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q8-how-do-you-shed-load-by-priority-l3-swiggy-flipkart)

??? question "LIFO or FIFO for a waiting queue?"
    <span class="badge badge-l3">L3</span> Facebook-style rounds · Atlassian

    Under overload, LIFO: the newest requests are the ones whose callers are still waiting. In measurements LIFO and FIFO served the same share, but LIFO's p99 stayed at 278–289 ms against FIFO's 461–466 ms. Better still, reject early and let the caller retry within its budget.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q9-lifo-or-fifo-for-a-waiting-queue-l3-facebook-style-rounds-atlassian)

??? question "What is a brownout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Myntra · Amazon

    Turning optional features down automatically when their dependencies struggle: a controller sets the share of requests that use the feature from how many recent calls were slow. It removes load, which a timeout alone doesn't: timeouts left recommendations at 0% for 15 s after a spike; the dimmer was back to 77–81%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q10-what-is-a-brownout-l2l3-myntra-amazon)

??? question "What is backpressure?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Confluent-style rounds · Walmart

    The consumer controlling how fast it receives: in `Flow`, by requesting *n* items; in Kafka, by polling. When the buffer is full the producer blocks or drops; buffering without bound only delays the failure. Measured: an unbounded queue aged events to 10 s; a 256-item buffer kept them under 0.3 s.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q11-what-is-backpressure-l2l3-confluent-style-rounds-walmart)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "How is it different from testing?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Accenture · SAP

    A test checks a known expected behaviour of a component; an experiment asks an open question about the whole system under conditions tests don't create (a black-holed dependency, a slow trickle, a dead pod at peak). Once an experiment's hypothesis holds, it often becomes a scheduled regression check, much like a test.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q2-how-is-it-different-from-testing-l2l3-accenture-sap)

??? question "What is a steady-state hypothesis?"
    <span class="badge badge-l3">L3</span> Netflix-style rounds · Flipkart

    A measurable statement of normal behaviour in user terms (99% of menu requests succeed within 300 ms) and a prediction that it holds under a fault. Using user-facing metrics keeps the experiment honest: internal metrics can look fine while users suffer.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q3-what-is-a-steady-state-hypothesis-l3-netflix-style-rounds-flipkart)

??? question "How do you limit the blast radius?"
    <span class="badge badge-l3">L3</span> Amazon · Swiggy

    Start small and widen: one instance (`mode: one`), a share of connections (`toxicity`), staging before production, short durations, business hours. Measured: the same fault on both instances instead of one hurt 93–94% of users during the experiment.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q4-how-do-you-limit-the-blast-radius-l3-amazon-swiggy)

??? question "What is an abort condition, and why automate it?"
    <span class="badge badge-l3">L3</span> Netflix-style rounds · Gremlin-style rounds

    A continuously checked condition (the SLO failing for N windows) that stops the experiment and rolls the fault back. Humans watching dashboards take minutes; measured, an automatic guard after three bad seconds cut the users hurt from about 1,500 to 150–180.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q5-what-is-an-abort-condition-and-why-automate-it-l3-netflix-style-rounds-gremlin-style-rounds)

??? question "Which faults would you inject first?"
    <span class="badge badge-l3">L3</span> Uber · Zomato

    The ones that produce hangs rather than errors: black holes (requests swallowed, connections held) and slow trickles, then latency, then pod kills and refused connections. Measured: fast failures were handled by every client; three of four client setups hung on a trickle.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q6-which-faults-would-you-inject-first-l3-uber-zomato)

??? question "Why did a circuit breaker fail a chaos experiment?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Walmart

    A breaker counts outcomes, and a call with no read timeout into a black hole never has one. Measured: no opening in 20 s, about 2,000 calls stuck, users failing in 19 of 20 windows; with a 250 ms read timeout the same experiment held. Timeouts are what feed a breaker.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q7-why-did-a-circuit-breaker-fail-a-chaos-experiment-l3l4-goldman-sachs-walmart)

??? question "Chaos Mesh or LitmusChaos?"
    <span class="badge badge-l3">L3</span> any Kubernetes shop

    Both run experiments as custom resources on Kubernetes. Chaos Mesh has a broad set of fault types, workflows with status checks that abort, and schedules; LitmusChaos has a hub of ready-made experiments and probes (HTTP, Prometheus, command, Kubernetes) with `stopOnFailure`. Choose by the experiments you need and the guard model; validate manifests strictly either way.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q9-chaos-mesh-or-litmuschaos-l3-any-kubernetes-shop)

??? question "What is a game day?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · PayPal

    A planned session where a team runs one or more experiments together, with observers, a runbook and a stop button, practising the system's behaviour and the team's response (detection, decisions, communication), followed by a written review with actions.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q11-what-is-a-game-day-l2l3-flipkart-paypal)


**[46. Disaster Recovery & Multi-Region](../resilience/ch46-disaster-recovery.md)**

??? question "Synchronous or asynchronous replication?"
    <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Synchronous loses nothing but every commit waits a round trip to the standby; asynchronous is free but loses what the standby hasn't received. Measured: in another zone, sync cost 1.3–1.7 ms per commit against 0.9–1.0 ms and lost nothing; across a 60 ms round trip each commit took ~63 ms; async lost 85–278 commits when the region was lost. Hence sync to nearby zones, async to far regions.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q2-synchronous-or-asynchronous-replication-l3-flipkart-razorpay)

??? question "What do remote_write, on and remote_apply mean?"
    <span class="badge badge-l3">L3</span> PostgreSQL-heavy shops

    What a synchronous commit waits for on the standby: the WAL handed to its OS (`remote_write`), flushed to its disk (`on`), or applied so standby queries see it (`remote_apply`, for read-your-writes on replicas). All three lost nothing when only the primary died in the measurements; the difference is in latency and in surviving the standby's own crash.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q3-what-do-remote_write-on-and-remote_apply-mean-l3-postgresql-heavy-shops)

??? question "What is fencing?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Atlassian · Microsoft-style rounds

    Making sure the old primary can no longer accept writes before promoting a new one: power it off, cut its network, revoke its storage or credentials, withdraw it from DNS and load balancers. It turns "two primaries" into "one primary and one stopped server".

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q5-what-is-fencing-l3l4-atlassian-microsoft-style-rounds)

??? question "How long does a failover take, and where does the time go?"
    <span class="badge badge-l3">L3</span> Swiggy · PhonePe

    Detection (probe interval × threshold), fencing, promotion, and clients finding the new primary. Measured: promotion took 0.1 s; detection 3.1 s with 1 s probes and 30.1 s with 10 s probes; clients asking each host for its role resumed 0.3 s after promotion, clients following a 30 s DNS TTL after 24–33 s. Tune probes and clients before the database.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q6-how-long-does-a-failover-take-and-where-does-the-time-go-l3-swiggy-phonepe)

??? question "How should an application connect to a replicated PostgreSQL?"
    <span class="badge badge-l3">L3</span> any Spring shop

    List every host in the JDBC URL with `targetServerType=primary`, so the driver connects to whichever accepts writes, with a short `hostRecheckSeconds`, a bounded `connectTimeout` and a non-zero `socketTimeout` (the default 0 waits for ever on a dead primary), and a pool that recycles connections.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q7-how-should-an-application-connect-to-a-replicated-postgresql-l3-any-spring-shop)

??? question "Replicas or backups?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Accenture · SAP

    Both: replicas protect against losing a machine or a zone, and copy bad writes everywhere at once; backups with WAL archiving protect against bad writes, deleted data and ransomware through point-in-time recovery. A backup is only as good as its last timed restore.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q8-replicas-or-backups-l2l3-accenture-sap)

??? question "What limits a restore's RPO and RTO?"
    <span class="badge badge-l3">L3</span> Walmart · Flipkart

    RPO: how far the WAL archive trails the database (`archive_timeout`, or near zero with streamed WAL). RTO: copying the base backup and replaying the WAL since, which grows with size and write volume. Measured: `archive_timeout = 60 s` lost 298–317 orders (the last 15–16 s), 10 s lost 99–119, `pg_receivewal --synchronous` none; restoring 0.75 GB plus 0.5 GB of WAL took about 5 s on local disk.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q9-what-limits-a-restores-rpo-and-rto-l3-walmart-flipkart)


### Security


**[35. Security Fundamentals & Threat Modeling](../security/ch35-security-fundamentals.md)**

??? question "What is STRIDE, and how do you apply it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Six threat categories, each the violation of a security property: Spoofing (authenticity), Tampering (integrity), Repudiation, Information disclosure (confidentiality), Denial of service (availability), Elevation of privilege (authorization). Draw the system as a data-flow diagram with trust boundaries, apply the categories that fit each element type, and record a mitigation or acceptance for each. FoodFleet's checkout: 21 elements, 69 threats.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q1-what-is-stride-and-how-do-you-apply-it-l2l3-cognizant-sap)

??? question "What is the most common API vulnerability, and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Broken object-level authorization (OWASP API1): authenticated callers acting on objects that aren't theirs by changing an id. Check ownership on every operation in the service, using the authenticated caller and the stored object, never ids from the request; answer 404 for objects the caller can't see; test the full role × action × state matrix.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q2-what-is-the-most-common-api-vulnerability-and-how-do-you-prevent-it-l2l3-flipkart-amazon)

??? question "Why return 404 instead of 403 for someone else's order?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    A 403 confirms the object exists, so sequential or guessable ids become a map of the business (order volumes, customer counts). 404 reveals nothing. Use 403 when the caller may see the object but not perform the action, like a customer refunding their own order.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q3-why-return-404-instead-of-403-for-someone-elses-order-l3-amazon-goldman-sachs)

??? question "What is mass assignment, and how does it happen in Spring?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    Binding a request body onto an entity so a client can set fields it shouldn't (status, price, owner): OWASP API3. It happens when controllers accept entities as `@RequestBody`. Use request types with only the changeable fields, and reject unknown fields: Jackson 3, and so Spring Boot 4, ignores them by default; set `spring.jackson.deserialization.fail-on-unknown-properties: true`.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q4-what-is-mass-assignment-and-how-does-it-happen-in-spring-l3-sap-cognizant)

??? question "How do you protect a login endpoint against guessing without locking customers out?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Growing delays per account after a few failures, plus limits per source address; no hard lockout, which attackers can trigger on purpose. Same answer for unknown users as for wrong passwords. Measured: ~26,700 checks an hour against one account without a throttle, 18 with it, whatever the number of source addresses. Add breached-password checks and MFA.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q6-how-do-you-protect-a-login-endpoint-against-guessing-without-locking-customers-out-l3-amazon-jp-morgan)

??? question "What is SSRF, and how do you defend against it?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Server-side request forgery (API7): the server fetches a URL the attacker chose, reaching internal systems or cloud metadata. Allow only the schemes and ports you need, resolve the host and refuse internal, loopback, link-local and private addresses (all of the resolved ones, not the URL's text), refuse redirects, and route outbound traffic through an egress proxy with the same rules.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q7-what-is-ssrf-and-how-do-you-defend-against-it-l3-goldman-sachs-amazon)

??? question "Where should authorization happen in a microservice system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP

    The gateway authenticates and applies coarse rules; the service that owns the data makes object, property and function decisions, because only it knows who owns what. Services re-verify tokens (zero trust); the database restricts by credentials and, optionally, row-level security as defence in depth.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q8-where-should-authorization-happen-in-a-microservice-system-l3l4-flipkart-sap)

??? question "Walk through the OWASP API Security Top 10."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · Amazon

    2023 edition: API1 broken object-level authorization, API2 broken authentication, API3 broken object property level authorization, API4 unrestricted resource consumption, API5 broken function-level authorization, API6 unrestricted access to sensitive business flows, API7 SSRF, API8 security misconfiguration, API9 improper inventory management, API10 unsafe consumption of APIs. Three of the first five are authorization.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q10-walk-through-the-owasp-api-security-top-10-l2l3-cognizant-amazon)

??? question "How do you test authorization thoroughly?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Beyond example tests, generate the full matrix of roles × actions × object states and compare the policy against rules written separately, in the business's words. Measured here: 18,600 decisions, 0 disagreements. Include awkward identities (one person with two roles) and every state.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q11-how-do-you-test-authorization-thoroughly-l3l4-goldman-sachs-sap)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "Walk through the authorization code flow with PKCE."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    The client creates a random verifier and sends its SHA-256 hash with the authorization request; the user signs in; the authorization server redirects back with a one-time code; the client redeems the code with the verifier at the token endpoint and gets tokens. A code captured on the way back is useless without the verifier.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q1-walk-through-the-authorization-code-flow-with-pkce-l2l3-flipkart-cognizant)

??? question "What changes in OAuth 2.1?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    It consolidates the security best practices: PKCE for all authorization code flows, no implicit flow, no password grant, exact redirect URI matching, no bearer tokens in query strings, refresh tokens sender-constrained or rotated. As of September 2026 it is still an Internet-Draft; RFC 9700 already recommends the same.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q2-what-changes-in-oauth-21-l2l3-sap-amazon)

??? question "What must a resource server check in a JWT?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Signature with a key from the issuer's JWKS, chosen by kid; the algorithm from its own allowlist and matching the key; issuer; audience; type; expiry and not-before with small skew; then scopes for the operation. Missing audience checks and token-chosen algorithms are the classic failures.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q4-what-must-a-resource-server-check-in-a-jwt-l3-amazon-goldman-sachs)

??? question "JWT or opaque tokens?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · JP Morgan

    JWTs verify locally (measured ~0.05–0.6 ms, no network) and keep working when the issuer is down, but can't be revoked before expiry (a revoked 15-minute token works 450 s on average). Opaque tokens are introspected (a network call per request or cache miss) and revocable immediately. Use short JWTs broadly and introspection where revocation must be immediate.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q5-jwt-or-opaque-tokens-l3l4-flipkart-jp-morgan)

??? question "How do you revoke a JWT?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    You mostly can't; you limit its life. Keep access tokens short, revoke the refresh token so no new ones are issued, and for sensitive APIs introspect or check a deny-list of token ids. Sender-constrained tokens make stolen ones unusable.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q6-how-do-you-revoke-a-jwt-l3-goldman-sachs-sap)

??? question "How should signing keys be rotated?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    In stages: publish the new key in the JWKS, wait longer than resource servers cache key sets, start signing with it, and keep the old key published until its tokens expire. Resource servers refresh on unknown kids with a rate limit. Measured: sudden rotation rejected 29 requests, staged 0.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q7-how-should-signing-keys-be-rotated-l3-amazon-jp-morgan)

??? question "Token relay or token exchange?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Relay passes the user's token on, so every downstream service and log holds a credential for everything the user's app may do. Exchange (RFC 8693) gets a new token for one audience, minimum scopes, minutes, with an `act` claim recording the acting service. Relay is acceptable only within one trust boundary with audience-restricted tokens.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q8-token-relay-or-token-exchange-l3l4-goldman-sachs-flipkart)

??? question "RS256 or ES256?"
    <span class="badge badge-l3">L3</span> SAP · Amazon

    Both are fine; never HS256 between parties. Measured in the JDK: RS256 verifies ~15× faster (verification dominates in microservices), ES256 signs ~5× faster and tokens are 40% smaller. Choose by what your issuer supports and where the CPU goes.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q9-rs256-or-es256-l3-sap-amazon)

??? question "Where should a browser app keep tokens?"
    <span class="badge badge-l3">L3</span> Flipkart · any

    Preferably not in the browser: a backend-for-frontend runs the code flow, keeps tokens server-side, and gives the browser an HttpOnly, Secure, SameSite session cookie. If tokens must be in the browser, keep them in memory, short-lived, with refresh token rotation, never in local storage.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q10-where-should-a-browser-app-keep-tokens-l3-flipkart-any)

??? question "How does a service call another service with no user involved?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Client credentials: the service authenticates to the token endpoint (private key JWT or mTLS better than a shared secret) and gets a token for the target audience with its own scopes. In a mesh, workload identity (SPIFFE, chapter 38) can replace or complement it.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q11-how-does-a-service-call-another-service-with-no-user-involved-l2l3-cognizant-sap)


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "How do you map Keycloak roles to Spring Security authorities?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    A `Converter<Jwt, Collection<GrantedAuthority>>` on the `JwtAuthenticationConverter` that reads `realm_access.roles` (and client roles if used) and the `scope` claim, keeping only roles the application defines. Spring Boot 4.1 also offers `authorities-claim-expressions` for simple cases.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q3-how-do-you-map-keycloak-roles-to-spring-security-authorities-l3-sap-flipkart)

??? question "Where do object-level checks go in Spring?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    In the service, with `@EnableMethodSecurity` and `@PreAuthorize("@orderAccess.canRead(authentication, #id)")`, the bean loading the object and applying plain-Java rules. URL rules can't know who owns an object. Answer 404 for denied reads.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q4-where-do-object-level-checks-go-in-spring-l3-amazon-goldman-sachs)

??? question "Why is CSRF disabled in this API but not in a BFF?"
    <span class="badge badge-l3">L3</span> Amazon · any

    CSRF exploits credentials the browser attaches automatically (cookies). Bearer tokens in an `Authorization` header aren't attached automatically, so a stateless token API doesn't need CSRF protection. A BFF that uses a session cookie does.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q5-why-is-csrf-disabled-in-this-api-but-not-in-a-bff-l3-amazon-any)

??? question "What changed in Spring Security 7?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    It ships with Spring Boot 4: the authorization server is part of Spring Security (`http.oauth2AuthorizationServer`), only the lambda DSL remains, and RFC 9068 validation is available (`JwtValidators.createAtJwtValidator()`). Check the migration guide and re-test security after upgrading.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q6-what-changed-in-spring-security-7-l3-sap-cognizant)

??? question "What defaults of Spring's authorization server would you change?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Access tokens addressed to the client (set audiences per API), refresh tokens reused (rotate them for public clients), no `typ: at+jwt` (add it), and token exchange scopes checked only against the client's registration (register minimal scopes). Read from the 7.1.1 sources in this chapter.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q7-what-defaults-of-springs-authorization-server-would-you-change-l3l4-goldman-sachs-jp-morgan)

??? question "How do you test security rules?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    MockMvc with spring-security-test's `jwt()` post-processor and realistic claims: no token → 401, wrong scope → 403, other users' objects → 404, missing role → 403, unknown endpoint → 403. Plain-Java rule classes get exhaustive unit tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q8-how-do-you-test-security-rules-l2l3-flipkart-sap)

??? question "Spring Authorization Server or Keycloak?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Keycloak (or a managed provider) when you need user management, federation, MFA, admin UI and many applications. Spring's authorization server when you need an embedded, code-configured provider with custom behaviour, or for local development and tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q10-spring-authorization-server-or-keycloak-l3l4-amazon-sap)

??? question "How do you handle the 'authorization server is down' case?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    JWT resource servers keep working with cached JWKS until tokens expire; new logins and refreshes fail. Make the identity provider highly available, keep JWKS caching sensible, use short tokens, and alert on token errors.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q11-how-do-you-handle-the-authorization-server-is-down-case-l3-jp-morgan-flipkart)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "What is SPIFFE, and what is SPIRE?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    SPIFFE standardises workload identity: SPIFFE IDs (`spiffe://trust-domain/path`), SVIDs (X.509 or JWT documents carrying them), trust bundles and the Workload API. SPIRE implements it: it attests nodes and workloads (for example by Kubernetes namespace and service account) and issues short-lived SVIDs that rotate automatically, so workloads get identity without secrets.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q2-what-is-spiffe-and-what-is-spire-l3-amazon-sap)

??? question "Why isn't a valid client certificate enough to allow a call?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    It proves identity, not permission. Every workload in the trust domain has a valid certificate; the service must decide which of them may call which route (peer policy, deny by default), and the token decides what may be done for which user.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q3-why-isnt-a-valid-client-certificate-enough-to-allow-a-call-l3-goldman-sachs-flipkart)

??? question "How do you rotate certificates without downtime?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Short lifetimes, rotation at about half-life, on both servers and clients (an `SSLContext` built once won't rotate), and CA rollovers with an overlap where bundles trust old and new CAs until every old SVID has expired. Measured: 0 failures with rotation, 77–78 failures on a CA switch without overlap.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q4-how-do-you-rotate-certificates-without-downtime-l3-amazon-jp-morgan)

??? question "How does Spring Boot do mutual TLS?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    SSL bundles: `spring.ssl.bundle.pem.<name>` with keystore and truststore, `server.ssl.bundle` and `server.ssl.client-auth: need`, `reload-on-update: true` for rotated files. Clients use `RestClientSsl.fromBundle`, which builds the request factory once, so rebuild clients on bundle updates.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q6-how-does-spring-boot-do-mutual-tls-l2l3-sap-cognizant)

??? question "Service mesh or application-level mTLS?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    A mesh gives mTLS and coarse policies everywhere without code changes, but the application sees no client certificate (certificate-bound tokens and per-request certificate checks don't work there) and Istio's defaults are permissive. Application-terminated mTLS gives the service the peer identity for fine-grained decisions and audit. Many platforms use the mesh broadly and application checks for sensitive services.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q7-service-mesh-or-application-level-mtls-l3l4-flipkart-amazon)

??? question "Client credentials vs token exchange between services?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Client credentials when the service acts for itself (sagas, jobs): one cached token per service. Token exchange when it acts for a user: a narrower token for the downstream audience carrying the user (chapter 36). Mutual TLS identifies the workload in both cases.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q8-client-credentials-vs-token-exchange-between-services-l3-amazon-goldman-sachs)

??? question "What goes wrong with Spring's client-credentials interceptor?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    By default it uses the current user as the principal and stores authorized clients per principal, so inside user requests it fetches a token per user (measured: 20,000 token requests and 5,000 stored tokens instead of 4). Use the service-oriented manager and a principal resolver that yields one fixed principal. Renewal has no lock, so many threads renew at once at expiry.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q9-what-goes-wrong-with-springs-client-credentials-interceptor-l3-sap-flipkart)

??? question "What is a certificate-bound access token?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    RFC 8705: the token carries the SHA-256 thumbprint of the client's certificate (`cnf.x5t#S256`), and the resource server accepts it only over a connection authenticated with that certificate. A stolen token is useless without the key. Spring Security validates it by default when the claim is present, but only where the application sees the client certificate.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q10-what-is-a-certificate-bound-access-token-l3-jp-morgan-goldman-sachs)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "What is 'secret zero', and how do you avoid it?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    The credential needed to fetch other secrets. Use platform identity: the pod's Kubernetes service-account token (or cloud workload identity) authenticates to Vault/OpenBao, which issues a scoped, short-lived token. Nothing is delivered to the pod.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q3-what-is-secret-zero-and-how-do-you-avoid-it-l3-amazon-sap)

??? question "Static vs dynamic secrets?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    Static secrets are created once and must be rotated deliberately; dynamic secrets are created per client on request with a lease, and expire or are revoked automatically. Dynamic database credentials give each pod its own role, so one leak is one role, and revoking it is one call. Measured: issuing took about 15 ms.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q4-static-vs-dynamic-secrets-l3-goldman-sachs-jp-morgan)

??? question "How do you rotate a database password without downtime?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Two valid credentials at once: change the account (or key) not in use, roll it out, switch, then change the other. In-place rotation fails until every instance has the new value. Measured: 14–16% of logins failed in place, 0 with two accounts. Dynamic credentials do this automatically.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q5-how-do-you-rotate-a-database-password-without-downtime-l3-amazon-flipkart)

??? question "Your Vault-backed service fails a day after deployment. Why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Dynamic credentials renewed but never rotated: renewal stops at max_ttl. Spring Cloud Vault 5.0.2 requests database credentials as renewable secrets. Request a rotating secret and update the pool (HikariCP 7's credentials provider), or set max_ttl longer than any pod lives. Measured: renew-only failed exactly at max_ttl.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q6-your-vault-backed-service-fails-a-day-after-deployment-why-l3l4-sap-goldman-sachs)

??? question "How do you keep secrets out of logs and dumps?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    A value type that masks itself, structured logging that never logs headers or credentials, actuator's `show-values: never`, and treating heap dumps as secrets. Measured: a `String` password was in every heap dump; only wiped `char[]`s were absent.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q9-how-do-you-keep-secrets-out-of-logs-and-dumps-l2l3-flipkart-any)

??? question "External Secrets Operator or the CSI driver or reading Vault directly?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    ESO syncs into Kubernetes Secrets (works with anything that reads Secrets; adds a copy in etcd and a refresh delay, one hour by default). The CSI driver mounts directly without a Secret object. Reading Vault directly (Spring Cloud Vault) gives leases and rotation in the application but couples it to Vault. FoodFleet: Vault directly for dynamic credentials, ESO for static third-party keys.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q10-external-secrets-operator-or-the-csi-driver-or-reading-vault-directly-l3-amazon-sap)

??? question "What happens when Vault/OpenBao is down?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    Running services keep working with their current credentials until their leases end; new pods can't start and renewals fail. Run it highly available with auto-unseal, alert on renewal failures, size lease TTLs to cover a realistic outage, and test the failure.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q11-what-happens-when-vaultopenbao-is-down-l3-jp-morgan-flipkart)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "How do you rate limit across 20 instances?"
    <span class="badge badge-l3">L3</span> Amazon · Swiggy

    Keep the state in a shared store: one Redis key per client, updated atomically by a Lua script (GCRA), using Redis's clock. Local limits multiply by the instance count (measured: 1,036 through a 260 budget with four instances; Redis, 258–260). Decide the failure mode: a short timeout and a per-instance fallback of limit ÷ instances.

    [Full answer in chapter 40](../security/ch40-api-security.md#q2-how-do-you-rate-limit-across-20-instances-l3-amazon-swiggy)

??? question "Where should rate limiting live: CDN, gateway or service?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    In layers. Per-address limits at the edge stop floods and credential stuffing cheaply; per-client and per-tenant limits need the authenticated identity, so they belong after authentication (gateway or service); business limits (one coupon per customer) belong in the service. Know your tools' behaviour: Spring Cloud Gateway's Redis limiter refills once a second and fails open.

    [Full answer in chapter 40](../security/ch40-api-security.md#q3-where-should-rate-limiting-live-cdn-gateway-or-service-l3-sap-flipkart)

??? question "One tenant is hurting all the others. What do you do?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Swiggy

    Limits keyed by tenant (and client within a tenant) in front of the global one, then per-tenant resource limits further down (connection pools, queues, database statement timeouts). Measured: with only a global limit the other tenants got 0.2% of their requests; with per-tenant limits, 100%.

    [Full answer in chapter 40](../security/ch40-api-security.md#q4-one-tenant-is-hurting-all-the-others-what-do-you-do-l3l4-goldman-sachs-swiggy)

??? question "What is ReDoS, and is Java safe from it?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    A regex that backtracks exponentially or polynomially on crafted input. JDK 9+ memoises failed positions for greedy loops, so the textbook `(\w+\s?)*` is fast, but the protection is off for patterns with backreferences and doesn't cover lazy loops or polynomial patterns. Measured: a lazy or backreferencing variant took over 10 s for 28 characters; a trailing-whitespace pattern 9.5 s for 20,000 spaces. Limit length first, avoid nested quantifiers, use RE2 for untrusted patterns.

    [Full answer in chapter 40](../security/ch40-api-security.md#q6-what-is-redos-and-is-java-safe-from-it-l3-jp-morgan-amazon)

??? question "When do you need CSRF protection?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Accenture

    When the browser attaches credentials automatically: cookies (and HTTP Basic, client certificates). An API that takes only bearer tokens in a header has nothing to forge. For cookie sessions use a CSRF token plus Fetch Metadata; SameSite is a useful extra but treats sibling subdomains as the same site (measured: they got even Strict cookies).

    [Full answer in chapter 40](../security/ch40-api-security.md#q8-when-do-you-need-csrf-protection-l2l3-sap-accenture)

??? question "WAF: worth it? How do you run it?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Accenture

    As one layer against generic attacks and as a fast virtual patch (Log4Shell), yes, and PCI DSS 4.0 requires one for public-facing payment applications. Tune it: as shipped, the CRS blocked 15% of FoodFleet's legitimate requests at PL1 and 49% at PL2 (measured), mostly methods and free text. Run detection-only first, write narrow exclusions, and don't expect it to catch BOLA or business-logic abuse (0 of 6 caught).

    [Full answer in chapter 40](../security/ch40-api-security.md#q10-waf-worth-it-how-do-you-run-it-l3-goldman-sachs-accenture)

??? question "How do you isolate tenants in a shared database?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Tenant from the token, never from the request; PostgreSQL row-level security on every shared table, `FORCE`d, with an application role that doesn't own the tables; the tenant set transaction-locally (`set_config(.., true)`) so pooled connections don't carry it. Measured: a forgotten predicate returned 40 rows instead of 2,000; a session-level `SET` leaked 4,000 rows to the next request. Silo databases for tenants that need them.

    [Full answer in chapter 40](../security/ch40-api-security.md#q11-how-do-you-isolate-tenants-in-a-shared-database-l3l4-sap-goldman-sachs)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "Your build passed the vulnerability scan last month. Are you safe?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Only for what was known last month. New advisories arrive daily: Keycloak 24.0.0 had 0 known findings on release day, 14 three months later, 196 today (measured). Rescan deployed SBOMs daily and act on what has a fix.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q2-your-build-passed-the-vulnerability-scan-last-month-are-you-safe-l3-amazon-flipkart)

??? question "How does a vulnerability scanner work, and why do two scanners disagree?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Inventory × advisory database × version matching. They differ in how they identify components (exact purls vs guessed CPEs: Syft guessed coordinates for 8–15% of jars), which databases they use, and how they compare versions (Maven's `2.0-rc1` < `2.0` < `2.0-sp1`; strings put `2.10` before `2.9`).

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q3-how-does-a-vulnerability-scanner-work-and-why-do-two-scanners-disagree-l3-goldman-sachs-amazon)

??? question "Which findings should fail the build?"
    <span class="badge badge-l3">L3</span> SAP · Infosys

    The ones a team can fix now: critical/high with a fixed version. Everything else is tracked with an owner. Accepted risk is a waiver naming one advisory and package, with a reason and an expiry, published as VEX. Blocking on everything gets the gate disabled.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q4-which-findings-should-fail-the-build-l3-sap-infosys)

??? question "What does signing an image prove?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    That someone holding a key (or a short-lived Sigstore certificate for an identity) signed this digest. It says nothing until you pin *who*: with `--certificate-identity-regexp '.*'` a file from the wrong project verified (measured). With a leaked key, a laptop rebuild verified; provenance naming builder and source refused it.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q5-what-does-signing-an-image-prove-l3-jp-morgan-amazon)

??? question "Keyless signing: how does it work, and why use it?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    The CI job's OIDC token is exchanged with Fulcio for a ten-minute certificate naming the workflow; the signature is logged in Rekor and/or timestamped; verifiers check the chain, the log/timestamp and the identity. No long-lived key to leak or rotate. syft's certificate was valid for ten minutes and verified a week later from the timestamp (measured).

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q6-keyless-signing-how-does-it-work-and-why-use-it-l3l4-goldman-sachs-sap)

??? question "How do you enforce that only trusted images run?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    An admission controller (Kyverno, Sigstore's policy-controller) that verifies signature identity and provenance for every pod, rejects tags it can't resolve, and rewrites tags to digests so the node pulls what was verified (Kyverno's `mutateDigest`, on by default). Run in Audit first, then Enforce, with monitoring and a break-glass path.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q9-how-do-you-enforce-that-only-trusted-images-run-l3-amazon-sap)

??? question "How do you comply with the RBI's payment data storage rules in a cloud architecture?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Paytm-/Razorpay-style rounds · JP Morgan

    Payment system data (including customer details and transaction data) stored only in India; if processed abroad, deleted there and brought back within one business day or 24 hours, whichever is earlier. Architecturally: classify services, run payment data stores and their backups, logs and analytics in Indian regions, and enforce it (FoodFleet's policy refuses `data-class: payment` outside Indian regions or the CDE). A CERT-In empanelled auditor's system audit report covers it.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q10-how-do-you-comply-with-the-rbis-payment-data-storage-rules-in-a-cloud-architecture-l3l4-paytm-razorpay-style-rounds-jp-morgan)


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "Which tests catch which bugs?"
    <span class="badge badge-l3">L3</span> Atlassian

    Measured on order-service with 20 planted bugs: unit tests caught all 7 logic bugs and nothing else; PostgreSQL repository tests 6 of 7 SQL bugs; contract tests 3 of 3 client bugs; component tests 12 across every kind, including the only catches of API bugs. Together, 19 of 20.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q4-which-tests-catch-which-bugs-l3-atlassian)

??? question "Pyramid or honeycomb for a microservice?"
    <span class="badge badge-l3">L3</span> Flipkart · Uber

    Measure where the service's bugs are. In the experiment, a pyramid without component tests caught 16 of 20 and a honeycomb without unit tests 15; each lost a different kind. Rules need unit tests at their edges; wiring needs integrated tests. Most microservices need a strong middle and unit tests for the rules they have.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q5-pyramid-or-honeycomb-for-a-microservice-l3-flipkart-uber)

??? question "What is mutation testing?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Planting bugs in working code and checking that tests fail. The share killed is a far better measure of a suite than line coverage. Tools like PIT generate small mutants automatically; hand-written realistic ones, from incidents, show which layer catches what.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q7-what-is-mutation-testing-l3-thoughtworks)

??? question "Your coverage is 90%. Is the suite good?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    Not necessarily. Coverage says code ran, not that anything was checked. Measured: R7 (rows returned in no defined order) ran under several tests and escaped all of them because they asserted only counts. Mutation analysis finds those gaps.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q8-your-coverage-is-90-is-the-suite-good-l3-goldman-sachs)

??? question "A test passed for the wrong reason. How does that happen?"
    <span class="badge badge-l3">L3</span> Microsoft

    Broad assertions. In the H2 suite, a test expecting a constraint failure used `assertThrows(IllegalStateException.class, …)` and passed because the insert failed earlier with a syntax error. Assert the specific cause or message.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q10-a-test-passed-for-the-wrong-reason-how-does-that-happen-l3-microsoft)

??? question "What should run on every commit, and what before release?"
    <span class="badge badge-l3">L3</span> Walmart

    Every commit: unit and contract tests, in under a minute or two. Before merge: repository and component tests. Before release: a few end-to-end tests and performance checks (chapters 64–65). The point is fast feedback for most bugs and full coverage before users see a change.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q11-what-should-run-on-every-commit-and-what-before-release-l3-walmart)


**[62. Unit, Slice & Testcontainers Tests](../testing/ch62-unit-slice-integration-tests.md)**

??? question "How do you keep Testcontainers tests fast?"
    <span class="badge badge-l3">L3</span> Atlassian

    One container per test run, not per class (measured: 4.0–4.7 s against 18.0–22.9 s for 8 classes); identical test configurations so Spring reuses contexts; rollback or truncation instead of new databases; reuse on developer machines (1.5–2.5 s). Testcontainers' PostgreSQL module already turns off `fsync`.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q4-how-do-you-keep-testcontainers-tests-fast-l3-atlassian)

??? question "What does `@ServiceConnection` do?"
    <span class="badge badge-l3">L3</span> VMware

    It tells Spring Boot to derive connection details (URL, user, password) from a container bean or field, so the application's DataSource points at the container without `@DynamicPropertySource` or properties. It also marks the DataSource as a test database for `@AutoConfigureTestDatabase`.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q5-what-does-serviceconnection-do-l3-vmware)

??? question "Your `@Transactional` tests pass alone and fail together. Why?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    Something commits outside the test's transaction (a `REQUIRES_NEW`, an async method, another DataSource, a message listener) and the rollback doesn't undo it. Measured: with the code committing on its own connection, 299 of 300 tests failed on the previous test's row. Truncate between tests instead.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q6-your-transactional-tests-pass-alone-and-fail-together-why-l3-goldman-sachs)

??? question "Rollback, truncate or a new database per test?"
    <span class="badge badge-l3">L3</span> Flipkart

    Rollback for data slices (0.30–0.34 ms per test) when everything happens on the test's connection; truncate for integrated tests (7.8–7.9 ms), which works whatever the code does; a database per test from a template (51–73 ms) for schema changes or parallel tests.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q7-rollback-truncate-or-a-new-database-per-test-l3-flipkart)

??? question "How do you test a 'once per customer' rule?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    With concurrent callers on the real database, asserting exactly one winner and no errors. Measured: check-then-insert passed every single-threaded test and raised 421–449 errors in 300 concurrent rounds; without the key, 172–186 rounds redeemed twice. `ON CONFLICT` had neither.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q8-how-do-you-test-a-once-per-customer-rule-l3-zomato-phonepe)

??? question "What is the Spring test context cache, and how do you break it?"
    <span class="badge badge-l3">L3</span> Microsoft

    Spring keeps up to 32 application contexts per JVM, keyed by the test's full configuration, and pauses ones not in use. Any difference (a `@MockitoBean`, a property, a profile, `@DirtiesContext`) makes a new context. Too many configurations mean slow suites and evictions.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q9-what-is-the-spring-test-context-cache-and-how-do-you-break-it-l3-microsoft)

??? question "Should container reuse be on in CI?"
    <span class="badge badge-l3">L3</span> Uber

    No. A reused container isn't removed and keeps data between runs; CI should start clean. Testcontainers only reuses when `testcontainers.reuse.enable=true` is set in the user's environment, which is meant for developers' machines.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q10-should-container-reuse-be-on-in-ci-l3-uber)


**[63. Contract Testing](../testing/ch63-contract-testing.md)**

??? question "Why not just diff the OpenAPI document?"
    <span class="badge badge-l3">L3</span> Razorpay

    Because it judges the schema, not the consumers. Measured on twelve changes: the diff caught 5 of 9 real breaks with 2 false alarms, missed a change of units entirely, and rated two real breaks as INFO. But it was the only check that caught a new enum value, so run it too, for review.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q4-why-not-just-diff-the-openapi-document-l3-razorpay)

??? question "What are matchers, and how strict should they be?"
    <span class="badge badge-l3">L3</span> Atlassian

    They say how a response is compared: equal to the example, same type, matching a format, or an array of at least N like the example. Measured: recorded examples with no matchers flagged all 5 harmless data changes; type matchers alone missed prices moving to paise; types plus formats caught 7 of 9 with no false alarms.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q5-what-are-matchers-and-how-strict-should-they-be-l3-atlassian)

??? question "What is can-i-deploy?"
    <span class="badge badge-l3">L3</span> Swiggy

    A broker query: has this version of a service been successfully verified against the versions of its partners deployed in the target environment? The Pact Broker answers yes only if every considered verification succeeded, and "unknown" if a required one is missing. It replaces "deploy and see" with a per-consumer answer.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q7-what-is-can-i-deploy-l3-swiggy)

??? question "Can contract tests replace end-to-end tests?"
    <span class="badge badge-l3">L3</span> Flipkart

    For API compatibility, mostly. Measured: contracts caught the same 7 of 9 breaks as an E2E suite, without deploying anything together. Keep a few E2E journeys for what contracts can't see: whole flows, configuration and infrastructure (chapter 64).

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q8-can-contract-tests-replace-end-to-end-tests-l3-flipkart)

??? question "A provider wants to remove a field. How do you know it's safe?"
    <span class="badge badge-l3">L3</span> Zomato

    Check the contracts: if no consumer's contract mentions the field, verification passes and it's safe. Measured: removing an unused field passed both contracts (correctly) while the OpenAPI diff called it breaking. If a consumer uses it, its contract fails and names the team to talk to.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q9-a-provider-wants-to-remove-a-field-how-do-you-know-its-safe-l3-zomato)

??? question "How do you change the meaning of a field safely?"
    <span class="badge badge-l3">L3</span> PhonePe

    Don't. Add a new field (`pricePaise`), migrate consumers, then remove the old one when no contract uses it. Measured: prices moved to paise passed the schema diff and type-only contracts, and only formats and real values caught it.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q10-how-do-you-change-the-meaning-of-a-field-safely-l3-phonepe)

??? question "Contracts for asynchronous messages?"
    <span class="badge badge-l3">L3</span> Uber

    The same idea: Pact message pacts and Spring Cloud Contract messaging contracts describe the message a consumer expects, and the producer verifies that its code produces it. Combine with a schema registry's compatibility rules (chapter 27) for the format.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q11-contracts-for-asynchronous-messages-l3-uber)


**[64. Component & End-to-End Testing](../testing/ch64-component-e2e-testing.md)**

??? question "What's a preview (ephemeral) environment?"
    <span class="badge badge-l3">L3</span> Atlassian

    A complete environment created for one pull request or pipeline run, usually a namespace from the production manifests, and deleted afterwards. Argo CD's ApplicationSet pull-request generator creates one per open pull request. Measured: a four-service environment was answering in about 7 s, 14–20 s four at once on a small node.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q4-whats-a-preview-ephemeral-environment-l3-atlassian)

??? question "Shared staging or preview environments?"
    <span class="badge badge-l3">L3</span> Flipkart

    Preview environments for automated tests; staging, if kept, for people. Measured: four pipelines sharing staging passed 1 of 12 suite runs with fixed data and 5 of 12 with unique data; fresh environments passed 12 of 12.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q5-shared-staging-or-preview-environments-l3-flipkart)

??? question "Unique test data fixed the coupon failures. Why not the stock?"
    <span class="badge badge-l3">L3</span> Zomato

    Because stock isn't the test's data, it's the environment's state, shared by every test that orders that dish. Unique customers isolate what tests own; shared counters (stock, quotas, rate limits) need a fresh environment or a test design that avoids them.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q6-unique-test-data-fixed-the-coupon-failures-why-not-the-stock-l3-zomato)

??? question "How many E2E tests should a system have?"
    <span class="badge badge-l3">L3</span> Uber

    Few: the journeys that matter most and that nothing smaller can check (whole flows across services, real configuration, real infrastructure). Everything else belongs in unit, contract and component tests, which are faster and say where the problem is.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q7-how-many-e2e-tests-should-a-system-have-l3-uber)

??? question "What do you do with a flaky test?"
    <span class="badge badge-l3">L3</span> Google

    Quarantine it (it stops gating), find the cause (usually timing, shared state or order dependence), fix it, then restore it. Never rerun to green silently: a suite that's sometimes red trains everyone to ignore red.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q8-what-do-you-do-with-a-flaky-test-l3-google)

??? question "How do you make component tests realistic?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    The real service binary (`@SpringBootTest` on a random port), the real database engine in a container, and stubs generated from or verified against the collaborators' contracts, so a stub can't drift from the real service.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q9-how-do-you-make-component-tests-realistic-l3-thoughtworks)

??? question "What does a preview environment cost?"
    <span class="badge badge-l3">L3</span> PhonePe

    Seconds to create and delete (measured: about 7 s and 11 s for four services), plus node capacity while it runs: with many open pull requests, enough nodes, or scale-to-zero for idle previews. Seed data and secrets per environment are the operational work.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q10-what-does-a-preview-environment-cost-l3-phonepe)

??? question "Where do E2E tests run in the pipeline?"
    <span class="badge badge-l3">L3</span> Amazon

    After unit, contract and component tests pass, in the pull request's preview environment, before merge. A smaller smoke set runs again after each production deployment (chapter 58's canary analysis covers the rest).

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q11-where-do-e2e-tests-run-in-the-pipeline-l3-amazon)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "What's the difference between a closed and an open load model?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    A closed model has a fixed number of users who each wait for a response before sending again; an open model sends requests at a rate regardless. Real users of a web service are open. Measured: against a freezing service, the closed model's p99 was 15 ms and the open model's 942 ms.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q1-whats-the-difference-between-a-closed-and-an-open-load-model-l3-flipkart-swiggy)

??? question "What is coordinated omission?"
    <span class="badge badge-l3">L3</span> Azul · Uber

    A load generator that waits for responses stops sending during a stall, so it never records the requests that would have waited. Its percentiles are far too good. Fix it with an open model, or correct closed-model samples with HdrHistogram's expected interval (measured: p99 898–901 ms instead of 15 ms).

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q2-what-is-coordinated-omission-l3-azul-uber)

??? question "How do you find a service's capacity?"
    <span class="badge badge-l3">L3</span> Amazon

    Step an arrival rate up until the latency objective breaks, and note the rate. Measured: p99 stayed at 13–14 ms up to 320 requests a second, rose near 380, and reached 1.5–1.7 s at 420, past the service's 390. A closed model just plateaus at capacity and never shows the knee.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q3-how-do-you-find-a-services-capacity-l3-amazon)

??? question "What is chaos engineering, and how is it different from testing?"
    <span class="badge badge-l3">L3</span> Netflix · Atlassian

    Running experiments that inject real-world faults (latency, errors, outages) to check a hypothesis about steady-state behaviour, ideally in production, with a controlled blast radius. A test checks known behaviour; an experiment looks for unknown weaknesses.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q5-what-is-chaos-engineering-and-how-is-it-different-from-testing-l3-netflix-atlassian)

??? question "What makes a good steady-state hypothesis?"
    <span class="badge badge-l3">L3</span> Razorpay

    User-visible measures taken from the SLOs, errors and latency, per short window. Measured: with no timeout, every order succeeded while half took 2 s; an errors-only hypothesis would have passed.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q6-what-makes-a-good-steady-state-hypothesis-l3-razorpay)

??? question "How do you keep a chaos experiment from becoming an incident?"
    <span class="badge badge-l3">L3</span> PhonePe

    Abort conditions checked automatically (here: below 90% success in a window removes the fault), a small blast radius (staging, then a fraction of traffic), an owner watching, and a tested rollback. Measured: the abort ended bad experiments 7 s after they started.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q7-how-do-you-keep-a-chaos-experiment-from-becoming-an-incident-l3-phonepe)

??? question "A dependency is slow. Retry or fall back?"
    <span class="badge badge-l3">L3</span> Zomato

    Time out quickly, then fall back if the business allows. Measured: one retry after a 300 ms timeout got 81% of orders through a half-slow dependency and 16% through a hung one; the last known price kept 100% at a 302–307 ms p99.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q8-a-dependency-is-slow-retry-or-fall-back-l3-zomato)

??? question "What is a fitness function?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    An automated check that an architectural characteristic holds, run continuously: layer dependencies, no cycles, allowed libraries, naming, or measured qualities like latency. With ArchUnit they're JUnit tests over compiled classes.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q9-what-is-a-fitness-function-l3-thoughtworks)

??? question "What can't ArchUnit see?"
    <span class="badge badge-l3">L3</span> Red Hat

    Anything not in the code's bytecode: compile-time constants that javac inlined (measured: a domain-to-infrastructure constant passed every rule, while `jdeps` saw the constant-pool entry) and dependencies by reflection (nothing saw `Class.forName`). Ban reflection outside configuration with a rule.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q10-what-cant-archunit-see-l3-red-hat)

??? question "How do you introduce architecture rules into a legacy codebase?"
    <span class="badge badge-l3">L3</span> Infosys

    Freeze: record existing violations in a store committed with the code, and fail only on new ones (ArchUnit's `FreezingArchRule`; store creation must be allowed explicitly). Measured: of 8 violations, the 3 new ones were reported. Shrink the store as code is fixed.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q11-how-do-you-introduce-architecture-rules-into-a-legacy-codebase-l3-infosys)
