---
title: "Lab 01 — Hello Microservice"
description: "Build FoodFleet's menu-service on Spring Boot 4: records that can't be invalid, a request type validated with Bean Validation, errors as RFC 9457 problem documents with a documented type, Actuator on its own port, virtual threads, and web-slice tests with MockMvcTester. About 45 minutes."
progress: done
---

# Lab 01 — Hello Microservice

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span></div>

| | |
|---|---|
| **You'll build** | menu-service: list a restaurant's menu, add a dish, with proper errors and health checks |
| **Time** | About 45 minutes |
| **Builds on** | [Your first microservice](../getting-started/first-microservice.md), which builds the same service with only the JDK |
| **Chapters** | [8 (Spring Boot 4)](../foundations/ch08-spring-boot-4-essentials.md), [21 (REST)](../communication/ch21-rest-api-design.md), [52 (health)](../observability/ch52-health-checks-actuator.md) |
| **Code** | `code/code-lab/lab-01-hello-microservice/` |

!!! note "About the lab code"
    The labs target Spring Boot 4.1.1 on Java 25. They were written where Maven Central wasn't reachable, so they haven't been compiled by the handbook's build. Instead, every type they import was checked against the source of the exact library versions Boot 4.1.1 manages ([how](index.md#how-the-lab-code-was-checked)). If something doesn't compile for you, the chapter it follows is the reference.

## 1. Start from the right starters

Spring Boot 4 has a starter per technology, and you declare each one your code uses ([ch. 8](../foundations/ch08-spring-boot-4-essentials.md#modular-starters)):

```xml title="code/code-lab/lab-01-hello-microservice/pom.xml (dependencies)"
--8<-- "code/code-lab/lab-01-hello-microservice/pom.xml:15:33"
```

`spring-boot-starter-webmvc` replaces Boot 3's `spring-boot-starter-web`. The test starter is per technology too.

## 2. A domain record that can't be invalid

```java title="MenuItem.java"
--8<-- "code/code-lab/lab-01-hello-microservice/src/main/java/dev/foodfleet/lab01/MenuItem.java"
```

Prices are whole paise. The compact constructor runs whichever way an item is created, so an item that costs nothing can't exist anywhere in the service.

## 3. A request type, validated at the edge

The client never sends a `MenuItem`: it sends what it's allowed to choose. The id is the service's to assign.

```java title="NewMenuItem.java"
--8<-- "code/code-lab/lab-01-hello-microservice/src/main/java/dev/foodfleet/lab01/NewMenuItem.java"
```

Request and response types per use case are a security decision as much as a design one: entities bound straight from JSON let clients set fields they shouldn't ([ADR-035](../security/ch35-security-fundamentals.md)).

## 4. The controller

```java title="MenuController.java"
--8<-- "code/code-lab/lab-01-hello-microservice/src/main/java/dev/foodfleet/lab01/MenuController.java"
```

`@Valid` makes Spring validate the body before your method runs. Creating a dish answers `201 Created` with a `Location` header, as HTTP intends.

## 5. Errors callers can parse

```java title="ApiErrors.java"
--8<-- "code/code-lab/lab-01-hello-microservice/src/main/java/dev/foodfleet/lab01/ApiErrors.java"
```

```yaml title="application.yml"
--8<-- "code/code-lab/lab-01-hello-microservice/src/main/resources/application.yml"
```

With `spring.mvc.problemdetails.enabled`, Spring's own errors (a failed validation, a wrong method, an unreadable body) are problem documents too, so a caller sees one error format whatever went wrong. The Actuator listens on port 8081, which a production cluster exposes only to monitoring ([ADR-052](../observability/ch52-health-checks-actuator.md)).

## 6. Test the web layer

`@WebMvcTest` starts only the web layer: controller, advice, validation and JSON. In Boot 4 it lives in the webmvc test module, and `MockMvcTester` gives AssertJ-style assertions:

```java title="MenuControllerTest.java"
--8<-- "code/code-lab/lab-01-hello-microservice/src/test/java/dev/foodfleet/lab01/MenuControllerTest.java:12:65"
```

## Run it

```bash
cd code/code-lab/lab-01-hello-microservice
mvn spring-boot:run
```

```console
$ curl -s localhost:8080/restaurants/r-1/menu?veg=true
{"restaurantId":"r-1","items":[{"id":"m-1","name":"Paneer tikka","pricePaise":24900,"vegetarian":true},{"id":"m-3","name":"Masala dosa","pricePaise":14900,"vegetarian":true}]}

$ curl -s -X POST localhost:8080/restaurants/r-1/menu -H 'Content-Type: application/json' -d '{"name":"","pricePaise":0}'
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Invalid request content.","instance":"/restaurants/r-1/menu"}

$ curl -s localhost:8081/actuator/health/readiness
{"status":"UP"}
```

The responses above show the expected shape; field order and the 400's detail text come from Spring and Jackson 3 and may differ slightly.

## Check yourself

- [ ] `mvn verify` passes the five tests.
- [ ] An unknown restaurant answers `404` with `Content-Type: application/problem+json` and your type URI.
- [ ] `POST` with an empty name answers `400` as a problem document, and nothing is added.
- [ ] `/actuator/health/liveness` answers on 8081, and not on 8080.

## Break it

!!! question "Exercises"
    1. Remove `@Valid`. What does the empty-name request do now, and where does it fail?
    2. Send `"pricePaise": null`. Jackson 3 refuses `null` for a primitive; what status do you get, and why is that better than Jackson 2's silent 0 ([ch. 8, incident 2](../foundations/ch08-spring-boot-4-essentials.md#7-anti-patterns-pitfalls))?
    3. Add a field `discountPercent` to `MenuItem` but not to `NewMenuItem`. Can a client set it? Now bind `MenuItem` directly in the controller and try again.
    4. Turn off `spring.mvc.problemdetails.enabled` and send `DELETE /restaurants/r-1/menu`. What does a caller get?

**Next:** [Lab 02](lab-02-persistence.md) moves the menus into PostgreSQL.
