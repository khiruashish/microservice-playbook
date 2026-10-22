---
title: "Distributed Tracing"
description: "Tracing at FoodFleet: Micrometer Tracing over OpenTelemetry in Spring Boot (the Java agent where code can't change), W3C trace context produced everywhere, parent-based sampling with tail sampling in the collector, context carried across every hand-off, and baggage allow-listed (ADR-049). Measured: bare threads and own queues lost the trace 200 of 200 times, executors 0; a pull consumer continued 0 of 220 traces until context was extracted by hand; one service re-deciding sampling left 400–904 fragments; client baggage reached a payment partner 50 of 50 times."
progress: done
---

# Distributed Tracing

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Recording each request's path as a trace of spans across services, joined by a propagated context (W3C `traceparent`, `tracestate`, `baggage`). **OpenTelemetry** is the standard API, SDK and protocol (OTLP); **Micrometer Tracing** is Spring's facade over it |
| **FoodFleet uses** | Spring Boot tracing (Micrometer Tracing → OpenTelemetry) in every service, the OpenTelemetry Java agent where code can't change; OTLP to the gateway collector; Tempo locally |
| **Decided in** | [ADR-049](../observability/ch49-distributed-tracing-otel.md#8-enterprise-case-study), [ADR-047](../observability/ch47-observability-fundamentals.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 49](../observability/ch49-distributed-tracing-otel.md), [chapter 47](../observability/ch47-observability-fundamentals.md), [lab 12](../code-lab/lab-12-observability.md) |
| **Related** | [OTel Collector & LGTM](otel-collector-lgtm.md), [Logging stack](logging-stack.md), [Kafka](kafka.md), [APM tools](apm-tools.md) |

## Configuration

```yaml title="application.yml (chapter 49)"
--8<-- "code/chapters/ch49-distributed-tracing-otel/src/main/resources/application.yml"
```

Produce W3C, consume everything (B3 too); sample 100% in services and let the collector decide; the ingress strips `traceparent`, `tracestate` and `baggage` from internet traffic.

## Where traces break

[Chapter 49](../observability/ch49-distributed-tracing-otel.md), under the agent, 200 orders per hand-off:

| Hand-off | Kept the trace |
|---|---:|
| `ExecutorService`, `CompletableFuture.runAsync`, virtual-thread executor, `HttpClient.sendAsync` | 200 / 200 |
| `new Thread(…)`, `Thread.ofVirtual().start(…)` | **0 / 200** |
| Own queue + worker thread | **0 / 200** |
| …task enqueued as `Context.current().wrap(task)` | 200 / 200 |

| Messages (NATS, 220 orders) | Consumer span in the order's trace |
|---|---:|
| Push handler (`MessageHandler`) | 220 / 220 |
| Pull loop (`nextMessage()`) | **0 / 220** |
| Pull, context extracted from headers by hand | 220 / 220 |

Spring Kafka's observations are switched on for templates and listeners; outbox rows store `traceparent` so the relay's publish continues the business transaction's trace. [Lab 12](../code-lab/lab-12-observability.md) shows the same split: 20 orders through an executor kept one trace, 20 through a bare thread split into two each.

## Sampling must follow the parent

| Configuration (1,000 orders, gateway keeps 10%) | Complete traces | Fragments |
|---|---:|---:|
| Parent-based everywhere | 105–108 | 0 |
| One service `traceidratio` 0.5, not parent-based | 89–105 | **400–401** |
| One service `traceidratio` 0.05 | **31–38** (43–47 missing it) | 0 |
| One service B3 only | **0** | **1,000** |
| Clients send `traceparent` with the sampled flag | **1,000** (100% sampled) | 0 |

Head sampling also decides before anyone knows how a request will go: [chapter 47](../observability/ch47-observability-fundamentals.md)'s 1% head sampling kept 2–4 of 248–285 slow traces; tail sampling kept all of them for 18–19% of the bytes.

## Baggage leaks

A client sent an email address and a session token as baggage. Without a policy, both reached an external payment partner on **50 of 50** calls. An allow-list at the gateway (tenant, city tier, experiment, priority; at most 512 bytes) and calling partners without baggage fixed it:

```java title="BaggagePolicy.java (chapter 49)"
--8<-- "code/chapters/ch49-distributed-tracing-otel/src/main/java/dev/foodfleet/ch49/baggage/BaggagePolicy.java:28:50"
```

Baggage isn't recorded on spans unless copied; Spring Boot's `remote-fields` is a propagation list, not a security allow-list.

## What it costs

| Setup (3 thin JVMs) | CPU per 1,000 orders | Startup |
|---|---:|---:|
| No instrumentation | 3,387–3,531 ms | 1.1 s |
| Agent, every trace exported | 4,864–5,289 ms (+38–56%) | 15.7–16.1 s |
| Agent, nothing exported | 4,418–4,596 ms | 13.6–13.7 s |

About 0.3 ms of CPU per span, mostly creating spans rather than sending them. Spans are also the costly signal per request: 2.6 KB per order for five spans, four times the wide events carrying the same context.

## Pitfalls

- **Threads and queues you own** without `Context.wrap`.
- **Pull consumers** without extraction.
- **Services that re-decide sampling**, or speak only B3.
- **Trusting incoming `traceparent`** from the internet: clients choose your sampling.
- **Baggage to third parties**.
- **Secrets on the JVM command line**: the agent copied a token from it into every span's resource; FoodFleet's collector deletes command-line attributes.
