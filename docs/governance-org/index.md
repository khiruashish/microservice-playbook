---
title: "Part 11 — Governance & Organization"
description: "How FoodFleet keeps seventy services coherent as they and the organisation change: APIs governed as products, a platform that makes the right setup the fast one, decisions recorded and checked, migrations planned from measurements, many tenants on one platform, functions where they win, and AI features behind a gateway, each measured with real linters, real Kubernetes, real PostgreSQL, a real monolith's code and history, and AWS list prices."
progress: done
---

# Part 11 — Governance & Organization

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Parts 1 to 10 built and ran services. Part 11 is about keeping many of them coherent while the system, the teams and the technology change:

- **APIs as products**: linted, catalogued, deprecated with evidence.
- **The platform**: golden paths that stay current, and checks that show where every service stands.
- **Decisions**: recorded so they can be found, and made executable so they're kept.
- **Change at scale**: migrating a monolith step by step, from measurements.
- **New shapes**: many tenants on one platform, functions beside containers, and features that call models.

The recurring lesson is that **governance that isn't automated decays**:

- a standard announced but not checked;
- a template that can't be updated;
- an ADR edited in place;
- a tenant filter left to each query;
- a tool that trusts its arguments.

Each chapter measures what happens without the automation, and what the automation costs.

Every chapter measures its claims, with:

- Spectral's and Backstage's rules.
- A k3s cluster and a server-side dry run.
- Three real decision logs.
- Broadleaf Commerce's code and ten years of its history.
- PostgreSQL 16.
- JVMs cold-started with and without AOT caches.
- AWS list prices.
- An MCP server on the 2026-07-28 protocol.

## FoodFleet's governance, layer by layer

