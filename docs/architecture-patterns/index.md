---
title: "Part 2 — Architecture & Patterns"
description: "Pattern map and selection guide: hexagonal architecture, gateways and BFFs, discovery, data ownership, CQRS and event sourcing, sagas, outbox, strangler fig, sidecars, resilience, event-driven architecture and anti-patterns, each measured on FoodFleet."
progress: done
---

# Part 2 — Architecture & Patterns

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Part 1 decided **what** the services are. Part 2 is about **how they work together**: how a service is structured inside, how clients reach it, how services find each other, who owns which data, how a business transaction spans services, how events are published and consumed, how a monolith is retired, and how the system survives its dependencies' bad days.

Every chapter measures its pattern instead of only describing it: a simulation, a benchmark or a real run, with the verified output in the text and the code to reproduce it.

## Pattern map

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| Business logic tangled with frameworks and providers | Hexagonal / onion architecture | [9](ch09-hexagonal-clean-architecture.md) | Swapping a payment provider touches one adapter; ArchUnit guards the rule |
| Clients calling many services; cross-cutting edge concerns | API gateway, BFF | [10](ch10-api-gateway-bff.md) | One call per screen instead of four sequential ones; rate limiting at the edge |
| Finding healthy instances as they come and go | Service discovery | [11](ch11-service-discovery-patterns.md) | Errors from stale registries vs Kubernetes Services |
| Services sharing tables | Database per service, API composition | [12](ch12-database-per-service.md) | An ownership audit of every SQL statement; composition latency |
| Reads and writes with different shapes; full history needed | CQRS, event sourcing | [13](ch13-cqrs-event-sourcing.md) | Projections, snapshots and upcasting on a rider wallet |
| One business transaction across several services | Saga | [14](ch14-saga-pattern.md) | 10,000 sagas with crashes: 0 double charges with idempotent participants, 165 without |
| Saving data and publishing an event reliably | Transactional outbox, inbox, CDC | [15](ch15-outbox-inbox-cdc.md) | Dual writes lost or invented ~1.6% of events; outbox + inbox: 0 |
| Replacing a monolith without a big bang | Strangler fig, ACL, branch by abstraction | [16](ch16-strangler-fig-migration.md) | Shadowing found 3 bugs before any customer saw them; canary limited an incident to 8 customers |
| Cross-cutting concerns outside the app's code | Sidecar, ambassador, adapter | [17](ch17-sidecar-ambassador-adapter.md) | An ambassador lifted success from 89.5% to 99.7% for ~0.3 ms per hop |
| Slow or failing dependencies | Timeout, retry, bulkhead, circuit breaker, fallback | [18](ch18-resilience-patterns.md) | An unrelated endpoint went from 2.4% to 100% availability with a timeout |
| Sharing data and reacting to facts across services | Event notification, state transfer, event sourcing | [19](ch19-event-driven-architecture.md) | Sync calls failed 100% of orders in an outage; state transfer 0% |
| A microservice system that feels worse than the monolith | Anti-pattern detection, fitness functions | [20](ch20-microservice-anti-patterns.md) | 16 findings in the 2023 design, 1 accepted in 2024 |

## The chapters

<div class="grid cards" markdown>

-   **[9. Hexagonal, Clean & Onion Architecture](ch09-hexagonal-clean-architecture.md)**

    ---

    Ports and adapters inside one service. *payment-service, ArchUnit rules.*

-   **[10. API Gateway & Backend for Frontend](ch10-api-gateway-bff.md)**

    ---

    The edge as code; one BFF per client experience. *Spring Cloud Gateway Server MVC.*

-   **[11. Service Discovery Patterns](ch11-service-discovery-patterns.md)**

    ---

    Client-side, server-side and platform discovery. *A staleness simulation.*

-   **[12. Database per Service & API Composition](ch12-database-per-service.md)**

    ---

    One owner per table; composition for display joins. *An ownership audit.*

-   **[13. CQRS & Event Sourcing](ch13-cqrs-event-sourcing.md)**

    ---

    Decide/evolve, projections, snapshots, upcasters. *An event-sourced rider wallet.*

