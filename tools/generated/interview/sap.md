### Architecture & Patterns


**[9. Hexagonal, Clean & Onion Architecture](../architecture-patterns/ch09-hexagonal-clean-architecture.md)**

??? question "Explain hexagonal architecture."
    <span class="badge badge-l2">L2</span> Cognizant · TCS · SAP

    The application core (domain and use cases) is isolated from technology. It talks to the outside world through **ports**, interfaces it owns; **adapters** connect ports to technologies. Driving adapters (REST, messaging, tests) call driving ports; driven adapters (database, external APIs) implement driven ports. Dependencies point inward, so the core can be tested and changed without its infrastructure.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q1-explain-hexagonal-architecture-l2-cognizant-tcs-sap)

??? question "Hexagonal vs onion vs clean architecture?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    Same core idea (inward-pointing dependencies, framework-free core) drawn differently. Hexagonal emphasises the symmetry of inbound and outbound adapters; onion emphasises concentric layers inside the core; clean architecture adds use cases as explicit objects and names the dependency rule. Most teams mix the vocabulary.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q3-hexagonal-vs-onion-vs-clean-architecture-l3-sap-goldman-sachs)

??? question "Should JPA entities be your domain model?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    For core domains, usually not: JPA requires no-arg constructors, mutable fields and proxies, and lazy loading leaks persistence concerns into rules. Map between a clean domain model and persistence in the adapter, or use Spring Data JDBC, which respects aggregate boundaries. For simple CRUD services, JPA entities as the model are fine.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q7-should-jpa-entities-be-your-domain-model-l3-goldman-sachs-sap)

??? question "What's an anti-corruption layer in a hexagonal service?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    The translator inside a driven adapter that converts an external model (a payment provider's statuses and error codes) into the core's model. It's where interpretation decisions live, with fail-safe defaults for unknown values.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q8-whats-an-anti-corruption-layer-in-a-hexagonal-service-l3-jp-morgan-sap)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "Should the gateway aggregate responses?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    Usually not. Aggregation is client-specific, so it belongs in a BFF owned by the client team. A shared gateway that aggregates becomes a monolith that every team must change. Keep the gateway generic.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q4-should-the-gateway-aggregate-responses-l3-amazon-sap)

??? question "How does the gateway handle authentication?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    It validates tokens (signature, expiry, audience) and rejects unauthenticated calls early, then forwards the token (token relay) or a derived identity to services. Services still check authorization on their own resources. Client-supplied identity headers are stripped. [Chapter 36](../security/ch36-oauth2-oidc-jwt.md) covers the flows.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q7-how-does-the-gateway-handle-authentication-l3-goldman-sachs-sap)

??? question "How do you version APIs at the edge?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Public paths are stable; versions are carried in a header or path segment and routed by the gateway or handled by the service's own API versioning (chapter 8). BFF endpoints are often versioned by path to match app releases, because old app versions live for months.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q10-how-do-you-version-apis-at-the-edge-l2l3-sap-cognizant)


**[11. Service Discovery Patterns](../architecture-patterns/ch11-service-discovery-patterns.md)**

??? question "Self-registration vs third-party registration?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Self-registration: the instance registers itself and heartbeats (Eureka). Third-party: an external component registers instances based on observed state (Kubernetes from pod readiness, Consul agents). Third-party registration removes discovery code from services and reflects real readiness.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q3-self-registration-vs-third-party-registration-l2-sap-cognizant)

??? question "What's Eureka's self-preservation mode?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    When renewals drop below a threshold (85% of expected by default), the Eureka server assumes a network problem and stops evicting instances, protecting against mass eviction during partitions. The trade-off: dead instances remain listed until renewals recover, so clients need timeouts and retries.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q8-whats-eurekas-self-preservation-mode-l3-jp-morgan-sap)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

??? question "How do you join data across services?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    API composition for display queries: the owner of the list is queried first, then other owners are called in batch, in parallel, and joined in memory. For queries that filter or sort across owners or are heavy, build a CQRS read model from events. For analytics, a warehouse fed by events or CDC.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q3-how-do-you-join-data-across-services-l2l3-flipkart-sap)

??? question "How would you split a shared database in a running system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP · Flipkart

    Map table ownership; audit real statements per service (one DB user each); remove foreign writes first, then foreign reads (APIs, local copies, composition); drop cross-domain foreign keys; move tables into per-service schemas with grants; separate instances where needed. Each step is small and measurable.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q6-how-would-you-split-a-shared-database-in-a-running-system-l3l4-goldman-sachs-sap-flipkart)

??? question "Schema per service or database per service?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    Schema per service with one user each gives enforced ownership at low cost. Separate databases when a service needs different technology, failure isolation, compliance separation, independent scaling or its own upgrade schedule.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q8-schema-per-service-or-database-per-service-l3-jp-morgan-sap)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "What is CQRS?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Separating the model that handles commands (enforcing rules on writes) from the model(s) that answer queries. Read models are shaped per query and can live in different stores, kept up to date from the write side, usually through events and therefore eventually consistent.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q1-what-is-cqrs-l2l3-cognizant-sap-flipkart)

??? question "What are snapshots and when do you need them?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    Cached aggregate state at a version, so loading replays only later events. Needed for long streams; they're an optimisation that can be deleted and rebuilt, and must be versioned with the code that reads them.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q6-what-are-snapshots-and-when-do-you-need-them-l3-amazon-sap)

??? question "How does event sourcing work with GDPR's right to erasure?"
    <span class="badge badge-l4">L4</span> SAP · banks with EU operations

    Keep personal data out of events (reference ids, store PII separately), or encrypt it with a per-person key and delete the key (crypto-shredding). Design this before the first event is stored.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q8-how-does-event-sourcing-work-with-gdprs-right-to-erasure-l4-sap-banks-with-eu-operations)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

??? question "What is a saga?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A sequence of local transactions across services, where each step has a compensating action. If a step fails, the compensations of completed steps run in reverse order. It provides eventual atomicity without distributed locks.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q1-what-is-a-saga-l2l3-cognizant-sap-flipkart)

??? question "Orchestration vs choreography?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · SAP

    Orchestration: a central coordinator tells participants what to do and tracks state; easier to understand, monitor and change. Choreography: services react to each other's events; looser coupling, but the flow is implicit and compensation is spread out. Orchestrate flows with many steps or money; choreograph simple reactions.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q3-orchestration-vs-choreography-l3-amazon-flipkart-sap)

??? question "Sagas lack isolation. What problems does that cause and how do you handle them?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Dirty reads, lost updates and fuzzy reads of intermediate state. Countermeasures: semantic locks (PENDING states), commutative updates, re-reading values, pessimistic views (copying values into the saga), ordering by value, and reservations with expiry.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q6-sagas-lack-isolation-what-problems-does-that-cause-and-how-do-you-handle-them-l4-goldman-sachs-sap)


**[15. Transactional Outbox, Inbox & CDC](../architecture-patterns/ch15-outbox-inbox-cdc.md)**

??? question "What is the dual-write problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant · SAP

    Writing to two systems (a database and a broker) without a transaction covering both. A failure between the writes leaves them inconsistent: commit-then-publish loses events, publish-then-commit invents them.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q1-what-is-the-dual-write-problem-l2l3-flipkart-cognizant-sap)

??? question "Polling publisher vs CDC?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP · Goldman Sachs

    Polling is simple, in your code, works with any database, adds query load and latency of the poll interval. CDC (Debezium) reads the WAL: low latency, commit order, no table queries, but needs Kafka Connect, replication slots, monitoring of WAL retention and schema-change handling.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q4-polling-publisher-vs-cdc-l3l4-flipkart-sap-goldman-sachs)

??? question "How do sagas use the outbox?"
    <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    The orchestrator writes its saga-log update and the command for the next participant to the outbox in one transaction, so it can never record "sent" without sending, or send without recording. Participants use an inbox to deduplicate commands and write their replies through their own outbox.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q11-how-do-sagas-use-the-outbox-l4-sap-goldman-sachs)


**[16. Strangler Fig, ACL & Monolith Migration](../architecture-patterns/ch16-strangler-fig-migration.md)**

??? question "What is the strangler fig pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Incrementally replacing a legacy system by routing one capability at a time from the old system to a new one through a facade, until the old code can be deleted. Each step is small, reversible and gets production feedback, unlike a big-bang rewrite.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q1-what-is-the-strangler-fig-pattern-l2l3-cognizant-sap-flipkart)

??? question "What is an anti-corruption layer and where does it live?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    A translation layer between a new model and a legacy (or external) one, so the new model isn't shaped by the old one's codes and quirks. It lives in the new service at its boundary with the legacy system, often in both directions (reading legacy data, calling legacy functions). Untranslatable data should be quarantined, not guessed.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q2-what-is-an-anti-corruption-layer-and-where-does-it-live-l3-sap-goldman-sachs)

??? question "How do you migrate the data?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Read legacy data through an ACL first; then backfill and sync with CDC; reconcile continuously; switch write ownership once (never two writers); have the monolith read from the new owner; retire legacy tables when audits show no readers.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q7-how-do-you-migrate-the-data-l4-goldman-sachs-sap)

