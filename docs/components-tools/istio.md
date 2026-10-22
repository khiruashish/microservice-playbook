---
title: "Istio"
description: "Istio in ambient mode for every FoodFleet namespace (ADR-057): ztunnel on each node for mTLS and identity-based L4 authorization, waypoint proxies for L7 (per-request balancing, two retries with a budget, outlier detection with 50% maximum ejection) only where needed, and clients that keep timeouts but drop their own retries. What chapter 57 measured with the same Envoy features (retries alone 1,496 extra requests; outlier detection with Envoy's default 10% ejection limit ejected nothing; two sidecars with mTLS p99 21 ms against 4–6 ms), and lab 16's policies validated with istioctl 1.31.1."
progress: done
---

# Istio

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A service mesh: istiod (control plane: discovery, certificates, xDS config) plus a data plane of Envoy sidecars, or in **ambient mode** a per-node **ztunnel** (L4, mTLS) and optional **waypoint** Envoys (L7) |
| **FoodFleet uses** | Ambient mode in all namespaces; strict mTLS; AuthorizationPolicies by service account; waypoints for the gateway's upstreams, order-service and payment-service |
| **Decided in** | [ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study), [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study) (canary routing) |
| **Learn it in** | [Chapter 57](../deployment/ch57-service-mesh.md), [lab 16](../code-lab/lab-16-istio-canary.md), [chapter 38](../security/ch38-service-to-service-security.md) |
| **Related** | [Envoy](envoy.md), [Linkerd](linkerd.md), [mTLS & cert-manager](mtls-cert-manager.md), [Argo Rollouts](argo-rollouts.md), [Resilience4j](resilience4j.md) |

## Who may call a service

```yaml title="istio/mesh-security.yaml (lab 16)"
--8<-- "code/code-lab/lab-16-istio-canary/istio/mesh-security.yaml"
```

STRICT mTLS after a two-week PERMISSIVE phase, then an ALLOW policy naming service accounts. Istio's defaults are PERMISSIVE and allow-all. [Chapter 57](../deployment/ch57-service-mesh.md) checked the same rules on Envoy directly: a valid mesh certificate naming `sa/reporting` was refused, `sa/gateway` from another CA was refused, `sa/gateway` from the mesh CA got 200.

## Traffic policy

```yaml title="istio/traffic.yaml (lab 16)"
--8<-- "code/code-lab/lab-16-istio-canary/istio/traffic.yaml"
```

Chapter 57, one bad pod in three, 3,000 requests:

| Mesh features | Failed | Retries | Ejections |
|---|---:|---:|---:|
| none | 1,000 | 0 | – |
| 2 retries on another pod | 0 | **1,496–1,497** | – |
| Outlier detection (5 × 5xx) | 26 | 0 | 2 |
| …with Envoy's default `max_ejection_percent` (10%) | **1,000** | 0 | **0** |
| Both | 0 | 35 | 2 |

Retries alone feed a broken pod; ejection removes it; the default 10% limit is below one pod in three, so nothing was ever ejected. FoodFleet sets 50%. Clients keep timeouts and circuit breakers but **drop their own retries**, so the two layers don't multiply.

## Ambient or sidecars

| | Sidecar per pod | Ambient: ztunnel + waypoints |
|---|---|---|
| mTLS, L4 policy | In each sidecar | ztunnel per node, for every pod |
| L7 (retries, balancing, routing) | Everywhere | Only where a waypoint is deployed |
| Cost measured with Envoy sidecars | ~0.5 ms p50 per hop; two hops with mTLS p99 21 ms vs 4–6 ms; **208–217m CPU per Envoy** at 500 req/s, twice the app | ztunnel shared per node; waypoints are a shared hop, sized and scaled like a service |
| Pod restarts for injection | Yes | No |

FoodFleet chose ambient for all services, with waypoints only where L7 matters, and rehearses ambient upgrades in staging.

## Canaries

Argo Rollouts shifts Gateway API `HTTPRoute` weights, which waypoints apply inside the mesh: 10/25/50/100% with five minutes of analysis per step ([Argo Rollouts](argo-rollouts.md)). [Lab 16](../code-lab/lab-16-istio-canary.md) validates every resource with `istioctl` and a server dry run; the schemas refuse `mode: STRICTER` and `setWeight: ten`.

## Pitfalls

- **PERMISSIVE forever** and no AuthorizationPolicies.
- **Retries in the mesh and in the client**.
- **Default outlier-ejection limits**.
- **Apps on 0.0.0.0 with explicit sidecars**: chapter 57 reached the app around the proxy in plaintext; use redirection (iptables or ambient) and NetworkPolicies.
- **Waypoints unsized**: a shared proxy becomes the bottleneck.
- **Treating the mesh as the only identity check**: services still verify tokens for *who the user is* ([JWT](jwt-token-relay.md)).
