---
title: "Monitoring: Prometheus, Grafana, Alertmanager"
description: "Prometheus, Grafana and Alertmanager as FoodFleet runs them: percentile histograms with SLO buckets summed across pods (the averaged per-pod p99 read 137 ms when the truth was 322 ms; summed buckets 317–318 ms), a label budget (one restaurant_id label took 265 series to 260,005 and Prometheus from about 100 MB to 670 MB), recording rules for RED, USE and SLOs, and multi-window burn-rate alerts routed by Alertmanager, which paged within 1–43 minutes on fast incidents and ticketed slow burns at 8% of the budget."
progress: done
---

# Monitoring: Prometheus, Grafana, Alertmanager

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | **Prometheus**: a time-series database that scrapes (or receives) metrics and evaluates PromQL rules. **Grafana**: dashboards and exploration across metrics, logs and traces. **Alertmanager**: groups, inhibits, silences and routes alerts |
| **FoodFleet uses** | Prometheus 3 (scrapes `/actuator/prometheus` on the management port, plus remote write from the gateway collector), recording rules for RED and SLOs, Alertmanager routing pages and tickets, Grafana dashboards |
| **Decided in** | [ADR-047](../observability/ch47-observability-fundamentals.md#8-enterprise-case-study), [ADR-048](../observability/ch48-metrics-micrometer.md#8-enterprise-case-study), [ADR-051](../observability/ch51-slo-alerting.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 48](../observability/ch48-metrics-micrometer.md), [chapter 51](../observability/ch51-slo-alerting.md), [lab 20](../code-lab/lab-20-chaos-game-day.md) |
| **Related** | [Spring Boot Actuator](spring-boot-actuator.md), [OTel Collector & LGTM](otel-collector-lgtm.md), [Distributed tracing](distributed-tracing.md) |

## What the services expose

```yaml title="application.yml (chapter 48)"
--8<-- "code/chapters/ch48-metrics-micrometer/src/main/resources/application.yml"
```

## Percentiles come from summed buckets

[Chapter 48](../observability/ch48-metrics-micrometer.md): four pods, one of them adding 300 ms to 20% of its orders.

| Fleet p99 computed as | Result |
|---|---:|
| **True** | **322 ms** |
| `avg()` of the pods' client-side percentiles | **137 ms** |
| `max()` of the pods' client-side percentiles | 352 ms |
| `histogram_quantile` over summed buckets | **317–318 ms** |
| `histogram_quantile` over SLO buckets only (100, 250, 500, 1000 ms) | 382–386 ms |

Percentiles can't be averaged; buckets can be summed. FoodFleet's panels and SLOs use only `histogram_quantile(…, sum by (le, …)(rate(…_bucket[…])))` in recording rules. Client-side percentiles also remember a spike for 50–60 s and the timer's `max` for 130–140 s; a `rate[30s]` window forgets it after 30 s.

```yaml title="foodfleet-signals.rules.yml (chapter 48)"
--8<-- "code/chapters/ch48-metrics-micrometer/src/main/resources/prometheus/foodfleet-signals.rules.yml"
```

## Every label value is a series

| `restaurant_id` values | Series | Scrape took | Prometheus RSS | p99-by-route query |
|---:|---:|---:|---:|---:|
| none | 265 | 7–10 ms | 94–106 MB | 4–5 ms |
| 100 | 26,005 | 59–69 ms | 143 MB | 44–49 ms |
| 1,000 | **260,005** | **718–752 ms** | **672–673 MB** | **406–457 ms** |

FoodFleet's label budget is service, route, status and pod (plus region); IDs go on spans and wide events. Raw paths as the `uri` tag made 15,954 meters from 10,000 requests (566,367 series with histograms); Spring's path templates made 24. `max-uri-tags` caps it, alerted on, and a CI check scrapes each service after its tests and fails above its series budget.

## Alerting on SLOs

[Chapter 51](../observability/ch51-slo-alerting.md) replayed incidents through Prometheus's own rule engine for checkout's 99.9% SLO:

| Incident (budget it spends) | Static > 1% | At the SLO (> 0.1%) | **Multi-window page** | **Multi-window ticket** |
|---|---|---|---|---|
| Total outage, 3 min | 6 min | 6 min | **1 min** | — |
| 2% fail for 2 h | 7 min | 6 min | **43 min** | — |
| 0.6% fail for 18 h | **never** | 6 min | never | **9.9 h, 8.0%** |
| Healthy service, 7 days | 0 | **3 false pages** | 0 | 0 |

```yaml title="checkout-slo.rules.yml (chapter 51, generated)"
--8<-- "code/chapters/ch51-slo-alerting/src/main/resources/prometheus/checkout-slo.rules.yml:52:75"
```

Low-traffic services need a minimum request count in the long window (one request a minute paged on each of 16 failures without it), and latency SLIs need a histogram bucket **at** the objective: without one, `histogram_fraction` read 86% of the budget spent instead of 59%. [Lab 20](../code-lab/lab-20-chaos-game-day.md) runs these rules with promtool.

## Alertmanager

```yaml title="alertmanager.yml (chapter 51)"
--8<-- "code/chapters/ch51-slo-alerting/src/main/resources/alertmanager/alertmanager.yml"
```

On Alertmanager 0.34.1, the page went to the pager in 2 s; the ticket for the same SLO was inhibited while the page fired and delivered when it resolved.

## Running it

- **Scrape interval and rule windows together**: `rate()` needs at least four samples in its window.
- **Long-term storage and global queries** (Thanos, Mimir, a managed service) once one Prometheus per cluster isn't enough; remote write from the collector.
- **Grafana dashboards as code**, linked to runbooks; exemplars link a latency bucket to a trace.
- **Meta-monitoring**: alert when Prometheus or Alertmanager can't scrape, evaluate or send.

## Pitfalls

- **Averaging percentiles**.
- **High-cardinality labels**.
- **Static thresholds as pages**: they miss slow burns and page on noise.
- **Gauges on objects nobody holds**: Micrometer holds them weakly, and they read `NaN` after a GC.
- **Dashboards without alerts, alerts without runbooks**.
