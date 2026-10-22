### Foundations

| Ch. | FoodFleet's decision |
|---|---|
| [1](../foundations/ch01-architecture-styles.md) | Start FoodFleet as a modular monolith with extraction seams |
| [2](../foundations/ch02-characteristics-principles.md) | Every synchronous dependency declares a degradation mode |
| [3](../foundations/ch03-distributed-systems-fundamentals.md) | Consistency is chosen per data type, and money is never eventually consistent |
| [4](../foundations/ch04-twelve-factor.md) | Immutable images, promoted by digest; all variation through configuration |
| [5](../foundations/ch05-domain-driven-design.md) | Ordering protects its model with an anti-corruption layer towards Catalog |
| [6](../foundations/ch06-service-decomposition.md) | FoodFleet decomposes by business capability, verified by scoring and change coupling |
| [7](../foundations/ch07-conways-law-team-topologies.md) | FoodFleet organises around streams of change (Team Topologies) |
| [8](../foundations/ch08-spring-boot-4-essentials.md) | Spring Boot 4.1 baseline for all FoodFleet services |

### Architecture & Patterns

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Business logic tangled with frameworks and providers | Hexagonal / onion architecture | [9](../architecture-patterns/ch09-hexagonal-clean-architecture.md) | Swapping a payment provider touches one adapter; ArchUnit guards the rule |
| Clients calling many services; cross-cutting edge concerns | API gateway, BFF | [10](../architecture-patterns/ch10-api-gateway-bff.md) | One call per screen instead of four sequential ones; rate limiting at the edge |
| Finding healthy instances as they come and go | Service discovery | [11](../architecture-patterns/ch11-service-discovery-patterns.md) | Errors from stale registries vs Kubernetes Services |
| Services sharing tables | Database per service, API composition | [12](../architecture-patterns/ch12-database-per-service.md) | An ownership audit of every SQL statement; composition latency |
| Reads and writes with different shapes; full history needed | CQRS, event sourcing | [13](../architecture-patterns/ch13-cqrs-event-sourcing.md) | Projections, snapshots and upcasting on a rider wallet |
| One business transaction across several services | Saga | [14](../architecture-patterns/ch14-saga-pattern.md) | 10,000 sagas with crashes: 0 double charges with idempotent participants, 165 without |
| Saving data and publishing an event reliably | Transactional outbox, inbox, CDC | [15](../architecture-patterns/ch15-outbox-inbox-cdc.md) | Dual writes lost or invented ~1.6% of events; outbox + inbox: 0 |
| Replacing a monolith without a big bang | Strangler fig, ACL, branch by abstraction | [16](../architecture-patterns/ch16-strangler-fig-migration.md) | Shadowing found 3 bugs before any customer saw them; canary limited an incident to 8 customers |
| Cross-cutting concerns outside the app's code | Sidecar, ambassador, adapter | [17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md) | An ambassador lifted success from 89.5% to 99.7% for ~0.3 ms per hop |
| Slow or failing dependencies | Timeout, retry, bulkhead, circuit breaker, fallback | [18](../architecture-patterns/ch18-resilience-patterns.md) | An unrelated endpoint went from 2.4% to 100% availability with a timeout |
| Sharing data and reacting to facts across services | Event notification, state transfer, event sourcing | [19](../architecture-patterns/ch19-event-driven-architecture.md) | Sync calls failed 100% of orders in an outage; state transfer 0% |
| A microservice system that feels worse than the monolith | Anti-pattern detection, fitness functions | [20](../architecture-patterns/ch20-microservice-anti-patterns.md) | 16 findings in the 2023 design, 1 accepted in 2024 |

