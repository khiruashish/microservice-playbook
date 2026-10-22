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

??? question "How would you add a new payment provider?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A new driven adapter implementing `PaymentGateway`: an HTTP client in the provider's vocabulary, a translator with tests for every status and error code (unknown means "not approved"), idempotency keys per call, error mapping to the port's exceptions. Run it against the gateway contract test and the provider's sandbox, then route a small percentage of traffic to it by configuration.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q11-how-would-you-add-a-new-payment-provider-l3l4-goldman-sachs-flipkart)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "API gateway vs load balancer vs service mesh?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A load balancer distributes connections (L4/L7) across instances. An API gateway is an application-level edge for **north–south** traffic with API policies (auth, quotas, routing by path and version). A service mesh handles **east–west** traffic between services (mTLS, retries, telemetry) through sidecars or ambient proxies. They are complementary.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q2-api-gateway-vs-load-balancer-vs-service-mesh-l2l3-amazon-flipkart)

??? question "What is the BFF pattern and when would you use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Swiggy-/Zomato-style product companies

    A backend built for one client experience and owned by that client's team, exposing screen-shaped endpoints that aggregate and format data from domain services. Use it when clients differ significantly or when chatty client-to-service calls hurt latency and coupling. It must not hold domain rules.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q3-what-is-the-bff-pattern-and-when-would-you-use-it-l2l3-flipkart-swiggy-zomato-style-product-companies)

??? question "How do you implement rate limiting, and what are the pitfalls?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs · Flipkart

    Token bucket per key (user, API key or IP): capacity for bursts, steady refill, 429 with `Retry-After` when empty. Pitfalls: in-memory limits are per instance (use Redis or a distributed bucket for exact quotas); keying by IP punishes users behind carrier NAT; unbounded per-key state is a memory attack; limits must exist before you need them.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q5-how-do-you-implement-rate-limiting-and-what-are-the-pitfalls-l3-amazon-goldman-sachs-flipkart)

??? question "What happens when the gateway goes down?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Everything public goes down, which is why it must be stateless, horizontally scaled across zones, protected by disruption budgets, deployed with canaries, and kept simple (no business logic, few dependencies). Internal traffic must not route through it, so services keep working with each other during an edge incident.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q8-what-happens-when-the-gateway-goes-down-l3l4-amazon-flipkart)

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

??? question "Why does gRPC load-balance badly behind a Kubernetes Service?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    kube-proxy balances connections, and gRPC multiplexes all requests over one long-lived HTTP/2 connection, so each client sticks to one pod. Use a headless Service with client-side round robin, a mesh that balances per request, or limit connection age.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q7-why-does-grpc-load-balance-badly-behind-a-kubernetes-service-l3-flipkart-amazon)

??? question "How do you avoid errors during rolling deployments?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Readiness gates new pods until warm; on termination, a `preStop` pause lets endpoint removal propagate while the pod still serves, then graceful shutdown finishes in-flight requests; `maxUnavailable` and PodDisruptionBudgets keep enough capacity; callers retry idempotent requests elsewhere.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q10-how-do-you-avoid-errors-during-rolling-deployments-l3-flipkart-goldman-sachs)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

??? question "Why is a shared database an anti-pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    The schema becomes an unversioned contract for every service that touches it: renames break others, locks and heavy queries interfere, nobody owns the rules for the data, and services can't be deployed or scaled independently. It's the tightest coupling a system can have.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q2-why-is-a-shared-database-an-anti-pattern-l2l3-amazon-flipkart)

??? question "How do you join data across services?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    API composition for display queries: the owner of the list is queried first, then other owners are called in batch, in parallel, and joined in memory. For queries that filter or sort across owners or are heavy, build a CQRS read model from events. For analytics, a warehouse fed by events or CDC.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q3-how-do-you-join-data-across-services-l2l3-flipkart-sap)

??? question "How would you split a shared database in a running system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP · Flipkart

    Map table ownership; audit real statements per service (one DB user each); remove foreign writes first, then foreign reads (APIs, local copies, composition); drop cross-domain foreign keys; move tables into per-service schemas with grants; separate instances where needed. Each step is small and measurable.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q6-how-would-you-split-a-shared-database-in-a-running-system-l3l4-goldman-sachs-sap-flipkart)

??? question "What are the problems with API composition?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Latency and availability (every source is on the path), N+1 calls without batch endpoints, no efficient filtering or sorting across sources, and inconsistent snapshots between sources. Mitigate with batching, parallel calls, budgets, partial results and caching; switch to CQRS when queries cross owners.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q7-what-are-the-problems-with-api-composition-l3-amazon-flipkart)

??? question "Is it OK for a service to keep a copy of another service's data?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Yes, if the copy is read-only and its freshness is deliberate: a snapshot at a business moment (the price when ordered) or a replica kept current from the owner's events. Two services writing the same data is the problem, not copies.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q9-is-it-ok-for-a-service-to-keep-a-copy-of-another-services-data-l3-flipkart-goldman-sachs)

??? question "A product page needs data from six services. Design it."
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    A BFF composes the page: the catalog item as driver, then pricing, inventory, ratings and delivery promises in parallel with per-source budgets and fallbacks (hide ratings rather than fail the page), plus caching for slow-changing parts. Listings and search come from an index built from events. Name the latency budget and which parts are critical.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q11-a-product-page-needs-data-from-six-services-design-it-l4-flipkart-amazon)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "What is CQRS?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Separating the model that handles commands (enforcing rules on writes) from the model(s) that answer queries. Read models are shaped per query and can live in different stores, kept up to date from the write side, usually through events and therefore eventually consistent.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q1-what-is-cqrs-l2l3-cognizant-sap-flipkart)

??? question "How do read models stay up to date, and what about consistency?"
    <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    Projections subscribe to the event log from a checkpoint and update read models; checkpoint and read model are updated atomically, or handlers are idempotent. Read models are eventually consistent; measure lag, and for the user's own latest change return the command result or read from the write side.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q5-how-do-read-models-stay-up-to-date-and-what-about-consistency-l3-flipkart-jp-morgan)

??? question "When would you not use event sourcing?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    For CRUD-like data where nobody needs history, for teams without experience and without a clear benefit, and where simple audit tables suffice. It's a tool for ledgers, workflows and domains with disputes and many read views.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q9-when-would-you-not-use-event-sourcing-l3l4-flipkart-amazon)

??? question "Can Kafka be your event store?"
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    It's an excellent event log for integration and stream processing, but lacks per-aggregate optimistic concurrency, efficient per-stream reads and unlimited retention guarantees by default. Most systems use a proper event store (or a database table) for aggregates and Kafka for publishing integration events.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q10-can-kafka-be-your-event-store-l4-flipkart-amazon)

??? question "How do you rebuild or add a read model?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Reset the read model and its checkpoint, replay the log from the start. For large logs, build the new version alongside the old one (blue/green), switch reads when it has caught up, then drop the old one.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q11-how-do-you-rebuild-or-add-a-read-model-l3-goldman-sachs-flipkart)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

??? question "What is a saga?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A sequence of local transactions across services, where each step has a compensating action. If a step fails, the compensations of completed steps run in reverse order. It provides eventual atomicity without distributed locks.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q1-what-is-a-saga-l2l3-cognizant-sap-flipkart)

??? question "Orchestration vs choreography?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · SAP

    Orchestration: a central coordinator tells participants what to do and tracks state; easier to understand, monitor and change. Choreography: services react to each other's events; looser coupling, but the flow is implicit and compensation is spread out. Orchestrate flows with many steps or money; choreograph simple reactions.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q3-orchestration-vs-choreography-l3-amazon-flipkart-sap)

??? question "What happens if the orchestrator crashes mid-saga?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Its progress is in a durable saga log; another instance finds unfinished sagas and resumes from the last recorded step, re-sending the in-flight request (at-least-once). Idempotent participants make the repeat harmless. The log write and outgoing message use an outbox so they can't diverge.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q7-what-happens-if-the-orchestrator-crashes-mid-saga-l3-amazon-flipkart)

??? question "Would you use a workflow engine like Temporal?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · startups

    Yes for long-running flows with timers, human steps and many branches: durable execution removes most hand-written state handling. For a short, latency-sensitive saga a small orchestrator with a saga log may be simpler. Either way participants must still be idempotent.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q10-would-you-use-a-workflow-engine-like-temporal-l3l4-flipkart-startups)


**[15. Transactional Outbox, Inbox & CDC](../architecture-patterns/ch15-outbox-inbox-cdc.md)**

??? question "What is the dual-write problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant · SAP

    Writing to two systems (a database and a broker) without a transaction covering both. A failure between the writes leaves them inconsistent: commit-then-publish loses events, publish-then-commit invents them.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q1-what-is-the-dual-write-problem-l2l3-flipkart-cognizant-sap)

??? question "How does the transactional outbox work?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    The event is inserted into an outbox table in the same local transaction as the business change. A relay reads the outbox (by polling or from the transaction log) and publishes to the broker, retrying until acknowledged, then marks or deletes the row.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q2-how-does-the-transactional-outbox-work-l3-amazon-flipkart-goldman-sachs)

??? question "Polling publisher vs CDC?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP · Goldman Sachs

    Polling is simple, in your code, works with any database, adds query load and latency of the poll interval. CDC (Debezium) reads the WAL: low latency, commit order, no table queries, but needs Kafka Connect, replication slots, monitoring of WAL retention and schema-change handling.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q4-polling-publisher-vs-cdc-l3l4-flipkart-sap-goldman-sachs)

??? question "What is an inbox and why must it be in the same transaction?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A table of processed message ids in the consumer's database. Insert the id and apply the effect in one transaction: a duplicate fails the unique key and is skipped. If they were separate, a crash between them would lose the effect or apply it twice.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q7-what-is-an-inbox-and-why-must-it-be-in-the-same-transaction-l3-amazon-flipkart)

??? question "What can go wrong with Debezium in production?"
    <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Replication slots retaining WAL when not consumed (disk full), quiet tables not advancing the slot (needs heartbeats), schema changes, connector restarts replaying events, snapshots on first start, failover to a replica without the slot. Monitor slot lag and connector status.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q9-what-can-go-wrong-with-debezium-in-production-l4-flipkart-goldman-sachs)


**[16. Strangler Fig, ACL & Monolith Migration](../architecture-patterns/ch16-strangler-fig-migration.md)**

??? question "What is the strangler fig pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Incrementally replacing a legacy system by routing one capability at a time from the old system to a new one through a facade, until the old code can be deleted. Each step is small, reversible and gets production feedback, unlike a big-bang rewrite.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q1-what-is-the-strangler-fig-pattern-l2l3-cognizant-sap-flipkart)

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

??? question "Which capability would you extract first?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Cognizant · Flipkart

    One with a clear boundary, few dependencies, its own data, mostly reads or calculations (so it can be shadowed), frequent change (quick payback), and a team to own it. Not the core entity everything joins against.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q8-which-capability-would-you-extract-first-l3l4-sap-cognizant-flipkart)


**[17. Sidecar, Ambassador & Adapter](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)**

