### `edge-gateway`

**edge** · no bounded context of its own · owned by **Platform**

The only public entry point - routing, token validation, rate limits (GCRA in Redis), size limits, request ids, retries for idempotent reads, circuit breakers. No business logic.

| | |
|---|---|
| Built with | Spring Cloud Gateway Server Web MVC |
| Called by | `customer-app` (GraphQL), `partner-portal` (REST), `rider-app` (REST) |
| Calls | `customer-bff` (GraphQL), `partner-bff` (REST), `rider-bff` (REST) |
| Publishes | – |
| Consumes | – |
| Stores | redis: rate-limit counters (copies, ephemeral) |
| Decided in | [ADR-010](../architecture-patterns/ch10-api-gateway-bff.md "one API gateway for routing and protection; one BFF per client experience"), [ADR-040](../security/ch40-api-security.md "Rate limits per client and tenant in Redis (GCRA) with a bounded fallback; typed DTOs and FreeText validation; exact-origin CORS on /api, Fetch Metadata and csrf.spa() on /bff; CRS at PL2 tuned for JSON; PostgreSQL row-level security for pooled tenants"), [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md "Deadline budgets per hop; hedges and retries within budgets; AIMD admission control partitioned by priority at every service; brownouts for optional features; bounded buffers everywhere") |
| Code in | no chapter's code names it |

### `customer-bff`

**bff** · no bounded context of its own · owned by **Checkout (assumed)** · also called `mobile-bff`

One GraphQL schema for the iOS, Android and web customer apps, per-screen batching, persisted queries only.

| | |
|---|---|
| Built with | Spring for GraphQL |
| Called by | `edge-gateway` (GraphQL) |
| Calls | `menu-service` (REST), `zone-service` (REST; if it fails: none - serviceability is required), `eta-service` (gRPC; if it fails: hide the ETA), `order-service` (REST) |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-010](../architecture-patterns/ch10-api-gateway-bff.md "one API gateway for routing and protection; one BFF per client experience"), [ADR-024](../communication/ch24-graphql.md "The customer apps use a GraphQL API in the mobile BFF; services keep their own protocols") |
| Code in | no chapter's code names it |

### `partner-bff`

**bff** · no bounded context of its own · owned by **Restaurant Partner**

Per-screen endpoints for the restaurant partner portal (REST, session cookie with Fetch Metadata and csrf.spa()).

| | |
|---|---|
| Built with | Spring MVC |
| Called by | `edge-gateway` (REST) |
| Calls | `menu-service` (REST), `kitchen-service` (REST) |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-010](../architecture-patterns/ch10-api-gateway-bff.md "one API gateway for routing and protection; one BFF per client experience"), [ADR-040](../security/ch40-api-security.md "Rate limits per client and tenant in Redis (GCRA) with a bounded fallback; typed DTOs and FreeText validation; exact-origin CORS on /api, Fetch Metadata and csrf.spa() on /bff; CRS at PL2 tuned for JSON; PostgreSQL row-level security for pooled tenants") |
| Code in | no chapter's code names it |

### `rider-bff`

**bff** · no bounded context of its own · owned by **Logistics**

Per-screen endpoints for the rider app.

| | |
|---|---|
| Built with | Spring MVC |
| Called by | `edge-gateway` (REST) |
| Calls | `delivery-service` (REST) |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-010](../architecture-patterns/ch10-api-gateway-bff.md "one API gateway for routing and protection; one BFF per client experience") |
| Code in | no chapter's code names it |

### `tracking-gateway`

**edge** · no bounded context of its own · owned by **Logistics** · also called `tracking-service`

Live tracking - customers over SSE (replay buffer, heartbeats, 20,000 streams per instance), riders over one WebSocket per shift.

