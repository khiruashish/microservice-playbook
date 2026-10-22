#!/usr/bin/env bash
# Checks the Spring APIs and defaults this chapter relies on against Spring Framework 7.0.9 and Spring Boot 4.1.1.
# Usage: ./check_apis.sh <spring-framework> <spring-boot>
set -uo pipefail
SF=$1 BOOT=$2
fail=0
check() {   # description, root, path, extended regex
  if grep -Eq -- "$4" "$2/$3" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "$3"; fail=$((fail + 1)); fi
}
R=spring-context/src/main/java/org/springframework/resilience/annotation
T=module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure

echo "-- Spring Framework 7.0.9"
check "@Retryable maxRetries defaults to 3"                     "$SF" $R/Retryable.java 'long maxRetries\(\) default 3;'
check "@Retryable delay defaults to 1000 ms"                     "$SF" $R/Retryable.java 'long delay\(\) default 1000;'
check "@Retryable jitter defaults to 0"                          "$SF" $R/Retryable.java 'long jitter\(\) default 0;'
check "@Retryable multiplier defaults to 1.0 (fixed delay)"      "$SF" $R/Retryable.java 'double multiplier\(\) default 1.0;'
check "@Retryable timeout defaults to 0 (none)"                  "$SF" $R/Retryable.java 'long timeout\(\) default 0;'
check "@Retryable maxDelay"                                      "$SF" $R/Retryable.java 'maxDelay\(\)'
check "@ConcurrencyLimit policy defaults to BLOCK"               "$SF" $R/ConcurrencyLimit.java 'ThrottlePolicy policy\(\) default ThrottlePolicy.BLOCK;'
check "@ConcurrencyLimit ThrottlePolicy.REJECT"                  "$SF" $R/ConcurrencyLimit.java 'REJECT'
check "@EnableResilientMethods"                                  "$SF" $R/EnableResilientMethods.java 'public @interface EnableResilientMethods'
check "HttpHeaders.containsHeader(String)"                       "$SF" spring-web/src/main/java/org/springframework/http/HttpHeaders.java 'public boolean containsHeader\(String headerName\)'
check "RestClient.Builder.requestInterceptor(...)"               "$SF" spring-web/src/main/java/org/springframework/web/client/RestClient.java 'Builder requestInterceptor\(ClientHttpRequestInterceptor interceptor\);'

echo "-- Spring Boot 4.1.1"
check "server.tomcat.threads.max defaults to 200"                "$BOOT" $T/TomcatServerProperties.java 'private int max = 200;'
check "server.tomcat.accept-count defaults to 100"               "$BOOT" $T/TomcatServerProperties.java 'private int acceptCount = 100;'
check "server.tomcat.max-connections defaults to 8192"           "$BOOT" $T/TomcatServerProperties.java 'private int maxConnections = 8192;'
check "virtual threads: Tomcat uses a VirtualThreadExecutor"     "$BOOT" $T/TomcatVirtualThreadsWebServerFactoryCustomizer.java 'setExecutor\(new VirtualThreadExecutor'
check "spring.http.clients.* (connect-timeout, read-timeout)"    "$BOOT" module/spring-boot-http-client/src/main/java/org/springframework/boot/http/client/autoconfigure/HttpClientsProperties.java '@ConfigurationProperties\("spring.http.clients"\)'
check "...read-timeout has no default"                           "$BOOT" module/spring-boot-http-client/src/main/java/org/springframework/boot/http/client/autoconfigure/HttpClientSettingsProperties.java 'private @Nullable Duration readTimeout;'

echo
[ $fail -eq 0 ] && echo "all checks passed" || { echo "$fail checks failed"; exit 1; }