??? question "What is the sidecar pattern?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A helper container deployed in the same pod as the application, sharing its network and volumes, that adds capabilities (logging, proxying, config reload, telemetry) without changing the app's code. It has its own image, language and release cycle.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q1-what-is-the-sidecar-pattern-l2-cognizant-sap-flipkart)

??? question "What are native sidecar containers in Kubernetes?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Init containers with `restartPolicy: Always`. They start before app containers (startup probes can gate the app), run for the pod's life, are restarted if they exit, and stop after the app containers. They fix start-order races, lost last logs and Jobs that never complete.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q3-what-are-native-sidecar-containers-in-kubernetes-l3-amazon-flipkart)

??? question "Why can a proxy retry a GET but not a POST?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    GETs are safe and idempotent; a POST may have taken effect even if the response was lost, so repeating it could duplicate the effect (a second charge). A POST can be retried only if the API guarantees idempotency, for example with an Idempotency-Key the server deduplicates on.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q6-why-can-a-proxy-retry-a-get-but-not-a-post-l2l3-flipkart-amazon)

??? question "How do service meshes relate to these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A sidecar mesh puts an ambassador-style proxy (usually Envoy) in every pod for all traffic: mTLS, retries, traffic splitting, telemetry. Ambient mode moves that out of the pod to per-node and per-namespace proxies to cut per-pod cost.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q7-how-do-service-meshes-relate-to-these-patterns-l3l4-goldman-sachs-flipkart)


**[18. Resilience Patterns](../architecture-patterns/ch18-resilience-patterns.md)**

??? question "What is a cascading failure and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Cognizant

    A failure (often slowness) in one dependency exhausting resources in its callers, which then fail their callers. Prevent it with timeouts on every call, bulkheads per dependency, bounded queues and load shedding, circuit breakers, and fallbacks.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q1-what-is-a-cascading-failure-and-how-do-you-prevent-it-l2l3-amazon-flipkart-cognizant)

??? question "Explain the circuit breaker states."
    <span class="badge badge-l2">L2</span> SAP · Cognizant · Flipkart

    Closed: calls pass and outcomes are recorded; when the failure (or slow-call) rate over a window exceeds a threshold, it opens. Open: calls fail immediately for a wait duration. Half-open: a limited number of trial calls; if they succeed it closes, otherwise it reopens.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q2-explain-the-circuit-breaker-states-l2-sap-cognizant-flipkart)

??? question "What is a bulkhead, and how is it different from a rate limiter?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    A bulkhead caps concurrent calls to a dependency, isolating its slowness from the rest of the service. A rate limiter caps calls per time unit, for quotas or to protect a service from callers. Concurrency limits adapt to latency automatically; rate limits don't.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q4-what-is-a-bulkhead-and-how-is-it-different-from-a-rate-limiter-l2l3-flipkart-jp-morgan)

??? question "What makes a good fallback?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · product companies

    Local and cheap (cache, last known value, static default), clearly marked as degraded to the user, agreed with product, and tested. Not another remote call, and not something that hides data correctness problems (never fall back to "payment succeeded").

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q7-what-makes-a-good-fallback-l2l3-flipkart-product-companies)

??? question "How do you know your resilience settings work?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Measure: dashboards of timeouts, retries, breaker states and fallbacks per dependency; load tests with injected latency; chaos experiments in staging and production (latency, not just errors); and simulations like this chapter's to reason about settings before an incident.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q10-how-do-you-know-your-resilience-settings-work-l3l4-amazon-flipkart)

??? question "Would you put resilience in the app, a sidecar, or the mesh?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Timeouts everywhere; retries in exactly one layer, usually closest to the dependency; breakers either in the app or the mesh; fallbacks always in the app, because only it knows what a useful degraded answer is.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q12-would-you-put-resilience-in-the-app-a-sidecar-or-the-mesh-l4-goldman-sachs-flipkart)


**[19. Event-Driven Architecture](../architecture-patterns/ch19-event-driven-architecture.md)**

??? question "What does 'event-driven' mean?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    Components communicate by publishing facts that happened, without knowing who reacts. It covers different styles: event notification (thin events, consumers call back), event-carried state transfer (events carry state; consumers keep copies) and event sourcing (the event log is the system of record).

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q1-what-does-event-driven-mean-l2-cognizant-sap-flipkart)

??? question "Event notification vs event-carried state transfer?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Notification is small and keeps data with the owner, but consumers must call back, so the owner must be available and carries the load. ECST carries the state, so consumers work when the owner is down and never call it, at the cost of larger events, local copies, eventual consistency and schema coupling.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q3-event-notification-vs-event-carried-state-transfer-l3-flipkart-goldman-sachs)

??? question "How does a new consumer get the current state?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    From a compacted topic (latest event per key), then it follows the live stream; or from a snapshot or bulk API plus the stream from a known position. Replaying a delete-retention topic is incomplete and slow.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q5-how-does-a-new-consumer-get-the-current-state-l3l4-flipkart-goldman-sachs)

??? question "What is log compaction?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A Kafka retention policy that keeps at least the most recent message for each key, discarding older ones, so the topic represents current state per key. A null-value message (tombstone) deletes a key.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q6-what-is-log-compaction-l3-amazon-flipkart)


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "What is a distributed monolith?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A system split into services that still can't be developed, deployed or fail independently, because of shared databases, shared domain libraries, synchronous chains or cycles. It has the costs of distribution without the benefits. The clearest evidence is services that are always released together.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q1-what-is-a-distributed-monolith-l2l3-cognizant-sap-flipkart)

??? question "What is the N+1 problem between services and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Calling another service once per item in a loop. Latency and failure probability grow with N. Parallelising helps latency but not failure rate; a batch endpoint fixes both; a local replica of the data removes the calls.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q3-what-is-the-n1-problem-between-services-and-how-do-you-fix-it-l2l3-flipkart-amazon)

??? question "Why are cyclic dependencies between services a problem?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Neither service can be deployed, tested, scaled or failed independently; a slowdown in either propagates around the loop; startup ordering becomes fragile. Break the cycle by moving the shared responsibility into one service or by turning one direction into events or replicated data.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q6-why-are-cyclic-dependencies-between-services-a-problem-l3-amazon-flipkart)

??? question "How do you keep anti-patterns from coming back?"
    <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Fitness functions in the build: an architecture model generated from real evidence, detectors with agreed thresholds, and a baseline of accepted findings with owners and tickets that can only shrink.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q8-how-do-you-keep-anti-patterns-from-coming-back-l4-flipkart-goldman-sachs)


### Communication


**[21. Synchronous REST](../communication/ch21-rest-api-design.md)**

??? question "PUT vs PATCH vs POST?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · Flipkart

    PUT replaces a resource at a known URI and is idempotent. PATCH partially updates and isn't idempotent by itself (make it safe with If-Match). POST creates a subordinate resource or performs a non-idempotent action; make retries safe with an Idempotency-Key.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q2-put-vs-patch-vs-post-l1l2-cognizant-flipkart)

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

??? question "RestTemplate, RestClient, WebClient: which and why?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    RestClient (synchronous, fluent) for most cases; WebClient for reactive applications and streaming. RestTemplate's deprecation was announced in Spring Framework 7.0, with formal deprecation planned for 7.1 and removal in 8.0; migrate to RestClient.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q1-resttemplate-restclient-webclient-which-and-why-l1l2-cognizant-sap-flipkart)

??? question "What are HTTP service clients?"
    <span class="badge badge-l2">L2</span> SAP · Flipkart

    Interfaces annotated with `@HttpExchange` for which Spring generates the client on top of RestClient or WebClient. Since Spring Framework 7/Boot 4 they are organised in groups (`@ImportHttpServices`) configured by properties and configurers.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q2-what-are-http-service-clients-l2-sap-flipkart)

??? question "Is a blocking HTTP client a scalability problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    On platform threads, yes: concurrency is capped by the pool. On virtual threads, blocking parks cheaply; measured here, 1,000 concurrent 200 ms calls took 0.56 s on virtual threads vs 4.2 s on a 50-thread pool. Still bound concurrency explicitly to protect the dependency.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q5-is-a-blocking-http-client-a-scalability-problem-l2l3-flipkart-goldman-sachs)


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "What is gRPC, and how is it different from REST?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    An RPC framework: services and messages defined in `.proto` files, code generated for clients and servers, calls over HTTP/2 with Protocol Buffers payloads. REST models resources over HTTP with (usually) JSON and HTTP semantics such as caching. gRPC adds streaming, deadlines and a code-generated contract; REST is simpler to consume and works everywhere.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q1-what-is-grpc-and-how-is-it-different-from-rest-l1l2-cognizant-sap-flipkart)

??? question "Is gRPC always faster or smaller than JSON?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Smaller per message (2.5× in this chapter) and cheaper to parse, but not always smaller once compressed: a gzipped JSON stream was smaller than gzipped protobuf here, because gzip removes repeated field names. Choose gRPC for contracts, streaming and deadlines, and measure if size is the argument.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q3-is-grpc-always-faster-or-smaller-than-json-l2l3-flipkart-amazon)

??? question "How do you evolve a protobuf schema without breaking clients?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    Add fields with new numbers; never change a field's number or (incompatibly) its type; `reserved` removed numbers and names; keep a zero `UNSPECIFIED` enum value and handle unknown values; put a version in the package for breaking changes; enforce with a breaking-change checker in CI.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q7-how-do-you-evolve-a-protobuf-schema-without-breaking-clients-l2l3-flipkart-jp-morgan)

??? question "Why does gRPC load balancing behave oddly on Kubernetes?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    gRPC multiplexes calls over a long-lived HTTP/2 connection, and a ClusterIP Service balances connections, not requests, so one client sticks to one pod. Use client-side balancing (headless Service, `round_robin`), an L7 proxy or service mesh, and max connection age on servers.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q8-why-does-grpc-load-balancing-behave-oddly-on-kubernetes-l3-amazon-flipkart)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "What is GraphQL, and what problem does it solve?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A query language and runtime for APIs: the server publishes a typed schema, and clients request exactly the fields they need, across related types, in one request. It addresses over-fetching (whole resources for a small screen), under-fetching (many round trips to compose a screen) and endpoint sprawl for many client screens.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q1-what-is-graphql-and-what-problem-does-it-solve-l1l2-cognizant-sap-flipkart)

??? question "What is the N+1 problem in GraphQL and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Resolvers run per parent object, so a list of N items whose field calls a service makes 1 + N calls. Fix it with batch loading: DataLoader collects keys at one execution level and loads them in one call (Spring's `@BatchMapping` or `BatchLoaderRegistry`). In this chapter a screen went from 19 backend calls to 3, with identical data.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q3-what-is-the-n1-problem-in-graphql-and-how-do-you-fix-it-l2l3-flipkart-amazon-goldman-sachs)

??? question "How do you cache GraphQL responses?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    HTTP caching is hard because queries are POSTs to one URL. Options: normalised client caches (Apollo, Relay) keyed by type and ID, persisted queries sent as GET so CDNs can cache them, caching inside the services, and per-request DataLoader caching for repeated keys.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q7-how-do-you-cache-graphql-responses-l3-flipkart-amazon)

