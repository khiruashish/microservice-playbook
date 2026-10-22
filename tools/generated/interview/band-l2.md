### Architecture & Patterns


**[9. Hexagonal, Clean & Onion Architecture](../architecture-patterns/ch09-hexagonal-clean-architecture.md)**

??? question "Explain hexagonal architecture."
    <span class="badge badge-l2">L2</span> Cognizant · TCS · SAP

    The application core (domain and use cases) is isolated from technology. It talks to the outside world through **ports**, interfaces it owns; **adapters** connect ports to technologies. Driving adapters (REST, messaging, tests) call driving ports; driven adapters (database, external APIs) implement driven ports. Dependencies point inward, so the core can be tested and changed without its infrastructure.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q1-explain-hexagonal-architecture-l2-cognizant-tcs-sap)

??? question "What's the difference between driving and driven ports?"
    <span class="badge badge-l2">L2</span> Infosys · Accenture

    Driving (primary) ports are what the application offers: use cases, called by adapters such as controllers. Driven (secondary) ports are what the application needs: repositories, gateways, publishers, implemented by adapters. Both are defined by the core.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q2-whats-the-difference-between-driving-and-driven-ports-l2-infosys-accenture)

??? question "How does this differ from controller–service–repository layering?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    In layering, the service depends on the persistence layer. In hexagonal, the core defines the repository interface and persistence depends on the core (dependency inversion). That lets the core run with a fake, keeps persistence types out of business rules, and supports several entry points on the same use cases.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q4-how-does-this-differ-from-controllerservicerepository-layering-l2l3-flipkart-amazon)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "What does an API gateway do?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    It's the single entry point for clients: routes requests to services, and offloads cross-cutting concerns such as TLS, authentication, rate limiting, request ids, CORS, size limits and observability. It lets services change behind a stable public API.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q1-what-does-an-api-gateway-do-l2-tcs-infosys-cognizant)

??? question "API gateway vs load balancer vs service mesh?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    A load balancer distributes connections (L4/L7) across instances. An API gateway is an application-level edge for **north–south** traffic with API policies (auth, quotas, routing by path and version). A service mesh handles **east–west** traffic between services (mTLS, retries, telemetry) through sidecars or ambient proxies. They are complementary.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q2-api-gateway-vs-load-balancer-vs-service-mesh-l2l3-amazon-flipkart)

??? question "What is the BFF pattern and when would you use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Swiggy-/Zomato-style product companies

    A backend built for one client experience and owned by that client's team, exposing screen-shaped endpoints that aggregate and format data from domain services. Use it when clients differ significantly or when chatty client-to-service calls hurt latency and coupling. It must not hold domain rules.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q3-what-is-the-bff-pattern-and-when-would-you-use-it-l2l3-flipkart-swiggy-zomato-style-product-companies)

??? question "How do you version APIs at the edge?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Public paths are stable; versions are carried in a header or path segment and routed by the gateway or handled by the service's own API versioning (chapter 8). BFF endpoints are often versioned by path to match app releases, because old app versions live for months.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q10-how-do-you-version-apis-at-the-edge-l2l3-sap-cognizant)


**[11. Service Discovery Patterns](../architecture-patterns/ch11-service-discovery-patterns.md)**

??? question "What is service discovery and why do microservices need it?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    Instances come and go (scaling, deployments, failures), so callers can't use fixed addresses. Discovery keeps a current list of healthy instances per service name, through a registry, DNS or the platform, and the caller or a proxy picks one per request or connection.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q1-what-is-service-discovery-and-why-do-microservices-need-it-l2-tcs-infosys-cognizant)

??? question "Client-side vs server-side discovery?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Client-side: the caller queries the registry and chooses an instance itself (Eureka + Spring Cloud LoadBalancer, gRPC over a headless Service): rich policies, a library per language, client caches. Server-side: the caller uses a stable address and the platform or a proxy chooses (Kubernetes ClusterIP, cloud load balancers, meshes): language-neutral, simpler clients.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q2-client-side-vs-server-side-discovery-l2l3-amazon-flipkart)

??? question "Self-registration vs third-party registration?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Self-registration: the instance registers itself and heartbeats (Eureka). Third-party: an external component registers instances based on observed state (Kubernetes from pod readiness, Consul agents). Third-party registration removes discovery code from services and reflects real readiness.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q3-self-registration-vs-third-party-registration-l2-sap-cognizant)

??? question "How does service discovery work in Kubernetes?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · JP Morgan

    A Service selects pods by label; ready pods' IPs go into EndpointSlices; kube-proxy on each node routes connections to the Service's ClusterIP to a ready pod; CoreDNS resolves the Service name to the ClusterIP (or to all pod IPs for a headless Service). Readiness probes decide membership.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q4-how-does-service-discovery-work-in-kubernetes-l2l3-amazon-flipkart-jp-morgan)

??? question "Liveness vs readiness probes?"
    <span class="badge badge-l2">L2</span> Cognizant · Infosys · Amazon

    Liveness answers "should this container be restarted?" and must only check the process itself. Readiness answers "should it receive traffic now?" and controls Service membership. Checking dependencies in liveness causes restart storms; checking every dependency in readiness removes whole services during partial outages.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q9-liveness-vs-readiness-probes-l2-cognizant-infosys-amazon)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

??? question "What is the database-per-service pattern?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    Each service owns its data, and only that service reads and writes it; others go through its API or events. It can be implemented as private tables, a schema per service, or separate database instances. It's what makes services independently changeable and deployable.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q1-what-is-the-database-per-service-pattern-l2-tcs-infosys-cognizant)

??? question "Why is a shared database an anti-pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    The schema becomes an unversioned contract for every service that touches it: renames break others, locks and heavy queries interfere, nobody owns the rules for the data, and services can't be deployed or scaled independently. It's the tightest coupling a system can have.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q2-why-is-a-shared-database-an-anti-pattern-l2l3-amazon-flipkart)

??? question "How do you join data across services?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    API composition for display queries: the owner of the list is queried first, then other owners are called in batch, in parallel, and joined in memory. For queries that filter or sort across owners or are heavy, build a CQRS read model from events. For analytics, a warehouse fed by events or CDC.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q3-how-do-you-join-data-across-services-l2l3-flipkart-sap)

??? question "How do you handle reporting across microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · Accenture · Amazon

    Not on operational databases. Stream changes through events or CDC into a warehouse or lakehouse, model the data there, and give analysts access only to that store.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q10-how-do-you-handle-reporting-across-microservices-l2l3-cognizant-accenture-amazon)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "What is CQRS?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Separating the model that handles commands (enforcing rules on writes) from the model(s) that answer queries. Read models are shaped per query and can live in different stores, kept up to date from the write side, usually through events and therefore eventually consistent.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q1-what-is-cqrs-l2l3-cognizant-sap-flipkart)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

??? question "What is a saga?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A sequence of local transactions across services, where each step has a compensating action. If a step fails, the compensations of completed steps run in reverse order. It provides eventual atomicity without distributed locks.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q1-what-is-a-saga-l2l3-cognizant-sap-flipkart)

??? question "How do you show a saga's progress to users?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> product companies

    Expose the semantic lock as a state ("confirming with restaurant"), push updates as the saga progresses (WebSocket/SSE), and design copy for compensation outcomes ("restaurant couldn't accept; your payment was released").

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q12-how-do-you-show-a-sagas-progress-to-users-l2l3-product-companies)


**[15. Transactional Outbox, Inbox & CDC](../architecture-patterns/ch15-outbox-inbox-cdc.md)**

??? question "What is the dual-write problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant · SAP

    Writing to two systems (a database and a broker) without a transaction covering both. A failure between the writes leaves them inconsistent: commit-then-publish loses events, publish-then-commit invents them.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q1-what-is-the-dual-write-problem-l2l3-flipkart-cognizant-sap)


**[16. Strangler Fig, ACL & Monolith Migration](../architecture-patterns/ch16-strangler-fig-migration.md)**

??? question "What is the strangler fig pattern?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Incrementally replacing a legacy system by routing one capability at a time from the old system to a new one through a facade, until the old code can be deleted. Each step is small, reversible and gets production feedback, unlike a big-bang rewrite.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q1-what-is-the-strangler-fig-pattern-l2l3-cognizant-sap-flipkart)


**[17. Sidecar, Ambassador & Adapter](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)**

??? question "What is the sidecar pattern?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A helper container deployed in the same pod as the application, sharing its network and volumes, that adds capabilities (logging, proxying, config reload, telemetry) without changing the app's code. It has its own image, language and release cycle.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q1-what-is-the-sidecar-pattern-l2-cognizant-sap-flipkart)

??? question "Ambassador vs adapter?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Both are sidecars. An ambassador is an outbound proxy representing a remote service to the app (credentials, retries, routing). An adapter presents the app or a legacy system to the outside world in a standard form (metrics format, API shape).

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q2-ambassador-vs-adapter-l2l3-sap-cognizant)

??? question "Why can a proxy retry a GET but not a POST?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    GETs are safe and idempotent; a POST may have taken effect even if the response was lost, so repeating it could duplicate the effect (a second charge). A POST can be retried only if the API guarantees idempotency, for example with an Idempotency-Key the server deduplicates on.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q6-why-can-a-proxy-retry-a-get-but-not-a-post-l2l3-flipkart-amazon)

??? question "How would you ship logs from an app that only writes files?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    Share an emptyDir volume with a log-shipper sidecar that tails the file, keeps its position durably, handles rotation and only advances after the collector accepts a batch. Better long term: log to stdout and let a node-level agent collect.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q10-how-would-you-ship-logs-from-an-app-that-only-writes-files-l2l3-any)


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


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "What is a distributed monolith?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A system split into services that still can't be developed, deployed or fail independently, because of shared databases, shared domain libraries, synchronous chains or cycles. It has the costs of distribution without the benefits. The clearest evidence is services that are always released together.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q1-what-is-a-distributed-monolith-l2l3-cognizant-sap-flipkart)

??? question "What is the N+1 problem between services and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Calling another service once per item in a loop. Latency and failure probability grow with N. Parallelising helps latency but not failure rate; a batch endpoint fixes both; a local replica of the data removes the calls.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q3-what-is-the-n1-problem-between-services-and-how-do-you-fix-it-l2l3-flipkart-amazon)

??? question "What does deep synchronous chaining do to availability?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    Availabilities multiply: six services at 99.9% each give at most 99.4% for the chain, and latencies add. Shorten chains with replicated data and asynchronous steps, and protect the remaining calls with timeouts and fallbacks.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q10-what-does-deep-synchronous-chaining-do-to-availability-l2l3-any)


### Communication


**[21. Synchronous REST](../communication/ch21-rest-api-design.md)**

??? question "What makes an API RESTful?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Resources identified by URIs, a uniform interface (HTTP methods with their defined semantics), representations, stateless requests, cacheable responses, and ideally links between resources. In practice: nouns in URIs, methods and status codes used for what they mean.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q1-what-makes-an-api-restful-l1l2-cognizant-sap)

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

??? question "How do you prevent lost updates in a REST API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · SAP

    Optimistic concurrency: return an ETag (from a version column), require If-Match on updates, update with `where version = :expected`, return 412 when stale and 428 when If-Match is missing.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q5-how-do-you-prevent-lost-updates-in-a-rest-api-l2l3-jp-morgan-sap)

??? question "What is RFC 9457?"
    <span class="badge badge-l2">L2</span> SAP · Goldman Sachs

    Problem Details for HTTP APIs (obsoletes RFC 7807): a standard JSON error format with type, title, status, detail and instance, plus extensions. Spring supports it through `ProblemDetail` and `spring.mvc.problemdetails.enabled`.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q6-what-is-rfc-9457-l2-sap-goldman-sachs)

??? question "400 vs 422 vs 409?"
    <span class="badge badge-l2">L2</span> any

    400 for malformed or invalid requests (bad syntax, out-of-range parameter); 422 when the request is well-formed but semantically invalid for the business rules (many APIs use 400 for both, consistently); 409 when it conflicts with the resource's current state (cancel a delivered order).

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q7-400-vs-422-vs-409-l2-any)

??? question "How do ETags help performance?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Clients and caches revalidate with If-None-Match; an unchanged resource returns 304 with no body, saving bandwidth and, if the ETag comes from a version column checked cheaply, server work too.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q8-how-do-etags-help-performance-l2l3-amazon-flipkart)

??? question "How do you version a REST API?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Avoid versions for additive changes (clients ignore unknown fields). For breaking changes, version by header, media type or URI path, run versions side by side, announce deprecation with Deprecation/Sunset headers, and watch usage before removal. Spring Boot 4 supports this natively.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q9-how-do-you-version-a-rest-api-l2l3-sap-jp-morgan)


**[22. HTTP Clients](../communication/ch22-http-clients.md)**

??? question "RestTemplate, RestClient, WebClient: which and why?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    RestClient (synchronous, fluent) for most cases; WebClient for reactive applications and streaming. RestTemplate's deprecation was announced in Spring Framework 7.0, with formal deprecation planned for 7.1 and removal in 8.0; migrate to RestClient.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q1-resttemplate-restclient-webclient-which-and-why-l1l2-cognizant-sap-flipkart)

??? question "What are HTTP service clients?"
    <span class="badge badge-l2">L2</span> SAP · Flipkart

    Interfaces annotated with `@HttpExchange` for which Spring generates the client on top of RestClient or WebClient. Since Spring Framework 7/Boot 4 they are organised in groups (`@ImportHttpServices`) configured by properties and configurers.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q2-what-are-http-service-clients-l2-sap-flipkart)

