---
title: "Lab 10 — Saga with Compensation"
description: "Place an order across five services without a distributed transaction: an orchestrated saga with compensations before the kitchen's pivot and retries after it, a durable saga log that survives orchestrator crashes, idempotent participants, and the same flow choreographed through events. Five scripted failures run both ways side by side, then 10,000 sagas with random faults: no money held for uncooked food, and 165 double charges once payment-service stops being idempotent. About an hour, no Docker."
progress: done
---

# Lab 10 — Saga with Compensation

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **You'll build** | The place-order saga: an orchestrator, its log, five participants, and the choreographed equivalent |
| **Time** | About an hour |
| **Builds on** | [Lab 07](lab-07-kafka.md) and [lab 09](lab-09-outbox-cdc.md) (in production, saga commands and replies travel as events through an outbox) |
| **Chapters** | [12 (database per service)](../architecture-patterns/ch12-database-per-service.md), [14 (sagas)](../architecture-patterns/ch14-saga-pattern.md), [15 (outbox)](../architecture-patterns/ch15-outbox-inbox-cdc.md) |
| **Code** | `code/code-lab/lab-10-saga/`, on [chapter 14](../architecture-patterns/ch14-saga-pattern.md)'s saga engine |
| **Needs** | JDK 25 only |

!!! note "About the lab code"
    This lab **was run**: everything here is plain Java, compiled and run with JDK 25.0.4, and the outputs below are copied from that run. The saga engine and participants are chapter 14's; the lab adds `SagaTrace`, which scripts five failures and runs each through both styles. Message transport is in memory, so the saga's logic is what you exercise; labs 07 and 09 cover the transport.

## The problem: five databases, no rollback

Placing an order touches five services, each with its own database ([ADR-012](../architecture-patterns/ch12-database-per-service.md)). If the kitchen refuses an order after the payment was authorized, no transaction rolls the payment back. FoodFleet's first version didn't handle this, and in its first busy weekend about 0.4% of orders ended with money held for food that was never cooked.

A **saga** replaces the one transaction with a sequence of local ones. Every step before the point of no return has a **compensation** that semantically undoes it; if a later step fails, the compensations run in reverse.

## 1. Define the saga: where's the point of no return?

```java title="PlaceOrderSaga.java"
--8<-- "code/chapters/ch14-saga/src/main/java/dev/foodfleet/ch14/placeorder/PlaceOrderSaga.java:7:57"
```

