#!/usr/bin/env bash
# Checks each API, property and default this chapter relies on against the sources of the versions used:
# Spring Boot 4.1.1, Spring Framework 7.0.9, Spring Security 7.1.1, Spring Cloud Gateway 5.0.2 (Spring Cloud
# 2025.1.2), Jackson 3.1.5 and OWASP CRS 4.30.0.
# Usage: ./check_apis.sh <boot> <framework> <security> <gateway> <jackson-core> <jackson-databind> <crs>
set -uo pipefail
BOOT=$1 SF=$2 SS=$3 SCG=$4 JC=$5 JD=$6 CRS=$7
fail=0
check() {   # description, root, path, extended regex
  if grep -Eq -- "$4" "$2/$3" 2>/dev/null; then
    printf 'OK       %s\n' "$1"
  else
    printf 'MISSING  %s  (%s)\n' "$1" "$3"
    fail=$((fail + 1))
  fi
}
exists() {  # description, root, path
  if [ -e "$2/$3" ]; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "$3"; fail=$((fail + 1)); fi
}
T=module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/TomcatServerProperties.java
J=module/spring-boot-jackson/src/main/java/org/springframework/boot/jackson/autoconfigure/JacksonProperties.java
CORS=spring-web/src/main/java/org/springframework/web/cors/CorsConfiguration.java
SSW=web/src/main/java/org/springframework/security/web/csrf
SSC=config/src/main/java/org/springframework/security/config/annotation/web
G=spring-cloud-gateway-server-webflux/src/main
R=$G/java/org/springframework/cloud/gateway/filter/ratelimit

echo "-- Spring Boot 4.1.1"
check "server.max-http-request-header-size defaults to 8KB"       "$BOOT" module/spring-boot-web-server/src/main/java/org/springframework/boot/web/server/autoconfigure/ServerProperties.java 'maxHttpRequestHeaderSize = DataSize.ofKilobytes\(8\)'
check "server.tomcat.max-http-form-post-size defaults to 2MB"     "$BOOT" "$T" 'maxHttpFormPostSize = DataSize.ofMegabytes\(2\)'
check "server.tomcat.max-swallow-size defaults to 2MB"            "$BOOT" "$T" 'maxSwallowSize = DataSize.ofMegabytes\(2\)'
check "server.tomcat.max-parameter-count defaults to 1000"        "$BOOT" "$T" 'maxParameterCount = 1000'
check "server.tomcat.remoteip.internal-proxies: CIDR list..."     "$BOOT" "$T" 'comma separate list of'
check "...whose default trusts 10.0.0.0/8 and 100.64.0.0/10"      "$BOOT" "$T" '"10.0.0.0/8, 100.64.0.0/10'
check "server.servlet.session.cookie.same-site (Cookie.sameSite)" "$BOOT" module/spring-boot-web-server/src/main/java/org/springframework/boot/web/server/Cookie.java 'private @Nullable SameSite sameSite'
check "spring.jackson.deserialization.* (feature map)"            "$BOOT" "$J" 'Map<DeserializationFeature, Boolean> deserialization'
check "spring.jackson.factory.constraints.read..."                "$BOOT" "$J" 'private final Factory factory = new Factory\(\)'
check "...max-nesting-depth defaults to 500"                      "$BOOT" "$J" 'private int maxNestingDepth = 500'
check "...max-document-length defaults to unlimited (-1)"         "$BOOT" "$J" 'private long maxDocumentLength = -1L'
check "...max-string-length defaults to 100,000,000"              "$BOOT" "$J" 'private int maxStringLength = 100_000_000'
check "Jackson 3.1.5 is the managed version"                      "$BOOT" gradle.properties '^jacksonVersion=3\.1\.5$'
check "spring.mvc.problemdetails.enabled (default false)"         "$BOOT" module/spring-boot-webmvc/src/main/java/org/springframework/boot/webmvc/autoconfigure/WebMvcProperties.java 'private boolean enabled;'
check "spring.security.oauth2.resourceserver.jwt.audiences"       "$BOOT" module/spring-boot-security-oauth2-resource-server/src/main/java/org/springframework/boot/security/oauth2/server/resource/autoconfigure/OAuth2ResourceServerProperties.java 'private List<String> audiences'
check "spring.data.redis.timeout"                                 "$BOOT" module/spring-boot-data-redis/src/main/java/org/springframework/boot/data/redis/autoconfigure/DataRedisProperties.java 'private @Nullable Duration timeout'
exists "spring-boot-starter-validation"                           "$BOOT" starter/spring-boot-starter-validation
exists "spring-boot-starter-security-oauth2-resource-server"      "$BOOT" starter/spring-boot-starter-security-oauth2-resource-server
exists "spring-boot-starter-security-oauth2-client"               "$BOOT" starter/spring-boot-starter-security-oauth2-client
exists "spring-boot-starter-data-redis"                           "$BOOT" starter/spring-boot-starter-data-redis

echo "-- Spring Framework 7.0.9"
check "allowCredentials with allowedOrigins \"*\" is rejected"    "$SF" "$CORS" 'When allowCredentials is true, allowedOrigins cannot contain the special value'
check "allowedOrigins are compared ignoring case"                 "$SF" "$CORS" 'originToCheck.equalsIgnoreCase\(allowedOrigin\)'
check "allowedOriginPatterns: the pattern is quoted..."           "$SF" "$CORS" 'patternValue = "\\\\Q" \+ patternValue \+ "\\\\E"'
check "...and every * becomes .* (any characters, dots too)"      "$SF" "$CORS" 'patternValue.replace\("\*", "\\\\E\.\*\\\\Q"\)'
check "ProblemDetail.forStatusAndDetail / setProperty"            "$SF" spring-web/src/main/java/org/springframework/http/ProblemDetail.java 'public void setProperty\(String name, @Nullable Object value\)'

