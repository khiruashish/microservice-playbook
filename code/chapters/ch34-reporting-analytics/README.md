# Chapter 34 — Reporting & Analytics Across Services

| Package | What it is |
|---|---|
| `projection/` | `OrderEvent`, `OrderEventListener` (Kafka, `order-events`), `OrderFactProjector` (facts replaced only by newer versions; daily revenue changed by the delta between old and new state, in one statement; business day in the configured zone; an advisory lock per order) |
| `report/` | `RevenueReports` (daily revenue, top restaurants, monthly view; every report carries its freshness), `ReportController`, `MonthlyViewRefresher` (`REFRESH MATERIALIZED VIEW CONCURRENTLY` with a lock timeout) |
| `freshness/` | `Freshness` (as-of time, lag, stale against an objective) |
| `db/migration` | V1: `order_facts`, `daily_restaurant_revenue`, `projection_status`, `city_monthly_revenue` (materialized view with a unique index) |
| `verify/` | `common.py` (two clusters: three services' databases and a reporting copy by logical replication), `e1`–`e5`, `check_service_sql.py`, `run.sh` |

## Run

```bash
cd code
mvn -B -pl chapters/ch34-reporting-analytics -am verify   # the container test needs Docker (skipped without it)

# the experiments: two PostgreSQL 16 clusters (the services' one with wal_level = logical), pgbench, taskset, python3
SRC_PGHOST=... SRC_PGPORT=... RPT_PGHOST=... RPT_PGPORT=... PGUSER=postgres chapters/ch34-reporting-analytics/verify/run.sh
```

## Verified output

Two PostgreSQL 16.13 clusters in one 2-vCPU container, the services' cluster (and pgbench) pinned to CPU 0, the
reporting cluster to CPU 1. Two full runs; the chapter quotes the range of both. This is the second; its E3 section
is from a later run of `e3_cdc_lag.py` (twice), after the last step's report was changed to show that the consumer
cannot catch up once its slot is invalidated.

```text
== set-up
services built in 13 s
reporting copy by logical replication: initial copy 8.2 s

== E1 cross-service report
Net revenue per restaurant, Bengaluru, 2025-03-16 to 2025-04-14 (IST), top 20 with names.

approach                                  time   calls   rows sent   rows read by services  same answer
compose the three services' APIs        2907 ms     656     216,976              12,728,718  True
federated query (postgres_fdw)          2278 ms       1   1,355,001               8,035,414  True
same SQL on the reporting copy           764 ms       1          20                       0  True
pre-aggregated daily table               144 ms       1          20                       0  True

top row: ('Restaurant 1', '23506', '1347691302', '41412133', '1306279169')

== E4 materialized views
Summary of 889,455 restaurant-days; 4 readers do point lookups; 20,000 new delivered orders are folded in.

update                                             took     reads   longest read  reads > 100 ms
REFRESH MATERIALIZED VIEW                        3.45 s       978        3381 ms               4
REFRESH MATERIALIZED VIEW CONCURRENTLY          24.12 s   322,032          28 ms               0
incremental table: one delta per order          23.53 s   327,356          20 ms               0

incremental summary vs a full recomputation: 0 rows differ
a view refresh recomputes all 897,231 rows however few changed; its data is as old as the last refresh

== E5 incremental correctness
20,000 orders, 43,021 events delivered (2,081 redeliveries, some out of order).

projection              events/s   restaurant-days wrong  total revenue  worst day
counters                   3,964           839 of 3,271           +7.14%       8.2%
counters + dedup           2,503           294 of 3,269           +1.94%       2.3%
state + version            1,008             0 of 3,268           +0.00%       0.0%
... day in UTC               959           969 of 3,383           +0.00%       4.1%

orders placed between 00:00 and 05:30 IST (a different UTC date): 4.0%

== E2 reports on the primary
Order service writes (pgbench, 8 clients, 30 s) while two report loops run elsewhere or not at all.

reports run on                                writes/s          p50          p99   reports
nowhere (baseline)                               3,144       2.4 ms       5.5 ms
the order service's primary                      1,045       6.8 ms      23.7 ms        14
the reporting copy (other machine)               3,291       2.2 ms       5.9 ms        14
nowhere (baseline, again)                        3,105       2.4 ms       5.3 ms

A report transaction held open for 30 s of writes, then VACUUM on the order service's database.

report held open on                         dead rows left by VACUUM       after it ends
the order service's primary                                 93,879                   0
the reporting copy                                               0                   0

== E3 CDC lag and slots
Steady state: pgbench writes for 30 s; a probe order every 250 ms, timed until it is in the copy.

  writes/s 3,105; probes 110; lag p50 3 ms, p99 4 ms, max 5 ms

The consumer stops for 60 s while writes continue:

   after   WAL retained for the slot   copy behind by
    10 s                      30 MB             10 s
    20 s                      51 MB             20 s
    30 s                      74 MB             30 s
    40 s                      94 MB             40 s
    50 s                     113 MB             50 s
    60 s                     132 MB             60 s
  re-enabled: caught up in 6.1 s; WAL retained afterwards 13 MB

With max_slot_wal_keep_size = 64MB, the consumer stops again:

  slot status: lost after 7 s, at most 57 MB retained (the order service's disk was protected)
  consumer re-enabled, 15 s later: caught up False, copy behind by 96 s (the consumer keeps failing: the slot can no longer return changes)
  rebuilt by a fresh initial copy: 4,396,270 orders in 31.1 s

== service SQL
PASS Flyway migration V1 applies
PASS a new order is applied
PASS a placed order adds no revenue
PASS delivery adds the order
PASS a redelivered event is ignored
PASS an event older than the stored version is ignored
PASS totals unchanged by the duplicate and the late event
PASS a refund takes the order back out
PASS the refunded restaurant's row is back to zero
PASS an order at 01:30 IST counts on the IST day, not the UTC one
PASS top restaurants ranks by revenue
PASS the monthly view refreshes CONCURRENTLY
PASS freshness is the newest event time applied

all checks passed
```
