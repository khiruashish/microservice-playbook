---
title: "Cognizant, TCS, Infosys, Accenture"
description: "Preparing for service-company rounds, which favour implementation and migration scenarios from client work: eight scenario playbooks (upgrade to Spring Boot 4, replace Netflix OSS, containerize and deploy, add observability, secure APIs, break up a monolith, make messaging reliable, fix a slow service), each with the steps, the pitfall interviewers look for and the chapter that measured it; plus the 183 questions tagged TCS, Infosys, Cognizant, Accenture, Wipro, Thoughtworks or consulting."
progress: done
---

# Cognizant, TCS, Infosys, Accenture

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Service and consulting companies hire people to deliver for clients, so their rounds lean towards **scenarios**: "the client has X; how would you get them to Y?" Interviewers listen for a plan in steps, awareness of risk, and one or two concrete pitfalls you'd watch for. This page gives eight such scenarios as playbooks. Interview formats vary by account and band; check the current process with your recruiter.

## How to answer a scenario

1. **Clarify the starting point**: versions, team size, deadlines, what can't break.
2. **Give the plan in steps**, smallest safe step first, each one shippable.
3. **Name the risk at each step** and how you'd see it (a test, a metric, a canary).
4. **Close with how you'd know it worked**: a number, a dashboard, an SLO.

## Eight scenarios

### "Upgrade the client's services to Spring Boot 4"

- **Steps:** move to the latest 3.5.x first and clear deprecation warnings; Java 17+ (25 recommended); then Boot 4 with Spring Framework 7, Jakarta EE 11, Jackson 3; replace removed starters; run the full test suite, then a canary.
- **Pitfall to name:** Jackson 3's new package names and changed defaults reach every service that serialises JSON; contract tests between services catch what unit tests don't.
- **Read:** [ch. 8](../foundations/ch08-spring-boot-4-essentials.md) and the [Boot 3 → 4 migration guide](../appendix/boot-3-to-4-migration.md).

### "Replace Netflix OSS (Eureka, Zuul, Hystrix, Ribbon)"

- **Steps:** Hystrix → Resilience4j with explicit policies; Zuul → Spring Cloud Gateway; Ribbon → Spring Cloud LoadBalancer, or Kubernetes Services if the platform is Kubernetes; Eureka → Kubernetes DNS when on Kubernetes ([ADR-011](../architecture-patterns/ch11-service-discovery-patterns.md)).
- **Pitfall:** copying Hystrix's thread-pool timeouts into Resilience4j unchanged; library defaults were measured wrong for FoodFleet's traffic (breakers opened in 0.5–10 s depending on load) ([ch. 43](../resilience/ch43-resilience4j-deep-dive.md)).

### "Containerize the services and deploy them to Kubernetes"

- **Steps:** a multi-stage Dockerfile to a small runtime image, non-root; `MaxRAMPercentage` instead of `-Xmx`; liveness and readiness on Spring Boot's probe groups; graceful shutdown with a `preStop` drain; a shared Helm chart.
- **Pitfall:** pointing liveness at `/actuator/health`, which includes the database: a 60-second database outage caused 6 restarts of every pod ([ch. 52](../observability/ch52-health-checks-actuator.md)). And `-Xmx1g` in a 512 MB container is OOM-killed with no Java error ([ch. 53](../deployment/ch53-containerization.md)).

### "Add observability to an existing estate"

- **Steps:** structured JSON logs with the trace id; Micrometer metrics with histograms; OpenTelemetry tracing over OTLP to a collector; SLOs per user journey with burn-rate alerts.
- **Pitfall:** dashboards that average per-pod percentiles: a true p99 of 322 ms showed as 137 ms ([ch. 48](../observability/ch48-metrics-micrometer.md)).

### "Secure the APIs with OAuth 2"

- **Steps:** an identity provider (Keycloak or the client's own); every service a resource server checking issuer, audience and lifetime; deny by default; object-level checks in the service; token exchange for calls on a user's behalf.
- **Pitfall:** tokens accepted by every API because nobody checked the audience ([ch. 36](../security/ch36-oauth2-oidc-jwt.md), [ch. 37](../security/ch37-spring-security-7.md)).

### "Break up the monolith"

- **Steps:** measure coupling and change history first; make it a modular monolith with enforced boundaries; extract one context at a time by strangler fig with shadow traffic.
- **Pitfall:** the big-bang rewrite: eight months in, a rewrite still didn't match the system it replaced ([ch. 16](../architecture-patterns/ch16-strangler-fig-migration.md), [ch. 74](../governance-org/ch74-maturity-migration-roadmap.md)).

### "Make messaging reliable"

- **Steps:** a transactional outbox for publishing; consumers commit after processing and deduplicate with an inbox; dead-letter topics with alerts and replay.
- **Pitfall:** publishing to Kafka inside the database transaction, or after it, without an outbox: either can lose or invent events ([ch. 15](../architecture-patterns/ch15-outbox-inbox-cdc.md), [ch. 25](../communication/ch25-asynchronous-messaging.md)).

### "The client's service is slow under load"

- **Steps:** measure with an open (arrival-rate) load model; find the limiting resource; check timeouts and pool sizes; consider virtual threads for blocking I/O; tune the JVM for the container.
- **Pitfall:** closed-model load tests that hide queueing: p99 15 ms reported, 942 ms real ([ch. 65](../testing/ch65-performance-chaos-arch-tests.md), [ch. 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md), [ch. 67](../performance-scalability/ch67-jvm-tuning-containers.md)).

## The questions

The 183 chapter questions tagged TCS, Infosys, Cognizant, Accenture, Wipro, Thoughtworks, service companies or consulting firms. Tags are the chapters' judgment of where a question fits, not reports of real interviews.

--8<-- "tools/generated/interview/service-companies.md"
