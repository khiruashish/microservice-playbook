# Chapter 43 — Resilience4j & Spring Framework 7 Resilience

FoodFleet's order-service calls restaurant-service for menus and payment-service for authorisations. This module
holds the resilience configuration both calls use, and the experiments behind every value in it.

```text
src/main/java/dev/foodfleet/ch43/
├── policy/   ResiliencePolicies   FoodFleet's circuit breaker, retry, bulkhead and time limiter settings
│             Resilient            Retry ( CircuitBreaker ( Bulkhead ( call ) ) ), Spring Boot's aspect order, in code
│             Transient            "retry this?" through the cause chain (RestClient wraps IOException)
│             ClientErrorException 4xx: not retried, not counted against the dependency
└── web/      MenuClient           @Retry(fallbackMethod) @CircuitBreaker @Bulkhead, cached menu as the fallback
              PaymentClient        Spring Framework 7 @Retryable + @ConcurrencyLimit(REJECT), idempotency key
              ResilienceConfig     RestClients that map 4xx, breaker transitions logged
src/main/resources/order-service.yml   the same policy for the annotations
src/test/java/…                        20 tests, against the real libraries
verify/
├── build-libs.sh    builds Resilience4j 2.4.0 and Spring Framework 7.0.9's retry classes from their git tags
├── run.sh           eight experiments (about 8 minutes), then check_apis.sh
├── check_apis.sh    44 defaults and behaviours checked in the libraries' sources
└── tools/           Lab.java (scripted dependency, open-loop load), Measure.java
```

## Run

```bash
mvn -B -pl chapters/ch43-resilience4j -am verify       # from code/: compile and test
chapters/ch43-resilience4j/verify/run.sh               # experiments; needs git and JDK 21+
chapters/ch43-resilience4j/verify/run.sh breaker slow  # just some of them
```

## What the experiments found (two runs)

| Experiment | Finding |
|---|---|
| Breaker windows | Library defaults opened in 0.5–0.6 s at 100 calls/s but 7.5–10 s at 5/s, then stayed open for the rest of the run (60 s open state): every user refused after recovery. "Last 10 calls" opened on a 40% blip. A 5-second time window opened in 2–4 s at either rate and closed within 5 s of recovery. |
| Slow dependency | With defaults, a dependency answering in 3 s instead of 20 ms never opened the breaker (slow means 60 s). A 1 s slow-call threshold opened it; a bulkhead capped calls in progress at 32. |
| TimeLimiter | `cancelRunningFuture` cancelled the callers' futures, but work in a `CompletableFuture` ran to the end every time; an `ExecutorService` future was interrupted, which stopped sleeps and JDK `HttpClient` calls but not `HttpURLConnection` reads or CPU loops. |
| Decorator order | In Spring Boot's order, Retry retried `CallNotPermittedException`: 1 s of waiting for a fail-fast answer. A fallback on `@CircuitBreaker` (inside the retry) disabled retries: 68–70% fresh menus against 97–98%. |
| RateLimiter | The default config allowed over 10 million calls a second. 100 per second allowed 200 in some 1-second windows (fixed periods). With the default 5 s wait, over 900 callers queued. |
| Retry schedules | No jitter: 400–600 of 1,000 retries in the same 10 ms. Spring's jitter never goes below the initial delay; Resilience4j's randomization factor 1.0 spread retries widest. |
| Overload | `@ConcurrencyLimit`'s default BLOCK served everyone, at a p99 of 10 s, with about 4,000 callers waiting when arrivals stopped. REJECT and a Resilience4j bulkhead served half at 50 ms. |
| Overhead | 18–180 ns per decorator on one thread with default settings; under a microsecond for the whole pipeline, even contended. |

Numbers, conditions and the decisions they led to are in the chapter.