### Communication

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Designing resource APIs that scale and evolve | REST: resources, keyset pagination, ETags, RFC 9457 | [21](../communication/ch21-rest-api-design.md) | Keyset pagination 9,664× faster than offset at the last of 2 M rows, without offset's duplicates and skips |
| Calling other services from Spring | RestClient, HTTP service clients | [22](../communication/ch22-http-clients.md) | A client per call cost 6–7× at p50 over TLS; virtual threads ran 1,000 slow calls 7× faster than a 50-thread pool |
| Fast internal calls, streaming, deadlines | gRPC | [23](../communication/ch23-grpc.md) | Protobuf 2.5× smaller than JSON, but gzipped JSON streams smaller still; propagated deadlines cut wasted work from 639 ms to 2 ms per request |
| Many app screens with different data needs | GraphQL in the BFF | [24](../communication/ch24-graphql.md) | 12 requests became 1 and bytes fell 8×; without batching, 19 backend calls and slower than REST |
| Reliable asynchronous processing | Queues, topics, retries, DLTs | [25](../communication/ch25-asynchronous-messaging.md) | Committing after + idempotency: 0 lost, 0 duplicated; Spring Kafka's default silently skipped 16 messages in an outage |
| Live updates to apps | SSE, WebSocket | [26](../communication/ch26-websockets-sse.md) | Polling saw 10 of 40 positions at ~1,190 bytes each; push saw all 40 at ~110 bytes; 10,000 streams in one JVM on virtual threads |
| Changing APIs without breaking consumers | Contracts, tolerant readers | [27](../communication/ch27-api-contracts.md) | 11 of 12 "small" changes broke a strict consumer; diff + contracts together caught 7 of 11; a change of meaning escaped both |
| Retries without double charges | Idempotency keys, dedup, correlation | [28](../communication/ch28-idempotency-correlation.md) | On PostgreSQL, only an atomic claim before the side effect gave one charge per key; constraints and SERIALIZABLE protected rows, not money |

### Data

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Which database for this service? | PostgreSQL by default; a second store only for a measured need | [29](../data/ch29-polyglot-persistence.md) | A JSONB menu read 14× faster than five joins, price changes 5× slower; Redis 3.8–5.6× PostgreSQL's write rate for positions |
| One business change across two services | Outbox-driven saga, not 2PC | [30](../data/ch30-distributed-transactions.md) | One in-doubt 2PC transaction cut a hot dish to 2–6 orders/s; the outbox design took every order through a 10 s outage |
| Users seeing old data after their own writes | Session guarantees with consistency tokens | [31](../data/ch31-data-consistency.md) | Stale reads after one's own write: 15% from a replica, 0 with a token |
| Lost updates under concurrency | Atomic updates, versions, CRDTs | [31](../data/ch31-data-consistency.md) | Read-modify-write lost 15.5% of loyalty points; an atomic update none |
| Slow reads, hot keys | Caching by purpose | [32](../data/ch32-caching-strategies.md) | TTL-only caches stale in 97% of reads; set-if-newer under 0.05%; stale-while-revalidate stopped stampedes |
| Too many connections, slow migrations | Fleet-wide pool budgets, lock-safe DDL, expand/contract | [33](../data/ch33-database-scaling.md) | An instant DDL behind a report blocked writes for 10 s; a rename failed thousands of statements in a rolling deploy, expand/contract none |
| One primary can't take the writes | Sharding by the key queries start from, through slots | [33](../data/ch33-database-scaling.md) | `hash mod N` moved 80% of data going from 16 to 20 shards; fixed slots 19.5% |
| Reports that need several services' data | A reporting store fed by events or CDC | [34](../data/ch34-reporting-analytics.md) | Reports on the primary cut its writes by 67–69%; counting events overstated revenue by 7.1%, state with versions was exact |

