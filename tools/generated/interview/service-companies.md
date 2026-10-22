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

??? question "Hexagonal or vertical slice architecture?"
    <span class="badge badge-l3">L3</span> startups · consulting firms

    They answer different questions. Vertical slices organise code by feature, minimising coupling between features; hexagonal organises the boundary between logic and technology. Many teams slice by feature and use ports where a slice touches infrastructure.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q12-hexagonal-or-vertical-slice-architecture-l3-startups-consulting-firms)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "What does an API gateway do?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    It's the single entry point for clients: routes requests to services, and offloads cross-cutting concerns such as TLS, authentication, rate limiting, request ids, CORS, size limits and observability. It lets services change behind a stable public API.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q1-what-does-an-api-gateway-do-l2-tcs-infosys-cognizant)

??? question "How do you version APIs at the edge?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Public paths are stable; versions are carried in a header or path segment and routed by the gateway or handled by the service's own API versioning (chapter 8). BFF endpoints are often versioned by path to match app releases, because old app versions live for months.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q10-how-do-you-version-apis-at-the-edge-l2l3-sap-cognizant)


**[11. Service Discovery Patterns](../architecture-patterns/ch11-service-discovery-patterns.md)**

??? question "What is service discovery and why do microservices need it?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    Instances come and go (scaling, deployments, failures), so callers can't use fixed addresses. Discovery keeps a current list of healthy instances per service name, through a registry, DNS or the platform, and the caller or a proxy picks one per request or connection.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q1-what-is-service-discovery-and-why-do-microservices-need-it-l2-tcs-infosys-cognizant)

??? question "Self-registration vs third-party registration?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Self-registration: the instance registers itself and heartbeats (Eureka). Third-party: an external component registers instances based on observed state (Kubernetes from pod readiness, Consul agents). Third-party registration removes discovery code from services and reflects real readiness.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q3-self-registration-vs-third-party-registration-l2-sap-cognizant)

??? question "Liveness vs readiness probes?"
    <span class="badge badge-l2">L2</span> Cognizant · Infosys · Amazon

    Liveness answers "should this container be restarted?" and must only check the process itself. Readiness answers "should it receive traffic now?" and controls Service membership. Checking dependencies in liveness causes restart storms; checking every dependency in readiness removes whole services during partial outages.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q9-liveness-vs-readiness-probes-l2-cognizant-infosys-amazon)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

??? question "What is the database-per-service pattern?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    Each service owns its data, and only that service reads and writes it; others go through its API or events. It can be implemented as private tables, a schema per service, or separate database instances. It's what makes services independently changeable and deployable.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q1-what-is-the-database-per-service-pattern-l2-tcs-infosys-cognizant)

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


**[18. Resilience Patterns](../architecture-patterns/ch18-resilience-patterns.md)**

??? question "What is a cascading failure and how do you prevent it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Cognizant

    A failure (often slowness) in one dependency exhausting resources in its callers, which then fail their callers. Prevent it with timeouts on every call, bulkheads per dependency, bounded queues and load shedding, circuit breakers, and fallbacks.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q1-what-is-a-cascading-failure-and-how-do-you-prevent-it-l2l3-amazon-flipkart-cognizant)

??? question "Explain the circuit breaker states."
    <span class="badge badge-l2">L2</span> SAP · Cognizant · Flipkart

    Closed: calls pass and outcomes are recorded; when the failure (or slow-call) rate over a window exceeds a threshold, it opens. Open: calls fail immediately for a wait duration. Half-open: a limited number of trial calls; if they succeed it closes, otherwise it reopens.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q2-explain-the-circuit-breaker-states-l2-sap-cognizant-flipkart)

??? question "Hystrix is in maintenance. What do you use in Spring today?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    Spring Framework 7's `@Retryable` and `@ConcurrencyLimit` for retries and concurrency limits; Resilience4j (directly or via Spring Cloud CircuitBreaker) for breakers, rate limiters and time limiters; client-level timeouts on HTTP service clients.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q9-hystrix-is-in-maintenance-what-do-you-use-in-spring-today-l2l3-cognizant-sap)


**[19. Event-Driven Architecture](../architecture-patterns/ch19-event-driven-architecture.md)**

??? question "What does 'event-driven' mean?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    Components communicate by publishing facts that happened, without knowing who reacts. It covers different styles: event notification (thin events, consumers call back), event-carried state transfer (events carry state; consumers keep copies) and event sourcing (the event log is the system of record).

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q1-what-does-event-driven-mean-l2-cognizant-sap-flipkart)


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "What is a distributed monolith?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP · Flipkart

    A system split into services that still can't be developed, deployed or fail independently, because of shared databases, shared domain libraries, synchronous chains or cycles. It has the costs of distribution without the benefits. The clearest evidence is services that are always released together.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q1-what-is-a-distributed-monolith-l2l3-cognizant-sap-flipkart)


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


**[22. HTTP Clients](../communication/ch22-http-clients.md)**

??? question "RestTemplate, RestClient, WebClient: which and why?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    RestClient (synchronous, fluent) for most cases; WebClient for reactive applications and streaming. RestTemplate's deprecation was announced in Spring Framework 7.0, with formal deprecation planned for 7.1 and removal in 8.0; migrate to RestClient.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q1-resttemplate-restclient-webclient-which-and-why-l1l2-cognizant-sap-flipkart)

??? question "Why not create a client per request?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Amazon · Cognizant

    You lose connection reuse: every call pays TCP and TLS handshakes, and you churn threads, sockets and memory. Measured here: 15.8 ms vs 2.1 ms at p50 on loopback over TLS. Build clients once.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q3-why-not-create-a-client-per-request-l1l2-amazon-cognizant)

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

??? question "How would a browser call a gRPC service?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Browsers can't use raw gRPC (no control of HTTP/2 framing and trailers), so use gRPC-Web through a proxy (such as Envoy), the Connect protocol, or JSON transcoding at the gateway; or keep a REST/BFF layer at the edge, which is often simplest.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q9-how-would-a-browser-call-a-grpc-service-l2l3-sap-cognizant)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "What is GraphQL, and what problem does it solve?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A query language and runtime for APIs: the server publishes a typed schema, and clients request exactly the fields they need, across related types, in one request. It addresses over-fetching (whole resources for a small screen), under-fetching (many round trips to compose a screen) and endpoint sprawl for many client screens.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q1-what-is-graphql-and-what-problem-does-it-solve-l1l2-cognizant-sap-flipkart)

