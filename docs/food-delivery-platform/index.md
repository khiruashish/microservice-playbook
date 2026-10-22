---
title: "FoodFleet Overview"
description: "FoodFleet, the fictional food-delivery platform every chapter builds, in one place: what the business does, its 8 teams and 23 deployables, what the 77 ADRs decided, where this design departs from the usual reference architecture and why, and one machine-readable model (foodfleet.yaml) that these pages are generated from and that check.py validates against the chapters."
progress: done
---

# FoodFleet Overview

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

FoodFleet is a fictional food-delivery company in India. Customers order from restaurants in an app, restaurants accept and cook the orders, and riders collect and deliver them. Every chapter of this handbook solved one of FoodFleet's problems and recorded the answer as an ADR. This section puts those 77 decisions together into one platform.

## How these pages are made

The platform is described once, in a machine-readable model: `code/foodfleet/model/foodfleet.yaml` in the repository. Every service, call, topic and team in it names its source: an ADR or a chapter. The tables and diagrams on these pages are generated from it, and `check.py` validates it:

```bash
python3 code/foodfleet/model/check.py
```

Where no chapter decided something (who owns the customer BFF, say), the model fills the gap so the picture is complete and marks the fact **assumed**. The pages show those facts with an asterisk or "(assumed)".


## The business

| Who | What they do | Through |
|---|---|---|
| **Customers** | Browse menus near them, order, pay by UPI or card, track the rider | The iOS, Android and web apps |
| **Restaurant partners** | Keep menus and prices current, accept or reject orders, set prep times | The partner portal, partner webhooks |
| **Riders** | Accept deliveries, send their position, get paid from a wallet | The rider app |
| **FoodFleet staff** | Support, operations, finance and growth | Internal tools, reports |

The chapters describe FoodFleet at the scale where microservices start to pay off:

- **2,000 restaurants and 50,000 riders**, when one PostgreSQL database stopped fitting three workloads ([chapter 29](../data/ch29-polyglot-persistence.md)).
- **About 12,500 position writes a second** at peak, one ping per rider every 4 seconds ([chapter 1](../foundations/ch01-architecture-styles.md)).
- **Lunch and dinner peaks**, which the autoscaling minimums are raised for on a schedule ([ADR-056](../deployment/ch56-autoscaling.md)).
- **Restaurant chains as tenants**, each with a home region for personal data ([ADR-075](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)).

Money is always in whole paise, payments follow India's UPI and card rules, and personal data follows the DPDP Act.

## How FoodFleet got here

FoodFleet did not start with microservices. [ADR-001](../foundations/ch01-architecture-styles.md) started it as a **modular monolith** with seams for extraction, and services left it in the order the measurements justified:

1. **The modular monolith**: modules `order`, `restaurant`, `kitchen`, `delivery`, `payment` and `notification`, verified in CI ([chapter 1](../foundations/ch01-architecture-styles.md)).
2. **The platform first**: Kafka, OpenTelemetry, per-service pipelines and Argo CD, before the first extraction.
3. **`payment-service`**, for its PCI-DSS scope and separate release governance.
4. **`tracking-gateway`**, for its load shape: thousands of small writes a second.
5. **`kitchen-service`**, when a restaurant-partner team was formed to own it.

Pricing followed as **`pricing-service`**, by strangler fig with shadowing and cohort canaries ([ADR-016](../architecture-patterns/ch16-strangler-fig-migration.md)).

[Chapter 74](../governance-org/ch74-maturity-migration-roadmap.md) turned that sequence into a rule: extract the context that frees the most independent changes per remote dependency added, and stop when the next extraction buys little.

## Teams

FoodFleet organises around streams of change ([ADR-007](../foundations/ch07-conways-law-team-topologies.md)): four stream-aligned teams, two complicated-subsystem teams, a platform team and an enabling team, 49 people in all.

--8<-- "code/foodfleet/model/generated/teams.md"

The enabling team owns nothing on purpose: it coaches the other teams on SLOs and incident practice and moves on.

## What's in the platform

--8<-- "code/foodfleet/model/generated/summary.md"

The [architecture](architecture.md) page draws them, and the [service catalog](services.md) lists each one with its owner, purpose, data and the ADRs behind it.

## Where FoodFleet departs from the usual reference design

A typical Spring microservices reference architecture uses a set of well-known parts. FoodFleet's chapters measured the alternatives and chose differently in several places:

| Usual choice | FoodFleet's choice | Why | Decision |
|---|---|---|---|
| Eureka or Consul for discovery | Kubernetes Services and DNS | The platform already does it; a registry is one more thing to keep available | [ADR-011](../architecture-patterns/ch11-service-discovery-patterns.md) |
| HashiCorp Vault | OpenBao | An open-source fork under the Linux Foundation, the same API, dynamic database credentials | [ADR-039](../security/ch39-secrets-management.md) |
| Elasticsearch for search | PostgreSQL full-text search with a trigram fallback | Fast enough on FoodFleet's menu sizes, and no second copy to keep in sync | [ADR-029](../data/ch29-polyglot-persistence.md) |
| A separate store per need (MongoDB, Cassandra…) | PostgreSQL by default, Redis for copies and ephemeral state | Nothing else without a measurement that PostgreSQL can't do it | [ADR-029](../data/ch29-polyglot-persistence.md) |
| Choreographed events for checkout | An orchestrated saga, the kitchen's acceptance as the pivot | One place to see and compensate a failed order | [ADR-014](../architecture-patterns/ch14-saga-pattern.md) |
| Sidecar mesh | Istio in ambient mode | mTLS and L4 policy without a proxy per pod; waypoints only where L7 is needed | [ADR-057](../deployment/ch57-service-mesh.md) |
| Hystrix, Ribbon, Zuul, Sleuth | Resilience4j, Spring Cloud Gateway, Micrometer Tracing and OpenTelemetry | The old ones are in maintenance or retired | [ADR-043](../resilience/ch43-resilience4j-deep-dive.md), [ADR-010](../architecture-patterns/ch10-api-gateway-bff.md), [ADR-047](../observability/ch47-observability-fundamentals.md) |

