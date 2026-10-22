### Architecture & Patterns


**[9. Hexagonal, Clean & Onion Architecture](../architecture-patterns/ch09-hexagonal-clean-architecture.md)**

??? question "Hexagonal vs onion vs clean architecture?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    Same core idea (inward-pointing dependencies, framework-free core) drawn differently. Hexagonal emphasises the symmetry of inbound and outbound adapters; onion emphasises concentric layers inside the core; clean architecture adds use cases as explicit objects and names the dependency rule. Most teams mix the vocabulary.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q3-hexagonal-vs-onion-vs-clean-architecture-l3-sap-goldman-sachs)

??? question "Where do you put `@Transactional` in a hexagonal service?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    Not on domain code. Options: a transactional decorator around the application service in the composition root, `@Transactional` on the driving adapter, or short transactions inside repository adapters. If the use case calls a remote system, never hold a DB transaction across the call: record intent, call, record outcome, reconcile the gap.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q5-where-do-you-put-transactional-in-a-hexagonal-service-l3-jp-morgan-goldman-sachs)

??? question "Should JPA entities be your domain model?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    For core domains, usually not: JPA requires no-arg constructors, mutable fields and proxies, and lazy loading leaks persistence concerns into rules. Map between a clean domain model and persistence in the adapter, or use Spring Data JDBC, which respects aggregate boundaries. For simple CRUD services, JPA entities as the model are fine.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q7-should-jpa-entities-be-your-domain-model-l3-goldman-sachs-sap)

??? question "What's an anti-corruption layer in a hexagonal service?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    The translator inside a driven adapter that converts an external model (a payment provider's statuses and error codes) into the core's model. It's where interpretation decisions live, with fail-safe defaults for unknown values.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q8-whats-an-anti-corruption-layer-in-a-hexagonal-service-l3-jp-morgan-sap)

??? question "How would you add a new payment provider?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A new driven adapter implementing `PaymentGateway`: an HTTP client in the provider's vocabulary, a translator with tests for every status and error code (unknown means "not approved"), idempotency keys per call, error mapping to the port's exceptions. Run it against the gateway contract test and the provider's sandbox, then route a small percentage of traffic to it by configuration.

    [Full answer in chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md#q11-how-would-you-add-a-new-payment-provider-l3l4-goldman-sachs-flipkart)


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

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


**[11. Service Discovery Patterns](../architecture-patterns/ch11-service-discovery-patterns.md)**

??? question "How does service discovery work in Kubernetes?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · JP Morgan

    A Service selects pods by label; ready pods' IPs go into EndpointSlices; kube-proxy on each node routes connections to the Service's ClusterIP to a ready pod; CoreDNS resolves the Service name to the ClusterIP (or to all pod IPs for a headless Service). Readiness probes decide membership.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q4-how-does-service-discovery-work-in-kubernetes-l2l3-amazon-flipkart-jp-morgan)

??? question "An instance crashes. How long until callers stop sending it requests?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    It depends on every cache between the failure and the caller. Eureka defaults can take several minutes (lease, eviction, server cache, client fetch, load-balancer cache); Kubernetes removes a crashed pod in seconds but a hung one only after readiness probes fail (30 s with defaults). Mitigate with short timeouts and a retry on another instance for idempotent calls.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q6-an-instance-crashes-how-long-until-callers-stop-sending-it-requests-l3-amazon-goldman-sachs)

??? question "What's Eureka's self-preservation mode?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    When renewals drop below a threshold (85% of expected by default), the Eureka server assumes a network problem and stops evicting instances, protecting against mass eviction during partitions. The trade-off: dead instances remain listed until renewals recover, so clients need timeouts and retries.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q8-whats-eurekas-self-preservation-mode-l3-jp-morgan-sap)

??? question "How do you avoid errors during rolling deployments?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Readiness gates new pods until warm; on termination, a `preStop` pause lets endpoint removal propagate while the pod still serves, then graceful shutdown finishes in-flight requests; `maxUnavailable` and PodDisruptionBudgets keep enough capacity; callers retry idempotent requests elsewhere.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q10-how-do-you-avoid-errors-during-rolling-deployments-l3-flipkart-goldman-sachs)

??? question "Design discovery for a platform with VMs, two Kubernetes clusters and a mainframe."
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    One catalog spanning environments (Consul federation or a mesh with multi-cluster support), locality-aware routing, mainframe endpoints registered by a third party behind an ACL or adapter, health checks per environment, and failover rules tested with drills. Keep Kubernetes-native discovery inside clusters and use gateways or mesh gateways between them.

    [Full answer in chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md#q12-design-discovery-for-a-platform-with-vms-two-kubernetes-clusters-and-a-mainframe-l4-jp-morgan-goldman-sachs)


**[12. Database per Service & API Composition](../architecture-patterns/ch12-database-per-service.md)**

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

??? question "Schema per service or database per service?"
    <span class="badge badge-l3">L3</span> JP Morgan · SAP

    Schema per service with one user each gives enforced ownership at low cost. Separate databases when a service needs different technology, failure isolation, compliance separation, independent scaling or its own upgrade schedule.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q8-schema-per-service-or-database-per-service-l3-jp-morgan-sap)

??? question "Is it OK for a service to keep a copy of another service's data?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    Yes, if the copy is read-only and its freshness is deliberate: a snapshot at a business moment (the price when ordered) or a replica kept current from the owner's events. Two services writing the same data is the problem, not copies.

    [Full answer in chapter 12](../architecture-patterns/ch12-database-per-service.md#q9-is-it-ok-for-a-service-to-keep-a-copy-of-another-services-data-l3-flipkart-goldman-sachs)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "What is event sourcing?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Persisting every state change as an immutable event in an append-only store; current state is derived by replaying an aggregate's events (or a snapshot plus later events). It gives full history, auditability, temporal queries and the ability to build new read models from past data.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q2-what-is-event-sourcing-l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you handle concurrent commands on the same aggregate?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Optimistic concurrency: append with the expected stream version; if another writer appended first, the store rejects it. Reload, decide again (rules re-checked against the newer state), and retry a bounded number of times.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q4-how-do-you-handle-concurrent-commands-on-the-same-aggregate-l3-amazon-goldman-sachs)

??? question "How do read models stay up to date, and what about consistency?"
    <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    Projections subscribe to the event log from a checkpoint and update read models; checkpoint and read model are updated atomically, or handlers are idempotent. Read models are eventually consistent; measure lag, and for the user's own latest change return the command result or read from the write side.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q5-how-do-read-models-stay-up-to-date-and-what-about-consistency-l3-flipkart-jp-morgan)

??? question "How do you change the schema of events already stored?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Never rewrite history. Additive changes are tolerated by readers; breaking changes get a new schema version and an upcaster that converts old events on read; wrong data is corrected with compensating events. As a last resort, copy-and-transform into a new store.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q7-how-do-you-change-the-schema-of-events-already-stored-l4-goldman-sachs-jp-morgan)

??? question "How does event sourcing work with GDPR's right to erasure?"
    <span class="badge badge-l4">L4</span> SAP · banks with EU operations

    Keep personal data out of events (reference ids, store PII separately), or encrypt it with a per-person key and delete the key (crypto-shredding). Design this before the first event is stored.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q8-how-does-event-sourcing-work-with-gdprs-right-to-erasure-l4-sap-banks-with-eu-operations)

??? question "How do you rebuild or add a read model?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Reset the read model and its checkpoint, replay the log from the start. For large logs, build the new version alongside the old one (blue/green), switch reads when it has caught up, then drop the old one.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q11-how-do-you-rebuild-or-add-a-read-model-l3-goldman-sachs-flipkart)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

??? question "Why not use two-phase commit across microservices?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    It needs every participant and the coordinator available to finish, holds locks across network calls, requires XA-capable resources (brokers and HTTP APIs aren't), and couples services to a shared transaction manager. External systems like payment providers never participate.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q2-why-not-use-two-phase-commit-across-microservices-l3-goldman-sachs-amazon)

??? question "What are compensatable, pivot and retriable transactions?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Compensatable steps can be undone; the pivot is the point of no return (if it succeeds, the saga completes); retriable steps follow the pivot and must eventually succeed, so they are retried, not compensated. The order is compensatable*, pivot, retriable*.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q4-what-are-compensatable-pivot-and-retriable-transactions-l4-goldman-sachs-jp-morgan)

??? question "Why must saga participants be idempotent?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Retries after timeouts and orchestrator recovery after crashes deliver the same request more than once; replies can be lost after the work was done. Without idempotency, retries double-charge or double-book. And a repeated request must return the original outcome, not be re-evaluated.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q5-why-must-saga-participants-be-idempotent-l3-amazon-jp-morgan)

??? question "Sagas lack isolation. What problems does that cause and how do you handle them?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Dirty reads, lost updates and fuzzy reads of intermediate state. Countermeasures: semantic locks (PENDING states), commutative updates, re-reading values, pessimistic views (copying values into the saga), ordering by value, and reservations with expiry.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q6-sagas-lack-isolation-what-problems-does-that-cause-and-how-do-you-handle-them-l4-goldman-sachs-sap)

??? question "What if a compensation fails?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Compensations are designed never to fail for business reasons; technical failures are retried until they succeed (they're idempotent). If they keep failing, the saga is marked stuck and escalated, with alerting and a manual procedure, plus reconciliation against the external system.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q8-what-if-a-compensation-fails-l3l4-jp-morgan-goldman-sachs)


**[15. Transactional Outbox, Inbox & CDC](../architecture-patterns/ch15-outbox-inbox-cdc.md)**

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

??? question "How do you preserve event order?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Per aggregate: key messages by aggregate id (same partition), publish outbox rows in position order, stop on failure rather than skipping, and run one active relay per service (or partition relays by aggregate). Global order is rarely needed and expensive.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q5-how-do-you-preserve-event-order-l4-amazon-goldman-sachs)

??? question "Why `FOR UPDATE SKIP LOCKED`?"
    <span class="badge badge-l3">L3</span> JP Morgan · startups

    Several relay instances can poll the same table without blocking on each other or publishing the same row concurrently: each locks a batch and the others skip locked rows. It improves throughput but can reorder events across instances, so combine it with partitioning or a single active relay.

    [Full answer in chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md#q6-why-for-update-skip-locked-l3-jp-morgan-startups)

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

??? question "What is an anti-corruption layer and where does it live?"
    <span class="badge badge-l3">L3</span> SAP · Goldman Sachs

    A translation layer between a new model and a legacy (or external) one, so the new model isn't shaped by the old one's codes and quirks. It lives in the new service at its boundary with the legacy system, often in both directions (reading legacy data, calling legacy functions). Untranslatable data should be quarantined, not guessed.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q2-what-is-an-anti-corruption-layer-and-where-does-it-live-l3-sap-goldman-sachs)

??? question "How do you know the new service behaves like the old one?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Shadow traffic: call both on real production requests, return the old answer, compare field by field, and fix or explicitly accept every difference (with documented tolerances). Contract and characterization tests help, but production traffic finds the cases nobody thought of.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q4-how-do-you-know-the-new-service-behaves-like-the-old-one-l3l4-goldman-sachs-jp-morgan)

