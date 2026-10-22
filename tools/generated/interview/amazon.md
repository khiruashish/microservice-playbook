### Architecture & Patterns


**[9. Hexagonal, Clean & Onion Architecture](../architecture-patterns/ch09-hexagonal-clean-architecture.md)**

??? question "How does this differ from controller–service–repository layering?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    In layering, the service depends on the persistence layer. In hexagonal, the core defines the repository interface and persistence depends on the core (dependency inversion). That lets the core run with a fake, keeps persistence types out of business rules, and supports several entry points on the same use cases.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q4-how-does-this-differ-from-controllerservicerepository-layering-l2l3-flipkart-amazon)

??? question "How do you test a hexagonal service?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Domain unit tests; use-case tests through driving ports with fake driven adapters (in-memory repository, recording gateway); adapter tests against real infrastructure (embedded DB or Testcontainers, `@WebMvcTest`, translator tests); a few assembled-service tests. Keep fakes honest with **contract tests** that both the fake and the real adapter must pass.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q6-how-do-you-test-a-hexagonal-service-l3-amazon-flipkart)

??? question "Isn't this over-engineering for microservices, which are small anyway?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    For thin CRUD services, yes. For services with real rules and several integrations (payments, pricing, dispatch), the core is where the value is, and keeping it independent pays back in testability and change safety. Decide per service based on rule complexity and number of integrations, not as a company-wide mandate.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q9-isnt-this-over-engineering-for-microservices-which-are-small-anyway-l3l4-flipkart-amazon)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "API gateway vs load balancer vs service mesh?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A load balancer distributes connections (L4/L7) across instances. An API gateway is an application-level edge for **north–south** traffic with API policies (auth, quotas, routing by path and version). A service mesh handles **east–west** traffic between services (mTLS, retries, telemetry) through sidecars or ambient proxies. They are complementary.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q2-api-gateway-vs-load-balancer-vs-service-mesh-l2l3-amazon-flipkart)

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

??? question "What happens when the gateway goes down?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Everything public goes down, which is why it must be stateless, horizontally scaled across zones, protected by disruption budgets, deployed with canaries, and kept simple (no business logic, few dependencies). Internal traffic must not route through it, so services keep working with each other during an edge incident.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q8-what-happens-when-the-gateway-goes-down-l3l4-amazon-flipkart)

??? question "Design the edge for a food delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds · Amazon

    CDN for static assets and cacheable menus; cloud load balancer with TLS; a stateless API gateway (token validation, header hygiene, request ids, per-user rate limits on order placement, size limits, retries only for reads, circuit breakers with fallbacks); BFFs for the customer app, partner portal and rider app; WebSocket or SSE endpoint for live tracking; services private. Mention capacity (gateway replicas per zone), observability per route, and that internal calls bypass the gateway.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q12-design-the-edge-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds-amazon)


**[11. Service Discovery Patterns](../architecture-patterns/ch11-service-discovery-patterns.md)**

??? question "Client-side vs server-side discovery?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Client-side: the caller queries the registry and chooses an instance itself (Eureka + Spring Cloud LoadBalancer, gRPC over a headless Service): rich policies, a library per language, client caches. Server-side: the caller uses a stable address and the platform or a proxy chooses (Kubernetes ClusterIP, cloud load balancers, meshes): language-neutral, simpler clients.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q2-client-side-vs-server-side-discovery-l2l3-amazon-flipkart)

??? question "How does service discovery work in Kubernetes?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · JP Morgan

    A Service selects pods by label; ready pods' IPs go into EndpointSlices; kube-proxy on each node routes connections to the Service's ClusterIP to a ready pod; CoreDNS resolves the Service name to the ClusterIP (or to all pod IPs for a headless Service). Readiness probes decide membership.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q4-how-does-service-discovery-work-in-kubernetes-l2l3-amazon-flipkart-jp-morgan)

??? question "An instance crashes. How long until callers stop sending it requests?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    It depends on every cache between the failure and the caller. Eureka defaults can take several minutes (lease, eviction, server cache, client fetch, load-balancer cache); Kubernetes removes a crashed pod in seconds but a hung one only after readiness probes fail (30 s with defaults). Mitigate with short timeouts and a retry on another instance for idempotent calls.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q6-an-instance-crashes-how-long-until-callers-stop-sending-it-requests-l3-amazon-goldman-sachs)

??? question "Why does gRPC load-balance badly behind a Kubernetes Service?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    kube-proxy balances connections, and gRPC multiplexes all requests over one long-lived HTTP/2 connection, so each client sticks to one pod. Use a headless Service with client-side round robin, a mesh that balances per request, or limit connection age.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q7-why-does-grpc-load-balance-badly-behind-a-kubernetes-service-l3-flipkart-amazon)

??? question "Liveness vs readiness probes?"
    <span class="badge badge-l2">L2</span> Cognizant · Infosys · Amazon

    Liveness answers "should this container be restarted?" and must only check the process itself. Readiness answers "should it receive traffic now?" and controls Service membership. Checking dependencies in liveness causes restart storms; checking every dependency in readiness removes whole services during partial outages.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q9-liveness-vs-readiness-probes-l2-cognizant-infosys-amazon)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

??? question "Why is a shared database an anti-pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    The schema becomes an unversioned contract for every service that touches it: renames break others, locks and heavy queries interfere, nobody owns the rules for the data, and services can't be deployed or scaled independently. It's the tightest coupling a system can have.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q2-why-is-a-shared-database-an-anti-pattern-l2l3-amazon-flipkart)

??? question "What replaces foreign keys between services?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Ids stored as values; validation through the owner's API when the reference is created; events to react when the referenced entity changes or is deleted; and reconciliation jobs for critical relationships. Often you also store a snapshot of the referenced data at the business moment.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q4-what-replaces-foreign-keys-between-services-l3-goldman-sachs-amazon)

??? question "How do you keep data consistent across services without distributed transactions?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    Sagas: a sequence of local transactions with compensating actions, driven by events or an orchestrator, with reliable publishing through an outbox. Accept eventual consistency and design the user experience around it (chapters 14 and 15).

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q5-how-do-you-keep-data-consistent-across-services-without-distributed-transactions-l3-jp-morgan-amazon)

??? question "What are the problems with API composition?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Latency and availability (every source is on the path), N+1 calls without batch endpoints, no efficient filtering or sorting across sources, and inconsistent snapshots between sources. Mitigate with batching, parallel calls, budgets, partial results and caching; switch to CQRS when queries cross owners.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q7-what-are-the-problems-with-api-composition-l3-amazon-flipkart)

??? question "How do you handle reporting across microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · Accenture · Amazon

    Not on operational databases. Stream changes through events or CDC into a warehouse or lakehouse, model the data there, and give analysts access only to that store.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q10-how-do-you-handle-reporting-across-microservices-l2l3-cognizant-accenture-amazon)

??? question "A product page needs data from six services. Design it."
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    A BFF composes the page: the catalog item as driver, then pricing, inventory, ratings and delivery promises in parallel with per-source budgets and fallbacks (hide ratings rather than fail the page), plus caching for slow-changing parts. Listings and search come from an index built from events. Name the latency budget and which parts are critical.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q11-a-product-page-needs-data-from-six-services-design-it-l4-flipkart-amazon)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "What is event sourcing?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Persisting every state change as an immutable event in an append-only store; current state is derived by replaying an aggregate's events (or a snapshot plus later events). It gives full history, auditability, temporal queries and the ability to build new read models from past data.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q2-what-is-event-sourcing-l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you handle concurrent commands on the same aggregate?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Optimistic concurrency: append with the expected stream version; if another writer appended first, the store rejects it. Reload, decide again (rules re-checked against the newer state), and retry a bounded number of times.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q4-how-do-you-handle-concurrent-commands-on-the-same-aggregate-l3-amazon-goldman-sachs)

??? question "What are snapshots and when do you need them?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    Cached aggregate state at a version, so loading replays only later events. Needed for long streams; they're an optimisation that can be deleted and rebuilt, and must be versioned with the code that reads them.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q6-what-are-snapshots-and-when-do-you-need-them-l3-amazon-sap)

??? question "When would you not use event sourcing?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    For CRUD-like data where nobody needs history, for teams without experience and without a clear benefit, and where simple audit tables suffice. It's a tool for ledgers, workflows and domains with disputes and many read views.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q9-when-would-you-not-use-event-sourcing-l3l4-flipkart-amazon)

??? question "Can Kafka be your event store?"
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    It's an excellent event log for integration and stream processing, but lacks per-aggregate optimistic concurrency, efficient per-stream reads and unlimited retention guarantees by default. Most systems use a proper event store (or a database table) for aggregates and Kafka for publishing integration events.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q10-can-kafka-be-your-event-store-l4-flipkart-amazon)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

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


**[15. Transactional Outbox, Inbox & CDC](../architecture-patterns/ch15-outbox-inbox-cdc.md)**

??? question "How does the transactional outbox work?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    The event is inserted into an outbox table in the same local transaction as the business change. A relay reads the outbox (by polling or from the transaction log) and publishes to the broker, retrying until acknowledged, then marks or deletes the row.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q2-how-does-the-transactional-outbox-work-l3-amazon-flipkart-goldman-sachs)

??? question "Does the outbox give exactly-once delivery?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    No. It gives at-least-once: the relay can crash after publishing and before marking, or lose the broker's ack, and republish. Exactly-once *processing* needs an idempotent consumer, usually an inbox table keyed by message id.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q3-does-the-outbox-give-exactly-once-delivery-l3-amazon-jp-morgan)

??? question "How do you preserve event order?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Per aggregate: key messages by aggregate id (same partition), publish outbox rows in position order, stop on failure rather than skipping, and run one active relay per service (or partition relays by aggregate). Global order is rarely needed and expensive.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q5-how-do-you-preserve-event-order-l4-amazon-goldman-sachs)

??? question "What is an inbox and why must it be in the same transaction?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A table of processed message ids in the consumer's database. Insert the id and apply the effect in one transaction: a duplicate fails the unique key and is skipped. If they were separate, a crash between them would lose the effect or apply it twice.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q7-what-is-an-inbox-and-why-must-it-be-in-the-same-transaction-l3-amazon-flipkart)

??? question "Why not just use Kafka transactions?"
    <span class="badge badge-l4">L4</span> JP Morgan · Amazon

    Kafka transactions make consume-process-produce atomic within Kafka. Your PostgreSQL commit isn't part of them, so database plus Kafka is still a dual write. Use the outbox for the database-to-Kafka step; Kafka transactions can help in pure stream processing.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q10-why-not-just-use-kafka-transactions-l4-jp-morgan-amazon)


