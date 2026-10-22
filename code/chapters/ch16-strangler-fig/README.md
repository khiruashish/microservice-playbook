# Chapter 16 — Strangler Fig, ACL & Monolith Migration

FoodFleet moves **pricing** out of its monolith without a rewrite-and-switch weekend: the legacy code
stays in charge while pricing-service is shadowed against it, then takes traffic cohort by cohort,
with an automated guard and a one-line rollback.

| Package | What it is |
|---|---|
| `legacy/` | The monolith's pricing (rupees as `double`, IST strings, one-letter tiers) and its ORDERS row |
| `acl/` | Anti-corruption layer: legacy order rows to the new domain model, or to quarantine with a reason |
| `pricing/` | The `PricingService` seam, `LegacyPricingAdapter`, and `NewPricing` releases V1–V3 |
| `strangler/` | `StranglerFacade` (legacy / shadow / canary / new, optional fallback), sticky `Cohorts`, `ShadowComparator` |
| `branch/` | Branch by abstraction inside the monolith; `LegacyIsolationTest` (ArchUnit) keeps it honest |
| `rollout/` | Shadow traffic per release; big bang vs canary vs canary with fallback, during an incident |
| `gateway/` | The same routing at the edge: Spring Cloud Gateway Server Web MVC routes and a cohort predicate |

## Run

```bash
cd code
mvn -B -pl chapters/ch16-strangler-fig -am verify
java -cp chapters/ch16-strangler-fig/target/classes dev.foodfleet.ch16.rollout.RolloutSimulation
```

## Verified output

```text
A. Shadow traffic: 50,000 requests per release; customers always get the monolith's price

release     match  <=1 paise   mismatch    failed  mismatches by differing field
V1          29812          0      20188         0  {deliveryFee=3381, deliveryFee+surcharge=1678, surcharge=15129}
V2          46501       3499          0         0  {}
V3          49389        611          0         0  {}

B. Rolling out V3 while Whitefield (3% of traffic) is missing from its zone data
   1,000 requests/minute for 60 minutes; guard: >1% failures over 5 min with >=200 requests

strategy                                           rolled back  new-path  errors shown  customers fallbacks
big bang, no guard (on-call rolls back at 20 min)    minute 20     20000           592        583         0
big bang: 100% at once                                minute 1      1000            21         21         0
canary 1-5-25-50-100%, 10 min per step               minute 14       309             8          8         0
canary + fall back to monolith on error              minute 14       309             0          0         8
```

- **V1** priced 40% of baskets differently: late-night surcharge judged in UTC, gold members charged
  delivery, surcharge rounded down. **V2** fixed the first two. **V3** rounds half-up and still differs
  by 1 paisa on 1.2% of baskets, because the monolith's `double` arithmetic doesn't round half-up
  consistently (`224.5 * 0.15 * 100 = 3367.4999999999995`). The team accepted that difference
  explicitly (ADR-016) rather than reproduce the float behaviour.
- In the rollout, the 1% step never collected the 200 requests the guard needs; it proved nothing.
  The canary's real value came at 5%.
