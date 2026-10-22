---
title: "Keycloak & Spring Authorization Server"
description: "FoodFleet's identity provider: Keycloak for customers, restaurants, riders and staff, with the realm versioned and applied by the pipeline (audience per scope, PKCE for public clients, no password grant, rotating refresh tokens, token exchange for services); Spring Authorization Server only for local development, with Keycloak-shaped tokens. The defaults that matter in each: Spring's server addresses access tokens to the client and reuses refresh tokens; staged key rotation turned 29 rejected requests into 0."
progress: done
---

# Keycloak & Spring Authorization Server

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What they are** | OAuth 2.1 / OpenID Connect authorization servers: they authenticate users and clients and issue tokens. **Keycloak** is a complete identity product (realms, users, federation, admin console); **Spring Authorization Server** is a framework for building one in Spring Boot |
| **FoodFleet uses** | Keycloak 26 as the identity provider; Spring Authorization Server in local development only |
| **Decided in** | [ADR-037](../security/ch37-spring-security-7.md#8-enterprise-case-study), [ADR-036](../security/ch36-oauth2-oidc-jwt.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 36](../security/ch36-oauth2-oidc-jwt.md), [chapter 37](../security/ch37-spring-security-7.md), [lab 08](../code-lab/lab-08-oauth2-keycloak.md) |
| **Related** | [Spring Security](spring-security.md), [JWT & token relay](jwt-token-relay.md) |

## The flows FoodFleet allows

| Who | Flow | Token |
|---|---|---|
| Customers, riders, restaurant staff in apps and browsers | Authorization code + **PKCE** | Access token (JWT, 15 min, one audience per API), rotating refresh token |
| Services acting as themselves | Client credentials | One token per service, cached |
| Services acting for a user | **Token exchange** (RFC 8693) | One audience, minimum scopes, 5 min, `act` claim |
| Anyone, with a password, directly | **Not allowed** (no password grant) | — |

OAuth 2.1 drops the implicit and password grants. Browser apps use a BFF, so tokens never reach JavaScript.

## The realm, as code

```json title="foodfleet-lab-realm.json (lab 08, clients)"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/keycloak/foodfleet-lab-realm.json:97:153"
```

- **Public clients** (the app) use PKCE (`S256`) and have no secret.
- **Scopes map to audiences**: requesting `orders:read` puts `order-service` in the token's `aud`, so the token is good for that API only.
- **order-service** can exchange a user's token (`standard.token.exchange.enabled`) for a narrower one to payment-service.
- **`lab-tester`** exists only in the lab, to get tokens with `curl`; FoodFleet's realm has no password grant.

The realm is versioned and applied by the pipeline, and realm changes get the same review as code.

## Keycloak in production

| Concern | What FoodFleet does |
|---|---|
| Availability | A critical dependency: clustered, monitored. Resource servers verify JWTs locally, so APIs keep working while Keycloak is down, until tokens expire |
| Key rotation | Staged: the new key is published before it signs. Chapter 36: sudden rotation rejected **29** requests, staged **0** |
| JWKS refresh | Resource servers refresh on an unknown `kid` at most every 30 s: without the limit, 1,000 tokens with made-up kids caused 1,000 key-set fetches |
| Revocation | Access tokens live 15 minutes (a revoked one works for 450 s on average); payment and admin APIs also introspect, with a 30-second cache |
| Passwords | Only in Keycloak; services never see them |
| Upgrades | Continuous: chapter 41 found Keycloak 22.0.0, clean at release, with 212 known findings three years later |

## Spring Authorization Server, and why local only

Chapter 37 checked its defaults against the 7.1.1 sources:

| Default | Effect |
|---|---|
| Access tokens are addressed to the **client**, not the API | Resource servers checking `audiences` reject them unless a customizer sets `aud` per scope (chapter 37's `ScopeAudiences`) |
| Refresh tokens are **reused** by default | Set `reuseRefreshTokens(false)` for rotation |
| Token exchange checks requested scopes against the **requesting client's** registration | Exchanged tokens can't widen beyond what the client may ask for |

FoodFleet's migration to it failed on the first row. It now runs in local development with tokens shaped like Keycloak's (same claims, audiences per scope), so services behave identically in both.

| | Keycloak | Spring Authorization Server |
|---|---|---|
| What you get | A product: users, federation (LDAP, social), admin UI, MFA, themes | A framework: endpoints and token issuance; you build users, UI, MFA |
| Customization | SPIs in Java, themes | Plain Spring beans |
| Operating it | A stateful cluster with a database | Your own Spring Boot service |
| Fits | Most organisations needing an identity provider | Teams that must embed authorization in their own product |

## Pitfalls

- **Tokens valid for every API**: no audience per scope.
- **Long access tokens to avoid refreshes**: a revoked token keeps working for most of its lifetime.
- **The password grant "just for a test client"**: it ends up in production.
- **Unbounded JWKS refresh**: an outage multiplier for the identity provider.
- **Admin console exposed publicly**.
