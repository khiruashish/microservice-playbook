---
title: "20. Microservice Anti-Patterns"
description: "The distributed monolith, chatty services, nano-services, shared databases, shared-library hell, cycles and deep call chains: how to recognise each from evidence (deploy history, call graphs, table access, dependency versions), what each costs, and how to keep them out with a fitness function. FoodFleet's 2023 entity-service architecture analysed finding by finding."
tags:
  - architecture-patterns
  - anti-patterns
  - distributed-monolith
  - fitness-functions
progress: done
---

# 20. Microservice Anti-Patterns

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span><span class="badge badge-meta">Anti-pattern detector · fitness function · Monte Carlo</span></div>

<div class="grid two" markdown>

!!! info "Prerequisites"
    - [Chapter 6](../foundations/ch06-service-decomposition.md): decompositions A–D and change coupling
    - [Chapter 12](ch12-database-per-service.md), [chapter 18](ch18-resilience-patterns.md), [chapter 19](ch19-event-driven-architecture.md): the fixes this chapter keeps pointing to

!!! abstract "What you'll learn"
    - The anti-patterns that make microservices **worse than the monolith they replaced**, and the evidence that reveals each
    - A detector that reads an **architecture model** (calls, tables, libraries, deploy history) and reports findings with evidence
    - Measured: FoodFleet's 2023 entity services had **16 findings (7 high)**; the 2024 capability design has **1, accepted**
    - Measured: an N+1 call loop over 12 items fails **11×** more pages than one batch call, with **3.5×** the p99
    - Keeping anti-patterns out with a **fitness function and a shrinking baseline**

</div>

---

## 1 · Introduction & Context

### "We have microservices. Why is everything slower?"

By the end of 2023 FoodFleet had split its monolith into 14 services, one per entity: cart, order, order-line, customer-profile, address-book, menu, menu-item-pricing and so on (decomposition A in [chapter 6](../foundations/ch06-service-decomposition.md)). Six months later:

- releases still went out on a Thursday "train", six services at a time, coordinated in a spreadsheet;
- placing an order made nine synchronous calls; browsing a menu called the pricing service ten times;
- a change to the `Order` class in the shared `foodfleet-domain` jar meant rebuilding ten services, six of which were on different versions of it;
- coupon eligibility called customer-profile, which called order-service, which called coupon-service.

Every one of these is a known anti-pattern. Each was a reasonable local decision; together they produced a system with the costs of distribution (network, partial failure, operations) and none of the benefits (independent deployment, team autonomy, isolated failure). That combination has a name: the **distributed monolith**.

### Anti-patterns are found from evidence

Opinions about "too small" or "too coupled" don't settle arguments in an architecture review. Evidence does, and most of it already exists:

| Evidence | Where it comes from | Reveals |
|---|---|---|
| Deploy history | CI/CD logs | Lockstep releases (distributed monolith), nano-services |
| Calls per request | Distributed traces ([chapter 49](../observability/ch49-distributed-tracing-otel.md)) | Chatty calls, fan-out, deep chains, cycles |
| Table access | Database audit logs, the ownership audit from [chapter 12](ch12-database-per-service.md) | Shared databases |
| Library versions | Dependency manifests, SBOMs | Shared domain libraries, version skew |
| Service size and callers | Code metrics, service catalog | Nano-services |

This chapter puts that evidence into one plain-text model, runs detectors over it, and wires the result into the build.

### Where the names come from

<ul class="mh-timeline">
<li><span class="when">1999 · Foote & Yoder</span><span class="what"><em>Big Ball of Mud</em>: the architecture most systems actually have.</span></li>
<li><span class="when">2015 · Sam Newman, Stefan Tilkov</span><span class="what">Warnings against distributed monoliths and against "microservices first".</span></li>
<li><span class="when">2016–2018 · Richardson, Fowler</span><span class="what"><em>MonolithFirst</em>, "Microservice Prerequisites", and the anti-pattern catalogues on microservices.io.</span></li>
<li><span class="when">2017 · Ford, Parsons, Kua</span><span class="what"><em>Building Evolutionary Architectures</em>: fitness functions to keep architecture characteristics from eroding.</span></li>
<li><span class="when">2020s · Consolidation</span><span class="what">High-profile moves back from fine-grained services to fewer, larger ones (and to modular monoliths) make "right-sizing" a mainstream topic.</span></li>
</ul>

