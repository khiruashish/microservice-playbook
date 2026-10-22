---
title: "Coding Rounds"
description: "The four microservice building blocks interviewers ask candidates to code: a circuit breaker, a per-key token-bucket rate limiter, an idempotent consumer and a saga coordinator, each written in plain Java 25 with no libraries, explained the way you'd talk through it at a whiteboard, and tested with 24 tests including concurrent ones (6,400 threads' requests for 1,000 tokens get exactly 1,000)."
progress: done
---

# Coding Rounds

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Microservice coding rounds rarely ask for algorithms. They ask you to build, in 30–45 minutes, one of the mechanisms the architecture depends on, then probe the edge cases: concurrency, failure part-way, time. Four come up again and again. Each is below in plain Java 25, with no libraries, so nothing hides the logic.

```bash
code/interview/coding-rounds/run.sh      # compiles with javac and runs the 24 tests; under a second of work
```

!!! tip "How to run the round"
    1. **Restate the contract** before coding: what goes in, what comes out, what happens on failure.
    2. **Say the states or invariants** out loud ("closed, open, half-open"; "a message's effect and its record commit together").
    3. **Code the happy path, then the edge cases** the interviewer will ask about anyway: concurrency, time, crashes.
    4. **Make time injectable.** Tests that sleep are slow and flaky; a clock parameter shows you've written real tests.
    5. **Name what the library adds** that you didn't write: it shows you know where your version stops.

## 1. Circuit breaker

**The contract.** Wrap a call. While the dependency is healthy, pass calls through and count outcomes. When the failure rate over recent calls crosses a threshold, *open*: fail at once without calling, so the dependency can recover and callers don't wait. After a while, *half-open*: let a few trial calls through; close if they succeed, reopen if not.

```java title="code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/CircuitBreaker.java"
--8<-- "code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/CircuitBreaker.java:76:113"
```

**Talk through:**

- **The window is a ring buffer** of the last N outcomes, with a running failure count: O(1) per call.
- **A minimum number of calls** before the rate means anything: four failures out of four calls on a quiet endpoint shouldn't open it.
- **Half-open admits a fixed number of trials**; everyone else is still rejected, so a recovering dependency isn't flooded.
- **What Resilience4j adds** ([ch. 43](../resilience/ch43-resilience4j-deep-dive.md)): time-based windows, slow-call rates, ignored exceptions, metrics, events. Chapter 43 measured why the time-based window matters: a 100-call window took about ten seconds to open on a quiet endpoint; a 5-second window opened in 2–4 s at both 100 and 5 requests a second.

## 2. Rate limiter

**The contract.** Allow each client (or tenant) a sustained rate with some burst; refuse the rest with a `429` and a `Retry-After`.

```java title="code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/TokenBucket.java"
--8<-- "code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/TokenBucket.java:39:64"
```

**Talk through:**

- **Lazy refill.** Nothing runs in the background; each request adds the tokens earned since the bucket's last update. Idle keys cost nothing.
- **A lock per key**, not a global one, so tenants don't contend with each other. The test sends 6,400 concurrent requests for 1,000 tokens and gets exactly 1,000.
- **`Retry-After` falls out of the arithmetic**: the time to earn the missing fraction of a token.
- **Across instances, local state isn't enough.** Chapter 40 measured per-pod limits letting four times the budget through. FoodFleet keeps the state in Redis with GCRA, a token bucket stored as one timestamp per key, and when Redis is unreachable each instance enforces its share ([ch. 40](../security/ch40-api-security.md)).
- **Alternatives:** fixed windows allow twice the rate across a boundary; sliding windows cost more state; leaky buckets smooth output but add delay ([ch. 10](../architecture-patterns/ch10-api-gateway-bff.md)).

## 3. Idempotent consumer

**The contract.** Messages arrive at least once: redelivered after a crash, a rebalance or a lost acknowledgement. Each message's effect must happen exactly once.

```java title="code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/IdempotentConsumer.java"
--8<-- "code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/IdempotentConsumer.java:63:75"
```

**Talk through:**

