---
title: "Gateway API & Ingress"
description: "How external traffic enters FoodFleet's clusters: Kubernetes Gateway API HTTPRoutes (ADR-054) instead of Ingress, with the platform team owning Gateways and service teams owning routes; weighted backends that Argo Rollouts shifts for canaries at 10/25/50/100 (ADR-058); and where the cluster edge ends and the API gateway and BFFs begin (chapter 10). Ingress against Gateway API, implementations, and what to keep out of the edge."
progress: done
---

# Gateway API & Ingress

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Kubernetes APIs for routing traffic into a cluster. **Ingress**: host and path rules, everything else in controller-specific annotations. **Gateway API**: role-oriented resources (`GatewayClass`, `Gateway`, `HTTPRoute`, `GRPCRoute`) with weights, header matching and cross-namespace references, implemented by Envoy Gateway, Istio, NGINX Gateway Fabric, Contour, cloud controllers |
| **FoodFleet uses** | Gateway API `HTTPRoute`s for external traffic; weighted routes for canaries |
| **Decided in** | [ADR-054](../deployment/ch54-kubernetes-for-spring.md#8-enterprise-case-study), [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 54](../deployment/ch54-kubernetes-for-spring.md), [chapter 58](../deployment/ch58-cicd-gitops.md), [chapter 10](../architecture-patterns/ch10-api-gateway-bff.md), [lab 16](../code-lab/lab-16-istio-canary.md) |
| **Related** | [API gateway](api-gateway.md), [Argo Rollouts](argo-rollouts.md), [Istio](istio.md), [Envoy](envoy.md) |

## Ingress or Gateway API

From [chapter 54](../deployment/ch54-kubernetes-for-spring.md): the Gateway API for new work.

| | Ingress | Gateway API |
|---|---|---|
| Roles | One resource for everything | Platform owns `Gateway` (listeners, TLS); teams own `HTTPRoute`s, attached by namespace policy |
| Traffic splitting | Annotations, per controller | `backendRefs` with `weight`, portable |
| Matching | Host, path | Host, path, headers, query, method |
| Protocols | HTTP(S) | HTTP, gRPC (`GRPCRoute`), TLS, TCP/UDP (experimental) |
| Status | Stable, frozen | GA since v1.0 (2023), evolving |

## Canary weights

Argo Rollouts changes two weights on an `HTTPRoute`, and nothing else:

```yaml title="delivery/httproute-canary.yaml (chapter 58)"
--8<-- "code/chapters/ch58-cicd-gitops/src/main/resources/delivery/httproute-canary.yaml"
```

ADR-058's steps: 10, 25, 50, 100% with five minutes of analysis each, aborting when p99 is more than 20% above stable or early on 10 errors ([Argo Rollouts](argo-rollouts.md)). Inside the mesh, Istio's waypoints apply the same `HTTPRoute` weights ([lab 16](../code-lab/lab-16-istio-canary.md)).

## Where the edge ends

[Chapter 10](../architecture-patterns/ch10-api-gateway-bff.md)'s layout, outside in:

| Layer | Does |
|---|---|
| CDN | Static assets, cacheable menus |
| Cloud load balancer + Gateway | TLS, host and path routing, coarse limits |
| API gateway | Token validation, header hygiene, request ids, per-user rate limits, size limits |
| BFFs | Composition per client |

"At most ingress + one API gateway": more layers add latency and config drift. The ingress also strips incoming `traceparent`, `tracestate` and `baggage` from internet traffic ([ADR-049](../observability/ch49-distributed-tracing-otel.md#8-enterprise-case-study)), and services have no public DNS names or ingress of their own.

## Pitfalls

- **Annotation sprawl on Ingress**: behaviour that changes when the controller does.
- **Routes from any namespace allowed** on a shared Gateway: use `allowedRoutes`.
- **Business logic at the edge**: authorization by object belongs in the service.
- **Trusting client tracing and forwarding headers** (`X-Forwarded-For`, `traceparent`) at the edge.
- **Two controllers claiming the same class**.