**[16. Strangler Fig, ACL & Monolith Migration](../architecture-patterns/ch16-strangler-fig-migration.md)**

??? question "Branch by abstraction vs feature branches?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Branch by abstraction makes a large change on trunk: add an interface, move callers onto it, add the new implementation behind it, switch with a toggle, delete the old one. Every step is releasable. A long-lived feature branch defers integration and conflicts to the end, the same problem a big-bang rewrite has.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q3-branch-by-abstraction-vs-feature-branches-l3-amazon-flipkart)

??? question "Canary by request or by customer?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    By customer (or session, or tenant), using a stable hash, so a customer sees consistent behaviour and raising the percentage keeps existing cohorts in. Per-request splits are fine only for stateless, invisible changes.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q5-canary-by-request-or-by-customer-l3-flipkart-amazon)

??? question "What should trigger an automatic rollback?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Error rate, latency percentiles and at least one business metric (conversion, order value, payment success) on the new path compared with a threshold or the old path, over a window with a minimum sample size. A guard that can't get enough samples (tiny cohorts) can't decide.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q6-what-should-trigger-an-automatic-rollback-l4-amazon-flipkart)

??? question "How do you handle writes that can't be shadowed?"
    <span class="badge badge-l4">L4</span> JP Morgan · Amazon

    Compare decisions rather than effects (what the new service *would* write), run it against a copy of data, canary with very small cohorts and strong guards, or move writes last once reads have proven the model. Never execute both writes for real.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q10-how-do-you-handle-writes-that-cant-be-shadowed-l4-jp-morgan-amazon)


**[17. Sidecar, Ambassador & Adapter](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)**

??? question "What are native sidecar containers in Kubernetes?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Init containers with `restartPolicy: Always`. They start before app containers (startup probes can gate the app), run for the pod's life, are restarted if they exit, and stop after the app containers. They fix start-order races, lost last logs and Jobs that never complete.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q3-what-are-native-sidecar-containers-in-kubernetes-l3-amazon-flipkart)

??? question "What does an extra sidecar hop cost?"
    <span class="badge badge-l3">L3</span> Amazon

    Latency per call (fractions of a millisecond on loopback for a lightweight proxy; measure yours) and, more significantly, CPU and memory per pod, multiplied across the fleet, plus operational overhead (images, restarts, probes).

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q5-what-does-an-extra-sidecar-hop-cost-l3-amazon)

??? question "Why can a proxy retry a GET but not a POST?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    GETs are safe and idempotent; a POST may have taken effect even if the response was lost, so repeating it could duplicate the effect (a second charge). A POST can be retried only if the API guarantees idempotency, for example with an Idempotency-Key the server deduplicates on.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q6-why-can-a-proxy-retry-a-get-but-not-a-post-l2l3-flipkart-amazon)

??? question "How should timeouts be set between app and ambassador?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    The app's timeout must exceed the ambassador's total budget (per-try timeout × attempts + backoff), otherwise the app gives up while the ambassador is still retrying, wasting work and possibly causing duplicate effects.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q9-how-should-timeouts-be-set-between-app-and-ambassador-l3-jp-morgan-amazon)


**[18. Resilience Patterns](../architecture-patterns/ch18-resilience-patterns.md)**

??? question "What is a cascading failure and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Cognizant

    A failure (often slowness) in one dependency exhausting resources in its callers, which then fail their callers. Prevent it with timeouts on every call, bulkheads per dependency, bounded queues and load shedding, circuit breakers, and fallbacks.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q1-what-is-a-cascading-failure-and-how-do-you-prevent-it-l2l3-amazon-flipkart-cognizant)

??? question "When do retries make things worse?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    When the dependency is failing due to overload (retries add load), when several layers retry (multiplication), when retries aren't jittered (synchronized waves), and when operations aren't idempotent (duplicates). In the chapter's simulation, retries tripled load for no gain.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q3-when-do-retries-make-things-worse-l3-amazon-goldman-sachs)

??? question "How do you choose a timeout value?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    From data: above the dependency's healthy high percentile (p99.9), below what your caller will wait, and consistent with retries so attempts × timeout + backoff fits the caller's budget. Budgets should shrink as calls go deeper.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q5-how-do-you-choose-a-timeout-value-l3-amazon-goldman-sachs)

??? question "How do you know your resilience settings work?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Measure: dashboards of timeouts, retries, breaker states and fallbacks per dependency; load tests with injected latency; chaos experiments in staging and production (latency, not just errors); and simulations like this chapter's to reason about settings before an incident.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q10-how-do-you-know-your-resilience-settings-work-l3l4-amazon-flipkart)

??? question "What is load shedding?"
    <span class="badge badge-l3">L3</span> Amazon · Google-style rounds

    Rejecting work early (503, or dropping low-priority requests) when a service can't complete it in time, instead of queueing it. It keeps latency bounded for the work that is accepted and shortens recovery, because no backlog of abandoned requests is left.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q11-what-is-load-shedding-l3-amazon-google-style-rounds)


**[19. Event-Driven Architecture](../architecture-patterns/ch19-event-driven-architecture.md)**

??? question "Event vs command?"
    <span class="badge badge-l2">L2</span> Amazon · SAP

    An event is a past fact broadcast to anyone interested; the publisher doesn't expect anything. A command asks one receiver to do something, and it may refuse. Calling a command an event hides the coupling between sender and receiver.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q2-event-vs-command-l2-amazon-sap)

??? question "How do you handle duplicates and out-of-order events?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Key by entity so order holds per entity within a partition; include an owner-assigned version and apply newest-wins (idempotent and order-insensitive); for effects that aren't state updates, record processed event ids in an inbox.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q4-how-do-you-handle-duplicates-and-out-of-order-events-l3-amazon-jp-morgan)

??? question "What is log compaction?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A Kafka retention policy that keeps at least the most recent message for each key, discarding older ones, so the topic represents current state per key. A null-value message (tombstone) deletes a key.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q6-what-is-log-compaction-l3-amazon-flipkart)

??? question "What are the downsides of event-driven architecture?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Eventual consistency, harder debugging and tracing, hidden dependencies (who consumes what?), duplicate and ordering handling, schema governance, data copies (including personal data), and operational load (brokers, lag, dead letters).

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q8-what-are-the-downsides-of-event-driven-architecture-l3-goldman-sachs-amazon)


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "How would you detect a distributed monolith in an existing system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    From evidence: deploy history (co-deployment ratios), distributed traces (synchronous depth, cycles, fan-out), database audit logs (non-owner table access), dependency manifests (shared domain libraries, version skew), and change coupling in version control.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q2-how-would-you-detect-a-distributed-monolith-in-an-existing-system-l3l4-amazon-goldman-sachs)

??? question "What is the N+1 problem between services and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Calling another service once per item in a loop. Latency and failure probability grow with N. Parallelising helps latency but not failure rate; a batch endpoint fixes both; a local replica of the data removes the calls.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q3-what-is-the-n1-problem-between-services-and-how-do-you-fix-it-l2l3-flipkart-amazon)

??? question "Why are cyclic dependencies between services a problem?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Neither service can be deployed, tested, scaled or failed independently; a slowdown in either propagates around the loop; startup ordering becomes fragile. Break the cycle by moving the shared responsibility into one service or by turning one direction into events or replicated data.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q6-why-are-cyclic-dependencies-between-services-a-problem-l3-amazon-flipkart)

??? question "Your team wants to split a service 'for performance'. What do you ask?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Which part has a different load or latency profile, with data? How many calls per request will cross the new boundary? Will the parts change and release independently? Who owns each? If the answers are "same profile, many calls, always together, same team", the split will make performance worse.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q11-your-team-wants-to-split-a-service-for-performance-what-do-you-ask-l3l4-amazon-goldman-sachs)


### Communication


**[21. Synchronous REST](../communication/ch21-rest-api-design.md)**

??? question "Which methods are safe and which are idempotent, and why does it matter?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart

    GET and HEAD are safe and idempotent; PUT and DELETE are idempotent; POST and PATCH are neither by default. Clients, gateways and proxies decide what they may retry or cache from these properties.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q3-which-methods-are-safe-and-which-are-idempotent-and-why-does-it-matter-l2-amazon-flipkart)

??? question "Offset vs cursor pagination?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    Offset skips N rows each time, so cost grows with depth and live inserts/deletes cause duplicates and skips. Keyset/cursor pagination seeks to the last seen sort key using an index: constant cost and stable under changes, but no random page jumps. Use a unique sort key (add the id as tie-breaker) and opaque cursors.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q4-offset-vs-cursor-pagination-l2l3-amazon-flipkart-goldman-sachs)

??? question "How do ETags help performance?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Clients and caches revalidate with If-None-Match; an unchanged resource returns 304 with no body, saving bandwidth and, if the ETag comes from a version column checked cheaply, server work too.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q8-how-do-etags-help-performance-l2l3-amazon-flipkart)


**[22. HTTP Clients](../communication/ch22-http-clients.md)**

??? question "Why not create a client per request?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Amazon · Cognizant

    You lose connection reuse: every call pays TCP and TLS handshakes, and you churn threads, sockets and memory. Measured here: 15.8 ms vs 2.1 ms at p50 on loopback over TLS. Build clients once.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q3-why-not-create-a-client-per-request-l1l2-amazon-cognizant)

??? question "Connect timeout vs read timeout?"
    <span class="badge badge-l2">L2</span> Amazon · JP Morgan

    Connect timeout bounds establishing the connection (dead host, full backlog); read timeout bounds waiting for response data. Both must be set; defaults are often infinite or very long.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q4-connect-timeout-vs-read-timeout-l2-amazon-jp-morgan)

??? question "How do you handle error responses from another service?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    Map them in one place (a default status handler): RFC 9457 problems into typed exceptions carrying `type` and details, others into exceptions with status and a truncated body; decide per type whether to retry, fix input, or fail.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q7-how-do-you-handle-error-responses-from-another-service-l2-sap-amazon)