---

## 2 · Core Concepts & Terminology

### The catalogue

| Anti-pattern | What it looks like | Why it hurts | Evidence | Usual fix |
|---|---|---|---|---|
| **Distributed monolith** | Services that must be released together | All the costs of distribution, none of the independence | Lockstep deploys | Find the shared cause; version contracts; merge what changes together |
| **Chatty services (N+1)** | A loop of remote calls per item | Latency and failure multiply per item | Calls per request ≥ 5 on one edge | Batch endpoints; local replicas (ch. 19) |
| **Wide fan-out** | One request needs many services up | Availability is the product of all | ≥ 6 sync dependencies per request | Parallel calls with timeouts and fallbacks (ch. 18); replicate data |
| **Deep sync chains** | A → B → C → D → … | Each hop adds latency and a failure point | Longest sync path ≥ 3 hops | Replicate data; make later steps async |
| **Cyclic dependencies** | A needs B needs A | Neither can deploy, scale or fail alone | Cycles in the sync call graph | Move the responsibility; one direction as events |
| **Nano-services** | Services too small to pay their way | Network, pipeline and on-call cost for a function call's worth of work | Tiny + one caller or one release partner | Merge into the neighbour as a module |
| **Shared database** | Other services read or write your tables | Schema changes break others; no ownership | Non-owner table access | Database per service (ch. 12) |
| **Shared domain library** | Business classes in a common jar | Every model change is a coordinated release | `kind=domain` library used by many | Share contracts (OpenAPI, schemas), not classes |
| **Library version skew** | One shared library at many versions | Fixes in some services, not others; upgrade nobody can schedule | > 2 versions or different majors | Small, stable libraries; automated upgrades; BOMs |

Other anti-patterns this handbook covers elsewhere: entity services instead of capabilities ([chapter 6](../foundations/ch06-service-decomposition.md)), a gateway that grew business logic ([chapter 10](ch10-api-gateway-bff.md)), dual writes ([chapter 15](ch15-outbox-inbox-cdc.md)), retries in every layer ([chapter 18](ch18-resilience-patterns.md)), commands disguised as events ([chapter 19](ch19-event-driven-architecture.md)), and microservices without the platform to run them ([chapter 72](../governance-org/ch72-platform-engineering.md)).

### The architecture model

```text title="resources/architecture/foodfleet-2023.model (without the deploy history)"
--8<-- "code/chapters/ch20-anti-patterns/src/main/resources/architecture/foodfleet-2023.model:1:50"
```

One fact per line, kept in a repository and reviewed like code. Calls come from traces, tables from database audit logs, libraries from dependency manifests, deploys from the CI/CD history. The model doesn't need to be complete to be useful; it needs to be **true**.

---

## 3 · How It Works Internally

### Distributed monolith: reading the deploy history

For every pair of services, count how often they were released together relative to how often the less-frequently released one was released at all. Pairs above 70% are linked; linked groups of three or more are a lockstep cluster:

```java title="detect/DeployCouplingDetector.java"
--8<-- "code/chapters/ch20-anti-patterns/src/main/java/dev/foodfleet/ch20/detect/DeployCouplingDetector.java:13:64"
```

