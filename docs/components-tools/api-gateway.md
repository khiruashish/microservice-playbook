---
title: "API Gateway"
description: "The single public entry point in front of FoodFleet's services: what a gateway should do (routing, token validation, header hygiene, request ids, rate and size limits, retries for idempotent reads, circuit breakers) and what it must not (business rules, aggregation). Spring Cloud Gateway Server Web MVC as FoodFleet runs it, the MVC-or-WebFlux and build-or-buy choices, and the edge settings that came from incidents."
progress: done
---

# API Gateway

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A reverse proxy for **north–south** traffic (clients to services) that routes, protects and observes every request |
| **FoodFleet uses** | Spring Cloud Gateway Server Web MVC 5.0 (Spring Cloud 2025.1), as `edge-gateway`, three replicas across zones |
| **Decided in** | [ADR-010](../architecture-patterns/ch10-api-gateway-bff.md#8-enterprise-case-study): one gateway for routing and protection; one BFF per client experience |
| **Learn it in** | [Chapter 10](../architecture-patterns/ch10-api-gateway-bff.md), [lab 04](../code-lab/lab-04-discovery-gateway.md) |
| **Related** | [Rate limiting](rate-limiting.md), [BFF](bff.md), [Gateway API & Ingress](gateway-api-ingress.md), [Load balancing](load-balancing.md) |

## What it's for

Before the gateway, FoodFleet exposed fourteen services publicly, each with its own authentication, CORS and error format, and the app had to change whenever services were split. A gateway gives the outside world one stable surface while services change behind it.

| Concern | Gateway | BFF | Domain service |
|---|:---:|:---:|:---:|
| TLS, routing, request ids | ✅ | | |
| Token validation, rate limits, request size limits | ✅ | | |
| Coarse authorization ("must be logged in") | ✅ | ✅ | |
| Fine authorization ("this order is yours") | | | ✅ |
| Aggregating several services for one screen | | ✅ | |
| Business rules (prices, refunds, availability) | | | ✅ |
| Retries | Idempotent reads only | Per call, with budgets | Per call, with budgets |

!!! pitfall "The gateway is not an ESB"
    Once a gateway validates coupons, computes prices or maps one service's response into another's request, it has become a shared monolith every team must change and nobody owns. Keep it generic.

## FoodFleet's routes, as code

```java title="EdgeRoutes.java (chapter 10)"
--8<-- "code/chapters/ch10-api-gateway-bff/gateway/src/main/java/dev/foodfleet/ch10/gateway/EdgeRoutes.java:19:79"
```

- **Routes in priority order**, most specific first: `POST /api/orders` has its own rate limit before the general `/api/orders/**` route.
- **Retries only for reads.** Menus are retried twice on 5xx with backoff; payments never are, because a repeated `POST` is only safe with an idempotency key, which is the client's and payment-service's contract ([idempotency store](idempotency-store.md)).
- **A circuit breaker with a fast fallback** for menus, so a struggling menu-service is answered from a fallback instead of queueing requests at the edge.
- **Header hygiene on every route**: strip client-supplied internal headers, add a request id ([chapter 28](../communication/ch28-idempotency-correlation.md)).

The edge's own settings, from the same module:

```yaml title="application.yml (chapter 10, excerpt)"
--8<-- "code/chapters/ch10-api-gateway-bff/gateway/src/main/resources/application.yml:1:30"
```

Bodies and headers are capped before anything reads them; ADR-040 sets the body limit at 256 KB for the public API ([chapter 40](../security/ch40-api-security.md#8-enterprise-case-study)).

## Choices

**Web MVC or WebFlux.** Spring Cloud Gateway comes in two servers. FoodFleet uses **Web MVC** with virtual threads: filters are plain functions, blocking calls are allowed, and it matches the services' golden path. Chapter 10 planned to keep WebFlux for the live-tracking stream; [ADR-026](../communication/ch26-websockets-sse.md#8-enterprise-case-study) later gave tracking its own `tracking-gateway`, also on Spring MVC with virtual threads, which held 10,000 streams at about 50 KB each ([WebSockets & SSE](websockets-sse.md)).

| Option | Strengths | Watch out for |
|---|---|---|
| **Spring Cloud Gateway** | Routes and filters in Java, same tooling as the services | You operate it; custom logic tempts business rules |
| Envoy-based (Envoy Gateway, Istio ingress) | High performance, native Kubernetes Gateway API | Extensions in Lua, Wasm or external services |
| Kong, Apigee, Tyk | Developer portal, API keys, monetisation | Licence cost; configuration outside your codebase |
| Cloud API gateways | Managed, integrated with cloud identity | Per-request pricing; vendor-specific configuration |

Many organisations run two layers: an Envoy-based or cloud **ingress** for TLS and coarse routing ([Gateway API & Ingress](gateway-api-ingress.md)), and an application gateway for API policies. More layers than that add latency and places for configuration to drift.

## Pitfalls

- **Rate limits per instance.** With three gateway replicas, a local limit lets three times the budget through; [chapter 40](../security/ch40-api-security.md) measured four instances letting 1,036 requests through a 260-request budget. Share the limit in Redis ([rate limiting](rate-limiting.md)).
- **Retrying non-idempotent requests** at the edge duplicates orders and payments.
- **The gateway as a single point of failure.** Three replicas across zones, a PodDisruptionBudget, and changes reviewed like any service's.
- **Internal calls through the gateway.** East–west traffic goes service to service (through the mesh), not out and back in.

## Where next

- Build one in [lab 04](../code-lab/lab-04-discovery-gateway.md); protect it in [lab 13](../code-lab/lab-13-redis.md) (rate limits).
- Interview questions: [chapter 10, section 9](../architecture-patterns/ch10-api-gateway-bff.md#9-interview-qa-summary).