??? question "Why not create a client per request?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Amazon · Cognizant

    You lose connection reuse: every call pays TCP and TLS handshakes, and you churn threads, sockets and memory. Measured here: 15.8 ms vs 2.1 ms at p50 on loopback over TLS. Build clients once.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q3-why-not-create-a-client-per-request-l1l2-amazon-cognizant)

??? question "Connect timeout vs read timeout?"
    <span class="badge badge-l2">L2</span> Amazon · JP Morgan

    Connect timeout bounds establishing the connection (dead host, full backlog); read timeout bounds waiting for response data. Both must be set; defaults are often infinite or very long.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q4-connect-timeout-vs-read-timeout-l2-amazon-jp-morgan)

??? question "Is a blocking HTTP client a scalability problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    On platform threads, yes: concurrency is capped by the pool. On virtual threads, blocking parks cheaply; measured here, 1,000 concurrent 200 ms calls took 0.56 s on virtual threads vs 4.2 s on a 50-thread pool. Still bound concurrency explicitly to protect the dependency.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q5-is-a-blocking-http-client-a-scalability-problem-l2l3-flipkart-goldman-sachs)

??? question "When would you still use WebClient?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    In WebFlux applications, for streaming responses (SSE, large NDJSON), and where reactive composition and backpressure across many concurrent streams are genuinely needed.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q6-when-would-you-still-use-webclient-l2l3-any)

??? question "How do you handle error responses from another service?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    Map them in one place (a default status handler): RFC 9457 problems into typed exceptions carrying `type` and details, others into exceptions with status and a truncated body; decide per type whether to retry, fix input, or fail.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q7-how-do-you-handle-error-responses-from-another-service-l2-sap-amazon)

??? question "How would you migrate from Feign?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Replace `@FeignClient` with an `@HttpExchange` interface in an HTTP service group; Spring MVC annotations map to `@GetExchange` etc.; ErrorDecoder → status handler, RequestInterceptor → request interceptor, Retryer → `@Retryable`; timeouts move to `spring.http.serviceclient.<group>.*`. Migrate per client with contract tests.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q8-how-would-you-migrate-from-feign-l2l3-cognizant-sap)

??? question "What should every service client have configured?"
    <span class="badge badge-l2">L2</span> any

    Base URL from configuration, connect and read timeouts, error mapping, auth, observability (metrics and trace propagation), and resilience (bulkhead, retries only where safe).

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q9-what-should-every-service-client-have-configured-l2-any)

??? question "How should a client consume a paginated API?"
    <span class="badge badge-l2">L2</span> any

    Follow the server's cursor or `next` link without interpreting it, fetch pages lazily as the caller needs them, respect the page-size maximum, and handle an invalid-cursor problem by restarting from the first page.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q11-how-should-a-client-consume-a-paginated-api-l2-any)

??? question "How do ETags help a client?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    The client keeps the last representation and its ETag and sends If-None-Match; a 304 means no body to transfer or parse. For updates, it sends If-Match so it never overwrites changes it hasn't seen.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q12-how-do-etags-help-a-client-l2l3-any)


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "What is gRPC, and how is it different from REST?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    An RPC framework: services and messages defined in `.proto` files, code generated for clients and servers, calls over HTTP/2 with Protocol Buffers payloads. REST models resources over HTTP with (usually) JSON and HTTP semantics such as caching. gRPC adds streaming, deadlines and a code-generated contract; REST is simpler to consume and works everywhere.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q1-what-is-grpc-and-how-is-it-different-from-rest-l1l2-cognizant-sap-flipkart)

??? question "How does Protocol Buffers encode data?"
    <span class="badge badge-l2">L2</span> Amazon · Goldman Sachs

    Each field is a tag, `(field_number << 3) | wire_type`, followed by the value: varints for integers and enums, fixed 4 or 8 bytes for floats and doubles, length-prefixed bytes for strings and nested messages. Names aren't sent; in proto3 default values aren't sent either.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q2-how-does-protocol-buffers-encode-data-l2-amazon-goldman-sachs)

??? question "Is gRPC always faster or smaller than JSON?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Smaller per message (2.5× in this chapter) and cheaper to parse, but not always smaller once compressed: a gzipped JSON stream was smaller than gzipped protobuf here, because gzip removes repeated field names. Choose gRPC for contracts, streaming and deadlines, and measure if size is the argument.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q3-is-grpc-always-faster-or-smaller-than-json-l2l3-flipkart-amazon)

??? question "What are the four RPC types? Give a use for each."
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Unary (get an order's status), server streaming (live tracking updates), client streaming (a rider app uploading GPS points, one summary back), bidirectional (dispatch negotiation or chat, both sides sending independently).

    [Full answer in chapter 23](../communication/ch23-grpc.md#q4-what-are-the-four-rpc-types-give-a-use-for-each-l2-sap-cognizant)

??? question "How do deadlines work, and why are they better than timeouts?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    The client sets an absolute deadline, sent as `grpc-timeout`; servers that make further calls inside the request pass the remaining time on, and cancellation propagates when it expires. Fixed per-hop timeouts know nothing about the caller's budget, so work continues after the caller has given up: 639 ms of wasted work per request vs 2 ms in this chapter's simulation.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q5-how-do-deadlines-work-and-why-are-they-better-than-timeouts-l2l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you handle errors in gRPC?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    Return a specific status code with a description (`NOT_FOUND`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`, `UNAVAILABLE`…) and, where useful, rich details (`google.rpc.Status` with `ErrorInfo` or `BadRequest`). Map exceptions centrally (Spring's `GrpcExceptionHandler`); clients decide on retry or fallback by code.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q6-how-do-you-handle-errors-in-grpc-l2-sap-amazon)

??? question "How do you evolve a protobuf schema without breaking clients?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    Add fields with new numbers; never change a field's number or (incompatibly) its type; `reserved` removed numbers and names; keep a zero `UNSPECIFIED` enum value and handle unknown values; put a version in the package for breaking changes; enforce with a breaking-change checker in CI.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q7-how-do-you-evolve-a-protobuf-schema-without-breaking-clients-l2l3-flipkart-jp-morgan)

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

??? question "How is GraphQL different from REST?"
    <span class="badge badge-l2">L2</span> Amazon · Cognizant

    REST exposes resources at URLs with HTTP semantics (methods, status codes, caching); the server decides each response's shape. GraphQL exposes one endpoint and a schema; the client decides the shape. GraphQL gains flexibility and fewer round trips; REST keeps HTTP caching, simple per-endpoint cost and familiar tooling.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q2-how-is-graphql-different-from-rest-l2-amazon-cognizant)

??? question "What is the N+1 problem in GraphQL and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Resolvers run per parent object, so a list of N items whose field calls a service makes 1 + N calls. Fix it with batch loading: DataLoader collects keys at one execution level and loads them in one call (Spring's `@BatchMapping` or `BatchLoaderRegistry`). In this chapter a screen went from 19 backend calls to 3, with identical data.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q3-what-is-the-n1-problem-in-graphql-and-how-do-you-fix-it-l2l3-flipkart-amazon-goldman-sachs)

??? question "How do `@SchemaMapping` and `@BatchMapping` differ in Spring for GraphQL?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    `@SchemaMapping` resolves a field for one parent at a time and can take field arguments. `@BatchMapping` receives all parents at once and returns a map (or ordered list) of values, registering a DataLoader underneath; it can't take field arguments, for which you use `BatchLoaderRegistry`.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q4-how-do-schemamapping-and-batchmapping-differ-in-spring-for-graphql-l2-sap-cognizant)

??? question "How are errors reported in GraphQL?"
    <span class="badge badge-l2">L2</span> SAP · Amazon

    In an `errors` array alongside `data`, each with a message, a `path` to the failed field and `extensions` such as a classification. Other fields still return, so responses can be partially successful, usually with HTTP 200. Map exceptions to error types centrally and monitor errors in responses, not just HTTP status.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q6-how-are-errors-reported-in-graphql-l2-sap-amazon)

??? question "How do you secure a GraphQL endpoint?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    Authenticate at the gateway and pass identity in context (never as arguments), authorise per field or object, disable introspection in production for first-party apps, use persisted queries as an allow-list, limit depth, complexity, aliases and page sizes, and avoid leaking internals in error messages.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q10-how-do-you-secure-a-graphql-endpoint-l2l3-jp-morgan-goldman-sachs)

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

??? question "Explain at-most-once, at-least-once and exactly-once."
    <span class="badge badge-l2">L2</span> Goldman Sachs · JP Morgan · Amazon

    Acknowledge before processing: at-most-once, messages can be lost. Acknowledge after: at-least-once, messages can be duplicated. Exactly-once effects come from at-least-once delivery plus an idempotent consumer (or a transaction covering both the effect and the offset, as Kafka transactions do within Kafka). In this chapter's simulation, idempotency turned 9 duplicates into 0.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q3-explain-at-most-once-at-least-once-and-exactly-once-l2-goldman-sachs-jp-morgan-amazon)

??? question "How does Kafka guarantee ordering, and how can you lose it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Within a partition, records are stored and delivered in order; a key sends all records of an entity to one partition. You lose it by processing a partition's records in parallel, by non-blocking retries, by changing the partition count (keys move), or with producer retries without idempotence. Use enough partitions and key-ordered concurrency.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q4-how-does-kafka-guarantee-ordering-and-how-can-you-lose-it-l2l3-flipkart-amazon)

??? question "What is a poison message and how do you handle it?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    A message that can never be processed. Detect it (validation, deserialization errors), classify it as not retryable, and send it to a DLT with the error attached, so the partition or queue keeps moving. Retrying it in place blocks everything behind it.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q5-what-is-a-poison-message-and-how-do-you-handle-it-l2-sap-cognizant)

??? question "What does Spring Kafka do by default when a listener throws?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    `DefaultErrorHandler` retries the record up to 10 times with no delay, then logs it and commits past it: the message is skipped. Always configure a recoverer (usually `DeadLetterPublishingRecoverer`), a backoff, and not-retryable exceptions.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q7-what-does-spring-kafka-do-by-default-when-a-listener-throws-l2l3-any)

??? question "How do you scale a Kafka consumer?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Add consumers to the group up to the number of partitions; beyond that, add partitions (planning for key redistribution) or use key-ordered parallel processing within a partition. Watch consumer lag, and keep processing per poll within `max.poll.interval.ms`.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q8-how-do-you-scale-a-kafka-consumer-l2-flipkart-amazon)

??? question "What is consumer lag and why does it matter?"
    <span class="badge badge-l2">L2</span> any

    The difference between a partition's latest offset and the group's committed offset: how far behind the consumer is. Growing lag means the consumer can't keep up or is stuck; it is the primary health signal for log consumers, and should be alerted on growth per partition.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q9-what-is-consumer-lag-and-why-does-it-matter-l2-any)


**[26. WebSockets & Server-Sent Events](../communication/ch26-websockets-sse.md)**

??? question "What are the options for pushing updates from server to client?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    Short polling (ask on a timer), long polling (the server holds the request until there is news), Server-Sent Events (a streaming HTTP response of events) and WebSocket (a full-duplex connection after an HTTP upgrade); plus push notifications for mobile apps in the background.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q1-what-are-the-options-for-pushing-updates-from-server-to-client-l1l2-cognizant-sap-flipkart)

??? question "SSE or WebSocket: how do you choose?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart

    SSE when data flows server → client: it's plain HTTP, has automatic reconnection and resume (`Last-Event-ID`), and works through proxies. WebSocket when the client also streams (chat, GPS, collaborative editing, games) or you need binary frames. Measured here, both delivered every update for ~100–120 bytes each.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q2-sse-or-websocket-how-do-you-choose-l2-amazon-flipkart)

??? question "Why is polling a problem at scale?"
    <span class="badge badge-l2">L2</span> Amazon · Cognizant

    Each poll is a full request (headers, auth, TLS, routing), most answer "nothing new", data is stale by up to the interval, and intermediate states are missed. Here 1 s polling saw 10 of 40 positions at ~1,190 bytes each, 564 ms stale on average.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q3-why-is-polling-a-problem-at-scale-l2-amazon-cognizant)

??? question "How does the WebSocket handshake work?"
    <span class="badge badge-l2">L2</span> SAP · any

    The client sends an HTTP GET with `Upgrade: websocket`, `Connection: Upgrade`, `Sec-WebSocket-Key` and `Sec-WebSocket-Version: 13`; the server responds `101 Switching Protocols` with `Sec-WebSocket-Accept` = Base64(SHA-1(key + a fixed GUID)). After that, both sides send frames; client frames are masked.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q4-how-does-the-websocket-handshake-work-l2-sap-any)

??? question "How do you avoid losing updates when a connection drops?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Flipkart

    Give every event an id, keep a short replay buffer per stream, and have the client send its last id on reconnect (`Last-Event-ID` for SSE, a resume message for WebSocket). For state like positions, sending the latest state on reconnect may be enough.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q8-how-do-you-avoid-losing-updates-when-a-connection-drops-l2l3-sap-flipkart)

??? question "Why do long-lived connections need heartbeats?"
    <span class="badge badge-l2">L2</span> any

    Load balancers, proxies and NAT devices close connections that are idle longer than their timeout, often silently; heartbeats keep them open and let both sides detect dead peers. Send them more often than the shortest idle timeout on the path.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q9-why-do-long-lived-connections-need-heartbeats-l2-any)

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

??? question "What's the difference between backward and forward compatibility?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Backward compatible: existing consumers keep working with the new version (new provider, old consumer). Forward compatible: consumers built for the new version work with the old provider or old data. Schema registries define them from the reader's side; with `BACKWARD` you upgrade consumers first, with `FORWARD` producers first, with `FULL` either.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q2-whats-the-difference-between-backward-and-forward-compatibility-l2-flipkart-amazon)

