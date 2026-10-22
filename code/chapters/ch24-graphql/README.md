# Chapter 24 — GraphQL

The customer app's graph in FoodFleet's mobile BFF (`src/main/resources/graphql/customer.graphqls`). Three parts:

| Package | What it is |
|---|---|
| `mini/` | `Query` (parser for the subset used here), `Engine` (level-by-level execution with per-item or batch resolvers, static cost), `Json`. Small on purpose, to make N+1 and batching visible |
| `experiment/` | `FoodFleetBackends` (four services, in memory, counting calls), `FoodFleetGraph` (the graph on the mini engine), `ScreenComparison` (the "My orders" screen four ways), `QueryCost` (what nested queries cost) |
| `graph/` | Spring for GraphQL: `CustomerGraphController` (`@QueryMapping`, `@BatchMapping`, `@SchemaMapping`, `@ContextValue`), `GraphQlConfig` (depth and complexity limits, error mapping, header-to-context interceptor, schema inspection) |

## Run

```bash
cd code
mvn -B -pl chapters/ch24-graphql -am verify
java -cp chapters/ch24-graphql/target/classes dev.foodfleet.ch24.experiment.ScreenComparison
java -cp chapters/ch24-graphql/target/classes dev.foodfleet.ch24.experiment.QueryCost
GRAPHIQL_ENABLED=true mvn -pl chapters/ch24-graphql spring-boot:run      # GraphiQL at http://localhost:8080/graphiql
curl -s localhost:8080/graphql -H 'Content-Type: application/json' -H 'X-Customer-Id: c-1001' \
  -d '{"query":"{ myOrders(first: 2) { id status restaurant { name } rider { name } } }"}'
```

## Verified output

Measured on a JDK 21 runtime (the module targets JDK 25). The latency column is a model (80 ms mobile RTT per wave, 4/1 Mbit/s down/up, 6 ms per backend
call), the other columns are counted.

```text
"My orders" screen: 10 orders, 6 restaurants, 5 riders

approach                               requests  waves    req B    resp B   gzip B  backend latency ms
REST resources, composed by the app          12      2      239     16314     4834       12        184
GraphQL, per-item resolvers                   1      1      177      2579      583       19        197
GraphQL, batched (DataLoader)                 1      1      177      2579      583        3        101
BFF endpoint for this screen                  1      1       27      2568      575        3         93

per-item resolvers, calls by service: {order-service: recent orders=1, restaurant-service: one=10, rider-service: one=8}
batched resolvers,  calls by service: {order-service: recent orders=1, restaurant-service: batch=1, rider-service: batch=1}

GraphQL request body: 177 bytes; as a persisted query hash: 127 bytes
identical responses, per-item vs batched: true
```

```text
restaurant -> reviews(first: 10) -> restaurant -> ...   limit: complexity 2000

 levels  depth   complexity    fields if run   calls per-item   calls batched  with the limit
      1      3           13               13                2               2  allowed
      2      5          143              143               22               4  allowed
      3      7         1443             1443              222               6  allowed
      4      9        14443            14443             2222               8  rejected up front
      5     11       144443           144443            22222              10  rejected up front
```

## Tests

| Test | Checks |
|---|---|
| `EngineTest` | parsing; only selected fields returned; per-item = 1 + 10 + 8 calls, batched = 3; same response either way; static cost equals the work done |
| `ExperimentsTest` | the screen table's conclusions; each nesting level multiplies work by the page size; batching keeps calls linear but not work |
| `CustomerGraphControllerTest` | `@GraphQlTest` with graphql-java: the screen costs 3 service calls; `first: 500` is `BAD_REQUEST`; a complexity-14,443 query is rejected with no service called |
