# Chapter 18 — Resilience Patterns

Timeout, retry, bulkhead, circuit breaker and fallback, first as five small decorators you can read
in a minute each, then measured together in a deterministic simulation, then as a Spring Boot 4 stack.

| Package | What it is |
|---|---|
| `resilience/` | `Timeout`, `Retry` (full jitter), `Bulkhead`, `CircuitBreaker` (count-based, half-open trials), `Fallback`, all wrapping a common async `Call` |
| `sim/` | Discrete-event loop; `TrackingService` (50 workers, bounded queue, 2 s client timeout); `MapsProvider` (brownout, congestion, load shedding) |
| `simulation/` | Six scenarios with the same seeded traffic |
| `app/` | tracking-service in Spring Boot 4: client timeouts, `@Retryable`, `@ConcurrencyLimit`, Spring Cloud CircuitBreaker (Resilience4j), fallback to the last known ETA |

## Run

```bash
cd code
mvn -B -pl chapters/ch18-resilience-patterns -am verify
java -cp chapters/ch18-resilience-patterns/target/classes dev.foodfleet.ch18.simulation.ResilienceSimulation
```

## Verified output

```text
tracking-service, 50 workers; 200 status + 100 ETA req/s; maps provider browns out
(80% of calls ~2.5 s) from t=60s to t=180s. Figures for requests arriving during the incident.

scenario                                     status ok    status   ETA ok       ETA      ETA maps calls   maps  recovery
                                                          p99 ms           degraded   failed    per ETA   peak     after
no protection                                     2.4%      3284     0.7%      0.0%    99.3%       0.25     50       1 s
timeout 300 ms                                  100.0%         5    14.9%      0.0%    85.1%       1.00    300       1 s
timeout + 3 attempts, no backoff                100.0%       187    15.1%      0.0%    84.9%       2.81    300       1 s
timeout + bulkhead (20)                         100.0%         5    14.1%      0.0%    85.9%       0.73    168       1 s
timeout + bulkhead + circuit breaker            100.0%         5     0.2%      0.0%    99.8%       0.01     44       3 s
+ fallback, 2 attempts w/ jittered backoff      100.0%         5     0.2%     99.8%     0.0%       0.01     41       3 s
```

Across seeds 1–10 the conclusions hold: status availability 1.2–2.6% with no protection and 100% with
any timeout; ETA success 14–16% with timeouts; maps calls per ETA 2.80–2.81 with naive retries and 0.01
with a circuit breaker.