??? question "How is GraphQL different from REST?"
    <span class="badge badge-l2">L2</span> Amazon · Cognizant

    REST exposes resources at URLs with HTTP semantics (methods, status codes, caching); the server decides each response's shape. GraphQL exposes one endpoint and a schema; the client decides the shape. GraphQL gains flexibility and fewer round trips; REST keeps HTTP caching, simple per-endpoint cost and familiar tooling.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q2-how-is-graphql-different-from-rest-l2-amazon-cognizant)

??? question "How do `@SchemaMapping` and `@BatchMapping` differ in Spring for GraphQL?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    `@SchemaMapping` resolves a field for one parent at a time and can take field arguments. `@BatchMapping` receives all parents at once and returns a map (or ordered list) of values, registering a DataLoader underneath; it can't take field arguments, for which you use `BatchLoaderRegistry`.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q4-how-do-schemamapping-and-batchmapping-differ-in-spring-for-graphql-l2-sap-cognizant)


**[25. Asynchronous Messaging](../communication/ch25-asynchronous-messaging.md)**

??? question "What is the difference between a queue and a topic?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A queue delivers each message to one of its consumers (competing consumers, work distribution). A topic delivers each message to every subscriber; in Kafka, to every consumer group, with partitions shared out among the group's consumers.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q1-what-is-the-difference-between-a-queue-and-a-topic-l1l2-cognizant-sap-flipkart)

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

??? question "Why is polling a problem at scale?"
    <span class="badge badge-l2">L2</span> Amazon · Cognizant

    Each poll is a full request (headers, auth, TLS, routing), most answer "nothing new", data is stale by up to the interval, and intermediate states are missed. Here 1 s polling saw 10 of 40 positions at ~1,190 bytes each, 564 ms stale on average.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q3-why-is-polling-a-problem-at-scale-l2-amazon-cognizant)

??? question "How does Spring support SSE and WebSockets?"
    <span class="badge badge-l2">L2</span> SAP · Cognizant

    Spring MVC's `SseEmitter` (or WebFlux's `Flux<ServerSentEvent>`) for SSE; Spring WebSocket's `WebSocketHandler`, handshake interceptors and `ConcurrentWebSocketSessionDecorator` for raw WebSockets; and STOMP messaging over WebSocket with `@MessageMapping`, a simple in-memory broker or a broker relay to RabbitMQ/ActiveMQ for multi-instance fan-out.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q12-how-does-spring-support-sse-and-websockets-l2-sap-cognizant)


**[27. API Contracts & Schema Evolution](../communication/ch27-api-contracts.md)**

??? question "What is an API contract?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Everything consumers rely on: the shape of requests and responses, behaviour (status codes, errors, defaults, ordering), the meaning of fields, and non-functional expectations. Schema documents (OpenAPI, AsyncAPI, protobuf) capture the shape and part of the behaviour; meaning needs documentation and review.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q1-what-is-an-api-contract-l1l2-cognizant-sap)

??? question "How do you version a REST API?"
    <span class="badge badge-l2">L2</span> Cognizant · Amazon

    Avoid versions for additive changes; for real breaks, a new major version via URL (`/v2`), media type or header, run in parallel with the old one, with deprecation and `Sunset` headers, usage tracking and a retirement date (chapter 21 covers the mechanics).

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q7-how-do-you-version-a-rest-api-l2-cognizant-amazon)


**[28. Idempotency, Deduplication & Correlation](../communication/ch28-idempotency-correlation.md)**

??? question "What does idempotent mean, and which HTTP methods are idempotent?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Repeating the operation has the same effect as doing it once. `GET`, `HEAD`, `PUT`, `DELETE` and `OPTIONS` are idempotent by definition; `POST` and `PATCH` are not, so they need an idempotency key (or a design that makes them idempotent) to be retried safely.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q1-what-does-idempotent-mean-and-which-http-methods-are-idempotent-l1l2-cognizant-sap)

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


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "What is two-phase commit?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · SAP

    A protocol for atomic commit across several resources. Phase 1: the coordinator asks each participant to prepare (persist the work and promise to commit); phase 2: if all agreed, it logs COMMIT and tells everyone to commit, otherwise to abort. Between the phases the participants are in doubt and hold their locks.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q1-what-is-two-phase-commit-l2l3-cognizant-sap)


**[31. Data Consistency](../data/ch31-data-consistency.md)**

??? question "What does eventual consistency guarantee?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Only that replicas converge if writes stop. It says nothing about what a reader sees meanwhile: stale data, data going backwards, not seeing your own write. Real systems add session guarantees on top.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q1-what-does-eventual-consistency-guarantee-l2-cognizant-sap)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "Cache-aside, read-through, write-through, write-behind: what's the difference?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    Cache-aside: the app reads the cache and loads from the database on a miss. Read-through: the cache does the loading. Write-through: writes update the database and the cache together. Write-behind: writes go to the cache and are flushed to the database later. They trade staleness, write latency and durability differently.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q1-cache-aside-read-through-write-through-write-behind-whats-the-difference-l2-cognizant-sap)

??? question "What does @Cacheable(sync = true) do, and what doesn't it do?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Concurrent misses for the same key on one instance wait for a single load. It does nothing across instances (N instances, N loads: measured 4 at once with 4 instances) and it doesn't serve stale values; combine with stale-while-revalidate for expensive keys.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q5-what-does-cacheablesync-true-do-and-what-doesnt-it-do-l2l3-sap-cognizant)


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

??? question "Materialized view or summary table?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    A materialized view is simple but recomputes everything on refresh; a plain refresh blocks readers (measured: all readers waited 3.0–3.4 s), `CONCURRENTLY` doesn't but took 7× longer. An incremental summary table is always current and never blocks, at the cost of per-change logic. Views for slow-moving summaries, incremental tables for live dashboards.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q6-materialized-view-or-summary-table-l3-sap-cognizant)