Co-deployment is the most honest signal there is: teams can claim independence, but the deploy log shows whether they have it. The detector finds the *symptom*; the *cause* is usually one of the other findings (a shared library, a shared table, a synchronous contract that can't be versioned).

### Call-graph smells

Only synchronous edges count: an event doesn't make the caller wait or fail.

```java title="detect/CallGraphDetector.java"
--8<-- "code/chapters/ch20-anti-patterns/src/main/java/dev/foodfleet/ch20/detect/CallGraphDetector.java:40:80"
```

### Nano-services

Small alone isn't the smell. Small **and** used by only one caller (or released with one partner most of the time) is: that is a module of its neighbour, paying the costs of a service.

```java title="detect/NanoServiceDetector.java"
--8<-- "code/chapters/ch20-anti-patterns/src/main/java/dev/foodfleet/ch20/detect/NanoServiceDetector.java:28:48"
```

### Shared data and shared libraries

```java title="detect/SharedDataDetector.java"
--8<-- "code/chapters/ch20-anti-patterns/src/main/java/dev/foodfleet/ch20/detect/SharedDataDetector.java:30:61"
```

Utility libraries (logging setup, HTTP client defaults) are fine to share, if they stay small and stable. Domain libraries aren't: when `Order` lives in a jar that ten services depend on, the jar *is* the coupling, and version skew is how it shows.

---

## 4 · Design Considerations & Trade-offs

### Measured: FoodFleet 2023 vs 2024

The detector, with FoodFleet's thresholds, on both architectures. Verified output (2023, abbreviated):

```text
== foodfleet-2023-entity-services: 14 services, 16 findings {HIGH=7, MEDIUM=6, LOW=3} ==
HIGH   Distributed monolith           address-book-service,cart-service,coupon-service,customer-profile-service,order-line-service,order-service
       evidence: 6 services deployed together in 10 of 14 releases that touched them; pairs co-deploy >= 70%
HIGH   Distributed monolith           menu-item-pricing-service,menu-service,restaurant-profile-service
       evidence: 3 services deployed together in 13 of 18 releases that touched them; pairs co-deploy >= 70%
HIGH   Cyclic service dependency      coupon-service->customer-profile-service->order-service->coupon-service
HIGH   Cyclic service dependency      customer-profile-service->order-service->customer-profile-service
HIGH   Shared database                customers     (owned by customer-profile-service, also written by address-book-service)
HIGH   Shared database                order_lines   (owned by order-line-service, also written by order-service)
HIGH   Shared domain library          foodfleet-domain  (business classes shared by 10 services)
MEDIUM Deep synchronous call chain    coupon-service->customer-profile-service->order-service->menu-service->menu-item-pricing-service->zone-service
       evidence: 5 synchronous hops; at 99.90% each the chain is up 99.40% of the time
MEDIUM Chatty communication (N+1)     menu-service->menu-item-pricing-service   (10 synchronous calls per request)
MEDIUM Wide synchronous fan-out       order-service   (9 synchronous dependencies per request; all up at once: 99.10%)
MEDIUM Shared database                menu_items, orders   (read directly by other services)
MEDIUM Shared-library version skew    foodfleet-domain   (6 versions in use (2.7.0 ... 3.4.0) across 10 services)
LOW    Nano-service                   address-book-service, menu-item-pricing-service, order-line-service

== foodfleet-2024-capabilities: 9 services, 1 findings {MEDIUM=1} ==
MEDIUM Shared database                customers
       evidence: owned by customer, read directly by promotions
```

How the findings connect is the real lesson:

- The **order cluster's lockstep releases** (10 of 14) have three causes the detector also found: the shared `foodfleet-domain` jar, the `order_lines` table written by order-service, and the customer-profile ↔ order cycle. Fixing the symptom (better release coordination) would have changed nothing.
- **All three nano-services** sit inside a lockstep cluster or behind a single caller; decomposition B merged each into its neighbour (order-line into ordering, address-book into customer, menu-item-pricing into catalog).
- The **deep chain and the cycle share a cause**: coupon eligibility computed from live calls. In 2024, promotions keeps the customer tier it needs; its last direct read of the `customers` table is the one accepted finding, with an owner and a ticket.
- **Chatty menu pricing disappeared by merging**, not by optimising: the ten calls became method calls inside catalog.

### Measured: what chattiness costs

A page needs data for N menu items from another service. Each call: median 4 ms, p99 about 30 ms, failing 0.1% of the time (a healthy in-cluster call). 100,000 page loads per row, verified:

| Strategy | N | p50 | p99 | Pages failed | Calls per page |
|---|---:|---:|---:|---:|---:|
| N+1 sequential | 12 | 66.6 ms | 136.6 ms | **1.20%** | 12 |
| N parallel | 12 | 15.9 ms | 60.7 ms | 1.20% | 12 |
| One batch call | 12 | 5.2 ms | 39.4 ms | **0.11%** | 1 |
| Local replica | 12 | 0.1 ms | 0.1 ms | 0% | 0 |
| N+1 sequential | 40 | 229.2 ms | 341.7 ms | **3.92%** | 40 |
| N parallel | 40 | 25.0 ms | 80.2 ms | 3.92% | 40 |
| One batch call | 40 | 8.0 ms | 60.6 ms | 0.11% | 1 |

- **Parallelising an N+1 loop fixes the median, not the failure rate**: twelve calls still fail twelve times as often as one. It also waits for the slowest of N, so its p99 grows with N.
- **Batching fixes both**: one round trip, one failure chance, whatever N is.
- **A local replica** removes the question, at the cost of eventual consistency ([chapter 19](ch19-event-driven-architecture.md)).

### Right-sizing: neither nano nor monolith

| Signal | Probably too small (merge) | Probably too big (split) |
|---|---|---|
| Change coupling | Always changes with a neighbour | Unrelated changes queue behind each other |
| Callers | One caller, which owns the use case | Many unrelated capabilities behind one API |
| Team | Two services, one team, always deployed together | One service, several teams stepping on each other |
| Data | Needs a neighbour's data on every request | Tables used by unrelated parts of the code |
| Scaling | Same load profile as its caller | Parts with very different load or latency needs |
| Failure | Its failure always fails its caller anyway | One feature's failure takes down unrelated ones |

Chapter 6's guidance holds: size services by business capability and team ownership, then use evidence (this chapter's detectors, change coupling) to challenge the result in both directions.

