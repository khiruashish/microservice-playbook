---
title: "GraphQL"
description: "GraphQL in FoodFleet's customer BFF with Spring for GraphQL: a schema clients query by shape, resolvers that batch every cross-service field (one screen took 19 backend calls with per-item resolvers and 3 batched), complexity and depth limits checked before execution (each nesting level multiplied the work by 10), persisted queries only in production, partial results with HTTP 200, and why services don't expose GraphQL to each other. Measured on the 'My orders' screen: 1 request instead of 12, 8× fewer bytes."
progress: done
---

# GraphQL

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A query language and runtime for APIs: one endpoint, a typed schema, and clients that ask for exactly the fields they need |
| **FoodFleet uses** | Spring for GraphQL (on graphql-java) in the customer BFF, one schema for iOS, Android and web; persisted queries only; complexity limit 2,000, depth 8, page size 50 |
| **Decided in** | [ADR-024](../communication/ch24-graphql.md#8-enterprise-case-study): the customer apps use GraphQL in the BFF; services keep their own protocols |
| **Learn it in** | [Chapter 24](../communication/ch24-graphql.md) |
| **Related** | [Backend for Frontend](bff.md), [REST clients](http-service-clients.md), [CDN](cdn.md) |

## Resolvers, batched

```java title="CustomerGraphController.java (chapter 24)"
--8<-- "code/chapters/ch24-graphql/src/main/java/dev/foodfleet/ch24/graph/CustomerGraphController.java:19:69"
```

- **`@QueryMapping`** resolves top-level queries; each calls one service.
- **`@BatchMapping`** resolves a field for every parent at once (a DataLoader underneath): ten orders, one call to restaurant-service.
- **The customer comes from the gateway's verified header** (`@ContextValue`), never from a query argument.

## Measured: one screen, four ways

[Chapter 24](../communication/ch24-graphql.md): the "My orders" screen, 10 recent orders with restaurant, rider and items; 80 ms mobile round trip, 6 ms per backend call.

| Approach | Phone requests | Response bytes (gzip) | Backend calls | Modelled latency |
|---|---:|---:|---:|---:|
| REST resources, composed by the app | 12 (2 waves) | 16,314 (4,834) | 12 | 184 ms |
| GraphQL, per-item resolvers | **1** | 2,579 (583) | **19** | 197 ms |
| GraphQL, batched (`@BatchMapping`) | **1** | 2,579 (583) | **3** | **101 ms** |
| BFF endpoint for this screen | 1 | 2,568 (575) | 3 | 93 ms |

**N+1 is the default**: with a resolver per item, GraphQL made 19 backend calls and was slower than plain REST. FoodFleet tests each screen's query for its backend call count.

## Clients choose the shape, so clients choose the cost

`restaurant → reviews(first: 10) → restaurant → …`, nested deeper each row; FoodFleet's complexity limit is 2,000:

| Levels | Depth | Complexity (static) | Calls, per-item | Calls, batched | With the limit |
|---:|---:|---:|---:|---:|---|
| 2 | 5 | 143 | 22 | 4 | allowed |
| 3 | 7 | 1,443 | 222 | 6 | allowed |
| 4 | 9 | 14,443 | 2,222 | 8 | **rejected up front** |
| 5 | 11 | 144,443 | 22,222 | 10 | **rejected up front** |

The static complexity, computed from the query document alone, predicted the work exactly, so the server refuses a query **before running any of it**. Batching keeps calls linear but not the work. Use complexity and depth limits together, and in production accept only **persisted queries** registered by app builds, so nobody can send an arbitrary query at all.

## Operating it

| Concern | What changes from REST |
|---|---|
| Errors | Partial: HTTP 200 with `data` and `errors`; monitor per operation and per field error, not by status code |
| Caching | One `POST` endpoint: CDN caching is mostly lost; client caches (normalised by id) and service caches replace it |
| Security | Introspection and GraphiQL off outside development; authorization in the services, not in the schema |
| Evolution | Add fields freely; deprecate with `@deprecated` and watch field usage before removing |
| Many teams | Federation (Apollo, or graphql-java's federation support) when more than one team owns types; FoodFleet deferred it |

## Where GraphQL fits

| Fits | Doesn't fit |
|---|---|
| Client-facing APIs whose screens change faster than the backend | Service-to-service calls (REST, gRPC, events instead) |
| Several clients needing different shapes of the same data | Public APIs where caching and simple tooling matter most |
| Aggregating several services in a BFF | Bulk data transfer, file upload/download |

## Pitfalls

- **Per-item resolvers for cross-service fields**: N+1 calls hidden behind one request.
- **No limits**: one nested query can ask for millions of fields.
- **Treating HTTP 200 as success**: dashboards stay green while a field fails for everyone.
- **Business logic in resolvers**: the BFF becomes the domain layer.