??? question "Sync or async API with virtual threads?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Prefer synchronous calls on virtual threads for readability and debuggability; use structured concurrency or parallel calls for fan-out. Async/reactive APIs remain useful for streaming and backpressure, not for basic concurrency.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q10-sync-or-async-api-with-virtual-threads-l3-goldman-sachs-amazon)


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "How does Protocol Buffers encode data?"
    <span class="badge badge-l2">L2</span> Amazon · Goldman Sachs

    Each field is a tag, `(field_number << 3) | wire_type`, followed by the value: varints for integers and enums, fixed 4 or 8 bytes for floats and doubles, length-prefixed bytes for strings and nested messages. Names aren't sent; in proto3 default values aren't sent either.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q2-how-does-protocol-buffers-encode-data-l2-amazon-goldman-sachs)

??? question "Is gRPC always faster or smaller than JSON?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Smaller per message (2.5× in this chapter) and cheaper to parse, but not always smaller once compressed: a gzipped JSON stream was smaller than gzipped protobuf here, because gzip removes repeated field names. Choose gRPC for contracts, streaming and deadlines, and measure if size is the argument.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q3-is-grpc-always-faster-or-smaller-than-json-l2l3-flipkart-amazon)

??? question "How do deadlines work, and why are they better than timeouts?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    The client sets an absolute deadline, sent as `grpc-timeout`; servers that make further calls inside the request pass the remaining time on, and cancellation propagates when it expires. Fixed per-hop timeouts know nothing about the caller's budget, so work continues after the caller has given up: 639 ms of wasted work per request vs 2 ms in this chapter's simulation.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q5-how-do-deadlines-work-and-why-are-they-better-than-timeouts-l2l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you handle errors in gRPC?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    Return a specific status code with a description (`NOT_FOUND`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`, `UNAVAILABLE`…) and, where useful, rich details (`google.rpc.Status` with `ErrorInfo` or `BadRequest`). Map exceptions centrally (Spring's `GrpcExceptionHandler`); clients decide on retry or fallback by code.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q6-how-do-you-handle-errors-in-grpc-l2-sap-amazon)

??? question "Why does gRPC load balancing behave oddly on Kubernetes?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    gRPC multiplexes calls over a long-lived HTTP/2 connection, and a ClusterIP Service balances connections, not requests, so one client sticks to one pod. Use client-side balancing (headless Service, `round_robin`), an L7 proxy or service mesh, and max connection age on servers.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q8-why-does-grpc-load-balancing-behave-oddly-on-kubernetes-l3-amazon-flipkart)

??? question "How does streaming handle a slow client?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    HTTP/2 flow control limits how much a sender can have in flight per stream. In grpc-java, a server checks `isReady()` and uses `setOnReadyHandler` to send only when the client can take more, and coalesces or drops intermediate updates (like old rider positions) rather than buffering without bound.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q10-how-does-streaming-handle-a-slow-client-l3-goldman-sachs-amazon)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "How is GraphQL different from REST?"
    <span class="badge badge-l2">L2</span> Amazon · Cognizant

    REST exposes resources at URLs with HTTP semantics (methods, status codes, caching); the server decides each response's shape. GraphQL exposes one endpoint and a schema; the client decides the shape. GraphQL gains flexibility and fewer round trips; REST keeps HTTP caching, simple per-endpoint cost and familiar tooling.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q2-how-is-graphql-different-from-rest-l2-amazon-cognizant)

??? question "What is the N+1 problem in GraphQL and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Resolvers run per parent object, so a list of N items whose field calls a service makes 1 + N calls. Fix it with batch loading: DataLoader collects keys at one execution level and loads them in one call (Spring's `@BatchMapping` or `BatchLoaderRegistry`). In this chapter a screen went from 19 backend calls to 3, with identical data.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q3-what-is-the-n1-problem-in-graphql-and-how-do-you-fix-it-l2l3-flipkart-amazon-goldman-sachs)

??? question "How do you protect a GraphQL API from expensive queries?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Limit depth and computed complexity before execution (graphql-java instrumentations, with a cost function that multiplies by page sizes), cap page sizes, time out execution, and in production accept only persisted queries from your own apps. Batching alone doesn't help: it reduces calls, not the amount of work.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q5-how-do-you-protect-a-graphql-api-from-expensive-queries-l3-goldman-sachs-jp-morgan-amazon)

??? question "How are errors reported in GraphQL?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    In an `errors` array alongside `data`, each with a message, a `path` to the failed field and `extensions` such as a classification. Other fields still return, so responses can be partially successful, usually with HTTP 200. Map exceptions to error types centrally and monitor errors in responses, not just HTTP status.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q6-how-are-errors-reported-in-graphql-l2-sap-amazon)

??? question "How do you cache GraphQL responses?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    HTTP caching is hard because queries are POSTs to one URL. Options: normalised client caches (Apollo, Relay) keyed by type and ID, persisted queries sent as GET so CDNs can cache them, caching inside the services, and per-request DataLoader caching for repeated keys.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q7-how-do-you-cache-graphql-responses-l3-flipkart-amazon)

??? question "What is GraphQL federation and when is it worth it?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    A supergraph composed from subgraphs owned by different services, with a router planning each query across them; entities like `Restaurant` can be extended by several subgraphs. It is worth it when many teams contribute to one client-facing graph; otherwise a single BFF graph is simpler to run and debug.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q9-what-is-graphql-federation-and-when-is-it-worth-it-l3l4-amazon-sap)


**[25. Asynchronous Messaging](../communication/ch25-asynchronous-messaging.md)**

??? question "How is Kafka different from RabbitMQ?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart · SAP

    Kafka is a partitioned, replicated log: messages are retained and replayable, consumers track offsets, ordering is per partition, and parallelism is bounded by partitions. RabbitMQ is a queue broker: it routes messages through exchanges to queues, tracks per-message acknowledgement, and deletes acknowledged messages; it offers priorities, TTLs and flexible routing, but no replay.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q2-how-is-kafka-different-from-rabbitmq-l2-amazon-flipkart-sap)

??? question "Explain at-most-once, at-least-once and exactly-once."
    <span class="badge badge-l2">L2</span> Goldman Sachs · JP Morgan · Amazon

    Acknowledge before processing: at-most-once, messages can be lost. Acknowledge after: at-least-once, messages can be duplicated. Exactly-once effects come from at-least-once delivery plus an idempotent consumer (or a transaction covering both the effect and the offset, as Kafka transactions do within Kafka). In this chapter's simulation, idempotency turned 9 duplicates into 0.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q3-explain-at-most-once-at-least-once-and-exactly-once-l2-goldman-sachs-jp-morgan-amazon)

??? question "How does Kafka guarantee ordering, and how can you lose it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Within a partition, records are stored and delivered in order; a key sends all records of an entity to one partition. You lose it by processing a partition's records in parallel, by non-blocking retries, by changing the partition count (keys move), or with producer retries without idempotence. Use enough partitions and key-ordered concurrency.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q4-how-does-kafka-guarantee-ordering-and-how-can-you-lose-it-l2l3-flipkart-amazon)

??? question "Blocking or non-blocking retries in Spring Kafka?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Blocking (`DefaultErrorHandler` with a BackOff) keeps per-partition order but stalls the partition while retrying. Non-blocking (`@RetryableTopic`) moves failed records to delayed retry topics so the main topic keeps flowing, at the cost of ordering. Use blocking for sequential state, non-blocking for independent side effects. Measured: with one partner down, retry topics cut p99 from 30.9 s to 8.2 s.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q6-blocking-or-non-blocking-retries-in-spring-kafka-l3-amazon-goldman-sachs)

??? question "How do you scale a Kafka consumer?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Add consumers to the group up to the number of partitions; beyond that, add partitions (planning for key redistribution) or use key-ordered parallel processing within a partition. Watch consumer lag, and keep processing per poll within `max.poll.interval.ms`.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q8-how-do-you-scale-a-kafka-consumer-l2-flipkart-amazon)

??? question "What goes wrong if a consumer marks a message as processed before applying it?"
    <span class="badge badge-l3">L3</span> Amazon · any

    If the effect fails, the retry is treated as a duplicate and skipped: the message is silently lost despite at-least-once delivery. The processed marker and the effect must commit together (one database transaction), or the marker must be removed when the effect fails.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q12-what-goes-wrong-if-a-consumer-marks-a-message-as-processed-before-applying-it-l3-amazon-any)


**[26. WebSockets & Server-Sent Events](../communication/ch26-websockets-sse.md)**

??? question "SSE or WebSocket: how do you choose?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart

    SSE when data flows server → client: it's plain HTTP, has automatic reconnection and resume (`Last-Event-ID`), and works through proxies. WebSocket when the client also streams (chat, GPS, collaborative editing, games) or you need binary frames. Measured here, both delivered every update for ~100–120 bytes each.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q2-sse-or-websocket-how-do-you-choose-l2-amazon-flipkart)

??? question "Why is polling a problem at scale?"
    <span class="badge badge-l2">L2</span> Amazon · Cognizant

    Each poll is a full request (headers, auth, TLS, routing), most answer "nothing new", data is stale by up to the interval, and intermediate states are missed. Here 1 s polling saw 10 of 40 positions at ~1,190 bytes each, 564 ms stale on average.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q3-why-is-polling-a-problem-at-scale-l2-amazon-cognizant)

??? question "How do you scale WebSocket or SSE connections across many servers?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    Keep connections in a dedicated, horizontally scaled tier; get events to the right instance by broadcast (simple, N× work), routing via a registry, per-key pub/sub channels, or partitioned routing at the load balancer; use a shared broker (Kafka, Redis, or a STOMP broker relay). Size instances by connections and memory, not requests per second.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q5-how-do-you-scale-websocket-or-sse-connections-across-many-servers-l3-amazon-flipkart-goldman-sachs)

??? question "What is a reconnect storm and how do you prevent it?"
    <span class="badge badge-l3">L3</span> Amazon · any

    Many clients losing connections at once and reconnecting in step, overloading handshakes; timed-out attempts waste capacity and trigger retries. Prevent it with jittered reconnect delays (randomised SSE `retry:`, full-jitter backoff), gradual draining on deploys, and servers that drop work for clients that have already given up. Measured: 20.4 s and 209,000 wasted handshakes without jitter, 9.9 s and none with.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q7-what-is-a-reconnect-storm-and-how-do-you-prevent-it-l3-amazon-any)

??? question "What happens with a slow consumer, and how do you handle it?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Its output buffers grow or the sending thread blocks, which can delay other clients and exhaust memory. Bound per-connection buffers and send time, conflate (keep only the latest value per key), send asynchronously per connection, and close connections that can't keep up.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q11-what-happens-with-a-slow-consumer-and-how-do-you-handle-it-l3-goldman-sachs-amazon)


**[27. API Contracts & Schema Evolution](../communication/ch27-api-contracts.md)**