??? question "How do you migrate the data?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Read legacy data through an ACL first; then backfill and sync with CDC; reconcile continuously; switch write ownership once (never two writers); have the monolith read from the new owner; retire legacy tables when audits show no readers.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q7-how-do-you-migrate-the-data-l4-goldman-sachs-sap)

??? question "What are the risks of the strangler approach?"
    <span class="badge badge-l4">L4</span> Goldman Sachs

    Migrations that never finish (old code never deleted), the facade becoming a bottleneck or a place for business logic, two writers on shared data, a distributed monolith if new services keep calling back into the old one, and the cost of running both systems for longer.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q9-what-are-the-risks-of-the-strangler-approach-l4-goldman-sachs)

??? question "How do you handle writes that can't be shadowed?"
    <span class="badge badge-l4">L4</span> JP Morgan · Amazon

    Compare decisions rather than effects (what the new service *would* write), run it against a copy of data, canary with very small cohorts and strong guards, or move writes last once reads have proven the model. Never execute both writes for real.

    [Full answer in chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md#q10-how-do-you-handle-writes-that-cant-be-shadowed-l4-jp-morgan-amazon)


**[17. Sidecar, Ambassador & Adapter](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)**

??? question "Library or sidecar for resilience in a Java service?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    A library when the logic needs business context (which operations are safe to retry, fallbacks) or the fleet is one language; a sidecar when it must be uniform across languages, owned by the platform, or changed without redeploying apps. Never both for the same concern, or retries multiply.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q4-library-or-sidecar-for-resilience-in-a-java-service-l3-goldman-sachs-jp-morgan)

??? question "How do service meshes relate to these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    A sidecar mesh puts an ambassador-style proxy (usually Envoy) in every pod for all traffic: mTLS, retries, traffic splitting, telemetry. Ambient mode moves that out of the pod to per-node and per-namespace proxies to cut per-pod cost.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q7-how-do-service-meshes-relate-to-these-patterns-l3l4-goldman-sachs-flipkart)

??? question "How should timeouts be set between app and ambassador?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    The app's timeout must exceed the ambassador's total budget (per-try timeout × attempts + backoff), otherwise the app gives up while the ambassador is still retrying, wasting work and possibly causing duplicate effects.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q9-how-should-timeouts-be-set-between-app-and-ambassador-l3-jp-morgan-amazon)

??? question "What are the security considerations for sidecars?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Least privilege per container (secrets mounted only where needed), non-root and read-only filesystems, loopback binding for proxies holding credentials, image provenance and patching for every sidecar image, and network policy around anything that reaches external systems.

    [Full answer in chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#q11-what-are-the-security-considerations-for-sidecars-l3l4-goldman-sachs-jp-morgan)


**[18. Resilience Patterns](../architecture-patterns/ch18-resilience-patterns.md)**

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

??? question "In what order do you compose these patterns?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Commonly fallback outermost, then bulkhead, retry, circuit breaker, timeout innermost (Resilience4j's Spring annotations apply Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(call)))))). Whether the breaker sees attempts or operations depends on whether it is inside or outside retry; choose deliberately.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q8-in-what-order-do-you-compose-these-patterns-l3l4-goldman-sachs-sap)

??? question "Would you put resilience in the app, a sidecar, or the mesh?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Flipkart

    Timeouts everywhere; retries in exactly one layer, usually closest to the dependency; breakers either in the app or the mesh; fallbacks always in the app, because only it knows what a useful degraded answer is.

    [Full answer in chapter 18](../architecture-patterns/ch18-resilience-patterns.md#q12-would-you-put-resilience-in-the-app-a-sidecar-or-the-mesh-l4-goldman-sachs-flipkart)


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

??? question "How do you evolve event schemas?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · JP Morgan

    Treat events as contracts: add optional fields only, never rename or repurpose; enforce compatibility with a schema registry and consumer contract tests; publish breaking changes as a new event type or topic alongside the old one until consumers migrate.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q7-how-do-you-evolve-event-schemas-l3l4-sap-jp-morgan)

??? question "What are the downsides of event-driven architecture?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Eventual consistency, harder debugging and tracing, hidden dependencies (who consumes what?), duplicate and ordering handling, schema governance, data copies (including personal data), and operational load (brokers, lag, dead letters).

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q8-what-are-the-downsides-of-event-driven-architecture-l3-goldman-sachs-amazon)

??? question "How do you include personal data in events safely?"
    <span class="badge badge-l4">L4</span> JP Morgan · SAP

    Prefer not to: publish ids and let authorised consumers fetch details. If needed, minimise fields, encrypt sensitive ones (crypto-shredding for erasure), set retention deliberately, and document consumers for data-protection requests.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q10-how-do-you-include-personal-data-in-events-safely-l4-jp-morgan-sap)


**[20. Microservice Anti-Patterns](../architecture-patterns/ch20-microservice-anti-patterns.md)**

??? question "How would you detect a distributed monolith in an existing system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    From evidence: deploy history (co-deployment ratios), distributed traces (synchronous depth, cycles, fan-out), database audit logs (non-owner table access), dependency manifests (shared domain libraries, version skew), and change coupling in version control.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q2-how-would-you-detect-a-distributed-monolith-in-an-existing-system-l3l4-amazon-goldman-sachs)

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

??? question "How do you keep anti-patterns from coming back?"
    <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Fitness functions in the build: an architecture model generated from real evidence, detectors with agreed thresholds, and a baseline of accepted findings with owners and tickets that can only shrink.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q8-how-do-you-keep-anti-patterns-from-coming-back-l4-flipkart-goldman-sachs)

??? question "Your team wants to split a service 'for performance'. What do you ask?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Which part has a different load or latency profile, with data? How many calls per request will cross the new boundary? Will the parts change and release independently? Who owns each? If the answers are "same profile, many calls, always together, same team", the split will make performance worse.

    [Full answer in chapter 20](../architecture-patterns/ch20-microservice-anti-patterns.md#q11-your-team-wants-to-split-a-service-for-performance-what-do-you-ask-l3l4-amazon-goldman-sachs)


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

??? question "Connect timeout vs read timeout?"
    <span class="badge badge-l2">L2</span> Amazon · JP Morgan

    Connect timeout bounds establishing the connection (dead host, full backlog); read timeout bounds waiting for response data. Both must be set; defaults are often infinite or very long.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q4-connect-timeout-vs-read-timeout-l2-amazon-jp-morgan)

??? question "Is a blocking HTTP client a scalability problem?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    On platform threads, yes: concurrency is capped by the pool. On virtual threads, blocking parks cheaply; measured here, 1,000 concurrent 200 ms calls took 0.56 s on virtual threads vs 4.2 s on a 50-thread pool. Still bound concurrency explicitly to protect the dependency.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q5-is-a-blocking-http-client-a-scalability-problem-l2l3-flipkart-goldman-sachs)

??? question "Sync or async API with virtual threads?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Prefer synchronous calls on virtual threads for readability and debuggability; use structured concurrency or parallel calls for fan-out. Async/reactive APIs remain useful for streaming and backpressure, not for basic concurrency.

    [Full answer in chapter 22](../communication/ch22-http-clients.md#q10-sync-or-async-api-with-virtual-threads-l3-goldman-sachs-amazon)


**[23. gRPC & Protocol Buffers](../communication/ch23-grpc.md)**

??? question "How does Protocol Buffers encode data?"
    <span class="badge badge-l2">L2</span> Amazon · Goldman Sachs

    Each field is a tag, `(field_number << 3) | wire_type`, followed by the value: varints for integers and enums, fixed 4 or 8 bytes for floats and doubles, length-prefixed bytes for strings and nested messages. Names aren't sent; in proto3 default values aren't sent either.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q2-how-does-protocol-buffers-encode-data-l2-amazon-goldman-sachs)

??? question "How do deadlines work, and why are they better than timeouts?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    The client sets an absolute deadline, sent as `grpc-timeout`; servers that make further calls inside the request pass the remaining time on, and cancellation propagates when it expires. Fixed per-hop timeouts know nothing about the caller's budget, so work continues after the caller has given up: 639 ms of wasted work per request vs 2 ms in this chapter's simulation.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q5-how-do-deadlines-work-and-why-are-they-better-than-timeouts-l2l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you evolve a protobuf schema without breaking clients?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    Add fields with new numbers; never change a field's number or (incompatibly) its type; `reserved` removed numbers and names; keep a zero `UNSPECIFIED` enum value and handle unknown values; put a version in the package for breaking changes; enforce with a breaking-change checker in CI.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q7-how-do-you-evolve-a-protobuf-schema-without-breaking-clients-l2l3-flipkart-jp-morgan)

