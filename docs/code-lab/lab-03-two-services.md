---
title: "Lab 03 — Two Services Talking"
description: "Add order-service, which prices an order by calling menu-service through a Spring Boot 4 HTTP service client: an @HttpExchange interface registered in a group, timeouts set per group, a tolerant reader, the other service's errors translated into this service's own, and tests against a real HTTP server, including one that proves a slow dependency fails at the read timeout. About an hour."
progress: done
---

# Lab 03 — Two Services Talking

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **You'll build** | order-service's `POST /quotes`, priced from menu-service's current menu |
| **Time** | About an hour |
| **Builds on** | [Lab 01](lab-01-hello-microservice.md) (menu-service runs on 8080) |
| **Chapters** | [22 (HTTP clients)](../communication/ch22-http-clients.md), [27 (contracts)](../communication/ch27-api-contracts.md), [42 (failure modes)](../resilience/ch42-failure-modes.md) |
| **Code** | `code/code-lab/lab-03-two-services/` |

!!! note "About the lab code"
    Written for Spring Boot 4.1.1 and checked by import against its libraries' source, not compiled here ([how](index.md#how-the-lab-code-was-checked)). The property names were checked in Spring Boot's `HttpClientProperties`.

```mermaid
flowchart LR
    C(["client"]) -->|"POST /quotes<br/>dish ids, quantities"| O["order-service :8090"]
    O -->|"GET /restaurants/{id}/menu<br/>timeout 1 s"| M["menu-service :8080"]
```

## 1. The other service's API, as an interface

```java title="MenuClient.java"
--8<-- "code/code-lab/lab-03-two-services/src/main/java/dev/foodfleet/lab03/MenuClient.java"
```

order-service declares only the fields it uses. When menu-service adds `spiceLevel` next month, order-service doesn't notice: that's the tolerant reader that lets services change independently ([ADR-027](../communication/ch27-api-contracts.md)).

## 2. Register it in a group, configure the group

```java title="ClientsConfig.java"
--8<-- "code/code-lab/lab-03-two-services/src/main/java/dev/foodfleet/lab03/ClientsConfig.java"
```

```yaml title="application.yml"
--8<-- "code/code-lab/lab-03-two-services/src/main/resources/application.yml"
```

**One group per destination**, so a 50 ms service and a 2 s one don't share timeouts ([ADR-022](../communication/ch22-http-clients.md)). **Timeouts are mandatory**: a call without a read timeout waits as long as the other side does, and chapter 45 measured a breaker that never noticed a black-holed dependency because no call ever finished.

The pom declares `spring-boot-starter-restclient` in main scope. Chapter 8's incident was a service that got the RestClient module only through a test starter: green tests, then a crash on startup in production.

## 3. Translate the other service's failures

```java title="QuoteService.java (calling and translating)"
--8<-- "code/code-lab/lab-03-two-services/src/main/java/dev/foodfleet/lab03/QuoteService.java:50:61"
```

Each failure means something different to the caller:

| menu-service | order-service answers | Why |
|---|---|---|
| `404` | `404`: no such restaurant | The client asked for something that doesn't exist |
| Other `4xx`/`5xx` | `500` as written; it should be `502`, which is exercise 2 | menu-service failed, not the client |
| Timeout, refused connection | `503` with `retryable: true` | Temporary; worth retrying later |

Never pass the other service's error body through: it's that service's contract, not yours.

## 4. Test against a real server

```java title="QuoteServiceTest.java"
--8<-- "code/code-lab/lab-03-two-services/src/test/java/dev/foodfleet/lab03/QuoteServiceTest.java:21:104"
```

A mocked `MenuClient` would skip everything that goes wrong in practice: the URL, the JSON mapping, status handling, the timeout. A stand-in server on a random port (here the JDK's own `HttpServer`) exercises them all. The last test proves the read timeout works: a 3-second response fails in under 2 seconds.

[Chapter 63](../testing/ch63-contract-testing.md) goes one step further: menu-service verifies order-service's expectations in its own build, as a consumer contract.

## Run it

```bash
# terminal 1: lab 01's menu-service on 8080
cd code/code-lab/lab-01-hello-microservice && mvn spring-boot:run
# terminal 2
cd code/code-lab/lab-03-two-services && mvn spring-boot:run
```

```console
$ curl -s localhost:8090/quotes -H 'Content-Type: application/json' \
    -d '{"restaurantId":"r-1","lines":[{"itemId":"m-1","quantity":2},{"itemId":"m-3","quantity":1}]}'
{"restaurantId":"r-1","lines":[{"itemId":"m-1","name":"Paneer tikka","quantity":2,"linePaise":49800},{"itemId":"m-3","name":"Masala dosa","quantity":1,"linePaise":14900}],"totalPaise":64700}
```

Now stop menu-service and repeat: order-service answers `503` at once (connection refused), not after a long wait.

## Check yourself

- [ ] `mvn verify` passes the five tests.
- [ ] With menu-service stopped, `POST /quotes` answers `503` with `retryable: true`.
- [ ] A quantity of 0 or 51 is refused with `400` before menu-service is called.

## Break it

!!! question "Exercises"
    1. Remove `read-timeout` and rerun the slow test. How long does it take now, and what would that do to order-service's threads (or virtual threads, and its callers) during a menu-service slowdown?
    2. Map other `RestClientResponseException`s to `502` with a problem document. Which status should a menu-service `400` become, and why isn't it the client's fault?
    3. Add a second group for a pricing service with a 300 ms read timeout. Where would one shared group have gone wrong?
    4. Make `Item` a strict reader (fail on unknown properties) and run `ignoresFieldsItDoesntKnow`. That's the kind of change chapter 27 measured breaking 11 of 12 "small" provider changes.

**Next:** [Lab 04](lab-04-discovery-gateway.md) puts a gateway in front and finds services without fixed URLs.