??? question "What's the difference between backward and forward compatibility?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Backward compatible: existing consumers keep working with the new version (new provider, old consumer). Forward compatible: consumers built for the new version work with the old provider or old data. Schema registries define them from the reader's side; with `BACKWARD` you upgrade consumers first, with `FORWARD` producers first, with `FULL` either.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q2-whats-the-difference-between-backward-and-forward-compatibility-l2-flipkart-amazon)

??? question "Is adding a field a breaking change?"
    <span class="badge badge-l2">L2</span> Amazon · SAP

    In a response, not by the rules, but it breaks strict readers (Kotlin serialization without `ignoreUnknownKeys`, Jackson 2 outside Spring Boot). In a request, adding a required field breaks old clients. In this chapter's history, three "safe" additions broke strict consumers.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q3-is-adding-a-field-a-breaking-change-l2-amazon-sap)

??? question "How do you version a REST API?"
    <span class="badge badge-l2">L2</span> Cognizant · Amazon

    Avoid versions for additive changes; for real breaks, a new major version via URL (`/v2`), media type or header, run in parallel with the old one, with deprecation and `Sunset` headers, usage tracking and a retirement date (chapter 21 covers the mechanics).

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q7-how-do-you-version-a-rest-api-l2-cognizant-amazon)

??? question "Which schema registry compatibility mode would you choose for domain events, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    `FULL_TRANSITIVE`: any consumer version can read any event ever written, which matters for replays, new consumers reading history, and deploys in any order. The cost is that only optional fields (with defaults) can be added or removed; anything else is a new event type or topic.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q10-which-schema-registry-compatibility-mode-would-you-choose-for-domain-events-and-why-l3l4-amazon-jp-morgan)

??? question "How do you govern APIs across a hundred teams?"
    <span class="badge badge-l4">L4</span> Amazon · SAP

    Contracts in repos (spec-first), linting with shared rules, automated breaking-change checks, consumer contracts for critical consumers, a catalogue of APIs and their consumers, tolerant-reader defaults in the platform's client libraries, and a lightweight review only for breaking or semantic changes; teams evolve additively without asking permission.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q12-how-do-you-govern-apis-across-a-hundred-teams-l4-amazon-sap)


**[28. Idempotency, Deduplication & Correlation](../communication/ch28-idempotency-correlation.md)**

??? question "How do you implement idempotency keys?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    The client sends a unique key per action. The server atomically claims it (insert with a unique constraint) before any side effect, stores a request fingerprint, performs the operation, and stores the response with the business write. Duplicates get the stored response, 409 if still in progress, 422 if the fingerprint differs. Add a lease for crashes and pass the key downstream. Measured: exactly one charge per key under 4× concurrent duplicates.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q2-how-do-you-implement-idempotency-keys-l2l3-amazon-flipkart-goldman-sachs)

??? question "Why isn't a unique constraint on the payments table enough?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    It deduplicates rows, not side effects. If the external call happens before the insert, every duplicate still calls the provider; the constraint just rejects the extra rows. Measured: 200 rows, 800 charges.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q3-why-isnt-a-unique-constraint-on-the-payments-table-enough-l2l3-jp-morgan-amazon)

??? question "What goes wrong with in-memory deduplication in consumers?"
    <span class="badge badge-l2">L2</span> Amazon · any

    It's per instance and lost on restart. After rebalances and deploys, duplicates land on instances that never saw the originals (measured: 2,513 of 3,000 slipped through). Use a shared store or the inbox pattern, or make processing naturally idempotent.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q7-what-goes-wrong-with-in-memory-deduplication-in-consumers-l2-amazon-any)

??? question "Why not order events by timestamp?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Clocks on different machines differ by milliseconds even with NTP, and occasionally by much more; causally related events often happen milliseconds apart. Measured: 8% of orders misordered with good clocks, 39% with one drifting instance; causation ids had no errors. Use causation ids or per-entity sequence numbers.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q9-why-not-order-events-by-timestamp-l2l3-amazon-jp-morgan)

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

??? question "What's wrong with a projector that remembers the last outbox id it processed?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Amazon

    Sequence values are assigned at insert but become visible at commit, in a different order. A transaction that took id 1,000 can commit after the projector has read 1,001, and is never read. Measured: 29–48 of 5,000 items left wrong. Claim rows with `FOR UPDATE SKIP LOCKED` and delete them when applied, or follow the log position (logical replication, Debezium).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q6-whats-wrong-with-a-projector-that-remembers-the-last-outbox-id-it-processed-l3l4-goldman-sachs-amazon)

??? question "When do you need Elasticsearch instead of database full-text search?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · SAP

    When search is a product feature: relevance tuning, synonyms, facets and aggregations, many languages, typo-tolerant typeahead in milliseconds over millions of documents. Measured: PostgreSQL full-text + trigram fallback found 29 of 29 test queries at a 14 ms median over 192,000 items, but typos cost ~50 ms at the median. Then it's a derived store, fed from the source of truth.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q8-when-do-you-need-elasticsearch-instead-of-database-full-text-search-l2l3-amazon-sap)

??? question "Your team wants MongoDB, Elasticsearch, Redis and a time-series DB for one new service. How do you respond?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Ask for the access patterns with numbers and measure them on the supported stores first. Then add only stores that close a measured gap, each as a derived store or ephemeral state fed from one source of truth, with an owner and the operating cost accepted. At FoodFleet that turned four proposed stores into PostgreSQL plus Redis.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q12-your-team-wants-mongodb-elasticsearch-redis-and-a-time-series-db-for-one-new-service-how-do-you-respond-l4-amazon-goldman-sachs)


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "Why is 2PC avoided between microservices?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    It holds locks across network round trips (measured: 2.8× as long as a saga step, half the throughput on hot rows), it couples availability (no orders while payments was down), it blocks if the coordinator fails (one in-doubt transaction cut a hot path to 2–6 orders/s), and it needs XA-capable resources, which brokers, HTTP APIs and external providers aren't. It also couples teams to a shared transaction manager.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q2-why-is-2pc-avoided-between-microservices-l3-amazon-flipkart-goldman-sachs)

??? question "Does three-phase commit solve blocking?"
    <span class="badge badge-l4">L4</span> Amazon · any

    Only if message delays are bounded and failures detectable, which networks don't guarantee; under partitions it can produce inconsistency. Practical systems instead replicate the coordinator and participants with consensus (Paxos/Raft), as Spanner does, so a single machine's failure doesn't block.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q5-does-three-phase-commit-solve-blocking-l4-amazon-any)

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


**[31. Data Consistency](../data/ch31-data-consistency.md)**

??? question "What is read-your-writes and how do you provide it with read replicas?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    After a client writes, its reads reflect that write. Return a token with the write (in PostgreSQL, the WAL position after commit); route the client's reads to a replica whose replay position is at least the token, or wait briefly, or use the primary. Measured: 0 stale reads with 2.9% of reads on the primary, versus 15% stale from a lagging replica.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q2-what-is-read-your-writes-and-how-do-you-provide-it-with-read-replicas-l3-amazon-flipkart)

??? question "What are monotonic reads?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    A client never sees older data after it has seen newer. Load-balancing reads across replicas with different lag breaks it (measured: 19% of polls went backwards). Fix with sticky routing or a token of the newest position seen.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q4-what-are-monotonic-reads-l3-amazon-sap)

??? question "What is a lost update and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Two read-modify-write cycles interleave and one overwrites the other. Prevent with an atomic update (`SET x = x + 1`), a conditional update, `SELECT ... FOR UPDATE`, optimistic versions with retry, or REPEATABLE READ/SERIALIZABLE with retry. Measured: read-modify-write lost 15.5% of points; the atomic update lost none and was the fastest.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q5-what-is-a-lost-update-and-how-do-you-prevent-it-l2l3-goldman-sachs-jp-morgan-amazon)

??? question "Last-writer-wins: when is it acceptable?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · any

    For single values where losing a concurrent write is acceptable (a display name, a note), with timestamps from a hybrid logical clock so causally later writes win. Not for collections or counters: measured, LWW on whole carts lost 562–578 added items across 2,000 carts.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q8-last-writer-wins-when-is-it-acceptable-l3l4-amazon-any)

??? question "What is a CRDT? Give an example."
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    A data type whose replicas converge without coordination because merge is commutative, associative and idempotent. An OR-Set tags each add uniquely and removes only observed tags, so a concurrent add survives; a PN-counter keeps per-replica increments and decrements. Measured: no lost or resurrected items across 2,000 two-region carts.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q9-what-is-a-crdt-give-an-example-l4-amazon-goldman-sachs)

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

??? question "When is write-behind appropriate?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    For high-rate data that may be approximate: counters, impressions, last-seen times. It cuts database writes to batches (measured: ~7,000 statements/s to 1) but loses unflushed data if the cache loses it (measured: 9,991–10,651 views on a Redis restart). Flushes must be idempotent (atomic claim, batch ids). Never for money.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q7-when-is-write-behind-appropriate-l3-amazon-flipkart)

??? question "What happens to your service when Redis is down?"
    <span class="badge badge-l3">L3</span> Amazon · any

    It should fall back to the source of truth, slower but correct, with Redis timeouts shorter than the database's latency so a sick cache doesn't make things worse. Capacity planning must assume the database can take the cache's load for a while, or the cache becomes a hidden single point of failure.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q9-what-happens-to-your-service-when-redis-is-down-l3-amazon-any)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

??? question "Your migration only adds a nullable column, yet checkout froze. Why?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    The lock queue. The `ALTER TABLE` waited for a long query holding a lock on the table, and every later query waited behind the `ALTER TABLE`. Measured: 10 s of blocked writes behind a 12 s report. Use `lock_timeout` and retry, and keep long queries off the primary.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q3-your-migration-only-adds-a-nullable-column-yet-checkout-froze-why-l3-amazon-goldman-sachs)

??? question "Which schema changes are safe on a large, busy PostgreSQL table?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Catalogue-only ones: nullable columns, constant defaults (PostgreSQL 11+), `NOT VALID` constraints, dropping NOT NULL. Not: volatile defaults, most type changes (rewrites), plain index builds, validated constraints in one step. Use `CONCURRENTLY` and `NOT VALID` + `VALIDATE`.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q4-which-schema-changes-are-safe-on-a-large-busy-postgresql-table-l3-amazon-jp-morgan)

