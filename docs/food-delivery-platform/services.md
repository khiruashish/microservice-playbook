---
title: "Service Catalog"
description: "Every FoodFleet deployable, generated from the platform model: kind, owner, purpose, who calls it and what it calls with each fallback, the topics it publishes and consumes, what it stores, the ADRs behind it, and which chapters' code builds a version of it."
progress: done
---

# Service Catalog

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

FoodFleet has 23 deployables: 2 at the edge, 3 backends for frontends, 15 services, 1 shared AI gateway and 2 functions. Ten platform components support them. Everything on this page is generated from `code/foodfleet/model/foodfleet.yaml` by `check.py` ([how](index.md#how-these-pages-are-made)). An owner marked "assumed" is one no chapter assigned.

In a real organisation this page would be the developer portal's catalog, generated from each repository's `catalog-info.yaml` and checked in CI ([ADR-071](../governance-org/ch71-api-governance.md)). The model plays that role here.

## At a glance

--8<-- "code/foodfleet/model/generated/services.md"

\* Owner assumed: no chapter assigned it.

## Who owns what

Each team's services follow its stream of change ([ADR-007](../foundations/ch07-conways-law-team-topologies.md)). Every service has exactly one owning team; no team owns more than six deployables, apart from the platform team, whose components serve everyone.

--8<-- "code/foodfleet/model/generated/teams.md"

## Each service

Each card lists the service's connections in the model: its synchronous calls in both directions, with what the caller does if the call fails; its topics and saga command channels; and its data.

"Code in" lists the chapters whose code names the service (or one of its aliases), with how often. Each chapter builds the part of a service it needs, not the whole service, so a service's code is spread across those chapters. Start with the chapter at the top of its list.

--8<-- "code/foodfleet/model/generated/service-cards.md"

## Adding a service

A new FoodFleet service starts from the golden path in the portal ([ADR-072](../governance-org/ch72-platform-engineering.md)), which gives it the platform's defaults: probes, identity, telemetry, the outbox, the pipeline. Before it's built, the model should answer the questions this page shows for every other service:

1. **Which capability, and which team?** A service that no stream-aligned team can own on its own is probably a module of an existing service ([ADR-006](../foundations/ch06-service-decomposition.md)).
2. **Which data does it own?** Only its own tables, in its own schema ([ADR-012](../architecture-patterns/ch12-database-per-service.md)).
3. **Who calls it, and what do they do when it's down?** Every synchronous call declares a fallback ([ADR-002](../foundations/ch02-characteristics-principles.md)). If the honest answer is "the journey fails", the service joins that journey's critical path, and each service on it needs to be more available than the journey's SLO.
4. **What does it publish?** Through the outbox, on a topic with a schema ([ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md), [ADR-019](../architecture-patterns/ch19-event-driven-architecture.md)).

Then the service goes into the model, and `check.py` confirms the rest:

- its team exists;
- its name doesn't collide with another service's name or alias;
- its calls and topics connect services that exist;
- it adds no cycle to the synchronous calls;
- what it does to each journey's availability.
