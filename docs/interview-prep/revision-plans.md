---
title: "2-Week & 6-Week Revision Plans"
description: "Structured preparation for microservice interviews: a two-week plan for when the interview is booked, and a six-week plan for building depth, each in daily blocks of about two hours, with variants for L2, L3 and L4 and the round types to practise each week."
progress: done
---

# 2-Week & 6-Week Revision Plans

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Both plans assume about two hours a day. Each day has a **read** block (chapters' sections 2–4: concepts, mechanism, measurements) and an **answer** block (section 9's questions, out loud, before opening the answers). L1 candidates should follow the [L1 learning path](../getting-started/index.md#learning-paths) instead; these plans assume you've built services.

## The two-week plan

For when the interview is booked. It covers what comes up most, not everything.

| Day | Read | Answer | Practise |
|---|---|---|---|
| 1 | [1](../foundations/ch01-architecture-styles.md), [2](../foundations/ch02-characteristics-principles.md), [3](../foundations/ch03-distributed-systems-fundamentals.md) | Their questions | Explain "when not to use microservices" in two minutes |
| 2 | [6](../foundations/ch06-service-decomposition.md), [12](../architecture-patterns/ch12-database-per-service.md) | Their questions | Decompose a familiar app on paper |
| 3 | [14](../architecture-patterns/ch14-saga-pattern.md), [15](../architecture-patterns/ch15-outbox-inbox-cdc.md) | Their questions | Code the [saga coordinator](coding-rounds.md#4-saga-coordinator) without looking |
| 4 | [18](../architecture-patterns/ch18-resilience-patterns.md), [42](../resilience/ch42-failure-modes.md) | Their questions | Code the [circuit breaker](coding-rounds.md#1-circuit-breaker) |
| 5 | [21](../communication/ch21-rest-api-design.md), [22](../communication/ch22-http-clients.md), [23](../communication/ch23-grpc.md) | Their questions | Design an API for a cart, with errors and pagination |
| 6 | [25](../communication/ch25-asynchronous-messaging.md), [28](../communication/ch28-idempotency-correlation.md) | Their questions | Code the [idempotent consumer](coding-rounds.md#3-idempotent-consumer) |
| 7 | Rest, or catch up | [Cheat sheet](cheat-sheet.md) rules 1–10 from memory | |
| 8 | [29](../data/ch29-polyglot-persistence.md), [31](../data/ch31-data-consistency.md), [32](../data/ch32-caching-strategies.md) | Their questions | Design the [rate limiter](system-design-rounds.md#5-a-distributed-rate-limiter) |
| 9 | [36](../security/ch36-oauth2-oidc-jwt.md), [38](../security/ch38-service-to-service-security.md) | Their questions | Explain a token's journey from login to the third service |
| 10 | [47](../observability/ch47-observability-fundamentals.md), [51](../observability/ch51-slo-alerting.md), [52](../observability/ch52-health-checks-actuator.md) | Their questions | Ten [troubleshooting drills](troubleshooting-rounds.md#practice-drills) |
| 11 | [54](../deployment/ch54-kubernetes-for-spring.md), [58](../deployment/ch58-cicd-gitops.md) | Their questions | Ten more drills |
| 12 | [61](../testing/ch61-test-strategy.md), [66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) | Their questions | The [food-delivery design](../food-delivery-platform/system-design-interview.md), timed at 45 minutes |
| 13 | Your company page | Its questions you haven't seen | One more system design, timed |
| 14 | The [cheat sheet](cheat-sheet.md), once | Nothing new | Sleep |

**L4 variant:** replace days 5 and 9 with [7](../foundations/ch07-conways-law-team-topologies.md) + [16](../architecture-patterns/ch16-strangler-fig-migration.md) and [74](../governance-org/ch74-maturity-migration-roadmap.md) + [70](../performance-scalability/ch70-finops.md), and practise the [architect rounds](architect-leadership-rounds.md) structure on day 13.

## The six-week plan

For building depth. Each week ends with a timed design round and a set of drills.

| Week | Read | Rounds to practise |
|---|---|---|
| 1 · Foundations | Part 1 (chapters 1–8) | Explain your current system's architecture style and its trade-offs; L1–L2 questions |
| 2 · Patterns | Part 2 (9–20) | The four [coding rounds](coding-rounds.md); patterns' questions |
| 3 · Communication and data | Parts 3 and 4 (21–34) | [Payments](system-design-rounds.md#3-payments) and [notifications](system-design-rounds.md#4-notifications) designs |
| 4 · Security and resilience | Parts 5 and 6 (35–46) | 30 [troubleshooting drills](troubleshooting-rounds.md#practice-drills) from Parts 5–6 |
| 5 · Operations | Parts 7, 8 and 9 (47–65) | The [food-delivery design](../food-delivery-platform/system-design-interview.md); the [runbooks](../food-delivery-platform/runbooks.md) as scenario questions |
| 6 · Scale and organisation | Parts 10 and 11 (66–77); your company page | [Ride matching](system-design-rounds.md#2-ride-matching-uber-ola-rapido); the [architect rounds](architect-leadership-rounds.md); the [cheat sheet](cheat-sheet.md) on the last day |

**By band:**

- **L2:** in weeks 4–6, read sections 1–5 and 9 of each chapter and skip section 8's analogues. Spend the saved time building: the [Code Lab](../code-lab/index.md).
- **L3:** all sections. For each chapter, write down its ADR and the one number that justified it.
- **L4:** all sections, plus each part's overview and decision guide. For each part, prepare one story of a decision you made in that area, in the [architect structure](architect-leadership-rounds.md#a-structure-for-any-architect-question).

## How to practise answering

- **Out loud, timed.** Two minutes for a concept question, five for a trade-off, forty-five for a design.
- **One number per answer.** If you can't remember the chapter's measurement, say what you'd measure.
- **Record yourself once a week** and listen for filler, hedging and answers that never reach a decision.
- **Mock rounds** with a colleague: swap roles, and use the chapters' questions as the interviewer's script.
