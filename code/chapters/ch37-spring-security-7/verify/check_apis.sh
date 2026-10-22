#!/usr/bin/env bash
# Checks each Spring Security / Spring Boot API and property this module relies on against the 7.1.1 / 4.1.1
# sources: "OK" when the declaration is found where expected. Usage: ./check_apis.sh <spring-security> <spring-boot>
set -uo pipefail
SS=$1
BOOT=$2
fail=0
check() {   # description, root, path, extended regex
  if grep -Eq -- "$4" "$2/$3" 2>/dev/null; then
    printf 'OK       %s\n' "$1"
  else
    printf 'MISSING  %s  (%s)\n' "$1" "$3"
    fail=$((fail + 1))
  fi
}
CFG=config/src/main/java/org/springframework/security/config/annotation/web
HS=$CFG/builders/HttpSecurity.java
AS=oauth2/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization
RS=oauth2/oauth2-resource-server/src/main/java/org/springframework/security/oauth2/server/resource
JOSE=oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt
TEST=test/src/main/java/org/springframework/security/test/web/servlet/request/SecurityMockMvcRequestPostProcessors.java
CRYPTO=crypto/src/main/java/org/springframework/security/crypto
RSP=module/spring-boot-security-oauth2-resource-server/src/main/java/org/springframework/boot/security/oauth2/server/resource/autoconfigure/OAuth2ResourceServerProperties.java
ASP=module/spring-boot-security-oauth2-authorization-server/src/main/java/org/springframework/boot/security/oauth2/server/authorization/autoconfigure/servlet/OAuth2AuthorizationServerProperties.java