??? question "Which capability would you extract first?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Cognizant · Flipkart

    One with a clear boundary, few dependencies, its own data, mostly reads or calculations (so it can be shadowed), frequent change (quick payback), and a team to own it. Not the core entity everything joins against.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q8-which-capability-would-you-extract-first-l3l4-sap-cognizant-flipkart)


**[17. Sidecar, Ambassador & Adapter](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)**

??? question "What is the sidecar pattern?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A helper container deployed in the same pod as the application, sharing its network and volumes, that adds capabilities (logging, proxying, config reload, telemetry) without changing the app's code. It has its own image, language and release cycle.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q1-what-is-the-sidecar-pattern-l2-cognizant-sap-flipkart)

??? question "Ambassador vs adapter?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Both are sidecars. An ambassador is an outbound proxy representing a remote service to the app (credentials, retries, routing). An adapter presents the app or a legacy system to the outside world in a standard form (metrics format, API shape).

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q2-ambassador-vs-adapter-l2l3-sap-cognizant)

??? question "What is Dapr?"
    <span class="badge badge-l3">L3</span> startups · SAP

    A sidecar runtime that exposes infrastructure building blocks (state stores, pub/sub, bindings, secrets, workflows) through HTTP/gRPC APIs, so apps in any language use one API and the sidecar talks to the actual backend.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q8-what-is-dapr-l3-startups-sap)


**[18. Resilience Patterns](../architecture-patterns/ch18-resilience-patterns.md)**

??? question "Explain the circuit breaker states."
    <span class="badge badge-l2">L2</span> SAP · Cognizant · Flipkart

    Closed: calls pass and outcomes are recorded; when the failure (or slow-call) rate over a window exceeds a threshold, it opens. Open: calls fail immediately for a wait duration. Half-open: a limited number of trial calls; if they succeed it closes, otherwise it reopens.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q2-explain-the-circuit-breaker-states-l2-sap-cognizant-flipkart)

??? question "In what order do you compose these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Commonly fallback outermost, then bulkhead, retry, circuit breaker, timeout innermost (Resilience4j's Spring annotations apply Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(call)))))). Whether the breaker sees attempts or operations depends on whether it is inside or outside retry; choose deliberately.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q8-in-what-order-do-you-compose-these-patterns-l3l4-goldman-sachs-sap)

??? question "Hystrix is in maintenance. What do you use in Spring today?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Spring Framework 7's `@Retryable` and `@ConcurrencyLimit` for retries and concurrency limits; Resilience4j (directly or via Spring Cloud CircuitBreaker) for breakers, rate limiters and time limiters; client-level timeouts on HTTP service clients.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q9-hystrix-is-in-maintenance-what-do-you-use-in-spring-today-l2l3-cognizant-sap)


**[19. Event-Driven Architecture](../architecture-patterns/ch19-event-driven-architecture.md)**

??? question "What does 'event-driven' mean?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    Components communicate by publishing facts that happened, without knowing who reacts. It covers different styles: event notification (thin events, consumers call back), event-carried state transfer (events carry state; consumers keep copies) and event sourcing (the event log is the system of record).

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q1-what-does-event-driven-mean-l2-cognizant-sap-flipkart)

??? question "Event vs command?"
    <span class="badge badge-l2">L2</span> Amazon · SAP

    An event is a past fact broadcast to anyone interested; the publisher doesn't expect anything. A command asks one receiver to do something, and it may refuse. Calling a command an event hides the coupling between sender and receiver.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q2-event-vs-command-l2-amazon-sap)

??? question "How do you evolve event schemas?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · JP Morgan

    Treat events as contracts: add optional fields only, never rename or repurpose; enforce compatibility with a schema registry and consumer contract tests; publish breaking changes as a new event type or topic alongside the old one until consumers migrate.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q7-how-do-you-evolve-event-schemas-l3l4-sap-jp-morgan)

??? question "How do you include personal data in events safely?"
    <span class="badge badge-l4">L4</span> JP Morgan · SAP

    Prefer not to: publish ids and let authorised consumers fetch details. If needed, minimise fields, encrypt sensitive ones (crypto-shredding for erasure), set retention deliberately, and document consumers for data-protection requests.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q10-how-do-you-include-personal-data-in-events-safely-l4-jp-morgan-sap)


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "What is a distributed monolith?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A system split into services that still can't be developed, deployed or fail independently, because of shared databases, shared domain libraries, synchronous chains or cycles. It has the costs of distribution without the benefits. The clearest evidence is services that are always released together.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q1-what-is-a-distributed-monolith-l2l3-cognizant-sap-flipkart)

??? question "When is a service too small?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    When its distribution costs (network hops, a pipeline, on-call, monitoring) exceed its value: typically when it is always changed or released with a neighbour, has a single caller that owns the use case, and has no different scaling, security or ownership needs. Merge it into the neighbour as a module.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q4-when-is-a-service-too-small-l3-sap-goldman-sachs)

??? question "Why are shared libraries risky in microservices?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    A library with domain classes couples every service to one model and one release cadence; upgrades become coordinated releases and versions drift. Share contracts (API and event schemas) instead; keep shared code to small, stable utilities and automate their upgrades.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q5-why-are-shared-libraries-risky-in-microservices-l3-jp-morgan-sap)

??? question "Is a shared database always an anti-pattern?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Non-owner writes almost always are. Non-owner reads are a weaker smell that can be a deliberate, temporary step (for example during migration) if they are tracked and burned down. Separate schemas on one database server, with no cross-access, are fine.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q7-is-a-shared-database-always-an-anti-pattern-l3l4-sap-goldman-sachs)


### Communication


**[21. Synchronous REST](../communication/ch21-rest-api-design.md)**

??? question "What makes an API RESTful?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Resources identified by URIs, a uniform interface (HTTP methods with their defined semantics), representations, stateless requests, cacheable responses, and ideally links between resources. In practice: nouns in URIs, methods and status codes used for what they mean.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q1-what-makes-an-api-restful-l1l2-cognizant-sap)

??? question "How do you prevent lost updates in a REST API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · SAP

    Optimistic concurrency: return an ETag (from a version column), require If-Match on updates, update with `where version = :expected`, return 412 when stale and 428 when If-Match is missing.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q5-how-do-you-prevent-lost-updates-in-a-rest-api-l2l3-jp-morgan-sap)

??? question "What is RFC 9457?"
    <span class="badge badge-l2">L2</span> SAP · Goldman Sachs

    Problem Details for HTTP APIs (obsoletes RFC 7807): a standard JSON error format with type, title, status, detail and instance, plus extensions. Spring supports it through `ProblemDetail` and `spring.mvc.problemdetails.enabled`.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q6-what-is-rfc-9457-l2-sap-goldman-sachs)

??? question "How do you version a REST API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Avoid versions for additive changes (clients ignore unknown fields). For breaking changes, version by header, media type or URI path, run versions side by side, announce deprecation with Deprecation/Sunset headers, and watch usage before removal. Spring Boot 4 supports this natively.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q9-how-do-you-version-a-rest-api-l2l3-sap-jp-morgan)

??? question "Is HATEOAS worth it?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    Partly. Links for pagination, self and state-dependent actions spare clients from building URLs and encoding business rules; full hypermedia-driven clients make sense mostly for long-lived public APIs with many unknown consumers.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q10-is-hateoas-worth-it-l3-sap-goldman-sachs)


**[22. HTTP Clients](../communication/ch22-http-clients.md)**

??? question "RestTemplate, RestClient, WebClient: which and why?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    RestClient (synchronous, fluent) for most cases; WebClient for reactive applications and streaming. RestTemplate's deprecation was announced in Spring Framework 7.0, with formal deprecation planned for 7.1 and removal in 8.0; migrate to RestClient.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q1-resttemplate-restclient-webclient-which-and-why-l1l2-cognizant-sap-flipkart)

??? question "What are HTTP service clients?"
    <span class="badge badge-l2">L2</span> SAP · Flipkart

    Interfaces annotated with `@HttpExchange` for which Spring generates the client on top of RestClient or WebClient. Since Spring Framework 7/Boot 4 they are organised in groups (`@ImportHttpServices`) configured by properties and configurers.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q2-what-are-http-service-clients-l2-sap-flipkart)

??? question "How do you handle error responses from another service?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    Map them in one place (a default status handler): RFC 9457 problems into typed exceptions carrying `type` and details, others into exceptions with status and a truncated body; decide per type whether to retry, fix input, or fail.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q7-how-do-you-handle-error-responses-from-another-service-l2-sap-amazon)

??? question "How would you migrate from Feign?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Replace `@FeignClient` with an `@HttpExchange` interface in an HTTP service group; Spring MVC annotations map to `@GetExchange` etc.; ErrorDecoder → status handler, RequestInterceptor → request interceptor, Retryer → `@Retryable`; timeouts move to `spring.http.serviceclient.<group>.*`. Migrate per client with contract tests.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q8-how-would-you-migrate-from-feign-l2l3-cognizant-sap)


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "What is gRPC, and how is it different from REST?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    An RPC framework: services and messages defined in `.proto` files, code generated for clients and servers, calls over HTTP/2 with Protocol Buffers payloads. REST models resources over HTTP with (usually) JSON and HTTP semantics such as caching. gRPC adds streaming, deadlines and a code-generated contract; REST is simpler to consume and works everywhere.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q1-what-is-grpc-and-how-is-it-different-from-rest-l1l2-cognizant-sap-flipkart)

