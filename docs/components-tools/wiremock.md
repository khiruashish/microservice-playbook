---
title: "WireMock"
description: "HTTP stubs for FoodFleet's tests and local stack: WireMock stands in for the external payment provider (authorize, capture, void) in the local stack, while service-to-service stubs come from consumer contracts (ADR-063) and client tests use Spring's MockRestServiceServer. Where hand-written stubs go wrong (they agree with the consumer, not the provider), what WireMock's fault simulation can and can't stand in for (chapter 45 needed real TCP faults: a trickle hung clients a stub's fixed delay never would), and stubs generated from contracts."
progress: done
---

# WireMock

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | An HTTP mock server: request matching (URL, headers, body), canned or templated responses, stateful scenarios, delays and faults, recording, and an admin API; runs in a JUnit test, as a standalone JAR or as a container |
| **FoodFleet uses** | A container stub for the external payment provider in the local stack; Spring's `MockRestServiceServer` in HTTP client tests; Pact mock servers for consumer contracts |
| **Learn it in** | [Local stack](../food-delivery-platform/docker-local-stack.md), [chapter 22](../communication/ch22-http-clients.md), [chapter 63](../testing/ch63-contract-testing.md) |
| **Related** | [Contract testing](contract-testing-tools.md), [Testcontainers](testcontainers.md), [Chaos tools](chaos-tools.md) |

## The payment provider, stubbed

The local stack runs `wiremock/wiremock:3.13.1` with three mappings, in the checkout and full profiles:

```json title="stubs/payment-provider/mappings/payments.json"
--8<-- "code/foodfleet/local/stubs/payment-provider/mappings/payments.json"
```

An external provider is the right place for a hand-written stub: FoodFleet can't run it, and its sandbox is slow or rate-limited.

## Between FoodFleet's own services: stubs from contracts

A hand-written stub encodes what the *consumer* believes. [Chapter 63](../testing/ch63-contract-testing.md) measured twelve menu-service changes; the contracts that consumers wrote with type matchers and formats caught 7 of 9 breaking changes with no false alarms, and recorded examples flagged every harmless data change. ADR-063 therefore uses Pact for HTTP between services, with the provider verifying every consumer's contract. Spring Cloud Contract, where teams already use it, generates WireMock stubs for consumers from contracts the provider verifies, which keeps WireMock but removes the guesswork.

For a single HTTP client's behaviour (headers, error mapping, retries), Spring's `MockRestServiceServer` binds to the `RestClient` builder with no network ([chapter 22](../communication/ch22-http-clients.md)).

## Faults: what a stub can stand in for

| Fault | WireMock | What chapter 45 found with real TCP faults (Toxiproxy) |
|---|---|---|
| Slow response | `fixedDelayMilliseconds`, chunked dribble | A **1 KB/s trickle hung** the JDK client even with a request timeout, and Spring's simple factory with a read timeout |
| Connection reset, garbage | `Fault.CONNECTION_RESET_BY_PEER`, `RANDOM_DATA_THEN_CLOSE` | Reset: `IOException` at once in every client |
| Requests swallowed, connection held | Not directly | Clients without timeouts **hung**; a circuit breaker never opened |

For fault behaviour that depends on TCP, use a proxy ([Chaos tools](chaos-tools.md)).

## Pitfalls

- **Stubs that drift from the provider**: generate them from contracts, or verify them.
- **Over-specific matching** (exact bodies): tests break on harmless changes, like recorded contracts.
- **Stubs that always succeed instantly**: [chapter 64](../testing/ch64-component-e2e-testing.md)'s component tests passed with stubs that assigned couriers at once and never ran out of stock; E2E found the real delays.
- **Shared stub servers between parallel tests** without per-test isolation.