| | |
|---|---|
| Built with | Spring MVC on virtual threads, SSE, WebSocket |
| Called by | `customer-app` (SSE), `rider-app` (WebSocket) |
| Calls | `eta-service` (gRPC; if it fails: positions without an ETA) |
| Publishes | `tracking-positions` |
| Consumes | – |
| Stores | redis: per-order pub/sub channels and live rider positions (ephemeral) |
| Decided in | [ADR-026](../communication/ch26-websockets-sse.md "Customers receive tracking over SSE, riders send positions over WebSocket, through a dedicated tracking gateway"), [ADR-029](../data/ch29-polyglot-persistence.md "PostgreSQL by default; Redis for copies and ephemeral state; nothing else without measurements") |
| Code in | [chapter 18](../architecture-patterns/ch18-resilience-patterns.md) (7), [chapter 22](../communication/ch22-http-clients.md) (4), [chapter 23](../communication/ch23-grpc.md) (3), [chapter 26](../communication/ch26-websockets-sse.md) (1) |

### `order-service`

**service** · Ordering context · owned by **Checkout** · also called `ordering`, `ordering-service`

Cart and orders; runs the place-order saga as its orchestrator (kitchen acceptance is the pivot).

| | |
|---|---|
| Called by | `customer-bff` (REST), `orders-mcp` (REST) |
| Calls | `pricing-service` (REST; if it fails: none - no order without a price), `promotions-service` (REST; if it fails: place the order without the coupon) |
| Publishes | `order-events`, `saga commands: payment.authorize / capture / void`, `saga commands: kitchen.accept`, `saga commands: delivery.request-rider`, `saga commands: coupon reserve / release` |
| Consumes | `foodfleet.catalog.menu-items`, `payment-events` |
| Stores | postgresql: orders, order lines, carts, saga log, outbox, inbox |
| Decided in | [ADR-005](../foundations/ch05-domain-driven-design.md "Ordering protects its model with an anti-corruption layer towards Catalog"), [ADR-014](../architecture-patterns/ch14-saga-pattern.md "Place-order is an orchestrated saga with the kitchen as pivot"), [ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md "Every service publishes through a transactional outbox; every consumer uses an inbox"), [ADR-021](../communication/ch21-rest-api-design.md "FoodFleet REST APIs use keyset pagination, conditional updates and RFC 9457 errors"), [ADR-028](../communication/ch28-idempotency-correlation.md "Keys for side effects, versions for state, and three ids on every message"), [ADR-030](../data/ch30-distributed-transactions.md "No distributed transactions between services; outbox-driven sagas with idempotent participants"), [ADR-031](../data/ch31-data-consistency.md "Session guarantees with consistency tokens; atomic updates; CRDTs for multi-writer data") |
| Code in | [chapter 5](../foundations/ch05-domain-driven-design.md) (111), [chapter 51](../observability/ch51-slo-alerting.md) (107), [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md) (19), [chapter 7](../foundations/ch07-conways-law-team-topologies.md) (19), [chapter 71](../governance-org/ch71-api-governance.md) (17), [chapter 12](../architecture-patterns/ch12-database-per-service.md) (17) | and 32 more

### `menu-service`

**service** · Catalog context · owned by **Restaurant Partner** · also called `catalog`, `catalog-service`, `restaurant-service`

Restaurants, menus, availability and item prices; one JSONB menu card per restaurant rebuilt in the same transaction; dish search in PostgreSQL full-text with a trigram fallback.