### Security

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Not knowing what could go wrong | Threat models as code, OWASP API Top 10 controls | [35](../security/ch35-security-fundamentals.md) | 69 threats in the checkout model, 67 mitigated, 2 accepted with owners; an authorization policy agreed with an independent rule table on all 18,600 decisions |
| Tokens that can't be revoked | Short-lived JWTs, or introspection where revocation must be fast | [36](../security/ch36-oauth2-oidc-jwt.md) | A revoked 15-minute JWT kept working 450 s on average; introspection with a 30 s cache 15 s |
| Rotating signing keys without errors | Publish the new key before signing with it | [36](../security/ch36-oauth2-oidc-jwt.md) | A sudden rotation rejected 29 requests; publishing 6 minutes ahead rejected 0 |
| Securing a Spring service | One deny-by-default chain, method security, the right password encoder | [37](../security/ch37-spring-security-7.md) | bcrypt 10 took 72 ms per check, bcrypt 12 about 290 ms; Spring's PBKDF2 default is half OWASP's minimum iterations |
| Services trusting each other's network | SPIFFE identities, mutual TLS, per-route peer policies | [38](../security/ch38-service-to-service-security.md) | An expired certificate kept getting connections through TLS resumption (19 of 19) until a per-request check; rotation at half-life failed 0 of 482 |
| Secrets in env vars, passwords nobody can change | OpenBao, dynamic credentials, two-version rotation | [39](../security/ch39-secrets-management.md) | In-place rotation failed 14–16% of logins, two accounts none; renew-only credentials failed at max_ttl, renew-then-rotate never |
| A rotated password that doesn't lock anyone out | Revocation that terminates sessions | [39](../security/ch39-secrets-management.md) | After a password change, expiry or `NOLOGIN`, the open session kept working |
| One client or tenant overwhelming the API | GCRA limits per client and tenant, shared in Redis | [40](../security/ch40-api-security.md) | Local limits let 1,036 through a 260 budget, Redis 258–260; a global limit left other tenants 0.2% of their requests, per-tenant limits 100% |
| CORS and CSRF confusion | Exact-origin CORS for bearer APIs; Fetch Metadata and tokens for cookie sessions | [40](../security/ch40-api-security.md) | In Chromium, a sibling subdomain got even `SameSite=Strict` cookies; a reflecting server leaked the user's orders |
| A WAF that blocks customers | The CRS tuned for a JSON API, with field-level exclusions | [40](../security/ch40-api-security.md) | As shipped: 6 of 41 legitimate requests blocked at PL1, 20 at PL2; tuned: 0 and 4; business-logic abuse caught: 0 of 6 |
| A query that forgets the tenant | Row-level security, forced, transaction-local tenant | [40](../security/ch40-api-security.md) | 2,000 rows became 40; a session-level `SET` leaked 4,000 rows to the next request |
| "Are we affected?" taking days | SBOMs per artifact, daily rescans of what is deployed | [41](../security/ch41-supply-chain-compliance.md) | A Keycloak release went from 0 known findings to 196 without changing |
| Untrusted images and pipelines | Keyless signing, provenance at admission, SHA-pinned actions | [41](../security/ch41-supply-chain-compliance.md) | With a leaked signing key, only the provenance policy refused a laptop rebuild |
| Erasure requests, data residency | Declared data classes, OPA policies, crypto-shredding | [41](../security/ch41-supply-chain-compliance.md) | 9 violations in 2 of 4 deployments; 0 of 9,000 erased fields readable in backups and topics |

### Resilience

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Latency that explodes near capacity | Utilisation headroom, explicit concurrency limits | [42](../resilience/ch42-failure-modes.md) | p99 at 4.5× the service time at 52% load, 12× at 97% |
| Retries that multiply through a call chain | Retry at one layer, or a retry budget | [42](../resilience/ch42-failure-modes.md) | 3 attempts at every layer of three: 27 calls per user to a dead dependency; a 10% budget: 1.4 |
| An outage that outlives its trigger | Shedding work older than the caller's timeout | [42](../resilience/ch42-failure-modes.md) | With retries, 0% served for 30 s after a 5 s slowdown; shedding at 150 ms: back to 100% in the next window |
| Reconnect storms after a restart | Exponential backoff with full jitter | [42](../resilience/ch42-failure-modes.md) | 600 clients reconnected in under 6 s with full jitter, 42 s with plain exponential backoff |
| A breaker that never opens, or never closes | Time-based windows, a slow-call threshold, short open state | [43](../resilience/ch43-resilience4j-deep-dive.md) | Defaults: 7.5–10 s to open at 5 calls/s, a 3 s dependency never "slow"; a 5 s window opened in 2–4 s |
| Fallbacks that disable retries | The fallback on the outermost decorator | [43](../resilience/ch43-resilience4j-deep-dive.md) | Fresh menus 68–70% with the fallback inside the retry, 97–98% outside |
| A timeout that doesn't stop the work | Cancellable work, interrupt-aware clients | [43](../resilience/ch43-resilience4j-deep-dive.md) | `CompletableFuture` work ran to the end every time after the TimeLimiter fired |
| Tail latency with spare capacity | Hedged reads within a budget | [44](../resilience/ch44-timeouts-retries-backpressure.md) | p99 from 1 s to 88 ms for 10–11% more calls; unbudgeted hedges at 85% load served 18–68% |
| A slowdown that becomes an outage | Adaptive concurrency limits (AIMD) | [44](../resilience/ch44-timeouts-retries-backpressure.md) | No limit: 4% served in time, no recovery; AIMD: 53–54% of a possible 57% |
| Checkouts lost to browsing traffic | Limits partitioned by priority, LIFO waiting | [44](../resilience/ch44-timeouts-retries-backpressure.md) | 79–81% of checkouts served under 2× overload instead of about 50% |
| Not knowing whether the defences work | Experiments with an SLO hypothesis and an abort guard | [45](../resilience/ch45-chaos-engineering.md) | A guard cut hurt users from about 1,500 to 150–180; a 1 KB/s trickle hung every client but one |
| A breaker that a fault can't trigger | A read timeout under the breaker | [45](../resilience/ch45-chaos-engineering.md) | A black hole without a read timeout: the breaker never opened, 19 of 20 windows failed |
| Zero data loss across zones | Quorum-synchronous standbys | [46](../resilience/ch46-disaster-recovery.md) | 0 commits lost; 1.3–1.7 ms per commit instead of 0.9–1.0 ms in-zone; ~63 ms across a 60 ms round trip |
| Losing a region | Asynchronous cross-region standby, fencing before promotion | [46](../resilience/ch46-disaster-recovery.md) | 85–278 commits lost when link and primary died together; 17,000–22,700 when the link failed 2 s first |
| A failover slower than the database | Multi-host clients, bounded driver timeouts | [46](../resilience/ch46-disaster-recovery.md) | Promotion 0.1 s; role-checking clients resumed 0.3 s later, clients on a 30 s DNS TTL after 24–33 s |
| Bad writes on every replica | Backups with streamed WAL, timed restores | [46](../resilience/ch46-disaster-recovery.md) | `archive_timeout = 60 s` lost 298–317 orders, streamed WAL 0 |
| One tenant taking down everyone | Cells, shuffle sharding | [46](../resilience/ch46-disaster-recovery.md) | A poison tenant downed 100% of a shared pool, 12.7% with 8 cells, 1.7–3.6% with shuffle shards |

