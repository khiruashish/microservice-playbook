---
title: "Lab 19 — Contracts & Fitness Functions in CI"
description: "The checks a FoodFleet pull request must pass before it merges: order-service's Pact contract with menu-service, verified in menu-service's build against the real controller; architecture rules in ArchUnit, with a frozen baseline for legacy code; and a CI workflow that runs them with the OpenAPI diff, can-i-deploy and the platform's chart rules. Then the measured part: 12 API changes against an OpenAPI diff, contracts and E2E (contracts caught 7 of 9 breaking changes with no false alarms, the diff 5 with 2), and 10 planted architecture violations (8 caught; an inlined constant and a class loaded by name never are). About an hour."
progress: done
---

# Lab 19 — Contracts & Fitness Functions in CI

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **You'll build** | A consumer contract, its provider verification, architecture rules, and the pull-request workflow that runs them |
| **Time** | About an hour |
| **Builds on** | [Lab 03](lab-03-two-services.md) (order-service calling menu-service) and [lab 15](lab-15-kubernetes-helm-keda.md) (the platform rules) |
| **Chapters** | [63 (contract testing)](../testing/ch63-contract-testing.md), [65 (architecture tests)](../testing/ch65-performance-chaos-arch-tests.md), [73 (ADRs and fitness functions)](../governance-org/ch73-adrs-fitness-functions.md), [27 (API contracts)](../communication/ch27-api-contracts.md) |
| **Code** | `code/code-lab/lab-19-contracts-archunit/` |
| **Needs** | JDK 25 for `verify/run.sh`; Maven for the tests |

