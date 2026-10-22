# Chapter 39 — Secrets Management

| Package / file | What it is |
|---|---|
| `secret/` | `Secret` (masked in `toString`, wipeable, constant-time comparison, no `equals` by value), `MountedSecrets` (a Kubernetes Secret volume followed through the kubelet's atomic `..data` swaps, every key read from one version) |
| `vault/` | `VaultClient` (OpenBao / HashiCorp Vault HTTP API: Kubernetes login, KV v2 with check-and-set, database credentials, lease renew and revoke), `DatabaseCredentials` (renew until max_ttl caps the lease, then switch to new credentials and revoke the old ones) |
| `json/` | The strict JSON parser from chapter 36 |
| `web/` | order-service: `order-service.yml` (configuration from OpenBao/Vault via Spring Cloud Vault with Kubernetes auth), `DataSourceConfig` + `VaultDatabaseCredentials` (a rotating Spring Vault lease feeding HikariCP 7's `HikariCredentialsProvider`) |
| `resources/openbao/` | order-service's policy, the setup script (Kubernetes auth, KV v2, database engine with root rotation) and the creation/revocation SQL |
| `resources/eso/`, `resources/k8s/` | External Secrets Operator `ClusterSecretStore` and `ExternalSecret`; Kubernetes `EncryptionConfiguration` (KMS v2) and a Deployment mounting a Secret as a read-only volume |
| `verify/` | `run.sh`: a live policy check and five experiments (`tools/Measure.java`, `tools/Pg.java` — a tiny PostgreSQL client with SCRAM-SHA-256) against a real OpenBao dev server and PostgreSQL 16, then `check_apis.sh` against the sources of every project used |

## Run

```bash
cd code
mvn -B -pl chapters/ch39-secrets-management -am verify          # unit tests (JDK only, no OpenBao needed)

# the experiments: a JDK, the OpenBao binary and PostgreSQL 16 (superuser via psql; SCRAM logins over TCP)
PGHOST=/var/run/postgresql PGPORT=5432 PGUSER=postgres BAO_BIN=/path/to/bao \
  chapters/ch39-secrets-management/verify/run.sh
chapters/ch39-secrets-management/verify/run.sh leaks delivery   # the two that need only a JDK
```

## What was verified, and how

This environment has no Maven repository access, so the Spring application was not started here. Instead:

- The JDK code (`secret/`, `vault/`, `json/`) has 15 unit tests, all passing here.
- `verify/run.sh` ran against **OpenBao 2.7.1** (the release binary, SHA-256 checked against the published
  checksums) in dev mode and **PostgreSQL 16.13** with SCRAM-SHA-256 authentication: the policy, rotation, dynamic
  credential and revocation results below are from those real servers. HashiCorp Vault exposes the same HTTP API for
  everything used here, but was not run.
- The Spring classes in `web/` compile against stand-ins whose signatures were checked against the sources;
  `verify/check_apis.sh` checks 41 APIs, properties, defaults and facts against Spring Boot 4.1.1, Spring Cloud Vault
  5.0.2, Spring Vault 4.0.3, HikariCP 7.0.2, OpenBao 2.7.1, External Secrets Operator 2.12.0 and Kubernetes 1.37.1.
- The OpenBao policy was loaded into OpenBao and exercised (the `policy` section); the YAML files parse; the
  Kubernetes, ESO and Spring Cloud Vault field names are among the checked declarations. Kubernetes and ESO were not
  run.

## Verified output

JDK 21.0.10, OpenBao 2.7.1 and PostgreSQL 16.13 on one 2-vCPU container. Two runs with the same results except for
timings (the chapter quotes ranges). This is the second.

```text
OpenBao v2.7.1, 16.13 (Ubuntu 16.13-0ubuntu0.24.04.1)

== policy
a token with only the order-service policy (src/main/resources/openbao/order-service-policy.hcl):
  kv get secret/order-service/gateway        allowed
  kv get secret/payment-service/gateway      denied
  read database/creds/order-service          allowed
  read database/creds/payment-service        denied
  secrets list                               denied

== leaks
one marker secret per row; searched for in each place it might end up
where the secret was                                       found?
environment variable -> a child process's environment      inherited
environment variable -> /proc/<pid>/environ (same user)    readable
mounted file -> a child process's environment              absent
Secret wrapper -> toString / string concatenation          "Secret[****]"
heap dump contains the value?                              dump of all objects    dump of live objects
String field (e.g. a bound @ConfigurationProperties value) yes (1x)               yes (1x)
String used once, then unreachable                         yes (1x)               no
Secret (char[]) still in use                               yes (1x)               yes (1x)
Secret (char[]) after destroy()                            no                     no
heap dump sizes: 6 MB (all), 4 MB (live)

== delivery
a mounted secret updated 5 times, 1.5 s apart, the way the kubelet swaps ..data
version    read once at start-up          pinned file (like a subPath mount) MountedSecrets (watch + 1 s poll)
v1         v0                             v0                                 v1 after 3 ms
v2         v0                             v0                                 v2 after 3 ms
v3         v0                             v0                                 v3 after 2 ms
v4         v0                             v0                                 v4 after 2 ms
v5         v0                             v0                                 v5 after 3 ms

== static
6 pods, a pool of 3 per pod, connections replaced every 2 s (max lifetime), a query every 20 ms per pod; the new secret reaches each pod at a random moment within 6 s of the change (ESO refresh + kubelet sync, scaled down); 16 s
rotation                                                       ok   failed    first failure   login errors
one account, password changed in place at 4 s                3396      568            4.2 s            568
two accounts: idle one changed at 4 s, pods switch to it     4497        0                -              0

== dynamic
issue credentials (CREATE ROLE + grants): p50 17.2 ms, p99 24.5 ms; revoke: p50 27.8 ms, p99 40.9 ms (100 each)

role order-service-short: default_ttl 6 s, max_ttl 18 s (scaled down from 1 h / 24 h); 4 pods, pool of 3, connections replaced every 2 s, a query every 20 ms per pod; 40 s
client                                                           ok   failed    first failure   renewals  new creds
renew-only (Spring Cloud Vault's mode for database creds)      3367     2706           18.1 s         32          4
    database roles left behind afterwards: 0
DatabaseCredentials: renew, then rotate before max_ttl         7591        0                -         76         12
    database roles left behind afterwards: 0

== revocation
a session is open and working; then the credential is revoked, rotated or disabled
action                                                       new login                the open session, next query
OpenBao lease revoked (default revocation SQL)               refused (28P01)          still connected; query fails: 42501 permission denied for schema orders
OpenBao lease revoked (revocation SQL + pg_terminate_backend) refused (28P01)          session terminated by the server
static password changed (ALTER ROLE ... PASSWORD)            refused (28P01)          query works
password expired (VALID UNTIL in the past)                   refused (28P01)          query works
login disabled (ALTER ROLE ... NOLOGIN)                      refused (28000)          query works

revoke-prefix of 50 leases: API call 1374 ms; logins still working afterwards: 0 of 50

== apis
-- Spring Boot 4.1.1
OK       configtree: import reads files without ALWAYS_READ (cached)
OK       Fact: ...so content is read once, unless ALWAYS_READ
OK       management.endpoint.env.show-values defaults to NEVER
OK       DataSourceProperties.initializeDataSourceBuilder()
OK       DataSourceBuilder.type(Class)
OK       HikariCP version managed by Boot: 7.0.2
-- Spring Cloud Vault 5.0.2
OK       spring.cloud.vault.authentication=KUBERNETES
OK       spring.cloud.vault.kubernetes.role / kubernetes-path
OK       ...service-account-token-file default
OK       spring.cloud.vault.kv: backend secret, KV v2
OK       spring.cloud.vault.config.lifecycle.expiry-threshold
OK       spring.cloud.vault.database: enabled false by default
OK       Fact: database secrets are plain (non-leasing) metadata
OK       Fact: ...which are requested as renewable, not rotating
OK       Fact: the SecretLeaseContainer is registered as a bean
-- Spring Vault 4.0.3
OK       SecretLeaseContainer.requestRotatingSecret(String)
OK       Fact: requested after start → started immediately
OK       addLeaseListener(LeaseListener)
OK       LeaseListener.onLeaseEvent
OK       SecretLeaseCreatedEvent.getSecrets()
OK       SecretLeaseRotatedEvent extends SecretLeaseCreatedEvent
OK       SecretLeaseExpiredEvent
OK       SecretLeaseEvent.getSource() is the RequestedSecret
-- HikariCP 7.0.2
OK       HikariCredentialsProvider (since 7.0.0)
OK       HikariConfig.setCredentialsProvider
OK       Fact: the provider is asked for every new connection
OK       HikariPoolMXBean.softEvictConnections
OK       Credentials.of(user, password)
-- OpenBao 2.7.1
OK       Fact: default PostgreSQL revocation drops the role
OK       Fact: ...after revoking privileges and CONNECT
OK       Fact: ...and does not terminate sessions
OK       database/rotate-root/:name
OK       kubernetes auth role: bound_service_account_names
OK       kubernetes auth role: audience
-- External Secrets Operator 2.12.0
OK       ExternalSecret refreshInterval defaults to 1h
OK       remoteRef key / property
OK       Vault provider: kubernetes auth mountPath / role
OK       Vault provider: KV version
-- Kubernetes 1.37.1
OK       EncryptionConfiguration providers: kms
OK       KMS apiVersion (v2)
OK       identity provider

all APIs found
```