### Observability

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Not knowing which signal to use | Metrics to detect, traces to locate, wide events to explain | [47](../observability/ch47-observability-fundamentals.md) | A canary moved p99 from about 24 ms to 681–711 ms within one window; 11,000 log lines said nothing; spans named the cause |
| Keeping slow traces affordably | Tail sampling in the Collector | [47](../observability/ch47-observability-fundamentals.md) | 1% head sampling kept 2–4 of 248–285 slow traces; tail sampling kept all of them in 18–19% of the bytes |
| Fleet percentiles that look fine | Summed histogram buckets, never averaged percentiles | [48](../observability/ch48-metrics-micrometer.md) | Averaged p99s said 137 ms; the true p99 was 322 ms and the buckets said 317–318 ms |
| A Prometheus that keeps growing | Templated tags, a cap that's alerted on | [48](../observability/ch48-metrics-micrometer.md) | 10,000 requests: 24 meters with path patterns, 15,954 with raw paths, a 135 MB scrape |
| Traces that stop at a thread or a queue | Executors, `Context.wrap`, extraction from message headers | [49](../observability/ch49-distributed-tracing-otel.md) | Bare threads and pull consumers lost the trace 200 of 200 and 220 of 220 times |
| A sampler that breaks everyone's traces | Parent-based sampling everywhere, decided at the edge | [49](../observability/ch49-distributed-tracing-otel.md) | One non-parent-based service dropped itself from 43–47 sampled traces or stored 400–904 fragments |
| Logs that can't be followed across threads | The trace ID as the correlation field | [50](../observability/ch50-structured-logging.md) | MDC request IDs missing on 200 of 200 executor hops, wrong on 99 of 200 after a leak; the agent's trace ID right on all of them |
| Holes in the logs under load | `AsyncAppender` with `discardingThreshold` 0 | [50](../observability/ch50-structured-logging.md) | Defaults delivered 29–30% of INFO under a burst, silently |
| Personal data in logs | A masking encoder on every line, plus `Pii` types | [50](../observability/ch50-structured-logging.md) | Source-only masking leaked 5 of 5 secrets from messages, MDC and exceptions; line masking leaked none |
| Alerts that miss slow failures or cry wolf | Multi-window, multi-burn-rate SLO alerts | [51](../observability/ch51-slo-alerting.md) | A 1% static alert missed burns spending up to 43% of the budget; burn-rate alerts paged in 1–43 min and ticketed slow burns at 8% |
| Pages for every error on a quiet service | A minimum event count in the alert | [51](../observability/ch51-slo-alerting.md) | 16 pages in 30 days on a healthy service at 1 request/min; 0 with a 100-request minimum |
| A latency SLI that's an estimate | A histogram bucket at the objective | [51](../observability/ch51-slo-alerting.md) | Without it, 99.14% for a true 99.41%: the budget looked 86% spent instead of 59% |
| Probes that restart the fleet | Liveness and readiness on Boot's groups only | [52](../observability/ch52-health-checks-actuator.md) | Liveness on the database: 6 restarts in a 60 s outage and half of `/menu` failing; Boot's groups: none and 100% |
| Errors on every deploy | Graceful shutdown with a `preStop` drain | [52](../observability/ch52-health-checks-actuator.md) | 79–96 failed requests per rolling restart without a drain, 0 with a `preStop` sleep |