### Deployment & Platform


**[53. Containerization](../deployment/ch53-containerization.md)**

??? question "Why multi-stage Docker builds?"
    <span class="badge badge-l1">L1</span> TCS · Infosys

    The build needs a JDK, Maven and sources; the runtime needs none of them. A multi-stage build compiles in one stage and copies only the output into a small runtime image, so there are fewer bytes, fewer vulnerabilities and no secrets from the build.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q1-why-multi-stage-docker-builds-l1-tcs-infosys)

??? question "Why does docker stop take 10 seconds?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Wipro · Accenture

    PID 1 isn't the JVM. A shell-form entrypoint or wrapper script receives SIGTERM and doesn't forward it, so after the timeout comes SIGKILL. Measured: 10.2 s and exit 137 against 157–171 ms with exec form or `exec java`.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q8-why-does-docker-stop-take-10-seconds-l1l2-wipro-accenture)


**[54. Kubernetes for Spring Developers](../deployment/ch54-kubernetes-for-spring.md)**

??? question "What's the difference between CPU requests and limits?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    A request is what the scheduler reserves and the share you get under contention. A limit is a CFS quota per 100 ms period; past it, the container is throttled until the next period. Measured: a 250m limit gave 3.9–4.0 s JVM starts and a 93–97 ms p99 for 5 ms of work, with almost no CPU used on average.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q1-whats-the-difference-between-cpu-requests-and-limits-l2-tcs-infosys)

??? question "What is the Downward API for?"
    <span class="badge badge-l2">L2</span> Accenture

    Giving the pod its own metadata (name, namespace, node, labels) and resources (requests and limits) as env vars or files. Use them for log and metric fields and to size thread pools. An unset limit reads as the node's allocatable.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q7-what-is-the-downward-api-for-l2-accenture)

??? question "Deployment, StatefulSet, DaemonSet, Job: when?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Wipro · Cognizant

    

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q8-deployment-statefulset-daemonset-job-when-l1l2-wipro-cognizant)


**[55. Helm, Kustomize & Environment Promotion](../deployment/ch55-helm-kustomize.md)**

??? question "Helm or kustomize?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Helm when you need packaging, defaults and logic, a release history and hooks, or are consuming third-party software. kustomize when plain YAML with small per-environment patches is enough, and no templating language is wanted. Many teams use both: a shared chart for their services, kustomize overlays over upstream manifests. Either way, render and review the output.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q1-helm-or-kustomize-l2-tcs-infosys)

??? question "What does `--rollback-on-failure` do?"
    <span class="badge badge-l2">L2</span> Wipro · Cognizant

    It waits for the release's resources to be ready and, if they aren't by the timeout, rolls back to the last successful revision. It replaced `--atomic` in Helm 4. Measured: a crashing release was rolled back after the full 60 s timeout, with history showing the failed revision. The rolling update had already kept the old pods serving.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q6-what-does-rollback-on-failure-do-l2-wipro-cognizant)

??? question "How do you keep a Helm chart reviewable?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Keep logic in helpers and out of templates, fail loudly on invalid values (`fail`, a values schema), and review the rendered output, not the template diff: CI posts `helm template` diffs for every environment on each PR. Version the chart like a library.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q8-how-do-you-keep-a-helm-chart-reviewable-l3-thoughtworks)

??? question "What does kustomize's hash suffix on ConfigMaps buy you?"
    <span class="badge badge-l2">L2</span> Accenture

    A new name for every content change, and every reference rewritten to match. The pod template changes, so the change rolls out like a release, and the old ConfigMap stays for a rollback. The cost is accumulation, so prune old ones.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q10-what-does-kustomizes-hash-suffix-on-configmaps-buy-you-l2-accenture)


**[56. Autoscaling](../deployment/ch56-autoscaling.md)**

??? question "How does the HPA decide how many pods to run?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Every 15 s it reads pod CPU from metrics-server, divides by the pods' requests, and computes desired = ceil(utilization ÷ target × pods). Ratios within 10% of 1 change nothing. Then a stabilization window and rate policies smooth the result, within min and max.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q1-how-does-the-hpa-decide-how-many-pods-to-run-l2-tcs-infosys)

??? question "What is utilization relative to?"
    <span class="badge badge-l2">L2</span> Wipro

    The pods' requests. 50% means each pod uses half its request. Doubling the request with the same target halves the pod count for the same load.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q5-what-is-utilization-relative-to-l2-wipro)

??? question "Should scale-up be as fast as possible?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Only with somewhere for the pods to go. Measured on one busy node, starting four JVMs at once starved the pod already serving and 150–181 requests failed. Fast scale-ups need node headroom, and a minimum that doesn't make every peak start from one pod.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q9-should-scale-up-be-as-fast-as-possible-l3-thoughtworks)

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

??? question "Mesh or client library?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Client libraries are lighter and see application context; a mesh is uniform across languages and adds identity and mTLS. Many shops do both: timeouts and circuit breakers in the client, balancing, retries and mTLS in the mesh.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q10-mesh-or-client-library-l3-thoughtworks)


**[58. CI/CD, GitOps & Progressive Delivery](../deployment/ch58-cicd-gitops.md)**

??? question "What is GitOps?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Desired state for each environment in Git, declaratively, pulled and continuously reconciled by an agent in the cluster. Deploys are commits, rollbacks are reverts, and drift is corrected. Measured: commits applied within one 10 s interval, and a manual scale reverted within one.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q1-what-is-gitops-l2-tcs-infosys)

??? question "What does 'build once' mean, and why?"
    <span class="badge badge-l2">L2</span> Wipro

    One image per commit, referred to by digest in every environment, with its SBOM and signature. Rebuilding per environment means production runs bits nobody tested (chapter 55).

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q7-what-does-build-once-mean-and-why-l2-wipro)

??? question "How do you roll back with GitOps?"
    <span class="badge badge-l2">L2</span> Accenture

    Revert the commit in the environment repository; the controller applies it. For a canary in progress, the rollout controller sets the canary weight to 0 immediately, faster than any commit.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q8-how-do-you-roll-back-with-gitops-l2-accenture)