!!! note "About the lab code"
    `verify/run.sh` **was run** with JDK 25.0.4: it compiles and runs [chapter 63](../testing/ch63-contract-testing.md)'s contract experiment and [chapter 65](../testing/ch65-performance-chaos-arch-tests.md)'s architecture experiment, which implement Pact's matching and ArchUnit's bytecode reading in plain Java; the results matched both chapters. The Spring, Pact 4.7.5 and ArchUnit 1.5.1 tests are checked by import against their source, not compiled or run here ([how](index.md#how-the-lab-code-was-checked)); the Pact Spring 7 provider API was taken from its module's README at that tag. `ci/pull-request.yml` is an example and wasn't run.

## 1. order-service writes down what it needs

```java title="ConsumerOrderServiceTest.java"
--8<-- "code/code-lab/lab-19-contracts-archunit/src/test/java/dev/foodfleet/lab19/ConsumerOrderServiceTest.java:18:67"
```

The test runs order-service's real client against Pact's mock server, which answers only what the contract says, and writes the contract to a file (in FoodFleet, to the Pact Broker). Two choices matter:

- **Only what's read.** `MenuPriceClient` reads `price` and `available`, so that's all the contract mentions. menu-service may add fields freely.
- **Formats, not values.** `stringMatcher("price", "^\\d+\\.\\d{2}$", "129.50")` accepts any price with two decimals. A new price on the menu passes; a price in paise (`"12950"`) fails.

## 2. menu-service checks every consumer's contract in its own build

```java title="ProviderMenuServiceTest.java"
--8<-- "code/code-lab/lab-19-contracts-archunit/src/test/java/dev/foodfleet/lab19/ProviderMenuServiceTest.java:22:60"
```

Each interaction becomes a test, replayed against the real controller through MockMvc after its **provider state** has set up the data. A menu-service change that breaks order-service now fails in **menu-service's** pull request, before it merges, instead of in an environment after it deploys. `can-i-deploy` then asks the broker whether this version has been verified against every consumer version in production ([ADR-063](../testing/ch63-contract-testing.md#8-enterprise-case-study)).

## 3. Architecture rules

```java title="ArchitectureTest.java"
--8<-- "code/code-lab/lab-19-contracts-archunit/src/test/java/dev/foodfleet/lab19/ArchitectureTest.java:14:50"
```

The rules are ordinary tests: they read the compiled classes and fail the build like any assertion. `FreezingArchRule` is how an old service adopts a rule it already breaks: today's violations are recorded, and only new ones fail.

## 4. The pull request

```yaml title="ci/pull-request.yml"
--8<-- "code/code-lab/lab-19-contracts-archunit/ci/pull-request.yml"
```

Every check comes from an ADR, and every check but one blocks the merge. The OpenAPI diff is shown for review: section 5 shows why.

## 5. Measured: what each check catches

`verify/run.sh` replays chapter 63's 12 changes to menu-service against both consumers' real code and all of menu-service's data, so "actually breaks" is known, then asks each check:

```console
$ JAVA_HOME=/path/to/jdk-25 code/code-lab/lab-19-contracts-archunit/verify/run.sh
    change                                     OpenAPI    contract:  contract:  E2E        | actually breaks
                                               diff       order-svc  app-bff    (rest. 7)  | (all data)
C1  field renamed: available -> inStock        FLAGS      FLAGS      FLAGS      FLAGS      | order-service, app-bff
C2  optional field removed: description        passes     passes     FLAGS      FLAGS      | app-bff
C3  unused field removed: calories             FLAGS      passes     passes     passes     | nothing
C4  price sent as a number                     FLAGS      passes     passes     passes     | nothing
C5  enum value added: spice EXTRA_HOT          FLAGS      passes     passes     passes     | app-bff
C6  price null when sold out                   FLAGS      FLAGS      FLAGS      FLAGS      | order-service, app-bff
C7  unknown item: 200 unavailable, not 404     passes     FLAGS      passes     FLAGS      | order-service
C8  path renamed: /items/ -> /menu-items/      FLAGS      FLAGS      passes     FLAGS      | order-service
C9  new required header X-Client-Version       FLAGS      FLAGS      FLAGS      FLAGS      | order-service, app-bff
C10 field added: allergens                     passes     passes     passes     passes     | nothing
C11 price in paise, still a string             passes     FLAGS      FLAGS      FLAGS      | order-service, app-bff
C12 menu paged: 20 items and a next link       passes     passes     passes     passes     | app-bff

9 of 12 changes break a consumer
   OpenAPI diff             caught  5 of 9, false alarms 2,    1.3 ms per change
   order-service contract   caught  6 of 9, false alarms 0,   16.5 ms per change
   app-bff contract         caught  5 of 9, false alarms 0,    9.1 ms per change
   E2E on restaurant 7      caught  7 of 9, false alarms 0,   23.6 ms per change
```

- **The diff sees the document, not the consumers.** It flagged removing a field nobody reads (C3) and missed a meaning change that kept the schema (C11, paise in the same string field).
- **Each contract sees only its consumer**, which is why it has no false alarms; together the two caught 7 of 9.
- **Two breaks nobody caught**: a new enum value (C5) and paging (C12) broke app-bff in ways no example covered. That's the case for tolerant readers ([ADR-027](../communication/ch27-api-contracts.md#8-enterprise-case-study)): unknown enum values mapped to a default, and lists that don't assume completeness.

Chapter 65's architecture experiment plants 10 violations in a small hexagonal service:

```console
     planted violation                                layered arch slices free  domain free  naming in ap no standard
V1   domain uses an API type                          caught (1)   caught (2)   caught (1)   -            -
V4   controller reaches into infrastructure           caught (1)   -            -            -            -
V5   domain uses java.sql                             -            -            caught (1)   -            -
V8   domain uses an infrastructure constant           -            -            -            -            -
V10  domain loads an infrastructure class by name     -            -            -            -            -
missed by every rule: [V8, V10]
frozen: a legacy service with V1 and V5 has 5 violations, all stored; after V2 is added, 8 violations, 3 of them new and reported
   the sample service                     10 classes   read     3.1 ms   all 5 rules     0.5 ms
```

(Five of the ten rows shown.) Eight were caught in about 4 ms. The two that weren't are worth knowing: the compiler copies a `static final` constant's value into the class that uses it, so no dependency remains in the bytecode (V8), and `Class.forName("…")` is a string (V10). Architecture rules are a floor, not a proof.

## Run it

```bash
code/code-lab/lab-19-contracts-archunit/verify/run.sh               # both experiments, ~40 s
cd code/code-lab/lab-19-contracts-archunit && mvn verify            # consumer, then provider, then architecture
```

## Check yourself

- [ ] `mvn verify` writes `target/pacts/order-service-menu-service.json` and the provider test verifies both interactions.
- [ ] You can say which of the 12 changes only a contract catches, and which only the diff catches.
- [ ] You can explain why an inlined constant is invisible to ArchUnit.

## Break it

!!! question "Exercises"
    1. Make `PriceController` return the price in paise (`"12950"`). Which test fails, and in which service's build?
    2. Change the contract to match `price` by type only (`stringType`). Repeat exercise 1. What does that teach about matchers?
    3. Inject `InMemoryMenuItems` directly into `PriceController`. Which rule fails, and what does its message say?
    4. Add a `PriceCalculator` in `domain` that calls `System.currentTimeMillis()`. Does any rule catch it? Should one?

**Next:** [Lab 20](lab-20-chaos-game-day.md) breaks FoodFleet on purpose, in a game day.