??? question "Is adding a field a breaking change?"
    <span class="badge badge-l2">L2</span> Amazon · SAP

    In a response, not by the rules, but it breaks strict readers (Kotlin serialization without `ignoreUnknownKeys`, Jackson 2 outside Spring Boot). In a request, adding a required field breaks old clients. In this chapter's history, three "safe" additions broke strict consumers.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q3-is-adding-a-field-a-breaking-change-l2-amazon-sap)

??? question "What is a tolerant reader?"
    <span class="badge badge-l2">L2</span> any

    A consumer that reads only what it needs, ignores unknown fields and maps unknown enum values to a safe default, so additive changes don't break it. With Jackson 3, unknown fields are ignored by default; unknown enum values need `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` and an `@JsonEnumDefaultValue` constant.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q4-what-is-a-tolerant-reader-l2-any)

??? question "What are consumer-driven contracts, and how do they differ from a schema diff?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Consumers publish executable expectations (requests and the response parts they use) that run against the provider in its build. A diff compares schema versions: broad, but blind to behaviour and usage, so it raises false alarms and misses behaviour changes. Contracts are precise but cover only consumers who write them. Here each caught breaks the other missed.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q5-what-are-consumer-driven-contracts-and-how-do-they-differ-from-a-schema-diff-l2l3-goldman-sachs-flipkart)

??? question "How do you version a REST API?"
    <span class="badge badge-l2">L2</span> Cognizant · Amazon

    Avoid versions for additive changes; for real breaks, a new major version via URL (`/v2`), media type or header, run in parallel with the old one, with deprecation and `Sunset` headers, usage tracking and a retirement date (chapter 21 covers the mechanics).

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q7-how-do-you-version-a-rest-api-l2-cognizant-amazon)

??? question "How would you rename a field without breaking consumers?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    Expand and contract: add the new field and populate both, migrate consumers (contracts and usage data show who's left), then remove the old field. Never rename in one step.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q8-how-would-you-rename-a-field-without-breaking-consumers-l2l3-flipkart-any)

??? question "What is AsyncAPI, and why does an event-driven system need it?"
    <span class="badge badge-l2">L2</span> SAP · Goldman Sachs

    AsyncAPI documents event-driven APIs: channels (topics), operations (send/receive), messages, headers and payload schemas. Events are contracts between teams just like HTTP APIs; documenting them enables review, code generation, validation and discovery, alongside a schema registry for runtime compatibility.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q9-what-is-asyncapi-and-why-does-an-event-driven-system-need-it-l2-sap-goldman-sachs)


**[28. Idempotency, Deduplication & Correlation](../communication/ch28-idempotency-correlation.md)**

??? question "What does idempotent mean, and which HTTP methods are idempotent?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Repeating the operation has the same effect as doing it once. `GET`, `HEAD`, `PUT`, `DELETE` and `OPTIONS` are idempotent by definition; `POST` and `PATCH` are not, so they need an idempotency key (or a design that makes them idempotent) to be retried safely.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q1-what-does-idempotent-mean-and-which-http-methods-are-idempotent-l1l2-cognizant-sap)

??? question "How do you implement idempotency keys?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    The client sends a unique key per action. The server atomically claims it (insert with a unique constraint) before any side effect, stores a request fingerprint, performs the operation, and stores the response with the business write. Duplicates get the stored response, 409 if still in progress, 422 if the fingerprint differs. Add a lease for crashes and pass the key downstream. Measured: exactly one charge per key under 4× concurrent duplicates.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q2-how-do-you-implement-idempotency-keys-l2l3-amazon-flipkart-goldman-sachs)

??? question "Why isn't a unique constraint on the payments table enough?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    It deduplicates rows, not side effects. If the external call happens before the insert, every duplicate still calls the provider; the constraint just rejects the extra rows. Measured: 200 rows, 800 charges.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q3-why-isnt-a-unique-constraint-on-the-payments-table-enough-l2l3-jp-morgan-amazon)

??? question "Is exactly-once delivery possible?"
    <span class="badge badge-l2">L2</span> any

    Not across an unreliable network: a sender can't distinguish a lost request from a lost acknowledgement, so it must retry, producing duplicates. Systems achieve exactly-once *effects* by combining at-least-once delivery with idempotent processing (or, within one system like Kafka, with transactions).

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q5-is-exactly-once-delivery-possible-l2-any)

??? question "How long should you keep idempotency keys or processed message ids?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    Longer than the slowest source of duplicates you care about, and published to clients. For API keys, longer than any client retry (24 h is common). For consumers, longer than redeliveries and re-publishes; for replays days later, use version checks on the entity instead of a huge store. Measured: a 1-hour window missed every replay; a 7-day table needed 302 M ids.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q6-how-long-should-you-keep-idempotency-keys-or-processed-message-ids-l2l3-flipkart-sap)

??? question "What goes wrong with in-memory deduplication in consumers?"
    <span class="badge badge-l2">L2</span> Amazon · any

    It's per instance and lost on restart. After rebalances and deploys, duplicates land on instances that never saw the originals (measured: 2,513 of 3,000 slipped through). Use a shared store or the inbox pattern, or make processing naturally idempotent.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q7-what-goes-wrong-with-in-memory-deduplication-in-consumers-l2-amazon-any)

??? question "What's the difference between correlation id, causation id and trace id?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    The correlation id groups everything in one business flow, possibly over days and many traces. The causation id points to the message that directly caused this one, giving the exact sequence. The trace id (W3C `traceparent`) groups the spans of one request tree for latency analysis and is often sampled. The event id identifies one message for deduplication.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q8-whats-the-difference-between-correlation-id-causation-id-and-trace-id-l2l3-sap-goldman-sachs)

??? question "Why not order events by timestamp?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Clocks on different machines differ by milliseconds even with NTP, and occasionally by much more; causally related events often happen milliseconds apart. Measured: 8% of orders misordered with good clocks, 39% with one drifting instance; causation ids had no errors. Use causation ids or per-entity sequence numbers.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q9-why-not-order-events-by-timestamp-l2l3-amazon-jp-morgan)

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

??? question "How do you choose a database for a microservice?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    From its access patterns with numbers (operations, rates, latency, consistency, loss tolerance), then the default store measured against them, then the operating cost of anything extra. The source of truth usually needs transactions and constraints (relational); specialised stores are justified by a measured gap, e.g. Redis for 12,500 position writes/s where PostgreSQL managed 7,100–8,000 with every commit synced (Redis: 30,000+).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q2-how-do-you-choose-a-database-for-a-microservice-l2l3-amazon-flipkart)

??? question "Document store or relational for a restaurant menu?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    It depends on the operations. Measured: a JSONB document read the whole menu 14× faster, but one price change was 5× slower with 15× the WAL, concurrent edits to one menu serialised (11× slower), and a cross-restaurant query was 180× slower. Best of both: write to tables, keep a document per menu rebuilt in the same transaction for reads.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q3-document-store-or-relational-for-a-restaurant-menu-l2l3-flipkart-amazon)

??? question "Is Redis faster than PostgreSQL?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    For small writes, yes, and the honest comparison is at the same durability: about 31,000 vs 7,100–8,000 updates/s with every write synced, 90,000+ vs 16,800 with up to a second of possible loss. Not for everything: Redis's sorted radius search computes and sorts every member in the circle (~950/s with 1,900 riders inside) where PostgreSQL's KNN index stopped after ten (~3,650/s).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q7-is-redis-faster-than-postgresql-l2l3-flipkart-any)

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


**[31. Data Consistency](../data/ch31-data-consistency.md)**

??? question "What does eventual consistency guarantee?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Only that replicas converge if writes stop. It says nothing about what a reader sees meanwhile: stale data, data going backwards, not seeing your own write. Real systems add session guarantees on top.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q1-what-does-eventual-consistency-guarantee-l2-cognizant-sap)

??? question "What is a lost update and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Two read-modify-write cycles interleave and one overwrites the other. Prevent with an atomic update (`SET x = x + 1`), a conditional update, `SELECT ... FOR UPDATE`, optimistic versions with retry, or REPEATABLE READ/SERIALIZABLE with retry. Measured: read-modify-write lost 15.5% of points; the atomic update lost none and was the fastest.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q5-what-is-a-lost-update-and-how-do-you-prevent-it-l2l3-goldman-sachs-jp-morgan-amazon)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "Cache-aside, read-through, write-through, write-behind: what's the difference?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Cache-aside: the app reads the cache and loads from the database on a miss. Read-through: the cache does the loading. Write-through: writes update the database and the cache together. Write-behind: writes go to the cache and are flushed to the database later. They trade staleness, write latency and durability differently.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q1-cache-aside-read-through-write-through-write-behind-whats-the-difference-l2-cognizant-sap)

??? question "What should you cache?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    What is expensive to produce or very hot, and may be slightly stale. Measured: Redis didn't speed up a primary-key read of a menu card (at 2.1–2.2 ms p50 against 1.7–1.8 ms), but turned a 130 ms aggregation into a 1.1 ms read. Never cache the inputs of decisions like payment or stock checks.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q2-what-should-you-cache-l2l3-flipkart-amazon)

??? question "What does @Cacheable(sync = true) do, and what doesn't it do?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Concurrent misses for the same key on one instance wait for a single load. It does nothing across instances (N instances, N loads: measured 4 at once with 4 instances) and it doesn't serve stale values; combine with stale-while-revalidate for expensive keys.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q5-what-does-cacheablesync-true-do-and-what-doesnt-it-do-l2l3-sap-cognizant)

??? question "How do you choose a TTL?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any

    From the staleness the business accepts and how the value is invalidated. With explicit invalidation, the TTL is a safety net and can be long; without it, the TTL is the staleness bound. Add jitter to avoid synchronised expiries; use soft and hard TTLs for stale-while-revalidate.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q11-how-do-you-choose-a-ttl-l2l3-any)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

??? question "How big should a connection pool be?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    Small, and sized for the database, not the instance: a few times the database's cores across the whole fleet, then measured. Measured on 2 vCPUs: 16 to 256 connections added at most 10–19% throughput and raised p99 inside the database from 6 ms to 76–98 ms. Extra demand should wait in the pool, not in the database.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q1-how-big-should-a-connection-pool-be-l2l3-flipkart-cognizant)

??? question "Why is opening a connection per request bad?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    A PostgreSQL connection is a process: forking, authentication and warming caches cost milliseconds. Measured: 6,470–6,748 transactions/s with a pool of 16 against 428–461 with a new connection per transaction.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q2-why-is-opening-a-connection-per-request-bad-l2-cognizant-sap)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "How do you build a report that needs data from several microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    Not by joining their databases. For small, rare queries, compose their APIs; for real reports, build a reporting store owned by a reporting service and fed by their events or CDC, shaped for the queries. Measured: API composition 2.7–2.9 s with hundreds of calls, a reporting copy under a second, a pre-aggregated table about 0.14 s.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q1-how-do-you-build-a-report-that-needs-data-from-several-microservices-l2l3-flipkart-cognizant)

??? question "Why not give analysts read access to the services' databases?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Load, bloat and coupling. Measured: two report loops on the primary cut write throughput by 67–69%; a long report transaction left 94,000–97,000 dead rows VACUUM couldn't remove; and every service's schema becomes a public contract.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q2-why-not-give-analysts-read-access-to-the-services-databases-l2-sap-cognizant)

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

??? question "What are layered jars, and why use them?"
    <span class="badge badge-l2">L2</span> Spring-heavy shops

    Separate image layers for dependencies, the Boot loader, snapshot dependencies and application classes. Each commit changes only the last one, and registries and nodes reuse the rest. Measured: a one-class change re-pushed 23–26 KB layered against the whole fat jar (1.6 MB here, tens of MB in real services).

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q3-what-are-layered-jars-and-why-use-them-l2-spring-heavy-shops)

??? question "Dockerfile, Jib or Buildpacks?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Atlassian · Zomato

    

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q4-dockerfile-jib-or-buildpacks-l2l3-atlassian-zomato)

??? question "How does the JVM size its heap in a container?"
    <span class="badge badge-l2">L2</span> Amazon · Walmart

    From the cgroup memory limit: 25% by default (`MaxRAMPercentage`), 50% when memory is small. Measured: 247 MiB in a 1 GB container and 121 MiB in 256 MB. Set `MaxRAMPercentage=75` to use the memory you pay for, and leave the rest for metaspace, threads, buffers and code cache.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q5-how-does-the-jvm-size-its-heap-in-a-container-l2-amazon-walmart)

??? question "Why does docker stop take 10 seconds?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Wipro · Accenture

    PID 1 isn't the JVM. A shell-form entrypoint or wrapper script receives SIGTERM and doesn't forward it, so after the timeout comes SIGKILL. Measured: 10.2 s and exit 137 against 157–171 ms with exec form or `exec java`.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q8-why-does-docker-stop-take-10-seconds-l1l2-wipro-accenture)

??? question "How do you harden a container?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks

    Run as a non-root user with a read-only root filesystem (a tmpfs for `/tmp`), drop all capabilities, set `no-new-privileges`, and use no shell or package manager in the image. Measured: menu-service ran unchanged under all of these.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q10-how-do-you-harden-a-container-l2l3-banks)


**[54. Kubernetes for Spring Developers](../deployment/ch54-kubernetes-for-spring.md)**

??? question "What's the difference between CPU requests and limits?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    A request is what the scheduler reserves and the share you get under contention. A limit is a CFS quota per 100 ms period; past it, the container is throttled until the next period. Measured: a 250m limit gave 3.9–4.0 s JVM starts and a 93–97 ms p99 for 5 ms of work, with almost no CPU used on average.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q1-whats-the-difference-between-cpu-requests-and-limits-l2-tcs-infosys)

