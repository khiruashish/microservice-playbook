---
title: "Testing Strategy"
description: "What FoodFleet tests, where and when: the suites every service has and what each catches (chapter 61's 20 planted bugs: unit tests 7, component tests 12, all five suites 19), the 38 consumer–provider contracts the platform model implies, the E2E journeys and preview environments, and load, chaos and architecture tests, each with what its chapter measured."
progress: done
---

# Testing Strategy

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Three incidents in one quarter passed 2,100 green tests at FoodFleet ([chapter 61](../testing/ch61-test-strategy.md)). The tests weren't too few; they were aimed at the wrong bugs. FoodFleet's strategy since then starts from where bugs actually live, and measures which suite catches them.

## The suites every service has

| Suite | What it covers | Runs | Decision |
|---|---|---|---|
| **Unit** | Rules at their edges: caps, rounding, boundaries, branches | Every commit | [ADR-061](../testing/ch61-test-strategy.md) |
| **Repository** | The service's SQL on PostgreSQL 16 in a container, one container per run, schema from the Flyway migrations | Before merge | [ADR-062](../testing/ch62-unit-slice-integration-tests.md) |
| **Concurrency** | Every once-only rule (a coupon redeemed once, an order placed once) with concurrent callers: one winner, no errors | Before merge | [ADR-062](../testing/ch62-unit-slice-integration-tests.md) |
| **Contract** | The service's clients against its providers' contracts; its own API against its consumers' | Every commit | [ADR-063](../testing/ch63-contract-testing.md) |
| **Component** | Three to ten tests of the whole service through its API, with PostgreSQL and contract stubs | Every commit | [ADR-061](../testing/ch61-test-strategy.md), [ADR-064](../testing/ch64-component-e2e-testing.md) |
| **Architecture** | ArchUnit layers, cycles and coding rules, with a committed freeze store | Every build | [ADR-065](../testing/ch65-performance-chaos-arch-tests.md) |
| **E2E** | At most a dozen journeys per domain, in a preview environment per pull request | Before merge | [ADR-064](../testing/ch64-component-e2e-testing.md) |
| **Load** | k6 at a constant arrival rate | Release pipeline, critical services | [ADR-065](../testing/ch65-performance-chaos-arch-tests.md) |
| **Chaos** | Experiments per critical journey with SLO guards | CI, staging weekly, production for proven hypotheses | [ADR-045](../resilience/ch45-chaos-engineering.md) |

No suite uses H2. Each postmortem adds a planted bug to the service's list, with the test that catches it.

## Which suite catches what

[Chapter 61](../testing/ch61-test-strategy.md) planted 20 bugs in order-service and ran each suite against them:

| Bugs of this kind | Caught by |
|---|---|
| 7 logic bugs (caps, rounding, a minimum order) | Unit tests: all 7. Component tests: 3 |
| 7 SQL and transaction bugs | Repository tests on PostgreSQL: 6. On H2: none, and H2 failed 3 of 4 tests on correct code |
| 3 API bugs (a wrong status, a renamed field) | Component tests only |
| 3 client bugs (a wrong path, a parsed price) | Contract tests: 3. Component tests: 2 |

| Combination | Caught, of 20 |
|---|---|
| Unit tests alone | 7 |
| Component tests alone | 12 |
| A pyramid without component tests | 16 |
| Unit and component | 16 |
| All five suites | **19** |

The one bug every suite missed (lines read in no particular order) lacked an assertion, not a layer. The lesson isn't a shape, pyramid or honeycomb: each kind of bug has a suite that sees it, and the mix follows the service's bugs.

## Contracts the platform implies

Every dependency in the [platform model](index.md#how-these-pages-are-made) is a contract to keep. `check.py` lists them, provider by provider:

--8<-- "code/foodfleet/model/generated/contracts.md"

That's 38 consumer–provider pairs. Three of the synchronous ones, the edge gateway in front of each BFF, are routes rather than contracts: the gateway forwards requests without reading them, so the BFFs' real consumers are the apps, which publish their own contracts ([ADR-027](../communication/ch27-api-contracts.md)).

[Chapter 63](../testing/ch63-contract-testing.md) measured contracts against twelve real menu-service changes:

- Consumer contracts caught **7 of 9** breaking changes with no false alarms, each naming the consumer affected.
- An OpenAPI diff caught 5, with 2 false alarms; it was the only check to catch a new enum value, and it missed a change of units.
- The E2E suite caught the same 7 as the contracts, much later and more slowly.

So contracts are the main safety net between services, an OpenAPI diff runs beside them for review, and `can-i-deploy` gates every deployment.

## End to end, but only a few journeys

E2E tests are slow and they fail for reasons unrelated to the change. [Chapter 64](../testing/ch64-component-e2e-testing.md) measured both:

- Four pipelines sharing one staging environment passed **1–5 of 12** suite runs; with a fresh environment per pull request, **12 of 12**.
- Fixed sleeps failed 1–28% of asynchronous checks; polling with Awaitility, none of 400.
- Five journeys took 1.9–2.3 s as component tests and 19–28 s as E2E.

So FoodFleet keeps E2E to the journeys that matter, browse, order, pay and track, each run in a preview environment created by an Argo CD ApplicationSet from the production chart with the pull request's images. Tests create their own data and never sleep. Shared staging remains for manual testing and demos; no pipeline gates on it.

The one E2E check that covers the whole platform is the synthetic order: in staging it must produce a single trace through every service it touches ([observability](observability.md)).

## Load, chaos and architecture

| Test | What FoodFleet does | What the chapter measured |
|---|---|---|
| **Load** | Open (arrival-rate) models, reporting p99, p99.9 and max; capacity by stepped rates per release | Against a service that froze periodically, a closed model reported p99 15 ms; the open model, 942 ms |
| **Chaos** | Monthly experiments per critical dependency, hypotheses from SLOs, faults through the mesh, abort on one bad window | Only a timeout with a fallback kept the hypothesis with a dependency slow or hung; the abort limited bad experiments to 7 s |
| **Architecture** | ArchUnit in every build; `Class.forName` only in configuration; `jdeps` between modules | Five fitness functions caught 8 of 10 planted violations in under a millisecond; an inlined constant and reflection escaped |

[Chapter 73](../governance-org/ch73-adrs-fitness-functions.md) adds fitness functions traced to ADRs, and latency checks that compare against a recent baseline, best of three, rather than a fixed budget, which missed a fourfold regression.

## The platform's own tests

The FoodFleet pages are tested the same way the services are:

| Check | What it guards |
|---|---|
| `code/foodfleet/model/check.py` | The model is consistent with itself and with the chapters' ADRs; no synchronous cycles |
| `code/foodfleet/deploy/conformance.py` | Every rendered service follows the platform's deployment ADRs (21 of 21, 20 checks each) |
| `promtool check rules` on the generated SLO rules | The SLOs compile into valid Prometheus rules (72 rules) |
| `mkdocs build --strict` | Every link and included fragment resolves |
