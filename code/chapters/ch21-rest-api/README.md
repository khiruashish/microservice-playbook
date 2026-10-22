# Chapter 21 — Synchronous REST

order-history-service: a customer's past orders, paged with keyset cursors, cacheable with ETags,
updatable only with If-Match, every error an RFC 9457 problem.

| Path | What it is |
|---|---|
| `paging/Cursor` | Opaque, versioned, URL-safe cursor: the sort key (placed_at, id) of the last item |
| `paging/PagingAnomalySimulation` | Offset vs keyset paging through a list that changes while you read it |
| `orders/JdbcOrderStore` | Keyset query `(placed_at, id) < (:t, :id)` on a matching index; optimistic update on `version` |
| `api/OrderHistoryController` | `GET /api/customers/{id}/orders?limit&cursor` (Link rel=next), `GET /api/orders/{id}` (ETag, 304), `PATCH /api/orders/{id}/delivery-instructions` (If-Match: 428/412) |
| `api/ApiProblems`, `ApiProblem` | RFC 9457 problems with stable `type` URIs and extensions |
| `bench/` | PostgreSQL benchmark: offset vs keyset on 2,000,000 orders |

## Run

```bash
cd code
mvn -B -pl chapters/ch21-rest-api -am verify        # the store tests need Docker; skipped without it
java -cp chapters/ch21-rest-api/target/classes dev.foodfleet.ch21.paging.PagingAnomalySimulation

# benchmark (any PostgreSQL 14+)
psql -d bench -f chapters/ch21-rest-api/bench/pagination-setup.sql
python3 chapters/ch21-rest-api/bench/pagination_benchmark.py "psql -X -d bench"
```

## Verified output

PostgreSQL 16.13, 2 vCPUs, 2,000,000 rows (478 MB), page size 20, median of 7 runs, data in cache.
The script asserts both queries return the same rows before timing them.

```text
   page  rows skipped |  OFFSET ms   buffers |  KEYSET ms   buffers |   ratio
      1             0 |      0.057         4 |      0.057         4 |      1x
     10           180 |      0.090         7 |      0.065         5 |      1x
    100         1,980 |      0.498        40 |      0.068         5 |      7x
  1,000        19,980 |      4.783       368 |      0.101         4 |     47x
 10,000       199,980 |     57.540     3,640 |      0.084         5 |    685x
 50,000       999,980 |    245.972    18,193 |      0.073         4 |   3369x
 99,999     1,999,960 |    550.832    35,994 |      0.057         4 |   9664x
```

```text
Support dashboard: 2,000 open orders, an agent reads 50 pages of 20 (15 s per page);
meanwhile orders arrive at the top and close anywhere in the list.

strategy  per page: new / closed   should see  duplicates   skipped
OFFSET    0 / 0                          1000           0         0
KEYSET    0 / 0                          1000           0         0
OFFSET    3 / 0                           853         147         0
KEYSET    3 / 0                          1000           0         0
OFFSET    0 / 3                           998           0        45
KEYSET    0 / 3                           953           0         0
OFFSET    3 / 3                           855         111         0
KEYSET    3 / 3                           966           0         0
OFFSET    10 / 10                         553         379         0
KEYSET    10 / 10                         883           0         0
```
