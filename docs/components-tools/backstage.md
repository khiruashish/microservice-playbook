---
title: "Backstage"
description: "FoodFleet's developer portal (ADR-072, ADR-071): Backstage's scaffolder creates services from a versioned template that records its version, catalog-info.yaml in every repository is checked in CI, and scorecards show conformance. Measured: a name to two ready pods answering in 6.5–8.0 s passing 12 of 12 checks; fleet conformance 96% from the current template, 42% hand-written, 8% from the old template; a three-way-merge upgrade took four old services from 4 to 40 of 48 checks; 15 of 31 catalog entities failed a governance check, only 3 on Backstage's required fields."
progress: done
---

# Backstage

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Spotify's open-source developer portal (CNCF): a **software catalog** of components, APIs, groups and systems from `catalog-info.yaml` files; a **scaffolder** that creates repositories from templates; TechDocs; plugins for CI, Kubernetes, scorecards |
| **FoodFleet uses** | The scaffolder for new services from versioned templates; the catalog as the record of owners, APIs and consumers; scorecards per service and team |
| **Decided in** | [ADR-072](../governance-org/ch72-platform-engineering.md#8-enterprise-case-study), [ADR-071](../governance-org/ch71-api-governance.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 72](../governance-org/ch72-platform-engineering.md), [chapter 71](../governance-org/ch71-api-governance.md), [chapter 7](../foundations/ch07-conways-law-team-topologies.md) |
| **Related** | [OpenAPI](openapi-springdoc.md), [AsyncAPI](asyncapi.md), [Helm](helm.md), [GitHub Actions](github-actions.md) |

## The golden path

[Chapter 72](../governance-org/ch72-platform-engineering.md): rider-ratings, from template v2:

| Step | Time |
|---|---:|
| Render the template (8 files) | under 0.05 s |
| Compile, build the image, load it | 3.3–3.5 s |
| Apply and wait for 2 ready pods | 2.3–4.0 s |
| First answer through the Service | 0.7 s |
| **Total** | **6.5–8.0 s**, 12 of 12 checks |

The template generated 90 lines of platform files (Dockerfile, manifests, VPA, catalog entry, OpenAPI document, CI workflow) and 45 lines of application code. The catalog entry it writes:

```yaml title="templates/v2/catalog-info.yaml (chapter 72)"
--8<-- "code/chapters/ch72-platform-engineering/src/main/resources/templates/v2/catalog-info.yaml"
```

## Templates that keep reaching services

| Fleet of twelve | Conformance |
|---|---:|
| From the current template | **96%** |
| Hand-written | 42% |
| From the previous template | **8%** |

A template decision reaches only new services unless updates flow. ADR-072: each template release opens a **three-way-merge** pull request in every generated service (as Copier does):

```java title="Upgrade.java (chapter 72)"
--8<-- "code/chapters/ch72-platform-engineering/src/main/java/dev/foodfleet/ch72/scaffold/Upgrade.java:49:57"
```

Four drifted v1 services went from **4 to 40 of 48** checks automatically, with 4 conflicts where teams had changed the same settings (a JVM flag, memory, a CI step).

## A catalog worth trusting

[Chapter 71](../governance-org/ch71-api-governance.md) checked 37 entities:

| Check | Failing |
|---|---:|
| Backstage's required fields (type, lifecycle, owner, definition) | 3 |
| Owner is a group in the catalog | 2 |
| Lifecycle is experimental, production or deprecated | 1 (`prod`) |
| `providesApis` names an API in the catalog | 1 |
| The API's OpenAPI document lints clean | 4 |
| A production component has a runbook annotation | 3 |

**16 of 31** components and APIs passed everything. Backstage's schema is a floor; FoodFleet's CI checks the rest. The catalog declared 6 consumers of payment-service; the gateway's access log saw 8.

## Pitfalls

- **A portal as a project, not a product**: an empty catalog nobody updates.
- **Templates that generate and forget**: no version recorded, no update path.
- **Catalog entries without checks**: owners that don't exist, APIs that resolve to nothing.
- **Plugins before content**: the catalog and golden path are the value.
