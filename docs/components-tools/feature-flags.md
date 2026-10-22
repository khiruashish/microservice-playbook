---
title: "Feature Flags"
description: "Separating deploying code from releasing behaviour: what flags are for (release, experiment, operational kill switch, permission) and what they cost, where FoodFleet keeps them (a watched mounted directory that changes without a restart, pruned by GitOps when deleted), the flag that outlived its deletion and enabled dead code months later, OpenFeature as the vendor-neutral API, and flags against canaries."
progress: done
---

# Feature Flags

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A runtime switch that turns behaviour on or off, for everyone or for chosen users, without a deployment |
| **FoodFleet uses** | Flags as values in a ConfigMap mounted as a watched directory, changed by pull request to the environment repository and applied by Argo CD with pruning |
| **Decided in** | [ADR-054](../deployment/ch54-kubernetes-for-spring.md#8-enterprise-case-study) (live values in a watched mount), [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study) (prune on) |
| **Learn it in** | [Chapter 54](../deployment/ch54-kubernetes-for-spring.md), [chapter 58](../deployment/ch58-cicd-gitops.md), [chapter 16](../architecture-patterns/ch16-strangler-fig-migration.md) (flags around a migration) |
| **Related** | [Configuration management](configuration-management.md), [Argo Rollouts](argo-rollouts.md), [Argo CD](argo-cd.md) |

## Four kinds of flag

| Kind | Example at FoodFleet | Lives for | Who flips it |
|---|---|---|---|
| **Release** | `new-checkout`: code merged and deployed dark, turned on when ready | Days to weeks, then deleted | The team |
| **Experiment** | A/B test of the cart layout, by customer cohort | The experiment | Product, with an analysis |
| **Operational (kill switch)** | Turn off recommendations when the model service struggles | Permanently | On-call |
| **Permission** | A feature for partner-plan restaurants only | Permanently | Product; really an entitlement |

Each kind wants different handling. Release flags must die quickly; kill switches must be tested regularly or they won't work when needed; permission flags belong in the domain model, not in a flag service.

## Flags against canaries

From [chapter 58](../deployment/ch58-cicd-gitops.md)'s comparison of release strategies:

| Strategy | Exposure if it's bad | Rollback | FoodFleet uses it for |
|---|---|---|---|
| Canary with analysis | A small slice of traffic, for seconds | Weight to 0 | Every customer-facing release |
| Feature flag | Chosen users | Flag off | Behaviour changes inside a release |

They combine: the canary proves the new build is healthy with the flag **off**; the flag then exposes the behaviour to staff, one city, everyone. Separating the two lets a team deploy whenever the code is ready and release when the business is.

## Where FoodFleet keeps them

```yaml title="gitops/menu-prod/flags.yaml (chapter 58)"
--8<-- "code/chapters/ch58-cicd-gitops/src/main/resources/gitops/menu-prod/flags.yaml"
```

- **A watched mounted directory**, so a change reaches pods without a restart; [chapter 54](../deployment/ch54-kubernetes-for-spring.md) measured 1.4 to 76 seconds for a ConfigMap change to arrive. Environment variables never update, so a flag in one is a deployment.
- **Changed by pull request**, so every flip has an author, a reason and a history.
- **Pruned when deleted.** Chapter 58's "flag that wouldn't die": a team deleted a flag's ConfigMap file when the feature launched, the cluster kept the ConfigMap, and months later a new service read the stale value and enabled dead code. With Argo CD's `prune: true`, the deleted file was gone from the cluster within 7–8 seconds.

A minute of propagation is fine for a release flag and too slow for a kill switch in an incident. For those, a flag service with streaming updates (or a direct `kubectl` change through break-glass, committed afterwards) is the faster path.

## OpenFeature

OpenFeature (a CNCF project) is a vendor-neutral API for evaluating flags: the code calls `client.getBooleanValue("new-checkout", false, context)`, and a provider behind it reads from a file, flagd, Unleash, LaunchDarkly or a cloud service. Switching providers doesn't touch the code, and the evaluation context (user, tenant, city) is passed explicitly, which makes targeting testable.

| Option | Strengths | Watch out for |
|---|---|---|
| Values in a ConfigMap (FoodFleet) | No new service; Git history; GitOps pruning | Minute-scale propagation; no per-user targeting without code |
| flagd (OpenFeature's reference daemon) | Targeting rules, fast updates, flags as files | One more component to run |
| Unleash, Flagsmith (open source) | UI, targeting, gradual rollout, audit | A service on the request path; cache its answers |
| LaunchDarkly, cloud services | Mature targeting and experimentation, streaming | Cost; data leaves your platform |

## Pitfalls

- **Flags that never die**: each one doubles the paths to test. Give release flags an expiry date and a ticket to remove them.
- **Flags evaluated in many places for one decision**: evaluate once per request and pass the result down.
- **Flag checks in hot loops** against a remote service: cache, and fail to a safe default.
- **Kill switches never exercised**: flip them in game days ([chapter 45](../resilience/ch45-chaos-engineering.md)).
- **Permissions as flags**: entitlements belong in the domain and its audit trail.