??? question "How do you rename a column with zero downtime?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Expand/contract: add the new column; deploy code that writes both and reads the old; backfill; deploy code that reads the new; deploy code that writes only the new; drop the old in a later release. Measured: in-place renames failed thousands of statements during a rolling deploy; expand/contract none.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q6-how-do-you-rename-a-column-with-zero-downtime-l3-flipkart-amazon)

??? question "How do you backfill a column on a billion-row table?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    In keyset batches by primary key, each its own short transaction, throttled, resumable, bounded by the max id at start, watching replica lag and autovacuum. Measured on 2 M rows: one `UPDATE` made writes wait up to 5.5–6.8 s; batches of 1,000 kept them at or under 60 ms.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q7-how-do-you-backfill-a-column-on-a-billion-row-table-l3-amazon-jp-morgan)

??? question "How do you choose a shard key?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    High cardinality, even distribution, and present in most queries. Measured: by city, one shard held 6× the average and half the shards held nothing; by customer with the shard embedded in order ids, 70% of queries hit one shard and the largest shard was 1.19× the average.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q9-how-do-you-choose-a-shard-key-l4-amazon-flipkart)

??? question "Why not hash(key) mod N?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Changing N remaps almost every key. Measured: 80% of orders moved going from 16 to 20 shards, against 19.5% with 4,096 fixed slots and 18.6% with a consistent-hash ring. Slots also give a small, explicit map and a unit of migration.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q10-why-not-hashkey-mod-n-l3l4-amazon-sap)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "Outbox events or CDC for feeding analytics?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Outbox events for data other teams depend on: deliberate, in the domain's language, versioned. CDC when you need everything without changing the source, or for internal pipelines, accepting that it exposes table structure. Many platforms use both into the same Kafka backbone.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q3-outbox-events-or-cdc-for-feeding-analytics-l3-amazon-goldman-sachs)

??? question "How do you keep aggregates correct when events are duplicated or out of order?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Keep the latest state per entity, replaced only by a higher version, and change aggregates by the delta between old and new state in the same transaction. Measured: counting events +7.1%, counting with dedup +1.9% (corrections), state with versions exact.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q5-how-do-you-keep-aggregates-correct-when-events-are-duplicated-or-out-of-order-l3-amazon-flipkart)

??? question "How do you know a report is up to date?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    You don't, unless it tells you. Track the event time of the newest change applied, show it as "as of", compare it with a freshness objective, alert when exceeded. Measured: with the CDC consumer stopped, the copy fell 60 s behind in 60 s and nothing in the data showed it.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q8-how-do-you-know-a-report-is-up-to-date-l3-jp-morgan-amazon)

??? question "Data warehouse, data lake, lakehouse: what's the difference?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    A warehouse stores modelled, cleaned data in a managed engine with schema on write. A lake stores raw and refined files cheaply in open formats, schema on read. A lakehouse adds table formats (Iceberg, Delta, Hudi) to the lake for transactions, schema evolution and time travel, so warehouse-style queries run on lake storage.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q9-data-warehouse-data-lake-lakehouse-whats-the-difference-l3-amazon-sap)


### Deployment & Platform


**[53. Containerization](../deployment/ch53-containerization.md)**

??? question "How does the JVM size its heap in a container?"
    <span class="badge badge-l2">L2</span> Amazon · Walmart

    From the cgroup memory limit: 25% by default (`MaxRAMPercentage`), 50% when memory is small. Measured: 247 MiB in a 1 GB container and 121 MiB in 256 MB. Set `MaxRAMPercentage=75` to use the memory you pay for, and leave the rest for metaspace, threads, buffers and code cache.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q5-how-does-the-jvm-size-its-heap-in-a-container-l2-amazon-walmart)


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

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

??? question "A method call becomes a remote call during extraction. What changes in your code?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Everything about failure. You need timeouts, a policy for "couldn't ask" versus "the answer was no", retries only for idempotent operations, circuit breakers, no database transaction held across the call, versioned contracts, and tracing.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q8-a-method-call-becomes-a-remote-call-during-extraction-what-changes-in-your-code-l3-amazon-flipkart)

??? question "Prime Video moved a system back to a monolith. Did microservices fail?"
    <span class="badge badge-l4">L4</span> Amazon · any product company

    No. One workload (audio and video quality monitoring) was built as a fine-grained distributed pipeline whose data passing between components dominated cost. Consolidating it into a single process removed that overhead. The lesson is to pick granularity by workload characteristics, not that one style always wins.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q11-prime-video-moved-a-system-back-to-a-monolith-did-microservices-fail-l4-amazon-any-product-company)

??? question "How does Conway's law influence this decision?"
    <span class="badge badge-l4">L4</span> SAP · Amazon

    Systems mirror the communication structure of the organisations that build them. Service boundaries that don't match team boundaries create constant cross-team coordination. Design team and service boundaries together (the "inverse Conway manoeuvre"), and don't create more services than teams can own.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q12-how-does-conways-law-influence-this-decision-l4-sap-amazon)

??? question "How would you justify the cost of microservices to a CFO?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Amazon · SAP

    In business outcomes, not technology. Show the current constraint (lead time, release contention, outage blast radius, scaling cost of one hot feature), the expected improvement in DORA metrics or infrastructure cost, the one-off and recurring costs (platform, people, runtime), and staged checkpoints where you stop if the numbers don't materialise.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q13-how-would-you-justify-the-cost-of-microservices-to-a-cfo-l4-goldman-sachs-amazon-sap)


**[2. Characteristics & Principles](../foundations/ch02-characteristics-principles.md)**

??? question "How do you evolve an API without breaking consumers?"
    <span class="badge badge-l2">L2</span> Amazon · SAP · Flipkart

    Prefer additive changes, make consumers tolerant readers, version only for unavoidable breaking changes, announce deprecation (Deprecation and Sunset headers), measure per-version traffic, and protect it all with contract tests.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q3-how-do-you-evolve-an-api-without-breaking-consumers-l2-amazon-sap-flipkart)

??? question "What does 'design for failure' mean in code?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Timeouts on every remote call. Retries only for idempotent operations and transient errors, with backoff and jitter. Bounded concurrency. Circuit breakers. A defined degradation mode per dependency. And tests for each failure path.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q5-what-does-design-for-failure-mean-in-code-l2l3-amazon-goldman-sachs)

??? question "Your service calls three downstream services. How do you set timeouts?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Start from the caller's latency SLO and allocate a budget per hop, using each dependency's measured p99 plus headroom. Make sure retries fit inside the budget. Propagate a deadline so downstream services stop work the caller has abandoned.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q6-your-service-calls-three-downstream-services-how-do-you-set-timeouts-l3-flipkart-amazon)

??? question "Is code duplication across services acceptable?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · SAP

    Often yes. DRY across service boundaries creates coupling through shared libraries. Share technical concerns (logging, tracing setup), never domain models. Duplicate small DTOs and own them.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q8-is-code-duplication-across-services-acceptable-l2l3-amazon-sap)

??? question "How do you enforce principles across 50 teams without an architecture review board?"
    <span class="badge badge-l4">L4</span> SAP · Amazon · Flipkart

    Paved road plus fitness functions. Service templates with secure, observable defaults. Shared architecture-rule tests (ArchUnit, Spring Modulith) run in every pipeline. API linting. Scorecards in the developer portal. Exceptions through lightweight ADRs.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q9-how-do-you-enforce-principles-across-50-teams-without-an-architecture-review-board-l4-sap-amazon-flipkart)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Name the fallacies of distributed computing. Which hurt microservices most?"
    <span class="badge badge-l2">L2</span> Cognizant · TCS · Amazon

    The network is reliable; latency is zero; bandwidth is infinite; the network is secure; topology doesn't change; there is one administrator; transport cost is zero; the network is homogeneous. The first two hurt most: unreliability causes lost or duplicated work, and latency kills chatty designs.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q1-name-the-fallacies-of-distributed-computing-which-hurt-microservices-most-l2-cognizant-tcs-amazon)

??? question "Explain the CAP theorem precisely."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    During a network partition, a replicated system must choose between linearizable consistency and availability. Partition tolerance isn't optional, and the choice only applies while partitioned.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q2-explain-the-cap-theorem-precisely-l2l3-amazon-flipkart-goldman-sachs)

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

??? question "How do you choose between CP and AP for a FoodFleet feature?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    By the cost of each kind of error. A stale menu price is cheap and recoverable, so choose AP with a freshness SLO. Double-redeeming a coupon or double-paying a restaurant is expensive, so choose CP with conditional writes. Write it in the service charter, and test the partition behaviour.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q13-how-do-you-choose-between-cp-and-ap-for-a-foodfleet-feature-l4-amazon-flipkart)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "Why store configuration in the environment rather than in the jar?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Amazon · Flipkart

    So one build can run in every environment. If config is baked in, each environment needs its own build, and production runs an artifact that was never tested.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q2-why-store-configuration-in-the-environment-rather-than-in-the-jar-l1l2-amazon-flipkart)

??? question "Explain graceful shutdown on Kubernetes."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Kubernetes removes the pod from endpoints and sends SIGTERM at about the same time. The app stops accepting new connections, finishes in-flight work within a timeout, releases resources and exits. Add a short `preStop` sleep because endpoint removal is asynchronous, and set `terminationGracePeriodSeconds` higher than the app's shutdown timeout.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q5-explain-graceful-shutdown-on-kubernetes-l2l3-amazon-jp-morgan)

??? question "How do you make a Spring Boot service start fast?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Measure first. Then use a layered image, and on Java 25 an AOT cache trained at build time (JEP 483 and 514). Consider CRaC for checkpoint/restore, or GraalVM native images when startup and memory dominate and their build constraints are acceptable. Avoid heavy work in the startup path, and lazily initialise optional components.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q8-how-do-you-make-a-spring-boot-service-start-fast-l3l4-amazon-sap)

??? question "Which factors would you relax for a stateful system like Kafka or PostgreSQL on Kubernetes?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    The factors are about *applications*. Stateful backing services are run with operators and StatefulSets, stable identities, persistent volumes and careful disruption budgets. The application side treats them as attached resources and stays twelve-factor.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q11-which-factors-would-you-relax-for-a-stateful-system-like-kafka-or-postgresql-on-kubernetes-l4-amazon-flipkart)


**[5. Domain-Driven Design](../foundations/ch05-domain-driven-design.md)**

??? question "What is an aggregate and how do you choose its boundary?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    A cluster of objects treated as one consistency unit, accessed through its root. The boundary comes from **invariants that must hold at every commit**. Keep aggregates small, reference others by id, and use eventual consistency between them.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q3-what-is-an-aggregate-and-how-do-you-choose-its-boundary-l3-amazon-flipkart-goldman-sachs)

