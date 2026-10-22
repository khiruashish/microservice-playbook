---
title: "Resilience4j"
description: "Resilience4j 2.4's circuit breaker, retry, bulkhead, rate limiter and time limiter as FoodFleet configures them, and why no library default reaches production: a default breaker took 7.5–10 s to open on a quiet endpoint, never noticed a 3-second dependency, and refused every user for the minute after recovery; FoodFleet's 5-second time window opened in 2–4 s at any traffic and closed within 5 s. The aspect order Spring Boot applies, where the fallback must go, and why a TimeLimiter doesn't stop the work."
progress: done
---

# Resilience4j

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A lightweight fault-tolerance library: decorators (CircuitBreaker, Retry, Bulkhead, RateLimiter, TimeLimiter) around any call, with Spring Boot annotations and Micrometer metrics |
| **FoodFleet uses** | Resilience4j 2.4.0's Spring Boot 4 module, with policies from the platform's `ResiliencePolicies`: a breaker and a rejecting bulkhead for every synchronous dependency |
| **Decided in** | [ADR-043](../resilience/ch43-resilience4j-deep-dive.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 43](../resilience/ch43-resilience4j-deep-dive.md), [chapter 18](../architecture-patterns/ch18-resilience-patterns.md), [lab 06](../code-lab/lab-06-resilience.md) |
| **Related** | [Spring Framework 7 resilience](spring-framework-resilience.md), [REST clients](http-service-clients.md), [Istio](istio.md) (retries in the mesh) |

## FoodFleet's settings

```yaml title="application.yml (lab 06, resilience4j)"
--8<-- "code/code-lab/lab-06-resilience/src/main/resources/application.yml:15:48"
```

| Setting | Default | FoodFleet | Measured reason ([chapter 43](../resilience/ch43-resilience4j-deep-dive.md)) |
|---|---|---|---|
| Window | Last 100 calls | **Last 5 seconds**, min 20 calls | A count window opened in 0.5 s at 100 calls/s but 7.5–10 s at 5/s; 5 s opened in 1.9–3.8 s at both |
| Slow-call threshold | 60 s | **1 s**, 50% of calls | A default breaker never opened against a dependency answering in 3 s |
| Open state | 60 s | **5 s** | The default refused 100% of users after the dependency had recovered |
| Retry | 3 attempts, 500 ms, no jitter | 3 attempts, 100 ms ×2, randomization **1.0**, refusals ignored | Default retries arrived in lockstep: 40–60% of clients within the same 10 ms |
| Bulkhead wait | 0 | **0**, size by Little's law (32 for menus) | A waiting limit is a queue |
| RateLimiter | 50 per 500 **ns** | Always set explicitly, wait 0 | The default permitted 10.9–11.4 million calls in 10 s |
| Health indicator | On | **Off** | An open breaker must not take this service out of its load balancer |

## Composition order

Spring Boot applies the annotations as **Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( call ) ) ) ) )**, whatever order they're written in.

| Measured, 50 users/s | Result |
|---|---|
| Retry around the breaker, retrying `CallNotPermittedException` | Every user waited **1,000 ms** while the breaker was open |
| The same, refusals not retried | **0 ms** |
| Breaker around the retry | 150–198 calls reached the dead dependency instead of 10 |
| Fallback on `@Retry` (outermost), 30% failures | **97.4–98.0%** of users got a fresh menu |
| Fallback on `@CircuitBreaker` (inner) | 67.5–70.4%: every first failure became a stale menu |

**Put the fallback on the outermost annotation**, and don't retry the breaker's own refusal.

```java title="ResilientMenus.java (lab 06)"
--8<-- "code/code-lab/lab-06-resilience/src/main/java/dev/foodfleet/lab06/ResilientMenus.java:17:50"
```

## TimeLimiter doesn't stop the work

40 calls that would take 3 s each, a 500 ms timeout, `cancelRunningFuture = true`:

| Work | Future | Work still running 1 s after the timeout |
|---|---|---:|
| JDK `HttpClient.send` | `CompletableFuture` | **40 of 40** |
| JDK `HttpClient.send` | `ExecutorService.submit` | 0 of 40 |
| `HttpURLConnection` read | either | **40 of 40** |

A TimeLimiter protects the caller's latency, not the dependency's load. The timeout that stops work is the HTTP client's read timeout or the database driver's query timeout. FoodFleet uses TimeLimiter only for asynchronous calls.

## What it costs

The whole pipeline (Retry + CircuitBreaker + Bulkhead) cost 175–178 ns per call on one thread and under 0.8 µs contended, against milliseconds for the network call it protects. Overhead is never a reason to skip it.

## Resilience4j or Spring Framework 7

| Need | Use |
|---|---|
| Circuit breaker, rate limiter, time-window statistics, metrics per instance | Resilience4j |
| A simple retry or a concurrency limit, no breaker | Spring's `@Retryable`, `@ConcurrencyLimit(policy = REJECT)` ([page](spring-framework-resilience.md)) |
| Retries, ejection and per-request balancing across services | The mesh: since [ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study), client libraries keep timeouts and drop retries where a waypoint retries |

## Pitfalls

- **Shipping defaults**: every one of them assumes a traffic level and tolerance for slowness that aren't yours.
- **Breaker without a read timeout**: a black-holed dependency never completes a call, so the breaker never sees a failure.
- **Counting 4xx as failures**: a breaker that counts `404`s opens on bad requests, not on a sick dependency.
- **The health indicator on**: an open breaker marks the service DOWN and the platform removes healthy pods.
- **Retrying non-idempotent calls**: a payment retried without an idempotency key charges twice.