### Deployment & Platform

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| A SIGTERM that's ignored | Exec-form entrypoints | [53](../deployment/ch53-containerization.md) | Shell-form and wrapper entrypoints made `docker stop` wait 10 s and kill the service |
| Slow, throttled JVM startup in pods | CPU requests without limits | [54](../deployment/ch54-kubernetes-for-spring.md) | At a 250m limit: 3.9–4.0 s to start against 0.7–0.8 s, p99 93–97 ms against 10–16 ms |
| Config changes that don't arrive | Mounted, watched config, or a restart on change | [54](../deployment/ch54-kubernetes-for-spring.md), [55](../deployment/ch55-helm-kustomize.md) | Without a checksum or hashed name, no pod restarted and the environment variable stayed old |
| New pods that get no traffic | Connection lifetime caps, or a mesh | [54](../deployment/ch54-kubernetes-for-spring.md), [57](../deployment/ch57-service-mesh.md) | A keep-alive client sent every request to the first pod for 50 s; through Envoy 333/333/334 |
| Outages during node drains | A PodDisruptionBudget | [54](../deployment/ch54-kubernetes-for-spring.md) | 295–331 failed requests without one, 0–1 with `minAvailable: 2` |
| Deploys that fight other controllers | Server-side apply; no `replicas` in the manifest | [55](../deployment/ch55-helm-kustomize.md) | Helm 4 failed with a field conflict where Helm 3 silently reset 5 replicas to 2 |
| Two versions behind one Service | Promotion by digest | [55](../deployment/ch55-helm-kustomize.md) | A moved tag ran two builds on a scale-out; digests kept all four pods on one |
| An HPA that overshoots | Readiness after warm-up | [56](../deployment/ch56-autoscaling.md) | Ready-at-once pods drove the HPA to 8 pods for 4 pods' work; held until warm, 5 |
| Flapping replica counts | A scale-down stabilization window | [56](../deployment/ch56-autoscaling.md) | No window: 10–11 changes and 325–432 failures in 8 minutes; default: 2 changes |
| One bad pod in three | Retries plus outlier detection | [57](../deployment/ch57-service-mesh.md) | 1,000 of 3,000 failed with neither; 0 with both and 35 retries; Envoy's 10% default never ejected |
| Bad releases reaching everyone | Canaries with per-version analysis | [58](../deployment/ch58-cicd-gitops.md) | Aborted at 10% after 7–10 failures, against 944–953 for a rolling update |
| Drift from hand edits | GitOps with self-heal and prune | [58](../deployment/ch58-cicd-gitops.md) | Commits applied in 1.0–9.8 s; scaled and deleted objects restored; a hand-added variable stayed |
| Cloud keys in Secrets | Workload identity | [59](../deployment/ch59-cloud-native-platforms.md) | Wrong audience, account and signature refused; a deleted pod's token still verified offline |
| Throttled cloud APIs | Adaptive retries with a quota | [59](../deployment/ch59-cloud-native-platforms.md) | Failed calls at twice the limit: 141–161 adaptive, 766–914 legacy and standard |
| Functions or containers | Traffic shape and the cold start | [59](../deployment/ch59-cloud-native-platforms.md) | $0.01–0.10 a day against $2.37 for quiet services; containers win only when they follow load at scale |
| Slow JVM starts | An AOT cache trained through HTTP; native for functions | [60](../deployment/ch60-native-images-startup.md) | 1 CPU: 1.1–1.2 s JVM, 0.6–0.7 s AOT cache, 0.25–0.27 s native; native served 27–42% less after a minute |

