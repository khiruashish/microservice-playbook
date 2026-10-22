---
title: "Contract Testing: Pact & Spring Cloud Contract"
description: "Consumer-driven contracts at FoodFleet (ADR-063): Pact for HTTP between services, written by consumers from their client tests with type matchers and formats, verified by the provider for the main branch and every deployed version, and can-i-deploy before each deployment; oasdiff on provider pull requests. Measured on twelve menu-service changes: the OpenAPI diff flagged harmless changes and missed two that broke a consumer; recorded-example contracts failed on every data change; type matchers plus formats caught 7 of 9 breaking changes with no false alarms. Lab 19 runs it with Pact V4."
progress: done
---

# Contract Testing: Pact & Spring Cloud Contract

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | **Pact**: consumers record interactions (with matchers) in a mock-server test; providers replay them; a **broker** stores contracts and verification results and answers `can-i-deploy`. **Spring Cloud Contract**: contracts (Groovy/YAML) usually live with the provider, which generates tests from them and WireMock stubs for consumers |
| **FoodFleet uses** | Pact for HTTP between services; Spring Cloud Contract where teams already use it; oasdiff on provider pull requests |
| **Decided in** | [ADR-063](../testing/ch63-contract-testing.md#8-enterprise-case-study), [ADR-027](../communication/ch27-api-contracts.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 63](../testing/ch63-contract-testing.md), [lab 19](../code-lab/lab-19-contracts-archunit.md), [chapter 27](../communication/ch27-api-contracts.md) |
| **Related** | [OpenAPI & springdoc](openapi-springdoc.md), [AsyncAPI](asyncapi.md), [WireMock](wiremock.md), [Testcontainers](testcontainers.md) |

## Which check catches which change

[Chapter 63](../testing/ch63-contract-testing.md), selected rows of twelve menu-service changes:

| Change | OpenAPI diff | order-service contract | app-bff contract | Actually breaks |
|---|---|---|---|---|
| C2 optional field removed | INFO | passes | **fails** | app-bff |
| C3 unused field removed | **ERR** | passes | passes | nothing |
| C4 price as a number, not a string | **ERR** | passes | passes | nothing |
| C5 enum value added | ERR | passes | passes | **app-bff** |
| C7 unknown item: 200 instead of 404 | INFO | **fails** | passes | order-service |
| C11 price in paise, still a string | no change | **fails** | **fails** | both |

The schema diff checks the schema: noisy as a gate and blind to meaning. Contracts check what each consumer uses, but only with the data in their provider states: C5's new enum value broke app-bff and no contract had it. Provider states must cover every enum value and long lists.

## How strict a contract should be

| Contracts | Breaking caught (of 9) | Harmless API changes flagged (of 3) | Data changes flagged (of 5) |
|---|---:|---:|---:|
| Examples recorded, no matchers | 7 | **2** | **5** |
| Type matchers only | 6 | 1 | 0 |
| **Type matchers + formats** | **7** | **0** | **0** |

Recorded contracts are snapshot tests; type matchers alone missed the price in paise; a regex for "two decimals" caught it.

## In code (lab 19)

```java title="ConsumerOrderServiceTest.java (lab 19, Pact V4)"
--8<-- "code/code-lab/lab-19-contracts-archunit/src/test/java/dev/foodfleet/lab19/ConsumerOrderServiceTest.java:18:67"
```

```java title="ProviderMenuServiceTest.java (lab 19)"
--8<-- "code/code-lab/lab-19-contracts-archunit/src/test/java/dev/foodfleet/lab19/ProviderMenuServiceTest.java:22:60"
```

In production the provider pulls contracts from the broker (for main and each deployed version) instead of `@PactFolder`, and the pipeline runs `can-i-deploy --to-environment` before deploying and records the deployment.

## Pact or Spring Cloud Contract

| | Pact | Spring Cloud Contract |
|---|---|---|
| Who writes contracts | Consumers, from their tests | Usually the provider (or consumers by PR) |
| Languages | Polyglot | JVM-first |
| Deploy safety | Broker + `can-i-deploy` per environment | Stubs as versioned artifacts |
| Messaging | Message pacts | Messaging contracts |

## Pitfalls

- **Recorded examples as contracts**.
- **Contracts nobody verifies** on the provider's build, or only against main and not what's deployed.
- **Provider states with tidy data**: production has enum values and list lengths your tests don't.
- **Contracts as functional tests**: assert what the consumer needs, not the provider's business rules.
- **Skipping `can-i-deploy`**: a green build doesn't mean the deployed consumers agree.