### Thresholds are policy

The detector's numbers (70% co-deploys, 5 calls per request, 6 dependencies, 3 hops, 2 endpoints and 3,000 lines for a nano-service) are FoodFleet's choices, recorded in ADR-020. Other systems will choose differently. What matters is that the thresholds are written down, applied automatically, and changed deliberately, not argued about per review.

---

## 5 · Hands-on Implementation

Code: `code/chapters/ch20-anti-patterns/`. Plain Java; the model files and baseline are in `src/main/resources/architecture/`.

```bash
cd code
mvn -B -pl chapters/ch20-anti-patterns -am verify
java -cp chapters/ch20-anti-patterns/target/classes dev.foodfleet.ch20.detect.AntiPatternReport
java -cp chapters/ch20-anti-patterns/target/classes dev.foodfleet.ch20.chatty.ChattinessSimulation
```

### 🔵 Level 2: each detector on a small model

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span></div>

```java title="DetectorsTest.java"
--8<-- "code/chapters/ch20-anti-patterns/src/test/java/dev/foodfleet/ch20/detect/DetectorsTest.java"
```

### 🟣 Level 3: thresholds as policy

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span></div>

```java title="detect/AntiPatternReport.java"
--8<-- "code/chapters/ch20-anti-patterns/src/main/java/dev/foodfleet/ch20/detect/AntiPatternReport.java:17:24"
```

### 🔴 Level 4: the fitness function and the shrinking baseline

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

A finding the team has looked at and accepted goes in a baseline with an owner and a ticket. The build fails on any **new** finding, on an **expired** acceptance, and on a baseline entry that is **no longer found**: once fixed, it must be removed, so it can't return unnoticed. The baseline can only shrink.

```java title="detect/Baseline.java"
--8<-- "code/chapters/ch20-anti-patterns/src/main/java/dev/foodfleet/ch20/detect/Baseline.java:10:74"
```

```text title="resources/architecture/accepted-findings.txt"
--8<-- "code/chapters/ch20-anti-patterns/src/main/resources/architecture/accepted-findings.txt"
```

```java title="ArchitectureFitnessTest.java"
--8<-- "code/chapters/ch20-anti-patterns/src/test/java/dev/foodfleet/ch20/fitness/ArchitectureFitnessTest.java"
```

The second test is the point: run against the 2023 model, the same build would have failed with more than ten new findings, long before the release train became a habit. Chapter 73 places this among FoodFleet's other fitness functions.