??? question "How do keep-alive connections affect releases?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    They stay on the pods they were opened to. Measured: after a blue/green switch, every keep-alive request kept going to the old slot. Drain old pods (graceful shutdown closes connections), cap connection lifetimes, or switch at a per-request proxy.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q11-how-do-keep-alive-connections-affect-releases-l3-thoughtworks)


**[59. Cloud-Native on AWS, Azure & GCP](../deployment/ch59-cloud-native-platforms.md)**

??? question "How should a pod on EKS, AKS or GKE call cloud APIs?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    With workload identity: the pod's projected service account token, with the cloud as audience, is exchanged for short credentials of a role that trusts that one service account. No keys. Measured: the exchange refused the wrong audience, the wrong account and an edited token.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q1-how-should-a-pod-on-eks-aks-or-gke-call-cloud-apis-l2-tcs-infosys)

??? question "How do you make Java functions start fast?"
    <span class="badge badge-l3">L3</span> Accenture

    SnapStart (a CRaC-style snapshot) on Lambda, GraalVM native images, an AOT cache, smaller dependency graphs, and provisioned concurrency for latency-critical paths. Chapter 60 measures native images and AOT caches.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q8-how-do-you-make-java-functions-start-fast-l3-accenture)


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

??? question "What is the difference between a monolith and microservices?"
    <span class="badge badge-l1">L1</span> Cognizant · TCS · Infosys

    **Short answer (30 sec):** A monolith is one deployable containing all capabilities, usually with one database. Microservices split capabilities into independently deployable services, each owning its data and talking over the network. The trade is simplicity and strong consistency for independent deployment, scaling and team autonomy.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q1-what-is-the-difference-between-a-monolith-and-microservices-l1-cognizant-tcs-infosys)

??? question "What is a modular monolith and why is it popular again?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · Accenture · SAP

    One deployable split into business modules with enforced public APIs and hidden internals, with each module owning its data. You get most of the design benefits of microservices without the network costs, and modules can be extracted later.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q2-what-is-a-modular-monolith-and-why-is-it-popular-again-l1l2-cognizant-accenture-sap)

??? question "You're asked to migrate a 10-year-old banking monolith to microservices. What's your plan?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · SAP · Cognizant

    Don't rewrite. Modularise in place, establish the platform, then strangle capabilities one at a time, starting at the edges, keeping the core ledger coarse-grained, and measuring outcomes at every step.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q10-youre-asked-to-migrate-a-10-year-old-banking-monolith-to-microservices-whats-your-plan-l4-jp-morgan-goldman-sachs-sap-cognizant)


**[2. Characteristics & Principles](../foundations/ch02-characteristics-principles.md)**

??? question "What are the key characteristics of microservices?"
    <span class="badge badge-l1">L1</span> Cognizant · TCS · Infosys

    Independently deployable services organised around business capabilities. Each owns its data and is run by the team that built it. They communicate through smart endpoints and dumb pipes, are designed for failure, and depend on heavy automation.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q1-what-are-the-key-characteristics-of-microservices-l1-cognizant-tcs-infosys)

??? question "What does single responsibility mean for a service?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Accenture · Flipkart

    One business capability and one reason to change, owned by one team. It is not a line-count limit and not one entity per service.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q2-what-does-single-responsibility-mean-for-a-service-l1l2-accenture-flipkart)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Name the fallacies of distributed computing. Which hurt microservices most?"
    <span class="badge badge-l2">L2</span> Cognizant · TCS · Amazon

    The network is reliable; latency is zero; bandwidth is infinite; the network is secure; topology doesn't change; there is one administrator; transport cost is zero; the network is homogeneous. The first two hurt most: unreliability causes lost or duplicated work, and latency kills chatty designs.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q1-name-the-fallacies-of-distributed-computing-which-hurt-microservices-most-l2-cognizant-tcs-amazon)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "What is the Twelve-Factor App? Name the factors."
    <span class="badge badge-l1">L1</span> Cognizant · TCS · Infosys · Accenture

    A methodology from Heroku (2011) for building apps that any platform can deploy, scale and replace without special handling. The factors: codebase, dependencies, config, backing services, build-release-run, processes, port binding, concurrency, disposability, dev/prod parity, logs, admin processes.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q1-what-is-the-twelve-factor-app-name-the-factors-l1-cognizant-tcs-infosys-accenture)

??? question "How does Spring Boot resolve configuration from many sources?"
    <span class="badge badge-l2">L2</span> SAP · service companies

    A precedence order in which command-line arguments override system properties, which override environment variables, which override config files (profile-specific over default). Environment variables bind through relaxed binding (`FOODFLEET_ZONES_CITY` → `foodfleet.zones.city`). `spring.config.import` adds config trees, Config Server or Vault.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q3-how-does-spring-boot-resolve-configuration-from-many-sources-l2-sap-service-companies)

??? question "What is dev/prod parity and how do you achieve it with Spring Boot?"
    <span class="badge badge-l2">L2</span> Flipkart · Cognizant

    Minimise the time, personnel and tooling gaps between development and production. With Spring Boot: Docker Compose support for local backing services, Testcontainers with `@ServiceConnection` for tests (same major versions as production), the same image in every environment, and continuous deployment so changes reach production quickly.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q7-what-is-devprod-parity-and-how-do-you-achieve-it-with-spring-boot-l2-flipkart-cognizant)


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

??? question "How do you find bounded contexts in a new domain?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · consulting firms

    Talk to domain experts. Run Event Storming: domain events on a timeline, then commands, actors, policies and aggregates. Look for language changes ("order" becomes "ticket" becomes "delivery job"), different rules and lifecycles, and team and process boundaries. Validate with the context map and the expected rate of change. [Chapter 6](../foundations/ch06-service-decomposition.md) walks through it.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q9-how-do-you-find-bounded-contexts-in-a-new-domain-l3l4-flipkart-consulting-firms)


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

