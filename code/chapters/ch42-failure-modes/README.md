# Chapter 42 — Failure Modes & Cascading Failures

| Package / file | What it is |
|---|---|
| `resilience/` | `Backoff` (fixed, exponential, full jitter), `RetryBudget` (retries as a fraction of requests), `RetryPolicy` (attempts, backoff, budget, deadline), `Deadline` (propagated as an absolute time in `X-Request-Deadline`), `QueueAgeShedder` (refuse work that waited too long or whose caller gave up), `P2cBalancer` (power of two choices by requests in flight) |
| `web/` | order-service: `DeadlineFilter`, `DeadlinePropagation` and `BudgetedRetry` (RestClient interceptors), `ClientsConfig`, `MenuService` (Spring Framework 7's `@ConcurrencyLimit(REJECT)` and `@Retryable` with backoff and jitter), `order-service.yml` (Tomcat bounds, virtual threads, client timeouts) |
| `verify/tools/Lab.java` | A small real system on loopback: HTTP services with fixed worker pools and FIFO queues, open-loop Poisson load generators, calls with timeouts, retries and deadlines |
| `verify/tools/Measure.java` | Five experiments: `queueing`, `amplification`, `metastable`, `herd`, `tail` |

## Run

```bash
cd code
mvn -B -pl chapters/ch42-failure-modes -am verify          # unit tests

chapters/ch42-failure-modes/verify/run.sh                   # all experiments (about 12 minutes; JDK 21+ only)
chapters/ch42-failure-modes/verify/run.sh metastable herd   # some of them
```

## What was verified, and how

- `resilience/` and the web interceptors and filter have 13 tests, all passing here (the web ones against stand-ins
  for the servlet and Spring types, whose signatures were checked against the sources).
- Every experiment ran real HTTP services (the JDK's HTTP server) and real clients (the JDK's HTTP client) on
  loopback; only the work inside a service is a sleep.
- `check_apis.sh` checks 17 defaults and APIs against Spring Framework 7.0.9 and Spring Boot 4.1.1. The Spring
  application itself was not started (no Maven access).

## Verified output

JDK 21.0.10 on one 2-vCPU container. Two runs; counts and shapes were the same, percentages and timings vary (the
chapter quotes ranges). This is the second.

```text
== queueing
one service: 8 workers; work is a sleep, exponential with mean 25 ms; users arrive as a Poisson process
(utilisation and service time measured inside the server; M/M/c is the textbook prediction for them)
utilisation    rate/s     service time   mean (M/M/c)     mean (measured)  p50        p99        p99 / service time
49%            153        25.5 ms        26 ms            27 ms            20 ms      116 ms     4.5x
70%            225        24.8 ms        28 ms            28 ms            21 ms      120 ms     4.8x
80%            250        25.4 ms        32 ms            34 ms            25 ms      151 ms     5.9x
89%            283        25.2 ms        45 ms            41 ms            32 ms      156 ms     6.2x
99%            307        25.8 ms        288 ms           223 ms           208 ms     482 ms     18.7x
99%            312        25.4 ms        391 ms           98 ms            88 ms      279 ms     11.0x

== amplification
edge -> order -> restaurant; 50 user requests/s for 10 s; 2 s deadline, 500 ms per attempt

restaurant-service down (every call fails fast with 503)
retry policy                               users served     calls to order per user    calls to restaurant per user upper bound
no retries                                 0.0%             1.00                       1.00                       1x
3 attempts at every layer                  0.0%             9.00                       27.00                      27x
3 attempts at the edge only                0.0%             3.00                       3.00                       3x
3 attempts at every layer, 10% budget      0.0%             1.25                       1.39                       27x (budget-limited)

restaurant-service failing 30% of calls at random
retry policy                               users served     calls to order per user    calls to restaurant per user upper bound
no retries                                 69.2%            1.00                       1.00                       1x
3 attempts at every layer                  100.0%           1.03                       1.49                       27x
3 attempts at the edge only                97.2%            1.40                       1.40                       3x
3 attempts at every layer, 10% budget      96.7%            1.22                       1.35                       27x (budget-limited)

== metastable
one service, capacity 320 requests/s; users arrive at 220/s (69%) for 45 s; each attempt times out at 300 ms, each user gives up after 1 s
trigger: from 10 s to 15 s the service runs 3x slower (capacity 107/s), then recovers fully
share of users served, per 5-second window (the trigger is in the third window)
client / server                                            0-5    5-10   10-15  15-20  20-25  25-30  30-35  35-40  40-45   wasted work
no retries                                                 100%   100%   3%     0%     73%    100%   100%   100%   100%    1811 of 9947 served too late
3 attempts, no backoff                                     100%   100%   8%     0%     0%     0%     0%     0%     0%      9688 of 12147 served too late
3 attempts, jittered backoff                               100%   100%   4%     0%     0%     0%     0%     0%     0%      9698 of 12162 served too late
3 attempts, 10% retry budget                               100%   100%   8%     0%     22%    100%   100%   100%   100%    2446 of 10279 served too late
3 attempts, no backoff; server sheds work queued > 300 ms  100%   100%   7%     11%    8%     9%     8%     8%     15%     115 of 12006 served too late
3 attempts, no backoff; server sheds work queued > 150 ms  100%   100%   45%    100%   100%   100%   100%   100%   100%    0 of 9504 served too late
3 attempts, 10% budget; server sheds > 150 ms              100%   100%   45%    99%    100%   100%   100%   100%   100%    0 of 9217 served too late

== herd
600 clients lose their connections at the same moment (a gateway restart) and reconnect;
a reconnect costs 20 ms of a worker (TLS, authentication, resync); 4 workers: 200 reconnects/s;
the gateway sheds reconnects that waited over 250 ms; each attempt times out at 1 s
reconnect policy                   attempts           peak per 100 ms later 50% reconnected      99%              all
immediately                        3631 (6.1 each)    249                  2.7 s                4.3 s            4.4 s
after a fixed 1 s                  2784 (4.6 each)    275                  5.3 s                10.5 s           10.6 s
exponential 0.5 s .. 8 s           2955 (4.9 each)    330                  8.8 s                42.1 s           42.1 s
exponential, full jitter           1723 (2.9 each)    49                   1.6 s                3.8 s            5.4 s

== tail
4 instances of menu-service; instance 4 adds 300 ms to 20% of its requests; health checks: 200 200 200 200
each user request fans out to k calls and waits for all of them; 30 user requests/s for 15 s
k      balancing                p50        p99        users waiting > 250 ms   calls sent to instance 4
1      round robin              12 ms      332 ms     7.2%                     25%
1      power of two choices     10 ms      312 ms     3.3%                     16%
5      round robin              30 ms      332 ms     23.5%                    25%
5      power of two choices     26 ms      323 ms     14.1%                    15%
10     round robin              52 ms      355 ms     40.5%                    25%
10     power of two choices     36 ms      329 ms     24.1%                    14%

== apis
-- Spring Framework 7.0.9
OK       @Retryable maxRetries defaults to 3
OK       @Retryable delay defaults to 1000 ms
OK       @Retryable jitter defaults to 0
OK       @Retryable multiplier defaults to 1.0 (fixed delay)
OK       @Retryable timeout defaults to 0 (none)
OK       @Retryable maxDelay
OK       @ConcurrencyLimit policy defaults to BLOCK
OK       @ConcurrencyLimit ThrottlePolicy.REJECT
OK       @EnableResilientMethods
OK       HttpHeaders.containsHeader(String)
OK       RestClient.Builder.requestInterceptor(...)
-- Spring Boot 4.1.1
OK       server.tomcat.threads.max defaults to 200
OK       server.tomcat.accept-count defaults to 100
OK       server.tomcat.max-connections defaults to 8192
OK       virtual threads: Tomcat uses a VirtualThreadExecutor
OK       spring.http.clients.* (connect-timeout, read-timeout)
OK       ...read-timeout has no default

all checks passed
```
