---
title: "Spring Framework 7 Resilience"
description: "Spring Framework 7's built-in @Retryable and @ConcurrencyLimit (enabled by @EnableResilientMethods), and when FoodFleet uses them instead of Resilience4j. Measured: the default retry (1 s, no jitter) sent 40–54% of a thousand clients' retries into the same 10 ms; Spring's jitter is half as wide as it looks; and @ConcurrencyLimit's default BLOCK policy 'served everyone' at a median of 5 s with 4,000 callers still queued, where REJECT served at the dependency's own latency."
progress: done
---

# Spring Framework 7 Resilience

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Two annotations in Spring Framework 7's core (`org.springframework.resilience.annotation`): `@Retryable` and `@ConcurrencyLimit`, plus `RetryTemplate` for programmatic retries |
| **FoodFleet uses** | Both, where no circuit breaker is needed: `@Retryable` with multiplier, jitter at least the delay, and a timeout; `@ConcurrencyLimit` always with `policy = REJECT` |
| **Decided in** | [ADR-043](../resilience/ch43-resilience4j-deep-dive.md#8-enterprise-case-study) (superseding [ADR-018](../architecture-patterns/ch18-resilience-patterns.md#8-enterprise-case-study)'s blocking limit) |
| **Learn it in** | [Chapter 43](../resilience/ch43-resilience4j-deep-dive.md), [chapter 18](../architecture-patterns/ch18-resilience-patterns.md), [chapter 8](../foundations/ch08-spring-boot-4-essentials.md) |
| **Related** | [Resilience4j](resilience4j.md), [REST clients](http-service-clients.md) |

## Turning them on

Nothing happens without `@EnableResilientMethods` on a configuration class: the annotations are silently ignored. Both work on any Spring bean method, including methods returning reactive types.

```java title="MapsGateway.java (chapter 18)"
--8<-- "code/chapters/ch18-resilience-patterns/src/main/java/dev/foodfleet/ch18/app/MapsGateway.java:9:37"
```

Chapter 18's example predates ADR-043: it keeps `@ConcurrencyLimit`'s default `BLOCK` policy, which chapter 43 then measured. FoodFleet writes `@ConcurrencyLimit(limit = 20, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)`.

## `@Retryable`

| Attribute | Default (Spring Framework 7.0.9) | FoodFleet |
|---|---|---|
| `maxRetries` | 3 | 1–3, by budget |
| `delay` | 1,000 ms | 100–200 ms |
| `multiplier` | 1.0 (fixed delay) | 2 |
| `jitter` | 0 | **At least the delay** |
| `timeout` | 0 (none) | The caller's remaining budget |
| `includes` / `excludes` / `predicate` | Any exception | I/O errors and 5xx only; never 4xx |

[Chapter 43](../resilience/ch43-resilience4j-deep-dive.md): 1,000 clients fail at once and retry; peak = most retries in any 10 ms.

| Policy | First retry: peak / spread | Third retry |
|---|---:|---:|
| Spring defaults (1 s, no jitter) | **396–537** / 34–38 ms | 256–374 / 48–52 ms |
| 200 ms, ×2, jitter 100 ms | 152–225 / 89–90 ms | 27–38 / 840–857 ms |
| 200 ms, ×2, jitter 200 ms | 79–86 / 179–182 ms | 16–17 / 1,417–1,468 ms |

**Spring's jitter never goes below the initial delay**, so `jitter = 100` on a 200 ms delay spread the first retry over about 90 ms, not 200. Set jitter equal to the delay for a useful spread.

## `@ConcurrencyLimit`

A bulkhead: at most *n* concurrent invocations of the method. With virtual threads nothing else limits how many requests can wait on a slow dependency at once.

Each call 50 ms, at most 20 at a time (400/s capacity), users arriving at 800/s for 10 s:

| Limit | Served | Served p50 | Served p99 | Callers waiting when arrivals stopped |
|---|---:|---:|---:|---:|
| `@ConcurrencyLimit(20)`, **BLOCK** (the default) | 100% | **5,017–5,062 ms** | **9,951–9,976 ms** | **3,975–3,989** |
| `@ConcurrencyLimit(20)`, **REJECT** | 47.8–48.3% | 50 ms | 50–51 ms | 19 |
| Resilience4j `Bulkhead`, no wait | 46.8–47.4% | 50 ms | 51 ms | 16–19 |

BLOCK is an unbounded queue: it "served everyone" by making everyone wait, and the backlog outlived the overload. REJECT behaves exactly like Resilience4j's bulkhead. That measurement is why ADR-043 replaced chapter 18's blocking limit.

## When to use which

| Need | Use |
|---|---|
| Retry a flaky call, no breaker | `@Retryable` |
| Limit concurrency to one dependency | `@ConcurrencyLimit(policy = REJECT)` or a Resilience4j `Bulkhead` |
| Stop calling a dead dependency, with time-window statistics | [Resilience4j](resilience4j.md)'s circuit breaker |
| Rate limits per client or tenant | [Rate limiting](rate-limiting.md) in Redis |
| Retries across the service mesh | The mesh, and drop the client-side retry ([ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study)) |

## Pitfalls

- **Forgetting `@EnableResilientMethods`**: no error, no retries.
- **Self-invocation**: like every Spring proxy-based annotation, calling the method from the same class bypasses it.
- **The default BLOCK policy** in front of user traffic.
- **Retries in two layers** (method and mesh, or two nested methods): attempts multiply. Chapter 17's incident: three retries in the service and three in an ambassador made nine calls per failure.