??? question "How big should a microservice be?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · TCS · Infosys

    Not a line count. Owned by one team, changed for one reason, deployable on its own, owning its data, and understandable by a new team member in a couple of weeks. Too small shows up as chatty call chains and shotgun changes; too large shows up as several teams negotiating releases.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q4-how-big-should-a-microservice-be-l2l3-amazon-tcs-infosys)

??? question "What are the signs that a decomposition is wrong?"
    <span class="badge badge-l3">L3</span> Flipkart · Cognizant · Amazon

    Shotgun changes across services, lock-step deploys, chatty sync chains, proposals for distributed transactions, shared tables, generic "common" or "manager" services, cyclic calls, and services whose releases need several teams' sign-off.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q10-what-are-the-signs-that-a-decomposition-is-wrong-l3-flipkart-cognizant-amazon)


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "What is Conway's Law?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · TCS · SAP

    Systems end up mirroring the communication structure of the organisation that builds them (Melvin Conway, 1968). Teams that talk easily produce tightly integrated parts; teams that rarely talk produce formal interfaces between their parts.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q1-what-is-conways-law-l2l3-cognizant-tcs-sap)

??? question "Strong vs weak vs collective code ownership?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> SAP · Cognizant

    Strong: only the owner changes the code. Weak: anyone may change it, the owner reviews. Collective: no owner. For production services, one owning team per service (strong or weak with mandatory owner review); collective ownership only inside a single team.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q7-strong-vs-weak-vs-collective-code-ownership-l2l3-sap-cognizant)

??? question "Is the Spotify model a good template?"
    <span class="badge badge-l3">L3</span> startups · consulting firms

    It's a snapshot of how one company described its teams around 2012, and people who worked there have said it wasn't fully implemented even then. Copying the labels (squads, tribes, chapters, guilds) doesn't reproduce the outcome. Start from your own streams of change, cognitive load and interactions; Team Topologies gives vocabulary for that, not a template.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q12-is-the-spotify-model-a-good-template-l3-startups-consulting-firms)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "What are the biggest changes in Spring Boot 4 compared with Boot 3?"
    <span class="badge badge-l2">L2</span> Cognizant · TCS · Infosys

    Spring Framework 7 and Jakarta EE 11; Boot split into **modular starters**; **Jackson 3**; **JSpecify** null-safety annotations; built-in **API versioning**; the **HTTP service registry** for declarative clients; **`@Retryable`/`@ConcurrencyLimit`** in the framework; `@MockitoBean`, `MockMvcTester`, `RestTestClient` for testing. Java 17 is still the minimum.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q1-what-are-the-biggest-changes-in-spring-boot-4-compared-with-boot-3-l2-cognizant-tcs-infosys)

??? question "How do you call another microservice in Spring Boot 4?"
    <span class="badge badge-l2">L2</span> Cognizant · Accenture · Flipkart

    Declare an interface with `@HttpExchange`/`@GetExchange`, register it with `@ImportHttpServices(group = "kitchen", types = …)`, and configure `spring.http.serviceclient.kitchen.base-url`, timeouts and API version in properties. Inject the interface. Add `spring-boot-starter-restclient`. Use one group per downstream service so timeouts differ.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q6-how-do-you-call-another-microservice-in-spring-boot-4-l2-cognizant-accenture-flipkart)

??? question "What changed in Spring Boot testing?"
    <span class="badge badge-l2">L2</span> TCS · Infosys · Cognizant

    `@MockBean`/`@SpyBean` are gone; use `@MockitoBean`/`@MockitoSpyBean`. `@SpringBootTest` doesn't configure MockMvc or `TestRestTemplate` by itself; add `@AutoConfigureMockMvc` or `@AutoConfigureTestRestTemplate`. Test starters are per technology (`spring-boot-starter-webmvc-test`). `MockMvcTester` gives AssertJ-style assertions, and `RestTestClient` offers a RestClient-style API bound to MockMvc, an application context or a running server.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q11-what-changed-in-spring-boot-testing-l2-tcs-infosys-cognizant)


### Governance & Organization


**[71. API Governance & Developer Portals](../governance-org/ch71-api-governance.md)**

??? question "What is API governance?"
    <span class="badge badge-l3">L3</span> TCS · Infosys

    The rules for designing, changing and retiring APIs, and how they're enforced. Effective governance is mostly automated: a style guide as linting rules in CI, a versioning and deprecation policy, a catalog of APIs and owners, and human review only where rules can't judge.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q1-what-is-api-governance-l3-tcs-infosys)

??? question "How do you introduce lint rules to existing APIs?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Record each API's current findings as a baseline and fail only on new ones; shrink the baseline over time. Measured: order-service's 7 findings were baselined; a change was judged on its 3 new ones and credited with 1 fix.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q3-how-do-you-introduce-lint-rules-to-existing-apis-l3-thoughtworks)


**[72. Platform Engineering & Golden Paths](../governance-org/ch72-platform-engineering.md)**

??? question "What is platform engineering?"
    <span class="badge badge-l3">L3</span> Thoughtworks · Infosys

    Building and running an internal developer platform (self-service tools, templates, infrastructure and documentation) as a product for the organisation's developers, so stream-aligned teams can deliver without tickets or deep infrastructure knowledge.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q1-what-is-platform-engineering-l3-thoughtworks-infosys)

??? question "What should the platform team not own?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    The services. The platform owns the paths (templates, tools, infrastructure, checks) and their updates; stream-aligned teams own their services, including decisions to differ. A platform team that ends up running services becomes a bottleneck.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q11-what-should-the-platform-team-not-own-l3-thoughtworks)


**[73. ADRs, Fitness Functions & Tech Radar](../governance-org/ch73-adrs-fitness-functions.md)**

??? question "What is an ADR, and what goes in one?"
    <span class="badge badge-l2">L2</span> Thoughtworks · Infosys

    A short, numbered record of one architectural decision, kept with the code: context, decision, status and consequences (Nygard's format), optionally with considered options and their pros and cons (MADR). It records why, so later engineers don't relitigate or unknowingly undo it.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q1-what-is-an-adr-and-what-goes-in-one-l2-thoughtworks-infosys)

