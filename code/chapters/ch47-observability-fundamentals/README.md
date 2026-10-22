# Chapter 47 — Observability Fundamentals

What each signal (metrics, logs, traces, profiles, change events, wide events) can answer, what it costs and how they
join up, measured on FoodFleet's checkout services under the OpenTelemetry Java agent.

```text
src/main/java/dev/foodfleet/ch47/
├── events/       WideEvent, WideEventWriter   one structured event per request, written as a JSON line
├── correlation/  TraceParent                  the W3C traceparent header: parse, validate, child contexts
├── sampling/     TraceIdRatio                 head sampling by trace ID, the OpenTelemetry SDK's rule
│                 TailPolicy                   keep errors, slow traces and a baseline, after the fact
└── analysis/     BubbleUp                     what differs about the bad requests, from any fields
src/main/resources/
├── otel/collector.yaml          gateway Collector: trim resource, span metrics from 100%, tail sampling
├── otel/agent.properties        the Java agent for services that can't change
├── prometheus/                  prometheus.yml and RED recording rules
└── application.yml              a Spring Boot 4.1.1 service exporting traces and logs over OTLP
src/test/java/…   19 tests
verify/
├── build-libs.sh   agent 2.32.0, otelcol-contrib 0.162.0, Prometheus 3.15.0 binaries; sources for check_apis.sh
├── run.sh          find, overhead, profile, cardinality; then the configurations and check_apis.sh
├── check_apis.sh   25 defaults and behaviours in OpenTelemetry Java, its instrumentation, the Collector,
│                   Prometheus and Spring Boot sources
└── tools/          Services (gateway, order, payment on the JDK's HTTP server), Lab, Measure, MetricsEmitter, Json
```

## Run

```bash
mvn -B -pl chapters/ch47-observability-fundamentals -am verify    # from code/: compile and test
chapters/ch47-observability-fundamentals/verify/run.sh            # about 16 minutes; Linux x86-64, git, curl, JDK 21+
```

Work files (spans, logs, wide events, Prometheus data, JFR recordings) go to `/tmp/c47` (`-Dc47.dir=...`); the
`find` mode leaves them there for you to explore.

## What the experiments found

Two runs on JDK 25.0.4, agent 2.32.0 (SDK 1.66.0), Collector 0.162.0 and Prometheus 3.15.0, one 2-vCPU container.

| Experiment | Finding |
|---|---|
| A canary adding 600 ms to 5% of orders | Metrics: order p99 23–25 ms → 681–711 ms within one 10-s window, on one pod; the median never moved; metrics lagged users by a window. Logs: 11,000 INFO lines, nothing relevant. Traces: 1% head sampling kept 2–4 of 248–285 slow traces, tail sampling all of them for 18–19% of the bytes. Spans with request fields and wide events isolated bank-x + UPI + the canary pod with 100% precision and recall. |
| Cost per order | Spans 2.6 KB (5 per order), wide events 0.6 KB, logs 0.24 KB; metrics ~500 KB/min regardless of traffic. Each batch carries the resource: 1,645 characters, 983 of them the JVM command line. A `-D…token` flag appeared in spans and in Prometheus labels; `-D…password` was masked. All resource attributes as labels: scrapes 5.7× bigger. |
| Instrumentation overhead | Agent: +38–56% CPU per order on thin services (+25–36% exporting nothing), +110–125 MB per JVM, startup 1.1 s → 14–16 s; 10% sampling saved little CPU. JFR: +4–7% CPU, ~1 s startup. |
| Profiling | A 7 ms payment span had no children; JFR put 63–64% of the service's CPU in the method hashing idempotency keys with PBKDF2, while its leaf view showed only digest internals. |
| Cardinality | A `restaurant_id` label: 265 → 260,005 series at 1,000 values; Prometheus 94–106 → 672–673 MB, scrape 7–10 → 718–752 ms, p99 query 4–5 → 406–457 ms. |
| Configurations and sources | `collector.yaml` valid for otelcol-contrib 0.162.0 (strict: unknown keys fail); Prometheus config and 4 rules valid; 25 of 25 source checks passed. |

Spring Boot's own instrumentation (Micrometer Observation, OpenTelemetry export) is measured in chapters 48 and 49.
