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


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "What is gRPC, and how is it different from REST?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    An RPC framework: services and messages defined in `.proto` files, code generated for clients and servers, calls over HTTP/2 with Protocol Buffers payloads. REST models resources over HTTP with (usually) JSON and HTTP semantics such as caching. gRPC adds streaming, deadlines and a code-generated contract; REST is simpler to consume and works everywhere.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q1-what-is-grpc-and-how-is-it-different-from-rest-l1l2-cognizant-sap-flipkart)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "What is GraphQL, and what problem does it solve?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A query language and runtime for APIs: the server publishes a typed schema, and clients request exactly the fields they need, across related types, in one request. It addresses over-fetching (whole resources for a small screen), under-fetching (many round trips to compose a screen) and endpoint sprawl for many client screens.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q1-what-is-graphql-and-what-problem-does-it-solve-l1l2-cognizant-sap-flipkart)


**[25. Asynchronous Messaging](../communication/ch25-asynchronous-messaging.md)**

??? question "What is the difference between a queue and a topic?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    A queue delivers each message to one of its consumers (competing consumers, work distribution). A topic delivers each message to every subscriber; in Kafka, to every consumer group, with partitions shared out among the group's consumers.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q1-what-is-the-difference-between-a-queue-and-a-topic-l1l2-cognizant-sap-flipkart)


**[26. WebSockets & Server-Sent Events](../communication/ch26-websockets-sse.md)**

??? question "What are the options for pushing updates from server to client?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP · Flipkart

    Short polling (ask on a timer), long polling (the server holds the request until there is news), Server-Sent Events (a streaming HTTP response of events) and WebSocket (a full-duplex connection after an HTTP upgrade); plus push notifications for mobile apps in the background.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q1-what-are-the-options-for-pushing-updates-from-server-to-client-l1l2-cognizant-sap-flipkart)


**[27. API Contracts & Schema Evolution](../communication/ch27-api-contracts.md)**

??? question "What is an API contract?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Everything consumers rely on: the shape of requests and responses, behaviour (status codes, errors, defaults, ordering), the meaning of fields, and non-functional expectations. Schema documents (OpenAPI, AsyncAPI, protobuf) capture the shape and part of the behaviour; meaning needs documentation and review.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q1-what-is-an-api-contract-l1l2-cognizant-sap)


**[28. Idempotency, Deduplication & Correlation](../communication/ch28-idempotency-correlation.md)**

??? question "What does idempotent mean, and which HTTP methods are idempotent?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Repeating the operation has the same effect as doing it once. `GET`, `HEAD`, `PUT`, `DELETE` and `OPTIONS` are idempotent by definition; `POST` and `PATCH` are not, so they need an idempotency key (or a design that makes them idempotent) to be retried safely.

    [Full answer in chapter 28](../communication/ch28-idempotency-correlation.md#q1-what-does-idempotent-mean-and-which-http-methods-are-idempotent-l1l2-cognizant-sap)


### Data


**[29. Polyglot Persistence](../data/ch29-polyglot-persistence.md)**

??? question "What is polyglot persistence?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Cognizant · SAP

    Using different storage technologies for different data and access patterns within one system, typically per service: relational for transactions, a search engine for text search, a key-value store for caches and fast-changing ephemeral state. Database per service makes it possible; the cost is operating each store and keeping copies consistent.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q1-what-is-polyglot-persistence-l1l2-cognizant-sap)


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

??? question "Deployment, StatefulSet, DaemonSet, Job: when?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Wipro · Cognizant

    

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q8-deployment-statefulset-daemonset-job-when-l1l2-wipro-cognizant)


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


**[2. Characteristics & Principles](../foundations/ch02-characteristics-principles.md)**

??? question "What are the key characteristics of microservices?"
    <span class="badge badge-l1">L1</span> Cognizant · TCS · Infosys

    Independently deployable services organised around business capabilities. Each owns its data and is run by the team that built it. They communicate through smart endpoints and dumb pipes, are designed for failure, and depend on heavy automation.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q1-what-are-the-key-characteristics-of-microservices-l1-cognizant-tcs-infosys)

??? question "What does single responsibility mean for a service?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Accenture · Flipkart

    One business capability and one reason to change, owned by one team. It is not a line-count limit and not one entity per service.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q2-what-does-single-responsibility-mean-for-a-service-l1l2-accenture-flipkart)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "What is the Twelve-Factor App? Name the factors."
    <span class="badge badge-l1">L1</span> Cognizant · TCS · Infosys · Accenture

    A methodology from Heroku (2011) for building apps that any platform can deploy, scale and replace without special handling. The factors: codebase, dependencies, config, backing services, build-release-run, processes, port binding, concurrency, disposability, dev/prod parity, logs, admin processes.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q1-what-is-the-twelve-factor-app-name-the-factors-l1-cognizant-tcs-infosys-accenture)

??? question "Why store configuration in the environment rather than in the jar?"
    <span class="badge badge-l1">L1</span> <span class="badge badge-l2">L2</span> Amazon · Flipkart

    So one build can run in every environment. If config is baked in, each environment needs its own build, and production runs an artifact that was never tested.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q2-why-store-configuration-in-the-environment-rather-than-in-the-jar-l1l2-amazon-flipkart)


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


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "Why structured logs instead of text?"
    <span class="badge badge-l1">L1</span> TCS · Infosys

    Fields can be queried, aggregated and joined (`order.id`, `trace.id`, `log.level`) without fragile parsing, and multi-line stack traces stay in one record. Spring Boot 4 writes ECS, Logstash or GELF JSON with one property. The cost is bytes: 365 bytes against 121 per line here with Logback's defaults, about 290 when trimmed.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q1-why-structured-logs-instead-of-text-l1-tcs-infosys)


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


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "What is the test pyramid?"
    <span class="badge badge-l1">L1</span> TCS · Infosys

    Mike Cohn's guideline: many fast unit tests, fewer service or integration tests, and few slow UI or end-to-end tests, because cost and brittleness grow toward the top.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q1-what-is-the-test-pyramid-l1-tcs-infosys)
