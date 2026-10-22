# Chapter 38 — Service-to-Service Security

| Package / file | What it is |
|---|---|
| `spiffe/` | `SpiffeId` (strict parsing by the SPIFFE-ID standard), `X509Svid` (the X509-SVID rules a normal trust manager doesn't check; validity per request), `SpiffeTrustManager` (validates an SVID against the bundle of its own trust domain, then authorizes the peer; bundles swappable for CA rotation), `RotatingKeyManager` (new SVIDs under a running `SSLContext`), `TrustBundles`, `SpiffeTls` (TLS 1.3 mutual-TLS sockets) |
| `authz/` | `PeerPolicy`: which SPIFFE ID may call which method and path; deny by default; ambiguous raw paths denied |
| `token/` | `CertificateBinding` (RFC 8705 `cnf.x5t#S256`, same decision table as Spring Security's validator), `ServiceTokens` (client-credentials token shared by all threads, single-flight renewal at 80% of its lifetime) |
| `pki/` | `DevCa`: a JDK-only development CA that issues SPIFFE SVIDs (and deliberately broken certificates for tests). Not for production |
| `web/payments/` | payment-service: mutual TLS from a Spring Boot PEM SSL bundle (`payment-service.yml`), `PeerPolicyFilter` before the bearer-token filter, scopes per route, deny by default |
| `web/orders/` | order-service's `PaymentsClient`: SSL bundle + Spring Security's client-credentials interceptor with a fixed principal; rebuilt when the SVID files change |
| `resources/spire/` | SPIRE 1.15 server and agent configuration (Kubernetes PSAT node attestation, k8s workload attestation, KMS keys), registration entries, spiffe-helper configuration |
| `resources/istio/` | The same intent in a service mesh: mesh-wide STRICT mTLS and AuthorizationPolicies by peer identity |
| `verify/` | `run.sh`: six experiments with real TLS 1.3 on loopback (`tools/Measure.java`), then `check_apis.sh` checks every Spring API, property and default relied on against the Spring Security 7.1.1, Spring Boot 4.1.1 and Spring Framework 7.0.9 sources |

## Run

```bash
cd code
mvn -B -pl chapters/ch38-service-to-service-security -am verify     # unit tests (real mutual TLS on loopback) + the filter test

# the experiments (a JDK only) and the API checks (git + github.com)
chapters/ch38-service-to-service-security/verify/run.sh
chapters/ch38-service-to-service-security/verify/run.sh resumption       # one experiment

# on Kubernetes with SPIRE: deploy SPIRE with resources/spire/*.conf, run resources/spire/entries.sh, add the
# spiffe-helper sidecar (resources/spire/helper.conf) sharing /run/spiffe with the application container
```

## What was verified, and how

This environment has no Maven repository access, so the Spring applications were not started here, and SPIRE and
Istio were not run. Instead:

- Everything in `spiffe/`, `authz/`, `token/` and `pki/` is plain JDK code: 31 unit tests pass here, several over real
  TLS 1.3 connections on loopback, and `verify/run.sh` measures it.
- The Spring classes in `web/` compile against stand-ins whose signatures were checked against the Spring sources;
  `verify/check_apis.sh` repeats that check for every API, property and default the chapter relies on (42, all
  found). `PeerPolicyFilterTest` (Spring's servlet mocks) runs in a normal Maven build.
- The SPIRE and Istio files were checked against the SPIRE v1.15.3 documentation and the Istio 1.31.1 API protos
  (field names, defaults, selectors, path templates); the YAML parses and the shell script passes `bash -n`.
- The resumption results depend on the JDK's TLS implementation: they were measured on JDK 21.0.10. Run
  `verify/run.sh resumption` on the JDK you deploy.

## Verified output

JDK 21.0.10 in a 2-vCPU container, everything on loopback (client and server share the CPUs). Two runs; the chapter
quotes the range of both where they differ. This is the second.

```text
== handshake
each connection: connect, handshake, one 512-byte request and response, close; 200 connections x 5 rounds, median round; one client thread
connection                                        ms/connection    connections/s
plain TCP (no TLS)                                        0.246             4057
TLS 1.3, server certificate only (EC P-256)               4.217              237
mutual TLS 1.3, EC P-256 SVIDs                            4.002              250
mutual TLS 1.3, RSA-2048 SVIDs                            4.067              246
mutual TLS 1.3, EC, session resumed                       1.832              546

one kept-alive connection, 512-byte request and response, 20,000 round trips, median of 5
connection                                           us/request
plain TCP (no TLS)                                         41.6
mutual TLS 1.3, EC P-256 SVIDs                             57.2

== rejections
A: SpiffeTrustManager at the handshake (any foodfleet.internal SVID), then PeerPolicy per request
B: standard PKIX trust manager with FoodFleet's CA in the trust store, then the same per-request checks
client presents                            request                          A: SpiffeTrustManager                                      B: PKIX trust manager
order-service SVID, capture                POST /payments/42/capture        200, rule capture                                          200, rule capture
settlement-job SVID, run settlements       POST /internal/settlements/run   200, rule settle                                           200, rule settle
rider-service SVID (valid), capture        POST /payments/42/capture        403 by policy                                              403 by policy
order-service SVID, run settlements        POST /internal/settlements/run   403 by policy                                              403 by policy
order-service SVID, encoded path           POST /payments/42/%63apture      403 ambiguous path                                         403 ambiguous path
no client certificate                      POST /payments/42/capture        refused: Empty client certificate chain                    refused: Empty client certificate chain
partner.example SVID (not federated)       POST /payments/42/capture        refused: no trust bundle for trust domain partner.example  refused: PKIX path failed
order-service ID signed by another CA      POST /payments/42/capture        refused: path validation failed: signature check failed    refused: PKIX path failed
order-service SVID, expired                POST /payments/42/capture        refused: path validation failed: validity check failed     refused: PKIX path failed
two URI SANs                               POST /payments/42/capture        refused: more than one URI SAN                             403 more than one URI SAN
CA certificate as client certificate       POST /payments/42/capture        refused: certificate is a CA (cA=true)                     refused: KeyUsage does not allow digital signatures
ordinary TLS certificate (DNS SAN only)    POST /payments/42/capture        refused: no URI SAN                                        403 no URI SAN

== resumption
JDK 21.0.10; server session tickets on (default); default session timeouts 86400 s (server) / 86400 s (client)
order-service's SVID expires 3 s after its first connection; it keeps calling: a new connection every 250 ms, and one kept connection, until 6 s after expiry
setup                                          new connections after expiry         kept connection after expiry  
JDK defaults                                   19 accepted, 0 403, 0 refused        200 accepted as order-service 
server session timeout 2 s                     19 accepted, 0 403, 0 refused        200 accepted as order-service 
client session timeout 2 s                     4 accepted, 0 403, 17 refused        200 accepted as order-service 
JDK defaults + validity checked per request    0 accepted, 20 403, 0 refused        403 SVID expired              
JDK 21.0.10; server session tickets off (jdk.tls.server.enableSessionTicketExtension=false); default session timeouts 86400 s (server) / 86400 s (client)
order-service's SVID expires 3 s after its first connection; it keeps calling: a new connection every 250 ms, and one kept connection, until 6 s after expiry
setup                                          new connections after expiry         kept connection after expiry  
server session timeout 2 s                     19 accepted, 0 403, 0 refused        200 accepted as order-service 

== rotation
SVID lifetime 4 s (SPIRE default: 1 h), rotation at half the lifetime (SPIRE's default), 12 s of new connections every 20 ms, full handshakes
scenario                                                         ok   failed    first failure
SVIDs loaded once (SSLContext never updated)                    115      366            3.6 s
SVIDs rotated at half-life (key manager updated)                482        0                -
CA rollover at 3.5 s, bundles switched to the new CA            420       78            3.5 s
CA rollover at 3.5 s, bundles hold old + new CA                 494        0                -

== binding
order-service's access token for payment-service, presented by different connections
token          presented over                                             payment-service
bearer         order-service's mutual-TLS connection                      accepted: bearer token: no certificate binding
bearer         rider-service's connection (token stolen from a log)       accepted: bearer token: no certificate binding
bearer         TLS ended at a proxy; application sees no certificate      accepted: bearer token: no certificate binding
cert-bound     order-service's mutual-TLS connection                      accepted: bound to this client's certificate
cert-bound     rider-service's connection (token stolen from a log)       401 invalid_token: bound to a different certificate
cert-bound     TLS ended at a proxy; application sees no certificate      401 invalid_token: bound token but no client certificate on this request

== tokens
64 request threads for 9 s, each: get a token, then 2 ms of downstream work; token lifetime 3 s; loopback token endpoint
strategy                                                          calls   token reqs max in flight   p50 get us   p99 get us
fetch a token for every call                                      44304        44304           64         7559        44056
Spring-style store, skew 20% of lifetime, one principal          261985          256           64            0            1
Spring-style store keyed by the user (5,000 users)               213651        20000           64            0         6470
ServiceTokens: one renewal at 80% of lifetime                    260473            4            1            0            1
stored tokens, keyed by user: 5000; one principal: 1

== apis
-- Spring Security 7.1.1: payment-service's filter chain
OK       HttpSecurity.addFilterBefore(Filter, Class)
OK       BearerTokenAuthenticationFilter
OK       Fact: x509() reads the principal from the subject DN by default
OK       Fact: ...and takes the CN, not a URI SAN
-- Spring Security 7.1.1: certificate-bound tokens (RFC 8705)
OK       Fact: default JWT validators include the x5t#S256 check
OK       Fact: ...which reads the request's client certificate
OK       Fact: ...and rejects a bound token without one
OK       Fact: Boot's JwtDecoder uses those defaults
OK       Fact: the authorization server adds cnf.x5t#S256
OK       TokenSettings.x509CertificateBoundAccessTokens
OK       Fact: tls_client_auth matches the subject DN only
OK       X509ClientCertificateAuthenticationProvider.setCertificateVerifier
-- Spring Security 7.1.1: client credentials from a RestClient
OK       AuthorizedClientServiceOAuth2AuthorizedClientManager(repo, service)
OK       ...setAuthorizedClientProvider
OK       OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials()
OK       OAuth2ClientHttpRequestInterceptor(OAuth2AuthorizedClientManager)
OK       ...setPrincipalResolver
OK       Fact: default principal = the request's authenticated user
OK       Fact: no principal → one anonymous principal
OK       RequestAttributePrincipalResolver
OK       RequestAttributeClientRegistrationIdResolver.clientRegistrationId(String)
OK       Fact: tokens stored per (registration, principal name)
OK       Fact: client-credentials renewal skew 60 s
-- Spring Boot 4.1.1: SSL bundles and clients
OK       server.ssl.bundle
OK       server.ssl.client-auth (NONE/WANT/NEED)
OK       spring.ssl.bundle.pem.*.keystore / truststore
OK       ...certificate and private-key
OK       spring.ssl.bundle.pem.*.reload-on-update
OK       spring.ssl.bundle.watch.file.quiet-period (default 10 s)
OK       Fact: watched PEM files must be file resources
OK       SslBundles.addBundleUpdateHandler(String, Consumer)
OK       Fact: Tomcat re-reads an updated bundle
OK       RestClientSsl.fromBundle(SslBundle)
OK       Fact: fromBundle builds the request factory once
OK       Fact: Boot's default authorized-client service is in memory
OK       starter spring-boot-starter-restclient
OK       starter spring-boot-starter-security-oauth2-client
-- Spring Framework 7.0.9: RestClient and filters
OK       RestClient.Builder.clone()
OK       RestClient.Builder.apply(Consumer)
OK       RestClient.Builder.requestInterceptor
OK       RestClient request attributes(Consumer<Map>)
OK       OncePerRequestFilter.doFilterInternal

all APIs found
```
