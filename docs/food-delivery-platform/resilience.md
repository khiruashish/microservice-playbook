---
title: "Resilience"
description: "What FoodFleet does when something fails: a matrix generated from the platform model of which journeys fail or degrade when each service is down; the defaults every call gets (deadlines, one layer of retries, breakers, bulkheads, admission control); how overload is shed by priority; the chaos experiments that keep it honest; and recovery from losing a zone, a region or a cell, each with what its chapter measured. Plus the conflict between ADR-018's and ADR-043's numbers."
progress: done
---

# Resilience

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

FoodFleet's resilience starts from one rule: every synchronous dependency declares what happens when it fails ([ADR-002](../foundations/ch02-characteristics-principles.md)). Two Q4 incidents were caused by optional features, recommendations and the ETA, sitting on the critical path with no timeout and no fallback. The rest of this page is how that rule is carried out, and checked.

## What fails when a service is down

`check.py` walks each journey's calls in the [platform model](index.md#how-these-pages-are-made). A service is **required** by a journey if the journey reaches it only through calls without a fallback; it **degrades** the journey if every path to it has one:

--8<-- "code/foodfleet/model/generated/impact.md"

Services not listed affect none of the three journeys synchronously: they work from events, or serve the partner and rider apps.

What the matrix says:

- **Checkout depends on four services**: the edge, the customer BFF, order-service and pricing-service. Payment, the kitchen and delivery aren't on the list, because the saga reaches them through Kafka after the order is placed ([order lifecycle](order-lifecycle.md)). If payment-service is down, orders queue in `PLACED` instead of failing.
- **Pricing matters twice.** Browsing degrades without it (menus show the last known or base price), but checkout fails: no order without a price ([ADR-016](../architecture-patterns/ch16-strangler-fig-migration.md)). That makes pricing-service, extracted from the monolith by strangler fig, as critical as order-service.
- **Browsing needs the zone service**, because a customer can't see a menu without knowing whether the restaurant delivers to them. That's chapter 6's choice ("serviceability is required"); a cached zone map in the BFF would turn it into a degradation.
- **Tracking depends only on its own gateway.** Positions arrive through Kafka; the ETA is optional.

