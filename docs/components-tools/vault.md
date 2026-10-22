---
title: "HashiCorp Vault"
description: "Secrets management with Vault's API, run as OpenBao (its open-source fork): Kubernetes auth per service, one least-privilege policy each, dynamic database credentials per pod that renew and rotate before max_ttl, static API keys synced by External Secrets into read-only volumes, and revocation that ends sessions. Measured against OpenBao and PostgreSQL 16: environment variables readable through /proc, in-place rotation failing 14–16% of logins, renew-only credentials failing at max_ttl, and a changed password leaving open sessions working."
progress: done
---

# HashiCorp Vault

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A secrets store with an HTTP API: authentication methods (Kubernetes, OIDC, AppRole), policies, secrets engines (KV, database, PKI, transit), leases and audit |
| **FoodFleet uses** | **OpenBao**, the Linux Foundation's open-source fork of Vault (same API, so Vault clients and Spring Cloud Vault work unchanged): highly available, auto-unsealed with KMS, audited |
| **Decided in** | [ADR-039](../security/ch39-secrets-management.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 39](../security/ch39-secrets-management.md), [lab 05](../code-lab/lab-05-config-vault.md) |
| **Related** | [Configuration management](configuration-management.md), [mTLS](mtls-cert-manager.md), [PostgreSQL](postgresql.md) |

## Vault or OpenBao

HashiCorp moved Vault to the Business Source License in 2023; OpenBao forked the last open-source version under the Linux Foundation and keeps its HTTP API. FoodFleet runs OpenBao; everything on this page works against either.

## Identity gets you the secrets

There is no "secret zero": a pod proves who it is with its Kubernetes service account token, and OpenBao maps that to a role and a policy, one per service.

```bash title="openbao/setup.sh (lab 05)"
--8<-- "code/code-lab/lab-05-config-vault/openbao/setup.sh"
```

Run against OpenBao 2.4.4 in lab 05, menu-service's token read its own secret and got `403` writing it or reading order-service's.

## Where a secret ends up

[Chapter 39](../security/ch39-secrets-management.md):

| Where the secret was | Found? |
|---|---|
| Environment variable → a child process's environment | **inherited** |
| Environment variable → `/proc/<pid>/environ` (same user) | **readable** |
| Mounted file → a child process's environment | absent |
| A `String` used once, then unreachable → heap dump of all objects | **yes** |

Mount secrets as files, never environment variables; treat heap dumps as secrets (FoodFleet never exposes the Actuator's `heapdump`).

## Rotation, measured

| Strategy | Failed | Why |
|---|---:|---|
| One shared password, changed in place | **568–621** logins (14–16%) | Pods that haven't received the new password can't connect until propagation finishes |
| **Two accounts**, the idle one changed, pods switch | 0 | Two propagation windows, no gap |
| Dynamic credentials, **renew only** (Spring Cloud Vault's database mode) | **2,706–2,790**, from exactly `max_ttl` | A lease can't be renewed past its maximum |
| Dynamic credentials, **renew, then rotate before `max_ttl`** | **0** | New credentials before the old expire |

Issuing a dynamic credential took 14.7–17.2 ms (OpenBao creates a PostgreSQL role per request); FoodFleet's are per pod, `default_ttl` 1 h, `max_ttl` 24 h, fed to HikariCP through a credentials provider.

## Revocation must end sessions

| Action | New login | The open session's next query |
|---|---|---|
| Lease revoked, default revocation SQL | refused | **still connected** (permission errors on tables) |
| Lease revoked, revocation SQL with `pg_terminate_backend` | refused | **terminated** |
| Static password changed | refused | **works** |
| `NOLOGIN` | refused | **works** |

PostgreSQL checks credentials only at login. FoodFleet's revocation SQL terminates sessions; revoking all 50 leases of a role took 1.19–1.37 s.

## Static secrets

Third-party API keys live in KV v2, synced by the External Secrets Operator (`refreshInterval` 5 m) into Kubernetes Secrets mounted read-only and watched, rotated with two valid versions. Kubernetes Secrets are encrypted at rest with KMS v2. A mounted secret changed five times reached a directory-watching reader 2–6 ms after each swap; `configtree:` and `subPath` readers never saw a change.

## Pitfalls

- **One policy for every service**: a compromised menu-service reads the payment keys.
- **The root token in applications**.
- **Secrets in environment variables**, in logs, in heap dumps.
- **Renew-only dynamic credentials**: fine for 23 hours, then every new connection fails.
- **Revocation that leaves sessions open**.
- **OpenBao as a single point of failure**: it is on the start-up and renewal path, so it runs highly available with alerting.