??? question "How do you change a decision recorded in an ADR?"
    <span class="badge badge-l2">L2</span> Thoughtworks

    Write a new ADR that supersedes or amends it, and update the old one's status with a link (adr-tools' `adr new -s` and `adr link` do both). Never edit an accepted record. Measured on this handbook's log: an edited record cited a decision made 14 months later.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q2-how-do-you-change-a-decision-recorded-in-an-adr-l2-thoughtworks)

??? question "What is a fitness function?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    An automated, objective check that an architectural characteristic still holds: dependencies, compatibility, latency, startup, security settings. Atomic or holistic; static (reads code) or dynamic (measures the running system); triggered on change or continual.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q4-what-is-a-fitness-function-l3-thoughtworks)

??? question "What is a technology radar, and how do you enforce it?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    A periodic view of technologies in Adopt, Trial, Assess and Hold rings (Build Your Own Radar now calls Hold "Caution"), with a reason for each. Publish it as data and map Hold blips to packages or artifacts that a build check rejects; here it caught `SimpleDateFormat` in one release.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q9-what-is-a-technology-radar-and-how-do-you-enforce-it-l3-thoughtworks)


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "How do you plan a monolith-to-microservices migration?"
    <span class="badge badge-l4">L4</span> Thoughtworks · Infosys

    Measure first: contexts mapped to code, import coupling, cycles, history. Make it a modular monolith (no cycles, shared code independent of business code), then extract contexts in order of value per new remote dependency through a strangler facade, re-measuring after each step and stopping when the next step buys little.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q1-how-do-you-plan-a-monolith-to-microservices-migration-l4-thoughtworks-infosys)

??? question "What is a microservices maturity model good for?"
    <span class="badge badge-l4">L4</span> Accenture

    Sequencing capabilities: modular monolith, first extractions, independent services, evolving platform. It's useful only with measured exit gates (cycles, platform edges, coordinated changes); self-assessed questionnaires tell you little.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q9-what-is-a-microservices-maturity-model-good-for-l4-accenture)


**[75. Multi-Tenancy, Multi-Region & Compliance](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)**

??? question "What are the main multi-tenancy models?"
    <span class="badge badge-l2">L2</span> Infosys · TCS

    Pool (shared tables with a tenant column), bridge (a schema per tenant), silo (a database or whole stack per tenant). Measured at 100 tenants: provisioning 0.3 ms, 7 ms and 52–87 ms per tenant; 36 MB, 43 MB and 811 MB; 1, 1 and 100 connections.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q1-what-are-the-main-multi-tenancy-models-l2-infosys-tcs)

??? question "How do you test tenant isolation?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Fitness functions (every tenant table has a forced policy; no service connects as owner), tests that run queries without a tenant and expect nothing, and periodic cross-tenant probes in staging.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q11-how-do-you-test-tenant-isolation-l3-thoughtworks)


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

??? question "What is the difference between monitoring and observability?"
    <span class="badge badge-l1">L1</span> TCS · Infosys

    Monitoring checks for failures you anticipated, with predefined metrics, dashboards and alerts. Observability is being able to explain failures you didn't anticipate from data you already collect, by slicing it by any field. Monitoring tells you *that* and *where*; observability tells you *why*.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q1-what-is-the-difference-between-monitoring-and-observability-l1-tcs-infosys)

??? question "Name the signals and what each is for."
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Wipro · Accenture

    Metrics: aggregated numbers for alerting and trends. Logs: records of what code decided. Traces: one request's path and where its time went. Profiles: which code used CPU or memory. Events: changes (deploys, flags) and wide per-request records for slicing.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q2-name-the-signals-and-what-each-is-for-l1l2-wipro-accenture)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "Counter, gauge, timer, distribution summary: when to use which?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Counter for things that happen (orders placed; take `rate()`), gauge for a current level read when scraped (queue length), timer for durations (count, total, max, histogram), distribution summary for non-time sizes (basket value), long-task timer for work still running.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q1-counter-gauge-timer-distribution-summary-when-to-use-which-l2-tcs-infosys)

??? question "Why did my gauge become NaN?"
    <span class="badge badge-l2">L2</span> Infosys · Accenture

    Micrometer holds a gauge's object weakly so a gauge doesn't keep it alive; if nothing else references it, it is garbage-collected and the gauge reports NaN. Keep a reference, register it in a binder that owns it, or use `strongReference(true)`.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q10-why-did-my-gauge-become-nan-l2-infosys-accenture)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "What is in a W3C traceparent header?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Version, a 16-byte trace ID, the 8-byte span ID of the caller's span, and flags whose low bit says whether the trace is sampled: `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`. `tracestate` carries vendor data alongside.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q1-what-is-in-a-w3c-traceparent-header-l2-tcs-infosys)

??? question "When do you add manual spans?"
    <span class="badge badge-l2">L2</span> Infosys · Accenture

    Around internal work that matters and that instrumentation can't see: a fraud decision, a pricing calculation, a batch step. Name it for the operation, add namespaced attributes with low-to-moderate cardinality, record exceptions and set the status, and keep it short-lived.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q10-when-do-you-add-manual-spans-l2-infosys-accenture)


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "Why structured logs instead of text?"
    <span class="badge badge-l1">L1</span> TCS · Infosys

    Fields can be queried, aggregated and joined (`order.id`, `trace.id`, `log.level`) without fragile parsing, and multi-line stack traces stay in one record. Spring Boot 4 writes ECS, Logstash or GELF JSON with one property. The cost is bytes: 365 bytes against 121 per line here with Logback's defaults, about 290 when trimmed.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q1-why-structured-logs-instead-of-text-l1-tcs-infosys)

??? question "What fields should every log line have?"
    <span class="badge badge-l2">L2</span> Zomato · Accenture

    Timestamp (UTC, ISO-8601), level, logger, message, service name and version, environment, region or zone, and trace and span IDs. Event-specific fields go in as key-value pairs: order ID, amount, attempt. Leave out anything nobody queries.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q8-what-fields-should-every-log-line-have-l2-zomato-accenture)

