# Chapter 36 — OAuth 2.1, OIDC & JWT

| Package | What it is |
|---|---|
| `json/` | `Json`: strict minimal JSON (duplicate keys, trailing data and deep nesting refused) |
| `jwt/` | `Jwk` (RSA and P-256 keys from a JWKS), `JwtSigner` (the authorization server's side), `JwtVerifier` (the resource server's side: algorithm allowlist, key by kid, signature, issuer, audience, `at+jwt` type, time), `VerifiedJwt`, `JwtRejected` |
| `discovery/` | `JwksClient`: OpenID Connect discovery, JWKS cache, refresh on an unknown kid at most every 30 s |
| `pkce/` | `Pkce`: RFC 7636 verifier and S256 challenge |
| `exchange/` | `TokenExchangePolicy` (RFC 8693 rules at the authorization server: one audience, fewer scopes, shorter life, `act` claim), `TokenExchangeClient` (the calling service's side, cached by token hash) |
| `authserver/` | `MiniAuthorizationServer`: a JDK-only authorization server for tests and measurements (discovery, JWKS with sudden or staged key rotation, authorization code + PKCE, client credentials, token exchange, introspection, revocation). Not for production |
| `web/` | order-service as a resource server built from the above (`BearerTokenFilter`, scope checks with RFC 6750 errors) and its token exchange for payment-service |
| `verify/` | `run.sh` and `tools/Measure.java`: the measurements below, with a JDK only |

## Run

```bash
cd code
mvn -B -pl chapters/ch36-oauth2-oidc-jwt -am verify     # 22 unit tests, including the flows over loopback HTTP

# the measurements: only a JDK (21+) is needed
chapters/ch36-oauth2-oidc-jwt/verify/run.sh
```

## Verified output

JDK 21 in a 2-vCPU container; the authorization server on a loopback port. Two runs; `revocation` and `rotation`
are deterministic, the timings varied between runs (the chapter quotes the range of both). This is the second.

```text
== crypto
one thread, 3 s per figure
alg            signs/s      verifies/s       token bytes
ES256            4,614           1,656               394
RS256              924          24,215               650

== validation
per request, 3000 requests, the authorization server on the same machine (loopback)
method                                            p50 us      p99 us     calls to the issuer
JWT verified locally (ES256, cached JWKS)            652        11172 (discovery + JWKS, once)
opaque token, introspected per request              1403        6171         one per request

== revocation
100,000 tokens revoked (logout, compromised account) at a random moment of their life;
how long each kept working afterwards
lifetime      resource servers check by                         mean         worst
5 min         local JWT verification                           150 s         300 s
5 min         introspection, cached 30 s                        15 s          30 s
5 min         introspection, no cache                            0 s           0 s
15 min        local JWT verification                           450 s         900 s
15 min        introspection, cached 30 s                        15 s          30 s
15 min        introspection, no cache                            0 s           0 s
60 min        local JWT verification                          1802 s        3600 s
60 min        introspection, cached 30 s                        15 s          30 s
60 min        introspection, no cache                            0 s           0 s

== rotation
a resource server's JWKS cache: refreshed every 5 min, and on an unknown kid at most every 30 s

1,000 tokens with unknown kids, refresh limit 30 s: rejected 1,000, JWKS fetched 0 times
1,000 tokens with unknown kids, refresh limit  0 s: rejected 1,000, JWKS fetched 1,000 times

key rotation, one request a second for 10 minutes after the switch:
  sudden (new key signs at once)                        29 requests rejected
  staged (published 6 minutes before it signs)           0 requests rejected

== exchange
token       aud               scope                                                   act                         lifetime
user's      order-service     payments:capture profile:read orders:read orders:write  -                              900 s
exchanged   payment-service   payments:capture                                        {"sub":"order-service"}        300 s

exchange cost: first call per user p50 3191 us, p99 11889 us (an HTTP call to the issuer); later calls from the cache 27.6 us
```
