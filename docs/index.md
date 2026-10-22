---
title: Microservices Enterprise Handbook
description: Beginner to architect microservices handbook with Java 25 and Spring Boot 4
template: home.html
hide:
  - navigation
  - toc
---

# Microservices Enterprise Handbook

## Choose your path

Every page and every example is tagged with the experience band it targets. Start at your band, then go one level up.

<div class="grid" markdown>

<div class="card mh-path l1" markdown>
### :material-sprout: L1 · Foundation <small>0–3 yrs</small>
Understand what a microservice is and build, test and run one with Spring Boot 4.

[Chapter 1](foundations/ch01-architecture-styles.md) → [Your first microservice](getting-started/first-microservice.md) → [Lab 01](code-lab/lab-01-hello-microservice.md)
</div>

<div class="card mh-path l2" markdown>
### :material-hammer-wrench: L2 · Practitioner <small>3–7 yrs</small>
Ship production services: gateways, discovery, config, Kafka, resilience and security.

[Communication](communication/index.md) → [Resilience4j](resilience/ch43-resilience4j-deep-dive.md) → [Labs 04–08](code-lab/index.md)
</div>

<div class="card mh-path l3" markdown>
### :material-sitemap: L3 · Senior / Lead <small>7–11 yrs</small>
Design multi-service systems with sagas, outbox, CQRS, observability and SLOs.

[Patterns](architecture-patterns/index.md) → [Saga](architecture-patterns/ch14-saga-pattern.md) → [FoodFleet](food-delivery-platform/index.md)
</div>

<div class="card mh-path l4" markdown>
### :material-chess-king: L4 · Architect <small>11–15+ yrs</small>
Own org-wide architecture: migration strategy, platform engineering, cost and governance.

[Migration roadmap](governance-org/ch74-maturity-migration-roadmap.md) → [Team Topologies](foundations/ch07-conways-law-team-topologies.md) → [Architect rounds](interview-prep/architect-leadership-rounds.md)
</div>

</div>

## What's inside

<div class="grid cards" markdown>

-   :material-book-open-page-variant:{ .lg .middle } **77 concept chapters**

    ---

    Foundations, patterns, communication, data, security, resilience, observability,
    deployment, testing, performance and governance. Every chapter follows the same 9-part structure.

    [:octicons-arrow-right-24: Start with Foundations](foundations/index.md)

-   :material-toolbox:{ .lg .middle } **Components & Tools**

    ---

    API Gateway, Kafka, Resilience4j, Keycloak, Redis, OpenTelemetry, Kubernetes, Istio,
    Argo CD and 60+ more, each covering what it is, why it's needed, configuration and production use.

    [:octicons-arrow-right-24: Browse components](components-tools/index.md)

-   :material-moped:{ .lg .middle } **FoodFleet capstone**

    ---

    A real-time food delivery platform with 17 services, Kafka sagas, live rider tracking,
    Helm charts, GitOps and SLO dashboards.

    [:octicons-arrow-right-24: Explore FoodFleet](food-delivery-platform/index.md)

-   :material-account-tie-voice:{ .lg .middle } **Interview Prep**

    ---

    Interview questions grouped by band, by company style (Amazon, Flipkart, Goldman Sachs,
    JP Morgan, SAP, service companies) and by round type, with model answers.

    [:octicons-arrow-right-24: Prepare](interview-prep/index.md)

</div>

## How every chapter is built

| # | Part | What you get |
|---|---|---|
| 1 | Introduction & Context | The problem, history, and a plain-language analogy |
| 2 | Core Concepts | Vocabulary and mental models, with diagrams |
| 3 | How It Works Internally | Mechanics, protocols, sequence diagrams, failure modes |
| 4 | Design Trade-offs | When to use it, when not to, and comparison tables |
| 5 | Hands-on Implementation | 🟢 → 🔵 → 🟣 → 🔴 runnable Java 25 / Spring Boot 4 code with tests |
| 6 | Production Readiness | Tuning, security, observability, capacity, checklists |
| 7 | Anti-patterns & Pitfalls | Real (anonymized) incidents, root causes and fixes |
| 8 | Enterprise Case Study | FoodFleet plus a fintech or e-commerce analogue, with an ADR |
| 9 | Interview Q&A + Summary | Questions across bands, model answers and key takeaways |

!!! tip "Track progress"
    The [Handbook Roadmap](appendix/roadmap.md) shows which pages are complete. It is regenerated from the master plan on every build.
