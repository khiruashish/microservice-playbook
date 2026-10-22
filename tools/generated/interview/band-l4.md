### Architecture & Patterns


**[9. Hexagonal, Clean & Onion Architecture](../architecture-patterns/ch09-hexagonal-clean-architecture.md)**

??? question "Isn't this over-engineering for microservices, which are small anyway?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    For thin CRUD services, yes. For services with real rules and several integrations (payments, pricing, dispatch), the core is where the value is, and keeping it independent pays back in testability and change safety. Decide per service based on rule complexity and number of integrations, not as a company-wide mandate.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q9-isnt-this-over-engineering-for-microservices-which-are-small-anyway-l3l4-flipkart-amazon)

??? question "How would you add a new payment provider?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A new driven adapter implementing `PaymentGateway`: an HTTP client in the provider's vocabulary, a translator with tests for every status and error code (unknown means "not approved"), idempotency keys per call, error mapping to the port's exceptions. Run it against the gateway contract test and the provider's sandbox, then route a small percentage of traffic to it by configuration.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q11-how-would-you-add-a-new-payment-provider-l3l4-goldman-sachs-flipkart)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "What happens when the gateway goes down?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Everything public goes down, which is why it must be stateless, horizontally scaled across zones, protected by disruption budgets, deployed with canaries, and kept simple (no business logic, few dependencies). Internal traffic must not route through it, so services keep working with each other during an edge incident.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q8-what-happens-when-the-gateway-goes-down-l3l4-amazon-flipkart)

??? question "GraphQL instead of BFFs?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · startups

    GraphQL lets each client ask for exactly the fields it needs from one schema, which removes much of the shaping work BFFs do. It still needs per-field authorization, query cost limits and careful resolver fan-out. Many organisations combine them: a GraphQL layer per client experience, owned by that client team.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q11-graphql-instead-of-bffs-l3l4-flipkart-startups)

??? question "Design the edge for a food delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds · Amazon

    CDN for static assets and cacheable menus; cloud load balancer with TLS; a stateless API gateway (token validation, header hygiene, request ids, per-user rate limits on order placement, size limits, retries only for reads, circuit breakers with fallbacks); BFFs for the customer app, partner portal and rider app; WebSocket or SSE endpoint for live tracking; services private. Mention capacity (gateway replicas per zone), observability per route, and that internal calls bypass the gateway.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q12-design-the-edge-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds-amazon)


**[11. Service Discovery Patterns](../architecture-patterns/ch11-service-discovery-patterns.md)**

??? question "Design discovery for a platform with VMs, two Kubernetes clusters and a mainframe."
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    One catalog spanning environments (Consul federation or a mesh with multi-cluster support), locality-aware routing, mainframe endpoints registered by a third party behind an ACL or adapter, health checks per environment, and failover rules tested with drills. Keep Kubernetes-native discovery inside clusters and use gateways or mesh gateways between them.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q12-design-discovery-for-a-platform-with-vms-two-kubernetes-clusters-and-a-mainframe-l4-jp-morgan-goldman-sachs)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

??? question "How would you split a shared database in a running system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP · Flipkart

    Map table ownership; audit real statements per service (one DB user each); remove foreign writes first, then foreign reads (APIs, local copies, composition); drop cross-domain foreign keys; move tables into per-service schemas with grants; separate instances where needed. Each step is small and measurable.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q6-how-would-you-split-a-shared-database-in-a-running-system-l3l4-goldman-sachs-sap-flipkart)

??? question "A product page needs data from six services. Design it."
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    A BFF composes the page: the catalog item as driver, then pricing, inventory, ratings and delivery promises in parallel with per-source budgets and fallbacks (hide ratings rather than fail the page), plus caching for slow-changing parts. Listings and search come from an index built from events. Name the latency budget and which parts are critical.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q11-a-product-page-needs-data-from-six-services-design-it-l4-flipkart-amazon)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "How do you change the schema of events already stored?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Never rewrite history. Additive changes are tolerated by readers; breaking changes get a new schema version and an upcaster that converts old events on read; wrong data is corrected with compensating events. As a last resort, copy-and-transform into a new store.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q7-how-do-you-change-the-schema-of-events-already-stored-l4-goldman-sachs-jp-morgan)

??? question "How does event sourcing work with GDPR's right to erasure?"
    <span class="badge badge-l4">L4</span> SAP · banks with EU operations

    Keep personal data out of events (reference ids, store PII separately), or encrypt it with a per-person key and delete the key (crypto-shredding). Design this before the first event is stored.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q8-how-does-event-sourcing-work-with-gdprs-right-to-erasure-l4-sap-banks-with-eu-operations)

??? question "When would you not use event sourcing?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    For CRUD-like data where nobody needs history, for teams without experience and without a clear benefit, and where simple audit tables suffice. It's a tool for ledgers, workflows and domains with disputes and many read views.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q9-when-would-you-not-use-event-sourcing-l3l4-flipkart-amazon)

??? question "Can Kafka be your event store?"
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    It's an excellent event log for integration and stream processing, but lacks per-aggregate optimistic concurrency, efficient per-stream reads and unlimited retention guarantees by default. Most systems use a proper event store (or a database table) for aggregates and Kafka for publishing integration events.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q10-can-kafka-be-your-event-store-l4-flipkart-amazon)

??? question "Design a wallet for delivery partners."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Event-sourced wallet per partner (earnings, tips, adjustments, payout requested/completed/failed), rules in decide (minimum payout, available balance, one payout in flight), optimistic concurrency, idempotent credits per order, projections for the app, ops queue and finance, payouts through a saga with the bank, integration events through an outbox, PII outside events.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q12-design-a-wallet-for-delivery-partners-l4-swiggy-zomato-style-system-design-rounds)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

??? question "What are compensatable, pivot and retriable transactions?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Compensatable steps can be undone; the pivot is the point of no return (if it succeeds, the saga completes); retriable steps follow the pivot and must eventually succeed, so they are retried, not compensated. The order is compensatable*, pivot, retriable*.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q4-what-are-compensatable-pivot-and-retriable-transactions-l4-goldman-sachs-jp-morgan)

??? question "Sagas lack isolation. What problems does that cause and how do you handle them?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Dirty reads, lost updates and fuzzy reads of intermediate state. Countermeasures: semantic locks (PENDING states), commutative updates, re-reading values, pessimistic views (copying values into the saga), ordering by value, and reservations with expiry.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q6-sagas-lack-isolation-what-problems-does-that-cause-and-how-do-you-handle-them-l4-goldman-sachs-sap)

??? question "What if a compensation fails?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Compensations are designed never to fail for business reasons; technical failures are retried until they succeed (they're idempotent). If they keep failing, the saga is marked stuck and escalated, with alerting and a manual procedure, plus reconciliation against the external system.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q8-what-if-a-compensation-fails-l3l4-jp-morgan-goldman-sachs)

??? question "Design the order placement for a food delivery app."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    An orchestrated saga: create order (PENDING) → reserve coupon → authorize payment → restaurant accepts (pivot) → capture, assign rider, confirm. Compensations: release coupon, void authorization, reject order. Idempotency keys per step, outbox and inbox, timeouts (restaurant doesn't respond in 3 minutes → reject and compensate), STUCK dashboard, customer-visible "confirming with restaurant" state.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q9-design-the-order-placement-for-a-food-delivery-app-l4-swiggy-zomato-style-system-design-rounds)

??? question "Would you use a workflow engine like Temporal?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · startups

    Yes for long-running flows with timers, human steps and many branches: durable execution removes most hand-written state handling. For a short, latency-sensitive saga a small orchestrator with a saga log may be simpler. Either way participants must still be idempotent.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q10-would-you-use-a-workflow-engine-like-temporal-l3l4-flipkart-startups)


**[15. Transactional Outbox, Inbox & CDC](../architecture-patterns/ch15-outbox-inbox-cdc.md)**

??? question "Polling publisher vs CDC?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP · Goldman Sachs

    Polling is simple, in your code, works with any database, adds query load and latency of the poll interval. CDC (Debezium) reads the WAL: low latency, commit order, no table queries, but needs Kafka Connect, replication slots, monitoring of WAL retention and schema-change handling.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q4-polling-publisher-vs-cdc-l3l4-flipkart-sap-goldman-sachs)

??? question "How do you preserve event order?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Per aggregate: key messages by aggregate id (same partition), publish outbox rows in position order, stop on failure rather than skipping, and run one active relay per service (or partition relays by aggregate). Global order is rarely needed and expensive.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q5-how-do-you-preserve-event-order-l4-amazon-goldman-sachs)

??? question "What can go wrong with Debezium in production?"
    <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Replication slots retaining WAL when not consumed (disk full), quiet tables not advancing the slot (needs heartbeats), schema changes, connector restarts replaying events, snapshots on first start, failover to a replica without the slot. Monitor slot lag and connector status.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q9-what-can-go-wrong-with-debezium-in-production-l4-flipkart-goldman-sachs)

??? question "Why not just use Kafka transactions?"
    <span class="badge badge-l4">L4</span> JP Morgan · Amazon

    Kafka transactions make consume-process-produce atomic within Kafka. Your PostgreSQL commit isn't part of them, so database plus Kafka is still a dual write. Use the outbox for the database-to-Kafka step; Kafka transactions can help in pure stream processing.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q10-why-not-just-use-kafka-transactions-l4-jp-morgan-amazon)

??? question "How do sagas use the outbox?"
    <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    The orchestrator writes its saga-log update and the command for the next participant to the outbox in one transaction, so it can never record "sent" without sending, or send without recording. Participants use an inbox to deduplicate commands and write their replies through their own outbox.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q11-how-do-sagas-use-the-outbox-l4-sap-goldman-sachs)


**[16. Strangler Fig, ACL & Monolith Migration](../architecture-patterns/ch16-strangler-fig-migration.md)**

??? question "How do you know the new service behaves like the old one?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Shadow traffic: call both on real production requests, return the old answer, compare field by field, and fix or explicitly accept every difference (with documented tolerances). Contract and characterization tests help, but production traffic finds the cases nobody thought of.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q4-how-do-you-know-the-new-service-behaves-like-the-old-one-l3l4-goldman-sachs-jp-morgan)

