# Chapter 37 — Spring Security 7

| Package / file | What it is |
|---|---|
| `ordersvc/security/` | `SecurityConfig` (URL rules by scope and role, deny by default, stateless resource server, `@EnableMethodSecurity`), `TokenClaims` (Keycloak and Spring Authorization Server claims → `SCOPE_` and `ROLE_` authorities, FoodFleet roles only), `TokenAuthoritiesConverter` |
| `ordersvc/orders/` | `OrderService` (`@PreAuthorize("@orderAccess.canRead(authentication, #id)")`), `OrderAccess` (the bean method security calls), `OrderRules` (plain Java, testable), `OrderController` (denied reads answer 404) |
| `authserver/` | A development authorization server on Spring Security 7's built-in OAuth 2.1 Authorization Server, configured by `authserver.yml`; `AccessTokenClaims` (typ `at+jwt`, `client_id`, audience from scopes or the token-exchange request, roles); `Users` (DelegatingPasswordEncoder, bcrypt 12) |
| `resources/keycloak/` | `foodfleet-realm.json` (roles, PKCE public client, token-exchange service client, audience mappers) and `compose.yaml` |
| `verify/` | `run.sh`: compiles spring-security-crypto from the 7.1.1 sources and measures it (`tools/Measure.java`), then `check_apis.sh` checks every Spring API and property used here against the 7.1.1 / 4.1.1 sources |

## Run

```bash
cd code
mvn -B -pl chapters/ch37-spring-security-7 -am verify      # unit tests + the MockMvc security test (no IdP needed)

# order-service (port 8081) against the development authorization server (port 9000):
ORDER_SERVICE_CLIENT_SECRET='{bcrypt}...' DEV_USER_PASSWORD=... \
  mvn -pl chapters/ch37-spring-security-7 spring-boot:run -Dspring-boot.run.main-class=dev.foodfleet.ch37.authserver.AuthServerApplication
mvn -pl chapters/ch37-spring-security-7 spring-boot:run -Dspring-boot.run.main-class=dev.foodfleet.ch37.ordersvc.OrderServiceApplication

# or against Keycloak:
KEYCLOAK_ADMIN_PASSWORD=... docker compose -f chapters/ch37-spring-security-7/src/main/resources/keycloak/compose.yaml up
OAUTH_ISSUER=http://localhost:8180/realms/foodfleet mvn -pl chapters/ch37-spring-security-7 spring-boot:run \
  -Dspring-boot.run.main-class=dev.foodfleet.ch37.ordersvc.OrderServiceApplication

# the checks: a JDK, git and github.com access
chapters/ch37-spring-security-7/verify/run.sh
```

## What was verified, and how

This environment has no Maven repository access, so the Spring application itself was not started here. Instead:

- The module compiles against stand-ins whose signatures were checked against the Spring Security 7.1.1 and
  Spring Boot 4.1.1 sources; `verify/check_apis.sh` repeats that check for every API and property (below).
- The plain-Java parts (claims mapping, audiences, order rules) are unit-tested; the password-encoder tests run
  against Spring Security's real `crypto` classes compiled from source.
- `OrderServiceSecurityTest` (MockMvc with spring-security-test's `jwt()`) runs in a normal Maven build; it
  needs no identity provider or Docker.
- The Keycloak realm was checked for JSON syntax only; import it with the compose file.

## Verified output

JDK 21 in a 2-vCPU container. Two runs; the encoder timings varied by a few percent (the chapter quotes the range of
both). This is the second.

```text
== encoders
one thread; median of several runs after one warm-up
encoder                                                  encode ms    matches ms     logins/s per core
BCryptPasswordEncoder() (strength 10, the default)            73.1          72.2                  13.9
BCryptPasswordEncoder(12)                                    259.2         290.5                   3.4
BCryptPasswordEncoder(14)                                   1094.0        1129.1                   0.9
Pbkdf2 defaultsForSpringSecurity_v5_8 (310,000)               72.0          70.7                  14.2
Pbkdf2, 600,000 iterations (OWASP minimum)                   139.8         135.5                   7.4

== upgrade
DelegatingPasswordEncoder: encodes {bcrypt} at 12, also maps {pbkdf2@SpringSecurity_v5_8}
stored hash                         matches the right password                  upgrade?
{bcrypt} strength 12 (current)      true                                           false
{bcrypt} strength 10                true                                            true
{pbkdf2@SpringSecurity_v5_8}        true                                            true
{noop} (plain text, not mapped)     exception: There is no password encoder m...             -
no {id} prefix (legacy column)      exception: Given that there is no default...             -

== apis
-- Spring Security 7.1.1: configuration DSL
OK       HttpSecurity.authorizeHttpRequests(Customizer)
OK       HttpSecurity.oauth2ResourceServer(Customizer)
OK       HttpSecurity.oauth2AuthorizationServer(Customizer)
OK       HttpSecurity.sessionManagement / csrf / httpBasic / formLogin
OK       registry.requestMatchers(HttpMethod, String...)
OK       AuthorizedUrl.hasRole / hasAuthority / denyAll
OK       OAuth2ResourceServerConfigurer.jwt(Customizer)
OK       JwtConfigurer.jwtAuthenticationConverter(Converter)
OK       SessionManagementConfigurer.sessionCreationPolicy
OK       @EnableMethodSecurity
OK       @PreAuthorize
OK       AuthorizationDeniedException
-- Spring Security 7.1.1: resource server
OK       JwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter
OK       JwtAuthenticationConverter.setPrincipalClaimName
OK       Default JWT validation: typ JWT or absent
OK       RFC 9068 validator: JwtValidators.createAtJwtValidator()
-- Spring Security 7.1.1: authorization server (built in since 7.0)
OK       OAuth2TokenCustomizer
OK       JwtEncodingContext.getJwsHeader / getClaims
OK       OAuth2TokenContext.getAuthorizationGrant / getAuthorizedScopes
OK       JoseHeader builder .type(String)
OK       JwtClaimsSet.Builder.audience(List)
OK       OAuth2TokenExchangeAuthenticationToken.getAudiences
OK       Fact: default access-token audience is the client id
OK       Fact: exchanged scopes are checked against the requesting client's registration
-- Spring Security 7.1.1: users, passwords, tests
OK       InMemoryUserDetailsManager(UserDetails...)
OK       BCryptPasswordEncoder default strength 10
OK       Pbkdf2 v5_8 defaults: 310,000 iterations
OK       SecurityMockMvcRequestPostProcessors.jwt()
OK       JwtRequestPostProcessor.authorities(Converter)
-- Spring Boot 4.1.1: properties and test support
OK       spring.security.oauth2.resourceserver.jwt.issuer-uri
OK       spring.security.oauth2.resourceserver.jwt.audiences
OK       spring.security.oauth2.resourceserver.jwt.jws-algorithms
OK       spring.security.oauth2.authorizationserver.client.*.registration
OK       ...client.*.require-proof-key (default true)
OK       ...client.*.token.reuse-refresh-tokens (default true)
OK       ...client.*.token.access-token-time-to-live (default 5m)
OK       @AutoConfigureMockMvc in spring-boot-webmvc-test
OK       starter spring-boot-starter-security-oauth2-resource-server
OK       starter spring-boot-starter-security-oauth2-authorization-server

all APIs found
```
