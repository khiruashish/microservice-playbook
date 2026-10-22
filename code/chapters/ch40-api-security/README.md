# Chapter 40 — API Security Hardening

| Package / file | What it is |
|---|---|
| `ratelimit/` | `FixedWindowLimiter` and `SlidingWindowLimiter` (for comparison), `GcraLimiter` (token bucket as one timestamp per key, with idle-key eviction), `RedisGcraLimiter` (the same in Redis, `resources/ratelimit/gcra.lua`), `LayeredLimiter` (per client, then per tenant), `RateLimitHeaders` (`RateLimit-Policy`, `RateLimit`, `Retry-After`) |
| `validation/` | `FreeText` (NFC, no controls, bidi overrides, lone surrogates or noncharacters, length in code points), `EmailAddress` (linear time), `InvalidInput` (field and rule, never the value) |
| `cors/` | `CorsPolicy`: exact HTTPS origins, normalised, `Vary: Origin`, preflight checks; refuses `*`, `null`, patterns and paths in configuration |
| `csrf/` | `FetchMetadataPolicy`: refuses cross-site and untrusted same-site writes using `Sec-Fetch-*`, falls back to `Origin` |
| `tenancy/` | `TenantId` (from the token), `TenantTransactions` (the tenant set transaction-locally for row-level security) |
| `web/` | order-service's edge: `ApiSecurityConfig` (a `/bff` chain with Fetch Metadata and `csrf.spa()`, an `/api` chain with bearer tokens, CORS and no CSRF), `RateLimitFilter` (Redis limits with a per-instance fallback), `EdgeConfig`, `OrderRequest` (Bean Validation), `OrderController`, `ApiErrors`, `order-service.yml` |
| `resources/` | `tenancy/schema.sql` (forced row-level security, a non-owner role), `waf/api-setup.conf` and `waf/exclusions.conf` (OWASP CRS for a JSON API), `gateway/gateway.yml` (the Spring Cloud Gateway equivalent of the limits) |
| `verify/` | `run.sh` with eight experiments, `check_apis.sh`, `tools/` (`Measure.java`, `BrowserLab.java` + `browser.py` for Chromium, `Pg.java`, `Resp.java`, `redos.py`, `redos.js`), `waf/` (a Coraza harness and the request corpus) |

## Run

```bash
cd code
mvn -B -pl chapters/ch40-api-security -am verify                 # unit tests (JDK only)

# the experiments: a JDK, Redis, PostgreSQL 16 (superuser via psql; SCRAM logins over TCP), python3 with
# Playwright and Chromium, node, Go 1.25+
PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres chapters/ch40-api-security/verify/run.sh
chapters/ch40-api-security/verify/run.sh limiters fairness cors  # the ones that need only a JDK
```

## What was verified, and how

This environment has no Maven repository access, so the Spring application was not started here. Instead:

- The JDK code (`ratelimit/`, `validation/`, `cors/`, `csrf/`, `tenancy/`) and `RateLimitFilter` have 40 tests, all
  passing here (the filter test against stand-ins for the servlet and Spring Security types).
- `verify/run.sh` ran the limiters in simulated time and against **Redis 7.0.15**; the regular expressions in JDK 21,
  Python 3.11 and Node 22; CORS and CSRF in **Chromium 141** (Playwright) against `BrowserLab`, a local HTTPS server
  that answers for four sites (`*.test` mapped to 127.0.0.1, nothing leaves the machine); the request corpus through
  **Coraza 3.8.1 with OWASP CRS 4.30.0**; and tenant isolation against **PostgreSQL 16.13**.
- The Spring classes in `web/` compile against stand-ins whose signatures were checked against the sources;
  `verify/check_apis.sh` checks 48 APIs, properties, defaults and facts against Spring Boot 4.1.1, Spring Framework
  7.0.9, Spring Security 7.1.1, Spring Cloud Gateway 5.0.2, Jackson 3.1.5 and OWASP CRS 4.30.0.
- `waf/go.mod` names Coraza 3.8.1; Go modules could not be fetched from the module proxy here, so the harness was
  built with the same modules cloned from their GitHub mirrors (`WAF_BIN` lets `run.sh` use such a build).

## Verified output

JDK 21.0.10, Redis 7.0.15, PostgreSQL 16.13, Chromium 141, Coraza 3.8.1 (Go 1.26.9) on one 2-vCPU container. Two runs
with identical counts; timings differ (the chapter quotes ranges). This is the second; the API checks are from the
first.