??? question "Why only one aggregate per transaction?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    It keeps locks small and transactions short, avoids contention, and keeps each aggregate independently storable and scalable, which is a prerequisite for splitting contexts into services. Changes elsewhere follow via domain events in their own transactions.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q4-why-only-one-aggregate-per-transaction-l3-amazon-jp-morgan)

??? question "Explain the context map patterns."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Amazon

    Partnership, Shared Kernel, Customer–Supplier, Conformist, Anti-Corruption Layer, Open Host Service, Published Language, Separate Ways. They describe both the technical integration and the **power relationship** between teams: who adapts to whom.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q7-explain-the-context-map-patterns-l3l4-sap-amazon)

??? question "Core, supporting and generic subdomains: why does it matter?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    It decides where to invest. Core gets the best people and rich models. Supporting gets pragmatic, simple solutions. Generic gets bought or reused. Misclassifying a generic subdomain as core (building your own payment gateway) wastes years.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q8-core-supporting-and-generic-subdomains-why-does-it-matter-l4-amazon-flipkart)

??? question "Bounded context vs microservice: always one-to-one?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    No. A context is a model boundary, a service a deployment boundary. One context can be several services (for scaling), and several contexts can live in one modular monolith. One service spanning several contexts is a smell.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q10-bounded-context-vs-microservice-always-one-to-one-l3l4-amazon-sap)

??? question "How do domain events relate to integration events?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Domain events are facts inside a context, and can evolve with the model. Integration events are the **published language** for other contexts: versioned, documented, backward-compatible, often a reduced projection. Many teams translate domain events into integration events at the boundary (an outbox writer).

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q12-how-do-domain-events-relate-to-integration-events-l3-amazon-flipkart)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

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

??? question "Design the services for a food delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds · Amazon

    Run through capabilities (catalog, ordering, payment, kitchen, delivery and dispatch, zones, promotions, notification, customer), state the integrators that keep cart, order and order lines together, and the disintegrators that split zones (read-heavy, latency-critical) from dispatch (CPU-heavy, per order) and payments (compliance). Show the checkout sync path, its availability, and which steps are async (kitchen, rider assignment, notifications). Mention that you'd start ordering as a modular monolith if the domain were new.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q12-design-the-services-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds-amazon)


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "What is the inverse Conway maneuver?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Deliberately shape teams to match the architecture you want, so that Conway's Law produces it. In practice teams, ownership, data and architecture must change together; changing only the org chart doesn't work.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q2-what-is-the-inverse-conway-maneuver-l3-flipkart-amazon)

??? question "Explain the four team types in Team Topologies."
    <span class="badge badge-l3">L3</span> SAP · Amazon · JP Morgan

    **Stream-aligned** (owns a stream of change end to end; the default), **platform** (self-service internal services that reduce others' load), **complicated-subsystem** (owns something needing rare specialist skills), **enabling** (coaches teams in a capability, then leaves). Most teams should be stream-aligned; the others exist to make stream-aligned teams fast.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q3-explain-the-four-team-types-in-team-topologies-l3-sap-amazon-jp-morgan)

??? question "What is cognitive load and why does it matter for microservices?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    The amount a team must keep in mind to do its work. Microservices multiply what a team has to know (deployments, observability, contracts, on-call). If a team's load exceeds its capacity, quality and speed drop. Limit domains per team, and use platforms to remove extraneous load.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q5-what-is-cognitive-load-and-why-does-it-matter-for-microservices-l3-amazon-flipkart)

??? question "How would you size and staff teams for 40 microservices?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · Amazon

    Don't start from 40 services; start from streams of change and domain complexity. Small long-lived teams (up to about 9), each owning one complex domain, or one complicated plus a simple one, or two or three simple ones. Strong single-team ownership; a platform team so service count doesn't multiply extraneous load. If 40 services can't be distributed that way, there are too many services (chapter 6).

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q6-how-would-you-size-and-staff-teams-for-40-microservices-l4-jp-morgan-goldman-sachs-amazon)

??? question "What makes a good platform team?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    It treats the platform as a product for internal users: self-service, documented, with a roadmap and satisfaction metrics; it builds the thinnest viable platform (buy what you can); it's consumed as a service, and collaborates only to discover new features. A platform that needs tickets for routine work is a bottleneck, not a platform.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q9-what-makes-a-good-platform-team-l3l4-amazon-jp-morgan)

??? question "A team owns 12 services and is constantly firefighting. What do you do?"
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    First check whether the 12 services are really separate capabilities or a fragmented one (merge if they always change together, chapter 6). Then check the load: domain count and complexity, operational toil. Options: merge services, move a domain to another or a new team, invest in platform capabilities that remove toil, and add an enabling team for reliability practices. Track the result with on-call load and lead time.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q10-a-team-owns-12-services-and-is-constantly-firefighting-what-do-you-do-l4-flipkart-amazon)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "How do virtual threads work, and when should you not use them?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    They're JVM-scheduled threads that unmount from a carrier platform thread when they block on I/O, so blocking code scales to many concurrent requests. They don't help CPU-bound work. Don't pool them, avoid heavy `ThreadLocal` caches, and put explicit limits on shared resources. On Java 21–23, long `synchronized` blocks around I/O pin the carrier; Java 24 fixed that (JEP 491).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q2-how-do-virtual-threads-work-and-when-should-you-not-use-them-l2l3-amazon-flipkart-goldman-sachs)

??? question "How does Spring Boot 4's API versioning work?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    Configure where the version comes from (header, query parameter, path segment or media type parameter) with `spring.mvc.apiversion.*`, then add `version = "1.0"` or `"2.0+"` to request mappings. Unsupported versions get 400. A default version can serve old clients. An `ApiVersionDeprecationHandler` bean adds `Deprecation`, `Sunset` and `Link` headers to deprecated versions. Clients (RestClient, HTTP service clients) can send the version automatically.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q4-how-does-spring-boot-4s-api-versioning-work-l2l3-sap-amazon)

??? question "What are `@Retryable` and `@ConcurrencyLimit` in Spring Framework 7, and when are they not enough?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Annotations in `org.springframework.resilience.annotation`, enabled with `@EnableResilientMethods`. `@Retryable` retries selected exceptions with backoff and jitter; `@ConcurrencyLimit` caps concurrent invocations (a simple bulkhead). They don't provide circuit breakers, rate limiters or time limiters: use Resilience4j (Spring Cloud CircuitBreaker) for those (chapter 18).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q7-what-are-retryable-and-concurrencylimit-in-spring-framework-7-and-when-are-they-not-enough-l3-amazon-flipkart)

??? question "How do you make a Spring Boot service start faster in Kubernetes?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Measure first. Then: trim the classpath (modular starters), lazy-initialize only where safe, use the **AOT cache** (Java 25: `-XX:AOTCacheOutput` training run in the Dockerfile, `-XX:AOTCache` at runtime), optionally Spring AOT on the JVM, and GraalVM native images for scale-to-zero. Tune CPU requests: JIT and startup are CPU-hungry. Use startup probes so slow starts don't trigger restarts.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q9-how-do-you-make-a-spring-boot-service-start-faster-in-kubernetes-l3l4-amazon-goldman-sachs)


### Governance & Organization


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "Which service do you extract first?"
    <span class="badge badge-l3">L3</span> Amazon

    One that changes often, sits outside the dependency cycles and adds few remote dependencies. Measured on Broadleaf: admin, content and search freed 31.8% of changes for 340 remote edges; starting with the hub (order) cost 1,083 for about the same.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q2-which-service-do-you-extract-first-l3-amazon)


**[75. Multi-Tenancy, Multi-Region & Compliance](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)**

??? question "How do you implement data residency in a multi-region system?"
    <span class="badge badge-l4">L4</span> Amazon

    A home region per tenant (or user) in a registry and in tokens; the gateway routes there; personal data is written only there; replication carries reference data and aggregates; DR stays within the jurisdiction.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q7-how-do-you-implement-data-residency-in-a-multi-region-system-l4-amazon)


**[76. Serverless & Function-Based Services](../governance-org/ch76-serverless-microservices.md)**

??? question "When is it the wrong choice?"
    <span class="badge badge-l3">L3</span> Amazon

    For steady, high-rate APIs, and for sparse but latency-critical calls. A light call cost 8× a call on a busy container; two containers were cheaper above 106 (light) or 28–31 (heavy) requests a second. Sparse traffic risks cold starts.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q2-when-is-it-the-wrong-choice-l3-amazon)

??? question "What changed in Lambda billing in 2025?"
    <span class="badge badge-l3">L3</span> AWS-heavy shops

    From 1 August 2025, the INIT phase of on-demand functions on managed runtimes is billed as part of duration. Slow initialisation now costs money as well as latency.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q7-what-changed-in-lambda-billing-in-2025-l3-aws-heavy-shops)

??? question "Does memory size matter for a Java function?"
    <span class="badge badge-l3">L3</span> AWS-heavy shops

    Yes: Lambda allocates CPU in proportion to memory, one vCPU at 1,769 MB. Too little memory slows JVM start and every request; the GB-second price means the right size is found by measuring duration × memory.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q10-does-memory-size-matter-for-a-java-function-l3-aws-heavy-shops)


**[77. AI-Native Microservices](../governance-org/ch77-ai-native-microservices.md)**

??? question "How should a gateway retry 429 and 529?"
    <span class="badge badge-l3">L3</span> Amazon

    With backoff and jitter, honouring `retry-after` for 429 (your limit) and retrying or failing over for 529 (provider capacity), with a cap. Abandoned streams may still count against your limit for a while; 3–5 retries met 429 for that reason here.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q3-how-should-a-gateway-retry-429-and-529-l3-amazon)


### Observability


**[47. Observability Fundamentals](../observability/ch47-observability-fundamentals.md)**

