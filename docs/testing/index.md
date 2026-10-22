---
title: "Part 9 — Testing"
description: "How FoodFleet tests a system of services: which kinds of tests catch which bugs, unit and slice tests with real databases in containers, consumer-driven contracts, component tests and preview environments, and the tests of qualities (load, chaos, architecture), each measured with planted bugs, real PostgreSQL, a Kubernetes cluster and real load."
progress: done
---

# Part 9 — Testing

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Part 8 put services into production. Part 9 is how FoodFleet knows a change is safe to put there:

- **Which tests catch which bugs**, and so which mix a service needs.
- **How to write them**: unit tests, slices, real databases, contracts, components, previews.
- **The qualities** that aren't features: load, resilience, architecture.

The recurring lesson is that the cheap way to test something quietly tests something else:

- An H2 database instead of PostgreSQL.
- A rollback that doesn't cover the code's own transactions.
- Recorded examples as contracts.
- A shared staging environment.
- A sleep.
- A load generator that waits politely through a stall.
- A rule that can't see an inlined constant.

Every chapter measures its claims:

- Planted bugs and planted violations.
- PostgreSQL 16 in containers.
- A k3s 1.37 cluster.
- Real HTTP load.
- The sources of JUnit 6, Spring Boot 4.1, Testcontainers 2, Pact JVM, Spring Cloud Contract, oasdiff, k6, Gatling and ArchUnit.

The measured output is in the text, and the code to reproduce it is in the repository.

## FoodFleet's testing, layer by layer

