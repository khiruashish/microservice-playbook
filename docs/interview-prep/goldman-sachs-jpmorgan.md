---
title: "Goldman Sachs & JP Morgan"
description: "Preparing for bank and payments rounds: the three things they probe (consistency where money moves, an audit trail you can prove, and payments that survive retries and outages), worked as a ledger design answer with numbers from the chapters; the 77 fintech analogues the chapters end with; and the 278 questions tagged Goldman Sachs, JP Morgan, banks, PayPal, Razorpay, PhonePe or Paytm."
progress: done
---

# Goldman Sachs & JP Morgan

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Banks and payment companies ask the same distributed-systems questions as everyone else, with one difference: **a wrong answer moves money**. So their rounds go deeper on three things: consistency where money changes hands, an audit trail you can prove to a regulator, and payments that stay correct through retries, crashes and outages. This page works through those as a design answer, then lists the questions. The same preparation suits Indian payments companies, which the tags include. Interview formats change; check the current process with your recruiter.

## The three things they probe

### 1. Consistency: where it's non-negotiable, and where it isn't

The answer they want is **not** "make everything strongly consistent". It's a map:

| Data | Model | Why |
|---|---|---|
| Ledger postings, balances | Linearizable within the owning service; conditional writes | A lost update is lost money ([ADR-003](../foundations/ch03-distributed-systems-fundamentals.md)) |
| A transfer across services or banks | A saga of local transactions, with compensations | No shared transaction exists between systems ([ch. 30](../data/ch30-distributed-transactions.md#fintech-analogue-funds-transfer-between-banks)) |
| Statements, dashboards | Eventually consistent views, labelled with their as-of time | Views lag; the ledger doesn't ([ch. 34](../data/ch34-reporting-analytics.md)) |
| A customer re-reading their own payment | Read-your-writes, via consistency tokens | 15% of re-reads from a lagging replica were stale; with tokens, 0 ([ch. 31](../data/ch31-data-consistency.md)) |

Know why two-phase commit isn't the answer between services: it holds locks across systems, and one participant's outage blocks everyone ([ch. 30](../data/ch30-distributed-transactions.md)).

### 2. Auditability: being able to prove what happened

- **Append-only journals, balances derived.** Corrections are reversing entries, never updates. That's event sourcing, which ledgers have done for decades ([ch. 13](../architecture-patterns/ch13-cqrs-event-sourcing.md#fintech-analogue-the-ledger-was-always-event-sourced)). FoodFleet's rider wallets work this way: disputes that took days now take minutes, because any balance at any time can be explained event by event ([ADR-013](../architecture-patterns/ch13-cqrs-event-sourcing.md)).
- **One trace and three ids on every message**: event id, correlation id, causation id ([ADR-028](../communication/ch28-idempotency-correlation.md)).
- **Logs that can be shown to an auditor**: structured, correlated by trace id, and never containing card or contact data. Source-only masking leaked from message text, the MDC and exception messages in chapter 50 ([ch. 50](../observability/ch50-structured-logging.md)).
- **Evidence for the supply chain**: SBOMs per artifact, signatures and provenance enforced at admission, compliance rules as policy ([ch. 41](../security/ch41-supply-chain-compliance.md#fintech-analogue-regulated-vendors-and-evidence)).
- **Decisions on record**: ADRs never edited after acceptance ([ch. 73](../governance-org/ch73-adrs-fitness-functions.md)).

### 3. Payments that survive retries and outages

- **Idempotency end to end.** An idempotency key from the client, stored with the payment in the same transaction, passed to the provider; repeats return the stored outcome. In chapter 14's 10,000 simulated sagas, one non-idempotent payment service double-charged 165 customers, ₹82,500; idempotent, ₹0 ([ch. 14](../architecture-patterns/ch14-saga-pattern.md)). Banks do the same with end-to-end references such as ISO 20022's `UETR` ([ch. 28](../communication/ch28-idempotency-correlation.md#fintech-analogue-payment-instructions)).
- **Authorize before, capture after the point of no return**, so nobody pays for a service that wasn't delivered.
- **Unknown outcomes are normal.** A timeout doesn't mean failure: query the status by reference, and reconcile daily against the provider's statement.
- **Zero loss across sites, at a price.** Synchronous replication within a metro (in chapter 46, 1.3–1.7 ms per commit instead of 0.9–1.0 ms), asynchronous to a far site, and regional failover only after fencing, by a human decision ([ch. 46](../resilience/ch46-disaster-recovery.md#fintech-analogue-payments-with-zero-loss-across-sites)).
- **Calls to providers through one controlled place**: FoodFleet's payment ambassador, 400 ms per try, retries only for idempotent requests, a breaker ([ADR-017](../architecture-patterns/ch17-sidecar-ambassador-adapter.md)).

## A design answer: a payments ledger

If asked to "design a ledger" or "design a payment service", the structure that covers all three:

1. **Accounts and postings.** A posting has two or more entries that sum to zero (double entry), an instruction id with a unique constraint, and a timestamp that's never used for ordering. Balances are projections ([ch. 3](../foundations/ch03-distributed-systems-fundamentals.md#fintech-analogue-the-double-entry-ledger)).
2. **One owner.** Only the ledger service writes postings, in its own database; others send instructions ([ADR-012](../architecture-patterns/ch12-database-per-service.md)).
3. **Instructions in, events out**, through an outbox, so a posting and its event commit together ([ch. 15](../architecture-patterns/ch15-outbox-inbox-cdc.md)).
4. **Reconciliation**, daily, matching provider statements to postings by reference, as the backstop for everything above ([ch. 34](../data/ch34-reporting-analytics.md)).
5. **Recovery tiers.** The ledger is Tier 0: no acknowledged posting lost to a zone failure ([ADR-046](../resilience/ch46-disaster-recovery.md)).

## The fintech analogues

Every chapter ends its case study with how finance applies the same idea. All 77, for revision:

--8<-- "tools/generated/interview/analogues-fintech.md"

## The questions

The 278 chapter questions tagged Goldman Sachs, JP Morgan, banks, PayPal, Razorpay, PhonePe or Paytm. Tags are the chapters' judgment of where a question fits, not reports of real interviews.

--8<-- "tools/generated/interview/goldman-sachs-jpmorgan.md"
