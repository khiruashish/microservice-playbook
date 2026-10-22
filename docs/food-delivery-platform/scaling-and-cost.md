---
title: "Scaling & Cost"
description: "How each part of FoodFleet scales, what limits it, and what it costs: the scaling signal for every kind of workload, how peaks are met before they arrive, and the generated container fleet priced with chapter 70's own packing and OpenCost's default prices: $936–1,134 a month at the autoscalers' minimums and $3,043–3,687 at their maximums for compute alone, with node shape alone worth 17%. Plus cost per 1,000 orders and when a workload belongs on functions."
progress: done
---

# Scaling & Cost

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

FoodFleet's traffic has a shape: two daily peaks, lunch and dinner, and bigger ones for sales and cricket finals. Scaling means meeting those peaks without paying for them all day. Part 10 ([chapters 66–70](../performance-scalability/index.md)) measured both halves; this page applies them to the whole platform.

## What limits each part, and how it scales

[ADR-068](../performance-scalability/ch68-horizontal-scaling.md)'s rule: every service documents the shared bottleneck that limits it, and scales on that signal, not on whatever is easiest to measure.

| Workload | What limits it | Scales on | Decision |
|---|---|---|---|
| Request/response services | CPU, within a concurrency limit sized by Little's law | HPA on CPU at 60% of requests; readiness only after warm-up | [ADR-056](../deployment/ch56-autoscaling.md), [ADR-066](../performance-scalability/ch66-latency-virtual-threads-reactive.md) |
| Kafka consumers | Partitions, and the sink they write to | KEDA on consumer lag; never beyond the partition count | [ADR-056](../deployment/ch56-autoscaling.md), [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| tracking-gateway | Open streams: about 20,000 per instance | Should be connection count; today CPU, like the rest ([Kubernetes](kubernetes.md#where-the-chapters-set-different-numbers)) | [ADR-026](../communication/ch26-websockets-sse.md) |
| PostgreSQL | One primary's writes; connections | Fixed pools budgeted fleet-wide; reads to replicas with consistency tokens; sharding by customer through 4,096 slots, deferred until writes need it | [ADR-031](../data/ch31-data-consistency.md), [ADR-033](../data/ch33-database-scaling.md) |
| Tenants and cities | Blast radius rather than capacity | Cells by city; shared fleets shuffle-sharded | [ADR-046](../resilience/ch46-disaster-recovery.md) |
| Event-driven and bursty work | Nothing steady to keep warm | Functions on Lambda | [ADR-076](../governance-org/ch76-serverless-microservices.md) |

**A shared dependency caps everyone behind it.** In chapter 68, four copies of a service served 121–132 requests a second on their own, and 50 behind a dependency limited to 50 a second. Adding copies in front of a bottleneck adds nothing; the service's README says which bottleneck to watch.

**State stays out of the pods.** Carts kept in memory survived 0–2 of 200 times without affinity, and 0 of 200 after the pod holding them was replaced even with affinity; in PostgreSQL, 200 of 200 ([ADR-068](../performance-scalability/ch68-horizontal-scaling.md)). Every pod is interchangeable, which is what makes autoscaling and spot capacity safe.

## Meeting the peaks before they arrive

An HPA reacts after load arrives, and chapter 56 measured how long that takes: 29–34 seconds before a scale-up even starts, then the JVM's start-up. So FoodFleet doesn't wait for lunch ([ADR-056](../deployment/ch56-autoscaling.md)):

- **Minimums carry normal traffic**, and a scheduled job raises them 15 minutes before the lunch and dinner peaks.
- **Spare node capacity** for two pods per zone, so a scale-up doesn't wait for a new node.
- **Readiness gated on warm-up**, so the HPA doesn't count cold pods as capacity (ready-at-once pods drove it to its maximum of 8 for four pods' worth of load).
- **A 300-second scale-down window**, so one-minute bursts don't make it flap (10–11 scaling changes in eight minutes without it, 2 with it).
- **AOT caches** halve the time from start to first response ([ADR-060](../deployment/ch60-native-images-startup.md)).

## What the container fleet costs

The [Kubernetes page](kubernetes.md#values-from-the-model) generated a values file for each of the 21 container services. `code/foodfleet/cost/run.sh` prices that fleet with chapter 70's own `Fleet` class: pods packed first-fit decreasing onto nodes, as the scheduler and Karpenter roughly would, at OpenCost's default price list ($0.031611 per vCPU-hour and $0.004237 per GiB-hour on demand):

--8<-- "code/foodfleet/cost/cost.md"

What the table says:

- **The floor is about $1,000 a month of compute**: 49 pods, the 7 services on a journey's required path at three replicas each and the other 14 at two. That's what availability across three zones costs before any traffic.
- **Node shape is worth 17%.** The fleet is CPU-bound: with the chart's default of 500m CPU and 512 MiB per pod, general-purpose nodes (4 GB per vCPU) leave three quarters of their memory unrequested. Compute-optimised nodes (2 GB per vCPU) run the same pods for $936 instead of $1,134. Karpenter chooses shapes from the pending pods' requests, which is one reason ADR-059 adopted it.
- **Requests are guesses until measured.** Every service here requests the chart's default. Chapter 70 measured FoodFleet's services using 3–10% of their requested CPU, and VPA's recommendations took its 24-service fleet from 97 to 30 CPUs. The same review ([ADR-070](../performance-scalability/ch70-finops.md), monthly) will move these numbers more than anything on this page.
- **Compute is only part of the bill.** Managed Kafka, Aurora PostgreSQL, Redis, load balancers, cross-zone traffic and telemetry storage are extra, and for a platform this size they can match or exceed the cluster.

### Spot, carefully

All-spot is 79% cheaper at these prices, but chapter 70 measured why not to do it: when spot capacity was reclaimed under load, an all-spot service served 0–1% of requests for a minute, while a 3 on-demand + 3 spot split served 55–61%. So FoodFleet runs an on-demand base sized to carry each service's peak alone, and spot copies above it, across instance types and zones ([ADR-070](../performance-scalability/ch70-finops.md)). Commitments (savings plans) cover the base once it has been right-sized, not before.

### Who pays for what

OpenCost allocates costs by the `team` label every resource carries. The rule matters more than it looks: charged by usage, 93% of chapter 70's bill was idle capacity nobody owned; charged by the larger of request and usage per pod, 34%, with the idle remainder owned by the platform team. That gives teams a reason to right-size their requests.

## Cost per 1,000 orders

[ADR-070](../performance-scalability/ch70-finops.md) reports cost per 1,000 orders beside the SLOs, because a total bill rises with growth and says nothing about efficiency. The chapters don't fix FoodFleet's order volume, so here it is at two illustrative volumes, for the compute floor alone:

| Orders a month | Compute floor ($936–1,134) per 1,000 orders |
|---|---|
| 1 million (about 33,000 a day) | $0.94–1.13 |
| 10 million (about 330,000 a day) | $0.09–0.11 |

The fixed floor dominates at low volume. That's the honest case against microservices for a small business ([ADR-001](../foundations/ch01-architecture-styles.md) started FoodFleet as a modular monolith): 49 pods across three zones cost the same whether they serve one order or a million.

## Functions or containers

[Chapter 76](../governance-org/ch76-serverless-microservices.md) priced the same workloads both ways. Functions win below a crossover set by the minimum of two always-on containers: about 30 thumbnails or 100 fee quotes a second, sustained. Partner webhooks cost under a cent a day as functions. So:

- `partner-webhooks` and `menu-photo-processor` run on Lambda ([ADR-076](../governance-org/ch76-serverless-microservices.md)).
- Steady request/response services stay on containers.
- Each workload's platform is re-checked quarterly against the crossover, since workloads grow past it in one direction and idle services fall below it in the other.
