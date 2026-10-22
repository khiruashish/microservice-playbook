---
title: "Configuration Management"
description: "How configuration reaches FoodFleet's services without rebuilding them: one image per commit promoted by digest, typed and validated settings that fail at startup, ConfigMaps mounted as files with a checksum that rolls pods on change, a watched directory for the few values that change live, secrets from OpenBao, and Spring Cloud Config where a config server still fits. Measured on Kubernetes: environment variables, configtree: and subPath files never saw a ConfigMap change; a watched mount saw it after 1.4 to 76 seconds."
progress: done
---

# Configuration Management

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Getting the values that differ between environments, cities and moments into running services, safely |
| **FoodFleet uses** | ConfigMaps mounted as files (`spring.config.import: configtree:`) with a checksum annotation; a watched directory for live values; secrets from OpenBao through External Secrets; `@ConfigurationProperties` records with validation |
| **Decided in** | [ADR-004](../foundations/ch04-twelve-factor.md#8-enterprise-case-study) (one image, all variation through configuration), [ADR-054](../deployment/ch54-kubernetes-for-spring.md#8-enterprise-case-study), [ADR-055](../deployment/ch55-helm-kustomize.md#8-enterprise-case-study), [ADR-039](../security/ch39-secrets-management.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 4](../foundations/ch04-twelve-factor.md), [chapter 54](../deployment/ch54-kubernetes-for-spring.md), [lab 05](../code-lab/lab-05-config-vault.md) |
| **Related** | [Feature flags](feature-flags.md), [HashiCorp Vault](vault.md), [Helm](helm.md), [Kustomize](kustomize.md) |

## The rules

1. **One image, every environment.** The digest tested is the digest deployed; environments and city cells differ only in configuration (ADR-004). Rebuilding per environment once shipped an untested artifact.
2. **Typed and validated at startup.** A `@ConfigurationProperties` record with Bean Validation fails the start on a bad value, before the pod is ready, instead of on the first order. No defaults for business-critical values.
3. **Choose one change model per value**: rolled out with a restart (most values), or changed live (a few, and the code must expect it).
4. **Secrets are not configuration**: they come from a secrets store, as files, never environment variables ([Vault](vault.md)).

```java title="MenuSettings.java (lab 05)"
--8<-- "code/code-lab/lab-05-config-vault/menu-service/src/main/java/dev/foodfleet/lab05/menu/MenuSettings.java"
```

## Measured: how a ConfigMap change reaches the application

[Chapter 54](../deployment/ch54-kubernetes-for-spring.md) read one discount four ways at once and patched the ConfigMap three times (seconds until the service saw it, two runs):

| Change | Environment variable | `configtree:` (read at startup) | Mounted directory, watched | `subPath` file |
|---|---|---|---|---|
| 10 → 15 | never | never | 1.4 / 76.2 s | never |
| 15 → 20 | never | never | 11.3 / 68.0 s | never |
| 20 → 25 | never | never | 24.4 / 67.1 s | never |

- **Only a mounted directory updates**, after the kubelet's next sync of the pod: plan for a minute or more.
- **Environment variables, `subPath` mounts and anything read once are fixed at container start.** A partial restart then leaves pods disagreeing; FoodFleet once had pods applying different discounts.
- **So:** values that must change together are rolled out: the chart hashes the ConfigMap into a pod-template annotation, so a change is a rollout ([Helm](helm.md)). Values meant to change live go in a watched directory. `subPath` is rejected by admission policy.

## Where the values live

| Option | Strengths | Weaknesses | FoodFleet use |
|---|---|---|---|
| Environment variables | Universal, visible in manifests | Flat; restart to change; secrets leak through `/proc` | Per-city scalars |
| Mounted files (`configtree:`, ConfigMap volume) | Structured, secret-safe, can reload | Volume wiring | Most configuration; credentials as files |
| Spring Cloud Config Server | Git history, per-profile overlays, `/actuator/refresh` | Another service to keep available | Not in clusters; shown in [lab 05](../code-lab/lab-05-config-vault.md) |
| Watched directory or config service | Changes without restart | The code must handle a value changing mid-request | Feature flags, discounts |
| Vault / OpenBao | Dynamic, rotated, audited | Operational complexity | Every secret |

Refresh has a limit worth knowing: lab 05's `MenuSettings` is a record, bound once through its constructor, so `/actuator/refresh` doesn't change it. Only mutable `@ConfigurationProperties` classes and `@RefreshScope` beans rebind. FoodFleet treats that as a feature: a value that changes live is a deliberate decision, not something every setting can do.

## Review and history

Configuration is code: it lives in the environment repository, changes by pull request, and is applied by Argo CD with pruning, so a value deleted from Git is deleted from the cluster ([Argo CD](argo-cd.md)). Chapter 58's flag incident is the reason: a ConfigMap deleted from the repository lived on in the cluster and a new service read its stale value months later.

## Pitfalls

- **Defaults for critical values**: the service starts in production with a development value.
- **Secrets in environment variables**: readable through `/proc/<pid>/environ` and in crash dumps ([chapter 39](../security/ch39-secrets-management.md)).
- **`@RefreshScope` everywhere**: a value changes in the middle of a request, and every refresh rebuilds beans.
- **No checksum on the pod template**: `helm upgrade` changes the ConfigMap and no pod restarts.
