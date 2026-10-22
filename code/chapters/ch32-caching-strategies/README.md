# Chapter 32 — Caching Strategies

| Package | What it is |
|---|---|
| `info/` | `CacheConfig` (a `RedisCacheManagerBuilderCustomizer` making Boot's RedisCacheManager transaction-aware), `RestaurantInfoService` (`@Cacheable(sync = true)`, `@CacheEvict` applied after commit) |
| `menu/` | `ItemPriceCache` (`PUT_IF_NEWER` Lua: an old price can never replace a newer one), `ItemPrices` (cache-aside reads, write-through after commit, works with Redis down) |
| `top/` | `SoftTtlCache` (stale-while-revalidate with a refresh lock), `CacheStore`, `RedisCacheStore`, `TopRestaurants` |
| `views/` | `ViewCounter` (HINCRBY), `ViewFlusher` (atomic claim by RENAME, batch id recorded with the counts, cleanup) |
| `verify/` | `experiment.py` (what to cache, invalidation, stampedes, write-behind) and `check_service_sql.py` (the service's SQL and Lua as written) |

## Run

```bash
cd code
mvn -B -pl chapters/ch32-caching-strategies -am verify   # the container test needs Docker (skipped without it)

# the experiments: PostgreSQL 16, Redis 7, python3 (standard library only); FLUSHES the Redis instance
PGHOST=localhost PGPORT=5432 PGUSER=postgres REDIS_PORT=6379 \
REDIS_RESTART="redis-cli -p 6379 shutdown nosave; redis-server --port 6379 --daemonize yes --save ''" \
chapters/ch32-caching-strategies/verify/run.sh
PGHOST=localhost PGPORT=5432 PGUSER=postgres REDIS_PORT=6379 python3 chapters/ch32-caching-strategies/verify/check_service_sql.py
```

## Verified output

PostgreSQL 16.13 and Redis 7.0.15 in one 2-vCPU container; a 0.5 ms pause before every database and Redis call stands in
for the network. Two full runs; the chapter quotes the range of both. This is the second.

```text
== read
16 clients for 15 s; requests follow a Zipf distribution over 2,000 restaurants; round trip 0.5 ms

what            cache                                  req/s  p50 ms  p99 ms  DB reads/s  hit rate
menu card       none                                    7240     1.8     7.1      7239.5      0.0%
menu card       Redis, cache-aside, TTL 60 s            6211     2.2     7.8       134.0     97.8%
menu card       in-process (5 s) + Redis (60 s)        13760     0.0     7.3       135.0     99.0%
top restaurants none                                       8  1960.2  3352.0         8.1      0.0%
top restaurants Redis, cache-aside, TTL 60 s           10554     1.1     3.6         1.1    100.0%

== invalidation
200 menu items, 16 clients for 15 s, 90% reads through the cache, 10% price changes, 2 ms apart.
A reader that misses spends 0-5 ms between reading the database and filling the cache (serialisation,
a GC pause). A read is stale if it returns an older price than one committed before the read started;
stale entries are cache keys still older than the database after the run (they live until their TTL).

strategy                                       reads  stale reads  stale %  hit rate  stale entries
TTL only (60 s), no invalidation               68216        66169   97.00%     99.7%            200
delete key, then update the database           58177         1022    1.76%     88.8%              1
update the database, then delete key           58098          316    0.54%     89.0%              1
update, delete, delete again 500 ms later      47556          147    0.31%     80.6%              0
update, then write-through set-if-newer        63481           11    0.02%     99.7%              0
update, delete; fills need a lease             54615            6    0.01%     89.5%              0

== stampede
'Top restaurants' (an aggregation over 1 M reviews, ~148 ms) cached for 3 s;
The value is in the cache when the run starts; 4 app instances x 20 clients ask for it every 5 ms for 20 s.

protection                                     recomputes max at once   req/s  p50 ms  p99 ms  >100 ms
none (cache-aside)                                    160          80    2554     3.4    31.6      161
single-flight per instance                             20           4    6939     2.9    12.9      400
distributed lock (SET NX), others wait                  8           1    7408     3.0    13.0      400
probabilistic early refresh (XFetch)                  179          80     993     3.7    58.3      179
XFetch + lock: one early refresher                     17           1    8300     2.8    11.7       17
stale-while-revalidate (soft TTL) + lock                9           1    7522     3.3    15.1        0

== writebehind
16 clients count item views (2,000 items) for 10 s.

write path                                              views/s  DB statements/s   counted    in DB   lost  doubled
write-through: UPDATE per view                             7087           7086.8     70868    70868      0        0
write-behind: HINCRBY, flush every 1 s                    12271              1.0    122710   122710      0        0
write-behind, Redis restarts (no persistence) at 7 s      11112              0.9    111117   101126   9991        0
write-behind, flusher dies before its DB commit           12729              1.0    127293   127293      0        0
write-behind, flusher dies after commit, before cleanup    12479              1.0    124794   124794      0        0
  ...without the batch id                                 12954              1.0    129544   142361      0    12817
```

The service's own SQL and Lua (`check_service_sql.py`):

```text
PASS Flyway migration V1 applies
PASS the price update returns the new version
PASS the writer's write-through after commit is stored
PASS the stalled reader's late fill with version 1 is refused
PASS the same version again is refused (idempotent)
PASS the cache holds 2:34000 with a TTL
PASS an empty cache accepts a fill
PASS TOP returns ranked JSON, restaurants with too few reviews left out
PASS TOP returns [] when nothing qualifies
PASS CLAIM renames the pending hash and returns the batch id first
PASS after a crash, CLAIM returns the unfinished batch with its original id
PASS applying the same batch twice counts it once
PASS the next flush takes the view that arrived meanwhile, under a new batch id
PASS totals are exact
PASS CLAIM with nothing pending returns nothing

all checks passed
```

Unit tests (`SoftTtlCacheTest`): 5 passed, 0 failed.