??? question "How do you evolve a GraphQL schema without versioning the API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Add fields and types freely (clients only get what they ask for); deprecate fields with `@deprecated(reason:)`, track their usage per client operation, and remove them when usage drops to zero. Changing a field's type or nullability is breaking; run schema breaking-change checks in CI.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q11-how-do-you-evolve-a-graphql-schema-without-versioning-the-api-l2l3-sap-flipkart)


**[25. Asynchronous Messaging](../communication/ch25-asynchronous-messaging.md)**

??? question "What is the difference between a queue and a topic?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A queue delivers each message to one of its consumers (competing consumers, work distribution). A topic delivers each message to every subscriber; in Kafka, to every consumer group, with partitions shared out among the group's consumers.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q1-what-is-the-difference-between-a-queue-and-a-topic-l1l2-cognizant-sap-flipkart)

??? question "How is Kafka different from RabbitMQ?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart · SAP

    Kafka is a partitioned, replicated log: messages are retained and replayable, consumers track offsets, ordering is per partition, and parallelism is bounded by partitions. RabbitMQ is a queue broker: it routes messages through exchanges to queues, tracks per-message acknowledgement, and deletes acknowledged messages; it offers priorities, TTLs and flexible routing, but no replay.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q2-how-is-kafka-different-from-rabbitmq-l2-amazon-flipkart-sap)

??? question "How does Kafka guarantee ordering, and how can you lose it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Within a partition, records are stored and delivered in order; a key sends all records of an entity to one partition. You lose it by processing a partition's records in parallel, by non-blocking retries, by changing the partition count (keys move), or with producer retries without idempotence. Use enough partitions and key-ordered concurrency.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q4-how-does-kafka-guarantee-ordering-and-how-can-you-lose-it-l2l3-flipkart-amazon)

??? question "How do you scale a Kafka consumer?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Add consumers to the group up to the number of partitions; beyond that, add partitions (planning for key redistribution) or use key-ordered parallel processing within a partition. Watch consumer lag, and keep processing per poll within `max.poll.interval.ms`.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q8-how-do-you-scale-a-kafka-consumer-l2-flipkart-amazon)


**[26. WebSockets & Server-Sent Events](../communication/ch26-websockets-sse.md)**

??? question "What are the options for pushing updates from server to client?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    Short polling (ask on a timer), long polling (the server holds the request until there is news), Server-Sent Events (a streaming HTTP response of events) and WebSocket (a full-duplex connection after an HTTP upgrade); plus push notifications for mobile apps in the background.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q1-what-are-the-options-for-pushing-updates-from-server-to-client-l1l2-cognizant-sap-flipkart)

??? question "SSE or WebSocket: how do you choose?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart

    SSE when data flows server → client: it's plain HTTP, has automatic reconnection and resume (`Last-Event-ID`), and works through proxies. WebSocket when the client also streams (chat, GPS, collaborative editing, games) or you need binary frames. Measured here, both delivered every update for ~100–120 bytes each.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q2-sse-or-websocket-how-do-you-choose-l2-amazon-flipkart)

??? question "How do you scale WebSocket or SSE connections across many servers?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    Keep connections in a dedicated, horizontally scaled tier; get events to the right instance by broadcast (simple, N× work), routing via a registry, per-key pub/sub channels, or partitioned routing at the load balancer; use a shared broker (Kafka, Redis, or a STOMP broker relay). Size instances by connections and memory, not requests per second.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q5-how-do-you-scale-websocket-or-sse-connections-across-many-servers-l3-amazon-flipkart-goldman-sachs)

??? question "How do you avoid losing updates when a connection drops?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Give every event an id, keep a short replay buffer per stream, and have the client send its last id on reconnect (`Last-Event-ID` for SSE, a resume message for WebSocket). For state like positions, sending the latest state on reconnect may be enough.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q8-how-do-you-avoid-losing-updates-when-a-connection-drops-l2l3-sap-flipkart)


**[27. API Contracts & Schema Evolution](../communication/ch27-api-contracts.md)**

??? question "What's the difference between backward and forward compatibility?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Backward compatible: existing consumers keep working with the new version (new provider, old consumer). Forward compatible: consumers built for the new version work with the old provider or old data. Schema registries define them from the reader's side; with `BACKWARD` you upgrade consumers first, with `FORWARD` producers first, with `FULL` either.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q2-whats-the-difference-between-backward-and-forward-compatibility-l2-flipkart-amazon)

??? question "What are consumer-driven contracts, and how do they differ from a schema diff?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Consumers publish executable expectations (requests and the response parts they use) that run against the provider in its build. A diff compares schema versions: broad, but blind to behaviour and usage, so it raises false alarms and misses behaviour changes. Contracts are precise but cover only consumers who write them. Here each caught breaks the other missed.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q5-what-are-consumer-driven-contracts-and-how-do-they-differ-from-a-schema-diff-l2l3-goldman-sachs-flipkart)

??? question "How would you rename a field without breaking consumers?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    Expand and contract: add the new field and populate both, migrate consumers (contracts and usage data show who's left), then remove the old field. Never rename in one step.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q8-how-would-you-rename-a-field-without-breaking-consumers-l2l3-flipkart-any)


**[28. Idempotency, Deduplication & Correlation](../communication/ch28-idempotency-correlation.md)**

??? question "How do you implement idempotency keys?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    The client sends a unique key per action. The server atomically claims it (insert with a unique constraint) before any side effect, stores a request fingerprint, performs the operation, and stores the response with the business write. Duplicates get the stored response, 409 if still in progress, 422 if the fingerprint differs. Add a lease for crashes and pass the key downstream. Measured: exactly one charge per key under 4× concurrent duplicates.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q2-how-do-you-implement-idempotency-keys-l2l3-amazon-flipkart-goldman-sachs)

??? question "How long should you keep idempotency keys or processed message ids?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    Longer than the slowest source of duplicates you care about, and published to clients. For API keys, longer than any client retry (24 h is common). For consumers, longer than redeliveries and re-publishes; for replays days later, use version checks on the entity instead of a huge store. Measured: a 1-hour window missed every replay; a 7-day table needed 302 M ids.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q6-how-long-should-you-keep-idempotency-keys-or-processed-message-ids-l2l3-flipkart-sap)

??? question "What happens if the server crashes after claiming a key?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Without a lease, the key stays in progress and the client gets 409 forever. With a lease, a retry of the same request takes over after it expires and re-asks the provider with the same downstream key, which returns the original result if the first attempt did charge. A unique constraint on the provider's reference catches the rare overlap of two owners.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q11-what-happens-if-the-server-crashes-after-claiming-a-key-l3-goldman-sachs-flipkart)


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

??? question "How do you keep a cache or search index in sync with the database?"
    <span class="badge badge-l3">L3</span> Flipkart · SAP

    Make the database the source of truth, record changes in the same transaction (outbox) or read the change log (CDC), and apply them with a projector that retries until it succeeds and writes only if the version is newer. Monitor lag and drift, and be able to rebuild the copy. Measured: zero wrong items, median lag 3 ms.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q5-how-do-you-keep-a-cache-or-search-index-in-sync-with-the-database-l3-flipkart-sap)

??? question "Is Redis faster than PostgreSQL?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    For small writes, yes, and the honest comparison is at the same durability: about 31,000 vs 7,100–8,000 updates/s with every write synced, 90,000+ vs 16,800 with up to a second of possible loss. Not for everything: Redis's sorted radius search computes and sorts every member in the circle (~950/s with 1,900 riders inside) where PostgreSQL's KNN index stopped after ten (~3,650/s).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q7-is-redis-faster-than-postgresql-l2l3-flipkart-any)

??? question "How do you store time-series data in a relational database?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Append in time order into tables partitioned by time, with a BRIN index on the timestamp and B-trees only for the lookups you need (e.g. by device and time). Retention drops old partitions. Measured: the time index went from 79 MB to 168 kB, and removing a day took 83 kB of WAL instead of 171 MB. Move to a time-series or columnar store for compression and downsampling at much larger volumes.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q10-how-do-you-store-time-series-data-in-a-relational-database-l2l3-sap-flipkart)


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "Why is 2PC avoided between microservices?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    It holds locks across network round trips (measured: 2.8× as long as a saga step, half the throughput on hot rows), it couples availability (no orders while payments was down), it blocks if the coordinator fails (one in-doubt transaction cut a hot path to 2–6 orders/s), and it needs XA-capable resources, which brokers, HTTP APIs and external providers aren't. It also couples teams to a shared transaction manager.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q2-why-is-2pc-avoided-between-microservices-l3-amazon-flipkart-goldman-sachs)

??? question "How does a saga differ from a distributed transaction?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    Each step commits locally and at once; failures are undone by compensating steps, not rollback. It gives up isolation (intermediate states are visible) in exchange for no cross-service locks and independent availability. Measured: the saga's payment step held its lock 4.3–4.5 ms vs 2PC's 11–12.5 ms.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q6-how-does-a-saga-differ-from-a-distributed-transaction-l2l3-flipkart-sap)

??? question "Why should order placement return 202 Accepted?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Because the order exists but its payment isn't decided yet. Saying so lets the request be one local transaction, keeps ordering available when payments isn't (measured: all orders accepted during a 10 s outage, confirmed within 10.2 s), and the client follows a status resource or receives a push.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q7-why-should-order-placement-return-202-accepted-l3-amazon-flipkart)

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

??? question "Optimistic or pessimistic locking?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    Optimistic when conflicts are rare and the work between read and write is long (user edits: ETag + If-Match); pessimistic when conflicts are frequent and the critical section is short. Best of all, avoid both by making the change in the database in one statement. Under heavy contention optimistic retries dominate (thousands in the experiment).

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q6-optimistic-or-pessimistic-locking-l3-jp-morgan-flipkart)

??? question "Explain PACELC with an example from your system."
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    If partitioned, choose availability or consistency; else, latency or consistency. FoodFleet's restaurant listing chooses availability and latency (replicas, caches); the wallet chooses consistency (primary only) and accepts the latency; profiles use tokens to get read-your-writes at near-replica latency.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q11-explain-pacelc-with-an-example-from-your-system-l3l4-amazon-flipkart)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "What should you cache?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    What is expensive to produce or very hot, and may be slightly stale. Measured: Redis didn't speed up a primary-key read of a menu card (at 2.1–2.2 ms p50 against 1.7–1.8 ms), but turned a 130 ms aggregation into a 1.1 ms read. Never cache the inputs of decisions like payment or stock checks.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q2-what-should-you-cache-l2l3-flipkart-amazon)

??? question "How do you prevent a cache stampede?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon · JP Morgan

    Coalesce misses (single-flight per instance or a distributed lock), refresh before expiry (probabilistic early expiration, with a lock), or serve stale while one caller refreshes. Add TTL jitter. Measured: 160 recomputations unprotected; 9–10 with stale-while-revalidate, and no request over 100 ms.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q4-how-do-you-prevent-a-cache-stampede-l3-flipkart-amazon-jp-morgan)