??? question "How do ConfigMap changes reach a Spring Boot app?"
    <span class="badge badge-l2">L2</span> Spring-heavy shops

    Mounted directories are updated by the kubelet through a `..data` symlink swap; env vars, `subPath` mounts and values read at startup (including `configtree:`) are not. Measured: a watched mount saw changes after 1.4–76 s, and the other three never did. Either watch and reload, or restart on change.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q3-how-do-configmap-changes-reach-a-spring-boot-app-l2-spring-heavy-shops)

??? question "What does a PodDisruptionBudget do?"
    <span class="badge badge-l2">L2</span> Zomato · Walmart

    It limits voluntary disruptions (drains, upgrades, autoscaler removals): the eviction API refuses evictions that break it. Measured: without one, a drain evicted all three pods in 2 s and 295–331 requests failed; with `minAvailable: 2`, one pod was evicted, the rest refused, and 0–1 requests failed. It doesn't protect against crashes.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q5-what-does-a-poddisruptionbudget-do-l2-zomato-walmart)

??? question "What is the Downward API for?"
    <span class="badge badge-l2">L2</span> Accenture

    Giving the pod its own metadata (name, namespace, node, labels) and resources (requests and limits) as env vars or files. Use them for log and metric fields and to size thread pools. An unset limit reads as the node's allocatable.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q7-what-is-the-downward-api-for-l2-accenture)

??? question "Deployment, StatefulSet, DaemonSet, Job: when?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Wipro · Cognizant

    

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q8-deployment-statefulset-daemonset-job-when-l1l2-wipro-cognizant)

??? question "How should a Spring Boot service find other services?"
    <span class="badge badge-l2">L2</span> Swiggy-style rounds

    By Service DNS name (`http://order-service.orders.svc.cluster.local` or just `order-service`), with Kubernetes doing discovery. No Eureka, no client-side registry, unless you need per-request balancing, which a mesh provides.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q10-how-should-a-spring-boot-service-find-other-services-l2-swiggy-style-rounds)


**[55. Helm, Kustomize & Environment Promotion](../deployment/ch55-helm-kustomize.md)**

??? question "Helm or kustomize?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Helm when you need packaging, defaults and logic, a release history and hooks, or are consuming third-party software. kustomize when plain YAML with small per-environment patches is enough, and no templating language is wanted. Many teams use both: a shared chart for their services, kustomize overlays over upstream manifests. Either way, render and review the output.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q1-helm-or-kustomize-l2-tcs-infosys)

??? question "How do you make pods pick up a ConfigMap change?"
    <span class="badge badge-l2">L2</span> Zomato · Paytm

    Change the pod template when the config changes: a `checksum/config` annotation in Helm, or kustomize's hashed generator names. Measured: both replaced the pods in 5–9 s; without them, no pod restarted and the env var stayed old. Alternatively, a watched mount with code that reloads (chapter 54).

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q5-how-do-you-make-pods-pick-up-a-configmap-change-l2-zomato-paytm)

??? question "What does `--rollback-on-failure` do?"
    <span class="badge badge-l2">L2</span> Wipro · Cognizant

    It waits for the release's resources to be ready and, if they aren't by the timeout, rolls back to the last successful revision. It replaced `--atomic` in Helm 4. Measured: a crashing release was rolled back after the full 60 s timeout, with history showing the failed revision. The rolling update had already kept the old pods serving.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q6-what-does-rollback-on-failure-do-l2-wipro-cognizant)

??? question "Where do secrets go in Helm or kustomize?"
    <span class="badge badge-l2">L2</span> banks

    Not in values files or Git in plain text. Reference secrets managed by an external secrets operator, or keep them encrypted (SOPS, sealed secrets) and decrypted in the cluster. Templates then mount a Secret by name (chapter 39).

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q9-where-do-secrets-go-in-helm-or-kustomize-l2-banks)

??? question "What does kustomize's hash suffix on ConfigMaps buy you?"
    <span class="badge badge-l2">L2</span> Accenture

    A new name for every content change, and every reference rewritten to match. The pod template changes, so the change rolls out like a release, and the old ConfigMap stays for a rollback. The cost is accumulation, so prune old ones.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q10-what-does-kustomizes-hash-suffix-on-configmaps-buy-you-l2-accenture)


**[56. Autoscaling](../deployment/ch56-autoscaling.md)**

??? question "How does the HPA decide how many pods to run?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Every 15 s it reads pod CPU from metrics-server, divides by the pods' requests, and computes desired = ceil(utilization ÷ target × pods). Ratios within 10% of 1 change nothing. Then a stabilization window and rate policies smooth the result, within min and max.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q1-how-does-the-hpa-decide-how-many-pods-to-run-l2-tcs-infosys)

??? question "Why did the HPA take so long to scale up?"
    <span class="badge badge-l2">L2</span> Swiggy · Zomato

    The metric is a window average from metrics-server (15 s resolution here, 60 s upstream), and the HPA syncs every 15 s. Measured: 29–34 s from load to the first new pods, and 77–94 s to the final size. Then the pods have to start. Keep headroom (a target of 50–70%) and raise minimums before known peaks.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q2-why-did-the-hpa-take-so-long-to-scale-up-l2-swiggy-zomato)

??? question "Why doesn't the HPA scale down right away?"
    <span class="badge badge-l2">L2</span> Flipkart

    The default scale-down stabilization window is 300 s: it scales down only to the highest recommendation of the last five minutes. Measured: back to one pod 331–333 s after the load stopped. It's there to prevent flapping; measured without it, one-minute bursts caused 325–432 failed requests in eight minutes, against 97–192 with it.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q3-why-doesnt-the-hpa-scale-down-right-away-l2-flipkart)

??? question "What is utilization relative to?"
    <span class="badge badge-l2">L2</span> Wipro

    The pods' requests. 50% means each pod uses half its request. Doubling the request with the same target halves the pod count for the same load.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q5-what-is-utilization-relative-to-l2-wipro)

??? question "What should you alert on?"
    <span class="badge badge-l2">L2</span> Accenture

    The HPA at its maximum for longer than a few minutes (capacity is capped), `ScalingLimited` conditions, pods pending for lack of nodes, and flapping (scaling events per hour).

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q11-what-should-you-alert-on-l2-accenture)


**[57. Service Mesh](../deployment/ch57-service-mesh.md)**

??? question "What does a service mesh give you?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Per-request load balancing, retries, timeouts and outlier detection; mTLS with workload identities and authorization; and uniform telemetry, all outside the application, in any language. Measured: per-request balancing fixed the keep-alive imbalance within 10 s of a scale-out.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q1-what-does-a-service-mesh-give-you-l2-tcs-infosys)

??? question "Data plane versus control plane?"
    <span class="badge badge-l2">L2</span> Accenture

    The data plane is the proxies that carry traffic (Envoy, linkerd2-proxy, ztunnel). The control plane discovers endpoints, issues certificates and pushes configuration to them over xDS (istiod, Linkerd's controller). This chapter wrote the data plane's configuration by hand.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q2-data-plane-versus-control-plane-l2-accenture)

??? question "What is outlier detection?"
    <span class="badge badge-l2">L2</span> Flipkart

    Passive health checking: an endpoint that fails N times in a row is ejected from balancing for a growing period. Measured: 26 failures in a minute instead of 1,000. Watch `max_ejection_percent`: its 10% default ejected nothing in a three-pod service.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q6-what-is-outlier-detection-l2-flipkart)

??? question "Why native sidecars?"
    <span class="badge badge-l2">L2</span> Kubernetes rounds

    As an init container with `restartPolicy: Always`, the proxy starts before the app (so its first calls work) and stops after it (so its last ones do), and Jobs can complete. Before, apps raced their sidecars at both ends.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q9-why-native-sidecars-l2-kubernetes-rounds)


**[58. CI/CD, GitOps & Progressive Delivery](../deployment/ch58-cicd-gitops.md)**

??? question "What is GitOps?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Desired state for each environment in Git, declaratively, pulled and continuously reconciled by an agent in the cluster. Deploys are commits, rollbacks are reverts, and drift is corrected. Measured: commits applied within one 10 s interval, and a manual scale reverted within one.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q1-what-is-gitops-l2-tcs-infosys)

??? question "How does a canary decide to continue?"
    <span class="badge badge-l2">L2</span> Swiggy · Zomato

    After each traffic step, compare the canary's error rate and latency with the stable version's over a window, with a minimum sample, and abort early on a burst. Measured: a release failing 5% was aborted at 10% after 19–22 s, with 7–10 failed requests.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q4-how-does-a-canary-decide-to-continue-l2-swiggy-zomato)

??? question "What does 'build once' mean, and why?"
    <span class="badge badge-l2">L2</span> Wipro

    One image per commit, referred to by digest in every environment, with its SBOM and signature. Rebuilding per environment means production runs bits nobody tested (chapter 55).

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q7-what-does-build-once-mean-and-why-l2-wipro)

??? question "How do you roll back with GitOps?"
    <span class="badge badge-l2">L2</span> Accenture

    Revert the commit in the environment repository; the controller applies it. For a canary in progress, the rollout controller sets the canary weight to 0 immediately, faster than any commit.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q8-how-do-you-roll-back-with-gitops-l2-accenture)


**[59. Cloud-Native on AWS, Azure & GCP](../deployment/ch59-cloud-native-platforms.md)**

??? question "How should a pod on EKS, AKS or GKE call cloud APIs?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    With workload identity: the pod's projected service account token, with the cloud as audience, is exchanged for short credentials of a role that trusts that one service account. No keys. Measured: the exchange refused the wrong audience, the wrong account and an edited token.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q1-how-should-a-pod-on-eks-aks-or-gke-call-cloud-apis-l2-tcs-infosys)


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

??? question "Why is a Java service slow to start?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    At startup the JVM loads, verifies and links thousands of classes, runs static initialisers and the framework's wiring, and then interprets the first requests until the JIT compiles hot code. Measured at 1 CPU: 1.1–1.2 s to the first answer, and 27–48 ms for that first request against about 2 ms once warm.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q1-why-is-a-java-service-slow-to-start-l2-tcs-infosys)

??? question "What is class data sharing (CDS)?"
    <span class="badge badge-l2">L2</span> Wipro

    An archive of parsed, verified classes that the JVM maps at startup instead of loading them from jars. The JDK ships one for its own classes; AppCDS adds the application's, from a training run. Measured: 753–761 ms instead of 1.1–1.2 s.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q2-what-is-class-data-sharing-cds-l2-wipro)


### Foundations


**[1. Monolith vs SOA vs Microservices vs Modular Monolith](../foundations/ch01-architecture-styles.md)**

??? question "What is a modular monolith and why is it popular again?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · Accenture · SAP

    One deployable split into business modules with enforced public APIs and hidden internals, with each module owning its data. You get most of the design benefits of microservices without the network costs, and modules can be extracted later.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q2-what-is-a-modular-monolith-and-why-is-it-popular-again-l1l2-cognizant-accenture-sap)

??? question "What is a distributed monolith and how do you detect one?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Services that are physically separate but logically coupled, so they must change and deploy together. You pay all the costs of distribution and get none of the independence.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q3-what-is-a-distributed-monolith-and-how-do-you-detect-one-l2l3-flipkart-amazon)

??? question "Isn't SOA the same as microservices?"
    <span class="badge badge-l2">L2</span> JP Morgan · SAP · Goldman Sachs

    They share the idea of services, but differ in where logic lives and who governs. SOA used smart pipes (an ESB with routing, transformation and business rules), canonical enterprise models and central governance. Microservices use dumb pipes, bounded contexts and team-owned services.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q4-isnt-soa-the-same-as-microservices-l2-jp-morgan-sap-goldman-sachs)

??? question "When would you not use microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    With few teams, a domain still being discovered, no platform maturity (CI/CD, observability, on-call), heavy cross-entity transactional needs, or uniform load. Start with a modular monolith and extract when a module earns it.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q5-when-would-you-not-use-microservices-l2l3-amazon-goldman-sachs)

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

??? question "Explain 'smart endpoints and dumb pipes'."
    <span class="badge badge-l2">L2</span> JP Morgan · SAP

    Business logic lives in services; the transport only moves messages. This avoids the ESB trap, where routing and transformation logic in middleware creates a central bottleneck.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q4-explain-smart-endpoints-and-dumb-pipes-l2-jp-morgan-sap)

??? question "What does 'design for failure' mean in code?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Timeouts on every remote call. Retries only for idempotent operations and transient errors, with backoff and jitter. Bounded concurrency. Circuit breakers. A defined degradation mode per dependency. And tests for each failure path.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q5-what-does-design-for-failure-mean-in-code-l2l3-amazon-goldman-sachs)

??? question "Is code duplication across services acceptable?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · SAP

    Often yes. DRY across service boundaries creates coupling through shared libraries. Share technical concerns (logging, tracing setup), never domain models. Duplicate small DTOs and own them.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q8-is-code-duplication-across-services-acceptable-l2l3-amazon-sap)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Name the fallacies of distributed computing. Which hurt microservices most?"
    <span class="badge badge-l2">L2</span> Cognizant · TCS · Amazon

    The network is reliable; latency is zero; bandwidth is infinite; the network is secure; topology doesn't change; there is one administrator; transport cost is zero; the network is homogeneous. The first two hurt most: unreliability causes lost or duplicated work, and latency kills chatty designs.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q1-name-the-fallacies-of-distributed-computing-which-hurt-microservices-most-l2-cognizant-tcs-amazon)