??? question "What does the OpenTelemetry Collector do?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Microsoft · Amazon

    It receives telemetry (OTLP and other formats), processes it (memory limits, batching, attribute changes, redaction, sampling, deriving metrics from spans) and exports it to backends. It decouples services from backends and is where tail sampling and cost controls live.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q8-what-does-the-opentelemetry-collector-do-l2l3-microsoft-amazon)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "How do you keep metric cardinality under control?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Atlassian · Amazon

    Tag values from enums and templates; `MeterFilter.ignoreTags` for identifiers; caps as a backstop with alerts; a series budget per service checked in CI; recording rules that aggregate away pod-level detail; per-entity questions in traces and events.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q11-how-do-you-keep-metric-cardinality-under-control-l3l4-atlassian-amazon)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "What does parent-based sampling mean, and why use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · PayPal

    Follow the caller's sampled flag; apply a ratio only to new roots. Every service then keeps or drops the same traces, so sampled traces are complete. Measured: parent-based everywhere gave 105–108 complete traces from 1,000 orders at 10% and nothing else; one service re-deciding produced 400–904 orphan fragments or 43–47 incomplete traces.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q6-what-does-parent-based-sampling-mean-and-why-use-it-l2l3-amazon-paypal)


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "What does Logback's AsyncAppender do when the queue fills?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Below 20% free capacity (`discardingThreshold`, default queue/5), it silently drops TRACE, DEBUG and INFO. When the queue is full, it blocks the caller, or with `neverBlock` drops any event. Measured under a burst: 29–30% of INFO delivered with the defaults; with `neverBlock`, 9% of WARN.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q5-what-does-logbacks-asyncappender-do-when-the-queue-fills-l3-amazon-walmart)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "Why not alert on error ratio > 1%?"
    <span class="badge badge-l2">L2</span> Zomato · Amazon

    It has no relationship to the budget. It misses slow failures and pages on brief ones. Measured: it never fired for failure rates of 0.6%, 0.25% and 0.15% that spent 14.5–43% of the month's budget.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q5-why-not-alert-on-error-ratio-1-l2-zomato-amazon)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "What timeouts do health checks need?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Each check needs its own timeout below the probe's (1 second by default), running in parallel, with a timeout counted as DOWN. Otherwise one slow dependency makes the endpoint slow, the probe times out, and that counts as a failure. Cache expensive checks for a few seconds when many callers poll.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q9-what-timeouts-do-health-checks-need-l3-amazon-walmart)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "How does Little's law size a pool or a limit?"
    <span class="badge badge-l3">L3</span> Amazon

    In-flight = throughput × latency. 200 threads at 150 ms per request finish at most 1,333 a second (measured: 1,060–1,100 with overhead). To serve 1,500/s at about 0.18 s, you need about 270 in progress, so a limit of 300.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q5-how-does-littles-law-size-a-pool-or-a-limit-l3-amazon)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "What is consistent hashing, and why virtual nodes?"
    <span class="badge badge-l3">L3</span> Amazon · Uber

    Nodes and keys on a hash ring; a key belongs to the next node. Adding a node moves only the keys it takes over (measured: 20% going 4 → 5, against mod n's 80%). With one point per node the arcs are uneven (busiest node 2.26× the average); 256 points per node brought that to 1.10×.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q6-what-is-consistent-hashing-and-why-virtual-nodes-l3-amazon-uber)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "Commitments or right-sizing first?"
    <span class="badge badge-l3">L3</span> Amazon

    Right-size first. A commitment pays for capacity for one to three years; committing before right-sizing locks in the waste (here, about three quarters of the nodes).

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q9-commitments-or-right-sizing-first-l3-amazon)


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

??? question "Does jitter fix retry storms?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    It fixes synchronization, not volume. It turned a reconnect herd that took 42 s into one that took 6 s, but in the metastable experiment jittered retries still stayed at 0% goodput: the retries still arrived. Volume needs a budget or shedding.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q5-does-jitter-fix-retry-storms-l3-amazon-sap)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "Why doesn't my circuit breaker open when the dependency is slow?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Slow successes are only counted as slow above `slowCallDurationThreshold`, which defaults to 60 s, and the slow-call rate threshold defaults to 100%. Measured: 20 seconds of 3-second answers never opened a default breaker. Set the slow-call threshold below your callers' timeout (FoodFleet: 1 s at 50%).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q3-why-doesnt-my-circuit-breaker-open-when-the-dependency-is-slow-l3-amazon-walmart)

??? question "How do you size a bulkhead?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Little's law: concurrent calls = throughput × latency. 400 calls/s at 50 ms needs 20; add headroom for the latency you accept, not the latency of a broken dependency. Reject when full (wait 0). Measured: a bulkhead of 32 held a slow dependency to 32 calls in progress where an unbounded caller reached 146–166.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q11-how-do-you-size-a-bulkhead-l3l4-amazon-flipkart)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "How do you choose a timeout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    From two numbers: the dependency's latency distribution (a ceiling a little above its p99.9, so healthy calls aren't cut off) and the caller's remaining deadline (never wait longer than the caller will). Use the smaller, minus a reserve for your own work, and skip the call if what's left is too small to succeed.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q1-how-do-you-choose-a-timeout-l2l3-amazon-flipkart)

??? question "What is a hedged request, and when is it dangerous?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google · Amazon

    A duplicate request sent when the first hasn't answered by about the p95; the first answer wins. It cut p99 from the full 1 s budget to 88 ms for 10% more calls with spare capacity (measured). Near capacity, hedges make calls slower, which triggers more hedges: at 85% load only 18–43% of users were served. Budget them (5% kept 99%) and use them only for idempotent reads.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q3-what-is-a-hedged-request-and-when-is-it-dangerous-l3l4-google-amazon)

??? question "Why is 'no limit' worse than any limit?"
    <span class="badge badge-l3">L3</span> Amazon · Zomato

    Without a limit the excess waits in a queue: everyone gets slow, callers time out, and the service keeps working for them after they leave. Measured: 4% of users served in a slowdown, and only 2–7% after it ended, because the backlog had to drain. With any limit, half were served and recovery was immediate.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q7-why-is-no-limit-worse-than-any-limit-l3-amazon-zomato)

??? question "What is a brownout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Myntra · Amazon

    Turning optional features down automatically when their dependencies struggle: a controller sets the share of requests that use the feature from how many recent calls were slow. It removes load, which a timeout alone doesn't: timeouts left recommendations at 0% for 15 s after a spike; the dimmer was back to 77–81%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q10-what-is-a-brownout-l2l3-myntra-amazon)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "How do you limit the blast radius?"
    <span class="badge badge-l3">L3</span> Amazon · Swiggy

    Start small and widen: one instance (`mode: one`), a share of connections (`toxicity`), staging before production, short durations, business hours. Measured: the same fault on both instances instead of one hurt 93–94% of users during the experiment.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q4-how-do-you-limit-the-blast-radius-l3-amazon-swiggy)

??? question "Should you run chaos in production?"
    <span class="badge badge-l4">L4</span> Amazon · Netflix-style rounds

    Eventually, for experiments that have held in staging, because only production has real traffic, data and dependencies, but with a small blast radius, an automatic guard, business hours, on-call present and a freeze calendar. Production chaos without those is just an outage you scheduled.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q10-should-you-run-chaos-in-production-l4-amazon-netflix-style-rounds)


**[46. Disaster Recovery & Multi-Region](../resilience/ch46-disaster-recovery.md)**

??? question "Why is asynchronous replication dangerous during a partition?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    The primary keeps accepting commits it can't replicate, so the loss grows with the partition's length instead of being milliseconds. Measured: 2 s of partition lost every commit in those 2 s. Promoting the standby without fencing the old primary then creates two histories.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q4-why-is-asynchronous-replication-dangerous-during-a-partition-l4-amazon-goldman-sachs)

??? question "What is a cell-based architecture?"
    <span class="badge badge-l4">L4</span> Amazon · Atlassian

    Many complete, independent copies of a service, each serving a slice of tenants, with a thin router in front. A failure, a bad deploy or a poison tenant reaches one cell. Measured with 8 instances and 10,000 tenants: a poison tenant took down 100% in a shared pool, 12.7% with 8 cells, and 1.7–3.6% with shuffle sharding (each tenant on 3 or 2 of the 8).

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q11-what-is-a-cell-based-architecture-l4-amazon-atlassian)


### Security


**[35. Security Fundamentals & Threat Modeling](../security/ch35-security-fundamentals.md)**

??? question "What is the most common API vulnerability, and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Broken object-level authorization (OWASP API1): authenticated callers acting on objects that aren't theirs by changing an id. Check ownership on every operation in the service, using the authenticated caller and the stored object, never ids from the request; answer 404 for objects the caller can't see; test the full role × action × state matrix.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q2-what-is-the-most-common-api-vulnerability-and-how-do-you-prevent-it-l2l3-flipkart-amazon)

??? question "Why return 404 instead of 403 for someone else's order?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    A 403 confirms the object exists, so sequential or guessable ids become a map of the business (order volumes, customer counts). 404 reveals nothing. Use 403 when the caller may see the object but not perform the action, like a customer refunding their own order.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q3-why-return-404-instead-of-403-for-someone-elses-order-l3-amazon-goldman-sachs)

??? question "How do you protect a login endpoint against guessing without locking customers out?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Growing delays per account after a few failures, plus limits per source address; no hard lockout, which attackers can trigger on purpose. Same answer for unknown users as for wrong passwords. Measured: ~26,700 checks an hour against one account without a throttle, 18 with it, whatever the number of source addresses. Add breached-password checks and MFA.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q6-how-do-you-protect-a-login-endpoint-against-guessing-without-locking-customers-out-l3-amazon-jp-morgan)

??? question "What is SSRF, and how do you defend against it?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Server-side request forgery (API7): the server fetches a URL the attacker chose, reaching internal systems or cloud metadata. Allow only the schemes and ports you need, resolve the host and refuse internal, loopback, link-local and private addresses (all of the resolved ones, not the URL's text), refuse redirects, and route outbound traffic through an egress proxy with the same rules.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q7-what-is-ssrf-and-how-do-you-defend-against-it-l3-goldman-sachs-amazon)

??? question "Walk through the OWASP API Security Top 10."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · Amazon

    2023 edition: API1 broken object-level authorization, API2 broken authentication, API3 broken object property level authorization, API4 unrestricted resource consumption, API5 broken function-level authorization, API6 unrestricted access to sensitive business flows, API7 SSRF, API8 security misconfiguration, API9 improper inventory management, API10 unsafe consumption of APIs. Three of the first five are authorization.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q10-walk-through-the-owasp-api-security-top-10-l2l3-cognizant-amazon)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "What changes in OAuth 2.1?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    It consolidates the security best practices: PKCE for all authorization code flows, no implicit flow, no password grant, exact redirect URI matching, no bearer tokens in query strings, refresh tokens sender-constrained or rotated. As of September 2026 it is still an Internet-Draft; RFC 9700 already recommends the same.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q2-what-changes-in-oauth-21-l2l3-sap-amazon)