??? question "When is write-behind appropriate?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    For high-rate data that may be approximate: counters, impressions, last-seen times. It cuts database writes to batches (measured: ~7,000 statements/s to 1) but loses unflushed data if the cache loses it (measured: 9,991–10,651 views on a Redis restart). Flushes must be idempotent (atomic claim, batch ids). Never for money.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q7-when-is-write-behind-appropriate-l3-amazon-flipkart)

??? question "Local (in-process) cache or distributed cache?"
    <span class="badge badge-l3">L3</span> Flipkart · SAP

    Local caches are fastest (no network; measured p50 under 0.1 ms) but stale per instance and multiply memory; distributed caches are shared and consistent across instances but cost a network hop. Use local for small, hot, slowly-changing data with a short TTL, often in front of a distributed one.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q10-local-in-process-cache-or-distributed-cache-l3-flipkart-sap)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

??? question "How big should a connection pool be?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    Small, and sized for the database, not the instance: a few times the database's cores across the whole fleet, then measured. Measured on 2 vCPUs: 16 to 256 connections added at most 10–19% throughput and raised p99 inside the database from 6 ms to 76–98 ms. Extra demand should wait in the pool, not in the database.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q1-how-big-should-a-connection-pool-be-l2l3-flipkart-cognizant)

??? question "How do you rename a column with zero downtime?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Expand/contract: add the new column; deploy code that writes both and reads the old; backfill; deploy code that reads the new; deploy code that writes only the new; drop the old in a later release. Measured: in-place renames failed thousands of statements during a rolling deploy; expand/contract none.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q6-how-do-you-rename-a-column-with-zero-downtime-l3-flipkart-amazon)

??? question "Read replicas or sharding?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Replicas scale reads that tolerate lag and are cheap to add; sharding scales writes and data size but makes cross-shard queries, transactions and operations hard. Exhaust indexing, caching, replicas, partitioning and write reduction first; shard when one primary can't take the writes.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q8-read-replicas-or-sharding-l3l4-flipkart-goldman-sachs)

??? question "How do you choose a shard key?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    High cardinality, even distribution, and present in most queries. Measured: by city, one shard held 6× the average and half the shards held nothing; by customer with the shard embedded in order ids, 70% of queries hit one shard and the largest shard was 1.19× the average.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q9-how-do-you-choose-a-shard-key-l4-amazon-flipkart)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "How do you build a report that needs data from several microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    Not by joining their databases. For small, rare queries, compose their APIs; for real reports, build a reporting store owned by a reporting service and fed by their events or CDC, shaped for the queries. Measured: API composition 2.7–2.9 s with hundreds of calls, a reporting copy under a second, a pre-aggregated table about 0.14 s.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q1-how-do-you-build-a-report-that-needs-data-from-several-microservices-l2l3-flipkart-cognizant)

??? question "How do you keep aggregates correct when events are duplicated or out of order?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Keep the latest state per entity, replaced only by a higher version, and change aggregates by the delta between old and new state in the same transaction. Measured: counting events +7.1%, counting with dedup +1.9% (corrections), state with versions exact.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q5-how-do-you-keep-aggregates-correct-when-events-are-duplicated-or-out-of-order-l3-amazon-flipkart)

??? question "What's the most common bug in daily reports?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    Days in the wrong time zone. `date(placed_at)` on a UTC server put 4% of Bengaluru orders (00:00–05:30 IST) on the previous day in our data. Compute the business day in the business's zone once and store it.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q7-whats-the-most-common-bug-in-daily-reports-l2l3-flipkart-any)


### Deployment & Platform


**[53. Containerization](../deployment/ch53-containerization.md)**

??? question "How do you make Java images small?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    Use a runtime with only the modules you need (jdeps + jlink) or a JRE, on distroless instead of a full OS, with no build tools. Measured: 469 MB with the full JDK, 119 MB with jlink `java.se`, 88 MB with jdeps' 7 modules.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q2-how-do-you-make-java-images-small-l2-swiggy-flipkart)

??? question "How does the JVM size its heap in a container?"
    <span class="badge badge-l2">L2</span> Amazon · Walmart

    From the cgroup memory limit: 25% by default (`MaxRAMPercentage`), 50% when memory is small. Measured: 247 MiB in a 1 GB container and 121 MiB in 256 MB. Set `MaxRAMPercentage=75` to use the memory you pay for, and leave the rest for metaspace, threads, buffers and code cache.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q5-how-does-the-jvm-size-its-heap-in-a-container-l2-amazon-walmart)

??? question "How do you speed up JVM startup in containers?"
    <span class="badge badge-l3">L3</span> Swiggy · Meesho

    Use CDS or the JDK 24+ AOT cache from a training run, a smaller runtime, lazy initialization where safe, and enough CPU during startup. Measured: an AOT cache took first response from 1.2–1.3 s to 0.49–0.51 s at 1 CPU, for 24 MB of image. Native images and CRaC are chapter 60.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q9-how-do-you-speed-up-jvm-startup-in-containers-l3-swiggy-meesho)


**[54. Kubernetes for Spring Developers](../deployment/ch54-kubernetes-for-spring.md)**

??? question "Why don't new pods get traffic after scaling out?"
    <span class="badge badge-l3">L3</span> Flipkart · Uber

    kube-proxy balances connections, and existing keep-alive connections stay on the pods they were opened to. Measured: all pooled requests stayed on the original pod for 50 s, while new connections spread at once. Cap connection lifetime, or balance per request with a mesh or a client-side balancer.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q4-why-dont-new-pods-get-traffic-after-scaling-out-l3-flipkart-uber)

??? question "What does a PodDisruptionBudget do?"
    <span class="badge badge-l2">L2</span> Zomato · Walmart

    It limits voluntary disruptions (drains, upgrades, autoscaler removals): the eviction API refuses evictions that break it. Measured: without one, a drain evicted all three pods in 2 s and 295–331 requests failed; with `minAvailable: 2`, one pod was evicted, the rest refused, and 0–1 requests failed. It doesn't protect against crashes.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q5-what-does-a-poddisruptionbudget-do-l2-zomato-walmart)


**[55. Helm, Kustomize & Environment Promotion](../deployment/ch55-helm-kustomize.md)**

??? question "Why did our deploy reset the HPA's replica count?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    The manifest sets `replicas`, and a client-side apply (kubectl or Helm 3) resets fields that differ. Measured: 5 → 2. Server-side apply refuses with a conflict instead, and Helm 4 fails the release. The fix is to omit `replicas` when an HPA owns it; then the apply kept 5.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q3-why-did-our-deploy-reset-the-hpas-replica-count-l3-swiggy-flipkart)


**[56. Autoscaling](../deployment/ch56-autoscaling.md)**

??? question "Why doesn't the HPA scale down right away?"
    <span class="badge badge-l2">L2</span> Flipkart

    The default scale-down stabilization window is 300 s: it scales down only to the highest recommendation of the last five minutes. Measured: back to one pod 331–333 s after the load stopped. It's there to prevent flapping; measured without it, one-minute bursts caused 325–432 failed requests in eight minutes, against 97–192 with it.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q3-why-doesnt-the-hpa-scale-down-right-away-l2-flipkart)

??? question "HPA, VPA and cluster autoscaler: how do they fit?"
    <span class="badge badge-l3">L3</span> Walmart · platform rounds

    The HPA changes the number of pods; VPA changes their requests; the cluster autoscaler (or Karpenter) changes the number of nodes. Don't let HPA and VPA both act on CPU for the same workload; use VPA in recommendation mode to set requests, and the HPA to scale.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q6-hpa-vpa-and-cluster-autoscaler-how-do-they-fit-l3-walmart-platform-rounds)


**[57. Service Mesh](../deployment/ch57-service-mesh.md)**

??? question "What is outlier detection?"
    <span class="badge badge-l2">L2</span> Flipkart

    Passive health checking: an endpoint that fails N times in a row is ejected from balancing for a growing period. Measured: 26 failures in a minute instead of 1,000. Watch `max_ejection_percent`: its 10% default ejected nothing in a three-pod service.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q6-what-is-outlier-detection-l2-flipkart)


**[58. CI/CD, GitOps & Progressive Delivery](../deployment/ch58-cicd-gitops.md)**

??? question "Canary or blue/green?"
    <span class="badge badge-l3">L3</span> Flipkart · Walmart

    Canary limits exposure and needs per-version metrics; blue/green switches everything at once but switches back instantly, at double capacity. Measured: the blue/green switch took under a second for new connections, but keep-alive clients stayed on the old slot.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q5-canary-or-bluegreen-l3-flipkart-walmart)


**[59. Cloud-Native on AWS, Azure & GCP](../deployment/ch59-cloud-native-platforms.md)**

??? question "A cloud API is throttling us. What do you do?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    Stop calling it as often: cache, batch, and spread startup calls. Then use adaptive or standard retries with jitter and keep the retry quota. Measured at twice the limit: adaptive mode cut failed calls by over 80% compared with standard, and throttled responses by more than half. Ask for a quota increase only after that.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q5-a-cloud-api-is-throttling-us-what-do-you-do-l3-swiggy-flipkart)

??? question "Managed Kafka or self-run?"
    <span class="badge badge-l3">L3</span> Walmart

    Managed (MSK, Event Hubs' Kafka endpoint, Google's managed Kafka, Confluent Cloud) unless you need a feature or scale they don't offer, or the cost difference at your volume pays for a platform team. You still own topic design, partitions, client configuration and consumer lag (chapters 25 and 69).

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q9-managed-kafka-or-self-run-l3-walmart)


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

??? question "What makes a good training run?"
    <span class="badge badge-l3">L3</span> Flipkart

    The real service doing its real work through its real entry points. Measured: a cache trained without the HTTP server saved about half as much, at 1 CPU and at 0.25 CPU. With Spring Boot, `-Dspring.context.exit=onRefresh` covers the context; add recorded traffic for the request path.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q4-what-makes-a-good-training-run-l3-flipkart)


### Foundations


**[1. Monolith vs SOA vs Microservices vs Modular Monolith](../foundations/ch01-architecture-styles.md)**

??? question "What is a distributed monolith and how do you detect one?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Services that are physically separate but logically coupled, so they must change and deploy together. You pay all the costs of distribution and get none of the independence.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q3-what-is-a-distributed-monolith-and-how-do-you-detect-one-l2l3-flipkart-amazon)

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

??? question "What does single responsibility mean for a service?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Accenture · Flipkart

    One business capability and one reason to change, owned by one team. It is not a line-count limit and not one entity per service.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q2-what-does-single-responsibility-mean-for-a-service-l1l2-accenture-flipkart)

??? question "How do you evolve an API without breaking consumers?"
    <span class="badge badge-l2">L2</span> Amazon · SAP · Flipkart

    Prefer additive changes, make consumers tolerant readers, version only for unavoidable breaking changes, announce deprecation (Deprecation and Sunset headers), measure per-version traffic, and protect it all with contract tests.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q3-how-do-you-evolve-an-api-without-breaking-consumers-l2-amazon-sap-flipkart)

