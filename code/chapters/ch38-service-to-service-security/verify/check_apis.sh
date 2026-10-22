#!/usr/bin/env bash
# Checks each Spring API, property and default this module relies on against the Spring Security 7.1.1,
# Spring Boot 4.1.1 and Spring Framework 7.0.9 sources: "OK" when the declaration is found where expected.
# Usage: ./check_apis.sh <spring-security> <spring-boot> <spring-framework>
set -uo pipefail
SS=$1
BOOT=$2
SF=$3
fail=0
check() {   # description, root, path, extended regex
  if grep -Eq -- "$4" "$2/$3" 2>/dev/null; then
    printf 'OK       %s\n' "$1"
  else
    printf 'MISSING  %s  (%s)\n' "$1" "$3"
    fail=$((fail + 1))
  fi
}
HS=config/src/main/java/org/springframework/security/config/annotation/web/builders/HttpSecurity.java
X509=web/src/main/java/org/springframework/security/web/authentication/preauth/x509
JOSE=oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt
RS=oauth2/oauth2-resource-server/src/main/java/org/springframework/security/oauth2/server/resource
CL=oauth2/oauth2-client/src/main/java/org/springframework/security/oauth2/client
AS=oauth2/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization
ASC=config/src/main/java/org/springframework/security/config/annotation/web/configurers/oauth2/server/authorization
SSLP=core/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/ssl
SRV=module/spring-boot-web-server/src/main/java/org/springframework/boot/web/server/Ssl.java
RC=module/spring-boot-restclient/src/main/java/org/springframework/boot/restclient/autoconfigure
OC=module/spring-boot-security-oauth2-client/src/main/java/org/springframework/boot/security/oauth2/client/autoconfigure
RSA=module/spring-boot-security-oauth2-resource-server/src/main/java/org/springframework/boot/security/oauth2/server/resource/autoconfigure
WEB=spring-web/src/main/java/org/springframework/web

