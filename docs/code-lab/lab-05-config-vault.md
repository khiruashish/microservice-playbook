---
title: "Lab 05 — Centralized Config & Vault"
description: "Take configuration and secrets out of the jar: typed, validated settings that fail at startup when wrong; a Spring Cloud Config Server serving them per service and profile; refresh, and why immutable settings don't refresh; secrets from OpenBao through Spring Cloud Vault with a least-privilege policy (checked against OpenBao 2.4.4: the service's token reads its own secret and is refused writing it or reading another service's); and how FoodFleet does the same in Kubernetes. About 75 minutes."
progress: done
---

# Lab 05 — Centralized Config & Vault

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **You'll build** | A Config Server, a menu-service that reads settings from it and a secret from OpenBao, and a least-privilege OpenBao policy |
| **Time** | About 75 minutes |
| **Builds on** | [Lab 01](lab-01-hello-microservice.md) |
| **Chapters** | [4 (twelve-factor)](../foundations/ch04-twelve-factor.md), [39 (secrets)](../security/ch39-secrets-management.md), [54 (Kubernetes configuration)](../deployment/ch54-kubernetes-for-spring.md) |
| **Code** | `code/code-lab/lab-05-config-vault/` |
| **Needs** | The OpenBao CLI (`bao`), for the secrets part |

