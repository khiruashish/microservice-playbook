---
title: "Spring Security"
description: "Spring Security 7 as every FoodFleet service uses it: an OAuth 2 resource server configured by issuer, audience and algorithm allowlist; URL rules by scope and role ending in denyAll(); a claims converter that maps only FoodFleet's roles; method security for object-level decisions (another customer's order answers 404); certificate-bound tokens checked by default; and tests with jwt() that need no identity provider. Plus the password encoders measured: bcrypt 12 costs about 290 ms per login per core."
progress: done
---

# Spring Security

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Spring's authentication and authorization framework: a filter chain in front of every request, method security, OAuth 2 client, resource server and authorization server |
| **FoodFleet uses** | Spring Security 7.1 in every service as a **resource server** for Keycloak's tokens, with deny-by-default URL rules and method security; the built-in authorization server in local development only |
| **Decided in** | [ADR-037](../security/ch37-spring-security-7.md#8-enterprise-case-study), with [ADR-036](../security/ch36-oauth2-oidc-jwt.md#8-enterprise-case-study) (tokens) and [ADR-035](../security/ch35-security-fundamentals.md#8-enterprise-case-study) (authorization in services) |
| **Learn it in** | [Chapter 37](../security/ch37-spring-security-7.md), [chapter 35](../security/ch35-security-fundamentals.md), [lab 08](../code-lab/lab-08-oauth2-keycloak.md) |
| **Related** | [Keycloak & Spring Authorization Server](keycloak-authorization-server.md), [JWT & token relay](jwt-token-relay.md), [mTLS](mtls-cert-manager.md) |

## Which tokens are accepted

```yaml title="application.yml (lab 08)"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/resources/application.yml:1:10"
```

`issuer-uri` gives discovery, the signing keys and the `iss` check; `audiences` refuses tokens issued for another API (before this, a token from one FoodFleet application was accepted by every API); `jws-algorithms` is an allowlist, so the token's header doesn't choose how it's verified. Signature, expiry and not-before are always checked.

## What each request needs

```java title="SecurityConfig.java (lab 08)"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/security/SecurityConfig.java"
```

- **Scopes** (`SCOPE_orders:read`) say what the application may do on the user's behalf; **roles** (`ROLE_SUPPORT`) say what the user is.
- **Map only known roles.** Keycloak puts roles in `realm_access.roles`, including its own (`offline_access`, `default-roles-…`); the converter maps only FoodFleet's five.
- **`anyRequest().denyAll()` last**: a new endpoint is closed until someone writes its rule.

## Whose object is it

URL rules can't know whose order `o-2` is. One policy component per aggregate decides, called from `@PreAuthorize` on every method that reads one:

```java title="OrderAccess.java (lab 08)"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/OrderAccess.java"
```

Another customer's order answers **404**, the same as a missing one, so ids can't be probed. [Chapter 35](../security/ch35-security-fundamentals.md) checked order-service's policy against an independently written rule table: 18,600 decisions (31 callers × 120 orders × 5 actions), **0 disagreements**, 95% of them denials. FoodFleet's order-visibility incident was exactly a missing object-level check.

## Defaults worth knowing (Spring Security 7.1.1)

| Default | Effect | FoodFleet |
|---|---|---|
| `JwtValidators.createDefault()` includes the `x5t#S256` validator | Certificate-bound tokens are refused over a different client certificate | Used for payment calls; breaks if a proxy terminates TLS ([mTLS](mtls-cert-manager.md)) |
| Method security needs `@EnableMethodSecurity` | `@PreAuthorize` is ignored without it | In the platform starter |
| The client-credentials interceptor resolves the principal from the request | Inside a user request it stored a token per user: 20,000 token requests instead of 4 in chapter 38 | A service principal resolver |
| `BCryptPasswordEncoder()` strength 10 | 72 ms per check | Passwords only in Keycloak |

## Password encoders, measured

| Encoder | Per check | Logins/s per core |
|---|---:|---:|
| `BCryptPasswordEncoder()` (strength 10) | 72.2–72.4 ms | 13.8–13.9 |
| `BCryptPasswordEncoder(12)` | 289.1–290.5 ms | 3.4–3.5 |
| `Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (310,000 iterations) | 70.7–70.9 ms | 14.1–14.2 |
| PBKDF2-HMAC-SHA256, 600,000 iterations (OWASP's minimum) | 135.0–135.5 ms | 7.4 |

The work factor is a capacity number: a login spike is a CPU spike. `DelegatingPasswordEncoder` keeps old hashes working and flags them for re-hashing at the next login.

## Testing

```java title="OrderControllerTest.java (lab 08, excerpt)"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/test/java/dev/foodfleet/lab08/OrderControllerTest.java:17:49"
```

`jwt()` builds an authenticated token in the test with exactly the claims and authorities given; tests use Keycloak-shaped claims so the local and real setups stay aligned.

## Pitfalls

- **`permitAll()` as the last rule**, or no rule for new endpoints.
- **Mapping every role in the token**: identity-provider roles become authorities nobody reviewed.
- **Authorization in the gateway only**: object-level decisions need the stored object.
- **Binding request bodies to entities**: mass assignment ([chapter 35](../security/ch35-security-fundamentals.md)).
- **Logging the `Authorization` header**: chapter 36's leaked-token incident.