??? question "Your service calls three downstream services. How do you set timeouts?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Start from the caller's latency SLO and allocate a budget per hop, using each dependency's measured p99 plus headroom. Make sure retries fit inside the budget. Propagate a deadline so downstream services stop work the caller has abandoned.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q6-your-service-calls-three-downstream-services-how-do-you-set-timeouts-l3-flipkart-amazon)

??? question "How do you enforce principles across 50 teams without an architecture review board?"
    <span class="badge badge-l4">L4</span> SAP · Amazon · Flipkart

    Paved road plus fitness functions. Service templates with secure, observable defaults. Shared architecture-rule tests (ArchUnit, Spring Modulith) run in every pipeline. API linting. Scorecards in the developer portal. Exceptions through lightweight ADRs.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q9-how-do-you-enforce-principles-across-50-teams-without-an-architecture-review-board-l4-sap-amazon-flipkart)

??? question "How does decentralized data management affect reporting?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    You can't join across service databases, so you replicate. Services publish events or CDC streams into an analytics store (warehouse or lake) where reporting joins happen. Operational queries across services use API composition or dedicated read models.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q10-how-does-decentralized-data-management-affect-reporting-l3-goldman-sachs-flipkart)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Explain the CAP theorem precisely."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    During a network partition, a replicated system must choose between linearizable consistency and availability. Partition tolerance isn't optional, and the choice only applies while partitioned.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q2-explain-the-cap-theorem-precisely-l2l3-amazon-flipkart-goldman-sachs)

??? question "Explain consistency models. Which would you choose for an order-status page?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style product companies

    From linearizable through causal and session guarantees to eventual. For an order-status page: linearizable writes inside order-service (versioned transitions), and **monotonic reads** for the customer so status never goes backwards. Read-your-writes if the customer can change something on that page.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q4-explain-consistency-models-which-would-you-choose-for-an-order-status-page-l3-flipkart-swiggy-style-product-companies)

??? question "What is 'exactly-once' and is it possible?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Exactly-once *delivery* over an unreliable network isn't achievable. Exactly-once *effects* are: at-least-once delivery plus idempotent processing, with the dedup record committed atomically with the effect. Kafka's EOS covers read-process-write within Kafka. External side effects still need idempotency.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q7-what-is-exactly-once-and-is-it-possible-l2l3-flipkart-amazon-goldman-sachs)

??? question "A customer updates their address and immediately sees the old one. Why, and how do you fix it?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style

    The read went to an asynchronously replicated replica that hadn't caught up. Provide read-your-writes: route that user's reads to the primary for a short window, or return a version token on write and require a replica at least that fresh.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q10-a-customer-updates-their-address-and-immediately-sees-the-old-one-why-and-how-do-you-fix-it-l3-flipkart-swiggy-style)

??? question "How do you choose between CP and AP for a FoodFleet feature?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    By the cost of each kind of error. A stale menu price is cheap and recoverable, so choose AP with a freshness SLO. Double-redeeming a coupon or double-paying a restaurant is expensive, so choose CP with conditional writes. Write it in the service charter, and test the partition behaviour.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q13-how-do-you-choose-between-cp-and-ap-for-a-foodfleet-feature-l4-amazon-flipkart)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "Why store configuration in the environment rather than in the jar?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Amazon · Flipkart

    So one build can run in every environment. If config is baked in, each environment needs its own build, and production runs an artifact that was never tested.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q2-why-store-configuration-in-the-environment-rather-than-in-the-jar-l1l2-amazon-flipkart)

??? question "What does 'stateless' mean for a service that keeps WebSocket connections?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style

    Connections are inherently tied to a pod, but *state* must not be. Fan out updates through Redis or Kafka so any pod can push to its connected clients. Clients reconnect to any pod after a restart and resume from a cursor.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q4-what-does-stateless-mean-for-a-service-that-keeps-websocket-connections-l3-flipkart-swiggy-style)

??? question "What is dev/prod parity and how do you achieve it with Spring Boot?"
    <span class="badge badge-l2">L2</span> Flipkart · Cognizant

    Minimise the time, personnel and tooling gaps between development and production. With Spring Boot: Docker Compose support for local backing services, Testcontainers with `@ServiceConnection` for tests (same major versions as production), the same image in every environment, and continuous deployment so changes reach production quickly.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q7-what-is-devprod-parity-and-how-do-you-achieve-it-with-spring-boot-l2-flipkart-cognizant)

??? question "Which factors would you relax for a stateful system like Kafka or PostgreSQL on Kubernetes?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    The factors are about *applications*. Stateful backing services are run with operators and StatefulSets, stable identities, persistent volumes and careful disruption budgets. The application side treats them as attached resources and stays twelve-factor.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q11-which-factors-would-you-relax-for-a-stateful-system-like-kafka-or-postgresql-on-kubernetes-l4-amazon-flipkart)


**[5. Domain-Driven Design](../foundations/ch05-domain-driven-design.md)**

??? question "Entity vs value object?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Flipkart

    An entity has identity that persists as its attributes change (an order line within an order). A value object has no identity, is immutable and is equal by value (Money, Address). Prefer value objects: they're simpler, thread-safe and self-validating.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q2-entity-vs-value-object-l2-tcs-infosys-flipkart)

??? question "What is an aggregate and how do you choose its boundary?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    A cluster of objects treated as one consistency unit, accessed through its root. The boundary comes from **invariants that must hold at every commit**. Keep aggregates small, reference others by id, and use eventual consistency between them.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q3-what-is-an-aggregate-and-how-do-you-choose-its-boundary-l3-amazon-flipkart-goldman-sachs)

??? question "Core, supporting and generic subdomains: why does it matter?"
    <span class="badge badge-l4">L4</span> Amazon · Flipkart

    It decides where to invest. Core gets the best people and rich models. Supporting gets pragmatic, simple solutions. Generic gets bought or reused. Misclassifying a generic subdomain as core (building your own payment gateway) wastes years.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q8-core-supporting-and-generic-subdomains-why-does-it-matter-l4-amazon-flipkart)

??? question "How do you find bounded contexts in a new domain?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · consulting firms

    Talk to domain experts. Run Event Storming: domain events on a timeline, then commands, actors, policies and aggregates. Look for language changes ("order" becomes "ticket" becomes "delivery job"), different rules and lifecycles, and team and process boundaries. Validate with the context map and the expected rate of change. [Chapter 6](../foundations/ch06-service-decomposition.md) walks through it.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q9-how-do-you-find-bounded-contexts-in-a-new-domain-l3l4-flipkart-consulting-firms)

??? question "What's wrong with an anemic domain model?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    The rules live outside the objects, so invalid states are possible and rules get duplicated and drift. It's fine for CRUD-like supporting subdomains, and costly for core domains with real invariants.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q11-whats-wrong-with-an-anemic-domain-model-l2l3-goldman-sachs-flipkart)

??? question "How do domain events relate to integration events?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Domain events are facts inside a context, and can evolve with the model. Integration events are the **published language** for other contexts: versioned, documented, backward-compatible, often a reduced projection. Many teams translate domain events into integration events at the boundary (an outbox writer).

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q12-how-do-domain-events-relate-to-integration-events-l3-amazon-flipkart)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

??? question "How do you decide service boundaries?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Start from business capabilities and bounded contexts, discovered with domain experts (Event Storming). Group things that change together and must be consistent together; separate things with different owners, scaling needs, volatility or compliance requirements. Validate candidates against real use cases (how many calls per checkout?) and against change history, and start as modules before making them services.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q1-how-do-you-decide-service-boundaries-l2l3-cognizant-sap-flipkart)

??? question "What is Event Storming and what do you get out of it?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart · consulting firms

    A workshop where domain experts and engineers place domain events (orange) on a timeline, then add commands, actors, policies, aggregates, external systems and hotspots. You get a shared understanding of the process, a list of open questions, aggregate candidates, and boundary candidates where the language changes and at pivotal events. Policies between lanes usually become asynchronous integration events.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q3-what-is-event-storming-and-what-do-you-get-out-of-it-l3-sap-flipkart-consulting-firms)

??? question "What goes wrong when you decompose by entity?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Business operations span many services, so use cases become long synchronous call chains: more latency, lower availability (0.999<sup>11</sup> ≈ 98.9% for 11 services on a path), and invariants that used to be one transaction now need sagas. Features need coordinated changes across services. It's a distributed monolith.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q5-what-goes-wrong-when-you-decompose-by-entity-l3-flipkart-amazon-goldman-sachs)

??? question "A checkout path has 6 services at 99.9% each. What's its availability, and what do you do about it?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    0.999<sup>6</sup> ≈ 99.4%, about 4.3 hours of downtime a month if failures are independent. Options: fewer services on the sync path (merge, or make steps async), higher availability per service, and fallbacks so non-critical dependencies (coupon, recommendations) degrade instead of failing checkout.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q8-a-checkout-path-has-6-services-at-999-each-whats-its-availability-and-what-do-you-do-about-it-l3-amazon-flipkart)

??? question "What are the signs that a decomposition is wrong?"
    <span class="badge badge-l3">L3</span> Flipkart · Cognizant · Amazon

    Shotgun changes across services, lock-step deploys, chatty sync chains, proposals for distributed transactions, shared tables, generic "common" or "manager" services, cyclic calls, and services whose releases need several teams' sign-off.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q10-what-are-the-signs-that-a-decomposition-is-wrong-l3-flipkart-cognizant-amazon)


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "What is the inverse Conway maneuver?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Deliberately shape teams to match the architecture you want, so that Conway's Law produces it. In practice teams, ownership, data and architecture must change together; changing only the org chart doesn't work.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q2-what-is-the-inverse-conway-maneuver-l3-flipkart-amazon)

??? question "What is cognitive load and why does it matter for microservices?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    The amount a team must keep in mind to do its work. Microservices multiply what a team has to know (deployments, observability, contracts, on-call). If a team's load exceeds its capacity, quality and speed drop. Limit domains per team, and use platforms to remove extraneous load.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q5-what-is-cognitive-load-and-why-does-it-matter-for-microservices-l3-amazon-flipkart)

??? question "How do you know whether your org structure matches your architecture?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Measure: teams per delivered feature, cross-team pull requests, lock-step releases, wait time for other teams; compare dependencies between components with declared team interactions (socio-technical congruence); survey cognitive load. Look for coordination needs without an agreed channel and channels with no need.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q8-how-do-you-know-whether-your-org-structure-matches-your-architecture-l4-goldman-sachs-flipkart)