??? question "Explain the CAP theorem precisely."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    During a network partition, a replicated system must choose between linearizable consistency and availability. Partition tolerance isn't optional, and the choice only applies while partitioned.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q2-explain-the-cap-theorem-precisely-l2l3-amazon-flipkart-goldman-sachs)

??? question "What is 'exactly-once' and is it possible?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Exactly-once *delivery* over an unreliable network isn't achievable. Exactly-once *effects* are: at-least-once delivery plus idempotent processing, with the dedup record committed atomically with the effect. Kafka's EOS covers read-process-write within Kafka. External side effects still need idempotency.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q7-what-is-exactly-once-and-is-it-possible-l2l3-flipkart-amazon-goldman-sachs)

??? question "A request times out. Should the client retry?"
    <span class="badge badge-l2">L2</span> any company

    Only if the operation is idempotent (naturally or via an idempotency key). A timeout means "unknown". Retrying a non-idempotent operation risks doing it twice, and not retrying risks not doing it at all. Retry with backoff and jitter, within the caller's deadline.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q12-a-request-times-out-should-the-client-retry-l2-any-company)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "Why store configuration in the environment rather than in the jar?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Amazon · Flipkart

    So one build can run in every environment. If config is baked in, each environment needs its own build, and production runs an artifact that was never tested.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q2-why-store-configuration-in-the-environment-rather-than-in-the-jar-l1l2-amazon-flipkart)

??? question "How does Spring Boot resolve configuration from many sources?"
    <span class="badge badge-l2">L2</span> SAP · service companies

    A precedence order in which command-line arguments override system properties, which override environment variables, which override config files (profile-specific over default). Environment variables bind through relaxed binding (`FOODFLEET_ZONES_CITY` → `foodfleet.zones.city`). `spring.config.import` adds config trees, Config Server or Vault.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q3-how-does-spring-boot-resolve-configuration-from-many-sources-l2-sap-service-companies)

??? question "Explain graceful shutdown on Kubernetes."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Kubernetes removes the pod from endpoints and sends SIGTERM at about the same time. The app stops accepting new connections, finishes in-flight work within a timeout, releases resources and exits. Add a short `preStop` sleep because endpoint removal is asynchronous, and set `terminationGracePeriodSeconds` higher than the app's shutdown timeout.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q5-explain-graceful-shutdown-on-kubernetes-l2l3-amazon-jp-morgan)

??? question "What is dev/prod parity and how do you achieve it with Spring Boot?"
    <span class="badge badge-l2">L2</span> Flipkart · Cognizant

    Minimise the time, personnel and tooling gaps between development and production. With Spring Boot: Docker Compose support for local backing services, Testcontainers with `@ServiceConnection` for tests (same major versions as production), the same image in every environment, and continuous deployment so changes reach production quickly.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q7-what-is-devprod-parity-and-how-do-you-achieve-it-with-spring-boot-l2-flipkart-cognizant)

??? question "Your scheduled job runs in every replica after scaling to 5 pods. What's wrong and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> any company

    Factors VIII and XII. The process model assumes replicas are interchangeable, so in-process schedulers multiply. Use a Kubernetes CronJob, a distributed lock (ShedLock) with fencing where correctness matters, or a dedicated single-replica worker.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q9-your-scheduled-job-runs-in-every-replica-after-scaling-to-5-pods-whats-wrong-and-how-do-you-fix-it-l2l3-any-company)


**[5. Domain-Driven Design](../foundations/ch05-domain-driven-design.md)**

??? question "What is a bounded context?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant · Accenture

    The explicit boundary within which a model and its ubiquitous language are consistent. Inside it each term has one meaning; across contexts the same word ("order") can mean different things, and you translate at the boundary.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q1-what-is-a-bounded-context-l2-sap-cognizant-accenture)

??? question "Entity vs value object?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Flipkart

    An entity has identity that persists as its attributes change (an order line within an order). A value object has no identity, is immutable and is equal by value (Money, Address). Prefer value objects: they're simpler, thread-safe and self-validating.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q2-entity-vs-value-object-l2-tcs-infosys-flipkart)

??? question "Domain service vs application service?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · service companies

    A domain service holds domain logic that doesn't fit one entity (`PricingPolicy`). An application service orchestrates a use case: load, call the domain, save, publish. It contains no business rules.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q5-domain-service-vs-application-service-l2l3-sap-service-companies)

??? question "What's wrong with an anemic domain model?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    The rules live outside the objects, so invalid states are possible and rules get duplicated and drift. It's fine for CRUD-like supporting subdomains, and costly for core domains with real invariants.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q11-whats-wrong-with-an-anemic-domain-model-l2l3-goldman-sachs-flipkart)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

??? question "How do you decide service boundaries?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    Start from business capabilities and bounded contexts, discovered with domain experts (Event Storming). Group things that change together and must be consistent together; separate things with different owners, scaling needs, volatility or compliance requirements. Validate candidates against real use cases (how many calls per checkout?) and against change history, and start as modules before making them services.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q1-how-do-you-decide-service-boundaries-l2l3-cognizant-sap-flipkart)

??? question "Decompose by business capability vs by subdomain?"
    <span class="badge badge-l2">L2</span> Cognizant · Accenture · SAP

    Capability asks *what the business does* (take orders, dispatch riders); subdomain asks *where the model and language change*. They usually produce the same boundaries. When they differ, the language boundary wins, because one service with two meanings of "order" is the worse problem.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q2-decompose-by-business-capability-vs-by-subdomain-l2-cognizant-accenture-sap)

??? question "How big should a microservice be?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · TCS · Infosys

    Not a line count. Owned by one team, changed for one reason, deployable on its own, owning its data, and understandable by a new team member in a couple of weeks. Too small shows up as chatty call chains and shotgun changes; too large shows up as several teams negotiating releases.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q4-how-big-should-a-microservice-be-l2l3-amazon-tcs-infosys)


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "What is Conway's Law?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · TCS · SAP

    Systems end up mirroring the communication structure of the organisation that builds them (Melvin Conway, 1968). Teams that talk easily produce tightly integrated parts; teams that rarely talk produce formal interfaces between their parts.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q1-what-is-conways-law-l2l3-cognizant-tcs-sap)

??? question "Strong vs weak vs collective code ownership?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Strong: only the owner changes the code. Weak: anyone may change it, the owner reviews. Collective: no owner. For production services, one owning team per service (strong or weak with mandatory owner review); collective ownership only inside a single team.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q7-strong-vs-weak-vs-collective-code-ownership-l2l3-sap-cognizant)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "What are the biggest changes in Spring Boot 4 compared with Boot 3?"
    <span class="badge badge-l2">L2</span> Cognizant · TCS · Infosys

    Spring Framework 7 and Jakarta EE 11; Boot split into **modular starters**; **Jackson 3**; **JSpecify** null-safety annotations; built-in **API versioning**; the **HTTP service registry** for declarative clients; **`@Retryable`/`@ConcurrencyLimit`** in the framework; `@MockitoBean`, `MockMvcTester`, `RestTestClient` for testing. Java 17 is still the minimum.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q1-what-are-the-biggest-changes-in-spring-boot-4-compared-with-boot-3-l2-cognizant-tcs-infosys)

??? question "How do virtual threads work, and when should you not use them?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    They're JVM-scheduled threads that unmount from a carrier platform thread when they block on I/O, so blocking code scales to many concurrent requests. They don't help CPU-bound work. Don't pool them, avoid heavy `ThreadLocal` caches, and put explicit limits on shared resources. On Java 21–23, long `synchronized` blocks around I/O pin the carrier; Java 24 fixed that (JEP 491).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q2-how-do-virtual-threads-work-and-when-should-you-not-use-them-l2l3-amazon-flipkart-goldman-sachs)

??? question "How does Spring Boot 4's API versioning work?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    Configure where the version comes from (header, query parameter, path segment or media type parameter) with `spring.mvc.apiversion.*`, then add `version = "1.0"` or `"2.0+"` to request mappings. Unsupported versions get 400. A default version can serve old clients. An `ApiVersionDeprecationHandler` bean adds `Deprecation`, `Sunset` and `Link` headers to deprecated versions. Clients (RestClient, HTTP service clients) can send the version automatically.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q4-how-does-spring-boot-4s-api-versioning-work-l2l3-sap-amazon)

??? question "How do you call another microservice in Spring Boot 4?"
    <span class="badge badge-l2">L2</span> Cognizant · Accenture · Flipkart

    Declare an interface with `@HttpExchange`/`@GetExchange`, register it with `@ImportHttpServices(group = "kitchen", types = …)`, and configure `spring.http.serviceclient.kitchen.base-url`, timeouts and API version in properties. Inject the interface. Add `spring-boot-starter-restclient`. Use one group per downstream service so timeouts differ.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q6-how-do-you-call-another-microservice-in-spring-boot-4-l2-cognizant-accenture-flipkart)

??? question "What is JSpecify and why did Spring adopt it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · any Spring shop

    A standard set of nullness annotations (`@NullMarked`, `@Nullable`, `@NonNull`, `@NullUnmarked`) agreed by several vendors. Spring Framework 7 and Boot 4 annotate their APIs with it, so tools like NullAway, IntelliJ and Kotlin see consistent nullness from the framework down to your code. `@NullMarked` on a package makes non-null the default.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q8-what-is-jspecify-and-why-did-spring-adopt-it-l2l3-sap-any-spring-shop)

??? question "What changed in Spring Boot testing?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    `@MockBean`/`@SpyBean` are gone; use `@MockitoBean`/`@MockitoSpyBean`. `@SpringBootTest` doesn't configure MockMvc or `TestRestTemplate` by itself; add `@AutoConfigureMockMvc` or `@AutoConfigureTestRestTemplate`. Test starters are per technology (`spring-boot-starter-webmvc-test`). `MockMvcTester` gives AssertJ-style assertions, and `RestTestClient` offers a RestClient-style API bound to MockMvc, an application context or a running server.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q11-what-changed-in-spring-boot-testing-l2-tcs-infosys-cognizant)

??? question "What happens when an old app version calls a versioned endpoint without a version?"
    <span class="badge badge-l2">L2</span> Swiggy-/Zomato-style product companies

    With `spring.mvc.apiversion.default` set, it gets the default version (FoodFleet: 1.0); without a default and with versioning required, it gets 400. Serve old clients a default, mark that version deprecated with `Deprecation`/`Sunset` headers, measure its traffic, and force-update the app before the sunset date.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q12-what-happens-when-an-old-app-version-calls-a-versioned-endpoint-without-a-version-l2-swiggy-zomato-style-product-companies)


### Governance & Organization


**[71. API Governance & Developer Portals](../governance-org/ch71-api-governance.md)**

??? question "What do the Deprecation and Sunset headers contain?"
    <span class="badge badge-l2">L2</span> any API team

    RFC 9745's `Deprecation` is a structured-field date, `@` followed by epoch seconds (`@1767571200`); RFC 8594's `Sunset` is an HTTP-date (`Mon, 06 Jul 2026 00:00:00 GMT`). A `Link` with `rel="deprecation"` or `rel="sunset"` points to details.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q6-what-do-the-deprecation-and-sunset-headers-contain-l2-any-api-team)

??? question "snake_case or camelCase for JSON properties?"
    <span class="badge badge-l2">L2</span> Zalando

    Either, consistently. Zalando's guideline [118] requires snake_case; FoodFleet chose camelCase to match Jackson's defaults and its Java code, and wrote that down as a deliberate deviation. Consistency, enforced by a rule, matters more than the choice.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q10-snake_case-or-camelcase-for-json-properties-l2-zalando)


**[72. Platform Engineering & Golden Paths](../governance-org/ch72-platform-engineering.md)**

??? question "What is a golden path?"
    <span class="badge badge-l2">L2</span> Spotify

    The supported, easiest way to do a common task, such as creating a service, with good defaults built in. Optional, but far cheaper than the alternatives. Measured: from a name to two ready pods in 6.5–8.0 s with every platform decision included.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q2-what-is-a-golden-path-l2-spotify)

??? question "Are text checks on manifests enough?"
    <span class="badge badge-l2">L2</span> any platform team

    No; they confirm decisions, not validity. Measured: a manifest passed the probe check while the API server rejected it (a probe without a port). Add a server-side dry run (`kubectl apply --dry-run=server`) or schema validation.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q8-are-text-checks-on-manifests-enough-l2-any-platform-team)


**[73. ADRs, Fitness Functions & Tech Radar](../governance-org/ch73-adrs-fitness-functions.md)**

??? question "What is an ADR, and what goes in one?"
    <span class="badge badge-l2">L2</span> Thoughtworks · Infosys

    A short, numbered record of one architectural decision, kept with the code: context, decision, status and consequences (Nygard's format), optionally with considered options and their pros and cons (MADR). It records why, so later engineers don't relitigate or unknowingly undo it.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q1-what-is-an-adr-and-what-goes-in-one-l2-thoughtworks-infosys)

??? question "How do you change a decision recorded in an ADR?"
    <span class="badge badge-l2">L2</span> Thoughtworks

    Write a new ADR that supersedes or amends it, and update the old one's status with a link (adr-tools' `adr new -s` and `adr link` do both). Never edit an accepted record. Measured on this handbook's log: an edited record cited a decision made 14 months later.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q2-how-do-you-change-a-decision-recorded-in-an-adr-l2-thoughtworks)


**[75. Multi-Tenancy, Multi-Region & Compliance](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)**

??? question "What are the main multi-tenancy models?"
    <span class="badge badge-l2">L2</span> Infosys · TCS

    Pool (shared tables with a tenant column), bridge (a schema per tenant), silo (a database or whole stack per tenant). Measured at 100 tenants: provisioning 0.3 ms, 7 ms and 52–87 ms per tenant; 36 MB, 43 MB and 811 MB; 1, 1 and 100 connections.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q1-what-are-the-main-multi-tenancy-models-l2-infosys-tcs)

