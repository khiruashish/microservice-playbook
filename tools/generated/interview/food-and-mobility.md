### Architecture & Patterns


**[10. API Gateway & Backend for Frontend](../architecture-patterns/ch10-api-gateway-bff.md)**

??? question "What is the BFF pattern and when would you use it?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Swiggy-/Zomato-style product companies

    A backend built for one client experience and owned by that client's team, exposing screen-shaped endpoints that aggregate and format data from domain services. Use it when clients differ significantly or when chatty client-to-service calls hurt latency and coupling. It must not hold domain rules.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q3-what-is-the-bff-pattern-and-when-would-you-use-it-l2l3-flipkart-swiggy-zomato-style-product-companies)

??? question "Design the edge for a food delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds · Amazon

    CDN for static assets and cacheable menus; cloud load balancer with TLS; a stateless API gateway (token validation, header hygiene, request ids, per-user rate limits on order placement, size limits, retries only for reads, circuit breakers with fallbacks); BFFs for the customer app, partner portal and rider app; WebSocket or SSE endpoint for live tracking; services private. Mention capacity (gateway replicas per zone), observability per route, and that internal calls bypass the gateway.

    [Full answer in chapter 10](../architecture-patterns/ch10-api-gateway-bff.md#q12-design-the-edge-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds-amazon)


**[13. CQRS & Event Sourcing](../architecture-patterns/ch13-cqrs-event-sourcing.md)**

??? question "Design a wallet for delivery partners."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Event-sourced wallet per partner (earnings, tips, adjustments, payout requested/completed/failed), rules in decide (minimum payout, available balance, one payout in flight), optimistic concurrency, idempotent credits per order, projections for the app, ops queue and finance, payouts through a saga with the bank, integration events through an outbox, PII outside events.

    [Full answer in chapter 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#q12-design-a-wallet-for-delivery-partners-l4-swiggy-zomato-style-system-design-rounds)


**[14. Saga Pattern](../architecture-patterns/ch14-saga-pattern.md)**

??? question "Design the order placement for a food delivery app."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    An orchestrated saga: create order (PENDING) → reserve coupon → authorize payment → restaurant accepts (pivot) → capture, assign rider, confirm. Compensations: release coupon, void authorization, reject order. Idempotency keys per step, outbox and inbox, timeouts (restaurant doesn't respond in 3 minutes → reject and compensate), STUCK dashboard, customer-visible "confirming with restaurant" state.

    [Full answer in chapter 14](../architecture-patterns/ch14-saga-pattern.md#q9-design-the-order-placement-for-a-food-delivery-app-l4-swiggy-zomato-style-system-design-rounds)


**[19. Event-Driven Architecture](../architecture-patterns/ch19-event-driven-architecture.md)**

??? question "Design menu availability propagation for a food delivery app."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style rounds

    catalog owns items and publishes `MenuItemUpdated` (state, version, event id) via outbox to a compacted topic keyed by item id; ordering, search and recommendations keep replicas, newest-version-wins; ordering checks availability locally and the kitchen's accept step (the saga pivot) remains the final authority; monitor lag; new consumers bootstrap from the compacted topic.

    [Full answer in chapter 19](../architecture-patterns/ch19-event-driven-architecture.md#q11-design-menu-availability-propagation-for-a-food-delivery-app-l4-swiggy-zomato-style-rounds)


### Communication


**[21. Synchronous REST](../communication/ch21-rest-api-design.md)**

??? question "How would you design an order-history API for 50 million customers?"
    <span class="badge badge-l3">L3</span> Swiggy-/Zomato-style rounds

    `GET /customers/{id}/orders` with keyset cursors on `(placed_at, id)` backed by a `(customer_id, placed_at desc, id desc)` index, limit ≤ 100, `next` links, no counts; single orders with ETags and private caching; problem details for errors; read replicas or a read model for scale.

    [Full answer in chapter 21](../communication/ch21-rest-api-design.md#q11-how-would-you-design-an-order-history-api-for-50-million-customers-l3-swiggy-zomato-style-rounds)


### Data


**[30. Distributed Transactions](../data/ch30-distributed-transactions.md)**

??? question "Design order placement for a food delivery app without distributed transactions."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    `POST /orders` writes the order `PENDING` and an outbox row in one transaction and returns 202. A relay delivers the event to payment-service, which debits the wallet and takes stock in one local transaction keyed by order id (stored decision, idempotent). The relay records `CONFIRMED` or `REJECTED` with a reason and the app is notified. Recovery jobs with grace periods settle stuck and unknown cases; compensations (refund, restock) handle later cancellations.

    [Full answer in chapter 30](../data/ch30-distributed-transactions.md#q12-design-order-placement-for-a-food-delivery-app-without-distributed-transactions-l4-swiggy-zomato-style-system-design-rounds)


**[31. Data Consistency](../data/ch31-data-consistency.md)**

??? question "Design consistency for a food-delivery app's data."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Classify reads: wallet and payment status strong (primary); own profile, addresses and orders read-your-writes (tokens); tracking monotonic (tokens); menus, listings and ratings bounded-stale (replicas, caches). Counters by atomic updates; user-edited records with If-Match; cart as a CRDT if edited offline or in several regions; checkout re-reads prices and stock from the source of truth.

    [Full answer in chapter 31](../data/ch31-data-consistency.md#q12-design-consistency-for-a-food-delivery-apps-data-l4-swiggy-zomato-style-system-design-rounds)


**[32. Caching Strategies](../data/ch32-caching-strategies.md)**

??? question "Design caching for a food-delivery home screen."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Rankings and recommendations: stale-while-revalidate per city or segment with refresh locks and jitter. Restaurant details: cache-aside, evicted after commit. Prices and availability: write-through with versions, re-read at checkout. Images and static content: CDN. View counters: write-behind. Every cache optional, monitored, and never the input to payment or stock decisions.

    [Full answer in chapter 32](../data/ch32-caching-strategies.md#q12-design-caching-for-a-food-delivery-home-screen-l4-swiggy-zomato-style-system-design-rounds)


**[33. Database Scaling & Zero-Downtime Migrations](../data/ch33-database-scaling.md)**

??? question "Design the order database for a food-delivery platform growing 10× a year."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Start with one PostgreSQL primary, replicas for reads and reports, time-partitioned orders and archiving, small fleet-wide pool budgets and a pooler. Lock-safe, expand/contract migrations from day one. When writes outgrow the primary, shard by customer through a slot map with the slot embedded in order ids, restaurant-side views built from events (chapter 15), and slot moves as a rehearsed runbook.

    [Full answer in chapter 33](../data/ch33-database-scaling.md#q12-design-the-order-database-for-a-food-delivery-platform-growing-10-a-year-l4-swiggy-zomato-style-system-design-rounds)


**[34. Reporting & Analytics Across Services](../data/ch34-reporting-analytics.md)**

??? question "Design analytics for a food-delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Services publish outbox events (orders, payments, deliveries) to Kafka; CDC for the rest with capped slots. A reporting service maintains facts by version and incremental aggregates by city's business day for operations dashboards (minutes fresh, with as-of times). The same topics land in an Iceberg lake for history, finance reconciliation and data science. Datasets have owners and contracts; production databases are never queried for analytics.

    [Full answer in chapter 34](../data/ch34-reporting-analytics.md#q12-design-analytics-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds)


### Deployment & Platform


**[53. Containerization](../deployment/ch53-containerization.md)**

??? question "How do you make Java images small?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    Use a runtime with only the modules you need (jdeps + jlink) or a JRE, on distroless instead of a full OS, with no build tools. Measured: 469 MB with the full JDK, 119 MB with jlink `java.se`, 88 MB with jdeps' 7 modules.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q2-how-do-you-make-java-images-small-l2-swiggy-flipkart)

??? question "Dockerfile, Jib or Buildpacks?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Atlassian · Zomato

    

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q4-dockerfile-jib-or-buildpacks-l2l3-atlassian-zomato)

??? question "Why was my pod OOMKilled with no OutOfMemoryError?"
    <span class="badge badge-l3">L3</span> Uber · PhonePe

    The process exceeded the cgroup limit, usually because `-Xmx` (plus off-heap memory) was above it, and the kernel killed it. Measured: `-Xmx1g` in 512 MB gave exit 137, `OOMKilled: true` and no Java output. Size the heap as a percentage of the limit and use `ExitOnOutOfMemoryError`.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q6-why-was-my-pod-oomkilled-with-no-outofmemoryerror-l3-uber-phonepe)

??? question "How do you speed up JVM startup in containers?"
    <span class="badge badge-l3">L3</span> Swiggy · Meesho

    Use CDS or the JDK 24+ AOT cache from a training run, a smaller runtime, lazy initialization where safe, and enough CPU during startup. Measured: an AOT cache took first response from 1.2–1.3 s to 0.49–0.51 s at 1 CPU, for 24 MB of image. Native images and CRaC are chapter 60.

    [Full answer in chapter 53](../deployment/ch53-containerization.md#q9-how-do-you-speed-up-jvm-startup-in-containers-l3-swiggy-meesho)


**[54. Kubernetes for Spring Developers](../deployment/ch54-kubernetes-for-spring.md)**

??? question "Should JVM services have CPU limits?"
    <span class="badge badge-l3">L3</span> Swiggy · Atlassian

    Often not for latency-sensitive ones. Requests guarantee the share, and limits throttle bursts from startup, JIT and GC. If a limit is policy, keep it at 1–2 CPUs or more, and alert on throttling. Without a limit, set `-XX:ActiveProcessorCount`, or the JVM sizes itself for the whole node.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q2-should-jvm-services-have-cpu-limits-l3-swiggy-atlassian)

??? question "Why don't new pods get traffic after scaling out?"
    <span class="badge badge-l3">L3</span> Flipkart · Uber

    kube-proxy balances connections, and existing keep-alive connections stay on the pods they were opened to. Measured: all pooled requests stayed on the original pod for 50 s, while new connections spread at once. Cap connection lifetime, or balance per request with a mesh or a client-side balancer.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q4-why-dont-new-pods-get-traffic-after-scaling-out-l3-flipkart-uber)

??? question "What does a PodDisruptionBudget do?"
    <span class="badge badge-l2">L2</span> Zomato · Walmart

    It limits voluntary disruptions (drains, upgrades, autoscaler removals): the eviction API refuses evictions that break it. Measured: without one, a drain evicted all three pods in 2 s and 295–331 requests failed; with `minAvailable: 2`, one pod was evicted, the rest refused, and 0–1 requests failed. It doesn't protect against crashes.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q5-what-does-a-poddisruptionbudget-do-l2-zomato-walmart)

??? question "How should a Spring Boot service find other services?"
    <span class="badge badge-l2">L2</span> Swiggy-style rounds

    By Service DNS name (`http://order-service.orders.svc.cluster.local` or just `order-service`), with Kubernetes doing discovery. No Eureka, no client-side registry, unless you need per-request balancing, which a mesh provides.

    [Full answer in chapter 54](../deployment/ch54-kubernetes-for-spring.md#q10-how-should-a-spring-boot-service-find-other-services-l2-swiggy-style-rounds)


**[55. Helm, Kustomize & Environment Promotion](../deployment/ch55-helm-kustomize.md)**

??? question "Why did our deploy reset the HPA's replica count?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    The manifest sets `replicas`, and a client-side apply (kubectl or Helm 3) resets fields that differ. Measured: 5 → 2. Server-side apply refuses with a conflict instead, and Helm 4 fails the release. The fix is to omit `replicas` when an HPA owns it; then the apply kept 5.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q3-why-did-our-deploy-reset-the-hpas-replica-count-l3-swiggy-flipkart)

??? question "How do you make pods pick up a ConfigMap change?"
    <span class="badge badge-l2">L2</span> Zomato · Paytm

    Change the pod template when the config changes: a `checksum/config` annotation in Helm, or kustomize's hashed generator names. Measured: both replaced the pods in 5–9 s; without them, no pod restarted and the env var stayed old. Alternatively, a watched mount with code that reloads (chapter 54).

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q5-how-do-you-make-pods-pick-up-a-configmap-change-l2-zomato-paytm)

??? question "Why did nothing fail while the bad release was crash-looping?"
    <span class="badge badge-l3">L3</span> Uber

    With 3 replicas, the default 25% `maxUnavailable` rounds down to 0, so the Deployment adds a new pod and waits for it before removing an old one. The new pod never became ready, so all three old pods kept serving. With `maxUnavailable: 1`, one old pod went away and capacity stayed at two-thirds.

    [Full answer in chapter 55](../deployment/ch55-helm-kustomize.md#q7-why-did-nothing-fail-while-the-bad-release-was-crash-looping-l3-uber)


**[56. Autoscaling](../deployment/ch56-autoscaling.md)**

??? question "Why did the HPA take so long to scale up?"
    <span class="badge badge-l2">L2</span> Swiggy · Zomato

    The metric is a window average from metrics-server (15 s resolution here, 60 s upstream), and the HPA syncs every 15 s. Measured: 29–34 s from load to the first new pods, and 77–94 s to the final size. Then the pods have to start. Keep headroom (a target of 50–70%) and raise minimums before known peaks.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q2-why-did-the-hpa-take-so-long-to-scale-up-l2-swiggy-zomato)

??? question "How do JVMs fool the HPA?"
    <span class="badge badge-l3">L3</span> Atlassian · Uber

    Warm-up CPU (class loading, JIT) looks like load. If pods are ready at once, the HPA counts it and scales further. Measured: 8 pods (the maximum) instead of 5. The HPA ignores samples from unready pods and from before a pod became ready within its first five minutes, so fail readiness until warm.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q4-how-do-jvms-fool-the-hpa-l3-atlassian-uber)

??? question "How do you scale on requests per second instead of CPU?"
    <span class="badge badge-l3">L3</span> Uber

    Expose the metric (Micrometer to Prometheus, chapter 48), serve it through a custom metrics adapter (Prometheus Adapter, or KEDA's Prometheus scaler), and use a `Pods` or `Object` metric with an `AverageValue` target. Useful for I/O-bound services whose CPU stays low.

    [Full answer in chapter 56](../deployment/ch56-autoscaling.md#q10-how-do-you-scale-on-requests-per-second-instead-of-cpu-l3-uber)


**[57. Service Mesh](../deployment/ch57-service-mesh.md)**

??? question "Where should retries live?"
    <span class="badge badge-l3">L3</span> Uber · Swiggy

    In one layer, with a budget, for idempotent requests only, and on a different endpoint. Measured: mesh retries alone hid a bad pod but cost ~1,500 retries per 3,000 requests; with outlier detection, 35. Two layers multiply.

    [Full answer in chapter 57](../deployment/ch57-service-mesh.md#q5-where-should-retries-live-l3-uber-swiggy)


**[58. CI/CD, GitOps & Progressive Delivery](../deployment/ch58-cicd-gitops.md)**

??? question "How does a canary decide to continue?"
    <span class="badge badge-l2">L2</span> Swiggy · Zomato

    After each traffic step, compare the canary's error rate and latency with the stable version's over a window, with a minimum sample, and abort early on a burst. Measured: a release failing 5% was aborted at 10% after 19–22 s, with 7–10 failed requests.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q4-how-does-a-canary-decide-to-continue-l2-swiggy-zomato)

??? question "Why did a healthy canary get aborted?"
    <span class="badge badge-l3">L3</span> Uber

    Measured: a one-pod canary at 50% weight took as much traffic as three stable pods, and its p99 crossed the absolute threshold. Scale the canary with its weight, warm it, and compare latency to stable rather than a fixed number.

    [Full answer in chapter 58](../deployment/ch58-cicd-gitops.md#q6-why-did-a-healthy-canary-get-aborted-l3-uber)


**[59. Cloud-Native on AWS, Azure & GCP](../deployment/ch59-cloud-native-platforms.md)**

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


**[60. Native Images, CRaC & Startup Optimization](../deployment/ch60-native-images-startup.md)**

??? question "GraalVM native image: what do you gain and lose?"
    <span class="badge badge-l3">L3</span> Swiggy · Zomato

    Gain: startup in tens of milliseconds (246–289 ms here, mostly Docker), memory in single-digit MiB (7.5–7.8 idle), small images (50.5 MB). Lose: peak throughput (994–1,026 against 1,411–1,701 requests a second on the JVM, with CE's Serial GC and no profile-guided optimisation), a 205 s, 1.73 GB build, and the closed-world assumption: reflection and resources must be declared.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q6-graalvm-native-image-what-do-you-gain-and-lose-l3-swiggy-zomato)

??? question "How does a native image know about reflection?"
    <span class="badge badge-l3">L3</span> Uber

    From reachability metadata: shipped by libraries (the GraalVM metadata repository), generated by frameworks (Spring AOT), or recorded by the native-image agent during a training run. Anything the agent didn't see fails at runtime, so tests should run against the executable.

    [Full answer in chapter 60](../deployment/ch60-native-images-startup.md#q7-how-does-a-native-image-know-about-reflection-l3-uber)


### Foundations


**[1. Monolith vs SOA vs Microservices vs Modular Monolith](../foundations/ch01-architecture-styles.md)**

??? question "Can a monolith scale to Flipkart or Swiggy traffic?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style product companies

    Stateless monoliths scale horizontally, and many very large sites ran (and some still run) on well-modularised monoliths. The real limits are database write throughput, differing scaling profiles, and organisational scaling (many teams in one codebase), not request throughput as such.

    [Full answer in chapter 1](../foundations/ch01-architecture-styles.md#q9-can-a-monolith-scale-to-flipkart-or-swiggy-traffic-l2l3-flipkart-swiggy-style-product-companies)


**[3. Distributed Systems Fundamentals](../foundations/ch03-distributed-systems-fundamentals.md)**

??? question "Explain consistency models. Which would you choose for an order-status page?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style product companies

    From linearizable through causal and session guarantees to eventual. For an order-status page: linearizable writes inside order-service (versioned transitions), and **monotonic reads** for the customer so status never goes backwards. Read-your-writes if the customer can change something on that page.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q4-explain-consistency-models-which-would-you-choose-for-an-order-status-page-l3-flipkart-swiggy-style-product-companies)

??? question "A customer updates their address and immediately sees the old one. Why, and how do you fix it?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style

    The read went to an asynchronously replicated replica that hadn't caught up. Provide read-your-writes: route that user's reads to the primary for a short window, or return a version token on write and require a replica at least that fresh.

    [Full answer in chapter 3](../foundations/ch03-distributed-systems-fundamentals.md#q10-a-customer-updates-their-address-and-immediately-sees-the-old-one-why-and-how-do-you-fix-it-l3-flipkart-swiggy-style)


**[4. Twelve-Factor & Fifteen-Factor Apps](../foundations/ch04-twelve-factor.md)**

??? question "What does 'stateless' mean for a service that keeps WebSocket connections?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy-style

    Connections are inherently tied to a pod, but *state* must not be. Fan out updates through Redis or Kafka so any pod can push to its connected clients. Clients reconnect to any pod after a restart and resume from a cursor.

    [Full answer in chapter 4](../foundations/ch04-twelve-factor.md#q4-what-does-stateless-mean-for-a-service-that-keeps-websocket-connections-l3-flipkart-swiggy-style)


**[6. Service Decomposition](../foundations/ch06-service-decomposition.md)**

??? question "Design the services for a food delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds · Amazon

    Run through capabilities (catalog, ordering, payment, kitchen, delivery and dispatch, zones, promotions, notification, customer), state the integrators that keep cart, order and order lines together, and the disintegrators that split zones (read-heavy, latency-critical) from dispatch (CPU-heavy, per order) and payments (compliance). Show the checkout sync path, its availability, and which steps are async (kitchen, rider assignment, notifications). Mention that you'd start ordering as a modular monolith if the domain were new.

    [Full answer in chapter 6](../foundations/ch06-service-decomposition.md#q12-design-the-services-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds-amazon)


**[8. Spring Boot 4 Essentials for Microservices](../foundations/ch08-spring-boot-4-essentials.md)**

??? question "What happens when an old app version calls a versioned endpoint without a version?"
    <span class="badge badge-l2">L2</span> Swiggy-/Zomato-style product companies

    With `spring.mvc.apiversion.default` set, it gets the default version (FoodFleet: 1.0); without a default and with versioning required, it gets 400. Serve old clients a default, mark that version deprecated with `Deprecation`/`Sunset` headers, measure its traffic, and force-update the app before the sunset date.

    [Full answer in chapter 8](../foundations/ch08-spring-boot-4-essentials.md#q12-what-happens-when-an-old-app-version-calls-a-versioned-endpoint-without-a-version-l2-swiggy-zomato-style-product-companies)


### Governance & Organization


**[71. API Governance & Developer Portals](../governance-org/ch71-api-governance.md)**

??? question "How should FoodFleet version its APIs?"
    <span class="badge badge-l3">L3</span> Swiggy

    Additive changes need no version (consumers ignore unknown fields); breaking changes get a new version in the `API-Version` header (Spring Framework 7 supports header versioning), running side by side with the old one until its sunset. No versions in URLs.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q4-how-should-foodfleet-version-its-apis-l3-swiggy)

??? question "How do you know who calls your API?"
    <span class="badge badge-l3">L3</span> Uber

    From traffic, not declarations: a client identity on every call (`X-Client-Id`, mTLS identity, OAuth client ID) logged at the gateway or mesh. Measured: the catalog declared 6 consumers of payment-service; the log saw 8, and both undeclared ones used the deprecated operation.

    [Full answer in chapter 71](../governance-org/ch71-api-governance.md#q7-how-do-you-know-who-calls-your-api-l3-uber)


**[72. Platform Engineering & Golden Paths](../governance-org/ch72-platform-engineering.md)**

??? question "What goes into a service template?"
    <span class="badge badge-l3">L3</span> Swiggy

    Everything a service needs that isn't its business logic: build, Dockerfile with JVM flags, manifests (requests, probes, PDB), autoscaling and VPA, catalog entry, API description, CI with linting, dashboards and alerts. Here: 90 lines of platform files to 45 of starter code.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q5-what-goes-into-a-service-template-l3-swiggy)

??? question "How do you know where every service stands?"
    <span class="badge badge-l3">L3</span> Uber

    Conformance checks per decision, run in each repository's CI and shown as scorecards in the portal (Backstage Tech Insights, Cortex, OpsLevel). Measured across 12 services: from 0 to 12 of 12, with averages of 96%, 42% and 8% by origin.

    [Full answer in chapter 72](../governance-org/ch72-platform-engineering.md#q7-how-do-you-know-where-every-service-stands-l3-uber)


**[73. ADRs, Fitness Functions & Tech Radar](../governance-org/ch73-adrs-fitness-functions.md)**

??? question "How do ADRs and fitness functions relate?"
    <span class="badge badge-l3">L3</span> Swiggy

    The ADR says what and why; the function checks it. Each function names its ADR and each ADR lists its functions, so a failure explains itself and a decision has evidence. Measured: before this chapter, code checked 7 of FoodFleet's 72 decisions.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q5-how-do-adrs-and-fitness-functions-relate-l3-swiggy)

??? question "Static vs dynamic fitness functions: where does each run?"
    <span class="badge badge-l3">L3</span> Uber

    Static ones (dependencies, API compatibility, banned technologies) in every build: under 5 ms each here. Dynamic ones (latency, startup, resilience) in the pipeline against a deployment: about 8 s per release here, and noisy, so they need repeats.

    [Full answer in chapter 73](../governance-org/ch73-adrs-fitness-functions.md#q6-static-vs-dynamic-fitness-functions-where-does-each-run-l3-uber)


**[74. Maturity Model & Migration Roadmap](../governance-org/ch74-maturity-migration-roadmap.md)**

??? question "What is a modular monolith, and why go there first?"
    <span class="badge badge-l3">L3</span> Swiggy

    One deployable whose contexts have explicit boundaries and no cycles, with shared code that doesn't depend on business code. Splitting a tangled monolith distributes its cycles: every extraction order passed through 12–16 pairs of services depending on each other.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q4-what-is-a-modular-monolith-and-why-go-there-first-l3-swiggy)

??? question "How do you keep a migration from sliding back?"
    <span class="badge badge-l3">L3</span> Uber

    Fitness functions for each gate in every build: no new cycles between contexts, no platform → business imports, no new remote dependencies without an ADR. Track the numbers on a dashboard.

    [Full answer in chapter 74](../governance-org/ch74-maturity-migration-roadmap.md#q11-how-do-you-keep-a-migration-from-sliding-back-l3-uber)


**[75. Multi-Tenancy, Multi-Region & Compliance](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)**

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


**[76. Serverless & Function-Based Services](../governance-org/ch76-serverless-microservices.md)**

??? question "Why does the first request of a function take longer than later ones?"
    <span class="badge badge-l3">L3</span> Swiggy

    Classes it uses are loaded on first use and the code is interpreted. The thumbnail's first call took 56–110 ms against 20–26 ms warm; a training run that exercises the function moves that work into the AOT cache.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q4-why-does-the-first-request-of-a-function-take-longer-than-later-ones-l3-swiggy)

??? question "What is the cost crossover between functions and containers?"
    <span class="badge badge-l4">L4</span> Uber

    Where requests × (duration × memory × GB-second price + request fee) exceeds the cost of the smallest highly available container deployment. With measured times: about 106 light or 28–31 heavy requests a second against two containers.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q6-what-is-the-cost-crossover-between-functions-and-containers-l4-uber)

??? question "How do you handle a caller with a tight timeout on a function?"
    <span class="badge badge-l3">L3</span> Zomato

    Acknowledge quickly and process asynchronously (202 plus a queue), optimise cold starts, and add provisioned instances only if the objective still isn't met. A short keep-alive gave 25% cold starts on sparse traffic.

    [Full answer in chapter 76](../governance-org/ch76-serverless-microservices.md#q9-how-do-you-handle-a-caller-with-a-tight-timeout-on-a-function-l3-zomato)


**[77. AI-Native Microservices](../governance-org/ch77-ai-native-microservices.md)**

??? question "Why put an LLM gateway between services and model providers?"
    <span class="badge badge-l3">L3</span> Swiggy · Zomato

    To own credentials, deadlines, retries, limits, caching, routing and cost accounting in one place, instead of in every service's SDK configuration. Measured: per-feature limits alone decided whether 19–32 or 60 of 60 chats succeeded during a batch job.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q1-why-put-an-llm-gateway-between-services-and-model-providers-l3-swiggy-zomato)

??? question "How do you stop a batch job from starving interactive AI features?"
    <span class="badge badge-l3">L3</span> Uber

    Per-feature concurrency (or token-rate) limits, with batch capped below the provider limit, or separate capacity. A single fair queue removed errors but made chats wait 9.3 s; a cap of 4 of 8 kept them at 2.0 s.

    [Full answer in chapter 77](../governance-org/ch77-ai-native-microservices.md#q4-how-do-you-stop-a-batch-job-from-starving-interactive-ai-features-l3-uber)


### Observability


**[47. Observability Fundamentals](../observability/ch47-observability-fundamentals.md)**

??? question "Why can't metrics answer 'which users are affected'?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    Each label value multiplies the number of time series, so user IDs, order IDs or restaurant IDs can't be labels. Measured: a `restaurant_id` label with 1,000 values turned 265 series into 260,005 and took Prometheus from about 100 MB to 670 MB. Per-entity questions go to traces and wide events, which cost per record, not per distinct value.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q3-why-cant-metrics-answer-which-users-are-affected-l2-swiggy-flipkart)

??? question "Head sampling or tail sampling?"
    <span class="badge badge-l3">L3</span> Uber · Razorpay

    Head sampling decides at the start, by trace ID: cheap and stateless, but it keeps rare slow or failed requests only at the sampling rate. Tail sampling decides after the whole trace: it keeps every error and slow request, at the cost of buffering traces in a collector tier that routes by trace ID. Measured: 1% head sampling kept 2–4 of 248–285 slow traces; tail sampling (errors, slower than 300 ms, 1% baseline) kept all of them for 18–19% of the bytes.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q5-head-sampling-or-tail-sampling-l3-uber-razorpay)

??? question "How do you correlate logs, metrics and traces?"
    <span class="badge badge-l2">L2</span> Zomato · Walmart

    Propagate W3C trace context on every hop; put the trace ID in every log record and wide event; attach trace IDs to metric histograms as exemplars; use the same resource attributes (service.name, service.version, service.instance.id) on all signals.

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q6-how-do-you-correlate-logs-metrics-and-traces-l2-zomato-walmart)

??? question "Design FoodFleet's observability."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    OTLP from every service to a gateway collector tier; RED metrics per service, route and pod derived from 100% of spans; tail sampling keeping errors, slow traces and a baseline; structured logs and wide events with trace IDs; change events from the deploy pipeline; continuous JFR profiles; a cardinality budget and cost per signal per team; SLO alerts on metrics (chapter 51).

    [Full answer in chapter 47](../observability/ch47-observability-fundamentals.md#q12-design-foodfleets-observability-l4-swiggy-zomato-style-system-design-rounds)


**[48. Metrics with Micrometer](../observability/ch48-metrics-micrometer.md)**

??? question "Why can't you average percentiles across pods?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    A percentile isn't additive: the fleet's p99 depends on how every pod's whole distribution combines, which the per-pod p99s don't contain. Measured with one slow pod in four: the average of the pods' p99s was 137 ms, the true fleet p99 322 ms, `histogram_quantile` over summed buckets 317–318 ms. Publish histogram buckets and compute the percentile from their sum.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q2-why-cant-you-average-percentiles-across-pods-l2l3-swiggy-flipkart)

??? question "How does Spring decide the uri tag, and how does it go wrong?"
    <span class="badge badge-l3">L3</span> Walmart · Zomato

    On the server, the matched path pattern (or NOT_FOUND / REDIRECTION / UNKNOWN); on the client, the URI template given to RestClient. It goes wrong when code concatenates IDs into the template, or overrides the convention with the raw path. Measured: 10,000 requests gave 24 meters with path patterns, 15,954 with raw paths and 19,918 with concatenated client URIs.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q6-how-does-spring-decide-the-uri-tag-and-how-does-it-go-wrong-l3-walmart-zomato)

??? question "What does max-uri-tags do?"
    <span class="badge badge-l3">L3</span> Razorpay · Uber

    It caps the distinct uri values per meter name (100 by default), first come, first served: once full, new values are denied, with one warning. It stops the explosion but can hide new endpoints; fix the source of values and alert on the warning.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q7-what-does-max-uri-tags-do-l3-razorpay-uber)

??? question "Design FoodFleet's metrics."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Micrometer with Spring Boot's Observations for HTTP and business operations; histograms with SLO boundaries; a shared policy of filters and caps; business meters in binders; Prometheus with RED, USE and business recording rules; dashboards and SLO alerts (chapter 51) on those rules; a series budget per service; identifiers in traces and wide events, never in tags.

    [Full answer in chapter 48](../observability/ch48-metrics-micrometer.md#q12-design-foodfleets-metrics-l4-swiggy-zomato-style-system-design-rounds)


**[49. Distributed Tracing with OpenTelemetry](../observability/ch49-distributed-tracing-otel.md)**

??? question "How does a trace continue across threads?"
    <span class="badge badge-l2">L2</span> <span class="badge badge-l3">L3</span> Swiggy · PhonePe

    The context is bound to the thread; it must be captured when work is handed over and restored when it runs. The agent does this for JDK executors and async callbacks; bare threads and own queues need `Context.wrap`. Measured: executors, `CompletableFuture`, scheduled and virtual-thread executors kept the trace 200 of 200 times; `new Thread()`, `Thread.ofVirtual().start()` and a hand-rolled queue lost it 200 of 200.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q2-how-does-a-trace-continue-across-threads-l2l3-swiggy-phonepe)

??? question "Parent or link for a consumer span?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Uber · Zomato

    Parent when the message is handled promptly, one by one, as part of the same user action; a new trace with links when messages are batched, delayed for a long time, or many producers feed one consumer operation. OpenTelemetry's instrumentation calls the latter "receive telemetry".

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q4-parent-or-link-for-a-consumer-span-l3l4-uber-zomato)

??? question "How do you carry trace context through an outbox?"
    <span class="badge badge-l3">L3</span> Swiggy-style rounds

    Inject the current context into a `traceparent` (and optional `baggage`) column when the outbox row is written; the relay extracts it and either continues the trace or links to it when publishing, so the event's consumers join the order's trace.

    [Full answer in chapter 49](../observability/ch49-distributed-tracing-otel.md#q11-how-do-you-carry-trace-context-through-an-outbox-l3-swiggy-style-rounds)


**[50. Structured Logging & Correlation](../observability/ch50-structured-logging.md)**

??? question "What is the MDC, and what goes wrong with it?"
    <span class="badge badge-l2">L2</span> Swiggy · PhonePe

    It is a per-thread map that Logback copies into every event. It doesn't follow work to other threads, and values left on pooled threads mislabel later work. Measured: request IDs were missing on 200 of 200 lines from executors and new threads, and wrong on 99 of 200 after a task leaked one.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q2-what-is-the-mdc-and-what-goes-wrong-with-it-l2-swiggy-phonepe)

??? question "What fields should every log line have?"
    <span class="badge badge-l2">L2</span> Zomato · Accenture

    Timestamp (UTC, ISO-8601), level, logger, message, service name and version, environment, region or zone, and trace and span IDs. Event-specific fields go in as key-value pairs: order ID, amount, attempt. Leave out anything nobody queries.

    [Full answer in chapter 50](../observability/ch50-structured-logging.md#q8-what-fields-should-every-log-line-have-l2-zomato-accenture)


**[51. SLIs, SLOs, Error Budgets & Alerting](../observability/ch51-slo-alerting.md)**

??? question "What is an error budget, and what is it for?"
    <span class="badge badge-l3">L3</span> Swiggy · Razorpay

    1 − SLO: the failures you can afford, 43.2 minutes a month at 99.9%. It turns reliability into a shared currency. While there is budget, ship. When it is running out, slow down and fix reliability. The error budget policy writes that down in advance, agreed with product.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q2-what-is-an-error-budget-and-what-is-it-for-l3-swiggy-razorpay)

??? question "Why not alert on error ratio > 1%?"
    <span class="badge badge-l2">L2</span> Zomato · Amazon

    It has no relationship to the budget. It misses slow failures and pages on brief ones. Measured: it never fired for failure rates of 0.6%, 0.25% and 0.15% that spent 14.5–43% of the month's budget.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q5-why-not-alert-on-error-ratio-1-l2-zomato-amazon)

??? question "Why not alert when the error ratio exceeds the SLO?"
    <span class="badge badge-l3">L3</span> Uber · Atlassian

    Noise and duration. Random variation crosses the line on healthy services (3 times a week here), and any burn over 1× keeps it on for as long as it lasts (more than 5,700 minutes for a 4-day burn). That trains people to silence it.

    [Full answer in chapter 51](../observability/ch51-slo-alerting.md#q6-why-not-alert-when-the-error-ratio-exceeds-the-slo-l3-uber-atlassian)


**[52. Health Checks & Actuator Hardening](../observability/ch52-health-checks-actuator.md)**

??? question "Should the liveness probe check the database?"
    <span class="badge badge-l2">L2</span> Swiggy · Flipkart

    No. A restart can't fix the database, and every replica fails together. Measured: during a 60-second outage, every pod restarted twice, `/menu` (no database) failed more than half the time, and orders came back 14–95 seconds after the database. Keep shared dependencies in `/actuator/health` for monitoring.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q2-should-the-liveness-probe-check-the-database-l2-swiggy-flipkart)

??? question "Why do deploys fail requests even with graceful shutdown?"
    <span class="badge badge-l3">L3</span> Uber · Zomato

    Endpoint removal is asynchronous. SIGTERM arrives while kube-proxies and load balancers still route new connections to the pod. Graceful shutdown finishes in-flight work but refuses new connections. Measured: 79–96 failures per rolling restart with graceful shutdown alone, 0 with a 5-second `preStop` sleep.

    [Full answer in chapter 52](../observability/ch52-health-checks-actuator.md#q5-why-do-deploys-fail-requests-even-with-graceful-shutdown-l3-uber-zomato)

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

??? question "What replaces the thread pool's limit when you move to virtual threads?"
    <span class="badge badge-l3">L3</span> Zomato

    An explicit one: a bulkhead, `@ConcurrencyLimit` or an adaptive limit per endpoint and dependency, sized by Little's law. Measured: unbounded, 86–93% of requests failed at 2,500/s and the event loop ran out of memory; a limit of 300 served 1,510–1,588/s at a p99 of 261–303 ms.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q4-what-replaces-the-thread-pools-limit-when-you-move-to-virtual-threads-l3-zomato)

??? question "Why is fan-out bad for tail latency?"
    <span class="badge badge-l3">L3</span> Google · Uber

    The request waits for the slowest call, so it's slow if any call is: 1 − (1 − p)ⁿ. Measured with 1% slow calls: 5% of requests slow at 5 calls, 17–19% at 20, 39–41% at 50; at 20 calls, the p90 was the slow path.

    [Full answer in chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md#q6-why-is-fan-out-bad-for-tail-latency-l3-google-uber)


**[67. JVM Tuning in Containers](../performance-scalability/ch67-jvm-tuning-containers.md)**

??? question "Which collector does the JVM choose by default?"
    <span class="badge badge-l3">L3</span> Swiggy

    G1 on a "server-class machine": at least 2 CPUs and 2 GB less 256 MB, so 1,792 MB. Otherwise Serial. Measured: a 2-CPU, 1 GiB container got Serial and a 256 MB heap. Set the collector explicitly.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q2-which-collector-does-the-jvm-choose-by-default-l3-swiggy)

??? question "G1, ZGC or Parallel?"
    <span class="badge badge-l3">L3</span> Atlassian · Uber

    G1 as the balanced default (longest pause 85–112 ms here). ZGC when pauses must be under a millisecond and the pod has CPU and memory to spare (measured: about 30% more CPU, 1.3 GB RSS against 0.95). Parallel for throughput-only batch work (lowest CPU). Never Serial for a service with a large live set (655–704 ms pauses).

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q5-g1-zgc-or-parallel-l3-atlassian-uber)

??? question "Why did ZGC make latency worse?"
    <span class="badge badge-l3">L3</span> Zomato

    Its collection is concurrent, so it needs CPU while the application runs. On one CPU it competed with requests: pauses of 0.1 ms, p99 106–136 ms, against G1's 68–92 ms. Concurrent collectors trade CPU for pauses.

    [Full answer in chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md#q6-why-did-zgc-make-latency-worse-l3-zomato)


**[68. Horizontal Scaling & Partitioning](../performance-scalability/ch68-horizontal-scaling.md)**

??? question "You scaled a deployment and the new pods get no traffic. Why?"
    <span class="badge badge-l3">L3</span> Swiggy

    Callers reuse connections opened before the new pods existed, and an L4 Service never moves them. Measured: after scaling 2 → 4 with keep-alive, the new copies got 0%. Use per-request balancing (mesh, gRPC client-side) and a maximum connection age.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q4-you-scaled-a-deployment-and-the-new-pods-get-no-traffic-why-l3-swiggy)

??? question "What is consistent hashing, and why virtual nodes?"
    <span class="badge badge-l3">L3</span> Amazon · Uber

    Nodes and keys on a hash ring; a key belongs to the next node. Adding a node moves only the keys it takes over (measured: 20% going 4 → 5, against mod n's 80%). With one point per node the arcs are uneven (busiest node 2.26× the average); 256 points per node brought that to 1.10×.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q6-what-is-consistent-hashing-and-why-virtual-nodes-l3-amazon-uber)

??? question "How do you handle a hot key?"
    <span class="badge badge-l3">L3</span> Zomato

    Hashing can't split one key. Use a finer key if ordering allows (order ID: perfectly even), or salt the hottest keys over sub-keys. Measured at Zipf s = 1.0 over 12 partitions: by restaurant 19.0% on one partition; with the top 10 salted over 4, 12.2%; by order ID, 8.4%.

    [Full answer in chapter 68](../performance-scalability/ch68-horizontal-scaling.md#q9-how-do-you-handle-a-hot-key-l3-zomato)


**[69. High-Throughput Kafka Tuning](../performance-scalability/ch69-kafka-throughput-tuning.md)**

??? question "Why does adding consumers sometimes not reduce lag?"
    <span class="badge badge-l2">L2</span> Swiggy

    A partition goes to one member of a group, so a group's parallelism is capped by the partition count. Measured: 12 consumers on 6 partitions gave the same 4,800–4,900 records a second as 6, with 6 idle.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q5-why-does-adding-consumers-sometimes-not-reduce-lag-l2-swiggy)

??? question "How do you process faster without more partitions?"
    <span class="badge badge-l3">L3</span> Zomato

    Concurrency inside the consumer, keyed so each key stays in order (Confluent Parallel Consumer, or a keyed executor), or batching the work per poll. Measured: 32 key-ordered lanes gave 16,600–16,800 records a second from one consumer; one write per poll gave 62,000–63,000.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q6-how-do-you-process-faster-without-more-partitions-l3-zomato)

??? question "What does acks=all cost?"
    <span class="badge badge-l3">L3</span> Uber

    Waiting for every in-sync replica, so latency rises by the slowest follower's fetch, typically a few milliseconds within a region. It isn't measured here (the test broker had no replicas). With `min.insync.replicas=2` it's what makes an acknowledged record survive a broker loss, so FoodFleet keeps it.

    [Full answer in chapter 69](../performance-scalability/ch69-kafka-throughput-tuning.md#q9-what-does-acksall-cost-l3-uber)


**[70. Cost Optimization (FinOps)](../performance-scalability/ch70-finops.md)**

??? question "Why are Kubernetes clusters usually over-provisioned?"
    <span class="badge badge-l3">L3</span> Swiggy · Flipkart

    Nodes are bought for requests, and requests are guesses padded "to be safe" and copied from templates. Measured: workloads used 3–10% of their requested CPU and 5–21% of their memory; right-sized, the fleet needed 8–9 nodes instead of 32.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q2-why-are-kubernetes-clusters-usually-over-provisioned-l3-swiggy-flipkart)

??? question "Why can't a bursty job be right-sized like a service?"
    <span class="badge badge-l3">L3</span> Uber

    Its p90 is its burst. Measured: a job averaging 0.37 CPU used both cores 20% of the time; VPA recommended 2.12–2.13 CPUs. Save by scheduling it (spot, off-peak, a shared batch pool), not by shrinking it.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q5-why-cant-a-bursty-job-be-right-sized-like-a-service-l3-uber)

??? question "When is spot capacity safe?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    For work that can lose copies without losing users: stateless services above an on-demand base that carries the load alone, batch jobs with checkpoints, CI. Measured: all-spot served 0–1% of requests during a reclaim; 3 on-demand + 3 spot served 55–61% because the remaining copies were overloaded.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q7-when-is-spot-capacity-safe-l3-zomato-phonepe)

??? question "What are unit economics, and which unit?"
    <span class="badge badge-l3">L3</span> Swiggy

    Cost per unit of business value: per 1,000 orders, per delivery, per active restaurant. It shows whether costs grow faster than the business and lets teams compare designs. Track it per team beside SLOs.

    [Full answer in chapter 70](../performance-scalability/ch70-finops.md#q8-what-are-unit-economics-and-which-unit-l3-swiggy)


### Resilience


**[42. Failure Modes & Cascading Failures](../resilience/ch42-failure-modes.md)**

??? question "How should a server shed load?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    Cheaply and early, by what predicts failure: queue time and the caller's deadline. Bounded concurrency with fast rejection, then drop queued work that has waited longer than the callers can wait. Measured: shedding at the client's timeout (300 ms) didn't recover; at 150 ms the service recovered the moment the trigger ended.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q6-how-should-a-server-shed-load-l3-flipkart-swiggy)

??? question "Design FoodFleet to recover from a five-minute dependency slowdown."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Deadlines on every hop; retries only at the edge or within per-dependency budgets with full jitter; explicit concurrency limits that reject; queue-age shedding below callers' timeouts; circuit breakers on the slow dependency with a degraded path; P2C balancing; ≥ 20% headroom; dashboards for goodput and queue time; and a load test that injects the slowdown and checks goodput returns when it ends.

    [Full answer in chapter 42](../resilience/ch42-failure-modes.md#q12-design-foodfleet-to-recover-from-a-five-minute-dependency-slowdown-l4-swiggy-zomato-style-system-design-rounds)


**[43. Resilience4j & Spring Framework 7 Resilience](../resilience/ch43-resilience4j-deep-dive.md)**

??? question "Count-based or time-based window?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    A count window reacts in "N calls", which is fast for busy endpoints and slow for quiet ones: measured, the 100-call default opened in 0.5–0.6 s at 100/s and 7.5–10 s at 5/s. A time window judges the same span at any rate (5 s opened in 2–4 s at both); add a minimum number of calls so a few failures on a quiet endpoint don't open it.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q2-count-based-or-time-based-window-l3-flipkart-swiggy)

??? question "Design resilience for FoodFleet's order-service calls."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Classify dependencies: menus (fallback to a stale cache), payments (no fallback, idempotent retries only), recommendations (empty fallback). Per dependency: HTTP connect/read timeouts; a time-window breaker with a slow-call threshold and a short open state; a Little's-law bulkhead that rejects; retries at one layer on transient failures with full jitter, ignoring refusals; fallback on the outermost layer; metrics and alerts on transitions; the policy as a shared library tested against the real library on each upgrade.

    [Full answer in chapter 43](../resilience/ch43-resilience4j-deep-dive.md#q12-design-resilience-for-foodfleets-order-service-calls-l4-swiggy-zomato-style-system-design-rounds)


**[44. Timeouts, Retries, Backpressure & Load Shedding](../resilience/ch44-timeouts-retries-backpressure.md)**

??? question "What is a timeout budget?"
    <span class="badge badge-l3">L3</span> Google-style rounds · Swiggy

    The original caller's deadline, shared out along the call chain: each hop gets what is left, so inner timeouts are always shorter than outer ones and retries come out of the same pot. It stops services working for callers who have already given up.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q2-what-is-a-timeout-budget-l3-google-style-rounds-swiggy)

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

??? question "Design FoodFleet's order-service for a 2× lunch spike."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Deadlines from the edge; per-hop timeouts from the remaining budget; admission control first in the chain (AIMD, partitioned by priority, 503 + `Retry-After`); recommendations behind a dimmer and limiting themselves; hedged menu reads within a 5% budget; retries at one layer within budgets; bounded buffers on event consumers; dashboards by priority; and a load test at 2× that passes on checkout goodput and recovery.

    [Full answer in chapter 44](../resilience/ch44-timeouts-retries-backpressure.md#q12-design-foodfleets-order-service-for-a-2-lunch-spike-l4-swiggy-zomato-style-system-design-rounds)


**[45. Chaos Engineering](../resilience/ch45-chaos-engineering.md)**

??? question "How do you limit the blast radius?"
    <span class="badge badge-l3">L3</span> Amazon · Swiggy

    Start small and widen: one instance (`mode: one`), a share of connections (`toxicity`), staging before production, short durations, business hours. Measured: the same fault on both instances instead of one hurt 93–94% of users during the experiment.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q4-how-do-you-limit-the-blast-radius-l3-amazon-swiggy)

??? question "Which faults would you inject first?"
    <span class="badge badge-l3">L3</span> Uber · Zomato

    The ones that produce hangs rather than errors: black holes (requests swallowed, connections held) and slow trickles, then latency, then pod kills and refused connections. Measured: fast failures were handled by every client; three of four client setups hung on a trickle.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q6-which-faults-would-you-inject-first-l3-uber-zomato)

??? question "Design a chaos programme for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Define each critical journey's SLO and a per-second probe; build a catalogue of hypotheses per journey (slow and black-holed dependencies, pod and zone loss, a slow database); run them in CI with Toxiproxy and in staging as Chaos Mesh workflows with SLO guards, one instance at a time; validate manifests strictly; schedule the experiments that found something weekly; quarterly game days; graduate held experiments to production with on-call present.

    [Full answer in chapter 45](../resilience/ch45-chaos-engineering.md#q12-design-a-chaos-programme-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


**[46. Disaster Recovery & Multi-Region](../resilience/ch46-disaster-recovery.md)**

??? question "How long does a failover take, and where does the time go?"
    <span class="badge badge-l3">L3</span> Swiggy · PhonePe

    Detection (probe interval × threshold), fencing, promotion, and clients finding the new primary. Measured: promotion took 0.1 s; detection 3.1 s with 1 s probes and 30.1 s with 10 s probes; clients asking each host for its role resumed 0.3 s after promotion, clients following a 30 s DNS TTL after 24–33 s. Tune probes and clients before the database.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q6-how-long-does-a-failover-take-and-where-does-the-time-go-l3-swiggy-phonepe)

??? question "Active-active or active-passive?"
    <span class="badge badge-l4">L4</span> Uber · Zomato

    Active-passive is simpler and loses at most the replication lag; active-active removes regional failover time but needs every write's conflicts designed away: home regions per tenant, conflict-free data types, or idempotent merges. Most systems mix them: carts or presence active-active, orders and payments with one home region.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q10-active-active-or-active-passive-l4-uber-zomato)

??? question "Design disaster recovery for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Tier the services; Tier 0 with quorum-synchronous standbys in other zones and automatic, fenced zone failover; an asynchronous standby and streamed WAL in a second region, promoted by runbook after fencing; multi-host clients with bounded timeouts; per-region ID ranges; cells by city; monitored lag and archive age; monthly timed rehearsals of failover and point-in-time restore.

    [Full answer in chapter 46](../resilience/ch46-disaster-recovery.md#q12-design-disaster-recovery-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


### Security


**[35. Security Fundamentals & Threat Modeling](../security/ch35-security-fundamentals.md)**

??? question "Threat-model a food-delivery checkout."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    DFD with customer app, restaurant tablet, gateway, order and payment services, their stores, the event topic, the payment provider and restaurant webhooks, across internet, edge, services, data and partner zones. Key threats: BOLA on orders, tampering with amounts (never trust client prices), payment callback spoofing (verify signatures), SSRF through webhooks, card data exposure (provider tokens only), denial of service on the gateway and provider (rate limits, circuit breakers, outbox retries), repudiation of refunds (audit log with caller). Record mitigations and acceptances; check them in the build.

    [Full answer in chapter 35](../security/ch35-security-fundamentals.md#q12-threat-model-a-food-delivery-checkout-l4-swiggy-zomato-style-system-design-rounds)


**[36. OAuth 2.1, OIDC & JWT](../security/ch36-oauth2-oidc-jwt.md)**

??? question "Design authentication and authorization for a food-delivery platform."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    One identity provider (Keycloak) for customers, restaurant staff, riders and staff tools; authorization code + PKCE in apps, BFF for web; JWT access tokens with audience per API, 15 minutes, rotating refresh tokens; client credentials for batch jobs; token exchange for service calls on a user's behalf; introspection for payments and admin; staged key rotation; object-level authorization in each service (chapter 35).

    [Full answer in chapter 36](../security/ch36-oauth2-oidc-jwt.md#q12-design-authentication-and-authorization-for-a-food-delivery-platform-l4-swiggy-zomato-style-system-design-rounds)


**[37. Spring Security 7](../security/ch37-spring-security-7.md)**

??? question "Design security for FoodFleet's services with Spring."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Keycloak realm as code (roles, PKCE clients, audience mappers, token exchange); every service a resource server with a shared converter, scope and role URL rules, `denyAll()` last, method security per object, stateless; BFFs with `oauth2Login` and CSRF for web; security MockMvc tests in every pipeline; passwords only in Keycloak.

    [Full answer in chapter 37](../security/ch37-spring-security-7.md#q12-design-security-for-foodfleets-services-with-spring-l4-swiggy-zomato-style-system-design-rounds)


**[38. Service-to-Service Security](../security/ch38-service-to-service-security.md)**

??? question "Design service-to-service security for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    SPIRE issues one-hour SVIDs by attestation; Spring Boot SSL bundles with reload, mutual TLS required; a peer-policy filter (SVID rules and validity per request, deny by default per route) before token checks; client credentials per service for sagas, token exchange for user calls, certificate-bound tokens for payments; Istio STRICT with allow-nothing where the mesh runs; rotation on both sides, CA rollover with overlap, connection age capped; peer identity in every audit log.

    [Full answer in chapter 38](../security/ch38-service-to-service-security.md#q12-design-service-to-service-security-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


**[39. Secrets Management](../security/ch39-secrets-management.md)**

??? question "Design secrets management for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    OpenBao, HA and audited; Kubernetes auth with a role and policy per service; dynamic per-pod database credentials that renew and rotate, revocation that terminates sessions; third-party keys in KV v2, synced by ESO to Secrets mounted read-only and rotated with two versions; KMS v2 encryption at rest; no secrets in env vars, logs or dumps; an inventory with owners and ages; `revoke-prefix` in the incident runbook.

    [Full answer in chapter 39](../security/ch39-secrets-management.md#q12-design-secrets-management-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


**[40. API Security Hardening](../security/ch40-api-security.md)**

??? question "How do you rate limit across 20 instances?"
    <span class="badge badge-l3">L3</span> Amazon · Swiggy

    Keep the state in a shared store: one Redis key per client, updated atomically by a Lua script (GCRA), using Redis's clock. Local limits multiply by the instance count (measured: 1,036 through a 260 budget with four instances; Redis, 258–260). Decide the failure mode: a short timeout and a per-instance fallback of limit ÷ instances.

    [Full answer in chapter 40](../security/ch40-api-security.md#q2-how-do-you-rate-limit-across-20-instances-l3-amazon-swiggy)

??? question "One tenant is hurting all the others. What do you do?"
    <span class="badge badge-l3">L3</span> <span class="badge badge-l4">L4</span> Goldman Sachs · Swiggy

    Limits keyed by tenant (and client within a tenant) in front of the global one, then per-tenant resource limits further down (connection pools, queues, database statement timeouts). Measured: with only a global limit the other tenants got 0.2% of their requests; with per-tenant limits, 100%.

    [Full answer in chapter 40](../security/ch40-api-security.md#q4-one-tenant-is-hurting-all-the-others-what-do-you-do-l3l4-goldman-sachs-swiggy)

??? question "Harden FoodFleet's public API."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Edge: CDN with bot management and per-address limits; WAF with the CRS tuned for JSON. Service: two chains (bearer API without CSRF and with exact CORS; cookie BFF with Fetch Metadata and CSRF tokens); GCRA limits per client and tenant in Redis with a bounded fallback; typed DTOs, `FreeText`, body and parser limits. Data: row-level security per tenant, object checks in every query. Operations: false-positive tracking, limit dashboards per tenant, written fail modes.

    [Full answer in chapter 40](../security/ch40-api-security.md#q12-harden-foodfleets-public-api-l4-swiggy-zomato-style-system-design-rounds)


**[41. Supply-Chain Security & Compliance](../security/ch41-supply-chain-compliance.md)**

??? question "Design supply-chain security and compliance for FoodFleet."
    <span class="badge badge-l4">L4</span> Swiggy-/Zomato-style system design rounds

    Build: pinned actions, least-privilege tokens, repository manager, CycloneDX SBOM in every jar, CI gate with expiring waivers. Release: keyless signing of digests, SBOM and SLSA provenance attestations. Deploy: Kyverno enforcing identity, provenance, digests; OPA rules for data class, region (RBI), PCI namespace, DPDP retention. Operate: an inventory rescanning deployed SBOMs daily; crypto-shredding for erasure; incident runbooks with the 6/72-hour clocks; evidence per release for audits.

    [Full answer in chapter 41](../security/ch41-supply-chain-compliance.md#q12-design-supply-chain-security-and-compliance-for-foodfleet-l4-swiggy-zomato-style-system-design-rounds)


### Testing


**[61. Test Pyramid & Honeycomb](../testing/ch61-test-strategy.md)**

??? question "What is the testing honeycomb and why was it proposed?"
    <span class="badge badge-l2">L2</span> Swiggy · Zomato

    Spotify's shape for microservices: few "implementation detail" tests, mostly "integration" tests of each service in isolation through its API, and few "integrated" tests that depend on other services running. Because a microservice is small and most of its complexity is in how it talks to others, so that's where the bugs are.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q2-what-is-the-testing-honeycomb-and-why-was-it-proposed-l2-swiggy-zomato)

??? question "Pyramid or honeycomb for a microservice?"
    <span class="badge badge-l3">L3</span> Flipkart · Uber

    Measure where the service's bugs are. In the experiment, a pyramid without component tests caught 16 of 20 and a honeycomb without unit tests 15; each lost a different kind. Rules need unit tests at their edges; wiring needs integrated tests. Most microservices need a strong middle and unit tests for the rules they have.

    [Full answer in chapter 61](../testing/ch61-test-strategy.md#q5-pyramid-or-honeycomb-for-a-microservice-l3-flipkart-uber)


**[62. Unit, Slice & Testcontainers Tests](../testing/ch62-unit-slice-integration-tests.md)**

??? question "Why Testcontainers instead of H2?"
    <span class="badge badge-l2">L2</span> Razorpay · Swiggy

    Because H2 isn't the database you run. Measured: H2 2.5 in PostgreSQL mode failed 8 of 19 PostgreSQL features, including `ON CONFLICT (…)`, upserts, `jsonb`, arrays, partial indexes and `RETURNING`, and it hid most of a race that PostgreSQL showed.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q3-why-testcontainers-instead-of-h2-l2-razorpay-swiggy)

??? question "How do you test a 'once per customer' rule?"
    <span class="badge badge-l3">L3</span> Zomato · PhonePe

    With concurrent callers on the real database, asserting exactly one winner and no errors. Measured: check-then-insert passed every single-threaded test and raised 421–449 errors in 300 concurrent rounds; without the key, 172–186 rounds redeemed twice. `ON CONFLICT` had neither.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q8-how-do-you-test-a-once-per-customer-rule-l3-zomato-phonepe)

??? question "Should container reuse be on in CI?"
    <span class="badge badge-l3">L3</span> Uber

    No. A reused container isn't removed and keeps data between runs; CI should start clean. Testcontainers only reuses when `testcontainers.reuse.enable=true` is set in the user's environment, which is meant for developers' machines.

    [Full answer in chapter 62](../testing/ch62-unit-slice-integration-tests.md#q10-should-container-reuse-be-on-in-ci-l3-uber)


**[63. Contract Testing](../testing/ch63-contract-testing.md)**

??? question "What is can-i-deploy?"
    <span class="badge badge-l3">L3</span> Swiggy

    A broker query: has this version of a service been successfully verified against the versions of its partners deployed in the target environment? The Pact Broker answers yes only if every considered verification succeeded, and "unknown" if a required one is missing. It replaces "deploy and see" with a per-consumer answer.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q7-what-is-can-i-deploy-l3-swiggy)

??? question "A provider wants to remove a field. How do you know it's safe?"
    <span class="badge badge-l3">L3</span> Zomato

    Check the contracts: if no consumer's contract mentions the field, verification passes and it's safe. Measured: removing an unused field passed both contracts (correctly) while the OpenAPI diff called it breaking. If a consumer uses it, its contract fails and names the team to talk to.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q9-a-provider-wants-to-remove-a-field-how-do-you-know-its-safe-l3-zomato)

??? question "Contracts for asynchronous messages?"
    <span class="badge badge-l3">L3</span> Uber

    The same idea: Pact message pacts and Spring Cloud Contract messaging contracts describe the message a consumer expects, and the producer verifies that its code produces it. Combine with a schema registry's compatibility rules (chapter 27) for the format.

    [Full answer in chapter 63](../testing/ch63-contract-testing.md#q11-contracts-for-asynchronous-messages-l3-uber)


**[64. Component & End-to-End Testing](../testing/ch64-component-e2e-testing.md)**

??? question "Why are E2E tests flaky?"
    <span class="badge badge-l2">L2</span> Swiggy

    Shared environments, shared data and timing. Measured: in a shared staging environment, 1–5 of 12 suite runs passed because of other pipelines' data and stock; fixed sleeps failed 1–28% of asynchronous checks.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q2-why-are-e2e-tests-flaky-l2-swiggy)

??? question "Unique test data fixed the coupon failures. Why not the stock?"
    <span class="badge badge-l3">L3</span> Zomato

    Because stock isn't the test's data, it's the environment's state, shared by every test that orders that dish. Unique customers isolate what tests own; shared counters (stock, quotas, rate limits) need a fresh environment or a test design that avoids them.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q6-unique-test-data-fixed-the-coupon-failures-why-not-the-stock-l3-zomato)

??? question "How many E2E tests should a system have?"
    <span class="badge badge-l3">L3</span> Uber

    Few: the journeys that matter most and that nothing smaller can check (whole flows across services, real configuration, real infrastructure). Everything else belongs in unit, contract and component tests, which are faster and say where the problem is.

    [Full answer in chapter 64](../testing/ch64-component-e2e-testing.md#q7-how-many-e2e-tests-should-a-system-have-l3-uber)


**[65. Performance, Chaos & Architecture Tests](../testing/ch65-performance-chaos-arch-tests.md)**

??? question "What's the difference between a closed and an open load model?"
    <span class="badge badge-l3">L3</span> Flipkart · Swiggy

    A closed model has a fixed number of users who each wait for a response before sending again; an open model sends requests at a rate regardless. Real users of a web service are open. Measured: against a freezing service, the closed model's p99 was 15 ms and the open model's 942 ms.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q1-whats-the-difference-between-a-closed-and-an-open-load-model-l3-flipkart-swiggy)

??? question "What is coordinated omission?"
    <span class="badge badge-l3">L3</span> Azul · Uber

    A load generator that waits for responses stops sending during a stall, so it never records the requests that would have waited. Its percentiles are far too good. Fix it with an open model, or correct closed-model samples with HdrHistogram's expected interval (measured: p99 898–901 ms instead of 15 ms).

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q2-what-is-coordinated-omission-l3-azul-uber)

??? question "A dependency is slow. Retry or fall back?"
    <span class="badge badge-l3">L3</span> Zomato

    Time out quickly, then fall back if the business allows. Measured: one retry after a 300 ms timeout got 81% of orders through a half-slow dependency and 16% through a hung one; the last known price kept 100% at a 302–307 ms p99.

    [Full answer in chapter 65](../testing/ch65-performance-chaos-arch-tests.md#q8-a-dependency-is-slow-retry-or-fall-back-l3-zomato)