??? question "A team owns 12 services and is constantly firefighting. What do you do?"
    <span class="badge badge-l4">L4</span> Flipkart · Amazon

    First check whether the 12 services are really separate capabilities or a fragmented one (merge if they always change together, chapter 6). Then check the load: domain count and complexity, operational toil. Options: merge services, move a domain to another or a new team, invest in platform capabilities that remove toil, and add an enabling team for reliability practices. Track the result with on-call load and lead time.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q10-a-team-owns-12-services-and-is-constantly-firefighting-what-do-you-do-l4-flipkart-amazon)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "How do virtual threads work, and when should you not use them?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    They're JVM-scheduled threads that unmount from a carrier platform thread when they block on I/O, so blocking code scales to many concurrent requests. They don't help CPU-bound work. Don't pool them, avoid heavy `ThreadLocal` caches, and put explicit limits on shared resources. On Java 21–23, long `synchronized` blocks around I/O pin the carrier; Java 24 fixed that (JEP 491).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q2-how-do-virtual-threads-work-and-when-should-you-not-use-them-l2l3-amazon-flipkart-goldman-sachs)

??? question "Virtual threads or WebFlux for a new service?"
    <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    For request/response services that call databases and other services, Spring MVC with virtual threads: simpler code, same scalability for I/O-bound work, every blocking library works. WebFlux when you need streaming, back-pressure, or a fully non-blocking gateway.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q3-virtual-threads-or-webflux-for-a-new-service-l3-flipkart-jp-morgan)

??? question "How do you call another microservice in Spring Boot 4?"
    <span class="badge badge-l2">L2</span> Cognizant · Accenture · Flipkart

    Declare an interface with `@HttpExchange`/`@GetExchange`, register it with `@ImportHttpServices(group = "kitchen", types = …)`, and configure `spring.http.serviceclient.kitchen.base-url`, timeouts and API version in properties. Inject the interface. Add `spring-boot-starter-restclient`. Use one group per downstream service so timeouts differ.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q6-how-do-you-call-another-microservice-in-spring-boot-4-l2-cognizant-accenture-flipkart)

??? question "What are `@Retryable` and `@ConcurrencyLimit` in Spring Framework 7, and when are they not enough?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Annotations in `org.springframework.resilience.annotation`, enabled with `@EnableResilientMethods`. `@Retryable` retries selected exceptions with backoff and jitter; `@ConcurrencyLimit` caps concurrent invocations (a simple bulkhead). They don't provide circuit breakers, rate limiters or time limiters: use Resilience4j (Spring Cloud CircuitBreaker) for those (chapter 18).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q7-what-are-retryable-and-concurrencylimit-in-spring-framework-7-and-when-are-they-not-enough-l3-amazon-flipkart)

??? question "How would you migrate 30 services from Boot 3 to Boot 4?"
    <span class="badge badge-l4">L4</span> JP Morgan · SAP · Flipkart

    Upgrade all to the latest 3.5 and clear deprecations; publish a Boot 4 golden-path template; migrate a pilot service with OpenRewrite (`UpgradeSpringBoot_4_0`), the properties migrator and classic starters as a bridge; add contract tests around JSON before switching Jackson defaults; then roll out team by team behind canaries, with a packaged-jar smoke test in CI. Track progress on a dashboard of service versions.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q10-how-would-you-migrate-30-services-from-boot-3-to-boot-4-l4-jp-morgan-sap-flipkart)


### Governance & Organization


**[71. API Governance & Developer Portals](../governance-org/ch71-api-governance.md)**

??? question "How do you deprecate an endpoint safely?"
    <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Mark it in the document (`deprecated`, `x-sunset`), serve `Deprecation`, `Sunset` and `Link` headers, have client libraries warn, track usage by caller, contact the remaining callers, brown out near the date, and remove only when usage is zero. Measured: two of four callers saw nothing because they used plain HTTP.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q5-how-do-you-deprecate-an-endpoint-safely-l3-flipkart-razorpay)


**[73. ADRs, Fitness Functions & Tech Radar](../governance-org/ch73-adrs-fitness-functions.md)**

??? question "How do you make performance checks in CI not flaky?"
    <span class="badge badge-l3">L3</span> Flipkart

    Repeat and aggregate (best of three), use relative thresholds wide enough for the environment's noise, refresh the baseline in the same environment, and push small regressions to microbenchmarks. Measured: single runs at +10% raised 3–9 false alarms in 12.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q8-how-do-you-make-performance-checks-in-ci-not-flaky-l3-flipkart)


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "Why not start with the easiest service?"
    <span class="badge badge-l3">L3</span> Flipkart

    The easiest are usually the ones that rarely change, so extracting them buys little. Measured: fewest-dependants-first freed 5% of changes after three extractions.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q3-why-not-start-with-the-easiest-service-l3-flipkart)


**[76. Serverless & Function-Based Services](../governance-org/ch76-serverless-microservices.md)**

??? question "What makes up a Java cold start, and how do you shorten it?"
    <span class="badge badge-l3">L3</span> Flipkart

    Platform sandbox, JVM start, class loading and linking, framework start and the first request. An AOT cache from a representative training run, C1 and Serial GC cut the JVM's part from 246–329 ms to 113–196 ms here; SnapStart or a native image go further.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q3-what-makes-up-a-java-cold-start-and-how-do-you-shorten-it-l3-flipkart)


**[77. AI-Native Microservices](../governance-org/ch77-ai-native-microservices.md)**

??? question "How do you set timeouts for streamed LLM responses?"
    <span class="badge badge-l3">L3</span> Flipkart

    A first-token deadline, an idle deadline between chunks, and a total deadline. A total deadline alone held stalled streams for 10–30 s; idle deadlines detected stalls after 1 s and retried, keeping p99 at 5.1–5.3 s.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q2-how-do-you-set-timeouts-for-streamed-llm-responses-l3-flipkart)


### Observability


**[47. Observability Fundamentals](../observability/ch47-observability-fundamentals.md)**

??? question "Why can't metrics answer 'which users are affected'?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    Each label value multiplies the number of time series, so user IDs, order IDs or restaurant IDs can't be labels. Measured: a `restaurant_id` label with 1,000 values turned 265 series into 260,005 and took Prometheus from about 100 MB to 670 MB. Per-entity questions go to traces and wide events, which cost per record, not per distinct value.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q3-why-cant-metrics-answer-which-users-are-affected-l2-swiggy-flipkart)

??? question "How do you correlate logs, metrics and traces?"
    <span class="badge badge-l2">L2</span> Zomato · Walmart

    Propagate W3C trace context on every hop; put the trace ID in every log record and wide event; attach trace IDs to metric histograms as exemplars; use the same resource attributes (service.name, service.version, service.instance.id) on all signals.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q6-how-do-you-correlate-logs-metrics-and-traces-l2-zomato-walmart)

??? question "What does instrumentation cost?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    CPU, memory, startup time and network, plus storage downstream. Measured on three thin services: the Java agent added 38–56% CPU per order (25–36% with nothing exported), 110–125 MB per JVM and 13–15 s of startup; JFR 4–7% CPU. Measure it per service, and budget it like any other dependency.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q9-what-does-instrumentation-cost-l3-flipkart-goldman-sachs)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "Why can't you average percentiles across pods?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    A percentile isn't additive: the fleet's p99 depends on how every pod's whole distribution combines, which the per-pod p99s don't contain. Measured with one slow pod in four: the average of the pods' p99s was 137 ms, the true fleet p99 322 ms, `histogram_quantile` over summed buckets 317–318 ms. Publish histogram buckets and compute the percentile from their sum.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q2-why-cant-you-average-percentiles-across-pods-l2l3-swiggy-flipkart)

??? question "How does Spring decide the uri tag, and how does it go wrong?"
    <span class="badge badge-l3">L3</span> Walmart · Zomato

    On the server, the matched path pattern (or NOT_FOUND / REDIRECTION / UNKNOWN); on the client, the URI template given to RestClient. It goes wrong when code concatenates IDs into the template, or overrides the convention with the raw path. Measured: 10,000 requests gave 24 meters with path patterns, 15,954 with raw paths and 19,918 with concatenated client URIs.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q6-how-does-spring-decide-the-uri-tag-and-how-does-it-go-wrong-l3-walmart-zomato)

??? question "What does recording a metric cost?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Tens of nanoseconds for a counter or plain timer, about a hundred with a histogram; client-side percentiles and Observations cost more, especially under contention. Measured: 12–13 ns for a counter, 51–52 ns for a timer, 84–97 ns with a histogram, about 200 ns for client-side percentiles (1.4–1.5 µs contended), 0.7–1.3 µs for an Observation. Register meters once; turn off long-task timers you don't use.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q9-what-does-recording-a-metric-cost-l3-goldman-sachs-flipkart)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "How is trace context carried through Kafka?"
    <span class="badge badge-l3">L3</span> Flipkart · Walmart

    The producer's instrumentation writes `traceparent` (and baggage) into record headers; the consumer's instrumentation wraps the records iterator and makes a `process` span current per record, as a child of the producer's span, from `next()` until the next `hasNext()`. In Spring Kafka, enable template and listener observations (off by default).

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q3-how-is-trace-context-carried-through-kafka-l3-flipkart-walmart)


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "How do you correlate logs across services and threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Put the trace ID on every line, from Micrometer Tracing or the OpenTelemetry agent. Trace context is propagated across HTTP, messaging and executors, so it follows the work. Measured: the agent's `trace_id` was right on every executor hop, including a thread with a leaked MDC. It was missing only on bare threads.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q3-how-do-you-correlate-logs-across-services-and-threads-l2l3-flipkart-razorpay)

??? question "What does Logback's AsyncAppender do when the queue fills?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Below 20% free capacity (`discardingThreshold`, default queue/5), it silently drops TRACE, DEBUG and INFO. When the queue is full, it blocks the caller, or with `neverBlock` drops any event. Measured under a burst: 29–30% of INFO delivered with the defaults; with `neverBlock`, 9% of WARN.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q5-what-does-logbacks-asyncappender-do-when-the-queue-fills-l3-amazon-walmart)

??? question "How do you control logging cost?"
    <span class="badge badge-l3">L3</span> Flipkart · Meesho

    Fewer bytes, not faster encoders. JSON costs about 1–2 µs per line in CPU. Volume costs storage and ingest money. Exclude unused fields, cap stack traces, sample INFO for successful requests on hot paths, use metrics for counts instead of a line per event, and set retention by data class.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q10-how-do-you-control-logging-cost-l3-flipkart-meesho)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "What is a burn rate?"
    <span class="badge badge-l3">L3</span> Flipkart · PhonePe

    The error ratio divided by the budget: how many times faster than "exactly on budget" you are failing. Burn rate 1 spends the budget in the window; 14.4 spends 2% of a 30-day budget in an hour.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q3-what-is-a-burn-rate-l3-flipkart-phonepe)

??? question "Percentile or ratio for latency SLOs?"
    <span class="badge badge-l3">L3</span> Walmart · Myntra

    A ratio. Percentiles can't be averaged across windows or instances and only say pass or fail. A ratio gives a budget and a burn rate: 96.96% within 300 ms is 3× the budget; 89.85% is 10×.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q9-percentile-or-ratio-for-latency-slos-l3-walmart-myntra)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "Should the liveness probe check the database?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    No. A restart can't fix the database, and every replica fails together. Measured: during a 60-second outage, every pod restarted twice, `/menu` (no database) failed more than half the time, and orders came back 14–95 seconds after the database. Keep shared dependencies in `/actuator/health` for monitoring.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q2-should-the-liveness-probe-check-the-database-l2-swiggy-flipkart)