??? question "What should trigger an automatic rollback?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Error rate, latency percentiles and at least one business metric (conversion, order value, payment success) on the new path compared with a threshold or the old path, over a window with a minimum sample size. A guard that can't get enough samples (tiny cohorts) can't decide.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q6-what-should-trigger-an-automatic-rollback-l4-amazon-flipkart)

??? question "How do you migrate the data?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Read legacy data through an ACL first; then backfill and sync with CDC; reconcile continuously; switch write ownership once (never two writers); have the monolith read from the new owner; retire legacy tables when audits show no readers.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q7-how-do-you-migrate-the-data-l4-goldman-sachs-sap)

??? question "Which capability would you extract first?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Cognizant · Flipkart

    One with a clear boundary, few dependencies, its own data, mostly reads or calculations (so it can be shadowed), frequent change (quick payback), and a team to own it. Not the core entity everything joins against.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q8-which-capability-would-you-extract-first-l3l4-sap-cognizant-flipkart)

??? question "What are the risks of the strangler approach?"
    <span class="badge badge-l4">L4</span> Goldman Sachs

    Migrations that never finish (old code never deleted), the facade becoming a bottleneck or a place for business logic, two writers on shared data, a distributed monolith if new services keep calling back into the old one, and the cost of running both systems for longer.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q9-what-are-the-risks-of-the-strangler-approach-l4-goldman-sachs)

??? question "How do you handle writes that can't be shadowed?"
    <span class="badge badge-l4">L4</span> JP Morgan · Amazon

    Compare decisions rather than effects (what the new service *would* write), run it against a copy of data, canary with very small cohorts and strong guards, or move writes last once reads have proven the model. Never execute both writes for real.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q10-how-do-you-handle-writes-that-cant-be-shadowed-l4-jp-morgan-amazon)

??? question "When is a migration done?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    When the old code, its data, the flags and the facade routes for that capability are deleted, not when the new service takes 100% of traffic. Set a deadline for deletion and track it.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q12-when-is-a-migration-done-l3l4-any)


**[17. Sidecar, Ambassador & Adapter](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)**

??? question "How do service meshes relate to these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A sidecar mesh puts an ambassador-style proxy (usually Envoy) in every pod for all traffic: mTLS, retries, traffic splitting, telemetry. Ambient mode moves that out of the pod to per-node and per-namespace proxies to cut per-pod cost.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q7-how-do-service-meshes-relate-to-these-patterns-l3l4-goldman-sachs-flipkart)

??? question "What are the security considerations for sidecars?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Least privilege per container (secrets mounted only where needed), non-root and read-only filesystems, loopback binding for proxies holding credentials, image provenance and patching for every sidecar image, and network policy around anything that reaches external systems.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q11-what-are-the-security-considerations-for-sidecars-l3l4-goldman-sachs-jp-morgan)


**[18. Resilience Patterns](../architecture-patterns/ch18-resilience-patterns.md)**

??? question "In what order do you compose these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Commonly fallback outermost, then bulkhead, retry, circuit breaker, timeout innermost (Resilience4j's Spring annotations apply Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(call)))))). Whether the breaker sees attempts or operations depends on whether it is inside or outside retry; choose deliberately.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q8-in-what-order-do-you-compose-these-patterns-l3l4-goldman-sachs-sap)

??? question "How do you know your resilience settings work?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Measure: dashboards of timeouts, retries, breaker states and fallbacks per dependency; load tests with injected latency; chaos experiments in staging and production (latency, not just errors); and simulations like this chapter's to reason about settings before an incident.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q10-how-do-you-know-your-resilience-settings-work-l3l4-amazon-flipkart)

??? question "Would you put resilience in the app, a sidecar, or the mesh?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Timeouts everywhere; retries in exactly one layer, usually closest to the dependency; breakers either in the app or the mesh; fallbacks always in the app, because only it knows what a useful degraded answer is.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q12-would-you-put-resilience-in-the-app-a-sidecar-or-the-mesh-l4-goldman-sachs-flipkart)


**[19. Event-Driven Architecture](../architecture-patterns/ch19-event-driven-architecture.md)**

??? question "How does a new consumer get the current state?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    From a compacted topic (latest event per key), then it follows the live stream; or from a snapshot or bulk API plus the stream from a known position. Replaying a delete-retention topic is incomplete and slow.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q5-how-does-a-new-consumer-get-the-current-state-l3l4-flipkart-goldman-sachs)

??? question "How do you evolve event schemas?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · JP Morgan

    Treat events as contracts: add optional fields only, never rename or repurpose; enforce compatibility with a schema registry and consumer contract tests; publish breaking changes as a new event type or topic alongside the old one until consumers migrate.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q7-how-do-you-evolve-event-schemas-l3l4-sap-jp-morgan)

??? question "How do you include personal data in events safely?"
    <span class="badge badge-l4">L4</span> JP Morgan · SAP

    Prefer not to: publish ids and let authorised consumers fetch details. If needed, minimise fields, encrypt sensitive ones (crypto-shredding for erasure), set retention deliberately, and document consumers for data-protection requests.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q10-how-do-you-include-personal-data-in-events-safely-l4-jp-morgan-sap)

??? question "Design menu availability propagation for a food delivery app."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style rounds

    catalog owns items and publishes `MenuItemUpdated` (state, version, event id) via outbox to a compacted topic keyed by item id; ordering, search and recommendations keep replicas, newest-version-wins; ordering checks availability locally and the kitchen's accept step (the saga pivot) remains the final authority; monitor lag; new consumers bootstrap from the compacted topic.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q11-design-menu-availability-propagation-for-a-food-delivery-app-l4-swiggy-zomato-style-rounds)


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "How would you detect a distributed monolith in an existing system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    From evidence: deploy history (co-deployment ratios), distributed traces (synchronous depth, cycles, fan-out), database audit logs (non-owner table access), dependency manifests (shared domain libraries, version skew), and change coupling in version control.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q2-how-would-you-detect-a-distributed-monolith-in-an-existing-system-l3l4-amazon-goldman-sachs)

??? question "Is a shared database always an anti-pattern?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Non-owner writes almost always are. Non-owner reads are a weaker smell that can be a deliberate, temporary step (for example during migration) if they are tracked and burned down. Separate schemas on one database server, with no cross-access, are fine.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q7-is-a-shared-database-always-an-anti-pattern-l3l4-sap-goldman-sachs)

??? question "How do you keep anti-patterns from coming back?"
    <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Fitness functions in the build: an architecture model generated from real evidence, detectors with agreed thresholds, and a baseline of accepted findings with owners and tickets that can only shrink.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q8-how-do-you-keep-anti-patterns-from-coming-back-l4-flipkart-goldman-sachs)

??? question "Microservices or modular monolith for a new product?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> startups · any

    Usually a modular monolith first, with clear module boundaries and owned data, extracting a service when there is a concrete reason (independent scaling, deployment cadence, team autonomy, isolation). Starting with fine-grained services before boundaries are known is the most common route to a distributed monolith.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q9-microservices-or-modular-monolith-for-a-new-product-l3l4-startups-any)

??? question "Your team wants to split a service 'for performance'. What do you ask?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Which part has a different load or latency profile, with data? How many calls per request will cross the new boundary? Will the parts change and release independently? Who owns each? If the answers are "same profile, many calls, always together, same team", the split will make performance worse.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q11-your-team-wants-to-split-a-service-for-performance-what-do-you-ask-l3l4-amazon-goldman-sachs)

??? question "Name anti-patterns you've seen and how you fixed them."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Use a concrete story with evidence and outcome: e.g., a lockstep cluster caused by a shared domain library and a table written by two services, found from deploy logs and database audit logs, fixed by merging the services that always changed together and replacing the library with published contracts, then guarded by a fitness function.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q12-name-anti-patterns-youve-seen-and-how-you-fixed-them-l3l4-any)


### Communication


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "When would you not use gRPC?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Public or partner APIs, browser-facing endpoints, cacheable resource reads, small teams with HTTP-only tooling, and simple request/response integrations where the benefits (streaming, deadlines, generated contracts) won't be used. The cost is real: schema tooling, HTTP/2-aware infrastructure and harder debugging.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q12-when-would-you-not-use-grpc-l3l4-any)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "Should microservices talk to each other in GraphQL?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Usually not. Between services you want fixed contracts, predictable cost, deadlines and simple tooling: REST, gRPC or events. GraphQL's strength is letting many changing client screens choose their data, so it belongs at the client-facing edge, typically in a BFF or a federated gateway.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q8-should-microservices-talk-to-each-other-in-graphql-l3l4-any)

??? question "What is GraphQL federation and when is it worth it?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    A supergraph composed from subgraphs owned by different services, with a router planning each query across them; entities like `Restaurant` can be extended by several subgraphs. It is worth it when many teams contribute to one client-facing graph; otherwise a single BFF graph is simpler to run and debug.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q9-what-is-graphql-federation-and-when-is-it-worth-it-l3l4-amazon-sap)

??? question "GraphQL or a BFF with REST endpoints: how do you decide?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    A BFF endpoint per screen is simple, cacheable and predictable, and was as fast as GraphQL for one screen here. GraphQL wins when there are many screens and clients that change often, because one schema replaces many endpoints, at the cost of batching discipline, query limits, new monitoring and weaker HTTP caching.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q12-graphql-or-a-bff-with-rest-endpoints-how-do-you-decide-l3l4-any)


**[25. Asynchronous Messaging](../communication/ch25-asynchronous-messaging.md)**

??? question "How would you design dead-letter handling for a payments system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Classify errors; retry transient ones with bounded backoff in order (per account); dead-letter the rest with full context (headers, original offset, error); alert immediately; give operations a tool to inspect, fix and replay with approval and audit; make consumers idempotent so replays are safe; never drop.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q10-how-would-you-design-dead-letter-handling-for-a-payments-system-l3l4-jp-morgan-goldman-sachs)


**[27. API Contracts & Schema Evolution](../communication/ch27-api-contracts.md)**

