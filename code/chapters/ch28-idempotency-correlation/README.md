# Chapter 28 — Idempotency, Deduplication & Correlation

| Package | What it is |
|---|---|
| `payments/` | `POST /payments` with `Idempotency-Key`: `IdempotencyKeys` (one-statement claim with a lease, stored responses), `PaymentService` (claim → provider call with our key → payment + response in one transaction), `PaymentController`, Flyway migration; `PaymentIdempotencyContainerTest` (Testcontainers) |
| `dedup/` | `DedupWindows`: duplicates by source (retry, rebalance, outbox re-publish, replay) against five dedup stores |
| `correlation/` | `TraceParent` (W3C Trace Context), `EventEnvelope` (event, correlation and causation ids), `CausationVsClocks` |
| `verify/` | The PostgreSQL experiment (`experiment.sh`: five strategies, 800 concurrent requests each, one psql session per request), `claim_test.sql`, `claim_once.sql` and `service_flow.sql` (the service's SQL, run as written) |

## Run

```bash
cd code
mvn -B -pl chapters/ch28-idempotency-correlation -am verify   # container test needs Docker
java -cp chapters/ch28-idempotency-correlation/target/classes dev.foodfleet.ch28.dedup.DedupWindows
java -cp chapters/ch28-idempotency-correlation/target/classes dev.foodfleet.ch28.correlation.CausationVsClocks
PGHOST=localhost PGPORT=5432 PGUSER=postgres chapters/ch28-idempotency-correlation/verify/experiment.sh
```

## Verified output

PostgreSQL 16.13, 2-vCPU container; 200 keys, each requested 4 times concurrently, 16 sessions at a time;
the provider call is 20 ms. Four runs; charges and rows for A, C and E identical every time, B 315–346 charges
(83–94 keys charged more than once), D 457–487 charges (135–145 keys) with 265–296 serialization failures.

```text
strategy                             charges keys >1 charge payment rows   errors  responses
a_no_key                                 800           200         800        0  201=800
b_check_then_insert                      346            94         346        0  201=346, replayed=454
c_idempotency_key                        200             0         200        0  201=200, 201 replayed=466, 409 in progress=134
d_serializable                           481           138         192      289  -
e_unique_constraint                      800           200         200      600  -
```

The claim statement under contention: 200 keys × 8 concurrent attempts = 1,600 attempts, 200 claims.

```text
500 events/s on 6 instances; 9,500 duplicates by cause; duplicates that get applied twice

dedup store                                     retry  rebalance  republish     replay    total       ids kept
(duplicates)                                     5000       3000        500       1000     9500               
no deduplication                                 5000       3000        500       1000     9500              0
in-memory LRU per instance, 100k ids                0       2513          0       1000     3513        600,000
shared cache (Redis), 1 h TTL                       0          0          0       1000     1000      1,800,000
inbox table, 7-day retention                        0          0          0          0        0    302,400,000
1 h cache + version on the order row                0          0          0          0        0      1,800,000
```

```text
10,000 orders x 6 events (50,000 cause -> effect links); clocks within a few ms

                                       links inverted by time orders misordered by time   by causation
all clocks within a few ms                        829 ( 1.7%)              817 ( 8.2%)              0
one kitchen instance 250 ms behind               4184 ( 8.4%)             3930 (39.3%)              0

events of an order found by its orderId field: 40,000 of 60,000 (67%); by correlation id: all 60,000
```

The simulations ran on a JDK 21 runtime (the module targets JDK 25).

## Tests

| Test | Checks |
|---|---|
| `PaymentIdempotencyContainerTest` | 16 concurrent duplicates → 1 charge, 1 payment; replay with the same body; 422 for a reused key; an expired claim taken over without a second charge |
| `PaymentControllerTest` | Idempotency-Key as a Structured Field string; fingerprints |
| `DedupWindowsTest`, `CorrelationTest` | the tables' conclusions; the W3C example traceparent; causation chains |
