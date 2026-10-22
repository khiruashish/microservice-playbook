# Chapter 44 — Timeouts, Retries, Backpressure & Load Shedding

How much work FoodFleet's order-service takes on, and which work it drops when it can't do it all.

```text
src/main/java/dev/foodfleet/ch44/
├── budget/   TimeoutBudget        a hop's timeout: min(ceiling, time left − reserve), or no call at all
├── limit/    AdaptiveLimits       AIMD admission limit, "slow" at half the callers' deadline (Netflix concurrency-limits)
│             PartitionedLimiter   concrete partitioned limiter: critical 90% / sheddable 10% at the limit
│             Priority             CRITICAL / SHEDDABLE from X-FoodFleet-Priority (unmarked = sheddable)
├── hedge/    HedgedReads          Resilience4j Hedge after the p95, paid from a 5% hedge budget
├── degrade/  Brownout             dimmer for optional features: halve above 10% slow, +10 points per second
└── web/      AdmissionFilter      first filter: permit or 503 + Retry-After; outcomes fed back to the limit
              MenuPageService      menu required, recommendations optional (budget + dimmer)
              OverloadConfig       limiter bean, filter registration, dimmer ticker
src/main/resources/order-service.yml   virtual threads, client timeouts
src/test/java/…                        14 tests, against the real libraries
verify/
├── build-libs.sh    builds concurrency-limits 0.5.4 and Resilience4j 2.4.0 (core, hedge) from their git tags
├── run.sh           five experiments (about 14 minutes), then check_apis.sh
├── check_apis.sh    24 defaults and behaviours in the libraries' and the JDK's sources
└── tools/           Lab.java (worker-pool server, open-loop users), Measure.java
```

## Run

```bash
mvn -B -pl chapters/ch44-timeouts-retries-backpressure -am verify     # from code/: compile and test
chapters/ch44-timeouts-retries-backpressure/verify/run.sh             # experiments; needs git and a JDK
JAVA_HOME=/path/to/jdk-21 chapters/ch44-timeouts-retries-backpressure/verify/run.sh priority   # the JDK 21 pinning row
```

## What the experiments found

Two runs on JDK 25.0.4 (the JDK 21 row: two runs on 21.0.10).

| Experiment | Finding |
|---|---|
| Long tail, spare capacity | Hedging after 50 ms took p99 from the full 1 s budget to 88 ms for 10–11% more calls. |
| Long tail, 85% load | Unbudgeted hedges and 100 ms retries tipped the dependency over: 18–68% of users served, up to 8 calls per user. Hedges capped at 5% of reads: 99.2–99.5%. |
| Slowdown 2.5× | No limit: 4% served in time, no recovery afterwards. AIMD with "slow" at half the deadline: 100% when healthy, 53–54% in the slowdown (57% possible). Vegas's defaults rejected 52–53% of a healthy service's traffic. |
| 2× overload, two priorities | Partitions: 79–81% of checkouts served instead of about 50%. LIFO waiting kept p99 at 278–289 ms, FIFO 461–466 ms. On JDK 21, `BlockingLimiter` pinned virtual threads: 1.5–1.8% served. |
| Lunch spike, optional feature | Timeouts alone: recommendations on 1–2% of pages during the spike and 0% after. The dependency limiting itself: 65–66% and back to normal at once. A dimmer: 32–34%, with half the calls. |
| Stream 2× faster than its consumer | Unbounded queue: events 10 s old. Bounded `Flow` buffer: under 0.3 s, by blocking the producer or dropping half. |

Numbers, conditions and the decisions they led to are in the chapter.
