---
title: "Argo Rollouts"
description: "Canary releases at FoodFleet (ADR-058): Argo Rollouts at 10, 25, 50, 100% with five minutes of analysis per step, the canary scaled with its weight, per-version error rate and p99 no more than 20% above stable, early abort on 10 errors. Measured in chapter 58: a release failing 5% was stopped at 10% after about 200 requests (7–10 failed) against 944–953 failed as a rolling update; a healthy canary on one pod was aborted at 50% by its own latency. Lab 16's AnalysisTemplate, tested with promtool."
progress: done
---

# Argo Rollouts

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A Kubernetes controller and `Rollout` resource for progressive delivery: canary and blue/green strategies with traffic routing (Gateway API plugin, Istio, NGINX, ALB, …) and `AnalysisTemplate`s that query metrics to promote or abort |
| **FoodFleet uses** | Canaries for services through Gateway API weights; the shared chart can point its HPA at a Rollout (`autoscaling.target: Rollout`) |
| **Decided in** | [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 58](../deployment/ch58-cicd-gitops.md), [lab 16](../code-lab/lab-16-istio-canary.md) |
| **Related** | [Argo CD](argo-cd.md), [Gateway API](gateway-api-ingress.md), [Istio](istio.md), [Monitoring](monitoring-prometheus-grafana.md) |

## What a canary buys

[Chapter 58](../deployment/ch58-cicd-gitops.md), 100 requests a second:

| Release | Outcome | Requests served by the new version | Failed |
|---|---|---:|---:|
| Failing 5%, as a canary | **aborted at 10%** after 19–22 s | 192–223 | **7–10** |
| Failing 5%, rolling update, undone after 2 min | rolled out, then undone | 11,854–11,879 | **944–953** |
| Healthy, canary on 1 pod | promoted (run 2); **aborted at 50%** on latency (run 1) | 6,065; 1,766 | 0 |

- **Exposure fell by two orders of magnitude**, and two minutes is a fast human reaction.
- **An early burst rule** (6–10 errors in the first 200 requests) stopped it without waiting for the window.
- **Capacity must follow weight**: a single canary pod at 50% of traffic was slow, and the latency check aborted a healthy release. Hence `setCanaryScale: matchTrafficWeight`.

## The Rollout

```yaml title="rollouts/rollout.yaml (lab 16)"
--8<-- "code/code-lab/lab-16-istio-canary/rollouts/rollout.yaml"
```

`workloadRef` takes over the shared chart's Deployment; `dynamicStableScale` shrinks stable as the canary grows. FoodFleet routes with the Gateway API plugin (in ambient mode the waypoint implements the `HTTPRoute`); lab 16 uses Istio's `VirtualService`, which needs no plugin. The steps are the same.

## The analysis, tested before it runs

```yaml title="rollouts/analysis.yaml (lab 16)"
--8<-- "code/code-lab/lab-16-istio-canary/rollouts/analysis.yaml"
```

- **Per version**: the canary's own Service, never the pooled rate.
- **Latency relative to stable** (ADR-058: no more than 20% slower), so a busy node slows both and aborts neither.
- **No verdict without traffic**: fewer than 100 requests returns -1, inconclusive; four of those pause for a person.
- **`or on() vector(0)`** so a canary with no errors reads 0, not "no data".

[Lab 16](../code-lab/lab-16-istio-canary.md) runs both queries through promtool against synthetic series (the p99 ratio is 2.0, not 199/99, because Prometheus interpolates within buckets), and dry-runs every resource against the CRDs.

## Pitfalls

- **Analysis on the service's total error rate**: 10% of traffic failing 5% is 0.5% overall.
- **A fixed latency threshold**: aborts on busy nodes, passes slow releases on quiet ones.
- **Canary capacity not scaled with weight**.
- **Blue/green with keep-alive clients**: chapter 58's switch left a pooled client on the old slot for 1,527 requests; switch at a proxy that balances per request.
- **Database migrations a canary's old version can't live with** ([Flyway](flyway-liquibase.md)).
