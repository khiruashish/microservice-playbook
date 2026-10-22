---
title: "Service-to-Service Communication"
description: "How FoodFleet's services talk, generated from the platform model: which protocol for which kind of call and why (REST at the edge, GraphQL for the customer apps, gRPC inside, SSE and WebSocket for tracking, Kafka for everything that can wait), all 26 synchronous calls with their fallbacks, the 15 that have none yet, and the rules every call follows: deadlines, identity, idempotency keys and error documents."
progress: done
---

# Service-to-Service Communication

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

FoodFleet's services talk in two ways: **synchronous calls**, when the caller needs an answer to carry on, and **messages through Kafka**, when it doesn't. The design rule is to prefer messages, because a synchronous call couples the caller's availability to the callee's ([chapter 3](../foundations/ch03-distributed-systems-fundamentals.md)). The tables on this page are generated from the platform model ([how](index.md#how-these-pages-are-made)).

## Which protocol, and why

| Kind of call | Protocol | Why | Decision |
|---|---|---|---|
| Public and partner APIs | REST over HTTP, OpenAPI 3.1 | Every client and tool understands it; HTTP caching, standard errors | [ADR-021](../communication/ch21-rest-api-design.md) |
| The customer apps | GraphQL, in the customer BFF only | One request per screen instead of 12; persisted queries only | [ADR-024](../communication/ch24-graphql.md) |
| Inside, on latency-sensitive paths (ETA, dispatch, tracking) | gRPC | Binary, multiplexed, with deadlines that propagate and cancel | [ADR-023](../communication/ch23-grpc.md) |
| Inside, everything else | REST through HTTP service clients | Typed interfaces, consistent timeouts and errors | [ADR-022](../communication/ch22-http-clients.md) |
| Live tracking to customers | Server-sent events | One-way, survives proxies, resumes with event ids | [ADR-026](../communication/ch26-websockets-sse.md) |
| Positions from riders | One WebSocket per shift | Many small messages from one client | [ADR-026](../communication/ch26-websockets-sse.md) |
| Anything that can wait: events, saga steps, reference data | Kafka, through outboxes and inboxes | The sender doesn't need the receiver to be up | [ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md), [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md), [ADR-025](../communication/ch25-asynchronous-messaging.md) |

Services don't expose GraphQL to each other, and nothing public is gRPC.

The model's 26 synchronous calls, by protocol:

--8<-- "code/foodfleet/model/generated/protocols.md"

## Every synchronous call

The diagram is on the [architecture](architecture.md#2-containers) page. Here is each call, with what the caller does when it fails:

--8<-- "code/foodfleet/model/generated/calls.md"

## Calls with no fallback yet

[ADR-002](../foundations/ch02-characteristics-principles.md) says every synchronous dependency declares a degradation mode: what the caller does when the dependency is slow or down. The model records one for 6 calls. The other 15 (leaving out the apps' calls to the edge) are decisions still to make:

--8<-- "code/foodfleet/model/generated/open-fallbacks.md"

Three of these are worth a closer look:

- **`delivery-service` → `zone-service`** may not need to exist. Delivery already consumes the zone state topic ([ADR-019](../architecture-patterns/ch19-event-driven-architecture.md)), so it has a local copy of every zone. The call is one of the model's assumptions; if it's real, the copy is its fallback.
- **`eta-service`'s two calls** need no fallback of their own: every caller of `eta-service` already has one (hide the ETA, or send positions without it). Failing fast is the right answer, as long as the deadline is short.
- **`customer-bff` → `order-service`** can't have a fallback: there's no way to place an order without the service that places orders. That's why the [overview](index.md#what-the-model-says-about-availability) found that order-service and the three services in front of it each need about 99.975% availability for checkout to reach its SLO.

## Rules every call follows

These come with the service template ([ADR-072](../governance-org/ch72-platform-engineering.md)), so a new service follows them without deciding to.

### Deadlines, not just timeouts

The edge sets a deadline for the whole request. Each hop's timeout is the smaller of its own ceiling and the time left minus a reserve; a call with less than 20 ms left isn't made ([ADR-042](../resilience/ch42-failure-modes.md), [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md)). gRPC carries the deadline itself; HTTP calls carry it in an `X-Request-Deadline` header ([chapter 42](../resilience/ch42-failure-modes.md)'s `DeadlineFilter`). Retries happen at one layer only, within a 10% budget, with full jitter. Idempotent reads may hedge after the dependency's p95, within a 5% budget.

### Admission control before work

Every service puts an AIMD admission limit first, split 90% for critical requests and 10% for sheddable ones; the edge marks priority by route. Over the limit, a request gets `503` with `Retry-After: 1` straight away instead of waiting in a queue ([ADR-044](../resilience/ch44-timeouts-retries-backpressure.md)). Checkout is critical; browsing and tracking are sheddable.

### Identity on every hop

- **The workload:** every service has a SPIFFE identity, and every connection is mutual TLS, checked against a deny-by-default policy per route ([ADR-038](../security/ch38-service-to-service-security.md)); Istio ambient enforces mTLS underneath ([ADR-057](../deployment/ch57-service-mesh.md)).
- **The user:** a call made for a user carries an exchanged token: one audience, minimal scopes, five minutes ([ADR-036](../security/ch36-oauth2-oidc-jwt.md)). Calls made by a service for itself use its own client-credentials token.

### Safe to repeat

- Every operation with side effects takes an `Idempotency-Key`, stored with the business write for 24 hours ([ADR-028](../communication/ch28-idempotency-correlation.md)).
- Updates to a resource require `If-Match` with its version ([ADR-021](../communication/ch21-rest-api-design.md)).
- Every message carries `event-id`, `correlation-id`, `causation-id` and `traceparent` ([ADR-028](../communication/ch28-idempotency-correlation.md)).

### Errors callers can parse

Every error is an RFC 9457 problem document with a documented `type` URI ([ADR-021](../communication/ch21-rest-api-design.md)). gRPC errors use standard status codes with details.

### Contracts in the repository

Every HTTP API has an OpenAPI document and every topic an AsyncAPI document, in the producer's repository. CI fails on breaking changes outside a new major version. Consumers that matter to customers publish contracts that run in the provider's build, and every consumer is a tolerant reader ([ADR-027](../communication/ch27-api-contracts.md), [ADR-063](../testing/ch63-contract-testing.md)).

## Messages

Most of FoodFleet's communication goes through Kafka, not through the calls above. Producers write through a transactional outbox; consumers commit after processing and deduplicate through an inbox ([ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md), [ADR-025](../communication/ch25-asynchronous-messaging.md)). [Kafka topic design](kafka-design.md) lists every topic, and [order lifecycle & sagas](order-lifecycle.md) follows an order through the saga's command channels.