??? question "What must a resource server check in a JWT?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Signature with a key from the issuer's JWKS, chosen by kid; the algorithm from its own allowlist and matching the key; issuer; audience; type; expiry and not-before with small skew; then scopes for the operation. Missing audience checks and token-chosen algorithms are the classic failures.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q4-what-must-a-resource-server-check-in-a-jwt-l3-amazon-goldman-sachs)

??? question "How should signing keys be rotated?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    In stages: publish the new key in the JWKS, wait longer than resource servers cache key sets, start signing with it, and keep the old key published until its tokens expire. Resource servers refresh on unknown kids with a rate limit. Measured: sudden rotation rejected 29 requests, staged 0.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q7-how-should-signing-keys-be-rotated-l3-amazon-jp-morgan)

??? question "RS256 or ES256?"
    <span class="badge badge-l3">L3</span> SAP · Amazon

    Both are fine; never HS256 between parties. Measured in the JDK: RS256 verifies ~15× faster (verification dominates in microservices), ES256 signs ~5× faster and tokens are 40% smaller. Choose by what your issuer supports and where the CPU goes.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q9-rs256-or-es256-l3-sap-amazon)


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "Where do object-level checks go in Spring?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    In the service, with `@EnableMethodSecurity` and `@PreAuthorize("@orderAccess.canRead(authentication, #id)")`, the bean loading the object and applying plain-Java rules. URL rules can't know who owns an object. Answer 404 for denied reads.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q4-where-do-object-level-checks-go-in-spring-l3-amazon-goldman-sachs)

??? question "Why is CSRF disabled in this API but not in a BFF?"
    <span class="badge badge-l3">L3</span> Amazon · any

    CSRF exploits credentials the browser attaches automatically (cookies). Bearer tokens in an `Authorization` header aren't attached automatically, so a stateless token API doesn't need CSRF protection. A BFF that uses a session cookie does.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q5-why-is-csrf-disabled-in-this-api-but-not-in-a-bff-l3-amazon-any)

??? question "Spring Authorization Server or Keycloak?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Keycloak (or a managed provider) when you need user management, federation, MFA, admin UI and many applications. Spring's authorization server when you need an embedded, code-configured provider with custom behaviour, or for local development and tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q10-spring-authorization-server-or-keycloak-l3l4-amazon-sap)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "What is SPIFFE, and what is SPIRE?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    SPIFFE standardises workload identity: SPIFFE IDs (`spiffe://trust-domain/path`), SVIDs (X.509 or JWT documents carrying them), trust bundles and the Workload API. SPIRE implements it: it attests nodes and workloads (for example by Kubernetes namespace and service account) and issues short-lived SVIDs that rotate automatically, so workloads get identity without secrets.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q2-what-is-spiffe-and-what-is-spire-l3-amazon-sap)

??? question "How do you rotate certificates without downtime?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Short lifetimes, rotation at about half-life, on both servers and clients (an `SSLContext` built once won't rotate), and CA rollovers with an overlap where bundles trust old and new CAs until every old SVID has expired. Measured: 0 failures with rotation, 77–78 failures on a CA switch without overlap.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q4-how-do-you-rotate-certificates-without-downtime-l3-amazon-jp-morgan)

??? question "Service mesh or application-level mTLS?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    A mesh gives mTLS and coarse policies everywhere without code changes, but the application sees no client certificate (certificate-bound tokens and per-request certificate checks don't work there) and Istio's defaults are permissive. Application-terminated mTLS gives the service the peer identity for fine-grained decisions and audit. Many platforms use the mesh broadly and application checks for sensitive services.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q7-service-mesh-or-application-level-mtls-l3l4-flipkart-amazon)

??? question "Client credentials vs token exchange between services?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Client credentials when the service acts for itself (sagas, jobs): one cached token per service. Token exchange when it acts for a user: a narrower token for the downstream audience carrying the user (chapter 36). Mutual TLS identifies the workload in both cases.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q8-client-credentials-vs-token-exchange-between-services-l3-amazon-goldman-sachs)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "Are Kubernetes Secrets secure?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Not by default: they are base64-encoded and stored unencrypted in etcd, and anyone who can create pods in a namespace can read its Secrets. Turn on encryption at rest (KMS v2), restrict RBAC, mount them as read-only volumes, and keep the source of truth in a secrets store.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q2-are-kubernetes-secrets-secure-l2-flipkart-amazon)

??? question "What is 'secret zero', and how do you avoid it?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    The credential needed to fetch other secrets. Use platform identity: the pod's Kubernetes service-account token (or cloud workload identity) authenticates to Vault/OpenBao, which issues a scoped, short-lived token. Nothing is delivered to the pod.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q3-what-is-secret-zero-and-how-do-you-avoid-it-l3-amazon-sap)

??? question "How do you rotate a database password without downtime?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Two valid credentials at once: change the account (or key) not in use, roll it out, switch, then change the other. In-place rotation fails until every instance has the new value. Measured: 14–16% of logins failed in place, 0 with two accounts. Dynamic credentials do this automatically.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q5-how-do-you-rotate-a-database-password-without-downtime-l3-amazon-flipkart)

??? question "External Secrets Operator or the CSI driver or reading Vault directly?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    ESO syncs into Kubernetes Secrets (works with anything that reads Secrets; adds a copy in etcd and a refresh delay, one hour by default). The CSI driver mounts directly without a Secret object. Reading Vault directly (Spring Cloud Vault) gives leases and rotation in the application but couples it to Vault. FoodFleet: Vault directly for dynamic credentials, ESO for static third-party keys.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q10-external-secrets-operator-or-the-csi-driver-or-reading-vault-directly-l3-amazon-sap)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "Fixed window, sliding window or token bucket?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart

    Fixed windows are cheap but allow twice the limit across a boundary (measured: 200 in a minute for a limit of 100). Sliding window counters fix most of that with two counters. A token bucket (GCRA) gives a steady rate plus a deliberate burst; the burst size is part of the limit (a bucket of 100 allowed 199 in a minute, a bucket of 10, 109).

    [Full answer in chapter 40](../security/ch40-api-security.md#q1-fixed-window-sliding-window-or-token-bucket-l2-amazon-flipkart)

??? question "How do you rate limit across 20 instances?"
    <span class="badge badge-l3">L3</span> Amazon · Swiggy

    Keep the state in a shared store: one Redis key per client, updated atomically by a Lua script (GCRA), using Redis's clock. Local limits multiply by the instance count (measured: 1,036 through a 260 budget with four instances; Redis, 258–260). Decide the failure mode: a short timeout and a per-instance fallback of limit ÷ instances.

    [Full answer in chapter 40](../security/ch40-api-security.md#q2-how-do-you-rate-limit-across-20-instances-l3-amazon-swiggy)

??? question "What is ReDoS, and is Java safe from it?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    A regex that backtracks exponentially or polynomially on crafted input. JDK 9+ memoises failed positions for greedy loops, so the textbook `(\w+\s?)*` is fast, but the protection is off for patterns with backreferences and doesn't cover lazy loops or polynomial patterns. Measured: a lazy or backreferencing variant took over 10 s for 28 characters; a trailing-whitespace pattern 9.5 s for 20,000 spaces. Limit length first, avoid nested quantifiers, use RE2 for untrusted patterns.

    [Full answer in chapter 40](../security/ch40-api-security.md#q6-what-is-redos-and-is-java-safe-from-it-l3-jp-morgan-amazon)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "Your build passed the vulnerability scan last month. Are you safe?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Only for what was known last month. New advisories arrive daily: Keycloak 24.0.0 had 0 known findings on release day, 14 three months later, 196 today (measured). Rescan deployed SBOMs daily and act on what has a fix.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q2-your-build-passed-the-vulnerability-scan-last-month-are-you-safe-l3-amazon-flipkart)

??? question "How does a vulnerability scanner work, and why do two scanners disagree?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Inventory × advisory database × version matching. They differ in how they identify components (exact purls vs guessed CPEs: Syft guessed coordinates for 8–15% of jars), which databases they use, and how they compare versions (Maven's `2.0-rc1` < `2.0` < `2.0-sp1`; strings put `2.10` before `2.9`).

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q3-how-does-a-vulnerability-scanner-work-and-why-do-two-scanners-disagree-l3-goldman-sachs-amazon)

??? question "What does signing an image prove?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    That someone holding a key (or a short-lived Sigstore certificate for an identity) signed this digest. It says nothing until you pin *who*: with `--certificate-identity-regexp '.*'` a file from the wrong project verified (measured). With a leaked key, a laptop rebuild verified; provenance naming builder and source refused it.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q5-what-does-signing-an-image-prove-l3-jp-morgan-amazon)

??? question "What is SLSA, and what level should FoodFleet aim for?"
    <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    A framework for build integrity. Build L1: provenance exists; L2: generated and signed by a hosted builder; L3: a hardened builder where the build can't forge its own provenance. GitHub Actions with `attest-build-provenance` and reusable workflows gets close to L3; admission checks the provenance's builder and source.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q7-what-is-slsa-and-what-level-should-foodfleet-aim-for-l4-amazon-jp-morgan)

??? question "How do you enforce that only trusted images run?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    An admission controller (Kyverno, Sigstore's policy-controller) that verifies signature identity and provenance for every pod, rejects tags it can't resolve, and rewrites tags to digests so the node pulls what was verified (Kyverno's `mutateDigest`, on by default). Run in Audit first, then Enforce, with monitoring and a break-glass path.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q9-how-do-you-enforce-that-only-trusted-images-run-l3-amazon-sap)


### Testing


**[64. Component & End-to-End Testing](../testing/ch64-component-e2e-testing.md)**

??? question "Where do E2E tests run in the pipeline?"
    <span class="badge badge-l3">L3</span> Amazon

    After unit, contract and component tests pass, in the pull request's preview environment, before merge. A smaller smoke set runs again after each production deployment (chapter 58's canary analysis covers the rest).

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q11-where-do-e2e-tests-run-in-the-pipeline-l3-amazon)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "How do you find a service's capacity?"
    <span class="badge badge-l3">L3</span> Amazon

    Step an arrival rate up until the latency objective breaks, and note the rate. Measured: p99 stayed at 13–14 ms up to 320 requests a second, rose near 380, and reached 1.5–1.7 s at 420, past the service's 390. A closed model just plateaus at capacity and never shows the knee.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q3-how-do-you-find-a-services-capacity-l3-amazon)
