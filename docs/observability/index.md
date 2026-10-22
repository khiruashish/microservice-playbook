---
title: "Part 7 — Observability"
description: "How FoodFleet sees what its services are doing and decides when to wake someone: signals and their costs, metrics with Micrometer, distributed tracing with OpenTelemetry, structured logs with correlation, SLOs with burn-rate alerts, and health probes that help instead of hurt, each measured with real collectors, rule engines and a Kubernetes cluster."
progress: done
---

# Part 7 — Observability

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Part 6 built defences against failure. Part 7 is how you find out they're needed, and whether they worked:

- **Signals:** metrics that say something is wrong, traces that say where, and logs and wide events that say why.
- **Objectives:** that say whether users noticed.
- **Probes:** that let Kubernetes act on its own without making things worse.

The recurring lesson is that every signal has defaults that quietly mislead: averaged percentiles, a request ID that doesn't follow a thread, an appender that drops INFO, an alert threshold that misses a slow burn, a liveness probe that restarts the fleet for someone else's outage.

Every chapter measures its claims on real systems:

- Prometheus 3.15 and its rule engine, the OpenTelemetry Java agent 2.32 and Collector 0.162.
- Micrometer 1.17, Logback 1.5.38, NATS and Alertmanager 0.34.
- A k3s 1.37 Kubernetes cluster.

The measured output is in the text, and the code to reproduce it is in the repository.

## FoodFleet's observability architecture

| Layer | Question | FoodFleet's choice | Owner | Chapters |
|---|---|---|---|---|
| Collection | How do signals leave a service? | OTLP to a gateway Collector; the Java agent where code can't change; Micrometer for metrics | Platform | [47](ch47-observability-fundamentals.md) |
| Metrics | Is something wrong, and how much? | Percentile histograms with SLO boundaries; templated `uri` tags with a cap; RED, USE and business recording rules | Each service team | [48](ch48-metrics-micrometer.md) |
| Traces | Where did the time go? | W3C context everywhere; 100% sent, tail-sampled in the Collector; context carried across every thread and message hand-off | Platform + teams | [47](ch47-observability-fundamentals.md), [49](ch49-distributed-tracing-otel.md) |
| Logs | What exactly happened to this request? | ECS JSON on stdout; the trace ID on every line; MDC through try-with-resources; masking on every appender | Each service team | [50](ch50-structured-logging.md) |
| Objectives | Did users notice, and is it urgent? | SLOs per journey with 30-day budgets; generated multi-window burn-rate rules; page vs ticket routing; an error budget policy | Teams + product + SRE | [51](ch51-slo-alerting.md) |
| Automation | When should Kubernetes act? | Probes on Boot's liveness and readiness groups only; startup probes; drained shutdown; Actuator on its own port | Platform + teams | [52](ch52-health-checks-actuator.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| Not knowing which signal to use | Metrics to detect, traces to locate, wide events to explain | [47](ch47-observability-fundamentals.md) | A canary moved p99 from about 24 ms to 681–711 ms within one window; 11,000 log lines said nothing; spans named the cause |
| Keeping slow traces affordably | Tail sampling in the Collector | [47](ch47-observability-fundamentals.md) | 1% head sampling kept 2–4 of 248–285 slow traces; tail sampling kept all of them in 18–19% of the bytes |
| Fleet percentiles that look fine | Summed histogram buckets, never averaged percentiles | [48](ch48-metrics-micrometer.md) | Averaged p99s said 137 ms; the true p99 was 322 ms and the buckets said 317–318 ms |
| A Prometheus that keeps growing | Templated tags, a cap that's alerted on | [48](ch48-metrics-micrometer.md) | 10,000 requests: 24 meters with path patterns, 15,954 with raw paths, a 135 MB scrape |
| Traces that stop at a thread or a queue | Executors, `Context.wrap`, extraction from message headers | [49](ch49-distributed-tracing-otel.md) | Bare threads and pull consumers lost the trace 200 of 200 and 220 of 220 times |
| A sampler that breaks everyone's traces | Parent-based sampling everywhere, decided at the edge | [49](ch49-distributed-tracing-otel.md) | One non-parent-based service dropped itself from 43–47 sampled traces or stored 400–904 fragments |
| Logs that can't be followed across threads | The trace ID as the correlation field | [50](ch50-structured-logging.md) | MDC request IDs missing on 200 of 200 executor hops, wrong on 99 of 200 after a leak; the agent's trace ID right on all of them |
| Holes in the logs under load | `AsyncAppender` with `discardingThreshold` 0 | [50](ch50-structured-logging.md) | Defaults delivered 29–30% of INFO under a burst, silently |
| Personal data in logs | A masking encoder on every line, plus `Pii` types | [50](ch50-structured-logging.md) | Source-only masking leaked 5 of 5 secrets from messages, MDC and exceptions; line masking leaked none |
| Alerts that miss slow failures or cry wolf | Multi-window, multi-burn-rate SLO alerts | [51](ch51-slo-alerting.md) | A 1% static alert missed burns spending up to 43% of the budget; burn-rate alerts paged in 1–43 min and ticketed slow burns at 8% |
| Pages for every error on a quiet service | A minimum event count in the alert | [51](ch51-slo-alerting.md) | 16 pages in 30 days on a healthy service at 1 request/min; 0 with a 100-request minimum |
| A latency SLI that's an estimate | A histogram bucket at the objective | [51](ch51-slo-alerting.md) | Without it, 99.14% for a true 99.41%: the budget looked 86% spent instead of 59% |
| Probes that restart the fleet | Liveness and readiness on Boot's groups only | [52](ch52-health-checks-actuator.md) | Liveness on the database: 6 restarts in a 60 s outage and half of `/menu` failing; Boot's groups: none and 100% |
| Errors on every deploy | Graceful shutdown with a `preStop` drain | [52](ch52-health-checks-actuator.md) | 79–96 failed requests per rolling restart without a drain, 0 with a `preStop` sleep |