??? question "What timeouts do health checks need?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Each check needs its own timeout below the probe's (1 second by default), running in parallel, with a timeout counted as DOWN. Otherwise one slow dependency makes the endpoint slow, the probe times out, and that counts as a failure. Cache expensive checks for a few seconds when many callers poll.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q9-what-timeouts-do-health-checks-need-l3-amazon-walmart)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "Virtual threads or WebFlux?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    For request/response services with blocking I/O, virtual threads: the same capacity and tail (measured: both about 1,490/s, p99 279–411 ms) with plain code, stack traces and blocking libraries. WebFlux for streaming, backpressure, WebSockets and gateways, or when the stack is already reactive end to end.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q2-virtual-threads-or-webflux-l3-swiggy-flipkart)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "How big should the heap be in a container?"
    <span class="badge badge-l3">L3</span> Flipkart

    Big enough for the live set with headroom for churn, small enough that heap + native memory fit. Measured in 1 GiB with a 350 MB live set: 25% failed at startup, 50–75% caused full GCs (p99 0.5–0.7 s), 90% worked until 150 MB of direct buffers got the container OOMKilled.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q3-how-big-should-the-heap-be-in-a-container-l3-flipkart)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "Why didn't adding pods increase throughput?"
    <span class="badge badge-l3">L3</span> Flipkart

    A shared dependency was saturated. Measured: alone, 1, 2 and 4 copies served 35, 62–66 and 121–132 requests a second; behind a dependency allowing 50 a second, 4 copies served 50, each at a third of capacity. Find and scale the bottleneck, and autoscale on the signal it produces.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q5-why-didnt-adding-pods-increase-throughput-l3-flipkart)


**[69. High-Throughput Kafka Tuning](../performance-scalability/ch69-kafka-throughput-tuning.md)**

??? question "Which compression codec?"
    <span class="badge badge-l3">L3</span> Flipkart

    lz4 or snappy for speed, zstd when storage and network matter more, never gzip at volume. Measured on JSON order events: lz4 and snappy 4.4–4.5×, zstd 6.75× at about 1.5× lz4's CPU, gzip 7.31× at an eighth of the throughput. Compression also cuts broker work, since the broker stores batches as they are.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q3-which-compression-codec-l3-flipkart)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "Why are Kubernetes clusters usually over-provisioned?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    Nodes are bought for requests, and requests are guesses padded "to be safe" and copied from templates. Measured: workloads used 3–10% of their requested CPU and 5–21% of their memory; right-sized, the fleet needed 8–9 nodes instead of 32.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q2-why-are-kubernetes-clusters-usually-over-provisioned-l3-swiggy-flipkart)


### Resilience


**[42. Failure Modes & Cascading Failures](../resilience/ch42-failure-modes.md)**

??? question "Why does latency jump near capacity?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Queueing: waiting time grows like 1/(1−ρ). Measured: p99 was 4.5× the service time at 52% utilisation and 12× at 97%; mean latency tripled between 80% and 97%. Keep headroom so that losing a zone or a slow dependency doesn't push you past ~80%.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q2-why-does-latency-jump-near-capacity-l2l3-amazon-flipkart)

??? question "What is a metastable failure?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Google-/Amazon-style rounds · Flipkart

    A failure kept alive by the system's own reactions after the trigger is gone. A brief slowdown grows queues; requests time out and are retried; the extra load keeps queues long, so every request times out. Measured: 0% of users served for 30 s after a 5-second trigger. Breaking the loop takes shedding stale work, budgets and deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q4-what-is-a-metastable-failure-l3l4-google-amazon-style-rounds-flipkart)

??? question "How should a server shed load?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    Cheaply and early, by what predicts failure: queue time and the caller's deadline. Bounded concurrency with fast rejection, then drop queued work that has waited longer than the callers can wait. Measured: shedding at the client's timeout (300 ms) didn't recover; at 150 ms the service recovered the moment the trigger ended.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q6-how-should-a-server-shed-load-l3-flipkart-swiggy)

??? question "Why does fan-out hurt tail latency?"
    <span class="badge badge-l3">L3</span> Google-style rounds · Flipkart

    A request that waits for *k* calls is slow if any is slow: 1 − (1 − p)ᵏ. With 5% slow calls, fan-out 10 made 41–42% of requests slow (measured). Reduce fan-out, balance by load, hedge idempotent reads, and set per-call deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q10-why-does-fan-out-hurt-tail-latency-l3-google-style-rounds-flipkart)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "Count-based or time-based window?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    A count window reacts in "N calls", which is fast for busy endpoints and slow for quiet ones: measured, the 100-call default opened in 0.5–0.6 s at 100/s and 7.5–10 s at 5/s. A time window judges the same span at any rate (5 s opened in 2–4 s at both); add a minimum number of calls so a few failures on a quiet endpoint don't open it.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q2-count-based-or-time-based-window-l3-flipkart-swiggy)

??? question "Why doesn't my circuit breaker open when the dependency is slow?"
    <span class="badge badge-l3">L3</span> Amazon · Walmart

    Slow successes are only counted as slow above `slowCallDurationThreshold`, which defaults to 60 s, and the slow-call rate threshold defaults to 100%. Measured: 20 seconds of 3-second answers never opened a default breaker. Set the slow-call threshold below your callers' timeout (FoodFleet: 1 s at 50%).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q3-why-doesnt-my-circuit-breaker-open-when-the-dependency-is-slow-l3-amazon-walmart)

??? question "Where should the fallback go?"
    <span class="badge badge-l3">L3</span> Flipkart · Myntra

    On the outermost annotation (`@Retry`), so it runs only when everything else has given up. On `@CircuitBreaker` it catches the first failure inside the retry, and the retry never runs: measured 68–70% fresh results against 97–98%.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q5-where-should-the-fallback-go-l3-flipkart-myntra)

??? question "How do you size a bulkhead?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Flipkart

    Little's law: concurrent calls = throughput × latency. 400 calls/s at 50 ms needs 20; add headroom for the latency you accept, not the latency of a broken dependency. Reject when full (wait 0). Measured: a bulkhead of 32 held a slow dependency to 32 calls in progress where an unbounded caller reached 146–166.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q11-how-do-you-size-a-bulkhead-l3l4-amazon-flipkart)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "How do you choose a timeout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    From two numbers: the dependency's latency distribution (a ceiling a little above its p99.9, so healthy calls aren't cut off) and the caller's remaining deadline (never wait longer than the caller will). Use the smaller, minus a reserve for your own work, and skip the call if what's left is too small to succeed.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q1-how-do-you-choose-a-timeout-l2l3-amazon-flipkart)

??? question "Why not just retry with a short timeout?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    The abandoned attempt keeps running, so a short timeout multiplies load exactly when the dependency is slow. At 85% load, 100 ms timeouts with retries sent 5.6–8.2 calls per user and served 20–49% (measured); a 300 ms timeout that only fired on real stalls served 100%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q4-why-not-just-retry-with-a-short-timeout-l3-flipkart-goldman-sachs)

??? question "How do you shed load by priority?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    Mark requests at the edge (checkout critical, browsing sheddable), and give each class a guaranteed share of the admission limit that applies only when the limit is reached, so spare capacity is still shared. Measured at 2× overload: 79–81% of checkouts served instead of about 50%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q8-how-do-you-shed-load-by-priority-l3-swiggy-flipkart)

??? question "What is a brownout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Myntra · Amazon

    Turning optional features down automatically when their dependencies struggle: a controller sets the share of requests that use the feature from how many recent calls were slow. It removes load, which a timeout alone doesn't: timeouts left recommendations at 0% for 15 s after a spike; the dimmer was back to 77–81%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q10-what-is-a-brownout-l2l3-myntra-amazon)

??? question "What is backpressure?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Confluent-style rounds · Walmart

    The consumer controlling how fast it receives: in `Flow`, by requesting *n* items; in Kafka, by polling. When the buffer is full the producer blocks or drops; buffering without bound only delays the failure. Measured: an unbounded queue aged events to 10 s; a 256-item buffer kept them under 0.3 s.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q11-what-is-backpressure-l2l3-confluent-style-rounds-walmart)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "What is a steady-state hypothesis?"
    <span class="badge badge-l3">L3</span> Netflix-style rounds · Flipkart

    A measurable statement of normal behaviour in user terms (99% of menu requests succeed within 300 ms) and a prediction that it holds under a fault. Using user-facing metrics keeps the experiment honest: internal metrics can look fine while users suffer.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q3-what-is-a-steady-state-hypothesis-l3-netflix-style-rounds-flipkart)

??? question "Why did a circuit breaker fail a chaos experiment?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Walmart

    A breaker counts outcomes, and a call with no read timeout into a black hole never has one. Measured: no opening in 20 s, about 2,000 calls stuck, users failing in 19 of 20 windows; with a 250 ms read timeout the same experiment held. Timeouts are what feed a breaker.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q7-why-did-a-circuit-breaker-fail-a-chaos-experiment-l3l4-goldman-sachs-walmart)

??? question "What is a game day?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · PayPal

    A planned session where a team runs one or more experiments together, with observers, a runbook and a stop button, practising the system's behaviour and the team's response (detection, decisions, communication), followed by a written review with actions.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q11-what-is-a-game-day-l2l3-flipkart-paypal)


**[46. Disaster Recovery & Multi-Region](../resilience/ch46-disaster-recovery.md)**

??? question "Synchronous or asynchronous replication?"
    <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Synchronous loses nothing but every commit waits a round trip to the standby; asynchronous is free but loses what the standby hasn't received. Measured: in another zone, sync cost 1.3–1.7 ms per commit against 0.9–1.0 ms and lost nothing; across a 60 ms round trip each commit took ~63 ms; async lost 85–278 commits when the region was lost. Hence sync to nearby zones, async to far regions.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q2-synchronous-or-asynchronous-replication-l3-flipkart-razorpay)

??? question "What limits a restore's RPO and RTO?"
    <span class="badge badge-l3">L3</span> Walmart · Flipkart

    RPO: how far the WAL archive trails the database (`archive_timeout`, or near zero with streamed WAL). RTO: copying the base backup and replaying the WAL since, which grows with size and write volume. Measured: `archive_timeout = 60 s` lost 298–317 orders (the last 15–16 s), 10 s lost 99–119, `pg_receivewal --synchronous` none; restoring 0.75 GB plus 0.5 GB of WAL took about 5 s on local disk.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q9-what-limits-a-restores-rpo-and-rto-l3-walmart-flipkart)


### Security


**[35. Security Fundamentals & Threat Modeling](../security/ch35-security-fundamentals.md)**

??? question "What is the most common API vulnerability, and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Broken object-level authorization (OWASP API1): authenticated callers acting on objects that aren't theirs by changing an id. Check ownership on every operation in the service, using the authenticated caller and the stored object, never ids from the request; answer 404 for objects the caller can't see; test the full role × action × state matrix.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q2-what-is-the-most-common-api-vulnerability-and-how-do-you-prevent-it-l2l3-flipkart-amazon)