echo "-- Spring Security 7.1.1: configuration DSL"
check "HttpSecurity.authorizeHttpRequests(Customizer)"      "$SS" "$HS" 'public HttpSecurity authorizeHttpRequests\('
check "HttpSecurity.oauth2ResourceServer(Customizer)"       "$SS" "$HS" 'public HttpSecurity oauth2ResourceServer\('
check "HttpSecurity.oauth2AuthorizationServer(Customizer)"  "$SS" "$HS" 'public HttpSecurity oauth2AuthorizationServer\('
check "HttpSecurity.sessionManagement / csrf / httpBasic / formLogin" "$SS" "$HS" 'public HttpSecurity formLogin\(Customizer'
check "registry.requestMatchers(HttpMethod, String...)"     "$SS" config/src/main/java/org/springframework/security/config/annotation/web/AbstractRequestMatcherRegistry.java 'public C requestMatchers\(HttpMethod method, String\.\.\. patterns\)'
check "AuthorizedUrl.hasRole / hasAuthority / denyAll"      "$SS" "$CFG/configurers/AuthorizeHttpRequestsConfigurer.java" 'public AuthorizationManagerRequestMatcherRegistry denyAll\(\)'
check "OAuth2ResourceServerConfigurer.jwt(Customizer)"      "$SS" "$CFG/configurers/oauth2/server/resource/OAuth2ResourceServerConfigurer.java" 'public OAuth2ResourceServerConfigurer<H> jwt\(Customizer<JwtConfigurer>'
check "JwtConfigurer.jwtAuthenticationConverter(Converter)" "$SS" "$CFG/configurers/oauth2/server/resource/OAuth2ResourceServerConfigurer.java" 'public JwtConfigurer jwtAuthenticationConverter\('
check "SessionManagementConfigurer.sessionCreationPolicy"   "$SS" "$CFG/configurers/SessionManagementConfigurer.java" 'public SessionManagementConfigurer<H> sessionCreationPolicy\('
check "@EnableMethodSecurity"                               "$SS" config/src/main/java/org/springframework/security/config/annotation/method/configuration/EnableMethodSecurity.java 'public @interface EnableMethodSecurity'
check "@PreAuthorize"                                       "$SS" core/src/main/java/org/springframework/security/access/prepost/PreAuthorize.java 'public @interface PreAuthorize'
check "AuthorizationDeniedException"                        "$SS" core/src/main/java/org/springframework/security/authorization/AuthorizationDeniedException.java 'class AuthorizationDeniedException'
echo "-- Spring Security 7.1.1: resource server"
check "JwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter" "$SS" "$RS/authentication/JwtAuthenticationConverter.java" 'public void setJwtGrantedAuthoritiesConverter\('
check "JwtAuthenticationConverter.setPrincipalClaimName"    "$SS" "$RS/authentication/JwtAuthenticationConverter.java" 'public void setPrincipalClaimName\('
check "Default JWT validation: typ JWT or absent"           "$SS" "$JOSE/JwtTypeValidator.java" 'validator.setAllowEmpty\(true\)'
check "RFC 9068 validator: JwtValidators.createAtJwtValidator()" "$SS" "$JOSE/JwtValidators.java" 'public static AtJwtBuilder createAtJwtValidator\(\)'
echo "-- Spring Security 7.1.1: authorization server (built in since 7.0)"
check "OAuth2TokenCustomizer"                               "$SS" "$AS/token/OAuth2TokenCustomizer.java" 'interface OAuth2TokenCustomizer'
check "JwtEncodingContext.getJwsHeader / getClaims"         "$SS" "$AS/token/JwtEncodingContext.java" 'public JwtClaimsSet.Builder getClaims\(\)'
check "OAuth2TokenContext.getAuthorizationGrant / getAuthorizedScopes" "$SS" "$AS/token/OAuth2TokenContext.java" 'default Set<String> getAuthorizedScopes\(\)'
check "JoseHeader builder .type(String)"                    "$SS" "$JOSE/JoseHeader.java" 'public B type\(String type\)'
check "JwtClaimsSet.Builder.audience(List)"                 "$SS" "$JOSE/JwtClaimsSet.java" 'public Builder audience\(List<String> audience\)'
check "OAuth2TokenExchangeAuthenticationToken.getAudiences" "$SS" "$AS/authentication/OAuth2TokenExchangeAuthenticationToken.java" 'public Set<String> getAudiences\(\)'
check "Fact: default access-token audience is the client id" "$SS" "$AS/token/JwtGenerator.java" 'audience\(Collections.singletonList\(registeredClient.getClientId\(\)\)\)'
check "Fact: exchanged scopes are checked against the requesting client's registration" "$SS" "$AS/authentication/OAuth2TokenExchangeAuthenticationProvider.java" 'validateRequestedScopes\(registeredClient, tokenExchangeAuthentication.getScopes\(\)\)'
echo "-- Spring Security 7.1.1: users, passwords, tests"
check "InMemoryUserDetailsManager(UserDetails...)"          "$SS" core/src/main/java/org/springframework/security/provisioning/InMemoryUserDetailsManager.java 'public InMemoryUserDetailsManager\(UserDetails\.\.\. users\)'
check "BCryptPasswordEncoder default strength 10"           "$SS" "$CRYPTO/bcrypt/BCryptPasswordEncoder.java" 'this.strength = \(strength == -1\) \? 10 : strength'
check "Pbkdf2 v5_8 defaults: 310,000 iterations"            "$SS" "$CRYPTO/password/Pbkdf2PasswordEncoder.java" 'DEFAULT_ITERATIONS = 310000'
check "SecurityMockMvcRequestPostProcessors.jwt()"          "$SS" "$TEST" 'public static JwtRequestPostProcessor jwt\(\)'
check "JwtRequestPostProcessor.authorities(Converter)"      "$SS" "$TEST" 'public JwtRequestPostProcessor authorities\(Converter<Jwt, Collection<GrantedAuthority>>'
echo "-- Spring Boot 4.1.1: properties and test support"
check "spring.security.oauth2.resourceserver.jwt.issuer-uri"     "$BOOT" "$RSP" 'private @Nullable String issuerUri'
check "spring.security.oauth2.resourceserver.jwt.audiences"      "$BOOT" "$RSP" 'private List<String> audiences'
check "spring.security.oauth2.resourceserver.jwt.jws-algorithms" "$BOOT" "$RSP" 'private List<String> jwsAlgorithms = Arrays.asList\("RS256"\)'
check "spring.security.oauth2.authorizationserver.client.*.registration" "$BOOT" "$ASP" 'private final Registration registration'
check "...client.*.require-proof-key (default true)"        "$BOOT" "$ASP" 'private boolean requireProofKey = true'
check "...client.*.token.reuse-refresh-tokens (default true)" "$BOOT" "$ASP" 'private boolean reuseRefreshTokens = true'
check "...client.*.token.access-token-time-to-live (default 5m)" "$BOOT" "$ASP" 'accessTokenTimeToLive = Duration.ofMinutes\(5\)'
check "@AutoConfigureMockMvc in spring-boot-webmvc-test"    "$BOOT" module/spring-boot-webmvc-test/src/main/java/org/springframework/boot/webmvc/test/autoconfigure/AutoConfigureMockMvc.java 'public @interface AutoConfigureMockMvc'
check "starter spring-boot-starter-security-oauth2-resource-server" "$BOOT" platform/spring-boot-dependencies/build.gradle '"spring-boot-starter-security-oauth2-resource-server"'
check "starter spring-boot-starter-security-oauth2-authorization-server" "$BOOT" platform/spring-boot-dependencies/build.gradle '"spring-boot-starter-security-oauth2-authorization-server"'
echo
[ $fail -eq 0 ] && echo "all APIs found" || echo "$fail missing"
exit $fail
