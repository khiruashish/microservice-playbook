---
title: "Kubernetes"
description: "What Kubernetes does to a Spring service and FoodFleet's rules for it (ADR-054), measured on a real cluster: a 250m CPU limit made JVM startup five times slower and p99 93–97 ms (no limit: 10–16 ms), so CPU requests without limits; ConfigMap changes reached only a watched mounted directory, after 1–76 s; a keep-alive pool sent every request to the one old pod after a scale-out; a drain without a PodDisruptionBudget failed 295–331 requests, with one 0–1. Probes, shutdown and the Actuator port from ADR-052."
progress: done
---

# Kubernetes

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | The container orchestrator: Deployments and Pods, Services (kube-proxy), ConfigMaps and Secrets, probes, PodDisruptionBudgets, HPAs, NetworkPolicies, and an API server every tool talks to |
| **FoodFleet uses** | Every service as a Deployment from the shared `foodfleet-service` chart, with the resource, configuration, disruption and probe rules below |
| **Decided in** | [ADR-054](../deployment/ch54-kubernetes-for-spring.md#8-enterprise-case-study), [ADR-052](../observability/ch52-health-checks-actuator.md#8-enterprise-case-study), [ADR-056](../deployment/ch56-autoscaling.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 54](../deployment/ch54-kubernetes-for-spring.md), [chapter 52](../observability/ch52-health-checks-actuator.md), [lab 15](../code-lab/lab-15-kubernetes-helm-keda.md) |
| **Related** | [Helm](helm.md), [Kustomize](kustomize.md), [KEDA](keda.md), [Gateway API](gateway-api-ingress.md), [Spring Boot Actuator](spring-boot-actuator.md) |

## A Spring service's Deployment

```yaml title="deployment.yaml (chapter 54)"
--8<-- "code/chapters/ch54-kubernetes-for-spring/src/main/resources/k8s/deployment.yaml"
```

## Measured on a real cluster

**CPU limits** ([chapter 54](../deployment/ch54-kubernetes-for-spring.md)), one replica, 20 requests/s of 5 ms CPU each:

| Resources | Ready after | JVM "started in" | p99 | Throttled time |
|---|---:|---:|---:|---:|
| limit 250m | 6.2–6.5 s | **3,900–4,007 ms** | **93–97 ms** | 927–2,239 ms |
| limit 1000m | 2.9–3.1 s | 939–950 ms | 11 ms | 0 |
| **request 250m, no limit** | **2.2–2.7 s** | **723–797 ms** | 10–16 ms | 0 |

FoodFleet sets CPU requests from 30 days' p95 use, **no CPU limits**, `-XX:ActiveProcessorCount` equal to the request rounded up, and memory limit = request with the heap at 75%.

**Configuration changes** (seconds until the service saw a new value):

| Environment variable | `configtree:` (read once) | Mounted directory, watched | `subPath` file |
|---|---|---|---|
| never | never | **1.4–76.2 s** | never |

Live values (discounts, flags) go in a watched mounted directory; everything else rolls the pods through a ConfigMap hash ([Helm](helm.md)). `subPath` for configuration is refused by admission policy.

**Scale-out with keep-alive clients:** new connections spread to new pods within seconds; a pooled keep-alive client sent **all 250 requests per window to the old pod** for the rest of the run. kube-proxy balances connections, not requests: FoodFleet caps client connection lifetime at 30 s, and gRPC goes through the mesh.

**Node drain:**

| | Ready during the drain | Failed requests |
|---|---:|---:|
| No PodDisruptionBudget | **0 of 3** | **295–331** |
| PDB `minAvailable: 2` | 2 of 3 | 0–1 |

Every multi-replica service has a PDB (`maxUnavailable: 1`) and zone topology spread.

## Probes and shutdown

Liveness and readiness on Spring Boot's probe groups only, a startup probe, graceful shutdown with `preStop: sleep: 5`: [Spring Boot Actuator](spring-boot-actuator.md) has the measurements (a database in liveness: 6 restarts in a 60 s outage; a preStop drain: 0 failed requests in a rolling restart).

## Pitfalls

- **CPU limits on JVMs**, or none of `ActiveProcessorCount` and requests.
- **Environment variables for values expected to change live**.
- **Keep-alive pools without a lifetime cap**: autoscaled pods sit idle.
- **No PDB**, or a PDB with no room (`minAvailable` = replicas): drains never finish.
- **`latest` tags**, `imagePullPolicy` guesswork: deploy by digest.
- **Business logic in init containers or sidecars nobody owns**.
