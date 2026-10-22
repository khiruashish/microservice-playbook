---
title: "Envoy"
description: "Envoy, the proxy inside Istio's sidecars and waypoints, Envoy Gateway and most API gateways, configured by hand in chapter 57 so each feature could be measured: STRICT_DNS on a headless Service for per-request balancing, mTLS with SPIFFE identities and a per-service allow-list, two retries plus outlier detection (0 failures against one bad pod in three, 35 retries), and the default max_ejection_percent of 10% that ejected nothing. Costs: about half a millisecond per hop at p50, and 208–217m CPU per proxy at 500 requests/s."
progress: done
---

# Envoy

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A high-performance L4/L7 proxy configured by listeners, routes and clusters, statically or dynamically over xDS; per-request balancing, retries, outlier detection, circuit breaking, TLS, rich stats and tracing |
| **FoodFleet uses** | Through Istio: waypoint proxies in ambient mode, and Gateway API implementations at the edge |
| **Decided in** | [ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 57](../deployment/ch57-service-mesh.md), [chapter 10](../architecture-patterns/ch10-api-gateway-bff.md) |
| **Related** | [Istio](istio.md), [Gateway API](gateway-api-ingress.md), [API gateway](api-gateway.md) |

## Configured by hand

[Chapter 57](../deployment/ch57-service-mesh.md) ran Envoy 1.39 sidecars with no control plane: a small Java class rendered the bootstrap configuration a control plane would push. The caller's outbound sidecar:

```yaml title="envoy/caller-outbound.yaml (chapter 57)"
--8<-- "code/chapters/ch57-service-mesh/src/main/resources/envoy/caller-outbound.yaml"
```

- **`STRICT_DNS` on a headless Service**: the cluster resolves to every pod and re-resolves every 2 s, so Envoy balances each request itself over pooled connections, bypassing kube-proxy.
- **Retry policy**: two retries on 5xx, on another host.
- **Outlier detection**: 5 consecutive 5xx eject a host for 30 s, doubling on repeat.
- **TLS** with the workload's SPIFFE certificate; the server side admits a list of SPIFFE IDs.

## Measured

| Experiment | Result |
|---|---|
| Keep-alive client, scale-out 1 → 3 pods | Through kube-proxy 1,000 / 0 / 0 per window; through Envoy **333 / 333 / 334** |
| One bad pod in three, retries only | 0 failed, **1,496–1,497 retries** |
| …outlier detection only | 26 failed |
| …with the default `max_ejection_percent` (10%) | **1,000 failed, 0 ejections** |
| …both | 0 failed, 35 retries |
| mTLS handshake on a new connection | 6.9–7.9 ms (50 ms until `TCP_NODELAY` removed a delayed-ACK wait) |
| App listening on 0.0.0.0 | Reached around the sidecar in plaintext: **200** |

| Path (500 req/s, keep-alive) | p50 | p99 |
|---|---:|---:|
| No proxy | 0.50–0.58 ms | 3.8–5.6 ms |
| 1 proxy | 0.98–1.03 ms | 4.9–9.4 ms |
| 2 proxies, mTLS | 1.52–1.53 ms | 21.0–21.1 ms |

Each Envoy used 208–217m of CPU against the app's 97–100m, and 32–44 MiB.

## Where Envoy runs

| Role | Product |
|---|---|
| Mesh sidecar / waypoint | Istio (sidecar or ambient waypoint), Consul, Kuma |
| Kubernetes edge (Gateway API) | Envoy Gateway, Istio ingress, Contour |
| API gateway | Gloo, Ambassador/Emissary, cloud gateways built on Envoy |

Extensions are written in Lua, Wasm, or as external authorization/processing services.

## Pitfalls

- **Default `max_ejection_percent`** (10%) with small replica counts.
- **Retries without a budget** (`retry_budget`) or on non-idempotent routes.
- **Missing `TCP_NODELAY`** in custom clients and tests: a 40 ms delayed-ACK stall.
- **Bypassable sidecars** (app on all interfaces, no redirection).
- **Per-pod proxy CPU unbudgeted**.
- **Hand-written config in production**: use a control plane; hand-writing is for learning what it does.