??? question "What are the four RPC types? Give a use for each."
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Unary (get an order's status), server streaming (live tracking updates), client streaming (a rider app uploading GPS points, one summary back), bidirectional (dispatch negotiation or chat, both sides sending independently).

    [Full answer in chapter 23](../communication/ch23-grpc.md#q4-what-are-the-four-rpc-types-give-a-use-for-each-l2-sap-cognizant)

??? question "How do you handle errors in gRPC?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    Return a specific status code with a description (`NOT_FOUND`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`, `UNAVAILABLE`…) and, where useful, rich details (`google.rpc.Status` with `ErrorInfo` or `BadRequest`). Map exceptions centrally (Spring's `GrpcExceptionHandler`); clients decide on retry or fallback by code.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q6-how-do-you-handle-errors-in-grpc-l2-sap-amazon)

??? question "How would a browser call a gRPC service?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Browsers can't use raw gRPC (no control of HTTP/2 framing and trailers), so use gRPC-Web through a proxy (such as Envoy), the Connect protocol, or JSON transcoding at the gateway; or keep a REST/BFF layer at the edge, which is often simplest.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q9-how-would-a-browser-call-a-grpc-service-l2l3-sap-cognizant)

??? question "How do you implement cross-cutting concerns in gRPC with Spring?"
    <span class="badge badge-l2">L2</span> SAP · any

    Interceptors: server interceptors registered globally (`@GlobalServerInterceptor`) or per service, client interceptors per channel. Use them for auth, logging, metrics and propagating context in metadata; Spring Boot adds observation interceptors when Micrometer is present.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q11-how-do-you-implement-cross-cutting-concerns-in-grpc-with-spring-l2-sap-any)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "What is GraphQL, and what problem does it solve?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A query language and runtime for APIs: the server publishes a typed schema, and clients request exactly the fields they need, across related types, in one request. It addresses over-fetching (whole resources for a small screen), under-fetching (many round trips to compose a screen) and endpoint sprawl for many client screens.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q1-what-is-graphql-and-what-problem-does-it-solve-l1l2-cognizant-sap-flipkart)

??? question "How do `@SchemaMapping` and `@BatchMapping` differ in Spring for GraphQL?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    `@SchemaMapping` resolves a field for one parent at a time and can take field arguments. `@BatchMapping` receives all parents at once and returns a map (or ordered list) of values, registering a DataLoader underneath; it can't take field arguments, for which you use `BatchLoaderRegistry`.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q4-how-do-schemamapping-and-batchmapping-differ-in-spring-for-graphql-l2-sap-cognizant)

??? question "How are errors reported in GraphQL?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    In an `errors` array alongside `data`, each with a message, a `path` to the failed field and `extensions` such as a classification. Other fields still return, so responses can be partially successful, usually with HTTP 200. Map exceptions to error types centrally and monitor errors in responses, not just HTTP status.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q6-how-are-errors-reported-in-graphql-l2-sap-amazon)

??? question "What is GraphQL federation and when is it worth it?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    A supergraph composed from subgraphs owned by different services, with a router planning each query across them; entities like `Restaurant` can be extended by several subgraphs. It is worth it when many teams contribute to one client-facing graph; otherwise a single BFF graph is simpler to run and debug.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q9-what-is-graphql-federation-and-when-is-it-worth-it-l3l4-amazon-sap)

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

??? question "What is a poison message and how do you handle it?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    A message that can never be processed. Detect it (validation, deserialization errors), classify it as not retryable, and send it to a DLT with the error attached, so the partition or queue keeps moving. Retrying it in place blocks everything behind it.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q5-what-is-a-poison-message-and-how-do-you-handle-it-l2-sap-cognizant)

??? question "When would you choose RabbitMQ over Kafka?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    For task queues and work distribution with per-message acknowledgement, priorities, TTLs, delayed or routed delivery, and no need for replay or long retention; or when consumers need to scale independently of a partition count. Kafka for event streams with many subscribers, replay, high throughput and per-key ordering.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q11-when-would-you-choose-rabbitmq-over-kafka-l3-sap-cognizant)


**[26. WebSockets & Server-Sent Events](../communication/ch26-websockets-sse.md)**

??? question "What are the options for pushing updates from server to client?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    Short polling (ask on a timer), long polling (the server holds the request until there is news), Server-Sent Events (a streaming HTTP response of events) and WebSocket (a full-duplex connection after an HTTP upgrade); plus push notifications for mobile apps in the background.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q1-what-are-the-options-for-pushing-updates-from-server-to-client-l1l2-cognizant-sap-flipkart)

??? question "How does the WebSocket handshake work?"
    <span class="badge badge-l2">L2</span> SAP · any

    The client sends an HTTP GET with `Upgrade: websocket`, `Connection: Upgrade`, `Sec-WebSocket-Key` and `Sec-WebSocket-Version: 13`; the server responds `101 Switching Protocols` with `Sec-WebSocket-Accept` = Base64(SHA-1(key + a fixed GUID)). After that, both sides send frames; client frames are masked.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q4-how-does-the-websocket-handshake-work-l2-sap-any)

??? question "How do you avoid losing updates when a connection drops?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Give every event an id, keep a short replay buffer per stream, and have the client send its last id on reconnect (`Last-Event-ID` for SSE, a resume message for WebSocket). For state like positions, sending the latest state on reconnect may be enough.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q8-how-do-you-avoid-losing-updates-when-a-connection-drops-l2l3-sap-flipkart)

??? question "How do you authenticate WebSocket connections?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · SAP

    At the handshake: headers from native clients (or verified by the gateway), cookies or a short-lived ticket for browsers (which can't set custom headers on WebSocket). Store the identity in the session, check origins for browsers, authorise each subscription, and close or refresh sessions when tokens expire.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q10-how-do-you-authenticate-websocket-connections-l2l3-jp-morgan-sap)

??? question "How does Spring support SSE and WebSockets?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Spring MVC's `SseEmitter` (or WebFlux's `Flux<ServerSentEvent>`) for SSE; Spring WebSocket's `WebSocketHandler`, handshake interceptors and `ConcurrentWebSocketSessionDecorator` for raw WebSockets; and STOMP messaging over WebSocket with `@MessageMapping`, a simple in-memory broker or a broker relay to RabbitMQ/ActiveMQ for multi-instance fan-out.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q12-how-does-spring-support-sse-and-websockets-l2-sap-cognizant)


**[27. API Contracts & Schema Evolution](../communication/ch27-api-contracts.md)**

??? question "What is an API contract?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Everything consumers rely on: the shape of requests and responses, behaviour (status codes, errors, defaults, ordering), the meaning of fields, and non-functional expectations. Schema documents (OpenAPI, AsyncAPI, protobuf) capture the shape and part of the behaviour; meaning needs documentation and review.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q1-what-is-an-api-contract-l1l2-cognizant-sap)

??? question "Is adding a field a breaking change?"
    <span class="badge badge-l2">L2</span> Amazon · SAP

    In a response, not by the rules, but it breaks strict readers (Kotlin serialization without `ignoreUnknownKeys`, Jackson 2 outside Spring Boot). In a request, adding a required field breaks old clients. In this chapter's history, three "safe" additions broke strict consumers.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q3-is-adding-a-field-a-breaking-change-l2-amazon-sap)

??? question "Spring Cloud Contract or Pact?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Spring Cloud Contract: contracts usually live with the provider, generate provider tests and WireMock stubs for consumers; natural for Spring-only shops. Pact: consumers generate contracts from their tests, a broker stores them and answers "can I deploy?"; polyglot and consumer-led. Both test examples, not every possible response.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q6-spring-cloud-contract-or-pact-l3-sap-jp-morgan)

??? question "What is AsyncAPI, and why does an event-driven system need it?"
    <span class="badge badge-l2">L2</span> SAP · Goldman Sachs

    AsyncAPI documents event-driven APIs: channels (topics), operations (send/receive), messages, headers and payload schemas. Events are contracts between teams just like HTTP APIs; documenting them enables review, code generation, validation and discovery, alongside a schema registry for runtime compatibility.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q9-what-is-asyncapi-and-why-does-an-event-driven-system-need-it-l2-sap-goldman-sachs)

??? question "How do you govern APIs across a hundred teams?"
    <span class="badge badge-l4">L4</span> Amazon · SAP

    Contracts in repos (spec-first), linting with shared rules, automated breaking-change checks, consumer contracts for critical consumers, a catalogue of APIs and their consumers, tolerant-reader defaults in the platform's client libraries, and a lightweight review only for breaking or semantic changes; teams evolve additively without asking permission.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q12-how-do-you-govern-apis-across-a-hundred-teams-l4-amazon-sap)


**[28. Idempotency, Deduplication & Correlation](../communication/ch28-idempotency-correlation.md)**

??? question "What does idempotent mean, and which HTTP methods are idempotent?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Repeating the operation has the same effect as doing it once. `GET`, `HEAD`, `PUT`, `DELETE` and `OPTIONS` are idempotent by definition; `POST` and `PATCH` are not, so they need an idempotency key (or a design that makes them idempotent) to be retried safely.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q1-what-does-idempotent-mean-and-which-http-methods-are-idempotent-l1l2-cognizant-sap)