The calls that have no declared fallback yet are listed on the [communication map](communication-map.md#calls-with-no-fallback-yet).

## The defaults every call gets

These come with the platform's client starter and `ResiliencePolicies`, so a service gets them without configuring anything:

| Mechanism | Default | What the chapter measured | Decision |
|---|---|---|---|
| Deadline | Set at the edge; each hop's timeout is min(ceiling, time left − reserve); no call with less than 20 ms left | — | [ADR-042](../resilience/ch42-failure-modes.md), [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md) |
| Retries | One layer only, the service closest to the dependency; idempotent calls; full jitter; within a 10% budget | Three layers of retries sent 27 calls per request to a dead dependency; a budget, 1.4 | [ADR-042](../resilience/ch42-failure-modes.md) |
| Circuit breaker | A 5-second time window, at least 20 calls, opens at 50% failures or 50% of calls over 1 s, open 5 s, 5 trial calls, 4xx ignored | Default breakers opened in 0.5–10 s depending on traffic and never noticed a 3-second dependency; this one opened in 2–4 s at 100/s and at 5/s | [ADR-043](../resilience/ch43-resilience4j-deep-dive.md) |
| Bulkhead | Sized by Little's law, rejecting instead of waiting | A blocking limit queued about 4,000 checkout callers | [ADR-043](../resilience/ch43-resilience4j-deep-dive.md) |
| Admission control | AIMD per service, 90% for critical requests and 10% for sheddable; `503` with `Retry-After: 1` | Through a 2.5× slowdown, no limit served 4% and didn't recover; AIMD served 53–54% of a possible 57% | [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md) |
| Hedging | Idempotent reads only, after the dependency's p95, within a 5% budget | At 85% load, unbudgeted hedges and retries served 18–68% of users; a 5% budget 99% | [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md), [ADR-066](../performance-scalability/ch66-latency-virtual-threads-reactive.md) |
| Load balancing | Power of two choices; per request, through the mesh | P2C halved the users hit by a gray-failing pod at fan-out 10 | [ADR-042](../resilience/ch42-failure-modes.md), [ADR-057](../deployment/ch57-service-mesh.md) |
| Brownouts | Optional features behind a dimmer; services others depend on for optional features limit themselves | In a spike, a self-limiting dependency kept recommendations on 65–66% of pages; timeouts alone, 1–2% | [ADR-044](../resilience/ch44-timeouts-retries-backpressure.md) |

!!! warning "ADR-018 and ADR-043 set different numbers"
    [ADR-018](../architecture-patterns/ch18-resilience-patterns.md) set a breaker over 50 calls (50% failures or 80% slow), open 5 s, 3 trial calls, and at most 2 attempts per call. [ADR-043](../resilience/ch43-resilience4j-deep-dive.md), 25 chapters later, set a 5-second time window with a 20-call minimum, 50% failures or 50% slow over 1 s, 5 trial calls, and 3 attempts. ADR-043 measured why: a count-based window took about ten seconds to open on quiet endpoints. It doesn't say it supersedes ADR-018, so both stand in the log. **The platform follows ADR-043**, which is newer and measured, and a superseding note on ADR-018 would make that official ([ADR-073](../governance-org/ch73-adrs-fitness-functions.md)).

## Overload: shed browsing to keep checkout

Lunch and dinner are FoodFleet's peaks. When demand exceeds capacity, something has to be refused, and FoodFleet chooses what ([ADR-044](../resilience/ch44-timeouts-retries-backpressure.md)):

1. **The edge marks each route** critical (checkout, payment) or sheddable (browsing, recommendations, tracking). New routes are sheddable until someone classifies them.
2. **Every service's admission filter** reserves 90% of its concurrency for critical requests.
3. **Optional features dim first**: recommendations, ETAs and rankings go before menus do.
4. **Rejected requests get `503` and `Retry-After: 1`** at once, rather than waiting until they time out.

Measured at twice the capacity, partitioning served 79–81% of checkouts against 49–50% without it. Overload costs browsing first, and the service recovers as soon as load drops.

## Chaos experiments

Resilience settings decay: a lost read timeout reached staging unnoticed in chapter 45. So each critical journey owns a catalogue of experiments ([ADR-045](../resilience/ch45-chaos-engineering.md)):

| Journey | Example experiments |
|---|---|
| Browse | pricing-service slow (the menu should show base prices); zone-service down (the journey fails, by design) |
| Order | promotions-service down (orders without coupons); payment-service down (orders queue in `PLACED`) |
| Pay | the provider black-holes requests (the ambassador's 400 ms timeout and breaker) |
| Track | eta-service down (positions without an ETA); a tracking instance killed (clients reconnect with jitter) |

Every experiment checks the steady state first and has a guard on the journey's SLO that aborts after three bad seconds; that guard cut the users hurt by one experiment from about 1,500 to 150–180. Faults include black holes and slow trickles, not only errors: three of four common client configurations hung on a trickle. Experiments run in CI with Toxiproxy and as Chaos Mesh workflows in staging; experiments that once found a weakness run weekly, game days quarterly, and production experiments only for hypotheses that have held in staging. The examples in the table are this page's reading of the model's fallbacks; the chapter defines the catalogue's structure, not its entries.

## Losing a zone, a region or a cell

| Failure | What happens | Measured | Decision |
|---|---|---|---|
| A zone | Tier 0 data has a synchronous standby in another zone; automatic failover with fencing | No commits lost; promotion in 0.1 s | [ADR-046](../resilience/ch46-disaster-recovery.md) |
| A region | Asynchronous standby in Hyderabad; promotion only by runbook, after fencing the old region | A lost region cost 85–278 commits; a 2-second partition before it, 17,000–22,700 if promoted without fencing | [ADR-046](../resilience/ch46-disaster-recovery.md) |
| A cell (a bad deploy, a poison tenant) | Tenants placed in cells by city; shared fleets shuffle-sharded 2 of 8 | A poison tenant reached 12.7% of tenants with 8 cells, 1.7–3.6% with shuffle shards, against 100% with one pool | [ADR-046](../resilience/ch46-disaster-recovery.md) |
| A client following DNS | All clients list every host with `targetServerType=primary` | Clients on a 30 s DNS TTL resumed 24–33 s after a 0.1 s promotion | [ADR-046](../resilience/ch46-disaster-recovery.md) |
| The WAL archive | WAL streamed to the other region | A 60-second archive timeout lost 298–317 orders; streaming, none | [ADR-046](../resilience/ch46-disaster-recovery.md) |

Regional failover needs a human decision, so its recovery time is minutes, not seconds. That's deliberate: a health check from one region can't tell a dead region from a broken link, and promoting during a partition forks the order history. The steps are in the [runbooks](runbooks.md).
