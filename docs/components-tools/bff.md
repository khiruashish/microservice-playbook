---
title: "Backend for Frontend"
description: "A small service per client experience, owned by the client's team, with screen-shaped endpoints or a GraphQL schema: FoodFleet's tracking screen went from four sequential calls over a mobile network (about 580 ms before anything rendered, and an error if any call failed) to one call of about 200 ms with partial results. How the mobile BFF aggregates in two phases on virtual threads within a 600 ms budget, what belongs in a BFF and what doesn't, and why FoodFleet's customer BFF moved from 140 REST endpoints to one GraphQL schema."
progress: done
---

# Backend for Frontend

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A service built for one kind of client (the customer app, the partner portal, the rider app) that exposes endpoints shaped like its screens |
| **FoodFleet uses** | Three BFFs (customer-bff, partner-bff, rider-bff), Spring Boot 4 with virtual threads, each owned by its client team, behind the [API gateway](api-gateway.md); the customer BFF serves GraphQL ([ADR-024](../communication/ch24-graphql.md#8-enterprise-case-study)) |
| **Decided in** | [ADR-010](../architecture-patterns/ch10-api-gateway-bff.md#8-enterprise-case-study), [ADR-024](../communication/ch24-graphql.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 10](../architecture-patterns/ch10-api-gateway-bff.md) |
| **Related** | [API gateway](api-gateway.md), [GraphQL](graphql.md), [REST clients & HTTP interfaces](http-service-clients.md) |

## Why: the tracking screen

The order-tracking screen needed the order, its ETA, the restaurant and the rider. The app fetched them one after another over a mobile network, and when any one failed, the screen showed an error although the other three had answered. [Chapter 10](../architecture-patterns/ch10-api-gateway-bff.md)'s arithmetic, with a 100 ms mobile round trip and 30–60 ms backend calls:

| | Calls over the mobile network | Time to render |
|---|---|---|
| App calls 4 services in sequence | 4 | ≈ **580 ms** |
| App calls 4 services in parallel | 4 (concurrent) | ≈ 160 ms, but 4 radios' worth, 4 TLS sessions, 4 failure points in the app |
| **App calls the BFF once** | 1 | ≈ **200 ms**, one failure point, partial results |

The other gains don't show in the timing: the BFF binds 5 of the order's 30 fields, so payloads shrink; a display bug is fixed once for every app version; and partial failure is handled in one place.

## How FoodFleet's mobile BFF aggregates

```java title="TrackingScreenService.java (chapter 10)"
--8<-- "code/chapters/ch10-api-gateway-bff/mobile-bff/src/main/java/dev/foodfleet/ch10/bff/tracking/TrackingScreenService.java:20:78"
```

- **Two phases**: the order is required (no order, no screen); ETA, restaurant and rider are optional and fetched in parallel.
- **One budget for the optional parts** (600 ms), not one timeout each: whatever hasn't answered by then is left out and named in `failed`, and the app shows what it has.
- **Virtual threads** make the parallel calls plain blocking code ([chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md)).
- **A missing rider is a state, not an error**: `404` means not assigned yet.

## What belongs in a BFF

| Belongs | Doesn't belong |
|---|---|
| Calling several services for one screen, in parallel, with partial results | Business rules: prices, refunds, availability (they stay in the services) |
| Formatting for display: times, currency, labels, the client's language | Data the BFF owns: a BFF has no database of record |
| Versioned paths that follow app releases (`/bff/mobile/v3/...`) | Calls between BFFs |
| Coarse authorization; passing the user's token on | Fine authorization: "this order is yours" is the service's decision |

Some duplication between BFFs is accepted (two BFFs call the same services); a shared "BFF library" that grows business logic is how the pattern decays back into a monolith.

## BFF and GraphQL

They aren't alternatives so much as two ways to build the same layer. FoodFleet started with REST endpoints per screen; by 2024 the customer BFF had 140 of them, versioned per app release, and the web team was building a second BFF with overlapping endpoints. [ADR-024](../communication/ch24-graphql.md#8-enterprise-case-study) turned the **customer BFF into one GraphQL schema** for iOS, Android and web; the partner and rider BFFs are outside that decision.

| | REST endpoints per screen | GraphQL schema in the BFF |
|---|---|---|
| A new screen or redesign | A new endpoint, versioned with the app | Usually a new query, no backend change |
| Partial results | Written by hand | Built in (`data` plus `errors`) |
| Cost control | Each endpoint's cost is known | Complexity and depth limits, persisted queries |
| HTTP caching | Per URL, at the CDN | Mostly lost; client and service caches instead |

Chapter 24 measured the "My orders" screen: the app composing REST resources took 12 requests in two waves (modelled 184 ms); GraphQL with batched resolvers took one request and 3 backend calls (101 ms), close to a hand-written BFF endpoint (93 ms). Without batching, GraphQL made 19 backend calls and was slower than REST. See [GraphQL](graphql.md).

## Pitfalls

- **A BFF per platform instead of per experience**: iOS and Android show the same screens, so one customer BFF serves both.
- **A BFF that calls services sequentially** loses most of the latency gain.
- **No budget**: the screen waits for its slowest optional part.
- **BFF-to-BFF calls** couple client teams again.
