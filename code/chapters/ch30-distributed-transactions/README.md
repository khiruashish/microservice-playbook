# Chapter 30 — Distributed Transactions

| Package | What it is |
|---|---|
| `orders/` | `OrderController` (`POST /orders` → 202 Accepted), `OrderService` (order `PENDING` + outbox row in one transaction), `OrderRelay` (claims outbox rows with `SKIP LOCKED`, asks payments, records the decisions, all in one order-side transaction) |
| `payments/` | `PaymentLedger`: applies a batch in one transaction (idempotent debit rows, totals per dish and customer locked in key order), falls back to one by one when a constraint refuses the batch, stores a decision per order |
| `indoubt/` | For systems that run two-phase commit: `InDoubtPolicy` (decision log first, presumed abort after a grace period) and `InDoubtMonitor` (`pg_prepared_xacts` every 10 s) |
| `verify/` | `experiment.py`: local vs 2PC (`PREPARE TRANSACTION`) vs saga vs outbox on two PostgreSQL clusters; `check_service_sql.py`: the service's SQL exactly as written in the Java sources |

The `orders` and `payments` schemas stand in for two services' databases; no transaction in the code spans both.

## Run

```bash
cd code
mvn -B -pl chapters/ch30-distributed-transactions -am verify   # the container test needs Docker (skipped without it)

# the experiments: two PostgreSQL 16 clusters with max_prepared_transactions > 0, python3 (standard library only)
PGHOST_A=localhost PGPORT_A=5432 PGHOST_B=localhost PGPORT_B=5433 PGUSER=postgres \
B_STOP="pg_ctl -D /data/b -m fast -w stop" B_START="pg_ctl -D /data/b -l /data/b/log -w start" \
B_RESTART="pg_ctl -D /data/b -m fast -w restart" chapters/ch30-distributed-transactions/verify/run.sh
PGHOST_A=localhost PGPORT_A=5432 PGUSER=postgres python3 chapters/ch30-distributed-transactions/verify/check_service_sql.py
```

A network round trip is simulated by a pause of `RTT_MS` (default 1 ms) before each statement, so locks are held across
round trips as they would be across a network. `THREADS` (default 8) sets the number of clients.

## Verified output

PostgreSQL 16.13, two clusters in one 2-vCPU container. Two full runs; the chapter quotes the range of both. This is the second.