-   **[14. Saga Pattern](ch14-saga-pattern.md)**

    ---

    Orchestration vs choreography; compensatable, pivot and retriable steps. *10,000 simulated orders.*

-   **[15. Transactional Outbox, Inbox & CDC](ch15-outbox-inbox-cdc.md)**

    ---

    No more dual writes; polling relay or Debezium; consumer deduplication. *PostgreSQL verified.*

-   **[16. Strangler Fig, ACL & Monolith Migration](ch16-strangler-fig-migration.md)**

    ---

    Shadow traffic, sticky canary cohorts, anti-corruption layers. *Pricing leaves the monolith.*

-   **[17. Sidecar, Ambassador & Adapter](ch17-sidecar-ambassador-adapter.md)**

    ---

    Helpers in the pod; native sidecars; from sidecars to meshes. *Measured over real HTTP.*

-   **[18. Resilience Patterns](ch18-resilience-patterns.md)**

    ---

    Timeout, retry, bulkhead, breaker, fallback, composed. *A brownout simulated six ways.*

-   **[19. Event-Driven Architecture](ch19-event-driven-architecture.md)**

    ---

    Notification vs state transfer vs event sourcing. *Spring Cloud Stream.*

-   **[20. Microservice Anti-Patterns](ch20-microservice-anti-patterns.md)**

    ---

    Distributed monolith, chatty calls, nano-services, shared code and data. *A detector and a fitness function.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Building services already** (🔵 L2) | 9 → 10 → 18 → 12 | [Part 3: Communication](../communication/index.md) |
| **Leading a team** (🟣 L3) | 12 → 14 → 15 → 18 → 19 | [Part 6: Resilience](../resilience/index.md) |
| **Designing or migrating a system** (🔴 L4) | 16 → 20 → 13 → 14 → 19 | [Migration roadmap](../governance-org/ch74-maturity-migration-roadmap.md) |
| **Preparing for interviews** | 14, 15, 18, 19, 20 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-009 · Chapter 9</span><span class="what">payment-service uses ports and adapters; providers are adapters.</span></li>
<li><span class="when">ADR-010 · Chapter 10</span><span class="what">One API gateway for routing and protection; one BFF per client experience.</span></li>
<li><span class="when">ADR-011 · Chapter 11</span><span class="what">Kubernetes Services are the discovery mechanism.</span></li>
<li><span class="when">ADR-012 · Chapter 12</span><span class="what">One owner per table; composition for display joins.</span></li>
<li><span class="when">ADR-013 · Chapter 13</span><span class="what">Rider wallets are event-sourced.</span></li>
<li><span class="when">ADR-014 · Chapter 14</span><span class="what">Place-order is an orchestrated saga with the kitchen as pivot.</span></li>
<li><span class="when">ADR-015 · Chapter 15</span><span class="what">Every service publishes through an outbox; every consumer uses an inbox.</span></li>
<li><span class="when">ADR-016 · Chapter 16</span><span class="what">Pricing leaves the monolith by strangler fig, shadowed and canaried.</span></li>
<li><span class="when">ADR-017 · Chapter 17</span><span class="what">Payment calls go through a platform-owned ambassador.</span></li>
<li><span class="when">ADR-018 · Chapter 18</span><span class="what">Every remote call is timed out, bulkheaded and observable.</span></li>
<li><span class="when">ADR-019 · Chapter 19</span><span class="what">Reference data is shared by event-carried state transfer.</span></li>
<li><span class="when">ADR-020 · Chapter 20</span><span class="what">Anti-patterns are detected from evidence and gated in the build.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch09-hexagonal-architecture,chapters/ch10-api-gateway-bff,chapters/ch11-service-discovery,chapters/ch12-database-per-service,chapters/ch13-cqrs-event-sourcing,chapters/ch14-saga,chapters/ch15-outbox-inbox-cdc,chapters/ch16-strangler-fig,chapters/ch17-sidecar-ambassador-adapter,chapters/ch18-resilience-patterns,chapters/ch19-event-driven-architecture,chapters/ch20-anti-patterns -am verify
    ```
    JDK 25 and Maven 3.9+. Chapter 15 also needs Docker for its Testcontainers tests (skipped without it).