!!! note "About the lab code"
    The Java code is written for Spring Boot 4.1.1 and Spring Cloud 2025.1.2 and checked by import against their source, not compiled here ([how](index.md#how-the-lab-code-was-checked)); the property names were checked in Spring Cloud Vault's `VaultProperties`, `VaultKeyValueBackendProperties` and `VaultDatabaseProperties`. The OpenBao part **was run**, against OpenBao 2.4.4.

## Why this lab uses OpenBao, not Vault

FoodFleet uses OpenBao, the open-source fork of HashiCorp Vault under the Linux Foundation ([ADR-039](../security/ch39-secrets-management.md)). It keeps Vault's HTTP API, so Spring Cloud Vault talks to it unchanged: everything below works against either.

## 1. Settings: typed, validated, failing early

```java title="MenuSettings.java"
--8<-- "code/code-lab/lab-05-config-vault/menu-service/src/main/java/dev/foodfleet/lab05/menu/MenuSettings.java"
```

A discount of 500% fails at startup, before the service takes traffic, not on the first order:

```java title="MenuSettingsTest.java"
--8<-- "code/code-lab/lab-05-config-vault/menu-service/src/test/java/dev/foodfleet/lab05/menu/MenuSettingsTest.java:13:40"
```

## 2. A Config Server

```java title="ConfigServerApplication.java"
--8<-- "code/code-lab/lab-05-config-vault/config-server/src/main/java/dev/foodfleet/lab05/configserver/ConfigServerApplication.java"
```

```yaml title="config-repo/menu-service.yml and menu-service-prod.yml"
--8<-- "code/code-lab/lab-05-config-vault/config-repo/menu-service.yml"

--8<-- "code/code-lab/lab-05-config-vault/config-repo/menu-service-prod.yml"
```

The server serves `menu-service.yml`, overlaid by `menu-service-<profile>.yml` for the active profile. In a team, the folder is a Git repository: every change reviewed, every value's history kept.

## 3. The service imports both

```yaml title="menu-service/src/main/resources/application.yml"
--8<-- "code/code-lab/lab-05-config-vault/menu-service/src/main/resources/application.yml"
```

`spring.config.import` loads settings from the Config Server and secrets from OpenBao's KV engine into the same `Environment`. Both are `optional:`, so the service still starts on its defaults if they're down; for a secret the service can't work without, drop `optional:` and let it fail fast.

## 4. Secrets from OpenBao, least privilege

```bash title="openbao/setup.sh"
--8<-- "code/code-lab/lab-05-config-vault/openbao/setup.sh"
```

Run against OpenBao 2.4.4 in dev mode, the token created for menu-service:

| Operation | Result |
|---|---|
| Read `secret/menu-service` | `{'partner-api-key': 'pk_test_lab05'}` |
| Write `secret/menu-service` | `403 permission denied` |
| Read `secret/order-service` | `403 permission denied` |

One policy per service, as [ADR-039](../security/ch39-secrets-management.md) requires: a compromised menu-service can't read order-service's database password.

The key lives only in memory:

```java title="PartnerApiKey.java"
--8<-- "code/code-lab/lab-05-config-vault/menu-service/src/main/java/dev/foodfleet/lab05/menu/PartnerApiKey.java"
```

Chapter 39 found secrets readable from environment variables through `/proc`, and a heap dump holding a database password, which is why FoodFleet never exposes the Actuator's `heapdump` endpoint ([ADR-052](../observability/ch52-health-checks-actuator.md)).

## 5. Refresh, and its limits

`POST /actuator/refresh` makes the service re-read the Config Server. Try it: change `discount-percent` in `menu-service.yml`, refresh, then `GET /settings`. The value **doesn't change**: `MenuSettings` is a record, bound once through its constructor, and immutable configuration can't be rebound in place. Mutable `@ConfigurationProperties` classes and `@RefreshScope` beans can be.

FoodFleet treats that as a feature ([ADR-054](../deployment/ch54-kubernetes-for-spring.md)): values that must change live (feature flags, discounts) come from a watched mounted directory; everything else changes by a rollout, because a restarted pod is known to have every new value, and chapter 54 measured config updates reaching some pods and not others.

## In Kubernetes

| Lab | FoodFleet in Kubernetes | Why |
|---|---|---|
| Config Server | ConfigMaps mounted as files (`spring.config.import: configtree:`), with a checksum annotation so changes roll out | No extra server to keep available; changes reviewed in the environment repository ([ADR-055](../deployment/ch55-helm-kustomize.md)) |
| Token authentication | Kubernetes auth: the pod's service account token is exchanged for an OpenBao token | No token to distribute ([ADR-039](../security/ch39-secrets-management.md)) |
| A static database password | Dynamic database credentials per pod (1 h TTL, 24 h max), renewed **and rotated before the maximum** through a Spring Vault lease and HikariCP's credentials provider | In-place password rotation failed 14–16% of logins in chapter 39. Spring Cloud Vault's `spring.cloud.vault.database` mode only renews, and failed every new connection from exactly `max_ttl`; renew-then-rotate, none |
| KV secrets read at startup | API keys synced by External Secrets into read-only mounted Secrets, two valid versions during rotation | Rotation without a restart race |

## Run it

```bash
bao server -dev -dev-root-token-id=lab-root &                 # dev mode: in memory, never for real secrets
BAO_TOKEN=lab-root code/code-lab/lab-05-config-vault/openbao/setup.sh   # prints menu-service's token
cd code/code-lab/lab-05-config-vault/config-server && mvn spring-boot:run &
cd ../menu-service && BAO_TOKEN=<the printed token> mvn spring-boot:run
curl -s localhost:8080/settings
# {"discountPercent":10,"maxItemsPerOrder":50,"partnerApiKeyPresent":true}
```

## Check yourself

- [ ] `GET /settings` shows the Config Server's discount (10), and 0 with `--spring.profiles.active=prod`.
- [ ] `partnerApiKeyPresent` is `true` with the menu-service token, and the key appears in no log line.
- [ ] Starting with `discount-percent: 500` fails at startup.

## Break it

!!! question "Exercises"
    1. Stop the Config Server and restart menu-service. Which values does it use? Now remove `optional:` and restart. Which behaviour do you want for a payment service?
    2. Convert `MenuSettings` to a mutable class with setters and refresh again. What changed, and what can go wrong when a value changes in the middle of a request?
    3. Use the root token instead of menu-service's. What does that token let a compromised service do?
    4. Put the API key in an environment variable instead, start the service, and read `/proc/<pid>/environ`.

**Next:** [Lab 06](lab-06-resilience.md) protects order-service from a failing menu-service.
