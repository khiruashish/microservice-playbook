---
title: "Architect & Leadership Rounds"
description: "Architect and principal rounds ask about decisions that span teams and years: migration strategy, build versus buy, organisation design, governance and cost. A five-part structure for answering them, and a worked answer for each kind of question with the measurement behind it: Broadleaf's history freed 32% of changes for 340 remote dependencies when extracted in the right order, and Team Topologies took FoodFleet from 3.38 teams per feature to 1.75."
progress: done
---

# Architect & Leadership Rounds

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

Architect rounds aren't harder system design rounds. They ask about decisions whose consequences outlive the people who made them: how to migrate, what to build and what to buy, how teams should be shaped, how standards are kept, what it all costs. There's rarely a right answer; there's a well-reasoned one, and interviewers listen for the reasoning.

## A structure for any architect question

1. **The forces.** What makes this hard here: team size, rate of change, risk, regulation, money, time. Ask about them before answering.
2. **The options**, two or three, each with what it costs and what it buys.
3. **The choice**, and the evidence you'd want before committing: a measurement, a spike, a pilot.
4. **How you'd know you were wrong**: the signal, and what you'd do then. Decisions are reversible or not; say which.
5. **The organisation**: who owns it, how it's reviewed, how it's kept true. An architecture nobody maintains decays ([Part 11](../governance-org/index.md)).

This is an ADR said out loud: context, decision, consequences. Every chapter of this handbook ends with one, written in that shape.

## Migration strategy

**"How would you move this monolith to microservices?"**

- **First, whether to.** A modular monolith with enforced boundaries gets most of the benefit for a fraction of the cost; FoodFleet started there ([ADR-001](../foundations/ch01-architecture-styles.md)). The case for services is independent teams and different load shapes, not size.
- **Measure before you plan.** [Chapter 74](../governance-org/ch74-maturity-migration-roadmap.md) analysed a real monolith, Broadleaf Commerce, and ten years of its history: 9 of its contexts sat in one dependency cycle, and its layers carried 157 imports in the wrong direction. Nothing in that cycle can be extracted until it's broken.
- **Order by value per cost.** Extracting in order of independent changes freed per remote dependency added freed 32% of changes at the cost of 340 dependencies crossing the network; extracting the most-changed modules first freed about the same, 33%, with 1,083. Re-plan after every step.
- **Strangle, don't rewrite.** A rewrite chased a moving target for eight months; shadow traffic found three bugs and an undocumented behaviour in two weeks, before any customer saw them ([ch. 16](../architecture-patterns/ch16-strangler-fig-migration.md)).
- **Stop when the next extraction buys little.** The goal is a system teams can change independently, not a service count.

## Build versus buy

**"Would you build this or buy it?"**

Separate three questions: is it a **differentiator** (build), is it **commodity** (buy or use open source), and **who runs it** (managed, or yours to operate).

| Capability | FoodFleet's choice | Why | Chapter |
|---|---|---|---|
| Saga orchestration | A hand-written orchestrator for checkout; a workflow engine for long flows with humans | One latency-sensitive flow doesn't justify an engine; refunds with review and onboarding do | [14](../architecture-patterns/ch14-saga-pattern.md#build-or-use-a-workflow-engine) |
| API gateway | Spring Cloud Gateway, run by the platform team | Same language and tooling as the services; custom filters in Java | [10](../architecture-patterns/ch10-api-gateway-bff.md) |
| Kafka and PostgreSQL | Managed (MSK, Aurora) | Commodity to operate; the platform team is eight people | [59](../deployment/ch59-cloud-native-platforms.md) |
| Dispatch optimisation | Built, by a specialist team | It's the business's edge | [7](../foundations/ch07-conways-law-team-topologies.md) |
| Model access | A thin gateway in front of providers | Deadlines, limits and cost accounting are FoodFleet's; the models aren't | [77](../governance-org/ch77-ai-native-microservices.md) |
| Search | PostgreSQL now; a search engine when relevance tuning is needed | A second store costs operations and sync; adopt it when the need is measured | [29](../data/ch29-polyglot-persistence.md) |

The cost people forget: **operating** what you build or self-host, for years. Count it in people, not licences.

## Organisation design

**"How would you organise 50 engineers around this system?"**

- **Teams follow streams of change**, so most features need one team. Four team types: stream-aligned, platform, complicated-subsystem, enabling ([ch. 7](../foundations/ch07-conways-law-team-topologies.md)).
- **Cognitive load is the constraint.** A team owns what it can hold in its head; one complex domain is a full load.
- **The measured effect** at FoodFleet, from layer teams to stream-aligned ones:

| | Layer teams | Team Topologies |
|---|---:|---:|
| Teams per feature | 3.38 | 1.75 |
| Handoffs across 8 features | 19 | 6 |
| Teams over their cognitive-load budget | 4 | 1 |
| Socio-technical congruence | 62% | 92% |

- **Interactions are designed too**: collaboration time-boxed, X-as-a-service by default, facilitation by enabling teams that leave.
- **The inverse Conway manoeuvre**: shape teams for the architecture you want, because the architecture will follow the teams.

## Governance that keeps working

**"How do you keep 70 services consistent?"**

- **Standards as code, not documents**: lint rules, conformance checks, fitness functions. Spectral and house rules caught 26 of 33 planted API problems; 7 still needed a reviewer ([ch. 71](../governance-org/ch71-api-governance.md)).
- **Golden paths that stay current**: templates whose releases open update pull requests. An old template's services passed 8% of current checks; three-way-merge updates took them from 4 to 40 of 48 ([ch. 72](../governance-org/ch72-platform-engineering.md)).
- **Decisions recorded and checked**: ADRs never edited, fitness functions traced to them. Code checked only 7 of 72 decisions before chapter 73 ([ch. 73](../governance-org/ch73-adrs-fitness-functions.md)).
- **Make the right way the easy way.** Governance that relies on people remembering decays; the recurring lesson of [Part 11](../governance-org/index.md).

## Cost

**"The cloud bill doubled. What do you do?"**

- **Allocate before you cut**: costs per team by label, charged on the larger of request and usage. By usage alone, 93% of chapter 70's bill was idle capacity nobody owned ([ch. 70](../performance-scalability/ch70-finops.md)).
- **Right-size from measurements**: services used 3–10% of requested CPU; VPA's recommendations took the fleet from 97 to 30 CPUs.
- **Don't cut what protects latency**: a CPU limit at the recommendation raised p99 from 5 ms to 78–204 ms.
- **Spot above an on-demand base**: all-spot served 0–1% for a minute when reclaimed; 3 on-demand + 3 spot, 55–61%.
- **Report cost per unit of business** (per 1,000 orders) beside the SLOs, so growth and efficiency aren't confused.

## The questions

The [L4 questions](band-l4.md) page has all 205 questions the chapters mark L4, with short answers.