| | |
|---|---|
| Called by | `customer-bff` (REST), `partner-bff` (REST), `menus-mcp` (REST) |
| Calls | `pricing-service` (REST; if it fails: last known or base price (fail open)) |
| Publishes | `foodfleet.catalog.menu-items` |
| Consumes | `foodfleet.zones.zone` |
| Stores | postgresql: restaurants, menus, menu cards, outbox |
| Decided in | [ADR-012](../architecture-patterns/ch12-database-per-service.md "one owner per table; schema per service by default; composition for display joins"), [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md "Reference data is shared by event-carried state transfer on compacted topics"), [ADR-029](../data/ch29-polyglot-persistence.md "PostgreSQL by default; Redis for copies and ephemeral state; nothing else without measurements"), [ADR-032](../data/ch32-caching-strategies.md "Cache by purpose: evict-after-commit for details, versioned write-through for prices, stale-while-revalidate for rankings, write-behind for counters") |
| Code in | [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md) (51), [chapter 71](../governance-org/ch71-api-governance.md) (24), [chapter 5](../foundations/ch05-domain-driven-design.md) (24), [chapter 29](../data/ch29-polyglot-persistence.md) (23), [chapter 55](../deployment/ch55-helm-kustomize.md) (21), [chapter 58](../deployment/ch58-cicd-gitops.md) (18) | and 24 more

### `kitchen-service`

**service** · Kitchen context · owned by **Restaurant Partner**

Kitchen tickets - accept or reject orders, prep times; the saga's pivot step.

| | |
|---|---|
| Called by | `eta-service` (gRPC), `partner-bff` (REST) |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | `order-events`, `saga commands: kitchen.accept` |
| Stores | postgresql: tickets, inbox, outbox |
| Decided in | [ADR-014](../architecture-patterns/ch14-saga-pattern.md "Place-order is an orchestrated saga with the kitchen as pivot"), [ADR-025](../communication/ch25-asynchronous-messaging.md "Consumers commit after processing, are idempotent, and dead-letter instead of skipping or looping") |
| Code in | [chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md) (8), [chapter 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md) (2), [chapter 14](../architecture-patterns/ch14-saga-pattern.md) (2), [chapter 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md) (1), [chapter 8](../foundations/ch08-spring-boot-4-essentials.md) (1) |

### `delivery-service`

**service** · Delivery context · owned by **Logistics**

Rider assignment requests and rider wallets (event-sourced, read models by projection).

| | |
|---|---|
| Called by | `rider-bff` (REST) |
| Calls | `dispatch-optimizer` (gRPC), `zone-service` (REST) |
| Publishes | `foodfleet.delivery.rider-status`, `wallet-events` |
| Consumes | `order-events`, `foodfleet.zones.zone`, `saga commands: delivery.request-rider` |
| Stores | postgresql: assignments, wallet event store and projections, outbox |
| Decided in | [ADR-013](../architecture-patterns/ch13-cqrs-event-sourcing.md "Rider wallets are event-sourced; read models via projections"), [ADR-014](../architecture-patterns/ch14-saga-pattern.md "Place-order is an orchestrated saga with the kitchen as pivot") |
| Code in | [chapter 71](../governance-org/ch71-api-governance.md) (8), [chapter 64](../testing/ch64-component-e2e-testing.md) (3), [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) (1), [chapter 14](../architecture-patterns/ch14-saga-pattern.md) (1), [chapter 12](../architecture-patterns/ch12-database-per-service.md) (1), [chapter 4](../foundations/ch04-twelve-factor.md) (1) |

### `zone-service`

**service** · Delivery context · owned by **Logistics** · also called `delivery-zones`

Delivery zones and serviceability, read on every menu browse; separate from delivery for its scaling profile.

| | |
|---|---|
| Called by | `customer-bff` (REST), `delivery-service` (REST) |
| Calls | nothing synchronously |
| Publishes | `foodfleet.zones.zone` |
| Consumes | – |
| Stores | postgresql: zones, outbox |
| Decided in | [ADR-006](../foundations/ch06-service-decomposition.md "FoodFleet decomposes by business capability, verified by scoring and change coupling"), [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md "Reference data is shared by event-carried state transfer on compacted topics") |
| Code in | [chapter 5](../foundations/ch05-domain-driven-design.md) (2), [chapter 4](../foundations/ch04-twelve-factor.md) (2), [chapter 6](../foundations/ch06-service-decomposition.md) (1) |