??? question "Should logs go to files or stdout in Kubernetes?"
    <span class="badge badge-l2">L2</span> Infosys · Wipro

    Stdout, as JSON lines. The node's agent (Fluent Bit, the OpenTelemetry Collector) collects, enriches with pod metadata and ships them. Apps don't manage rotation or disk, and a crashing pod's last lines are still collected.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q11-should-logs-go-to-files-or-stdout-in-kubernetes-l2-infosys-wipro)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "What are an SLI, an SLO and an SLA?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    An SLI is a measured ratio of good to valid events, such as checkout requests without server errors. An SLO is the target for it over a window: 99.9% over 30 days. An SLA is a contract with consequences, set looser than the SLO so you hear about problems before you owe anything.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q1-what-are-an-sli-an-slo-and-an-sla-l2-tcs-infosys)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "What's the difference between liveness and readiness?"
    <span class="badge badge-l1">L1</span> TCS · Infosys

    Liveness failing restarts the container: use it for a stuck or broken process. Readiness failing removes the pod from Service endpoints for new connections, with no restart: use it for "not able to take traffic right now", such as during startup or shutdown.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q1-whats-the-difference-between-liveness-and-readiness-l1-tcs-infosys)

??? question "What is a startup probe for?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Accenture · Wipro

    Giving a slow start its own budget. Liveness and readiness don't run until it succeeds. Measured: a 40-second start under a 10-second liveness delay never started (4–5 restarts, CrashLoopBackOff); with a startup probe it was ready in 43–44 seconds.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q4-what-is-a-startup-probe-for-l1l2-accenture-wipro)

??? question "What's the HTTP contract of a health endpoint?"
    <span class="badge badge-l1">L1</span> Infosys · Cognizant

    UP and UNKNOWN answer 200; DOWN and OUT_OF_SERVICE answer 503. Kubernetes HTTP probes succeed on 200–399. The body has the aggregated status and, when authorized, each component's.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q8-whats-the-http-contract-of-a-health-endpoint-l1-infosys-cognizant)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "Do virtual threads make a service faster?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    No. They make waiting cheap, so more requests can wait at once. Measured: at 250 requests a second, platform threads, virtual threads and an event loop all served the quote at a p50 of 153–155 ms. Virtual threads raised capacity from 1,060–1,100 to about 1,490 a second, because 200 platform threads were the limit.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q1-do-virtual-threads-make-a-service-faster-l2-tcs-infosys)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "How does the JVM know it's in a container?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Since JDK 10 (and 8u191) it reads its cgroup's CPU quota and memory limit (`UseContainerSupport`, on by default) and treats them as the machine. Measured: a quota of 0.5 or 1 CPU showed as 1 processor, 1.5 as 2, and the heap came from the container's memory.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q1-how-does-the-jvm-know-its-in-a-container-l2-tcs-infosys)

??? question "What's the difference between OutOfMemoryError and OOMKilled?"
    <span class="badge badge-l2">L2</span> Wipro

    `OutOfMemoryError` is the JVM failing to allocate on its heap (or metaspace, or direct memory); it can be logged and handled, and `ExitOnOutOfMemoryError` makes it exit. OOMKilled is the kernel killing the container for exceeding its limit (exit 137): no stack trace, and usually caused by native memory beyond the heap.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q4-whats-the-difference-between-outofmemoryerror-and-oomkilled-l2-wipro)


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


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "What is chaos engineering?"
    <span class="badge badge-l2">L2</span> TCS · Infosys

    Running controlled experiments that inject real-world faults (latency, crashes, partitions) into a system to check a hypothesis about its steady state, so weaknesses are found at a time you choose rather than during an incident. It is an experiment, not random breakage: steady state, hypothesis, small blast radius, automatic stop.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q1-what-is-chaos-engineering-l2-tcs-infosys)

??? question "How is it different from testing?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Accenture · SAP

    A test checks a known expected behaviour of a component; an experiment asks an open question about the whole system under conditions tests don't create (a black-holed dependency, a slow trickle, a dead pod at peak). Once an experiment's hypothesis holds, it often becomes a scheduled regression check, much like a test.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q2-how-is-it-different-from-testing-l2l3-accenture-sap)


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

??? question "What is mass assignment, and how does it happen in Spring?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    Binding a request body onto an entity so a client can set fields it shouldn't (status, price, owner): OWASP API3. It happens when controllers accept entities as `@RequestBody`. Use request types with only the changeable fields, and reject unknown fields: Jackson 3, and so Spring Boot 4, ignores them by default; set `spring.jackson.deserialization.fail-on-unknown-properties: true`.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q4-what-is-mass-assignment-and-how-does-it-happen-in-spring-l3-sap-cognizant)

??? question "Walk through the OWASP API Security Top 10."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Cognizant · Amazon

    2023 edition: API1 broken object-level authorization, API2 broken authentication, API3 broken object property level authorization, API4 unrestricted resource consumption, API5 broken function-level authorization, API6 unrestricted access to sensitive business flows, API7 SSRF, API8 security misconfiguration, API9 improper inventory management, API10 unsafe consumption of APIs. Three of the first five are authorization.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q10-walk-through-the-owasp-api-security-top-10-l2l3-cognizant-amazon)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "Walk through the authorization code flow with PKCE."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Cognizant

    The client creates a random verifier and sends its SHA-256 hash with the authorization request; the user signs in; the authorization server redirects back with a one-time code; the client redeems the code with the verifier at the token endpoint and gets tokens. A code captured on the way back is useless without the verifier.

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q1-walk-through-the-authorization-code-flow-with-pkce-l2l3-flipkart-cognizant)

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

??? question "What changed in Spring Security 7?"
    <span class="badge badge-l3">L3</span> SAP · Cognizant

    It ships with Spring Boot 4: the authorization server is part of Spring Security (`http.oauth2AuthorizationServer`), only the lambda DSL remains, and RFC 9068 validation is available (`JwtValidators.createAtJwtValidator()`). Check the migration guide and re-test security after upgrading.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q6-what-changed-in-spring-security-7-l3-sap-cognizant)


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

??? question "How do secrets reach a Spring Boot application?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    `spring.config.import` with `configtree:` for mounted files (read once at start-up) or `vault://` for Spring Cloud Vault (Kubernetes auth, KV v2 as properties, leases managed by Spring Vault). Rotating values need code that reacts: a lease listener or a file watcher.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q8-how-do-secrets-reach-a-spring-boot-application-l2-cognizant-sap)


