---
title: "Scenario & Troubleshooting Rounds"
description: "Production-incident rounds: a six-step method for answering 'this is happening in production, what do you do?', the ten symptoms interviewers describe most with their usual causes and the first thing to check, and 235 practice drills generated from the chapters' incidents, each showing the context and symptoms with the root cause and fix folded away so you can test yourself."
progress: done
---

# Scenario & Troubleshooting Rounds

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

"Checkout latency doubled ten minutes ago. What do you do?" Troubleshooting rounds test whether you can stay methodical with partial information. Interviewers care less about the answer than about the order you work in: impact first, then mitigation, then cause.

## A method that works for any scenario

1. **Impact.** Who is affected, how many, since when? Is it one journey, one region, one tenant? An SLO burn tells you how fast the budget is going ([ch. 51](../observability/ch51-slo-alerting.md)).
2. **What changed?** Deploys, config, flags, traffic, a dependency's release. Most incidents follow a change; deploy events beside the metrics make this a glance ([ch. 47](../observability/ch47-observability-fundamentals.md)).
3. **Mitigate before you understand.** Roll back, shed load, fail over, turn a feature off. Say this early: interviewers listen for it.
4. **Narrow down with signals.** Metrics say *what* and *where*, traces say *which hop*, logs say *why*, profiles say *what the CPU did*. Go from the broad signal to the narrow one.
5. **Root cause**, with evidence: the measurement that confirms it, not the first plausible story.
6. **Prevent the class, not the instance.** A test, an alert, a fitness function or a platform default, so no team hits it again.

## The symptoms interviewers describe most

| Symptom | Usual causes | Check first | Chapter |
|---|---|---|---|
| Latency up, errors flat | A slow dependency; GC pauses; CPU throttling; a lock or pool exhausted | The slow trace's longest span; GC logs; CPU throttling metrics | [42](../resilience/ch42-failure-modes.md), [54](../deployment/ch54-kubernetes-for-spring.md), [67](../performance-scalability/ch67-jvm-tuning-containers.md) |
| Errors up after a deploy | The release | Roll back; then the canary analysis that should have caught it | [58](../deployment/ch58-cicd-gitops.md) |
| Everything restarted at once | A liveness probe that checks a dependency | What the liveness group includes | [52](../observability/ch52-health-checks-actuator.md) |
| Pods OOMKilled with no Java error | Non-heap memory: threads, direct buffers, metaspace | Native memory tracking against the limit | [53](../deployment/ch53-containerization.md), [67](../performance-scalability/ch67-jvm-tuning-containers.md) |
| One slow pod drags p99 | Gray failure; keep-alive connections pinned to it | Per-pod latency; outlier detection | [42](../resilience/ch42-failure-modes.md), [57](../deployment/ch57-service-mesh.md) |
| Consumer lag growing | A slow sink; a hot partition; a poison message | Lag per partition; the DLT | [25](../communication/ch25-asynchronous-messaging.md), [69](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| Duplicates (double charges, double emails) | Retries without idempotency; commit before processing | Where the idempotency key is checked, and in which transaction | [28](../communication/ch28-idempotency-correlation.md) |
| Data "going backwards" | Reads spread across lagging replicas | Which replica served the read | [31](../data/ch31-data-consistency.md) |
| Database connections exhausted | Pools multiplied by a scale-out or a deploy surge | Pool size × pods against `max_connections` | [33](../data/ch33-database-scaling.md) |
| A dashboard that looks fine while users complain | Averaged percentiles; a static threshold above a slow burn | How the percentile is computed; the burn rate | [48](../observability/ch48-metrics-micrometer.md), [51](../observability/ch51-slo-alerting.md) |

The [FoodFleet runbooks](../food-delivery-platform/runbooks.md) work through the platform's alerts in this shape.

## Practice drills

Every chapter has three incidents, each written as context, symptoms, root cause and fix. Here they are as drills: read the context and symptoms, decide what you'd check and what you think the cause is, then open the fold. Generated from the chapters by `tools/interview.py`.

--8<-- "tools/generated/interview/incidents.md"