??? question "How does streaming handle a slow client?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    HTTP/2 flow control limits how much a sender can have in flight per stream. In grpc-java, a server checks `isReady()` and uses `setOnReadyHandler` to send only when the client can take more, and coalesces or drops intermediate updates (like old rider positions) rather than buffering without bound.

    [Full answer in chapter 23](../communication/ch23-grpc.md#q10-how-does-streaming-handle-a-slow-client-l3-goldman-sachs-amazon)


**[24. GraphQL](../communication/ch24-graphql.md)**

??? question "What is the N+1 problem in GraphQL and how do you fix it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Resolvers run per parent object, so a list of N items whose field calls a service makes 1 + N calls. Fix it with batch loading: DataLoader collects keys at one execution level and loads them in one call (Spring's `@BatchMapping` or `BatchLoaderRegistry`). In this chapter a screen went from 19 backend calls to 3, with identical data.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q3-what-is-the-n1-problem-in-graphql-and-how-do-you-fix-it-l2l3-flipkart-amazon-goldman-sachs)

??? question "How do you protect a GraphQL API from expensive queries?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    Limit depth and computed complexity before execution (graphql-java instrumentations, with a cost function that multiplies by page sizes), cap page sizes, time out execution, and in production accept only persisted queries from your own apps. Batching alone doesn't help: it reduces calls, not the amount of work.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q5-how-do-you-protect-a-graphql-api-from-expensive-queries-l3-goldman-sachs-jp-morgan-amazon)

??? question "How do you secure a GraphQL endpoint?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    Authenticate at the gateway and pass identity in context (never as arguments), authorise per field or object, disable introspection in production for first-party apps, use persisted queries as an allow-list, limit depth, complexity, aliases and page sizes, and avoid leaking internals in error messages.

    [Full answer in chapter 24](../communication/ch24-graphql.md#q10-how-do-you-secure-a-graphql-endpoint-l2l3-jp-morgan-goldman-sachs)


**[25. Asynchronous Messaging](../communication/ch25-asynchronous-messaging.md)**

??? question "Explain at-most-once, at-least-once and exactly-once."
    <span class="badge badge-l2">L2</span> Goldman Sachs · JP Morgan · Amazon

    Acknowledge before processing: at-most-once, messages can be lost. Acknowledge after: at-least-once, messages can be duplicated. Exactly-once effects come from at-least-once delivery plus an idempotent consumer (or a transaction covering both the effect and the offset, as Kafka transactions do within Kafka). In this chapter's simulation, idempotency turned 9 duplicates into 0.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q3-explain-at-most-once-at-least-once-and-exactly-once-l2-goldman-sachs-jp-morgan-amazon)

??? question "Blocking or non-blocking retries in Spring Kafka?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Blocking (`DefaultErrorHandler` with a BackOff) keeps per-partition order but stalls the partition while retrying. Non-blocking (`@RetryableTopic`) moves failed records to delayed retry topics so the main topic keeps flowing, at the cost of ordering. Use blocking for sequential state, non-blocking for independent side effects. Measured: with one partner down, retry topics cut p99 from 30.9 s to 8.2 s.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q6-blocking-or-non-blocking-retries-in-spring-kafka-l3-amazon-goldman-sachs)

??? question "How would you design dead-letter handling for a payments system?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Classify errors; retry transient ones with bounded backoff in order (per account); dead-letter the rest with full context (headers, original offset, error); alert immediately; give operations a tool to inspect, fix and replay with approval and audit; make consumers idempotent so replays are safe; never drop.

    [Full answer in chapter 25](../communication/ch25-asynchronous-messaging.md#q10-how-would-you-design-dead-letter-handling-for-a-payments-system-l3l4-jp-morgan-goldman-sachs)


**[26. WebSockets & Server-Sent Events](../communication/ch26-websockets-sse.md)**

??? question "How do you scale WebSocket or SSE connections across many servers?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    Keep connections in a dedicated, horizontally scaled tier; get events to the right instance by broadcast (simple, N× work), routing via a registry, per-key pub/sub channels, or partitioned routing at the load balancer; use a shared broker (Kafka, Redis, or a STOMP broker relay). Size instances by connections and memory, not requests per second.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q5-how-do-you-scale-websocket-or-sse-connections-across-many-servers-l3-amazon-flipkart-goldman-sachs)

??? question "How many concurrent connections can one Java server hold?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    With a blocked platform thread per connection, about as many as the thread pool (measured: 200 of 10,000). With virtual threads or a reactive server, tens of thousands per instance; here 10,000 SSE streams used ~50 KB each. File descriptors, memory, the load balancer and broadcast CPU become the limits.

    [Full answer in chapter 26](../communication/ch26-websockets-sse.md#q6-how-many-concurrent-connections-can-one-java-server-hold-l3-goldman-sachs-jp-morgan)

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

??? question "What is AsyncAPI, and why does an event-driven system need it?"
    <span class="badge badge-l2">L2</span> SAP · Goldman Sachs

    AsyncAPI documents event-driven APIs: channels (topics), operations (send/receive), messages, headers and payload schemas. Events are contracts between teams just like HTTP APIs; documenting them enables review, code generation, validation and discovery, alongside a schema registry for runtime compatibility.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q9-what-is-asyncapi-and-why-does-an-event-driven-system-need-it-l2-sap-goldman-sachs)

??? question "Which schema registry compatibility mode would you choose for domain events, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Amazon · JP Morgan

    `FULL_TRANSITIVE`: any consumer version can read any event ever written, which matters for replays, new consumers reading history, and deploys in any order. The cost is that only optional fields (with defaults) can be added or removed; anything else is a new event type or topic.

    [Full answer in chapter 27](../communication/ch27-api-contracts.md#q10-which-schema-registry-compatibility-mode-would-you-choose-for-domain-events-and-why-l3l4-amazon-jp-morgan)


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


### Data


**[29. Polyglot Persistence](../data/ch29-polyglot-persistence.md)**

??? question "Why are dual writes dangerous?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Amazon

    There is no transaction across two stores, so a crash between the writes, an outage of the second store or two concurrent writers leave them disagreeing, silently. Measured: 43–77 of 5,000 items wrong after 20,000 changes under faults; writing the cache first even produced prices that were never committed. Use an outbox or CDC with a projector.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q4-why-are-dual-writes-dangerous-l3-goldman-sachs-jp-morgan-amazon)

??? question "What's wrong with a projector that remembers the last outbox id it processed?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Amazon

    Sequence values are assigned at insert but become visible at commit, in a different order. A transaction that took id 1,000 can commit after the projector has read 1,001, and is never read. Measured: 29–48 of 5,000 items left wrong. Claim rows with `FOR UPDATE SKIP LOCKED` and delete them when applied, or follow the log position (logical replication, Debezium).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q6-whats-wrong-with-a-projector-that-remembers-the-last-outbox-id-it-processed-l3l4-goldman-sachs-amazon)

??? question "Why can adding an index slow writes down so much?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    Every index must be updated on every write that changes its columns, and in PostgreSQL an indexed column change also prevents HOT updates, so all indexes get new entries. Measured: a GiST geo index took position updates from 98% HOT to 0%, cut the rate from about 16,800 to 7,600 per second and more than doubled WAL per update (217 → 517 B).

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q9-why-can-adding-an-index-slow-writes-down-so-much-l3-jp-morgan-goldman-sachs)

??? question "Your team wants MongoDB, Elasticsearch, Redis and a time-series DB for one new service. How do you respond?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    Ask for the access patterns with numbers and measure them on the supported stores first. Then add only stores that close a measured gap, each as a derived store or ephemeral state fed from one source of truth, with an owner and the operating cost accepted. At FoodFleet that turned four proposed stores into PostgreSQL plus Redis.

    [Full answer in chapter 29](../data/ch29-polyglot-persistence.md#q12-your-team-wants-mongodb-elasticsearch-redis-and-a-time-series-db-for-one-new-service-how-do-you-respond-l4-amazon-goldman-sachs)


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "Why is 2PC avoided between microservices?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    It holds locks across network round trips (measured: 2.8× as long as a saga step, half the throughput on hot rows), it couples availability (no orders while payments was down), it blocks if the coordinator fails (one in-doubt transaction cut a hot path to 2–6 orders/s), and it needs XA-capable resources, which brokers, HTTP APIs and external providers aren't. It also couples teams to a shared transaction manager.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q2-why-is-2pc-avoided-between-microservices-l3-amazon-flipkart-goldman-sachs)

??? question "What happens if the coordinator crashes after the participants prepared?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    The participants stay prepared, holding locks, across restarts, until the coordinator recovers and reads its log or someone resolves them. With presumed abort, no logged decision means roll back. Measured: the prepared transaction survived a database restart and kept its locks until recovery ran.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q3-what-happens-if-the-coordinator-crashes-after-the-participants-prepared-l3l4-goldman-sachs-jp-morgan)

??? question "What is a heuristic decision and why is it dangerous?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Committing or rolling back an in-doubt transaction without the coordinator's decision, usually by an operator to free blocked rows. If another participant got the opposite decision, the system is inconsistent ("heuristic mixed") and needs manual repair. Use the decision log; audit any heuristic action.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q4-what-is-a-heuristic-decision-and-why-is-it-dangerous-l4-jp-morgan-goldman-sachs)

??? question "What do you do when you don't know whether a remote commit happened?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Don't guess. Leave the operation pending and reconcile from the other side's records (or retry idempotently with the same key). Rejecting on any error charged customers for rejected orders in incident 3; measured, a database restart left up to one order (of 8 in flight) with an unknown outcome.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q8-what-do-you-do-when-you-dont-know-whether-a-remote-commit-happened-l3-goldman-sachs-amazon)

??? question "How do you make a saga participant safe to retry?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Key the work by the request's id and store the decision in the same local transaction (an inbox or decision row); a repeat returns the stored decision. Lock rows in a consistent order, and for batches fall back to one by one so business rejections stay per request.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q9-how-do-you-make-a-saga-participant-safe-to-retry-l3-amazon-jp-morgan)

??? question "When is 2PC acceptable?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Inside one service or one database product: a service writing to two of its own databases at low volume with an operations team ready for in-doubt recovery; distributed SQL databases that run 2PC over consensus-replicated participants; legacy integration in an application server. Not across services, teams or external systems.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q11-when-is-2pc-acceptable-l4-goldman-sachs-sap)


**[31. Data Consistency](../data/ch31-data-consistency.md)**

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

??? question "What is a CRDT? Give an example."
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    A data type whose replicas converge without coordination because merge is commutative, associative and idempotent. An OR-Set tags each add uniquely and removes only observed tags, so a concurrent add survives; a PN-counter keeps per-replica increments and decrements. Measured: no lost or resurrected items across 2,000 two-region carts.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q9-what-is-a-crdt-give-an-example-l4-amazon-goldman-sachs)

??? question "What is a hybrid logical clock and why use one?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    A timestamp of (physical time, counter) that never goes backwards and is always greater than any timestamp the node has seen. It stays close to wall time but respects causality, so ordering by it never puts an effect before what its writer had seen, even with skewed clocks. Used by CockroachDB and YugabyteDB.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q10-what-is-a-hybrid-logical-clock-and-why-use-one-l4-goldman-sachs-sap)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "'Update the database, then delete the cache key': is that safe?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Mostly, not always. A reader that missed just before the update can fill the old value just after the delete, and it stays until the TTL. Measured: 1–3 stale entries left in a 15 s run. Make cache writes conditional on version (set-if-newer), use leases, or delete again shortly after.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q3-update-the-database-then-delete-the-cache-key-is-that-safe-l3-amazon-goldman-sachs)

??? question "How do you prevent a cache stampede?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon · JP Morgan

    Coalesce misses (single-flight per instance or a distributed lock), refresh before expiry (probabilistic early expiration, with a lock), or serve stale while one caller refreshes. Add TTL jitter. Measured: 160 recomputations unprotected; 9–10 with stale-while-revalidate, and no request over 100 ms.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q4-how-do-you-prevent-a-cache-stampede-l3-flipkart-amazon-jp-morgan)

??? question "Why might a cache eviction in a @Transactional method not work?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · any

    Unless the cache manager is transaction-aware, the eviction happens before the commit, and a concurrent reader can reload the old row and cache it. Make the RedisCacheManager transaction-aware (Spring Boot: a `RedisCacheManagerBuilderCustomizer` calling `transactionAware()`), or evict after commit.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q6-why-might-a-cache-eviction-in-a-transactional-method-not-work-l3-goldman-sachs-any)

??? question "How do you keep a shared cache consistent across many services?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Let one service own the data and the cache, or drive invalidation from the owner's change stream (outbox or CDC) so no writer has to know every key. Version cached values so late or duplicate invalidations can't move a cache backwards.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q8-how-do-you-keep-a-shared-cache-consistent-across-many-services-l4-goldman-sachs-sap)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

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

??? question "How do you backfill a column on a billion-row table?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    In keyset batches by primary key, each its own short transaction, throttled, resumable, bounded by the max id at start, watching replica lag and autovacuum. Measured on 2 M rows: one `UPDATE` made writes wait up to 5.5–6.8 s; batches of 1,000 kept them at or under 60 ms.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q7-how-do-you-backfill-a-column-on-a-billion-row-table-l3-amazon-jp-morgan)

??? question "Read replicas or sharding?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Flipkart · Goldman Sachs

    Replicas scale reads that tolerate lag and are cheap to add; sharding scales writes and data size but makes cross-shard queries, transactions and operations hard. Exhaust indexing, caching, replicas, partitioning and write reduction first; shard when one primary can't take the writes.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q8-read-replicas-or-sharding-l3l4-flipkart-goldman-sachs)