### Where the chapters disagree with each other

Seventy-seven decisions made one at a time don't all line up. The model resolves these, and says how:

- **Topic names.** [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md) names state topics `foodfleet.<context>.<entity>`; [chapter 25](../communication/ch25-asynchronous-messaging.md)'s event topic is just `order-events`. The model keeps both as the chapters wrote them; [Kafka topic design](kafka-design.md) proposes one convention.
- **How payment learns of an order.** [Chapter 5](../foundations/ch05-domain-driven-design.md)'s context map has Payment react to `OrderPlaced`; [ADR-014](../architecture-patterns/ch14-saga-pattern.md) later made it a saga participant that receives `payment.authorize` commands. The model follows the later decision.
- **Service names.** Early chapters say `ordering-service`, `catalog-service` and `tracking-service`; later ones say `order-service`, `menu-service` and `tracking-gateway`. The model uses the later names and keeps the earlier ones as aliases, and `check.py` fails if two services claim the same name.
- **ADR dates.** [Chapter 73](../governance-org/ch73-adrs-fitness-functions.md) linted the handbook's own decision log and reported the out-of-order dates it found, rather than editing accepted ADRs.

## What the model says about availability

`check.py` follows each journey's synchronous calls and separates the services a journey can't work without from the ones it can do without (the call declares a fallback, as [ADR-002](../foundations/ch02-characteristics-principles.md) requires):

--8<-- "code/foodfleet/model/generated/journeys.md"

Placing an order needs four services in a row: the edge gateway, the customer BFF, order-service and pricing-service. If each is up 99.9% of the time and they fail independently, the journey is up about 99.6%, below its 99.9% SLO ([chapter 51](../observability/ch51-slo-alerting.md)). Each would need about 99.975%. Browsing has the same shape, four required services against a 99.9% SLO; tracking, with one required service against 99.5%, is the only journey a 99.9% service meets comfortably. That's why the chapters keep the critical path short:

- The coupon is optional: an order goes ahead without it if promotions-service is down.
- Payment is asynchronous, a saga step after the order is placed, so the provider's outages don't fail checkout.
- Reference data, such as zones and menus, is copied by event-carried state transfer instead of being called.

Optional calls cost almost nothing: making every call on the browse path required would take it from 99.6% to 99.2%.

## Running it

FoodFleet is a design assembled from the chapters, not one deployable system. Each piece runs where its chapter built it:

| To run | Go to |
|---|---|
| A FoodFleet service in 15 minutes, with only a JDK | [Your first microservice](../getting-started/first-microservice.md) |
| The model's checks and the generated tables | `python3 code/foodfleet/model/check.py` |
| A chapter's part of the platform, with its measurements | That chapter's `verify/run.sh` ([environment setup](../getting-started/environment-setup.md) lists what each needs) |
| The labs that build services step by step | The [Code Lab](../code-lab/index.md) |
| A local stack of the main services | [Docker & local stack](docker-local-stack.md) |

## The pages in this section

| Page | What it covers |
|---|---|
| [Architecture](architecture.md) | Context, containers, the context map and the deployment, all from the model |
| [Order lifecycle & sagas](order-lifecycle.md) | From cart to delivered, and every way it can fail |
| [Service-to-service communication](communication-map.md) | Every call and its protocol, timeout and fallback |
| [Kafka topic design](kafka-design.md) | Topics, keys, partitions, schemas and retention |
| [API gateway & BFF](api-gateway.md) | The edge and the three BFFs |
| [Security](security.md) | Identity, tokens, workload identity, secrets and the supply chain |
| [Data & caching](data-and-caching.md) | Who owns which tables, and every cache |
| [Resilience](resilience.md) | Deadlines, limits, breakers and fallbacks per journey |
| [Observability & SLOs](observability.md) | Signals, dashboards and the SLOs |
| [Service catalog](services.md) | Every service, generated from the model |
| [Docker & local stack](docker-local-stack.md) | Running the core services on one machine |
| [Kubernetes & service mesh](kubernetes.md) | Manifests, probes, autoscaling and the mesh |
| [CI/CD & GitOps](cicd.md) | From a commit to a canary |
| [Testing strategy](testing.md) | What each kind of test covers, per service |
| [Architecture decision records](adrs.md) | All 77 ADRs in one log |
| [Runbooks](runbooks.md) | What to do when an alert fires |
| [Scaling & cost](scaling-and-cost.md) | Capacity, autoscaling and cost per 1,000 orders |
| [Interview: design Swiggy/Zomato](system-design-interview.md) | The system design interview, answered with this platform |
