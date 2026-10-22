---
title: "WebSockets & SSE"
description: "Pushing live updates to FoodFleet's apps: Server-Sent Events for the customer's tracking screen, a WebSocket for the rider's position stream, push notifications for the background. Measured over real sockets: polling saw a quarter of the updates at about 1,190 bytes each, SSE and WebSocket every update at 100–120 bytes; virtual threads held 10,000 streams at about 50 KB each where a thread pool held 200; and jittered reconnects recovered 200,000 clients in 9.9 s instead of 20.4 s."
progress: done
---

# WebSockets & SSE

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Long-lived connections over which the server sends updates as they happen: **SSE** (one-way, plain HTTP, `text/event-stream`) and **WebSocket** (two-way, its own framing after an HTTP upgrade) |
| **FoodFleet uses** | A dedicated `tracking-gateway` on Spring MVC with virtual threads: SSE (`SseEmitter`) for customers, a WebSocket per shift for riders, FCM/APNs push for the background |
| **Decided in** | [ADR-026](../communication/ch26-websockets-sse.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 26](../communication/ch26-websockets-sse.md) |
| **Related** | [gRPC](grpc.md) (streaming between services), [Kafka](kafka.md), [Redis / Valkey](redis-valkey.md) (fan-out), [Notification gateways](notification-gateways.md) |

## Measured: four ways to keep a map current

[Chapter 26](../communication/ch26-websockets-sse.md): a rider position published 40 times, every 250 ms, over real sockets; every request carries a 700-character bearer token, as the app's would.

| Transport | Requests | Positions seen | Bytes per position | Staleness mean / p95 |
|---|---:|---:|---:|---:|
| Short polling (1 s) | 12 | **10 / 40** | 1,191 | **564** / 1,000 ms |
| Long polling | 40 | 40 / 40 | 1,047 | 2.9 / 6.5 ms |
| Server-Sent Events | 1 | 40 / 40 | **120** | 1.3 / 2.7 ms |
| WebSocket | 1 | 40 / 40 | **101** | 1.0 / 2.3 ms |

Polling at FoodFleet's scale had been 60,000 requests a second at peak, with maps that jumped every 5 seconds. After ADR-026, a few hundred connects a second.

## Which one

| | SSE | WebSocket |
|---|---|---|
| Direction | Server to client | Both |
| Protocol | Plain HTTP; works through proxies, HTTP/2 multiplexes streams | Upgrade to its own protocol; proxies must allow it |
| Reconnect and resume | Built into the browser's `EventSource`: `retry:`, `Last-Event-ID` | Yours to write |
| Auth | Normal HTTP headers (outside browsers) | At the handshake only |
| FoodFleet | Customers watching an order | Riders sending a position a second |

## FoodFleet's SSE endpoint

```java title="TrackingStreamController.java (chapter 26)"
--8<-- "code/chapters/ch26-websockets-sse/src/main/java/dev/foodfleet/ch26/tracking/TrackingStreamController.java:19:76"
```

- **Event ids and a replay buffer** (50 events): a client that reconnects with `Last-Event-ID` gets what it missed.
- **A random `retry:` of 1–10 s per client**, so a fleet-wide disconnect doesn't come back in one second.
- **A heartbeat every 15 s**, so proxies don't close an idle connection.
- **503 with `Retry-After` at 20,000 streams** per instance: shed early, before memory runs out.

## Connections are the scaling unit

`CapacityServer` held SSE streams with a blocked thread each; 10,000 clients connected, then one event was broadcast:

| Server threads | Streams served | Received the event | Per stream |
|---|---:|---:|---:|
| 200 platform threads (pool) | **200** | 200 | — |
| Virtual thread per stream | **10,000** | **10,000** | **~48–50 KB** |

With a pool, the pool size is the connection limit; 9,800 clients connected at TCP level and never got a response. Next limits after memory: file descriptors (`ulimit -n`), the load balancer's connection limits, broadcast CPU.

## When everyone reconnects at once

A load balancer restart drops 200,000 tracking connections; the fleet can do 20,000 handshakes a second, so the best possible recovery is 10 s:

| Reconnect policy | 99% back after | Peak attempts/s | Wasted handshakes |
|---|---:|---:|---:|
| Immediately, then every 1 s | 20.4 s | 200,000 | **209,000** |
| Fixed 3 s (a browser's `EventSource` default) | 20.9 s | 200,000 | 135,400 |
| Exponential backoff, no jitter | 19.9 s | 200,000 | 155,600 |
| Exponential backoff, **full jitter** | **9.9 s** | **20,233** | **0** |

Backoff without randomness doesn't help when everyone backs off in step. For SSE, the server sets the jitter through `retry:`.

## Across instances

A client's stream lives on one instance; the update can arrive at any. FoodFleet routes positions through Kafka (`tracking-positions`, keyed by order) and per-order Redis pub/sub channels to whichever instance holds the stream. Deploys drain streams in batches over 60 s instead of dropping them all.

## Pitfalls

- **Streams on a thread pool**: the pool size becomes the user limit.
- **Proxies that buffer** (`X-Accel-Buffering: no` for nginx) or time out idle connections (heartbeats).
- **No resume**: every reconnect loses what happened in between.
- **Reconnect storms** from synchronised clients.
- **Relying on a stream when the app is in the background**: mobile OSes close it; push notifications carry the key moments.