??? question "Where should the tenant ID come from?"
    <span class="badge badge-l2">L2</span> any SaaS company

    From the authenticated token, as a claim set by the identity provider, never from a header or parameter the client controls. Then carried in the request context to every data access.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q10-where-should-the-tenant-id-come-from-l2-any-saas-company)


**[76. Serverless & Function-Based Services](../governance-org/ch76-serverless-microservices.md)**

??? question "When does serverless (FaaS) make sense?"
    <span class="badge badge-l2">L2</span> Infosys · TCS

    For sparse, bursty or event-driven work, where per-request pricing and scale to zero beat paying for idle capacity. Measured over a simulated day: 2,000 webhooks cost $0.0006 as functions, against $2.37 for two always-on containers.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q1-when-does-serverless-faas-make-sense-l2-infosys-tcs)

??? question "What is Spring Cloud Function for?"
    <span class="badge badge-l2">L2</span> Thoughtworks

    Writing business logic as `Function`/`Consumer`/`Supplier` beans, selected with `spring.cloud.function.definition`, and running them through adapters (AWS `FunctionInvoker`, Azure, HTTP) without changing the code.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q8-what-is-spring-cloud-function-for-l2-thoughtworks)


**[77. AI-Native Microservices](../governance-org/ch77-ai-native-microservices.md)**

??? question "How should a tool report 'not allowed' or 'not found'?"
    <span class="badge badge-l2">L2</span> Thoughtworks

    As a tool result with `isError: true`, not a protocol error, so the model sees it and can respond to the user (the MCP specification says so explicitly).

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q10-how-should-a-tool-report-not-allowed-or-not-found-l2-thoughtworks)


### Observability


**[47. Observability Fundamentals](../observability/ch47-observability-fundamentals.md)**

??? question "Name the signals and what each is for."
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Wipro · Accenture

    Metrics: aggregated numbers for alerting and trends. Logs: records of what code decided. Traces: one request's path and where its time went. Profiles: which code used CPU or memory. Events: changes (deploys, flags) and wide per-request records for slicing.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q2-name-the-signals-and-what-each-is-for-l1l2-wipro-accenture)

??? question "Why can't metrics answer 'which users are affected'?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    Each label value multiplies the number of time series, so user IDs, order IDs or restaurant IDs can't be labels. Measured: a `restaurant_id` label with 1,000 values turned 265 series into 260,005 and took Prometheus from about 100 MB to 670 MB. Per-entity questions go to traces and wide events, which cost per record, not per distinct value.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q3-why-cant-metrics-answer-which-users-are-affected-l2-swiggy-flipkart)

??? question "What is cardinality, and how do you control it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Atlassian · PhonePe

    The number of distinct values of a label (and, multiplied, of label combinations). Control it with a budget per service, reviewed labels, route templates instead of raw paths, and dropping or aggregating labels in the collector before storage.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q4-what-is-cardinality-and-how-do-you-control-it-l2l3-atlassian-phonepe)

??? question "How do you correlate logs, metrics and traces?"
    <span class="badge badge-l2">L2</span> Zomato · Walmart

    Propagate W3C trace context on every hop; put the trace ID in every log record and wide event; attach trace IDs to metric histograms as exemplars; use the same resource attributes (service.name, service.version, service.instance.id) on all signals.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q6-how-do-you-correlate-logs-metrics-and-traces-l2-zomato-walmart)

??? question "What does the OpenTelemetry Collector do?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Microsoft · Amazon

    It receives telemetry (OTLP and other formats), processes it (memory limits, batching, attribute changes, redaction, sampling, deriving metrics from spans) and exports it to backends. It decouples services from backends and is where tail sampling and cost controls live.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q8-what-does-the-opentelemetry-collector-do-l2l3-microsoft-amazon)

??? question "What should never go into telemetry?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks · PayPal

    Secrets, credentials, card numbers and other personal data, in logs, span attributes, metric labels and resource attributes alike. Measured: the Java agent's default resource includes the JVM command line and masks only properties named *password* or *secret*; a token passed with `-D` appeared in spans and in Prometheus labels. Redact at the source and again in the collector.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q11-what-should-never-go-into-telemetry-l2l3-banks-paypal)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "Counter, gauge, timer, distribution summary: when to use which?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Counter for things that happen (orders placed; take `rate()`), gauge for a current level read when scraped (queue length), timer for durations (count, total, max, histogram), distribution summary for non-time sizes (basket value), long-task timer for work still running.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q1-counter-gauge-timer-distribution-summary-when-to-use-which-l2-tcs-infosys)

??? question "Why can't you average percentiles across pods?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    A percentile isn't additive: the fleet's p99 depends on how every pod's whole distribution combines, which the per-pod p99s don't contain. Measured with one slow pod in four: the average of the pods' p99s was 137 ms, the true fleet p99 322 ms, `histogram_quantile` over summed buckets 317–318 ms. Publish histogram buckets and compute the percentile from their sum.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q2-why-cant-you-average-percentiles-across-pods-l2l3-swiggy-flipkart)

??? question "What are RED, USE and the four golden signals?"
    <span class="badge badge-l2">L2</span> Google-style SRE rounds

    RED for services: rate, errors, duration. USE for resources: utilisation, saturation, errors. The golden signals: latency, traffic, errors, saturation. In Spring: RED from `http.server.requests`; USE from JVM, executor and connection-pool meters.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q4-what-are-red-use-and-the-four-golden-signals-l2-google-style-sre-rounds)

??? question "What is the Observation API, and why use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Spring-heavy shops

    Instrument once with an Observation; handlers turn it into a timer, a span and anything else. Low-cardinality key values become metric tags and span attributes; high-cardinality ones only span attributes. In the tests, the checkout Observation's timer carried payment.method, city.tier and outcome, never order.id or restaurant.id; measured, an Observation with the meter handler costs 0.7–1.3 µs.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q5-what-is-the-observation-api-and-why-use-it-l2l3-spring-heavy-shops)

??? question "Why did my gauge become NaN?"
    <span class="badge badge-l2">L2</span> Infosys · Accenture

    Micrometer holds a gauge's object weakly so a gauge doesn't keep it alive; if nothing else references it, it is garbage-collected and the gauge reports NaN. Keep a reference, register it in a binder that owns it, or use `strongReference(true)`.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q10-why-did-my-gauge-become-nan-l2-infosys-accenture)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "What is in a W3C traceparent header?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Version, a 16-byte trace ID, the 8-byte span ID of the caller's span, and flags whose low bit says whether the trace is sampled: `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`. `tracestate` carries vendor data alongside.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q1-what-is-in-a-w3c-traceparent-header-l2-tcs-infosys)

??? question "How does a trace continue across threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · PhonePe

    The context is bound to the thread; it must be captured when work is handed over and restored when it runs. The agent does this for JDK executors and async callbacks; bare threads and own queues need `Context.wrap`. Measured: executors, `CompletableFuture`, scheduled and virtual-thread executors kept the trace 200 of 200 times; `new Thread()`, `Thread.ofVirtual().start()` and a hand-rolled queue lost it 200 of 200.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q2-how-does-a-trace-continue-across-threads-l2l3-swiggy-phonepe)

??? question "What does parent-based sampling mean, and why use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · PayPal

    Follow the caller's sampled flag; apply a ratio only to new roots. Every service then keeps or drops the same traces, so sampled traces are complete. Measured: parent-based everywhere gave 105–108 complete traces from 1,000 orders at 10% and nothing else; one service re-deciding produced 400–904 orphan fragments or 43–47 incomplete traces.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q6-what-does-parent-based-sampling-mean-and-why-use-it-l2l3-amazon-paypal)

??? question "How does Spring Boot 4 do tracing?"
    <span class="badge badge-l2">L2</span> Spring-heavy shops

    Micrometer Observations produce spans through Micrometer Tracing with an OpenTelemetry (or Brave) bridge; properties set sampling (`management.tracing.sampling.probability`, 0.1 by default), propagation (produce W3C, consume W3C and B3 by default) and baggage (`remote-fields`, `tag-fields`, `correlation.fields`); spans are exported over OTLP.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q9-how-does-spring-boot-4-do-tracing-l2-spring-heavy-shops)

??? question "When do you add manual spans?"
    <span class="badge badge-l2">L2</span> Infosys · Accenture

    Around internal work that matters and that instrumentation can't see: a fraud decision, a pricing calculation, a batch step. Name it for the operation, add namespaced attributes with low-to-moderate cardinality, record exceptions and set the status, and keep it short-lived.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q10-when-do-you-add-manual-spans-l2-infosys-accenture)


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "What is the MDC, and what goes wrong with it?"
    <span class="badge badge-l2">L2</span> Swiggy · PhonePe

    It is a per-thread map that Logback copies into every event. It doesn't follow work to other threads, and values left on pooled threads mislabel later work. Measured: request IDs were missing on 200 of 200 lines from executors and new threads, and wrong on 99 of 200 after a task leaked one.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q2-what-is-the-mdc-and-what-goes-wrong-with-it-l2-swiggy-phonepe)

??? question "How do you correlate logs across services and threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Put the trace ID on every line, from Micrometer Tracing or the OpenTelemetry agent. Trace context is propagated across HTTP, messaging and executors, so it follows the work. Measured: the agent's `trace_id` was right on every executor hop, including a thread with a leaked MDC. It was missing only on bare threads.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q3-how-do-you-correlate-logs-across-services-and-threads-l2l3-flipkart-razorpay)

??? question "What fields should every log line have?"
    <span class="badge badge-l2">L2</span> Zomato · Accenture

    Timestamp (UTC, ISO-8601), level, logger, message, service name and version, environment, region or zone, and trace and span IDs. Event-specific fields go in as key-value pairs: order ID, amount, attempt. Leave out anything nobody queries.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q8-what-fields-should-every-log-line-have-l2-zomato-accenture)

??? question "MDC or key-value pairs?"
    <span class="badge badge-l2">L2</span> Spring-heavy shops

    Use the MDC for context that applies to everything a thread does during a request: request ID, tenant. Use key-value pairs (`atInfo().addKeyValue`) for facts about one event. Key-value pairs need no cleanup and can't leak into other requests.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q9-mdc-or-key-value-pairs-l2-spring-heavy-shops)

??? question "Should logs go to files or stdout in Kubernetes?"
    <span class="badge badge-l2">L2</span> Infosys · Wipro

    Stdout, as JSON lines. The node's agent (Fluent Bit, the OpenTelemetry Collector) collects, enriches with pod metadata and ships them. Apps don't manage rotation or disk, and a crashing pod's last lines are still collected.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q11-should-logs-go-to-files-or-stdout-in-kubernetes-l2-infosys-wipro)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "What are an SLI, an SLO and an SLA?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    An SLI is a measured ratio of good to valid events, such as checkout requests without server errors. An SLO is the target for it over a window: 99.9% over 30 days. An SLA is a contract with consequences, set looser than the SLO so you hear about problems before you owe anything.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q1-what-are-an-sli-an-slo-and-an-sla-l2-tcs-infosys)

??? question "Why not alert on error ratio > 1%?"
    <span class="badge badge-l2">L2</span> Zomato · Amazon

    It has no relationship to the budget. It misses slow failures and pages on brief ones. Measured: it never fired for failure rates of 0.6%, 0.25% and 0.15% that spent 14.5–43% of the month's budget.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q5-why-not-alert-on-error-ratio-1-l2-zomato-amazon)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "Should the liveness probe check the database?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    No. A restart can't fix the database, and every replica fails together. Measured: during a 60-second outage, every pod restarted twice, `/menu` (no database) failed more than half the time, and orders came back 14–95 seconds after the database. Keep shared dependencies in `/actuator/health` for monitoring.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q2-should-the-liveness-probe-check-the-database-l2-swiggy-flipkart)

??? question "And readiness?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> PhonePe · Razorpay

    Not for dependencies every replica shares: they all go not-ready together and the Service has no endpoints. Measured: new connections refused for the whole outage and 10–12 seconds after it. Answer 503 fast from the endpoints that need the dependency instead. Readiness on a dependency makes sense only when it is per-pod.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q3-and-readiness-l2l3-phonepe-razorpay)

??? question "What is a startup probe for?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Accenture · Wipro

    Giving a slow start its own budget. Liveness and readiness don't run until it succeeds. Measured: a 40-second start under a 10-second liveness delay never started (4–5 restarts, CrashLoopBackOff); with a startup probe it was ready in 43–44 seconds.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q4-what-is-a-startup-probe-for-l1l2-accenture-wipro)

??? question "How does Spring Boot support Kubernetes probes?"
    <span class="badge badge-l2">L2</span> Spring-heavy shops

    `ApplicationAvailability` tracks liveness (CORRECT/BROKEN) and readiness (ACCEPTING/REFUSING) states. They are served as `/actuator/health/liveness` and `/readiness` groups, on by default in Boot 4. Readiness goes REFUSING on shutdown, and `server.shutdown` is graceful by default since 3.4.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q6-how-does-spring-boot-support-kubernetes-probes-l2-spring-heavy-shops)

??? question "How do you secure Actuator?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks · Goldman Sachs

    

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q7-how-do-you-secure-actuator-l2l3-banks-goldman-sachs)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "Do virtual threads make a service faster?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    No. They make waiting cheap, so more requests can wait at once. Measured: at 250 requests a second, platform threads, virtual threads and an event loop all served the quote at a p50 of 153–155 ms. Virtual threads raised capacity from 1,060–1,100 to about 1,490 a second, because 200 platform threads were the limit.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q1-do-virtual-threads-make-a-service-faster-l2-tcs-infosys)