### `dispatch-optimizer`

**service** · Delivery context · owned by **Dispatch** · also called `dispatch-service`

Rider-to-order matching behind a versioned dispatch API.

| | |
|---|---|
| Called by | `eta-service` (gRPC), `delivery-service` (gRPC) |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | `tracking-positions`, `foodfleet.delivery.rider-status` |
| Stores | postgresql: rider history partitioned by day (BRIN)<br>redis: live rider positions (ephemeral) |
| Decided in | [ADR-007](../foundations/ch07-conways-law-team-topologies.md "FoodFleet organises around streams of change (Team Topologies)"), [ADR-023](../communication/ch23-grpc.md "Internal latency-sensitive and streaming APIs use gRPC; the edge stays REST"), [ADR-029](../data/ch29-polyglot-persistence.md "PostgreSQL by default; Redis for copies and ephemeral state; nothing else without measurements") |
| Code in | [chapter 7](../foundations/ch07-conways-law-team-topologies.md) (6), [chapter 71](../governance-org/ch71-api-governance.md) (2), [chapter 8](../foundations/ch08-spring-boot-4-essentials.md) (1) |

### `promotions-service`

**service** · Promotions context · owned by **Growth** · also called `promotions`, `coupon-service`

Coupons and offers; reserves and releases coupons as a saga participant.

| | |
|---|---|
| Called by | `order-service` (REST) |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | `saga commands: coupon reserve / release` |
| Stores | postgresql: coupons, redemptions, inbox, outbox |
| Decided in | [ADR-014](../architecture-patterns/ch14-saga-pattern.md "Place-order is an orchestrated saga with the kitchen as pivot") |
| Code in | [chapter 71](../governance-org/ch71-api-governance.md) (13), [chapter 7](../foundations/ch07-conways-law-team-topologies.md) (7), [chapter 14](../architecture-patterns/ch14-saga-pattern.md) (5), [chapter 41](../security/ch41-supply-chain-compliance.md) (1), [chapter 31](../data/ch31-data-consistency.md) (1), [chapter 6](../foundations/ch06-service-decomposition.md) (1) |

### `customer-service`

**service** · Customer context · owned by **Growth** · also called `customer`

Customer profiles and address books.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | – |
| Stores | postgresql: profiles, addresses |
| Decided in | [ADR-012](../architecture-patterns/ch12-database-per-service.md "one owner per table; schema per service by default; composition for display joins") |
| Code in | [chapter 30](../data/ch30-distributed-transactions.md) (33), [chapter 31](../data/ch31-data-consistency.md) (22), [chapter 77](../governance-org/ch77-ai-native-microservices.md) (20), [chapter 64](../testing/ch64-component-e2e-testing.md) (17), [chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md) (16), [chapter 21](../communication/ch21-rest-api-design.md) (15) | and 27 more

### `notification-service`

**service** · Notification context · owned by **Growth**

Push, SMS and email from business events; idempotent per message.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | `order-events`, `wallet-events` |
| Stores | postgresql: notification log, inbox |
| Decided in | [ADR-025](../communication/ch25-asynchronous-messaging.md "Consumers commit after processing, are idempotent, and dead-letter instead of skipping or looping") |
| Code in | [chapter 27](../communication/ch27-api-contracts.md) (4), [chapter 71](../governance-org/ch71-api-governance.md) (2), [chapter 28](../communication/ch28-idempotency-correlation.md) (1) |

### `payment-service`

**service** · Payment context · owned by **Payments**