**[40. API Security Hardening](../security/ch40-api-security.md)**

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

??? question "WAF: worth it? How do you run it?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Accenture

    As one layer against generic attacks and as a fast virtual patch (Log4Shell), yes, and PCI DSS 4.0 requires one for public-facing payment applications. Tune it: as shipped, the CRS blocked 15% of FoodFleet's legitimate requests at PL1 and 49% at PL2 (measured), mostly methods and free text. Run detection-only first, write narrow exclusions, and don't expect it to catch BOLA or business-logic abuse (0 of 6 caught).

    [Full answer in chapter 40](../security/ch40-api-security.md#q10-waf-worth-it-how-do-you-run-it-l3-goldman-sachs-accenture)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "What is an SBOM, and what do you do with it?"
    <span class="badge badge-l2">L2</span> Cognizant · SAP

    A list of every component in an artifact with exact identities (purls) and versions, in CycloneDX or SPDX. You keep one per release, attach it to the image, and match it against advisories continuously, so "are we affected?" is a query. Spring Boot's parent configures the CycloneDX plugin and serves the result at `/actuator/sbom`.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q1-what-is-an-sbom-and-what-do-you-do-with-it-l2-cognizant-sap)

??? question "Which findings should fail the build?"
    <span class="badge badge-l3">L3</span> SAP · Infosys

    The ones a team can fix now: critical/high with a fixed version. Everything else is tracked with an owner. Accepted risk is a waiver naming one advisory and package, with a reason and an expiry, published as VEX. Blocking on everything gets the gate disabled.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q4-which-findings-should-fail-the-build-l3-sap-infosys)

??? question "Why pin GitHub Actions by SHA?"
    <span class="badge badge-l2">L2</span> Flipkart · Accenture

    Tags are mutable: in March 2025 the `tj-actions/changed-files` tags were moved to a commit that dumped runner secrets into logs. A SHA can't be moved. Pair it with Renovate/Dependabot updates and per-job least-privilege `permissions`.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q8-why-pin-github-actions-by-sha-l2-flipkart-accenture)


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "What is the test pyramid?"
    <span class="badge badge-l1">L1</span> TCS · Infosys

    Mike Cohn's guideline: many fast unit tests, fewer service or integration tests, and few slow UI or end-to-end tests, because cost and brittleness grow toward the top.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q1-what-is-the-test-pyramid-l1-tcs-infosys)

??? question "Unit, integration, contract, component: what's the difference?"
    <span class="badge badge-l2">L2</span> Wipro

    Unit: a class in memory with fakes. Integration: code against a real dependency (a database). Contract: a client against the other service's recorded API. Component: one whole service through its API, with its own database real and other services stubbed.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q3-unit-integration-contract-component-whats-the-difference-l2-wipro)

??? question "What is mutation testing?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    Planting bugs in working code and checking that tests fail. The share killed is a far better measure of a suite than line coverage. Tools like PIT generate small mutants automatically; hand-written realistic ones, from incidents, show which layer catches what.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q7-what-is-mutation-testing-l3-thoughtworks)


**[62. Unit, Slice & Testcontainers Tests](../testing/ch62-unit-slice-integration-tests.md)**

??? question "What's the difference between a unit test and a slice test?"
    <span class="badge badge-l2">L2</span> TCS · Cognizant

    A unit test constructs classes directly, in memory, with no framework. A slice test starts part of a Spring application: `@WebMvcTest` the web layer with MockMvc, `@JdbcTest`/`@DataJpaTest` the data layer. Slices test how your code works with the framework; unit tests test your logic.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q1-whats-the-difference-between-a-unit-test-and-a-slice-test-l2-tcs-cognizant)

??? question "What does `@DataJpaTest` do by default?"
    <span class="badge badge-l2">L2</span> Infosys

    Loads JPA repositories and their configuration only, runs each test in a transaction that's rolled back, and replaces the DataSource with an embedded database unless it's already a test database. In Spring Boot 4.1 the default is `Replace.NON_TEST`, and a `@ServiceConnection` container counts as a test database, so it's kept.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q2-what-does-datajpatest-do-by-default-l2-infosys)

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

??? question "How do you test something asynchronous?"
    <span class="badge badge-l2">L2</span> Wipro

    Poll until it's true or a timeout passes (Awaitility: every 100 ms, up to 10 s by default), never a fixed sleep. Measured: polling failed none of 400 checks and averaged 580–678 ms; a 1 s sleep failed 9.5–12.5% and took longer.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q3-how-do-you-test-something-asynchronous-l2-wipro)

??? question "How do you make component tests realistic?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    The real service binary (`@SpringBootTest` on a random port), the real database engine in a container, and stubs generated from or verified against the collaborators' contracts, so a stub can't drift from the real service.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q9-how-do-you-make-component-tests-realistic-l3-thoughtworks)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "Which percentiles should a load test report?"
    <span class="badge badge-l2">L2</span> TCS

    p50, p99, p99.9 and max, plus the load model and the throughput achieved. Averages and p95 hide stalls; a max far above the p99 in a closed-model test is a sign of coordinated omission.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q4-which-percentiles-should-a-load-test-report-l2-tcs)

??? question "What is a fitness function?"
    <span class="badge badge-l3">L3</span> Thoughtworks

    An automated check that an architectural characteristic holds, run continuously: layer dependencies, no cycles, allowed libraries, naming, or measured qualities like latency. With ArchUnit they're JUnit tests over compiled classes.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q9-what-is-a-fitness-function-l3-thoughtworks)

??? question "How do you introduce architecture rules into a legacy codebase?"
    <span class="badge badge-l3">L3</span> Infosys

    Freeze: record existing violations in a store committed with the code, and fail only on new ones (ArchUnit's `FreezingArchRule`; store creation must be allowed explicitly). Measured: of 8 violations, the 3 new ones were reported. Shrink the store as code is fixed.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q11-how-do-you-introduce-architecture-rules-into-a-legacy-codebase-l3-infosys)