??? question "Which schema registry compatibility mode would you choose for domain events, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    `FULL_TRANSITIVE`: any consumer version can read any event ever written, which matters for replays, new consumers reading history, and deploys in any order. The cost is that only optional fields (with defaults) can be added or removed; anything else is a new event type or topic.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q10-which-schema-registry-compatibility-mode-would-you-choose-for-domain-events-and-why-l3l4-amazon-jp-morgan)

??? question "What can't contract tests and schema checks catch?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Changes of meaning or units with the same name and type, behaviour not covered by examples, performance changes, and consumers that publish no contracts. Prevent these with treating meaning changes as new fields, consumer review of semantic changes, and usage tracking.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q11-what-cant-contract-tests-and-schema-checks-catch-l3l4-any)

??? question "How do you govern APIs across a hundred teams?"
    <span class="badge badge-l4">L4</span> Amazon · SAP

    Contracts in repos (spec-first), linting with shared rules, automated breaking-change checks, consumer contracts for critical consumers, a catalogue of APIs and their consumers, tolerant-reader defaults in the platform's client libraries, and a lightweight review only for breaking or semantic changes; teams evolve additively without asking permission.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q12-how-do-you-govern-apis-across-a-hundred-teams-l4-amazon-sap)


### Data


**[29. Polyglot Persistence](../data/ch29-polyglot-persistence.md)**

??? question "What's wrong with a projector that remembers the last outbox id it processed?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Amazon

    Sequence values are assigned at insert but become visible at commit, in a different order. A transaction that took id 1,000 can commit after the projector has read 1,001, and is never read. Measured: 29–48 of 5,000 items left wrong. Claim rows with `FOR UPDATE SKIP LOCKED` and delete them when applied, or follow the log position (logical replication, Debezium).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q6-whats-wrong-with-a-projector-that-remembers-the-last-outbox-id-it-processed-l3l4-goldman-sachs-amazon)

??? question "What does every additional database cost?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> any

    Provisioning, backups and restore drills, monitoring and alerting, upgrades and security patches, access control, capacity planning, on-call expertise, local and CI setup, and a sync path from the source of truth with its own failure modes and lag. These costs are permanent and usually exceed the licence or hosting bill.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q11-what-does-every-additional-database-cost-l3l4-any)

??? question "Your team wants MongoDB, Elasticsearch, Redis and a time-series DB for one new service. How do you respond?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Ask for the access patterns with numbers and measure them on the supported stores first. Then add only stores that close a measured gap, each as a derived store or ephemeral state fed from one source of truth, with an owner and the operating cost accepted. At FoodFleet that turned four proposed stores into PostgreSQL plus Redis.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q12-your-team-wants-mongodb-elasticsearch-redis-and-a-time-series-db-for-one-new-service-how-do-you-respond-l4-amazon-goldman-sachs)


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "What happens if the coordinator crashes after the participants prepared?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    The participants stay prepared, holding locks, across restarts, until the coordinator recovers and reads its log or someone resolves them. With presumed abort, no logged decision means roll back. Measured: the prepared transaction survived a database restart and kept its locks until recovery ran.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q3-what-happens-if-the-coordinator-crashes-after-the-participants-prepared-l3l4-goldman-sachs-jp-morgan)

??? question "What is a heuristic decision and why is it dangerous?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Committing or rolling back an in-doubt transaction without the coordinator's decision, usually by an operator to free blocked rows. If another participant got the opposite decision, the system is inconsistent ("heuristic mixed") and needs manual repair. Use the decision log; audit any heuristic action.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q4-what-is-a-heuristic-decision-and-why-is-it-dangerous-l4-jp-morgan-goldman-sachs)

??? question "Does three-phase commit solve blocking?"
    <span class="badge badge-l4">L4</span> Amazon · any

    Only if message delays are bounded and failures detectable, which networks don't guarantee; under partitions it can produce inconsistency. Practical systems instead replicate the coordinator and participants with consensus (Paxos/Raft), as Spanner does, so a single machine's failure doesn't block.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q5-does-three-phase-commit-solve-blocking-l4-amazon-any)

??? question "When is 2PC acceptable?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Inside one service or one database product: a service writing to two of its own databases at low volume with an operations team ready for in-doubt recovery; distributed SQL databases that run 2PC over consensus-replicated participants; legacy integration in an application server. Not across services, teams or external systems.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q11-when-is-2pc-acceptable-l4-goldman-sachs-sap)

??? question "Design order placement for a food delivery app without distributed transactions."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    `POST /orders` writes the order `PENDING` and an outbox row in one transaction and returns 202. A relay delivers the event to payment-service, which debits the wallet and takes stock in one local transaction keyed by order id (stored decision, idempotent). The relay records `CONFIRMED` or `REJECTED` with a reason and the app is notified. Recovery jobs with grace periods settle stuck and unknown cases; compensations (refund, restock) handle later cancellations.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q12-design-order-placement-for-a-food-delivery-app-without-distributed-transactions-l4-swiggy-zomato-style-system-design-rounds)


**[31. Data Consistency](../data/ch31-data-consistency.md)**

??? question "How does PostgreSQL report replication lag, and what can go wrong reading from a replica?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    `pg_stat_replication` on the primary (write/flush/replay lag), `pg_last_wal_replay_lsn()` and `pg_last_xact_replay_timestamp()` on the replica. Pitfalls: stale reads, queries cancelled by replay conflicts (SQLSTATE 40001), and a statement's snapshot preceding the functions in it, so check the position before reading, in a separate statement.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q7-how-does-postgresql-report-replication-lag-and-what-can-go-wrong-reading-from-a-replica-l3l4-goldman-sachs-sap)

??? question "Last-writer-wins: when is it acceptable?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · any

    For single values where losing a concurrent write is acceptable (a display name, a note), with timestamps from a hybrid logical clock so causally later writes win. Not for collections or counters: measured, LWW on whole carts lost 562–578 added items across 2,000 carts.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q8-last-writer-wins-when-is-it-acceptable-l3l4-amazon-any)

??? question "What is a CRDT? Give an example."
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    A data type whose replicas converge without coordination because merge is commutative, associative and idempotent. An OR-Set tags each add uniquely and removes only observed tags, so a concurrent add survives; a PN-counter keeps per-replica increments and decrements. Measured: no lost or resurrected items across 2,000 two-region carts.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q9-what-is-a-crdt-give-an-example-l4-amazon-goldman-sachs)

??? question "What is a hybrid logical clock and why use one?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    A timestamp of (physical time, counter) that never goes backwards and is always greater than any timestamp the node has seen. It stays close to wall time but respects causality, so ordering by it never puts an effect before what its writer had seen, even with skewed clocks. Used by CockroachDB and YugabyteDB.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q10-what-is-a-hybrid-logical-clock-and-why-use-one-l4-goldman-sachs-sap)

??? question "Explain PACELC with an example from your system."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    If partitioned, choose availability or consistency; else, latency or consistency. FoodFleet's restaurant listing chooses availability and latency (replicas, caches); the wallet chooses consistency (primary only) and accepts the latency; profiles use tokens to get read-your-writes at near-replica latency.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q11-explain-pacelc-with-an-example-from-your-system-l3l4-amazon-flipkart)

??? question "Design consistency for a food-delivery app's data."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Classify reads: wallet and payment status strong (primary); own profile, addresses and orders read-your-writes (tokens); tracking monotonic (tokens); menus, listings and ratings bounded-stale (replicas, caches). Counters by atomic updates; user-edited records with If-Match; cart as a CRDT if edited offline or in several regions; checkout re-reads prices and stock from the source of truth.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q12-design-consistency-for-a-food-delivery-apps-data-l4-swiggy-zomato-style-system-design-rounds)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "How do you keep a shared cache consistent across many services?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Let one service own the data and the cache, or drive invalidation from the owner's change stream (outbox or CDC) so no writer has to know every key. Version cached values so late or duplicate invalidations can't move a cache backwards.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q8-how-do-you-keep-a-shared-cache-consistent-across-many-services-l4-goldman-sachs-sap)

??? question "Design caching for a food-delivery home screen."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Rankings and recommendations: stale-while-revalidate per city or segment with refresh locks and jitter. Restaurant details: cache-aside, evicted after commit. Prices and availability: write-through with versions, re-read at checkout. Images and static content: CDN. View counters: write-behind. Every cache optional, monitored, and never the input to payment or stock decisions.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q12-design-caching-for-a-food-delivery-home-screen-l4-swiggy-zomato-style-system-design-rounds)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

??? question "Read replicas or sharding?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Replicas scale reads that tolerate lag and are cheap to add; sharding scales writes and data size but makes cross-shard queries, transactions and operations hard. Exhaust indexing, caching, replicas, partitioning and write reduction first; shard when one primary can't take the writes.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q8-read-replicas-or-sharding-l3l4-flipkart-goldman-sachs)

??? question "How do you choose a shard key?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    High cardinality, even distribution, and present in most queries. Measured: by city, one shard held 6× the average and half the shards held nothing; by customer with the shard embedded in order ids, 70% of queries hit one shard and the largest shard was 1.19× the average.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q9-how-do-you-choose-a-shard-key-l4-amazon-flipkart)

??? question "Why not hash(key) mod N?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Changing N remaps almost every key. Measured: 80% of orders moved going from 16 to 20 shards, against 19.5% with 4,096 fixed slots and 18.6% with a consistent-hash ring. Slots also give a small, explicit map and a unit of migration.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q10-why-not-hashkey-mod-n-l3l4-amazon-sap)

??? question "How do you move data between shards without downtime?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Per slot: copy the rows, stream changes (logical replication, CDC or dual writes), verify counts and checksums, pause writes to that slot briefly, switch the slot map, resume, then delete the source copy. Routers read a versioned slot map; the old owner rejects writes for moved slots.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q11-how-do-you-move-data-between-shards-without-downtime-l4-goldman-sachs-jp-morgan)