| Layer | What it checks | FoodFleet's choice | When it runs | Chapters |
|---|---|---|---|---|
| Unit | Rules and their edges | Plain JUnit 6, no Spring, no containers | Every commit | [61](ch61-test-strategy.md), [62](ch62-unit-slice-integration-tests.md) |
| Repository | SQL, transactions, constraints, races | `@JdbcTest`/`@DataJpaTest` on PostgreSQL via Testcontainers, one container per run; no H2 | Every commit | [62](ch62-unit-slice-integration-tests.md) |
| Contract | What each consumer relies on | Pact, consumer-written, type matchers with formats; can-i-deploy gates releases | Every build of either side | [63](ch63-contract-testing.md) |
| Component | One service through its API | `@SpringBootTest` on a random port, contract stubs, real database | Every commit | [64](ch64-component-e2e-testing.md) |
| End-to-end | A few journeys across services | Preview environment per pull request; tests own their data; Awaitility, never sleeps | Before merge | [64](ch64-component-e2e-testing.md) |
| Qualities | Latency, capacity, resilience, architecture | k6 open models; chaos experiments with abort; ArchUnit with frozen debt | Release, monthly, every build | [65](ch65-performance-chaos-arch-tests.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| Arguing pyramid vs honeycomb | Planted bugs against your suites | [61](ch61-test-strategy.md) | Unit tests caught 7 of 7 logic bugs and nothing else; component tests 12 of 20; all five suites 19 |
| Repository tests on H2 | PostgreSQL in a container | [61](ch61-test-strategy.md), [62](ch62-unit-slice-integration-tests.md) | H2 failed 3 of 4 tests on correct code and 8 of 19 PostgreSQL features |
| Slow container tests | One container per run, reuse locally | [62](ch62-unit-slice-integration-tests.md) | 4.0–4.7 s against 18.0–22.9 s for 8 classes; 1.5–2.5 s reused |
| Tests that pass alone and fail together | Truncate instead of rollback for integrated tests | [62](ch62-unit-slice-integration-tests.md) | Rollback leaked into 299 of 300 tests when code committed on its own connection |
| A rule that must hold "once" | A concurrent test on the real database | [62](ch62-unit-slice-integration-tests.md) | Check-then-insert passed every single-threaded test, then 421–449 errors under concurrency |
| Providers breaking consumers | Consumer-driven contracts and can-i-deploy | [63](ch63-contract-testing.md) | Contracts caught 7 of 9 breaking changes with no false alarms; an OpenAPI diff 5 with 2 |
| Contracts that fail on every data change | Type matchers with formats | [63](ch63-contract-testing.md) | Recorded examples flagged 5 of 5 harmless data changes |
| A red shared staging | A preview environment per pull request | [64](ch64-component-e2e-testing.md) | Shared staging passed 1–5 of 12 suite runs; fresh environments 12 of 12, ready in about 7 s |
| Flaky asynchronous tests | Polling with a timeout | [64](ch64-component-e2e-testing.md) | Sleeps failed 1–28% of runs; polling none of 400, faster on average |
| Load-test numbers that look too good | Open (arrival-rate) models | [65](ch65-performance-chaos-arch-tests.md) | A closed model's p99 was 15 ms; the open model's 942 ms |
| Not knowing capacity | Stepped arrival rates | [65](ch65-performance-chaos-arch-tests.md) | Closed models plateaued; at 108% of capacity the open model's p99 was 1.5–1.7 s |
| Resilience you've never tested | Chaos experiments with abort conditions | [65](ch65-performance-chaos-arch-tests.md) | Only a timeout with a fallback held the hypothesis; aborts ended bad experiments in 7 s |
| Architecture drift | Fitness functions with frozen debt | [65](ch65-performance-chaos-arch-tests.md) | 8 of 10 violations caught in under a millisecond; an inlined constant and reflection escaped |

## The chapters

<div class="grid cards" markdown>

-   **[61. Test Pyramid & Honeycomb](ch61-test-strategy.md)**

    ---

    Which suite catches which bug, measured with 20 planted bugs. *JUnit 6 tags, PostgreSQL 16, H2 2.5.*

-   **[62. Unit, Slice & Testcontainers Tests](ch62-unit-slice-integration-tests.md)**

    ---

    Container lifecycles, isolation, H2's gaps, concurrency tests. *PostgreSQL in Docker; Spring Boot 4.1 slices.*

-   **[63. Contract Testing](ch63-contract-testing.md)**

    ---

    Consumer contracts against an OpenAPI diff and E2E; matcher strictness; can-i-deploy. *Pact JVM, oasdiff.*

-   **[64. Component & End-to-End Testing](ch64-component-e2e-testing.md)**

    ---

    Preview environments, shared staging, sleeps vs polling, component vs E2E. *A k3s 1.37 cluster.*

-   **[65. Performance, Chaos & Architecture Tests](ch65-performance-chaos-arch-tests.md)**

    ---

    Coordinated omission, capacity, chaos with abort conditions, fitness functions. *k6, Gatling and ArchUnit's rules.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **New to testing services** (🟢 L1) | 61 → 62 | [Part 10: Performance & Scalability](../performance-scalability/index.md) |
| **Building services already** (🔵 L2) | 61 → 62 → 63 → 64 | [Part 10: Performance & Scalability](../performance-scalability/index.md) |
| **Leading a team** (🟣 L3) | 61 → 63 → 64 → 65 | [Part 10: Performance & Scalability](../performance-scalability/index.md) |
| **Preparing for interviews** | 61, 63, 65 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-061 · Chapter 61</span><span class="what">Unit tests for rules, repository tests on the production engine, contract tests for clients, component tests through the API; no H2; incidents replayed as planted bugs.</span></li>
<li><span class="when">ADR-062 · Chapter 62</span><span class="what">PostgreSQL via Testcontainers, one container per run with @ServiceConnection; rollback in slices, truncation in integrated tests; concurrent tests for once-only rules; Flyway schemas.</span></li>
<li><span class="when">ADR-063 · Chapter 63</span><span class="what">Pact contracts from every consumer; provider verification with realistic states; can-i-deploy gates deployments; OpenAPI diffs reviewed.</span></li>
<li><span class="when">ADR-064 · Chapter 64</span><span class="what">Component tests on every commit; E2E in a preview environment per pull request; tests own their data and poll; staging for people only.</span></li>
<li><span class="when">ADR-065 · Chapter 65</span><span class="what">Open-model load tests and stepped capacity tests; chaos experiments with SLO hypotheses and automatic abort; ArchUnit fitness functions with frozen debt.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch61-test-strategy,chapters/ch62-unit-slice-integration-tests,chapters/ch63-contract-testing,chapters/ch64-component-e2e-testing,chapters/ch65-performance-chaos-arch-tests -am verify
    ```
    JDK 25 and Maven 3.9+. Each chapter's `verify/` folder holds its experiments and says what they need:

    - **61 and 62:** PostgreSQL (or Docker); 62 builds a PostgreSQL image or uses the official one.
    - **63 and 65:** only a JDK.
    - **64:** Docker and a Kubernetes cluster (chapter 52's `k3s-lab.sh` starts one).