??? question "How do you move data between shards without downtime?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Per slot: copy the rows, stream changes (logical replication, CDC or dual writes), verify counts and checksums, pause writes to that slot briefly, switch the slot map, resume, then delete the source copy. Routers read a versioned slot map; the old owner rejects writes for moved slots.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q11-how-do-you-move-data-between-shards-without-downtime-l4-goldman-sachs-jp-morgan)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "Outbox events or CDC for feeding analytics?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Outbox events for data other teams depend on: deliberate, in the domain's language, versioned. CDC when you need everything without changing the source, or for internal pipelines, accepting that it exposes table structure. Many platforms use both into the same Kafka backbone.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q3-outbox-events-or-cdc-for-feeding-analytics-l3-amazon-goldman-sachs)

??? question "What is a replication slot, and how can it take a database down?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    The source's bookmark for a logical (or physical) replication consumer; WAL is kept until the consumer confirms it. A stopped or orphaned consumer makes WAL accumulate until the disk is full. Measured: 132–158 MB a minute at our write rate. Cap it with `max_slot_wal_keep_size`, alert on retained WAL, drop slots with their consumers.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q4-what-is-a-replication-slot-and-how-can-it-take-a-database-down-l3-goldman-sachs-jp-morgan)

??? question "How do you know a report is up to date?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    You don't, unless it tells you. Track the event time of the newest change applied, show it as "as of", compare it with a freshness objective, alert when exceeded. Measured: with the CDC consumer stopped, the copy fell 60 s behind in 60 s and nothing in the data showed it.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q8-how-do-you-know-a-report-is-up-to-date-l3-jp-morgan-amazon)

??? question "What is data mesh, and when is it worth it?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Domain teams own and publish their analytical data as products (owner, schema, semantics, freshness and quality objectives) on a self-serve platform, with federated governance. Worth it when a central data team has become the bottleneck for many domains; without a strong platform and clear ownership it becomes many inconsistent pipelines.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q10-what-is-data-mesh-and-when-is-it-worth-it-l4-goldman-sachs-sap)

??? question "A reporting store disagrees with the payments system. What do you do?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    The system of record wins. Reconcile daily per day and entity, find the class of difference (missed events, duplicates, corrections, time zones, late data), fix the projection, and rebuild the affected range by replay. Reports carry their as-of time so known gaps are explainable.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q11-a-reporting-store-disagrees-with-the-payments-system-what-do-you-do-l3l4-jp-morgan-goldman-sachs)


### Deployment & Platform


**[53. Containerization](../deployment/ch53-containerization.md)**

??? question "Why was my pod OOMKilled with no OutOfMemoryError?"
    <span class="badge badge-l3">L3</span> Uber · PhonePe

    The process exceeded the cgroup limit, usually because `-Xmx` (plus off-heap memory) was above it, and the kernel killed it. Measured: `-Xmx1g` in 512 MB gave exit 137, `OOMKilled: true` and no Java output. Size the heap as a percentage of the limit and use `ExitOnOutOfMemoryError`.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q6-why-was-my-pod-oomkilled-with-no-outofmemoryerror-l3-uber-phonepe)

??? question "Which GC does the JVM choose in a container?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    G1 only on a "server-class machine", meaning 2 or more CPUs and about 1.8 GB or more; otherwise Serial. Measured: Serial at 1 GB with 2 CPUs and at 4 GB with 1 CPU. Set the collector explicitly for small pods.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q7-which-gc-does-the-jvm-choose-in-a-container-l3-goldman-sachs)

??? question "How do you harden a container?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks

    Run as a non-root user with a read-only root filesystem (a tmpfs for `/tmp`), drop all capabilities, set `no-new-privileges`, and use no shell or package manager in the image. Measured: menu-service ran unchanged under all of these.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q10-how-do-you-harden-a-container-l2l3-banks)

??? question "What does an SBOM miss?"
    <span class="badge badge-l3">L3</span> Razorpay · security rounds

    Anything without package metadata. syft found the Java runtime and jars in the lab's images but not glibc, which was copied in without the package database. A scanner can't flag what the SBOM doesn't list. Use bases that ship metadata (distroless, Buildpacks), and check the SBOM's coverage.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q11-what-does-an-sbom-miss-l3-razorpay-security-rounds)


**[54. Kubernetes for Spring Developers](../deployment/ch54-kubernetes-for-spring.md)**

??? question "Env vars or mounted files for secrets?"
    <span class="badge badge-l3">L3</span> banks

    Mounted files. Env vars are visible in `/proc`, inherited by child processes, printed by debug endpoints and captured in heap dumps (chapter 52), and they never rotate. Mounted Secrets update and can be read on demand.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q6-env-vars-or-mounted-files-for-secrets-l3-banks)

??? question "What makes a JVM pod OOMKilled on Kubernetes?"
    <span class="badge badge-l3">L3</span> PhonePe

    Total process memory above the memory limit: heap plus metaspace, threads, direct buffers and code cache. Use `MaxRAMPercentage=75` with limit = request, and `ExitOnOutOfMemoryError`. Chapter 53 measured `-Xmx` above the limit as a silent kernel kill.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q11-what-makes-a-jvm-pod-oomkilled-on-kubernetes-l3-phonepe)


**[55. Helm, Kustomize & Environment Promotion](../deployment/ch55-helm-kustomize.md)**

??? question "How do you promote a build from staging to production?"
    <span class="badge badge-l3">L3</span> Razorpay · Atlassian

    Build once, push, and refer to the image by digest. Promotion is a change to prod's values or overlay that copies the digest staging ran, checked by policy. Never rebuild for prod and never promote a tag. Measured: after a tag moved, a scale-out ran two versions behind one Service; pinned by digest, every pod ran the same build.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q2-how-do-you-promote-a-build-from-staging-to-production-l3-razorpay-atlassian)

??? question "How do you make pods pick up a ConfigMap change?"
    <span class="badge badge-l2">L2</span> Zomato · Paytm

    Change the pod template when the config changes: a `checksum/config` annotation in Helm, or kustomize's hashed generator names. Measured: both replaced the pods in 5–9 s; without them, no pod restarted and the env var stayed old. Alternatively, a watched mount with code that reloads (chapter 54).

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q5-how-do-you-make-pods-pick-up-a-configmap-change-l2-zomato-paytm)

??? question "Where do secrets go in Helm or kustomize?"
    <span class="badge badge-l2">L2</span> banks

    Not in values files or Git in plain text. Reference secrets managed by an external secrets operator, or keep them encrypted (SOPS, sealed secrets) and decrypted in the cluster. Templates then mount a Secret by name (chapter 39).

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q9-where-do-secrets-go-in-helm-or-kustomize-l2-banks)

??? question "How do you detect drift between Git and the cluster?"
    <span class="badge badge-l3">L3</span> PhonePe

    Diff rendered manifests against live objects (`kubectl diff`, `helm diff`), or run a GitOps controller that reconciles continuously and reports drift. Note what applying doesn't fix: measured, a hand-added environment variable survived every apply method, because no manifest mentioned it.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q11-how-do-you-detect-drift-between-git-and-the-cluster-l3-phonepe)


**[56. Autoscaling](../deployment/ch56-autoscaling.md)**

??? question "When would you use KEDA?"
    <span class="badge badge-l3">L3</span> PhonePe

    For event-driven work: Kafka lag, queue depth, cron windows, or anything that should scale to zero. It feeds external metrics to an HPA it manages. CPU lags a backlog; lag doesn't.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q7-when-would-you-use-keda-l3-phonepe)

??? question "What does `behavior` let you tune?"
    <span class="badge badge-l3">L3</span> Razorpay

    Per direction: a stabilization window, and policies limiting change per period (pods or percent), with `selectPolicy` choosing the most or least permissive. Defaults: up by 4 pods or 100% per 15 s with no window; down by up to 100% per 15 s after a 300 s window.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q8-what-does-behavior-let-you-tune-l3-razorpay)


**[57. Service Mesh](../deployment/ch57-service-mesh.md)**

??? question "How does mTLS in a mesh authenticate a service?"
    <span class="badge badge-l3">L3</span> Razorpay · banks

    Each workload's certificate carries its identity as a SPIFFE URI SAN, issued by the mesh CA and rotated within hours. Both sides verify the chain and the peer's identity. Measured: the right CA with the wrong service account was refused, as was the right name from another CA.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q3-how-does-mtls-in-a-mesh-authenticate-a-service-l3-razorpay-banks)

??? question "How do you roll out strict mTLS without an outage?"
    <span class="badge badge-l3">L3</span> PhonePe

    Enable the mesh in permissive mode (accepting both), watch telemetry for plaintext callers, fix them, then switch namespaces to strict one at a time, with an exception list that has an expiry date.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q11-how-do-you-roll-out-strict-mtls-without-an-outage-l3-phonepe)


**[58. CI/CD, GitOps & Progressive Delivery](../deployment/ch58-cicd-gitops.md)**

??? question "Push-based or pull-based deployment?"
    <span class="badge badge-l3">L3</span> Atlassian · banks

    Pull keeps credentials in the cluster, corrects drift and prunes deletions; push deploys immediately but forgets the cluster between deploys. Most teams combine CI (push to the registry and the environment repo) with GitOps (pull into clusters).

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q2-push-based-or-pull-based-deployment-l3-atlassian-banks)

??? question "Why prune, and why is it off by default in Argo CD?"
    <span class="badge badge-l3">L3</span> PhonePe

    Without pruning, objects deleted from Git live on (measured: pruned in 7–8 s with it). It's off by default because a bad commit or a mis-rendered manifest could delete production objects; teams enable it with protection like sync windows and prune confirmation for critical kinds.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q9-why-prune-and-why-is-it-off-by-default-in-argo-cd-l3-phonepe)

??? question "What signals belong in canary analysis?"
    <span class="badge badge-l3">L3</span> Razorpay

    Per-version request error rate and latency from the edge or mesh, plus business signals where available (checkout conversion). Compare to stable over the same window, require a minimum sample, and keep thresholds tied to SLOs (chapter 51).

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q10-what-signals-belong-in-canary-analysis-l3-razorpay)


**[59. Cloud-Native on AWS, Azure & GCP](../deployment/ch59-cloud-native-platforms.md)**

??? question "How does IRSA or Workload Identity actually work?"
    <span class="badge badge-l3">L3</span> Atlassian · Razorpay

    The cluster is an OIDC issuer that signs tokens and publishes its keys. The cloud is configured to trust that issuer, and a role trusts a subject (`system:serviceaccount:ns:name`) and audience. The cloud verifies tokens offline with the published keys and issues credentials for an hour or so.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q2-how-does-irsa-or-workload-identity-actually-work-l3-atlassian-razorpay)

??? question "Why is the default service account token dangerous outside the cluster?"
    <span class="badge badge-l3">L3</span> banks

    Its audience is the API server, and for compatibility it's valid for a year (measured: 31,536,000 s), though the kubelet refreshes it hourly. Never send it anywhere; mint projected tokens with a specific audience instead.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q4-why-is-the-default-service-account-token-dangerous-outside-the-cluster-l3-banks)