### Testing

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Arguing pyramid vs honeycomb | Planted bugs against your suites | [61](../testing/ch61-test-strategy.md) | Unit tests caught 7 of 7 logic bugs and nothing else; component tests 12 of 20; all five suites 19 |
| Repository tests on H2 | PostgreSQL in a container | [61](../testing/ch61-test-strategy.md), [62](../testing/ch62-unit-slice-integration-tests.md) | H2 failed 3 of 4 tests on correct code and 8 of 19 PostgreSQL features |
| Slow container tests | One container per run, reuse locally | [62](../testing/ch62-unit-slice-integration-tests.md) | 4.0–4.7 s against 18.0–22.9 s for 8 classes; 1.5–2.5 s reused |
| Tests that pass alone and fail together | Truncate instead of rollback for integrated tests | [62](../testing/ch62-unit-slice-integration-tests.md) | Rollback leaked into 299 of 300 tests when code committed on its own connection |
| A rule that must hold "once" | A concurrent test on the real database | [62](../testing/ch62-unit-slice-integration-tests.md) | Check-then-insert passed every single-threaded test, then 421–449 errors under concurrency |
| Providers breaking consumers | Consumer-driven contracts and can-i-deploy | [63](../testing/ch63-contract-testing.md) | Contracts caught 7 of 9 breaking changes with no false alarms; an OpenAPI diff 5 with 2 |
| Contracts that fail on every data change | Type matchers with formats | [63](../testing/ch63-contract-testing.md) | Recorded examples flagged 5 of 5 harmless data changes |
| A red shared staging | A preview environment per pull request | [64](../testing/ch64-component-e2e-testing.md) | Shared staging passed 1–5 of 12 suite runs; fresh environments 12 of 12, ready in about 7 s |
| Flaky asynchronous tests | Polling with a timeout | [64](../testing/ch64-component-e2e-testing.md) | Sleeps failed 1–28% of runs; polling none of 400, faster on average |
| Load-test numbers that look too good | Open (arrival-rate) models | [65](../testing/ch65-performance-chaos-arch-tests.md) | A closed model's p99 was 15 ms; the open model's 942 ms |
| Not knowing capacity | Stepped arrival rates | [65](../testing/ch65-performance-chaos-arch-tests.md) | Closed models plateaued; at 108% of capacity the open model's p99 was 1.5–1.7 s |
| Resilience you've never tested | Chaos experiments with abort conditions | [65](../testing/ch65-performance-chaos-arch-tests.md) | Only a timeout with a fallback held the hypothesis; aborts ended bad experiments in 7 s |
| Architecture drift | Fitness functions with frozen debt | [65](../testing/ch65-performance-chaos-arch-tests.md) | 8 of 10 violations caught in under a millisecond; an inlined constant and reflection escaped |

### Performance & Scalability

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| Latency rises with load but CPU is idle | Virtual threads (or an event loop) | [66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) | 200 platform threads capped a quote at 1,060–1,100/s; virtual threads reached about 1,490/s with 25 OS threads |
| A service falls over past capacity | A concurrency limit with fast rejection | [66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) | Unbounded: 86–93% failed; a limit of 300 served 1,510–1,588/s at p99 ≤ 303 ms |
| Requests that fan out are slow | Fewer calls, or hedged reads with a budget | [66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) | 50 calls with 1% slow: 39–41% of requests slow; hedging cut p99 to 53–86 ms |
| Long GC pauses in small pods | An explicit collector | [67](../performance-scalability/ch67-jvm-tuning-containers.md) | A 2-CPU, 1 GiB pod got Serial: 655–704 ms pauses |
| OOMKilled pods | Heap from the live set, non-heap measured with NMT | [67](../performance-scalability/ch67-jvm-tuning-containers.md) | 90% heap + 150 MB of direct buffers was killed in one run of two |
| New pods get no traffic | Per-request balancing, connection recycling | [68](../performance-scalability/ch68-horizontal-scaling.md) | After scaling 2 → 4 with keep-alive, new copies got 0% |
| More pods, same throughput | Find and scale the shared bottleneck | [68](../performance-scalability/ch68-horizontal-scaling.md) | 4 copies served 50/s behind a 50/s dependency |
| Resharding moves everything | Consistent, rendezvous or jump hashing | [68](../performance-scalability/ch68-horizontal-scaling.md) | mod n moved 80% of keys going 4 → 5; the others 20% |
| Brokers busy at modest traffic | Producer batching | [69](../performance-scalability/ch69-kafka-throughput-tuning.md) | linger 0: 66,000–69,000 requests for 100,000 records; 20 ms: about 950 |
| Consumer lag that won't clear | Batch writes, key-ordered concurrency | [69](../performance-scalability/ch69-kafka-throughput-tuning.md) | A commit per record: 1,384–1,554 rows/s; a poll per transaction 29,000–59,000 |
| A large, mostly idle cluster | Requests from measured usage | [70](../performance-scalability/ch70-finops.md) | Workloads used 3–10% of their requested CPU; right-sized, 97 → 30 CPUs and 32 → 8–9 nodes |
| Saving on compute without outages | Spot above an on-demand base | [70](../performance-scalability/ch70-finops.md) | All-spot served 0–1% during a reclaim; 3 on-demand + 3 spot served 55–61% |