!!! question "Try it"
    1. Add `call ordering -> catalog per-request=12 sync` to the 2024 model. Which findings appear, and what would you change instead of accepting them?
    2. Lower the deploy-coupling threshold to 50%. Does the 2024 history show any cluster? Is it real coupling or coincidence (look at how many deploys each service had)?
    3. Add a `library foodfleet-events kind=domain` used by ordering and kitchen to the 2024 model. Is sharing event *schemas* (not classes) a smell? How would you model it?
    4. In `ChattinessSimulation`, give the batch call a 1% chance of timing out when N > 30. When does batching stop paying off?

---

## 6 · Production Readiness

- [x] **An architecture model kept in a repository**, generated where possible (traces, audit logs, manifests, CI history) and reviewed like code
- [x] **Detectors run on every change** to the model and nightly on regenerated data
- [x] **Thresholds written down** in an ADR and changed deliberately
- [x] **Accepted findings have owners, tickets and (optionally) expiry dates**; the baseline only shrinks
- [x] **Deploy independence measured**: share of releases that touch one service only, per team, on a dashboard
- [x] **Call graphs from traces**: calls per request per edge, sync depth, fan-out, reviewed when new dependencies appear
- [x] **Database access audited**: no non-owner writes; reads tracked and burned down
- [x] **Shared libraries limited** to small utilities; domain code never shared; upgrades automated
- [x] **Merges are allowed**: a process exists for consolidating services, not only for splitting them

---

## 7 · Anti-patterns & Pitfalls

!!! pitfall "Incident 1: the Thursday release train"
    **Context.** Six order-related services were "independently deployable" in principle.
    **Symptoms.** Every change shipped on Thursdays, all six together, after a two-hour coordination call; a bug in one rolled back all six.
    **Root cause.** A shared domain library, a table written by two services, and a synchronous cycle: changes rippled across all six.
    **Fix.** Decomposition B merged the cluster into ordering (a modular monolith inside, chapter 1) and customer; the library was split into per-service models plus published contracts.

!!! pitfall "Incident 2: the menu page that got slower after a 'performance' split"
    **Context.** Pricing was split out of menu-service "so it could scale independently".
    **Symptoms.** Menu page p99 rose from 40 ms to 180 ms; menu availability dropped measurably.
    **Root cause.** Ten synchronous pricing calls per menu page (N+1), each a chance to be slow or fail; pricing never needed different scaling.
    **Fix.** Merged back into catalog. Where a remote call per item is unavoidable elsewhere, a batch endpoint is now required by API review.

!!! pitfall "Incident 3: the jar that pinned ten services"
    **Context.** `foodfleet-domain` held `Order`, `Customer` and `MenuItem` classes for everyone.
    **Symptoms.** A security fix in a transitive dependency took seven weeks to reach all services; six versions of the jar were in production.
    **Root cause.** Domain classes shared across bounded contexts: every upgrade was a business-logic change for someone.
    **Fix.** Each service owns its model; contracts are shared as OpenAPI and event schemas; a small `foodfleet-platform` utility library with automated upgrades.

| Anti-pattern (in how teams fight anti-patterns) | Symptom | Fix |
|---|---|---|
| Architecture reviews by opinion | Endless debates, no change | Evidence: deploys, traces, audit logs |
| Fixing symptoms (release coordination) | The train gets faster but never goes away | Fix the cause the detectors point to |
| Splitting further to fix coupling | More services, same coupling | Merge what changes together |
| One-off cleanups | Anti-patterns return | Fitness functions in the build |
| Baselines that only grow | Accepted findings forever | Owners, tickets, expiry; remove fixed entries |
| Thresholds argued per review | Inconsistent decisions | Thresholds in an ADR |

---

## 8 · Enterprise Case Study

### FoodFleet: from 14 entity services to 9 capabilities, kept there