??? question "Why report p99.9 and max as well as p99?"
    <span class="badge badge-l2">L2</span> Atlassian

    Because a p99 near a boundary is unstable and hides what's behind it. Measured: with 1% of calls slow, one run's p99 was 17.6 ms and the next 200.6 ms. Users who make many requests, or requests that fan out, see the p99.9.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q9-why-report-p999-and-max-as-well-as-p99-l2-atlassian)

??? question "How many threads does a WebFlux service use?"
    <span class="badge badge-l2">L2</span> Red Hat

    Reactor Netty starts max(CPUs, 4) event-loop threads, shared by server and client. Blocking calls belong on `Schedulers.boundedElastic()` (10 × CPUs threads), never on the loop. A service that blocks on the loop stalls every connection on it.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q11-how-many-threads-does-a-webflux-service-use-l2-red-hat)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "How does the JVM know it's in a container?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Since JDK 10 (and 8u191) it reads its cgroup's CPU quota and memory limit (`UseContainerSupport`, on by default) and treats them as the machine. Measured: a quota of 0.5 or 1 CPU showed as 1 processor, 1.5 as 2, and the heap came from the container's memory.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q1-how-does-the-jvm-know-its-in-a-container-l2-tcs-infosys)

??? question "What's the difference between OutOfMemoryError and OOMKilled?"
    <span class="badge badge-l2">L2</span> Wipro

    `OutOfMemoryError` is the JVM failing to allocate on its heap (or metaspace, or direct memory); it can be logged and handled, and `ExitOnOutOfMemoryError` makes it exit. OOMKilled is the kernel killing the container for exceeding its limit (exit 137): no stack trace, and usually caused by native memory beyond the heap.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q4-whats-the-difference-between-outofmemoryerror-and-oomkilled-l2-wipro)

??? question "Is JFR safe to run in production?"
    <span class="badge badge-l2">L2</span> PhonePe

    Yes; it was designed for that. Measured: a continuous recording with `default` settings cost 2–7% more CPU and `profile` 5–9%, with latency changes within noise. Keep it on, bounded by `maxsize`/`maxage`, and dump it when something goes wrong.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q9-is-jfr-safe-to-run-in-production-l2-phonepe)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "What makes a service stateless?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    No copy holds data between requests that another copy would need: sessions, carts and caches that must agree live in shared stores. Measured: with carts in memory, 0–2 of 200 survived requests spread over 3 copies; in PostgreSQL, all 200, even after a copy was replaced.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q1-what-makes-a-service-stateless-l2-tcs-infosys)

??? question "Are sticky sessions a good idea?"
    <span class="badge badge-l2">L2</span> Wipro

    Rarely. They keep in-memory state reachable only while that copy lives, and they spread load badly. Measured: ClientIP affinity behind one address sent 100% of requests to one of 3 copies, and replacing it lost all 200 carts. Move the state out instead.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q2-are-sticky-sessions-a-good-idea-l2-wipro)


**[69. High-Throughput Kafka Tuning](../performance-scalability/ch69-kafka-throughput-tuning.md)**

??? question "What do linger.ms and batch.size do?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    The producer keeps a batch per partition; `batch.size` caps its bytes, and `linger.ms` is how long it waits for more records before sending. Measured at 10,000 records a second: linger 0 made 66,000–69,000 requests, Kafka 4's 5 ms made about 3,300, and 20 ms with 128 KB batches about 950, for an 11 ms higher median latency.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q1-what-do-lingerms-and-batchsize-do-l2-tcs-infosys)

??? question "Why does adding consumers sometimes not reduce lag?"
    <span class="badge badge-l2">L2</span> Swiggy

    A partition goes to one member of a group, so a group's parallelism is capped by the partition count. Measured: 12 consumers on 6 partitions gave the same 4,800–4,900 records a second as 6, with 6 idle.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q5-why-does-adding-consumers-sometimes-not-reduce-lag-l2-swiggy)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "What is FinOps?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    The practice of managing cloud cost as engineering and finance together: inform (allocate costs to teams and units), optimise (right-size, buy capacity well) and operate (keep doing it). Its question is "what does a unit of business cost, and is that changing?".

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q1-what-is-finops-l2-tcs-infosys)


### Resilience


**[42. Failure Modes & Cascading Failures](../resilience/ch42-failure-modes.md)**

??? question "What is a cascading failure?"
    <span class="badge badge-l2">L2</span> TCS · Cognizant

    A failure that spreads through dependencies: a slow service ties up its callers' threads and queues, their latency rises, their callers time out and retry, and the slowness moves outward until unrelated features fail. Timeouts, bounded concurrency and shedding stop it from spreading.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q1-what-is-a-cascading-failure-l2-tcs-cognizant)

??? question "Why does latency jump near capacity?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    Queueing: waiting time grows like 1/(1−ρ). Measured: p99 was 4.5× the service time at 52% utilisation and 12× at 97%; mean latency tripled between 80% and 97%. Keep headroom so that losing a zone or a slow dependency doesn't push you past ~80%.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q2-why-does-latency-jump-near-capacity-l2l3-amazon-flipkart)

??? question "What is a thundering herd, and how do you avoid one?"
    <span class="badge badge-l2">L2</span> Accenture · Infosys

    Many clients acting at once: reconnects after a restart, cache entries expiring together, jobs at :00. Full jitter on retries and reconnects, randomised schedules, request coalescing for caches. Measured: exponential backoff without jitter took 42 s to reconnect 600 clients, full jitter under 6 s with about 40% fewer attempts.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q8-what-is-a-thundering-herd-and-how-do-you-avoid-one-l2-accenture-infosys)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "Explain a circuit breaker's states."
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    CLOSED: calls pass, outcomes recorded in a sliding window; when the failure or slow-call rate passes its threshold (after a minimum number of calls) it opens. OPEN: calls are refused at once for the wait duration. HALF_OPEN: a few trial calls decide whether to close or open again. Resilience4j adds DISABLED, FORCED_OPEN and METRICS_ONLY.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q1-explain-a-circuit-breakers-states-l2-tcs-infosys-cognizant)

??? question "Why doesn't my Resilience4j retry retry RestClient failures?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Infosys · Wipro

    `retryExceptions` matches the exception's own class, and RestClient throws `ResourceAccessException` wrapping the `IOException`. Use `retryOnException` with a predicate that walks the cause chain (or Spring's `@Retryable(includes = ...)`, which does).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q7-why-doesnt-my-resilience4j-retry-retry-restclient-failures-l2l3-infosys-wipro)

??? question "Resilience4j or Spring Framework 7's `@Retryable`?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Spring 7 covers retries and concurrency limits with no extra dependency; set a multiplier, jitter (at least the delay: it never goes below the initial delay) and a timeout. For circuit breakers, slow-call detection, rate limits, metrics and per-instance configuration, use Resilience4j. They coexist.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q8-resilience4j-or-spring-framework-7s-retryable-l2l3-sap-cognizant)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "How do you choose a timeout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart

    From two numbers: the dependency's latency distribution (a ceiling a little above its p99.9, so healthy calls aren't cut off) and the caller's remaining deadline (never wait longer than the caller will). Use the smaller, minus a reserve for your own work, and skip the call if what's left is too small to succeed.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q1-how-do-you-choose-a-timeout-l2l3-amazon-flipkart)

??? question "What is a brownout?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Myntra · Amazon

    Turning optional features down automatically when their dependencies struggle: a controller sets the share of requests that use the feature from how many recent calls were slow. It removes load, which a timeout alone doesn't: timeouts left recommendations at 0% for 15 s after a spike; the dimmer was back to 77–81%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q10-what-is-a-brownout-l2l3-myntra-amazon)

??? question "What is backpressure?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Confluent-style rounds · Walmart

    The consumer controlling how fast it receives: in `Flow`, by requesting *n* items; in Kafka, by polling. When the buffer is full the producer blocks or drops; buffering without bound only delays the failure. Measured: an unbounded queue aged events to 10 s; a 256-item buffer kept them under 0.3 s.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q11-what-is-backpressure-l2l3-confluent-style-rounds-walmart)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "What is chaos engineering?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Running controlled experiments that inject real-world faults (latency, crashes, partitions) into a system to check a hypothesis about its steady state, so weaknesses are found at a time you choose rather than during an incident. It is an experiment, not random breakage: steady state, hypothesis, small blast radius, automatic stop.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q1-what-is-chaos-engineering-l2-tcs-infosys)

??? question "How is it different from testing?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Accenture · SAP

    A test checks a known expected behaviour of a component; an experiment asks an open question about the whole system under conditions tests don't create (a black-holed dependency, a slow trickle, a dead pod at peak). Once an experiment's hypothesis holds, it often becomes a scheduled regression check, much like a test.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q2-how-is-it-different-from-testing-l2l3-accenture-sap)

??? question "What is a game day?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · PayPal

    A planned session where a team runs one or more experiments together, with observers, a runbook and a stop button, practising the system's behaviour and the team's response (detection, decisions, communication), followed by a written review with actions.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q11-what-is-a-game-day-l2l3-flipkart-paypal)


**[46. Disaster Recovery & Multi-Region](../resilience/ch46-disaster-recovery.md)**

??? question "What are RPO and RTO?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    RPO is how much acknowledged data you may lose in a disaster (time or transactions); RTO is how long until the service works again. Both should be measured in rehearsals, not just declared: a synchronous standby gives an RPO of zero; an asynchronous one, its replication lag.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q1-what-are-rpo-and-rto-l2-tcs-infosys)

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

??? question "What is the most common API vulnerability, and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon

    Broken object-level authorization (OWASP API1): authenticated callers acting on objects that aren't theirs by changing an id. Check ownership on every operation in the service, using the authenticated caller and the stored object, never ids from the request; answer 404 for objects the caller can't see; test the full role × action × state matrix.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q2-what-is-the-most-common-api-vulnerability-and-how-do-you-prevent-it-l2l3-flipkart-amazon)

??? question "How should passwords be stored?"
    <span class="badge badge-l2">L2</span> any

    With a slow, salted, adaptive hash: Argon2id, scrypt, bcrypt, or PBKDF2-HMAC-SHA256 at 600,000+ iterations (OWASP's minimum), with parameters stored so they can be raised. Measured: 11.7–13.0 million SHA-256 guesses a second against 7 PBKDF2 guesses on one core. Better still, let an identity provider hold them.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q5-how-should-passwords-be-stored-l2-any)

??? question "Walk through the OWASP API Security Top 10."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · Amazon

    2023 edition: API1 broken object-level authorization, API2 broken authentication, API3 broken object property level authorization, API4 unrestricted resource consumption, API5 broken function-level authorization, API6 unrestricted access to sensitive business flows, API7 SSRF, API8 security misconfiguration, API9 improper inventory management, API10 unsafe consumption of APIs. Three of the first five are authorization.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q10-walk-through-the-owasp-api-security-top-10-l2l3-cognizant-amazon)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "Walk through the authorization code flow with PKCE."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    The client creates a random verifier and sends its SHA-256 hash with the authorization request; the user signs in; the authorization server redirects back with a one-time code; the client redeems the code with the verifier at the token endpoint and gets tokens. A code captured on the way back is useless without the verifier.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q1-walk-through-the-authorization-code-flow-with-pkce-l2l3-flipkart-cognizant)

??? question "What changes in OAuth 2.1?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Amazon

    It consolidates the security best practices: PKCE for all authorization code flows, no implicit flow, no password grant, exact redirect URI matching, no bearer tokens in query strings, refresh tokens sender-constrained or rotated. As of September 2026 it is still an Internet-Draft; RFC 9700 already recommends the same.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q2-what-changes-in-oauth-21-l2l3-sap-amazon)

??? question "Access token vs ID token?"
    <span class="badge badge-l2">L2</span> Cognizant · any

    The ID token (OIDC) tells the client who signed in; it is for the client and must not be sent to APIs. The access token is for APIs and carries audience and scopes. RFC 9068 marks JWT access tokens with `typ: at+jwt` so resource servers can tell them apart.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q3-access-token-vs-id-token-l2-cognizant-any)

??? question "How does a service call another service with no user involved?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Client credentials: the service authenticates to the token endpoint (private key JWT or mTLS better than a shared secret) and gets a token for the target audience with its own scopes. In a mesh, workload identity (SPIFFE, chapter 38) can replace or complement it.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q11-how-does-a-service-call-another-service-with-no-user-involved-l2l3-cognizant-sap)


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "How do you configure a Spring Boot 4 service as an OAuth 2 resource server?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Add `spring-boot-starter-security-oauth2-resource-server`; set `spring.security.oauth2.resourceserver.jwt.issuer-uri`, `audiences` and `jws-algorithms`; define a `SecurityFilterChain` with `oauth2ResourceServer(jwt)` and explicit `authorizeHttpRequests` rules ending in `denyAll()`; make it stateless.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q1-how-do-you-configure-a-spring-boot-4-service-as-an-oauth-2-resource-server-l2-cognizant-sap)

??? question "hasRole vs hasAuthority?"
    <span class="badge badge-l2">L2</span> Cognizant · any

    `hasAuthority("X")` checks the exact string; `hasRole("X")` checks `ROLE_X`. By convention scopes map to `SCOPE_` authorities and roles to `ROLE_`; a converter decides which claims become which.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q2-hasrole-vs-hasauthority-l2-cognizant-any)

??? question "How do you test security rules?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · SAP

    MockMvc with spring-security-test's `jwt()` post-processor and realistic claims: no token → 401, wrong scope → 403, other users' objects → 404, missing role → 403, unknown endpoint → 403. Plain-Java rule classes get exhaustive unit tests.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q8-how-do-you-test-security-rules-l2l3-flipkart-sap)

