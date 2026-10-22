---
title: "CDN"
description: "Content delivery networks in front of FoodFleet's apps: static assets and shared, cacheable responses (menus with Cache-Control: public) served from the edge, personal data marked private and revalidated with ETags and 304s, versioned URLs instead of purges, and why GraphQL needs persisted GET queries to be cacheable at all. What a CDN adds beyond caching (TLS, DDoS absorption, WAF) and what it must never cache."
progress: done
---

# CDN

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A network of edge servers that cache and serve HTTP responses close to users, honouring `Cache-Control`, `ETag` and `Vary`; usually also terminating TLS, absorbing DDoS traffic and running a WAF (CloudFront, Cloudflare, Fastly, Akamai, Azure Front Door) |
| **FoodFleet uses** | A CDN for static assets and cacheable menus in front of the cloud load balancer and API gateway |
| **Decided in** | [Chapter 10](../architecture-patterns/ch10-api-gateway-bff.md) (edge layout), [chapter 21](../communication/ch21-rest-api-design.md) (caching headers) |
| **Learn it in** | [Chapter 21](../communication/ch21-rest-api-design.md), [chapter 24](../communication/ch24-graphql.md) (GraphQL and HTTP caching) |
| **Related** | [API gateway](api-gateway.md), [Multi-level caching](multi-level-caching.md), [Rate limiting](rate-limiting.md) |

## Where it sits

[Chapter 10](../architecture-patterns/ch10-api-gateway-bff.md)'s edge, outside in: **CDN** for static assets and cacheable menus; a cloud load balancer with TLS; the stateless API gateway (token validation, request ids, rate limits); then the BFFs and services.

## Headers decide what it caches

From [chapter 21](../communication/ch21-rest-api-design.md):

| Header | Use |
|---|---|
| `ETag` | Version of this representation (a version column gives strong ETags for free) |
| `Cache-Control: private, max-age=0, must-revalidate` | Personal data: browsers may cache but must revalidate every time; **CDNs must not store it** |
| `Cache-Control: public, max-age=60` | Shared data like menus: CDNs and gateways may cache |
| `Vary` | The representation depends on a header (`Accept-Language`, `API-Version`) |

With `If-None-Match`, an unchanged resource returns `304 Not Modified` without a body. If the ETag comes from a version column checked cheaply, that also saves server work; a hash-based ETag (Spring's `ShallowEtagHeaderFilter`) only saves bandwidth, since the response is still computed.

## Practices

- **Versioned asset URLs** (`app.3f9c2a.js`) with `Cache-Control: public, max-age=31536000, immutable`; deploys change the URL instead of purging.
- **Short `max-age` plus `stale-while-revalidate`** for shared API responses, so the edge serves the old copy while it refetches.
- **`s-maxage`** when the edge may keep something longer than browsers.
- **Cache keys**: strip tracking query parameters; keep `Vary` to the headers that actually change the response.
- **Purge by tag or key** for the rare urgent change (a menu taken down), not as the normal path.
- **Origin shielding**: one regional layer in front of the origin, so a cold cache doesn't send every edge to it at once.

## GraphQL and CDNs

[Chapter 24](../communication/ch24-graphql.md): GraphQL requests are `POST`s to one URL, which CDNs don't cache. Caching at the edge needs **persisted queries sent as `GET`** (a hash in the URL), and only for queries whose results are shared. FoodFleet's customer BFF serves GraphQL ([ADR-024](../communication/ch24-graphql.md#8-enterprise-case-study)) and uses persisted queries as an allow-list in production; personal results stay uncached.

## Beyond caching

| Feature | Note |
|---|---|
| TLS at the edge | Shorter handshakes for distant users; re-encrypt to the origin |
| DDoS absorption, WAF | Volumetric attacks stop at the edge; WAF rules complement, not replace, the gateway's validation and rate limits |
| Edge compute | Header rewrites, redirects, A/B routing; keep business logic out |
| Origin protection | Allow only the CDN's addresses or a signed header at the origin |

## Pitfalls

- **Caching personal data** because `Cache-Control` was missing and the CDN applied a default TTL; set it on every response.
- **`Set-Cookie` on cacheable responses**: one user's session served to others, or the response never cached.
- **Purging as the release process**: purges are eventually consistent and rate-limited.
- **A `Vary: *` or `Vary: Authorization`** that silently disables caching.
- **An origin reachable around the CDN**, bypassing its WAF and rate limits.