```text
== limiters
policy: 100 requests per minute, one client
                                 retrying every 10 ms for 10 min    bursts of 200 around minute boundaries
limiter                          allowed    max in 60 s  max in 1 s   max in 1 s
fixed window                     1100       200          100          200
sliding window counter           1041       149          100          100
token bucket (GCRA), burst 100   1099       199          100          100
token bucket (GCRA), burst 10    1009       109          11           10

limiter                          ns per decision        bytes per tracked key
fixed window                     61                     65
sliding window counter           113                    74
token bucket (GCRA), burst 100   83                     66
token bucket (GCRA), burst 10    120                    66

== fairness
capacity 1,000 req/s; spice-route's POS sync loops at 5,000 req/s; 4 tenants at 100 req/s each
limits                                       quiet tenants served     spice-route served
global limit only                            0.2% (9 of 4000)         20.2% (10090 of 50000)
per-tenant (250/s) + global                  100.0% (4000 of 4000)    5.1% (2549 of 50000)
per-client (100/s) + per-tenant + global     100.0% (4000 of 4000)    2.0% (1019 of 50000)

== redos
java.util.regex on JDK 21: time to reject one input (cut off at 10 s)
input: n word characters, then '!' (after an opening quote for the quoted patterns)
pattern                                                    n=16       n=20       n=24       n=28
name: ^(\w+\s?)*$                                          1.44 ms    0.64 ms    0.79 ms    0.55 ms   
name, lazy: ^(\w+?\s?)*?$                                  35 ms      161 ms     2489 ms    > 10 s    
quoted name, backreference: ^(['"])(?:\w+\s?)*\1$          24 ms      222 ms     3054 ms    > 10 s    
quoted name, no backreference: ^['"](?:\w+\s?)*['"]$       0.39 ms    0.26 ms    0.12 ms    0.13 ms   
a widely copied email regex                                0.13 ms    0.15 ms    0.21 ms    0.24 ms   
EmailAddress.parse (length check, split, possessive)       6.29 ms    0.099 ms   0.011 ms   0.010 ms  

polynomial cases, input of n characters:
pattern                                                    n=1,000    n=5,000    n=10,000   n=20,000
trailing whitespace: [\s\u200c]+$ (find)                   23 ms      527 ms     2497 ms    9542 ms   
String.strip()                                             0.11 ms    0.011 ms   0.010 ms   0.010 ms  
CSV with 12 fields: ^(.*?,){11}P                           > 10 s     -          -          -         
Python 3.11 re: ^(\w+\s?)*$                                7.19 ms    108 ms     2046 ms    32363 ms  
Node 22.22.2 RegExp: ^(\w+\s?)*$                           8.03 ms    9.69 ms    159 ms     2432 ms   

== cors
which Origin values each matcher accepts (✔ = gets the response, with the user's cookies)
matcher                                        1   2   3   4   5   6   7   8   9  
reflect any Origin                             ✔   ✔   ✔   ✔   ✔   ✔   ✔   ✔   ✔    7 not ours
o.endsWith("foodfleet.in")                     ✔   ✔   ✔   ·   ·   ✔   ✔   ✔   ·    4 not ours
o.contains("foodfleet.in")                     ✔   ✔   ✔   ✔   ·   ✔   ✔   ✔   ·    5 not ours
regex https://.*.foodfleet.in (dot unescaped)  ✔   ✔   ✔   ·   ·   ✔   ✔   ·   ·    3 not ours
Spring pattern https://*foodfleet.in           ✔   ✔   ✔   ·   ·   ✔   ✔   ·   ·    3 not ours
Spring pattern https://*.foodfleet.in          ✔   ✔   ·   ·   ·   ·   ✔   ·   ·    1 not ours
allow-list incl. "null"                        ✔   ✔   ·   ·   ·   ·   ·   ·   ✔    1 not ours
CorsPolicy (exact allow-list)                  ✔   ✔   ·   ·   ·   ·   ·   ·   ·    0 not ours
  1 https://www.foodfleet.in                     ours
  2 https://partners.foodfleet.in                ours
  3 https://evilfoodfleet.in                     attacker registers a domain
  4 https://www.foodfleet.in.attacker.example    attacker's subdomain
  5 https://foodfleet-in.attacker.example        attacker registers a domain
  6 https://wwwxfoodfleet.in                     attacker registers a domain
  7 https://reviews.foodfleet.in                 our subdomain hosting user content
  8 http://www.foodfleet.in                      downgraded to http (network attacker)
  9 null                                         sandboxed iframe, file:, some redirects

== redis
Redis 7.0.15
policy: 50 requests/s per client, burst 10; the client sends 400 requests/s for 5 s through 4 instances; at most 260 should get through
where the limit lives                          allowed (even)   allowed (70/10/10/10) p50          p99
in each instance (50/s each)                   1036             859              13 µs        203 µs
in each instance, divided by 4 (fallback)      244              244              9.2 µs       58 µs
Redis, shared (gcra.lua)                       259              259              446 µs       4274 µs
Redis, back to back on one connection                                            84 µs        238 µs

when Redis cannot be reached, time per rate-limit decision:
  connection refused (Redis down)                  median 94 µs
  no answer (partition), 50 ms connect timeout     median 50796 µs

10 requests/s, burst 10; a client sending 100 requests/s for 5 s (after its first second):
limiter                                      allowed    most allowed in any 100 ms allowed in a second's first 100 ms
Spring Cloud Gateway RedisRateLimiter        50         10                         50 of 50
gcra.lua (this chapter)                      50         2                          5 of 50

== tenancy
PostgreSQL 16.13 (Ubuntu 16.13-0ubuntu0.24.04.1)
orders: 200000 rows, 50 tenants; cust-1 has 40 orders in tenant-07

a query that forgets "AND tenant_id = ?", run for tenant-07 (rows returned for cust-1):
  no row-level security, connected as the app                2000
  row-level security, connected as the app                   40
  row-level security, as the table owner, not FORCEd         2000
  row-level security, as the table owner, FORCEd             40

the tenant setting never set on this connection (a code path that skips it):
  policy uses current_setting('app.tenant_id')               ERROR 42704
  policy uses nullif(current_setting(.., true), '')          0
the same, on a connection where an earlier transaction had set it locally (value now ''):
  policy uses current_setting('app.tenant_id')               0
  policy uses nullif(current_setting(.., true), '')          0

one pooled connection, two requests: tenant-07's, then tenant-12's on a code path that skips setting the tenant
  session SET app.tenant_id (stays on the connection)            tenant-12's request sees 4000 rows of tenant-07
  set_config(.., true) in the transaction (TenantTransactions)   tenant-12's request sees 0 rows of no tenant

writing another tenant's row while scoped to tenant-07:
  INSERT ... tenant_id = 'tenant-12'                         ERROR 42501 (row-level security)
  UPDATE orders SET tenant_id = 'tenant-12' (moving a row out) ERROR 42501 (row-level security)

cost per query (client round trip, 3,000 queries each, after warm-up):
  query                                        explicit tenant predicate  row-level security
  one order by id                              p50 167 µs, p99 512 µs     p50 202 µs, p99 587 µs
  a customer's orders (about 40 rows)          p50 444 µs, p99 1249 µs    p50 506 µs, p99 1393 µs
  count a tenant's orders                      p50 689 µs, p99 1695 µs    p50 826 µs, p99 2614 µs

plans (EXPLAIN, row-level security):
  Index Scan using orders_pkey on orders  (cost=0.42..8.45 rows=1 width=8)
    Index Cond: ((tenant_id = NULLIF(current_setting('app.tenant_id'::text, true), ''::text)) AND (id = 42))

== browser
Chromium 141.0.7390.37

state-changing requests to foodfleet.test/bff, the user logged in there (session cookies set with each SameSite value)
page                     request                                    reached server   cookies sent               Sec-Fetch-Site  CSRF token  Fetch Metadata
evil.test                form POST (top-level navigation)           yes              · · None unset             cross-site      refused     refused (cross-site)
evil.test                fetch POST, no-cors, text/plain            yes              · · None ·                 cross-site      refused     refused (cross-site)
evil.test                fetch POST, JSON (needs preflight)         no (preflight refused)
evil.test                fetch POST, JSON, server reflects Origin   yes + preflight  · · None ·                 cross-site      refused     refused (cross-site)
reviews.foodfleet.test   form POST (top-level navigation)           yes              Lax Strict None unset      same-site       refused     refused (same-site, untrusted origin)
reviews.foodfleet.test   fetch POST, no-cors, text/plain            yes              Lax Strict None unset      same-site       refused     refused (same-site, untrusted origin)
evil.test                link to /bff/orders (GET navigation)       yes              Lax · None unset           cross-site      n/a (GET)   accepted (cross-site navigation)
foodfleet.test           the app's own fetch, token in header       yes              Lax Strict None unset      same-origin     accepted    accepted (same-origin)

a page reading the user's orders from foodfleet.test/bff/orders with fetch(..., {credentials: 'include'})
page                     request Origin                         server CORS                         session cookie   what the page got
evil.test                https://evil.test:8443                 reflects any Origin + credentials   SameSite=Lax     empty list (no cookie sent)
evil.test                https://evil.test:8443                 reflects any Origin + credentials   SameSite=None    the orders
evil.test                https://evil.test:8443                 * + credentials                     SameSite=None    nothing (browser blocked the read)
evil.test                https://evil.test:8443                 exact allow-list (www only)         SameSite=None    nothing (browser blocked the read)
evil.test                null                                   allow-list + "null"                 SameSite=None    the orders
evil.test                null                                   exact allow-list (www only)         SameSite=None    nothing (browser blocked the read)
reviews.foodfleet.test   https://reviews.foodfleet.test:8443    reflects any Origin + credentials   SameSite=Lax     the orders
reviews.foodfleet.test   https://reviews.foodfleet.test:8443    exact allow-list (www only)         SameSite=Lax     nothing (browser blocked the read)
www.foodfleet.test       https://www.foodfleet.test:8443        exact allow-list (www only)         SameSite=Lax     the orders

the cross-site form POST again, 125 s after login: cookies sent · · None ·

== waf
-- OWASP CRS 4.30.0 as shipped
corpus: 41 benign, 20 attacks, 6 application-logic abuses
paranoia   benign blocked         attacks blocked      logic abuses blocked     median / p99 per request
PL1        6 of 41                19 of 20             2 of 6                   2.789ms / 8.234ms
PL2        20 of 41               20 of 20             2 of 6                   4.785ms / 12.296ms
PL3        24 of 41               20 of 20             2 of 6                   6.009ms / 14.101ms
PL4        36 of 41               20 of 20             2 of 6                   6.469ms / 14.379ms
-- with FoodFleet's API settings (resources/waf/api-setup.conf)
corpus: 41 benign, 20 attacks, 6 application-logic abuses
paranoia   benign blocked         attacks blocked      logic abuses blocked     median / p99 per request
PL1        2 of 41                19 of 20             0 of 6                   2.99ms / 7.764ms
PL2        17 of 41               20 of 20             0 of 6                   5.704ms / 15.321ms
PL3        21 of 41               20 of 20             0 of 6                   6.385ms / 13.75ms
PL4        36 of 41               20 of 20             0 of 6                   6.316ms / 14.826ms
-- plus the rule exclusions for free-text fields (resources/waf/exclusions.conf)
corpus: 41 benign, 20 attacks, 6 application-logic abuses
paranoia   benign blocked         attacks blocked      logic abuses blocked     median / p99 per request
PL1        0 of 41                17 of 20             0 of 6                   2.386ms / 7.404ms
    missed attack:  sqli: ' OR '1'='1 in query
    missed attack:  sqli: UNION SELECT in query
    missed attack:  sqli: stacked query in path param
PL2        4 of 41                19 of 20             0 of 6                   5.006ms / 12.285ms
    false positive: search: fish and chips or burger (932236)
    false positive: restaurant: menu description with markup (941320)
    false positive: support: chat message with URL (931130)
    false positive: sort and fields params (932236)
    missed attack:  sqli: UNION SELECT in query
PL3        8 of 41                19 of 20             0 of 6                   6.189ms / 14.985ms
    false positive: search: fish and chips or burger (932236)
    false positive: search: Kannada dish (920272)
    false positive: order note: gate 3, 'B' block (near drop-off) (933161)
    false positive: order note: Hindi and emoji (920272)
    false positive: restaurant: menu description with markup (941320)
    false positive: support: chat message with URL (931130)
    false positive: sort and fields params (932236)
    false positive: webhook from payment gateway (932350)
    missed attack:  sqli: UNION SELECT in query
PL4        36 of 41               20 of 20             0 of 6                   6.426ms / 14.571ms
    false positive: search: biryani near me (920273)
    false positive: search: mac & cheese (920273)
    false positive: search: fish and chips or burger (920273,932236)
    false positive: search: Union Square Bakery (920273)
    false positive: search: select menu items (920273)
    false positive: search: Kannada dish (920272,920273)
    false positive: search: 2 for 1 offers (920273)
    false positive: order note: don't add onions; select the less spicy one (920273)
    false positive: order note: ring twice -- dog barks (920273)
    false positive: order note: gate 3, 'B' block (near drop-off) (920273,933161)
    false positive: order note: door code #4521 * then 2 (920273)
    false positive: order note: Hindi and emoji (920272,920273)
    false positive: order note: drop at the security desk or reception (920273)
    false positive: order note: 1 = no cutlery, 2 = cutlery (920273)
    false positive: order note: exec lunch for the team (920273,942432)
    false positive: order note: <3 smiley (920273)
    false positive: address: No. 12/3, 1st Cross; HSR (920273)
    false positive: address: flat 'C-404', opp. temple (920273)
    false positive: profile: name O'Brien (920273)
    false positive: profile: name with hyphen and dot (920273)
    false positive: review: late & cold :( (920273)
    false positive: review: best pizza <3 !!! (920273)
    false positive: review: or else / and then (920273)
    false positive: review: script of a movie (920273)
    false positive: restaurant: menu description with markup (920273,941320)
    false positive: restaurant: hours 10:00-23:00 (920273,942432)
    false positive: restaurant: SQL-like item name (920273)
    false positive: rider: delivery proof note (920273)
    false positive: support: chat message with URL (920273,931130)
    false positive: support: pasted app error (920273)
    false positive: support: Windows path in message (920273,920460)
    false positive: graphql-style query param (920273)
    false positive: sort and fields params (932236)
    false positive: webhook from payment gateway (920273,932350)
    false positive: code snippet in partner support (920273)
    false positive: search: rock & roll cafe (920273)


== apis
-- Spring Boot 4.1.1
OK       server.max-http-request-header-size defaults to 8KB
OK       server.tomcat.max-http-form-post-size defaults to 2MB
OK       server.tomcat.max-swallow-size defaults to 2MB
OK       server.tomcat.max-parameter-count defaults to 1000
OK       server.tomcat.remoteip.internal-proxies: CIDR list...
OK       ...whose default trusts 10.0.0.0/8 and 100.64.0.0/10
OK       server.servlet.session.cookie.same-site (Cookie.sameSite)
OK       spring.jackson.deserialization.* (feature map)
OK       spring.jackson.factory.constraints.read...
OK       ...max-nesting-depth defaults to 500
OK       ...max-document-length defaults to unlimited (-1)
OK       ...max-string-length defaults to 100,000,000
OK       Jackson 3.1.5 is the managed version
OK       spring.mvc.problemdetails.enabled (default false)
OK       spring.security.oauth2.resourceserver.jwt.audiences
OK       spring.data.redis.timeout
OK       spring-boot-starter-validation
OK       spring-boot-starter-security-oauth2-resource-server
OK       spring-boot-starter-security-oauth2-client
OK       spring-boot-starter-data-redis
-- Spring Framework 7.0.9
OK       allowCredentials with allowedOrigins "*" is rejected
OK       allowedOrigins are compared ignoring case
OK       allowedOriginPatterns: the pattern is quoted...
OK       ...and every * becomes .* (any characters, dots too)
OK       ProblemDetail.forStatusAndDetail / setProperty
-- Spring Security 7.1.1
OK       csrf.spa() (since 7.0)...
OK       ...uses CookieCsrfTokenRepository.withHttpOnlyFalse()
OK       default CSRF repository: the HTTP session
OK       CSRF not required for GET, HEAD, TRACE, OPTIONS
OK       cookie XSRF-TOKEN, header X-XSRF-TOKEN
OK       HttpSecurity.securityMatcher(String...)
OK       HttpSecurity.cors(Customizer)
OK       HttpSecurity.addFilterAfter(Filter, Class)
-- Spring Cloud Gateway 5.0.2
OK       properties under spring.cloud.gateway.server.webflux
OK       RedisRateLimiter script: Redis TIME in whole seconds
OK       RedisRateLimiter passes an empty 'now'
OK       RedisRateLimiter fails open on Redis errors
OK       RedisRateLimiter headers are X-RateLimit-*
OK       RequestRateLimiter: denyEmptyKey true, 403 by default
-- Jackson 3.1.5
OK       StreamReadConstraints: max depth 500
OK       ...max document length unlimited
OK       ...max string length 100,000,000
OK       FAIL_ON_UNKNOWN_PROPERTIES is off by default in Jackson 3
-- OWASP CRS 4.30.0
OK       inbound anomaly score threshold defaults to 5
OK       blocking paranoia level defaults to 1
OK       allowed methods default to GET HEAD POST OPTIONS
OK       rule 911100 enforces the allowed methods
OK       plain application/json is parsed by the engine's rule 200001, not CRS

all checks passed
```
