---
title: "Port & Naming Conventions"
description: "The conventions FoodFleet's services share, collected from the chapters and the platform model: ports, Kubernetes names and labels, topic names, HTTP headers, error types, Java packages and Maven coordinates, configuration properties and metrics. Where chapters used different conventions for the same thing, the table says which one the platform follows."
progress: done
---

# Port & Naming Conventions

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span></div>

A convention saves a decision every time someone creates a service. These are FoodFleet's, gathered from the chapters and the [platform model](../food-delivery-platform/index.md#how-these-pages-are-made). The service template applies most of them ([ADR-072](../governance-org/ch72-platform-engineering.md)).

## Ports

| Port | What | Where it's set |
|---|---|---|
| 8080 | The application's HTTP API | Every service; the Kubernetes Service exposes it as port 80 |
| 8081 | Spring Boot Actuator: health groups, `info`, `prometheus` | `management.server.port`; reachable only from the monitoring namespace ([ADR-052](../observability/ch52-health-checks-actuator.md)) |
| 9090 | gRPC (Spring gRPC's default) | gRPC providers only: `kitchen-service`, `dispatch-optimizer`, `eta-service` ([ADR-023](../communication/ch23-grpc.md)) |
| 8443 | HTTPS with mutual TLS, when a service terminates TLS itself | Chapter 38's SPIFFE examples ([ADR-038](../security/ch38-service-to-service-security.md)) |
| 8100–8120 | Services on a developer machine | The [local stack](../food-delivery-platform/docker-local-stack.md) maps each service to its own host port |

## Kubernetes

| Thing | Convention | Example |
|---|---|---|
| Deployment, Service, ServiceAccount | The service's name | `order-service` |
| Namespace | The service's name, except where chapter 38 named one | `orders`, `payments`, `menu-service` |
| Labels | `app`, `app.kubernetes.io/name`, `app.kubernetes.io/part-of: foodfleet`, `foodfleet.dev/team` | `foodfleet.dev/team: checkout` |
| ConfigMap | `<service>-config`, mounted at `/etc/foodfleet/config` | `order-service-config` |
| SPIFFE ID | `spiffe://foodfleet.internal/ns/<namespace>/sa/<service>` | `spiffe://foodfleet.internal/ns/orders/sa/order-service` |
| Istio principal | `cluster.local/ns/<namespace>/sa/<service>` | `cluster.local/ns/payments/sa/payment-service` |

The team label is what cost allocation and paging group by ([ADR-070](../performance-scalability/ch70-finops.md)).

## Kafka topics

| Kind | Convention | Example |
|---|---|---|
| State (compacted) | `foodfleet.<context>.<entity>` ([ADR-019](../architecture-patterns/ch19-event-driven-architecture.md)) | `foodfleet.catalog.menu-items` |
| Events | `foodfleet.<context>.<entity>-events` (proposed) | `foodfleet.ordering.order-events` |
| Saga commands | `foodfleet.<context>.commands` (proposed) | `foodfleet.kitchen.commands` |
| Saga replies | `foodfleet.<context>.saga-replies` (proposed) | `foodfleet.ordering.saga-replies` |
| Dead letters | `<topic>-dlt` ([ADR-025](../communication/ch25-asynchronous-messaging.md)) | `foodfleet.ordering.order-events-dlt` |
| Consumer group | The consuming service's name | `kitchen-service` |

The chapters used six names for two topics (`order-events`, `foodfleet.order.events`, `foodfleet.order.placed.v1`, `orders` and two for menus); the "proposed" rows are the convention [Kafka topic design](../food-delivery-platform/kafka-design.md#a-proposal) recommends to replace them. Versions live in the schema registry, not in topic names.

## HTTP headers

| Header | Direction | Meaning | Decision |
|---|---|---|---|
| `Idempotency-Key` | Request | Required on every non-idempotent operation with side effects; kept 24 h | [ADR-028](../communication/ch28-idempotency-correlation.md) |
| `If-Match` / `ETag` | Request / response | Optimistic concurrency on updates: 428 without, 412 when stale | [ADR-021](../communication/ch21-rest-api-design.md) |
| `Consistency-Token` | Response, then request | The primary's WAL position after a write; reads that need read-your-writes send it back | [ADR-031](../data/ch31-data-consistency.md) |
| `X-Request-Deadline` | Request | The caller's deadline, which every hop budgets from and never extends | [ADR-042](../resilience/ch42-failure-modes.md) |
| `X-FoodFleet-Priority` | Request | Critical or sheddable, set by the edge per route | [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md) |
| `traceparent`, `tracestate`, `baggage` | Request, message | W3C trace context; stripped from internet traffic at the edge | [ADR-049](../observability/ch49-distributed-tracing-otel.md) |
| `Retry-After` | Response | With 429 and 503: when to try again | [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md) |
| `Deprecation`, `Sunset`, `Link` | Response | On deprecated operations | [ADR-071](../governance-org/ch71-api-governance.md) |
| `X-Customer-Id`, `X-Rider-Id`, `X-Client-Id` | Request, edge to services | Identity verified by the gateway; services never trust these from outside | [ADR-024](../communication/ch24-graphql.md), [ADR-026](../communication/ch26-websockets-sse.md) |

Message headers on every Kafka record: `event-id`, `correlation-id`, `causation-id`, `traceparent` ([ADR-028](../communication/ch28-idempotency-correlation.md)).

## Errors

Every error is an RFC 9457 problem document (`application/problem+json`) with a `type` URI under `https://docs.foodfleet.example/problems/`, documented per API ([ADR-021](../communication/ch21-rest-api-design.md)). Examples from the chapters: `…/problems/invalid-cursor`, `…/problems/stale-version`. Objects a caller may not read answer 404, not 403, so existence doesn't leak ([ADR-035](../security/ch35-security-fundamentals.md)).

## Code

| Thing | Convention | Example |
|---|---|---|
| Maven group | `dev.foodfleet.handbook` (the handbook's modules) | `dev.foodfleet.handbook:ch14-saga` |
| Java packages | `dev.foodfleet.<chapter or service>.<part>` | `dev.foodfleet.ch14.saga` |
| Hexagonal layout | `domain`, `application`, `adapter.in.*`, `adapter.out.*` | [ch. 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md) |
| Money | `long` paise, never floating point | `pricePaise: 24900` |
| Time | `Instant` in UTC on the wire; a city's time zone only for business days | [ADR-034](../data/ch34-reporting-analytics.md) |
| HTTP client groups | One per destination, named after it without hyphens | `spring.http.serviceclient.menuservice.base-url` |
| gRPC channels | One per destination | `spring.grpc.client.channel.etaservice.target` |

## Configuration and metrics

| Thing | Convention |
|---|---|
| Configuration | Environment variables for what differs per environment; files in a watched directory for what changes live; never secrets in either ([ADR-004](../foundations/ch04-twelve-factor.md), [ADR-039](../security/ch39-secrets-management.md)) |
| Metric names | Micrometer's dotted names in code (`http.server.requests`), Prometheus's underscores in queries |
| Metric tags | `service`, `route` (the URI template), `status`, `pod`; never ids ([ADR-048](../observability/ch48-metrics-micrometer.md)) |
| SLO rule names | `slo:sli_error:ratio_rate<window>` with an `slo` label ([observability](../food-delivery-platform/observability.md#from-objectives-to-alerts)) |
| Log fields | ECS: `service.name`, `service.version`, `trace.id`, … ([ADR-050](../observability/ch50-structured-logging.md)) |
