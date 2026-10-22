---
title: "Load Balancing"
description: "Where FoodFleet's traffic gets spread, and at which level: kube-proxy balances connections, so a keep-alive client kept every request on one pod for 50 s after a scale-out, while per-request balancing in a mesh split it 333/333/334 within 10 s. Client-side balancing with Spring Cloud LoadBalancer, gRPC over headless Services, connection lifetime caps, and the algorithms (round robin, least requests, power of two choices) and when each matters."
progress: done
---

# Load Balancing

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Choosing which instance serves each connection or request |
| **FoodFleet uses** | Kubernetes Services (kube-proxy) for discovery; Istio waypoints for per-request balancing where it matters; connection lifetime caps in every HTTP client; client-side round robin for gRPC |
| **Decided in** | [ADR-011](../architecture-patterns/ch11-service-discovery-patterns.md#8-enterprise-case-study), [ADR-054](../deployment/ch54-kubernetes-for-spring.md#8-enterprise-case-study), [ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md), [chapter 54](../deployment/ch54-kubernetes-for-spring.md), [chapter 57](../deployment/ch57-service-mesh.md), [lab 04](../code-lab/lab-04-discovery-gateway.md) |
| **Related** | [Service discovery](service-discovery.md), [Envoy](envoy.md), [Istio](istio.md), [gRPC](grpc.md) |

## Connections or requests: the question that matters

There are three places a choice can be made, and they don't see the same thing:

| Where | Balances | Examples |
|---|---|---|
| **Server-side, L4** | Connections | Kubernetes ClusterIP (kube-proxy), cloud network load balancers |
| **Server-side, L7** | Requests | Envoy (mesh sidecar or waypoint), gateways, cloud application load balancers |
| **Client-side** | Requests | Spring Cloud LoadBalancer, gRPC's `round_robin` over a headless Service |

A connection-level balancer can only choose when a connection opens. A client with a keep-alive pool opens its connections once, so whatever it chose then, it keeps.

## Measured: the scale-out nobody used

[Chapter 54](../deployment/ch54-kubernetes-for-spring.md) scaled menu-service from 1 pod to 3 under 50 requests a second through a ClusterIP Service; the new pods were ready at about 14 s. Requests per pod per 10-second window:

| Window | New connection per request: pod 1 / 2 / 3 | Keep-alive pool: pod 1 / 2 / 3 |
|---|---|---|
| 0–10 s | 250 / 0 / 0 | 250 / 0 / 0 |
| 10–20 s | 147 / 62 / 41 | 250 / 0 / 0 |
| 30–60 s | 73–101 / 81–96 / 67–89 | **250 / 0 / 0** |

[Chapter 57](../deployment/ch57-service-mesh.md) repeated it through Envoy: **851–878 / 61–75 / 61–74** in the first window, then **333 / 333 / 334** in every window after. That is the autoscaling trap of a sale day: an HPA adds pods that existing clients never use.

## The fixes, cheapest first

1. **Cap connection lifetime in every client pool** (ADR-054: 30 s). Each reconnect goes through kube-proxy again and can land on a new pod. Reactor Netty: `maxLifeTime`; Apache HttpClient: `connectionTimeToLive`; the JDK client has an idle timeout (`jdk.httpclient.keepalive.timeout`) but no lifetime limit, so a busy connection is never closed. The cost is a TLS handshake per connection every 30 s.
2. **Balance per request in the mesh** for services where it matters (ADR-057: an Istio waypoint), which also brings retries on another pod and outlier detection.
3. **gRPC**: one HTTP/2 connection carries everything, so connection-level balancing is useless. FoodFleet uses a headless Service with the client's `round_robin` policy and periodic re-resolution, or the mesh ([gRPC](grpc.md)).

## Algorithms

| Algorithm | Picks | Good for |
|---|---|---|
| Round robin | The next instance in turn | Uniform requests and instances; Spring Cloud LoadBalancer's default |
| Random | Any instance | Many clients with no shared state |
| Least requests / least connections | The instance with the fewest in flight | Variable request cost; Envoy's `LEAST_REQUEST` |
| Power of two choices | The less loaded of two random instances | Least-requests behaviour without global state; what Envoy's least-request does by default |
| Consistent hashing (ring, Maglev) | Always the same instance for a key | Caches and sessions; loses balance when keys are skewed |
| Zone-aware | An instance in the caller's zone first | Cross-zone data transfer costs; Kubernetes topology-aware routing |

Round robin is fine until requests differ in cost by an order of magnitude; then one instance holding three slow requests still gets its turn. Least requests is the usual fix.

## Client-side, in Spring

Spring Cloud LoadBalancer sits behind an HTTP service client called by service name. FoodFleet keeps it for local development and tests (with `SimpleDiscoveryClient`), and uses Kubernetes Services in clusters (ADR-011). [Lab 04](../code-lab/lab-04-discovery-gateway.md) wires it into the gateway and checks that requests alternate 2/2 across two instances.

Every client-side cache adds a staleness window: Spring Cloud LoadBalancer caches instance lists for 35 s by default, part of why Eureka-based discovery kept sending traffic to a dead instance for a mean of 242.5 s in chapter 11's simulation.

## Pitfalls

- **Scaling out with keep-alive clients and no lifetime cap**: the new pods sit idle.
- **Retries that land on the same bad instance**: a retry should go to a different instance (the mesh does this with `retryOn` and outlier detection; chapter 11 showed retry-on-another-instance turning 675 failures into 675 slower successes).
- **Health checks that only check the process**: a hung instance keeps its connections until readiness fails ([Spring Boot Actuator](spring-boot-actuator.md)).
- **Sticky sessions** to hide shared state: they break on every scale-in.
