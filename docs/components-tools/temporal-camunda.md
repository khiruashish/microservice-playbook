---
title: "Temporal & Camunda"
description: "Workflow engines at FoodFleet (ADR-014): place-order runs on a hand-written orchestrator with a saga log, because it is one short, latency-sensitive flow; long-running flows with timers and people (refunds with manual review, restaurant onboarding) use a workflow engine. What chapter 14's 10,000-saga simulation showed any engine still needs (idempotent participants: one non-idempotent payment service caused 165 double charges, ₹82,500), and Temporal's durable execution against Camunda's BPMN, Step Functions and the rest."
progress: done
---

# Temporal & Camunda

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Engines that run long-lived business processes durably. **Temporal** (from Uber's Cadence): workflows as ordinary code whose state is persisted as an event history and replayed; activities with retries and timeouts; timers that last months. **Camunda** (Zeebe in version 8) and Flowable: processes modelled in **BPMN**, with human tasks and business-visible state |
| **FoodFleet uses** | A workflow engine for refunds with manual review and restaurant onboarding; a hand-written orchestrator for place-order |
| **Decided in** | [ADR-014](../architecture-patterns/ch14-saga-pattern.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 14](../architecture-patterns/ch14-saga-pattern.md), [lab 10](../code-lab/lab-10-saga.md) |
| **Related** | [Outbox](outbox-implementation.md), [Idempotency store](idempotency-store.md), [Kafka](kafka.md), [Distributed scheduling](distributed-scheduling.md) |

## Build or use an engine

From [chapter 14](../architecture-patterns/ch14-saga-pattern.md):

| Option | Strengths | Watch out for |
|---|---|---|
| Hand-written orchestrator + saga log | Small, transparent, no new infrastructure | You own retries, timers, versioning, visibility |
| **Temporal / Cadence** | Durable execution: workflow code as plain functions, automatic state, timers, retries | New platform to run (or buy); deterministic-code rules |
| **Camunda / Zeebe, Flowable** | BPMN models, human tasks, business visibility | Modelling discipline; engine operations |
| AWS Step Functions, Azure Durable Functions | Managed, integrated with cloud services | Vendor lock-in; pricing per transition |
| Axon, Eventuate Tram | Saga support in event-driven Java frameworks | Framework commitment |

FoodFleet's split: **place-order** (one flow, latency-sensitive) on its own orchestrator: create order (PENDING) → reserve coupon → authorize payment → **kitchen accepts (pivot)** → capture → request rider → approve. **Refunds with manual review** and **restaurant onboarding**, which wait on people for hours or days and branch a lot, on an engine.

## What an engine doesn't do for you

Chapter 14 ran 10,000 place-order sagas with business rejections, transient failures, lost replies and orchestrator crashes:

| | Idempotent participants | payment-service **not** idempotent |
|---|---:|---:|
| Orchestrator crashes resumed from the log | 351 | 348 |
| Stuck after the first pass, then retried | 41 → 0 | 43 → 0 |
| Money held beyond completed orders | **₹0** | **₹82,500 (165 double charges)** |

An engine replaces the saga log and the retry loop; it re-runs activities after crashes exactly as the orchestrator did. Participants must still be idempotent per (workflow, step), with keys passed to providers ([Idempotency store](idempotency-store.md)). [Lab 10](../code-lab/lab-10-saga.md) shows the choreographed alternative losing track: with a reply lost, an order stayed PENDING with ₹450 held.

## Temporal or Camunda

| | Temporal | Camunda 8 (Zeebe) |
|---|---|---|
| Model | Code (Java SDK, others); replay requires deterministic workflow code | BPMN diagrams, executed by the engine; job workers in any language |
| People in the loop | Signals and queries from your UI | User tasks, forms, Tasklist |
| Visibility | Event history per workflow; web UI | Operate: process instances on the diagram |
| Versioning | `Workflow.getVersion`, worker versioning | Process versions; migrate running instances |
| Fits | Engineers owning complex, long-running logic | Processes business people read and change |

## Pitfalls

- **Non-determinism in Temporal workflow code**: time, random, I/O outside activities.
- **Activities without idempotency**.
- **Every flow on the engine**: the short, hot saga pays latency and a dependency for nothing.
- **No compensation design**: an engine runs what you model; steps after the pivot still need retry-until-success and a STUCK view.
- **Changing a running workflow's code without versioning**.
