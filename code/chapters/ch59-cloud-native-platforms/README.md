# Chapter 59 — Cloud-Native on AWS, Azure & GCP

Code and measurements for [chapter 59](../../../docs/deployment/ch59-cloud-native-platforms.md).

| Path | What |
|---|---|
| `src/main/java/.../identity/` | `TokenVerifier` (an RS256 JWT checked offline against the issuer's JWKS: signature, issuer, audience, expiry), `TrustPolicy` (a role that trusts one audience and named service accounts, and issues one-hour credentials), a minimal JSON reader |
| `src/main/java/.../retry/` | `RetryStrategy` and `Strategies`: the AWS SDK for Java v2 retry modes (none, legacy, standard, adaptive) with their constants, `RetryQuota` (the 500-token retry budget), `ClientRateLimiter` (adaptive mode's token bucket) |
| `src/main/java/.../serverless/` | `FunctionPool` (requests replayed against function instances with keep-warm and provisioned instances), `Traffic` (a day with night, lunch and dinner peaks), `CostModel` (Lambda and Fargate list prices, us-east-1, 2026-10-15) |
| `src/main/resources/k8s/` | `workload-identity.yaml`: a service account and a pod with a projected token for the cloud's audience |
| `src/test/java/` | 5 tests |
| `verify/` | `build-libs.sh` (the identity-lab image; AWS SDK and Kubernetes source clones), `run.sh`, `check_apis.sh` (16 checks), `tools/` (`IdentityLab`, `Measure`) |

```bash
../../ch52-health-checks-actuator/verify/k3s-lab.sh   # a cluster to act as the OIDC issuer
JAVA_HOME=/path/to/jdk-25 verify/run.sh               # about 25 minutes; throttling and serverless need no cluster
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, JDK 25.0.4, two runs)

**identity**: the cluster's service-account issuer acted as the OIDC provider; a token service checked tokens offline against its JWKS.

| Token presented | Answer |
|---|---|
| menu-service's projected token for `sts.foodfleet.local` | credentials for `menu-reader`, 60 minutes |
| menu-service's default token | refused: wrong audience (the API server's) |
| reporting's projected token | refused: not authorized to assume `menu-reader` |
| a token with one claim edited | refused: bad signature |

- Lifetimes: projected 600 s, refreshed by the kubelet after 515–517 s (the old token still verified, 77–83 s left); default token 31,536,000 s (365 days).
- After the pod was deleted, TokenReview still authenticated its token at 3 s (the API server's 10-second cache) and rejected it at 30 s and 120 s. The offline check accepted it throughout, with 475 s still to run at 120 s.

**throttling**: 40 SDK clients wanting 200 calls/s against an API limited to 100/s, for 60 s.

| Mode | Calls | Failed | Attempts/call | Throttled responses | p50 / p99 |
|---|---|---|---|---|---|
| none | 12,000 | 5,911 | 1.00 | 5,911 | 1 / 11–17 ms |
| legacy | 6,919–6,949 | 766–800 | 1.95–1.96 | 7,367–7,448 | 1 / 2,507–2,585 ms |
| standard | 7,011–7,060 | 866–914 | 1.64 | 5,376–5,423 | 1 / 2,416–2,436 ms |
| adaptive | 6,290–6,315 | 141–161 | 1.33–1.34 | 2,258–2,259 | 6–49 / 3,906–3,956 ms |

Successful calls: 101–103 a second in every mode. In a 20-second outage at 60 calls/s, retries raised attempts to 172–201 a second for the first 2 s; once each client's retry quota was spent, back to 60.

**serverless**: menu-service (chapter 54's image) cold-started in 1,388–1,452 ms (median of 10, four runs), warm requests 1.5–3.5 ms. A synthetic day priced at list prices:

| Peak | Functions $/day | Fargate sized for the peak | Fargate following load | Cold starts (5-min keep-warm) |
|---|---|---|---|---|
| 0.2 req/s | 0.01 | 2.37 | 2.37 | 57 (1.6%) |
| 5 | 0.10 | 2.37 | 2.37 | 53 |
| 50 | 0.99 | 3.55 | 2.54 | 74 |
| 200 | 3.96 | 14.22 | 4.30 | 140 |
| 1,000 | 19.81 | 71.09 | 15.73 | 261–263 |

**apis**: 16 of 16 found in the AWS SDK for Java 2.56.0 and Kubernetes 1.37.1.

## Notes

- No cloud account was used. The token service, the trust policy and the throttled API are local models; the tokens, the issuer, the JWKS, the kubelet's refresh and TokenReview are the real cluster's. The retry modes are ports of the SDK's, checked constant by constant by `check_apis.sh`.
- The serverless part was run twice more on its own after the cost table gained the "sized for the peak" column; the traffic and prices are deterministic, so only cold starts differed.
- A trial run before the two runs gave the same picture within a few percent, except a slow first cold start (5.8 s) and a lower adaptive p50.
- The outage scenario was changed once during development: with its first settings (fewer, slower clients) the retry quota was never exhausted.
