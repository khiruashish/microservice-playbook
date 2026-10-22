---
title: "How to Use This Handbook"
description: "What the handbook is, how every chapter is built, how its numbers were measured and how to rerun them, and a learning path for each of the four bands: 77 chapters in 11 parts, 77 ADRs for one fictional company, and the code to check every claim."
progress: done
---

# How to Use This Handbook

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

This handbook teaches microservices on Java 25 and Spring Boot 4 by building and measuring one system: **FoodFleet**, a fictional food-delivery platform.

- **One company, followed throughout.** Every chapter adds to FoodFleet's architecture and ends with an **architecture decision record (ADR)**, numbered like the chapter.
- **The ADRs add up to a design.** Together, 77 of them, they are FoodFleet's whole design.
- **Claims are measured.** Where a chapter says something is faster, safer or cheaper, it shows a measurement and the code that produced it, so you can rerun it and disagree.

## What's in it

| Part | Chapters | What it covers |
|---|---|---|
| [1. Foundations](../foundations/index.md) | 1–8 | Architecture styles, principles, distributed systems, twelve-factor, DDD, decomposition, teams, Spring Boot 4 |
| [2. Architecture & Patterns](../architecture-patterns/index.md) | 9–20 | Hexagonal, gateways, discovery, data ownership, CQRS, sagas, outbox, strangler fig, resilience, events, anti-patterns |
| [3. Communication](../communication/index.md) | 21–28 | REST, HTTP clients, gRPC, GraphQL, messaging, WebSockets, contracts, idempotency |
| [4. Data](../data/index.md) | 29–34 | Polyglot persistence, distributed transactions, consistency, caching, scaling, analytics |
| [5. Security](../security/index.md) | 35–41 | Fundamentals, OAuth/OIDC, Spring Security 7, service-to-service, secrets, API security, supply chain |
| [6. Resilience](../resilience/index.md) | 42–46 | Failure modes, Resilience4j, timeouts and retries, chaos, disaster recovery |
| [7. Observability](../observability/index.md) | 47–52 | Signals, metrics, tracing, logging, SLOs, health checks |
| [8. Deployment & Platform](../deployment/index.md) | 53–60 | Containers, Kubernetes, Helm, autoscaling, mesh, CI/CD, cloud platforms, startup |
| [9. Testing](../testing/index.md) | 61–65 | Strategy, unit and integration, contracts, component and E2E, performance and chaos |
| [10. Performance & Scalability](../performance-scalability/index.md) | 66–70 | Latency, JVM tuning, horizontal scaling, Kafka throughput, FinOps |
| [11. Governance & Organization](../governance-org/index.md) | 71–77 | API governance, platform engineering, ADRs, migration, tenancy, serverless, AI |

Beside the parts are reference sections: [Components & Tools](../components-tools/index.md), the [Code Lab](../code-lab/index.md), the [FoodFleet platform](../food-delivery-platform/index.md), [Interview prep](../interview-prep/index.md) and the [Appendix](../appendix/index.md). The [roadmap](../appendix/roadmap.md) shows which pages are finished. The [glossary](glossary.md) collects 463 terms from the chapters.

## How a chapter is built

Every chapter has the same nine sections, so you can skip to what you need:

| Section | What you'll find |
|---|---|
| 1 · Introduction & Context | FoodFleet's problem, and a timeline of where the idea came from |
| 2 · Core Concepts & Terminology | A diagram and definitions (collected in the [glossary](glossary.md)) |
| 3 · How It Works Internally | The mechanism, with excerpts from the chapter's code |
| 4 · Design Considerations & Trade-offs | **The measurements**: setup, results, what they show, and a "Choosing" table |
| 5 · Hands-on Implementation | The code's layout, what to do at each level, how to run it, exercises |
| 6 · Production Readiness | A checklist |
| 7 · Anti-patterns & Pitfalls | Three incidents and a table of anti-patterns |
| 8 · Enterprise Case Study | FoodFleet's ADR, and how fintech and e-commerce apply the same idea |
| 9 · Interview Q&A + Summary | Twelve questions with short answers, takeaways, further reading |

## How to read the numbers

Measurements follow a few conventions:

- **Two runs, shown as ranges.** Each experiment ran twice. Where the runs differed, both values appear: "p99 25–38 ms". Where they didn't, one value appears, and the chapter says the runs were identical.
- **The setup is stated.** Section 4 opens with what ran where: JDK, versions, machine (usually a shared 2-vCPU VM), data sizes.
- **Real software where possible.** PostgreSQL, Kafka clients, a k3s cluster, Docker, and the actual libraries. Where something couldn't be run, such as a hosted LLM API or a cloud bill, the chapter says what stands in for it and why.
- **Claims about libraries are checked against their source.** Each chapter's `check_apis.sh` greps the projects' own source or documentation, at a pinned version, for every behaviour the chapter relies on: 41 chapters, about 720 checks.

Absolute numbers depend on hardware; the comparisons, and the reasons behind them, are the point.

## Badges and levels

| Badge | Band | Who it's for |
|---|---|---|
| <span class="badge badge-l1">L1 · Foundation</span> | 🟢 L1 | New to microservices; comfortable with Java |
| <span class="badge badge-l2">L2 · Practitioner</span> | 🔵 L2 | Builds and runs services day to day |
| <span class="badge badge-l3">L3 · Senior/Lead</span> | 🟣 L3 | Leads a team; decides patterns and trade-offs |
| <span class="badge badge-l4">L4 · Architect</span> | 🔴 L4 | Designs systems and platforms across teams |