??? question "Which password encoder?"
    <span class="badge badge-l2">L2</span> any

    `DelegatingPasswordEncoder` with bcrypt (strength from measurement; 12 cost ~290 ms here) or Argon2; old hashes upgraded on login via `upgradeEncoding`. Spring's PBKDF2 "v5.8" defaults are 310,000 iterations, below OWASP's current 600,000. Better: keep passwords in the identity provider.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q9-which-password-encoder-l2-any)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "What is mutual TLS, and what does it not give you?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Both sides present certificates, so each knows which workload is at the other end. It doesn't say what that workload may do (you still need a per-route policy and tokens), it is checked at the handshake only, and it ends wherever TLS is terminated.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q1-what-is-mutual-tls-and-what-does-it-not-give-you-l2-cognizant-sap)

??? question "How does Spring Boot do mutual TLS?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    SSL bundles: `spring.ssl.bundle.pem.<name>` with keystore and truststore, `server.ssl.bundle` and `server.ssl.client-auth: need`, `reload-on-update: true` for rotated files. Clients use `RestClientSsl.fromBundle`, which builds the request factory once, so rebuild clients on bundle updates.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q6-how-does-spring-boot-do-mutual-tls-l2l3-sap-cognizant)

??? question "What does 'zero trust' mean between microservices?"
    <span class="badge badge-l2">L2</span> Cognizant · any

    No trust from network location: every call is authenticated (workload identity), authorized (per route and per object), encrypted, and logged; credentials are short-lived and scoped. NIST SP 800-207 describes the architecture.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q11-what-does-zero-trust-mean-between-microservices-l2-cognizant-any)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "Why not keep secrets in environment variables?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    They are inherited by every child process, readable in `/proc/<pid>/environ` by the same user, shown by debug tools, often logged by crash reporters, and never updated without a restart. Measured: inherited by a child process and readable through `/proc`. Mount secrets as files instead.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q1-why-not-keep-secrets-in-environment-variables-l2-cognizant-sap)

??? question "Are Kubernetes Secrets secure?"
    <span class="badge badge-l2">L2</span> Flipkart · Amazon

    Not by default: they are base64-encoded and stored unencrypted in etcd, and anyone who can create pods in a namespace can read its Secrets. Turn on encryption at rest (KMS v2), restrict RBAC, mount them as read-only volumes, and keep the source of truth in a secrets store.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q2-are-kubernetes-secrets-secure-l2-flipkart-amazon)

??? question "How do secrets reach a Spring Boot application?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    `spring.config.import` with `configtree:` for mounted files (read once at start-up) or `vault://` for Spring Cloud Vault (Kubernetes auth, KV v2 as properties, leases managed by Spring Vault). Rotating values need code that reacts: a lease listener or a file watcher.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q8-how-do-secrets-reach-a-spring-boot-application-l2-cognizant-sap)

??? question "How do you keep secrets out of logs and dumps?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · any

    A value type that masks itself, structured logging that never logs headers or credentials, actuator's `show-values: never`, and treating heap dumps as secrets. Measured: a `String` password was in every heap dump; only wiped `char[]`s were absent.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q9-how-do-you-keep-secrets-out-of-logs-and-dumps-l2l3-flipkart-any)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "Fixed window, sliding window or token bucket?"
    <span class="badge badge-l2">L2</span> Amazon · Flipkart

    Fixed windows are cheap but allow twice the limit across a boundary (measured: 200 in a minute for a limit of 100). Sliding window counters fix most of that with two counters. A token bucket (GCRA) gives a steady rate plus a deliberate burst; the burst size is part of the limit (a bucket of 100 allowed 199 in a minute, a bucket of 10, 109).

    [Full answer in chapter 40](../security/ch40-api-security.md#q1-fixed-window-sliding-window-or-token-bucket-l2-amazon-flipkart)

??? question "What does input validation protect against, and what doesn't it?"
    <span class="badge badge-l2">L2</span> Cognizant · TCS

    It enforces the contract (types, lengths, ranges, allowed fields), which blocks mass assignment and nonsense values and limits what reaches the code. It doesn't stop SQL injection (bind parameters do) or XSS (output encoding does), and filtering quotes rejects real customers like O'Brien. Normalise, check, measure length in code points.

    [Full answer in chapter 40](../security/ch40-api-security.md#q5-what-does-input-validation-protect-against-and-what-doesnt-it-l2-cognizant-tcs)

??? question "Does CORS protect my API?"
    <span class="badge badge-l2">L2</span> Cognizant · Infosys

    No. CORS is the browser letting other origins *read* responses; it doesn't stop requests (form posts and simple fetches go out regardless) and means nothing to non-browser clients. Misconfigured, it hands other origins the user's data. Measured in Chromium: a server reflecting the Origin gave a cross-site page the user's orders when the cookie was `SameSite=None`.

    [Full answer in chapter 40](../security/ch40-api-security.md#q7-does-cors-protect-my-api-l2-cognizant-infosys)

??? question "When do you need CSRF protection?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Accenture

    When the browser attaches credentials automatically: cookies (and HTTP Basic, client certificates). An API that takes only bearer tokens in a header has nothing to forge. For cookie sessions use a CSRF token plus Fetch Metadata; SameSite is a useful extra but treats sibling subdomains as the same site (measured: they got even Strict cookies).

    [Full answer in chapter 40](../security/ch40-api-security.md#q8-when-do-you-need-csrf-protection-l2l3-sap-accenture)

??? question "How do you configure CORS for an SPA on www calling api?"
    <span class="badge badge-l2">L2</span> Flipkart · TCS

    An exact allow-list of origins (`https://www.foodfleet.in`), credentials only if cookies are needed, the methods and headers actually used, `Vary: Origin`, a preflight max age. Not `*` with credentials (browsers refuse it), not reflection, not suffix or pattern matching, never `null`. Better still, serve the SPA and its BFF from one origin and need no CORS.

    [Full answer in chapter 40](../security/ch40-api-security.md#q9-how-do-you-configure-cors-for-an-spa-on-www-calling-api-l2-flipkart-tcs)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "What is an SBOM, and what do you do with it?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    A list of every component in an artifact with exact identities (purls) and versions, in CycloneDX or SPDX. You keep one per release, attach it to the image, and match it against advisories continuously, so "are we affected?" is a query. Spring Boot's parent configures the CycloneDX plugin and serves the result at `/actuator/sbom`.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q1-what-is-an-sbom-and-what-do-you-do-with-it-l2-cognizant-sap)

??? question "Why pin GitHub Actions by SHA?"
    <span class="badge badge-l2">L2</span> Flipkart · Accenture

    Tags are mutable: in March 2025 the `tj-actions/changed-files` tags were moved to a commit that dumped runner secrets into logs. A SHA can't be moved. Pair it with Renovate/Dependabot updates and per-job least-privilege `permissions`.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q8-why-pin-github-actions-by-sha-l2-flipkart-accenture)


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "What is the testing honeycomb and why was it proposed?"
    <span class="badge badge-l2">L2</span> Swiggy · Zomato

    Spotify's shape for microservices: few "implementation detail" tests, mostly "integration" tests of each service in isolation through its API, and few "integrated" tests that depend on other services running. Because a microservice is small and most of its complexity is in how it talks to others, so that's where the bugs are.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q2-what-is-the-testing-honeycomb-and-why-was-it-proposed-l2-swiggy-zomato)

??? question "Unit, integration, contract, component: what's the difference?"
    <span class="badge badge-l2">L2</span> Wipro

    Unit: a class in memory with fakes. Integration: code against a real dependency (a database). Contract: a client against the other service's recorded API. Component: one whole service through its API, with its own database real and other services stubbed.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q3-unit-integration-contract-component-whats-the-difference-l2-wipro)

??? question "Why not test the repository on H2?"
    <span class="badge badge-l2">L2</span> Razorpay

    Because it isn't the database production runs. Measured: H2 2.5 in PostgreSQL mode couldn't parse the service's `ON CONFLICT (key) DO NOTHING`, failed 3 of 4 tests on correct code, and caught none of 20 bugs. PostgreSQL in a container caught 6 (chapter 62 makes it fast).

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q6-why-not-test-the-repository-on-h2-l2-razorpay)

??? question "How do you keep a growing test suite fast?"
    <span class="badge badge-l2">L2</span> PhonePe

    Tag suites and run them in stages; keep unit tests in memory; share expensive fixtures such as one database container per run (chapter 62); run in parallel; delete tests that never catch anything and duplicate others.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q9-how-do-you-keep-a-growing-test-suite-fast-l2-phonepe)


**[62. Unit, Slice & Testcontainers Tests](../testing/ch62-unit-slice-integration-tests.md)**

??? question "What's the difference between a unit test and a slice test?"
    <span class="badge badge-l2">L2</span> TCS · Cognizant

    A unit test constructs classes directly, in memory, with no framework. A slice test starts part of a Spring application: `@WebMvcTest` the web layer with MockMvc, `@JdbcTest`/`@DataJpaTest` the data layer. Slices test how your code works with the framework; unit tests test your logic.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q1-whats-the-difference-between-a-unit-test-and-a-slice-test-l2-tcs-cognizant)

??? question "What does `@DataJpaTest` do by default?"
    <span class="badge badge-l2">L2</span> Infosys

    Loads JPA repositories and their configuration only, runs each test in a transaction that's rolled back, and replaces the DataSource with an embedded database unless it's already a test database. In Spring Boot 4.1 the default is `Replace.NON_TEST`, and a `@ServiceConnection` container counts as a test database, so it's kept.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q2-what-does-datajpatest-do-by-default-l2-infosys)

??? question "Why Testcontainers instead of H2?"
    <span class="badge badge-l2">L2</span> Razorpay · Swiggy

    Because H2 isn't the database you run. Measured: H2 2.5 in PostgreSQL mode failed 8 of 19 PostgreSQL features, including `ON CONFLICT (…)`, upserts, `jsonb`, arrays, partial indexes and `RETURNING`, and it hid most of a race that PostgreSQL showed.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q3-why-testcontainers-instead-of-h2-l2-razorpay-swiggy)

??? question "When is mocking the database acceptable?"
    <span class="badge badge-l2">L2</span> Wipro

    In unit tests of logic that uses a repository: a fake or mock keeps them fast and focused. Never as the test of the repository itself, whose SQL only the real database can check.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q11-when-is-mocking-the-database-acceptable-l2-wipro)


**[63. Contract Testing](../testing/ch63-contract-testing.md)**

??? question "What is a consumer-driven contract?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    A test artefact written by a consumer, listing the requests it makes and the parts of each response it relies on. The provider verifies every consumer's contract in its build, so its obligations are exactly what its consumers use, not everything it could return.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q1-what-is-a-consumer-driven-contract-l2-tcs-infosys)

??? question "How does Pact work end to end?"
    <span class="badge badge-l2">L2</span> Thoughtworks

    The consumer's test runs its real client against Pact's mock server, which records the interactions into a pact file. The file is published to a broker. The provider's build fetches it, replays each request against the real provider (after setting up the named provider state), and checks the responses with the pact's matchers. The result goes back to the broker, which answers can-i-deploy.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q2-how-does-pact-work-end-to-end-l2-thoughtworks)

??? question "Pact or Spring Cloud Contract?"
    <span class="badge badge-l2">L2</span> Wipro

    Pact is consumer-driven and polyglot, with a broker and can-i-deploy. Spring Cloud Contract keeps contracts with the provider (Groovy, YAML, Java, Kotlin), generates provider tests and WireMock stubs, and suits teams that are all on the JVM and agree contracts together. Both verify the same thing.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q3-pact-or-spring-cloud-contract-l2-wipro)

??? question "What are provider states?"
    <span class="badge badge-l2">L2</span> Cognizant

    Named preconditions in a contract ("kulfi is sold out") that the provider's verification sets up before replaying the interaction, usually by seeding test data. They're also the limit of what contracts check: in the experiment, a new enum value and pagination appeared only in data no provider state covered, and no contract caught them.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q6-what-are-provider-states-l2-cognizant)


**[64. Component & End-to-End Testing](../testing/ch64-component-e2e-testing.md)**

??? question "What's a component test, and how is it different from an E2E test?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    A component test runs one service for real, with its database in a container and its collaborators stubbed, and tests it through its API. An E2E test deploys every service and tests a user journey across them. Measured: five journeys took 1.9–2.3 s as component tests and 19–28 s as E2E.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q1-whats-a-component-test-and-how-is-it-different-from-an-e2e-test-l2-tcs-infosys)

??? question "Why are E2E tests flaky?"
    <span class="badge badge-l2">L2</span> Swiggy

    Shared environments, shared data and timing. Measured: in a shared staging environment, 1–5 of 12 suite runs passed because of other pipelines' data and stock; fixed sleeps failed 1–28% of asynchronous checks.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q2-why-are-e2e-tests-flaky-l2-swiggy)

??? question "How do you test something asynchronous?"
    <span class="badge badge-l2">L2</span> Wipro

    Poll until it's true or a timeout passes (Awaitility: every 100 ms, up to 10 s by default), never a fixed sleep. Measured: polling failed none of 400 checks and averaged 580–678 ms; a 1 s sleep failed 9.5–12.5% and took longer.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q3-how-do-you-test-something-asynchronous-l2-wipro)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "Which percentiles should a load test report?"
    <span class="badge badge-l2">L2</span> TCS

    p50, p99, p99.9 and max, plus the load model and the throughput achieved. Averages and p95 hide stalls; a max far above the p99 in a closed-model test is a sign of coordinated omission.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q4-which-percentiles-should-a-load-test-report-l2-tcs)
