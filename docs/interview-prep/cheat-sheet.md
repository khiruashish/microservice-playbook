---
title: "Last-Night Cheat Sheet"
description: "The whole handbook on a few printable pages: ten rules that come up in every round, the numbers worth quoting, and every part's 'if your problem is…, reach for…' table with what the chapter measured, generated from the part overviews (Part 1 from its ADRs)."
progress: done
---

# Last-Night Cheat Sheet

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Read this the night before, not instead of the chapters. It's built to print: use your browser's print, and the navigation drops away.

## Ten rules that come up in every round

1. **Start with a modular monolith**; extract a service when a team or a load shape needs it, measured ([ch. 1](../foundations/ch01-architecture-styles.md), [ch. 74](../governance-org/ch74-maturity-migration-roadmap.md)).
2. **One owner per table.** No service reads another's database ([ch. 12](../architecture-patterns/ch12-database-per-service.md)).
3. **Every remote call has a deadline, a bulkhead and a declared fallback**; retry at one layer only ([ch. 2](../foundations/ch02-characteristics-principles.md), [ch. 42](../resilience/ch42-failure-modes.md)).
4. **No distributed transactions.** Sagas with idempotent participants; a pivot after which you only go forward ([ch. 14](../architecture-patterns/ch14-saga-pattern.md)).
5. **Publish through an outbox, consume through an inbox** ([ch. 15](../architecture-patterns/ch15-outbox-inbox-cdc.md)).
6. **Keys for side effects, versions for state**: idempotency keys on writes, `If-Match` on updates ([ch. 28](../communication/ch28-idempotency-correlation.md)).
7. **Money is never eventually consistent**; views may be ([ch. 3](../foundations/ch03-distributed-systems-fundamentals.md)).
8. **SLOs per user journey, alerts on burn rate**, not on static thresholds ([ch. 51](../observability/ch51-slo-alerting.md)).
9. **Liveness never checks dependencies** ([ch. 52](../observability/ch52-health-checks-actuator.md)).
10. **Measure with open models**; closed-model load tests hide queueing ([ch. 65](../testing/ch65-performance-chaos-arch-tests.md)).

## Numbers worth quoting

| Claim | Number | Chapter |
|---|---|---|
| Availability multiplies along a chain | 4 services at 99.9% → 99.6%; each needs 99.975% for a 99.9% journey | [FoodFleet](../food-delivery-platform/index.md#what-the-model-says-about-availability) |
| Retries at every layer amplify | 3 layers sent 27 calls per request to a dead dependency; a budget, 1.4 | [42](../resilience/ch42-failure-modes.md) |
| Idempotency matters | 165 double charges in 10,000 sagas without it, 0 with | [14](../architecture-patterns/ch14-saga-pattern.md) |
| Dual writes are unsafe | ~1.6% of events lost or invented; outbox + inbox, 0 | [15](../architecture-patterns/ch15-outbox-inbox-cdc.md) |
| Keyset beats offset | 9,664× faster at the last page of 2 M rows | [21](../communication/ch21-rest-api-design.md) |
| Averaged percentiles lie | Averaged p99 137 ms, true 322 ms | [48](../observability/ch48-metrics-micrometer.md) |
| Static alerts miss slow burns | A 1% threshold missed failures that spent up to 43% of the budget | [51](../observability/ch51-slo-alerting.md) |
| Canaries limit damage | 7–10 failed requests, against 944–953 for a rolling update | [58](../deployment/ch58-cicd-gitops.md) |
| Closed load models hide queues | p99 15 ms reported, 942 ms real | [65](../testing/ch65-performance-chaos-arch-tests.md) |
| Requests are mostly idle | Services used 3–10% of requested CPU; right-sizing 97 → 30 CPUs | [70](../performance-scalability/ch70-finops.md) |
| Tenant isolation must not depend on each query | Per-session tenant leaked in 177 of 193 forgotten requests; per transaction, none | [75](../governance-org/ch75-multi-tenancy-multi-region-compliance.md) |

## Every part, on one table each

Generated from each part's overview by `tools/interview.py`. Part 1 has no such table, so its rows are its ADRs.

--8<-- "tools/generated/interview/cheat-sheet.md"