??? question "Functions or containers?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    Functions for spiky, low-volume, event-driven work where cold starts don't matter; containers for steady, latency-sensitive, high-volume services. Measured and priced: in our model functions cost cents a day where two always-on containers cost $2.37, and stayed cheaper than containers sized for the peak even at 1,000 requests a second; only containers scaling minute by minute with the load beat them, between 200 and 1,000 requests a second. A JVM cold start was about 1.4 s.

    [Full answer in chapter 59](../deployment/ch59-cloud-native-platforms.md#q7-functions-or-containers-l3-zomato-phonepe)


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

??? question "Should a pod warm up before reporting ready?"
    <span class="badge badge-l3">L3</span> PhonePe

    Briefly, with a time budget. Measured: a 3,000-request warm-up gave 6–10 ms first requests instead of 27–48 ms, but took 4.3 s at 1 CPU and 17.5 s at 0.25 CPU. Chapter 56 showed the other side: pods that are ready before they're warm over-scale the service.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q9-should-a-pod-warm-up-before-reporting-ready-l3-phonepe)


### Foundations


**[1. Monolith vs SOA vs Microservices vs Modular Monolith](../foundations/ch01-architecture-styles.md)**

??? question "Isn't SOA the same as microservices?"
    <span class="badge badge-l2">L2</span> JP Morgan · SAP · Goldman Sachs

    They share the idea of services, but differ in where logic lives and who governs. SOA used smart pipes (an ESB with routing, transformation and business rules), canonical enterprise models and central governance. Microservices use dumb pipes, bounded contexts and team-owned services.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q4-isnt-soa-the-same-as-microservices-l2-jp-morgan-sap-goldman-sachs)

??? question "When would you not use microservices?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    With few teams, a domain still being discovered, no platform maturity (CI/CD, observability, on-call), heavy cross-entity transactional needs, or uniform load. Start with a modular monolith and extract when a module earns it.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q5-when-would-you-not-use-microservices-l2l3-amazon-goldman-sachs)

??? question "Order and kitchen are separate modules or services. How do you keep them consistent?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan · Flipkart

    Each side commits locally. The producer records the event in the same transaction as its state change (event publication registry or outbox). Consumers process at least once and idempotently. Multi-step business workflows use a saga with compensations.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q7-order-and-kitchen-are-separate-modules-or-services-how-do-you-keep-them-consistent-l3-goldman-sachs-jp-morgan-flipkart)

??? question "You're asked to migrate a 10-year-old banking monolith to microservices. What's your plan?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs · SAP · Cognizant

    Don't rewrite. Modularise in place, establish the platform, then strangle capabilities one at a time, starting at the edges, keeping the core ledger coarse-grained, and measuring outcomes at every step.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q10-youre-asked-to-migrate-a-10-year-old-banking-monolith-to-microservices-whats-your-plan-l4-jp-morgan-goldman-sachs-sap-cognizant)

??? question "How would you justify the cost of microservices to a CFO?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Amazon · SAP

    In business outcomes, not technology. Show the current constraint (lead time, release contention, outage blast radius, scaling cost of one hot feature), the expected improvement in DORA metrics or infrastructure cost, the one-off and recurring costs (platform, people, runtime), and staged checkpoints where you stop if the numbers don't materialise.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q13-how-would-you-justify-the-cost-of-microservices-to-a-cfo-l4-goldman-sachs-amazon-sap)


**[2. Characteristics & Principles](../foundations/ch02-characteristics-principles.md)**

??? question "Explain 'smart endpoints and dumb pipes'."
    <span class="badge badge-l2">L2</span> JP Morgan · SAP

    Business logic lives in services; the transport only moves messages. This avoids the ESB trap, where routing and transformation logic in middleware creates a central bottleneck.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q4-explain-smart-endpoints-and-dumb-pipes-l2-jp-morgan-sap)

??? question "What does 'design for failure' mean in code?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Timeouts on every remote call. Retries only for idempotent operations and transient errors, with backoff and jitter. Bounded concurrency. Circuit breakers. A defined degradation mode per dependency. And tests for each failure path.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q5-what-does-design-for-failure-mean-in-code-l2l3-amazon-goldman-sachs)

??? question "Fail open or fail closed?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    It depends on the cost of being wrong. Enrichments (recommendations, surge display) fail open. Money movement, fraud checks and irreversible actions fail closed into a safe, recoverable state. The choice is documented per dependency.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q7-fail-open-or-fail-closed-l3-goldman-sachs-jp-morgan)

??? question "How does decentralized data management affect reporting?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    You can't join across service databases, so you replicate. Services publish events or CDC streams into an analytics store (warehouse or lake) where reporting joins happen. Operational queries across services use API composition or dedicated read models.

    [Full answer in chapter 2](../foundations/ch02-characteristics-principles.md#q10-how-does-decentralized-data-management-affect-reporting-l3-goldman-sachs-flipkart)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Explain the CAP theorem precisely."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    During a network partition, a replicated system must choose between linearizable consistency and availability. Partition tolerance isn't optional, and the choice only applies while partitioned.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q2-explain-the-cap-theorem-precisely-l2l3-amazon-flipkart-goldman-sachs)

??? question "What is PACELC and why is it more useful day to day?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    If there's a Partition, choose Availability or Consistency; Else, choose Latency or Consistency. Partitions are rare but latency is paid on every request, so the E side drives most design decisions, such as synchronous versus asynchronous replication.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q3-what-is-pacelc-and-why-is-it-more-useful-day-to-day-l3-goldman-sachs-sap)

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

??? question "What is split brain and how do fencing tokens prevent damage?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Two nodes both believe they are leader, for example after a GC pause outlives a lease. A fencing token is a monotonically increasing number issued with each lease. The protected resource rejects writes carrying an older token, so a stale leader's writes fail even if it doesn't know it's stale.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q11-what-is-split-brain-and-how-do-fencing-tokens-prevent-damage-l4-goldman-sachs-sap)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "Explain graceful shutdown on Kubernetes."
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Kubernetes removes the pod from endpoints and sends SIGTERM at about the same time. The app stops accepting new connections, finishes in-flight work within a timeout, releases resources and exits. Add a short `preStop` sleep because endpoint removal is asynchronous, and set `terminationGracePeriodSeconds` higher than the app's shutdown timeout.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q5-explain-graceful-shutdown-on-kubernetes-l2l3-amazon-jp-morgan)

??? question "How do you run database migrations in a twelve-factor way?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · SAP

    Versioned migrations (Flyway or Liquibase) in the codebase, applied by the release: on startup for simple services, or as a pre-deploy Job for large changes. Use expand/contract so old and new versions run against the same schema during rolling deploys. Never apply them by hand.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q6-how-do-you-run-database-migrations-in-a-twelve-factor-way-l3-goldman-sachs-sap)

??? question "What did 'Beyond the Twelve-Factor App' add, and why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    API first (contracts enable parallel teams), telemetry (you can't operate what you can't see), and authentication and authorization (security can't be bolted on in a zero-trust, many-service world). It also reinterpreted some factors for containers and microservices.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q10-what-did-beyond-the-twelve-factor-app-add-and-why-l3l4-sap-goldman-sachs)

??? question "How do you prove to an auditor that production runs the tested artifact?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Build once and deploy by immutable digest. Sign images and attach SBOMs, record each release (digest plus config commit) in Git, enforce signature verification at admission, and show that only configuration differs between UAT and production.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q12-how-do-you-prove-to-an-auditor-that-production-runs-the-tested-artifact-l4-jp-morgan-goldman-sachs)


**[5. Domain-Driven Design](../foundations/ch05-domain-driven-design.md)**

??? question "What is an aggregate and how do you choose its boundary?"
    <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    A cluster of objects treated as one consistency unit, accessed through its root. The boundary comes from **invariants that must hold at every commit**. Keep aggregates small, reference others by id, and use eventual consistency between them.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q3-what-is-an-aggregate-and-how-do-you-choose-its-boundary-l3-amazon-flipkart-goldman-sachs)

??? question "Why only one aggregate per transaction?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    It keeps locks small and transactions short, avoids contention, and keeps each aggregate independently storable and scalable, which is a prerequisite for splitting contexts into services. Changes elsewhere follow via domain events in their own transactions.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q4-why-only-one-aggregate-per-transaction-l3-amazon-jp-morgan)

??? question "What is an anti-corruption layer? When do you need one?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs · SAP

    A translation layer that converts an upstream model into the downstream's own model, protecting the downstream from upstream quirks and changes. Use it with legacy systems, external vendors, volatile upstreams, or whenever the upstream model doesn't fit your domain.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q6-what-is-an-anti-corruption-layer-when-do-you-need-one-l3-jp-morgan-goldman-sachs-sap)

??? question "What's wrong with an anemic domain model?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    The rules live outside the objects, so invalid states are possible and rules get duplicated and drift. It's fine for CRUD-like supporting subdomains, and costly for core domains with real invariants.

    [Full answer in chapter 5](../foundations/ch05-domain-driven-design.md#q11-whats-wrong-with-an-anemic-domain-model-l2l3-goldman-sachs-flipkart)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

??? question "What goes wrong when you decompose by entity?"
    <span class="badge badge-l3">L3</span> Flipkart · Amazon · Goldman Sachs

    Business operations span many services, so use cases become long synchronous call chains: more latency, lower availability (0.999<sup>11</sup> ≈ 98.9% for 11 services on a path), and invariants that used to be one transaction now need sagas. Features need coordinated changes across services. It's a distributed monolith.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q5-what-goes-wrong-when-you-decompose-by-entity-l3-flipkart-amazon-goldman-sachs)

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


**[7. Conway's Law & Team Topologies](../foundations/ch07-conways-law-team-topologies.md)**

??? question "Explain the four team types in Team Topologies."
    <span class="badge badge-l3">L3</span> SAP · Amazon · JP Morgan

    **Stream-aligned** (owns a stream of change end to end; the default), **platform** (self-service internal services that reduce others' load), **complicated-subsystem** (owns something needing rare specialist skills), **enabling** (coaches teams in a capability, then leaves). Most teams should be stream-aligned; the others exist to make stream-aligned teams fast.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q3-explain-the-four-team-types-in-team-topologies-l3-sap-amazon-jp-morgan)

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

??? question "Who should own the shared database during a migration to microservices?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> JP Morgan · SAP

    In the end, nobody: each service owns its data. During migration, assign each table to the team owning the capability that writes it, even while it physically lives in the shared database; other teams read through APIs or views, and DBA expertise moves to an enabling or platform role. That is the first step of database per service.

    [Full answer in chapter 7](../foundations/ch07-conways-law-team-topologies.md#q11-who-should-own-the-shared-database-during-a-migration-to-microservices-l3l4-jp-morgan-sap)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "How do virtual threads work, and when should you not use them?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Amazon · Flipkart · Goldman Sachs

    They're JVM-scheduled threads that unmount from a carrier platform thread when they block on I/O, so blocking code scales to many concurrent requests. They don't help CPU-bound work. Don't pool them, avoid heavy `ThreadLocal` caches, and put explicit limits on shared resources. On Java 21–23, long `synchronized` blocks around I/O pin the carrier; Java 24 fixed that (JEP 491).

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q2-how-do-virtual-threads-work-and-when-should-you-not-use-them-l2l3-amazon-flipkart-goldman-sachs)

??? question "Virtual threads or WebFlux for a new service?"
    <span class="badge badge-l3">L3</span> Flipkart · JP Morgan

    For request/response services that call databases and other services, Spring MVC with virtual threads: simpler code, same scalability for I/O-bound work, every blocking library works. WebFlux when you need streaming, back-pressure, or a fully non-blocking gateway.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q3-virtual-threads-or-webflux-for-a-new-service-l3-flipkart-jp-morgan)

??? question "What changed in Jackson 3 that can break a running system?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    New packages (`tools.jackson`), immutable `JsonMapper`, unchecked exceptions, and new defaults: ISO-8601 dates instead of timestamps, errors for `null` into primitives and for trailing tokens, alphabetical property order, enums read and written with `toString()`. Each can change your API's output or reject input that used to work. Pin output with contract tests and switch defaults deliberately.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q5-what-changed-in-jackson-3-that-can-break-a-running-system-l3-goldman-sachs-jp-morgan)

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

??? question "How do you deprecate an endpoint safely?"
    <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Mark it in the document (`deprecated`, `x-sunset`), serve `Deprecation`, `Sunset` and `Link` headers, have client libraries warn, track usage by caller, contact the remaining callers, brown out near the date, and remove only when usage is zero. Measured: two of four callers saw nothing because they used plain HTTP.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q5-how-do-you-deprecate-an-endpoint-safely-l3-flipkart-razorpay)


**[73. ADRs, Fitness Functions & Tech Radar](../governance-org/ch73-adrs-fitness-functions.md)**

??? question "Is adding a field to a Java record in an API a breaking change?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    For anyone who constructs it, yes: the canonical constructor's signature changes and the old one is gone. Readers are unaffected. A compatibility function over bytecode reported exactly that removed constructor.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q10-is-adding-a-field-to-a-java-record-in-an-api-a-breaking-change-l3-goldman-sachs)


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "What's wrong with a shared library in a migration?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    Nothing, if it depends on no business code. Broadleaf's platform imported order, payment, customer and checkout 60 times; as a library, that would make every service depend on them. Invert those edges first.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q8-whats-wrong-with-a-shared-library-in-a-migration-l3-goldman-sachs)


**[75. Multi-Tenancy, Multi-Region & Compliance](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)**

??? question "How do you honour the right to erasure with backups?"
    <span class="badge badge-l4">L4</span> Goldman Sachs

    Crypto-shredding: per-subject keys, erasure by deleting the key, which makes every copy of the ciphertext unreadable. Measured: after DELETE and VACUUM FULL the email survived in WAL and backup; encrypted, it never reached disk.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q8-how-do-you-honour-the-right-to-erasure-with-backups-l4-goldman-sachs)