!!! decision "ADR-020: Architecture anti-patterns are detected from evidence and gated in the build"
    **Status:** Accepted · **Date:** 2024-07-01

    **Context.** The 2023 entity-service architecture had lockstep releases, synchronous cycles, chatty calls, shared tables and a shared domain library (16 findings, 7 high). Decomposition B (ADR-006) and chapters 12–19 removed all but one. Without a guard, the same pressures would bring them back.

    **Decision.** The platform team maintains an architecture model generated weekly from traces (calls per request), database audit logs (table access), dependency manifests (libraries) and CI/CD history (deploys), with hand-maintained service metadata. Detectors run in the architecture repository's build with these thresholds: lockstep ≥ 70% co-deploys over ≥ 5 deploys; chatty ≥ 5 sync calls per request; fan-out ≥ 6 sync dependencies; chains ≥ 3 sync hops; nano-services ≤ 2 endpoints and ≤ 3,000 lines with one caller or release partner; any non-owner table access; any shared domain library; > 2 versions or mixed majors of a shared library. New findings fail the build; accepted findings need an owner and a ticket, and are removed when fixed.

    **Consequences.** ✅ New coupling is visible the week it appears, with evidence, not at the next incident. ✅ Reviews discuss causes, not opinions. ⚠️ The model is only as true as its sources; trace sampling and audit-log coverage are monitored. ⚠️ Thresholds will produce some false positives; the baseline is where judgment is recorded.

### Fintech analogue: the "core platform" library 🔴

Banks that moved to services often kept a "core platform" jar containing account, customer and transaction classes for consistency. Over time it became the bank's real monolith: every change needed sign-off from every consuming team, and services on old versions computed things differently (day-count conventions, rounding). The usual remedy matches FoodFleet's: canonical *contracts* (message schemas, often ISO 20022 for payments) owned by a standards group, with each service owning its internal model and translating at its boundary.

### E-commerce analogue: the checkout fan-out

E-commerce checkouts tend to accrete synchronous dependencies: cart, pricing, promotions, tax, inventory, shipping options, fraud, payment, loyalty. Each was added for a good reason; together they make checkout availability the product of a dozen services. Teams that measure this typically move reference data (prices, tax tables, shipping rules) to local replicas, keep only authoritative decisions (payment, final inventory reservation) synchronous, and make the rest asynchronous after the order is placed.

---

## 9 · Interview Q&A + Summary

#### Q1. What is a distributed monolith? · <span class="badge badge-l2">L2</span><span class="badge badge-l3">L3</span> · Cognizant · SAP · Flipkart

**Short answer:** A system split into services that still can't be developed, deployed or fail independently, because of shared databases, shared domain libraries, synchronous chains or cycles. It has the costs of distribution without the benefits. The clearest evidence is services that are always released together.

#### Q2. How would you detect a distributed monolith in an existing system? · <span class="badge badge-l3">L3</span><span class="badge badge-l4">L4</span> · Amazon · Goldman Sachs

**Short answer:** From evidence: deploy history (co-deployment ratios), distributed traces (synchronous depth, cycles, fan-out), database audit logs (non-owner table access), dependency manifests (shared domain libraries, version skew), and change coupling in version control.

#### Q3. What is the N+1 problem between services and how do you fix it? · <span class="badge badge-l2">L2</span><span class="badge badge-l3">L3</span> · Flipkart · Amazon

**Short answer:** Calling another service once per item in a loop. Latency and failure probability grow with N. Parallelising helps latency but not failure rate; a batch endpoint fixes both; a local replica of the data removes the calls.

#### Q4. When is a service too small? · <span class="badge badge-l3">L3</span> · SAP · Goldman Sachs

**Short answer:** When its distribution costs (network hops, a pipeline, on-call, monitoring) exceed its value: typically when it is always changed or released with a neighbour, has a single caller that owns the use case, and has no different scaling, security or ownership needs. Merge it into the neighbour as a module.

#### Q5. Why are shared libraries risky in microservices? · <span class="badge badge-l3">L3</span> · JP Morgan · SAP

**Short answer:** A library with domain classes couples every service to one model and one release cadence; upgrades become coordinated releases and versions drift. Share contracts (API and event schemas) instead; keep shared code to small, stable utilities and automate their upgrades.

#### Q6. Why are cyclic dependencies between services a problem? · <span class="badge badge-l3">L3</span> · Amazon · Flipkart