```text
== 1. throughput and lock holding
8 clients, 15 s per run, simulated round trip 1.0 ms before every statement

strategy    dishes  orders/s   p50 ms   p99 ms  failed  B lock held p50      confirmed after p50/p99 ms   consistent
local       10,000       970      8.1     11.4       0           5.5 ms                                   yes
2pc         10,000       398     19.7     28.1       0          12.2 ms                                   yes
saga        10,000       731     10.7     15.7       0           4.4 ms                                   yes
outbox      10,000      1308      5.9      9.4       0           6.6 ms                20 / 32 (1306/s)   yes
local        2 hot       313     19.2     94.4       0           5.8 ms                                   yes
2pc          2 hot       161     40.2    172.7       0          11.1 ms                                   yes
saga         2 hot       406     17.2     56.1       0           4.3 ms                                   yes
outbox       2 hot      1321      5.8      9.3       0           6.9 ms                30 / 48 (1318/s)   yes

== 2. the coordinator dies after PREPARE
2 hot dishes, 30 s. At 5 s the coordinator (2PC) or orchestrator (saga) dies right after the prepare / the payment step of one order.
At 12 s cluster B restarts. At 20 s the recovery job runs.

2pc: orders/s, lock timeouts, unreachable per 5 s
   0-5  s      154/s        0 lock timeouts      0 unreachable
   5-10 s        6/s       14 lock timeouts      1 unreachable
  10-15 s        2/s       14 lock timeouts     24 unreachable
  15-20 s        4/s       14 lock timeouts      0 unreachable
  20-25 s      155/s        0 lock timeouts      0 unreachable
  25-30 s      165/s        0 lock timeouts      0 unreachable
  before the restart: 1 prepared transaction(s) on B ['order-773'], holding 6 locks
  after the restart: 1 prepared transaction(s) on B ['order-773'], holding 6 locks
  recovery: in-doubt transactions resolved: 0 committed, 2 rolled back
  afterwards: 2439 confirmed, 0 without a debit, 0 debits without an order, 0 pending, money matches debits: True

saga: orders/s, lock timeouts, unreachable per 5 s
   0-5  s      389/s        0 lock timeouts      0 unreachable
   5-10 s      395/s        0 lock timeouts      1 unreachable
  10-15 s      378/s        0 lock timeouts     24 unreachable
  15-20 s      392/s        0 lock timeouts      0 unreachable
  20-25 s      410/s        0 lock timeouts      0 unreachable
  25-30 s      406/s        0 lock timeouts      0 unreachable
  recovery: saga recovery finished 1 PENDING orders
  afterwards: 11853 confirmed, 0 without a debit, 0 debits without an order, 0 pending, money matches debits: True


== 3. the payments database is down for 10 s
10,000 dishes, 30 s; cluster B is stopped at 10 s and started at 20 s

2pc:
   0-10 s      400 orders/s accepted       0 refused or failed
  10-20 s        1 orders/s accepted     350 refused or failed
  20-30 s      405 orders/s accepted       2 refused or failed
  6 orders took over 1 s: committed in A, waiting to commit in B (max 10.1 s)
  prepared transactions on B after the outage: 0; in-doubt transactions resolved: 0 committed, 0 rolled back
  afterwards: 8074 confirmed, 0 without a debit, 0 debits without an order, 0 pending, money matches debits: True

saga:
   0-10 s      716 orders/s accepted       0 refused or failed
  10-20 s        2 orders/s accepted    1395 refused or failed
  20-30 s      696 orders/s accepted      13 refused or failed
  saga recovery finished 0 PENDING orders
  afterwards: 14145 confirmed, 0 without a debit, 0 debits without an order, 0 pending, money matches debits: True

outbox:
   0-10 s     1272 orders/s accepted       0 refused or failed
  10-20 s     1428 orders/s accepted       0 refused or failed
  20-30 s     1177 orders/s accepted       0 refused or failed
  orders taken during the outage: 14287, all confirmed within 10.1 s of being placed
  afterwards: 38775 confirmed, 0 without a debit, 0 debits without an order, 0 pending, money matches debits: True
```

The service's own SQL (`check_service_sql.py`):

```text
PASS Flyway migration V1 applies
PASS INSERT_ORDER returns the new id
PASS the outbox holds one row per order
PASS CLAIM returns the three orders
PASS a second relay claims nothing meanwhile (SKIP LOCKED)
PASS INSERT_DEBITS inserts all three
PASS DEBIT_WALLETS refuses to overdraw wallet 2 (23514 -> DataIntegrityViolationException)
PASS one by one: 1 debited, 2 insufficient funds, 3 debited
PASS DECIDED returns a stored decision for every order
PASS the orders carry the decisions
PASS the outbox is empty
PASS balances: 1 paid twice, 2 untouched
PASS stock: 500 down one, 501 sold out
PASS a redelivered batch inserts no debit
PASS a redelivered batch gets the same decisions
PASS DECIDE never overturns a decided order
PASS TAKE_STOCK sorts, then locks (LockRows above Sort)
PASS TAKE_STOCK / DEBIT_WALLETS apply the totals (49 - 2, 50000 - 7000)
PASS PREPARED lists both, oldest first
PASS DECISIONS reads the coordinator's log
PASS a prepared transaction keeps its row locks (lock timeout, 55P03)
PASS COMMIT / ROLLBACK PREPARED resolve them
PASS only the committed one took effect

all checks passed
```

Unit tests (`InDoubtPolicyTest`, `BatchesTest`, `PlaceOrderTest`): 6 passed.
