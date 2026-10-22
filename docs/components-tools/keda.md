---
title: "KEDA"
description: "Event-driven autoscaling for FoodFleet's Kafka consumers (ADR-056): KEDA scales on consumer lag, which grows before CPU does, while request/response services keep CPU-based HPAs. The shared chart renders a ScaledObject instead of an HPA when a service sets autoscaling.kafkaLag (lag 300 at a threshold of 50 means 6 replicas, capped at the partition count), checked against KEDA 2.20.2's CRDs. Plus what chapter 56 measured about any HPA: first scale-up 29–34 s after a step, and a scale-down window that prevents paying start-up cost on every swing."
progress: done
---

# KEDA

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Kubernetes Event-Driven Autoscaling: a `ScaledObject` declares triggers (Kafka lag, queue depth, Prometheus queries, cron, …); KEDA serves them as external metrics to an HPA it creates, and can scale to zero |
| **FoodFleet uses** | KEDA on Kafka consumer lag for consumers (kitchen, order and notification consumers), with CPU as a second trigger |
| **Decided in** | [ADR-056](../deployment/ch56-autoscaling.md#8-enterprise-case-study) |
| **Learn it in** | [Lab 15](../code-lab/lab-15-kubernetes-helm-keda.md), [chapter 56](../deployment/ch56-autoscaling.md), [chapter 25](../communication/ch25-asynchronous-messaging.md) |
| **Related** | [Kafka](kafka.md), [Helm](helm.md), [Kubernetes](kubernetes.md) |

## Why lag

CPU lags a backlog: the queue grows before CPU does, and a consumer waiting on a slow database uses little CPU while falling behind. Lag measures the work waiting.

## In the shared chart

```yaml title="values/kitchen-service.yaml (lab 15)"
--8<-- "code/code-lab/lab-15-kubernetes-helm-keda/values/kitchen-service.yaml"
```

```yaml title="foodfleet-service/templates/hpa.yaml, the KEDA branch"
--8<-- "code/foodfleet/deploy/foodfleet-service/templates/hpa.yaml:22:53"
```

- **KEDA creates the HPA**, so the chart renders none: two HPAs on one Deployment overwrite each other every 15 s.
- **`lagThreshold` is per replica**: lag 300 at 50 means 6 replicas.
- **Replicas beyond the partition count do nothing**; KEDA caps there unless `allowIdleConsumers` is set (`order-events` has 24 partitions).
- **CPU stays as a second trigger** at 60%, for when processing itself is the bottleneck.
- **`autoscaling.target: Rollout`** points the scaler at an Argo Rollout instead of a Deployment.

[Lab 15](../code-lab/lab-15-kubernetes-helm-keda.md) checks the rendered ScaledObject against KEDA 2.20.2's CRDs with a server dry run. Trigger metadata is free-form in the schema, so a misspelt `lagTreshold` passes; the key names were checked against the Kafka scaler's source. The chart change left the other 21 services' manifests byte-for-byte the same.

## HPA behaviour still applies

KEDA's scaling goes through an HPA, so [chapter 56](../deployment/ch56-autoscaling.md)'s measurements hold:

| | Result |
|---|---|
| First scale-up after a step in load | 29–34 s (metrics need samples; the HPA checks every 15 s) |
| Readiness failing until warm vs ready at once | peak **5** pods vs **8** (warm-up CPU counted as load) |
| Bursty load, scale-down window 300 s vs 0 s | 2 vs **10–11** scaling changes; 97–192 vs **325–432** failed requests |

FoodFleet keeps the 300 s scale-down window, readiness gated on warm-up, and raises minimums before lunch and dinner peaks rather than waiting for any metric.

## Pitfalls

- **An HPA and a ScaledObject on the same workload**.
- **More replicas than partitions**.
- **Scale to zero for latency-sensitive consumers**: the first message waits for a cold JVM.
- **Lag measured on a consumer group that's rebalancing on every scale**: use cooperative rebalancing and a scale-down window.
- **Credentials for the Kafka trigger** in plain ScaledObject metadata: use a `TriggerAuthentication`.
