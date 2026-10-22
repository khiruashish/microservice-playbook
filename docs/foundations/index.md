---
title: "Part 1 — Foundations"
description: "Part overview and reading order: architecture styles, principles, distributed-systems fundamentals, twelve-factor apps, DDD, decomposition, Conway's Law and Spring Boot 4."
progress: done
---

# Part 1 — Foundations

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Part 1 answers the questions that come **before** patterns and tools: should this be a microservice at all, where are the boundaries, what does the network change, who owns what, and what does a well-built Spring Boot 4 service look like. Every later part assumes it.

Each chapter follows the same nine sections (context, concepts, internals, trade-offs, hands-on code, production checklist, pitfalls, case study, interview Q&A) and ships runnable code under `code/chapters/`.

## The chapters

<div class="grid cards" markdown>

-   **[1. Monolith vs SOA vs Microservices vs Modular Monolith](ch01-architecture-styles.md)**

    ---

    The same kitchen-ticket feature in four styles; why a modular monolith is the default start. *Spring Modulith, event publication registry.*

-   **[2. Characteristics & Principles](ch02-characteristics-principles.md)**

    ---

    Independent deployability, tolerant readers, API evolution, retry arithmetic. *menu-service with API versioning.*

-   **[3. Distributed Systems Fundamentals](ch03-distributed-systems-fundamentals.md)**

    ---

    Fallacies, CAP and PACELC, clocks, quorums, idempotency. *Simulations you can run.*

-   **[4. Twelve-Factor & Fifteen-Factor Apps](ch04-twelve-factor.md)**

    ---

    Each factor as a concrete Spring Boot 4 mechanism. *zone-service, Dockerfile with AOT cache, Kubernetes manifests.*

-   **[5. Domain-Driven Design](ch05-domain-driven-design.md)**

    ---

    Subdomains, bounded contexts, context maps, aggregates, an anti-corruption layer. *Ordering context.*

-   **[6. Service Decomposition](ch06-service-decomposition.md)**

    ---

    Event Storming, granularity forces, and a tool that scores candidate splits and mines change coupling from git.

-   **[7. Conway's Law & Team Topologies](ch07-conways-law-team-topologies.md)**

    ---

    Team types, interaction modes, cognitive load; checking an org design; CODEOWNERS and Backstage from one model.

-   **[8. Spring Boot 4 Essentials](ch08-spring-boot-4-essentials.md)**

    ---

    Modular starters, Jackson 3, JSpecify, virtual threads, HTTP service clients, API versioning. *eta-service.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **New to microservices** (🟢 L1) | 1 → 2 → 4 → 8 | [Your first microservice](../getting-started/first-microservice.md), [Lab 01](../code-lab/lab-01-hello-microservice.md) |
| **Building services already** (🔵 L2) | 2 → 3 → 5 → 8 | [Part 2: Patterns](../architecture-patterns/index.md) |
| **Leading a team** (🟣 L3) | 3 → 5 → 6 → 7 | [Hexagonal architecture](../architecture-patterns/ch09-hexagonal-clean-architecture.md), [Saga](../architecture-patterns/ch14-saga-pattern.md) |
| **Designing the system** (🔴 L4) | 1 → 6 → 7, skim the rest | [Migration roadmap](../governance-org/ch74-maturity-migration-roadmap.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">Chapter 1</span><span class="what">FoodFleet starts as a <strong>modular monolith</strong> and extracts kitchen-service when a real reason appears.</span></li>
<li><span class="when">Chapters 2–4</span><span class="what">menu-service and zone-service show principles, failure arithmetic and twelve-factor operation.</span></li>
<li><span class="when">Chapter 5</span><span class="what">The <strong>context map</strong>: Ordering, Catalog, Kitchen, Delivery, Payment, Promotions.</span></li>
<li><span class="when">Chapter 6</span><span class="what">Four candidate splits are scored; <strong>nine capability services</strong> win (ADR-006).</span></li>
<li><span class="when">Chapter 7</span><span class="what">Teams are reorganised around those capabilities (ADR-007).</span></li>
<li><span class="when">Chapter 8</span><span class="what">Every service moves to the <strong>Spring Boot 4.1 golden path</strong> (ADR-008).</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch01-architecture-styles,chapters/ch02-principles,chapters/ch03-distributed-fundamentals,chapters/ch04-twelve-factor,chapters/ch05-domain-driven-design,chapters/ch06-service-decomposition,chapters/ch07-team-topologies,chapters/ch08-spring-boot-4 -am verify
    ```
    JDK 25 and Maven 3.9+. Chapter 4 also needs Docker for its Testcontainers tests.