??? question "Design the order database for a food-delivery platform growing 10× a year."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Start with one PostgreSQL primary, replicas for reads and reports, time-partitioned orders and archiving, small fleet-wide pool budgets and a pooler. Lock-safe, expand/contract migrations from day one. When writes outgrow the primary, shard by customer through a slot map with the slot embedded in order ids, restaurant-side views built from events (chapter 15), and slot moves as a rehearsed runbook.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q12-design-the-order-database-for-a-food-delivery-platform-growing-10-a-year-l4-swiggy-zomato-style-system-design-rounds)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "What is data mesh, and when is it worth it?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Domain teams own and publish their analytical data as products (owner, schema, semantics, freshness and quality objectives) on a self-serve platform, with federated governance. Worth it when a central data team has become the bottleneck for many domains; without a strong platform and clear ownership it becomes many inconsistent pipelines.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q10-what-is-data-mesh-and-when-is-it-worth-it-l4-goldman-sachs-sap)

??? question "A reporting store disagrees with the payments system. What do you do?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    The system of record wins. Reconcile daily per day and entity, find the class of difference (missed events, duplicates, corrections, time zones, late data), fix the projection, and rebuild the affected range by replay. Reports carry their as-of time so known gaps are explainable.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q11-a-reporting-store-disagrees-with-the-payments-system-what-do-you-do-l3l4-jp-morgan-goldman-sachs)

??? question "Design analytics for a food-delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Services publish outbox events (orders, payments, deliveries) to Kafka; CDC for the rest with capped slots. A reporting service maintains facts by version and incremental aggregates by city's business day for operations dashboards (minutes fresh, with as-of times). The same topics land in an Iceberg lake for history, finance reconciliation and data science. Datasets have owners and contracts; production databases are never queried for analytics.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q12-design-analytics-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds)


### Deployment & Platform


**[53. Containerization](../deployment/ch53-containerization.md)**

??? question "Design FoodFleet's image pipeline."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q12-design-foodfleets-image-pipeline-l4-system-design-rounds)


**[54. Kubernetes for Spring Developers](../deployment/ch54-kubernetes-for-spring.md)**

??? question "Design the Kubernetes template for FoodFleet's services."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q12-design-the-kubernetes-template-for-foodfleets-services-l4-system-design-rounds)


**[55. Helm, Kustomize & Environment Promotion](../deployment/ch55-helm-kustomize.md)**

??? question "Design FoodFleet's deployment pipeline from commit to prod."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q12-design-foodfleets-deployment-pipeline-from-commit-to-prod-l4-system-design-rounds)


**[56. Autoscaling](../deployment/ch56-autoscaling.md)**

??? question "Design autoscaling for FoodFleet's lunch peak."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q12-design-autoscaling-for-foodfleets-lunch-peak-l4-system-design-rounds)


**[57. Service Mesh](../deployment/ch57-service-mesh.md)**

??? question "Design FoodFleet's service-to-service traffic layer."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q12-design-foodfleets-service-to-service-traffic-layer-l4-system-design-rounds)


**[58. CI/CD, GitOps & Progressive Delivery](../deployment/ch58-cicd-gitops.md)**

??? question "Design FoodFleet's path from commit to production."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q12-design-foodfleets-path-from-commit-to-production-l4-system-design-rounds)


**[59. Cloud-Native on AWS, Azure & GCP](../deployment/ch59-cloud-native-platforms.md)**

??? question "How do you keep a multi-cloud option without paying for it every day?"
    <span class="badge badge-l4">L4</span> architecture rounds

    Be portable where it's cheap: Kubernetes manifests, OpenTelemetry, Kafka and PostgreSQL protocols, Terraform modules per cloud. Accept cloud-specific identity and managed services, documented as the parts to rewrite. Rehearse a move once, small.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q11-how-do-you-keep-a-multi-cloud-option-without-paying-for-it-every-day-l4-architecture-rounds)

??? question "Design FoodFleet's cloud platform."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q12-design-foodfleets-cloud-platform-l4-system-design-rounds)


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

??? question "Native image or JVM for a new service?"
    <span class="badge badge-l4">L4</span> architecture rounds

    JVM with an AOT cache for long-running services, where throughput and library freedom matter and the cache closes half the startup gap. Native for functions, jobs, CLIs and scale-to-zero, where startup and memory dominate and the service is small enough to keep its metadata tested.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q11-native-image-or-jvm-for-a-new-service-l4-architecture-rounds)

??? question "Design FoodFleet's startup strategy."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q12-design-foodfleets-startup-strategy-l4-system-design-rounds)


### Foundations


**[1. Monolith vs SOA vs Microservices vs Modular Monolith](../foundations/ch01-architecture-styles.md)**

??? question "How do you decide which module to extract first?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon · SAP

    Extract where independence creates measurable value: a distinct scaling profile, a distinct compliance scope, a team that will own it, failure isolation needs, and low synchronous coupling with clean data ownership. Avoid modules that need ACID transactions with others.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q6-how-do-you-decide-which-module-to-extract-first-l3l4-flipkart-amazon-sap)

??? question "You're asked to migrate a 10-year-old banking monolith to microservices. What's your plan?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · SAP · Cognizant

    Don't rewrite. Modularise in place, establish the platform, then strangle capabilities one at a time, starting at the edges, keeping the core ledger coarse-grained, and measuring outcomes at every step.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q10-youre-asked-to-migrate-a-10-year-old-banking-monolith-to-microservices-whats-your-plan-l4-jp-morgan-goldman-sachs-sap-cognizant)

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

??? question "How do you enforce principles across 50 teams without an architecture review board?"
    <span class="badge badge-l4">L4</span> SAP · Amazon · Flipkart

    Paved road plus fitness functions. Service templates with secure, observable defaults. Shared architecture-rule tests (ArchUnit, Spring Modulith) run in every pipeline. API linting. Scorecards in the developer portal. Exceptions through lightweight ADRs.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q9-how-do-you-enforce-principles-across-50-teams-without-an-architecture-review-board-l4-sap-amazon-flipkart)

??? question "What is a fitness function? Give examples."
    <span class="badge badge-l4">L4</span> SAP · ThoughtWorks-style consultancies

    An automated, objective check that an architectural property still holds. Examples: layering rules (ArchUnit), module boundaries (Spring Modulith `verify()`), "no `RestTemplate`" rules, API breaking-change detection in CI, p99 latency gates in performance tests, dependency-age checks.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q12-what-is-a-fitness-function-give-examples-l4-sap-thoughtworks-style-consultancies)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Design an idempotency-key mechanism for a payments API."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · Amazon

    The client generates a unique key per logical operation. The server atomically claims it (insert-if-absent with a unique constraint), stores a fingerprint of the request, runs the operation, and stores the response. Same key and body → replay the stored response. Same key while running → 409. Same key with a different body → 422. Failures release the key. Keys expire after a retention period longer than any retry horizon.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q8-design-an-idempotency-key-mechanism-for-a-payments-api-l3l4-jp-morgan-goldman-sachs-amazon)

??? question "What is split brain and how do fencing tokens prevent damage?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Two nodes both believe they are leader, for example after a GC pause outlives a lease. A fencing token is a monotonically increasing number issued with each lease. The protected resource rejects writes carrying an older token, so a stale leader's writes fail even if it doesn't know it's stale.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q11-what-is-split-brain-and-how-do-fencing-tokens-prevent-damage-l4-goldman-sachs-sap)

??? question "How do you choose between CP and AP for a FoodFleet feature?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    By the cost of each kind of error. A stale menu price is cheap and recoverable, so choose AP with a freshness SLO. Double-redeeming a coupon or double-paying a restaurant is expensive, so choose CP with conditional writes. Write it in the service charter, and test the partition behaviour.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q13-how-do-you-choose-between-cp-and-ap-for-a-foodfleet-feature-l4-amazon-flipkart)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "How do you make a Spring Boot service start fast?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Measure first. Then use a layered image, and on Java 25 an AOT cache trained at build time (JEP 483 and 514). Consider CRaC for checkpoint/restore, or GraalVM native images when startup and memory dominate and their build constraints are acceptable. Avoid heavy work in the startup path, and lazily initialise optional components.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q8-how-do-you-make-a-spring-boot-service-start-fast-l3l4-amazon-sap)

??? question "What did 'Beyond the Twelve-Factor App' add, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    API first (contracts enable parallel teams), telemetry (you can't operate what you can't see), and authentication and authorization (security can't be bolted on in a zero-trust, many-service world). It also reinterpreted some factors for containers and microservices.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q10-what-did-beyond-the-twelve-factor-app-add-and-why-l3l4-sap-goldman-sachs)

??? question "Which factors would you relax for a stateful system like Kafka or PostgreSQL on Kubernetes?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    The factors are about *applications*. Stateful backing services are run with operators and StatefulSets, stable identities, persistent volumes and careful disruption budgets. The application side treats them as attached resources and stays twelve-factor.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q11-which-factors-would-you-relax-for-a-stateful-system-like-kafka-or-postgresql-on-kubernetes-l4-amazon-flipkart)

??? question "How do you prove to an auditor that production runs the tested artifact?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Build once and deploy by immutable digest. Sign images and attach SBOMs, record each release (digest plus config commit) in Git, enforce signature verification at admission, and show that only configuration differs between UAT and production.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q12-how-do-you-prove-to-an-auditor-that-production-runs-the-tested-artifact-l4-jp-morgan-goldman-sachs)


**[5. Domain-Driven Design](../foundations/ch05-domain-driven-design.md)**

??? question "Explain the context map patterns."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Amazon

    Partnership, Shared Kernel, Customer–Supplier, Conformist, Anti-Corruption Layer, Open Host Service, Published Language, Separate Ways. They describe both the technical integration and the **power relationship** between teams: who adapts to whom.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q7-explain-the-context-map-patterns-l3l4-sap-amazon)

??? question "Core, supporting and generic subdomains: why does it matter?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    It decides where to invest. Core gets the best people and rich models. Supporting gets pragmatic, simple solutions. Generic gets bought or reused. Misclassifying a generic subdomain as core (building your own payment gateway) wastes years.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q8-core-supporting-and-generic-subdomains-why-does-it-matter-l4-amazon-flipkart)

??? question "How do you find bounded contexts in a new domain?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · consulting firms

    Talk to domain experts. Run Event Storming: domain events on a timeline, then commands, actors, policies and aggregates. Look for language changes ("order" becomes "ticket" becomes "delivery job"), different rules and lifecycles, and team and process boundaries. Validate with the context map and the expected rate of change. [Chapter 6](../foundations/ch06-service-decomposition.md) walks through it.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q9-how-do-you-find-bounded-contexts-in-a-new-domain-l3l4-flipkart-consulting-firms)