echo "-- Spring Security 7.1.1"
check "csrf.spa() (since 7.0)..."                                 "$SS" "$SSC/configurers/CsrfConfigurer.java" 'public CsrfConfigurer<H> spa\(\)'
check "...uses CookieCsrfTokenRepository.withHttpOnlyFalse()"     "$SS" "$SSC/configurers/CsrfConfigurer.java" 'this.csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse\(\)'
check "default CSRF repository: the HTTP session"                 "$SS" "$SSC/configurers/CsrfConfigurer.java" 'csrfTokenRepository = new HttpSessionCsrfTokenRepository\(\)'
check "CSRF not required for GET, HEAD, TRACE, OPTIONS"           "$SS" "$SSW/CsrfFilter.java" 'Arrays.asList\("GET", "HEAD", "TRACE", "OPTIONS"\)'
check "cookie XSRF-TOKEN, header X-XSRF-TOKEN"                    "$SS" "$SSW/CookieCsrfTokenRepository.java" 'DEFAULT_CSRF_HEADER_NAME = "X-XSRF-TOKEN"'
check "HttpSecurity.securityMatcher(String...)"                   "$SS" "$SSC/builders/HttpSecurity.java" 'public HttpSecurity securityMatcher\(String\.\.\. patterns\)'
check "HttpSecurity.cors(Customizer)"                             "$SS" "$SSC/builders/HttpSecurity.java" 'public HttpSecurity cors\(Customizer<CorsConfigurer<HttpSecurity>>'
check "HttpSecurity.addFilterAfter(Filter, Class)"                "$SS" "$SSC/builders/HttpSecurity.java" 'public HttpSecurity addFilterAfter\(Filter filter'

echo "-- Spring Cloud Gateway 5.0.2"
check "properties under spring.cloud.gateway.server.webflux"      "$SCG" $G/java/org/springframework/cloud/gateway/config/GatewayProperties.java 'PREFIX = "spring.cloud.gateway.server.webflux"'
check "RedisRateLimiter script: Redis TIME in whole seconds"      "$SCG" $G/resources/META-INF/scripts/request_rate_limiter.lua "redis.call\('TIME'\)\[1\]"
check "RedisRateLimiter passes an empty 'now'"                    "$SCG" "$R/RedisRateLimiter.java" 'burstCapacity \+ "", "", requestedTokens'
check "RedisRateLimiter fails open on Redis errors"               "$SCG" "$R/RedisRateLimiter.java" 'return Flux.just\(Arrays.asList\(1L, -1L\)\)'
check "RedisRateLimiter headers are X-RateLimit-*"                "$SCG" "$R/RedisRateLimiter.java" 'REMAINING_HEADER = "X-RateLimit-Remaining"'
check "RequestRateLimiter: denyEmptyKey true, 403 by default"     "$SCG" $G/java/org/springframework/cloud/gateway/filter/factory/RequestRateLimiterGatewayFilterFactory.java 'emptyKeyStatusCode = HttpStatus.FORBIDDEN.name\(\)'

echo "-- Jackson 3.1.5"
check "StreamReadConstraints: max depth 500"                      "$JC" src/main/java/tools/jackson/core/StreamReadConstraints.java 'DEFAULT_MAX_DEPTH = 500'
check "...max document length unlimited"                          "$JC" src/main/java/tools/jackson/core/StreamReadConstraints.java 'DEFAULT_MAX_DOC_LEN = -1L'
check "...max string length 100,000,000"                          "$JC" src/main/java/tools/jackson/core/StreamReadConstraints.java 'DEFAULT_MAX_STRING_LEN = 100_000_000'
check "FAIL_ON_UNKNOWN_PROPERTIES is off by default in Jackson 3" "$JD" src/main/java/tools/jackson/databind/DeserializationFeature.java 'FAIL_ON_UNKNOWN_PROPERTIES\(false\)'

echo "-- OWASP CRS 4.30.0"
check "inbound anomaly score threshold defaults to 5"             "$CRS" rules/REQUEST-901-INITIALIZATION.conf "setvar:'tx.inbound_anomaly_score_threshold=5'"
check "blocking paranoia level defaults to 1"                     "$CRS" rules/REQUEST-901-INITIALIZATION.conf "setvar:'tx.blocking_paranoia_level=1'"
check "allowed methods default to GET HEAD POST OPTIONS"          "$CRS" rules/REQUEST-901-INITIALIZATION.conf "setvar:'tx.allowed_methods=GET HEAD POST OPTIONS'"
check "rule 911100 enforces the allowed methods"                  "$CRS" rules/REQUEST-911-METHOD-ENFORCEMENT.conf 'id:911100'
check "plain application/json is parsed by the engine's rule 200001, not CRS" "$CRS" rules/REQUEST-901-INITIALIZATION.conf 'Mirrors the sample rule 200006 in modsecurity.conf-recommended'

echo
[ $fail -eq 0 ] && echo "all checks passed" || { echo "$fail checks failed"; exit 1; }
