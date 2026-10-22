# Chapter 48 — Metrics with Micrometer

Micrometer in Spring Boot 4: meters, Observations, RED/USE recording rules, and the decisions that make metrics
trustworthy (histograms, short memory, countable tag values), measured on Micrometer 1.17.1.

```text
src/main/java/dev/foodfleet/ch48/
├── metrics/       MetricsPolicy   common tags, identifiers never as tags, uri cap, bounded histograms + SLO buckets
│                  OrderMetrics    business meters registered once in a MeterBinder; tags from enums
├── observation/   CheckoutObservation, Checkout   one Observation with declared low/high-cardinality keys
└── config/        MetricsConfiguration            the policy as Spring Boot 4 beans
src/main/resources/
├── application.yml                       Boot 4.1.1 metrics and observation properties
└── prometheus/                           prometheus.yml and RED, USE and business recording rules
src/test/java/…   14 tests
verify/
├── build-libs.sh   Micrometer 1.17.1, Prometheus client 1.7.0, HdrHistogram, LatencyUtils, context-propagation from
│                   their tags; Spring Framework 7.0.9 web/core sources; Prometheus 3.15.0
├── run.sh          aggregate, decay, cardinality, cost, gauge; then promtool and check_apis.sh
├── check_apis.sh   25 defaults and behaviours in Micrometer, Spring Framework and Spring Boot sources
└── tools/          Lab (registries over HTTP, Prometheus), Measure
```

## Run

```bash
mvn -B -pl chapters/ch48-metrics-micrometer -am verify    # from code/: compile and test
chapters/ch48-metrics-micrometer/verify/run.sh            # about 10 minutes; git, curl, python3, JDK 21+, Linux x86-64
```

`build-libs.sh` compiles the libraries from source because the experiments ran without Maven Central; Micrometer's
optional binders for third-party libraries (Jetty, gRPC, servlet filters, AOP) are left out, everything the
experiments use is the released code.

## What the experiments found

Two runs on JDK 25.0.4 (a third of `aggregate` for the chapter's log), Prometheus 3.15.0, one 2-vCPU container.

| Experiment | Finding |
|---|---|
| Fleet percentile, one slow pod in four | True p99 322 ms; average of the pods' client-side p99s 137 ms; max 352 ms; `histogram_quantile` over summed buckets 317–318 ms; SLO buckets alone 382–386 ms (but the 250 ms share exactly). With a histogram, the Prometheus registry leaves client-side percentiles out. |
| A 10-second spike | Back under 100 ms: true after 5 s, `rate[30s]` histogram p99 after 30 s, `rate[1m]` after 60 s, client-side p99 after 50–60 s, the timer's max after 130–140 s. |
| The uri tag, 10,000 requests | Spring's path patterns: 24 meters. Raw paths: 15,954 meters, 566,367 series and an 87 MB scrape with histograms. Concatenated RestClient URIs: 19,918 meters, 707,089 series, 135 MB, 1.35 s per scrape. A cap of 100 kept it small and gave a new endpoint 0 timers. |
| Recording cost | Counter 12–13 ns, timer 51–52 ns, with histogram 84–97 ns (428–441 contended), client-side percentiles 186–217 ns (1.4–1.5 µs contended), meter built per call 218–254 ns, Observation 0.7–1.3 µs (long-task timer off saves 29–66%). |
| Gauges | A gauge on an object nothing else holds read NaN after one GC; held or `strongReference(true)` stayed 42. |
| Configuration and sources | Prometheus config and 10 rules valid (`promtool`); 25 of 25 source checks passed. |
