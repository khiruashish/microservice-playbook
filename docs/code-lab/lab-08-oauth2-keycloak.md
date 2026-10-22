---
title: "Lab 08 — OAuth2 with Keycloak"
description: "Secure order-service as an OAuth 2 resource server for Keycloak's tokens: issuer, audience and algorithm checks from configuration; rules per URL and scope that end in denyAll; roles mapped from Keycloak's claims, only the ones FoodFleet defines; object-level authorization in one place, with another customer's order answering 404; the caller's token relayed to menu-service; and tests that build tokens without a Keycloak. About an hour."
progress: done
---

# Lab 08 — OAuth2 with Keycloak

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **You'll build** | order-service accepting only valid Keycloak tokens meant for it, letting customers see their own orders and support see all |
| **Time** | About an hour |
| **Builds on** | [Lab 03](lab-03-two-services.md) |
| **Chapters** | [35 (security fundamentals)](../security/ch35-security-fundamentals.md), [36 (OAuth 2 and JWT)](../security/ch36-oauth2-oidc-jwt.md), [37 (Spring Security 7)](../security/ch37-spring-security-7.md) |
| **Code** | `code/code-lab/lab-08-oauth2-keycloak/` |

!!! note "About the lab code"
    Written for Spring Boot 4.1.1 with Spring Security 7.1.1, checked by import against their source, not compiled here ([how](index.md#how-the-lab-code-was-checked)); the HTTP service group configurer's API was checked in Spring Framework's source. `SecurityConfig`, `TokenClaims` and the realm come from [chapter 37](../security/ch37-spring-security-7.md)'s module.

## 1. Which tokens are accepted

```yaml title="application.yml"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/resources/application.yml"
```

Three properties, three checks ([ADR-036](../security/ch36-oauth2-oidc-jwt.md)):

- **`issuer-uri`**: the service fetches Keycloak's signing keys from it and rejects tokens from any other issuer.
- **`audiences`**: a token issued for another API is refused. Before this, a token from one FoodFleet application was accepted by every API.
- **`jws-algorithms`**: an allowlist. The token's header doesn't get to choose how it's verified.

Signature, expiry and not-before are always checked.

## 2. What each request needs

```java title="SecurityConfig.java"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/security/SecurityConfig.java"
```

Two levels of authority, and a closed default:

- **Scopes** (`SCOPE_orders:read`) say what the *application* may do on the user's behalf.
- **Roles** (`ROLE_SUPPORT`) say what the *user* is. Keycloak puts them in `realm_access.roles`; `TokenClaims` maps only FoodFleet's five roles, so Keycloak's own (`offline_access`, `default-roles-…`) never become authorities by accident.
- **`anyRequest().denyAll()`**: a new endpoint is closed until someone writes its rule ([ADR-037](../security/ch37-spring-security-7.md)).

## 3. Whose order is it?

```java title="OrderAccess.java"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/OrderAccess.java"
```

```java title="OrderController.java"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/OrderController.java:12:41"
```

URL rules can't know whose order `o-2` is; only the service can. That check lives in **one component**, called by `@PreAuthorize` on every endpoint that reads an order, so no endpoint can forget it. FoodFleet's order-visibility incident was exactly a missing check like this ([ch. 35](../security/ch35-security-fundamentals.md#7-anti-patterns-pitfalls)). Another customer's order answers **404**, the same as a missing one, so order ids can't be probed.

## 4. Calling the next service as the user

```java title="TokenRelay.java"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/TokenRelay.java"
```

```java title="ClientsConfig.java"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/main/java/dev/foodfleet/lab08/ClientsConfig.java"
```

The relay is added to the `menu` group only. Relaying is the simple version; FoodFleet uses **token exchange** for calls on a user's behalf: the identity provider swaps the token for one with only the next service's audience, fewer scopes and five minutes to live. Chapter 36 measured a 4-scope, 15-minute token narrowed to 1 scope and 5 minutes, and relayed customer tokens had leaked from a partner's logs before the change.

## 5. Test every rule without a Keycloak

```java title="OrderControllerTest.java"
--8<-- "code/code-lab/lab-08-oauth2-keycloak/src/test/java/dev/foodfleet/lab08/OrderControllerTest.java:17:66"
```

`jwt()` builds an authenticated token in the test with exactly the subject and authorities you give it. Chapter 35 went further: its order policy was tested against an independently written rule table, and agreed on all 18,600 decisions.

## Run it with Keycloak

The lab realm adds three users and a `lab-tester` client that allows the password grant, so you can get tokens with `curl`. **That grant is for this lab only**: FoodFleet's realm has no password grant ([ADR-037](../security/ch37-spring-security-7.md)).

```bash
docker run -d --name keycloak -p 8180:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v $PWD/code/code-lab/lab-08-oauth2-keycloak/keycloak:/opt/keycloak/data/import:ro \
  quay.io/keycloak/keycloak:26.7.5 start-dev --import-realm
cd code/code-lab/lab-08-oauth2-keycloak && mvn spring-boot:run &

TOKEN=$(curl -s localhost:8180/realms/foodfleet/protocol/openid-connect/token \
  -d grant_type=password -d client_id=lab-tester -d username=asha -d password=lab-only | python3 -c 'import json,sys;print(json.load(sys.stdin)["access_token"])')
curl -s -H "Authorization: Bearer $TOKEN" localhost:8090/orders/o-1     # Asha's order: 200
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" localhost:8090/orders/o-2    # Ravi's: 404
```

Paste the token into a JWT decoder to see `aud`, `scope`, `realm_access.roles` and `sub`.

## Check yourself

- [ ] `mvn verify` passes all six tests.
- [ ] Asha's token reads `o-1` and gets `404` for `o-2`; `agent7`'s token reads both.
- [ ] A token whose audience you remove (delete the `orders:read` scope from `lab-tester`) is rejected with `401`.

## Break it

!!! question "Exercises"
    1. Remove `audiences` from `application.yml`, and get a token for a different client and audience. What does order-service now accept?
    2. Map every role in `realm_access.roles`, not just FoodFleet's. Which authorities does a plain Keycloak user get?
    3. Delete `@PreAuthorize` from `menuForOrder`. Which test should catch that, and does it? Write it.
    4. Log the outgoing `Authorization` header in `TokenRelay`. Why is that line the incident chapter 36 started from?

**Next:** [Lab 09](lab-09-outbox-cdc.md) makes event publishing reliable with an outbox.
