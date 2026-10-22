# Chapter 25 — Asynchronous Messaging

Consumers of FoodFleet's `order-events` topic (keyed by order id). Two parts:

| Package | What it is |
|---|---|
| `sim/` | `DeliverySemantics` (when the consumer commits, and what a crash costs), `OrderingSimulation` (parallelism within a partition), `RetryStrategies` (five ways to handle failures, through a poison message and a dependency outage) |
| `kafka/` | `KitchenTicketListener` (ordered; blocking retries with backoff, then DLT; not-retryable errors straight to the DLT, see `KafkaErrorHandlingConfig`), `ReceiptEmailListener` (non-blocking `@RetryableTopic` 1 s / 10 s / 60 s, then `@DltHandler`), `ProcessedEvents` (idempotency) |

## Run

```bash
cd code
mvn -B -pl chapters/ch25-messaging -am verify            # the Kafka test starts an embedded broker
for c in DeliverySemantics OrderingSimulation RetryStrategies; do
  java -cp chapters/ch25-messaging/target/classes dev.foodfleet.ch25.sim.$c
done
```

## Verified output

Simulations, deterministic (fixed seeds); run on a JDK 21 runtime (the module targets JDK 25).

```text
100,000 messages; a crash every ~5,000 on average, at a random point

commit                          crashes     lost  duplicate effects   redelivered
COMMIT_BEFORE_PROCESSING             26        8                  0             0
COMMIT_AFTER_EACH                    26        0                  9            17
COMMIT_EVERY_500                     28        0               6180          6188
COMMIT_AFTER_EACH_IDEMPOTENT         26        0                  0            17
```

```text
2,000 events of 500 orders on one partition; 2-20 ms each

consumer                        time ms     events/s     out of order
ONE_THREAD_PER_PARTITION          10021          200                0
POOL_OF_8                          1263         1583              384
KEY_ORDERED_LANES_8                1309         1528                0
```

```text
one partition, 18,000 order events over 180 s; poison message at ~20 s; dependency failing 60-90 s

FULL OUTAGE: every call fails (200 ms timeout)
strategy                                        delivered  lost  poison>DLT  good>DLT unprocessed    p99 s    max s out of order
Retry forever (blocking)                             1768     0           0         0       16231      0.0      0.0            0
10 quick attempts, then log and skip                17984    16           0         0           0     28.8     29.9           11
Blocking backoff (31 s), then DLT                   17999     0           1         0           0     48.5     49.6            1
Blocking backoff + not-retryable to DLT             17999     0           1         0           0     30.9     32.0            1
Retry topics 1s/10s/60s + not-retryable to DLT      17999     0           1         0           0     28.3     89.1           95

PARTIAL OUTAGE: calls for 1 in 20 orders fail (one partner's API down)
strategy                                        delivered  lost  poison>DLT  good>DLT unprocessed    p99 s    max s out of order
Retry forever (blocking)                             1768     0           0         0       16231      0.0      0.0            0
10 quick attempts, then log and skip                17985    15           0         0           0     26.5     27.6            9
Blocking backoff (31 s), then DLT                   17999     0           1         0           0     48.4     49.5            1
Blocking backoff + not-retryable to DLT             17999     0           1         0           0     30.9     32.0            1
Retry topics 1s/10s/60s + not-retryable to DLT      17999     0           1         0           0      8.2     76.7           18
```

## Tests

| Test | Checks |
|---|---|
| `SimulationsTest` | the three tables' conclusions |
| `OrderEventsKafkaTest` | embedded Kafka: a temporary failure retried in place with order kept; an invalid event in `order-events-dlt` with its cause header and no partner call; a duplicate applied once; an invalid receipt dead-lettered immediately |
