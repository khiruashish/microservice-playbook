---
title: "Kubernetes & Service Mesh"
description: "How FoodFleet's 21 container workloads run on Kubernetes: one shared Helm chart that applies ADRs 52–56, 59, 60 and 70, values generated from the platform model, all 21 services rendered, checked against 20 rules each naming its ADR, and accepted by a server-side dry run on k3s 1.37. Chapter 55's own lab chart passes 7 of the same 20. Plus the mesh, the cluster, and four places where the chapters set different numbers for the same thing."
progress: done
---

# Kubernetes & Service Mesh

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Part 8 decided, one chapter at a time, how a FoodFleet service runs on Kubernetes: its probes ([52](../observability/ch52-health-checks-actuator.md)), its image ([53](../deployment/ch53-containerization.md)), its resources and spread ([54](../deployment/ch54-kubernetes-for-spring.md)), its chart ([55](../deployment/ch55-helm-kustomize.md)), its autoscaling ([56](../deployment/ch56-autoscaling.md)), its mesh ([57](../deployment/ch57-service-mesh.md)) and its start-up ([60](../deployment/ch60-native-images-startup.md)). Each chapter's lab applied its own decision. This page puts them all in one chart and checks the result for every service.

## One chart, every decision

[ADR-055](../deployment/ch55-helm-kustomize.md) says every service deploys with a shared `foodfleet-service` chart and teams own only a values file. `code/foodfleet/deploy/foodfleet-service/` is that chart with the platform's decisions applied:

| What the chart sets | Value | Decision |
|---|---|---|
| Image | By digest only; non-root, read-only root filesystem, all capabilities dropped | [ADR-004](../foundations/ch04-twelve-factor.md), [ADR-053](../deployment/ch53-containerization.md) |
| JVM flags | `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError`, `ActiveProcessorCount` from the CPU request, the AOT cache | [ADR-053](../deployment/ch53-containerization.md), [ADR-054](../deployment/ch54-kubernetes-for-spring.md), [ADR-060](../deployment/ch60-native-images-startup.md) |
| Resources | A CPU request and no CPU limit; memory limit equal to request | [ADR-054](../deployment/ch54-kubernetes-for-spring.md), [ADR-070](../performance-scalability/ch70-finops.md) |
| Probes | Liveness and readiness on Spring Boot's probe groups, on the Actuator port 8081; a 2-minute startup budget | [ADR-052](../observability/ch52-health-checks-actuator.md) |
| Shutdown | `preStop` sleep 5 s, 30 s grace | [ADR-052](../observability/ch52-health-checks-actuator.md) |
| Availability | A PDB with `maxUnavailable: 1`; spread across zones | [ADR-054](../deployment/ch54-kubernetes-for-spring.md) |
| Configuration | Files in a watched directory, never `subPath`; a checksum annotation so changes roll out | [ADR-054](../deployment/ch54-kubernetes-for-spring.md), [ADR-055](../deployment/ch55-helm-kustomize.md) |
| Rollout | `maxUnavailable: 0`; no `replicas` field, because the HPA owns it | [ADR-055](../deployment/ch55-helm-kustomize.md) |
| Autoscaling | CPU at 60% of requests; 300 s scale-down window; down at most 50% a minute | [ADR-056](../deployment/ch56-autoscaling.md) |
| Identity | A service account per service | [ADR-038](../security/ch38-service-to-service-security.md), [ADR-059](../deployment/ch59-cloud-native-platforms.md) |
| Network | The Actuator port only from the monitoring namespace | [ADR-052](../observability/ch52-health-checks-actuator.md) |
| Cost | A team label on everything | [ADR-070](../performance-scalability/ch70-finops.md) |

```yaml title="code/foodfleet/deploy/foodfleet-service/templates/deployment.yaml (the container)"
--8<-- "code/foodfleet/deploy/foodfleet-service/templates/deployment.yaml:33:70"
```

## Values from the model

Teams own a values file each, and most of it follows from what the [platform model](index.md#how-these-pages-are-made) already knows. `generate.py` writes one per deployable (21; the two functions run on Lambda):

- **Name, team and namespace** from the model.
- **Minimum replicas:** 3, one per zone, for the 7 services some journey can't work without (from the same journey walk as [resilience](resilience.md#what-fails-when-a-service-is-down)); 2 for the other 14.
- **gRPC providers** (`kitchen-service`, `dispatch-optimizer`, `eta-service`) get Spring gRPC's port 9090 on the pod and the Service, from the model's gRPC calls ([ADR-023](../communication/ch23-grpc.md)).
- **tracking-gateway** gets 1.5 GiB, because an instance holds about 20,000 streams in about 1 GB, and a 75-second grace period, because its streams drain over 60 seconds ([ADR-026](../communication/ch26-websockets-sse.md)).

```yaml title="code/foodfleet/deploy/values/tracking-gateway.yaml"
--8<-- "code/foodfleet/deploy/values/tracking-gateway.yaml"
```

Image digests are placeholders; in the pipeline, CI writes the digest it built ([ADR-055](../deployment/ch55-helm-kustomize.md)).

## Checked three ways

**1. Rendered.** `generate.py` renders every service with `helm template` (Helm 4.1.4).

**2. Against the decisions.** `conformance.py` runs 20 checks on each rendered service, one per rule, each naming its ADR, the way chapter 72's platform checks do:

```console
$ python3 code/foodfleet/deploy/generate.py
rendered 21 services: 7 with minReplicas 3, 14 with 2
$ python3 code/foodfleet/deploy/conformance.py code/foodfleet/deploy/build/rendered
customer-bff                             20 of 20
customer-service                         20 of 20
…
zone-service                             20 of 20
21 of 21 manifests conform
```

For comparison, chapter 55's lab chart (built to show Helm's mechanics, not the whole platform) rendered with its production values:

```text
ch55-chart-prod                           7 of 20  FAIL: ADR-052 liveness on /actuator/health/liveness, …
```

It misses the probes, the shutdown drain, the Actuator network policy, the security context, `ActiveProcessorCount`, the zone spread, the rollout settings, the HPA behaviour, the service account, the AOT cache and the team label. That isn't a flaw in chapter 55, whose subject was Helm. It shows how far a chart drifts when each decision lives in its own chapter, which is the problem [ADR-072](../governance-org/ch72-platform-engineering.md) solves with versioned templates and conformance checks.

**3. By the API server.** Every rendered service was applied with `kubectl apply --dry-run=server` to a k3s 1.37 cluster, which runs admission and schema validation without creating anything: **21 of 21 accepted**. That includes `preStop.sleep`, which needs Kubernetes 1.30 or later.

What the dry run can't show is whether the services start and pass their probes; that needs the services themselves, which the [local stack](docker-local-stack.md) covers.

## The mesh

FoodFleet runs Istio in ambient mode ([ADR-057](../deployment/ch57-service-mesh.md)):

- **ztunnel on every node** gives mTLS and L4 authorization without a proxy per pod. Chapter 57 measured each sidecar at 208–217m CPU at 500 requests a second, twice what the application used.
- **Strict mTLS** in every namespace, after a two-week permissive phase.
- **AuthorizationPolicies** name the allowed callers per service. FoodFleet's are [generated from the model](security.md#who-may-call-whom).
- **Waypoint proxies** (L7) only for the gateway's upstreams, order-service and payment-service: per-request balancing, outlier detection, and two retries on 5xx within a budget.
- **Clients keep** their timeouts and circuit breakers, and drop their own retries.

Per-request balancing matters because keep-alive connections stick: through a plain Service, 1–2 of 4 copies sat idle and new copies stayed at 0% after a scale-out ([chapter 68](../performance-scalability/ch68-horizontal-scaling.md)).

## The cluster

| Layer | Choice | Decision |
|---|---|---|
| Kubernetes | EKS, nodes by Karpenter | [ADR-059](../deployment/ch59-cloud-native-platforms.md) |
| Capacity | An on-demand base sized for each service's peak; spot above it across instance types and zones; commitments for the base | [ADR-070](../performance-scalability/ch70-finops.md) |
| Cloud identity | EKS Pod Identity per service; no access keys | [ADR-059](../deployment/ch59-cloud-native-platforms.md) |
| Edge | Gateway API `HTTPRoute`s | [ADR-054](../deployment/ch54-kubernetes-for-spring.md) |
| Peaks | Minimums raised by a scheduled job 15 minutes before lunch and dinner; spare room for two pods per zone | [ADR-056](../deployment/ch56-autoscaling.md) |
| Managed data | MSK for Kafka, Aurora PostgreSQL, multi-AZ | [ADR-059](../deployment/ch59-cloud-native-platforms.md) |

## Where the chapters set different numbers

Building one chart meant choosing between decisions that disagree. The chart's choices, and why:

| Setting | The chapters say | The chart does |
|---|---|---|
| **Heap size** | ADR-053 and ADR-054: `MaxRAMPercentage=75`. ADR-067, later: compute it per service from the measured live set and native memory | 75 by default, with `heapPercent` per service, so ADR-067's numbers can be set as each service's load test produces them |
| **CPU requests** | ADR-054: last 30 days' p95 use. ADR-056: VPA recommendations, reviewed quarterly. ADR-070: VPA recommendations, values updated monthly | A value per service; the monthly VPA review (ADR-070, the latest) sets it |
| **Retries** | ADR-042 and ADR-018: in the service closest to the dependency. ADR-057: in the mesh's waypoints, and clients drop theirs | Not the chart's concern, but the two can't both hold: with waypoints, retries move to the mesh for the services behind them |
| **Graceful shutdown** | ADR-052: 30 s grace for every service. ADR-026: tracking streams drain over 60 s | 30 s by default; 75 s for tracking-gateway |
| **Scaling signal** | ADR-056: CPU for request/response services. ADR-068: scale on the signal that limits the service | CPU for all 21; tracking-gateway is limited by open streams, not CPU, so it should scale on connection count, which needs a custom metric the chart doesn't have yet |

Each of these is a candidate for a superseding ADR ([ADR-073](../governance-org/ch73-adrs-fitness-functions.md)).