??? question "Bounded context vs microservice: always one-to-one?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    No. A context is a model boundary, a service a deployment boundary. One context can be several services (for scaling), and several contexts can live in one modular monolith. One service spanning several contexts is a smell.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q10-bounded-context-vs-microservice-always-one-to-one-l3l4-amazon-sap)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

??? question "What are granularity disintegrators and integrators?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan · Amazon

    From *Software Architecture: The Hard Parts*. Disintegrators justify splitting: scope and function, code volatility, scalability, fault tolerance, security, extensibility. Integrators justify keeping together: database transactions, workflow coupling, shared code, data relationships. A boundary is right when the disintegrators clearly outweigh the integrators, and you write down which ones.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q6-what-are-granularity-disintegrators-and-integrators-l3l4-goldman-sachs-jp-morgan-amazon)

??? question "How would you find service boundaries in an existing monolith?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · SAP

    Combine three sources: Event Storming with the people who run the business process; static structure (package dependencies, Spring Modulith's module report); and **change coupling** from git history (modules that change together belong together). Where they agree, you have a candidate. Where they disagree, you have a design discussion. Then extract one capability at a time with the strangler fig.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q7-how-would-you-find-service-boundaries-in-an-existing-monolith-l4-jp-morgan-goldman-sachs-sap)

??? question "When is it right to keep things in one service even though the teams want to split?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    When a strong integrator holds: an invariant that must be ACID (ledger postings, order and its lines), very high call volume between the parts, or a domain still too new to have stable boundaries. You can still give teams autonomy with module boundaries and code ownership inside one deployable.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q9-when-is-it-right-to-keep-things-in-one-service-even-though-the-teams-want-to-split-l4-goldman-sachs-jp-morgan)

??? question "How do you split the database when you split a service?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · SAP

    Give each table one owning service; others use the owner's API or subscribe to its events and keep a local read model. Do it in steps: split schemas inside one database, remove cross-schema joins and foreign keys, then move schemas to separate databases. [Chapter 12](../architecture-patterns/ch12-database-per-service.md) covers the patterns and [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md) the migration.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q11-how-do-you-split-the-database-when-you-split-a-service-l3l4-jp-morgan-sap)

??? question "Design the services for a food delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds · Amazon

    Run through capabilities (catalog, ordering, payment, kitchen, delivery and dispatch, zones, promotions, notification, customer), state the integrators that keep cart, order and order lines together, and the disintegrators that split zones (read-heavy, latency-critical) from dispatch (CPU-heavy, per order) and payments (compliance). Show the checkout sync path, its availability, and which steps are async (kitchen, rider assignment, notifications). Mention that you'd start ordering as a modular monolith if the domain were new.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q12-design-the-services-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds-amazon)


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "What are the interaction modes, and how do you choose?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    **Collaboration** (close joint work, for discovery, time-boxed), **X-as-a-Service** (consume an API or platform with little coordination, for well-understood boundaries), **facilitating** (an enabling team coaching). Choose by how well the boundary is understood; expect modes to evolve from collaboration to X-as-a-Service.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q4-what-are-the-interaction-modes-and-how-do-you-choose-l3l4-goldman-sachs-sap)

??? question "How would you size and staff teams for 40 microservices?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · Amazon

    Don't start from 40 services; start from streams of change and domain complexity. Small long-lived teams (up to about 9), each owning one complex domain, or one complicated plus a simple one, or two or three simple ones. Strong single-team ownership; a platform team so service count doesn't multiply extraneous load. If 40 services can't be distributed that way, there are too many services (chapter 6).

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q6-how-would-you-size-and-staff-teams-for-40-microservices-l4-jp-morgan-goldman-sachs-amazon)

??? question "How do you know whether your org structure matches your architecture?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Measure: teams per delivered feature, cross-team pull requests, lock-step releases, wait time for other teams; compare dependencies between components with declared team interactions (socio-technical congruence); survey cognitive load. Look for coordination needs without an agreed channel and channels with no need.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q8-how-do-you-know-whether-your-org-structure-matches-your-architecture-l4-goldman-sachs-flipkart)

??? question "What makes a good platform team?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    It treats the platform as a product for internal users: self-service, documented, with a roadmap and satisfaction metrics; it builds the thinnest viable platform (buy what you can); it's consumed as a service, and collaborates only to discover new features. A platform that needs tickets for routine work is a bottleneck, not a platform.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q9-what-makes-a-good-platform-team-l3l4-amazon-jp-morgan)

??? question "A team owns 12 services and is constantly firefighting. What do you do?"
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    First check whether the 12 services are really separate capabilities or a fragmented one (merge if they always change together, chapter 6). Then check the load: domain count and complexity, operational toil. Options: merge services, move a domain to another or a new team, invest in platform capabilities that remove toil, and add an enabling team for reliability practices. Track the result with on-call load and lead time.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q10-a-team-owns-12-services-and-is-constantly-firefighting-what-do-you-do-l4-flipkart-amazon)

??? question "Who should own the shared database during a migration to microservices?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · SAP

    In the end, nobody: each service owns its data. During migration, assign each table to the team owning the capability that writes it, even while it physically lives in the shared database; other teams read through APIs or views, and DBA expertise moves to an enabling or platform role. That is the first step of database per service.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q11-who-should-own-the-shared-database-during-a-migration-to-microservices-l3l4-jp-morgan-sap)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "How do you make a Spring Boot service start faster in Kubernetes?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Measure first. Then: trim the classpath (modular starters), lazy-initialize only where safe, use the **AOT cache** (Java 25: `-XX:AOTCacheOutput` training run in the Dockerfile, `-XX:AOTCache` at runtime), optionally Spring AOT on the JVM, and GraalVM native images for scale-to-zero. Tune CPU requests: JIT and startup are CPU-hungry. Use startup probes so slow starts don't trigger restarts.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q9-how-do-you-make-a-spring-boot-service-start-faster-in-kubernetes-l3l4-amazon-goldman-sachs)

??? question "How would you migrate 30 services from Boot 3 to Boot 4?"
    <span class="badge badge-l4">L4</span> JP Morgan · SAP · Flipkart

    Upgrade all to the latest 3.5 and clear deprecations; publish a Boot 4 golden-path template; migrate a pilot service with OpenRewrite (`UpgradeSpringBoot_4_0`), the properties migrator and classic starters as a bridge; add contract tests around JSON before switching Jackson defaults; then roll out team by team behind canaries, with a packaged-jar smoke test in CI. Track progress on a dashboard of service versions.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q10-how-would-you-migrate-30-services-from-boot-3-to-boot-4-l4-jp-morgan-sap-flipkart)


### Governance & Organization


**[71. API Governance & Developer Portals](../governance-org/ch71-api-governance.md)**

??? question "Design FoodFleet's API governance for 200 services."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q12-design-foodfleets-api-governance-for-200-services-l4-architecture-rounds)


**[72. Platform Engineering & Golden Paths](../governance-org/ch72-platform-engineering.md)**

??? question "How do you measure a platform team's success?"
    <span class="badge badge-l4">L4</span> Google

    As a product: time to first deploy for a new service, adoption of each golden path, how many update PRs are merged per template release, scorecard trends, DORA metrics of teams on the platform, and developer satisfaction.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q10-how-do-you-measure-a-platform-teams-success-l4-google)

??? question "Design FoodFleet's internal developer platform."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q12-design-foodfleets-internal-developer-platform-l4-architecture-rounds)


**[73. ADRs, Fitness Functions & Tech Radar](../governance-org/ch73-adrs-fitness-functions.md)**

??? question "Why did a latency fitness function miss a fourfold regression?"
    <span class="badge badge-l4">L4</span> Google

    It checked a fixed 20 ms budget on a service whose p99 was 5 ms; quadrupling still fit. Budgets are contracts, not regression checks. Add a ratchet against a recent baseline (+50%, best of three caught every fourfold regression with no false alarms).

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q7-why-did-a-latency-fitness-function-miss-a-fourfold-regression-l4-google)

??? question "What do you do when a fitness function fails but the team wants to ship?"
    <span class="badge badge-l4">L4</span> any architect round

    Fix it, or accept it with a recorded decision (a new or amended ADR) that says why and for how long, visible in the portal. Accepted failures become the new baseline, as release 2's API break did here.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q11-what-do-you-do-when-a-fitness-function-fails-but-the-team-wants-to-ship-l4-any-architect-round)

??? question "Design FoodFleet's architecture governance."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q12-design-foodfleets-architecture-governance-l4-architecture-rounds)


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "How do you plan a monolith-to-microservices migration?"
    <span class="badge badge-l4">L4</span> Thoughtworks · Infosys

    Measure first: contexts mapped to code, import coupling, cycles, history. Make it a modular monolith (no cycles, shared code independent of business code), then extract contexts in order of value per new remote dependency through a strangler facade, re-measuring after each step and stopping when the next step buys little.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q1-how-do-you-plan-a-monolith-to-microservices-migration-l4-thoughtworks-infosys)

??? question "Can every change become independently deployable?"
    <span class="badge badge-l4">L4</span> Google

    Not with a given context map. History gives the ceiling: for Broadleaf, 49% of changes would belong to one service even with all 12 extracted, and 15.4% would need coordinated releases. Raising the ceiling means redrawing boundaries, not extracting more.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q7-can-every-change-become-independently-deployable-l4-google)

??? question "What is a microservices maturity model good for?"
    <span class="badge badge-l4">L4</span> Accenture

    Sequencing capabilities: modular monolith, first extractions, independent services, evolving platform. It's useful only with measured exit gates (cycles, platform edges, coordinated changes); self-assessed questionnaires tell you little.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q9-what-is-a-microservices-maturity-model-good-for-l4-accenture)

??? question "When do you stop extracting?"
    <span class="badge badge-l4">L4</span> any architect round

    When the next extraction buys little independent change for its coordination cost. On Broadleaf, the last six extractions of the value-per-edge order added 3.6% of independent changes for 331 more remote edges.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q10-when-do-you-stop-extracting-l4-any-architect-round)

