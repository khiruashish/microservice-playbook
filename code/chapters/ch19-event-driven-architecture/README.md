# Chapter 19 — Event-Driven Architecture

Menu data moves from catalog-service to search, recommendations and ordering in three styles, on
the same seeded lunch rush with a two-minute catalog outage: synchronous calls, event notification
(thin events plus a call back, naive or careful), and event-carried state transfer (the event holds
the item's state and version; consumers keep a replica).

| Package | What it is |
|---|---|
| `catalog/` | catalog-service: 5,000 items, changes, an API that goes down |
| `bus/` | A keyed topic: per-key order, 20–80 ms delivery, 1% redelivered later; the two event shapes |
| `consumer/` | `Replica` (newest version wins), `NotificationConsumer` (naive, or coalesced with jittered backoff), `StateTransferConsumer` |
| `simulation/` | `IntegrationStyles`: the comparison and the bootstrap cost of a new consumer |
| `app/` | Spring Cloud Stream: `StreamBridge` publisher keyed by item id, a `Consumer<MenuItemUpdated>` bean, a version-checked replica, test-binder tests |

## Run

```bash
cd code
mvn -B -pl chapters/ch19-event-driven-architecture -am verify
java -cp chapters/ch19-event-driven-architecture/target/classes dev.foodfleet.ch19.simulation.IntegrationStyles
```

## Verified output

```text
10 min lunch rush: 5,000 items, 50 changes/s, 200 orders/s, 3 consumers;
catalog API down from 4:00 to 6:00.

style                                orders  sold-out    wrong  failed in   sold-out    catalog       bus  staleness  staleness
                                     failed  accepted    price     outage  in outage  API calls        MB     p50 ms     p99 ms
synchronous call per order           20.21%     0.00%    0.00%     100.0%      0.00%     120338      0.00          -          -
notification, naive callbacks         0.00%     2.66%    1.09%       0.0%     12.96%    1182384      2.61         77     114477
notification, coalesced + backoff     0.00%     3.01%    1.17%       0.0%     13.09%     185383      2.61         78     124901
event-carried state transfer          0.00%     0.02%    0.01%       0.0%      0.00%          0      6.38         50         79

A new consumer joins after 10 minutes and needs the whole menu:
  call the catalog API:                  5,000 requests
  read a compacted topic (latest/key):   5,000 messages, 1.1 MB
  replay the full event log:            35,021 messages, 7.4 MB
```