Badges appear on each chapter, on each hands-on level and on each interview question. Chapters carry several badges: most are useful at L3, and the L1 and L4 badges mark where a chapter is a good entry point or matters most for system-wide decisions.

## Learning paths

**🟢 L1: from Java developer to your first services** (about 3 weeks)

[Your first microservice](first-microservice.md) → [1](../foundations/ch01-architecture-styles.md) → [2](../foundations/ch02-characteristics-principles.md) → [4](../foundations/ch04-twelve-factor.md) → [8](../foundations/ch08-spring-boot-4-essentials.md) → [21](../communication/ch21-rest-api-design.md) → [22](../communication/ch22-http-clients.md) → [47](../observability/ch47-observability-fundamentals.md) → [50](../observability/ch50-structured-logging.md) → [52](../observability/ch52-health-checks-actuator.md) → [53](../deployment/ch53-containerization.md) → [61](../testing/ch61-test-strategy.md)

**🔵 L2: building services that cooperate** (about 5 weeks)

[3](../foundations/ch03-distributed-systems-fundamentals.md) → [5](../foundations/ch05-domain-driven-design.md) → [9](../architecture-patterns/ch09-hexagonal-clean-architecture.md) → [12](../architecture-patterns/ch12-database-per-service.md) → [23](../communication/ch23-grpc.md) → [25](../communication/ch25-asynchronous-messaging.md) → [29](../data/ch29-polyglot-persistence.md) → [32](../data/ch32-caching-strategies.md) → [36](../security/ch36-oauth2-oidc-jwt.md) → [37](../security/ch37-spring-security-7.md) → [43](../resilience/ch43-resilience4j-deep-dive.md) → [48](../observability/ch48-metrics-micrometer.md) → [54](../deployment/ch54-kubernetes-for-spring.md) → [62](../testing/ch62-unit-slice-integration-tests.md) → [63](../testing/ch63-contract-testing.md)

**🟣 L3: leading a team's architecture** (about 6 weeks)

[6](../foundations/ch06-service-decomposition.md) → [13](../architecture-patterns/ch13-cqrs-event-sourcing.md) → [14](../architecture-patterns/ch14-saga-pattern.md) → [15](../architecture-patterns/ch15-outbox-inbox-cdc.md) → [18](../architecture-patterns/ch18-resilience-patterns.md) → [28](../communication/ch28-idempotency-correlation.md) → [31](../data/ch31-data-consistency.md) → [38](../security/ch38-service-to-service-security.md) → [44](../resilience/ch44-timeouts-retries-backpressure.md) → [49](../observability/ch49-distributed-tracing-otel.md) → [51](../observability/ch51-slo-alerting.md) → [57](../deployment/ch57-service-mesh.md) → [65](../testing/ch65-performance-chaos-arch-tests.md) → [66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) → [67](../performance-scalability/ch67-jvm-tuning-containers.md) → [71](../governance-org/ch71-api-governance.md) → [73](../governance-org/ch73-adrs-fitness-functions.md) → [75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)

**🔴 L4: designing systems and platforms** (about 5 weeks)

[1](../foundations/ch01-architecture-styles.md) → [6](../foundations/ch06-service-decomposition.md) → [7](../foundations/ch07-conways-law-team-topologies.md) → [16](../architecture-patterns/ch16-strangler-fig-migration.md) → [20](../architecture-patterns/ch20-microservice-anti-patterns.md) → [30](../data/ch30-distributed-transactions.md) → [41](../security/ch41-supply-chain-compliance.md) → [46](../resilience/ch46-disaster-recovery.md) → [59](../deployment/ch59-cloud-native-platforms.md) → [68](../performance-scalability/ch68-horizontal-scaling.md) → [70](../performance-scalability/ch70-finops.md) → [72](../governance-org/ch72-platform-engineering.md) → [74](../governance-org/ch74-maturity-migration-roadmap.md) → [76](../governance-org/ch76-serverless-microservices.md) → [77](../governance-org/ch77-ai-native-microservices.md)

**Interviewing soon?** Each part's overview has a decision guide ("if your problem is…, reach for…") with the measured reason. Read the overviews for the parts your role needs, then the chapters' sections 4 and 9.

## The code

Everything is in `code/`, one Maven module per chapter under `code/chapters/`:

```text
code/chapters/ch75-multi-tenancy-multi-region-compliance/
├── src/main/java/…    the chapter's code (what section 3 excerpts)
├── src/test/java/…    its unit tests
├── pom.xml
├── README.md          what's where, and the findings in brief
└── verify/
    ├── run.sh         the experiments behind section 4, then the tests and the source checks
    ├── build-libs.sh  fetches libraries and pinned source checkouts
    └── check_apis.sh  every library behaviour the chapter relies on, checked in that library's source
```

- **Every chapter** is a Maven module with tests (`mvn verify`).
- **Chapters 23, 27 and 28–77** also have a `verify/` folder whose `run.sh` reproduces the chapter's measurements.
- [Environment setup](environment-setup.md) lists what each chapter needs. `tools/check-env.sh` tells you which chapters your machine can run.

## Conventions

- **Admonitions.** A *decision* box is an ADR; a *pitfall* box is an incident, with context, symptoms, root cause and fix; a *question* box holds exercises.
- **ADR dates.** ADRs are dated, and from chapter 71 on they're a week apart. [Chapter 73](../governance-org/ch73-adrs-fitness-functions.md) lints this log and reports what it found.
- **Money in integers.** FoodFleet's prices are whole paise and its examples use India's context: UPI, GST, DPDP.
- **Current technology only.** Superseded tools (Hystrix, Ribbon, Zuul, Sleuth, ZooKeeper-mode Kafka) appear only as history, next to what replaced them.
