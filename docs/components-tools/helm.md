---
title: "Helm"
description: "FoodFleet deploys every service with one shared Helm chart (foodfleet-service) and a values file per environment (ADR-055), with Helm 4's server-side apply, replicas omitted when autoscaling, a checksum annotation so config changes roll pods, --rollback-on-failure, and promotion by digest. Measured on a real cluster: server-side apply turned an autoscaled Deployment into a conflict instead of silently resetting it; without the checksum 0 of 2 pods rolled; a failed release left 0 failed requests because of the rolling update, not the rollback; a moved tag put two builds behind one Service."
progress: done
---

# Helm

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Kubernetes' package manager: charts of Go-templated manifests rendered with values, installed as versioned releases with history and rollback. **Helm 4** applies server-side by default and replaces `--atomic` with `--rollback-on-failure` |
| **FoodFleet uses** | The shared `foodfleet-service` chart for all services; teams own one values file per environment |
| **Decided in** | [ADR-055](../deployment/ch55-helm-kustomize.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 55](../deployment/ch55-helm-kustomize.md), [lab 15](../code-lab/lab-15-kubernetes-helm-keda.md) |
| **Related** | [Kustomize](kustomize.md), [Kubernetes](kubernetes.md), [Argo CD](argo-cd.md), [KEDA](keda.md) |

## One chart, a values file per service

```yaml title="values/menu-service.yaml (lab 15)"
--8<-- "code/code-lab/lab-15-kubernetes-helm-keda/values/menu-service.yaml"
```

The chart renders Deployment, Service, ConfigMap, HPA (or a KEDA ScaledObject), PDB and NetworkPolicy; a conformance check runs on every rendered service ([lab 15](../code-lab/lab-15-kubernetes-helm-keda.md): 20 of 20 rules for each).

```yaml title="templates/deployment.yaml, top (chapter 55)"
--8<-- "code/chapters/ch55-helm-kustomize/src/main/resources/helm/foodfleet-service/templates/deployment.yaml:1:24"
```

## Measured on a real cluster

**Who owns `replicas`** (deployed with 2, scaled to 5, then re-applied):

| Re-applied with | Result | Replicas after |
|---|---|---:|
| `kubectl apply` (client-side) / Helm 3-style merge | ok | **2**: the scaling silently undone |
| `helm upgrade` (Helm 4, server-side) | **conflict, release failed** | 5 |
| …with `--force-conflicts` | ok | 2 |
| Manifest without `spec.replicas` | ok | **5** |

So the chart omits `replicas` when autoscaling, and conflicts fail the pipeline for a human to resolve.

**Config changes:**

| Deployed with | Pods replaced | Env var at the end |
|---|---:|---:|
| Chart with `checksum/config` annotation | **2 of 2** | new |
| Same chart without it | **0 of 2** | **old** |

**A release that crashes at start-up** (3 replicas under load):

| Deployed with | Returned | Failed requests |
|---|---|---:|
| `helm upgrade --rollback-on-failure --timeout 60s` | exit 1 after 61 s, rolled back | 0 |
| `kubectl apply` only | exit 0 at once; 1 pod crash-looping | 0 |

The rolling update (default `maxUnavailable` rounds to 0) protected users; the rollback cleaned up. FoodFleet sets the timeout to twice the service's normal rollout time and `maxUnavailable: 0`.

**A tag moved in the registry:** scaled from 2 to 4 pods, `menu-service:stable` ran **2 × 1.4.0 and 2 × 1.4.1** on a fresh node; by digest, 4 × 1.4.0. The chart refuses to render prod without a digest, and a promotion policy requires prod's digest to be the one staging ran.

## Pitfalls

- **Per-environment chart copies**: drift.
- **`replicas` in values when an HPA owns it**.
- **Config without a checksum annotation**.
- **Releases without `--wait`/timeout**: the pipeline reports success while pods crash-loop.
- **Tags in prod values**.
- **Secrets in values files**: use the external secrets operator ([Vault](vault.md)).
- **Logic-heavy templates**: if a value needs a loop of conditionals, it probably belongs in the chart's defaults.
