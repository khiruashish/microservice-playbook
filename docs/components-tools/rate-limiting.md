---
title: "Rate Limiting"
description: "Limits that protect FoodFleet's capacity and its tenants from each other: fixed windows, sliding windows, token buckets and GCRA compared (a fixed window let 200 requests through a 100-a-minute limit), why the burst is part of the limit, per-client and per-tenant keys (a global limit left other tenants 0.2% of their requests; per-tenant limits, 100%), shared state in Redis against per-instance counters (1,036 through a 260 budget), what to do when Redis is down, and Spring Cloud Gateway's limiter's behaviour."
progress: done
---

# Rate Limiting

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Refusing requests beyond an agreed rate, per key (client, tenant, IP, endpoint), with a `429` and `Retry-After` |
| **FoodFleet uses** | GCRA in Redis, per client within a tenant and per tenant, after authentication, at the gateway; each instance's share as the fallback |
| **Decided in** | [ADR-040](../security/ch40-api-security.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 40](../security/ch40-api-security.md), [chapter 10](../architecture-patterns/ch10-api-gateway-bff.md), [lab 13](../code-lab/lab-13-redis.md) |
| **Related** | [API gateway](api-gateway.md), [Redis / Valkey](redis-valkey.md), [Resilience4j](resilience4j.md) (bulkheads limit concurrency, not rate) |

## Algorithms, measured

[Chapter 40](../security/ch40-api-security.md): a policy of 100 requests a minute for one client; one client retries every 10 ms for ten minutes, another sends bursts of 200 in the 200 ms around each minute boundary.

| Limiter | Allowed in 10 min | Most in any 60 s | Most in any 1 s | Boundary bursts: most in any 1 s |
|---|---:|---:|---:|---:|
| Fixed window | 1,100 | **200** | 100 | **200** |
| Sliding window counter | 1,041 | 149 | 100 | 100 |
| Token bucket (GCRA), burst 100 | 1,099 | 199 | 100 | 100 |
| **Token bucket (GCRA), burst 10** | **1,009** | **109** | **11** | **10** |

- **A fixed window allows twice the limit** across a boundary. [Lab 13](../code-lab/lab-13-redis.md) saw it on Redis: 20 requests through a 10-a-second limit in 0.11 s.
- **The burst is part of the limit.** A bucket as big as the window is nearly as loose as a fixed window; a burst of 10 kept any minute within 109.
- **GCRA** is a token bucket stored as one number per key (the theoretical arrival time), so a decision is one `GET` and one `SET` in a script.

```lua title="gcra.lua (chapter 40)"
--8<-- "code/chapters/ch40-api-security/src/main/resources/ratelimit/gcra.lua"
```

The script runs atomically in Redis and uses Redis's clock, so gateway instances with skewed clocks agree.

## The key decides who is protected

Ten simulated seconds; the platform serves 1,000 requests a second; one tenant's point-of-sale sync loops at 5,000 a second; four other tenants send 100 a second each:

| Limits | Other tenants' requests served | The looping tenant's |
|---|---:|---:|
| Global limit only | **0.2%** | 20.2% |
| Per tenant (250/s), then global | **100%** | 5.1% |
| Per client (100/s), per tenant, then global | **100%** | 2.0% |

A global limit protects the servers, not the tenants: the loudest client gets the capacity. Key limits by authenticated client and tenant, which is why they run **after** authentication; per-IP limits before it stop floods and credential stuffing cheaply.

## Shared or local

50 requests a second per client with a burst of 10 should let at most 260 through in 5 seconds. The client sent 400 a second through 4 gateway instances:

| Where the limit lives | Allowed (even spread) | Allowed (70/10/10/10) | Decision p50 |
|---|---:|---:|---:|
| In each instance, 50/s each | **1,036** | 859 | 13 µs |
| In each instance, divided by 4 (the fallback) | 244 | 244 | 9–10 µs |
| **Redis, shared** | **258–259** | **259–260** | 428–446 µs |

- **Local limits multiply** with the number of instances, and with every autoscaling event.
- **A shared limit costs a round trip**: about 0.1 ms back to back, 0.4 ms under paced load.
- **A partitioned Redis is worse than a dead one**: a refused connection cost 94–207 µs per decision, an unanswered one the full 50 ms connect timeout. Keep the timeout short.
- **The fallback**: each instance enforces its share (limit ÷ instances), and an alert fires. Bounded, never unlimited and never refuse-all.

## Spring Cloud Gateway's `RedisRateLimiter`

Chapter 40 ran it against `gcra.lua` at 10 requests a second, burst 10, with a client sending 100 a second: both allowed 50 in 5 seconds, but Spring Cloud Gateway's let **all 10 of a second's requests through in its first 100 ms**, because it refills once a second; GCRA spread them (at most 2 in any 100 ms). It also fails open when Redis is unreachable. Fine for coarse limits; know the behaviour.

## Responses

Answer `429 Too Many Requests` with `Retry-After` (seconds), and the draft standard `RateLimit` and `RateLimit-Policy` headers if your clients use them. Never `503` for a limit: clients and meshes retry `503`s.

## Pitfalls

- **Limits on the wrong key**: per IP behind a carrier-grade NAT limits thousands of users together.
- **No limit on expensive endpoints only** (search, order placement): limits per endpoint class, not one per client.
- **Limits that only exist in one gateway**: a second ingress path bypasses them.
- **Rate limits instead of concurrency limits** for slow work: a report endpoint needs a bulkhead ([lab 18](../code-lab/lab-18-multi-tenancy.md)), since 10 requests a second of a 2-second report is 20 in flight.
