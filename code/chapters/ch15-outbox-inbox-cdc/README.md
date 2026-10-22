# Chapter 15 — Transactional Outbox, Inbox & CDC

ordering-service must change its database **and** tell other services (here loyalty-service) about it.
This module shows why writing to the database and the broker separately (a *dual write*) loses or
invents events, and how the **transactional outbox** (producer side) plus an **inbox** (consumer side)
make the pair reliable, with either a **polling publisher** or **log tailing / CDC** as the relay.

| Package | What it is |
|---|---|
| `sim/` | A service database (atomic local transactions, a sequence, a write-ahead log), a broker topic with at-least-once delivery, seeded fault injection |
| `orders/` | The order row and its events (OrderPlaced, OrderCancelled) |
| `dualwrite/` | Commit-then-publish and publish-then-commit, both broken |
| `outbox/` | `OutboxOrderService` (order + outbox row in one transaction), `PollingPublisher`, `LogTailingRelay` |
| `inbox/` | `LoyaltyConsumer`, with and without an inbox table |
| `simulation/` | 10,000 orders through five designs with the same faults |
| `app/` | The real thing on Spring Boot 4 + PostgreSQL: `Outbox` (JdbcClient, `MANDATORY` transaction), `OutboxRelay` (`FOR UPDATE SKIP LOCKED`), `Inbox` (`ON CONFLICT DO NOTHING`), `LoyaltyPointsHandler` |
| `debezium/` | Kafka Connect configuration for the CDC relay with Debezium's Outbox Event Router |

## Run

```bash
cd code
mvn -B -pl chapters/ch15-outbox-inbox-cdc -am verify     # container tests need Docker; skipped without it
java -cp chapters/ch15-outbox-inbox-cdc/target/classes dev.foodfleet.ch15.simulation.OutboxSimulation
```

## Verified output

```text
10,000 orders per design (10% cancelled), same seeded faults

design                                              events  on bus  lost phantom  dup msgs   dedup'd points off
dual write: commit, then publish                     11042   10873   169       0         0        67     -10959
dual write: publish, then commit                     10738   10902     0     164         0        56     +11393
outbox + polling publisher, consumer without inbox   10923   11019     0       0        96         0     +12519
outbox + polling publisher + inbox                   10923   11019     0       0        96       159         +0
outbox + log tailing (CDC) + inbox                   10918   11096     0       0       178       230         +0
```

- **events**: events that should exist (one per committed order, plus one per committed cancellation)
- **on bus**: messages on the broker, duplicates included
- **lost / phantom**: committed changes nobody heard about / announced changes that never committed
- **dup msgs**: duplicate messages on the broker (relay crashes, lost acks)
- **dedup'd**: duplicates the inbox skipped (broker duplicates plus consumer redeliveries)
- **points off**: loyalty points awarded minus points that should have been awarded

The SQL in `app/` (migration, relay query and update, inbox insert, points upsert, cleanup) was also run
against PostgreSQL 16, including the `SKIP LOCKED` case with a second session holding a row lock, and a
logical-decoding check that an outbox row inserted and deleted in one transaction still appears in the WAL.