??? question "How long should you keep idempotency keys or processed message ids?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    Longer than the slowest source of duplicates you care about, and published to clients. For API keys, longer than any client retry (24 h is common). For consumers, longer than redeliveries and re-publishes; for replays days later, use version checks on the entity instead of a huge store. Measured: a 1-hour window missed every replay; a 7-day table needed 302 M ids.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q6-how-long-should-you-keep-idempotency-keys-or-processed-message-ids-l2l3-flipkart-sap)

??? question "What's the difference between correlation id, causation id and trace id?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    The correlation id groups everything in one business flow, possibly over days and many traces. The causation id points to the message that directly caused this one, giving the exact sequence. The trace id (W3C `traceparent`) groups the spans of one request tree for latency analysis and is often sampled. The event id identifies one message for deduplication.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q8-whats-the-difference-between-correlation-id-causation-id-and-trace-id-l2l3-sap-goldman-sachs)

??? question "How is trace context propagated in Spring Boot?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Micrometer Tracing (with OpenTelemetry or Brave) instruments HTTP servers and clients, Kafka templates and listeners (when observation is enabled), reading and writing the W3C `traceparent` header, and adds `traceId`/`spanId` to the logging MDC. Business ids can travel as baggage via `management.tracing.baggage.remote-fields`.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q10-how-is-trace-context-propagated-in-spring-boot-l2-sap-cognizant)


### Data


**[29. Polyglot Persistence](../data/ch29-polyglot-persistence.md)**

??? question "What is polyglot persistence?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Using different storage technologies for different data and access patterns within one system, typically per service: relational for transactions, a search engine for text search, a key-value store for caches and fast-changing ephemeral state. Database per service makes it possible; the cost is operating each store and keeping copies consistent.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q1-what-is-polyglot-persistence-l1l2-cognizant-sap)

??? question "How do you keep a cache or search index in sync with the database?"
    <span class="badge badge-l3">L3</span> Flipkart · SAP

    Make the database the source of truth, record changes in the same transaction (outbox) or read the change log (CDC), and apply them with a projector that retries until it succeeds and writes only if the version is newer. Monitor lag and drift, and be able to rebuild the copy. Measured: zero wrong items, median lag 3 ms.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q5-how-do-you-keep-a-cache-or-search-index-in-sync-with-the-database-l3-flipkart-sap)

??? question "When do you need Elasticsearch instead of database full-text search?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · SAP

    When search is a product feature: relevance tuning, synonyms, facets and aggregations, many languages, typo-tolerant typeahead in milliseconds over millions of documents. Measured: PostgreSQL full-text + trigram fallback found 29 of 29 test queries at a 14 ms median over 192,000 items, but typos cost ~50 ms at the median. Then it's a derived store, fed from the source of truth.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q8-when-do-you-need-elasticsearch-instead-of-database-full-text-search-l2l3-amazon-sap)

??? question "How do you store time-series data in a relational database?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Append in time order into tables partitioned by time, with a BRIN index on the timestamp and B-trees only for the lookups you need (e.g. by device and time). Retention drops old partitions. Measured: the time index went from 79 MB to 168 kB, and removing a day took 83 kB of WAL instead of 171 MB. Move to a time-series or columnar store for compression and downsampling at much larger volumes.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q10-how-do-you-store-time-series-data-in-a-relational-database-l2l3-sap-flipkart)


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "What is two-phase commit?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    A protocol for atomic commit across several resources. Phase 1: the coordinator asks each participant to prepare (persist the work and promise to commit); phase 2: if all agreed, it logs COMMIT and tells everyone to commit, otherwise to abort. Between the phases the participants are in doubt and hold their locks.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q1-what-is-two-phase-commit-l2l3-cognizant-sap)

??? question "How does a saga differ from a distributed transaction?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    Each step commits locally and at once; failures are undone by compensating steps, not rollback. It gives up isolation (intermediate states are visible) in exchange for no cross-service locks and independent availability. Measured: the saga's payment step held its lock 4.3–4.5 ms vs 2PC's 11–12.5 ms.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q6-how-does-a-saga-differ-from-a-distributed-transaction-l2l3-flipkart-sap)

??? question "Is the transactional outbox a distributed transaction?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    No. It makes "change state and publish" atomic by writing both into one local database transaction; delivery happens afterwards, at least once, so consumers must be idempotent. It replaces XA between a database and a broker.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q10-is-the-transactional-outbox-a-distributed-transaction-l3-sap-flipkart)

??? question "When is 2PC acceptable?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Inside one service or one database product: a service writing to two of its own databases at low volume with an operations team ready for in-doubt recovery; distributed SQL databases that run 2PC over consensus-replicated participants; legacy integration in an application server. Not across services, teams or external systems.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q11-when-is-2pc-acceptable-l4-goldman-sachs-sap)


**[31. Data Consistency](../data/ch31-data-consistency.md)**

??? question "What does eventual consistency guarantee?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Only that replicas converge if writes stop. It says nothing about what a reader sees meanwhile: stale data, data going backwards, not seeing your own write. Real systems add session guarantees on top.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q1-what-does-eventual-consistency-guarantee-l2-cognizant-sap)

??? question "What are monotonic reads?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    A client never sees older data after it has seen newer. Load-balancing reads across replicas with different lag breaks it (measured: 19% of polls went backwards). Fix with sticky routing or a token of the newest position seen.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q4-what-are-monotonic-reads-l3-amazon-sap)

??? question "How does PostgreSQL report replication lag, and what can go wrong reading from a replica?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    `pg_stat_replication` on the primary (write/flush/replay lag), `pg_last_wal_replay_lsn()` and `pg_last_xact_replay_timestamp()` on the replica. Pitfalls: stale reads, queries cancelled by replay conflicts (SQLSTATE 40001), and a statement's snapshot preceding the functions in it, so check the position before reading, in a separate statement.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q7-how-does-postgresql-report-replication-lag-and-what-can-go-wrong-reading-from-a-replica-l3l4-goldman-sachs-sap)

??? question "What is a hybrid logical clock and why use one?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    A timestamp of (physical time, counter) that never goes backwards and is always greater than any timestamp the node has seen. It stays close to wall time but respects causality, so ordering by it never puts an effect before what its writer had seen, even with skewed clocks. Used by CockroachDB and YugabyteDB.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q10-what-is-a-hybrid-logical-clock-and-why-use-one-l4-goldman-sachs-sap)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "Cache-aside, read-through, write-through, write-behind: what's the difference?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Cache-aside: the app reads the cache and loads from the database on a miss. Read-through: the cache does the loading. Write-through: writes update the database and the cache together. Write-behind: writes go to the cache and are flushed to the database later. They trade staleness, write latency and durability differently.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q1-cache-aside-read-through-write-through-write-behind-whats-the-difference-l2-cognizant-sap)

??? question "What does @Cacheable(sync = true) do, and what doesn't it do?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Concurrent misses for the same key on one instance wait for a single load. It does nothing across instances (N instances, N loads: measured 4 at once with 4 instances) and it doesn't serve stale values; combine with stale-while-revalidate for expensive keys.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q5-what-does-cacheablesync-true-do-and-what-doesnt-it-do-l2l3-sap-cognizant)

??? question "How do you keep a shared cache consistent across many services?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Let one service own the data and the cache, or drive invalidation from the owner's change stream (outbox or CDC) so no writer has to know every key. Version cached values so late or duplicate invalidations can't move a cache backwards.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q8-how-do-you-keep-a-shared-cache-consistent-across-many-services-l4-goldman-sachs-sap)

??? question "Local (in-process) cache or distributed cache?"
    <span class="badge badge-l3">L3</span> Flipkart · SAP

    Local caches are fastest (no network; measured p50 under 0.1 ms) but stale per instance and multiply memory; distributed caches are shared and consistent across instances but cost a network hop. Use local for small, hot, slowly-changing data with a short TTL, often in front of a distributed one.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q10-local-in-process-cache-or-distributed-cache-l3-flipkart-sap)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

??? question "Why is opening a connection per request bad?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    A PostgreSQL connection is a process: forking, authentication and warming caches cost milliseconds. Measured: 6,470–6,748 transactions/s with a pool of 16 against 428–461 with a new connection per transaction.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q2-why-is-opening-a-connection-per-request-bad-l2-cognizant-sap)

??? question "What can go wrong with CREATE INDEX CONCURRENTLY?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    It can't run in a transaction, it waits for every older snapshot in the database, and if it fails it leaves an INVALID index that is maintained but unused. With `IF NOT EXISTS`, a rerun keeps the invalid index (measured). Check `pg_index.indisvalid`, drop and rebuild.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q5-what-can-go-wrong-with-create-index-concurrently-l3-goldman-sachs-sap)

??? question "Why not hash(key) mod N?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Changing N remaps almost every key. Measured: 80% of orders moved going from 16 to 20 shards, against 19.5% with 4,096 fixed slots and 18.6% with a consistent-hash ring. Slots also give a small, explicit map and a unit of migration.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q10-why-not-hashkey-mod-n-l3l4-amazon-sap)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "Why not give analysts read access to the services' databases?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Load, bloat and coupling. Measured: two report loops on the primary cut write throughput by 67–69%; a long report transaction left 94,000–97,000 dead rows VACUUM couldn't remove; and every service's schema becomes a public contract.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q2-why-not-give-analysts-read-access-to-the-services-databases-l2-sap-cognizant)