| Layer | What decides it | FoodFleet's choice | Chapter |
|---|---|---|---|
| APIs | Lint rules, catalog, deprecation | OpenAPI in every repo, Spectral plus house rules with a ratchet, Deprecation/Sunset headers and usage logs | [71](ch71-api-governance.md) |
| Platform | Templates, updates, conformance | Versioned golden paths in Backstage, updates by three-way merge, scorecards and a server-side dry run | [72](ch72-platform-engineering.md) |
| Decisions | Records and their checks | ADRs never edited, a linted log, fitness functions traced to ADRs, a radar as data | [73](ch73-adrs-fitness-functions.md) |
| Migration | Coupling, cycles, history | Modular monolith first, then extraction by value per remote dependency, with measured gates | [74](ch74-maturity-migration-roadmap.md) |
| Tenancy and data | Isolation, fairness, residency, erasure | Pooled tables with forced RLS, per-tenant bulkheads, home regions, crypto-shredding | [75](ch75-multi-tenancy-multi-region-compliance.md) |
| Compute model | Rate profile and service time | Functions for events and bursts, containers above the crossover | [76](ch76-serverless-microservices.md) |
| AI features | Gateway, limits, tools | One LLM gateway with stream-aware deadlines and per-feature limits; MCP servers with identity from the token | [77](ch77-ai-native-microservices.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| API reviews argue about the same things | A ruleset with house rules, and a ratchet for old APIs | [71](ch71-api-governance.md) | Spectral caught 10 of 33 planted problems, house rules 16 more; 7 needed a reviewer |
| Nobody knows who still calls a deprecated API | Deprecation headers and an access log by client | [71](ch71-api-governance.md) | 2 of 4 remaining callers were in no catalog; only the log found them |
| New services take weeks to set up | A golden path in a developer portal | [72](ch72-platform-engineering.md) | From a name to two ready pods in 6.5–8.0 s, 12 of 12 checks |
| Old services ignore new standards | Template updates by three-way merge | [72](ch72-platform-engineering.md) | An old template's services passed 8% of checks; merging took them from 4 to 40 of 48 |
| Decisions are forgotten or quietly undone | Fitness functions traced to ADRs | [73](ch73-adrs-fitness-functions.md) | Code checked 7 of 72 decisions; five functions caught one problem in each of six releases |
| Performance checks in CI are flaky or blind | A ratchet against a recent baseline, best of three | [73](ch73-adrs-fitness-functions.md) | A fixed budget passed a fourfold regression; single runs raised up to 9 false alarms in 12 |
| A migration plan from a whiteboard | Measure coupling, cycles and history first | [74](ch74-maturity-migration-roadmap.md) | Broadleaf: 9 contexts in one cycle, 157 imports from layers |
| What to extract first | Most independent changes per new remote dependency | [74](ch74-maturity-migration-roadmap.md) | 32% of changes freed for 340 remote edges, against 1,083 by most-changed-first |
| One tenant could see another's data | Forced RLS, tenant set per transaction | [75](ch75-multi-tenancy-multi-region-compliance.md) | A per-session tenant leaked in 177 of 193 forgotten requests; per transaction, none |
| One big tenant slows everyone | Per-tenant bulkheads | [75](ch75-multi-tenancy-multi-region-compliance.md) | Others' p99 25–38 ms unlimited, 8–11 ms with a bulkhead |
| "Forget me" must reach backups | Crypto-shredding | [75](ch75-multi-tenancy-multi-region-compliance.md) | DELETE left the email in WAL and backup; shredding left only unreadable ciphertext |
| Should this be a function? | Price the workload's rate and service time | [76](ch76-serverless-microservices.md) | Webhooks under a cent a day as functions; containers cheaper above 28–106 requests/s |
| Java functions start slowly | AOT cache, C1, Serial GC | [76](ch76-serverless-microservices.md) | JVM part of a cold start 246–329 ms → 113–196 ms |
| LLM calls hang or fail under load | A gateway with stream-aware deadlines and per-feature limits | [77](ch77-ai-native-microservices.md) | p99 5.1–5.3 s instead of 10–30 s on stalls; chats 2.0 s median beside a batch job |
| An assistant's tools leak data | Identity from the token, not from arguments | [77](ch77-ai-native-microservices.md) | Injected text read another customer's order 30 of 30 times with argument identity, 0 with token identity |

## The chapters

<div class="grid cards" markdown>

-   **[71. API Governance & Developer Portals](ch71-api-governance.md)**

    ---

    Spectral and house rules, a ratchet, deprecation headers and usage, catalog checks. *Spectral's rulesets, Backstage.*

-   **[72. Platform Engineering & Golden Paths](ch72-platform-engineering.md)**

    ---

    A template to a running pod, a drifted fleet, upgrades by three-way merge. *k3s, Backstage, Copier.*

-   **[73. ADRs, Fitness Functions & Tech Radar](ch73-adrs-fitness-functions.md)**

    ---

    Three decision logs linted, a release train against five fitness functions, how much to trust a latency check. *adr-tools, MADR, ClassFile API.*

-   **[74. Maturity Model & Migration Roadmap](ch74-maturity-migration-roadmap.md)**

    ---

    A real monolith's coupling, cycles and history; five extraction orders compared. *Broadleaf Commerce, Code Maat.*

-   **[75. Multi-Tenancy, Multi-Region & Compliance](ch75-multi-tenancy-multi-region-compliance.md)**

    ---

    Three tenancy models, tenant leaks, noisy neighbours, erasure and crypto-shredding. *PostgreSQL 16.*

-   **[76. Serverless & Function-Based Services](ch76-serverless-microservices.md)**

    ---

    Java cold starts with AOT caches; a day on functions, scale-to-zero and always-on containers. *JDK 25, Knative's defaults, AWS prices.*

-   **[77. AI-Native Microservices](ch77-ai-native-microservices.md)**

    ---

    An LLM gateway's deadlines and limits, response caching, and an MCP server's security. *MCP 2026-07-28, Spring AI.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Leading a team** (🟣 L3) | 71 → 73 → 75 → 77 | [Interview prep](../interview-prep/index.md) |
| **Running a platform team** (🔴 L4) | 72 → 73 → 76 → 77 | [Appendix: pattern decision trees](../appendix/pattern-decision-trees.md) |
| **Planning a migration** (🔴 L4) | 74 → 73 → 72 → 75 | [Part 3: the strangler fig](../architecture-patterns/ch16-strangler-fig-migration.md) |
| **Preparing for architect interviews** | 73, 74, 75, 77 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-071 · Chapter 71</span><span class="what">OpenAPI in every repository, linted with Spectral and house rules, baselines for legacy APIs; deprecations with Deprecation, Sunset and Link headers; a catalog with governance checks.</span></li>
<li><span class="when">ADR-072 · Chapter 72</span><span class="what">Services start from a versioned template in the portal; template releases open three-way-merge update PRs; conformance checks and a server-side dry run in CI.</span></li>
<li><span class="when">ADR-073 · Chapter 73</span><span class="what">ADRs never edited after acceptance, the log linted; fitness functions traced to ADRs; performance ratchets with repeats; the radar as data.</span></li>
<li><span class="when">ADR-074 · Chapter 74</span><span class="what">Migrations from measurements: modular monolith first, extraction by value per remote dependency, maturity judged by measured gates.</span></li>
<li><span class="when">ADR-075 · Chapter 75</span><span class="what">Pooled tables with forced RLS and the tenant per transaction; silos by tier; per-tenant bulkheads; home regions; crypto-shredding with keys in a KMS.</span></li>
<li><span class="when">ADR-076 · Chapter 76</span><span class="what">Functions for events, schedules and bursts with AOT-cached JVMs; containers above the cost crossover; reviewed quarterly.</span></li>
<li><span class="when">ADR-077 · Chapter 77</span><span class="what">One LLM gateway with stream-aware deadlines, per-feature limits, safe caching and cost accounting; MCP servers with identity from the token.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch71-api-governance,chapters/ch72-platform-engineering,chapters/ch73-adrs-fitness-functions,chapters/ch74-maturity-migration-roadmap,chapters/ch75-multi-tenancy-multi-region-compliance,chapters/ch76-serverless-microservices,chapters/ch77-ai-native-microservices -am verify
    ```
    JDK 25 and Maven 3.9+. Each chapter's `verify/` folder holds its experiments and says what they need:

    - **71, 76, 77:** a JDK 25 and git.
    - **72:** Docker, git and a Kubernetes cluster (chapter 52's `k3s-lab.sh` starts one).
    - **73:** git; about 20 minutes, mostly repeated latency runs.
    - **74:** git and a 70 MB clone of Broadleaf Commerce.
    - **75:** PostgreSQL 16 server binaries and 2 GB of disk.
