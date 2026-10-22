---
title: "Part 6 — Resilience & Fault Tolerance"
description: "How FoodFleet stays up when its parts don't, and a decision guide for resilience: failure modes and cascades, Resilience4j and Spring Framework 7, timeouts, retries, hedging and load shedding, chaos experiments, and disaster recovery across zones and regions, each measured with real services, faults and databases."
progress: done
---

# Part 6 — Resilience & Fault Tolerance

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

In a system of sixty services, something is always failing: an instance is slow, a dependency is restarting, a zone is losing packets. Most outages aren't caused by those failures but by how the rest of the system reacts to them: callers that wait for ever, retries that multiply load, queues that hold work nobody is waiting for any more, a failover that creates two histories. Part 6 starts with how failures spread, then builds the defences layer by layer, tests them by breaking things on purpose, and ends with losing a whole region.

Every chapter measures its claims on real systems: HTTP services and clients on the JDK, Resilience4j 2.4.0, Spring Framework 7, Netflix concurrency-limits, Toxiproxy faults, Chaos Mesh and LitmusChaos CRDs, and PostgreSQL 16 clusters with streaming replication. The measured output is in the text, and the code to reproduce it is in the repository.

## FoodFleet's resilience architecture

Each layer limits what the failure below it can do to the layer above. **Owner** is the team that runs the control; the last column is where the handbook builds it.