??? question "Plan the migration of a 2,800-file commerce monolith."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q12-plan-the-migration-of-a-2800-file-commerce-monolith-l4-architecture-rounds)


**[75. Multi-Tenancy, Multi-Region & Compliance](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)**

??? question "How do you implement data residency in a multi-region system?"
    <span class="badge badge-l4">L4</span> Amazon

    A home region per tenant (or user) in a registry and in tokens; the gateway routes there; personal data is written only there; replication carries reference data and aggregates; DR stays within the jurisdiction.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q7-how-do-you-implement-data-residency-in-a-multi-region-system-l4-amazon)

??? question "How do you honour the right to erasure with backups?"
    <span class="badge badge-l4">L4</span> Goldman Sachs

    Crypto-shredding: per-subject keys, erasure by deleting the key, which makes every copy of the ciphertext unreadable. Measured: after DELETE and VACUUM FULL the email survived in WAL and backup; encrypted, it never reached disk.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q8-how-do-you-honour-the-right-to-erasure-with-backups-l4-goldman-sachs)

??? question "What does crypto-shredding not solve?"
    <span class="badge badge-l4">L4</span> JP Morgan

    The key store becomes the copy to worry about: the deleted key was still in its WAL. Use a KMS or a key store with short retention. It also makes value search harder (blind indexes) and puts the KMS on the read path.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q9-what-does-crypto-shredding-not-solve-l4-jp-morgan)

??? question "Design FoodFleet's multi-tenant, multi-region chain platform."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q12-design-foodfleets-multi-tenant-multi-region-chain-platform-l4-architecture-rounds)


**[76. Serverless & Function-Based Services](../governance-org/ch76-serverless-microservices.md)**

??? question "What is the cost crossover between functions and containers?"
    <span class="badge badge-l4">L4</span> Uber

    Where requests × (duration × memory × GB-second price + request fee) exceeds the cost of the smallest highly available container deployment. With measured times: about 106 light or 28–31 heavy requests a second against two containers.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q6-what-is-the-cost-crossover-between-functions-and-containers-l4-uber)

??? question "What does provisioned concurrency do to the economics?"
    <span class="badge badge-l4">L4</span> Netflix

    It keeps instances initialised, removing cold starts, and is billed whether used or not, which turns a function back into always-on capacity. Use it where the latency objective needs it.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q11-what-does-provisioned-concurrency-do-to-the-economics-l4-netflix)

??? question "Decide FoodFleet's serverless strategy."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q12-decide-foodfleets-serverless-strategy-l4-architecture-rounds)


**[77. AI-Native Microservices](../governance-org/ch77-ai-native-microservices.md)**

??? question "Should you use semantic caching for LLM responses?"
    <span class="badge badge-l4">L4</span> Google

    Carefully. Exact and normalised caching captured 97% reuse with no wrong answers; similarity added 1.4 points and, at cosine 0.70 and below, answered vegetarian questions with non-vegetarian answers. Use it only where near-duplicates may share answers, after testing near-miss pairs.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q5-should-you-use-semantic-caching-for-llm-responses-l4-google)

??? question "How do you secure an MCP server?"
    <span class="badge badge-l4">L4</span> Goldman Sachs

    As an API: OAuth with audience-checked tokens, identity from the token, object-level authorization in every tool, header and body validation, audit logs, and injection tests. A tool trusting a `customer_id` argument returned another customer's order 30 times out of 30.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q8-how-do-you-secure-an-mcp-server-l4-goldman-sachs)

??? question "How do you control the cost of LLM features?"
    <span class="badge badge-l4">L4</span> Netflix

    Token and cost accounting per feature and tenant in the gateway, budgets with alerts, caching of repeats, smaller models for simple tasks, output limits, and batch work on cheaper capacity. Exact caching alone avoided 93% of calls on FAQ-like traffic.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q11-how-do-you-control-the-cost-of-llm-features-l4-netflix)

??? question "Design FoodFleet's AI platform."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q12-design-foodfleets-ai-platform-l4-architecture-rounds)


### Observability


**[47. Observability Fundamentals](../observability/ch47-observability-fundamentals.md)**

??? question "Design FoodFleet's observability."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    OTLP from every service to a gateway collector tier; RED metrics per service, route and pod derived from 100% of spans; tail sampling keeping errors, slow traces and a baseline; structured logs and wide events with trace IDs; change events from the deploy pipeline; continuous JFR profiles; a cardinality budget and cost per signal per team; SLO alerts on metrics (chapter 51).

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q12-design-foodfleets-observability-l4-swiggy-zomato-style-system-design-rounds)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "How do you keep metric cardinality under control?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Atlassian · Amazon

    Tag values from enums and templates; `MeterFilter.ignoreTags` for identifiers; caps as a backstop with alerts; a series budget per service checked in CI; recording rules that aggregate away pod-level detail; per-entity questions in traces and events.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q11-how-do-you-keep-metric-cardinality-under-control-l3l4-atlassian-amazon)

??? question "Design FoodFleet's metrics."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Micrometer with Spring Boot's Observations for HTTP and business operations; histograms with SLO boundaries; a shared policy of filters and caps; business meters in binders; Prometheus with RED, USE and business recording rules; dashboards and SLO alerts (chapter 51) on those rules; a series budget per service; identifiers in traces and wide events, never in tags.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q12-design-foodfleets-metrics-l4-swiggy-zomato-style-system-design-rounds)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "Parent or link for a consumer span?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Uber · Zomato

    Parent when the message is handled promptly, one by one, as part of the same user action; a new trace with links when messages are batched, delayed for a long time, or many producers feed one consumer operation. OpenTelemetry's instrumentation calls the latter "receive telemetry".

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q4-parent-or-link-for-a-consumer-span-l3l4-uber-zomato)

??? question "Should an internet-facing service trust an incoming traceparent?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Cloudflare-style rounds

    No. Parent-based sampling honours a sampled flag from anyone, so clients can force sampling (and costs), choose trace IDs and inject baggage. Strip trace headers at the edge and start new traces there; link to a partner's trace ID if you need it.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q8-should-an-internet-facing-service-trust-an-incoming-traceparent-l3l4-cloudflare-style-rounds)

??? question "Design tracing for FoodFleet's order journey."
    <span class="badge badge-l4">L4</span> system design rounds

    W3C propagation everywhere; edge strips internet trace headers; parent-based sampling with 100% sent to a tail-sampling collector; instrumented HTTP and Kafka with observations enabled; context carried through executors, outbox rows and pull consumers; links for batch and delayed work; baggage allow-listed and kept internal; manual spans for fraud, pricing and dispatch; a synthetic end-to-end trace check in staging.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q12-design-tracing-for-foodfleets-order-journey-l4-system-design-rounds)


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "Design FoodFleet's logging."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q12-design-foodfleets-logging-l4-system-design-rounds)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "Why multi-window, multi-burn-rate alerts?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google-style rounds

    The long window makes an alert significant, and the short window makes it stop when the problem does. Several burn rates separate "page now" from "ticket". Measured: they paged fast incidents within 1–43 minutes with at most 2.3% of the budget gone, ticketed slow ones at 8%, and stopped 4–29 minutes after recovery. A 1-hour-only alert held on for up to 59 minutes.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q4-why-multi-window-multi-burn-rate-alerts-l3l4-google-style-rounds)

??? question "What goes in an error budget policy?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Atlassian

    Who owns the SLO, and the thresholds that change behaviour: for example caution at half spent (rollback plans, extra review) and a freeze at fully spent (only fixes and reliability work). It also covers who can grant exceptions, how incidents that spend more than a set share get a postmortem, and that product signs it.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q10-what-goes-in-an-error-budget-policy-l4-goldman-sachs-atlassian)

??? question "How do SLOs work with dependencies?"
    <span class="badge badge-l4">L4</span> system design rounds

    A service's availability is bounded by its hard dependencies: three at 99.9% in series give about 99.7%. So set dependency SLOs tighter than the user-facing one, remove hard dependencies with fallbacks and caches (Part 6), and measure SLIs at the user-facing edge, where the product of all of them shows.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q11-how-do-slos-work-with-dependencies-l4-system-design-rounds)

??? question "Design SLOs and alerting for FoodFleet."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q12-design-slos-and-alerting-for-foodfleet-l4-system-design-rounds)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "Design health checking for FoodFleet."
    <span class="badge badge-l4">L4</span> system design rounds

    

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q12-design-health-checking-for-foodfleet-l4-system-design-rounds)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "Design order-service's quote for 5× traffic."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q12-design-order-services-quote-for-5-traffic-l4-architecture-rounds)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "Design FoodFleet's JVM settings for 200 services."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q12-design-foodfleets-jvm-settings-for-200-services-l4-architecture-rounds)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "Rendezvous or jump hashing, and when?"
    <span class="badge badge-l4">L4</span> Google

    Both move the minimum and spread almost perfectly (1.01× and 1.02× measured). Rendezvous works with named nodes that come and go but costs O(n) per lookup; jump needs no state and is fast but only adds or removes the last bucket, so it suits numbered shards.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q7-rendezvous-or-jump-hashing-and-when-l4-google)

??? question "What's the Universal Scalability Law?"
    <span class="badge badge-l4">L4</span> architecture rounds

    Gunther's model of throughput with N copies: contention (serialisation on something shared) bends the curve toward a ceiling, and coherency (copies coordinating with each other) makes it fall. The ledger here is pure contention: throughput rose to 50 a second and stayed there.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q11-whats-the-universal-scalability-law-l4-architecture-rounds)

??? question "Design order-service to scale 10× for a sale."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q12-design-order-service-to-scale-10-for-a-sale-l4-architecture-rounds)


**[69. High-Throughput Kafka Tuning](../performance-scalability/ch69-kafka-throughput-tuning.md)**

??? question "FoodFleet's `orders` topic must carry 10× today's traffic. What do you change?"
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q12-foodfleets-orders-topic-must-carry-10-todays-traffic-what-do-you-change-l4-architecture-rounds)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "Halve FoodFleet's compute bill without hurting SLOs."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q12-halve-foodfleets-compute-bill-without-hurting-slos-l4-architecture-rounds)


### Resilience


**[42. Failure Modes & Cascading Failures](../resilience/ch42-failure-modes.md)**