??? question "What does crypto-shredding not solve?"
    <span class="badge badge-l4">L4</span> JP Morgan

    The key store becomes the copy to worry about: the deleted key was still in its WAL. Use a KMS or a key store with short retention. It also makes value search harder (blind indexes) and puts the KMS on the read path.

    [Full answer in chapter 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#q9-what-does-crypto-shredding-not-solve-l4-jp-morgan)


**[77. AI-Native Microservices](../governance-org/ch77-ai-native-microservices.md)**

??? question "How do you secure an MCP server?"
    <span class="badge badge-l4">L4</span> Goldman Sachs

    As an API: OAuth with audience-checked tokens, identity from the token, object-level authorization in every tool, header and body validation, audit logs, and injection tests. A tool trusting a `customer_id` argument returned another customer's order 30 times out of 30.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q8-how-do-you-secure-an-mcp-server-l4-goldman-sachs)


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

??? question "What does instrumentation cost?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    CPU, memory, startup time and network, plus storage downstream. Measured on three thin services: the Java agent added 38–56% CPU per order (25–36% with nothing exported), 110–125 MB per JVM and 13–15 s of startup; JFR 4–7% CPU. Measure it per service, and budget it like any other dependency.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q9-what-does-instrumentation-cost-l3-flipkart-goldman-sachs)

??? question "What should never go into telemetry?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks · PayPal

    Secrets, credentials, card numbers and other personal data, in logs, span attributes, metric labels and resource attributes alike. Measured: the Java agent's default resource includes the JVM command line and masks only properties named *password* or *secret*; a token passed with `-D` appeared in spans and in Prometheus labels. Redact at the source and again in the collector.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q11-what-should-never-go-into-telemetry-l2l3-banks-paypal)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "publishPercentiles or publishPercentileHistogram?"
    <span class="badge badge-l3">L3</span> Atlassian · PhonePe

    Histograms, for anything you aggregate or alert on: buckets add up across pods and time windows, and any percentile can be computed later. Client-side percentiles are fixed numbers per process. In Micrometer's Prometheus registry, enabling the histogram drops the client-side percentiles from the output anyway.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q3-publishpercentiles-or-publishpercentilehistogram-l3-atlassian-phonepe)

??? question "What does max-uri-tags do?"
    <span class="badge badge-l3">L3</span> Razorpay · Uber

    It caps the distinct uri values per meter name (100 by default), first come, first served: once full, new values are denied, with one warning. It stops the explosion but can hide new endpoints; fix the source of values and alert on the warning.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q7-what-does-max-uri-tags-do-l3-razorpay-uber)

??? question "What does recording a metric cost?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    Tens of nanoseconds for a counter or plain timer, about a hundred with a histogram; client-side percentiles and Observations cost more, especially under contention. Measured: 12–13 ns for a counter, 51–52 ns for a timer, 84–97 ns with a histogram, about 200 ns for client-side percentiles (1.4–1.5 µs contended), 0.7–1.3 µs for an Observation. Register meters once; turn off long-task timers you don't use.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q9-what-does-recording-a-metric-cost-l3-goldman-sachs-flipkart)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "How does a trace continue across threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · PhonePe

    The context is bound to the thread; it must be captured when work is handed over and restored when it runs. The agent does this for JDK executors and async callbacks; bare threads and own queues need `Context.wrap`. Measured: executors, `CompletableFuture`, scheduled and virtual-thread executors kept the trace 200 of 200 times; `new Thread()`, `Thread.ofVirtual().start()` and a hand-rolled queue lost it 200 of 200.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q2-how-does-a-trace-continue-across-threads-l2l3-swiggy-phonepe)

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


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "What is the MDC, and what goes wrong with it?"
    <span class="badge badge-l2">L2</span> Swiggy · PhonePe

    It is a per-thread map that Logback copies into every event. It doesn't follow work to other threads, and values left on pooled threads mislabel later work. Measured: request IDs were missing on 200 of 200 lines from executors and new threads, and wrong on 99 of 200 after a task leaked one.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q2-what-is-the-mdc-and-what-goes-wrong-with-it-l2-swiggy-phonepe)

??? question "How do you correlate logs across services and threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Razorpay

    Put the trace ID on every line, from Micrometer Tracing or the OpenTelemetry agent. Trace context is propagated across HTTP, messaging and executors, so it follows the work. Measured: the agent's `trace_id` was right on every executor hop, including a thread with a leaked MDC. It was missing only on bare threads.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q3-how-do-you-correlate-logs-across-services-and-threads-l2l3-flipkart-razorpay)

??? question "How do you keep personal data out of logs?"
    <span class="badge badge-l3">L3</span> Paytm · banks

    Use layers: don't log what you don't need; use types that print masked (`Pii`); put a masking encoder over every finished line; and run a CI test that pushes fake PII through every path. Measured: source masking alone leaked from concatenated messages, MDC values and exception messages; the line-level encoder leaked nothing.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q6-how-do-you-keep-personal-data-out-of-logs-l3-paytm-banks)

??? question "Why not mask with regexes?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Atlassian

    It is slow, and it can be wrong. Six regexes cost 69–77 µs per 500-character line against 3.8–12.6 µs for a single-pass scanner. They also masked epoch-millisecond timestamps as card numbers, because 13-digit numbers often pass Luhn. Validate candidates (length, card prefix, Luhn) and scan once.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q7-why-not-mask-with-regexes-l3-goldman-sachs-atlassian)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "What is an error budget, and what is it for?"
    <span class="badge badge-l3">L3</span> Swiggy · Razorpay

    1 − SLO: the failures you can afford, 43.2 minutes a month at 99.9%. It turns reliability into a shared currency. While there is budget, ship. When it is running out, slow down and fix reliability. The error budget policy writes that down in advance, agreed with product.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q2-what-is-an-error-budget-and-what-is-it-for-l3-swiggy-razorpay)

??? question "What is a burn rate?"
    <span class="badge badge-l3">L3</span> Flipkart · PhonePe

    The error ratio divided by the budget: how many times faster than "exactly on budget" you are failing. Burn rate 1 spends the budget in the window; 14.4 spends 2% of a 30-day budget in an hour.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q3-what-is-a-burn-rate-l3-flipkart-phonepe)

??? question "How do you alert for a low-traffic service?"
    <span class="badge badge-l3">L3</span> PayPal · banks

    One failure is a big ratio, so burn-rate alerts page per failure: 16 pages in 30 days here, on a healthy service. Require a minimum number of events in the long window, lengthen the window, add synthetic probes, or combine small services into one SLO.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q7-how-do-you-alert-for-a-low-traffic-service-l3-paypal-banks)

??? question "What goes in an error budget policy?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · Atlassian

    Who owns the SLO, and the thresholds that change behaviour: for example caution at half spent (rollback plans, extra review) and a freeze at fully spent (only fixes and reliability work). It also covers who can grant exceptions, how incidents that spend more than a set share get a postmortem, and that product signs it.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q10-what-goes-in-an-error-budget-policy-l4-goldman-sachs-atlassian)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "And readiness?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> PhonePe · Razorpay

    Not for dependencies every replica shares: they all go not-ready together and the Service has no endpoints. Measured: new connections refused for the whole outage and 10–12 seconds after it. Answer 503 fast from the endpoints that need the dependency instead. Readiness on a dependency makes sense only when it is per-pod.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q3-and-readiness-l2l3-phonepe-razorpay)

??? question "How do you secure Actuator?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> banks · Goldman Sachs

    

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q7-how-do-you-secure-actuator-l2l3-banks-goldman-sachs)


### Performance & Scalability


**[66. Latency, Tail Latency, Virtual Threads vs Reactive](../performance-scalability/ch66-latency-virtual-threads-reactive.md)**

??? question "How do you build a latency budget?"
    <span class="badge badge-l3">L3</span> Razorpay

    Start from the objective (the quote's p99 under 500 ms), subtract what the calls need (three sequential 50 ms calls: 150 ms), and give each hop a timeout from what's left. Parallelise independent calls: the budget then pays for the slowest, not the sum.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q8-how-do-you-build-a-latency-budget-l3-razorpay)

??? question "Your service on virtual threads ran out of memory under load. Why?"
    <span class="badge badge-l3">L3</span> PhonePe

    Nothing limited how many requests waited, so each one's stack and buffers stayed on the heap until the heap filled. Measured: unbounded virtual threads peaked at 640 MB with a 512 MB heap at 2,500/s; the event loop ran out of memory. Limit in-progress requests and time out calls.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q10-your-service-on-virtual-threads-ran-out-of-memory-under-load-why-l3-phonepe)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "Is JFR safe to run in production?"
    <span class="badge badge-l2">L2</span> PhonePe

    Yes; it was designed for that. Measured: a continuous recording with `default` settings cost 2–7% more CPU and `profile` 5–9%, with latency changes within noise. Keep it on, bounded by `maxsize`/`maxage`, and dump it when something goes wrong.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q9-is-jfr-safe-to-run-in-production-l2-phonepe)

