---
title: "SonarQube"
description: "Static analysis and quality gates with SonarQube, and what FoodFleet relies on instead of coverage numbers: chapter 61 planted 20 realistic bugs in order-service and counted which suites caught them (one bug, lines read in no order, ran under several tests and failed none: coverage said the code ran, not that anything was checked). Where SonarQube helps (new-code gates, security hotspots, duplication), where compiler-level tools are stronger (NullAway, ArchUnit), and mutation testing for rule-heavy code."
progress: done
---

# SonarQube

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | A code-quality platform: static analysis rules for bugs, code smells, security hotspots and vulnerabilities (taint analysis in commercial editions), duplication and coverage import, with **quality gates** that pass or fail a branch or pull request, usually on new code |
| **FoodFleet uses** | **No ADR adopts it.** The handbook's quality signals are tests measured with planted bugs, compile-time null checks, architecture tests and dependency scanning |
| **Learn it in** | [Chapter 61](../testing/ch61-test-strategy.md), [chapter 65](../testing/ch65-performance-chaos-arch-tests.md) (architecture tests), [chapter 8](../foundations/ch08-spring-boot-4-essentials.md) (NullAway) |
| **Related** | [Security scanners](security-scanners.md), [GitHub Actions](github-actions.md), [Contract testing](contract-testing-tools.md) |

## Coverage says code ran

[Chapter 61](../testing/ch61-test-strategy.md) planted 20 realistic bugs, one at a time, into order-service and ran five suites. Every logic bug was caught by unit tests; every SQL bug but one by PostgreSQL repository tests. The one that escaped, **R7: lines read in no defined order**, ran under several tests and failed none, because nothing asserted the order. A coverage gate would have been green.

What finds that kind of gap:

| Technique | What it tells you |
|---|---|
| **Planted bugs from incidents** | Whether the suites catch the bugs this service actually had; each postmortem adds one |
| **Mutation testing** (PIT) on rule-heavy modules | Which changes to the code no test notices; read the survivors |
| Coverage | Which code no test ran: useful to find untested code, not to prove tested code |

## Where SonarQube helps

- **New-code quality gates** on pull requests: no new blocker issues, no new duplication, reviewed security hotspots.
- **Consistent rules across many repositories**, with history per project.
- **Security hotspots** that need a human look (regexes, crypto choices, deserialisation).
- **Coverage import** from JaCoCo as context, not a target.

## Stronger checks for specific risks

| Risk | Tool in the handbook |
|---|---|
| Null dereferences | **NullAway** with JSpecify annotations, failing the compile ([chapter 8](../foundations/ch08-spring-boot-4-essentials.md)) |
| Layering and dependency rules | **ArchUnit** with frozen violations ([lab 19](../code-lab/lab-19-contracts-archunit.md)) |
| Breaking API changes | oasdiff on the OpenAPI document ([OpenAPI](openapi-springdoc.md)) |
| Vulnerable dependencies | SBOM scanning, gated on fixable findings ([Security scanners](security-scanners.md)) |
| SQL that only works on H2 | Tests on PostgreSQL in a container: chapter 61's H2 suite failed 3 tests on correct code and caught nothing |

## Pitfalls

- **Coverage targets**: tests written to execute lines, not to check behaviour.
- **Gates on overall debt** for legacy code: nobody can ever pass; gate on new code.
- **Thousands of unreviewed issues**: tune the profile until findings are worth reading.
- **Treating static analysis as security review**.
