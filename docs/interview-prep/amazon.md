---
title: "Amazon"
description: "Preparing for Amazon-style rounds with this handbook: Leadership Principles stories built on the STAR structure, with one FoodFleet incident per principle as a model of how a story carries evidence; what scale-design rounds probe; and the 233 chapter questions tagged Amazon or AWS-heavy, each with its short answer and a link to the full one."
progress: done
---

# Amazon

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Amazon's loops are known for two things: behavioural questions anchored in its **Leadership Principles**, asked in every round, and **design rounds** that push on scale, failure and operational ownership. This page helps with both. Interview formats change; check the current process with your recruiter.

## Leadership Principles stories

Behavioural answers follow STAR: **S**ituation, **T**ask, **A**ction, **R**esult. What makes a story land is evidence: a number in the result, a decision you made rather than "we", and what you'd do differently.

Your stories must be your own. The table below shows how each principle maps to a kind of story, using FoodFleet's incidents as **models of structure**: each has a situation, a measurable failure, a decision and a measured result, which is the shape your own stories need.

| Principle | The kind of story | A FoodFleet model |
|---|---|---|
| Customer Obsession | You changed a design because of what customers experienced | Customers were charged for food never cooked; the saga moved capture after the kitchen's acceptance ([ch. 14](../architecture-patterns/ch14-saga-pattern.md#7-anti-patterns-pitfalls)) |
| Ownership | You fixed something outside your remit because it was yours to care about | A heap dump held the database password; Actuator was locked down fleet-wide ([ch. 52](../observability/ch52-health-checks-actuator.md#8-enterprise-case-study)) |
| Invent and Simplify | You removed complexity instead of adding it | Four new datastores were proposed; PostgreSQL and Redis covered all five workloads, measured ([ch. 29](../data/ch29-polyglot-persistence.md#4-design-considerations-trade-offs)) |
| Are Right, A Lot | Your judgment held against a plausible alternative, and you showed why | A closed-model load test said p99 15 ms; an open model showed 942 ms ([ch. 65](../testing/ch65-performance-chaos-arch-tests.md#7-anti-patterns-pitfalls)) |
| Learn and Be Curious | You found something by looking where nobody looked | Linting the team's own decision log found 11 misdated records ([ch. 73](../governance-org/ch73-adrs-fitness-functions.md#4-design-considerations-trade-offs)) |
| Hire and Develop the Best | You grew people or a team's capability | An enabling team that coaches, owns nothing, and moves on ([ch. 7](../foundations/ch07-conways-law-team-topologies.md#8-enterprise-case-study)) |
| Insist on the Highest Standards | You raised a bar others thought was fine | Three incidents passed 2,100 green tests; planted bugs showed which suites saw what ([ch. 61](../testing/ch61-test-strategy.md#7-anti-patterns-pitfalls)) |
| Think Big | You changed how many teams work, not one service | Golden paths with three-way-merge updates: a fleet from 4 to 40 of 48 checks ([ch. 72](../governance-org/ch72-platform-engineering.md#4-design-considerations-trade-offs)) |
| Bias for Action | You acted with a reversible decision instead of waiting | Canaries: a bad release stopped after 7–10 failed requests, not 950 ([ch. 58](../deployment/ch58-cicd-gitops.md#4-design-considerations-trade-offs)) |
| Frugality | You did more with less | Right-sizing took a fleet from 97 to 30 CPUs ([ch. 70](../performance-scalability/ch70-finops.md#4-design-considerations-trade-offs)) |
| Earn Trust | You were candid about a mistake or a risk | An endpoint removed after an email to its listed consumers broke the support console: two of its four remaining callers weren't in the catalog ([ch. 71](../governance-org/ch71-api-governance.md#7-anti-patterns-pitfalls)) |
| Dive Deep | You found the real cause under a misleading number | A dashboard's averaged p99 said 137 ms; the true p99 was 322 ms ([ch. 48](../observability/ch48-metrics-micrometer.md#7-anti-patterns-pitfalls)) |
| Have Backbone; Disagree and Commit | You argued against a popular plan, then committed to the decision | A rewrite chased a moving target; the team switched to a strangler fig ([ch. 16](../architecture-patterns/ch16-strangler-fig-migration.md#7-anti-patterns-pitfalls)) |
| Deliver Results | You shipped under constraints, with a measured outcome | Pricing extracted from the monolith with shadowing and cohort canaries ([ch. 16](../architecture-patterns/ch16-strangler-fig-migration.md#8-enterprise-case-study)) |
| Strive to be Earth's Best Employer | You made the work sustainable for people | Forty alerts per service, mostly noise, became pages only for real SLO threats ([ch. 51](../observability/ch51-slo-alerting.md#8-enterprise-case-study)) |
| Success and Scale Bring Broad Responsibility | You considered effects beyond the immediate goal | Erasure that reaches backups and topics, with crypto-shredding ([ch. 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md#4-design-considerations-trade-offs)) |

**Practise like this:** for each principle, write your own story in four lines (S, T, A, R), with at least one number in the result. Prepare two per principle, since follow-ups often ask for "another example". Expect "what would you do differently?" every time.

## Scale-design rounds

Design rounds tend to push on three things beyond the diagram:

- **Failure as the normal case.** Every dependency fails; what does the user see? Have a degradation mode for each call ([ADR-002](../foundations/ch02-characteristics-principles.md)), and know the arithmetic: four services in a row at 99.9% give 99.6% ([resilience](../food-delivery-platform/resilience.md)).
- **Operational ownership.** You build it, you run it: SLOs per journey, burn-rate alerts, runbooks ([observability](../food-delivery-platform/observability.md), [runbooks](../food-delivery-platform/runbooks.md)).
- **Cost at scale.** What it costs per unit of business, and what dominates ([scaling & cost](../food-delivery-platform/scaling-and-cost.md)).

The [system design rounds](system-design-rounds.md) page works through common prompts. The AWS-specific chapters: [59 (cloud-native platforms)](../deployment/ch59-cloud-native-platforms.md) and [76 (serverless)](../governance-org/ch76-serverless-microservices.md).

## The questions

The 233 chapter questions tagged Amazon or AWS-heavy shops. Tags are the chapters' judgment of where a question fits, not reports of real interviews.

--8<-- "tools/generated/interview/amazon.md"