| Layer | Question | FoodFleet's control | Owner | Chapters |
|---|---|---|---|---|
| Call | How long may this call take, and may it be repeated? | A deadline per user request, split into per-hop budgets; retries at one layer, within a 10% budget, with full jitter; hedges for idempotent reads within a 5% budget | Each service team | [42](ch42-failure-modes.md), [44](ch44-timeouts-retries-backpressure.md) |
| Dependency | When should we stop calling it? | Resilience4j circuit breakers with 5 s time windows and slow-call thresholds; bulkheads; fallbacks on the outermost decorator | Each service team | [43](ch43-resilience4j-deep-dive.md) |
| Service | How much work can we accept? | AIMD admission limits partitioned by priority; LIFO waiting; queue-age shedding below callers' timeouts; brownouts for optional features | Each service team | [42](ch42-failure-modes.md), [44](ch44-timeouts-retries-backpressure.md) |
| Fleet | Which instance gets the call? | Power-of-two-choices balancing; jittered reconnects | Platform | [42](ch42-failure-modes.md) |
| Verification | Do the defences actually work? | A chaos catalogue per critical journey, with SLO hypotheses and automatic guards; weekly in staging, quarterly game days | Each team + SRE | [45](ch45-chaos-engineering.md) |
| Data | How much can we lose, and how fast do we recover? | Recovery tiers; quorum-synchronous standbys across zones; asynchronous cross-region standby; fenced failover; streamed WAL; rehearsed restores | Data platform + service teams | [46](ch46-disaster-recovery.md) |
| Blast radius | How far can one failure reach? | Cells by city; shuffle sharding in shared fleets; fault experiments on one instance first | Platform | [45](ch45-chaos-engineering.md), [46](ch46-disaster-recovery.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| Latency that explodes near capacity | Utilisation headroom, explicit concurrency limits | [42](ch42-failure-modes.md) | p99 at 4.5× the service time at 52% load, 12× at 97% |
| Retries that multiply through a call chain | Retry at one layer, or a retry budget | [42](ch42-failure-modes.md) | 3 attempts at every layer of three: 27 calls per user to a dead dependency; a 10% budget: 1.4 |
| An outage that outlives its trigger | Shedding work older than the caller's timeout | [42](ch42-failure-modes.md) | With retries, 0% served for 30 s after a 5 s slowdown; shedding at 150 ms: back to 100% in the next window |
| Reconnect storms after a restart | Exponential backoff with full jitter | [42](ch42-failure-modes.md) | 600 clients reconnected in under 6 s with full jitter, 42 s with plain exponential backoff |
| A breaker that never opens, or never closes | Time-based windows, a slow-call threshold, short open state | [43](ch43-resilience4j-deep-dive.md) | Defaults: 7.5–10 s to open at 5 calls/s, a 3 s dependency never "slow"; a 5 s window opened in 2–4 s |
| Fallbacks that disable retries | The fallback on the outermost decorator | [43](ch43-resilience4j-deep-dive.md) | Fresh menus 68–70% with the fallback inside the retry, 97–98% outside |
| A timeout that doesn't stop the work | Cancellable work, interrupt-aware clients | [43](ch43-resilience4j-deep-dive.md) | `CompletableFuture` work ran to the end every time after the TimeLimiter fired |
| Tail latency with spare capacity | Hedged reads within a budget | [44](ch44-timeouts-retries-backpressure.md) | p99 from 1 s to 88 ms for 10–11% more calls; unbudgeted hedges at 85% load served 18–68% |
| A slowdown that becomes an outage | Adaptive concurrency limits (AIMD) | [44](ch44-timeouts-retries-backpressure.md) | No limit: 4% served in time, no recovery; AIMD: 53–54% of a possible 57% |
| Checkouts lost to browsing traffic | Limits partitioned by priority, LIFO waiting | [44](ch44-timeouts-retries-backpressure.md) | 79–81% of checkouts served under 2× overload instead of about 50% |
| Not knowing whether the defences work | Experiments with an SLO hypothesis and an abort guard | [45](ch45-chaos-engineering.md) | A guard cut hurt users from about 1,500 to 150–180; a 1 KB/s trickle hung every client but one |
| A breaker that a fault can't trigger | A read timeout under the breaker | [45](ch45-chaos-engineering.md) | A black hole without a read timeout: the breaker never opened, 19 of 20 windows failed |
| Zero data loss across zones | Quorum-synchronous standbys | [46](ch46-disaster-recovery.md) | 0 commits lost; 1.3–1.7 ms per commit instead of 0.9–1.0 ms in-zone; ~63 ms across a 60 ms round trip |
| Losing a region | Asynchronous cross-region standby, fencing before promotion | [46](ch46-disaster-recovery.md) | 85–278 commits lost when link and primary died together; 17,000–22,700 when the link failed 2 s first |
| A failover slower than the database | Multi-host clients, bounded driver timeouts | [46](ch46-disaster-recovery.md) | Promotion 0.1 s; role-checking clients resumed 0.3 s later, clients on a 30 s DNS TTL after 24–33 s |
| Bad writes on every replica | Backups with streamed WAL, timed restores | [46](ch46-disaster-recovery.md) | `archive_timeout = 60 s` lost 298–317 orders, streamed WAL 0 |
| One tenant taking down everyone | Cells, shuffle sharding | [46](ch46-disaster-recovery.md) | A poison tenant downed 100% of a shared pool, 12.7% with 8 cells, 1.7–3.6% with shuffle shards |

## The chapters

<div class="grid cards" markdown>

-   **[42. Failure Modes & Cascading Failures](ch42-failure-modes.md)**

    ---

    Queueing near saturation, retry amplification, metastable failures, reconnect herds, gray failures and fan-out. *Real HTTP services and load.*

-   **[43. Resilience4j & Spring Framework 7 Resilience](ch43-resilience4j-deep-dive.md)**

    ---

    Breakers, retries, bulkheads, rate limiters, time limiters, decorator order, `@Retryable` and `@ConcurrencyLimit`. *Defaults read from the source.*

-   **[44. Timeouts, Retries, Backpressure & Load Shedding](ch44-timeouts-retries-backpressure.md)**

    ---

    Deadline budgets, hedging, adaptive concurrency, priority shedding, brownouts, `Flow` backpressure. *Netflix concurrency-limits, JDK 25.*

-   **[45. Chaos Engineering](ch45-chaos-engineering.md)**

    ---

    Hypotheses, blast radius, abort guards, fault fidelity, Chaos Mesh and LitmusChaos. *Toxiproxy against real clients.*

-   **[46. Disaster Recovery & Multi-Region](ch46-disaster-recovery.md)**

    ---

    RPO and RTO, synchronous and asynchronous replication, failover and fencing, PITR, cells and shuffle sharding. *PostgreSQL 16 clusters.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Building services already** (🔵 L2) | 42 → 43 → 44 | [Part 7: Observability](../observability/index.md) |
| **Leading a team** (🟣 L3) | 42 → 44 → 43 → 45 | [Part 7: Observability](../observability/index.md) |
| **Designing a platform** (🔴 L4) | 42 → 44 → 45 → 46 | [Part 8: Deployment & Platform](../deployment/index.md) |
| **Preparing for interviews** | 42, 44, 46 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-042 · Chapter 42</span><span class="what">Deadlines on every hop; retries at one layer or within a 10% budget, with full jitter; concurrency limits that reject; queue-age shedding; P2C balancing.</span></li>
<li><span class="when">ADR-043 · Chapter 43</span><span class="what">Resilience4j for breakers, bulkheads and rate limits with measured settings; Spring Framework 7 annotations for simple cases; no library defaults in production.</span></li>
<li><span class="when">ADR-044 · Chapter 44</span><span class="what">Per-hop budgets; hedges and retries within budgets; AIMD admission partitioned by priority; brownouts; bounded buffers.</span></li>
<li><span class="when">ADR-045 · Chapter 45</span><span class="what">A chaos catalogue per critical journey, each experiment with an SLO hypothesis, a guard and the smallest blast radius.</span></li>
<li><span class="when">ADR-046 · Chapter 46</span><span class="what">Recovery tiers; zero-loss quorum replication in the region, asynchronous across regions with fencing; multi-host clients; streamed WAL; cells by city.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch42-failure-modes,chapters/ch43-resilience4j,chapters/ch44-timeouts-retries-backpressure,chapters/ch45-chaos-engineering,chapters/ch46-disaster-recovery -am verify
    ```
    JDK 25 and Maven 3.9+. Each chapter's `verify/` folder holds its experiments and says what they need: a JDK for 42; Resilience4j, Spring Framework and concurrency-limits classes built from their release tags (43, 44); Toxiproxy and Python with PyYAML and jsonschema (45); PostgreSQL 16 and Toxiproxy (46).