Three kinds of step ([ADR-014](../architecture-patterns/ch14-saga-pattern.md#8-enterprise-case-study)):

| Kind | Steps | If it fails |
|---|---|---|
| **Compensatable** | create order, reserve coupon, authorize payment | Undo it and everything before it |
| **Pivot** | kitchen accepts | Rejected: compensate. Unknown outcome: stop and retry, because the food may be cooking |
| **Retriable** | capture, request rider, approve | Retry until it succeeds; never compensate |

The order matters: the coupon check is cheap and fails often, so it runs before payment; once the kitchen is cooking, there's no undo, so everything after it must be able to succeed eventually.

## 2. The orchestrator writes before it moves

```java title="SagaOrchestrator.java (forward)"
--8<-- "code/chapters/ch14-saga/src/main/java/dev/foodfleet/ch14/saga/SagaOrchestrator.java:84:107"
```

Every transition goes to the **saga log** before the orchestrator moves on. A crashed orchestrator is replaced by another that reads the log and resumes; in production the log is a table in ordering-service's database, written in the same transaction as the outbox row carrying the next command ([lab 09](lab-09-outbox-cdc.md)).

The subtle line is the compensation start point. A **rejection** means the step didn't happen: undo the steps before it. **Exhausted retries** mean the outcome is unknown, since the reply may have been lost after the work was done: undo this step too. That's only safe if compensations are harmless when there's nothing to undo.

## 3. Participants keep two promises

```java title="Participant.java"
--8<-- "code/chapters/ch14-saga/src/main/java/dev/foodfleet/ch14/placeorder/Participant.java:31:63"
```

- **Idempotent actions.** A repeated `(saga id, operation)` has no second effect and returns the original outcome. The orchestrator retries after timeouts and after its own crashes, so every participant sees repeats.
- **Safe compensations.** Undoing something that never happened, or was already undone, does nothing.

In a real service, `done` is a table keyed by saga id and step, written in the same transaction as the effect: lab 07's inbox, used for commands.

## 4. Five failures, two styles

`SagaTrace` runs one order per scenario, first through the orchestrator, then through chapter 14's choreographed version, where each service reacts to the previous one's event and nobody holds the whole flow:

```java title="SagaTrace.java (scenarios)"
--8<-- "code/code-lab/lab-10-saga/src/dev/foodfleet/lab10/SagaTrace.java:50:56"
```

The run (each failure is scripted for its first calls; every later call succeeds):

```console
$ code/code-lab/lab-10-saga/run.sh
== card declined ==
orchestrated -> COMPENSATED
  log: started > done create order > done reserve coupon > failed authorize payment > compensated reserve coupon > compensated create order > compensated
  orchestrated: order REJECTED, held ₹0, coupon uses left 10, captures 0 -> ok
choreographed: no saga log; the events were:
  events: OrderCreated > CouponReserved > PaymentDeclined > CouponReleased > OrderRejected
  choreographed: order REJECTED, held ₹0, coupon uses left 10, captures 0, riders 0

== authorize: reply lost 3 times ==
orchestrated -> COMPENSATED
  log: started > done create order > done reserve coupon > failed authorize payment > compensated authorize payment > compensated reserve coupon > compensated create order > compensated
  orchestrated: order REJECTED, held ₹0, coupon uses left 10, captures 0 -> ok
choreographed: no saga log; the events were:
  events: OrderCreated > CouponReserved > DEAD-LETTER(CouponReserved)
  choreographed: order PENDING, held ₹450, coupon uses left 9, captures 0, riders 0

== rider service down (after the pivot) ==
orchestrated -> STUCK
  log: started > done create order > done reserve coupon > done authorize payment > done kitchen accepts > done capture payment > stuck at request rider
  after retryStuck -> COMPLETED
  orchestrated: order APPROVED, held ₹450, coupon uses left 9, captures 1 -> ok
choreographed: no saga log; the events were:
  events: OrderCreated > CouponReserved > PaymentAuthorized > TicketAccepted > DEAD-LETTER(TicketAccepted) > OrderApproved
  choreographed: order APPROVED, held ₹450, coupon uses left 9, captures 1, riders 0
```

(The happy path and the kitchen rejection are in the full output; both styles handle them the same way.)

What the two failures that differ show:

- **A lost reply.** payment-service authorized ₹450, but its replies never arrived. The orchestrator gave up after three attempts, treated the outcome as unknown, and voided the authorization, which a safe compensation does whether or not it took effect. The choreographed flow dead-lettered the event and stopped: the order is **PENDING**, ₹450 is held and a coupon use is gone until someone reads the dead-letter topic.
- **A failure after the pivot.** The orchestrator marked the saga **STUCK** at "request rider" and, once the rider service was back, `retryStuck` finished it from the log. In the choreographed flow, three services each react to `TicketAccepted` separately: payment captured, ordering approved, delivery failed and dead-lettered. The order says **APPROVED with no rider**, and no component knows.

That's the core of the trade-off chapter 14 draws: in choreography the flow exists only in the event trace, so a failure in the middle leaves each service's state locally consistent and the whole inconsistent. FoodFleet orchestrates place-order and keeps choreography for flows with no compensation, where each reaction stands alone ([chapter 14](../architecture-patterns/ch14-saga-pattern.md#choosing-orchestration-or-choreography)).

## 5. 10,000 sagas, with crashes

The second half of `run.sh` is chapter 14's simulation: 10,000 orders with random rejections, transient failures, lost replies and orchestrator crashes, each crash resumed from the log by a new orchestrator.

```console
== idempotent participants ==
10000 sagas, 351 orchestrator crashes (resumed from the log)
first pass:  {COMPLETED=8638, COMPENSATED=1321, STUCK=41}
after retry: {COMPLETED=8679, COMPENSATED=1321}
authorizations voided: 475
money held: ₹4,339,500, expected ₹4,339,500, extra ₹0 (0 double charges)
coupons leaked: 0, approved orders without a kitchen ticket: 0

== payment-service NOT idempotent ==
10000 sagas, 348 orchestrator crashes (resumed from the log)
first pass:  {COMPLETED=8540, COMPENSATED=1417, STUCK=43}
after retry: {COMPLETED=8583, COMPENSATED=1417}
authorizations voided: 518
money held: ₹4,374,000, expected ₹4,291,500, extra ₹82,500 (165 double charges)
coupons leaked: 0, approved orders without a kitchen ticket: 0
```

- **Every saga ended** COMPLETED or COMPENSATED after one retry pass; none was lost to a crash.
- **The 41 STUCK sagas** are the dashboard's job in production: a scheduled job retries them once the participant is healthy, and a person looks at any that stay stuck.
- **Idempotency is the whole difference** between the two runs: the same saga engine, with a payment-service that authorizes again when a request repeats, held ₹82,500 too much across 165 customers.

## Run it

```bash
code/code-lab/lab-10-saga/run.sh      # needs only a JDK 25 on the PATH, or JAVA_HOME set
```

## In production

| Lab | FoodFleet | Why |
|---|---|---|
| Saga log in memory | A table in ordering-service, written with the outbox row for the next command | Survives the orchestrator's pod ([ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md)) |
| Method calls | Commands and replies over Kafka, keyed by saga id | Participants can be down without losing the saga |
| `done` sets | An idempotency table per participant, keyed by saga id and step, returning the stored outcome | Repeats are normal: retries, redeliveries, resumed sagas |
| `retryStuck` by hand | A retry job, a dashboard of STUCK sagas, and an alert on their count and age | Steps after the pivot must eventually succeed |
| Hand-built orchestrator | The same for place-order; a workflow engine for flows with people in them | ADR-014: refund review and onboarding wait on humans |

## Check yourself

- [ ] `run.sh` ends with `all promises kept`, and the simulation shows 0 double charges with idempotent participants.
- [ ] You can say, for each step of the saga, what happens if it fails with a rejection and if it times out.
- [ ] You can point to the two scenarios where choreography left the order inconsistent, and say who would notice.

## Break it

!!! question "Exercises"
    1. In `SagaOrchestrator.forward`, compensate from `i - 1` for exhausted retries too (as for a rejection). Run the "reply lost" scenario. What does `SagaTrace` report, and why does `check` catch it?
    2. Move "kitchen accepts" before "authorize payment". `SagaDefinition` refuses to build it: why? Remove that check, and find the scenario that cooks food nobody paid for.
    3. Make `Participant.compensate` undo unconditionally, without checking `done`, and add a scenario where `payment.authorize` fails `TRANSIENT` three times (the request never arrived). What does `SagaTrace` report for the money held? (We got `held ₹-450 … BROKEN`: a refund of money never taken.)
    4. Add a step after the pivot that can be *rejected* (say, the restaurant's packaging check). What should happen to the saga, given there's no going back?

**Next:** [Lab 11](lab-11-cqrs-event-sourcing.md) keeps an order as its events and builds read models from them.
