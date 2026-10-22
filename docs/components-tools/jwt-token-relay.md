---
title: "JWT & Token Relay"
description: "JWT access tokens as FoodFleet's services verify them (audience, issuer, type, algorithm allowlist, time, scopes) and how a user's identity travels between services: relaying the token as is, or exchanging it for a narrower one. Measured: local verification at about 0.6 ms (RS256 about 45 µs) against 1.4 ms for introspection; a revoked 15-minute token works 450 s on average; token exchange narrowed a 4-scope, 15-minute token to 1 scope and 5 minutes for 3.2–3.6 ms on the first call and 26–28 µs cached."
progress: done
---

# JWT & Token Relay

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | JWTs: signed, self-contained tokens a service can verify without calling the issuer. Relay: forwarding the caller's token downstream. Exchange (RFC 8693): trading it for a new token meant for the next service only |
| **FoodFleet uses** | `at+jwt` access tokens, 15 minutes, one audience per API, RS256 or ES256; token exchange for calls on a user's behalf; introspection in addition for payment and admin APIs |
| **Decided in** | [ADR-036](../security/ch36-oauth2-oidc-jwt.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 36](../security/ch36-oauth2-oidc-jwt.md), [lab 08](../code-lab/lab-08-oauth2-keycloak.md) |
| **Related** | [Spring Security](spring-security.md), [Keycloak](keycloak-authorization-server.md), [mTLS](mtls-cert-manager.md) (certificate-bound tokens) |

## What a resource server checks

| Check | Why |
|---|---|
| Signature, with an **algorithm allowlist** (RS256, ES256) | The token's header must not choose how it's verified (`alg: none`, HMAC with the public key) |
| `iss` | Only FoodFleet's identity provider |
| `aud` | This API only; before ADR-036 a token from one application was accepted by every API |
| `typ: at+jwt` | An ID token is not an access token |
| `exp`, `nbf`, `iat` | With a small clock skew |
| `scope`, roles | Per URL and method ([Spring Security](spring-security.md)) |

## Measured: verifying and revoking

[Chapter 36](../security/ch36-oauth2-oidc-jwt.md), built with the JDK:

| | Result |
|---|---|
| Verify locally, ES256, cached keys | p50 552–652 µs; 2 calls to the identity provider in total |
| Verify locally, RS256 | about 45 µs (RSA verifies ~15× faster than ECDSA in the JDK; ES256 tokens are 40% smaller) |
| Introspect an opaque token per request | p50 1,403–1,426 µs, one call to the identity provider per request |
| A revoked 15-minute JWT keeps working | **450 s** on average, 900 s at worst |
| Introspection cached 30 s | 15 s on average, 30 s at worst |

**A JWT's lifetime is its revocation delay.** Keep access tokens short, rotate refresh tokens, and introspect where revocation must be immediate (payments, admin).

## Relaying the user's token

```java title="TokenRelay.java (lab 08)"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/TokenRelay.java:14:30"
```

Relaying is simple and lets the next service make its own decision about the same user. Its cost is the token's reach: the downstream service now holds a token that works everywhere the original does, for as long as it does. FoodFleet's incident was a partner's logs containing relayed customer tokens that couldn't be revoked.

## Exchanging it instead

```java title="TokenExchangePolicy.java (chapter 36, the rules)"
--8<-- "code/chapters/ch36-oauth2-oidc-jwt/src/main/java/dev/foodfleet/ch36/exchange/TokenExchangePolicy.java:12:22"
```

| Token | Audience | Scopes | `act` | Lifetime |
|---|---|---|---|---:|
| The user's, as issued to the app | order-service | orders:read orders:write payments:capture profile:read | — | 900 s |
| Exchanged by order-service | payment-service | payments:capture | `{"sub":"order-service"}` | 300 s |

The exchange is a call to the identity provider: 3.2–3.6 ms at the median for the first call per user, 26–28 µs from a cache keyed by the user token's hash. The `act` claim records which service acted, so payment-service's audit log says "order-service, for Asha".

## Service tokens

For calls a service makes as itself, client credentials: **one token per service, cached and renewed before expiry with a single request in flight**. Chapter 38 measured fetching a token per call (44,000 token requests in 9 s), Spring's interceptor keyed by user inside user requests (20,000), and a single-flight per-service cache (**4**).

## Pitfalls

- **No audience check**: any token from the identity provider works on every API.
- **Long-lived access tokens**: revocation that takes an hour.
- **Logging `Authorization` headers**, or tokens in URLs.
- **Relaying tokens to third parties**: exchange, or use the service's own credentials.
- **Unbounded JWKS refresh on unknown `kid`s**: attackers can make every pod fetch keys on every request ([Keycloak](keycloak-authorization-server.md)).
