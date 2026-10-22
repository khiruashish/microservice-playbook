---
title: "Kustomize"
description: "Template-free Kubernetes configuration with bases and overlays: where FoodFleet uses kustomize (platform components over upstream manifests) and where Helm (every service, ADR-055), measured side by side in chapter 55: both render in tens of milliseconds; a configMapGenerator's hashed name rolled 2 of 2 pods on a config change and disableNameSuffixHash rolled 0; an overlay with a digest placeholder passed the API server's dry run and was caught only by the promotion policy."
progress: done
---

# Kustomize

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A template-free way to customise Kubernetes YAML: a **base** plus **overlays** that patch it per environment, with generators for ConfigMaps and Secrets; built into `kubectl -k` |
| **FoodFleet uses** | Overlays over upstream manifests for platform components (ingress, operators); services use the Helm chart |
| **Decided in** | [ADR-055](../deployment/ch55-helm-kustomize.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 55](../deployment/ch55-helm-kustomize.md) |
| **Related** | [Helm](helm.md), [Argo CD](argo-cd.md), [Kubernetes](kubernetes.md) |

## Base and overlay

```yaml title="kustomize/base/kustomization.yaml (chapter 55)"
--8<-- "code/chapters/ch55-helm-kustomize/src/main/resources/kustomize/base/kustomization.yaml"
```

```yaml title="kustomize/overlays/prod/kustomization.yaml (chapter 55)"
--8<-- "code/chapters/ch55-helm-kustomize/src/main/resources/kustomize/overlays/prod/kustomization.yaml"
```

## Measured beside Helm

[Chapter 55](../deployment/ch55-helm-kustomize.md) deployed the same service both ways:

| | Helm | kustomize |
|---|---:|---:|
| Render dev / staging / prod | 45–62 ms | 30–62 ms |
| Config change rolls pods | with `checksum/config`: 2 of 2 | with `configMapGenerator`'s hashed name: **2 of 2** |
| …without it | 0 of 2 | `disableNameSuffixHash: true`: **0 of 2** |

The hashed name changes the pod template, so a config change becomes an ordinary rollout with readiness and rollback. Old ConfigMaps are pruned after 7 days so rollbacks still find theirs.

| Prod release | API server dry run | Promotion policy |
|---|---|---|
| Overlay as checked in (digest placeholder) | **valid** | rejected: not a sha256 digest |
| Overlay naming 1.4.1 while staging tested 1.4.0 | valid | rejected: prod runs a digest staging didn't test |

The API server validates shape, not intent: render and check every environment on every commit.

## Helm or kustomize

| | Helm | kustomize |
|---|---|---|
| Model | Templates + values | Patches over plain YAML |
| Packaging, versions, releases | Charts, release history, rollback | None (Git is the history) |
| Reading the output | Render to see it | The base *is* the output, plus patches |
| Fits | One chart for many similar services | Adapting manifests you don't own (upstream operators) |

They combine: kustomize can inflate a Helm chart (`helmCharts`), and Argo CD renders either.

## Pitfalls

- **`disableNameSuffixHash`** on configuration that pods read at start.
- **Deep overlay chains**: a value set in three places.
- **Strategic-merge patches on lists** without the right keys: replaced instead of merged; use JSON patches for precision.
- **Images by tag in overlays**: use `images:` with `digest:`.