??? question "Materialized view or summary table?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    A materialized view is simple but recomputes everything on refresh; a plain refresh blocks readers (measured: all readers waited 3.0–3.4 s), `CONCURRENTLY` doesn't but took 7× longer. An incremental summary table is always current and never blocks, at the cost of per-change logic. Views for slow-moving summaries, incremental tables for live dashboards.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q6-materialized-view-or-summary-table-l3-sap-cognizant)

??? question "Data warehouse, data lake, lakehouse: what's the difference?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    A warehouse stores modelled, cleaned data in a managed engine with schema on write. A lake stores raw and refined files cheaply in open formats, schema on read. A lakehouse adds table formats (Iceberg, Delta, Hudi) to the lake for transactions, schema evolution and time travel, so warehouse-style queries run on lake storage.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q9-data-warehouse-data-lake-lakehouse-whats-the-difference-l3-amazon-sap)

??? question "What is data mesh, and when is it worth it?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Domain teams own and publish their analytical data as products (owner, schema, semantics, freshness and quality objectives) on a self-serve platform, with federated governance. Worth it when a central data team has become the bottleneck for many domains; without a strong platform and clear ownership it becomes many inconsistent pipelines.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q10-what-is-data-mesh-and-when-is-it-worth-it-l4-goldman-sachs-sap)


### Deployment & Platform


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

??? question "What does the JDK 25 AOT cache add over CDS?"
    <span class="badge badge-l3">L3</span> Oracle · Atlassian

    Classes are stored loaded **and linked** (JEP 483), the training run is one step with `-XX:AOTCacheOutput` (JEP 514), and method profiles are recorded so the JIT starts compiling sooner (JEP 515). Measured: 611–732 ms at 1 CPU and 2.0–2.1 s at 0.25 CPU, the fastest JVM variant.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q3-what-does-the-jdk-25-aot-cache-add-over-cds-l3-oracle-atlassian)

??? question "What happens if the cache doesn't match?"
    <span class="badge badge-l3">L3</span> Red Hat

    With the default `-XX:AOTMode=auto`, the JVM starts without it, and the only sign is a slower start. With `-XX:AOTMode=on` an unusable cache stops the JVM, which is what a CI smoke test should use. Train and run with the same JDK and class path.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q5-what-happens-if-the-cache-doesnt-match-l3-red-hat)


### Foundations


**[1. Monolith vs SOA vs Microservices vs Modular Monolith](../foundations/ch01-architecture-styles.md)**

??? question "What is a modular monolith and why is it popular again?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · Accenture · SAP

    One deployable split into business modules with enforced public APIs and hidden internals, with each module owning its data. You get most of the design benefits of microservices without the network costs, and modules can be extracted later.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q2-what-is-a-modular-monolith-and-why-is-it-popular-again-l1l2-cognizant-accenture-sap)

??? question "Isn't SOA the same as microservices?"
    <span class="badge badge-l2">L2</span> JP Morgan · SAP · Goldman Sachs

    They share the idea of services, but differ in where logic lives and who governs. SOA used smart pipes (an ESB with routing, transformation and business rules), canonical enterprise models and central governance. Microservices use dumb pipes, bounded contexts and team-owned services.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q4-isnt-soa-the-same-as-microservices-l2-jp-morgan-sap-goldman-sachs)

??? question "How do you decide which module to extract first?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Amazon · SAP

    Extract where independence creates measurable value: a distinct scaling profile, a distinct compliance scope, a team that will own it, failure isolation needs, and low synchronous coupling with clean data ownership. Avoid modules that need ACID transactions with others.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q6-how-do-you-decide-which-module-to-extract-first-l3l4-flipkart-amazon-sap)

??? question "You're asked to migrate a 10-year-old banking monolith to microservices. What's your plan?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · SAP · Cognizant

    Don't rewrite. Modularise in place, establish the platform, then strangle capabilities one at a time, starting at the edges, keeping the core ledger coarse-grained, and measuring outcomes at every step.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q10-youre-asked-to-migrate-a-10-year-old-banking-monolith-to-microservices-whats-your-plan-l4-jp-morgan-goldman-sachs-sap-cognizant)

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

??? question "Explain 'smart endpoints and dumb pipes'."
    <span class="badge badge-l2">L2</span> JP Morgan · SAP

    Business logic lives in services; the transport only moves messages. This avoids the ESB trap, where routing and transformation logic in middleware creates a central bottleneck.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q4-explain-smart-endpoints-and-dumb-pipes-l2-jp-morgan-sap)

??? question "Is code duplication across services acceptable?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · SAP

    Often yes. DRY across service boundaries creates coupling through shared libraries. Share technical concerns (logging, tracing setup), never domain models. Duplicate small DTOs and own them.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q8-is-code-duplication-across-services-acceptable-l2l3-amazon-sap)

??? question "How do you enforce principles across 50 teams without an architecture review board?"
    <span class="badge badge-l4">L4</span> SAP · Amazon · Flipkart

    Paved road plus fitness functions. Service templates with secure, observable defaults. Shared architecture-rule tests (ArchUnit, Spring Modulith) run in every pipeline. API linting. Scorecards in the developer portal. Exceptions through lightweight ADRs.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q9-how-do-you-enforce-principles-across-50-teams-without-an-architecture-review-board-l4-sap-amazon-flipkart)

??? question "What is a fitness function? Give examples."
    <span class="badge badge-l4">L4</span> SAP · ThoughtWorks-style consultancies

    An automated, objective check that an architectural property still holds. Examples: layering rules (ArchUnit), module boundaries (Spring Modulith `verify()`), "no `RestTemplate`" rules, API breaking-change detection in CI, p99 latency gates in performance tests, dependency-age checks.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q12-what-is-a-fitness-function-give-examples-l4-sap-thoughtworks-style-consultancies)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "What is PACELC and why is it more useful day to day?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    If there's a Partition, choose Availability or Consistency; Else, choose Latency or Consistency. Partitions are rare but latency is paid on every request, so the E side drives most design decisions, such as synchronous versus asynchronous replication.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q3-what-is-pacelc-and-why-is-it-more-useful-day-to-day-l3-goldman-sachs-sap)

??? question "With N = 3, which R and W give strong consistency? Trade-offs?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    Any R + W > N: (2, 2) is balanced; (3, 1) gives fast reads but writes fail if any replica is down; (1, 3) is the reverse. Even with R + W > N, concurrent writes need conflict resolution, and "sloppy quorums" with hinted handoff weaken the guarantee during failures.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q9-with-n-3-which-r-and-w-give-strong-consistency-trade-offs-l3-amazon-sap)

??? question "What is split brain and how do fencing tokens prevent damage?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Two nodes both believe they are leader, for example after a GC pause outlives a lease. A fencing token is a monotonically increasing number issued with each lease. The protected resource rejects writes carrying an older token, so a stale leader's writes fail even if it doesn't know it's stale.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q11-what-is-split-brain-and-how-do-fencing-tokens-prevent-damage-l4-goldman-sachs-sap)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "How does Spring Boot resolve configuration from many sources?"
    <span class="badge badge-l2">L2</span> SAP · service companies

    A precedence order in which command-line arguments override system properties, which override environment variables, which override config files (profile-specific over default). Environment variables bind through relaxed binding (`FOODFLEET_ZONES_CITY` → `foodfleet.zones.city`). `spring.config.import` adds config trees, Config Server or Vault.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q3-how-does-spring-boot-resolve-configuration-from-many-sources-l2-sap-service-companies)

??? question "How do you run database migrations in a twelve-factor way?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    Versioned migrations (Flyway or Liquibase) in the codebase, applied by the release: on startup for simple services, or as a pre-deploy Job for large changes. Use expand/contract so old and new versions run against the same schema during rolling deploys. Never apply them by hand.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q6-how-do-you-run-database-migrations-in-a-twelve-factor-way-l3-goldman-sachs-sap)

??? question "How do you make a Spring Boot service start fast?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Measure first. Then use a layered image, and on Java 25 an AOT cache trained at build time (JEP 483 and 514). Consider CRaC for checkpoint/restore, or GraalVM native images when startup and memory dominate and their build constraints are acceptable. Avoid heavy work in the startup path, and lazily initialise optional components.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q8-how-do-you-make-a-spring-boot-service-start-fast-l3l4-amazon-sap)

??? question "What did 'Beyond the Twelve-Factor App' add, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    API first (contracts enable parallel teams), telemetry (you can't operate what you can't see), and authentication and authorization (security can't be bolted on in a zero-trust, many-service world). It also reinterpreted some factors for containers and microservices.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q10-what-did-beyond-the-twelve-factor-app-add-and-why-l3l4-sap-goldman-sachs)


**[5. Domain-Driven Design](../foundations/ch05-domain-driven-design.md)**

??? question "What is a bounded context?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant · Accenture

    The explicit boundary within which a model and its ubiquitous language are consistent. Inside it each term has one meaning; across contexts the same word ("order") can mean different things, and you translate at the boundary.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q1-what-is-a-bounded-context-l2-sap-cognizant-accenture)

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