## The chapters

<div class="grid cards" markdown>

-   **[47. Observability Fundamentals](ch47-observability-fundamentals.md)**

    ---

    Signals, wide events, sampling, cost per signal, BubbleUp, profiling. *OpenTelemetry agent and Collector, Prometheus.*

-   **[48. Metrics with Micrometer](ch48-metrics-micrometer.md)**

    ---

    Histograms vs percentiles, windows, cardinality, Observations, RED and USE. *Micrometer 1.17 and Prometheus 3.15.*

-   **[49. Distributed Tracing with OpenTelemetry](ch49-distributed-tracing-otel.md)**

    ---

    W3C context across threads and brokers, sampling agreement, baggage, manual spans. *The Java agent with NATS and a Collector.*

-   **[50. Structured Logging & Correlation](ch50-structured-logging.md)**

    ---

    JSON logs, MDC and trace IDs across threads, async appenders, PII masking. *Logback 1.5.38 and the Java agent.*

-   **[51. SLIs, SLOs, Error Budgets & Alerting](ch51-slo-alerting.md)**

    ---

    Error budgets, burn rates, generated rules, latency SLIs, routing. *Prometheus's rule engine and Alertmanager 0.34.*

-   **[52. Health Checks & Actuator Hardening](ch52-health-checks-actuator.md)**

    ---

    Liveness, readiness, startup probes, drained shutdown, Actuator exposure. *A k3s 1.37 cluster with PostgreSQL.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Building services already** (🔵 L2) | 47 → 48 → 50 → 52 | [Part 8: Deployment & Platform](../deployment/index.md) |
| **Leading a team** (🟣 L3) | 47 → 48 → 49 → 51 → 52 | [Part 8: Deployment & Platform](../deployment/index.md) |
| **Designing a platform** (🔴 L4) | 47 → 49 → 51 | [Part 8: Deployment & Platform](../deployment/index.md) |
| **Preparing for interviews** | 48, 49, 51 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-047 · Chapter 47</span><span class="what">All three signals over OTLP to a gateway Collector; 100% of spans sent and tail-sampled; wide events per request; metrics with a cardinality budget.</span></li>
<li><span class="when">ADR-048 · Chapter 48</span><span class="what">Percentile histograms with SLO boundaries; percentiles only from summed buckets; templated, capped <code>uri</code> tags; RED, USE and business recording rules.</span></li>
<li><span class="when">ADR-049 · Chapter 49</span><span class="what">W3C propagation everywhere; parent-based sampling decided at the edge; every asynchronous hand-off carries context; baggage allow-listed.</span></li>
<li><span class="when">ADR-050 · Chapter 50</span><span class="what">ECS JSON lines; the trace ID as the correlation field; MDC only through try-with-resources; a masking encoder everywhere; no silent log loss.</span></li>
<li><span class="when">ADR-051 · Chapter 51</span><span class="what">SLOs per journey with 30-day budgets; generated burn-rate alerts; static error pages removed; an error budget policy signed with product.</span></li>
<li><span class="when">ADR-052 · Chapter 52</span><span class="what">Probes on Boot's groups only; startup probes; drained shutdown; Actuator on its own port behind a NetworkPolicy.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch47-observability-fundamentals,chapters/ch48-metrics-micrometer,chapters/ch49-distributed-tracing-otel,chapters/ch50-structured-logging,chapters/ch51-slo-alerting,chapters/ch52-health-checks-actuator -am verify
    ```
    JDK 25 and Maven 3.9+. Each chapter's `verify/` folder holds its experiments and says what they need:

    - **47 and 49:** the OpenTelemetry agent, Collector and Prometheus (49 also NATS).
    - **48 and 50:** Micrometer and Logback built from their release tags.
    - **51:** promtool and Alertmanager.
    - **52:** Docker, PostgreSQL and a Kubernetes cluster (`k3s-lab.sh` starts one).
