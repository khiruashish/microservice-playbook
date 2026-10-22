# Chapter 35 — Security Fundamentals & Threat Modeling

| Package | What it is | OWASP API Top 10 (2023) |
|---|---|---|
| `identity/` | `Caller`, `Role`, `TokenService` (HMAC-SHA256 signed tokens with expiry, constant-time verification; a stand-in until chapters 36-37) | API2 |
| `orders/` | `OrderAccessPolicy` (who may do what to which order), `OrderService` (checks in the service; others' orders answer 404), `OrderView` (per-audience fields), `OrderPatch` (the only changeable fields, validated, unknown fields rejected), `PageLimits` | API1, API3, API4, API5 |
| `auth/` | `PasswordHasher` (PBKDF2-HMAC-SHA256, 600,000 iterations, salted, upgradable), `LoginThrottle` (growing waits per account and per address, no hard lockout), `LoginService` (same answer for unknown users) | API2 |
| `outbound/` | `OutboundUrlPolicy` (checks resolved addresses, not URL text), `WebhookClient` (no redirects, timeouts, bounded responses) | API7, API10 |
| `threatmodel/` | STRIDE per element as code: `ThreatModel`, `CheckoutThreatModel` (FoodFleet checkout), `ThreatReport`; the build fails on an open threat | — |
| `web/` | Spring MVC adapter: `TokenAuthenticationFilter` (deny by default), controllers, RFC 9457 errors | API8 |
| `verify/` | `run.sh` and `tools/Measure.java`: the measurements below, with a JDK only | |

## Run

```bash
cd code
mvn -B -pl chapters/ch35-security-fundamentals -am verify     # 29 unit tests

# the measurements: only a JDK (21+) is needed; the security packages have no framework dependencies
chapters/ch35-security-fundamentals/verify/run.sh
```

## Verified output

JDK 21 in a 2-vCPU container. Two runs; `authz`, `throttle` and `threatmodel` are deterministic, `hashrate` varied
between runs (the chapter quotes the range of both). This is the second.

```text
== authz
31 callers x 120 orders x 5 actions = 18,600 decisions
  allowed 855, denied 17,745
  disagreements with the rule table: 0
  a customer allowed anything on another customer's order: 0

== throttle
password checks against one account in one hour:
  no throttle (one core verifying PBKDF2)       26,666
  LoginThrottle, one source address                 18
  LoginThrottle, 1,000 source addresses             18

== hashrate
hash computations per second on one CPU core:
  SHA-256, unsalted                     13,016,000
  PBKDF2-HMAC-SHA256, 600,000 its.             7.0   (144 ms per login)
  ratio                                  1,871,372x

== threatmodel
FoodFleet checkout: 21 elements, 69 threats

STRIDE category           mitigated  accepted   open
SPOOFING                          7         0      0
TAMPERING                        17         0      0
REPUDIATION                       7         1      0
INFORMATION_DISCLOSURE           17         0      0
DENIAL_OF_SERVICE                16         1      0
ELEVATION_OF_PRIVILEGE            3         0      0
```