??? question "Should you set CPU limits on JVM containers?"
    <span class="badge badge-l3">L3</span> Razorpay

    Whatever the limit, the JVM sizes itself from it: GC threads, JIT threads, the ForkJoin pool. A limit of 1 means one GC thread and, for ZGC, competition with the application. If you set limits, set them to at least 2 for G1 or ZGC, or tell the JVM explicitly with `-XX:ActiveProcessorCount`.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q11-should-you-set-cpu-limits-on-jvm-containers-l3-razorpay)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "How does Kafka assign a keyed record to a partition, and what happens when you add partitions?"
    <span class="badge badge-l3">L3</span> Confluent · PhonePe

    `murmur2(key) mod partitions`. Adding partitions changes the modulus, so most keys move (measured for mod n: 80–91%), and per-key ordering isn't kept across the change. Size partition counts for growth from the start.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q8-how-does-kafka-assign-a-keyed-record-to-a-partition-and-what-happens-when-you-add-partitions-l3-confluent-phonepe)


**[69. High-Throughput Kafka Tuning](../performance-scalability/ch69-kafka-throughput-tuning.md)**

??? question "Is max.in.flight.requests.per.connection=1 needed for ordering?"
    <span class="badge badge-l3">L3</span> PhonePe

    No, not with idempotence (the default since 3.0), which keeps order with up to 5. One in flight costs throughput whenever there's latency: measured over a 1 ms round trip, 268,000–269,000 records a second against 641,000–674,000.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q4-is-maxinflightrequestsperconnection1-needed-for-ordering-l3-phonepe)

??? question "How should a consumer write to a database?"
    <span class="badge badge-l3">L3</span> Razorpay

    A poll per transaction with batched inserts, an idempotent key, and offsets committed afterwards. Measured on PostgreSQL: 1,384–1,554 rows a second with a commit per record, 29,000–34,000 with JDBC batching, 45,000–46,000 with `reWriteBatchedInserts=true`, and 55,000–59,000 with COPY in polls of 5,000.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q7-how-should-a-consumer-write-to-a-database-l3-razorpay)

??? question "At-least-once, at-most-once or exactly-once?"
    <span class="badge badge-l3">L3</span> Paytm

    At-least-once (commit after processing) with an idempotent sink is the usual choice; at-most-once loses events on crashes; exactly-once needs Kafka transactions end to end, or a sink that stores offsets with the data. Measured: replaying a fifth of the events left every sink's row count unchanged.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q8-at-least-once-at-most-once-or-exactly-once-l3-paytm)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "Should you set CPU limits?"
    <span class="badge badge-l3">L3</span> Atlassian · Razorpay

    Not on latency-sensitive services. A limit throttles bursts even when the node is idle. Measured: a limit at VPA's target (0.2 CPU) raised p99 from 5 ms to 78–204 ms; the same request without a limit kept 4.6–5.3 ms. Set requests well and keep memory limits.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q4-should-you-set-cpu-limits-l3-atlassian-razorpay)

??? question "When is spot capacity safe?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    For work that can lose copies without losing users: stateless services above an on-demand base that carries the load alone, batch jobs with checkpoints, CI. Measured: all-spot served 0–1% of requests during a reclaim; 3 on-demand + 3 spot served 55–61% because the remaining copies were overloaded.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q7-when-is-spot-capacity-safe-l3-zomato-phonepe)


### Resilience


**[42. Failure Modes & Cascading Failures](../resilience/ch42-failure-modes.md)**

??? question "Why are retries dangerous?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    They multiply load exactly when a dependency is struggling, and they multiply across layers: three attempts at three layers sent 27 calls per user request to a dead service (measured). Retry at one layer or within a budget (10% sent 1.39), only idempotent operations, with jittered backoff.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q3-why-are-retries-dangerous-l3-amazon-goldman-sachs)

??? question "What is deadline propagation?"
    <span class="badge badge-l3">L3</span> Google-style rounds · JP Morgan

    Sending the absolute time the original caller stops waiting on every hop (gRPC does it natively), so each service refuses or abandons work that can't finish in time and never extends the budget. It removes wasted work deep in call chains.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q7-what-is-deadline-propagation-l3-google-style-rounds-jp-morgan)

??? question "What changes with virtual threads?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    The thread pool used to be an accidental bulkhead: 200 Tomcat threads capped concurrent work. With `spring.threads.virtual.enabled`, each request gets a virtual thread and that cap disappears, so a slow dependency can accumulate unbounded concurrent requests. Add explicit limits (`@ConcurrencyLimit(REJECT)`, connection pool sizes, semaphores) and deadlines.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q11-what-changes-with-virtual-threads-l3-sap-jp-morgan)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "In what order do Resilience4j's annotations apply?"
    <span class="badge badge-l3">L3</span> SAP · JP Morgan

    Retry outermost, then CircuitBreaker, RateLimiter, TimeLimiter, Bulkhead innermost, regardless of the order written (configurable with `*-aspect-order`). So each attempt is counted by the breaker and needs a bulkhead permit, and the retry must ignore the breaker's `CallNotPermittedException`, or an open breaker costs every user the retry delays (1 s, measured).

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q4-in-what-order-do-resilience4js-annotations-apply-l3-sap-jp-morgan)

??? question "Does a TimeLimiter stop the slow call?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Atlassian

    It stops waiting. `cancel(true)` on a `CompletableFuture` doesn't interrupt anything: 40 of 40 tasks ran on (measured). An `ExecutorService` future is interrupted, which stops sleeps and the JDK `HttpClient`, not `HttpURLConnection` reads or CPU loops. The timeout that stops work belongs in the client: read timeouts, query timeouts.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q6-does-a-timelimiter-stop-the-slow-call-l3-goldman-sachs-atlassian)

??? question "What's wrong with the default RateLimiter?"
    <span class="badge badge-l3">L3</span> Razorpay · PhonePe

    50 permits every 500 ns is 100 million a second: it allowed about 11 million calls a second on two cores. Its 5 s wait turns over-limit traffic into a queue (over 900 callers waiting, measured). And fixed periods allow twice the limit across a period boundary: use short periods or a token bucket where bursts matter.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q10-whats-wrong-with-the-default-ratelimiter-l3-razorpay-phonepe)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "Why not just retry with a short timeout?"
    <span class="badge badge-l3">L3</span> Flipkart · Goldman Sachs

    The abandoned attempt keeps running, so a short timeout multiplies load exactly when the dependency is slow. At 85% load, 100 ms timeouts with retries sent 5.6–8.2 calls per user and served 20–49% (measured); a 300 ms timeout that only fired on real stalls served 100%.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q4-why-not-just-retry-with-a-short-timeout-l3-flipkart-goldman-sachs)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

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

??? question "Why is asynchronous replication dangerous during a partition?"
    <span class="badge badge-l4">L4</span> Amazon · Goldman Sachs

    The primary keeps accepting commits it can't replicate, so the loss grows with the partition's length instead of being milliseconds. Measured: 2 s of partition lost every commit in those 2 s. Promoting the standby without fencing the old primary then creates two histories.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q4-why-is-asynchronous-replication-dangerous-during-a-partition-l4-amazon-goldman-sachs)

??? question "How long does a failover take, and where does the time go?"
    <span class="badge badge-l3">L3</span> Swiggy · PhonePe

    Detection (probe interval × threshold), fencing, promotion, and clients finding the new primary. Measured: promotion took 0.1 s; detection 3.1 s with 1 s probes and 30.1 s with 10 s probes; clients asking each host for its role resumed 0.3 s after promotion, clients following a 30 s DNS TTL after 24–33 s. Tune probes and clients before the database.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q6-how-long-does-a-failover-take-and-where-does-the-time-go-l3-swiggy-phonepe)


### Security


**[35. Security Fundamentals & Threat Modeling](../security/ch35-security-fundamentals.md)**

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

??? question "How do you keep a threat model up to date?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Keep it as code or structured data next to the system, change it in the same pull requests as the architecture, and check it in the build: no open threats, acceptances with owners and expiry dates, mitigations linked to tests or configuration. Feed incidents and pen-test findings back into it.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q9-how-do-you-keep-a-threat-model-up-to-date-l4-jp-morgan-goldman-sachs)

??? question "How do you test authorization thoroughly?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · SAP

    Beyond example tests, generate the full matrix of roles × actions × object states and compare the policy against rules written separately, in the business's words. Measured here: 18,600 decisions, 0 disagreements. Include awkward identities (one person with two roles) and every state.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q11-how-do-you-test-authorization-thoroughly-l3l4-goldman-sachs-sap)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

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


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "Where do object-level checks go in Spring?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    In the service, with `@EnableMethodSecurity` and `@PreAuthorize("@orderAccess.canRead(authentication, #id)")`, the bean loading the object and applying plain-Java rules. URL rules can't know who owns an object. Answer 404 for denied reads.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q4-where-do-object-level-checks-go-in-spring-l3-amazon-goldman-sachs)

??? question "What defaults of Spring's authorization server would you change?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Access tokens addressed to the client (set audiences per API), refresh tokens reused (rotate them for public clients), no `typ: at+jwt` (add it), and token exchange scopes checked only against the client's registration (register minimal scopes). Read from the 7.1.1 sources in this chapter.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q7-what-defaults-of-springs-authorization-server-would-you-change-l3l4-goldman-sachs-jp-morgan)

??? question "How do you handle the 'authorization server is down' case?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    JWT resource servers keep working with cached JWKS until tokens expire; new logins and refreshes fail. Make the identity provider highly available, keep JWKS caching sensible, use short tokens, and alert on token errors.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q11-how-do-you-handle-the-authorization-server-is-down-case-l3-jp-morgan-flipkart)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "Why isn't a valid client certificate enough to allow a call?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Flipkart

    It proves identity, not permission. Every workload in the trust domain has a valid certificate; the service must decide which of them may call which route (peer policy, deny by default), and the token decides what may be done for which user.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q3-why-isnt-a-valid-client-certificate-enough-to-allow-a-call-l3-goldman-sachs-flipkart)

??? question "How do you rotate certificates without downtime?"
    <span class="badge badge-l3">L3</span> Amazon · JP Morgan

    Short lifetimes, rotation at about half-life, on both servers and clients (an `SSLContext` built once won't rotate), and CA rollovers with an overlap where bundles trust old and new CAs until every old SVID has expired. Measured: 0 failures with rotation, 77–78 failures on a CA switch without overlap.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q4-how-do-you-rotate-certificates-without-downtime-l3-amazon-jp-morgan)

??? question "A workload's certificate expired. Can it still call you?"
    <span class="badge badge-l4">L4</span> Goldman Sachs · JP Morgan

    Possibly. Certificates are checked at the handshake; a kept connection carries on, and a resumed TLS session carries the original certificate without calling the trust manager. On JDK 21 a client kept resuming past expiry (19 of 19 accepted). Check validity per request and cap connection age.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q5-a-workloads-certificate-expired-can-it-still-call-you-l4-goldman-sachs-jp-morgan)