Authorize, capture, void and refund through provider adapters (ports and adapters), behind the platform's payment ambassador.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | `payment-provider` (REST) |
| Publishes | `payment-events` |
| Consumes | `saga commands: payment.authorize / capture / void` |
| Stores | postgresql: payments, idempotency keys, inbox, outbox |
| Sidecars | `payment-ambassador` |
| Decided in | [ADR-003](../foundations/ch03-distributed-systems-fundamentals.md "Consistency is chosen per data type, and money is never eventually consistent"), [ADR-009](../architecture-patterns/ch09-hexagonal-clean-architecture.md "payment-service uses ports and adapters; provider integrations are adapters"), [ADR-017](../architecture-patterns/ch17-sidecar-ambassador-adapter.md "Payment calls go through a platform-owned ambassador; vendor metrics through adapters"), [ADR-028](../communication/ch28-idempotency-correlation.md "Keys for side effects, versions for state, and three ids on every message") |
| Code in | [chapter 71](../governance-org/ch71-api-governance.md) (17), [chapter 38](../security/ch38-service-to-service-security.md) (15), [chapter 35](../security/ch35-security-fundamentals.md) (13), [chapter 39](../security/ch39-secrets-management.md) (9), [chapter 30](../data/ch30-distributed-transactions.md) (5), [chapter 41](../security/ch41-supply-chain-compliance.md) (4) | and 8 more

### `pricing-service`

**service** · Pricing context · owned by **Checkout (assumed)**

Quotes, surcharges and surge multipliers, extracted from the monolith by strangler fig.

| | |
|---|---|
| Called by | `menu-service` (REST), `order-service` (REST) |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | `foodfleet.catalog.menu-items` |
| Stores | postgresql: price rules<br>redis: price cache (version-guarded copies) |
| Decided in | [ADR-016](../architecture-patterns/ch16-strangler-fig-migration.md "Pricing leaves the monolith by strangler fig, with shadowing and cohort canaries"), [ADR-029](../data/ch29-polyglot-persistence.md "PostgreSQL by default; Redis for copies and ephemeral state; nothing else without measurements"), [ADR-032](../data/ch32-caching-strategies.md "Cache by purpose: evict-after-commit for details, versioned write-through for prices, stale-while-revalidate for rankings, write-behind for counters") |
| Code in | [chapter 2](../foundations/ch02-characteristics-principles.md) (14), [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md) (9), [chapter 71](../governance-org/ch71-api-governance.md) (2), [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) (1) |

### `eta-service`

**service** · Delivery context · owned by **Logistics (assumed)**

When will my food arrive? Fans out to kitchen and dispatch with deadlines.

| | |
|---|---|
| Called by | `customer-bff` (gRPC), `tracking-gateway` (gRPC) |
| Calls | `kitchen-service` (gRPC), `dispatch-optimizer` (gRPC) |
| Publishes | – |
| Consumes | `foodfleet.delivery.rider-status` |
| Stores | nothing of its own |
| Decided in | [ADR-008](../foundations/ch08-spring-boot-4-essentials.md "Spring Boot 4.1 baseline for all FoodFleet services"), [ADR-023](../communication/ch23-grpc.md "Internal latency-sensitive and streaming APIs use gRPC; the edge stays REST") |
| Code in | [chapter 71](../governance-org/ch71-api-governance.md) (2), [chapter 8](../foundations/ch08-spring-boot-4-essentials.md) (2), [chapter 23](../communication/ch23-grpc.md) (1) |

### `reporting-service`

**service** · Reporting context · owned by **Platform (assumed)**

Cross-service reports from a reporting store fed by change events; every report returns its as-of time.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | `order-events`, `foodfleet.catalog.menu-items`, `payment-events`, `wallet-events` |
| Stores | postgresql: reporting store (facts by key and version, materialized views) |
| Decided in | [ADR-034](../data/ch34-reporting-analytics.md "Reports come from a reporting store built from change events, never from the services' databases") |
| Code in | [chapter 34](../data/ch34-reporting-analytics.md) (1) |

### `llm-gateway`

**platform** · no bounded context of its own · owned by **Platform**

