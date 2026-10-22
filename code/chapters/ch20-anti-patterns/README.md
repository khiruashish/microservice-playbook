# Chapter 20 — Microservice Anti-Patterns

Anti-patterns are found from evidence, not opinion. This module reads an architecture model kept in
the repository (services, calls per request, table access, shared libraries, deploy history) and
reports anti-patterns with the evidence and the usual remedy. A fitness-function test fails the
build on any finding that isn't in an accepted baseline, and the baseline can only shrink.

| Package | What it is |
|---|---|
| `model/` | `SystemModel` and `ModelParser` for the plain-text `.model` format |
| `detect/` | `DeployCouplingDetector` (distributed monolith), `CallGraphDetector` (chatty, fan-out, deep chains, cycles), `NanoServiceDetector`, `SharedDataDetector` (shared tables, shared domain library, version skew), `AntiPatternReport`, `Baseline` |
| `chatty/` | `ChattinessSimulation`: N+1 vs parallel vs batch vs local replica |
| `resources/architecture/` | `foodfleet-2023.model` (entity services), `foodfleet-2024.model` (capabilities), `accepted-findings.txt` |

## Run

```bash
cd code
mvn -B -pl chapters/ch20-anti-patterns -am verify
java -cp chapters/ch20-anti-patterns/target/classes dev.foodfleet.ch20.detect.AntiPatternReport
java -cp chapters/ch20-anti-patterns/target/classes dev.foodfleet.ch20.chatty.ChattinessSimulation
```

## Verified output

```text
== foodfleet-2023-entity-services: 14 services, 16 findings {HIGH=7, MEDIUM=6, LOW=3} ==
HIGH   Distributed monolith           address-book-service,cart-service,coupon-service,customer-profile-service,order-line-service,order-service
       evidence: 6 services deployed together in 10 of 14 releases that touched them; pairs co-deploy >= 70%
       remedy:   find the shared cause (schema, library, synchronous contract); version APIs and events; merge services that always change together
HIGH   Distributed monolith           menu-item-pricing-service,menu-service,restaurant-profile-service
       evidence: 3 services deployed together in 13 of 18 releases that touched them; pairs co-deploy >= 70%
       remedy:   find the shared cause (schema, library, synchronous contract); version APIs and events; merge services that always change together
HIGH   Cyclic service dependency      coupon-service->customer-profile-service->order-service->coupon-service
       evidence: synchronous calls form a cycle of 3 services
       remedy:   move the shared responsibility into one service, or replace one direction with events
HIGH   Cyclic service dependency      customer-profile-service->order-service->customer-profile-service
       evidence: synchronous calls form a cycle of 2 services
       remedy:   move the shared responsibility into one service, or replace one direction with events
HIGH   Shared database                customers
       evidence: owned by customer-profile-service, also written by address-book-service
       remedy:   only the owner writes; others call its API or send it commands (chapter 12)
HIGH   Shared database                order_lines
       evidence: owned by order-line-service, also written by order-service
       remedy:   only the owner writes; others call its API or send it commands (chapter 12)
HIGH   Shared domain library          foodfleet-domain
       evidence: business classes shared by 10 services: address-book-service,cart-service,coupon-service,customer-profile-service,kitchen-ticket-service,menu-item-pricing-service,menu-service,order-line-service,order-service,payment-service
       remedy:   each service owns its model; share contracts (OpenAPI, event schemas), not classes
MEDIUM Deep synchronous call chain    coupon-service->customer-profile-service->order-service->menu-service->menu-item-pricing-service->zone-service
       evidence: 5 synchronous hops; at 99.90% each the chain is up 99.40% of the time
       remedy:   shorten: replicate data, or make downstream steps asynchronous
MEDIUM Chatty communication (N+1)     menu-service->menu-item-pricing-service
       evidence: 10 synchronous calls per request
       remedy:   batch endpoint (GET /items?ids=...), or keep a local copy via events (chapter 19)
MEDIUM Wide synchronous fan-out       order-service
       evidence: 9 synchronous dependencies per request; all up at once: 99.10%
       remedy:   compose in parallel with timeouts and fallbacks (chapter 18), or replicate data
MEDIUM Shared database                menu_items
       evidence: owned by menu-service, read directly by menu-item-pricing-service
       remedy:   publish events or expose a read API; readers keep their own copy (chapters 12, 19)
MEDIUM Shared database                orders
       evidence: owned by order-service, read directly by coupon-service,customer-profile-service
       remedy:   publish events or expose a read API; readers keep their own copy (chapters 12, 19)
MEDIUM Shared-library version skew    foodfleet-domain
       evidence: 6 versions in use (2.7.0, 2.8.5, 3.1.0, 3.2.0, 3.3.1, 3.4.0) across 10 services
       remedy:   keep shared libraries small and stable; automate upgrades (Renovate/Dependabot); a BOM
LOW    Nano-service                   address-book-service
       evidence: 2 endpoints, 1,900 lines; released with customer-profile-service most of the time
       remedy:   merge into customer-profile-service as a module (chapter 1); split again only for a real reason
LOW    Nano-service                   menu-item-pricing-service
       evidence: 2 endpoints, 2,600 lines; only called by menu-service
       remedy:   merge into menu-service as a module (chapter 1); split again only for a real reason
LOW    Nano-service                   order-line-service
       evidence: 2 endpoints, 1,400 lines; only called by order-service
       remedy:   merge into order-service as a module (chapter 1); split again only for a real reason

== foodfleet-2024-capabilities: 9 services, 1 findings {MEDIUM=1} ==
MEDIUM Shared database                customers
       evidence: owned by customer, read directly by promotions
       remedy:   publish events or expose a read API; readers keep their own copy (chapters 12, 19)

```

```text
A page needs N menu items from another service; 100,000 page loads per row.
Each call: median 4 ms, p99 ~30 ms, fails 0.1% of the time.

strategy            N     p50 ms     p99 ms pages failed  calls/page
SEQUENTIAL         12       66.6      136.6        1.20%          12
PARALLEL           12       15.9       60.7        1.20%          12
BATCH              12        5.2       39.4        0.11%           1
LOCAL_REPLICA      12        0.1        0.1        0.00%           0
SEQUENTIAL         40      229.2      341.7        3.92%          40
PARALLEL           40       25.0       80.2        3.92%          40
BATCH              40        8.0       60.6        0.11%           1
LOCAL_REPLICA      40        0.1        0.1        0.00%           0
```