**Short answer:** Neither service can be deployed, tested, scaled or failed independently; a slowdown in either propagates around the loop; startup ordering becomes fragile. Break the cycle by moving the shared responsibility into one service or by turning one direction into events or replicated data.

#### Q7. Is a shared database always an anti-pattern? · <span class="badge badge-l3">L3</span><span class="badge badge-l4">L4</span> · SAP · Goldman Sachs

**Short answer:** Non-owner writes almost always are. Non-owner reads are a weaker smell that can be a deliberate, temporary step (for example during migration) if they are tracked and burned down. Separate schemas on one database server, with no cross-access, are fine.

#### Q8. How do you keep anti-patterns from coming back? · <span class="badge badge-l4">L4</span> · Flipkart · Goldman Sachs

**Short answer:** Fitness functions in the build: an architecture model generated from real evidence, detectors with agreed thresholds, and a baseline of accepted findings with owners and tickets that can only shrink.

#### Q9. Microservices or modular monolith for a new product? · <span class="badge badge-l3">L3</span><span class="badge badge-l4">L4</span> · startups · any

**Short answer:** Usually a modular monolith first, with clear module boundaries and owned data, extracting a service when there is a concrete reason (independent scaling, deployment cadence, team autonomy, isolation). Starting with fine-grained services before boundaries are known is the most common route to a distributed monolith.

#### Q10. What does deep synchronous chaining do to availability? · <span class="badge badge-l2">L2</span><span class="badge badge-l3">L3</span> · any

**Short answer:** Availabilities multiply: six services at 99.9% each give at most 99.4% for the chain, and latencies add. Shorten chains with replicated data and asynchronous steps, and protect the remaining calls with timeouts and fallbacks.

#### Q11. Your team wants to split a service "for performance". What do you ask? · <span class="badge badge-l3">L3</span><span class="badge badge-l4">L4</span> · Amazon · Goldman Sachs

**Short answer:** Which part has a different load or latency profile, with data? How many calls per request will cross the new boundary? Will the parts change and release independently? Who owns each? If the answers are "same profile, many calls, always together, same team", the split will make performance worse.

#### Q12. Name anti-patterns you've seen and how you fixed them. · <span class="badge badge-l3">L3</span><span class="badge badge-l4">L4</span> · any

**Short answer:** Use a concrete story with evidence and outcome: e.g., a lockstep cluster caused by a shared domain library and a table written by two services, found from deploy logs and database audit logs, fixed by merging the services that always changed together and replacing the library with published contracts, then guarded by a fitness function.

### Key takeaways

- Most microservice failures are **anti-patterns that add distribution costs without independence**: lockstep releases, chatty calls, cycles, shared data and shared domain code.
- Find them from **evidence**: deploy history, traces, database audit logs, dependency manifests.
- **Fix causes, not symptoms**: the release train is a symptom; the shared jar, shared table and cycle are causes.
- Measured: FoodFleet 2023 had **16 findings (7 high)**, 2024 has **1 accepted**; an N+1 loop over 12 items fails **11×** more pages than one batch call.
- Keep them out with a **fitness function** and a **baseline that only shrinks**; allow **merges** as readily as splits.

### Further reading

- Sam Newman, *Building Microservices* (O'Reilly, 2nd ed. 2021), and *Monolith to Microservices* (2019)
- Martin Fowler, [MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html) and [Microservice Prerequisites](https://martinfowler.com/bliki/MicroservicePrerequisites.html)
- Neal Ford, Rebecca Parsons, Patrick Kua, Pramod Sadalage, *Building Evolutionary Architectures* (O'Reilly, 2nd ed. 2022): fitness functions
- Neal Ford, Mark Richards, Pramod Sadalage, Zhamak Dehghani, *Software Architecture: The Hard Parts* (O'Reilly, 2021): granularity integrators and disintegrators
- Brian Foote and Joseph Yoder, [Big Ball of Mud](http://www.laputan.org/mud/) (1999)

---

**Previous:** [← 19. Event-Driven Architecture](ch19-event-driven-architecture.md) · **Next:** [Part 3 — Communication →](../communication/index.md)
