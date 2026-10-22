---
title: "System Design Rounds"
description: "Five system design prompts that recur in microservice interviews, each answered in the same 45-minute structure with numbers from the chapters: food delivery (the full FoodFleet answer), ride matching (nearest drivers: PostgreSQL's KNN index answered 3,602–3,694 queries a second against Redis GEOSEARCH's 939–952 for a sorted top 10), payments, notifications and a distributed rate limiter."
progress: done
---

# System Design Rounds

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

A system design round is a 45-minute conversation about trade-offs. The structure below works for every prompt; the five designs after it apply it to the prompts that come up most in microservice interviews.

## The structure

| Minutes | Step | What to say |
|---|---|---|
| 0–5 | **Clarify** | Users, flows in scope, scale, what matters most, what may be stale |
| 5–10 | **Estimate** | Rates at peak, data sizes, connections; then *which number is the hard one* |
| 10–15 | **API and data** | The main calls with idempotency keys; the core entities and who owns them |
| 15–25 | **High-level design** | Boxes in layers, sync vs async on each arrow, one sentence per box |
| 25–40 | **Deep dives** | The two hardest parts, with alternatives and a number for each choice |
| 40–45 | **Failure and scale** | What breaks first, what users see, how you'd know |

Two habits lift any answer: **say which number is the hard one** after estimating, and **give each choice a measured reason** rather than an adjective.

## 1. Food delivery (Swiggy, Zomato)

The full worked answer is the [FoodFleet design interview](../food-delivery-platform/system-design-interview.md). In brief:

- **The hard numbers** are position writes (12,500 a second for 50,000 riders) and live map streams (100,000+), not orders (~170 a second at peak).
- **Checkout** is an orchestrated saga with the kitchen's acceptance as the pivot; only four services are on its synchronous path.
- **Tracking** is server-sent events through a dedicated gateway, about 20,000 streams per instance.

## 2. Ride matching (Uber, Ola, Rapido)

**Clarify:** match a rider request to a nearby available driver in seconds; drivers report positions every few seconds; pricing surges with demand.

**Estimate:** 200,000 drivers online, a ping every 4 s → **50,000 position writes a second**. Match requests: a few hundred a second per city at peak. The write rate is the hard number; the nearest-driver query is the hard *latency*.

**Design:**

- **Positions** go to an in-memory geo index per city, and asynchronously to history. Chapter 29 measured Redis at 3.8–5.6× PostgreSQL's position-write rate at the same durability ([ch. 29](../data/ch29-polyglot-persistence.md)).
- **The nearest-driver query is where a candidate can stand out.** Redis `GEOSEARCH … ASC COUNT 10` computes and sorts *every* driver inside the circle before returning ten: with about 1,900 drivers in a city-centre circle, 939–952 queries a second at 8.4–8.5 ms. PostgreSQL's KNN index stops after ten: 3,602–3,694 a second at 2.2 ms. `ANY` makes Redis fast (0.5–0.9 ms) but returns any ten, not the nearest ([ch. 29](../data/ch29-polyglot-persistence.md#4-design-considerations-trade-offs)). Options: a smaller radius in dense areas, a cell-based index (geohash or H3) with per-cell sets, or a KNN index.
- **Matching** is a separate service owned by a specialist team: assignment is an optimisation problem that changes often ([ch. 7](../foundations/ch07-conways-law-team-topologies.md)).
- **Offers are leases**: a driver is offered to one rider at a time, with a timeout, so two riders can't get the same driver.
- **Live updates** to both apps over WebSocket or SSE ([ch. 26](../communication/ch26-websockets-sse.md)).

**Failure:** a city's index lost → rebuilt from the next round of pings within seconds; positions are ephemeral, so Redis without persistence is acceptable for them, but not for anything a driver is paid on.

## 3. Payments

**Clarify:** accept payments by card and UPI through providers; refunds; no card numbers stored; exactly-once charging.

**Estimate:** modest rates (hundreds a second); the hard part is **correctness**, not throughput.

**Design:**

- **An idempotency key per payment** from the client, claimed atomically and stored with the result in the same transaction; passed to the provider. In chapter 28, only an atomic claim *before* the side effect gave one charge per key ([ch. 28](../communication/ch28-idempotency-correlation.md)).
- **A double-entry ledger**, append-only, balances derived; corrections as reversing entries ([ch. 3](../foundations/ch03-distributed-systems-fundamentals.md#fintech-analogue-the-double-entry-ledger)).
- **Authorize, then capture** once the service is delivered; a saga with void and refund as compensations ([ch. 14](../architecture-patterns/ch14-saga-pattern.md)).
- **Provider calls through one controlled place** with timeouts, retries only for idempotent requests, a breaker ([ADR-017](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)).
- **Unknown outcomes** (timeouts) resolved by status queries and daily reconciliation.
- **Zero loss**: synchronous standby in another zone; regional failover only after fencing ([ch. 46](../resilience/ch46-disaster-recovery.md)).

**The number to quote:** 165 customers double-charged in 10,000 sagas when one payment service wasn't idempotent; 0 when it was ([ch. 14](../architecture-patterns/ch14-saga-pattern.md)).

The [Goldman Sachs & JP Morgan](goldman-sachs-jpmorgan.md) page goes deeper.

## 4. Notifications

**Clarify:** push, SMS and email from business events; user preferences and quiet hours; some messages urgent (OTPs), some bulk (campaigns).

**Estimate:** bursts dominate: a campaign to 10 million users, or every order's status change at dinner.

**Design:**

- **Events in, not calls in.** Services publish business events; the notification service subscribes. Nobody waits for an SMS ([ch. 25](../communication/ch25-asynchronous-messaging.md)).
- **Separate queues by priority**, so a campaign can't delay an OTP. A task queue with per-message acknowledgement and priorities suits this; chapter 25 keeps RabbitMQ for exactly such work.
- **Idempotent per message**: the event id is the dedupe key, so a redelivery doesn't send twice ([ADR-025](../communication/ch25-asynchronous-messaging.md)).
- **Rate limits per provider and per user**; providers throttle, and users unsubscribe from noisy apps.
- **Provider failover** behind adapters, with a breaker per provider ([ch. 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md), [ch. 43](../resilience/ch43-resilience4j-deep-dive.md)).
- **No personal data in the events beyond ids**; the notification service looks up the contact details it needs ([ADR-019](../architecture-patterns/ch19-event-driven-architecture.md)).

## 5. A distributed rate limiter

**Clarify:** limit requests per client and per tenant across many gateway instances; low latency; what happens when the store is down.

**Design:**

- **Algorithm:** token bucket or GCRA (a token bucket stored as one timestamp per key); fixed windows allow twice the rate across a boundary ([ch. 10](../architecture-patterns/ch10-api-gateway-bff.md)).
- **Shared state in Redis**, updated atomically in one script. Chapter 40 measured per-pod limits letting four times the budget through; Redis-shared ones, the budget, at 0.1–0.4 ms per decision ([ch. 40](../security/ch40-api-security.md)).
- **Per tenant and per client within a tenant.** A global limit left other tenants 0.2% of their requests while one tenant's loop ran; per-tenant limits, 100%.
- **When Redis is unreachable**, each instance enforces its share of the limit and an alert fires: neither "allow everything" nor "refuse everything".
- **Responses:** `429` with `Retry-After`.

The [coding rounds](coding-rounds.md) page has a tested token bucket to write on the whiteboard.
