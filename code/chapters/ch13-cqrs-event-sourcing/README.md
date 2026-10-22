# Chapter 13 code — CQRS and event sourcing: FoodFleet rider wallet

Docs: `docs/architecture-patterns/ch13-cqrs-event-sourcing.md`

| Package | What it shows |
|---|---|
| `eventstore/` | Append-only store with per-stream optimistic concurrency and a global order; in-memory and JDBC implementations sharing one contract test; upcasters |
| `wallet/` | `RiderWallet`, an event-sourced aggregate (decide / evolve); codec with a v1→v2 upcaster; repository with snapshots and "as of" queries; service that retries on conflicts |
| `projection/` | Read models (daily earnings in IST, pending payouts) fed by a runner with checkpoints, lag and rebuild |

```bash
cd code
mvn -B -pl chapters/ch13-cqrs-event-sourcing -am verify
java -cp chapters/ch13-cqrs-event-sourcing/target/classes dev.foodfleet.ch13.WalletDemo
```

Output (verified):

```text
Events in stream: 18
Balance ₹585.00, available ₹85.00, payout pending po-1
Load with snapshots every 10 events reads 8 events, not 18
As of 9:00 pm IST the balance was ₹400.00
Meena's 2023 balance, from a v1 event in rupees: ₹38.50
Projection daily-earnings handled 20 events: 12 deliveries, earnings ₹540.00, tips ₹60.00
Projection pending-payouts: 1 pending
After payout completes: read model still says 1 pending (lag 1), after catch-up 0
Rebuilt daily-earnings from scratch: 21 events, same totals: true
```
