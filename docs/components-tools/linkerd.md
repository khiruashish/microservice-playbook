---
title: "Linkerd"
description: "Linkerd, the lightweight service mesh FoodFleet compared and didn't choose (ADR-057 chose Istio ambient): mTLS on by default, per-request balancing (EWMA), retries and timeouts with a small Rust proxy per pod, and fewer L7 features than Envoy. What a mesh buys, as chapter 57 measured it with Envoy (a keep-alive client split 333/333/334 across three pods instead of 1,000/0/0), what a proxy per pod costs, and when Linkerd would be the better fit."
progress: done
---

# Linkerd

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A CNCF-graduated service mesh: a control plane plus `linkerd2-proxy`, a small Rust sidecar injected per pod, giving mTLS by default, latency-aware per-request balancing, retries, timeouts, golden metrics and authorization policies |
| **FoodFleet uses** | **Not adopted.** Istio in ambient mode was chosen for all services ([ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study)) |
| **Learn it in** | [Chapter 57](../deployment/ch57-service-mesh.md) |
| **Related** | [Istio](istio.md), [Envoy](envoy.md), [mTLS & cert-manager](mtls-cert-manager.md) |

## What any mesh buys

[Chapter 57](../deployment/ch57-service-mesh.md) measured the mesh features with Envoy sidecars; Linkerd provides the same core:

| Problem | Without a mesh | With a proxy |
|---|---|---|
| Keep-alive clients after a scale-out from 1 to 3 pods | 1,000 requests per window to the first pod, for 50 s | **333 / 333 / 334** within seconds |
| Who may call menu-service | IP-based NetworkPolicies at best | Identity in the certificate, checked per connection |
| One bad pod in three | 1,000 of 3,000 failed | 0 with retries plus ejection |

## Linkerd against the alternatives

From chapter 57's comparison:

| Option | Gives you | Costs |
|---|---|---|
| **Linkerd** | mTLS by default, per-request balancing, retries, small Rust proxy | Fewer L7 features than Envoy |
| Istio, sidecar mode | Everything, plus traffic shifting, rich policy, Envoy extension points | A proxy per pod; the most moving parts |
| Istio, ambient mode | mTLS and L4 per node; L7 only where a waypoint is deployed | Newer; L7 needs waypoints |

## Why FoodFleet chose otherwise

- **Sidecar cost at scale.** Chapter 57 measured Envoy sidecars at 208–217m CPU each at 500 requests/s, twice the app. Linkerd's proxy is lighter, but it is still one per pod; ambient mode removes per-pod proxies for the many services that need only mTLS and L4 policy.
- **L7 only where it matters**: waypoints for the gateway's upstreams, order-service and payment-service.
- **Gateway API traffic shifting** for canaries through the same Envoy data plane ([Argo Rollouts](argo-rollouts.md)).

## When Linkerd fits

- A team that wants mTLS, golden metrics and sane balancing with minimal configuration and operations.
- Clusters where sidecar overhead per pod is acceptable and Envoy's extension points aren't needed.
- Gateway API support (`HTTPRoute`) for traffic splitting, and Argo Rollouts integration, exist there too.

Check the current licensing of stable release artifacts: since 2024, Buoyant publishes stable builds under its own terms, while edge releases remain open source.

## Pitfalls

- **Assuming mesh retries are safe for all routes**: retry only idempotent requests, with a budget.
- **Injection skipped** for a namespace: traffic flows without mTLS unless policy denies it.
- **Proxy resources unbudgeted** across hundreds of pods.