??? question "What is a metastable failure?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google-/Amazon-style rounds · Flipkart

    A failure kept alive by the system's own reactions after the trigger is gone. A brief slowdown grows queues; requests time out and are retried; the extra load keeps queues long, so every request times out. Measured: 0% of users served for 30 s after a 5-second trigger. Breaking the loop takes shedding stale work, budgets and deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q4-what-is-a-metastable-failure-l3l4-google-amazon-style-rounds-flipkart)

??? question "Design FoodFleet to recover from a five-minute dependency slowdown."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Deadlines on every hop; retries only at the edge or within per-dependency budgets with full jitter; explicit concurrency limits that reject; queue-age shedding below callers' timeouts; circuit breakers on the slow dependency with a degraded path; P2C balancing; ≥ 20% headroom; dashboards for goodput and queue time; and a load test that injects the slowdown and checks goodput returns when it ends.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q12-design-foodfleet-to-recover-from-a-five-minute-dependency-slowdown-l4-swiggy-zomato-style-system-design-rounds)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "How do you size a bulkhead?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Little's law: concurrent calls = throughput × latency. 400 calls/s at 50 ms needs 20; add headroom for the latency you accept, not the latency of a broken dependency. Reject when full (wait 0). Measured: a bulkhead of 32 held a slow dependency to 32 calls in progress where an unbounded caller reached 146–166.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q11-how-do-you-size-a-bulkhead-l3l4-amazon-flipkart)

??? question "Design resilience for FoodFleet's order-service calls."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Classify dependencies: menus (fallback to a stale cache), payments (no fallback, idempotent retries only), recommendations (empty fallback). Per dependency: HTTP connect/read timeouts; a time-window breaker with a slow-call threshold and a short open state; a Little's-law bulkhead that rejects; retries at one layer on transient failures with full jitter, ignoring refusals; fallback on the outermost layer; metrics and alerts on transitions; the policy as a shared library tested against the real library on each upgrade.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q12-design-resilience-for-foodfleets-order-service-calls-l4-swiggy-zomato-style-system-design-rounds)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "What is a hedged request, and when is it dangerous?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google · Amazon

    A duplicate request sent when the first hasn't answered by about the p95; the first answer wins. It cut p99 from the full 1 s budget to 88 ms for 10% more calls with spare capacity (measured). Near capacity, hedges make calls slower, which triggers more hedges: at 85% load only 18–43% of users were served. Budget them (5% kept 99%) and use them only for idempotent reads.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q3-what-is-a-hedged-request-and-when-is-it-dangerous-l3l4-google-amazon)

??? question "What is adaptive concurrency limiting?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Netflix-style rounds · Uber

    A limit on in-flight requests that moves with latency, like TCP congestion control: AIMD adds one while healthy and cuts 10% when a request is dropped or too slow; Vegas and Gradient infer queueing from latency trends. Requests over the limit get an immediate 503 instead of joining a queue.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q5-what-is-adaptive-concurrency-limiting-l3l4-netflix-style-rounds-uber)

??? question "Which algorithm would you choose?"
    <span class="badge badge-l4">L4</span> Netflix-style rounds · SAP

    The one whose signal you can define. In measurements, Vegas's defaults rejected half the traffic of a healthy service with variable latency, and Gradient2 depended on its floor; AIMD with "slow" set to half the callers' deadline rejected nothing when healthy and served 53–54% of a possible 57% in a slowdown. Test any of them on your own latency distribution.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q6-which-algorithm-would-you-choose-l4-netflix-style-rounds-sap)

??? question "Design FoodFleet's order-service for a 2× lunch spike."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Deadlines from the edge; per-hop timeouts from the remaining budget; admission control first in the chain (AIMD, partitioned by priority, 503 + `Retry-After`); recommendations behind a dimmer and limiting themselves; hedged menu reads within a 5% budget; retries at one layer within budgets; bounded buffers on event consumers; dashboards by priority; and a load test at 2× that passes on checkout goodput and recovery.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q12-design-foodfleets-order-service-for-a-2-lunch-spike-l4-swiggy-zomato-style-system-design-rounds)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "Why did a circuit breaker fail a chaos experiment?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Walmart

    A breaker counts outcomes, and a call with no read timeout into a black hole never has one. Measured: no opening in 20 s, about 2,000 calls stuck, users failing in 19 of 20 windows; with a 250 ms read timeout the same experiment held. Timeouts are what feed a breaker.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q7-why-did-a-circuit-breaker-fail-a-chaos-experiment-l3l4-goldman-sachs-walmart)

??? question "What is fault fidelity?"
    <span class="badge badge-l4">L4</span> Netflix-style rounds · Atlassian

    How faithfully the injected fault reproduces the real failure. A black hole on responses let the server's connection closes through and made a broken configuration nearly pass; one on requests, like a real partition, exposed it. Check that the fault behaves like the incident you fear.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q8-what-is-fault-fidelity-l4-netflix-style-rounds-atlassian)

??? question "Should you run chaos in production?"
    <span class="badge badge-l4">L4</span> Amazon · Netflix-style rounds

    Eventually, for experiments that have held in staging, because only production has real traffic, data and dependencies, but with a small blast radius, an automatic guard, business hours, on-call present and a freeze calendar. Production chaos without those is just an outage you scheduled.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q10-should-you-run-chaos-in-production-l4-amazon-netflix-style-rounds)

??? question "Design a chaos programme for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Define each critical journey's SLO and a per-second probe; build a catalogue of hypotheses per journey (slow and black-holed dependencies, pod and zone loss, a slow database); run them in CI with Toxiproxy and in staging as Chaos Mesh workflows with SLO guards, one instance at a time; validate manifests strictly; schedule the experiments that found something weekly; quarterly game days; graduate held experiments to production with on-call present.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q12-design-a-chaos-programme-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


**[46. Disaster Recovery & Multi-Region](../resilience/ch46-disaster-recovery.md)**

??? question "Why is asynchronous replication dangerous during a partition?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    The primary keeps accepting commits it can't replicate, so the loss grows with the partition's length instead of being milliseconds. Measured: 2 s of partition lost every commit in those 2 s. Promoting the standby without fencing the old primary then creates two histories.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q4-why-is-asynchronous-replication-dangerous-during-a-partition-l4-amazon-goldman-sachs)

??? question "What is fencing?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Atlassian · Microsoft-style rounds

    Making sure the old primary can no longer accept writes before promoting a new one: power it off, cut its network, revoke its storage or credentials, withdraw it from DNS and load balancers. It turns "two primaries" into "one primary and one stopped server".

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q5-what-is-fencing-l3l4-atlassian-microsoft-style-rounds)

??? question "Active-active or active-passive?"
    <span class="badge badge-l4">L4</span> Uber · Zomato

    Active-passive is simpler and loses at most the replication lag; active-active removes regional failover time but needs every write's conflicts designed away: home regions per tenant, conflict-free data types, or idempotent merges. Most systems mix them: carts or presence active-active, orders and payments with one home region.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q10-active-active-or-active-passive-l4-uber-zomato)

??? question "What is a cell-based architecture?"
    <span class="badge badge-l4">L4</span> Amazon · Atlassian

    Many complete, independent copies of a service, each serving a slice of tenants, with a thin router in front. A failure, a bad deploy or a poison tenant reaches one cell. Measured with 8 instances and 10,000 tenants: a poison tenant took down 100% in a shared pool, 12.7% with 8 cells, and 1.7–3.6% with shuffle sharding (each tenant on 3 or 2 of the 8).

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q11-what-is-a-cell-based-architecture-l4-amazon-atlassian)

??? question "Design disaster recovery for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Tier the services; Tier 0 with quorum-synchronous standbys in other zones and automatic, fenced zone failover; an asynchronous standby and streamed WAL in a second region, promoted by runbook after fencing; multi-host clients with bounded timeouts; per-region ID ranges; cells by city; monitored lag and archive age; monthly timed rehearsals of failover and point-in-time restore.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q12-design-disaster-recovery-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


### Security


**[35. Security Fundamentals & Threat Modeling](../security/ch35-security-fundamentals.md)**

??? question "Where should authorization happen in a microservice system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP

    The gateway authenticates and applies coarse rules; the service that owns the data makes object, property and function decisions, because only it knows who owns what. Services re-verify tokens (zero trust); the database restricts by credentials and, optionally, row-level security as defence in depth.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q8-where-should-authorization-happen-in-a-microservice-system-l3l4-flipkart-sap)

??? question "How do you keep a threat model up to date?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Keep it as code or structured data next to the system, change it in the same pull requests as the architecture, and check it in the build: no open threats, acceptances with owners and expiry dates, mitigations linked to tests or configuration. Feed incidents and pen-test findings back into it.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q9-how-do-you-keep-a-threat-model-up-to-date-l4-jp-morgan-goldman-sachs)

??? question "How do you test authorization thoroughly?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Beyond example tests, generate the full matrix of roles × actions × object states and compare the policy against rules written separately, in the business's words. Measured here: 18,600 decisions, 0 disagreements. Include awkward identities (one person with two roles) and every state.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q11-how-do-you-test-authorization-thoroughly-l3l4-goldman-sachs-sap)

??? question "Threat-model a food-delivery checkout."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    DFD with customer app, restaurant tablet, gateway, order and payment services, their stores, the event topic, the payment provider and restaurant webhooks, across internet, edge, services, data and partner zones. Key threats: BOLA on orders, tampering with amounts (never trust client prices), payment callback spoofing (verify signatures), SSRF through webhooks, card data exposure (provider tokens only), denial of service on the gateway and provider (rate limits, circuit breakers, outbox retries), repudiation of refunds (audit log with caller). Record mitigations and acceptances; check them in the build.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q12-threat-model-a-food-delivery-checkout-l4-swiggy-zomato-style-system-design-rounds)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "JWT or opaque tokens?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · JP Morgan

    JWTs verify locally (measured ~0.05–0.6 ms, no network) and keep working when the issuer is down, but can't be revoked before expiry (a revoked 15-minute token works 450 s on average). Opaque tokens are introspected (a network call per request or cache miss) and revocable immediately. Use short JWTs broadly and introspection where revocation must be immediate.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q5-jwt-or-opaque-tokens-l3l4-flipkart-jp-morgan)

