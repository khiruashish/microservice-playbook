---
title: "Load Testing: k6, Gatling, JMeter"
description: "Load testing at FoodFleet (ADR-065): k6 with constant-arrival-rate (open model) in the release pipeline for critical services, ramping-arrival-rate for capacity. Measured in chapter 65: against a service that froze for a second every ten, a closed model reported p99 15 ms when the truth was 942 ms; towards capacity, closed models only doubled latency while an open model found the knee at about 390 requests/s. Lab 14's Gatling simulation showed platform threads collapsing at 1,500/s and a concurrency limit set too low turning away 15,843 requests."
progress: done
---

# Load Testing: k6, Gatling, JMeter

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Tools that generate traffic and record latency: **k6** (Go, JavaScript scripts, `executor`s for open and closed models), **Gatling** (JVM, Java/Scala/Kotlin DSL, injection profiles), **JMeter** (GUI-built test plans, thread groups) |
| **FoodFleet uses** | k6 `constant-arrival-rate` in the release pipeline for critical services; Gatling in lab 14 |
| **Decided in** | [ADR-065](../testing/ch65-performance-chaos-arch-tests.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 65](../testing/ch65-performance-chaos-arch-tests.md), [lab 14](../code-lab/lab-14-virtual-threads-vs-webflux.md), [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md) |
| **Related** | [Chaos tools](chaos-tools.md), [Monitoring](monitoring-prometheus-grafana.md), [Profiling](profiling.md) |

## Open, not closed

A **closed** model has a fixed number of virtual users, each waiting for its response before sending the next: a slow server receives less load. An **open** model starts requests at a fixed rate, as customers do.

[Chapter 65](../testing/ch65-performance-chaos-arch-tests.md), a service that froze for 1 s every 10 s:

| Load model | p90 | p99 |
|---|---:|---:|
| Closed, 2 virtual users | 11.9–12.1 ms | **15.3–15.7 ms** |
| Closed, corrected for coordinated omission (HdrHistogram) | 14.4–14.9 ms | 898–901 ms |
| **Open, 150 requests/s** | **392–393 ms** | **942–943 ms** |

Towards capacity (about 390 requests/s):

| Load | Achieved | p50 | p99 |
|---|---:|---:|---:|
| Closed, 32 users | 387–389/s | 82 ms | 86 ms |
| Open, 380/s | 380/s | 10.7 ms | 18.4–45.9 ms |
| **Open, 420/s** | 388–391/s | **747–842 ms** | **1,495–1,654 ms** |

A closed model can't push past capacity; it just shows Little's law. The open model shows the knee and what happens past it.

## Gatling, open model (lab 14)

```java title="QuoteSimulation.java (lab 14)"
--8<-- "code/code-lab/lab-14-virtual-threads-vs-webflux/load-test/src/test/java/dev/foodfleet/lab14/QuoteSimulation.java:17:47"
```

| Model (50 ms backend) | Offered | Served/s | p99 | Failed / rejected |
|---|---:|---:|---:|---:|
| Platform threads (200) | 1,500 | 1,175 | 5,512 ms | 0 |
| Platform threads | 2,500 | 579 | 14,702 ms | **32,851 failed** |
| Virtual threads | 2,500 | 2,479 | 307 ms | 0 |
| Virtual threads, limit 300 | 2,500 | 1,695 | 239 ms | **15,843 rejected** |

The limit was below rate × latency (2,500/s × 0.19 s ≈ 470 in flight).

## Choosing

| | k6 | Gatling | JMeter |
|---|---|---|---|
| Scripts | JavaScript | Java/Kotlin/Scala DSL | XML plans (GUI) |
| Open model | `constant-arrival-rate`, `ramping-arrival-rate` | `constantUsersPerSec`, `rampUsersPerSec` | Possible (throughput timers, plugins); thread groups are closed |
| Fits | Pipelines, teams outside the JVM | JVM teams, rich reports | Existing plans, protocol breadth |

## Pitfalls

- **Closed models for latency SLOs**.
- **Generator on the same CPUs as the service** without saying so (lab 14 and chapter 66 note it).
- **Averages instead of percentiles**, or percentiles from a single generator process merged wrongly.
- **Test data that hits one cache key** or one partition.
- **No ramp to find the knee**: a pass at today's rate says nothing about the sale day.
