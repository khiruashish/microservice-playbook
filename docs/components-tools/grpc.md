---
title: "gRPC"
description: "gRPC for FoodFleet's latency-sensitive and streaming service-to-service APIs, built into Spring Boot 4.1: proto contracts where field numbers are the contract, unary and server-streaming RPCs, deadlines that propagate (cutting work done after the app gave up from 639 ms to 2 ms per request), status codes, schema checks with buf, and balancing per request because kube-proxy balances connections. Measured: protobuf 2.5× smaller than JSON raw, but a gzipped JSON stream was smaller still, so size alone is no reason to choose it."
progress: done
---

# gRPC

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | An RPC framework over HTTP/2: services and messages defined in Protocol Buffers, code generated per language, unary and streaming calls, deadlines and cancellation built in |
| **FoodFleet uses** | Spring Boot 4.1's built-in Spring gRPC (`@GrpcService`, `@ImportGrpcClients`) for tracking, eta and dispatch, and for every streaming API; `.proto` files in the `foodfleet-apis` repository with `buf lint` and `buf breaking` in CI |
| **Decided in** | [ADR-023](../communication/ch23-grpc.md#8-enterprise-case-study): internal latency-sensitive and streaming APIs use gRPC; the edge stays REST |
| **Learn it in** | [Chapter 23](../communication/ch23-grpc.md) |
| **Related** | [REST clients](http-service-clients.md), [WebSockets & SSE](websockets-sse.md), [Load balancing](load-balancing.md), [Envoy](envoy.md) |

## The contract

```protobuf title="tracking.proto (chapter 23)"
--8<-- "code/chapters/ch23-grpc/src/main/proto/foodfleet/tracking/v1/tracking.proto"
```

**Field numbers are the contract.** On the wire a field is its number and type, never its name: renaming a field is safe, reusing a number is not. Readers skip unknown fields, so adding fields is compatible; removing one means `reserved`, so its number is never reused. Enum value 0 must mean "not set". `buf breaking` in CI enforces all of this against the last released version.

## Calling it: always with a deadline

```java title="TrackingClient.java (chapter 23)"
--8<-- "code/chapters/ch23-grpc/src/main/java/dev/foodfleet/ch23/grpc/TrackingClient.java:13:38"
```

```yaml title="application.yml (chapter 23)"
--8<-- "code/chapters/ch23-grpc/src/main/resources/application.yml:1:10"
```

A deadline is absolute and **travels with the call**: each hop gets what's left, and when it runs out every service in the chain cancels. [Chapter 23](../communication/ch23-grpc.md) simulated tracking → eta → maps with the app's deadline at 500 ms and maps slow on 60% of calls:

| Mode | Succeeded | Maps busy per request | Work after the app gave up | p99 time held |
|---|---:|---:|---:|---:|
| Fixed 1 s timeout per hop | 39.3% | 928 ms | **639 ms** | 1,841 ms |
| Propagated deadline | 39.3% | 291 ms | **2 ms** | 504 ms |

Users see the same success rate; what changes is the wasted load on the dependency that is already struggling. FoodFleet's servers reject calls that arrive without a deadline (ADR-023).

## Measured: is protobuf smaller?

| Bytes on the wire | Protobuf | JSON | JSON / protobuf |
|---|---:|---:|---:|
| One `OrderStatus` | **104** | 255 | 2.45× |
| One `OrderStatus`, gzip | 124 | 217 | 1.75× |
| A ride: 360 positions as separate messages | **16,920** | 43,114 | 2.55× |
| The ride, whole stream gzipped | 5,558 | **3,539** | 0.64× |

Gzip made a single small protobuf message bigger, and a gzipped JSON stream beat protobuf, because gzip removes exactly the repeated field names that make JSON large. Choose gRPC for the contract and code generation, streaming, deadlines and cheaper parsing, not for size.

## Status codes

| Code | Means | The caller |
|---|---|---|
| `DEADLINE_EXCEEDED` | Out of time somewhere in the chain | Gives up; retrying needs time left |
| `UNAVAILABLE` | Transient: the server or connection is down | May retry, on another instance |
| `NOT_FOUND`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION` | The request is wrong for the current state | Doesn't retry |
| `RESOURCE_EXHAUSTED` | Rate or capacity limit | Backs off |
| `INTERNAL`, `UNKNOWN` | A bug | Doesn't retry; alerts |

## Balancing on Kubernetes

One HTTP/2 connection carries every call, so a ClusterIP Service (which balances connections) sends everything to one pod. Balance per request: through the mesh (ADR-057), or with a headless Service, the `dns:///` resolver and the `round_robin` policy, re-resolving periodically ([load balancing](load-balancing.md)). The FoodFleet chart exposes port 9090 for gRPC providers (`grpc: true`).

## When REST is still the answer

| Choose REST | Choose gRPC |
|---|---|
| Public and partner APIs, browsers (gRPC-Web needs a proxy) | Internal calls on a latency budget |
| HTTP caching, CDNs, simple tooling (`curl`) | Server or bidirectional streaming |
| Many consumers you don't control | Deadlines and cancellation across hops |

## Pitfalls

- **Reusing a field number**: old clients decode new data as the wrong field, silently.
- **No deadline**: a call can wait forever, holding a thread and a stream in every hop.
- **Connection-level balancing**: one pod busy, the rest idle.
- **Proxies, WAFs and dashboards that only know HTTP/1.1**: they need to learn HTTP/2 and gRPC status in trailers.
- **Debugging without reflection**: enable server reflection in non-production for `grpcurl`.