??? question "Bounded context vs microservice: always one-to-one?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    No. A context is a model boundary, a service a deployment boundary. One context can be several services (for scaling), and several contexts can live in one modular monolith. One service spanning several contexts is a smell.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q10-bounded-context-vs-microservice-always-one-to-one-l3l4-amazon-sap)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

??? question "How do you decide service boundaries?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Start from business capabilities and bounded contexts, discovered with domain experts (Event Storming). Group things that change together and must be consistent together; separate things with different owners, scaling needs, volatility or compliance requirements. Validate candidates against real use cases (how many calls per checkout?) and against change history, and start as modules before making them services.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q1-how-do-you-decide-service-boundaries-l2l3-cognizant-sap-flipkart)

??? question "Decompose by business capability vs by subdomain?"
    <span class="badge badge-l2">L2</span> Cognizant · Accenture · SAP

    Capability asks *what the business does* (take orders, dispatch riders); subdomain asks *where the model and language change*. They usually produce the same boundaries. When they differ, the language boundary wins, because one service with two meanings of "order" is the worse problem.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q2-decompose-by-business-capability-vs-by-subdomain-l2-cognizant-accenture-sap)

??? question "What is Event Storming and what do you get out of it?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart · consulting firms

    A workshop where domain experts and engineers place domain events (orange) on a timeline, then add commands, actors, policies, aggregates, external systems and hotspots. You get a shared understanding of the process, a list of open questions, aggregate candidates, and boundary candidates where the language changes and at pivotal events. Policies between lanes usually become asynchronous integration events.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q3-what-is-event-storming-and-what-do-you-get-out-of-it-l3-sap-flipkart-consulting-firms)

??? question "How would you find service boundaries in an existing monolith?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · SAP

    Combine three sources: Event Storming with the people who run the business process; static structure (package dependencies, Spring Modulith's module report); and **change coupling** from git history (modules that change together belong together). Where they agree, you have a candidate. Where they disagree, you have a design discussion. Then extract one capability at a time with the strangler fig.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q7-how-would-you-find-service-boundaries-in-an-existing-monolith-l4-jp-morgan-goldman-sachs-sap)

??? question "How do you split the database when you split a service?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · SAP

    Give each table one owning service; others use the owner's API or subscribe to its events and keep a local read model. Do it in steps: split schemas inside one database, remove cross-schema joins and foreign keys, then move schemas to separate databases. [Chapter 12](../architecture-patterns/ch12-database-per-service.md) covers the patterns and [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md) the migration.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q11-how-do-you-split-the-database-when-you-split-a-service-l3l4-jp-morgan-sap)


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "What is Conway's Law?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · TCS · SAP

    Systems end up mirroring the communication structure of the organisation that builds them (Melvin Conway, 1968). Teams that talk easily produce tightly integrated parts; teams that rarely talk produce formal interfaces between their parts.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q1-what-is-conways-law-l2l3-cognizant-tcs-sap)

??? question "Explain the four team types in Team Topologies."
    <span class="badge badge-l3">L3</span> SAP · Amazon · JP Morgan

    **Stream-aligned** (owns a stream of change end to end; the default), **platform** (self-service internal services that reduce others' load), **complicated-subsystem** (owns something needing rare specialist skills), **enabling** (coaches teams in a capability, then leaves). Most teams should be stream-aligned; the others exist to make stream-aligned teams fast.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q3-explain-the-four-team-types-in-team-topologies-l3-sap-amazon-jp-morgan)

??? question "What are the interaction modes, and how do you choose?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    **Collaboration** (close joint work, for discovery, time-boxed), **X-as-a-Service** (consume an API or platform with little coordination, for well-understood boundaries), **facilitating** (an enabling team coaching). Choose by how well the boundary is understood; expect modes to evolve from collaboration to X-as-a-Service.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q4-what-are-the-interaction-modes-and-how-do-you-choose-l3l4-goldman-sachs-sap)

??? question "Strong vs weak vs collective code ownership?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Strong: only the owner changes the code. Weak: anyone may change it, the owner reviews. Collective: no owner. For production services, one owning team per service (strong or weak with mandatory owner review); collective ownership only inside a single team.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q7-strong-vs-weak-vs-collective-code-ownership-l2l3-sap-cognizant)

??? question "Who should own the shared database during a migration to microservices?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · SAP

    In the end, nobody: each service owns its data. During migration, assign each table to the team owning the capability that writes it, even while it physically lives in the shared database; other teams read through APIs or views, and DBA expertise moves to an enabling or platform role. That is the first step of database per service.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q11-who-should-own-the-shared-database-during-a-migration-to-microservices-l3l4-jp-morgan-sap)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "How does Spring Boot 4's API versioning work?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    Configure where the version comes from (header, query parameter, path segment or media type parameter) with `spring.mvc.apiversion.*`, then add `version = "1.0"` or `"2.0+"` to request mappings. Unsupported versions get 400. A default version can serve old clients. An `ApiVersionDeprecationHandler` bean adds `Deprecation`, `Sunset` and `Link` headers to deprecated versions. Clients (RestClient, HTTP service clients) can send the version automatically.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q4-how-does-spring-boot-4s-api-versioning-work-l2l3-sap-amazon)

??? question "What is JSpecify and why did Spring adopt it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · any Spring shop

    A standard set of nullness annotations (`@NullMarked`, `@Nullable`, `@NonNull`, `@NullUnmarked`) agreed by several vendors. Spring Framework 7 and Boot 4 annotate their APIs with it, so tools like NullAway, IntelliJ and Kotlin see consistent nullness from the framework down to your code. `@NullMarked` on a package makes non-null the default.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q8-what-is-jspecify-and-why-did-spring-adopt-it-l2l3-sap-any-spring-shop)

??? question "How would you migrate 30 services from Boot 3 to Boot 4?"
    <span class="badge badge-l4">L4</span> JP Morgan · SAP · Flipkart

    Upgrade all to the latest 3.5 and clear deprecations; publish a Boot 4 golden-path template; migrate a pilot service with OpenRewrite (`UpgradeSpringBoot_4_0`), the properties migrator and classic starters as a bridge; add contract tests around JSON before switching Jackson defaults; then roll out team by team behind canaries, with a packaged-jar smoke test in CI. Track progress on a dashboard of service versions.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q10-how-would-you-migrate-30-services-from-boot-3-to-boot-4-l4-jp-morgan-sap-flipkart)


### Governance & Organization


**[71. API Governance & Developer Portals](../governance-org/ch71-api-governance.md)**

??? question "What is Backstage, and what makes a catalog useful?"
    <span class="badge badge-l3">L3</span> Spotify · Red Hat

    An open-source developer portal around a software catalog of components, APIs, systems and owners, described in files kept with the code. A catalog is useful when it's correct, so check it in CI. Measured: 15 of 31 entities failed a governance check, and only 3 of those broke Backstage's required fields.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q8-what-is-backstage-and-what-makes-a-catalog-useful-l3-spotify-red-hat)


**[72. Platform Engineering & Golden Paths](../governance-org/ch72-platform-engineering.md)**

??? question "How do you update services generated from an older template?"
    <span class="badge badge-l3">L3</span> Red Hat

    A three-way merge per file: old template as base, the service as ours, the new template as theirs (Copier's `update`, using `git merge-file`). Measured: 26 of 32 files replaced or added, 2 merged with teams' changes, 4 conflicts, and conformance from 4 to 40 of 48. Never regenerate; it discards teams' fixes.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q4-how-do-you-update-services-generated-from-an-older-template-l3-red-hat)


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "How do you know how tangled a codebase is?"
    <span class="badge badge-l3">L3</span> SAP

    Map packages to contexts, count imports between them, find strongly connected components, and compute the fewest edges that stop a layering. Broadleaf: one cycle of 9 contexts and 157 edges in the way.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q5-how-do-you-know-how-tangled-a-codebase-is-l3-sap)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "What is pinning, and how do you find it?"
    <span class="badge badge-l3">L3</span> Oracle · any Java shop

    A virtual thread that can't unmount while blocked, so it holds its carrier. On JDK 21, blocking inside `synchronized` pins (measured: 1,000 50 ms tasks took 25 s instead of 0.1 s); JDK 24+ fixed monitors, and native frames still pin. Find it with JFR's `jdk.VirtualThreadPinned` event; fix it with `ReentrantLock` or a newer JDK.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q3-what-is-pinning-and-how-do-you-find-it-l3-oracle-any-java-shop)

??? question "How many threads does a WebFlux service use?"
    <span class="badge badge-l2">L2</span> Red Hat

    Reactor Netty starts max(CPUs, 4) event-loop threads, shared by server and client. Blocking calls belong on `Schedulers.boundedElastic()` (10 × CPUs threads), never on the loop. A service that blocks on the loop stalls every connection on it.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q11-how-many-threads-does-a-webflux-service-use-l2-red-hat)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "What uses memory in a JVM besides the heap?"
    <span class="badge badge-l3">L3</span> Red Hat

    GC data structures, metaspace and class data, the JIT's code cache, thread stacks, direct and mapped buffers, JFR's buffers, and the C heap of native libraries. Measured with NMT: G1's structures 54 MB, ZGC's 238–266 MB for a 1 GB heap. Measure with `-XX:NativeMemoryTracking=summary` and `jcmd VM.native_memory`.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q7-what-uses-memory-in-a-jvm-besides-the-heap-l3-red-hat)

