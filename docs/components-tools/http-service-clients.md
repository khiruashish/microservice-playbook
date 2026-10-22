---
title: "REST Clients & HTTP Interfaces"
description: "How FoodFleet's Spring Boot 4 services call each other over HTTP: @HttpExchange interfaces in client groups on RestClient, configured per destination with mandatory timeouts; WebClient only for reactive and streaming code; RestTemplate and OpenFeign retired. Measured: a client per call cost 7× at the median over TLS, a call without a timeout was still blocked when the test stopped watching, and 1,000 slow calls took 560 ms on virtual threads against 4.2 s on a 50-thread pool."
progress: done
---

# REST Clients & HTTP Interfaces

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Spring's HTTP clients: `RestClient` (blocking, fluent), `WebClient` (reactive), and HTTP service clients: a Java interface annotated with `@HttpExchange`, implemented at run time on either |
| **FoodFleet uses** | HTTP service clients on RestClient, one group per destination (`@ImportHttpServices`), configured with `spring.http.serviceclient.<group>.*`, on virtual threads |
| **Decided in** | [ADR-022](../communication/ch22-http-clients.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 22](../communication/ch22-http-clients.md), [lab 03](../code-lab/lab-03-two-services.md), [lab 08](../code-lab/lab-08-oauth2-keycloak.md) (token relay) |
| **Related** | [Resilience4j](resilience4j.md), [Spring Framework 7 resilience](spring-framework-resilience.md), [WireMock](wiremock.md), [Load balancing](load-balancing.md) |

## Which client

| Client | Use it for | Status in Spring 7 |
|---|---|---|
| **HTTP service client** (`@HttpExchange` interface) on `RestClient` | Calls between MVC services | FoodFleet's default |
| `RestClient` directly | One-off calls, unusual requests | Current |
| `WebClient` | WebFlux services, streaming responses | Current |
| `RestTemplate` | Nothing new | Deprecation announced; migrate |
| Spring Cloud OpenFeign | Nothing new | Feature-complete; HTTP service clients replace it |

## The interface and its group

```java title="MenuClient.java (lab 03)"
--8<-- "code/code-lab/lab-03-two-services/src/main/java/dev/foodfleet/lab03/MenuClient.java:9:25"
```

```java title="ClientsConfig.java (lab 03)"
--8<-- "code/code-lab/lab-03-two-services/src/main/java/dev/foodfleet/lab03/ClientsConfig.java"
```

```yaml title="application.yml (lab 03)"
--8<-- "code/code-lab/lab-03-two-services/src/main/resources/application.yml:1:9"
```

- **One group per destination**: base URL, timeouts and interceptors are set once for every interface in the group.
- **Timeouts are mandatory** in FoodFleet's services: no default is safe for a remote call.
- **The interface is the caller's view** of the API: its own copy of the response records, tolerant of fields it doesn't read ([ADR-027](../communication/ch27-api-contracts.md#8-enterprise-case-study)).
- **Group configurers** add what every group needs: RFC 9457 problem details mapped to exceptions, the user's token relayed ([lab 08](../code-lab/lab-08-oauth2-keycloak.md)), a user agent.
- **Tests fake the interface**, or point the group's base URL at a stub server ([WireMock](wiremock.md)).

## Measured

[Chapter 22](../communication/ch22-http-clients.md), the JDK's `HttpClient` (one of RestClient's engines) on a 2-vCPU container:

| Decision | Result |
|---|---|
| One shared client vs a new client per call, 300 GETs over HTTPS | p50 **2.14 ms** vs **15.75 ms**: 7× for the TLS handshake alone, on loopback |
| No timeout vs a 300 ms request timeout, against a 60 s endpoint | Still blocked after 3,005 ms vs `HttpTimeoutException` after 302 ms |
| 1,000 calls to a 200 ms endpoint: 50-thread pool / virtual threads / `sendAsync` | 4,173 ms / **560 ms** / 1,374 ms |

- **Build clients once.** A client per call pays a handshake every time, and so does the server.
- **The pool's time is arithmetic**: 1,000 ÷ 50 × 200 ms = 4 s. Virtual threads run the same blocking code without the cap, which is why ADR-022 retired WebClient-with-`.block()` in MVC services.
- **Without a timeout a hung server holds the caller**, its thread and a connection for as long as the server likes.

## Settings that matter

| Setting | Why |
|---|---|
| `connect-timeout` | A dead host fails fast instead of waiting for TCP's retries |
| `read-timeout` | The budget per call; derived from the caller's own latency budget ([chapter 44](../resilience/ch44-timeouts-retries-backpressure.md)) |
| Connection lifetime (engine-specific) | Keep-alive pools otherwise ignore new pods ([load balancing](load-balancing.md)); ADR-054 caps it at 30 s |
| Engine choice | JDK client by default; Reactor Netty or Apache HttpClient 5 for lifetime caps and pool metrics |

## Pitfalls

- **`.block()` on WebClient in an MVC service**: two programming models and none of the benefits.
- **Retrying in the client and in the mesh**: attempts multiply; with the mesh retrying, clients keep timeouts and drop retries ([ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study)).
- **Sharing the provider's DTO jar**: every provider release forces a consumer release.
- **Treating every 4xx as an error to retry**: a `404` will be a `404` again ([lab 06](../code-lab/lab-06-resilience.md)).
