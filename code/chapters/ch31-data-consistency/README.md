# Chapter 31 — Data Consistency

| Package | What it is |
|---|---|
| `reads/` | `DataSources` (primary + replica pools), `ConsistencyToken` (WAL positions), `Positions`, `ReplicaRouter`: check the replica's replay position, then read; wait up to 50 ms; else the primary |
| `profiles/` | `ProfileService` / `ProfileController`: updates require `If-Match` (412 on a stale version) and return a `Consistency-Token`; reads accept it |
| `points/` | `LoyaltyPoints`: atomic add (upsert), conditional redeem |
| `merge/` | `OrSet` (observed-remove set CRDT), `HybridLogicalClock`, `LwwRegister` |
| `verify/` | `experiment.py` (read-your-writes, monotonic reads, lost updates on a primary with two streaming replicas; two-region conflict simulation) and `check_service_sql.py` (the service's SQL as written, against a real primary and replica) |

## Run

```bash
cd code
mvn -B -pl chapters/ch31-data-consistency -am verify   # the container test needs Docker (skipped without it)

# the experiments: a PostgreSQL 16 primary with two asynchronous streaming replicas, python3 (standard library only)
PGHOST=localhost PGUSER=postgres PRIMARY_PORT=5432 REPLICA1_PORT=5433 REPLICA2_PORT=5434 chapters/ch31-data-consistency/verify/run.sh
PGHOST=localhost PGUSER=postgres PRIMARY_PORT=5432 REPLICA1_PORT=5433 python3 chapters/ch31-data-consistency/verify/check_service_sql.py
```

A replica for local experiments: `pg_basebackup -h localhost -p 5432 -U postgres -D replica1 -R -X stream`, then start it on
another port. The experiments set `recovery_min_apply_delay` on the replicas for the "lagging" cases and reset it afterwards.

## Verified output

PostgreSQL 16.13 (a primary and two streaming replicas) in one 2-vCPU container. Two full runs; the chapter quotes the
range of both. This is the second.

```text
== ryw
32 customer sessions, 20 s per run: each operation is a profile update (1%) or a read of the own profile
(99%), 10 ms apart; right after an update the profile is read at once (the page reloads).
A read is stale if it shows an older version than the customer's own last write.

replica                       strategy                                 reads   stale  stale %  from primary  p50 ms  p99 ms  canceled
healthy (no delay)            always the replica                       52266      40    0.08%          0.0%     1.6     5.0         0
healthy (no delay)            always the primary                       52671       0    0.00%        100.0%     1.6     4.8         0
healthy (no delay)            primary for 1 s after own write          52236       0    0.00%         54.7%     1.6     5.2         0
healthy (no delay)            primary for 100 ms after own write       52609       0    0.00%          8.2%     1.6     4.8         0
healthy (no delay)            replica once it has the write (LSN)      45648       0    0.00%          0.0%     3.1     9.7         0
lagging (200 ms apply delay)  always the replica                       52674    7923   15.04%          0.0%     1.5     5.0         0
lagging (200 ms apply delay)  always the primary                       52303       0    0.00%        100.0%     1.6     5.0         0
lagging (200 ms apply delay)  primary for 1 s after own write          52413       0    0.00%         55.0%     1.6     5.1         0
lagging (200 ms apply delay)  primary for 100 ms after own write       52405    3545    6.76%          8.2%     1.6     4.9        11
lagging (200 ms apply delay)  replica once it has the write (LSN)      41828       0    0.00%          2.9%     3.0    56.7        19

== monotonic
For 20 s the kitchen advances the status of 100 orders (~800 changes/s on the primary). 16 tracking pages
each poll one of 10 orders every 10 ms through a load balancer over replica1 (no delay) and replica2
(300 ms apply delay).
A read goes backwards if it shows an earlier step of an order than that page has already shown.

routing                                      reads  went backwards       %  avg steps behind  from primary  canceled
random replica per request                   27452            5163  18.81%               1.5          0.0%         0
sticky replica per session                   27657               0   0.00%               0.0          0.0%         0
random replica, at least the seen LSN        23095               0   0.00%               0.0          0.0%         0

== lost
16 threads add 10 loyalty points at a time to 50 customers for 10 s, on the primary.

method                                                  adds/s  points lost  retries  p99 ms
read, add in the app, write (READ COMMITTED)              2943        45710        0    11.1
atomic UPDATE points = points + 10                        4416            0        0     8.8
SELECT ... FOR UPDATE, then write                         1832            0        0    19.0
optimistic: UPDATE ... WHERE version = v, retry           2438            0     4791    21.1
read, add, write in REPEATABLE READ, retry                1689            0     3154    28.8

== conflicts
Two regions accept writes to 2,000 shared family carts (20 operations each, family members a few hundred
ms apart, in either region) and replicate to each other with 50-500 ms lag; region B's clock is off by up
to the skew. Counted once everything has arrived everywhere:
  lost add: an added item is missing, though nobody who had seen that add removed it
  resurrected: a removed item is back, though every add of it had been seen by a remove
  stale note: the delivery note shown is one that a later writer had already seen and replaced

 clock skew  merge                                             regions agree  lost adds  resurrected  stale note
        0ms  last writer wins on the whole cart (wall clock)       2000/2000        562          259           0
        0ms  last writer wins per item (wall clock)                2000/2000         82            0           0
        0ms  OR-Set per item, note by hybrid logical clock         2000/2000          0            0           0
      250ms  last writer wins on the whole cart (wall clock)       2000/2000        578          280           2
      250ms  last writer wins per item (wall clock)                2000/2000         78            3           2
      250ms  OR-Set per item, note by hybrid logical clock         2000/2000          0            0           0
```

The service's own SQL (`check_service_sql.py`):

```text
PASS Flyway migration V1 applies
PASS on the primary, the replay position is NULL (the router won't trust it as a replica)
PASS on the replica, the replay position is a WAL position
PASS UPDATE against the current version returns the new version
PASS UPDATE against an old version changes nothing
PASS right after the write the replica is behind the token and still shows version 2
PASS once its replay position reaches the token (after ~275 ms) it shows version 3
PASS hex-pair parsing orders positions as pg_lsn does
PASS ADD creates the row
PASS ADD adds to it
PASS REDEEM takes points when there are enough
PASS REDEEM refuses (no row) when there aren't
PASS the balance is untouched by the refusal

all checks passed
```

Unit tests (`ConsistencyTokenTest`, `ReplicaRouterTest`, `OrSetTest`, `HybridLogicalClockTest`, `IfMatchTest`): 16 passed, 0 failed.
