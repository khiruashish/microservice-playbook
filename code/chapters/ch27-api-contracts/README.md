# Chapter 27 — API Contracts & Schema Evolution

order-service's Order API (`GET /orders/{id}`) and the order-events topic, their contract documents,
and what catches breaking changes.

| Package | What it is |
|---|---|
| `compat/` | `Schema`, `Consumer` (how each consumer decodes: strict or tolerant), `StructuralDiff` (oasdiff-style checks), `ContractChecks` (consumer-driven contract verification), `ChangeHistory` (12 changes × 5 consumers) |
| `api/` | `OrderController`, `OrderView`; `static/contracts/order-api.yaml` (OpenAPI 3.1) and `order-events.asyncapi.yaml` (AsyncAPI 3.0); `SpecDriftTest`; Spring Cloud Contract base class and contracts in `src/test/resources/contracts/tracking-service` |
| `consumer/` | `OrderSummary`: a tolerant reader with Jackson 3 (`@JsonEnumDefaultValue`) |
| `verify/` | `validate_contracts.py`: the documents against the official OpenAPI 3.1 and AsyncAPI 3.0.0 JSON Schemas, and `sample-responses.jsonl` (OrderView as Jackson 3 writes it) against the `Order` schema |

## Run

```bash
cd code
mvn -B -pl chapters/ch27-api-contracts -am verify     # includes the tests generated from the contracts
java -cp chapters/ch27-api-contracts/target/classes dev.foodfleet.ch27.compat.ChangeHistory
python3 chapters/ch27-api-contracts/verify/validate_contracts.py <oas-3.1 schema.yaml> <asyncapi 3.0.0.json> \
    chapters/ch27-api-contracts/verify/sample-responses.jsonl
```

## Verified output

The change history is a model of FoodFleet's consumers (how each decodes, what it reads), run on a JDK 21
runtime (the module targets JDK 25):

```text
12 changes to GET /orders/{id}; 5 consumers (2 strict on unknown fields or enums, 3 with contracts)

#   change                                             breaks (as built)              breaks (tolerant readers)      diff   contracts
1   add optional field promisedBy                      mobile                         -                              NONE   pass     
2   add status READY_FOR_PICKUP                        mobile, notification           -                              WARN   pass     
3   rename eta to etaMinutes                           mobile, notification           mobile, notification           ERROR  FAIL     
4   riderName absent until a rider is assigned         mobile                         mobile                         ERROR  pass     
5   totalPaise sent as a string (decimal safety)       web-app, analytics             web-app, analytics             ERROR  FAIL     
6   remove deprecated legacyCode (nobody reads it)     -                              -                              ERROR  pass     
7   add restaurant.cuisine                             mobile                         -                              NONE   pass     
8   etaMinutes now counts to pickup, not delivery      mobile, notification           mobile, notification           NONE   pass     
9   placedAt as epoch millis                           analytics                      analytics                      ERROR  FAIL     
10  remove restaurant.area (moved to /restaurants)     web-app                        web-app                        ERROR  pass     
11  unknown order: 200 with empty body instead of 404  mobile, web-app, notification  mobile, web-app, notification  NONE   FAIL     
12  split CANCELLED into BY_CUSTOMER / BY_RESTAURANT   mobile, notification           notification                   ERROR  pass     

as built:         11 of 12 changes break a consumer; diff fails the build for 6 of them (plus 1 that broke nobody); contracts for 4; either: 7; missed by both: 4

tolerant readers: 8 of 12 changes break a consumer; diff fails the build for 6 of them (plus 1 that broke nobody); contracts for 4; either: 7; missed by both: 1
```

`TolerantReaderTest` was run against Jackson 3.0 (jackson-databind 3.0 branch, built from source):
unknown fields are ignored by default, unknown enum values fail by default, and map to the
`@JsonEnumDefaultValue` constant with `EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE`.

Contract documents and responses, validated with jsonschema 4.26 against the official schemas
(OpenAPI `v3.1-dev` branch `src/schemas/validation/schema.yaml`; AsyncAPI `3.0.0-without-$id.json`):

```text
order-api.yaml against the OpenAPI 3.1 schema: valid
order-events.asyncapi.yaml against the AsyncAPI 3.0.0 schema: valid
response o-9001 against components.schemas.Order: valid
response o-9002 against components.schemas.Order: valid
```

## Tests

| Test | Checks |
|---|---|
| `CompatibilityTest` | strict readers break on additive changes; the diff's false alarm; the behaviour change only contracts catch; the meaning change nothing catches |
| `TolerantReaderTest` | Jackson 3 defaults and the tolerant enum setting |
| `SpecDriftTest` | `@WebMvcTest`: operations in `order-api.yaml` = operations implemented |
| generated `ContractVerifierTest` | Spring Cloud Contract: each consumer contract run against `OrderController` |
