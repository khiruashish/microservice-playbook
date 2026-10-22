---
title: "Logging: Loki, ELK, EFK"
description: "FoodFleet's logs: ECS JSON lines on stdout, the trace ID as the correlation field, a masking encoder on every appender, and an AsyncAppender that never discards silently (ADR-050), shipped by a node agent or the collector to a log store: Loki locally, Loki or Elasticsearch/OpenSearch in production. Measured: AsyncAppender's defaults dropped about 70% of INFO under a burst; JSON cost 365 bytes per event untrimmed; masking at the source leaked concatenated, MDC and exception values 5 of 5; masking the finished line leaked none."
progress: done
---

# Logging: Loki, ELK, EFK

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | The path from a log call to a searchable store. **ELK/EFK**: Elasticsearch (or OpenSearch) indexes every field, fed by Logstash or Fluentd/Fluent Bit, viewed in Kibana. **Loki**: indexes only labels and stores compressed lines, queried with LogQL in Grafana |
| **FoodFleet uses** | Logback with Spring Boot's ECS structured logging to stdout; a node agent (Fluent Bit or Grafana Alloy) or the OTLP collector ships lines; Loki in the local stack |
| **Decided in** | [ADR-050](../observability/ch50-structured-logging.md#8-enterprise-case-study), [ADR-047](../observability/ch47-observability-fundamentals.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 50](../observability/ch50-structured-logging.md), [chapter 4](../foundations/ch04-twelve-factor.md) (logs as event streams), [lab 12](../code-lab/lab-12-observability.md) |
| **Related** | [Distributed tracing](distributed-tracing.md), [OTel Collector & LGTM](otel-collector-lgtm.md), [Elasticsearch / OpenSearch](elasticsearch-opensearch.md) |

## The service's side

The service writes JSON lines to stdout and knows nothing about where they go ([chapter 4](../foundations/ch04-twelve-factor.md)):

```yaml title="application.yml (chapter 50)"
--8<-- "code/chapters/ch50-structured-logging/src/main/resources/application.yml"
```

```xml title="logback-spring.xml (chapter 50)"
--8<-- "code/chapters/ch50-structured-logging/src/main/resources/logback-spring.xml"
```

## Measured on Logback 1.5

**Correlation.** The MDC is a thread-local: on every executor hand-off, the request ID was missing 200 of 200 times, and a pooled thread whose task forgot to clear it logged **another request's ID** 99 times in 100. The agent's `trace_id` survived executors (not bare threads). FoodFleet correlates on the trace ID and sets the MDC only with try-with-resources.

**Bursts.** Eight threads, a sink at 50 µs per event:

| Appender | INFO delivered | WARN delivered | Burst took |
|---|---:|---:|---:|
| Synchronous | 100% | 100% | 970–984 ms |
| `AsyncAppender` defaults (queue 256) | **28.7–29.5%** | 100% | 300–345 ms |
| …`discardingThreshold` 0 | 100% | 100% | 814–825 ms |
| …`neverBlock` true | **2.6%** | **8.9–9.0%** | 10–20 ms |

The defaults drop INFO silently once the queue is 80% full. FoodFleet: queue 8192, `discardingThreshold` 0, `neverBlock` false, shipper lag alerted.

**Cost.** Default JSON was 365 bytes per event against 121 for text; trimmed JSON 289 (76 bytes saved per line is about 260 GB a day at 40,000 lines a second). About 1 µs more CPU per event than text.

**Personal data.**

| Approach | Leaked (of 5 values, per place) |
|---|---|
| `Pii` types at the source | Message concatenation **5/5**, MDC **5/5**, exception message **5/5**; arguments 0/5 |
| `MaskingEncoder` on every line | 0/5 everywhere, about 12 µs per event |

Both together are FoodFleet's rule, plus a CI test that logs fake PII through every path.

## Choosing a store

| | Loki | Elasticsearch / OpenSearch (ELK/EFK) |
|---|---|---|
| Indexes | Labels only (service, namespace, level); lines compressed in object storage | Every field (inverted index) |
| Query | LogQL: select by labels, then filter and parse lines | Full-text and field queries, aggregations |
| Cost at volume | Low storage and ingest cost | Higher: indexing CPU, heap, storage |
| Good at | "Show this trace's lines", recent troubleshooting next to Grafana metrics and traces | Ad-hoc search across fields, analytics on logs |
| Label discipline | Essential: high-cardinality labels (user, trace ID) explode streams | Mapping discipline: dynamic fields explode mappings |

With traces and wide events carrying per-request fields, FoodFleet's logs are mostly read by trace ID, which suits Loki; the local stack uses `grafana/otel-lgtm`. Either store works with the same JSON lines.

## Shipping

- **Node agent** (Fluent Bit, Grafana Alloy) tails container stdout: no dependency in the service, survives the service's crash.
- **OTLP logs** to the collector, with trace context attached by the SDK or agent: one pipeline for all signals.
- **Retention and access** per data class: logs hold personal data even after masking, so treat the store as such.

## Pitfalls

- **Text logs grepped by request ID** across threads.
- **Silent drops** from async appenders, or a blocked application from a full one with no alert.
- **PII masked only at the source**.
- **Trace or user IDs as Loki labels**.
- **Logging as metrics**: counting log lines for rates is costlier and less reliable than a counter.
- **Unbounded stack traces**: FoodFleet caps depth at 10.
