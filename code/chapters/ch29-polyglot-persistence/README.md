# Chapter 29 — Polyglot Persistence

| Package | What it is |
|---|---|
| `catalog/` | PostgreSQL as the source of truth: `CatalogService` (item row + menu card + outbox in one transaction), `MenuCards` (a JSONB card per restaurant, built from the tables), `DishSearch` + `SearchQuery` (full-text with prefix, partial word stemmed or as typed, trigram fallback for typos), `CatalogController` |
| `projection/` | `PriceProjector` claims outbox rows (`FOR UPDATE SKIP LOCKED`), applies them to Redis and deletes them in one transaction; `PriceCache` writes only newer versions (Lua) |
| `riders/` | `RiderLocations` (Redis geo set + last-seen set, atomic update and pruning scripts), `PositionHistory` + `PingBuffer` (batched inserts), `PartitionPlan` + `PartitionMaintenance` (daily partitions, retention by `DROP`) |
| `verify/` | The chapter's five experiments and `check_service_sql.py`, which runs the service's SQL and Lua exactly as written in the Java sources |

Two services share the module for brevity; in production each is a separate deployable with its own database.

## Run

```bash
cd code
mvn -B -pl chapters/ch29-polyglot-persistence -am verify      # PolyglotContainerTest needs Docker (skipped without it)

# the experiments: PostgreSQL 16+ with contrib (pg_trgm, cube, earthdistance), Redis 7,
# psql, pgbench, redis-benchmark, python3 (standard library only). FLUSHES the Redis instance.
PGHOST=localhost PGPORT=5432 PGUSER=postgres REDIS_PORT=6379 chapters/ch29-polyglot-persistence/verify/run.sh
PGHOST=localhost PGPORT=5432 PGUSER=postgres REDIS_PORT=6379 python3 chapters/ch29-polyglot-persistence/verify/check_service_sql.py
```

The Python scripts use `verify/wire.py`, a minimal PostgreSQL (simple query protocol, trust authentication)
and Redis (RESP2) client, so they need no drivers. For password authentication, run them against a local
instance with `trust` for the test user, or swap in psycopg and redis-py.

## Verified output

PostgreSQL 16.13 and Redis 7.0.15 on a 2-vCPU container. Two full runs; the chapter quotes the range of both.
This is the second run (dish search from its standalone rerun, which added the latency-by-kind table).