??? question "What is an evacuation failure?"
    <span class="badge badge-l3">L3</span> Oracle

    G1 copies live objects out of the regions it collects; when there are no free regions to copy into, the evacuation fails and G1 falls back to a full, compacting collection. Measured: with 1.5–2.2× the live set as heap, 12–14 evacuation-failure events led to full collections of 650–1,030 ms; at 2.6× there were none.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q8-what-is-an-evacuation-failure-l3-oracle)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "How does a Kubernetes Service balance load?"
    <span class="badge badge-l3">L3</span> Google · Red Hat

    kube-proxy picks an endpoint at random when a connection opens (iptables `statistic --mode random`, or nftables `numgen random`), so every request on a keep-alive connection goes to the same pod. Measured: a keep-alive client left 1–2 of 4 copies idle; a new connection per request spread 23–27% each.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q3-how-does-a-kubernetes-service-balance-load-l3-google-red-hat)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "How do you pick node shapes?"
    <span class="badge badge-l3">L3</span> Red Hat

    From the fleet's CPU:memory ratio after right-sizing, and let a provisioner such as Karpenter choose among several. Measured: after right-sizing, 89–97% of CPU and 35–38% of memory was allocated, so CPU-heavier nodes would pack better.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q10-how-do-you-pick-node-shapes-l3-red-hat)


### Resilience


**[42. Failure Modes & Cascading Failures](../resilience/ch42-failure-modes.md)**

??? question "Does jitter fix retry storms?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    It fixes synchronization, not volume. It turned a reconnect herd that took 42 s into one that took 6 s, but in the metastable experiment jittered retries still stayed at 0% goodput: the retries still arrived. Volume needs a budget or shedding.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q5-does-jitter-fix-retry-storms-l3-amazon-sap)

??? question "What is a gray failure?"
    <span class="badge badge-l3">L3</span> Microsoft-/Amazon-style rounds · SAP

    A degradation some requests see and health checks don't: a slow disk, a noisy neighbour. Detect it from the callers' side (per-instance latency, outstanding requests) and route around it: P2C sent the slow instance half its share and halved slow users at fan-out 10 (measured).

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q9-what-is-a-gray-failure-l3-microsoft-amazon-style-rounds-sap)

??? question "What changes with virtual threads?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    The thread pool used to be an accidental bulkhead: 200 Tomcat threads capped concurrent work. With `spring.threads.virtual.enabled`, each request gets a virtual thread and that cap disappears, so a slow dependency can accumulate unbounded concurrent requests. Add explicit limits (`@ConcurrencyLimit(REJECT)`, connection pool sizes, semaphores) and deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q11-what-changes-with-virtual-threads-l3-sap-jp-morgan)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "In what order do Resilience4j's annotations apply?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Retry outermost, then CircuitBreaker, RateLimiter, TimeLimiter, Bulkhead innermost, regardless of the order written (configurable with `*-aspect-order`). So each attempt is counted by the breaker and needs a bulkhead permit, and the retry must ignore the breaker's `CallNotPermittedException`, or an open breaker costs every user the retry delays (1 s, measured).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q4-in-what-order-do-resilience4js-annotations-apply-l3-sap-jp-morgan)

??? question "Resilience4j or Spring Framework 7's `@Retryable`?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Spring 7 covers retries and concurrency limits with no extra dependency; set a multiplier, jitter (at least the delay: it never goes below the initial delay) and a timeout. For circuit breakers, slow-call detection, rate limits, metrics and per-instance configuration, use Resilience4j. They coexist.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q8-resilience4j-or-spring-framework-7s-retryable-l2l3-sap-cognizant)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "Which algorithm would you choose?"
    <span class="badge badge-l4">L4</span> Netflix-style rounds · SAP

    The one whose signal you can define. In measurements, Vegas's defaults rejected half the traffic of a healthy service with variable latency, and Gradient2 depended on its floor; AIMD with "slow" set to half the callers' deadline rejected nothing when healthy and served 53–54% of a possible 57% in a slowdown. Test any of them on your own latency distribution.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q6-which-algorithm-would-you-choose-l4-netflix-style-rounds-sap)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "How is it different from testing?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Accenture · SAP

    A test checks a known expected behaviour of a component; an experiment asks an open question about the whole system under conditions tests don't create (a black-holed dependency, a slow trickle, a dead pod at peak). Once an experiment's hypothesis holds, it often becomes a scheduled regression check, much like a test.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q2-how-is-it-different-from-testing-l2l3-accenture-sap)


**[46. Disaster Recovery & Multi-Region](../resilience/ch46-disaster-recovery.md)**

??? question "Replicas or backups?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Accenture · SAP

    Both: replicas protect against losing a machine or a zone, and copy bad writes everywhere at once; backups with WAL archiving protect against bad writes, deleted data and ransomware through point-in-time recovery. A backup is only as good as its last timed restore.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q8-replicas-or-backups-l2l3-accenture-sap)


### Security


**[35. Security Fundamentals & Threat Modeling](../security/ch35-security-fundamentals.md)**

??? question "What is STRIDE, and how do you apply it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Six threat categories, each the violation of a security property: Spoofing (authenticity), Tampering (integrity), Repudiation, Information disclosure (confidentiality), Denial of service (availability), Elevation of privilege (authorization). Draw the system as a data-flow diagram with trust boundaries, apply the categories that fit each element type, and record a mitigation or acceptance for each. FoodFleet's checkout: 21 elements, 69 threats.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q1-what-is-stride-and-how-do-you-apply-it-l2l3-cognizant-sap)

??? question "What is mass assignment, and how does it happen in Spring?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    Binding a request body onto an entity so a client can set fields it shouldn't (status, price, owner): OWASP API3. It happens when controllers accept entities as `@RequestBody`. Use request types with only the changeable fields, and reject unknown fields: Jackson 3, and so Spring Boot 4, ignores them by default; set `spring.jackson.deserialization.fail-on-unknown-properties: true`.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q4-what-is-mass-assignment-and-how-does-it-happen-in-spring-l3-sap-cognizant)

??? question "Where should authorization happen in a microservice system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · SAP

    The gateway authenticates and applies coarse rules; the service that owns the data makes object, property and function decisions, because only it knows who owns what. Services re-verify tokens (zero trust); the database restricts by credentials and, optionally, row-level security as defence in depth.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q8-where-should-authorization-happen-in-a-microservice-system-l3l4-flipkart-sap)

??? question "How do you test authorization thoroughly?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Beyond example tests, generate the full matrix of roles × actions × object states and compare the policy against rules written separately, in the business's words. Measured here: 18,600 decisions, 0 disagreements. Include awkward identities (one person with two roles) and every state.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q11-how-do-you-test-authorization-thoroughly-l3l4-goldman-sachs-sap)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "What changes in OAuth 2.1?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    It consolidates the security best practices: PKCE for all authorization code flows, no implicit flow, no password grant, exact redirect URI matching, no bearer tokens in query strings, refresh tokens sender-constrained or rotated. As of September 2026 it is still an Internet-Draft; RFC 9700 already recommends the same.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q2-what-changes-in-oauth-21-l2l3-sap-amazon)

??? question "How do you revoke a JWT?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    You mostly can't; you limit its life. Keep access tokens short, revoke the refresh token so no new ones are issued, and for sensitive APIs introspect or check a deny-list of token ids. Sender-constrained tokens make stolen ones unusable.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q6-how-do-you-revoke-a-jwt-l3-goldman-sachs-sap)

??? question "RS256 or ES256?"
    <span class="badge badge-l3">L3</span> SAP · Amazon

    Both are fine; never HS256 between parties. Measured in the JDK: RS256 verifies ~15× faster (verification dominates in microservices), ES256 signs ~5× faster and tokens are 40% smaller. Choose by what your issuer supports and where the CPU goes.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q9-rs256-or-es256-l3-sap-amazon)

??? question "How does a service call another service with no user involved?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Client credentials: the service authenticates to the token endpoint (private key JWT or mTLS better than a shared secret) and gets a token for the target audience with its own scopes. In a mesh, workload identity (SPIFFE, chapter 38) can replace or complement it.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q11-how-does-a-service-call-another-service-with-no-user-involved-l2l3-cognizant-sap)


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "How do you configure a Spring Boot 4 service as an OAuth 2 resource server?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Add `spring-boot-starter-security-oauth2-resource-server`; set `spring.security.oauth2.resourceserver.jwt.issuer-uri`, `audiences` and `jws-algorithms`; define a `SecurityFilterChain` with `oauth2ResourceServer(jwt)` and explicit `authorizeHttpRequests` rules ending in `denyAll()`; make it stateless.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q1-how-do-you-configure-a-spring-boot-4-service-as-an-oauth-2-resource-server-l2-cognizant-sap)

??? question "How do you map Keycloak roles to Spring Security authorities?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    A `Converter<Jwt, Collection<GrantedAuthority>>` on the `JwtAuthenticationConverter` that reads `realm_access.roles` (and client roles if used) and the `scope` claim, keeping only roles the application defines. Spring Boot 4.1 also offers `authorities-claim-expressions` for simple cases.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q3-how-do-you-map-keycloak-roles-to-spring-security-authorities-l3-sap-flipkart)

