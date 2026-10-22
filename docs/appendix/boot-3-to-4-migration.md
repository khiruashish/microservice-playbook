---
title: "Spring Boot 3 → 4 Migration Checklist"
description: "A step-by-step checklist for moving a fleet of Spring Boot 3 microservices to Spring Boot 4: prepare on 3.5, migrate a pilot with OpenRewrite, then the platform changes area by area (modular starters, Jackson 3, Jakarta EE 11 and Undertow, testing, security, HTTP clients, resilience, observability, startup), each with what breaks and how to catch it, drawn from the chapters and their checks against Spring's source."
progress: done
---

# Spring Boot 3 → 4 Migration Checklist

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Spring Boot 4 moves to Spring Framework 7, Jakarta EE 11 and Jackson 3, and splits Boot itself into a module per technology. For one service that's a day's work; for thirty it's a programme. This checklist is in the order that keeps services shippable at every step. Each item says what breaks if you skip it and how to catch it. [Chapter 8](../foundations/ch08-spring-boot-4-essentials.md) explains the changes; the handbook's code targets Spring Boot 4.1.

!!! note "Verify against the release notes"
    The facts here were checked against Spring's source and documentation by the chapters' `check_apis.sh` scripts at the versions they pin. Minor releases add and change things: read the [Spring Boot 4.0 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes), and those of the version you're moving to.

## Phase 1: prepare on Boot 3

- [ ] **Upgrade every service to the latest 3.5.x.** APIs deprecated in 3.x are the ones removed in 4.0; the compiler's deprecation warnings are your to-do list.
- [ ] **Clear every deprecation warning**, with `-Xlint:deprecation` failing the build if you can.
- [ ] **Java 17 at least; 25 recommended.** Boot 4 needs 17; Java 25 brings the AOT cache and virtual threads without `synchronized` pinning ([ch. 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md)).
- [ ] **Contract tests around every JSON boundary** before you touch Jackson: consumer contracts between services ([ch. 63](../testing/ch63-contract-testing.md)) and recorded payloads from partners. These are what catch Jackson 3's changed defaults.
- [ ] **A CI step that starts the packaged jar**, not just the tests: `java -jar app.jar --spring.context.exit=onRefresh`. Chapter 8's incident was a service whose tests passed and whose first canary pod crashed at startup.
- [ ] **A dashboard of Boot versions per service**, from the build files or the catalog ([ch. 72](../governance-org/ch72-platform-engineering.md)), so progress is visible.

## Phase 2: one pilot service

- [ ] Run OpenRewrite's `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` (rewrite-spring) to update the build, starters, packages and properties.
- [ ] Add `spring-boot-properties-migrator` temporarily: it reports renamed properties at startup. Remove it afterwards.
- [ ] If the build doesn't compile quickly, use `spring-boot-starter-classic` (and `spring-boot-starter-test-classic`) **as a bridge only**, then move to modular starters.
- [ ] Ship the pilot through a canary ([ch. 58](../deployment/ch58-cicd-gitops.md)), and write down every surprise for the other teams.
- [ ] Publish the Boot 4 golden-path template from what the pilot learned ([ch. 72](../governance-org/ch72-platform-engineering.md)).

## Phase 3: area by area

### Starters and dependencies

- [ ] **Declare a starter for every technology your main code uses**: `spring-boot-starter-webmvc`, `-restclient`, `-webclient`, `-jdbc`, `-flyway`, and so on.
- [ ] **Check that each starter is in `main` scope, not only in tests.** *Breaks:* a module that arrives transitively through a test starter is on the test classpath only; tests pass and production fails to start (chapter 8: no `RestClient.Builder`). *Catch:* the packaged-jar start in CI.
- [ ] **Replace Undertow** with Tomcat or Jetty: Undertow doesn't support Servlet 6.1 and is gone.
- [ ] **Remove the classic starters** once everything compiles; they put every module on the classpath.

### JSON: Jackson 3

| Change | Breaks | Do |
|---|---|---|
| Packages `com.fasterxml.jackson.*` → `tools.jackson.*` (annotations stay) | Compilation, custom serializers | Let OpenRewrite move imports; review custom (de)serializers by hand |
| `ObjectMapper` → immutable `JsonMapper` | Code that reconfigures a shared mapper | `JsonMapper.builder()`, `mapper.rebuild()`; customise through `JsonMapperBuilderCustomizer` |
| Checked `JsonProcessingException` → unchecked `JacksonException` | Catch blocks, error mapping | Update error handling; check RFC 9457 error mapping still fires ([ch. 21](../communication/ch21-rest-api-design.md)) |
| Dates as ISO-8601 strings by default | Consumers expecting epoch numbers | Contract tests decide; set the old behaviour explicitly where a consumer needs it |
| `null` for a primitive is an error | Partner payloads with `null` numbers (chapter 8: 400s from a POS integration) | Make the field nullable and decide the default in your anti-corruption layer |
| Properties sorted alphabetically | Consumers or signatures that depend on order | Fix the consumer; order should never be a contract |
| Trailing tokens after the JSON value are errors | Sloppy clients | Fix the client, or relax the feature for that endpoint only |
| `spring.jackson.read.*`/`write.*` → `spring.jackson.json.read.*`/`json.write.*` | Silent: the old properties are ignored | The properties migrator reports them |