echo "-- Spring Security 7.1.1: payment-service's filter chain"
check "HttpSecurity.addFilterBefore(Filter, Class)"           "$SS" "$HS" 'public HttpSecurity addFilterBefore\(Filter filter, Class<\? extends Filter> beforeFilter\)'
check "BearerTokenAuthenticationFilter"                        "$SS" "$RS/web/authentication/BearerTokenAuthenticationFilter.java" 'public final class BearerTokenAuthenticationFilter|public class BearerTokenAuthenticationFilter'
check "Fact: x509() reads the principal from the subject DN by default" "$SS" "$X509/X509AuthenticationFilter.java" 'principalExtractor = new SubjectX500PrincipalExtractor\(\)'
check "Fact: ...and takes the CN, not a URI SAN"               "$SS" "$X509/SubjectX500PrincipalExtractor.java" 'private String subjectDnType = CN_SUBJECT_DN_TYPE'
echo "-- Spring Security 7.1.1: certificate-bound tokens (RFC 8705)"
check "Fact: default JWT validators include the x5t#S256 check" "$SS" "$JOSE/JwtValidators.java" 'new X509CertificateThumbprintValidator\('
check "Fact: ...which reads the request's client certificate"  "$SS" "$JOSE/X509CertificateThumbprintValidator.java" '"jakarta.servlet.request.X509Certificate"'
check "Fact: ...and rejects a bound token without one"         "$SS" "$JOSE/X509CertificateThumbprintValidator.java" 'Unable to obtain X509Certificate from current request'
check "Fact: Boot's JwtDecoder uses those defaults"            "$BOOT" "$RSA/JwtDecoderConfiguration.java" 'JwtValidators.createDefaultWithValidators\(validators\)'
check "Fact: the authorization server adds cnf.x5t#S256"       "$SS" "$ASC/DefaultOAuth2TokenCustomizers.java" 'cnfClaims.put\("x5t#S256"'
check "TokenSettings.x509CertificateBoundAccessTokens"         "$SS" "$AS/settings/TokenSettings.java" 'public Builder x509CertificateBoundAccessTokens\(boolean'
check "Fact: tls_client_auth matches the subject DN only"      "$SS" "$AS/authentication/X509ClientCertificateAuthenticationProvider.java" 'getSubjectX500Principal\(\).getName\(\).equals\(expectedSubjectDN\)'
check "X509ClientCertificateAuthenticationProvider.setCertificateVerifier" "$SS" "$AS/authentication/X509ClientCertificateAuthenticationProvider.java" 'public void setCertificateVerifier\('
echo "-- Spring Security 7.1.1: client credentials from a RestClient"
check "AuthorizedClientServiceOAuth2AuthorizedClientManager(repo, service)" "$SS" "$CL/AuthorizedClientServiceOAuth2AuthorizedClientManager.java" 'public AuthorizedClientServiceOAuth2AuthorizedClientManager\('
check "...setAuthorizedClientProvider"                         "$SS" "$CL/AuthorizedClientServiceOAuth2AuthorizedClientManager.java" 'public void setAuthorizedClientProvider\('
check "OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials()" "$SS" "$CL/OAuth2AuthorizedClientProviderBuilder.java" 'public OAuth2AuthorizedClientProviderBuilder clientCredentials\(\)'
check "OAuth2ClientHttpRequestInterceptor(OAuth2AuthorizedClientManager)" "$SS" "$CL/web/client/OAuth2ClientHttpRequestInterceptor.java" 'public OAuth2ClientHttpRequestInterceptor\(OAuth2AuthorizedClientManager'
check "...setPrincipalResolver"                                "$SS" "$CL/web/client/OAuth2ClientHttpRequestInterceptor.java" 'public void setPrincipalResolver\(PrincipalResolver'
check "Fact: default principal = the request's authenticated user" "$SS" "$CL/web/client/OAuth2ClientHttpRequestInterceptor.java" 'principalResolver = new SecurityContextHolderPrincipalResolver\(\)'
check "Fact: no principal → one anonymous principal"           "$SS" "$CL/web/client/OAuth2ClientHttpRequestInterceptor.java" 'principal = ANONYMOUS_AUTHENTICATION'
check "RequestAttributePrincipalResolver"                      "$SS" "$CL/web/client/RequestAttributePrincipalResolver.java" 'class RequestAttributePrincipalResolver implements OAuth2ClientHttpRequestInterceptor.PrincipalResolver'
check "RequestAttributeClientRegistrationIdResolver.clientRegistrationId(String)" "$SS" "$CL/web/client/RequestAttributeClientRegistrationIdResolver.java" 'public static Consumer<Map<String, Object>> clientRegistrationId\(String'
check "Fact: tokens stored per (registration, principal name)" "$SS" "$CL/InMemoryOAuth2AuthorizedClientService.java" 'new OAuth2AuthorizedClientId\(clientRegistrationId, principalName\)'
check "Fact: client-credentials renewal skew 60 s"             "$SS" "$CL/ClientCredentialsOAuth2AuthorizedClientProvider.java" 'clockSkew = Duration.ofSeconds\(60\)'
echo "-- Spring Boot 4.1.1: SSL bundles and clients"
check "server.ssl.bundle"                                      "$BOOT" "$SRV" 'private @Nullable String bundle'
check "server.ssl.client-auth (NONE/WANT/NEED)"                "$BOOT" "$SRV" 'private @Nullable ClientAuth clientAuth'
check "spring.ssl.bundle.pem.*.keystore / truststore"          "$BOOT" "$SSLP/PemSslBundleProperties.java" 'private final Store truststore = new Store\(\)'
check "...certificate and private-key"                         "$BOOT" "$SSLP/PemSslBundleProperties.java" 'private @Nullable String privateKey'
check "spring.ssl.bundle.pem.*.reload-on-update"               "$BOOT" "$SSLP/SslBundleProperties.java" 'private boolean reloadOnUpdate'
check "spring.ssl.bundle.watch.file.quiet-period (default 10 s)" "$BOOT" "$SSLP/SslProperties.java" 'quietPeriod = Duration.ofSeconds\(10\)'
check "Fact: watched PEM files must be file resources"         "$BOOT" "$SSLP/BundleContentProperty.java" 'throw new BundleContentNotWatchableException'
check "SslBundles.addBundleUpdateHandler(String, Consumer)"    "$BOOT" core/spring-boot/src/main/java/org/springframework/boot/ssl/SslBundles.java 'void addBundleUpdateHandler\(String name, Consumer<SslBundle> updateHandler\)'
check "Fact: Tomcat re-reads an updated bundle"                "$BOOT" module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/TomcatWebServerFactory.java 'sslBundles.addBundleUpdateHandler\(sslBundleName'
check "RestClientSsl.fromBundle(SslBundle)"                    "$BOOT" "$RC/RestClientSsl.java" 'Consumer<RestClient.Builder> fromBundle\(SslBundle bundle\)'
check "Fact: fromBundle builds the request factory once"       "$BOOT" "$RC/AutoConfiguredRestClientSsl.java" 'builder.requestFactory\(requestFactory\(bundle\)\)'
check "Fact: Boot's default authorized-client service is in memory" "$BOOT" "$OC/OAuth2ClientConfigurations.java" 'new InMemoryOAuth2AuthorizedClientService\(clientRegistrationRepository\)'
check "starter spring-boot-starter-restclient"                 "$BOOT" starter/spring-boot-starter-restclient/build.gradle 'description = '
check "starter spring-boot-starter-security-oauth2-client"     "$BOOT" starter/spring-boot-starter-security-oauth2-client/build.gradle 'description = '
echo "-- Spring Framework 7.0.9: RestClient and filters"
check "RestClient.Builder.clone()"                             "$SF" "$WEB/client/RestClient.java" 'Builder clone\(\);'
check "RestClient.Builder.apply(Consumer)"                     "$SF" "$WEB/client/RestClient.java" 'Builder apply\(Consumer<Builder> builderConsumer\)'
check "RestClient.Builder.requestInterceptor"                  "$SF" "$WEB/client/RestClient.java" 'Builder requestInterceptor\(ClientHttpRequestInterceptor interceptor\)'
check "RestClient request attributes(Consumer<Map>)"           "$SF" "$WEB/client/RestClient.java" 'S attributes\(Consumer<Map<String, Object>> attributesConsumer\)'
check "OncePerRequestFilter.doFilterInternal"                  "$SF" "$WEB/filter/OncePerRequestFilter.java" 'protected abstract void doFilterInternal\('
echo
if [ "$fail" -eq 0 ]; then echo "all APIs found"; else echo "$fail missing"; exit 1; fi