### Governance & Organization

| If your problem is… | Reach for | Ch. | Measured |
|---|---|---|---|
| API reviews argue about the same things | A ruleset with house rules, and a ratchet for old APIs | [71](../governance-org/ch71-api-governance.md) | Spectral caught 10 of 33 planted problems, house rules 16 more; 7 needed a reviewer |
| Nobody knows who still calls a deprecated API | Deprecation headers and an access log by client | [71](../governance-org/ch71-api-governance.md) | 2 of 4 remaining callers were in no catalog; only the log found them |
| New services take weeks to set up | A golden path in a developer portal | [72](../governance-org/ch72-platform-engineering.md) | From a name to two ready pods in 6.5–8.0 s, 12 of 12 checks |
| Old services ignore new standards | Template updates by three-way merge | [72](../governance-org/ch72-platform-engineering.md) | An old template's services passed 8% of checks; merging took them from 4 to 40 of 48 |
| Decisions are forgotten or quietly undone | Fitness functions traced to ADRs | [73](../governance-org/ch73-adrs-fitness-functions.md) | Code checked 7 of 72 decisions; five functions caught one problem in each of six releases |
| Performance checks in CI are flaky or blind | A ratchet against a recent baseline, best of three | [73](../governance-org/ch73-adrs-fitness-functions.md) | A fixed budget passed a fourfold regression; single runs raised up to 9 false alarms in 12 |
| A migration plan from a whiteboard | Measure coupling, cycles and history first | [74](../governance-org/ch74-maturity-migration-roadmap.md) | Broadleaf: 9 contexts in one cycle, 157 imports from layers |
| What to extract first | Most independent changes per new remote dependency | [74](../governance-org/ch74-maturity-migration-roadmap.md) | 32% of changes freed for 340 remote edges, against 1,083 by most-changed-first |
| One tenant could see another's data | Forced RLS, tenant set per transaction | [75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md) | A per-session tenant leaked in 177 of 193 forgotten requests; per transaction, none |
| One big tenant slows everyone | Per-tenant bulkheads | [75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md) | Others' p99 25–38 ms unlimited, 8–11 ms with a bulkhead |
| "Forget me" must reach backups | Crypto-shredding | [75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md) | DELETE left the email in WAL and backup; shredding left only unreadable ciphertext |
| Should this be a function? | Price the workload's rate and service time | [76](../governance-org/ch76-serverless-microservices.md) | Webhooks under a cent a day as functions; containers cheaper above 28–106 requests/s |
| Java functions start slowly | AOT cache, C1, Serial GC | [76](../governance-org/ch76-serverless-microservices.md) | JVM part of a cold start 246–329 ms → 113–196 ms |
| LLM calls hang or fail under load | A gateway with stream-aware deadlines and per-feature limits | [77](../governance-org/ch77-ai-native-microservices.md) | p99 5.1–5.3 s instead of 10–30 s on stalls; chats 2.0 s median beside a batch job |
| An assistant's tools leak data | Identity from the token, not from arguments | [77](../governance-org/ch77-ai-native-microservices.md) | Injected text read another customer's order 30 of 30 times with argument identity, 0 with token identity |