```text
== 1. menus: documents vs tables
storage, freshly loaded
  relational, 5 tables + indexes: 151 MB
  document, 1 table: 16 MB  (a menu: 43 kB of JSON, 7605 bytes stored compressed)

workload                          tps       avg ms     WAL bytes/tx
r_read_menu                       308       12.985                3
d_read_menu                      4371        0.915                0
r_read_item                     29428        0.136                1
d_read_item                     16085        0.249                0
r_update_price                   5986        0.668              598
d_update_price                   1173        3.410             9532
r_update_hot                     5854        0.683              164
d_update_hot                      522        7.660             9203

cross-restaurant query: available veg items under Rs 150 in Bengaluru, cheapest 20 (median of 5 runs)
  relational (partial index)              0.968 ms
  document (jsonb_path_query)           167.838 ms
same answer: true

after the updates
  document table: 181 MB (old versions of whole menus, until vacuum)

== 2. dish search
29 queries over 192000 items; precision = relevant share of the top 10; found = queries with at least one relevant result

strategy                                             exact       reordered     plural/stem            typo          typing   by ingredient         all   p50 ms   max ms
                                              prec / found    prec / found    prec / found    prec / found    prec / found    prec / found
LIKE '%q%', no index                           100% / 6/6       0% / 0/5      25% / 1/4       0% / 0/7     100% / 5/5       0% / 0/2   41% 12/29    88.82   110.97
LIKE '%q%', trigram index                      100% / 6/6       0% / 0/5      25% / 1/4       0% / 0/7     100% / 5/5       0% / 0/2   41% 12/29     0.65    10.72
full-text, ranked                              100% / 6/6     100% / 5/5     100% / 4/4       0% / 0/7       0% / 0/5     100% / 2/2   59% 17/29     5.01    21.78
full-text + prefix, stemmed                    100% / 6/6     100% / 5/5     100% / 4/4       0% / 0/7      80% / 4/5     100% / 2/2   72% 21/29     8.77    24.41
full-text + prefix, stemmed or as typed        100% / 6/6     100% / 5/5     100% / 4/4       0% / 0/7     100% / 5/5     100% / 2/2   76% 22/29     9.77    44.75
trigram similarity                             100% / 6/6     100% / 5/5      82% / 4/4     100% / 7/7     100% / 5/5       0% / 0/2   91% 27/29    60.86   434.23
the service: words, then similar names         100% / 6/6     100% / 5/5     100% / 4/4     100% / 7/7     100% / 5/5     100% / 2/2  100% 29/29    13.54    85.36

median ms by query kind
strategy                                             exact       reordered     plural/stem            typo          typing   by ingredient
LIKE '%q%', no index                                   0.4            91.8            90.3            89.8             0.6            91.2
LIKE '%q%', trigram index                              0.3             9.4             1.0             1.1             0.3             0.9
full-text, ranked                                      5.5             6.3             7.2             0.2             0.2            18.3
full-text + prefix, stemmed                            8.3             9.3            12.5             1.4             8.8            20.6
full-text + prefix, stemmed or as typed                8.5            10.8            23.5             2.7            10.5            34.5
trigram similarity                                    57.5            81.0            63.2            60.5           115.8            18.3
the service: words, then similar names                10.0            11.7            23.7            56.7            10.9            32.9

== 3. rider locations

position updates
  PostgreSQL, synchronous_commit=on                               8013/s    0.998 ms       258 WAL B/op
  Redis GEOADD, AOF appendfsync always                           30590/s    0.240 ms
  PostgreSQL, synchronous_commit=off                             16752/s    0.478 ms       217 WAL B/op
  Redis GEOADD, AOF appendfsync everysec                         90033/s    0.061 ms
  Redis GEOADD, no persistence                                  105330/s    0.054 ms
  (PostgreSQL: 98% of updates were HOT, touching no index)

  PostgreSQL + GiST geo index, synchronous_commit=off             7632/s    1.048 ms       518 WAL B/op
  (PostgreSQL with the geo index: 0% HOT)

nearest 10 riders within 3 km of a random point
  PostgreSQL, earthdistance + GiST, KNN order                     3694/s    2.166 ms         0 WAL B/op
  Redis GEOSEARCH BYRADIUS 3 km ASC COUNT 10                       939/s    8.479 ms
  Redis GEOSEARCH BYRADIUS 3 km COUNT 10 ANY (unsorted)           8333/s    0.932 ms
  (Redis searches one fixed point, the city centre; redis-benchmark cannot randomize coordinates)
  riders within 3 km of the centre: 1880
  Redis memory for 50,000 riders: 5565080B in the geo set

== 4. position history
ingest 10.08 M rows in time order
  plain table, B-tree on time                     47579 ms
  partitioned by day, BRIN on time                39034 ms

size
  B-tree index on recorded_at                  79 MB
  BRIN index on recorded_at (7 partitions)     168 kB
  plain table, total                           1137 MB
  partitioned table, total                     1070 MB

queries (median of 5)
  10-minute window, all riders: plain/B-tree      1.922 ms
  10-minute window, all riders: part/BRIN         8.553 ms
  one rider, last hour: plain                     0.149 ms
  one rider, last hour: partitioned               0.409 ms

retention: remove the oldest day (1.44 M rows)
  DELETE ... WHERE recorded_at < day 1             1152 ms, 171 MB of WAL; table still 1137 MB until VACUUM reuses the space
  DROP TABLE pings_part_d0 (one partition)          222 ms, 83 kB of WAL

== 5. keeping two stores in sync
5000 items, 8 writers, 20,000 price changes; items where Redis != PostgreSQL at the end

strategy                                      stale  phantom   wrong    chg/s   lag p50/p99/max ms   events
dual write: database, then Redis                 77        0      77     3321                        cache write failed, logged 996, crash 88
dual write: database, then Redis + version       74        0      74     3444                        cache write failed, logged 996, crash 88
dual write: Redis, then database                  1       42      43     3670                        cache write failed, request failed 1000, crash 99, db transaction failed 89
outbox, projector reads 'id > offset'            48        0      48     3493        8 / 292 / 324   projector crashes 53
outbox, projector claims rows (SKIP LOCKED)       0        0       0     3218        3 / 238 / 292   projector crashes 91

the race alone: two writers, same item, same moment (0-2 ms pause between their two writes)
dual write: database, then Redis                396 of 1,000 rounds left Redis wrong
dual write: database, then Redis + version        0 of 1,000 rounds left Redis wrong
dual write: Redis, then database                475 of 1,000 rounds left Redis wrong
```

The service's own SQL and Lua (`check_service_sql.py`, 32 checks):

```text
PASS Flyway migration V1 applies
PASS MenuCards.REBUILD creates the card at version 1
PASS CatalogService.UPDATE_ITEM returns restaurant and new version
PASS the card is rebuilt at version 2
PASS the card shows the new price and the options
PASS a section without items renders as [] (coalesce), not null
PASS UPDATE_ITEM on an unknown item returns no row
PASS PriceProjector.CLAIM returns the outbox row
PASS APPLY_IF_NEWER applies version 2
PASS the outbox is empty after commit
PASS an older version is refused
PASS the same version again is refused
PASS the copy holds v=2 p=34000 a=1
PASS rolled-back claims stay in the outbox
PASS a concurrent projector skips claimed rows
PASS BY_WORDS 'biryani chicken'
PASS BY_WORDS 'chicken biry'
PASS BY_WORDS 'chicken bir'
PASS BY_WORDS 'cottage cheese'
PASS BY_WORDS 'gulab'
PASS BY_WORDS 'dumplings'
PASS (the stemmed prefix alone misses 'chicken biry')
PASS BY_WORDS 'panner buter masala' finds nothing
PASS BY_SIMILAR_NAME 'panner buter masala' -> Paneer Butter Masala first
PASS BY_SIMILAR_NAME 'chicken biriyani' -> the biryani
PASS (two typos in two words: word similarity 0.52, below the 0.6 default: not found)
PASS BY_WORDS can use the GIN index
PASS BY_SIMILAR_NAME can use the trigram index
PASS 9 daily partitions (PartitionMaintenance's query)
PASS PositionHistory.INSERT writes the batch
PASS nearest first: riders 2 then 1, MG Road not within 3 km
PASS PRUNE removes the silent rider from both sets

all checks passed
```

Unit tests (`SearchQueryTest`, `PartitionPlanTest`, `PositionHistoryJsonTest`): 11 passed.