??? question "Client credentials vs token exchange between services?"
    <span class="badge badge-l3">L3</span> Amazon · Goldman Sachs

    Client credentials when the service acts for itself (sagas, jobs): one cached token per service. Token exchange when it acts for a user: a narrower token for the downstream audience carrying the user (chapter 36). Mutual TLS identifies the workload in both cases.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q8-client-credentials-vs-token-exchange-between-services-l3-amazon-goldman-sachs)

??? question "What is a certificate-bound access token?"
    <span class="badge badge-l3">L3</span> JP Morgan · Goldman Sachs

    RFC 8705: the token carries the SHA-256 thumbprint of the client's certificate (`cnf.x5t#S256`), and the resource server accepts it only over a connection authenticated with that certificate. A stolen token is useless without the key. Spring Security validates it by default when the claim is present, but only where the application sees the client certificate.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q10-what-is-a-certificate-bound-access-token-l3-jp-morgan-goldman-sachs)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "Static vs dynamic secrets?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · JP Morgan

    Static secrets are created once and must be rotated deliberately; dynamic secrets are created per client on request with a lease, and expire or are revoked automatically. Dynamic database credentials give each pod its own role, so one leak is one role, and revoking it is one call. Measured: issuing took about 15 ms.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q4-static-vs-dynamic-secrets-l3-goldman-sachs-jp-morgan)

??? question "Your Vault-backed service fails a day after deployment. Why?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Dynamic credentials renewed but never rotated: renewal stops at max_ttl. Spring Cloud Vault 5.0.2 requests database credentials as renewable secrets. Request a rotating secret and update the pool (HikariCP 7's credentials provider), or set max_ttl longer than any pod lives. Measured: renew-only failed exactly at max_ttl.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q6-your-vault-backed-service-fails-a-day-after-deployment-why-l3l4-sap-goldman-sachs)

??? question "You rotated a leaked password. Is the attacker out?"
    <span class="badge badge-l4">L4</span> JP Morgan · Goldman Sachs

    Not necessarily. PostgreSQL (like most databases) checks credentials at login; open sessions continue. Measured: after a password change, expiry or `NOLOGIN`, the open session still worked. Terminate sessions (`pg_terminate_backend`) as part of revocation, and use per-pod credentials so you know which to terminate.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q7-you-rotated-a-leaked-password-is-the-attacker-out-l4-jp-morgan-goldman-sachs)

??? question "What happens when Vault/OpenBao is down?"
    <span class="badge badge-l3">L3</span> JP Morgan · Flipkart

    Running services keep working with their current credentials until their leases end; new pods can't start and renewals fail. Run it highly available with auto-unseal, alert on renewal failures, size lease TTLs to cover a realistic outage, and test the failure.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q11-what-happens-when-vaultopenbao-is-down-l3-jp-morgan-flipkart)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "One tenant is hurting all the others. What do you do?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Swiggy

    Limits keyed by tenant (and client within a tenant) in front of the global one, then per-tenant resource limits further down (connection pools, queues, database statement timeouts). Measured: with only a global limit the other tenants got 0.2% of their requests; with per-tenant limits, 100%.

    [Full answer in chapter 40](../security/ch40-api-security.md#q4-one-tenant-is-hurting-all-the-others-what-do-you-do-l3l4-goldman-sachs-swiggy)

??? question "What is ReDoS, and is Java safe from it?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    A regex that backtracks exponentially or polynomially on crafted input. JDK 9+ memoises failed positions for greedy loops, so the textbook `(\w+\s?)*` is fast, but the protection is off for patterns with backreferences and doesn't cover lazy loops or polynomial patterns. Measured: a lazy or backreferencing variant took over 10 s for 28 characters; a trailing-whitespace pattern 9.5 s for 20,000 spaces. Limit length first, avoid nested quantifiers, use RE2 for untrusted patterns.

    [Full answer in chapter 40](../security/ch40-api-security.md#q6-what-is-redos-and-is-java-safe-from-it-l3-jp-morgan-amazon)

??? question "WAF: worth it? How do you run it?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Accenture

    As one layer against generic attacks and as a fast virtual patch (Log4Shell), yes, and PCI DSS 4.0 requires one for public-facing payment applications. Tune it: as shipped, the CRS blocked 15% of FoodFleet's legitimate requests at PL1 and 49% at PL2 (measured), mostly methods and free text. Run detection-only first, write narrow exclusions, and don't expect it to catch BOLA or business-logic abuse (0 of 6 caught).

    [Full answer in chapter 40](../security/ch40-api-security.md#q10-waf-worth-it-how-do-you-run-it-l3-goldman-sachs-accenture)

??? question "How do you isolate tenants in a shared database?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> SAP · Goldman Sachs

    Tenant from the token, never from the request; PostgreSQL row-level security on every shared table, `FORCE`d, with an application role that doesn't own the tables; the tenant set transaction-locally (`set_config(.., true)`) so pooled connections don't carry it. Measured: a forgotten predicate returned 40 rows instead of 2,000; a session-level `SET` leaked 4,000 rows to the next request. Silo databases for tenants that need them.

    [Full answer in chapter 40](../security/ch40-api-security.md#q11-how-do-you-isolate-tenants-in-a-shared-database-l3l4-sap-goldman-sachs)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "How does a vulnerability scanner work, and why do two scanners disagree?"
    <span class="badge badge-l3">L3</span> Goldman Sachs · Amazon

    Inventory × advisory database × version matching. They differ in how they identify components (exact purls vs guessed CPEs: Syft guessed coordinates for 8–15% of jars), which databases they use, and how they compare versions (Maven's `2.0-rc1` < `2.0` < `2.0-sp1`; strings put `2.10` before `2.9`).

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q3-how-does-a-vulnerability-scanner-work-and-why-do-two-scanners-disagree-l3-goldman-sachs-amazon)

??? question "What does signing an image prove?"
    <span class="badge badge-l3">L3</span> JP Morgan · Amazon

    That someone holding a key (or a short-lived Sigstore certificate for an identity) signed this digest. It says nothing until you pin *who*: with `--certificate-identity-regexp '.*'` a file from the wrong project verified (measured). With a leaked key, a laptop rebuild verified; provenance naming builder and source refused it.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q5-what-does-signing-an-image-prove-l3-jp-morgan-amazon)

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


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "Why not test the repository on H2?"
    <span class="badge badge-l2">L2</span> Razorpay

    Because it isn't the database production runs. Measured: H2 2.5 in PostgreSQL mode couldn't parse the service's `ON CONFLICT (key) DO NOTHING`, failed 3 of 4 tests on correct code, and caught none of 20 bugs. PostgreSQL in a container caught 6 (chapter 62 makes it fast).

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q6-why-not-test-the-repository-on-h2-l2-razorpay)

??? question "Your coverage is 90%. Is the suite good?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    Not necessarily. Coverage says code ran, not that anything was checked. Measured: R7 (rows returned in no defined order) ran under several tests and escaped all of them because they asserted only counts. Mutation analysis finds those gaps.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q8-your-coverage-is-90-is-the-suite-good-l3-goldman-sachs)

??? question "How do you keep a growing test suite fast?"
    <span class="badge badge-l2">L2</span> PhonePe

    Tag suites and run them in stages; keep unit tests in memory; share expensive fixtures such as one database container per run (chapter 62); run in parallel; delete tests that never catch anything and duplicate others.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q9-how-do-you-keep-a-growing-test-suite-fast-l2-phonepe)


**[62. Unit, Slice & Testcontainers Tests](../testing/ch62-unit-slice-integration-tests.md)**

??? question "Why Testcontainers instead of H2?"
    <span class="badge badge-l2">L2</span> Razorpay · Swiggy

    Because H2 isn't the database you run. Measured: H2 2.5 in PostgreSQL mode failed 8 of 19 PostgreSQL features, including `ON CONFLICT (…)`, upserts, `jsonb`, arrays, partial indexes and `RETURNING`, and it hid most of a race that PostgreSQL showed.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q3-why-testcontainers-instead-of-h2-l2-razorpay-swiggy)

??? question "Your `@Transactional` tests pass alone and fail together. Why?"
    <span class="badge badge-l3">L3</span> Goldman Sachs

    Something commits outside the test's transaction (a `REQUIRES_NEW`, an async method, another DataSource, a message listener) and the rollback doesn't undo it. Measured: with the code committing on its own connection, 299 of 300 tests failed on the previous test's row. Truncate between tests instead.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q6-your-transactional-tests-pass-alone-and-fail-together-why-l3-goldman-sachs)

??? question "How do you test a 'once per customer' rule?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    With concurrent callers on the real database, asserting exactly one winner and no errors. Measured: check-then-insert passed every single-threaded test and raised 421–449 errors in 300 concurrent rounds; without the key, 172–186 rounds redeemed twice. `ON CONFLICT` had neither.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q8-how-do-you-test-a-once-per-customer-rule-l3-zomato-phonepe)


**[63. Contract Testing](../testing/ch63-contract-testing.md)**

??? question "Why not just diff the OpenAPI document?"
    <span class="badge badge-l3">L3</span> Razorpay

    Because it judges the schema, not the consumers. Measured on twelve changes: the diff caught 5 of 9 real breaks with 2 false alarms, missed a change of units entirely, and rated two real breaks as INFO. But it was the only check that caught a new enum value, so run it too, for review.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q4-why-not-just-diff-the-openapi-document-l3-razorpay)

??? question "How do you change the meaning of a field safely?"
    <span class="badge badge-l3">L3</span> PhonePe

    Don't. Add a new field (`pricePaise`), migrate consumers, then remove the old one when no contract uses it. Measured: prices moved to paise passed the schema diff and type-only contracts, and only formats and real values caught it.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q10-how-do-you-change-the-meaning-of-a-field-safely-l3-phonepe)


**[64. Component & End-to-End Testing](../testing/ch64-component-e2e-testing.md)**

??? question "What does a preview environment cost?"
    <span class="badge badge-l3">L3</span> PhonePe

    Seconds to create and delete (measured: about 7 s and 11 s for four services), plus node capacity while it runs: with many open pull requests, enough nodes, or scale-to-zero for idle previews. Seed data and secrets per environment are the operational work.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q10-what-does-a-preview-environment-cost-l3-phonepe)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "What makes a good steady-state hypothesis?"
    <span class="badge badge-l3">L3</span> Razorpay

    User-visible measures taken from the SLOs, errors and latency, per short window. Measured: with no timeout, every order succeeded while half took 2 s; an errors-only hypothesis would have passed.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q6-what-makes-a-good-steady-state-hypothesis-l3-razorpay)

??? question "How do you keep a chaos experiment from becoming an incident?"
    <span class="badge badge-l3">L3</span> PhonePe

    Abort conditions checked automatically (here: below 90% success in a window removes the fault), a small blast radius (staging, then a fraction of traffic), an owner watching, and a tested rollback. Measured: the abort ended bad experiments 7 s after they started.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q7-how-do-you-keep-a-chaos-experiment-from-becoming-an-incident-l3-phonepe)
