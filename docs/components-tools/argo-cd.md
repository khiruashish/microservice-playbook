---
title: "Argo CD"
description: "GitOps delivery at FoodFleet (ADR-058): Argo CD per cluster, automated sync with prune and selfHeal, webhooks from Git, promotion by approved pull request. What chapter 58's minimal reconciler measured about the model: commits applied 1–10 s after push at a 10 s poll (Argo CD defaults to 120 s), a manual scale and an edited variable reverted within one interval, a deleted Deployment recreated, a removed ConfigMap pruned in 7–8 s, and an added variable Git never declared left in place."
progress: done
---

# Argo CD

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A GitOps controller for Kubernetes: `Application`s point at a Git path (plain YAML, kustomize, Helm), and the controller compares desired with live state, syncs, prunes and self-heals; ApplicationSets generate Applications per cluster, environment or pull request |
| **FoodFleet uses** | Argo CD per cluster, watching the environment repository; automated sync with `prune` and `selfHeal`; webhooks from the Git host |
| **Decided in** | [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study), [ADR-055](../deployment/ch55-helm-kustomize.md#8-enterprise-case-study) (promotion) |
| **Learn it in** | [Chapter 58](../deployment/ch58-cicd-gitops.md), [chapter 55](../deployment/ch55-helm-kustomize.md) |
| **Related** | [Argo Rollouts](argo-rollouts.md), [GitHub Actions](github-actions.md), [Helm](helm.md), [Kustomize](kustomize.md) |

## The model, measured

[Chapter 58](../deployment/ch58-cicd-gitops.md) wrote a minimal reconciler (polling every 10 s) to see what GitOps does and doesn't do:

```java title="Reconciler.java (chapter 58)"
--8<-- "code/chapters/ch58-cicd-gitops/src/main/java/dev/foodfleet/ch58/gitops/Reconciler.java:54:71"
```

| Change | Result | After |
|---|---|---|
| 5 commits changing the version | applied | **1.0–9.8 s** |
| `kubectl scale --replicas=5` (Git says 2) | reverted | 1.2–6.9 s |
| `kubectl set env VERSION=hotfix` | reverted | 9.0–9.3 s |
| `kubectl delete deployment` | recreated | 11.3–12.0 s |
| `git rm flags.yaml` | **pruned** the ConfigMap | 7.1–7.9 s |
| `kubectl set env DEBUG=true` (not in Git) | **kept**: nothing to compare against | — |

- **Commit to applied is the polling interval.** Argo CD polls every 120 s by default; FoodFleet uses webhooks.
- **Self-heal reverts what Git declares**, so incident changes must be committed (break-glass changes within a day).
- **What Git doesn't declare, GitOps doesn't see**: fields added by hand survive. Admission policies reject human changes to production namespaces outside break-glass.

## FoodFleet's setup

```yaml title="gitops/menu-prod/kustomization.yaml (chapter 58)"
--8<-- "code/chapters/ch58-cicd-gitops/src/main/resources/gitops/menu-prod/kustomization.yaml"
```

- **One environment repository**; CI opens pull requests that change digests; dev auto-merges, staging and prod need approval.
- **Promotion policy** in the repository's CI: prod's digest must be one staging ran.
- **Rollouts, not Deployments, for canaries** ([Argo Rollouts](argo-rollouts.md)); Argo CD shows their health.
- **`replicas` not in Git** when an HPA owns it, or self-heal fights the autoscaler ([Helm](helm.md)).

## Argo CD or Flux

| | Argo CD | Flux |
|---|---|---|
| UI | Rich web UI, app-of-apps, diff view | CLI-first; UIs from third parties |
| Multi-cluster | Central instance managing many clusters, or one per cluster | One per cluster |
| Sources | Git, Helm repos, OCI | Git, Helm, OCI, buckets |

## Pitfalls

- **`selfHeal` off**: drift accumulates silently.
- **`prune` off**: deleted resources live on.
- **Ignore-differences for everything noisy**: the diff stops meaning anything.
- **Secrets in the repo**: reference external secrets instead.
- **Argo CD with cluster-admin everywhere**: scope its projects to namespaces and resource kinds.