- **The claim and the effect commit together.** Claim first in a separate transaction and crash before the effect: the message is lost. Apply the effect first and crash before the claim: it's applied twice on redelivery. Only one transaction for both is correct.
- **The claim is an insert that can't succeed twice** (`INSERT … ON CONFLICT DO NOTHING` on the message id), not a read followed by a write, which two concurrent deliveries can both pass. The test delivers 10 messages 16,000 times concurrently; each is applied once.
- **Commit the broker offset after the transaction**, never before. Chapter 25 measured Spring Kafka's default error handling silently skipping 16 messages during an outage ([ch. 25](../communication/ch25-asynchronous-messaging.md)).
- **When the effect is outside your database** (an email, a payment provider), pass the message id on as an idempotency key ([ch. 28](../communication/ch28-idempotency-correlation.md)).

## 4. Saga coordinator

**The contract.** Run a sequence of steps across services with no shared transaction. If a step fails, undo the steps already done, unless the saga has passed its point of no return, after which it must finish.

```java title="code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/SagaCoordinator.java"
--8<-- "code/interview/coding-rounds/src/main/java/dev/foodfleet/interview/SagaCoordinator.java:64:109"
```

**Talk through:**

- **Three kinds of step.** Compensatable steps can be undone; the **pivot** is the point of no return (the kitchen starts cooking); retriable steps after it must eventually succeed. The constructor refuses a definition that puts them in any other order.
- **Log first.** Every transition is appended before the next action, so a new coordinator resumes a crashed saga from the log. The step in flight at the crash runs again, so every step must be idempotent; that's the [idempotent consumer](#3-idempotent-consumer) again, on the participant's side.
- **STUCK is a state, not an error.** After the pivot, a step that keeps failing waits for a retry job or a person. In chapter 14's simulation, 41 of 10,000 sagas went STUCK during outages and all completed afterwards ([ch. 14](../architecture-patterns/ch14-saga-pattern.md)).
- **What production adds:** the log and the outgoing command in one database transaction (an outbox), timeouts per step, a dashboard of STUCK sagas, or a workflow engine such as Temporal for long-running flows.

## The tests

```console
$ code/interview/coding-rounds/run.sh
PASS breaker: 3 of 7 failed (43%) stays closed
PASS breaker: 4 of 8 failed (50%) opens
PASS breaker: open rejects without calling
PASS breaker: half-open after the open duration
PASS breaker: two successful trials close it
PASS breaker: a failed trial reopens it
PASS breaker: 4 failures below the 5-call minimum don't open it
PASS bucket: a burst of 8 gets the capacity of 5
PASS bucket: Retry-After is 0.5 s at 2 tokens a second
PASS bucket: 1.5 s later, 3 more tokens
PASS bucket: one tenant's empty bucket doesn't limit another
PASS bucket: 6,400 concurrent requests for 1,000 tokens get exactly 1,000
PASS consumer: a redelivered message is applied once
PASS consumer: a failure part-way leaves no effect
PASS consumer: the redelivery is processed, not skipped
PASS consumer: 16,000 concurrent deliveries of 10 messages apply each once
PASS saga: completes in order
PASS saga: running a finished saga again does nothing
PASS saga: a declined card releases the coupon and rejects the order, in reverse
PASS saga: a kitchen rejection at the pivot voids the authorization and unwinds the rest
PASS saga: a compensatable step after the pivot is refused at definition time
PASS saga: capture failing after the pivot is retried 3 times, then STUCK, never compensated
PASS saga: the retry job resumes a STUCK saga to completion
PASS saga: a new coordinator resumes from the log without repeating finished steps
all 24 tests passed
```

## Follow-ups to expect

| Question | A short answer |
|---|---|
| "Make the rate limiter distributed." | Redis with an atomic script (GCRA: one timestamp per key), a short timeout, and a local fallback that enforces each instance's share |
| "What if two instances run the same saga?" | Claim the saga with a lease in the log's database; the step idempotency makes a duplicate harmless anyway |
| "How long do you keep processed ids?" | Longer than the longest redelivery window: the broker's retention for replays, or 24 h for idempotency keys as FoodFleet does |
| "Why not retry inside the breaker?" | Retries go outside the breaker (each attempt is counted) and at one layer only; inside, a retry storm hides from the breaker |
| "How would you test the breaker without sleeping?" | Inject the clock, as these tests do |
