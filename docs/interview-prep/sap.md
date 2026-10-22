---
title: "SAP"
description: "Preparing for enterprise-software rounds at SAP and similar vendors: many customers on one platform (tenancy models, isolation, fairness, residency and erasure), products customers extend without forking (ports and adapters, contracts, versioned APIs and events), and integration with systems you don't control (anti-corruption layers, events, migration), each with numbers from the chapters; plus the 185 questions tagged SAP, Oracle or Red Hat."
progress: done
---

# SAP

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Enterprise software vendors build products that thousands of customers run, extend and connect to their own systems. Their architecture rounds keep coming back to three questions: **how do many customers share one platform safely**, **how do customers extend the product without forking it**, and **how does it integrate with systems nobody controls**. This page prepares those three; the same preparation fits Oracle, Red Hat and other platform vendors, which the tags include. Interview formats change; check the current process with your recruiter.

## 1. Multi-tenancy

Start from the three models, and say what decides between them ([ch. 75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md)):

| Model | Isolation | Cost per tenant | Choose it for |
|---|---|---|---|
| **Pool**: shared tables with a tenant column | By policy | Lowest | Many small tenants |
| **Bridge**: a schema per tenant | By schema | Middle | Tenants that need their own schema changes |
| **Silo**: a database or cell per tenant | Physical | Highest | Regulated or very large tenants |

Then the parts interviewers probe, with chapter 75's measurements:

- **Isolation that doesn't depend on every query.** Forced row-level security with the tenant set per transaction. A tenant set per session leaked into 177 of 193 requests that forgot to set it; per transaction, none.
- **Fairness.** One tenant's load mustn't starve the others: per-tenant bulkheads kept others' p99 at 8–11 ms instead of 25–38 ms; per-tenant rate limits kept them at 100% of their requests instead of 0.2% under a global limit ([ch. 40](../security/ch40-api-security.md)).
- **Residency.** A home region per tenant that holds all its personal data; only aggregates leave.
- **Erasure.** DELETE left the data in the WAL and the backups; crypto-shredding (a key per customer, deleted on erasure) left only unreadable ciphertext.
- **Blast radius.** Cells and shuffle sharding: one poison tenant reached 12.7% of tenants with 8 cells and 1.7–3.6% with shuffle shards, against 100% in one pool ([ch. 46](../resilience/ch46-disaster-recovery.md)).

## 2. Extensibility without forks

Customers want to change behaviour; the vendor wants one codebase. The answers:

- **Ports and adapters.** The core defines interfaces; extensions are adapters. A new payment provider is an adapter, not a change to the domain ([ch. 9](../architecture-patterns/ch09-hexagonal-clean-architecture.md), [ADR-009](../architecture-patterns/ch09-hexagonal-clean-architecture.md)).
- **Contracts as published documents.** OpenAPI for HTTP, AsyncAPI for events, checked for breaking changes in CI; consumers are tolerant readers ([ch. 27](../communication/ch27-api-contracts.md)). Know which changes break: a new enum value breaks strict readers; a new optional field shouldn't.
- **Versioning and deprecation with evidence.** Deprecation and Sunset headers, and removal only when the access log shows no callers: of four remaining callers of one deprecated operation, two were in no catalog ([ch. 71](../governance-org/ch71-api-governance.md)).
- **Events as extension points.** Customers subscribe to business events rather than calling in; events carry versioned schemas checked for compatibility ([ch. 19](../architecture-patterns/ch19-event-driven-architecture.md)).
- **Governance that scales.** Lint rules, a ratchet for legacy APIs, a catalog: Spectral caught 10 of 33 planted API problems, house rules 16 more, and 7 needed a reviewer ([ch. 71](../governance-org/ch71-api-governance.md)).

## 3. Integration with systems you don't control

- **Anti-corruption layers.** Translate the other system's model at the boundary so it can't leak into yours ([ch. 5](../foundations/ch05-domain-driven-design.md), [ch. 16](../architecture-patterns/ch16-strangler-fig-migration.md)).
- **Adapters and ambassadors** for legacy ERP and warehouse systems ([ch. 17](../architecture-patterns/ch17-sidecar-ambassador-adapter.md#e-commerce-analogue-legacy-erp-and-warehouse-systems)).
- **Asynchronous by default.** The other system's outage shouldn't be yours: outbox out, inbox in, dead letters with replay ([ch. 15](../architecture-patterns/ch15-outbox-inbox-cdc.md), [ch. 25](../communication/ch25-asynchronous-messaging.md)).
- **Migration by strangler fig, not big bang.** A rewrite "chased a moving target" for eight months; shadow traffic found three bugs and one undocumented behaviour in two weeks ([ch. 16](../architecture-patterns/ch16-strangler-fig-migration.md)). And measure before you plan: chapter 74's analysis of a real monolith found 9 contexts in one dependency cycle ([ch. 74](../governance-org/ch74-maturity-migration-roadmap.md)).

## The questions

The 185 chapter questions tagged SAP, Oracle or Red Hat. Tags are the chapters' judgment of where a question fits, not reports of real interviews.

--8<-- "tools/generated/interview/sap.md"
