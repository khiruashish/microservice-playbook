---
title: "mTLS & cert-manager"
description: "Mutual TLS between FoodFleet's services: one-hour SPIFFE identities from SPIRE delivered to Spring Boot SSL bundles that reload, per-route peer policies, and what was measured with real TLS 1.3 (a 3.9–4.0 ms handshake and 56–57 µs per request on a kept connection; resumed sessions kept an expired certificate working 19 times out of 19; a CA switch without overlap failed 77–78 connections). Where Istio's mesh certificates and cert-manager fit, and what each is for."
progress: done
---

# mTLS & cert-manager

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | **Mutual TLS**: both ends present certificates, so each knows which workload it's talking to. **SPIFFE/SPIRE** issues short-lived workload identities. **cert-manager** automates certificates in Kubernetes from issuers (ACME, a private CA, Vault) |
| **FoodFleet uses** | SPIRE for every workload's X.509-SVID (one hour), delivered by spiffe-helper to Spring Boot SSL bundles; Istio in ambient mode adds mesh mTLS for all namespaces |
| **Decided in** | [ADR-038](../security/ch38-service-to-service-security.md#8-enterprise-case-study), [ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 38](../security/ch38-service-to-service-security.md), [chapter 57](../deployment/ch57-service-mesh.md), [lab 16](../code-lab/lab-16-istio-canary.md) |
| **Related** | [Istio](istio.md), [Spring Security](spring-security.md), [JWT](jwt-token-relay.md) |

## Two questions on every call

1. **Which workload is calling?** Mutual TLS with a workload identity, and a peer policy per route.
2. **What may it do, for whom?** A token: the service's own (client credentials) or one exchanged for the user ([JWT & token relay](jwt-token-relay.md)).

A stolen token from a log, presented over the wrong workload's connection, fails the first; a pod in the wrong namespace with a valid certificate fails the peer policy.

## Spring Boot with SPIFFE identities

```yaml title="payment-service.yml (chapter 38)"
--8<-- "code/chapters/ch38-service-to-service-security/src/main/resources/payment-service.yml:1:24"
```

- **SSL bundles with `reload-on-update`**: SPIRE rotates at half-life; Tomcat picks up the new files. Clients built from a bundle must be rebuilt on update (`RestClientSsl` doesn't reload).
- **A SPIFFE trust manager**, not a standard PKIX one: a standard trust manager with FoodFleet's CA accepted certificates that aren't valid SVIDs. The SVID rules (one URI SAN, the right trust domain) and the peer policy run per request.
- **Validity per request**: see below.

## Measured with real TLS 1.3

| | Result |
|---|---|
| New connection, mutual TLS, EC P-256 | **3.9–4.0 ms** (250–255 connections/s per thread); TLS with a server certificate only, 3.6–4.2 ms |
| Session resumed | 1.7–1.8 ms |
| A request on a kept connection | **56–57 µs** |
| An SVID expired 3 s in; new connections every 250 ms, JDK defaults | **19 of 19 accepted** after expiry: resumed sessions carry the original certificate |
| The same, validity checked per request | 0 accepted, 20 × 403 |
| SVIDs rotated at half-life | 0 failed connections |
| SVIDs loaded once and never updated | 366–377 failed |
| CA rollover with bundles switched to the new CA | **77–78 failed** |
| CA rollover with bundles holding old + new CA | 0 failed |

- **The handshake is the cost**; the client certificate adds little. Keep connections pooled and warm (and cap their age: FoodFleet uses 30 minutes for mTLS connections).
- **Short-lived certificates only help if checked**: resumed sessions and kept connections outlive them.
- **A CA rollover needs an overlap**: trust both CAs for one SVID lifetime.

## Who issues what

| Issuer | Issues | FoodFleet |
|---|---|---|
| **SPIRE** | SPIFFE X.509-SVIDs and JWT-SVIDs per workload, attested by node and service account | Every workload's identity (ADR-038) |
| **Istio's CA (istiod)** | Mesh certificates for ztunnel/sidecars, SPIFFE IDs from service accounts | Mesh mTLS in ambient mode (ADR-057); can be backed by SPIRE |
| **cert-manager** | Kubernetes `Certificate` resources from ACME (Let's Encrypt), a private CA, Vault/OpenBao, cloud CAs; renewed automatically | Not chosen by an ADR. Its natural place is public TLS for the edge (ACME through the Gateway API) and `istio-csr` if the mesh should use an external CA |

cert-manager stores keys in Kubernetes Secrets and renews before expiry (by default when two thirds of the lifetime has passed). For workload-to-workload identity it's the less common choice: SPIFFE gives attested identities without Secrets per pod, and the mesh issues its own.

## Pitfalls

- **Istio's defaults**: PERMISSIVE mTLS and allow-all without AuthorizationPolicies. Set STRICT and an ALLOW policy per workload ([Istio](istio.md)).
- **A standard trust manager** where a SPIFFE one is needed.
- **TLS terminated at a proxy**: the application never sees the client certificate, so certificate-bound tokens fail and peer policies can't run there.
- **An app listening on all interfaces** behind a mesh: chapter 57 reached it around the sidecar; bind to localhost or enforce at L4 too.
- **No alert on certificate expiry**: FoodFleet's internal-CA outage.
