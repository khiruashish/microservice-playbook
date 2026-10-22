# Chapter 33 — Database Scaling & Zero-Downtime Migrations

| Package | What it is |
|---|---|
| `address/` | `AddressPhase` (EXPAND → READ_NEW → CONTRACT: what each step writes and reads), `CustomerAddresses`, `KeysetBackfill` (throttled keyset batches that copy rows that differ), `BackfillRunner` (`@ConditionalOnBooleanProperty("app.backfill.enabled")`) |
| `pool/` | `PoolSizing` (fleet-wide pool budget against `max_connections`, deploy surge and the database's cores) |
| `schema/` | `InvalidIndexCheck` (reports indexes left INVALID by a failed `CREATE INDEX CONCURRENTLY`) |
| `sharding/` | `SlotRouter` (CRC32C → 4,096 slots → shards; growth plans that move the fewest slots) |
| `db/migration` | V1 tables · V2 expand (add column, drop NOT NULL) · V3 index `CONCURRENTLY`, alone, no `IF NOT EXISTS` · V4 CHECK `NOT VALID` · V5 `VALIDATE` |
| `db/later` | V6 contract (drop `address`), moved into `db/migration` in the release after every instance runs CONTRACT |
| `verify/` | `e1`–`e5` experiments, `check_service_sql.py` (the migrations and service SQL as written), `run.sh` |

## Run

```bash
cd code
mvn -B -pl chapters/ch33-database-scaling -am verify   # the container test needs Docker (skipped without it)

# the experiments: PostgreSQL 16 with max_connections >= 300, pgbench, psql, python3 (standard library only); ~25 min
PGHOST=localhost PGPORT=5432 PGUSER=postgres chapters/ch33-database-scaling/verify/run.sh
```

## Verified output

PostgreSQL 16.13 in one 2-vCPU container (`shared_buffers` 256 MB, `max_connections` 300), load from pgbench on the
same machine. Two full runs; the chapter quotes the range of both. This is the second.

```text
== E1 pool size
connections                             tps     avg ms       p99 ms latency at 256 requests
2                                      3052       0.65         1.34                83.9 ms
4                                      5774       0.69         1.69                44.3 ms
8                                      6323       1.25         3.22                40.5 ms
16                                     6470       2.42         5.69                39.6 ms
32                                     7099       4.33         9.77                36.1 ms
64                                     7699       7.87        17.39                33.3 ms
128                                    7685      15.29        34.91                33.3 ms
256                                    7319      29.93        76.24                35.0 ms
16, new connection per transaction      461     34.682

== E2 DDL under load
2 M orders; pgbench writes on 8 connections for 25 s; the change runs at 8 s.

change                                               took   longest write  writes > 1 s  throughput
                                                                                          vs before
ADD COLUMN note text                               0.01 s           16 ms             0        105%
ADD COLUMN tip int NOT NULL DEFAULT 0              0.01 s            5 ms             0        102%
ADD COLUMN ref uuid DEFAULT gen_random_uuid()      3.39 s         3288 ms             8         11%
ALTER COLUMN amount_paise TYPE bigint              1.57 s         1475 ms             8         26%
CREATE INDEX                                       0.81 s          820 ms             0         36%
CREATE INDEX CONCURRENTLY                          1.17 s           53 ms             0         63%
ADD CONSTRAINT CHECK                               0.12 s          117 ms             0         86%
ADD CONSTRAINT CHECK NOT VALID, then VALIDATE      0.12 s           10 ms             0         94%
ADD COLUMN behind a 12 s report query             10.02 s        10017 ms             8          5%
  ...with lock_timeout 500 ms and retries         10.57 s          506 ms             0         71%   8 attempts

A concurrent build that cannot finish:
  build while a REPEATABLE READ transaction is open: failed after 3.0 s (55P03 canceling statement due to lock timeout)
  index left behind, valid: f
  the same migration again (IF NOT EXISTS): succeeds; index valid: f; plan: Parallel Seq Scan on orders
  after DROP INDEX CONCURRENTLY and a fresh build: valid: t; plan: Index Scan using orders_restaurant_created on orders

== E3 backfill
2 M orders, a new nullable column delivery_zone; pgbench writes on 8 connections; the backfill starts at 5 s.

backfill                                      took  batches   longest write  writes > 1 s  throughput       WAL  table growth  missed
one UPDATE for all rows                      5.6 s        1         5529 ms             8          2%    602 MB        219 MB       0
batches of 10,000, no pause                 25.0 s      203          158 ms             0         56%    650 MB        222 MB       0
batches of 1,000, 20 ms pause               53.8 s     2028           58 ms             0         88%    797 MB        217 MB       0

== E4 rename
2,000 customers; 4 instances read and write addresses (50/50) every 2 ms; deploys roll one instance every 1.5 s.

migration                                                ops   errors  wrong reads   wrong at end
rename the column, then roll out the new version       22847     3973            0              0
roll out the new version, then rename the column       23173     4867            0              0
expand/contract, backfill only empty rows              48121        0           12              0   backfilled 438 rows
expand/contract, backfill rows that differ             48945        0            0              0   backfilled 534 rows

== E5 sharding
1,000,000 orders on 16 shards

shard key                           largest shard  vs average  emptiest  single-shard queries  shards per query
order id                                   62,862       1.01x    62,083                   40%              10.0
customer id                                74,262       1.19x    57,456                   30%              11.5
restaurant id                             230,846       3.69x    28,147                   25%              12.2
city                                      372,438       5.96x         0                    5%              15.2
customer id, shard in order id             74,262       1.19x    57,456                   70%               5.5

going from 16 to 20 shards: share of the 1,000,000 orders (keyed by customer id) that must move

hash(key) mod N                                80.3%
consistent-hash ring, 100 points per shard     18.6%
4,096 fixed slots, slots reassigned            19.5%

largest of 20 shards vs average afterwards: ring 1.29x, slots 1.26x (the ideal move is 4/20 = 20%)

== service SQL
PASS V1__customers_and_orders.sql applies (in a transaction)
PASS V2__expand_customer_delivery_address.sql applies (in a transaction)
PASS V3__orders_restaurant_created_index.sql holds only the non-transactional statement
PASS V3__orders_restaurant_created_index.sql applies (outside a transaction)
PASS V4__orders_amount_positive_not_valid.sql applies (in a transaction)
PASS V5__orders_amount_positive_validate.sql applies (in a transaction)
PASS no invalid indexes after the migrations
PASS the CHECK constraint is validated
PASS a zero amount is rejected
PASS EXPAND still reads the old column
PASS the backfill copies the empty row and the stale one
PASS a second backfill copies nothing
PASS past the end the batch returns NULL last_id (the loop's stop signal)
PASS READ_NEW reads the backfilled values
PASS EXPAND instances still running see READ_NEW's writes
PASS CONTRACT inserts without the old column (V2 dropped its NOT NULL)
PASS after V6 the CONTRACT statements still work
PASS a failed concurrent build is reported by InvalidIndexCheck

all checks passed
```