??? question "Token relay or token exchange?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Relay passes the user's token on, so every downstream service and log holds a credential for everything the user's app may do. Exchange (RFC 8693) gets a new token for one audience, minimum scopes, minutes, with an `act` claim recording the acting service. Relay is acceptable only within one trust boundary with audience-restricted tokens.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q8-token-relay-or-token-exchange-l3l4-goldman-sachs-flipkart)

??? question "Design authentication and authorization for a food-delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    One identity provider (Keycloak) for customers, restaurant staff, riders and staff tools; authorization code + PKCE in apps, BFF for web; JWT access tokens with audience per API, 15 minutes, rotating refresh tokens; client credentials for batch jobs; token exchange for service calls on a user's behalf; introspection for payments and admin; staged key rotation; object-level authorization in each service (chapter 35).

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q12-design-authentication-and-authorization-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds)


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "What defaults of Spring's authorization server would you change?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Access tokens addressed to the client (set audiences per API), refresh tokens reused (rotate them for public clients), no `typ: at+jwt` (add it), and token exchange scopes checked only against the client's registration (register minimal scopes). Read from the 7.1.1 sources in this chapter.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q7-what-defaults-of-springs-authorization-server-would-you-change-l3l4-goldman-sachs-jp-morgan)

??? question "Spring Authorization Server or Keycloak?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Keycloak (or a managed provider) when you need user management, federation, MFA, admin UI and many applications. Spring's authorization server when you need an embedded, code-configured provider with custom behaviour, or for local development and tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q10-spring-authorization-server-or-keycloak-l3l4-amazon-sap)

??? question "Design security for FoodFleet's services with Spring."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Keycloak realm as code (roles, PKCE clients, audience mappers, token exchange); every service a resource server with a shared converter, scope and role URL rules, `denyAll()` last, method security per object, stateless; BFFs with `oauth2Login` and CSRF for web; security MockMvc tests in every pipeline; passwords only in Keycloak.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q12-design-security-for-foodfleets-services-with-spring-l4-swiggy-zomato-style-system-design-rounds)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "A workload's certificate expired. Can it still call you?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Possibly. Certificates are checked at the handshake; a kept connection carries on, and a resumed TLS session carries the original certificate without calling the trust manager. On JDK 21 a client kept resuming past expiry (19 of 19 accepted). Check validity per request and cap connection age.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q5-a-workloads-certificate-expired-can-it-still-call-you-l4-goldman-sachs-jp-morgan)

??? question "Service mesh or application-level mTLS?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    A mesh gives mTLS and coarse policies everywhere without code changes, but the application sees no client certificate (certificate-bound tokens and per-request certificate checks don't work there) and Istio's defaults are permissive. Application-terminated mTLS gives the service the peer identity for fine-grained decisions and audit. Many platforms use the mesh broadly and application checks for sensitive services.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q7-service-mesh-or-application-level-mtls-l3l4-flipkart-amazon)

??? question "Design service-to-service security for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    SPIRE issues one-hour SVIDs by attestation; Spring Boot SSL bundles with reload, mutual TLS required; a peer-policy filter (SVID rules and validity per request, deny by default per route) before token checks; client credentials per service for sagas, token exchange for user calls, certificate-bound tokens for payments; Istio STRICT with allow-nothing where the mesh runs; rotation on both sides, CA rollover with overlap, connection age capped; peer identity in every audit log.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q12-design-service-to-service-security-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "Your Vault-backed service fails a day after deployment. Why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Dynamic credentials renewed but never rotated: renewal stops at max_ttl. Spring Cloud Vault 5.0.2 requests database credentials as renewable secrets. Request a rotating secret and update the pool (HikariCP 7's credentials provider), or set max_ttl longer than any pod lives. Measured: renew-only failed exactly at max_ttl.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q6-your-vault-backed-service-fails-a-day-after-deployment-why-l3l4-sap-goldman-sachs)

??? question "You rotated a leaked password. Is the attacker out?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Not necessarily. PostgreSQL (like most databases) checks credentials at login; open sessions continue. Measured: after a password change, expiry or `NOLOGIN`, the open session still worked. Terminate sessions (`pg_terminate_backend`) as part of revocation, and use per-pod credentials so you know which to terminate.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q7-you-rotated-a-leaked-password-is-the-attacker-out-l4-jp-morgan-goldman-sachs)

??? question "Design secrets management for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    OpenBao, HA and audited; Kubernetes auth with a role and policy per service; dynamic per-pod database credentials that renew and rotate, revocation that terminates sessions; third-party keys in KV v2, synced by ESO to Secrets mounted read-only and rotated with two versions; KMS v2 encryption at rest; no secrets in env vars, logs or dumps; an inventory with owners and ages; `revoke-prefix` in the incident runbook.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q12-design-secrets-management-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "One tenant is hurting all the others. What do you do?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Swiggy

    Limits keyed by tenant (and client within a tenant) in front of the global one, then per-tenant resource limits further down (connection pools, queues, database statement timeouts). Measured: with only a global limit the other tenants got 0.2% of their requests; with per-tenant limits, 100%.

    [Full answer in chapter 40](../security/ch40-api-security.md#q4-one-tenant-is-hurting-all-the-others-what-do-you-do-l3l4-goldman-sachs-swiggy)

??? question "How do you isolate tenants in a shared database?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Tenant from the token, never from the request; PostgreSQL row-level security on every shared table, `FORCE`d, with an application role that doesn't own the tables; the tenant set transaction-locally (`set_config(.., true)`) so pooled connections don't carry it. Measured: a forgotten predicate returned 40 rows instead of 2,000; a session-level `SET` leaked 4,000 rows to the next request. Silo databases for tenants that need them.

    [Full answer in chapter 40](../security/ch40-api-security.md#q11-how-do-you-isolate-tenants-in-a-shared-database-l3l4-sap-goldman-sachs)

??? question "Harden FoodFleet's public API."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Edge: CDN with bot management and per-address limits; WAF with the CRS tuned for JSON. Service: two chains (bearer API without CSRF and with exact CORS; cookie BFF with Fetch Metadata and CSRF tokens); GCRA limits per client and tenant in Redis with a bounded fallback; typed DTOs, `FreeText`, body and parser limits. Data: row-level security per tenant, object checks in every query. Operations: false-positive tracking, limit dashboards per tenant, written fail modes.

    [Full answer in chapter 40](../security/ch40-api-security.md#q12-harden-foodfleets-public-api-l4-swiggy-zomato-style-system-design-rounds)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "Keyless signing: how does it work, and why use it?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    The CI job's OIDC token is exchanged with Fulcio for a ten-minute certificate naming the workflow; the signature is logged in Rekor and/or timestamped; verifiers check the chain, the log/timestamp and the identity. No long-lived key to leak or rotate. syft's certificate was valid for ten minutes and verified a week later from the timestamp (measured).

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q6-keyless-signing-how-does-it-work-and-why-use-it-l3l4-goldman-sachs-sap)

??? question "What is SLSA, and what level should FoodFleet aim for?"
    <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    A framework for build integrity. Build L1: provenance exists; L2: generated and signed by a hosted builder; L3: a hardened builder where the build can't forge its own provenance. GitHub Actions with `attest-build-provenance` and reusable workflows gets close to L3; admission checks the provenance's builder and source.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q7-what-is-slsa-and-what-level-should-foodfleet-aim-for-l4-amazon-jp-morgan)

??? question "How do you comply with the RBI's payment data storage rules in a cloud architecture?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Paytm-/Razorpay-style rounds · JP Morgan

    Payment system data (including customer details and transaction data) stored only in India; if processed abroad, deleted there and brought back within one business day or 24 hours, whichever is earlier. Architecturally: classify services, run payment data stores and their backups, logs and analytics in Indian regions, and enforce it (FoodFleet's policy refuses `data-class: payment` outside Indian regions or the CDE). A CERT-In empanelled auditor's system audit report covers it.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q10-how-do-you-comply-with-the-rbis-payment-data-storage-rules-in-a-cloud-architecture-l3l4-paytm-razorpay-style-rounds-jp-morgan)

??? question "How do you honour a deletion request when data is in Kafka and backups?"
    <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Crypto-shredding: encrypt personal fields with a per-customer key before they leave the service, keep wrapped keys in a separate table backed by a KMS, and erase by deleting the key. Measured: 0 of 9,000 erased fields readable in the table, the topic or the backup; 8–30 µs per field. DPDP and GDPR both expect erasure across processors too, so the same applies to vendors.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q11-how-do-you-honour-a-deletion-request-when-data-is-in-kafka-and-backups-l4-sap-goldman-sachs)

??? question "Design supply-chain security and compliance for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Build: pinned actions, least-privilege tokens, repository manager, CycloneDX SBOM in every jar, CI gate with expiring waivers. Release: keyless signing of digests, SBOM and SLSA provenance attestations. Deploy: Kyverno enforcing identity, provenance, digests; OPA rules for data class, region (RBI), PCI namespace, DPDP retention. Operate: an inventory rescanning deployed SBOMs daily; crypto-shredding for erasure; incident runbooks with the 6/72-hour clocks; evidence per release for audits.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q12-design-supply-chain-security-and-compliance-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "Design FoodFleet's test strategy."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q12-design-foodfleets-test-strategy-l4-architecture-rounds)


**[62. Unit, Slice & Testcontainers Tests](../testing/ch62-unit-slice-integration-tests.md)**

??? question "Design FoodFleet's database testing setup."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q12-design-foodfleets-database-testing-setup-l4-architecture-rounds)


**[63. Contract Testing](../testing/ch63-contract-testing.md)**

??? question "Design FoodFleet's contract-testing setup."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q12-design-foodfleets-contract-testing-setup-l4-architecture-rounds)


**[64. Component & End-to-End Testing](../testing/ch64-component-e2e-testing.md)**

??? question "Design FoodFleet's E2E setup."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q12-design-foodfleets-e2e-setup-l4-architecture-rounds)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "Design FoodFleet's quality testing."
    <span class="badge badge-l4">L4</span> architecture rounds

    

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q12-design-foodfleets-quality-testing-l4-architecture-rounds)