- [ ] Start with `spring.jackson.use-jackson2-defaults=true`, then turn the defaults on **one at a time**, each behind contract tests. Give the flag an end date: two JSON behaviours across a fleet is its own bug.

### Testing

- [ ] `@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean`.
- [ ] `@SpringBootTest` no longer configures MockMvc or `TestRestTemplate` by itself: add `@AutoConfigureMockMvc` or `@AutoConfigureTestRestTemplate`.
- [ ] Test starters per technology (`spring-boot-starter-webmvc-test`, `spring-boot-starter-jdbc-test`).
- [ ] Prefer `MockMvcTester` (AssertJ-style) and `RestTestClient`.
- [ ] Move to Testcontainers 2 with `@ServiceConnection`, one container per test run, and remove H2 if you still test SQL on it ([ch. 62](../testing/ch62-unit-slice-integration-tests.md)).

### Security: Spring Security 7

- [ ] **The lambda DSL only**: chained `.and()` configuration no longer exists ([ch. 37](../security/ch37-spring-security-7.md)).
- [ ] **The authorization server is part of Spring Security** (`http.oauth2AuthorizationServer(...)`); move off the separate Spring Authorization Server dependency.
- [ ] Consider RFC 9068 access-token validation (`JwtValidators.createAtJwtValidator()`) and check audiences on every resource server ([ch. 36](../security/ch36-oauth2-oidc-jwt.md)).
- [ ] Re-run the authorization tests: deny-by-default rules, method security.

### Calling other services

- [ ] Move `RestTemplate` and Feign clients to `@HttpExchange` interfaces registered with `@ImportHttpServices`, one group per destination, with `spring.http.serviceclient.<group>.*` for base URL and timeouts ([ch. 22](../communication/ch22-http-clients.md), [ADR-022](../communication/ch22-http-clients.md)).
- [ ] With Spring Cloud 2025.1, a group with no `base-url` (or an `lb://` one) is load-balanced through the `DiscoveryClient` using the group name as the service id ([ch. 11](../architecture-patterns/ch11-service-discovery-patterns.md)). Check that's what you intend.
- [ ] Set connect and read timeouts on every group; there's no safe default for a remote call ([ch. 42](../resilience/ch42-failure-modes.md)).

### Resilience

- [ ] Framework 7's `@Retryable` and `@ConcurrencyLimit` can replace Spring Retry for simple cases; keep Resilience4j for circuit breakers ([ch. 43](../resilience/ch43-resilience4j-deep-dive.md)).
- [ ] **Turning on virtual threads removes an accidental limit.** A 200-thread Tomcat pool had been limiting concurrency; without it, thousands of requests reached a 30-connection pool and a rate-limited provider (chapter 8, incident 3). Add explicit limits before you switch.
- [ ] Don't pool virtual threads; limit the resource, not the threads.

### Observability

- [ ] Micrometer Tracing over OpenTelemetry; no Sleuth ([ch. 49](../observability/ch49-distributed-tracing-otel.md)).
- [ ] Structured logging with `logging.structured.format.console: ecs` ([ch. 50](../observability/ch50-structured-logging.md)).
- [ ] Check that histogram and SLO bucket properties still apply after the upgrade; an SLO without a bucket at its objective misreads its budget ([ch. 51](../observability/ch51-slo-alerting.md)).

### API versioning

- [ ] Replace home-grown versioning with the built-in support (`@GetMapping(version = …)`, `spring.mvc.apiversion.*`) where you need several versions at once; prefer compatible evolution where you don't ([ch. 8](../foundations/ch08-spring-boot-4-essentials.md), [ch. 27](../communication/ch27-api-contracts.md)).

### Null safety

- [ ] Spring Framework 7 and Boot 4 are annotated with JSpecify (`@NullMarked`, `@Nullable`). Optionally add NullAway to check your code against them.

### Startup and containers

- [ ] Add an AOT cache from a training run in the Dockerfile (Java 25): first response at 1 CPU went from 1.1–1.2 s to 0.6–0.7 s in chapter 60 ([ch. 60](../deployment/ch60-native-images-startup.md)).
- [ ] Re-check `MaxRAMPercentage` and the container limits; Boot 4's smaller classpaths change native memory ([ch. 67](../performance-scalability/ch67-jvm-tuning-containers.md)).

## Phase 4: the fleet

- [ ] Roll out team by team through canaries; one Boot version per service at a time.
- [ ] Track the version dashboard to zero Boot 3 services.
- [ ] Remove `use-jackson2-defaults`, classic starters and the properties migrator everywhere, on a date.
- [ ] Record the migration as an ADR, with what you'd do differently ([ch. 73](../governance-org/ch73-adrs-fitness-functions.md)).