Every model call - stream-aware deadlines, retries with retry-after, per-feature limits, caching, cost accounting.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-077](../governance-org/ch77-ai-native-microservices.md "all model calls go through FoodFleet's LLM gateway, which holds provider credentials and applies stream-aware deadlines (first token, idle, total), backoff that honours retry-after, per-feature concurrency limits that keep batch work below interactive work, exact and normalised response caching keyed by model, template version, tenant and user, and per-feature token and cost accounting; semantic caching is used only where a decision and an evaluation on near-miss pairs allow it; tools for assistants are MCP servers on the stateless Streamable HTTP transport (protocol 2026-07-28), catalogued, OAuth-protected, validating headers against bodies, and taking identity only from the token") |
| Code in | no chapter's code names it |

### `orders-mcp`

**service** · no bounded context of its own · owned by **Checkout (assumed)**

MCP server (stateless Streamable HTTP) exposing order tools to assistants; identity from the token.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | `order-service` (REST) |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-077](../governance-org/ch77-ai-native-microservices.md "all model calls go through FoodFleet's LLM gateway, which holds provider credentials and applies stream-aware deadlines (first token, idle, total), backoff that honours retry-after, per-feature concurrency limits that keep batch work below interactive work, exact and normalised response caching keyed by model, template version, tenant and user, and per-feature token and cost accounting; semantic caching is used only where a decision and an evaluation on near-miss pairs allow it; tools for assistants are MCP servers on the stateless Streamable HTTP transport (protocol 2026-07-28), catalogued, OAuth-protected, validating headers against bodies, and taking identity only from the token") |
| Code in | no chapter's code names it |

### `menus-mcp`

**service** · no bounded context of its own · owned by **Restaurant Partner (assumed)**

MCP server exposing menu search to assistants.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | `menu-service` (REST) |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-077](../governance-org/ch77-ai-native-microservices.md "all model calls go through FoodFleet's LLM gateway, which holds provider credentials and applies stream-aware deadlines (first token, idle, total), backoff that honours retry-after, per-feature concurrency limits that keep batch work below interactive work, exact and normalised response caching keyed by model, template version, tenant and user, and per-feature token and cost accounting; semantic caching is used only where a decision and an evaluation on near-miss pairs allow it; tools for assistants are MCP servers on the stateless Streamable HTTP transport (protocol 2026-07-28), catalogued, OAuth-protected, validating headers against bodies, and taking identity only from the token") |
| Code in | no chapter's code names it |

### `partner-webhooks`

**function** · no bounded context of its own · owned by **Restaurant Partner (assumed)**

Partner webhook receiver - acknowledge, then process asynchronously.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-076](../governance-org/ch76-serverless-microservices.md "event-driven, scheduled and bursty workloads run as functions (Spring Cloud Function beans on AWS Lambda, adapters at the edge), packaged with a JDK AOT cache from a representative training run and run with C1 and Serial GC; steady request/response APIs stay on containers; scale-to-zero containers serve workloads that need one image for both; each workload's platform is chosen from its measured rate, service time and latency objective, and re-checked quarterly against the cost crossover") |
| Code in | no chapter's code names it |

### `menu-photo-processor`

**function** · no bounded context of its own · owned by **Restaurant Partner (assumed)**

Thumbnails for uploaded menu photos, in bursts.

| | |
|---|---|
| Called by | nobody synchronously |
| Calls | nothing synchronously |
| Publishes | – |
| Consumes | – |
| Stores | nothing of its own |
| Decided in | [ADR-076](../governance-org/ch76-serverless-microservices.md "event-driven, scheduled and bursty workloads run as functions (Spring Cloud Function beans on AWS Lambda, adapters at the edge), packaged with a JDK AOT cache from a representative training run and run with C1 and Serial GC; steady request/response APIs stay on containers; scale-to-zero containers serve workloads that need one image for both; each workload's platform is chosen from its measured rate, service time and latency objective, and re-checked quarterly against the cost crossover") |
| Code in | no chapter's code names it |
