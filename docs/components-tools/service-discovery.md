---
title: "Service Discovery"
description: "How a FoodFleet service finds the instances of another: Kubernetes Services and readiness probes instead of a registry, Eureka and Consul where they still fit, and Spring Cloud LoadBalancer with SimpleDiscoveryClient for local development. Simulated over 200,000 failures: Eureka's defaults kept sending traffic to a dead instance for a mean of 242.5 s, a Kubernetes Service 3 s after a crash, and 27 s after a hang, which a retry on another instance turned from 675 failures into 675 slower successes."
progress: done
---

# Service Discovery

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Answering "where are the healthy instances of X right now?" for every call |
| **FoodFleet uses** | Kubernetes Services (ClusterIP DNS names) with readiness probes; headless Services for gRPC; Spring Cloud LoadBalancer with `SimpleDiscoveryClient` in development and tests |
| **Decided in** | [ADR-011](../architecture-patterns/ch11-service-discovery-patterns.md#8-enterprise-case-study): Kubernetes Services are the discovery mechanism; Eureka decommissioned |
| **Learn it in** | [Chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md), [lab 04](../code-lab/lab-04-discovery-gateway.md) |
| **Related** | [Load balancing](load-balancing.md), [Spring Boot Actuator](spring-boot-actuator.md) (readiness), [Kubernetes](kubernetes.md) |

## Three questions

Every discovery design answers **who registers** an instance, **who stores** the list, and **who chooses** an instance for a call. Each answer adds a cache, and each cache a window in which a dead instance still gets traffic.

| | Self-registration | Third-party registration |
|---|---|---|
| **Client-side choice** | Eureka + Spring Cloud LoadBalancer | gRPC `round_robin` over a headless Service (DNS from Kubernetes) |
| **Server-side choice** | Consul with a load balancer reading it | Kubernetes Service via kube-proxy; a mesh |

## Measured: how long traffic reaches a dead instance

[Chapter 11](../architecture-patterns/ch11-service-discovery-patterns.md) modelled each mechanism as a chain of timers whose phase at the moment of failure is random: 200,000 trials, 4 instances, 100 requests a second, one instance dies.

| Mechanism | Mean window | p99 window | Failed requests |
|---|---:|---:|---:|
| Eureka, defaults | **242.5 s** | 298.1 s | **6,062** |
| Eureka, tuned (5 s heartbeat, 15 s lease, no response cache, 5 s fetches) | 35.0 s | 41.5 s | 875 |
| DNS + health checks (10 s × 3 checks, 30 s TTL, 30 s JVM cache) | 55.0 s | 81.9 s | 1,375 |
| Kubernetes Service, **crash** | **3.0 s** | 3.0 s | 75 |
| Kubernetes Service, **hang** (readiness 10 s × 3) | 27.0 s | 31.9 s | 675 |
| Kubernetes Service, hang + **retry on another instance** | 27.0 s | 31.9 s | **0** (675 slowed) |

Eureka's defaults stack: a 30 s renewal, a 90 s lease (counted twice by a long-standing quirk, so up to 180 s), a 60 s eviction sweep, a 30 s server response cache, 30 s client fetches and Spring Cloud LoadBalancer's 35 s instance cache. Kubernetes removes a crashed pod's endpoint in seconds, but a **hung** process stays until its readiness probe fails: the probe matters more than the discovery technology.

## FoodFleet's set-up

- Services call each other by Service DNS name (`http://menu-service.menu-service.svc`), with no discovery library.
- Every service has a readiness probe that checks only essential dependencies, `server.shutdown=graceful` and a 5 s `preStop` pause, so endpoints are removed before the process stops taking requests ([Actuator](spring-boot-actuator.md)).
- Idempotent calls retry once on another instance, in the mesh since ADR-057.
- gRPC uses headless Services with client-side round robin, because kube-proxy balances connections and gRPC has one ([load balancing](load-balancing.md)).
- Locally, Spring Cloud LoadBalancer with a static list stands in:

```yaml title="lab 04: discovery without a registry"
--8<-- "code/code-lab/lab-04-discovery-gateway/src/main/resources/application.yml:1:19"
```

## When a registry still makes sense

| Situation | Choice |
|---|---|
| Everything on Kubernetes | Services and readiness probes |
| VMs and Kubernetes mixed, during a migration | Consul (catalog across both), or Eureka kept until the last VM moves (FoodFleet's path) |
| Many clusters | A mesh with multi-cluster endpoints, or Consul |
| Per-request policies (zone preference, weights) | A mesh or Gateway API features, not a client library |

## Pitfalls

- **Readiness probes that check every dependency**: one slow downstream takes every caller's pods out of rotation together.
- **No graceful shutdown**: requests in flight when the endpoint is removed fail.
- **JVM DNS caching** (`networkaddress.cache.ttl`) longer than the DNS TTL keeps old addresses after a failover.
- **Retries without "another instance"** go straight back to the hung one.