??? question "Where should authorization happen in a microservice system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP

    The gateway authenticates and applies coarse rules; the service that owns the data makes object, property and function decisions, because only it knows who owns what. Services re-verify tokens (zero trust); the database restricts by credentials and, optionally, row-level security as defence in depth.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q8-where-should-authorization-happen-in-a-microservice-system-l3l4-flipkart-sap)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "Walk through the authorization code flow with PKCE."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    The client creates a random verifier and sends its SHA-256 hash with the authorization request; the user signs in; the authorization server redirects back with a one-time code; the client redeems the code with the verifier at the token endpoint and gets tokens. A code captured on the way back is useless without the verifier.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q1-walk-through-the-authorization-code-flow-with-pkce-l2l3-flipkart-cognizant)

??? question "JWT or opaque tokens?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · JP Morgan

    JWTs verify locally (measured ~0.05–0.6 ms, no network) and keep working when the issuer is down, but can't be revoked before expiry (a revoked 15-minute token works 450 s on average). Opaque tokens are introspected (a network call per request or cache miss) and revocable immediately. Use short JWTs broadly and introspection where revocation must be immediate.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q5-jwt-or-opaque-tokens-l3l4-flipkart-jp-morgan)

??? question "Token relay or token exchange?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Relay passes the user's token on, so every downstream service and log holds a credential for everything the user's app may do. Exchange (RFC 8693) gets a new token for one audience, minimum scopes, minutes, with an `act` claim recording the acting service. Relay is acceptable only within one trust boundary with audience-restricted tokens.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q8-token-relay-or-token-exchange-l3l4-goldman-sachs-flipkart)

??? question "Where should a browser app keep tokens?"
    <span class="badge badge-l3">L3</span> Flipkart · any

    Preferably not in the browser: a backend-for-frontend runs the code flow, keeps tokens server-side, and gives the browser an HttpOnly, Secure, SameSite session cookie. If tokens must be in the browser, keep them in memory, short-lived, with refresh token rotation, never in local storage.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q10-where-should-a-browser-app-keep-tokens-l3-flipkart-any)


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "How do you map Keycloak roles to Spring Security authorities?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    A `Converter<Jwt, Collection<GrantedAuthority>>` on the `JwtAuthenticationConverter` that reads `realm_access.roles` (and client roles if used) and the `scope` claim, keeping only roles the application defines. Spring Boot 4.1 also offers `authorities-claim-expressions` for simple cases.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q3-how-do-you-map-keycloak-roles-to-spring-security-authorities-l3-sap-flipkart)

??? question "How do you test security rules?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    MockMvc with spring-security-test's `jwt()` post-processor and realistic claims: no token → 401, wrong scope → 403, other users' objects → 404, missing role → 403, unknown endpoint → 403. Plain-Java rule classes get exhaustive unit tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q8-how-do-you-test-security-rules-l2l3-flipkart-sap)

??? question "How do you handle the 'authorization server is down' case?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    JWT resource servers keep working with cached JWKS until tokens expire; new logins and refreshes fail. Make the identity provider highly available, keep JWKS caching sensible, use short tokens, and alert on token errors.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q11-how-do-you-handle-the-authorization-server-is-down-case-l3-jp-morgan-flipkart)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "Why isn't a valid client certificate enough to allow a call?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    It proves identity, not permission. Every workload in the trust domain has a valid certificate; the service must decide which of them may call which route (peer policy, deny by default), and the token decides what may be done for which user.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q3-why-isnt-a-valid-client-certificate-enough-to-allow-a-call-l3-goldman-sachs-flipkart)

??? question "Service mesh or application-level mTLS?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon

    A mesh gives mTLS and coarse policies everywhere without code changes, but the application sees no client certificate (certificate-bound tokens and per-request certificate checks don't work there) and Istio's defaults are permissive. Application-terminated mTLS gives the service the peer identity for fine-grained decisions and audit. Many platforms use the mesh broadly and application checks for sensitive services.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q7-service-mesh-or-application-level-mtls-l3l4-flipkart-amazon)

??? question "What goes wrong with Spring's client-credentials interceptor?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    By default it uses the current user as the principal and stores authorized clients per principal, so inside user requests it fetches a token per user (measured: 20,000 token requests and 5,000 stored tokens instead of 4). Use the service-oriented manager and a principal resolver that yields one fixed principal. Renewal has no lock, so many threads renew at once at expiry.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q9-what-goes-wrong-with-springs-client-credentials-interceptor-l3-sap-flipkart)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "Are Kubernetes Secrets secure?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Not by default: they are base64-encoded and stored unencrypted in etcd, and anyone who can create pods in a namespace can read its Secrets. Turn on encryption at rest (KMS v2), restrict RBAC, mount them as read-only volumes, and keep the source of truth in a secrets store.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q2-are-kubernetes-secrets-secure-l2-flipkart-amazon)

??? question "How do you rotate a database password without downtime?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Two valid credentials at once: change the account (or key) not in use, roll it out, switch, then change the other. In-place rotation fails until every instance has the new value. Measured: 14–16% of logins failed in place, 0 with two accounts. Dynamic credentials do this automatically.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q5-how-do-you-rotate-a-database-password-without-downtime-l3-amazon-flipkart)

??? question "How do you keep secrets out of logs and dumps?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    A value type that masks itself, structured logging that never logs headers or credentials, actuator's `show-values: never`, and treating heap dumps as secrets. Measured: a `String` password was in every heap dump; only wiped `char[]`s were absent.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q9-how-do-you-keep-secrets-out-of-logs-and-dumps-l2l3-flipkart-any)

??? question "What happens when Vault/OpenBao is down?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    Running services keep working with their current credentials until their leases end; new pods can't start and renewals fail. Run it highly available with auto-unseal, alert on renewal failures, size lease TTLs to cover a realistic outage, and test the failure.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q11-what-happens-when-vaultopenbao-is-down-l3-jp-morgan-flipkart)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "Fixed window, sliding window or token bucket?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart

    Fixed windows are cheap but allow twice the limit across a boundary (measured: 200 in a minute for a limit of 100). Sliding window counters fix most of that with two counters. A token bucket (GCRA) gives a steady rate plus a deliberate burst; the burst size is part of the limit (a bucket of 100 allowed 199 in a minute, a bucket of 10, 109).

    [Full answer in chapter 40](../security/ch40-api-security.md#q1-fixed-window-sliding-window-or-token-bucket-l2-amazon-flipkart)

??? question "Where should rate limiting live: CDN, gateway or service?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    In layers. Per-address limits at the edge stop floods and credential stuffing cheaply; per-client and per-tenant limits need the authenticated identity, so they belong after authentication (gateway or service); business limits (one coupon per customer) belong in the service. Know your tools' behaviour: Spring Cloud Gateway's Redis limiter refills once a second and fails open.

    [Full answer in chapter 40](../security/ch40-api-security.md#q3-where-should-rate-limiting-live-cdn-gateway-or-service-l3-sap-flipkart)

??? question "How do you configure CORS for an SPA on www calling api?"
    <span class="badge badge-l2">L2</span> Flipkart · TCS

    An exact allow-list of origins (`https://www.foodfleet.in`), credentials only if cookies are needed, the methods and headers actually used, `Vary: Origin`, a preflight max age. Not `*` with credentials (browsers refuse it), not reflection, not suffix or pattern matching, never `null`. Better still, serve the SPA and its BFF from one origin and need no CORS.

    [Full answer in chapter 40](../security/ch40-api-security.md#q9-how-do-you-configure-cors-for-an-spa-on-www-calling-api-l2-flipkart-tcs)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "Your build passed the vulnerability scan last month. Are you safe?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Only for what was known last month. New advisories arrive daily: Keycloak 24.0.0 had 0 known findings on release day, 14 three months later, 196 today (measured). Rescan deployed SBOMs daily and act on what has a fix.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q2-your-build-passed-the-vulnerability-scan-last-month-are-you-safe-l3-amazon-flipkart)

??? question "Why pin GitHub Actions by SHA?"
    <span class="badge badge-l2">L2</span> Flipkart · Accenture

    Tags are mutable: in March 2025 the `tj-actions/changed-files` tags were moved to a commit that dumped runner secrets into logs. A SHA can't be moved. Pair it with Renovate/Dependabot updates and per-job least-privilege `permissions`.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q8-why-pin-github-actions-by-sha-l2-flipkart-accenture)


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "Pyramid or honeycomb for a microservice?"
    <span class="badge badge-l3">L3</span> Flipkart · Uber

    Measure where the service's bugs are. In the experiment, a pyramid without component tests caught 16 of 20 and a honeycomb without unit tests 15; each lost a different kind. Rules need unit tests at their edges; wiring needs integrated tests. Most microservices need a strong middle and unit tests for the rules they have.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q5-pyramid-or-honeycomb-for-a-microservice-l3-flipkart-uber)

??? question "What should run on every commit, and what before release?"
    <span class="badge badge-l3">L3</span> Walmart

    Every commit: unit and contract tests, in under a minute or two. Before merge: repository and component tests. Before release: a few end-to-end tests and performance checks (chapters 64–65). The point is fast feedback for most bugs and full coverage before users see a change.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q11-what-should-run-on-every-commit-and-what-before-release-l3-walmart)


**[62. Unit, Slice & Testcontainers Tests](../testing/ch62-unit-slice-integration-tests.md)**

??? question "Rollback, truncate or a new database per test?"
    <span class="badge badge-l3">L3</span> Flipkart

    Rollback for data slices (0.30–0.34 ms per test) when everything happens on the test's connection; truncate for integrated tests (7.8–7.9 ms), which works whatever the code does; a database per test from a template (51–73 ms) for schema changes or parallel tests.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q7-rollback-truncate-or-a-new-database-per-test-l3-flipkart)


**[63. Contract Testing](../testing/ch63-contract-testing.md)**

??? question "Can contract tests replace end-to-end tests?"
    <span class="badge badge-l3">L3</span> Flipkart

    For API compatibility, mostly. Measured: contracts caught the same 7 of 9 breaks as an E2E suite, without deploying anything together. Keep a few E2E journeys for what contracts can't see: whole flows, configuration and infrastructure (chapter 64).

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q8-can-contract-tests-replace-end-to-end-tests-l3-flipkart)


**[64. Component & End-to-End Testing](../testing/ch64-component-e2e-testing.md)**

??? question "Shared staging or preview environments?"
    <span class="badge badge-l3">L3</span> Flipkart

    Preview environments for automated tests; staging, if kept, for people. Measured: four pipelines sharing staging passed 1 of 12 suite runs with fixed data and 5 of 12 with unique data; fresh environments passed 12 of 12.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q5-shared-staging-or-preview-environments-l3-flipkart)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "What's the difference between a closed and an open load model?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    A closed model has a fixed number of users who each wait for a response before sending again; an open model sends requests at a rate regardless. Real users of a web service are open. Measured: against a freezing service, the closed model's p99 was 15 ms and the open model's 942 ms.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q1-whats-the-difference-between-a-closed-and-an-open-load-model-l3-flipkart-swiggy)
