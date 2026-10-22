---
title: "Part 5 — Security"
description: "FoodFleet's security architecture layer by layer, and a decision guide for securing microservices: threat models, OAuth 2.1 and JWTs, Spring Security 7, workload identity and mutual TLS, secrets, API hardening, and supply chain and compliance, each measured against real servers, browsers and tools."
progress: done
---

# Part 5 — Security

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

A monolith had one front door and one database login. FoodFleet has sixty services, each with its own API, its own credentials and its own dependencies, talking to each other over a network that has to be treated as hostile. Part 5 secures that system from the outside in: who the users and services are, what each may do, where the secrets live, what the public edge refuses, and whether the code that runs is the code that was built.

Every chapter checks its claims against real systems (Keycloak, Spring Authorization Server, SPIRE certificates, OpenBao, PostgreSQL 16, Redis, Chromium, the OWASP Core Rule Set in Coraza, Sigstore, the GitHub Advisory Database). The verified output is in the text, and the code to reproduce it is in the repository.

## FoodFleet's security architecture

Each layer answers one question, and each relies on the layers under it. **Owner** is the team that runs the control; the last column is where the handbook builds it.

| Layer | Question | FoodFleet's control | Owner | Chapters |
|---|---|---|---|---|
| Threat model | What can go wrong, and what stops it? | STRIDE models as code next to each system; the build fails on an unmitigated new flow | Each service team | [35](ch35-security-fundamentals.md) |
| User identity | Who is the user? | Keycloak; OAuth 2.1 code flow with PKCE; short-lived, audience-checked JWTs; staged key rotation | Identity platform | [36](ch36-oauth2-oidc-jwt.md), [37](ch37-spring-security-7.md) |
| Authorization | What may this user do to this object? | Deny-by-default `SecurityFilterChain`s, method security with object checks, 404 for others' objects | Each service team | [35](ch35-security-fundamentals.md), [37](ch37-spring-security-7.md) |
| Workload identity | Which service is calling? | SPIFFE IDs from SPIRE, mutual TLS, a per-route peer policy; token exchange for calls on a user's behalf | Platform | [38](ch38-service-to-service-security.md), [36](ch36-oauth2-oidc-jwt.md) |
| Secrets | Where do credentials live, and for how long? | OpenBao with Kubernetes auth; dynamic database credentials that rotate; revocation that ends sessions | Platform | [39](ch39-secrets-management.md) |
| Public edge | What does the API refuse? | Rate limits per client and tenant (GCRA in Redis), typed DTOs, exact-origin CORS, Fetch Metadata and CSRF tokens on the BFF, a tuned WAF | Edge team + service teams | [40](ch40-api-security.md) |
| Tenant isolation | Can one tenant see another's rows? | PostgreSQL row-level security, forced, with a transaction-local tenant | Service teams | [40](ch40-api-security.md) |
| Supply chain | Is what runs what we built, from what? | SBOMs, daily rescans, keyless signing and SLSA provenance enforced at admission, SHA-pinned CI | Platform + security | [41](ch41-supply-chain-compliance.md) |
| Compliance | Can we prove it, and do we handle data lawfully? | Data class, region and retention declared and enforced (RBI, PCI DSS, DPDP); crypto-shredding for erasure | Security + data owners | [41](ch41-supply-chain-compliance.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| Not knowing what could go wrong | Threat models as code, OWASP API Top 10 controls | [35](ch35-security-fundamentals.md) | 69 threats in the checkout model, 67 mitigated, 2 accepted with owners; an authorization policy agreed with an independent rule table on all 18,600 decisions |
| Tokens that can't be revoked | Short-lived JWTs, or introspection where revocation must be fast | [36](ch36-oauth2-oidc-jwt.md) | A revoked 15-minute JWT kept working 450 s on average; introspection with a 30 s cache 15 s |
| Rotating signing keys without errors | Publish the new key before signing with it | [36](ch36-oauth2-oidc-jwt.md) | A sudden rotation rejected 29 requests; publishing 6 minutes ahead rejected 0 |
| Securing a Spring service | One deny-by-default chain, method security, the right password encoder | [37](ch37-spring-security-7.md) | bcrypt 10 took 72 ms per check, bcrypt 12 about 290 ms; Spring's PBKDF2 default is half OWASP's minimum iterations |
| Services trusting each other's network | SPIFFE identities, mutual TLS, per-route peer policies | [38](ch38-service-to-service-security.md) | An expired certificate kept getting connections through TLS resumption (19 of 19) until a per-request check; rotation at half-life failed 0 of 482 |
| Secrets in env vars, passwords nobody can change | OpenBao, dynamic credentials, two-version rotation | [39](ch39-secrets-management.md) | In-place rotation failed 14–16% of logins, two accounts none; renew-only credentials failed at max_ttl, renew-then-rotate never |
| A rotated password that doesn't lock anyone out | Revocation that terminates sessions | [39](ch39-secrets-management.md) | After a password change, expiry or `NOLOGIN`, the open session kept working |
| One client or tenant overwhelming the API | GCRA limits per client and tenant, shared in Redis | [40](ch40-api-security.md) | Local limits let 1,036 through a 260 budget, Redis 258–260; a global limit left other tenants 0.2% of their requests, per-tenant limits 100% |
| CORS and CSRF confusion | Exact-origin CORS for bearer APIs; Fetch Metadata and tokens for cookie sessions | [40](ch40-api-security.md) | In Chromium, a sibling subdomain got even `SameSite=Strict` cookies; a reflecting server leaked the user's orders |
| A WAF that blocks customers | The CRS tuned for a JSON API, with field-level exclusions | [40](ch40-api-security.md) | As shipped: 6 of 41 legitimate requests blocked at PL1, 20 at PL2; tuned: 0 and 4; business-logic abuse caught: 0 of 6 |
| A query that forgets the tenant | Row-level security, forced, transaction-local tenant | [40](ch40-api-security.md) | 2,000 rows became 40; a session-level `SET` leaked 4,000 rows to the next request |
| "Are we affected?" taking days | SBOMs per artifact, daily rescans of what is deployed | [41](ch41-supply-chain-compliance.md) | A Keycloak release went from 0 known findings to 196 without changing |
| Untrusted images and pipelines | Keyless signing, provenance at admission, SHA-pinned actions | [41](ch41-supply-chain-compliance.md) | With a leaked signing key, only the provenance policy refused a laptop rebuild |
| Erasure requests, data residency | Declared data classes, OPA policies, crypto-shredding | [41](ch41-supply-chain-compliance.md) | 9 violations in 2 of 4 deployments; 0 of 9,000 erased fields readable in backups and topics |

## The chapters

<div class="grid cards" markdown>

-   **[35. Security Fundamentals](ch35-security-fundamentals.md)**

    ---

    STRIDE threat models as code, the OWASP API Security Top 10, authorization, password storage. *Models checked in the build.*

-   **[36. OAuth 2.1, OIDC & JWT](ch36-oauth2-oidc-jwt.md)**

    ---

    Flows, token verification, revocation, key rotation, token exchange. *A local authorization server and verifier.*

-   **[37. Spring Security 7](ch37-spring-security-7.md)**

    ---

    Resource servers, method security, the built-in authorization server, Keycloak. *Defaults read from the source.*

-   **[38. Service-to-Service Security](ch38-service-to-service-security.md)**

    ---

    SPIFFE/SPIRE, mutual TLS, peer policies, certificate rotation, bound tokens. *Real TLS handshakes on JDK 21.*

-   **[39. Secrets Management](ch39-secrets-management.md)**

    ---

    OpenBao/Vault, dynamic database credentials, Kubernetes Secrets, rotation, revocation. *OpenBao and PostgreSQL 16.*

-   **[40. API Security Hardening](ch40-api-security.md)**

    ---

    Rate limiting, validation, ReDoS, CORS, CSRF, WAF, tenant isolation. *Redis, Chromium, Coraza, PostgreSQL.*

-   **[41. Supply-Chain Security & Compliance](ch41-supply-chain-compliance.md)**

    ---

    SBOMs, scanning over time, signing and provenance, admission, RBI/PCI/DPDP as policy, crypto-shredding. *Syft, cosign, OPA.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Building services already** (🔵 L2) | 35 → 36 → 37 → 40 | [Part 6: Resilience & Fault Tolerance](../resilience/index.md) |
| **Leading a team** (🟣 L3) | 35 → 37 → 38 → 39 → 40 | [Part 7: Observability](../observability/index.md) |
| **Designing a platform** (🔴 L4) | 35 → 38 → 39 → 41 | [Part 8: Deployment & Platform](../deployment/index.md) |
| **Preparing for interviews** | 36, 38, 40, 41 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-035 · Chapter 35</span><span class="what">Threat models as code per system; authorization in services; OWASP API Top 10 controls as tested defaults.</span></li>
<li><span class="when">ADR-036 · Chapter 36</span><span class="what">OAuth 2.1 flows only; short-lived, audience-checked JWTs; staged key rotation; token exchange on a user's behalf.</span></li>
<li><span class="when">ADR-037 · Chapter 37</span><span class="what">Keycloak as identity provider; every service a deny-by-default Spring Security resource server.</span></li>
<li><span class="when">ADR-038 · Chapter 38</span><span class="what">SPIFFE identities from SPIRE; mutual TLS with per-route peer policies in every service.</span></li>
<li><span class="when">ADR-039 · Chapter 39</span><span class="what">OpenBao for every secret; dynamic, rotating database credentials; revocation ends sessions.</span></li>
<li><span class="when">ADR-040 · Chapter 40</span><span class="what">Limits per client and tenant in Redis; exact-origin CORS and CSRF only where cookies are used; a tuned WAF; row-level security.</span></li>
<li><span class="when">ADR-041 · Chapter 41</span><span class="what">SBOMs with daily rescans; keyless signing and provenance enforced at admission; compliance as policy; crypto-shredding.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch35-security-fundamentals,chapters/ch36-oauth2-oidc-jwt,chapters/ch37-spring-security-7,chapters/ch38-service-to-service-security,chapters/ch39-secrets-management,chapters/ch40-api-security,chapters/ch41-supply-chain-compliance -am verify
    ```
    JDK 25 and Maven 3.9+. Each chapter's `verify/` folder holds its experiments and says what they need: a JDK for most, plus PostgreSQL 16 (39, 40), OpenBao (39), Redis, Chromium via Playwright and Go (40), and the cosign, Syft, crane, registry and OPA release binaries it downloads (41).