??? question "What changed in Spring Security 7?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    It ships with Spring Boot 4: the authorization server is part of Spring Security (`http.oauth2AuthorizationServer`), only the lambda DSL remains, and RFC 9068 validation is available (`JwtValidators.createAtJwtValidator()`). Check the migration guide and re-test security after upgrading.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q6-what-changed-in-spring-security-7-l3-sap-cognizant)

??? question "How do you test security rules?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    MockMvc with spring-security-test's `jwt()` post-processor and realistic claims: no token → 401, wrong scope → 403, other users' objects → 404, missing role → 403, unknown endpoint → 403. Plain-Java rule classes get exhaustive unit tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q8-how-do-you-test-security-rules-l2l3-flipkart-sap)

??? question "Spring Authorization Server or Keycloak?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · SAP

    Keycloak (or a managed provider) when you need user management, federation, MFA, admin UI and many applications. Spring's authorization server when you need an embedded, code-configured provider with custom behaviour, or for local development and tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q10-spring-authorization-server-or-keycloak-l3l4-amazon-sap)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "What is mutual TLS, and what does it not give you?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Both sides present certificates, so each knows which workload is at the other end. It doesn't say what that workload may do (you still need a per-route policy and tokens), it is checked at the handshake only, and it ends wherever TLS is terminated.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q1-what-is-mutual-tls-and-what-does-it-not-give-you-l2-cognizant-sap)

??? question "What is SPIFFE, and what is SPIRE?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    SPIFFE standardises workload identity: SPIFFE IDs (`spiffe://trust-domain/path`), SVIDs (X.509 or JWT documents carrying them), trust bundles and the Workload API. SPIRE implements it: it attests nodes and workloads (for example by Kubernetes namespace and service account) and issues short-lived SVIDs that rotate automatically, so workloads get identity without secrets.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q2-what-is-spiffe-and-what-is-spire-l3-amazon-sap)

??? question "How does Spring Boot do mutual TLS?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    SSL bundles: `spring.ssl.bundle.pem.<name>` with keystore and truststore, `server.ssl.bundle` and `server.ssl.client-auth: need`, `reload-on-update: true` for rotated files. Clients use `RestClientSsl.fromBundle`, which builds the request factory once, so rebuild clients on bundle updates.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q6-how-does-spring-boot-do-mutual-tls-l2l3-sap-cognizant)

??? question "What goes wrong with Spring's client-credentials interceptor?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    By default it uses the current user as the principal and stores authorized clients per principal, so inside user requests it fetches a token per user (measured: 20,000 token requests and 5,000 stored tokens instead of 4). Use the service-oriented manager and a principal resolver that yields one fixed principal. Renewal has no lock, so many threads renew at once at expiry.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q9-what-goes-wrong-with-springs-client-credentials-interceptor-l3-sap-flipkart)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "Why not keep secrets in environment variables?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    They are inherited by every child process, readable in `/proc/<pid>/environ` by the same user, shown by debug tools, often logged by crash reporters, and never updated without a restart. Measured: inherited by a child process and readable through `/proc`. Mount secrets as files instead.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q1-why-not-keep-secrets-in-environment-variables-l2-cognizant-sap)

??? question "What is 'secret zero', and how do you avoid it?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    The credential needed to fetch other secrets. Use platform identity: the pod's Kubernetes service-account token (or cloud workload identity) authenticates to Vault/OpenBao, which issues a scoped, short-lived token. Nothing is delivered to the pod.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q3-what-is-secret-zero-and-how-do-you-avoid-it-l3-amazon-sap)

??? question "Your Vault-backed service fails a day after deployment. Why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Dynamic credentials renewed but never rotated: renewal stops at max_ttl. Spring Cloud Vault 5.0.2 requests database credentials as renewable secrets. Request a rotating secret and update the pool (HikariCP 7's credentials provider), or set max_ttl longer than any pod lives. Measured: renew-only failed exactly at max_ttl.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q6-your-vault-backed-service-fails-a-day-after-deployment-why-l3l4-sap-goldman-sachs)

??? question "How do secrets reach a Spring Boot application?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    `spring.config.import` with `configtree:` for mounted files (read once at start-up) or `vault://` for Spring Cloud Vault (Kubernetes auth, KV v2 as properties, leases managed by Spring Vault). Rotating values need code that reacts: a lease listener or a file watcher.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q8-how-do-secrets-reach-a-spring-boot-application-l2-cognizant-sap)

??? question "External Secrets Operator or the CSI driver or reading Vault directly?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    ESO syncs into Kubernetes Secrets (works with anything that reads Secrets; adds a copy in etcd and a refresh delay, one hour by default). The CSI driver mounts directly without a Secret object. Reading Vault directly (Spring Cloud Vault) gives leases and rotation in the application but couples it to Vault. FoodFleet: Vault directly for dynamic credentials, ESO for static third-party keys.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q10-external-secrets-operator-or-the-csi-driver-or-reading-vault-directly-l3-amazon-sap)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "Where should rate limiting live: CDN, gateway or service?"
    <span class="badge badge-l3">L3</span> SAP · Flipkart

    In layers. Per-address limits at the edge stop floods and credential stuffing cheaply; per-client and per-tenant limits need the authenticated identity, so they belong after authentication (gateway or service); business limits (one coupon per customer) belong in the service. Know your tools' behaviour: Spring Cloud Gateway's Redis limiter refills once a second and fails open.

    [Full answer in chapter 40](../security/ch40-api-security.md#q3-where-should-rate-limiting-live-cdn-gateway-or-service-l3-sap-flipkart)

??? question "When do you need CSRF protection?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Accenture

    When the browser attaches credentials automatically: cookies (and HTTP Basic, client certificates). An API that takes only bearer tokens in a header has nothing to forge. For cookie sessions use a CSRF token plus Fetch Metadata; SameSite is a useful extra but treats sibling subdomains as the same site (measured: they got even Strict cookies).

    [Full answer in chapter 40](../security/ch40-api-security.md#q8-when-do-you-need-csrf-protection-l2l3-sap-accenture)

??? question "How do you isolate tenants in a shared database?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Tenant from the token, never from the request; PostgreSQL row-level security on every shared table, `FORCE`d, with an application role that doesn't own the tables; the tenant set transaction-locally (`set_config(.., true)`) so pooled connections don't carry it. Measured: a forgotten predicate returned 40 rows instead of 2,000; a session-level `SET` leaked 4,000 rows to the next request. Silo databases for tenants that need them.

    [Full answer in chapter 40](../security/ch40-api-security.md#q11-how-do-you-isolate-tenants-in-a-shared-database-l3l4-sap-goldman-sachs)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "What is an SBOM, and what do you do with it?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    A list of every component in an artifact with exact identities (purls) and versions, in CycloneDX or SPDX. You keep one per release, attach it to the image, and match it against advisories continuously, so "are we affected?" is a query. Spring Boot's parent configures the CycloneDX plugin and serves the result at `/actuator/sbom`.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q1-what-is-an-sbom-and-what-do-you-do-with-it-l2-cognizant-sap)

??? question "Which findings should fail the build?"
    <span class="badge badge-l3">L3</span> SAP · Infosys

    The ones a team can fix now: critical/high with a fixed version. Everything else is tracked with an owner. Accepted risk is a waiver naming one advisory and package, with a reason and an expiry, published as VEX. Blocking on everything gets the gate disabled.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q4-which-findings-should-fail-the-build-l3-sap-infosys)

??? question "Keyless signing: how does it work, and why use it?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    The CI job's OIDC token is exchanged with Fulcio for a ten-minute certificate naming the workflow; the signature is logged in Rekor and/or timestamped; verifiers check the chain, the log/timestamp and the identity. No long-lived key to leak or rotate. syft's certificate was valid for ten minutes and verified a week later from the timestamp (measured).

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q6-keyless-signing-how-does-it-work-and-why-use-it-l3l4-goldman-sachs-sap)

??? question "How do you enforce that only trusted images run?"
    <span class="badge badge-l3">L3</span> Amazon · SAP

    An admission controller (Kyverno, Sigstore's policy-controller) that verifies signature identity and provenance for every pod, rejects tags it can't resolve, and rewrites tags to digests so the node pulls what was verified (Kyverno's `mutateDigest`, on by default). Run in Audit first, then Enforce, with monitoring and a break-glass path.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q9-how-do-you-enforce-that-only-trusted-images-run-l3-amazon-sap)

??? question "How do you honour a deletion request when data is in Kafka and backups?"
    <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Crypto-shredding: encrypt personal fields with a per-customer key before they leave the service, keep wrapped keys in a separate table backed by a KMS, and erase by deleting the key. Measured: 0 of 9,000 erased fields readable in the table, the topic or the backup; 8–30 µs per field. DPDP and GDPR both expect erasure across processors too, so the same applies to vendors.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q11-how-do-you-honour-a-deletion-request-when-data-is-in-kafka-and-backups-l4-sap-goldman-sachs)


### Testing


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "What can't ArchUnit see?"
    <span class="badge badge-l3">L3</span> Red Hat

    Anything not in the code's bytecode: compile-time constants that javac inlined (measured: a domain-to-infrastructure constant passed every rule, while `jdeps` saw the constant-pool entry) and dependencies by reflection (nothing saw `Class.forName`). Ban reflection outside configuration with a rule.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q10-what-cant-archunit-see-l3-red-hat)
